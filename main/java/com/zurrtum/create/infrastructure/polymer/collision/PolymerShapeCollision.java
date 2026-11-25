package com.zurrtum.create.infrastructure.polymer.collision;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.contraptions.actors.seat.SeatBlock;
import com.zurrtum.create.content.contraptions.chassis.StickerBlock;
import com.zurrtum.create.content.contraptions.mounted.CartAssemblerBlock;
import com.zurrtum.create.content.decoration.copycat.CopycatBlock;
import com.zurrtum.create.content.decoration.slidingDoor.SlidingDoorBlock;
import com.zurrtum.create.content.kinetics.belt.BeltBlock;
import com.zurrtum.create.content.trains.track.TrackBlock;
import com.zurrtum.create.infrastructure.polymer.AdapterHolder;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import eu.pb4.polymer.blocks.impl.BlockExtBlockMapper;
import eu.pb4.polymer.core.api.block.BlockMapper;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockAwareAttachment;
import eu.pb4.polymer.virtualentity.api.elements.AbstractElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public final class PolymerShapeCollision {
    static final int CELL_BUDGET = 64;
    private static final double MIN_SIDE = 1 / 16.0;

    private static volatile Map<BlockState, List<AABB>> cells = Map.of();

    private PolymerShapeCollision() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> install());
        BlockMapper.DEFAULT_MAPPER_EVENT.register(
                (context, mapper) -> PolymerNativeClients.isNative(context) ? mapper : vanillaMapper(mapper));
    }

    private static boolean invisible(BlockState client) {
        return client.is(Blocks.BARRIER) || client.isAir();
    }

    private static BlockState carrier(BlockState source) {
        return Blocks.SCULK_VEIN
                .defaultBlockState()
                .setValue(
                        BlockStateProperties.WATERLOGGED,
                        !source.getFluidState().isEmpty());
    }

    private static BlockMapper vanillaMapper(BlockMapper fallback) {
        return new BlockMapper() {
            @Override
            public BlockState toClientSideState(BlockState state, PacketContext context) {
                BlockState client = fallback.toClientSideState(state, context);
                return cells.containsKey(state) && invisible(client) ? carrier(state) : client;
            }

            @Override
            public String getMapperName() {
                return "createpoly:shape_collision/" + fallback.getMapperName();
            }
        };
    }

    private static boolean excluded(Block block) {
        return block instanceof BeltBlock
                || block instanceof SeatBlock
                || block == AllBlocks.TURNTABLE
                || block instanceof CopycatBlock
                || block instanceof CartAssemblerBlock
                || block instanceof TrackBlock
                || block instanceof SlidingDoorBlock
                || block instanceof StickerBlock;
    }

    private static void install() {
        long started = System.nanoTime();
        Map<BlockState, List<AABB>> found = new IdentityHashMap<>();
        Set<Block> blocks = new LinkedHashSet<>();
        int visual = 0, thin = 0, budget = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (id == null || !id.getNamespace().equals(Create.MOD_ID) || excluded(block)) continue;
            if (block.hasDynamicShape()) continue;
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (PolymerStaticCollision.targets(state) || PolymerStaticCollision.carrier(state) != null) continue;
                if (PolymerBlockCollision.thinCarrier(state) != null) continue;
                VoxelShape source = PolymerBlockCollision.shape(state);
                if (source.isEmpty()) continue;
                BlockState client = BlockExtBlockMapper.INSTANCE.toClientSideState(state, null);
                if (PolymerBlockCollision.sameShape(source, PolymerBlockCollision.shape(client))) continue;
                if (!invisible(client)) {
                    visual++;
                    continue;
                }
                Cover cover = cover(source);
                if (cover.cells == null) {
                    if (cover.thin) thin++;
                    else budget++;
                    continue;
                }
                found.put(state, cover.cells);
                blocks.add(block);
            }
        }
        cells = Collections.unmodifiableMap(found);
        for (Block block : blocks) wrapHolder(block);
        Create.LOGGER.info(
                "Exact source collision cells for {} states of {} display-backed blocks in {} ms; unchanged: {} visual carriers, {} thinner than a cell, {} over the {}-cell budget",
                found.size(),
                blocks.size(),
                (System.nanoTime() - started) / 1_000_000,
                visual,
                thin,
                budget,
                CELL_BUDGET);
    }

    private record Cover(@Nullable List<AABB> cells, boolean thin) {}

    private static Cover cover(VoxelShape shape) {
        List<AABB> boxes = shape.toAabbs();
        for (AABB box : boxes)
            if (Math.min(box.getXsize(), Math.min(box.getYsize(), box.getZsize())) < MIN_SIDE - 1e-9)
                return new Cover(null, true);
        List<AABB> best = perBox(boxes);
        if ((best == null || best.size() > CELL_BUDGET / 4) && onGrid(boxes)) {
            try {
                List<AABB> tiled = PolymerStaticCollision.tile(shape);
                if (best == null || tiled.size() < best.size()) best = tiled;
            } catch (IllegalStateException ignored) {
            }
        }
        if (best == null || best.size() > CELL_BUDGET) return new Cover(null, false);
        VoxelShape union = Shapes.empty();
        for (AABB cell : best) union = Shapes.or(union, Shapes.create(cell));
        return PolymerBlockCollision.sameShape(shape, union)
                ? new Cover(List.copyOf(best), false)
                : new Cover(null, false);
    }

    private static @Nullable List<AABB> perBox(List<AABB> boxes) {
        List<AABB> result = new ArrayList<>();
        for (AABB box : boxes) {
            double side = Math.min(box.getXsize(), Math.min(box.getYsize(), box.getZsize()));
            double[] xs = starts(box.minX, box.maxX, side),
                    ys = starts(box.minY, box.maxY, side),
                    zs = starts(box.minZ, box.maxZ, side);
            if (result.size() + (long) xs.length * ys.length * zs.length > CELL_BUDGET) return null;
            for (double x : xs)
                for (double y : ys) for (double z : zs) result.add(new AABB(x, y, z, x + side, y + side, z + side));
        }
        return result;
    }

    private static double[] starts(double min, double max, double side) {
        int count = Math.max(1, (int) Math.ceil((max - min) / side - 1e-9));
        double[] starts = new double[count];
        for (int i = 0; i < count; i++) starts[i] = count == 1 ? min : min + i * (max - min - side) / (count - 1);
        return starts;
    }

    private static boolean onGrid(List<AABB> boxes) {
        for (AABB box : boxes)
            for (double value : new double[] {box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ})
                if (Math.abs(Math.rint(value * 16) / 16 - value) > 1e-8) return false;
        return true;
    }

    private static void wrapHolder(Block block) {
        BlockWithElementHolder delegate = BlockWithElementHolder.get(block.defaultBlockState());
        BlockWithElementHolder.registerOverlay(block, new BlockWithElementHolder() {
            @Override
            public @Nullable ElementHolder createElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
                ElementHolder holder = delegate == null ? null : delegate.createElementHolder(level, pos, state);
                if (holder == null) holder = new AdapterHolder();
                holder.addElement(new Cells(level, pos)).show(state);
                return holder;
            }

            @Override
            public Vec3 getElementHolderOffset(ServerLevel level, BlockPos pos, BlockState state) {
                return delegate == null
                        ? BlockWithElementHolder.super.getElementHolderOffset(level, pos, state)
                        : delegate.getElementHolderOffset(level, pos, state);
            }

            @Override
            public boolean tickElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
                return true;
            }

            @Override
            public @Nullable ElementHolder createMovingElementHolder(
                    ServerLevel level, BlockPos pos, BlockState state, @Nullable ElementHolder current) {
                return delegate == null ? null : delegate.createMovingElementHolder(level, pos, state, current);
            }

            @Override
            public @Nullable ElementHolder createStaticElementHolder(
                    ServerLevel level, BlockPos pos, BlockState state, @Nullable ElementHolder current) {
                return delegate == null ? null : delegate.createStaticElementHolder(level, pos, state, current);
            }
        });
    }

    private static final class Cells extends AbstractElement {
        private final ServerLevel level;
        private final BlockPos pos;
        private @Nullable BlockState shown;
        private List<VirtualElement> spawned = List.of();

        Cells(ServerLevel level, BlockPos pos) {
            this.level = level;
            this.pos = pos.immutable();
        }

        void show(BlockState state) {
            ElementHolder holder = getHolder();
            if (holder == null || state == shown) return;
            for (VirtualElement cell : spawned) holder.removeElement(cell);
            shown = state;
            List<AABB> boxes = cells.get(state);
            spawned = boxes == null ? List.of() : PolymerStaticCollision.add(holder, level, pos, boxes);
        }

        @Override
        public void tick() {
            ElementHolder holder = getHolder();
            BlockAwareAttachment attachment = holder == null ? null : BlockAwareAttachment.get(holder);
            if (attachment != null) show(attachment.getBlockState());
        }

        @Override
        public IntList getEntityIds() {
            return IntList.of();
        }

        @Override
        public void startWatching(ServerPlayer player, Consumer<Packet<ClientGamePacketListener>> packets) {}

        @Override
        public void stopWatching(ServerPlayer player, Consumer<Packet<ClientGamePacketListener>> packets) {}

        @Override
        public void notifyMove(Vec3 oldPos, Vec3 newPos, Vec3 delta) {}
    }
}
