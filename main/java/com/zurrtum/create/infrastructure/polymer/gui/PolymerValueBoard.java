package com.zurrtum.create.infrastructure.polymer.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.veeda.createpoly.PatchAssets;
import dev.veeda.createpoly.assets.CreateIds;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import javax.imageio.ImageIO;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;

final class PolymerValueBoard {
    static final int MAX_VALUE = 256;
    private static final int MILESTONE_INTERVAL = 32;
    private static final int MILESTONE_SIZE = 4;
    private static final int MILESTONE_COUNT = MAX_VALUE / MILESTONE_INTERVAL + 1;
    private static final int BAR_WIDTH = (MAX_VALUE + 1) + 1 + MILESTONE_COUNT * MILESTONE_SIZE;
    private static final int ROW_HEIGHT = 11;
    private static final int ROWS = 2;
    private static final int ICON_WIDTH = 7;
    private static final int ICON_ADVANCE = ICON_WIDTH + 1 + 1;
    private static final int DIGIT_ADVANCE = 6;
    private static final int LABEL_WIDTH = ICON_ADVANCE;
    private static final int ORIGIN_X = -7, ORIGIN_Y = -3;
    private static final int BOARD_WIDTH = LABEL_WIDTH + 14 + BAR_WIDTH + 8 - ORIGIN_X;
    private static final int BOARD_HEIGHT = ROWS * ROW_HEIGHT + 5;
    private static final int BOARD_ADVANCE = BOARD_WIDTH + 1;
    private static final int TAG_CELL_WIDTH = 40, TAG_CELL_HEIGHT = 14;
    private static final int ART_RAISE = 5;
    private static final int TEXT_COLOR = 0xFF442000;
    private static final String BOARD_CHAR = "\uE000";
    // Client font pages are 256x256; a wider glyph cannot be stitched and renders as the missing glyph, so the
    // 332px board ships as two adjacent 166px halves.
    private static final String BOARD_RIGHT_CHAR = "\uE001";

    private static final FontDescription BOARD_FONT = font("value_board_rpm");
    private static final FontDescription[] TAG_FONTS = {font("value_board_rpm_cw"), font("value_board_rpm_ccw")};
    private static final FontDescription SPACE_FONT = font("value_board_space");
    private static final FontDescription MARKER_FONT = font("value_board_marker");
    private static final FontDescription SLIDER_FONT = font("slider_art");
    static final int SLIDER_WIDTH = 240;

    private static final int SLIDER_HEIGHT = 20;
    private static final int[] ASCII_ADVANCE = {
        4, 2, 4, 6, 6, 6, 6, 2, 4, 4, 4, 6, 2, 6, 2, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 2, 2, 5, 6, 5, 6,
        7, 6, 6, 6, 6, 6, 6, 6, 6, 4, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 4, 6, 4, 6, 6,
        3, 6, 6, 6, 6, 6, 5, 6, 6, 2, 6, 5, 3, 6, 6, 6, 6, 6, 6, 6, 4, 6, 6, 6, 6, 6, 6, 4, 2, 4, 7
    };

    private static final String[][] DIGITS = {
        {".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."},
        {"..#..", ".##..", "..#..", "..#..", "..#..", "..#..", "#####"},
        {".###.", "#...#", "....#", "..##.", ".#...", "#...#", "#####"},
        {".###.", "#...#", "....#", "..##.", "....#", "#...#", ".###."},
        {"...##", "..#.#", ".#..#", "#...#", "#####", "....#", "....#"},
        {"#####", "#....", "####.", "....#", "....#", "#...#", ".###."},
        {"..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."},
        {"#####", "#...#", "....#", "...#.", "..#..", "..#..", "..#.."},
        {".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."},
        {".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."}
    };
    private static final String[] CLOCKWISE = {
        "..##.#.", ".#..##.", "#..###.", "#......", "#.....#", ".#...#.", "..###.."
    };

    private PolymerValueBoard() {}

    static Holder<Dialog> kineticForm(
            String title, String session, int revision, int row, int value, int max, Identifier action) {
        Style plain = Style.EMPTY.withColor(0xDDDDDD);
        List<DialogBody> body = List.of(
                new PlainMessage(Component.literal(title).withStyle(plain), BOARD_ADVANCE + 8),
                new PlainMessage(board(row, value, max, session, revision, action), boardBodyWidth()),
                new PlainMessage(
                        Component.literal("Click the board or use the buttons, then Confirm")
                                .withStyle(plain),
                        BOARD_ADVANCE + 8));
        var common = new CommonDialogData(
                Component.empty(),
                Optional.of(Component.literal(title)),
                true,
                false,
                DialogAction.NONE,
                body,
                List.of());
        List<ActionButton> actions = List.of(
                button("-32", 44, action, session, revision, "step", -MILESTONE_INTERVAL),
                button("-1", 44, action, session, revision, "step", -1),
                button("+1", 44, action, session, revision, "step", 1),
                button("+32", 44, action, session, revision, "step", MILESTONE_INTERVAL),
                button("⟳ ⟲", 44, action, session, revision, "flip", 0),
                new ActionButton(
                        new CommonButtonData(PolymerGuiTextures.actionLabel("Confirm"), 100),
                        Optional.of(new CustomAll(action, Optional.of(payload(session, revision, "save", 0))))));
        ActionButton cancel = new ActionButton(
                new CommonButtonData(PolymerGuiTextures.actionLabel("Cancel"), 100),
                Optional.of(new CustomAll(action, Optional.of(payload(session, revision, "cancel", 0)))));
        return Holder.direct(new MultiActionDialog(common, actions, Optional.of(cancel), 5));
    }

    private static ActionButton button(
            String label, int width, Identifier action, String session, int revision, String operation, int delta) {
        return new ActionButton(
                new CommonButtonData(Component.literal(label), width),
                Optional.of(new CustomAll(action, Optional.of(payload(session, revision, operation, delta)))));
    }

    private static CompoundTag payload(String session, int revision, String operation, int delta) {
        CompoundTag tag = new CompoundTag();
        tag.putString("session", session);
        tag.putInt("revision", revision);
        tag.putString("operation", operation);
        tag.putInt("delta", delta);
        return tag;
    }

    static Component board(int row, int value, int max, String session, int revision, Identifier action) {
        int clampedRow = Math.clamp(row, 0, ROWS - 1);
        int clampedValue = Math.clamp(value, 1, MAX_VALUE);
        int tagLeft = tagLeft(clampedValue);
        int tagAdvance = tagWidth(clampedValue) + 1;
        Style art = Style.EMPTY.withColor(0xFFFFFF).withoutShadow();
        MutableComponent text = Component.empty();
        text.append(Component.literal(" \n"));
        text.append(hits(0, max, session, revision, action));
        text.append(space(-BOARD_ADVANCE));
        text.append(Component.literal(BOARD_CHAR).withStyle(art.withFont(BOARD_FONT)));
        text.append(space(-1));
        text.append(Component.literal(BOARD_RIGHT_CHAR).withStyle(art.withFont(BOARD_FONT)));
        text.append(space(tagLeft - BOARD_ADVANCE));
        text.append(Component.literal(Character.toString(0xE100 + clampedValue - 1))
                .withStyle(art.withFont(TAG_FONTS[clampedRow])));
        text.append(space(BOARD_ADVANCE - tagLeft - tagAdvance));
        text.append(Component.literal("\n"));
        text.append(hits(1, max, session, revision, action));
        text.append(Component.literal("\n "));
        return text;
    }

    static int boardBodyWidth() {
        return BOARD_ADVANCE + 32;
    }

    private static Component hits(int row, int max, String session, int revision, Identifier action) {
        int start = LABEL_WIDTH + 14 - ORIGIN_X;
        MutableComponent line =
                Component.empty().append(space(start)).append(marker()).append(space(-2));
        int runValue = -1, runWidth = 0;
        for (int x = start; x <= BOARD_WIDTH; x++) {
            int closest = x < BOARD_WIDTH ? closestValue(x + 0.5f) : -1;
            if (closest != runValue && runWidth > 0) {
                line.append(hit(row, Math.min(runValue, max), runWidth, session, revision, action));
                runWidth = 0;
            }
            runValue = closest;
            runWidth++;
        }
        return line.append(space(-1)).append(marker()).append(space(BOARD_ADVANCE - BOARD_WIDTH - 1));
    }

    private static Component marker() {
        return Component.literal(BOARD_CHAR)
                .withStyle(Style.EMPTY.withColor(0xFFFFFF).withoutShadow().withFont(MARKER_FONT));
    }

    private static Component hit(int row, int value, int width, String session, int revision, Identifier action) {
        CompoundTag tag = payload(session, revision, "set", 0);
        tag.putInt("row", row);
        tag.putInt("value", value);
        Style style = Style.EMPTY
                .withClickEvent(new ClickEvent.Custom(action, Optional.of(tag)))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(value + (row == 0 ? "⟳" : "⟲"))));
        return Component.empty().withStyle(style).append(space(width));
    }

    private static int closestValue(float x) {
        int best = 1;
        for (int column = 1; column <= MAX_VALUE; column++)
            if (Math.abs(coordinateX(column) - x) < Math.abs(coordinateX(best) - x)) best = column;
        return best;
    }

    private static Component space(int pixels) {
        StringBuilder text = new StringBuilder();
        int remaining = Math.abs(pixels);
        for (int bit = 9; bit >= 0; bit--) {
            while (remaining >= 1 << bit) {
                text.append((char) ((pixels < 0 ? 0xE010 : 0xE000) + bit + 1));
                remaining -= 1 << bit;
            }
        }
        return Component.literal(text.toString()).withStyle(Style.EMPTY.withFont(SPACE_FONT));
    }

    private static int textWidth(int value) {
        return Integer.toString(value).length() * DIGIT_ADVANCE + ICON_ADVANCE;
    }

    private static int cursorWidth(int value) {
        return textWidth(value) / 2 * 2 + 3;
    }

    static int tagWidth(int value) {
        return cursorWidth(value) + 6;
    }

    static int tagLeft(int column) {
        int cursorX = (int) (coordinateX(column) + ORIGIN_X) - cursorWidth(column) / 2;
        return cursorX - 3 - ORIGIN_X;
    }

    private static float coordinateX(int column) {
        float x = (Math.max(1, column) - 1) / MILESTONE_INTERVAL * MILESTONE_SIZE + column + 1.5f;
        x += LABEL_WIDTH + 14 + 4;
        if (column % MILESTONE_INTERVAL == 0) x += MILESTONE_SIZE / 2;
        if (column > 0) x += MILESTONE_SIZE;
        return x - ORIGIN_X;
    }

    static int tagTop(int row) {
        int cursorY = (int) ((row + 0.5f) * ROW_HEIGHT - 0.5f) - 7;
        return cursorY - ORIGIN_Y;
    }

    static void addAssets(ResourcePackBuilder builder) {
        BufferedImage source;
        try (InputStream stream = PatchAssets.open("/assets/create/textures/gui/value_settings.png")) {
            if (stream == null) throw new IOException("Create value-settings texture is missing");
            source = ImageIO.read(stream);
            if (source == null) throw new IOException("Create value-settings texture cannot be decoded");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot generate RPM value board", exception);
        }
        writeAssets(source, (path, bytes) -> builder.addData(path, bytes));
    }

    static void writeAssets(BufferedImage source, BiConsumer<String, byte[]> out) {
        if (BOARD_WIDTH % 2 != 0 || BOARD_WIDTH / 2 > 256 || SLIDER_WIDTH > 256 || TAG_CELL_WIDTH > 256)
            throw new IllegalStateException("Font glyphs must fit the client's 256px font page");
        Canvas board = new Canvas(source, BOARD_WIDTH, BOARD_HEIGHT);
        drawBoard(board);
        Canvas tags = new Canvas(source, TAG_CELL_WIDTH * 16, TAG_CELL_HEIGHT * 16 * ROWS);
        for (int row = 0; row < ROWS; row++)
            for (int value = 1; value <= MAX_VALUE; value++)
                drawTag(
                        tags,
                        row,
                        value,
                        (value - 1) % 16 * TAG_CELL_WIDTH,
                        (row * 16 + (value - 1) / 16) * TAG_CELL_HEIGHT);
        addPng(out, "assets/create/textures/font/value_board_rpm.png", board.image);
        BufferedImage marker = new BufferedImage(1, 9, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 9; y++) marker.setRGB(0, y, 0x01FFFFFF);
        addPng(out, "assets/create/textures/font/value_board_marker.png", marker);
        out.accept(
                "assets/create/font/value_board_marker.json",
                fontJson(bitmap("create:font/value_board_marker.png", 9, 7, List.of(BOARD_CHAR)))
                        .getBytes(StandardCharsets.UTF_8));
        Canvas slider = new Canvas(source, SLIDER_WIDTH, SLIDER_HEIGHT);
        slider.stretched(2, 2, SLIDER_WIDTH - 4, SLIDER_HEIGHT - 4, 75, 9, 1, 1);
        brassFrame(slider, 0, 0, SLIDER_WIDTH, SLIDER_HEIGHT);
        addPng(out, "assets/create/textures/font/slider_art.png", slider.image);
        out.accept(
                "assets/create/font/slider_art.json",
                fontJson(bitmap("create:font/slider_art.png", SLIDER_HEIGHT, 13, List.of(BOARD_CHAR)))
                        .getBytes(StandardCharsets.UTF_8));
        addPng(out, "assets/create/textures/font/value_board_rpm_tags.png", tags.image);

        out.accept(
                "assets/create/font/value_board_rpm.json",
                fontJson(bitmap(
                                "create:font/value_board_rpm.png",
                                BOARD_HEIGHT,
                                7 + ART_RAISE,
                                List.of(BOARD_CHAR + BOARD_RIGHT_CHAR)))
                        .getBytes(StandardCharsets.UTF_8));
        for (int row = 0; row < ROWS; row++) {
            List<String> chars = new ArrayList<>();
            for (int line = 0; line < 16 * ROWS; line++) {
                StringBuilder text = new StringBuilder();
                for (int column = 0; column < 16; column++)
                    text.append(line / 16 == row ? (char) (0xE100 + line % 16 * 16 + column) : '\u0000');
                chars.add(text.toString());
            }
            out.accept(
                    "assets/create/font/value_board_rpm_" + (row == 0 ? "cw" : "ccw") + ".json",
                    fontJson(bitmap(
                                    "create:font/value_board_rpm_tags.png",
                                    TAG_CELL_HEIGHT,
                                    7 + ART_RAISE - tagTop(row),
                                    chars))
                            .getBytes(StandardCharsets.UTF_8));
        }
        JsonObject advances = new JsonObject();
        for (int bit = 0; bit <= 9; bit++) {
            advances.addProperty(Character.toString(0xE000 + bit + 1), 1 << bit);
            advances.addProperty(Character.toString(0xE010 + bit + 1), -(1 << bit));
        }
        JsonObject space = new JsonObject();
        space.addProperty("type", "space");
        space.add("advances", advances);
        out.accept("assets/create/font/value_board_space.json", fontJson(space).getBytes(StandardCharsets.UTF_8));
    }

    private static void drawBoard(Canvas c) {
        int x = -ORIGIN_X, y = -ORIGIN_Y;
        int height = ROWS * ROW_HEIGHT;
        brassFrame(c, x + LABEL_WIDTH + 14, y - 3, BAR_WIDTH + 8, height + 5);
        c.stretched(x + LABEL_WIDTH + 17, y, BAR_WIDTH + 2, height - 1, 75, 9, 1, 1);
        for (int row = 0; row < ROWS; row++) {
            int rowY = y + row * ROW_HEIGHT;
            int barX = x + LABEL_WIDTH + 14 + 4;
            c.cropped(x - 4, rowY, LABEL_WIDTH + 8, ROW_HEIGHT, 0, 31);
            for (int w = 0; w < BAR_WIDTH; w += 248)
                c.cropped(barX + w, rowY + 1, Math.min(248, BAR_WIDTH - w), 8, 7, 0);
            icon(c, x, rowY + 1, row == 1);
            for (int milestone = 0; milestone < MILESTONE_COUNT; milestone++)
                c.cropped(barX + milestone * (MILESTONE_SIZE + MILESTONE_INTERVAL), rowY + 1, 7, 8, 0, 0);
        }
        brassFrame(c, x - 7, y - 3, LABEL_WIDTH + 14, height + 5);
    }

    private static void brassFrame(Canvas c, int x, int y, int w, int h) {
        c.cropped(x, y, 4, 4, 65, 9);
        c.cropped(x + w - 4, y, 4, 4, 70, 9);
        c.cropped(x, y + h - 4, 4, 4, 65, 19);
        c.cropped(x + w - 4, y + h - 4, 4, 4, 70, 19);
        if (h > 8) {
            c.stretched(x, y + 4, 3, h - 8, 65, 14, 3, 4);
            c.stretched(x + w - 3, y + 4, 3, h - 8, 71, 14, 3, 4);
        }
        if (w > 8) {
            c.cropped(x + 4, y, w - 8, 3, 0, 24);
            c.cropped(x + 4, y + h - 3, w - 8, 3, 0, 27);
        }
    }

    private static void drawTag(Canvas c, int row, int value, int x, int y) {
        int width = cursorWidth(value);
        c.cropped(x, y, 3, 14, 0, 9);
        c.cropped(x + 3, y, width, 14, 4, 9);
        c.cropped(x + 3 + width, y, 3, 14, 61, 9);
        int textX = x + 5;
        for (char digit : Integer.toString(value).toCharArray()) {
            String[] rows = DIGITS[digit - '0'];
            for (int py = 0; py < 7; py++)
                for (int px = 0; px < 5; px++)
                    if (rows[py].charAt(px) == '#') c.pixel(textX + px, y + 3 + py, TEXT_COLOR);
            textX += DIGIT_ADVANCE;
        }
        icon(c, textX, y + 3, row == 1);
    }

    private static void icon(Canvas c, int x, int y, boolean counterClockwise) {
        for (int bold = 0; bold <= 1; bold++)
            for (int py = 0; py < ICON_WIDTH; py++)
                for (int px = 0; px < ICON_WIDTH; px++) {
                    int sx = counterClockwise ? ICON_WIDTH - 1 - px : px;
                    if (CLOCKWISE[py].charAt(sx) == '#') c.pixel(x + px + bold, y + py, TEXT_COLOR);
                }
    }

    private static JsonObject bitmap(String file, int height, int ascent, List<String> chars) {
        JsonObject provider = PolymerGuiGlyphs.bitmapProvider(file, height, ascent);
        JsonArray rows = new JsonArray();
        chars.forEach(rows::add);
        provider.add("chars", rows);
        return provider;
    }

    private static String fontJson(JsonObject... providers) {
        JsonArray list = new JsonArray();
        for (JsonObject provider : providers) list.add(provider);
        JsonObject root = new JsonObject();
        root.add("providers", list);
        return root.toString();
    }

    private static void addPng(BiConsumer<String, byte[]> out, String path, BufferedImage image) {
        try {
            out.accept(path, PngCodec.encode(image));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot encode " + path, exception);
        }
    }

    private static FontDescription font(String path) {
        return new FontDescription.Resource(CreateIds.id(path));
    }

    private static final class Canvas {
        final BufferedImage source, image;

        Canvas(BufferedImage source, int width, int height) {
            this.source = source;
            this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }

        void cropped(int x, int y, int w, int h, int u, int v) {
            for (int dy = 0; dy < h; dy++)
                for (int dx = 0; dx < w; dx++)
                    pixel(x + dx, y + dy, source.getRGB((u + dx) % source.getWidth(), (v + dy) % source.getHeight()));
        }

        void stretched(int x, int y, int w, int h, int u, int v, int uw, int vh) {
            for (int dy = 0; dy < h; dy++)
                for (int dx = 0; dx < w; dx++) pixel(x + dx, y + dy, source.getRGB(u + dx * uw / w, v + dy * vh / h));
        }

        void pixel(int x, int y, int argb) {
            if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) return;
            int alpha = argb >>> 24;
            if (alpha == 0) return;
            if (alpha == 255) {
                image.setRGB(x, y, argb);
                return;
            }
            int under = image.getRGB(x, y), underAlpha = under >>> 24;
            int outAlpha = alpha + underAlpha * (255 - alpha) / 255;
            int rgb = 0;
            for (int shift = 0; shift <= 16; shift += 8) {
                int top = argb >> shift & 0xFF, bottom = under >> shift & 0xFF;
                int value = (top * alpha + bottom * underAlpha * (255 - alpha) / 255) / Math.max(1, outAlpha);
                rgb |= Math.min(255, value) << shift;
            }
            image.setRGB(x, y, outAlpha << 24 | rgb);
        }
    }
}
