package com.zurrtum.create.infrastructure.polymer;

import com.google.gson.JsonParser;
import com.zurrtum.create.Create;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerBlockCollision;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerStaticCollision;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;

final class PolymerTerrainPromotion {
    private static final Map<PolymerBlockModel, Integer> CANDIDATES = loadCandidates();
    private static final Map<Key, BlockState> RESERVED = new HashMap<>();
    private static int promotedStates;

    private record Key(PolymerBlockModel model, BlockModelType type) {}

    private PolymerTerrainPromotion() {}

    private static Map<PolymerBlockModel, Integer> loadCandidates() {
        try (var input =
                PolymerTerrainPromotion.class.getResourceAsStream("/polymerpatch/terrain-model-candidates.json")) {
            if (input == null) return Map.of();
            var json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            List<Map.Entry<PolymerBlockModel, Integer>> measured = new ArrayList<>();
            for (var entry : json.getAsJsonArray("models")) {
                var row = entry.getAsJsonObject();
                if (!row.get("strategy").getAsString().equals("biome")) continue;
                var model = new PolymerBlockModel(
                        Identifier.parse(row.get("id").getAsString()),
                        row.get("x").getAsInt(),
                        row.get("y").getAsInt(),
                        row.get("uvlock").getAsBoolean(),
                        row.get("weight").getAsInt());
                measured.add(Map.entry(model, Math.max(0, row.get("placements").getAsInt())));
            }
            measured.sort(Comparator.<Map.Entry<PolymerBlockModel, Integer>>comparingInt(Map.Entry::getValue)
                    .reversed()
                    .thenComparing(e -> e.getKey().toString()));
            Map<PolymerBlockModel, Integer> result = new LinkedHashMap<>();
            for (var row : measured) result.put(row.getKey(), row.getValue());
            return Collections.unmodifiableMap(result);
        } catch (Exception invalid) {
            Create.LOGGER.warn("Ignoring invalid certified terrain-model manifest", invalid);
            return Map.of();
        }
    }

    static int promote(Map<BlockState, BlockState> clients, Map<BlockState, PolymerBlockModel> displays) {
        if (CANDIDATES.isEmpty()) return 0;
        int changed = 0;
        for (var candidate : CANDIDATES.keySet()) {
            var iterator = displays.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (!candidate.equals(entry.getValue()) || !eligible(entry.getKey())) continue;
                BlockState source = entry.getKey();
                BlockModelType type = source.getFluidState().isEmpty()
                        ? BlockModelType.BIOME_COLORED_LEAVES
                        : BlockModelType.BIOME_COLORED_LEAVES_WATERLOGGED;
                Key key = new Key(candidate, type);
                if (!RESERVED.containsKey(key))
                    RESERVED.put(key, PolymerBlockResourceUtils.requestBlock(type, candidate));
                BlockState carrier = RESERVED.get(key);
                if (carrier == null || !compatible(source, carrier)) continue;
                clients.put(source, carrier);
                iterator.remove();
                changed++;
            }
        }
        promotedStates += changed;
        return changed;
    }

    static boolean eligible(BlockState source) {
        if (PolymerStaticCollision.carrier(source) != null) return false;
        if (PolymerStaticCollision.targets(source)
                || !PolymerStaticCollision.cells(source).isEmpty()) return false;
        return PolymerBlockCollision.sameShape(PolymerBlockCollision.shape(source), Shapes.block());
    }

    static boolean compatible(BlockState source, BlockState carrier) {
        return source.getFluidState().equals(carrier.getFluidState())
                && !carrier.isSolidRender()
                && PolymerBlockCollision.sameShape(
                        PolymerBlockCollision.shape(source), PolymerBlockCollision.shape(carrier));
    }

    static void log() {
        long models = RESERVED.values().stream().filter(Objects::nonNull).count();
        Create.LOGGER.info(
                "Measured terrain promotion: certified models={}, allocated models={}, promoted source states={}, spare biome leaves dry={} wet={}",
                CANDIDATES.size(),
                models,
                promotedStates,
                PolymerBlockResourceUtils.getBlocksLeft(BlockModelType.BIOME_COLORED_LEAVES),
                PolymerBlockResourceUtils.getBlocksLeft(BlockModelType.BIOME_COLORED_LEAVES_WATERLOGGED));
    }
}
