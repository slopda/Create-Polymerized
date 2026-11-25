package com.zurrtum.create.infrastructure.polymer.nativeclient;

import com.mojang.authlib.GameProfile;
import com.zurrtum.create.Create;
import eu.pb4.polymer.core.api.block.BlockMapper;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.world.level.block.state.BlockState;

public final class PolymerNativeClients {
    private PolymerNativeClients() {}

    public static boolean ENABLED = true;
    private static final Set<UUID> NATIVE = ConcurrentHashMap.newKeySet();

    public static void register() {
        PolymerNativeRegistryIds.register();
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> NATIVE.remove(handler.player.getUUID()));
        BlockMapper.DEFAULT_MAPPER_EVENT.register(
                (context, mapper) -> isNative(context) ? nativeMapper(mapper) : mapper);
    }

    public static void onKnownPacks(GameProfile profile, List<KnownPack> packs) {
        if (!ENABLED || profile == null) return;
        boolean create = packs.stream()
                .anyMatch(pack -> pack.id().equals(Create.MOD_ID)
                        && pack.namespace().equals("vanilla")
                        && pack.version().equals(PolymerNativeVersionGate.requiredVersion()));
        Create.LOGGER.info("Known packs from {} ({}): {}", profile.name(), packs.size(), packs);
        if (create) {
            NATIVE.add(profile.id());
            Create.LOGGER.info(
                    "Native Create client detected for {}: sending real Create blocks/items", profile.name());
        } else {
            NATIVE.remove(profile.id());
        }
    }

    public static boolean isNative(UUID id) {
        return id != null && NATIVE.contains(id);
    }

    public static void forget(UUID id) {
        NATIVE.remove(id);
    }

    public static boolean isNative(ServerPlayer player) {
        return player != null && NATIVE.contains(player.getUUID());
    }

    public static boolean isNative(PacketContext context) {
        if (context == null || NATIVE.isEmpty()) return false;
        GameProfile profile = context.get(PacketContext.GAME_PROFILE);
        return profile != null && NATIVE.contains(profile.id());
    }

    private static BlockMapper nativeMapper(BlockMapper fallback) {
        return new BlockMapper() {
            @Override
            public BlockState toClientSideState(BlockState state, PacketContext context) {
                return BuiltInRegistries.BLOCK
                                .getKey(state.getBlock())
                                .getNamespace()
                                .equals(Create.MOD_ID)
                        ? state
                        : fallback.toClientSideState(state, context);
            }

            @Override
            public String getMapperName() {
                return "createpoly:native_create";
            }
        };
    }
}
