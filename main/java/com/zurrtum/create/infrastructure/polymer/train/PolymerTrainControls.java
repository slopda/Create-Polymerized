package com.zurrtum.create.infrastructure.polymer.train;

import com.zurrtum.create.content.contraptions.AbstractContraptionEntity;
import com.zurrtum.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.zurrtum.create.content.trains.entity.Carriage;
import com.zurrtum.create.content.trains.entity.CarriageContraptionEntity;
import com.zurrtum.create.infrastructure.player.FakePlayerEntity;
import com.zurrtum.create.infrastructure.polymer.PolymerInventorySync;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerContraptionPassengers;
import com.zurrtum.create.infrastructure.polymer.equipment.ControlFreezeState;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativeControlFreeze;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

public final class PolymerTrainControls {
    private record Driver(CarriageContraptionEntity entity, BlockPos controls) {}

    private static final Map<UUID, Driver> DRIVERS = new ConcurrentHashMap<>();

    public static void register() {
        NativeControlFreeze.register(ControlFreezeState.Owner.TRAIN, PolymerTrainControls::end);
        PolymerContraptionPassengers.register();
        PolymerTrainHud.register();
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            DRIVERS.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(PolymerTrainControls::freezeDrivers);
        ServerTickEvents.START_LEVEL_TICK.register(level -> {
            for (var iterator = DRIVERS.entrySet().iterator(); iterator.hasNext(); ) {
                var entry = iterator.next();
                Driver driver = entry.getValue();
                if (driver.entity.level() != level) continue;
                ServerPlayer player =
                        NativePresentationPolicy.onlinePlayer(level.getServer().getPlayerList(), entry.getKey());
                if (player == null
                        || PolymerNativeClients.isNative(player)
                        || player.level() != level
                        || !player.isAlive()
                        || player.isSpectator()
                        || driver.entity.isRemoved()
                        || !owns(driver.entity, entry.getKey())
                        || !isControls(driver.entity, driver.controls)
                        || driver.entity
                                        .toGlobalVector(Vec3.atCenterOf(driver.controls), 1)
                                        .distanceToSqr(player.position())
                                >= 64) {
                    stop(driver, entry.getKey());
                    iterator.remove();
                    continue;
                }
                if (level.tickRateManager().isEntityFrozen(driver.entity)) continue;
                if (!driver.entity.control(driver.controls, buttons(player.getLastClientInput()), player)) {
                    stop(driver, entry.getKey());
                    iterator.remove();
                }
            }
        });
    }

    private static void freezeDrivers(MinecraftServer server) {
        for (ServerPlayer player : NativePresentationPolicy.onlinePlayers(server.getPlayerList())) {
            Driver driver = DRIVERS.get(player.getUUID());
            boolean frozen = activeDriver(player, driver);
            NativeControlFreeze.set(player, ControlFreezeState.Owner.TRAIN, frozen);
        }
    }

    private static boolean activeDriver(ServerPlayer player, Driver driver) {
        return driver != null
                && !PolymerNativeClients.isNative(player)
                && player.isAlive()
                && !player.isSpectator()
                && player.level() == driver.entity.level()
                && !driver.entity.isRemoved()
                && owns(driver.entity, player.getUUID())
                && isControls(driver.entity, driver.controls)
                && driver.entity
                                .toGlobalVector(Vec3.atCenterOf(driver.controls), 1)
                                .distanceToSqr(player.position())
                        < 64;
    }

    private static void end(ServerPlayer player) {
        Driver driver = DRIVERS.remove(player.getUUID());
        try {
            if (driver != null) stop(driver, player.getUUID());
        } finally {
            NativeControlFreeze.set(player, ControlFreezeState.Owner.TRAIN, false);
        }
    }

    static List<Integer> buttons(Input input) {
        List<Integer> held = new ArrayList<>(6);
        if (input.forward()) held.add(0);
        if (input.backward()) held.add(1);
        if (input.left()) held.add(2);
        if (input.right()) held.add(3);
        if (input.jump()) held.add(4);
        if (input.shift()) held.add(5);
        return held;
    }

    public static ServerPlayer driver(CarriageContraptionEntity entity, BlockPos pos) {
        return DRIVERS.entrySet().stream()
                .filter(entry -> entry.getValue().entity == entity
                        && entry.getValue().controls.equals(pos)
                        && owns(entity, entry.getKey()))
                .map(entry -> NativePresentationPolicy.presentationPlayer(
                        entity.level().getServer().getPlayerList(), entry.getKey()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    static boolean isDriver(UUID player) {
        return NativePresentationPolicy.presentationClient(player) && DRIVERS.containsKey(player);
    }

    static CarriageContraptionEntity drivenEntity(ServerPlayer player) {
        if (!NativePresentationPolicy.allowsPresentation(player)) return null;
        Driver driver = DRIVERS.get(player.getUUID());
        return driver == null || driver.entity.isRemoved() || !owns(driver.entity, player.getUUID())
                ? null
                : driver.entity;
    }

    static Carriage drivenCarriage(ServerPlayer player) {
        CarriageContraptionEntity entity = drivenEntity(player);
        return entity == null ? null : entity.getCarriage();
    }

    static BlockPos drivenControls(ServerPlayer player) {
        if (!NativePresentationPolicy.allowsPresentation(player)) return null;
        Driver driver = DRIVERS.get(player.getUUID());
        return driver == null ? null : driver.controls;
    }

    public static void interacted(AbstractContraptionEntity entity, BlockPos pos, ServerPlayer player) {
        if (PolymerNativeClients.isNative(player)) return;
        PolymerInventorySync.afterMenu(player);
        if (!(entity instanceof CarriageContraptionEntity carriage)
                || player instanceof FakePlayerEntity
                || !isControls(entity, pos)) return;
        if (owns(entity, player.getUUID())) {
            Driver old = DRIVERS.put(player.getUUID(), new Driver(carriage, pos.immutable()));
            if (old != null && old.entity != entity) stop(old, player.getUUID());
            player.sendOverlayMessage(PolymerTrainMessages.started(player, entity));
        } else {
            Driver old = DRIVERS.get(player.getUUID());
            if (old != null && old.entity == entity) DRIVERS.remove(player.getUUID());
        }
    }

    private static boolean isControls(AbstractContraptionEntity entity, BlockPos pos) {
        if (entity.getContraption() == null) return false;
        var block = entity.getContraption().getBlocks().get(pos);
        return block != null && block.state().getBlock() instanceof ControlsBlock;
    }

    private static boolean owns(AbstractContraptionEntity entity, UUID player) {
        return entity.getControllingPlayer().filter(player::equals).isPresent();
    }

    private static void stop(Driver driver, UUID player) {
        if (owns(driver.entity, player)) driver.entity.stopControlling(driver.controls);
    }

    public static void remove(AbstractContraptionEntity entity) {
        DRIVERS.entrySet().removeIf(entry -> {
            if (entry.getValue().entity != entity) return false;
            stop(entry.getValue(), entry.getKey());
            return true;
        });
    }
}
