package com.zurrtum.create.infrastructure.polymer.gui;

import com.google.gson.JsonParser;
import com.zurrtum.create.Create;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

public final class PolymerSchematicGuis {
    private static volatile Map<String, String> language = Map.of();

    private PolymerSchematicGuis() {}

    public static Map<String, String> all() {
        Map<String, String> map = language;
        if (!map.isEmpty()) return map;
        synchronized (PolymerSchematicGuis.class) {
            if (!language.isEmpty()) return language;
            try {
                var path = FabricLoader.getInstance()
                        .getModContainer("create")
                        .orElseThrow()
                        .findPath("assets/create/lang/en_us.json")
                        .orElseThrow();
                Map<String, String> entries = new HashMap<>();
                JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                        .getAsJsonObject()
                        .entrySet()
                        .forEach(e -> entries.put(e.getKey(), e.getValue().getAsString()));
                language = Map.copyOf(entries);
            } catch (IOException | RuntimeException exception) {
                Create.LOGGER.warn("Server UIs could not read Create's language file", exception);
                language = Map.of("", "");
            }
            return language;
        }
    }

    public static String lang(String key) {
        return all().getOrDefault(key, key);
    }
}
