package com.zurrtum.create.infrastructure.polymer.nativeclient;

import com.zurrtum.create.Create;
import dev.veeda.createpoly.mixin.NativeTagPayloadAccessor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class PolymerNativeRegistryIds {
    private PolymerNativeRegistryIds() {}

    private static final ThreadLocal<Integer> CODEC_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static volatile Map<Object, Mapping> mappings = Map.of();

    private record Mapping(Map<Object, Integer> toClient, Object[] toServer, int[] serverToClient) {}

    static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> initialize());
    }

    public static void enterCodec() {
        CODEC_DEPTH.set(CODEC_DEPTH.get() + 1);
    }

    public static void exitCodec() {
        int depth = CODEC_DEPTH.get() - 1;
        if (depth <= 0) CODEC_DEPTH.remove();
        else CODEC_DEPTH.set(depth);
    }

    private static Mapping current(Object owner) {
        if (CODEC_DEPTH.get() == 0 || !PolymerNativeClients.isNative(PacketContext.get())) return null;
        return mappings.get(owner);
    }

    public static int toClient(Object owner, Object value, int serverId) {
        Mapping mapping = current(owner);
        if (mapping == null || value == null) return serverId;
        Integer id = mapping.toClient().get(value);
        if (id == null) throw new IllegalArgumentException("No original Create client wire ID for " + value);
        return id;
    }

    public static boolean translates(Object owner) {
        return current(owner) != null;
    }

    public static Object fromClient(Object owner, int id) {
        Mapping mapping = current(owner);
        if (mapping == null) throw new IllegalStateException("Native registry decoding outside its codec scope");
        return id >= 0 && id < mapping.toServer().length ? mapping.toServer()[id] : null;
    }

    public static Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> tags(
            Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> original) {
        Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> translated =
                new LinkedHashMap<>();
        original.forEach((key, payload) -> {
            Registry<?> registry = BuiltInRegistries.REGISTRY.getValue(key.identifier());
            Mapping mapping = mappings.get(registry);
            if (mapping == null) {
                translated.put(key, payload);
                return;
            }
            Map<Identifier, IntList> tags = new LinkedHashMap<>();
            ((NativeTagPayloadAccessor) (Object) payload).nativeTags().forEach((name, members) -> {
                IntList ids = new IntArrayList(members.size());
                for (int id : members) {
                    if (id < 0 || id >= mapping.serverToClient().length || mapping.serverToClient()[id] < 0)
                        throw new IllegalArgumentException("No original Create tag member ID in " + key);
                    ids.add(mapping.serverToClient()[id]);
                }
                tags.put(name, ids);
            });
            translated.put(key, new TagNetworkSerialization.NetworkPayload(tags));
        });
        return translated;
    }

    @SuppressWarnings("unchecked")
    private static void initialize() {
        Map<String, Map<String, Integer>> registries = new LinkedHashMap<>();
        Map<String, Integer> states = new HashMap<>();
        try (var raw = Objects.requireNonNull(
                        PolymerNativeRegistryIds.class.getResourceAsStream(
                                "/polymerpatch/native-create-6.0.9-2-registry.tsv.gz"),
                        "Missing original Create registry snapshot");
                var reader =
                        new BufferedReader(new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) {
                String[] fields = line.split("\t", 4);
                if (fields[0].equals("R")) {
                    if (registries
                                    .computeIfAbsent(fields[1], ignored -> new LinkedHashMap<>())
                                    .put(fields[3], Integer.parseInt(fields[2]))
                            != null) throw new IllegalStateException("Duplicate original registry entry");
                } else if (fields[0].equals("S")) {
                    if (states.put(fields[2], Integer.parseInt(fields[1])) != null)
                        throw new IllegalStateException("Duplicate original block state");
                } else throw new IllegalStateException("Invalid original registry snapshot row");
            }
        } catch (Exception error) {
            throw new IllegalStateException("Cannot load original Create wire IDs", error);
        }

        Map<Object, Mapping> result = new IdentityHashMap<>();
        int moved = 0;
        for (Registry<?> rawRegistry : BuiltInRegistries.REGISTRY) {
            Registry<Object> registry = (Registry<Object>) rawRegistry;
            Map<String, Integer> ids =
                    registries.get(registry.key().identifier().toString());
            if (ids == null) continue;
            Map<Object, Integer> toClient = new IdentityHashMap<>();
            Object[] toServer = new Object[Collections.max(ids.values()) + 1];
            for (var entry : ids.entrySet()) {
                Identifier key = Identifier.parse(entry.getKey());
                if (!registry.containsKey(key))
                    throw new IllegalStateException("Original registry entry missing: " + key);
                Object value = registry.getValue(key);
                toClient.put(value, entry.getValue());
                toServer[entry.getValue()] = value;
                if (registry.getId(value) != entry.getValue()) moved++;
            }
            int[] serverToClient = new int[registry.size()];
            Arrays.fill(serverToClient, -1);
            toClient.forEach((value, clientId) -> serverToClient[registry.getId(value)] = clientId);
            result.put(registry, new Mapping(Collections.unmodifiableMap(toClient), toServer, serverToClient));
        }
        Map<Object, Integer> toClient = new IdentityHashMap<>();
        Object[] toServer = new Object[Collections.max(states.values()) + 1];
        int unknown = 0, otherMods = 0;
        for (var state : Block.BLOCK_STATE_REGISTRY) {
            Integer id = states.get(state.toString());
            if (id == null) {
                // Other server-side mods (e.g. Polymer ports such as Blockus) add their own states; the original
                // Create client never sees those ids, Polymer sends them as vanilla states. They stay unmapped, so
                // toClient still refuses them instead of sending an unrelated block. Vanilla and Create states must
                // all match.
                String namespace =
                        BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace();
                if (namespace.equals("minecraft") || namespace.equals(Create.MOD_ID)) unknown++;
                else otherMods++;
                continue;
            }
            toClient.put(state, id);
            toServer[id] = state;
            if (Block.getId(state) != id) moved++;
        }
        if (unknown != 0 || Arrays.stream(toServer).anyMatch(Objects::isNull))
            throw new IllegalStateException(
                    "Original Create block-state schema mismatch: " + unknown + " extra server states");
        int[] serverToClient = new int[Block.BLOCK_STATE_REGISTRY.size()];
        Arrays.fill(serverToClient, -1);
        toClient.forEach(
                (value, clientId) -> serverToClient[Block.BLOCK_STATE_REGISTRY.getId((BlockState) value)] = clientId);
        result.put(
                Block.BLOCK_STATE_REGISTRY,
                new Mapping(Collections.unmodifiableMap(toClient), toServer, serverToClient));
        mappings = Collections.unmodifiableMap(result);
        Create.LOGGER.info(
                "Native Create wire-ID translation ready: {} registries, {} states, {} differing IDs, {} states from"
                        + " other mods left unmapped",
                result.size() - 1,
                states.size(),
                moved,
                otherMods);
    }
}
