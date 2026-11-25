package com.zurrtum.create.infrastructure.polymer.block;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.infrastructure.polymer.PolymerBlockProjections;
import com.zurrtum.create.infrastructure.polymer.PolymerLitModelHolder;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockAwareAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public final class PolymerFluidVisuals {
    private static boolean initialized;

    public static boolean register(Block block) {
        if (block != AllBlocks.HONEY && block != AllBlocks.CHOCOLATE) return false;
        if (!initialized) {
            initialized = true;
            PolymerFluidMeshes.registerModels();
        }
        PolymerBlock.registerOverlay(block, new FluidCarrier());
        BlockWithElementHolder.registerOverlay(block, new Provider());
        return true;
    }

    private record FluidCarrier() implements PolymerBlock {
        @Override
        public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
            return Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, state.getValue(LiquidBlock.LEVEL));
        }

        @Override
        public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
            return Blocks.AIR.defaultBlockState();
        }
    }

    private record Provider() implements BlockWithElementHolder {
        @Override
        public ElementHolder createElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
            return new FluidHolder(level, pos);
        }

        @Override
        public boolean tickElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
            return true;
        }
    }

    record Surface(String fluid, int mask, float nw, float ne, float sw, float se, int flow) {}

    static Surface surface(ServerLevel level, BlockPos pos) {
        // Polymer constructs holders while their LevelChunk is still loading. A normal world lookup here waits for
        // that same unfinished chunk. Read only completed chunks, including queries inside source shapes.
        BlockGetter view = new LoadedView(level);
        BlockState state = view.getBlockState(pos);
        if (state.getBlock() != AllBlocks.HONEY && state.getBlock() != AllBlocks.CHOCOLATE) return null;
        FluidState fluid = state.getFluidState();
        Fluid type = fluid.getType();
        float center = height(view, type, pos);
        float n = height(view, type, pos.north()), s = height(view, type, pos.south());
        float w = height(view, type, pos.west()), e = height(view, type, pos.east());
        float nw =
                center >= 1 ? 1 : average(view, type, center, n, w, pos.north().west());
        float ne =
                center >= 1 ? 1 : average(view, type, center, n, e, pos.north().east());
        float sw =
                center >= 1 ? 1 : average(view, type, center, s, w, pos.south().west());
        float se =
                center >= 1 ? 1 : average(view, type, center, s, e, pos.south().east());
        int mask = 0;
        for (Direction side : Direction.values()) {
            BlockPos next = pos.relative(side);
            BlockState neighbour = view.getBlockState(next);
            if (fluid.getType().isSame(neighbour.getFluidState().getType())) continue;
            float faceHeight = switch (side) {
                case UP -> Math.min(Math.min(nw, ne), Math.min(sw, se));
                case DOWN -> .8888889f;
                case NORTH -> Math.max(nw, ne);
                case SOUTH -> Math.max(sw, se);
                case WEST -> Math.max(nw, sw);
                case EAST -> Math.max(ne, se);
            };
            if (occluded(neighbour, side, faceHeight)) continue;
            mask |= 1 << side.ordinal();
        }
        var flow = fluid.getFlow(view, pos);
        int direction = flow.x == 0 && flow.z == 0
                ? -1
                : Math.floorMod(Math.round((float) ((Math.atan2(flow.z, flow.x) - Math.PI / 2) * 8 / Math.PI)), 16);
        return mask == 0
                ? null
                : new Surface(
                        state.getBlock() == AllBlocks.HONEY ? "honey" : "chocolate", mask, nw, ne, sw, se, direction);
    }

    private static float height(BlockGetter view, Fluid fluid, BlockPos pos) {
        BlockState state = view.getBlockState(pos);
        if (!fluid.isSame(state.getFluidState().getType())) return state.isSolid() ? -1 : 0;
        return fluid.isSame(view.getFluidState(pos.above()).getType())
                ? 1
                : state.getFluidState().getOwnHeight();
    }

    private static float average(BlockGetter view, Fluid fluid, float center, float a, float b, BlockPos diagonal) {
        if (a >= 1 || b >= 1) return 1;
        float sum = 0, weight = 0;
        float diagonalHeight = a > 0 || b > 0 ? height(view, fluid, diagonal) : -1;
        if (diagonalHeight >= 1) return 1;
        for (float h : new float[] {diagonalHeight, center, a, b})
            if (h >= 0) {
                float w = h >= .8f ? 10 : 1;
                sum += h * w;
                weight += w;
            }
        return sum / weight;
    }

    private static boolean occluded(BlockState neighbour, Direction side, float height) {
        VoxelShape shape = neighbour.getFaceOcclusionShape(side.getOpposite());
        if (shape == Shapes.empty()) return false;
        if (shape == Shapes.block()) return side != Direction.UP || height == 1;
        return Shapes.blockOccludes(Shapes.box(0, 0, 0, 1, height, 1), shape, side);
    }

    private record LoadedView(ServerLevel level) implements BlockGetter {
        private @Nullable LevelChunk chunk(BlockPos pos) {
            return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            LevelChunk chunk = chunk(pos);
            return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            LevelChunk chunk = chunk(pos);
            return chunk == null ? null : chunk.getBlockEntities().get(pos);
        }

        @Override
        public int getHeight() {
            return level.getHeight();
        }

        @Override
        public int getMinY() {
            return level.getMinY();
        }
    }

    private static final class FluidHolder extends PolymerLitModelHolder {
        private final ServerLevel level;
        private final BlockPos pos;
        private Surface shown;
        private final List<ItemDisplayElement> displays = new ArrayList<>();
        private int age = 4;

        FluidHolder(ServerLevel level, BlockPos pos) {
            super(level, pos);
            this.level = level;
            this.pos = pos.immutable();
            update();
        }

        @Override
        public void destroy() {
            super.destroy();
        }

        @Override
        public void notifyUpdate(HolderAttachment.UpdateType type) {
            super.notifyUpdate(type);
            if (type == BlockAwareAttachment.BLOCK_STATE_UPDATE) update();
        }

        @Override
        protected void onTick() {
            super.onTick();
            if (++age >= 5) {
                age = 0;
                update();
            }
        }

        private void update() {
            Surface next = surface(level, pos);
            if (Objects.equals(shown, next)) return;
            shown = next;
            List<PolymerFluidMeshes.Patch> patches = next == null ? List.of() : PolymerFluidMeshes.patches(next);
            while (displays.size() > patches.size()) removeElement(displays.removeLast());
            while (displays.size() < patches.size()) {
                ItemDisplayElement display = new ItemDisplayElement();
                display.setItemDisplayContext(ItemDisplayContext.NONE);
                PolymerDecorativeCulling.inBlock(display);
                displays.add(display);
                addElement(display);
            }
            for (int i = 0; i < patches.size(); i++) {
                PolymerFluidMeshes.Patch patch = patches.get(i);
                ItemDisplayElement display = displays.get(i);
                display.setItem(PolymerKineticVisuals.rotatingDisplayItem(
                        PolymerBlockProjections.displayItemModelId(patch.model())));
                PolymerFluidMeshes.apply(display, patch);
            }
        }
    }

    public static void addAssets(ResourcePackBuilder builder) {
        PolymerFluidMeshes.addAssets(builder);
    }
}
