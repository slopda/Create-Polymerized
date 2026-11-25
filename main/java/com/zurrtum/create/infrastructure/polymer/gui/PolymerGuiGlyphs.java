package com.zurrtum.create.infrastructure.polymer.gui;

import static dev.veeda.createpoly.assets.PackJson.array;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.veeda.createpoly.assets.PackAssetId;
import dev.veeda.createpoly.assets.PackAssetWriter;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

public final class PolymerGuiGlyphs {
    private PolymerGuiGlyphs() {}

    static Map<Integer, String> add(
            ResourcePackBuilder builder, Map<Integer, BufferedImage> glyphs, int color, boolean shadow)
            throws IOException {
        return add(builder, glyphs, color, shadow, false);
    }

    static Map<Integer, String> add(
            ResourcePackBuilder builder,
            Map<Integer, BufferedImage> glyphs,
            int color,
            boolean shadow,
            boolean comparisonSymbols)
            throws IOException {
        return add(new PackAssetWriter(builder), glyphs, color, shadow, comparisonSymbols);
    }

    static Map<Integer, String> add(
            PackAssetWriter assets, Map<Integer, BufferedImage> glyphs, int color, boolean shadow) throws IOException {
        return add(assets, glyphs, color, shadow, false);
    }

    private static Map<Integer, String> add(
            PackAssetWriter assets,
            Map<Integer, BufferedImage> glyphs,
            int color,
            boolean shadow,
            boolean comparisonSymbols)
            throws IOException {
        String palette = switch (color) {
            case 0xFFFFFF -> "w";
            case 0xD3D3D3 -> "g";
            case 0xCCDDFF -> "b";
            case 0xDDEEFF -> "c";
            default -> "x" + Integer.toHexString(color);
        };
        Map<Integer, String> models = new LinkedHashMap<>();
        for (var entry : new TreeMap<>(glyphs).entrySet()) {
            int cp = entry.getKey();
            if (!(cp >= 33 && cp < 127 || comparisonSymbols && (cp == 0x2264 || cp == 0x2265))) continue;
            String path = "g/" + palette + (shadow ? "s/" : "n/") + Integer.toString(cp, 36);
            BufferedImage glyph = entry.getValue(), out = new BufferedImage(9, 9, BufferedImage.TYPE_INT_ARGB);
            int shade =
                    0xFF000000 | ((color >> 16 & 255) / 4) << 16 | ((color >> 8 & 255) / 4) << 8 | (color & 255) / 4;
            if (shadow)
                for (int y = 0; y < 8; y++)
                    for (int x = 0; x < 8; x++) if ((glyph.getRGB(x, y) >>> 24) != 0) out.setRGB(x + 1, y + 1, shade);
            for (int y = 0; y < 8; y++)
                for (int x = 0; x < 8; x++) if ((glyph.getRGB(x, y) >>> 24) != 0) out.setRGB(x, y, 0xFF000000 | color);
            PackAssetId texture = new PackAssetId("create", "item/" + path);
            assets.itemPng(texture, out);
            JsonObject model = PolymerGuiModels.quad(texture, array(8, -1, 8), array(17, 8, 8), array(0, 0, 16, 16), 1);
            model.getAsJsonObject("textures").addProperty("particle", "#panel");
            assets.model(new PackAssetId("create", path), model);
            models.put(cp, "create:" + path);
        }
        return Map.copyOf(models);
    }

    public static String spaces(int base, float delta) {
        return spaces(base, delta, 0f);
    }

    public static String spaces(int base, float delta, float tolerance) {
        StringBuilder result = new StringBuilder();
        float remaining = Math.abs(delta);
        for (int step = 9; step >= -1; step--) {
            float size = (float) Math.pow(2, step);
            while (remaining >= size - tolerance) {
                result.appendCodePoint(base + (delta < 0 ? 0x20 : 0) + step + 1);
                remaining -= size;
            }
        }
        return result.toString();
    }

    public static JsonObject spaceProvider(int base) {
        JsonObject advances = new JsonObject();
        for (int step = -1; step <= 9; step++) {
            float size = (float) Math.pow(2, step);
            advances.addProperty(Character.toString(base + step + 1), size);
            advances.addProperty(Character.toString(base + 0x20 + step + 1), -size);
        }
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "space");
        provider.add("advances", advances);
        return provider;
    }

    public static JsonObject bitmapProvider(String file, Number height, Number ascent) {
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "bitmap");
        provider.addProperty("file", file);
        provider.addProperty("height", height);
        provider.addProperty("ascent", ascent);
        return provider;
    }

    public static JsonObject bitmap(String file, int height, int ascent, @Nullable String chars) {
        JsonObject provider = bitmapProvider(file, height, ascent);
        if (chars != null) {
            JsonArray list = new JsonArray();
            list.add(chars);
            provider.add("chars", list);
        }
        return provider;
    }

    public static JsonObject bitmapRows(String file, int height, int ascent, List<String> chars) {
        if (ascent > height) throw new IllegalStateException("Ascent " + ascent + " exceeds height " + height);
        JsonObject provider = bitmapProvider(file, height, ascent);
        JsonArray rows = new JsonArray();
        chars.forEach(rows::add);
        provider.add("chars", rows);
        return provider;
    }

    public static void writeFont(ResourcePackBuilder builder, String name, JsonArray providers) {
        JsonObject root = new JsonObject();
        root.add("providers", providers);
        builder.addStringData("assets/create/font/" + name + ".json", root.toString());
    }

    static JsonObject model(String id) {
        JsonObject model = new JsonObject();
        model.addProperty("type", "model");
        model.addProperty("model", id);
        return model;
    }

    static void fontPng(ResourcePackBuilder builder, String name, BufferedImage image) throws IOException {
        builder.addData("assets/create/textures/font/" + name + ".png", PngCodec.encode(image));
    }
}
