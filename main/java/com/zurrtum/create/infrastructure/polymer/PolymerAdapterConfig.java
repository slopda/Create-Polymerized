package com.zurrtum.create.infrastructure.polymer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zurrtum.create.Create;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class PolymerAdapterConfig {
    public static int blockDisplayDistance = 64;
    public static int contraptionDisplayDistance = 64;
    public static boolean blockHolderGate = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean loaded;

    private PolymerAdapterConfig() {}

    public static float blockScale() {
        return blockDisplayDistance / 64f;
    }

    public static float contraptionScale() {
        return contraptionDisplayDistance / 64f;
    }

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        Path file = FabricLoader.getInstance().getConfigDir().resolve("polymerpatch-create.json");
        JsonObject json = new JsonObject();
        try {
            if (Files.exists(file))
                json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                        .getAsJsonObject();
        } catch (Exception invalid) {
            Create.LOGGER.warn("[PolymerPatch] {} is not valid JSON; using defaults: {}", file, invalid.toString());
            json = new JsonObject();
        }
        blockDisplayDistance = clamp(json, "blockDisplayDistance", blockDisplayDistance);
        contraptionDisplayDistance = clamp(json, "contraptionDisplayDistance", contraptionDisplayDistance);
        if (json.has("blockHolderGate") && json.get("blockHolderGate").isJsonPrimitive())
            blockHolderGate = json.get("blockHolderGate").getAsBoolean();
        JsonObject out = new JsonObject();
        out.addProperty(
                "_comment",
                "Distances in blocks (16..512) that vanilla clients draw Create machine parts; 64 matches vanilla. "
                        + "Higher costs client FPS. blockHolderGate: send block parts only to players near enough to "
                        + "draw them.");
        out.addProperty("blockDisplayDistance", blockDisplayDistance);
        out.addProperty("contraptionDisplayDistance", contraptionDisplayDistance);
        out.addProperty("blockHolderGate", blockHolderGate);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(out) + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException unwritable) {
            Create.LOGGER.warn("[PolymerPatch] Could not write {}: {}", file, unwritable.toString());
        }
        Create.LOGGER.info(
                "[PolymerPatch] Display distance: blocks {}, contraptions {}, block holder gate {}",
                blockDisplayDistance,
                contraptionDisplayDistance,
                blockHolderGate);
    }

    private static int clamp(JsonObject json, String key, int fallback) {
        try {
            return json.has(key) ? Math.clamp(json.get(key).getAsInt(), 16, 512) : fallback;
        } catch (RuntimeException invalid) {
            return fallback;
        }
    }
}
