package com.zurrtum.create.infrastructure.polymer.gui;

import static com.zurrtum.create.infrastructure.polymer.gui.PolymerScheduleScreen.ART_X;
import static com.zurrtum.create.infrastructure.polymer.gui.PolymerScheduleScreen.ART_Y;
import static com.zurrtum.create.infrastructure.polymer.gui.PolymerScheduleScreen.CARD_HEADER;
import static com.zurrtum.create.infrastructure.polymer.gui.PolymerScheduleScreen.CARD_W;
import static com.zurrtum.create.infrastructure.polymer.gui.PolymerScheduleScreen.CARD_X;
import static com.zurrtum.create.infrastructure.polymer.gui.PolymerScheduleScreen.COVER_H;
import static com.zurrtum.create.infrastructure.polymer.gui.PolymerScheduleScreen.COVER_ITEM;
import static com.zurrtum.create.infrastructure.polymer.gui.PolymerScheduleScreen.COVER_W;
import static com.zurrtum.create.infrastructure.polymer.gui.PolymerScheduleScreen.COVER_X;
import static com.zurrtum.create.infrastructure.polymer.gui.PolymerScheduleScreen.COVER_Y;
import static com.zurrtum.create.infrastructure.polymer.gui.PolymerScheduleScreen.CUT_W;
import static com.zurrtum.create.infrastructure.polymer.gui.PolymerScheduleScreen.CUT_X;
import static com.zurrtum.create.infrastructure.polymer.gui.PolymerScheduleScreen.DATA_AREA;
import static com.zurrtum.create.infrastructure.polymer.gui.PolymerScheduleScreen.EDITOR_CUT_H;
import static com.zurrtum.create.infrastructure.polymer.gui.PolymerScheduleScreen.EDITOR_CUT_Y;
import static com.zurrtum.create.infrastructure.polymer.gui.PolymerScheduleScreen.EMPTY_ITEM;
import static com.zurrtum.create.infrastructure.polymer.gui.PolymerScheduleScreen.FILLER_H;
import static com.zurrtum.create.infrastructure.polymer.gui.PolymerScheduleScreen.FILLER_Y;
import static com.zurrtum.create.infrastructure.polymer.gui.PolymerScheduleScreen.MIDDLE;
import static com.zurrtum.create.infrastructure.polymer.gui.PolymerScheduleScreen.PLACEMENTS;
import static com.zurrtum.create.infrastructure.polymer.gui.PolymerScheduleScreen.STACKS;
import static com.zurrtum.create.infrastructure.polymer.gui.PolymerScheduleScreen.TEXT_TOPS;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zurrtum.create.Create;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerScheduleScreen.Piece;
import dev.veeda.createpoly.assets.PackAssetId;
import dev.veeda.createpoly.assets.PackAssetWriter;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class PolymerScheduleScreenAssets {
    private PolymerScheduleScreenAssets() {}

    static void addAssets(ResourcePackBuilder builder) {
        try {
            PackAssetWriter assets = new PackAssetWriter(builder);
            Map<Piece, BufferedImage> images = images(builder);
            for (Piece piece : Piece.values()) {
                BufferedImage image = images.get(piece);
                if (image == null || image.getWidth() != piece.width || image.getHeight() != piece.height)
                    throw new IOException("Schedule piece " + piece + " has the wrong size");
                if (image.getWidth() > 256 || image.getHeight() > 256)
                    throw new IOException("Schedule glyph exceeds a font page: " + piece);
                assets.png(
                        new PackAssetId(
                                Create.MOD_ID, "font/schedule/" + piece.name().toLowerCase()),
                        marked(image));
            }
            BufferedImage stacks = new BufferedImage(7, 8, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 7; y++)
                for (int x = 0; x < 7; x++) if (y % 2 == 0 || x == 0 || x == 6) stacks.setRGB(x, y, 0xFFFFFFFF);
            assets.png(new PackAssetId(Create.MOD_ID, "font/schedule/stacks"), stacks);

            JsonArray asciiRows = PolymerArtKit.asciiRows(builder);
            Set<Integer> tops = new TreeSet<>(PLACEMENTS.keySet());
            tops.addAll(TEXT_TOPS);
            Set<Integer> pads = new TreeSet<>();
            for (int top : tops) {
                int ascent = 13 - top;
                JsonArray providers = new JsonArray();
                if (TEXT_TOPS.contains(top)) {
                    JsonObject space = new JsonObject();
                    space.addProperty("type", "space");
                    JsonObject advances = new JsonObject();
                    advances.addProperty(" ", 4);
                    space.add("advances", advances);
                    providers.add(space);
                    int pad = Math.max(0, ascent - 8);
                    if (pad > 0) pads.add(pad);
                    JsonObject ascii = PolymerGuiGlyphs.bitmap(
                            pad == 0 ? "minecraft:font/ascii.png" : "create:font/schedule/ascii_pad" + pad + ".png",
                            8 + pad,
                            ascent,
                            null);
                    ascii.add("chars", asciiRows.deepCopy());
                    providers.add(ascii);
                    if (pad == 0)
                        providers.add(PolymerGuiGlyphs.bitmap(
                                "create:font/schedule/stacks.png", 8, ascent, Character.toString(STACKS)));
                }
                for (Piece piece : new TreeSet<>(PLACEMENTS.getOrDefault(top, Set.of()))) {
                    if (ascent > piece.height)
                        throw new IOException("Schedule glyph " + piece + " cannot reach top " + top);
                    providers.add(PolymerGuiGlyphs.bitmap(piece.file(), piece.height, ascent, piece.glyph()));
                }
                JsonObject root = new JsonObject();
                root.add("providers", providers);
                String path =
                        "assets/create/font/schedule/y" + (top < 0 ? "n" + -top : Integer.toString(top)) + ".json";
                if (!builder.addData(path, root.toString().getBytes(StandardCharsets.UTF_8)))
                    throw new IOException("Cannot write " + path);
            }
            BufferedImage ascii = PolymerArtKit.image(builder, "assets/minecraft/textures/font/ascii.png");
            for (int pad : pads) {
                BufferedImage padded = new BufferedImage(128, 16 * (8 + pad), BufferedImage.TYPE_INT_ARGB);
                var g = padded.createGraphics();
                for (int row = 0; row < 16; row++)
                    g.drawImage(ascii.getSubimage(0, row * 8, 128, 8), 0, row * (8 + pad), null);
                g.dispose();
                assets.png(new PackAssetId(Create.MOD_ID, "font/schedule/ascii_pad" + pad), padded);
            }

            Map<String, BufferedImage> covers = new LinkedHashMap<>();
            BufferedImage panel = images.get(Piece.PANEL);
            BufferedImage gap =
                    PolymerArtKit.copy(panel.getSubimage(COVER_X - ART_X, COVER_Y - ART_Y, COVER_W, COVER_H));
            BufferedImage head = PolymerArtKit.copy(gap), body = PolymerArtKit.copy(gap);
            int bandX = COVER_X - CARD_X;
            int fillerRows = FILLER_Y + FILLER_H - COVER_Y;
            for (int x = 0; x < COVER_W; x++) {
                for (int y = 0; y < fillerRows; y++)
                    overlay(body, x, y, images.get(Piece.FILL).getRGB(bandX + x, COVER_Y - FILLER_Y + y));
                for (int y = fillerRows; y < COVER_H; y++) {
                    overlay(head, x, y, images.get(Piece.HEAD_LIGHT).getRGB(bandX + x, y - fillerRows));
                    overlay(body, x, y, images.get(Piece.BODY).getRGB(bandX + x, y - fillerRows));
                }
            }
            covers.put("gap", gap);
            covers.put("gap_head", head);
            covers.put("body", body);
            PolymerArtKit art = new PolymerArtKit(builder, "schedule", 160, 116);
            art.layer(art.allocate(1), covers, COVER_X, COVER_Y, 0.01f);
            art.finish(COVER_ITEM);
            PolymerArtKit.empty(builder, EMPTY_ITEM);

            if (builder.getDataOrSource("assets/minecraft/models/item/clock_00.json") == null)
                throw new IOException("Vanilla clock models missing");
            for (int frame = 0; frame < 64; frame++) {
                String name = (frame < 10 ? "0" : "") + frame;
                assets.item(
                        new PackAssetId(Create.MOD_ID, "ui/schedule_clock_" + name),
                        JsonParser.parseString(
                                "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/clock_" + name
                                        + "\"}}"));
            }
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Cannot build Train Schedule UI source art", exception);
        }
    }

    private static BufferedImage marked(BufferedImage image) {
        BufferedImage out = PolymerArtKit.copy(image);
        int x = out.getWidth() - 1;
        for (int y = 0; y < out.getHeight(); y++) if ((out.getRGB(x, y) >>> 24) != 0) return out;
        out.setRGB(x, 0, 0x01FFFFFF);
        return out;
    }

    private static Map<Piece, BufferedImage> images(ResourcePackBuilder builder) throws IOException {
        BufferedImage schedule = PolymerArtKit.image(builder, "assets/create/textures/gui/schedule.png");
        BufferedImage editor = PolymerArtKit.image(builder, "assets/create/textures/gui/schedule_2.png");
        BufferedImage widgets = PolymerArtKit.image(builder, "assets/create/textures/gui/widgets.png");
        BufferedImage icons = PolymerArtKit.image(builder, "assets/create/textures/gui/icons.png");
        BufferedImage link = PolymerArtKit.image(builder, "assets/create/textures/gui/display_link.png");
        BufferedImage inventory = PolymerArtKit.image(builder, "assets/create/textures/gui/player_inventory.png");
        BufferedImage item = PolymerArtKit.image(builder, "assets/create/textures/item/schedule.png");
        Map<Piece, BufferedImage> images = new EnumMap<>(Piece.class);

        BufferedImage full = PolymerArtKit.copy(schedule.getSubimage(0, 0, 256, 226));
        BufferedImage dark = schedule.getSubimage(5, 235, 3, 1);
        for (int y = 16; y < 189; y++) for (int x = 0; x < 3; x++) full.setRGB(33 + x, y, dark.getRGB(x, 0));
        for (int y = 192; y < 218; y++) for (int x = 19; x < 42; x++) full.setRGB(x, y, full.getRGB(60, y));
        BufferedImage panel = new BufferedImage(240, 226, BufferedImage.TYPE_INT_ARGB);
        var g = panel.createGraphics();
        g.drawImage(full.getSubimage(0, 0, CUT_X, 226), 0, 0, null);
        g.drawImage(full.getSubimage(CUT_X + CUT_W, 0, 256 - CUT_X - CUT_W, 226), CUT_X, 0, null);
        g.dispose();
        images.put(Piece.PANEL, panel);

        int darkColor = schedule.getRGB(5, 233), medium = schedule.getRGB(6, 233), light = schedule.getRGB(7, 233);
        BufferedImage lightCard = card(100, light, light, darkColor, medium),
                mediumCard = card(100, medium, light, darkColor, medium);
        images.put(Piece.HEAD_LIGHT, PolymerArtKit.copy(lightCard.getSubimage(0, 0, CARD_W, 18)));
        images.put(Piece.HEAD_MEDIUM, PolymerArtKit.copy(mediumCard.getSubimage(0, 0, CARD_W, 18)));
        images.put(Piece.BODY, PolymerArtKit.copy(mediumCard.getSubimage(0, 40, CARD_W, 18)));
        images.put(Piece.BODY_LIGHT, PolymerArtKit.copy(lightCard.getSubimage(0, 18, CARD_W, 18)));
        images.put(Piece.BOTTOM, PolymerArtKit.copy(mediumCard.getSubimage(0, 82, CARD_W, 18)));
        images.put(
                Piece.BOTTOM_LIGHT,
                PolymerArtKit.copy(card(36, light, light, darkColor, medium).getSubimage(0, 18, CARD_W, 18)));
        images.put(Piece.FILL, PolymerArtKit.copy(mediumCard.getSubimage(0, 40, CARD_W, FILLER_H)));
        images.put(Piece.FILL_LIGHT, PolymerArtKit.copy(lightCard.getSubimage(0, 18, CARD_W, FILLER_H)));
        BufferedImage strip = schedule.getSubimage(5, 237, 3, 1);
        images.put(Piece.LINE, tile(strip, 3, 18));
        images.put(Piece.LINE_FILL, tile(strip, 3, FILLER_H));
        images.put(Piece.STRIP_WAIT, crop(schedule, 1, 239, 11, 16));
        images.put(Piece.STRIP_TRAVEL, crop(schedule, 12, 239, 11, 16));
        images.put(Piece.STRIP_DOTTED, crop(schedule, 23, 239, 11, 16));
        images.put(Piece.STRIP_END, crop(schedule, 34, 239, 11, 16));
        images.put(Piece.STRIP_ACTION, crop(schedule, 209, 239, 11, 16));
        images.put(Piece.CARD_NEW, crop(schedule, 79, 239, 16, 16));
        images.put(Piece.POINTER, crop(schedule, 185, 239, 21, 16));
        images.put(Piece.POINTER_OFF, crop(schedule, 171, 239, 13, 16));
        images.put(Piece.MOVE_UP, crop(schedule, 51, 230, 12, 12));
        images.put(Piece.MOVE_DOWN, crop(schedule, 65, 230, 12, 12));
        images.put(Piece.REMOVE, crop(schedule, 51, 243, 12, 12));
        images.put(Piece.DUPLICATE, crop(schedule, 65, 243, 12, 12));
        images.put(Piece.SCROLL_LEFT, crop(schedule, 161, 247, 4, 8));
        images.put(Piece.SCROLL_RIGHT, crop(schedule, 166, 247, 4, 8));
        images.put(Piece.COND_LEFT, crop(schedule, 116, 239, 6, 16));
        images.put(Piece.COND_LEFT_CLEAN, crop(schedule, 147, 239, 2, 16));
        images.put(Piece.COND_ITEM, crop(schedule, 125, 239, 18, 16));
        images.put(Piece.COND_RIGHT, crop(schedule, 144, 239, 2, 16));
        images.put(Piece.APPEND, crop(schedule, 150, 245, 10, 10));
        images.put(Piece.COND_NEW, crop(schedule, 96, 239, 19, 16));
        BufferedImage middle = schedule.getSubimage(123, 239, 1, 16);
        for (int bit = 0; bit < MIDDLE.length; bit++) images.put(MIDDLE[bit], tile(middle, 1 << bit, 16));
        images.put(Piece.GRAD_TOP, gradient(204, 10, 0x77, 0));
        images.put(Piece.GRAD_BOTTOM, gradient(204, 10, 0, 0x77));
        BufferedImage itemFrame = item.getSubimage(0, 0, 16, 16),
                large = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 48; y++) for (int x = 0; x < 48; x++) large.setRGB(x, y, itemFrame.getRGB(x / 3, y / 3));
        images.put(Piece.ITEM_3X, large);

        BufferedImage left = schedule.getSubimage(161, 247, 4, 8);
        BufferedImage up = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB),
                down = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 4; x++) {
                int pixel = left.getRGB(x, y);
                up.setRGB(4 + (7 - y), 6 + x, pixel);
                down.setRGB(4 + y, 6 + (3 - x), pixel);
            }
        images.put(Piece.BTN_LOOP, button(widgets, 0, icon(icons, 3, 1)));
        images.put(Piece.BTN_LOOP_ON, button(widgets, 72, icon(icons, 3, 1)));
        images.put(Piece.BTN_RESET, button(widgets, 0, icon(icons, 7, 0)));
        images.put(Piece.BTN_RESET_OFF, button(widgets, 90, icon(icons, 7, 0)));
        images.put(Piece.BTN_SKIP, button(widgets, 0, icon(icons, 5, 0)));
        images.put(Piece.BTN_SKIP_OFF, button(widgets, 90, icon(icons, 5, 0)));
        images.put(Piece.BTN_UP, button(widgets, 0, up));
        images.put(Piece.BTN_UP_OFF, button(widgets, 90, up));
        images.put(Piece.BTN_DOWN, button(widgets, 0, down));
        images.put(Piece.BTN_DOWN_OFF, button(widgets, 90, down));
        images.put(Piece.BTN_CONFIRM, button(widgets, 0, icon(icons, 0, 1)));
        images.put(Piece.BTN_TRASH, button(widgets, 0, icon(icons, 1, 0)));

        BufferedImage dim = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 256; y++) {
            int alpha = 0xC0 + (0xD0 - 0xC0) * y / 255;
            for (int x = 0; x < 256; x++) dim.setRGB(x, y, alpha << 24 | 0x101010);
        }
        images.put(Piece.DIM, dim);
        BufferedImage editorPanel = new BufferedImage(256, 89 - EDITOR_CUT_H, BufferedImage.TYPE_INT_ARGB);
        g = editorPanel.createGraphics();
        g.drawImage(editor.getSubimage(0, 0, 256, EDITOR_CUT_Y), 0, 0, null);
        g.drawImage(
                editor.getSubimage(0, EDITOR_CUT_Y + EDITOR_CUT_H, 256, 89 - EDITOR_CUT_Y - EDITOR_CUT_H),
                0,
                EDITOR_CUT_Y,
                null);
        g.dispose();
        images.put(Piece.EDITOR, editorPanel);
        images.put(Piece.INVENTORY, crop(inventory, 0, 0, 176, 108));
        images.put(Piece.SLOT_ADDITIONAL, crop(editor, 55, 47, 32, 18));
        images.put(Piece.SLOT_INACTIVE, crop(editor, 0, 91, 18, 18));
        images.put(Piece.DA_START, crop(link, 0, 163, 2, 18));
        images.put(Piece.DA_SPEECH, crop(link, 8, 163, 5, 18));
        images.put(Piece.DA_END, crop(link, 5, 163, 2, 18));
        BufferedImage area = link.getSubimage(3, 163, 1, 18);
        for (int bit = 0; bit < DATA_AREA.length; bit++) images.put(DATA_AREA[bit], tile(area, 1 << bit, 18));
        return images;
    }

    private static BufferedImage card(int height, int header, int light, int dark, int medium) {
        BufferedImage image = new BufferedImage(CARD_W, height, BufferedImage.TYPE_INT_ARGB);
        fill(image, 0, 1, CARD_W, height - 2, light);
        fill(image, 1, 0, CARD_W - 2, height, light);
        fill(image, 1, 1, CARD_W - 2, height - 2, dark);
        fill(image, 2, 2, CARD_W - 4, height - 4, medium);
        fill(image, 2, 2, CARD_W - 4, CARD_HEADER, header);
        return image;
    }

    private static void fill(BufferedImage image, int x, int y, int w, int h, int color) {
        for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) image.setRGB(xx, yy, color);
    }

    private static BufferedImage button(BufferedImage widgets, int u, BufferedImage icon) {
        BufferedImage image = crop(widgets, u, 0, 18, 18);
        var g = image.createGraphics();
        g.drawImage(icon, 1, 1, null);
        g.dispose();
        return image;
    }

    private static BufferedImage icon(BufferedImage icons, int column, int row) {
        return crop(icons, column * 16, row * 16, 16, 16);
    }

    private static BufferedImage gradient(int w, int h, int top, int bottom) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            int alpha = Math.round(top + (bottom - top) * (y + .5f) / h);
            for (int x = 0; x < w; x++) image.setRGB(x, y, alpha << 24);
        }
        return image;
    }

    private static BufferedImage tile(BufferedImage source, int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                image.setRGB(x, y, source.getRGB(x % source.getWidth(), y % source.getHeight()));
        return image;
    }

    private static BufferedImage crop(BufferedImage source, int x, int y, int w, int h) {
        return PolymerArtKit.copy(source.getSubimage(x, y, w, h));
    }

    private static void overlay(BufferedImage image, int x, int y, int argb) {
        int alpha = argb >>> 24;
        if (alpha == 0) return;
        if (alpha == 255) {
            image.setRGB(x, y, argb);
            return;
        }
        int base = image.getRGB(x, y), a = alpha, ia = 255 - a;
        int r = ((argb >> 16 & 255) * a + (base >> 16 & 255) * ia) / 255;
        int gr = ((argb >> 8 & 255) * a + (base >> 8 & 255) * ia) / 255;
        int b = ((argb & 255) * a + (base & 255) * ia) / 255;
        int outA = a + (base >>> 24) * ia / 255;
        image.setRGB(x, y, outA << 24 | r << 16 | gr << 8 | b);
    }
}
