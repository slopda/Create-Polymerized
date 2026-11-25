package com.zurrtum.create.infrastructure.polymer;

import com.google.gson.JsonParser;
import com.zurrtum.create.Create;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

final class PolymerBedrockItemIcons {
    private static Map<String, Integer> models;
    private static boolean glintEnabled;

    private PolymerBedrockItemIcons() {}

    static int icon(ItemStack stack, ItemDisplayContext context) {
        if (context != ItemDisplayContext.FIXED || stack.isEmpty()) return 0;
        var model = stack.get(DataComponents.ITEM_MODEL);
        if (model == null) model = BuiltInRegistries.ITEM.getKey(stack.getItem());
        int texture = table().getOrDefault(model.toString(), 0);
        if (stack.hasFoil() && !glintEnabled) return 0;
        return PolymerBedrockItemPose.visualIcon(texture, stack.hasFoil());
    }

    private static Map<String, Integer> table() {
        if (models != null) return models;
        Map<String, Integer> loaded = new HashMap<>();
        Path file = Path.of("polymerpatch", "item_icons.json");
        if (Files.isRegularFile(file))
            try {
                var root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                int schema = root.get("schema_version").getAsInt();
                if (schema != 2 && schema != 3) throw new IllegalArgumentException("Unknown icon table schema");
                boolean foil = schema == 3;
                if (foil
                        && (!root.has("foil_bit")
                                || root.get("foil_bit").getAsInt() != PolymerBedrockItemPose.GLINT_BIT))
                    throw new IllegalArgumentException("Unknown icon glint encoding");
                int count = root.getAsJsonArray("textures").size();
                if (count < 1 || count > PolymerBedrockItemPose.MAX_TEXTURES)
                    throw new IllegalArgumentException("Invalid icon texture count");
                for (var entry : root.getAsJsonObject("fixed_models").entrySet()) {
                    int index = entry.getValue().getAsInt();
                    if (index < 0 || index >= count) throw new IllegalArgumentException("Invalid icon index");
                    loaded.put(entry.getKey(), index + 1);
                }
                glintEnabled = foil;
            } catch (Exception error) {
                loaded.clear();
                glintEnabled = false;
                Create.LOGGER.error("Bedrock item icons: cannot read {}; retaining equipment renderer", file, error);
            }
        models = Map.copyOf(loaded);
        Create.LOGGER.info("Bedrock item icons: {} static FIXED models", models.size());
        return models;
    }
}
