package com.zurrtum.create.infrastructure.polymer.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.logistics.BigItemStack;
import dev.veeda.createpoly.PatchAssets;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

public final class PolymerMarketItems {
    private static volatile Map<Identifier, Integer> countIndices = Map.of();
    private static volatile Map<String, Integer> countSlots = Map.of();
    private static final Set<Identifier> missingModels = ConcurrentHashMap.newKeySet();

    public static void addAssets(ResourcePackBuilder builder) {
        builder.addPreFinishTask(PolymerMarketItems::wrapModels);
    }

    private static void wrapModels(ResourcePackBuilder builder) {
        Map<Identifier, JsonObject> definitions = new HashMap<>();
        try (InputStream stream = PatchAssets.open("/polymer/vanilla_item_definitions.json.gz")) {
            if (stream == null) throw new IllegalStateException("Native item definitions are missing");
            JsonObject vanilla = JsonParser.parseReader(
                            new InputStreamReader(new GZIPInputStream(stream), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            vanilla.entrySet()
                    .forEach(entry -> definitions.put(
                            Identifier.parse(entry.getKey()), entry.getValue().getAsJsonObject()));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read native stock item models", exception);
        }
        builder.forEachResource((path, resource) -> {
            if (!path.startsWith("assets/") || !path.endsWith(".json")) return;
            String[] parts = path.split("/", 4);
            if (parts.length != 4 || !parts[2].equals("items")) return;
            Identifier id = Identifier.fromNamespaceAndPath(parts[1], parts[3].substring(0, parts[3].length() - 5));
            if (id.getNamespace().equals("create")
                    && (id.getPath().startsWith("ui/")
                            || id.getPath().startsWith("polymer_part/")
                            || id.getPath().startsWith("polymer_block/"))) return;
            String data = builder.getStringData(path);
            if (data != null) definitions.put(id, JsonParser.parseString(data).getAsJsonObject());
        });
        JsonArray countModels = JsonParser.parseString(builder.getStringData("assets/create/items/ui/stock_count.json"))
                .getAsJsonObject()
                .getAsJsonObject("model")
                .getAsJsonArray("models")
                .deepCopy();
        for (JsonElement selector : countModels)
            for (JsonElement entry : selector.getAsJsonObject().getAsJsonArray("cases"))
                shear(entry.getAsJsonObject().getAsJsonObject("model"));
        Map<String, Integer> slots = new HashMap<>();
        for (int i = 0; i < 4; i++) {
            for (JsonElement entry : countModels.get(i).getAsJsonObject().getAsJsonArray("cases")) {
                JsonElement when = entry.getAsJsonObject().get("when");
                if (when.isJsonArray()) for (JsonElement key : when.getAsJsonArray()) slots.put(key.getAsString(), i);
                else slots.put(when.getAsString(), i);
            }
        }
        countSlots = Map.copyOf(slots);
        Map<Identifier, Integer> indices = new HashMap<>();
        for (Map.Entry<Identifier, JsonObject> entry : definitions.entrySet()) {
            JsonObject definition = entry.getValue();
            if (!definition.has("model") || !nativeModel(definition.getAsJsonObject("model"))) continue;
            int index = nextCountIndex(definition.get("model"));
            JsonArray models = new JsonArray();
            models.add(definition.get("model").deepCopy());
            for (int i = 0; i < 4; i++) {
                JsonObject selector = countModels.get(i).deepCopy().getAsJsonObject();
                selector.addProperty("index", index + i);
                models.add(selector);
            }
            JsonObject composite = new JsonObject();
            composite.addProperty("type", "minecraft:composite");
            composite.add("models", models);
            JsonObject wrapped = definition.deepCopy();
            wrapped.addProperty("oversized_in_gui", true);
            wrapped.add("model", composite);
            Identifier id = wrapper(entry.getKey());
            builder.addStringData("assets/create/items/" + id.getPath() + ".json", wrapped.toString());
            indices.put(entry.getKey(), index);
        }
        countIndices = Map.copyOf(indices);
        Create.LOGGER.info("Generated {} native stock icon and quantity composites", indices.size());
    }

    private static void shear(JsonObject model) {
        if (!model.has("type") || !model.get("type").getAsString().equals("minecraft:model")) return;
        JsonArray matrix = new JsonArray();
        for (float value : new float[] {1, 0, 0, 0, 0, 1, 0, 0, 1, -1, 1, .7f, 0, 0, 0, 1}) matrix.add(value);
        model.add("transformation", matrix);
    }

    private static int nextCountIndex(JsonElement element) {
        int index = 0;
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("type")
                    && object.get("type").getAsString().equals("minecraft:select")
                    && object.has("property")
                    && object.get("property").getAsString().equals("minecraft:custom_model_data")) {
                index = (object.has("index") ? object.get("index").getAsInt() : 0) + 1;
            }
            for (JsonElement child : object.asMap().values()) index = Math.max(index, nextCountIndex(child));
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) index = Math.max(index, nextCountIndex(child));
        }
        return index;
    }

    private static Identifier wrapper(Identifier original) {
        return CreateIds.id("ui/stock_entry/" + original.getNamespace() + "/" + original.getPath());
    }

    private static boolean nativeModel(JsonObject model) {
        String type = model.get("type").getAsString();
        return switch (type) {
            case "minecraft:empty", "minecraft:model", "minecraft:special", "minecraft:bundle/selected_item" -> true;
            case "minecraft:composite" -> {
                boolean valid = true;
                for (JsonElement child : model.getAsJsonArray("models")) valid &= nativeModel(child.getAsJsonObject());
                yield valid;
            }
            case "minecraft:condition" ->
                nativeModel(model.getAsJsonObject("on_true")) && nativeModel(model.getAsJsonObject("on_false"));
            case "minecraft:select", "minecraft:range_dispatch" -> {
                boolean valid = !model.has("fallback") || nativeModel(model.getAsJsonObject("fallback"));
                for (JsonElement child : model.getAsJsonArray(type.equals("minecraft:select") ? "cases" : "entries"))
                    valid &= nativeModel(child.getAsJsonObject().getAsJsonObject("model"));
                yield valid;
            }
            default -> false;
        };
    }

    static String formatCount(int count) {
        if (count <= 1) return "";
        if (count >= BigItemStack.INF) return "+";
        if (count >= 1_000_000) return count / 1_000_000 + "m";
        if (count >= 10_000) return count / 1_000 + "k";
        if (count >= 1_000) return (count * 10 / 1_000) / 10.0f + "k";
        return Integer.toString(count);
    }

    static String layout(String text) {
        if (text.equals("+")) return "plus";
        if (text.indexOf('.') >= 0) return "decimal";
        if (text.endsWith("k")) return "k" + (text.length() - 1);
        if (text.endsWith("m")) return "m" + (text.length() - 1);
        return "d" + text.length();
    }

    static ItemStack display(ItemStack original, int count) {
        return displayText(original, formatCount(count));
    }

    static ItemStack displayToolbox(ItemStack original, int count) {
        return displayText(original, Integer.toString(count));
    }

    private static ItemStack displayText(ItemStack original, String text) {
        ItemStack shown = original.copyWithCount(1);
        Identifier model =
                original.getOrDefault(DataComponents.ITEM_MODEL, BuiltInRegistries.ITEM.getKey(original.getItem()));
        Integer index = countIndices.get(model);
        if (index == null) {
            if (missingModels.add(model)) Create.LOGGER.warn("No native stock quantity wrapper exists for {}", model);
            return shown;
        }
        shown.set(DataComponents.ITEM_MODEL, wrapper(model));
        CustomModelData data = original.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
        String layout = layout(text);
        List<String> glyphs = new ArrayList<>(data.strings());
        while (glyphs.size() < index + 4) glyphs.add("");
        for (int i = 0; i < 4; i++) glyphs.set(index + i, "");
        for (int i = 0; i < Math.min(4, text.length()); i++) {
            String key = layout + "_" + i + "_" + glyph(text.charAt(i));
            Integer slot = countSlots.get(key);
            if (slot != null) glyphs.set(index + slot, key);
        }
        shown.set(
                DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(data.floats(), data.flags(), glyphs, data.colors()));
        return shown;
    }

    private static String glyph(char value) {
        return value == '.' ? "dot" : value == '+' ? "plus" : Character.toString(value);
    }
}
