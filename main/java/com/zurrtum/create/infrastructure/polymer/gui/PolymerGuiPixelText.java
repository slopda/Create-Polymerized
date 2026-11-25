package com.zurrtum.create.infrastructure.polymer.gui;

import static dev.veeda.createpoly.assets.PackJson.array;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerGogglesOverlay;
import dev.veeda.createpoly.assets.PackAssetId;
import dev.veeda.createpoly.assets.PackAssetWriter;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class PolymerGuiPixelText {
    static final int ROWS = 8;

    private static final String PIXEL = "create:item/ui/text_pixel";
    private static final float SHADOW_DEPTH = .002f;

    private PolymerGuiPixelText() {}

    static void addAssets(PackAssetWriter assets) {
        BufferedImage white = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) white.setRGB(x, y, 0xFFFFFFFF);
        PackAssetId texture = new PackAssetId("create", "item/ui/text_pixel");
        try {
            assets.itemPng(texture, white);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write the GUI text pixel", exception);
        }
        JsonObject model = PolymerGuiModels.quad(texture, array(8, 7, 8), array(9, 8, 8), array(0, 0, 16, 16), 1);
        JsonObject face = model.getAsJsonArray("elements")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("faces")
                .getAsJsonObject("south");
        face.addProperty("tintindex", 0);
        model.getAsJsonObject("textures").addProperty("particle", "#panel");
        assets.model(new PackAssetId("create", "item/ui/text_pixel"), model);
    }

    static int flags(int width) {
        return width * ROWS;
    }

    static void lane(
            JsonArray models,
            int firstFlag,
            int x,
            int y,
            int width,
            int tint,
            int shadowTint,
            float z,
            int itemCx,
            int itemCy) {
        JsonObject text = pixel(tint), shadow = shadowTint < 0 ? null : pixel(shadowTint);
        for (int column = 0; column < width; column++)
            for (int row = 0; row < ROWS; row++) {
                int flag = firstFlag + column * ROWS + row;
                models.add(placed(condition(flag, text), x + column, y + row, z, itemCx, itemCy));
                if (shadow != null && column + 1 < width)
                    models.add(placed(
                            condition(flag, shadow), x + column + 1, y + row + 1, z - SHADOW_DEPTH, itemCx, itemCy));
            }
    }

    static int write(
            List<Boolean> flags,
            int firstFlag,
            int laneX,
            int width,
            int x,
            String text,
            Map<Integer, BufferedImage> glyphs,
            Map<Integer, Integer> extra,
            boolean ownAdvance) {
        for (int cp : text.codePoints().toArray()) {
            int safe = cp >= 33 && cp < 127 || extra.containsKey(cp) ? cp : cp == ' ' ? ' ' : '?';
            Integer fixed = extra.get(cp);
            int advance =
                    fixed != null ? fixed : PolymerGogglesOverlay.width(Character.toString(ownAdvance ? cp : safe));
            BufferedImage glyph = glyphs.get(safe);
            if (safe != ' ' && glyph != null && x >= laneX && x + advance <= laneX + width) {
                for (int gx = 0; gx < 8; gx++)
                    for (int gy = 0; gy < ROWS; gy++)
                        if ((glyph.getRGB(gx, gy) >>> 24) != 0 && x + gx - laneX < width)
                            flags.set(firstFlag + (x + gx - laneX) * ROWS + gy, true);
            }
            x += advance;
        }
        return x;
    }

    static int shadow(int color) {
        return ((color >> 16 & 0xFF) / 4) << 16 | ((color >> 8 & 0xFF) / 4) << 8 | (color & 0xFF) / 4;
    }

    private static JsonObject pixel(int tint) {
        JsonObject model = new JsonObject();
        model.addProperty("type", "minecraft:model");
        model.addProperty("model", PIXEL);
        JsonObject source = new JsonObject();
        source.addProperty("type", "minecraft:custom_model_data");
        source.addProperty("index", tint);
        source.addProperty("default", 0xFFFFFF);
        JsonArray tints = new JsonArray();
        tints.add(source);
        model.add("tints", tints);
        return model;
    }

    private static JsonObject condition(int flag, JsonObject model) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "minecraft:condition");
        o.addProperty("property", "minecraft:custom_model_data");
        o.addProperty("index", flag);
        o.add("on_true", model.deepCopy());
        JsonObject empty = new JsonObject();
        empty.addProperty("type", "minecraft:empty");
        o.add("on_false", empty);
        return o;
    }

    private static JsonObject placed(JsonObject condition, int x, int y, float z, int itemCx, int itemCy) {
        JsonObject inner = condition.getAsJsonObject("on_true");
        JsonArray matrix = new JsonArray();
        for (float value :
                new float[] {1, 0, 0, (x - itemCx) / 16f, 0, 1, 0, (itemCy - y) / 16f, 0, 0, 1, z, 0, 0, 0, 1})
            if (value == (int) value) matrix.add((int) value);
            else matrix.add(value);
        inner.add("transformation", matrix);
        return condition;
    }

    record Lane(int x, int y, int width, int tint, int shadowTint) {}

    record Layout(List<Lane> lanes) {
        Layout(Lane... lanes) {
            this(List.of(lanes));
        }

        int first(int lane) {
            int first = 0;
            for (int i = 0; i < lane; i++) first += flags(lanes.get(i).width());
            return first;
        }

        int flagCount() {
            return first(lanes.size());
        }

        List<Boolean> blank() {
            return new ArrayList<>(Collections.nCopies(flagCount(), false));
        }

        int tints() {
            int tints = 0;
            for (Lane lane : lanes) tints = Math.max(tints, Math.max(lane.tint(), lane.shadowTint()) + 1);
            return tints;
        }

        void models(JsonArray models, int dx, int dy, float z, int itemCx, int itemCy) {
            for (int i = 0; i < lanes.size(); i++) {
                Lane lane = lanes.get(i);
                lane(
                        models,
                        first(i),
                        dx + lane.x(),
                        dy + lane.y(),
                        lane.width(),
                        lane.tint(),
                        lane.shadowTint(),
                        z,
                        itemCx,
                        itemCy);
            }
        }

        void write(
                List<Boolean> pixels,
                int lane,
                int x,
                String text,
                Map<Integer, BufferedImage> glyphs,
                Map<Integer, Integer> extra) {
            Lane at = lanes.get(lane);
            PolymerGuiPixelText.write(pixels, first(lane), at.x(), at.width(), x, text, glyphs, extra, true);
        }

        void color(List<Integer> colors, int lane, int color) {
            Lane at = lanes.get(lane);
            colors.set(at.tint(), color);
            colors.set(at.shadowTint(), shadow(color));
        }
    }
}
