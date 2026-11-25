package com.zurrtum.create.infrastructure.polymer.gui;

import static com.zurrtum.create.infrastructure.polymer.gui.PolymerGuiModels.caseOf;
import static dev.veeda.createpoly.assets.PackJson.array;

import com.google.gson.*;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerGogglesOverlay;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;

final class PolymerStationGuiForeground {
    private static final PolymerGuiModels.Folder ASSETS = new PolymerGuiModels.Folder("station", 100, 26, false);

    private record Lane(int x, int y, int width) {}

    private record Label(int x, int y, String value, int color) {}

    private record Icon(int x, int type) {}

    private static final Lane[] NORMAL = {new Lane(14, 4, 172), new Lane(25, 47, 150), new Lane(39, 108, 58)};
    private static final Lane[] ASSEMBLY = assemblyLanes();
    private static final int[][] NORMAL_COLORS = {{0x592424}, {0x7A7A7A, 0xC6C6C6}, {0xEEEEEE}};
    private static final int[][] ASSEMBLY_COLORS = {
        {0x7A7A7A, 0x775B5B},
        {0x7A7A7A, 0x775B5B},
        {0x775B5B},
        {0x775B5B},
        {0x775B5B},
        {0x775B5B},
        {0x775B5B},
        {0x775B5B}
    };
    private static final int ROWS = 8;
    private static final int[] ICON_WIDTHS = {19, 7, 13, 19, 19};

    private static volatile Map<Integer, BufferedImage> glyphs = Map.of();
    private static volatile Map<Integer, Integer> iconAdvances = Map.of();
    private static final JsonObject EMPTY =
            JsonParser.parseString("{\"type\":\"minecraft:empty\"}").getAsJsonObject();

    private static Lane[] assemblyLanes() {
        Lane[] lanes = new Lane[8];
        lanes[0] = new Lane(25, 37, 150);
        for (int i = 1; i < 8; i++) lanes[i] = new Lane(30, 57 + (i - 1) * 9, 134);
        return lanes;
    }

    static ItemStack item(Component title, boolean assembly) {
        List<Label> labels = new ArrayList<>();
        List<Icon> icons = new ArrayList<>();
        int[] cursor = {0}, mask = {0};
        title.visit(
                (style, text) -> {
                    if (!(style.getFont() instanceof FontDescription.Resource resource)) return Optional.empty();
                    String font = resource.id().getPath();
                    if (font.equals("station_sgui")) {
                        for (int cp : text.codePoints().toArray()) {
                            if (cp >= 0xE000 && cp < 0xE010) {
                                mask[0] = cp - 0xE000;
                                cursor[0] += 201;
                            } else if (cp >= 0xF000 && cp <= 0xF00A) cursor[0] += 1 << (cp - 0xF000);
                            else if (cp >= 0xF020 && cp <= 0xF02A) cursor[0] -= 1 << (cp - 0xF020);
                        }
                    } else if (font.startsWith("station_text_")) {
                        labels.add(new Label(
                                cursor[0] + 20,
                                Integer.parseInt(font.substring(13)),
                                text,
                                style.getColor() == null
                                        ? 0xFFFFFF
                                        : style.getColor().getValue()));
                        cursor[0] += PolymerGogglesOverlay.width(text);
                    } else if (font.equals("station_train_icons")) {
                        for (int cp : text.codePoints().toArray()) {
                            int type = cp - 0xE100;
                            icons.add(new Icon(cursor[0] + 20, type));
                            cursor[0] += iconAdvances.getOrDefault(type, 20);
                        }
                    }
                    return Optional.empty();
                },
                Style.EMPTY);
        Lane[] lanes = assembly ? ASSEMBLY : NORMAL;
        List<String> strings = new ArrayList<>(Collections.nCopies(2 + (assembly ? 0 : 400), ""));
        strings.set(0, assembly ? "assembly" : "station");
        strings.set(1, Integer.toString(mask[0]));
        List<Boolean> flags = new ArrayList<>(
                Collections.nCopies(Arrays.stream(lanes).mapToInt(Lane::width).sum() * ROWS, false));
        List<Integer> colors = new ArrayList<>(Collections.nCopies(lanes.length, 0x7A7A7A));
        int errorLine = 1;
        for (Label label : labels) {
            int lane;
            if (!assembly) lane = label.y() == 4 ? 0 : label.y() == 47 ? 1 : 2;
            else if (label.y() == 47) lane = 0;
            else {
                if ((mask[0] & 1) == 0 || label.y() == 4 || label.value().equals("Resolve this and retry")) continue;
                if (errorLine >= lanes.length) continue;
                lane = errorLine++;
            }
            Lane area = lanes[lane];
            int offset = 0;
            for (int i = 0; i < lane; i++) offset += lanes[i].width() * ROWS;
            int[] allowed = (assembly ? ASSEMBLY_COLORS : NORMAL_COLORS)[lane];
            if (Arrays.stream(allowed).noneMatch(value -> value == label.color()))
                throw new IllegalStateException("Unexpected station text colour " + Integer.toHexString(label.color()));
            colors.set(lane, label.color());
            int x = label.x();
            for (int cp : label.value().codePoints().toArray()) {
                int safe = cp >= 32 && cp < 127 ? cp : '?';
                BufferedImage glyph = glyphs.get(safe);
                if (safe != ' '
                        && glyph != null
                        && drawable(assembly, lane, safe)
                        && x >= area.x()
                        && x + glyphWidth(safe) <= area.x() + area.width())
                    for (int gx = 0; gx < 8; gx++)
                        for (int gy = 0; gy < ROWS; gy++)
                            if ((glyph.getRGB(gx, gy) >>> 24) != 0)
                                flags.set(offset + (x + gx - area.x()) * ROWS + gy, true);
                x += PolymerGogglesOverlay.width(Character.toString(cp));
            }
        }
        if (!assembly)
            for (Icon icon : icons)
                if (iconFits(icon.x(), icon.type())) strings.set(2 + icon.x(), Integer.toString(icon.type()));
        if (!assembly)
            for (Label label : labels)
                if (label.y() == 4) {
                    int x = label.x() + PolymerGogglesOverlay.width(label.value()) + 5;
                    if (x >= 0 && x + 13 <= 200) strings.set(2 + 200 + x, "edit");
                }
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.ITEM_MODEL, Identifier.parse("create:ui/station_foreground"));
        stack.set(
                DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.copyOf(flags), List.copyOf(strings), List.copyOf(colors)));
        return stack;
    }

    private static boolean drawable(boolean assembly, int lane, int cp) {
        return cp > 32 && cp < 127 && (!assembly || lane != 0 || "0123456789No BogeysAssemblyFailed".indexOf(cp) >= 0);
    }

    private static boolean iconFits(int x, int type) {
        return type >= 0 && type < 15 && x >= 4 && x + ICON_WIDTHS[type % 5] <= 166;
    }

    private static int glyphWidth(int cp) {
        BufferedImage glyph = glyphs.get(cp);
        if (glyph == null) return 6;
        int width = 0;
        for (int x = 0; x < 8; x++)
            for (int y = 0; y < 8; y++) if ((glyph.getRGB(x, y) >>> 24) != 0) width = Math.max(width, x + 1);
        return width;
    }

    static void initialize(ResourcePackBuilder builder) throws IOException {
        JsonArray chars = PolymerArtKit.asciiRows(builder);
        BufferedImage ascii = PolymerArtKit.image(builder, "assets/minecraft/textures/font/ascii.png");
        Map<Integer, BufferedImage> map = new HashMap<>();
        for (int y = 0; y < chars.size(); y++) {
            int[] row = chars.get(y).getAsString().codePoints().toArray();
            for (int x = 0; x < row.length; x++) map.put(row[x], ascii.getSubimage(x * 8, y * 8, 8, 8));
        }
        glyphs = Map.copyOf(map);
    }

    static void decorate(BufferedImage panel, boolean assembly, boolean error) {
        if (!assembly) return;
        Graphics2D g = panel.createGraphics();
        draw(g, new Label(100 - PolymerGogglesOverlay.width("Train Assembly") / 2, 4, "Train Assembly", 0x0E2233));
        if (error)
            draw(
                    g,
                    new Label(
                            100 - PolymerGogglesOverlay.width("Resolve this and retry") / 2,
                            123,
                            "Resolve this and retry",
                            0x7A7A7A));
        else {
            wrapped(g, 28, 62, "Use Train Casing on highlighted Tracks to create bogeys.", 134);
            wrapped(g, 28, 94, "Remove bogeys by breaking the block on top.", 134);
            wrapped(g, 28, 117, "Build carriages attached to one or two bogeys each.", 138);
        }
        g.dispose();
    }

    private static void wrapped(Graphics2D g, int x, int y, String value, int width) {
        String line = "";
        for (String word : value.split(" ")) {
            if (!line.isEmpty() && PolymerGogglesOverlay.width(line + " " + word) > width) {
                draw(g, new Label(x, y, line, 0x7A7A7A));
                y += 9;
                line = "";
            }
            line += (line.isEmpty() ? "" : " ") + word;
        }
        if (!line.isEmpty()) draw(g, new Label(x, y, line, 0x7A7A7A));
    }

    private static void draw(Graphics2D g, Label label) {
        int x = label.x();
        for (int cp : label.value().codePoints().toArray()) {
            BufferedImage source = glyphs.get(cp);
            if (source != null) {
                BufferedImage tinted = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
                for (int yy = 0; yy < 8; yy++)
                    for (int xx = 0; xx < 8; xx++)
                        tinted.setRGB(xx, yy, (source.getRGB(xx, yy) & 0xFF000000) | label.color());
                g.drawImage(tinted, x, label.y(), null);
            }
            x += PolymerGogglesOverlay.width(Character.toString(cp));
        }
    }

    static void addAssets(ResourcePackBuilder builder, Map<Integer, Integer> advances) throws IOException {
        iconAdvances = Map.copyOf(advances);
        for (int cp = 33; cp < 127; cp++) {
            BufferedImage glyph = glyphs.get(cp);
            if (glyph == null) throw new IOException("Missing ASCII glyph " + cp);
            for (int y = 0; y < 8; y++)
                for (int x = 0; x < 8; x++)
                    if ((glyph.getRGB(x, y) >>> 24) != 0 && (glyph.getRGB(x, y) | 0xFF000000) != 0xFFFFFFFF)
                        throw new IOException("ASCII glyph " + cp + " is not plain white");
        }
        BufferedImage white = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) white.setRGB(x, y, 0xFFFFFFFF);
        ASSETS.png(builder, "pixel", white);
        quad(builder, "pixel", "pixel", 1, 1, 0, 0, 1, true);
        for (int mask = 0; mask < 16; mask++) {
            BufferedImage panel = PngCodec.decode(
                    builder.getDataOrSource("assets/create/textures/font/station_panel_" + mask + ".png"));
            for (int half = 0; half < 2; half++) {
                String name = "panel_" + mask + "_" + half;
                ASSETS.png(builder, name, panel.getSubimage(half * 100, 0, 100, panel.getHeight()));
                quad(
                        builder,
                        name,
                        name,
                        100,
                        panel.getHeight(),
                        half == 0 ? -50 : 50,
                        26 - panel.getHeight() / 2f,
                        4,
                        false);
            }
        }
        BufferedImage edit =
                PngCodec.decode(builder.getDataOrSource("assets/create/textures/font/station_edit_name.png"));
        ASSETS.png(builder, "edit_name", edit);
        quad(builder, "edit_name", "edit_name", 13, 13, 6.5f, 0, 1, false);
        for (int icon = 0; icon < 15; icon++) {
            BufferedImage img = PngCodec.decode(
                    builder.getDataOrSource("assets/create/textures/font/station_train_" + icon + ".png"));
            String name = "train_" + icon;
            ASSETS.png(builder, name, img);
            quad(builder, name, name, img.getWidth(), 10, img.getWidth() / 2f, -5, 1, false);
        }
        JsonArray views = new JsonArray();
        for (boolean assembly : new boolean[] {false, true}) {
            JsonArray models = new JsonArray(), panels = new JsonArray();
            for (int mask = assembly ? 8 : 0; mask < (assembly ? 16 : 8); mask++) {
                JsonArray halves = new JsonArray();
                for (int h = 0; h < 2; h++) halves.add(ASSETS.model("panel_" + mask + "_" + h));
                panels.add(caseOf(Integer.toString(mask), composite(halves)));
            }
            models.add(select(1, panels));
            int flag = 0, row = 0;
            for (Lane lane : assembly ? ASSEMBLY : NORMAL) {
                JsonObject pixel = ASSETS.model("pixel");
                JsonArray tints = new JsonArray();
                JsonObject tint = new JsonObject();
                tint.addProperty("type", "minecraft:custom_model_data");
                tint.addProperty("index", row);
                tint.addProperty("default", 0xFFFFFF);
                tints.add(tint);
                pixel.add("tints", tints);
                JsonArray pixels = new JsonArray();
                for (int x = lane.x(); x < lane.x() + lane.width(); x++)
                    for (int y = 0; y < ROWS; y++) {
                        JsonObject choice = condition(flag++, pixel);
                        choice.add("transformation", translation((x - 100) / 16f, (26 - lane.y() - y) / 16f, 0.02f));
                        pixels.add(choice);
                    }
                models.add(composite(pixels));
                row++;
            }
            int index = 2;
            if (!assembly)
                for (int x = 0; x < 200; x++) {
                    JsonArray choices = new JsonArray();
                    for (int icon = 0; icon < 15; icon++)
                        if (iconFits(x, icon))
                            choices.add(caseOf(Integer.toString(icon), ASSETS.model("train_" + icon)));
                    int slot = index++;
                    if (choices.isEmpty()) continue;
                    JsonObject choice = select(slot, choices);
                    choice.add("transformation", translation((x - 100) / 16f, 6 / 16f, 0.01f));
                    models.add(choice);
                }
            if (!assembly) {
                JsonArray choices = new JsonArray();
                choices.add(caseOf("edit", ASSETS.model("edit_name")));
                for (int x = 0; x < 200; x++) {
                    int slot = index++;
                    if (x + 13 > 200) continue;
                    JsonObject choice = select(slot, choices);
                    choice.add("transformation", translation((x - 100) / 16f, 18.5f / 16f, 0.01f));
                    models.add(choice);
                }
            }
            views.add(caseOf(assembly ? "assembly" : "station", composite(models)));
        }
        JsonObject item = new JsonObject();
        item.addProperty("oversized_in_gui", true);
        item.add("model", select(0, views));
        builder.addStringData("assets/create/items/ui/station_foreground.json", item.toString());
    }

    private static JsonObject condition(int index, JsonObject model) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "minecraft:condition");
        o.addProperty("property", "minecraft:custom_model_data");
        o.addProperty("index", index);
        o.add("on_true", model);
        o.add("on_false", EMPTY);
        return o;
    }

    private static JsonObject select(int index, JsonArray cases) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "minecraft:select");
        o.addProperty("property", "minecraft:custom_model_data");
        o.addProperty("index", index);
        o.add("cases", cases);
        o.add("fallback", EMPTY);
        return o;
    }

    private static JsonObject composite(JsonArray models) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "minecraft:composite");
        o.add("models", models);
        return o;
    }

    private static JsonArray translation(float x, float y, float z) {
        JsonArray a = new JsonArray();
        for (float value : new float[] {1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1})
            if (value == (int) value) a.add((int) value);
            else a.add(value);
        return a;
    }

    private static void quad(
            ResourcePackBuilder b,
            String name,
            String texture,
            float w,
            float h,
            float dx,
            float dy,
            float scale,
            boolean glyph) {
        JsonObject o = new JsonObject();
        o.addProperty("gui_light", "front");
        JsonObject textures = new JsonObject();
        textures.addProperty("panel", "create:item/ui/station/" + texture);
        o.add("textures", textures);
        JsonObject element = new JsonObject();
        element.add("from", array(glyph ? 8 : 8 - w / scale / 2, glyph ? 8 - h : 8 - h / scale / 2, 8));
        element.add("to", array(glyph ? 8 + w : 8 + w / scale / 2, glyph ? 8 : 8 + h / scale / 2, 8));
        element.addProperty("shade", false);
        JsonObject face = new JsonObject();
        face.add("uv", array(0, 0, 16, 16));
        face.addProperty("texture", "#panel");
        if (glyph) face.addProperty("tintindex", 0);
        JsonObject faces = new JsonObject();
        faces.add("south", face);
        element.add("faces", faces);
        JsonArray elements = new JsonArray();
        elements.add(element);
        o.add("elements", elements);
        JsonObject gui = new JsonObject();
        gui.add("rotation", array(0, 0, 0));
        gui.add("translation", array(dx, dy, 0));
        gui.add("scale", array(scale, scale, 1));
        JsonObject display = new JsonObject();
        display.add("gui", gui);
        o.add("display", display);
        b.addStringData("assets/create/models/item/ui/station/" + name + ".json", o.toString());
    }
}
