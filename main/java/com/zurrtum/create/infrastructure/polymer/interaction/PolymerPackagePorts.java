package com.zurrtum.create.infrastructure.polymer.interaction;

import com.zurrtum.create.AllHandle;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.zurrtum.create.content.logistics.packagePort.PackagePortTarget.ChainConveyorFrogportTarget;
import com.zurrtum.create.content.logistics.packagePort.frogport.FrogportBlockEntity;
import com.zurrtum.create.infrastructure.config.AllConfigs;
import com.zurrtum.create.infrastructure.packet.c2s.PackagePortPlacementPacket;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.*;
import org.jspecify.annotations.Nullable;

public final class PolymerPackagePorts {
    private PolymerPackagePorts() {}

    private record Selection(
            ServerLevel level,
            ChainConveyorBlockEntity conveyor,
            @Nullable BlockPos connection,
            float position) {}

    private record Placement(ServerPlayer player, FrogportBlockEntity port, Selection selection) {}

    private static final Map<UUID, Selection> SELECTED = new HashMap<>();
    private static final Map<UUID, Placement> PENDING = new HashMap<>();

    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            SELECTED.remove(handler.player.getUUID());
            PENDING.remove(handler.player.getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SELECTED.clear();
            PENDING.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            List<Placement> work = List.copyOf(PENDING.values());
            PENDING.clear();
            for (Placement request : work) place(request);
            SELECTED.entrySet().removeIf(entry -> {
                ServerPlayer player =
                        NativePresentationPolicy.presentationPlayer(server.getPlayerList(), entry.getKey());
                if (player == null || !valid(player, entry.getValue())) return true;
                if (server.getTickCount() % 2 == 0 && player.getMainHandItem().is(AllItems.PACKAGE_FROGPORT))
                    preview(player, entry.getValue());
                return false;
            });
        });
    }

    public static void select(
            ServerPlayer player, ChainConveyorBlockEntity be, @Nullable BlockPos connection, float position) {
        if (!Float.isFinite(position) || player.level() != be.getLevel()) return;
        Selection selection =
                new Selection(player.level(), be, connection == null ? null : connection.immutable(), position);
        if (!valid(player, selection)) return;
        SELECTED.put(player.getUUID(), selection);
        player.sendOverlayMessage(Component.translatable("create.package_port.valid"));
    }

    private static boolean valid(ServerPlayer player, Selection selection) {
        BlockPos pos = selection.conveyor.getBlockPos();
        return player.isAlive()
                && !player.isSpectator()
                && player.level() == selection.level
                && selection.level.hasChunkAt(pos)
                && selection.level.getBlockEntity(pos) == selection.conveyor
                && !selection.conveyor.isRemoved()
                && (selection.connection == null
                        || selection.conveyor.connections.contains(selection.connection)
                                && selection.level.hasChunkAt(pos.offset(selection.connection)));
    }

    private static ChainConveyorFrogportTarget target(Selection selection, BlockPos port) {
        return new ChainConveyorFrogportTarget(
                selection.conveyor.getBlockPos().subtract(port), selection.position, selection.connection, false);
    }

    private static @Nullable String invalid(Vec3 target, BlockPos port) {
        Vec3 diff = target.subtract(Vec3.atBottomCenterOf(port));
        if (diff.y < 0) return "create.package_port.cannot_reach_down";
        if (diff.length() > AllConfigs.server().logistics.packagePortRange.get()) return "create.package_port.too_far";
        return null;
    }

    public static void onPlacementRequest(ServerPlayer player, BlockPos pos) {
        if (!player.connection.server.isSameThread()) {
            player.connection.server.execute(() -> onPlacementRequest(player, pos.immutable()));
            return;
        }
        if (!(player.level().getBlockEntity(pos) instanceof FrogportBlockEntity port)) return;
        Selection selected = SELECTED.remove(player.getUUID());
        if (selected == null) {
            player.sendOverlayMessage(Component.translatable("create.gui.package_port.not_targeting_anything"));
            return;
        }
        PENDING.put(player.getUUID(), new Placement(player, port, selected));
    }

    private static void place(Placement request) {
        ServerPlayer player = request.player;
        FrogportBlockEntity port = request.port;
        Selection selection = request.selection;
        BlockPos pos = port.getBlockPos();
        ServerLevel level = player.level();
        if (!valid(player, selection)
                || !level.hasChunkAt(pos)
                || level.getBlockEntity(pos) != port
                || port.isRemoved()
                || port.target != null
                || !player.mayBuild()
                || !player.isWithinBlockInteractionRange(pos, 1)
                || !player.mayInteract(level, pos)
                || !level.mayInteract(player, pos)) return;
        ChainConveyorFrogportTarget target = target(selection, pos);
        Vec3 point = target.getExactTargetLocation(port, level, pos);
        String error = invalid(point, pos);
        if (error != null) {
            player.sendOverlayMessage(Component.translatable(error));
            return;
        }
        AllHandle.onPackagePortPlacement(player.connection, new PackagePortPlacementPacket(target, pos));
    }

    private static void particle(ServerPlayer player, Vec3 at, int color, float scale) {
        player.level()
                .sendParticles(
                        player, new DustParticleOptions(color, scale), false, false, at.x, at.y, at.z, 1, 0, 0, 0, 0);
    }

    private static void preview(ServerPlayer player, Selection selection) {
        Vec3 point = selection.conveyor.getPackagePosition(selection.position, selection.connection);
        var hit = player.pick(player.blockInteractionRange(), 1, false);
        particle(player, point, 0x9EDE73, .7f);
        if (!(hit instanceof BlockHitResult block) || block.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = block.getBlockPos();
        if (!player.level().getBlockState(pos).canBeReplaced()) pos = pos.relative(block.getDirection());
        String error = invalid(point, pos);
        int color = error == null ? 0x9EDE73 : 0xFF7171;
        player.sendOverlayMessage(Component.translatable(error == null ? "create.package_port.valid" : error));
        if (!player.level().getBlockState(pos).canBeReplaced()) return;
        Vec3 origin = Vec3.atBottomCenterOf(pos);
        double phase = (player.level().getGameTime() / 3d) % 2.5;
        for (int i = 0; i < 4; i++) particle(player, origin.lerp(point, (phase + i * 2.5) / 10), color, .5f);
        for (int i = 0; i < 4; i++)
            particle(
                    player,
                    new Vec3(pos.getX() + .125 + (i % 2) * .75, pos.getY() + .02, pos.getZ() + .125 + (i / 2) * .75),
                    color,
                    .5f);
    }
}
