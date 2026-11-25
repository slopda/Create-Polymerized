package com.zurrtum.create.infrastructure.polymer.collision;

import com.zurrtum.create.infrastructure.polymer.PolymerReach;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

public final class PolymerProxyMining {
    private record Target(ServerLevel level, BlockPos pos, BlockState source, long deadline, double speed) {}

    private static final Map<UUID, Target> TARGETS = new HashMap<>();

    private PolymerProxyMining() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> TARGETS.entrySet().removeIf(entry -> {
            Target target = entry.getValue();
            ServerPlayer player = NativePresentationPolicy.onlinePlayer(server.getPlayerList(), entry.getKey());
            if (player == null) return true;
            if (!NativePresentationPolicy.allowsPresentation(player)) {
                restoreSpeed(player);
                return true;
            }
            if (player.level() != target.level
                    || !player.isAlive()
                    || player.isSpectator()
                    || target.level.getGameTime() > target.deadline
                    || hit(player, target.level, target.pos) == null
                    || target.level.getBlockState(target.pos) != target.source
                    || supportsPlayer(player, target)) {
                if (player.level() == target.level && target.level.hasChunkAt(target.pos))
                    NativePresentationPolicy.sendPresentation(
                            player,
                            new ClientboundBlockUpdatePacket(target.pos, target.level.getBlockState(target.pos)));
                restoreSpeed(player);
                return true;
            }
            double speed = speed(player, target.level, target.pos, target.source);
            if (Math.abs(speed - target.speed) > 1e-8) {
                sendSpeed(player, speed);
                entry.setValue(new Target(target.level, target.pos, target.source, target.deadline, speed));
            }
            return false;
        }));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            TARGETS.keySet().forEach(id -> {
                var player = NativePresentationPolicy.presentationPlayer(server.getPlayerList(), id);
                if (player != null) restoreSpeed(player);
            });
            TARGETS.clear();
        });
    }

    public static @Nullable BlockHitResult hit(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player.level() != level
                || !player.isAlive()
                || player.isSpectator()
                || !level.hasChunkAt(pos)
                || !level.mayInteract(player, pos)
                || !player.mayInteract(level, pos)) return null;
        BlockHitResult hit = PolymerReach.targetBlock(player);
        return hit != null && hit.getBlockPos().equals(pos) ? hit : null;
    }

    public static boolean attack(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (PolymerStaticCollision.hit(player, level, pos) == null) return false;
        if (player.isCreative()) return player.gameMode.destroyBlock(pos);
        if (player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) return false;
        BlockState source = level.getBlockState(pos);
        Target target =
                new Target(level, pos.immutable(), source, level.getGameTime() + 8, speed(player, level, pos, source));
        if (supportsPlayer(player, target)) return false;
        Target previous = TARGETS.get(player.getUUID());
        if (previous != null
                && (!previous.pos.equals(pos) || previous.level != level)
                && player.level() == previous.level
                && previous.level.hasChunkAt(previous.pos))
            NativePresentationPolicy.sendPresentation(
                    player, new ClientboundBlockUpdatePacket(previous.pos, previous.level.getBlockState(previous.pos)));
        sendSpeed(player, target.speed);
        TARGETS.put(player.getUUID(), target);
        boolean wet = !target.source.getFluidState().isEmpty();
        NativePresentationPolicy.sendPresentation(
                player,
                new ClientboundBlockUpdatePacket(
                        pos, Blocks.SCULK_VEIN.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, wet)));
        return true;
    }

    private static boolean supportsPlayer(ServerPlayer player, Target target) {
        var bounds = player.getBoundingBox().inflate(.02, .06, .02);
        for (var box : target.source
                .getCollisionShape(target.level, target.pos, CollisionContext.of(player))
                .toAabbs()) if (box.move(target.pos).intersects(bounds)) return true;
        return false;
    }

    static boolean hidden(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Target target = TARGETS.get(player.getUUID());
        return target != null && target.level == level && target.pos.equals(pos);
    }

    public static void action(ServerPlayer player, BlockPos pos, ServerboundPlayerActionPacket.Action action) {
        Target target = TARGETS.get(player.getUUID());
        if (target == null || player.level() != target.level || !target.pos.equals(pos)) return;
        if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK)
            TARGETS.put(
                    player.getUUID(),
                    new Target(target.level, target.pos, target.source, target.level.getGameTime() + 8, target.speed));
        else if (action == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK) {
            TARGETS.remove(player.getUUID());
            restoreSpeed(player);
            if (target.level.hasChunkAt(pos))
                NativePresentationPolicy.sendPresentation(
                        player, new ClientboundBlockUpdatePacket(pos, target.level.getBlockState(pos)));
        }
    }

    public static void progress(ServerPlayer player, BlockPos pos) {
        Target target = TARGETS.get(player.getUUID());
        if (target != null && player.level() == target.level && target.pos.equals(pos))
            TARGETS.put(
                    player.getUUID(),
                    new Target(target.level, target.pos, target.source, target.level.getGameTime() + 8, target.speed));
    }

    static double speed(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState source) {
        double sourceProgress = source.getDestroyProgress(player, level, pos);
        double nativeProgress = Blocks.SCULK_VEIN.defaultBlockState().getDestroyProgress(player, level, pos);
        double real = player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED);
        if (sourceProgress <= 0 || nativeProgress <= 0) return 0;
        return Math.min(1024, Math.max(0, real * sourceProgress / nativeProgress));
    }

    private static void sendSpeed(ServerPlayer player, double value) {
        AttributeInstance projected = new AttributeInstance(Attributes.BLOCK_BREAK_SPEED, ignored -> {});
        projected.setBaseValue(value);
        NativePresentationPolicy.sendPresentation(
                player, new ClientboundUpdateAttributesPacket(player.getId(), List.of(projected)));
    }

    private static void restoreSpeed(ServerPlayer player) {
        var actual = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
        if (actual != null)
            NativePresentationPolicy.sendPresentation(
                    player, new ClientboundUpdateAttributesPacket(player.getId(), List.of(actual)));
    }
}
