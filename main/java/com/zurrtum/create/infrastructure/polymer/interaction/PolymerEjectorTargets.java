package com.zurrtum.create.infrastructure.polymer.interaction;

import com.zurrtum.create.AllHandle;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.catnip.math.VecHelper;
import com.zurrtum.create.content.decoration.placard.PlacardBlock;
import com.zurrtum.create.content.decoration.placard.PlacardBlockEntity;
import com.zurrtum.create.content.logistics.depot.EjectorBlock;
import com.zurrtum.create.content.logistics.depot.EjectorBlockEntity;
import com.zurrtum.create.content.logistics.depot.EntityLauncher;
import com.zurrtum.create.infrastructure.config.AllConfigs;
import com.zurrtum.create.infrastructure.packet.c2s.EjectorPlacementPacket;
import com.zurrtum.create.infrastructure.polymer.AdapterInteractions;
import com.zurrtum.create.infrastructure.polymer.PolymerReach;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public final class PolymerEjectorTargets {
    private static final int TARGET = 0xFFCB74;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private static final class Session {
        final UUID owner;

        @Nullable
        ItemStack currentItem;

        @Nullable
        BlockPos selection;

        PolymerArmTargets.@Nullable Outline outline;

        @Nullable
        BlockPos outlined;

        Session(UUID owner) {
            this.owner = owner;
        }

        void clearOutline() {
            if (outline != null) outline.destroy();
            outline = null;
            outlined = null;
        }
    }

    private PolymerEjectorTargets() {}

    public static void register() {
        AdapterInteractions.useBlock((player, level, hand, hit) -> {
            if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel)) return InteractionResult.PASS;
            Session session = SESSIONS.get(sp.getUUID());
            if (session == null
                    || session.currentItem == null
                    || session.currentItem != sp.getMainHandItem()
                    || sp.isSpectator()
                    || !sp.isShiftKeyDown()) return InteractionResult.PASS;
            sp.sendOverlayMessage(
                    Component.translatable("create.weighted_ejector.target_set").withStyle(ChatFormatting.GOLD));
            session.selection = hit.getBlockPos().immutable();
            return InteractionResult.SUCCESS;
        });
        AdapterInteractions.attackBlock((player, level, hand, pos, direction) -> {
            if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel serverLevel))
                return InteractionResult.PASS;
            if (placard(sp, serverLevel, pos)) return InteractionResult.SUCCESS;
            Session session = SESSIONS.get(sp.getUUID());
            if (session == null
                    || session.currentItem == null
                    || !sp.isShiftKeyDown()
                    || !pos.equals(session.selection)) return InteractionResult.PASS;
            session.selection = null;
            return InteractionResult.SUCCESS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : NativePresentationPolicy.presentationPlayers(server.getPlayerList())) {
                standOnEjector(player);
                tick(player);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Session session = SESSIONS.remove(handler.player.getUUID());
            if (session != null) session.clearOutline();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SESSIONS.values().forEach(Session::clearOutline);
            SESSIONS.clear();
        });
    }

    private static boolean placard(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PlacardBlock block) || player.isSpectator() || !player.mayBuild())
            return false;
        if (!(level.getBlockEntity(pos) instanceof PlacardBlockEntity placard)
                || placard.getHeldItem().isEmpty()) return false;
        block.attack(state, level, pos, player);
        return true;
    }

    public static void onEjectorPlaced(ServerPlayer player, BlockPos pos) {
        Session session = SESSIONS.get(player.getUUID());
        BlockPos selection = session == null ? null : session.selection;
        Direction direction = validDirection(pos, selection);
        if (direction == null || selection == null) {
            player.sendOverlayMessage(Component.translatable(
                            selection == null
                                    ? "create.weighted_ejector.no_target"
                                    : "create.weighted_ejector.target_not_valid")
                    .withStyle(ChatFormatting.WHITE));
        } else {
            player.sendOverlayMessage(Component.translatable(
                            "create.weighted_ejector.targeting", selection.getX(), selection.getY(), selection.getZ())
                    .withStyle(ChatFormatting.GREEN));
            BlockPos diff = pos.subtract(selection);
            int h = Math.abs(diff.getX() + diff.getZ());
            int v = -diff.getY();
            AllHandle.onEjectorPlacement(player.connection, new EjectorPlacementPacket(h, v, pos, direction));
        }
        if (session != null) {
            session.selection = null;
            session.currentItem = null;
        }
    }

    private static @Nullable Direction validDirection(BlockPos pos, @Nullable BlockPos selection) {
        if (selection == null || VecHelper.onSameAxis(pos, selection, Axis.Y)) return null;
        int xDiff = selection.getX() - pos.getX(), zDiff = selection.getZ() - pos.getZ();
        int max = AllConfigs.server().kinetics.maxEjectorDistance.get();
        if (Math.abs(xDiff) > max || Math.abs(zDiff) > max) return null;
        if (xDiff == 0) return Direction.get(zDiff < 0 ? AxisDirection.NEGATIVE : AxisDirection.POSITIVE, Axis.Z);
        if (zDiff == 0) return Direction.get(xDiff < 0 ? AxisDirection.NEGATIVE : AxisDirection.POSITIVE, Axis.X);
        return null;
    }

    private static void standOnEjector(ServerPlayer player) {
        if (!player.onGround() || player.isSpectator() || player.isPassenger() || !player.isAlive()) return;
        ServerLevel level = player.level();
        BlockPos pos = player.blockPosition();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof EjectorBlock block)) return;
        if (!(level.getBlockEntity(pos) instanceof EjectorBlockEntity be)
                || be.getState() != EjectorBlockEntity.State.CHARGED) return;
        Vec3 before = player.position();
        block.onEntityMovement(level, player);
        Vec3 after = player.position();
        if (!after.equals(before))
            player.teleportTo(level, after.x, after.y, after.z, Set.of(), player.getYRot(), player.getXRot(), false);
    }

    private static void tick(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        Session session = SESSIONS.get(player.getUUID());
        boolean ejector = held.is(AllItems.WEIGHTED_EJECTOR), wrench = held.is(AllItems.WRENCH);
        if (!ejector && !wrench && session == null) return;
        if (session == null) {
            session = new Session(player.getUUID());
            SESSIONS.put(player.getUUID(), session);
        }
        if (!ejector) session.currentItem = null;
        else if (held != session.currentItem) {
            session.selection = null;
            session.currentItem = held;
        }
        BlockHitResult block = PolymerReach.targetBlock(player);
        if (wrench) {
            if (block != null
                    && player.level().getBlockEntity(block.getBlockPos()) instanceof EjectorBlockEntity be
                    && !be.getTargetPosition().equals(be.getBlockPos())) session.selection = be.getTargetPosition();
            else session.selection = null;
        }
        BlockPos shown = ejector || wrench ? session.selection : null;
        outline(player, session, shown);
        if (shown != null && block != null && player.level().getGameTime() % 2 == 0) arc(player, shown, block, wrench);
        if (!ejector && !wrench && session.outline == null) SESSIONS.remove(player.getUUID());
    }

    private static void outline(ServerPlayer player, Session session, @Nullable BlockPos pos) {
        if (pos == null || !player.level().isLoaded(pos)) {
            session.clearOutline();
            return;
        }
        ServerLevel level = player.level();
        VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        AABB bounds = shape.isEmpty() ? new AABB(BlockPos.ZERO) : shape.bounds();
        if (!pos.equals(session.outlined)) {
            session.clearOutline();
            session.outline = new PolymerArmTargets.Outline(session.owner, level, pos, bounds, TARGET);
            session.outlined = pos;
        } else if (session.outline != null) {
            session.outline.update(bounds, TARGET);
        }
    }

    private static void arc(ServerPlayer player, BlockPos selection, BlockHitResult hit, boolean wrench) {
        BlockPos pos = wrench ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection());
        int xDiff = selection.getX() - pos.getX(),
                yDiff = selection.getY() - pos.getY(),
                zDiff = selection.getZ() - pos.getZ();
        int validX = Math.abs(zDiff) > Math.abs(xDiff) ? 0 : xDiff;
        int validZ = Math.abs(zDiff) < Math.abs(xDiff) ? 0 : zDiff;
        BlockPos validPos = selection.offset(validX, yDiff, validZ);
        Direction d = validDirection(validPos, selection);
        if (d == null) return;
        EntityLauncher launcher = new EntityLauncher(Math.abs(validX + validZ), yDiff);
        double total = launcher.getTotalFlyingTicks() + 3;
        int segments = (int) total / 3 + 1;
        double offset = total / segments;
        boolean valid = xDiff == validX && zDiff == validZ;
        DustParticleOptions dust = new DustParticleOptions(valid ? 0x9EDE73 : 0xFF7171, 1);
        double time = player.level().getGameTime() / 3.0;
        for (int i = 0; i < segments; i++) {
            Vec3 at = launcher.getGlobalPos(time % offset + i * offset, d, pos).add(xDiff - validX, 0, zDiff - validZ);
            player.level().sendParticles(player, dust, false, false, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }
}
