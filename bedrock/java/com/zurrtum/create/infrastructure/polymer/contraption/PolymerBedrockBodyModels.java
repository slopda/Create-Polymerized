package com.zurrtum.create.infrastructure.polymer.contraption;

import com.google.gson.JsonParser;
import com.zurrtum.create.Create;
import com.zurrtum.create.infrastructure.polymer.PolymerBedrockCarriers;
import com.zurrtum.create.infrastructure.polymer.PolymerBedrockMeshes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class PolymerBedrockBodyModels {
    public static final long MARKER = 0x707063746d657368L;
    public static final String POSE_HOOK = "polymerpatch.contraptionMeshPose";
    private static @Nullable Map<String, PolymerBedrockMeshes.Model> table;
    private static Map<String, PolymerBedrockMeshes.Model> parts = Map.of();
    private static Map<String, PolymerBedrockMeshes.Model> blocks = Map.of();
    private static final Map<BlockState, PolymerBedrockMeshes.Model> cache = new IdentityHashMap<>();
    private static final Set<BlockState> unsupported = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<BlockState, java.util.Optional<PolymerBedrockMeshes.Model>> blockCache =
            new IdentityHashMap<>();

    private PolymerBedrockBodyModels() {}

    public static PolymerBedrockMeshes.@Nullable Model blockMesh(BlockState state) {
        table();
        if (blocks.isEmpty()) return null;
        return blockCache
                .computeIfAbsent(
                        state, s -> java.util.Optional.ofNullable(blocks.get(PolymerBedrockCarriers.stateKey(s))))
                .orElse(null);
    }

    public static PolymerBedrockMeshes.@Nullable Model partMesh(net.minecraft.resources.Identifier model) {
        table();
        return parts.get(model.toString());
    }

    static PolymerBedrockMeshes.@Nullable Model mesh(BlockState state) {
        var found = cache.get(state);
        if (found != null || unsupported.contains(state)) return found;
        found = table().get(PolymerBedrockCarriers.stateKey(state));
        if (found == null) unsupported.add(state);
        else cache.put(state, found);
        return found;
    }

    private static Map<String, PolymerBedrockMeshes.Model> table() {
        if (table != null) return table;
        Path file = Path.of("polymerpatch", "contraption_meshes.json");
        Map<String, PolymerBedrockMeshes.Model> loaded = new HashMap<>(),
                loadedParts = new HashMap<>(),
                loadedBlocks = new HashMap<>();
        try {
            var root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (root.get("format").getAsInt() != 1) throw new IllegalArgumentException("Unknown mesh table format");
            int geometries = root.get("geometries").getAsInt(),
                    materials = root.get("materials").getAsInt();
            read(root.getAsJsonObject("states"), geometries, materials, loaded);
            if (root.has("parts")) read(root.getAsJsonObject("parts"), geometries, materials, loadedParts);
            if (root.has("blocks")) read(root.getAsJsonObject("blocks"), geometries, materials, loadedBlocks);
        } catch (Exception e) {
            loaded.clear();
            loadedParts.clear();
            loadedBlocks.clear();
            Create.LOGGER.error("Bedrock contraption meshes: cannot load {}; using item fallback", file, e);
        }
        table = Map.copyOf(loaded);
        parts = Map.copyOf(loadedParts);
        blocks = Map.copyOf(loadedBlocks);
        Create.LOGGER.info(
                "Bedrock contraption meshes: {} source states, {} block-part models, {} oversize block states from {}",
                table.size(),
                parts.size(),
                blocks.size(),
                file);
        return table;
    }

    private static void read(
            com.google.gson.JsonObject entries,
            int geometries,
            int materials,
            Map<String, PolymerBedrockMeshes.Model> into) {
        for (var entry : entries.entrySet()) {
            var pair = entry.getValue().getAsJsonArray();
            int g = pair.get(0).getAsInt(), m = pair.get(1).getAsInt();
            if (g < 0 || g >= geometries || m < 0 || m >= materials)
                throw new IllegalArgumentException("Invalid source mesh indices for " + entry.getKey());
            into.put(entry.getKey(), new PolymerBedrockMeshes.Model(g, m));
        }
    }
}
