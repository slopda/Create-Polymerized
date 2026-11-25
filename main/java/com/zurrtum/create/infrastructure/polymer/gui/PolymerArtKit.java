package com.zurrtum.create.infrastructure.polymer.gui;

import static com.zurrtum.create.infrastructure.polymer.gui.PolymerGuiModels.caseOf;
import static com.zurrtum.create.infrastructure.polymer.gui.PolymerGuiModels.select;
import static dev.veeda.createpoly.assets.PackJson.array;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zurrtum.create.Create;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerGogglesOverlay;
import dev.veeda.createpoly.assets.CreateIds;
import dev.veeda.createpoly.assets.PackAssetId;
import dev.veeda.createpoly.assets.PackAssetWriter;
import dev.veeda.createpoly.assets.PackJson;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;

public final class PolymerArtKit {
    private final PackAssetWriter assets;
    private final String folder;
    private final int itemCx, itemCy;
    private final PolymerGuiModels.Folder art;
    public final JsonArray models = new JsonArray();
    private int index;
    private final List<Integer> palette = new ArrayList<>();
    private static volatile Map<String, List<Integer>> PALETTES = Map.of();
    private static volatile Map<Integer, BufferedImage> laneGlyphs = Map.of();

    public PolymerArtKit(ResourcePackBuilder builder, String folder, int itemCx, int itemCy) {
        this.assets = new PackAssetWriter(builder);
        this.folder = folder;
        this.itemCx = itemCx;
        this.itemCy = itemCy;
        art = new PolymerGuiModels.Folder(folder, itemCx, itemCy, false);
    }

    public int allocate(int count) {
        int first = index;
        index += count;
        return first;
    }

    int strings() {
        return index;
    }

    public void panel(String name, BufferedImage image, int x, int y, float z) throws IOException {
        int tileW = Math.min(image.getWidth(), 128), tileH = Math.min(image.getHeight(), 128);
        for (int ty = 0; ty * tileH < image.getHeight(); ty++)
            for (int tx = 0; tx * tileW < image.getWidth(); tx++) {
                int w = Math.min(tileW, image.getWidth() - tx * tileW),
                        h = Math.min(tileH, image.getHeight() - ty * tileH);
                String tile = name + "_" + tx + "_" + ty;
                png(tile, image.getSubimage(tx * tileW, ty * tileH, w, h));
                quad(tile, w, h, 4);
                models.add(placed(tile, x + tx * tileW + w / 2f, y + ty * tileH + h / 2f, z, 4));
            }
    }

    /** A selectable layer: case value -> image drawn with top-left at (x, y). Cases are written in key order so callers
     * may pass {@code Map.of}, whose iteration order differs per JVM run (the pack hash must not change on restart). */
    public void layer(int stringIndex, Map<String, BufferedImage> cases, int x, int y, float z) throws IOException {
        JsonArray list = new JsonArray();
        for (var entry : new TreeMap<>(cases).entrySet()) {
            String name = "layer_" + stringIndex + "_" + entry.getKey();
            BufferedImage image = entry.getValue();
            JsonArray tiles = new JsonArray();
            for (int ty = 0; ty < image.getHeight(); ty += 128)
                for (int tx = 0; tx < image.getWidth(); tx += 128) {
                    int w = Math.min(128, image.getWidth() - tx), h = Math.min(128, image.getHeight() - ty);
                    String tile = name + "_" + tx + "_" + ty;
                    png(tile, image.getSubimage(tx, ty, w, h));
                    quad(tile, w, h, 4);
                    tiles.add(placed(tile, x + tx + w / 2f, y + ty + h / 2f, z, 4));
                }
            JsonObject composite = new JsonObject();
            composite.addProperty("type", "minecraft:composite");
            composite.add("models", tiles);
            list.add(caseOf(entry.getKey(), composite));
        }
        models.add(select(stringIndex, list));
    }

    void bar(int stringIndex, String name, BufferedImage bar, int x, int y, float z) throws IOException {
        png(name, bar);
        JsonArray list = new JsonArray();
        int w = bar.getWidth(), h = bar.getHeight();
        for (int width = 1; width <= w; width++) {
            String model = name + "_" + width;
            float hw = width / 4f / 2, hh = h / 4f / 2;
            writeModel(
                    model,
                    name,
                    array(8 - hw, 8 - hh, 8),
                    array(8 + hw, 8 + hh, 8),
                    array(0, 0, 16f * width / w, 16),
                    4);
            list.add(caseOf(Integer.toString(width), placed(model, x + width / 2f, y + h / 2f, z, 4)));
        }
        models.add(select(stringIndex, list));
    }

    void compactLane(
            int firstFlag,
            int x,
            int y,
            int width,
            Map<Integer, BufferedImage> glyphs,
            int color,
            boolean shadow,
            float z) {
        laneGlyphs = Map.copyOf(glyphs);
        PolymerGuiPixelText.addAssets(assets);
        PolymerGuiPixelText.lane(
                models,
                firstFlag,
                x,
                y,
                width,
                tint(color),
                shadow ? tint(PolymerGuiPixelText.shadow(color)) : -1,
                z,
                itemCx,
                itemCy);
    }

    private int tint(int color) {
        int index = palette.indexOf(color);
        if (index < 0) {
            palette.add(color);
            index = palette.size() - 1;
        }
        return index;
    }

    public void finish(String item) {
        JsonObject composite = new JsonObject();
        composite.addProperty("type", "minecraft:composite");
        composite.add("models", models);
        JsonObject definition = new JsonObject();
        definition.addProperty("oversized_in_gui", true);
        definition.add("model", composite);
        assets.item(new PackAssetId(Create.MOD_ID, "ui/" + item), definition);
        Map<String, List<Integer>> palettes = new HashMap<>(PALETTES);
        palettes.put(item, List.copyOf(palette));
        PALETTES = Map.copyOf(palettes);
    }

    public static void empty(ResourcePackBuilder builder, String item) {
        new PackAssetWriter(builder)
                .item(
                        new PackAssetId(Create.MOD_ID, "ui/" + item),
                        JsonParser.parseString("{\"model\":{\"type\":\"minecraft:empty\"}}"));
    }

    public static ItemStack stack(String item, List<String> strings) {
        return stack(item, strings, null);
    }

    public static ItemStack stack(String item, List<String> strings, List<Boolean> flags) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.ITEM_MODEL, CreateIds.id("ui/" + item));
        if (strings != null || flags != null)
            stack.set(
                    DataComponents.CUSTOM_MODEL_DATA,
                    new CustomModelData(
                            List.of(),
                            flags == null ? List.of() : List.copyOf(flags),
                            strings == null ? List.of() : List.copyOf(strings),
                            flags == null ? List.of() : PALETTES.getOrDefault(item, List.of())));
        return stack;
    }

    static List<String> blank(int count) {
        return new ArrayList<>(Collections.nCopies(count, ""));
    }

    static List<Boolean> flags(int width) {
        return new ArrayList<>(Collections.nCopies(PolymerGuiPixelText.flags(width), false));
    }

    static int write(List<Boolean> flags, int first, int laneX, int width, int x, String text) {
        return PolymerGuiPixelText.write(flags, first, laneX, width, x, text, laneGlyphs, Map.of(), false);
    }

    public static int width(String text) {
        int width = 0;
        for (int cp : text.codePoints().toArray())
            width += PolymerGogglesOverlay.width(Character.toString(cp >= 32 && cp < 127 ? cp : '?'));
        return width;
    }

    public static BufferedImage image(ResourcePackBuilder builder, String path) throws IOException {
        byte[] data = builder.getDataOrSource(path);
        if (data == null) throw new IOException("Missing " + path);
        return PngCodec.decode(data);
    }

    public static BufferedImage copy(BufferedImage source) {
        BufferedImage image = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return image;
    }

    public static Map<Integer, BufferedImage> asciiGlyphs(ResourcePackBuilder builder) throws IOException {
        JsonArray chars = asciiRows(builder);
        BufferedImage ascii = image(builder, "assets/minecraft/textures/font/ascii.png");
        Map<Integer, BufferedImage> map = new HashMap<>();
        for (int y = 0; y < chars.size(); y++) {
            int[] row = chars.get(y).getAsString().codePoints().toArray();
            for (int x = 0; x < row.length; x++)
                if (row[x] != 0) map.put(row[x], ascii.getSubimage(x * 8, y * 8, 8, 8));
        }
        return map;
    }

    public static void text(
            BufferedImage target,
            Map<Integer, BufferedImage> glyphs,
            int x,
            int y,
            String value,
            int color,
            boolean shadow) {
        int shadowColor = ((color >> 16 & 0xFF) / 4) << 16 | ((color >> 8 & 0xFF) / 4) << 8 | (color & 0xFF) / 4;
        for (int pass = shadow ? 0 : 1; pass < 2; pass++) {
            int pen = x, offset = pass == 0 ? 1 : 0, rgb = 0xFF000000 | (pass == 0 ? shadowColor : color);
            for (int cp : value.codePoints().toArray()) {
                BufferedImage glyph = glyphs.get(cp >= 32 && cp < 127 ? cp : '?');
                if (glyph != null)
                    for (int gy = 0; gy < 8; gy++)
                        for (int gx = 0; gx < 8; gx++) {
                            int tx = pen + gx + offset, ty = y + gy + offset;
                            if ((glyph.getRGB(gx, gy) >>> 24) != 0
                                    && tx >= 0
                                    && ty >= 0
                                    && tx < target.getWidth()
                                    && ty < target.getHeight()) target.setRGB(tx, ty, rgb);
                        }
                pen += PolymerGogglesOverlay.width(Character.toString(cp >= 32 && cp < 127 ? cp : '?'));
            }
        }
    }

    private void png(String name, BufferedImage image) throws IOException {
        assets.itemPng(new PackAssetId(Create.MOD_ID, "item/ui/" + folder + "/" + name), image);
    }

    private void quad(String name, int w, int h, float scale) {
        float hw = w / scale / 2, hh = h / scale / 2;
        writeModel(name, name, array(8 - hw, 8 - hh, 8), array(8 + hw, 8 + hh, 8), array(0, 0, 16, 16), scale);
    }

    private void writeModel(String name, String texture, JsonArray from, JsonArray to, JsonArray uv, float scale) {
        JsonObject model = PolymerGuiModels.quad(
                new PackAssetId(Create.MOD_ID, "item/ui/" + folder + "/" + texture), from, to, uv, scale);
        assets.model(new PackAssetId(Create.MOD_ID, "item/ui/" + folder + "/" + name), model);
    }

    private JsonObject placed(String name, float x, float y, float z, float scale) {
        JsonObject model = art.model(name);
        model.add("transformation", art.centred(x, y, z, scale));
        return model;
    }

    public static void rawText(
            BufferedImage target,
            Map<Integer, BufferedImage> glyphs,
            int x,
            int y,
            String value,
            int color,
            boolean shadow) {
        int shadowColor = ((color >> 16 & 0xFF) / 4) << 16 | ((color >> 8 & 0xFF) / 4) << 8 | (color & 0xFF) / 4;
        for (int pass = shadow ? 0 : 1; pass < 2; pass++) {
            int pen = x, offset = pass == 0 ? 1 : 0, rgb = 0xFF000000 | (pass == 0 ? shadowColor : color);
            for (int cp : value.codePoints().toArray()) {
                BufferedImage glyph = glyphs.get(cp);
                if (glyph != null)
                    for (int gy = 0; gy < 8; gy++)
                        for (int gx = 0; gx < 8; gx++) {
                            int tx = pen + gx + offset, ty = y + gy + offset;
                            if ((glyph.getRGB(gx, gy) >>> 24) != 0
                                    && tx >= 0
                                    && ty >= 0
                                    && tx < target.getWidth()
                                    && ty < target.getHeight()) target.setRGB(tx, ty, rgb);
                        }
                pen += PolymerGogglesOverlay.width(Character.toString(cp));
            }
        }
    }

    public static Component space(FontDescription font, int pixels) {
        StringBuilder text = new StringBuilder();
        int remaining = Math.abs(pixels);
        for (int bit = 9; bit >= 0; bit--)
            while (remaining >= 1 << bit) {
                text.append((char) ((pixels < 0 ? 0xE010 : 0xE000) + bit + 1));
                remaining -= 1 << bit;
            }
        return Component.literal(text.toString()).withStyle(Style.EMPTY.withFont(font));
    }

    public static JsonArray asciiRows(ResourcePackBuilder builder) throws IOException {
        byte[] defaults = builder.getDataOrSource("assets/minecraft/font/include/default.json");
        if (defaults == null) throw new IOException("Vanilla default font unavailable");
        for (JsonElement element : PackJson.object(defaults).getAsJsonArray("providers")) {
            JsonObject object = element.getAsJsonObject();
            if ("bitmap".equals(object.get("type").getAsString())
                    && object.get("file").getAsString().endsWith("font/ascii.png"))
                return object.getAsJsonArray("chars");
        }
        throw new IOException("No vanilla ascii provider");
    }
}
