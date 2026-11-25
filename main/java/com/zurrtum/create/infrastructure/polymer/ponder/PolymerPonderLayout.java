package com.zurrtum.create.infrastructure.polymer.ponder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerArtKit;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerGuiGlyphs;
import dev.veeda.createpoly.assets.CreateIds;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.joml.Vector3f;

final class PolymerPonderLayout {
    static final int WIDTH = 450, SCENE_WIDTH = 256, SCENE_HEIGHT = 150;
    private static final int PADDING = 4, BODY_SPACING = 10, ICON = 16, ICON_SPACING = 2;
    static final int HEADER_TEXT = WIDTH - ICON - ICON_SPACING - 2 * PADDING;

    static final int HEADER_MESSAGE = HEADER_TEXT + 2 * PADDING;
    private static final int CONTROLS = WIDTH - 2 * PADDING, CENTRE = CONTROLS / 2;
    private static final int HEADER_ORIGIN = ICON + ICON_SPACING + PADDING;
    private static final int BUTTON = 20, BAR_X = CENTRE - 112, BAR_W = 224, BAR_H = 7, BAR_LINE_TOP = 18, BAR_ROW = 21;
    static final int FLASH_LEVELS = 3;

    private static final FontDescription FONT = new FontDescription.Resource(CreateIds.id("ponder_layout"));
    private static final FontDescription SPACE_FONT = new FontDescription.Resource(CreateIds.id("value_board_space"));
    private static final Style ART =
            Style.EMPTY.withFont(FONT).withColor(0xFFFFFF).withoutShadow();

    static int glyphCodepoint(int index) {
        if (index > 0xFFFFD - 0xF0000)
            throw new IllegalStateException("Ponder layout glyphs exceed Private Use Area-A");
        return 0xF0000 + index;
    }

    private record Glyph(String name, int ch, int width, int height, int ascent, String source, String file) {
        int advance() {
            return width + 1;
        }
    }

    record Control(String operation, String label, int icon, int x) {}

    static final List<Control> CONTROLS_ROW = List.of(
            new Control("identify", "Identify (not available here)", 3 + 2 * 16, CENTRE - 96),
            new Control("previous", "Previous Scene", 2 * 16, CENTRE - 38),
            new Control("close", "Close", 1 + 2 * 16, CENTRE - 10),
            new Control("next", "Next Scene", 2 + 2 * 16, CENTRE + 18),
            new Control("replay", "Replay", 4 + 2 * 16, CENTRE + 76),
            new Control("back", "Think Back", 2 * 16, 0));

    static final int SIDE_MESSAGE = WIDTH - SCENE_WIDTH - 2, SIDE_TEXT = SIDE_MESSAGE - 2 * PADDING;
    static final int SIDE_LINES = 15, CALLOUT_Y = 32;
    private static final int SCENE_GAP = 6;
    private static final int SCENE_ASCENT = 14;
    static final int CAPTION_TEXT = SIDE_TEXT - 10;

    private static final Map<String, Glyph> GLYPHS = new LinkedHashMap<>();
    private static final Map<String, String> ALIASES = new HashMap<>();

    private static final Set<String> REGISTERED = new HashSet<>();

    private static void alias(String name, String canonical, int width, int height, int ascent) {
        if (!GLYPHS.containsKey(canonical)) glyph(canonical, width, height, ascent);
        ALIASES.put(name, canonical);
    }

    private static void glyph(String name, int width, int height, int ascent) {
        glyph(name, width, height, ascent, name);
    }

    private static void glyph(String name, int width, int height, int ascent, String source) {
        if (GLYPHS.containsKey(name)) throw new IllegalStateException("Ponder layout glyph registered twice: " + name);
        int padded = Math.max(height, ascent);
        GLYPHS.put(
                name,
                new Glyph(
                        name,
                        glyphCodepoint(GLYPHS.size()),
                        width,
                        padded,
                        ascent,
                        source,
                        padded == height ? source : source + "_h" + padded));
    }

    static {
        glyph("header_box", 30, 30, 7 + 2);
        glyph("header_streak", 200, 26, 7 + 2);
        glyph("arrows_left", CENTRE, BUTTON, 7);
        glyph("arrows_right", CONTROLS - CENTRE - 1, BUTTON, 7);
        for (Control control : CONTROLS_ROW)
            for (String state : List.of("on", "off")) {
                glyph(control.operation + "_" + state, BUTTON, BUTTON, 7);
                glyph(control.operation + "_" + state + "_low", BUTTON, BUTTON - 9, 7);
            }
        for (int level = 1; level <= FLASH_LEVELS; level++) {
            glyph("next_flash" + level, BUTTON, BUTTON, 7);
            glyph("next_flash" + level + "_low", BUTTON, BUTTON - 9, 7);
        }
        glyph("tag_button_on", BUTTON, BUTTON, 7 - 4);
        glyph("tag_button_missing", BUTTON, BUTTON, 7 - 4);
        glyph("tag_click", BUTTON, 9, 7);
        glyph("tag_frame", BUTTON, BUTTON, 13);
        glyph("tag_associated", PolymerArtKit.width("Associated Entries") + 18, 18, 7 - 2);
    }

    static synchronized void register(PolymerPonderScene scene) {
        if (!REGISTERED.add(scene.id)) return;
        for (int level = 0; level <= PolymerPonderDialog.BAR_LEVELS; level++)
            for (int zone = 0; zone < scene.seekZones.length; zone++)
                alias(
                        "bar_" + scene.id + "_" + level + "_" + zone,
                        barSlice(scene, level, zone),
                        zoneEnd(scene, zone) - zoneStart(scene, zone),
                        BAR_H,
                        7 - (BAR_ROW - BAR_LINE_TOP));
        for (int caption = 1; caption <= scene.captions.size(); caption++)
            for (int view = 0; view < scene.views(); view++) {
                CaptionBox box = captionBox(scene, caption, view);
                int h = box.height(), ascent = SCENE_ASCENT - box.top();
                for (String side : new String[] {"L", "R"})
                    if (!GLYPHS.containsKey(boxPiece(side, h, box.top())))
                        glyph(boxPiece(side, h, box.top()), 2, h, ascent, boxArt(side, h));
                for (int k : boxTiles(box.width() - 4))
                    if (!GLYPHS.containsKey(boxPiece("T" + k, h, box.top())))
                        glyph(boxPiece("T" + k, h, box.top()), k, h, ascent, boxArt("T" + k, h));
                for (int i = 0; i < box.lines().length; i++) TEXT_ROWS.add(box.top() + 4 + 9 * i);
                for (int level = 1; level < PolymerPonderScene.FADES.length; level++)
                    for (int[] piece : linePieces(box, level))
                        alias(
                                lineName(scene, caption, view, level, piece[0]),
                                lineArt(piece[1] - piece[0], SCENE_ASCENT - box.line()),
                                piece[1] - piece[0],
                                2,
                                SCENE_ASCENT - box.line());
            }
        for (int index = 0; index < scene.outlines.size(); index++)
            for (int view = 0; view < scene.views(); view++)
                for (int state = PolymerPonderScene.firstOutlineState(scene.outlines.get(index));
                        state < PolymerPonderScene.OUTLINE_STATES;
                        state++) {
                    OutlineImage outline = outline(scene, scene.outlines.get(index), view, state);
                    glyph(
                            outlineName(scene, index, view, state),
                            outline.image().getWidth(),
                            outline.image().getHeight(),
                            SCENE_ASCENT - outline.y());
                }
        for (int from = 0; from < upNextWidth(scene) + 8; from += 255)
            glyph(
                    "upnext_" + scene.id + "_" + from,
                    Math.min(255, upNextWidth(scene) + 8 - from),
                    UP_NEXT_H,
                    SCENE_ASCENT - UP_NEXT_TOP);
    }

    private static String boxPiece(String piece, int h, int top) {
        return "cbox_" + piece + "_" + h + "_" + top;
    }

    private static String boxArt(String piece, int h) {
        return "cboxart_" + piece + "_" + h;
    }

    private static List<Integer> boxTiles(int width) {
        List<Integer> tiles = new ArrayList<>();
        for (int k = 128; k >= 1; k >>= 1)
            while (width >= k) {
                tiles.add(k);
                width -= k;
            }
        return tiles;
    }

    private static final SortedSet<Integer> TEXT_ROWS = new TreeSet<>();

    static FontDescription textFont(int row) {
        return new FontDescription.Resource(CreateIds.id("ponder_text_" + row));
    }

    private static int textColor(int level) {
        float fade = PolymerPonderScene.FADES[level];
        return Math.round((TEXT_COLOR >> 16 & 0xFF) * fade) << 16
                | Math.round((TEXT_COLOR >> 8 & 0xFF) * fade) << 8
                | Math.round((TEXT_COLOR & 0xFF) * fade);
    }

    private static String asciiText(String value) {
        StringBuilder text = new StringBuilder();
        value.codePoints().forEach(cp -> text.append(cp >= 32 && cp < 127 ? (char) cp : '?'));
        return text.toString();
    }

    private static String barSlice(PolymerPonderScene scene, int level, int zone) {
        int fill = (BAR_W - 4) * level / PolymerPonderDialog.BAR_LEVELS;
        int reach = Math.clamp(2 + fill - zoneStart(scene, zone), 0, zoneEnd(scene, zone) - zoneStart(scene, zone));
        return "bar_" + scene.id + "_z" + zone + "_f" + reach;
    }

    private static String lineArt(int length, int ascent) {
        return "line_l" + length + "_a" + ascent;
    }

    private static Style captionStyle(int level) {
        int v = Math.round(255 * PolymerPonderScene.FADES[level]);
        return Style.EMPTY.withColor(v << 16 | v << 8 | v);
    }

    private static String lineName(PolymerPonderScene scene, int caption, int view, int level, int from) {
        return "line_" + scene.id + "_" + caption + "_" + view + "_" + level + "_"
                + (from < 0 ? "m" + -from : Integer.toString(from));
    }

    private static String outlineName(PolymerPonderScene scene, int index, int view, int state) {
        return "outline_" + scene.id + "_" + index + "_" + view + "_" + state;
    }

    static final int GRID_MESSAGE = WIDTH - 3, GRID_TEXT = GRID_MESSAGE - 2 * PADDING, GRID_CENTRE = GRID_TEXT / 2;
    static final int GRID_TOP_LINES = 3, ROW_LINES = 4;

    static final int DESCRIPTION_W = 220;

    static int[] rowCounts(PolymerPonderScenes.Tag tag) {
        int count = tag.items().size(), rows = Math.clamp((int) Math.ceil(count / 11.0), 1, 3);
        int[] counts = new int[rows];
        for (int i = 0; i < rows; i++) counts[i] = count / rows + (i < count % rows ? 1 : 0);
        return counts;
    }

    static int gridLines(PolymerPonderScenes.Tag tag) {
        return GRID_TOP_LINES + ROW_LINES * rowCounts(tag).length;
    }

    static int[] buttonAt(PolymerPonderScenes.Tag tag, int index) {
        int[] counts = rowCounts(tag);
        int row = 0;
        while (index >= counts[row]) index -= counts[row++];
        int rowWidth = counts[row] * 28 + (counts[row] - 1) * 8;
        return new int[] {GRID_CENTRE - rowWidth / 2 + index * 36 + 4, GRID_TOP_LINES * 9 + row * 36 + 4};
    }

    private static int streakLength(PolymerPonderScenes.Tag tag) {
        int[] counts = rowCounts(tag);
        return (counts[0] * 28 + (counts[0] - 1) * 8) / 2 + 75;
    }

    private static int streakBreadth(PolymerPonderScenes.Tag tag) {
        int rows = rowCounts(tag).length;
        return rows * 28 + (rows - 1) * 8 + 10;
    }

    static synchronized void register(PolymerPonderScenes.Tag tag) {
        if (GLYPHS.containsKey("tag_streak_left_" + tag.id())) return;
        int top = GRID_TOP_LINES * 9 + (streakBreadth(tag) - 10) / 2 - streakBreadth(tag) / 2;
        glyph("tag_streak_left_" + tag.id(), streakLength(tag), streakBreadth(tag), 7 - top);
        glyph("tag_streak_right_" + tag.id(), streakLength(tag), streakBreadth(tag), 7 - top);
        int lines =
                PolymerPonderScene.wrapLines(tag.description(), DESCRIPTION_W).size();
        glyph("tag_description_" + tag.id(), DESCRIPTION_W + 14, lines * 9 + 14, 7);
    }

    static Component tagGrid(
            PolymerPonderScenes.Tag tag,
            Predicate<String> ported,
            Function<String, CompoundTag> payload,
            Function<String, Component> name) {
        Line[] rows = new Line[gridLines(tag)];
        for (int i = 0; i < rows.length; i++) rows[i] = new Line();
        rows[0].to(GRID_CENTRE - streakLength(tag))
                .glyph("tag_streak_left_" + tag.id(), Style.EMPTY)
                .to(GRID_CENTRE)
                .glyph("tag_streak_right_" + tag.id(), Style.EMPTY)
                .to(GRID_CENTRE - GLYPHS.get("tag_associated").width / 2)
                .glyph("tag_associated", Style.EMPTY);
        for (int index = 0; index < tag.items().size(); index++) {
            String item = tag.items().get(index);
            int[] at = buttonAt(tag, index);
            int line = at[1] / 9;
            boolean clickable = ported.test(item);
            MutableComponent tooltip = name.apply(item).copy();
            Style style = Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(tooltip));
            if (clickable)
                style = style.withClickEvent(
                        new ClickEvent.Custom(PolymerPonderDialog.ACTION, Optional.of(payload.apply("item:" + item))));
            rows[line].to(at[0]).glyph(clickable ? "tag_button_on" : "tag_button_missing", style);
            rows[line + 1].to(at[0]).glyph("tag_click", style);
            rows[line + 2].to(at[0]).glyph("tag_click", style);
        }
        return lines(GRID_TEXT, false, rows);
    }

    static Component tagDescription(PolymerPonderScenes.Tag tag) {
        Glyph glyph = GLYPHS.get("tag_description_" + tag.id());
        Line[] rows = new Line[(glyph.height + 8) / 9 + 1];
        rows[0] = new Line().glyph(glyph.name, Style.EMPTY);
        for (int i = 1; i < rows.length; i++) rows[i] = new Line();
        return lines(glyph.width + 1, false, rows);
    }

    static Component tagFooter(boolean back, Function<String, CompoundTag> payload) {
        Line top = new Line(), low = new Line();
        if (back) {
            Style click = Style.EMPTY
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Think Back")))
                    .withClickEvent(
                            new ClickEvent.Custom(PolymerPonderDialog.ACTION, Optional.of(payload.apply("back"))));
            top.glyph("back_on", click);
            low.glyph("back_on_low", click);
        }
        return lines(CONTROLS, false, top, low);
    }

    static Component tagButton(PolymerPonderScenes.Tag tag, Function<String, CompoundTag> payload) {
        Style click = Style.EMPTY
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(tag.title())))
                .withClickEvent(new ClickEvent.Custom(
                        PolymerPonderDialog.ACTION, Optional.of(payload.apply("tag:" + tag.id()))));
        Line first = new Line()
                .to(-2 - HEADER_ORIGIN)
                .glyph("tag_frame", Style.EMPTY)
                .to(4)
                .text(tag.title(), 0xDDEEFF)
                .withStyle(click);
        return lines(HEADER_TEXT, false, first);
    }

    static boolean registered(PolymerPonderScene scene) {
        return REGISTERED.contains(scene.id);
    }

    private PolymerPonderLayout() {}

    private static final class Line {
        final MutableComponent text = Component.empty();
        int pen;

        Line to(int x) {
            if (x != pen) text.append(PolymerArtKit.space(SPACE_FONT, x - pen));
            pen = x;
            return this;
        }

        Line glyph(String name, Style click) {
            Glyph glyph = GLYPHS.get(ALIASES.getOrDefault(name, name));
            text.append(Component.literal(Character.toString(glyph.ch())).withStyle(click.applyTo(ART)));
            pen += glyph.advance();
            return this;
        }

        Line text(String value, int color) {
            text.append(Component.literal(value)
                    .withStyle(Style.EMPTY.withColor(color).withoutShadow()));
            pen += PolymerArtKit.width(value);
            return this;
        }

        Line text(String value, int color, FontDescription font) {
            text.append(Component.literal(value)
                    .withStyle(Style.EMPTY.withFont(font).withColor(color).withoutShadow()));
            pen += PolymerArtKit.width(value);
            return this;
        }

        Line withStyle(Style style) {
            var siblings = text.getSiblings();
            var last = (MutableComponent) siblings.getLast();
            siblings.set(siblings.size() - 1, last.withStyle(style.applyTo(last.getStyle())));
            return this;
        }
    }

    private static Component lines(int width, boolean reserve, Line... lines) {
        MutableComponent result = Component.empty();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) result.append(Component.literal("\n"));
            result.append(lines[i].to(width).text);
        }
        return reserve ? result.append(Component.literal("\n ")) : result;
    }

    private static int zoneStart(PolymerPonderScene scene, int zone) {
        return zone == 0 ? 0 : BAR_W * scene.seekZones[zone][0] / scene.length;
    }

    private static int zoneEnd(PolymerPonderScene scene, int zone) {
        return zone == scene.seekZones.length - 1 ? BAR_W - 1 : zoneStart(scene, zone + 1);
    }

    static String seekLabel(PolymerPonderScene scene, int target) {
        if (target == 0) return "Jump to start";
        if (target >= scene.length) return "Jump to end";
        return "Jump to keyframe";
    }

    static Component header(String title) {
        Line first = new Line()
                .to(26 - HEADER_ORIGIN)
                .glyph("header_streak", Style.EMPTY)
                .to(-7 - HEADER_ORIGIN)
                .glyph("header_box", Style.EMPTY)
                .to(38 - HEADER_ORIGIN)
                .text("Pondering about...", 0xA8A8A8);
        Line second = new Line().to(38 - HEADER_ORIGIN).text(title, 0xEEEEEE);
        return lines(HEADER_TEXT, true, first, second);
    }

    static Component controls(
            PolymerPonderScene scene,
            int caption,
            int bar,
            int flash,
            Predicate<String> enabled,
            Function<String, CompoundTag> payload) {
        Line top = new Line();
        top.to(0).glyph("arrows_left", Style.EMPTY).to(CENTRE).glyph("arrows_right", Style.EMPTY);
        Line low = new Line();
        for (Control control : CONTROLS_ROW) {
            boolean on = enabled.test(control.operation);
            if (!on
                    && (control.operation.equals("previous")
                            || control.operation.equals("next")
                            || control.operation.equals("back"))) continue;
            String state = on && flash > 0 && control.operation.equals("next") ? "flash" + flash : on ? "on" : "off";
            Style click = Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(Component.literal(control.label)));
            if (on)
                click = click.withClickEvent(new ClickEvent.Custom(
                        PolymerPonderDialog.ACTION, Optional.of(payload.apply(control.operation))));
            String name = state.startsWith("flash") ? "next_" + state : control.operation + "_" + state;
            top.to(control.x).glyph(name, click);
            low.to(control.x).glyph(name + "_low", click);
        }
        Line track = new Line();
        for (int zone = 0; zone < scene.seekZones.length; zone++) {
            int target = scene.seekZones[zone][2];
            Style click = Style.EMPTY
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal(seekLabel(scene, target))))
                    .withClickEvent(new ClickEvent.Custom(
                            PolymerPonderDialog.ACTION, Optional.of(payload.apply("seek:" + target))));
            track.to(BAR_X + zoneStart(scene, zone)).glyph("bar_" + scene.id + "_" + bar + "_" + zone, click);
        }
        return lines(CONTROLS, false, top, low, track);
    }

    static Component callout(PolymerPonderScene scene, int tick, int view, PolymerPonderScene upNext) {
        Line first = new Line();
        for (int index = 0; index < scene.outlines.size(); index++) {
            var outline = scene.outlines.get(index);
            int state = PolymerPonderScene.outlineState(outline, tick);
            if (state < 0 || !scene.facing(outline, view)) continue;
            first.to(outline(scene, outline, view, state).x() - SCENE_WIDTH - SCENE_GAP)
                    .glyph(outlineName(scene, index, view, state), Style.EMPTY);
        }
        for (int caption = 1; caption <= scene.captions.size(); caption++)
            drawCaption(first, scene, caption, scene.captionFade(caption, tick), view);
        if (upNext != null)
            for (int from = 0; from < upNextWidth(upNext) + 8; from += 255)
                first.to(UP_NEXT_X - upNextWidth(upNext) / 2 - 4 + from)
                        .glyph("upnext_" + upNext.id + "_" + from, Style.EMPTY);
        Line[] rows = new Line[SIDE_LINES];
        rows[0] = first;
        for (int i = 1; i < rows.length; i++) rows[i] = new Line();
        return lines(SIDE_TEXT, false, rows);
    }

    private static void drawCaption(Line line, PolymerPonderScene scene, int caption, int level, int view) {
        if (level == 0) return;
        CaptionBox box = captionBox(scene, caption, view);
        Style tint = captionStyle(level);
        int h = box.height(), top = box.top();
        line.to(0).glyph(boxPiece("L", h, top), tint);
        int x = 2;
        for (int k : boxTiles(box.width() - 4)) {
            line.to(x).glyph(boxPiece("T" + k, h, top), tint);
            x += k;
        }
        line.to(box.width() - 2).glyph(boxPiece("R", h, top), tint);
        for (int i = 0; i < box.lines().length; i++)
            line.to(4).text(asciiText(box.lines()[i]), textColor(level), textFont(top + 4 + 9 * i));
        for (int[] piece : linePieces(box, level))
            line.to(piece[0]).glyph(lineName(scene, caption, view, level, piece[0]), Style.EMPTY);
    }

    static Component captionLayer(PolymerPonderScene scene, int caption, int level, int view) {
        Line line = new Line();
        drawCaption(line, scene, caption, level, view);
        return lines(SIDE_TEXT, false, line);
    }

    private static void textFonts(ResourcePackBuilder builder) throws IOException {
        JsonArray chars = PolymerArtKit.asciiRows(builder);
        BufferedImage ascii = PolymerArtKit.image(builder, "assets/minecraft/textures/font/ascii.png");
        int cell = ascii.getWidth() / 16;
        Set<Integer> padded = new HashSet<>();
        for (int row : TEXT_ROWS) {
            int ascent = SCENE_ASCENT - row, height = Math.max(cell, ascent);
            String file = "minecraft:font/ascii.png";
            if (height > cell) {
                file = "create:font/ponder_text_h" + height + ".png";
                if (padded.add(height)) {
                    BufferedImage tall = new BufferedImage(
                            ascii.getWidth(), ascii.getHeight() / cell * height, BufferedImage.TYPE_INT_ARGB);
                    for (int r = 0; r < ascii.getHeight() / cell; r++)
                        tall.createGraphics()
                                .drawImage(ascii.getSubimage(0, r * cell, ascii.getWidth(), cell), 0, r * height, null);
                    builder.addData(
                            "assets/create/textures/font/ponder_text_h" + height + ".png", PngCodec.encode(tall));
                }
            }
            JsonObject space = new JsonObject();
            space.addProperty("type", "space");
            space.add("advances", JsonParser.parseString("{\" \":4}"));
            JsonObject bitmap = PolymerGuiGlyphs.bitmapProvider(file, height, ascent);
            bitmap.add("chars", chars);
            JsonArray providers = new JsonArray();
            providers.add(space);
            providers.add(bitmap);
            JsonObject font = new JsonObject();
            font.add("providers", providers);
            builder.addStringData("assets/create/font/ponder_text_" + row + ".json", font.toString());
        }
    }

    static List<String> assetPaths() {
        List<String> paths = new ArrayList<>(List.of("assets/create/font/ponder_layout.json"));
        for (int row : TEXT_ROWS) paths.add("assets/create/font/ponder_text_" + row + ".json");
        for (Sheet sheet : sheets()) paths.add("assets/create/textures/font/ponder_layout/" + sheet.file() + ".png");
        return paths;
    }

    static final int SHEET_WIDTH = 2048, SHEET_HEIGHT = 2048;

    record Sheet(String file, int width, int height, int ascent, int columns, List<Glyph> glyphs) {
        int rows() {
            return (glyphs.size() + columns - 1) / columns;
        }
    }

    static List<Sheet> sheets() {
        Map<String, List<Glyph>> groups = new LinkedHashMap<>();
        for (Glyph glyph : GLYPHS.values())
            groups.computeIfAbsent(glyph.width + "x" + glyph.height + "a" + glyph.ascent, k -> new ArrayList<>())
                    .add(glyph);
        List<Sheet> sheets = new ArrayList<>();
        for (var group : groups.entrySet()) {
            Glyph first = group.getValue().getFirst();
            int columns = Math.max(1, Math.min(group.getValue().size(), SHEET_WIDTH / first.width));
            int perSheet = columns * Math.max(1, SHEET_HEIGHT / first.height);
            for (int start = 0, part = 0; start < group.getValue().size(); start += perSheet, part++) {
                List<Glyph> glyphs = group.getValue()
                        .subList(start, Math.min(group.getValue().size(), start + perSheet));
                sheets.add(new Sheet(
                        "s" + group.getKey() + "_" + part,
                        first.width,
                        first.height,
                        first.ascent,
                        Math.min(columns, glyphs.size()),
                        List.copyOf(glyphs)));
            }
        }
        return sheets;
    }

    static void addAssets(
            ResourcePackBuilder builder, List<PolymerPonderScene> scenes, Map<String, BufferedImage> preview)
            throws IOException {
        for (PolymerPonderScene scene : scenes) register(scene);
        for (PolymerPonderScenes.Tag tag : PolymerPonderScenes.TAGS) register(tag);
        BufferedImage icons = PolymerArtKit.image(builder, "assets/ponder/textures/gui/widgets.png");
        Map<Integer, BufferedImage> ascii = PolymerArtKit.asciiGlyphs(builder);
        Map<String, BufferedImage> images = new LinkedHashMap<>();
        images.put("header_box", box(30, 30, IDLE_TOP, IDLE_BOTTOM, false));
        images.put("tag_button_on", box(BUTTON, BUTTON, BUTTON_TOP, BUTTON_BOTTOM, false));
        images.put("tag_button_missing", box(BUTTON, BUTTON, MISSING_TOP, MISSING_BOTTOM, false));
        images.put("tag_click", new BufferedImage(BUTTON, 9, BufferedImage.TYPE_INT_ARGB));
        images.put("tag_frame", box(BUTTON, BUTTON, BUTTON_TOP, BUTTON_BOTTOM, false));
        BufferedImage associated =
                new BufferedImage(GLYPHS.get("tag_associated").width, 18, BufferedImage.TYPE_INT_ARGB);
        boxElement(associated, 0, 0, associated.getWidth() - 8, 10, IDLE_TOP, IDLE_BOTTOM);
        PolymerArtKit.text(associated, ascii, 9, 5, "Associated Entries", 0xEEEEEE, false);
        images.put("tag_associated", associated);
        for (PolymerPonderScenes.Tag tag : PolymerPonderScenes.TAGS) {
            images.put("tag_streak_right_" + tag.id(), gridStreak(streakLength(tag), streakBreadth(tag), false));
            images.put("tag_streak_left_" + tag.id(), gridStreak(streakLength(tag), streakBreadth(tag), true));
            images.put("tag_description_" + tag.id(), descriptionImage(ascii, tag));
        }
        images.put("header_streak", streak());
        BufferedImage arrows = arrows();
        images.put("arrows_left", arrows.getSubimage(0, 0, CENTRE, BUTTON));
        images.put("arrows_right", arrows.getSubimage(CENTRE, 0, CONTROLS - CENTRE - 1, BUTTON));
        for (Control control : CONTROLS_ROW)
            for (boolean on : new boolean[] {true, false}) {
                BufferedImage button = button(icons, control.icon, on);
                String name = control.operation + "_" + (on ? "on" : "off");
                images.put(name, button);
                images.put(name + "_low", button.getSubimage(0, 9, BUTTON, BUTTON - 9));
            }
        Control next = CONTROLS_ROW.stream()
                .filter(c -> c.operation.equals("next"))
                .findFirst()
                .orElseThrow();
        for (int level = 1; level <= FLASH_LEVELS; level++) {
            BufferedImage button = flashButton(icons, next.icon, level / (float) FLASH_LEVELS);
            images.put("next_flash" + level, button);
            images.put("next_flash" + level + "_low", button.getSubimage(0, 9, BUTTON, BUTTON - 9));
        }
        for (PolymerPonderScene scene : scenes) {
            for (int level = 0; level <= PolymerPonderDialog.BAR_LEVELS; level++) {
                BufferedImage full = bar(scene, level);
                for (int zone = 0; zone < scene.seekZones.length; zone++)
                    images.putIfAbsent(
                            barSlice(scene, level, zone),
                            full.getSubimage(
                                    zoneStart(scene, zone), 0, zoneEnd(scene, zone) - zoneStart(scene, zone), BAR_H));
            }
            for (int caption = 1; caption <= scene.captions.size(); caption++)
                for (int view = 0; view < scene.views(); view++) {
                    CaptionBox box = captionBox(scene, caption, view);
                    BufferedImage art = new BufferedImage(260, box.height(), BufferedImage.TYPE_INT_ARGB);
                    boxElement(art, 0, 0, 252, box.lines().length * 9 - 1, WINDOW_TOP, WINDOW_BOTTOM);
                    images.computeIfAbsent(boxArt("L", box.height()), k -> art.getSubimage(0, 0, 2, box.height()));
                    images.computeIfAbsent(boxArt("R", box.height()), k -> art.getSubimage(258, 0, 2, box.height()));
                    for (int tile : boxTiles(box.width() - 4))
                        images.computeIfAbsent(
                                boxArt("T" + tile, box.height()), k -> art.getSubimage(2, 0, tile, box.height()));
                    for (int level = 1; level < PolymerPonderScene.FADES.length; level++)
                        for (int[] piece : linePieces(box, level))
                            images.computeIfAbsent(
                                    lineArt(piece[1] - piece[0], SCENE_ASCENT - box.line()),
                                    k -> lineImage(piece[1] - piece[0]));
                }
            for (int index = 0; index < scene.outlines.size(); index++)
                for (int view = 0; view < scene.views(); view++)
                    for (int state = PolymerPonderScene.firstOutlineState(scene.outlines.get(index));
                            state < PolymerPonderScene.OUTLINE_STATES;
                            state++)
                        images.put(
                                outlineName(scene, index, view, state),
                                outline(scene, scene.outlines.get(index), view, state)
                                        .image());
            BufferedImage upNext = upNextImage(ascii, icons, scene);
            for (int from = 0; from < upNext.getWidth(); from += 255)
                images.put(
                        "upnext_" + scene.id + "_" + from,
                        upNext.getSubimage(from, 0, Math.min(255, upNext.getWidth() - from), UP_NEXT_H));
        }
        JsonArray providers = new JsonArray();
        Map<String, BufferedImage> written = new LinkedHashMap<>();
        for (Sheet sheet : sheets()) {
            BufferedImage page = new BufferedImage(
                    sheet.width() * sheet.columns(), sheet.height() * sheet.rows(), BufferedImage.TYPE_INT_ARGB);
            var pageGraphics = page.createGraphics();
            JsonArray chars = new JsonArray();
            StringBuilder row = new StringBuilder();
            for (int i = 0; i < sheet.rows() * sheet.columns(); i++) {
                if (i < sheet.glyphs().size()) {
                    Glyph glyph = sheet.glyphs().get(i);
                    BufferedImage image = written.get(glyph.file);
                    if (image == null) {
                        BufferedImage source = images.get(glyph.source);
                        image = new BufferedImage(source.getWidth(), glyph.height, BufferedImage.TYPE_INT_ARGB);
                        var g = image.createGraphics();
                        g.drawImage(source, 0, 0, null);
                        g.dispose();
                        keepWidth(image);
                        written.put(glyph.file, image);
                    }
                    if (image.getWidth() != glyph.width || image.getHeight() != glyph.height)
                        throw new IOException("Ponder layout glyph size " + glyph.name);
                    if (preview != null) preview.put(glyph.name, image);
                    pageGraphics.drawImage(
                            image, (i % sheet.columns()) * sheet.width(), (i / sheet.columns()) * sheet.height(), null);
                    row.appendCodePoint(glyph.ch);
                } else row.append('\u0000');
                if ((i + 1) % sheet.columns() == 0) {
                    chars.add(row.toString());
                    row.setLength(0);
                }
            }
            pageGraphics.dispose();
            builder.addData(
                    "assets/create/textures/font/ponder_layout/" + sheet.file() + ".png", PngCodec.encode(page));
            JsonObject provider = PolymerGuiGlyphs.bitmapProvider(
                    "create:font/ponder_layout/" + sheet.file() + ".png", sheet.height(), sheet.ascent());
            provider.add("chars", chars);
            providers.add(provider);
        }
        JsonObject root = new JsonObject();
        root.add("providers", providers);
        builder.addStringData("assets/create/font/ponder_layout.json", root.toString());
        textFonts(builder);
    }

    private static final int IDLE_TOP = 0x40FFEEDD, IDLE_BOTTOM = 0x20FFEEDD;
    private static final int BUTTON_TOP = 0x60C0C0FF, BUTTON_BOTTOM = 0x30C0C0FF;
    private static final int DISABLED_TOP = 0x80909090, DISABLED_BOTTOM = 0x20909090;

    private static int overBlack(int argb) {
        int a = argb >>> 24;
        return 0xFF000000
                | ((argb >> 16 & 0xFF) * a / 255) << 16
                | ((argb >> 8 & 0xFF) * a / 255) << 8
                | (argb & 0xFF) * a / 255;
    }

    private static int lerp(int from, int to, float t) {
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8)
            result |= Math.round((from >>> shift & 0xFF) + ((to >>> shift & 0xFF) - (from >>> shift & 0xFF)) * t)
                    << shift;
        return result;
    }

    private static BufferedImage box(int w, int h, int top, int bottom, boolean filled) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            int border = overBlack(lerp(top, bottom, y / (float) (h - 1)));
            for (int x = 0; x < w; x++) {
                boolean edge = x == 0 || y == 0 || x == w - 1 || y == h - 1;
                if (edge) image.setRGB(x, y, border);
                else if (filled) image.setRGB(x, y, 0xFF000000);
            }
        }
        return image;
    }

    private static BufferedImage flashButton(BufferedImage icons, int icon, float amount) {
        int top = lerp(BUTTON_TOP, 0xFFFFFF | Math.min(255, (BUTTON_TOP >>> 24) + 150) << 24, amount);
        int bottom = lerp(BUTTON_BOTTOM, 0x9B9B9B | Math.min(255, (BUTTON_BOTTOM >>> 24) + 150) << 24, amount);
        return button(icons, icon, true, top, bottom);
    }

    private static BufferedImage button(BufferedImage icons, int icon, boolean enabled) {
        return button(
                icons, icon, enabled, enabled ? BUTTON_TOP : DISABLED_TOP, enabled ? BUTTON_BOTTOM : DISABLED_BOTTOM);
    }

    private static BufferedImage button(
            BufferedImage icons, int icon, boolean enabled, int borderTop, int borderBottom) {
        BufferedImage image = box(BUTTON, BUTTON, borderTop, borderBottom, true);
        BufferedImage source = icons.getSubimage(icon % 16 * 16, icon / 16 * 16, 16, 16);
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                int argb = source.getRGB(x, y);
                int a = (argb >>> 24) * (enabled ? 255 : 90) / 255;
                if (a == 0) continue;
                int under = image.getRGB(x + 2, y + 2);
                image.setRGB(x + 2, y + 2, 0xFF000000 | lerp(under & 0xFFFFFF, argb & 0xFFFFFF, a / 255f));
            }
        return image;
    }

    private static BufferedImage bar(PolymerPonderScene scene, int level) {
        BufferedImage image = box(BAR_W, BAR_H, IDLE_TOP, IDLE_BOTTOM, true);
        int inner = BAR_W - 4, fill = inner * level / PolymerPonderDialog.BAR_LEVELS;
        for (int x = 0; x < fill; x++) {
            for (int y = 2; y <= 3; y++) image.setRGB(2 + x, y, overBlack(0x80AAAADD));
            image.setRGB(2 + x, 4, overBlack(0x50AAAADD));
        }
        for (int frame : scene.keyframes) {
            if (frame == 0) continue;
            int x = 2 + inner * frame / scene.length;
            for (int y = 1; y < BAR_H - 1; y++) image.setRGB(x, y, overBlack(0x70FFFFFF));
        }
        return image;
    }

    private static final int MISSING_TOP = 0x70984500, MISSING_BOTTOM = 0x70692400;

    private static BufferedImage gridStreak(int length, int breadth, boolean mirrored) {
        BufferedImage image = new BufferedImage(length, breadth, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < length; i++) {
            float t = (i + .5f) / length;
            float alpha = t < .5f
                    ? .625f - .125f * t / .5f
                    : t < .75f ? .5f - (.5f - .0625f) * (t - .5f) / .25f : .0625f * (1 - (t - .75f) / .25f);
            int argb = Math.round(alpha * 255) << 24 | 0x101010;
            for (int y = 0; y < breadth; y++) image.setRGB(mirrored ? length - 1 - i : i, y, argb);
        }
        return image;
    }

    private static BufferedImage descriptionImage(Map<Integer, BufferedImage> ascii, PolymerPonderScenes.Tag tag) {
        List<String> lines = PolymerPonderScene.wrapLines(tag.description(), DESCRIPTION_W);
        BufferedImage image = new BufferedImage(DESCRIPTION_W + 14, lines.size() * 9 + 14, BufferedImage.TYPE_INT_ARGB);
        boxElement(image, 0, 0, DESCRIPTION_W + 6, lines.size() * 9 + 6, IDLE_TOP, IDLE_BOTTOM);
        for (int i = 0; i < lines.size(); i++)
            PolymerArtKit.text(image, ascii, 7, 7 + 9 * i, lines.get(i), 0xEEEEEE, false);
        return image;
    }

    private static BufferedImage streak() {
        BufferedImage image = new BufferedImage(200, 26, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 200; x++) {
            int alpha = Math.round(0xA0 * (1 - x / 199f));
            for (int y = 0; y < 26; y++) image.setRGB(x, y, alpha << 24);
        }
        return image;
    }

    private static BufferedImage arrows() {
        BufferedImage image = new BufferedImage(CONTROLS, BUTTON, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        int c1 = 0x40AA9999, c2 = 0x20AA9999, c3 = 0x10AA9999;
        chevron(g, CENTRE - 20, 20, 5, c1, c2);
        chevron(g, CENTRE + 20, -20, -5, c1, c2);
        chevron(g, CENTRE - 90, 70, 5, c1, c3);
        chevron(g, CENTRE + 90, -70, -5, c1, c3);
        g.dispose();
        return image;
    }

    private static void chevron(Graphics2D g, int x, int width, int indent, int from, int to) {
        int ox = x - indent, h = BUTTON;
        Polygon shape = new Polygon(
                new int[] {ox, ox + indent, ox + width + indent, ox + width, ox + width + indent, ox + indent},
                new int[] {h / 2, 0, 0, h / 2, h, h},
                6);
        g.setPaint(new GradientPaint(ox, 0, new Color(from, true), ox + width + indent, 0, new Color(to, true)));
        g.fillPolygon(shape);
    }

    private static final int TEXT_COLOR = 0xF6F6E5, LINE_SHADE = 0x414141;
    private static final int WINDOW_TOP = 0x607A6000, WINDOW_BOTTOM = 0x207A6000;

    record CaptionBox(String[] lines, int textWidth, int line, int from) {
        int width() {
            return textWidth + 8;
        }

        int height() {
            return lines.length * 9 + 7;
        }

        int top() {
            return line - 1;
        }
    }

    static CaptionBox captionBox(PolymerPonderScene scene, int caption, int view) {
        var step = scene.captions.get(caption - 1);
        String[] lines = step.lines().toArray(String[]::new);
        int textWidth = 0;
        for (String line : lines) textWidth = Math.max(textWidth, PolymerArtKit.width(line));
        int height = lines.length * 9 + 7;
        if (step.target() == null) return new CaptionBox(lines, textWidth, CALLOUT_Y, Integer.MIN_VALUE);
        var point = scene.project(step.target(), view);
        int line = Math.clamp((int) point.y(), 1, SCENE_HEIGHT - height + 1);
        return new CaptionBox(lines, textWidth, line, Math.round(point.x()) - SCENE_WIDTH - SCENE_GAP);
    }

    static List<int[]> linePieces(CaptionBox box, int level) {
        List<int[]> pieces = new ArrayList<>();
        if (box.from == Integer.MIN_VALUE || box.from == 14) return pieces;
        int length = Math.max(1, Math.round(Math.abs(14 - box.from) * PolymerPonderScene.FADES[level]));
        int start = box.from <= 14 ? box.from : box.from - length;
        int end = Math.min(start + length, SIDE_TEXT - 1);
        for (int from = start; from < end; from += 255) pieces.add(new int[] {from, Math.min(end, from + 255)});
        return pieces;
    }

    private static void boxElement(BufferedImage image, int x, int y, int w, int h, int top, int bottom) {
        fill(image, x + 1, y, x + w + 7, y + 1, 0xFF000000);
        fill(image, x, y + 1, x + 1, y + h + 7, 0xFF000000);
        fill(image, x + 1, y + h + 7, x + w + 7, y + h + 8, 0xFF000000);
        fill(image, x + w + 7, y + 1, x + w + 8, y + h + 7, 0xFF000000);
        fill(image, x + 1, y + 1, x + w + 7, y + h + 7, 0xFF000000);
        fill(image, x + 1, y + 1, x + w + 7, y + 2, overBlack(top));
        fill(image, x + 1, y + h + 6, x + w + 7, y + h + 7, overBlack(bottom));
        for (int row = 2; row < h + 6; row++) {
            int border = overBlack(lerp(top, bottom, (row + .5f - 2) / (h + 4)));
            image.setRGB(x + 1, y + row, border);
            image.setRGB(x + w + 6, y + row, border);
        }
    }

    private static final int UP_NEXT_X = 4 + CENTRE + 18 + 10 - SCENE_WIDTH - SCENE_GAP;
    private static final int UP_NEXT_TOP = SCENE_HEIGHT + BODY_SPACING + PADDING - 1 - 34, UP_NEXT_H = 33;

    static int upNextWidth(PolymerPonderScene next) {
        return Math.max(PolymerArtKit.width(next.title), PolymerArtKit.width("Up Next:")) + 5;
    }

    private static BufferedImage upNextImage(
            Map<Integer, BufferedImage> ascii, BufferedImage icons, PolymerPonderScene next) {
        int w = upNextWidth(next);
        BufferedImage image = new BufferedImage(w + 8, UP_NEXT_H, BufferedImage.TYPE_INT_ARGB);
        boxElement(image, 0, 0, w, 20, IDLE_TOP, IDLE_BOTTOM);
        int ax = 4 + w / 2, divotY = 34 - 9;
        BufferedImage back = icons.getSubimage(0, 24, 8, 8), color = icons.getSubimage(8, 24, 8, 8);
        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 8; x++) {
                int px = ax - 4 + x, py = divotY + y;
                if (py >= UP_NEXT_H) continue;
                image.setRGB(px, py, over(image.getRGB(px, py), back.getRGB(x, y)));
                image.setRGB(px, py, over(image.getRGB(px, py), multiply(color.getRGB(x, y), IDLE_BOTTOM)));
            }
        int label = PolymerArtKit.width("Up Next:"), title = PolymerArtKit.width(next.title);
        PolymerArtKit.text(image, ascii, ax - label / 2, 34 - 29, "Up Next:", 0xA3A3A3, false);
        PolymerArtKit.text(image, ascii, ax - title / 2, 34 - 19, next.title, 0xEEEEEE, false);
        return image;
    }

    private static int multiply(int argb, int tint) {
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8)
            result |= ((argb >>> shift & 0xFF) * (tint >>> shift & 0xFF) / 255) << shift;
        return result;
    }

    private static int over(int under, int top) {
        float ta = (top >>> 24) / 255f, ua = (under >>> 24) / 255f, a = ta + ua * (1 - ta);
        if (a == 0) return 0;
        int result = Math.round(a * 255) << 24;
        for (int shift = 0; shift < 24; shift += 8) {
            float c = ((top >> shift & 0xFF) * ta + (under >> shift & 0xFF) * ua * (1 - ta)) / a;
            result |= Math.round(c) << shift;
        }
        return result;
    }

    private static void fill(BufferedImage image, int x0, int y0, int x1, int y1, int argb) {
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) image.setRGB(x, y, argb);
    }

    private static BufferedImage lineImage(int width) {
        BufferedImage image = new BufferedImage(width, 2, BufferedImage.TYPE_INT_ARGB);
        fill(image, 0, 0, width, 1, 0xFF000000 | TEXT_COLOR);
        fill(image, 0, 1, width, 2, 0xFF000000 | LINE_SHADE);
        return image;
    }

    record OutlineImage(BufferedImage image, int x, int y) {}

    static OutlineImage outline(PolymerPonderScene scene, PolymerPonderScene.Outline outline, int view, int state) {
        float grow = PolymerPonderScene.outlineGrowth(state), inflate = 1 / 128f;
        var c = outline.centre();
        var e = outline.expand();
        float[] min = {c.x() - e.x() * grow - inflate, c.y() - e.y() * grow - inflate, c.z() - e.z() * grow - inflate};
        float[] max = {c.x() + e.x() * grow + inflate, c.y() + e.y() * grow + inflate, c.z() + e.z() * grow + inflate};
        float[][] corners = new float[8][];
        for (int corner = 0; corner < 8; corner++) {
            var p = scene.project(
                    new PolymerPonderScene.Point(
                            (corner & 1) == 0 ? min[0] : max[0],
                            (corner & 2) == 0 ? min[1] : max[1],
                            (corner & 4) == 0 ? min[2] : max[2]),
                    view);
            corners[corner] = new float[] {p.x(), p.y()};
        }
        float stroke = outline.lineWidth() * 16 * scene.scale;
        int pad = (int) Math.ceil(stroke) + 1;
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        for (float[] p : corners) {
            x0 = Math.min(x0, p[0]);
            y0 = Math.min(y0, p[1]);
            x1 = Math.max(x1, p[0]);
            y1 = Math.max(y1, p[1]);
        }
        int left = (int) Math.floor(x0) - pad, top = (int) Math.floor(y0) - pad;
        BufferedImage canvas = new BufferedImage(
                (int) Math.ceil(x1) + pad - left + 1, (int) Math.ceil(y1) + pad - top + 1, BufferedImage.TYPE_INT_ARGB);
        var g = canvas.createGraphics();
        int alpha = Math.round(255 * PolymerPonderScene.outlineAlpha(state));
        g.setColor(new Color(outline.color() | alpha << 24, true));
        g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        int hidden = -1;
        if (e.x() > .1f && e.y() > .1f && e.z() > .1f) {
            var view3 = scene.viewTransform(view, scene.scale, SCENE_WIDTH, SCENE_HEIGHT);
            float far = Float.MAX_VALUE;
            for (int corner = 0; corner < 8; corner++) {
                float z = view3.transformPosition(new Vector3f(
                                (corner & 1) == 0 ? min[0] : max[0],
                                (corner & 2) == 0 ? min[1] : max[1],
                                (corner & 4) == 0 ? min[2] : max[2]))
                        .z;
                if (z < far) {
                    far = z;
                    hidden = corner;
                }
            }
        }
        for (int corner = 0; corner < 8; corner++)
            for (int axis = 0; axis < 3; axis++) {
                int next = corner | 1 << axis;
                if (next == corner || corner == hidden || next == hidden) continue;
                g.draw(new Line2D.Float(
                        corners[corner][0] - left,
                        corners[corner][1] - top,
                        corners[next][0] - left,
                        corners[next][1] - top));
            }
        g.dispose();
        int cx0 = canvas.getWidth(), cy0 = canvas.getHeight(), cx1 = -1, cy1 = -1;
        for (int y = 0; y < canvas.getHeight(); y++)
            for (int x = 0; x < canvas.getWidth(); x++)
                if ((canvas.getRGB(x, y) >>> 24) != 0) {
                    cx0 = Math.min(cx0, x);
                    cy0 = Math.min(cy0, y);
                    cx1 = Math.max(cx1, x);
                    cy1 = Math.max(cy1, y);
                }
        return new OutlineImage(
                copy(canvas.getSubimage(cx0, cy0, cx1 - cx0 + 1, cy1 - cy0 + 1)), left + cx0, top + cy0);
    }

    private static BufferedImage copy(BufferedImage image) {
        BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = result.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return result;
    }

    private static void keepWidth(BufferedImage image) {
        int x = image.getWidth() - 1;
        for (int y = 0; y < image.getHeight(); y++) if ((image.getRGB(x, y) >>> 24) != 0) return;
        image.setRGB(x, image.getHeight() - 1, 0x01000000);
    }
}
