package com.zurrtum.create.infrastructure.polymer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class PolymerTintedModels {
    private enum Tint {
        GRASS,
        FOLIAGE
    }

    private static final Map<Block, Tint> TINTED = Map.of(
            Blocks.GRASS_BLOCK, Tint.GRASS,
            Blocks.SHORT_GRASS, Tint.GRASS,
            Blocks.FERN, Tint.GRASS,
            Blocks.SUGAR_CANE, Tint.GRASS,
            Blocks.OAK_LEAVES, Tint.FOLIAGE,
            Blocks.JUNGLE_LEAVES, Tint.FOLIAGE,
            Blocks.ACACIA_LEAVES, Tint.FOLIAGE,
            Blocks.DARK_OAK_LEAVES, Tint.FOLIAGE,
            Blocks.MANGROVE_LEAVES, Tint.FOLIAGE);

    private PolymerTintedModels() {}

    public static boolean isTinted(BlockState state) {
        if (!TINTED.containsKey(state.getBlock())) {
            return false;
        }
        return !(state.hasProperty(BlockStateProperties.SNOWY) && state.getValue(BlockStateProperties.SNOWY));
    }

    public static Identifier itemModel(BlockState state) {
        if (state.is(Blocks.GRASS_BLOCK) && state.getValue(BlockStateProperties.SNOWY))
            return CreateIds.id("polymer_tint/minecraft/grass_block_snow");
        if (!isTinted(state)) {
            return null;
        }
        return itemModelId(state.getBlock());
    }

    public static ItemStack displayItem(BlockState state, Level level, BlockPos pos) {
        ItemStack item = new ItemStack(Items.PAPER);
        item.set(DataComponents.ITEM_MODEL, itemModelId(state.getBlock()));
        return tintItem(item, state, level, pos);
    }

    public static ItemStack tintItem(ItemStack item, BlockState state, Level level, BlockPos pos) {
        if (!isTinted(state)) return item;
        Biome biome = level.getBiome(pos).value();
        int color = TINTED.get(state.getBlock()) == Tint.GRASS ? grassColor(biome, pos) : foliageColor(biome);
        // Item tints are ARGB and biome colours RGB, so force full alpha (zero alpha draws black). Copycat part
        // stacks keep their part selector string.
        PolymerPartItems.setColors(item, List.of(0xFF000000 | color));
        return item;
    }

    // A dedicated server never loads the colormap textures, so Biome.getGrassColor/getFoliageColor return 0.
    // Rebuild the lookup from its three corner colours: the maps are indexed by (temperature, downfall *
    // temperature), a triangle with corners hot+wet, hot+dry and cold, and barycentric weights match it within a
    // few RGB steps.
    private static final int[] GRASS_CORNERS = {0x47CD33, 0xBFB755, 0x80B497};
    private static final int[] FOLIAGE_CORNERS = {0x1ABF00, 0xAEA42A, 0x60A17B};

    private static int grassColor(Biome biome, BlockPos pos) {
        var effects = biome.getSpecialEffects();
        int color = effects.grassColorOverride().orElseGet(() -> climateColor(biome, GRASS_CORNERS));
        return effects.grassColorModifier().modifyColor(pos.getX(), pos.getZ(), color);
    }

    private static int foliageColor(Biome biome) {
        return biome.getSpecialEffects().foliageColorOverride().orElseGet(() -> climateColor(biome, FOLIAGE_CORNERS));
    }

    private static int climateColor(Biome biome, int[] corners) {
        float temperature = Math.clamp(biome.getBaseTemperature(), 0, 1);
        float downfall = Math.clamp(biome.climateSettings.downfall(), 0, 1);
        float wet = temperature * downfall;
        float dry = temperature * (1 - downfall);
        float cold = 1 - temperature;
        int rgb = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            float channel = wet * ((corners[0] >> shift) & 0xFF)
                    + dry * ((corners[1] >> shift) & 0xFF)
                    + cold * ((corners[2] >> shift) & 0xFF);
            rgb |= Math.clamp(Math.round(channel), 0, 255) << shift;
        }
        return rgb;
    }

    public static void addItemModels(ResourcePackBuilder builder) {
        builder.addStringData(
                "assets/create/items/polymer_tint/minecraft/grass_block_snow.json",
                "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:block/grass_block_snow\"}}");
        for (Block block : TINTED.keySet()) {
            Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
            Identifier itemModel = itemModelId(block);

            JsonObject tint = new JsonObject();
            tint.addProperty("type", "minecraft:custom_model_data");
            tint.addProperty("index", 0);
            tint.addProperty("default", -1);
            JsonArray tints = new JsonArray();
            tints.add(tint);

            JsonObject model = new JsonObject();
            model.addProperty("type", "minecraft:model");
            model.addProperty("model", blockId.getNamespace() + ":block/" + blockId.getPath());
            model.add("tints", tints);
            JsonObject definition = new JsonObject();
            definition.add("model", model);
            builder.addStringData(
                    "assets/" + itemModel.getNamespace() + "/items/" + itemModel.getPath() + ".json",
                    definition.toString());
        }
    }

    private static Identifier itemModelId(Block block) {
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
        return CreateIds.id("polymer_tint/" + blockId.getNamespace() + "/" + blockId.getPath());
    }
}
