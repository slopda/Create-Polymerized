package com.zurrtum.create.infrastructure.polymer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zurrtum.create.Create;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

final class PolymerItemModelAssets {
    private static final Map<Identifier, Identifier> VANILLA_ITEM_MODEL_ALIASES = Map.ofEntries(
            Map.entry(
                    PolymerBlockProjections.modelId("block/copper_backtank/item"),
                    PolymerBlockProjections.modelId("block/polymer/copper_backtank_item")),
            Map.entry(
                    PolymerBlockProjections.modelId("block/netherite_backtank/item"),
                    PolymerBlockProjections.modelId("block/polymer/netherite_backtank_item")),
            Map.entry(
                    PolymerBlockProjections.modelId("block/chain_conveyor/item"),
                    PolymerBlockProjections.modelId("block/polymer/chain_conveyor_item")),
            Map.entry(
                    PolymerBlockProjections.modelId("block/crushing_wheel/item"),
                    PolymerBlockProjections.modelId("block/polymer/crushing_wheel_item")),
            Map.entry(
                    PolymerBlockProjections.modelId("block/encased_cogwheel/item"),
                    PolymerBlockProjections.modelId("block/polymer/encased_cogwheel_item")),
            Map.entry(
                    PolymerBlockProjections.modelId("block/encased_cogwheel/item_brass"),
                    PolymerBlockProjections.modelId("block/polymer/encased_cogwheel_brass_item")),
            Map.entry(
                    PolymerBlockProjections.modelId("block/encased_shaft/item"),
                    PolymerBlockProjections.modelId("block/polymer/encased_shaft_item")),
            Map.entry(
                    PolymerBlockProjections.modelId("block/encased_shaft/item_brass"),
                    PolymerBlockProjections.modelId("block/polymer/encased_shaft_brass_item")),
            Map.entry(
                    PolymerBlockProjections.modelId("block/flywheel/item"),
                    PolymerBlockProjections.modelId("block/polymer/flywheel_item")),
            Map.entry(
                    PolymerBlockProjections.modelId("block/mechanical_roller/item"),
                    PolymerBlockProjections.modelId("block/polymer/mechanical_roller_item")));

    private PolymerItemModelAssets() {}

    static void rewriteCreateItemModels(ResourcePackBuilder builder) {
        int converted = 0;
        int unavailable = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null || !id.getNamespace().equals(Create.MOD_ID)) {
                continue;
            }

            String assetPath = "assets/create/items/" + id.getPath() + ".json";
            String source = builder.getStringData(assetPath);
            if (source == null) {
                continue;
            }

            try {
                JsonObject definition = JsonParser.parseString(source).getAsJsonObject();
                JsonObject model = definition.getAsJsonObject("model");
                if (model == null) {
                    continue;
                }

                String modelId = vanillaItemModelId(id, model);
                if (modelId == null) {
                    if (model.has("type") && model.get("type").isJsonPrimitive()) {
                        String type = model.get("type").getAsString();
                        if (type.startsWith("create:model/")) {
                            unavailable++;
                            builder.logError(
                                    "No vanilla item-model fallback is registered for " + id + " (" + type + ")",
                                    new IllegalStateException("Unsupported Create item model type " + type));
                        }
                    }
                    continue;
                }

                JsonObject vanillaModel = new JsonObject();
                vanillaModel.addProperty("type", "minecraft:model");
                vanillaModel.addProperty("model", modelId);
                if (id.getPath().equals("large_water_wheel")
                        || "create:model/oversized".equals(model.get("type").getAsString())) {
                    vanillaModel.addProperty("oversized_in_gui", true);
                }
                definition.add("model", vanillaModel);
                builder.addStringData(assetPath, definition.toString());
                converted++;
            } catch (RuntimeException exception) {
                unavailable++;
                builder.logError("Could not convert Create item model " + id + " for vanilla clients", exception);
            }
        }

        Create.LOGGER.info(
                "Converted {} Create item model definitions to vanilla format; {} could not be rewritten",
                converted,
                unavailable);
    }

    private static String vanillaItemModelId(Identifier itemId, JsonObject model) {
        switch (itemId.getPath()) {
            case "schematicannon":
                return "create:block/polymer/schematicannon_item";
            case "water_wheel":
                return "create:block/polymer/water_wheel_item";
            case "large_water_wheel":
                return "create:block/polymer/large_water_wheel_item";
            case "blaze_burner":
                return "create:block/polymer/blaze_burner_item";
            case "empty_blaze_burner":
                return "create:block/polymer/blaze_burner";
        }
        if (itemId.getPath().equals("clipboard")) {
            return "create:item/clipboard_0";
        }
        if (itemId.getPath().endsWith("_valve_handle")) {
            return "create:block/polymer/" + itemId.getPath();
        }

        if (!model.has("type")) {
            return null;
        }
        if (model.has("model")) {
            Identifier source = Identifier.parse(model.get("model").getAsString());
            Identifier alias = VANILLA_ITEM_MODEL_ALIASES.getOrDefault(
                    source, PolymerBlockProjections.VANILLA_MODEL_ALIASES.get(source));
            if (alias != null) return alias.toString();
        }
        String type = model.get("type").getAsString();
        if (!type.startsWith("create:model/")) {
            return null;
        }

        if (model.has("model")) {
            Identifier source = Identifier.parse(model.get("model").getAsString());
            return PolymerBlockProjections.VANILLA_MODEL_ALIASES
                    .getOrDefault(source, source)
                    .toString();
        }

        return switch (type) {
            case "create:model/extendo_grip" -> "create:item/polymer/extendo_grip";
            case "create:model/handheld_worldshaper" -> "create:item/handheld_worldshaper/item";
            case "create:model/linked_controller" -> "create:item/linked_controller/item";
            case "create:model/potato_cannon" -> "create:item/potato_cannon/item";
            case "create:model/wand_of_symmetry" -> "create:item/wand_of_symmetry/item";
            case "create:model/wrench" -> "create:item/wrench/item";
            default -> null;
        };
    }

    static void addDisplayItemModels(ResourcePackBuilder builder) {
        for (Identifier model : PolymerBlockProjections.DISPLAY_BLOCK_MODELS) {
            Identifier itemModel = PolymerBlockProjections.displayItemModelId(model);
            JsonObject inner = new JsonObject();
            inner.addProperty("type", "minecraft:model");
            inner.addProperty("model", model.toString());
            if (model.getPath().startsWith("block/controller_rail/")) {
                JsonObject tint = new JsonObject();
                tint.addProperty("type", "minecraft:custom_model_data");
                tint.addProperty("index", 0);
                tint.addProperty("default", 0xFF4B0000);
                JsonArray tints = new JsonArray();
                tints.add(tint);
                inner.add("tints", tints);
            }
            JsonObject definition = new JsonObject();
            definition.add("model", inner);
            builder.addStringData(
                    "assets/" + itemModel.getNamespace() + "/items/" + itemModel.getPath() + ".json",
                    definition.toString());
        }
    }
}
