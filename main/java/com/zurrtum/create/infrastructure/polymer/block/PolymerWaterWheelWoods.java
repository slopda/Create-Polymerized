package com.zurrtum.create.infrastructure.polymer.block;

import static com.zurrtum.create.infrastructure.polymer.gui.PolymerArtKit.image;

import com.google.gson.JsonObject;
import com.zurrtum.create.Create;
import dev.veeda.createpoly.assets.PackJson;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class PolymerWaterWheelWoods {
    private PolymerWaterWheelWoods() {}

    static final String[] MODELS = {"water_wheel_rotor", "large_water_wheel", "large_water_wheel_extension"};
    private static final String[] WOODS = {
        "oak", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak", "bamboo", "crimson", "warped"
    };
    private static final Set<String> AVAILABLE = new HashSet<>();

    private static final Map<String, Identifier> MODEL_IDS = new HashMap<>();

    static {
        for (String model : MODELS)
            for (String wood : WOODS)
                MODEL_IDS.put(model + "_" + wood, PolymerBlockParts.model("polymer/water_wheel/" + model + "_" + wood));
    }

    static void register() {}

    static @Nullable Identifier model(String model, BlockState material) {
        String wood = wood(material);
        if (wood == null || !AVAILABLE.contains(wood)) return null;
        return MODEL_IDS.get(model + "_" + wood);
    }

    private static @Nullable String wood(BlockState material) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(material.getBlock());
        if (!id.getNamespace().equals("minecraft") || !id.getPath().endsWith("_planks")) return null;
        String wood = id.getPath().substring(0, id.getPath().length() - "_planks".length());
        return wood.equals("spruce") ? null : wood;
    }

    private static String log(String wood) {
        return switch (wood) {
            case "crimson", "warped" -> wood + "_stem";
            case "bamboo" -> "bamboo_block";
            default -> wood + "_log";
        };
    }

    public static void addAssets(ResourcePackBuilder builder) {
        try {
            Map<String, JsonObject> models = new LinkedHashMap<>();
            for (String model : MODELS) {
                byte[] data = builder.getDataOrSource("assets/create/models/block/polymer/" + model + ".json");
                if (data == null) throw new IOException("Missing wheel model " + model);
                models.put(model, PackJson.object(data));
            }
            Map<String, BufferedImage> faces = new HashMap<>();
            for (JsonObject model : models.values())
                for (var entry : model.getAsJsonObject("textures").entrySet()) {
                    String texture = entry.getValue().getAsString();
                    if (!texture.contains("source_faces/") || faces.containsKey(texture)) continue;
                    faces.put(
                            texture,
                            image(
                                    builder,
                                    "assets/create/textures/"
                                            + Identifier.parse(texture).getPath() + ".png"));
                }
            for (String wood : WOODS) {
                Map<Integer, Integer> palette;
                try {
                    palette = palette(builder, wood);
                } catch (IOException missing) {
                    Create.LOGGER.warn("Water wheel wood {} skipped: {}", wood, missing.getMessage());
                    continue;
                }
                Map<String, String> renamed = new HashMap<>();
                for (var face : faces.entrySet()) {
                    String path = Identifier.parse(face.getKey()).getPath() + "_" + wood;
                    png(builder, "assets/create/textures/" + path + ".png", recolor(face.getValue(), palette));
                    renamed.put(face.getKey(), "create:" + path);
                }
                for (var model : models.entrySet()) {
                    JsonObject copy = model.getValue().deepCopy();
                    JsonObject textures = copy.getAsJsonObject("textures");
                    for (String key : new ArrayList<>(textures.keySet())) {
                        String replacement = renamed.get(textures.get(key).getAsString());
                        if (replacement != null) textures.addProperty(key, replacement);
                    }
                    textures.addProperty("particle", "minecraft:block/" + wood + "_planks");
                    String name = model.getKey() + "_" + wood;
                    builder.addStringData(
                            "assets/create/models/block/polymer/water_wheel/" + name + ".json", copy.toString());
                }
                AVAILABLE.add(wood);
            }
        } catch (IOException | RuntimeException e) {
            Create.LOGGER.error("Failed to generate water wheel wood variants", e);
        }
    }

    private static Map<Integer, Integer> palette(ResourcePackBuilder builder, String wood) throws IOException {
        String[][] pairs = {
            {"spruce_planks", wood + "_planks"},
            {"spruce_log", log(wood)},
            {"stripped_spruce_log", "stripped_" + log(wood)},
            {"spruce_log_top", log(wood) + "_top"},
        };
        Map<Integer, Map<Integer, Integer>> votes = new LinkedHashMap<>();
        for (String[] pair : pairs) {
            BufferedImage source = image(builder, "assets/minecraft/textures/block/" + pair[0] + ".png");
            BufferedImage target = image(builder, "assets/minecraft/textures/block/" + pair[1] + ".png");
            Map<Integer, Map<Integer, Integer>> local = new HashMap<>();
            for (int y = 0; y < 16; y++)
                for (int x = 0; x < 16; x++) {
                    int from = source.getRGB(x, y), to = target.getRGB(x % target.getWidth(), y % target.getHeight());
                    if (from >>> 24 == 0) continue;
                    local.computeIfAbsent(from, k -> new HashMap<>()).merge(to, 1, Integer::sum);
                }
            for (var entry : local.entrySet()) votes.putIfAbsent(entry.getKey(), entry.getValue());
        }
        Map<Integer, Integer> palette = new HashMap<>();
        for (var entry : votes.entrySet())
            palette.put(
                    entry.getKey(),
                    Collections.max(entry.getValue().entrySet(), Map.Entry.comparingByValue())
                            .getKey());
        return palette;
    }

    private static BufferedImage recolor(BufferedImage source, Map<Integer, Integer> palette) {
        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++)
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                out.setRGB(x, y, palette.getOrDefault(argb, argb));
            }
        return out;
    }

    private static void png(ResourcePackBuilder builder, String path, BufferedImage image) throws IOException {
        builder.addData(path, PngCodec.encode(image));
    }
}
