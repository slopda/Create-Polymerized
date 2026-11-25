package com.zurrtum.create.infrastructure.polymer;

import com.mojang.authlib.GameProfile;
import com.zurrtum.create.Create;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.core.api.block.BlockMapper;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class PolymerBedrockCarriers {
    private PolymerBedrockCarriers() {}

    public static boolean SKIP_PAPER_DISPLAYS = true;

    public static boolean bedrockNative(net.minecraft.server.level.ServerPlayer player, BlockState state) {
        return isBedrock(player.getUUID()) && nativeIds().contains(net.minecraft.world.level.block.Block.getId(state));
    }

    public static boolean skipPaperDisplays(net.minecraft.server.network.ServerGamePacketListenerImpl listener) {
        return SKIP_PAPER_DISPLAYS && listener != null && isBedrock(listener.player.getUUID());
    }

    public static boolean ENABLED = true;
    private static final Map<BlockState, PolymerBlockModel> CANDIDATES = new LinkedHashMap<>();
    private static final Map<BlockState, BlockState> CARRIERS = new HashMap<>();

    private static boolean registered;
    private static @Nullable Object geyserApi;
    private static @Nullable Method isBedrockPlayer;
    private static boolean geyserLookedUp;

    static void candidate(BlockState source, @Nullable PolymerBlockModel model) {
        if (ENABLED && model != null) CANDIDATES.putIfAbsent(source, model);
    }

    /**
     * Allocates carriers after every Java projection is registered (so Java allocation is unchanged) and
     * hands each one to {@code export} as carrier, source, model.
     */
    static void allocate(BiFunction<PolymerBlockModel, BlockState, BlockState> request, ExportSink export) {
        if (!ENABLED) return;
        int unavailable = 0;
        for (var entry : CANDIDATES.entrySet()) {
            BlockState carrier = request.apply(entry.getValue(), entry.getKey());
            if (carrier == null) {
                unavailable++;
                continue;
            }
            CARRIERS.put(entry.getKey(), carrier);
            export.accept(carrier, entry.getKey(), entry.getValue());
        }
        if (!registered && !CARRIERS.isEmpty()) {
            registered = true;
            BlockMapper.DEFAULT_MAPPER_EVENT.register(
                    (context, mapper) -> isBedrock(context) ? bedrockMapper(mapper) : mapper);
            net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
                    (listener, sender, server) -> server.execute(() -> exportClientStates(listener)));
        }
        // State ids and tags are final only once the registries are frozen; export at server start, before Geyser
        // (which defines its custom blocks while the server starts) reads the file.
        if (!exportRegistered) {
            exportRegistered = true;
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(
                    server -> exportJavaStates());
        }
        Create.LOGGER.info(
                "Bedrock static models: {} display-drawn Create states, {} carriers allocated, {} without a free carrier",
                CANDIDATES.size(),
                CARRIERS.size(),
                unavailable);
    }

    private static boolean exportRegistered;

    static void exportJavaStates() {
        com.google.gson.JsonArray states = new com.google.gson.JsonArray();
        var blocks = net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        for (var block : blocks) {
            var id = blocks.getKey(block);
            if (!"create".equals(id.getNamespace())) continue;
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                o.addProperty("state", stateKey(state));
                o.addProperty("id", net.minecraft.world.level.block.Block.getId(state));
                o.addProperty("group", blocks.getId(block));
                o.addProperty(
                        "hardness",
                        state.getDestroySpeed(
                                net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO));
                o.addProperty(
                        "waterlogged",
                        state.hasProperty(
                                        net.minecraft.world.level.block.state.properties.BlockStateProperties
                                                .WATERLOGGED)
                                && state.getValue(
                                        net.minecraft.world.level.block.state.properties.BlockStateProperties
                                                .WATERLOGGED));
                o.addProperty("blockEntity", state.hasBlockEntity());
                o.addProperty(
                        "pick",
                        net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getKey(block.asItem())
                                .toString());
                com.google.gson.JsonArray boxes = new com.google.gson.JsonArray();
                try {
                    for (var box : state.getCollisionShape(
                                    net.minecraft.world.level.EmptyBlockGetter.INSTANCE,
                                    net.minecraft.core.BlockPos.ZERO)
                            .toAabbs()) {
                        com.google.gson.JsonArray b = new com.google.gson.JsonArray();
                        b.add(box.minX);
                        b.add(box.minY);
                        b.add(box.minZ);
                        b.add(box.maxX);
                        b.add(box.maxY);
                        b.add(box.maxZ);
                        boxes.add(b);
                    }
                } catch (RuntimeException e) {
                }
                o.add("collision", boxes);
                states.add(o);
            }
        }
        try {
            java.nio.file.Path out = java.nio.file.Path.of("polymerpatch", "create_java_states.json");
            java.nio.file.Files.createDirectories(out.getParent());
            java.nio.file.Files.writeString(out, new com.google.gson.Gson().toJson(states));
            Create.LOGGER.info("Wrote polymerpatch/create_java_states.json ({} Create states)", states.size());
        } catch (java.io.IOException | RuntimeException e) {
            Create.LOGGER.warn("Could not write polymerpatch/create_java_states.json", e);
        }
    }

    private static final java.nio.file.Path NATIVE_IDS_FILE =
            java.nio.file.Path.of("polymerpatch", "bedrock_native_ids.txt");
    private static volatile it.unimi.dsi.fastutil.ints.IntSet nativeIds = it.unimi.dsi.fastutil.ints.IntSets.EMPTY_SET;
    private static volatile long nativeIdsStamp = Long.MIN_VALUE;
    private static volatile long nativeIdsChecked;

    private static it.unimi.dsi.fastutil.ints.IntSet nativeIds() {
        long now = System.currentTimeMillis();
        if (now - nativeIdsChecked < 1000) return nativeIds;
        nativeIdsChecked = now;
        try {
            long stamp = java.nio.file.Files.isRegularFile(NATIVE_IDS_FILE)
                    ? java.nio.file.Files.getLastModifiedTime(NATIVE_IDS_FILE).toMillis()
                    : -1;
            if (stamp == nativeIdsStamp) return nativeIds;
            nativeIdsStamp = stamp;
            it.unimi.dsi.fastutil.ints.IntOpenHashSet ids = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
            if (stamp >= 0)
                for (String line : java.nio.file.Files.readAllLines(NATIVE_IDS_FILE))
                    if (!line.isBlank()) ids.add(Integer.parseInt(line.trim()));
            nativeIds = ids;
            Create.LOGGER.info("Bedrock native Create block states: {}", ids.size());
        } catch (java.io.IOException | RuntimeException e) {
            Create.LOGGER.warn("Could not read " + NATIVE_IDS_FILE, e);
        }
        return nativeIds;
    }

    private static boolean exportedJava, exportedBedrock;

    private static void exportClientStates(net.minecraft.server.network.ServerGamePacketListenerImpl listener) {
        boolean bedrock = isBedrock(listener.player.getUUID());
        if (bedrock ? exportedBedrock : exportedJava) return;
        if (bedrock) exportedBedrock = true;
        else exportedJava = true;
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        BlockMapper mapper = BlockMapper.get(listener);
        PacketContext.runWithContext(listener, () -> {
            for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
                if (!"create".equals(id.getNamespace())) continue;
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    BlockState client;
                    try {
                        client = mapper.toClientSideState(state, PacketContext.get());
                    } catch (RuntimeException e) {
                        continue;
                    }
                    root.addProperty(stateKey(state), stateKey(client));
                }
            }
        });
        String name = bedrock ? "client_states_bedrock.json" : "client_states_java.json";
        try {
            java.nio.file.Path out = java.nio.file.Path.of("polymerpatch", name);
            java.nio.file.Files.createDirectories(out.getParent());
            java.nio.file.Files.writeString(out, new com.google.gson.Gson().toJson(root));
            Create.LOGGER.info("Wrote polymerpatch/{} ({} Create states)", name, root.size());
        } catch (java.io.IOException | RuntimeException e) {
            Create.LOGGER.warn("Could not write polymerpatch/" + name, e);
        }
    }

    public static String stateKey(BlockState state) {
        StringBuilder key = new StringBuilder(net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(state.getBlock())
                .toString());
        var properties = state.getProperties().stream()
                .sorted(java.util.Comparator.comparing(
                        net.minecraft.world.level.block.state.properties.Property::getName))
                .toList();
        if (!properties.isEmpty()) {
            key.append('[');
            for (int i = 0; i < properties.size(); i++) {
                if (i > 0) key.append(',');
                key.append(properties.get(i).getName()).append('=').append(valueName(state, properties.get(i)));
            }
            key.append(']');
        }
        return key.toString();
    }

    private static <T extends Comparable<T>> String valueName(
            BlockState state, net.minecraft.world.level.block.state.properties.Property<T> property) {
        return property.getName(state.getValue(property));
    }

    interface ExportSink {
        void accept(BlockState carrier, BlockState source, PolymerBlockModel model);
    }

    private static BlockMapper bedrockMapper(BlockMapper fallback) {
        return new BlockMapper() {
            @Override
            public BlockState toClientSideState(BlockState state, PacketContext context) {
                if (nativeIds().contains(net.minecraft.world.level.block.Block.getId(state))) return state;
                BlockState carrier = CARRIERS.get(state);
                return carrier != null ? carrier : fallback.toClientSideState(state, context);
            }

            @Override
            public String getMapperName() {
                return "polymerpatch_create:bedrock_static";
            }
        };
    }

    static boolean isBedrock(@Nullable PacketContext context) {
        if (context == null) return false;
        GameProfile profile = context.get(PacketContext.GAME_PROFILE);
        return profile != null && isBedrock(profile.id());
    }

    public static boolean isBedrock(@Nullable UUID id) {
        if (id == null) return false;
        if (!geyserLookedUp) {
            geyserLookedUp = true;
            try {
                Class<?> api = Class.forName("org.geysermc.geyser.api.GeyserApi");
                geyserApi = api.getMethod("api").invoke(null);
                isBedrockPlayer = api.getMethod("isBedrockPlayer", UUID.class);
            } catch (ClassNotFoundException | LinkageError e) {
                return false;
            } catch (ReflectiveOperationException | RuntimeException e) {
                geyserApi = null;
            }
            if (geyserApi == null) geyserLookedUp = false;
        }
        if (geyserApi == null || isBedrockPlayer == null) return false;
        try {
            return (boolean) isBedrockPlayer.invoke(geyserApi, id);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
