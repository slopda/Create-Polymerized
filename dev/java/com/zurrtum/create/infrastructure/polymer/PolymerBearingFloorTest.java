package com.zurrtum.create.infrastructure.polymer;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.content.contraptions.AbstractContraptionEntity;
import com.zurrtum.create.content.contraptions.bearing.BearingBlock;
import com.zurrtum.create.content.contraptions.bearing.BearingContraption;
import com.zurrtum.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.zurrtum.create.content.contraptions.glue.SuperGlueEntity;
import com.zurrtum.create.content.kinetics.motor.CreativeMotorBlock;
import com.zurrtum.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerContraptionFloors;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerContraptionVisuals;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class PolymerBearingFloorTest implements ModInitializer {
    private static AbstractContraptionEntity focus;
    private static final Map<UUID, ReturnPoint> RETURNS = new HashMap<>();

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> stopping());
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof AbstractContraptionEntity contraption) remove(contraption);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(Commands.literal("createfly")
                        .then(Commands.literal("floor")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.literal("demo")
                                        .executes(context ->
                                                demo(context.getSource().getPlayerOrException()))
                                        .then(Commands.literal("plain")
                                                .executes(context ->
                                                        demo(context.getSource().getPlayerOrException(), true))))
                                .then(Commands.literal("board")
                                        .executes(context ->
                                                board(context.getSource().getPlayerOrException())))
                                .then(Commands.literal("back")
                                        .executes(context ->
                                                back(context.getSource().getPlayerOrException())))
                                .then(Commands.literal("enable").executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    AbstractContraptionEntity nearest = nearestBearing(player);
                                    if (nearest == null
                                            || PolymerContraptionFloors.plan(nearest)
                                                    .isEmpty()) {
                                        player.sendSystemMessage(
                                                Component.literal(
                                                        "Stand within 8 blocks of a horizontal bearing with an exposed full-block floor."));
                                        return 0;
                                    }
                                    enable(nearest);
                                    player.sendSystemMessage(
                                            Component.literal(
                                                    "Native floor enabled on this bearing: original test10 corrected impulse. RPM unchanged. /createfly floor disable to restore."));
                                    return 1;
                                }))
                                .then(Commands.literal("disable").executes(context -> {
                                    clear();
                                    context.getSource()
                                            .sendSuccess(
                                                    () -> Component.literal("Native floor comparison disabled."),
                                                    false);
                                    return 1;
                                }))
                                .then(Commands.literal("status").executes(context -> {
                                    context.getSource()
                                            .sendSuccess(
                                                    () -> Component.literal(
                                                            focus == null
                                                                    ? "Native floors activate automatically on supported bearings and minecarts."
                                                                    : "Focused native floor: " + focus.getUUID()
                                                                            + "; cells="
                                                                            + (PolymerContraptionFloors.floor(focus)
                                                                                            == null
                                                                                    ? 0
                                                                                    : PolymerContraptionFloors.floor(
                                                                                                    focus)
                                                                                            .cells
                                                                                            .size())),
                                                    false);
                                    return 1;
                                })))));
    }

    private static AbstractContraptionEntity nearestBearing(ServerPlayer player) {
        return player
                .level()
                .getEntitiesOfClass(
                        AbstractContraptionEntity.class, player.getBoundingBox().inflate(8))
                .stream()
                .filter(entity -> !entity.isRemoved()
                        && entity.getContraption() instanceof BearingContraption
                        && entity.position().distanceToSqr(player.position()) <= 64)
                .min(Comparator.comparingDouble(entity -> entity.position().distanceToSqr(player.position())))
                .orElse(null);
    }

    private record ReturnPoint(ServerLevel world, Vec3 position, float yaw, float pitch) {}

    private static int demo(ServerPlayer player) {
        return demo(player, false);
    }

    private static int demo(ServerPlayer player, boolean plain) {
        if (focus != null && !focus.isRemoved()) {
            player.sendSystemMessage(Component.literal(
                    "A comparison is already active. Use /createfly floor board, or disable it first."));
            return 0;
        }
        ServerLevel world = (ServerLevel) player.level();
        BlockPos base = player.blockPosition().offset(6, 4, 0);
        if (base.getY() < world.getMinY() || base.getY() + 5 >= world.getMaxY()) return 0;
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, 0, -2), base.offset(2, 4, 2))) {
            if (!world.getWorldBorder().isWithinBounds(pos)
                    || !world.getBlockState(pos).isAir()) {
                player.sendSystemMessage(
                        Component.literal(
                                "Demo needs empty space six blocks east and four blocks above you. Move to an open area and retry."));
                return 0;
            }
        }
        List<BlockPos> placed = new ArrayList<>();
        MechanicalBearingBlockEntity bearing = null;
        SuperGlueEntity glue = null;
        try {
            placed.add(base);
            world.setBlock(
                    base,
                    AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.UP),
                    3);
            placed.add(base.above());
            world.setBlock(
                    base.above(),
                    AllBlocks.MECHANICAL_BEARING.defaultBlockState().setValue(BearingBlock.FACING, Direction.UP),
                    3);
            for (int x = -1; x <= 1; x++)
                for (int z = -1; z <= 1; z++) {
                    BlockPos pos = base.offset(x, 2, z);
                    placed.add(pos);
                    world.setBlock(pos, (plain ? Blocks.STONE : Blocks.SLIME_BLOCK).defaultBlockState(), 3);
                }
            if (plain) {
                glue = new SuperGlueEntity(world, SuperGlueEntity.span(base.offset(-1, 2, -1), base.offset(1, 2, 1)));
                if (!world.addFreshEntity(glue)) throw new IllegalStateException("Cannot create demo glue");
            }
            CreativeMotorBlockEntity motor = (CreativeMotorBlockEntity) world.getBlockEntity(base);
            motor.generatedSpeed.setValue(3);
            motor.initialize();
            bearing = (MechanicalBearingBlockEntity) world.getBlockEntity(base.above());
            bearing.setSpeed(motor.getGeneratedSpeed());
            bearing.assemble();
            if (bearing.getMovedContraption() == null || !enable(bearing.getMovedContraption()))
                throw new IllegalStateException("Demo bearing assembly failed");
            PolymerContraptionVisuals.tick(bearing.getMovedContraption());
            player.sendSystemMessage(
                    Component.literal(
                            "3 RPM " + (plain ? "glued stone" : "slime")
                                    + " demo ready. /createfly floor board to stand on it; /createfly floor back to return. Ordinary demo blocks remain for testing."));
            return 1;
        } catch (RuntimeException failure) {
            if (bearing != null) bearing.disassemble();
            if (glue != null) glue.discard();
            for (BlockPos pos : placed) world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            player.sendSystemMessage(
                    Component.literal("Could not assemble the demo; its temporary placement was removed."));
            return 0;
        }
    }

    private static int board(ServerPlayer player) {
        var floor = focus == null ? null : PolymerContraptionFloors.floor(focus);
        if (floor == null || floor.cells.isEmpty() || floor.owner.isRemoved()) return 0;
        RETURNS.putIfAbsent(
                player.getUUID(),
                new ReturnPoint((ServerLevel) player.level(), player.position(), player.getYRot(), player.getXRot()));
        BlockPos local = floor.positions.stream()
                .min(Comparator.comparingDouble(pos -> Vec3.atLowerCornerOf(pos).lengthSqr()))
                .orElseThrow();
        Vec3 top = floor.owner.toGlobalVector(new Vec3(local.getX() + .5, local.getY() + 1, local.getZ() + .5), 1);
        if (!(floor.owner.level() instanceof ServerLevel level)) return 0;
        player.teleportTo(level, top.x, top.y, top.z, Set.of(), player.getYRot(), player.getXRot(), false);
        player.sendSystemMessage(Component.literal(
                "Turn off creative flight to test standing/walking. /createfly floor back returns you."));
        return 1;
    }

    private static int back(ServerPlayer player) {
        ReturnPoint point = RETURNS.remove(player.getUUID());
        if (point == null) return 0;
        player.teleportTo(
                point.world,
                point.position.x,
                point.position.y,
                point.position.z,
                Set.of(),
                point.yaw,
                point.pitch,
                false);
        return 1;
    }

    private static void stopping() {
        focus = null;
        RETURNS.clear();
    }

    private static boolean enable(AbstractContraptionEntity entity) {
        if (!PolymerContraptionFloors.enable(entity)) return false;
        focus = entity;
        return true;
    }

    private static void remove(AbstractContraptionEntity entity) {
        if (focus == entity) focus = null;
    }

    private static void clear() {
        if (focus != null) PolymerContraptionFloors.disable(focus);
        focus = null;
    }
}
