package com.zurrtum.create.infrastructure.polymer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zurrtum.create.Create;
import com.zurrtum.create.infrastructure.polymer.block.PolymerCopycatParts;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

final class PolymerModelSharing {
    private PolymerModelSharing() {}

    static final String SHARED = "block/polymer/shared/";
    private static final String MODELS = "assets/create/models/";

    static void share(ResourcePackBuilder builder) {
        TreeSet<String> paths = new TreeSet<>();
        builder.forEachResource((path, resource) -> {
            if (path.startsWith(MODELS)
                    && path.endsWith(".json")
                    && !path.startsWith(MODELS + "block/p/")
                    && !path.startsWith(MODELS + "item/ui/ponder")
                    && !path.startsWith(MODELS + PolymerCopycatParts.FOLDER)
                    && !path.startsWith(MODELS + SHARED)) paths.add(path);
        });
        Map<String, List<String>> byContent = new LinkedHashMap<>();
        Map<String, JsonObject> textures = new LinkedHashMap<>();
        for (String path : paths) {
            String source = builder.getStringData(path);
            if (source == null) continue;
            JsonObject model;
            try {
                model = JsonParser.parseString(source).getAsJsonObject();
            } catch (RuntimeException notAnObject) {
                continue;
            }
            if (model.has("parent") || !model.has("elements")) continue;
            JsonObject own = model.has("textures") && model.get("textures").isJsonObject()
                    ? model.getAsJsonObject("textures")
                    : null;
            if (model.has("textures") && own == null) continue;
            model.remove("textures");
            byContent.computeIfAbsent(model.toString(), k -> new ArrayList<>()).add(path);
            if (own != null) textures.put(path, own);
        }
        int parents = 0, children = 0;
        for (var group : byContent.entrySet()) {
            if (group.getValue().size() < 2) continue;
            String parent = SHARED + Integer.toHexString(parents++);
            builder.addStringData(MODELS + parent + ".json", group.getKey());
            for (String path : group.getValue()) {
                JsonObject child = new JsonObject();
                child.addProperty("parent", "create:" + parent);
                if (textures.containsKey(path)) child.add("textures", textures.get(path));
                builder.addStringData(path, child.toString());
                children++;
            }
        }
        Create.LOGGER.info("Shared {} model geometries among {} generated models", parents, children);
    }
}
