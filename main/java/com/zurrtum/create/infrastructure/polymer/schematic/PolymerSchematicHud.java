package com.zurrtum.create.infrastructure.polymer.schematic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerArtKit;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerGuiGlyphs;
import dev.veeda.createpoly.assets.CreateIds;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

final class PolymerSchematicHud {
    static final String[] NAMES = {"Position", "Move XZ", "Move Y", "Rotate", "Mirror", "Print"};
    private static final int[][] ICONS = {{0, 48}, {0, 64}, {16, 64}, {32, 64}, {48, 64}, {0, 16}};
    private static final String[][] DESCRIPTIONS = {
        {"Moves the structure to a location.", "[Click] the ground to place.", "", ""},
        {
            "Shifts the Schematic Horizontally.",
            "[Right-Click] pushes it the way you face,",
            "[Left-Click] pulls it in.",
            ""
        },
        {"Shifts the Schematic Vertically.", "[Right-Click] moves it up,", "[Left-Click] moves it down.", ""},
        {
            "Rotates the Schematic around its center.",
            "[Right-Click] rotates 90 Degrees clockwise,",
            "[Left-Click] counter-clockwise.",
            ""
        },
        {"Flips the Schematic along the face you", "look along. [Click] to flip it.", "", ""},
        {
            "Instantly places the structure in the world.",
            "[Right-Click] to confirm placement.",
            "This tool is for Creative Mode only.",
            ""
        }
    };

    private static final int[] TOOLTIP_OFFSET = {38, 50, 60, 72};
    private static final int[] TOOLTIP_COLORS = {0xEEEEEE, 0xCCDDFF, 0xCCDDFF, 0xCCCCDD};
    private static final int LABEL = 0xCCDDFF;
    private static final int PANEL_UP = 33, FOCUS_LIFT = 10, PANEL_H = 30, TOOLTIP_H = 52, STRIP = 110;
    private static final int SPACE = 0xF000, PANEL = 0xE000, TOOLTIP = 0xE040, ICON = 0xE100, LEVELS = FOCUS_LIFT + 1;
    private static final int TOOLTIP_MIN_LIFT = 3;

    private static final String SPACE_FONT = "schematic_hud_space", IMAGE_FONT = "schematic_hud";
    private static final int REFRESH_TICKS = 20;
    private static volatile Map<Integer, Integer> imageAdvance = Map.of(), textAdvance = Map.of();

    private static final Map<UUID, Shown> SHOWN = new HashMap<>();
    private static final Map<UUID, Float> LIFT = new HashMap<>();

    private record Shown(String key, int sentTick) {}

    private PolymerSchematicHud() {}

    static void show(ServerPlayer player, int selected, boolean creative, boolean force) {
        if (textAdvance.isEmpty()) return;
        boolean focused = player.isShiftKeyDown();
        float y = LIFT.getOrDefault(player.getUUID(), 0f);
        if (!force) {
            y = focused ? y + (FOCUS_LIFT - y) * .1f : y * .9f;
            LIFT.put(player.getUUID(), y);
        }
        int lift = Math.max(0, Math.min(FOCUS_LIFT, Math.round(y)));
        String key = selected + ":" + creative + ":" + focused + ":" + lift;
        int now = player.level().getServer().getTickCount();
        Shown shown = SHOWN.get(player.getUUID());
        if (!force && shown != null && shown.key.equals(key) && now - shown.sentTick < REFRESH_TICKS) return;
        SHOWN.put(player.getUUID(), new Shown(key, now));
        player.sendOverlayMessage(compose(selected, creative ? 6 : 5, focused, lift));
    }

    static void hide(ServerPlayer player) {
        LIFT.remove(player.getUUID());
        if (SHOWN.remove(player.getUUID()) != null) player.sendOverlayMessage(Component.empty());
    }

    static void forget(UUID player) {
        SHOWN.remove(player);
        LIFT.remove(player);
    }

    private static Component compose(int selected, int tools, boolean focused, int lift) {
        Line line = new Line();
        int w = Math.max(tools * 50 + 30, 220);
        int left = -w / 2;
        for (int x = 0; x < w; x += STRIP)
            line.image(left + x, PANEL + lift * 4 + (focused ? 2 : 0) + (w - x >= STRIP ? 0 : 1));
        if (lift >= TOOLTIP_MIN_LIFT)
            for (int x = 0; x < w; x += STRIP) line.image(left + x, TOOLTIP + lift * 2 + (w - x >= STRIP ? 0 : 1));
        int x0 = left + 15;
        for (int i = 0; i < tools; i++) {
            int variant = i == selected ? 2 : focused ? 1 : 0;
            line.image(x0 + i * 50 + 16, ICON + (lift * 6 + i) * 3 + variant);
        }
        String header = focused ? "[SCROLL] to Cycle" : "Hold [Shift] to Focus";
        line.centred(0, header, textFont(lift, 0), LABEL);
        String name = NAMES[Math.min(selected, NAMES.length - 1)];
        line.centred(x0 + selected * 50 + 24, name, textFont(lift, 1), LABEL);
        if (lift >= TOOLTIP_MIN_LIFT) {
            String[] description = DESCRIPTIONS[Math.min(selected, DESCRIPTIONS.length - 1)];
            for (int row = 0; row < 4; row++)
                if (!description[row].isEmpty())
                    line.text(x0 - 10, description[row], textFont(lift, 2 + row), TOOLTIP_COLORS[row]);
        }
        line.home();
        return line.out;
    }

    private static String textFont(int lift, int row) {
        return "schematic_hud_t" + lift + "_" + row;
    }

    private static int textTop(int lift, int row) {
        int panel = -PANEL_UP - lift;
        return row == 0 ? panel - 10 : row == 1 ? panel + 18 : panel + TOOLTIP_OFFSET[row - 2];
    }

    private static final class Line {
        final MutableComponent out = Component.empty();
        int cursor;

        void shift(int delta) {
            if (delta == 0) return;
            StringBuilder text = new StringBuilder();
            int left = Math.abs(delta);
            for (int bit = 10; bit >= 0; bit--)
                while (left >= 1 << bit) {
                    text.appendCodePoint(SPACE + (delta < 0 ? 32 : 0) + bit);
                    left -= 1 << bit;
                }
            out.append(Component.literal(text.toString()).withStyle(style(SPACE_FONT, 0xFFFFFF)));
            cursor += delta;
        }

        void image(int x, int code) {
            shift(x - cursor);
            out.append(Component.literal(Character.toString(code)).withStyle(style(IMAGE_FONT, 0xFFFFFF)));
            cursor += imageAdvance.getOrDefault(code, 0);
        }

        void text(int x, String value, String font, int color) {
            shift(x - cursor);
            StringBuilder safe = new StringBuilder();
            int width = 0;
            for (int cp : value.codePoints().toArray()) {
                int c = cp == ' ' || textAdvance.containsKey(cp) ? cp : '?';
                safe.appendCodePoint(c);
                width += c == ' ' ? 4 : textAdvance.getOrDefault(c, 6);
            }
            out.append(Component.literal(safe.toString()).withStyle(style(font, color)));
            cursor += width;
        }

        void centred(int centre, String value, String font, int color) {
            int width = 0;
            for (int cp : value.codePoints().toArray()) width += cp == ' ' ? 4 : textAdvance.getOrDefault(cp, 6);
            text(centre - width / 2, value, font, color);
        }

        void home() {
            shift(-cursor);
        }
    }

    private static Style style(String font, int color) {
        return Style.EMPTY
                .withFont(new FontDescription.Resource(CreateIds.id(font)))
                .withColor(color)
                .withoutShadow();
    }

    static void addAssets(ResourcePackBuilder builder) {
        try {
            BufferedImage overlay = PolymerArtKit.image(builder, "assets/create/textures/gui/overlay.png");
            BufferedImage icons = PolymerArtKit.image(builder, "assets/create/textures/gui/icons.png");
            int base = overlay.getRGB(0, 0);
            Map<Integer, Integer> images = new HashMap<>();
            JsonArray imageProviders = new JsonArray();
            BufferedImage[][] panels = new BufferedImage[2][2];
            for (int focus = 0; focus < 2; focus++)
                for (int part = 0; part < 2; part++)
                    panels[focus][part] =
                            filled(part == 0 ? STRIP : 60, PANEL_H, tint(base, 0xFFFFFF, focus == 1 ? 0xE0 : 0x80));
            int[] iconAlpha = {0x33, 0xFF, 0xFF}, iconRaise = {0, 0, 10};
            BufferedImage[][] iconArt = new BufferedImage[ICONS.length][3];
            for (int tool = 0; tool < ICONS.length; tool++)
                for (int v = 0; v < 3; v++) {
                    BufferedImage icon = new BufferedImage(16, 17, BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < 16; y++)
                        for (int x = 0; x < 16; x++) {
                            int src = icons.getRGB(ICONS[tool][0] + x, ICONS[tool][1] + y);
                            int a = (src >>> 24) * iconAlpha[v] / 255;
                            if (a == 0) continue;
                            blend(icon, x, y + 1, a << 24);
                            blend(icon, x, y, a << 24 | src & 0xFFFFFF);
                        }
                    icon.setRGB(15, 16, icon.getRGB(15, 16) | 0x01000000);
                    iconArt[tool][v] = icon;
                }
            for (int lift = 0; lift < LEVELS; lift++) {
                int panelTop = -PANEL_UP - lift;
                for (int focus = 0; focus < 2; focus++)
                    for (int part = 0; part < 2; part++)
                        imageProviders.add(image(
                                builder,
                                images,
                                PANEL + lift * 4 + focus * 2 + part,
                                "panel_" + lift + "_" + focus + "_" + part,
                                panels[focus][part],
                                panelTop));
                if (lift >= TOOLTIP_MIN_LIFT)
                    for (int part = 0; part < 2; part++)
                        imageProviders.add(image(
                                builder,
                                images,
                                TOOLTIP + lift * 2 + part,
                                "tooltip_" + lift + "_" + part,
                                filled(
                                        part == 0 ? STRIP : 60,
                                        TOOLTIP_H,
                                        tint(base, 0xB2B2CC, lift * 255 / FOCUS_LIFT)),
                                panelTop + 33));
                for (int tool = 0; tool < ICONS.length; tool++)
                    for (int v = 0; v < 3; v++)
                        imageProviders.add(image(
                                builder,
                                images,
                                ICON + (lift * 6 + tool) * 3 + v,
                                "icon_" + lift + "_" + tool + "_" + v,
                                iconArt[tool][v],
                                panelTop + 11 - iconRaise[v]));
            }
            JsonObject imageFont = new JsonObject();
            imageFont.add("providers", imageProviders);
            builder.addStringData("assets/create/font/" + IMAGE_FONT + ".json", imageFont.toString());

            JsonObject spaces = new JsonObject();
            for (int bit = 0; bit <= 10; bit++) {
                spaces.addProperty(Character.toString(SPACE + bit), 1 << bit);
                spaces.addProperty(Character.toString(SPACE + 32 + bit), -(1 << bit));
            }
            builder.addStringData(
                    "assets/create/font/" + SPACE_FONT + ".json",
                    font(space(spaces)).toString());

            final JsonArray layout = PolymerArtKit.asciiRows(builder);
            BufferedImage ascii = PolymerArtKit.image(builder, "assets/minecraft/textures/font/ascii.png");
            int cell = ascii.getWidth() / 16;
            Map<Integer, Integer> text = new HashMap<>();
            for (int row = 0; row < layout.size(); row++) {
                int[] codes = layout.get(row).getAsString().codePoints().toArray();
                for (int col = 0; col < codes.length; col++) {
                    if (codes[col] == 0 || codes[col] == ' ') continue;
                    text.put(codes[col], measured(ascii.getSubimage(col * cell, row * cell, cell, cell)) + 1);
                }
            }
            Map<String, String> atlases = new HashMap<>();
            JsonObject blank = new JsonObject();
            blank.addProperty(" ", 4);
            for (int lift = 0; lift < LEVELS; lift++)
                for (int row = 0; row < 6; row++) {
                    if (row >= 2 && lift < TOOLTIP_MIN_LIFT) continue;
                    int ascent = 7 - textTop(lift, row), height = Math.max(cell, ascent);
                    int alpha = row >= 2 ? lift * 255 / FOCUS_LIFT : 255;
                    String file = atlases.computeIfAbsent(height + "_" + alpha, key -> {
                        if (height == cell && alpha == 255) return "minecraft:font/ascii.png";
                        BufferedImage padded = new BufferedImage(
                                ascii.getWidth(), layout.size() * height, BufferedImage.TYPE_INT_ARGB);
                        for (int r = 0; r < layout.size(); r++)
                            for (int y = 0; y < cell; y++)
                                for (int x = 0; x < ascii.getWidth(); x++) {
                                    int src = ascii.getRGB(x, r * cell + y);
                                    padded.setRGB(
                                            x, r * height + y, ((src >>> 24) * alpha / 255) << 24 | src & 0xFFFFFF);
                                }
                        try {
                            return png(builder, "ascii_" + height + "_" + alpha, padded);
                        } catch (IOException error) {
                            throw new UncheckedIOException(error);
                        }
                    });
                    JsonObject bitmap = PolymerGuiGlyphs.bitmapProvider(file, height, ascent);
                    bitmap.add("chars", layout.deepCopy());
                    builder.addStringData(
                            "assets/create/font/" + textFont(lift, row) + ".json",
                            font(space(blank), bitmap).toString());
                }
            imageAdvance = Map.copyOf(images);
            textAdvance = Map.copyOf(text);
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot build schematic tool HUD", error);
        }
    }

    private static JsonObject space(JsonObject advances) {
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "space");
        provider.add("advances", advances.deepCopy());
        return provider;
    }

    private static JsonObject font(JsonObject... providers) {
        JsonArray list = new JsonArray();
        for (JsonObject provider : providers) list.add(provider);
        JsonObject font = new JsonObject();
        font.add("providers", list);
        return font;
    }

    private static JsonObject image(
            ResourcePackBuilder builder,
            Map<Integer, Integer> advances,
            int code,
            String name,
            BufferedImage art,
            int top)
            throws IOException {
        int ascent = 7 - top, height = Math.max(art.getHeight(), ascent);
        BufferedImage cellImage = art;
        if (height > art.getHeight()) {
            cellImage = new BufferedImage(art.getWidth(), height, BufferedImage.TYPE_INT_ARGB);
            cellImage.getGraphics().drawImage(art, 0, 0, null);
        }
        advances.put(code, measured(cellImage) + 1);
        JsonObject provider =
                PolymerGuiGlyphs.bitmapProvider(png(builder, "schematic_hud_" + name, cellImage), height, ascent);
        JsonArray chars = new JsonArray();
        chars.add(Character.toString(code));
        provider.add("chars", chars);
        return provider;
    }

    private static int measured(BufferedImage image) {
        for (int x = image.getWidth() - 1; x >= 0; x--)
            for (int y = 0; y < image.getHeight(); y++) if ((image.getRGB(x, y) >>> 24) != 0) return x + 1;
        return 0;
    }

    private static BufferedImage filled(int width, int height, int argb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) image.setRGB(x, y, argb);
        return image;
    }

    private static int tint(int base, int rgb, int alpha) {
        int a = (base >>> 24) * alpha / 255;
        int r = (base >> 16 & 0xFF) * (rgb >> 16 & 0xFF) / 255,
                g = (base >> 8 & 0xFF) * (rgb >> 8 & 0xFF) / 255,
                b = (base & 0xFF) * (rgb & 0xFF) / 255;
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static void blend(BufferedImage image, int x, int y, int argb) {
        int sa = argb >>> 24;
        if (sa == 0) return;
        int dst = image.getRGB(x, y), da = dst >>> 24;
        int oa = sa + da * (255 - sa) / 255;
        if (oa == 0) return;
        int[] out = new int[3];
        for (int c = 0; c < 3; c++) {
            int shift = 16 - c * 8;
            int s = argb >> shift & 0xFF, d = dst >> shift & 0xFF;
            out[c] = (s * sa + d * da * (255 - sa) / 255) / oa;
        }
        image.setRGB(x, y, oa << 24 | out[0] << 16 | out[1] << 8 | out[2]);
    }

    private static String png(ResourcePackBuilder builder, String name, BufferedImage image) throws IOException {
        builder.addData("assets/create/textures/font/schematic_hud/" + name + ".png", PngCodec.encode(image));
        return "create:font/schematic_hud/" + name + ".png";
    }
}
