package com.zurrtum.create.infrastructure.polymer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zurrtum.create.Create;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

public final class PolymerPartItems {
    private PolymerPartItems() {}

    static final String PREFIX = "polymer_part/";
    static final int BUCKETS = 32;
    static final int SLOT = 0;

    static boolean isPart(Identifier itemModel) {
        return itemModel.getNamespace().equals(Create.MOD_ID)
                && itemModel.getPath().startsWith(PREFIX)
                && !itemModel.getPath().startsWith(PREFIX + "_b");
    }

    private static String bucket(String key) {
        return PREFIX + "_b" + Math.floorMod(key.hashCode(), BUCKETS);
    }

    public static void setItemModel(ItemStack stack, Identifier itemModel) {
        if (!isPart(itemModel)) {
            stack.set(DataComponents.ITEM_MODEL, itemModel);
            return;
        }
        String key = itemModel.getPath().substring(PREFIX.length());
        stack.set(DataComponents.ITEM_MODEL, CreateIds.id(bucket(key)));
        CustomModelData data = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
        List<String> strings = new ArrayList<>(data.strings());
        while (strings.size() <= SLOT) strings.add("");
        strings.set(SLOT, key);
        stack.set(
                DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(data.floats(), data.flags(), List.copyOf(strings), data.colors()));
    }

    public static void setColors(ItemStack stack, List<Integer> colors) {
        CustomModelData data = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
        stack.set(
                DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(data.floats(), data.flags(), data.strings(), List.copyOf(colors)));
    }

    static void fold(ResourcePackBuilder builder) {
        String folder = "assets/" + Create.MOD_ID + "/items/" + PREFIX;
        Map<String, JsonArray> buckets = new TreeMap<>();
        Set<String> folded = new HashSet<>();
        TreeMap<String, String> definitions = new TreeMap<>();
        builder.forEachResource((path, resource) -> {
            if (path.startsWith(folder) && path.endsWith(".json")) definitions.put(path, null);
        });
        for (String path : definitions.keySet()) {
            String key = path.substring(folder.length(), path.length() - ".json".length());
            JsonObject definition =
                    JsonParser.parseString(builder.getStringData(path)).getAsJsonObject();
            JsonElement model = definition.get("model");
            if (definition.size() != 1 || model == null || !plain(model))
                throw new IllegalStateException("Part item definition is not one plain model: " + path);
            JsonObject when = new JsonObject();
            when.addProperty("when", key);
            when.add("model", model);
            buckets.computeIfAbsent(bucket(key), k -> new JsonArray()).add(when);
            folded.add(path);
        }
        for (var bucket : buckets.entrySet()) {
            JsonObject select = new JsonObject();
            select.addProperty("type", "minecraft:select");
            select.addProperty("property", "minecraft:custom_model_data");
            select.addProperty("index", SLOT);
            select.add("cases", bucket.getValue());
            JsonObject empty = new JsonObject();
            empty.addProperty("type", "minecraft:empty");
            select.add("fallback", empty);
            JsonObject item = new JsonObject();
            item.add("model", select);
            builder.addStringData("assets/" + Create.MOD_ID + "/items/" + bucket.getKey() + ".json", item.toString());
        }
        builder.addResourceConverter((path, resource) -> folded.contains(path) ? null : resource);
        Create.LOGGER.info("Folded {} part item definitions into {} bucket definitions", folded.size(), buckets.size());
    }

    private static boolean plain(JsonElement model) {
        if (!model.isJsonObject()) return false;
        JsonObject object = model.getAsJsonObject();
        return object.has("type")
                && object.get("type").getAsString().replace("minecraft:", "").equals("model");
    }
}
