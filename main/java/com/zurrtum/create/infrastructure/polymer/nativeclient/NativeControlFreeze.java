package com.zurrtum.create.infrastructure.polymer.nativeclient;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zurrtum.create.Create;
import com.zurrtum.create.infrastructure.polymer.equipment.ControlFreezeState;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class NativeControlFreeze {
    record SavedControl(float walking, float flying, Optional<Double> movementBase) {
        ControlFreezeState.Speeds speeds() {
            return new ControlFreezeState.Speeds(walking, flying);
        }
    }

    static final Codec<SavedControl> SAVED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.FLOAT.fieldOf("walking").forGetter(SavedControl::walking),
                    Codec.FLOAT.fieldOf("flying").forGetter(SavedControl::flying),
                    Codec.DOUBLE.optionalFieldOf("movement_base").forGetter(SavedControl::movementBase))
            .apply(instance, SavedControl::new));

    private static final AttachmentType<SavedControl> SAVED = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath("createpoly", "control_speeds"),
            builder -> builder.persistent(SAVED_CODEC).copyOnDeath());
    private static final Identifier MODIFIER = Identifier.fromNamespaceAndPath("createpoly", "controls_active");
    private static final Map<UUID, ControlFreezeState> ACTIVE = new HashMap<>();
    private static final Map<ControlFreezeState.Owner, Consumer<ServerPlayer>> CLEANUPS =
            new EnumMap<>(ControlFreezeState.Owner.class);
    private static boolean registered;
    private static final ThreadLocal<ServerPlayer> LOADED_RECOVERY = new ThreadLocal<>();

    private NativeControlFreeze() {}

    public static void register(ControlFreezeState.Owner owner, Consumer<ServerPlayer> cleanup) {
        if (CLEANUPS.putIfAbsent(owner, cleanup) != null)
            throw new IllegalStateException("Duplicate control lifecycle owner: " + owner);
        if (registered) return;
        registered = true;
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> finishLoaded(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> finish(handler.player));
        ServerPlayerEvents.LEAVE.register(NativeControlFreeze::finish);
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            finish(oldPlayer);
            finishLoaded(newPlayer);
        });
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(
                (player, origin, destination) -> finish(player));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damage) -> {
            if (entity instanceof ServerPlayer player) finish(player);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (ServerPlayer player : NativePresentationPolicy.onlinePlayers(server.getPlayerList())) finish(player);
        });
    }

    private static void finishLoaded(ServerPlayer player) {
        ServerPlayer previous = LOADED_RECOVERY.get();
        LOADED_RECOVERY.set(player);
        try {
            finish(player);
        } finally {
            if (previous == null) LOADED_RECOVERY.remove();
            else LOADED_RECOVERY.set(previous);
        }
    }

    private static void finish(ServerPlayer player) {
        try {
            end(player);
        } catch (RuntimeException error) {
            Create.LOGGER.error("Control cleanup failed for {}; recovery will be retried", player.getUUID(), error);
        }
    }

    public static void set(ServerPlayer player, ControlFreezeState.Owner owner, boolean held) {
        if (held && PolymerNativeClients.isNative(player)) return;
        ControlFreezeState state = ACTIVE.get(player.getUUID());
        if (held) {
            if (state == null) {
                state = new ControlFreezeState();
                ACTIVE.put(player.getUUID(), state);
            }
            state.acquire(owner, new PlayerState(player));
        } else if (state != null) {
            var bridge = new PlayerState(player);
            state.release(owner, bridge);
            if (state.empty()) {
                ControlFreezeState.recover(bridge);
                ACTIVE.remove(player.getUUID());
            }
        }
    }

    private static void end(ServerPlayer player) {
        RuntimeException failure = null;
        for (Consumer<ServerPlayer> cleanup : CLEANUPS.values()) {
            try {
                cleanup.accept(player);
            } catch (RuntimeException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        try {
            ControlFreezeState.recover(new PlayerState(player));
            ACTIVE.remove(player.getUUID());
        } catch (RuntimeException error) {
            if (failure == null) failure = error;
            else failure.addSuppressed(error);
        }
        if (failure != null) throw failure;
    }

    private record PlayerState(ServerPlayer player) implements ControlFreezeState.PlayerState {
        private AttachmentTarget attachments() {
            return (AttachmentTarget) player;
        }

        @Override
        public ControlFreezeState.Speeds speeds() {
            var abilities = player.getAbilities();
            return new ControlFreezeState.Speeds(abilities.getWalkingSpeed(), abilities.getFlyingSpeed());
        }

        @Override
        public void speeds(ControlFreezeState.Speeds speeds) {
            SavedControl saved = attachments().getAttached(SAVED);
            player.getAbilities().setWalkingSpeed(speeds.walking());
            player.getAbilities().setFlyingSpeed(speeds.flying());
            var movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (LOADED_RECOVERY.get() == player
                    && saved != null
                    && speeds.walking() == saved.walking()
                    && movement != null
                    && movement.getBaseValue() == 0)
                movement.setBaseValue(saved.movementBase().orElse((double) saved.walking()));
        }

        @Override
        public Optional<ControlFreezeState.Speeds> saved() {
            return Optional.ofNullable(attachments().getAttached(SAVED)).map(SavedControl::speeds);
        }

        @Override
        public void save(ControlFreezeState.Speeds speeds) {
            var movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
            attachments()
                    .setAttached(
                            SAVED,
                            new SavedControl(
                                    speeds.walking(),
                                    speeds.flying(),
                                    movement == null ? Optional.empty() : Optional.of(movement.getBaseValue())));
        }

        @Override
        public void clearSaved() {
            attachments().removeAttached(SAVED);
        }

        @Override
        public void modifiers(boolean held) {
            for (var attribute : List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH)) {
                var instance = player.getAttribute(attribute);
                if (instance == null) continue;
                instance.removeModifier(MODIFIER);
                if (held)
                    instance.addTransientModifier(
                            new AttributeModifier(MODIFIER, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }

        @Override
        public void sync() {
            NativePresentationPolicy.syncControlAbilities(player);
        }
    }
}
