package com.zurrtum.create.infrastructure.polymer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zurrtum.create.Create;
import eu.pb4.polymer.resourcepack.api.PackResource;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.resources.Identifier;

final class PolymerModelMaterials {
    static void addAssets(ResourcePackBuilder builder) {
        for (String atlas : new String[] {"blocks", "items"})
            builder.addStringData("assets/minecraft/atlases/" + atlas + ".json", "{\"sources\":[]}");
        builder.addPreFinishTask(PolymerModelMaterials::finish);
    }

    private static void finish(ResourcePackBuilder builder) {
        Set<String> resources = new TreeSet<>();
        Map<String, JsonObject> changed = new TreeMap<>();
        Set<String> sprites = new TreeSet<>();
        builder.forEachResource((path, resource) -> resources.add(path));
        for (String path : resources) {
            if (!path.endsWith(".json")
                    || !(path.startsWith("assets/create/models/block/polymer/")
                            || path.startsWith("assets/create/models/item/ui/"))) continue;
            String source = builder.getStringData(path);
            if (source == null) continue;
            JsonObject model = JsonParser.parseString(source).getAsJsonObject();
            JsonObject textures = model.has("textures") ? model.getAsJsonObject("textures") : null;
            if (textures == null) continue;
            if (!textures.has("particle") && model.has("elements")) {
                outer:
                for (JsonElement element : model.getAsJsonArray("elements")) {
                    JsonObject part = element.getAsJsonObject();
                    if (!part.has("faces")) continue;
                    for (JsonElement face :
                            part.getAsJsonObject("faces").asMap().values()) {
                        JsonElement texture = face.getAsJsonObject().get("texture");
                        if (texture != null && texture.isJsonPrimitive()) {
                            textures.add("particle", texture.deepCopy());
                            changed.put(path, model);
                            break outer;
                        }
                    }
                }
            }
            for (JsonElement entry : textures.asMap().values()) {
                JsonElement value =
                        entry.isJsonObject() ? entry.getAsJsonObject().get("sprite") : entry;
                if (value == null || !value.isJsonPrimitive()) continue;
                String name = value.getAsString();
                if (name.startsWith("#")) continue;
                Identifier id = Identifier.parse(name);
                if (!id.getNamespace().equals(Create.MOD_ID)
                        || id.getPath().startsWith("block/")
                        || id.getPath().startsWith("item/")) continue;
                if (!resources.contains("assets/" + id.getNamespace() + "/textures/" + id.getPath() + ".png"))
                    throw new IllegalStateException("Missing native model material " + id + " in " + path);
                sprites.add(id.toString());
            }
        }
        changed.forEach((path, model) -> builder.addStringData(path, model.toString()));
        // DefaultRPBuilder merges atlasDefinitions before pre-finish tasks. addData(atlas) here would update that
        // already-consumed map. Adapt the final emitted files through the public converter instead.
        builder.addResourceConverter((path, resource) -> {
            if (!path.equals("assets/minecraft/atlases/blocks.json")
                    && !path.equals("assets/minecraft/atlases/items.json")) return resource;
            JsonObject data = resource.asJson().getAsJsonObject();
            JsonArray sources = data.has("sources") ? data.getAsJsonArray("sources") : new JsonArray();
            Set<String> existing = new TreeSet<>();
            for (JsonElement entry : sources) {
                JsonObject entryData = entry.getAsJsonObject();
                if (entryData.has("resource")
                        && entryData.get("type").getAsString().endsWith("single"))
                    existing.add(entryData.get("resource").getAsString());
            }
            for (String sprite : sprites)
                if (existing.add(sprite)) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("type", "minecraft:single");
                    entry.addProperty("resource", sprite);
                    sources.add(entry);
                }
            data.add("sources", sources);
            return PackResource.fromJson(data);
        });
        Create.LOGGER.info(
                "Registered {} direct model textures in native atlases and completed {} decorative particle bindings",
                sprites.size(),
                changed.size());
        PolymerModelSharing.share(builder);
        PolymerPartItems.fold(builder);
    }
}
