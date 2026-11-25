package com.zurrtum.create.infrastructure.polymer.collision;

import com.zurrtum.create.AllShapes;
import com.zurrtum.create.content.contraptions.actors.seat.SeatBlock;
import com.zurrtum.create.content.contraptions.mounted.CartAssemblerBlock;
import com.zurrtum.create.content.decoration.bracket.BracketBlock;
import dev.veeda.createpoly.mixin.BlockResourceCreatorAccessor;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.impl.DefaultModelData;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public final class PolymerBlockCollision {
    private record Candidate(BlockModelType type, VoxelShape shape, boolean wet, boolean occludes) {}

    private static final Map<BlockState, Optional<BlockModelType>> TYPES = new IdentityHashMap<>();
    private static final Map<BlockModelType, BlockState> EMPTY = new EnumMap<>(BlockModelType.class);
    private static List<Candidate> candidates;
    private static final BlockState[] THIN = new BlockState[2];

    private PolymerBlockCollision() {}

    public static void reserveCarriers() {
        var creator = BlockResourceCreatorAccessor.polymerpatch$getSharedCreator();
        var thinModel = PolymerBlockModel.of(PolymerCrackModels.model(Shapes.box(0, 0, 0, 1, 3 / 16.0, 1)));
        THIN[0] = creator.requestBlock(BlockModelType.TRAPDOOR_BOTTOM, s -> s.is(Blocks.IRON_TRAPDOOR), thinModel);
        THIN[1] = creator.requestBlock(
                BlockModelType.TRAPDOOR_BOTTOM_WATERLOGGED, s -> s.is(Blocks.IRON_TRAPDOOR), thinModel);
        for (var block : BuiltInRegistries.BLOCK) {
            var id = BuiltInRegistries.BLOCK.getKey(block);
            if (id == null || !id.getNamespace().equals("create")) continue;
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                BlockModelType type = typeFor(state);
                if (type != null && !EMPTY.containsKey(type)) {
                    BlockState carrier = PolymerCrackModels.request(type, shape(state));
                    if (carrier != null) EMPTY.put(type, carrier);
                }
            }
        }
    }

    public static boolean sameShape(VoxelShape first, VoxelShape second) {
        if (!Shapes.joinIsNotEmpty(first, second, BooleanOp.NOT_SAME)) return true;
        if (first.isEmpty() || second.isEmpty()) return false;
        AABB a = first.bounds(), b = second.bounds();
        if (Math.abs(a.minX - b.minX) > 1e-9
                || Math.abs(a.minY - b.minY) > 1e-9
                || Math.abs(a.minZ - b.minZ) > 1e-9
                || Math.abs(a.maxX - b.maxX) > 1e-9
                || Math.abs(a.maxY - b.maxY) > 1e-9
                || Math.abs(a.maxZ - b.maxZ) > 1e-9) return false;
        return !Shapes.joinIsNotEmpty(normalize(first), normalize(second), BooleanOp.NOT_SAME);
    }

    private static VoxelShape normalize(VoxelShape shape) {
        VoxelShape result = Shapes.empty();
        for (AABB box : shape.toAabbs())
            result = Shapes.or(
                    result,
                    Shapes.create(new AABB(
                            clean(box.minX),
                            clean(box.minY),
                            clean(box.minZ),
                            clean(box.maxX),
                            clean(box.maxY),
                            clean(box.maxZ))));
        return result;
    }

    private static double clean(double coordinate) {
        return Math.rint(coordinate * 1e9) / 1e9;
    }

    public static VoxelShape shape(BlockState state) {
        if (state.getBlock() instanceof SeatBlock) return AllShapes.SEAT_COLLISION_PLAYERS;
        if (state.getBlock() instanceof CartAssemblerBlock)
            return AllShapes.CART_ASSEMBLER_PLAYER_COLLISION.get(
                    state.getValue(CartAssemblerBlock.RAIL_SHAPE) == RailShape.NORTH_SOUTH
                            ? Direction.Axis.Z
                            : Direction.Axis.X);
        return state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.positionContext(0));
    }

    public static @Nullable BlockModelType typeFor(BlockState state) {
        if (state.getBlock() instanceof BracketBlock) return BlockModelType.LEAVES;
        return TYPES.computeIfAbsent(state, PolymerBlockCollision::match).orElse(null);
    }

    private static Optional<BlockModelType> match(BlockState state) {
        VoxelShape source = shape(state);
        boolean wet = !state.getFluidState().isEmpty();
        if (sameShape(source, Shapes.block())) {
            if (state.isSolidRender() && !wet) return Optional.of(BlockModelType.FULL_BLOCK);
        }
        for (Candidate candidate : candidates()) {
            if (candidate.wet != wet || candidate.occludes && !state.canOcclude()) continue;
            if (sameShape(source, candidate.shape)) return Optional.of(candidate.type);
        }
        return Optional.empty();
    }

    public static @Nullable BlockState thinCarrier(BlockState source) {
        VoxelShape shape = shape(source);
        if (shape.isEmpty()) return null;
        AABB b = shape.bounds();
        if (b.minX > 1e-9 || b.minY > 1e-9 || b.minZ > 1e-9) return null;
        if (b.maxX < 1 - 1e-9 || b.maxZ < 1 - 1e-9 || b.maxY > 3 / 16.0 + 1e-9) return null;
        return THIN[source.getFluidState().isEmpty() ? 0 : 1];
    }

    public static BlockState emptyCarrier(BlockState source, BlockState fallback) {
        BlockState special = PolymerStaticCollision.carrier(source);
        if (special != null) return special;
        if (sameShape(shape(source), Shapes.block()))
            return Blocks.BARRIER
                    .defaultBlockState()
                    .setValue(
                            BlockStateProperties.WATERLOGGED,
                            !source.getFluidState().isEmpty());
        BlockState wall = PolymerWallCollision.carrier(source);
        if (wall != null) return wall;
        BlockState thin = thinCarrier(source);
        if (thin != null) return thin;
        BlockModelType type = typeFor(source);
        if (type == null) return fallback;
        // Allocation must precede pack generation, never happen on a packet thread.
        BlockState state = EMPTY.get(type);
        return state == null ? fallback : state;
    }

    private static List<Candidate> candidates() {
        if (candidates != null) return candidates;
        var result = new ArrayList<Candidate>();
        for (BlockModelType type : BlockModelType.values()) {
            String name = type.name();
            if (name.contains("FIRE")
                    || name.contains("CACTUS")
                    || name.contains("VINES")
                    || name.contains("SCAFFOLDING")
                    || name.contains("BED_")
                    || name.equals("HEAD")
                    || name.contains("SCULK_SENSOR_ACTIVE")) continue;
            List<BlockState> pool = DefaultModelData.USABLE_STATES.get(type);
            if (pool == null || pool.isEmpty()) continue;
            BlockState first = pool.getFirst();
            VoxelShape shape = shape(first);
            boolean wet = !first.getFluidState().isEmpty();
            boolean occludes = first.canOcclude();
            if (pool.stream()
                    .anyMatch(state -> wet != !state.getFluidState().isEmpty()
                            || occludes != state.canOcclude()
                            || !sameShape(shape, shape(state)))) continue;
            result.add(new Candidate(type, shape, wet, occludes));
        }
        candidates = List.copyOf(result);
        return candidates;
    }
}
