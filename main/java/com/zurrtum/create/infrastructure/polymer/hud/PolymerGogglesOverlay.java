package com.zurrtum.create.infrastructure.polymer.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zurrtum.create.Create;
import com.zurrtum.create.infrastructure.polymer.PolymerBedrockUi;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerGuiGlyphs;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerScreenEditors;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import dev.veeda.createpoly.assets.CreateIds;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class PolymerGogglesOverlay {
    static final int MAX_LINES = 16;
    private static final int REFRESH_TICKS = 20;
    private static final int STAY_TICKS = 30;
    private static final int FADE_TICKS = 4, FADE_LEVELS = 4, SLIDE_PX = 8;
    private static final int TEXT_DX = 12, TEXT_Y = -12, ICON_DX = 10, ICON_Y = -16;
    public static final int Y_STEP = 20, Y_STEPS = 4, ROW_SHIFT = Y_STEP / 10;

    public record Offset(int x, int k) {
        public static final Offset DEFAULT = new Offset(20, 0);
    }

    private static final int SUBTITLE_TOP = 10;
    private static final int TEXT_CELL_W = 9, TEXT_CELL_H = 36, TEXT_HEIGHT = TEXT_CELL_H / 2;
    private static final int TILE_BITS = 5, STRIP_CELLS = 2 + TILE_BITS, STRIP_CELL_W = 1 << (TILE_BITS - 1);

    private static final int BOX_BG = 0xB4100010;
    private static final int BORDER_TOP = 0x505000FF, BORDER_BOTTOM = 0x5028007F;
    private static final int SPACE_BASE = 0xF000;
    private static final int BOX_BASE = 0xE000, BOX_LEVEL_STRIDE = 0x110, ICON_CHAR = 0xE900;
    private static final char FULL_BLOCK = '█', SHADE_BLOCK = '▒';

    private static final Map<UUID, Shown> SHOWN = new HashMap<>();
    private static volatile Map<Integer, Integer> advances = Map.of();
    private static volatile Map<Integer, Integer> cellWidths = Map.of();

    private record Shown(
            List<Component> lines,
            int started,
            int sent,
            int slide,
            PolymerPlacementIndicator.Frame indicator,
            Offset offset,
            Component radial) {}

    private PolymerGogglesOverlay() {}

    public static void register() {
        PolymerScreenEditors.register();
        PolymerHudRadial.register();
        ServerTickEvents.END_SERVER_TICK.register(PolymerGogglesOverlay::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            SHOWN.remove(handler.player.getUUID());
            BEDROCK_SHOWN.remove(handler.player.getUUID());
            PolymerPlacementIndicator.clear(handler.player.getUUID());
            PolymerHudRadial.forget(handler.player.getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SHOWN.clear();
            BEDROCK_SHOWN.clear();
            PolymerPlacementIndicator.clearAll();
        });
    }

    private static void tick(MinecraftServer server) {
        if (advances.isEmpty()) return;
        int now = server.getTickCount();
        for (ServerPlayer player : NativePresentationPolicy.onlinePlayers(server.getPlayerList())) {
            if (PolymerNativeClients.isNative(player)) {
                SHOWN.remove(player.getUUID());
                PolymerPlacementIndicator.clear(player.getUUID());
                continue;
            }
            if (PolymerBedrockUi.available(player)) {
                tickBedrock(player, now);
                continue;
            }
            Component radial = PolymerHudRadial.tick(player);
            List<Component> preview = radial != null ? null : PolymerScreenEditors.gogglePreview(player);
            List<Component> lines = radial != null
                    ? List.of()
                    : preview != null
                            ? preview
                            : player.isSpectator() || player.isRemoved() ? List.of() : tooltip(player);
            Offset offset = PolymerScreenEditors.goggleOffset(player);
            PolymerPlacementIndicator.Frame indicator = radial != null ? null : PolymerPlacementIndicator.tick(player);
            Shown shown = SHOWN.get(player.getUUID());
            if (lines.isEmpty() && indicator == null && radial == null) {
                if (shown != null) {
                    SHOWN.remove(player.getUUID());
                    NativePresentationPolicy.sendPresentation(player, new ClientboundClearTitlesPacket(true));
                }
                continue;
            }
            int started = shown == null || shown.lines().isEmpty() != lines.isEmpty() ? now : shown.started();
            int age = now - started;
            float fade = Math.min(1, age / (float) FADE_TICKS);
            int slide = lines.isEmpty() ? 0 : Math.round((float) Math.pow(1 - fade, 3) * SLIDE_PX);
            int level = Math.round(fade * FADE_LEVELS);
            boolean changed = shown == null
                    || !shown.lines().equals(lines)
                    || slide != shown.slide()
                    || !Objects.equals(indicator, shown.indicator())
                    || !offset.equals(shown.offset())
                    || !Objects.equals(radial, shown.radial())
                    || !lines.isEmpty() && age <= FADE_TICKS;
            if (shown == null || now - shown.sent() >= REFRESH_TICKS && age >= FADE_TICKS) {
                SHOWN.put(player.getUUID(), new Shown(lines, started, now, slide, indicator, offset, radial));
                NativePresentationPolicy.sendPresentation(
                        player, new ClientboundSetTitlesAnimationPacket(0, STAY_TICKS, 0));
                NativePresentationPolicy.sendPresentation(
                        player,
                        new ClientboundSetSubtitleTextPacket(
                                radial != null
                                        ? radial
                                        : composeWithIndicator(lines, slide, level, indicator, offset)));
                NativePresentationPolicy.sendPresentation(player, new ClientboundSetTitleTextPacket(Component.empty()));
            } else if (changed) {
                SHOWN.put(player.getUUID(), new Shown(lines, started, shown.sent(), slide, indicator, offset, radial));
                NativePresentationPolicy.sendPresentation(
                        player,
                        new ClientboundSetSubtitleTextPacket(
                                radial != null
                                        ? radial
                                        : composeWithIndicator(lines, slide, level, indicator, offset)));
            }
        }
    }

    static final String BEDROCK_MARKER = "@cg@";
    static final String BEDROCK_MARKER_UPDATE = "@cu@";

    private static final Map<UUID, BedrockShown> BEDROCK_SHOWN = new HashMap<>();

    private record BedrockShown(List<Component> lines, int sent) {}

    private static void tickBedrock(ServerPlayer player, int now) {
        List<Component> lines = player.isSpectator() || player.isRemoved() ? List.of() : tooltip(player);
        BedrockShown shown = BEDROCK_SHOWN.get(player.getUUID());
        if (lines.isEmpty()) {
            if (shown != null) {
                BEDROCK_SHOWN.remove(player.getUUID());
                player.connection.send(new ClientboundClearTitlesPacket(true));
            }
            return;
        }
        if (shown != null && shown.lines().equals(lines)) return;
        BEDROCK_SHOWN.put(player.getUUID(), new BedrockShown(lines, now));
        MutableComponent text = Component.literal(shown == null ? BEDROCK_MARKER : BEDROCK_MARKER_UPDATE);
        for (int i = 0; i < Math.min(lines.size(), MAX_LINES); i++) {
            if (i > 0) text.append("\n");
            text.append(lines.get(i));
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(0, BEDROCK_STAY_TICKS, 0));
        player.connection.send(new ClientboundSetTitleTextPacket(text));
    }

    private static final int BEDROCK_STAY_TICKS = 72000;

    static Component composeWithIndicator(
            List<Component> lines, int slide, int level, PolymerPlacementIndicator.Frame indicator, Offset offset) {
        return (lines.isEmpty() ? Component.empty() : compose(lines, slide, level, offset))
                .copy()
                .append(PolymerPlacementIndicator.compose(indicator));
    }

    static List<Component> tooltip(ServerPlayer player) {
        return PolymerGogglesContent.tooltip(player);
    }

    public static int width(String text) {
        Map<Integer, Integer> table = advances;
        int width = 0;
        for (int i = 0; i < text.length(); ) {
            int codepoint = text.codePointAt(i);
            width += table.getOrDefault(codepoint, table.getOrDefault((int) '?', 6));
            i += Character.charCount(codepoint);
        }
        return width;
    }

    private static int lineWidth(Component line) {
        return width(line.getString());
    }

    static Component compose(List<Component> lines, int slide, int level, Offset offset) {
        Pen pen = new Pen(offset.x() + 0.5f < 0 ? -slide : slide);
        final int textX = offset.x() + TEXT_DX, iconX = offset.x() + ICON_DX, k = offset.k();
        int textWidth = 0;
        for (Component line : lines) textWidth = Math.max(textWidth, lineWidth(line));
        int n = Math.min(lines.size(), MAX_LINES);
        FontDescription box = font(boxFont(k));
        if (level > 0) {
            int shown = k == 0 ? Math.min(level, FADE_LEVELS) : FADE_LEVELS;
            int base = BOX_BASE + (shown - 1) * BOX_LEVEL_STRIDE + n * 16;
            pen.glyph(box, 0xFFFFFF, base, textX - 4, 2);
            int middle = textWidth + 4, x = textX - 2;
            for (int bit = TILE_BITS - 1; bit >= 0; bit--) {
                while (middle >= 1 << bit) {
                    pen.glyph(box, 0xFFFFFF, base + 2 + bit, x, 1 << bit);
                    x += 1 << bit;
                    middle -= 1 << bit;
                }
            }
            pen.glyph(box, 0xFFFFFF, base + 1, textX + textWidth + 2, 2);
        }
        pen.glyph(box, 0xFFFFFF, ICON_CHAR, iconX, 16);
        for (int i = 0; i < n; i++) {
            FontDescription font = font(lineFont(i, k));
            int[] cursor = {textX};
            lines.get(i)
                    .visit(
                            (style, string) -> {
                                int color = Optional.ofNullable(style.getColor())
                                        .map(TextColor::getValue)
                                        .orElse(0xFFFFFF);
                                for (int j = 0; j < string.length(); ) {
                                    int codepoint = string.codePointAt(j);
                                    j += Character.charCount(codepoint);
                                    if (!advances.containsKey(codepoint)) codepoint = '?';
                                    int advance = advances.get(codepoint);
                                    Integer cell = cellWidths.get(codepoint);
                                    if (cell != null) pen.glyph(font, color, codepoint, cursor[0], cell);
                                    cursor[0] += advance;
                                }
                                return Optional.empty();
                            },
                            Style.EMPTY);
        }
        pen.moveTo(box, 0);
        return pen.finish();
    }

    static String lineFont(int line, int k) {
        int shift = k * ROW_SHIFT;
        if (line == 0) return shift == 0 ? "goggle_line_0" : "goggle_line_0s" + signed(shift);
        int row = line + shift;
        return row >= 1 ? "goggle_line_" + row : "goggle_line_n" + -row;
    }

    static String boxFont(int k) {
        return k == 0 ? "goggle_box" : "goggle_box_s" + signed(k);
    }

    private static String signed(int value) {
        return value < 0 ? "n" + -value : Integer.toString(value);
    }

    private static int lineTop(int line, int shift) {
        return line == 0 ? TEXT_Y + 10 * shift : TEXT_Y + 2 + 10 * (line + shift);
    }

    private static final class Pen {
        final MutableComponent root = Component.empty();
        final int slide;
        float units;
        FontDescription runFont;
        int runColor = -1;
        StringBuilder run = new StringBuilder();

        Pen(int slide) {
            this.slide = slide;
        }

        void glyph(FontDescription font, int color, int codepoint, int guiX, int texels) {
            moveTo(font, guiX + slide);
            append(font, color, Character.toString(codepoint));
            units += (int) (0.5f + texels * 0.5f) + 1;
        }

        void moveTo(FontDescription font, int guiX) {
            float delta = guiX / 2f - units;
            if (delta == 0) return;
            append(font, runColor < 0 ? 0xFFFFFF : runColor, PolymerGuiGlyphs.spaces(SPACE_BASE, delta));
            units += delta;
        }

        private void append(FontDescription font, int color, String text) {
            if (!font.equals(runFont) || color != runColor) flush();
            runFont = font;
            runColor = color;
            run.append(text);
        }

        private void flush() {
            if (run.isEmpty()) return;
            root.append(Component.literal(run.toString())
                    .withStyle(Style.EMPTY.withFont(runFont).withColor(runColor).withoutShadow()));
            run = new StringBuilder();
        }

        Component finish() {
            flush();
            return root;
        }
    }

    private static FontDescription font(String path) {
        return new FontDescription.Resource(CreateIds.id(path));
    }

    public static void addAssets(ResourcePackBuilder builder) {
        PolymerPlacementIndicator.addAssets(builder);
        try {
            byte[] defaultFont = builder.getDataOrSource("assets/minecraft/font/include/default.json");
            byte[] asciiPng = builder.getDataOrSource("assets/minecraft/textures/font/ascii.png");
            byte[] gogglesPng = builder.getDataOrSource("assets/create/textures/item/goggles.png");
            if (defaultFont == null || asciiPng == null || gogglesPng == null)
                throw new IOException("Vanilla font or goggles texture unavailable");
            writeAssets(
                    JsonParser.parseString(new String(defaultFont, StandardCharsets.UTF_8)),
                    PngCodec.decode(asciiPng),
                    PngCodec.decode(gogglesPng),
                    builder::addData);
        } catch (IOException | RuntimeException exception) {
            advances = Map.of();
            Create.LOGGER.warn("Goggles HUD disabled: cannot build its fonts", exception);
        }
    }

    static void writeAssets(
            JsonElement defaultFont, BufferedImage ascii, BufferedImage goggles, BiConsumer<String, byte[]> out)
            throws IOException {
        List<String> rows = null;
        for (JsonElement provider : defaultFont.getAsJsonObject().getAsJsonArray("providers")) {
            JsonObject object = provider.getAsJsonObject();
            if ("bitmap".equals(object.get("type").getAsString())
                    && object.get("file").getAsString().endsWith("font/ascii.png")) {
                rows = new ArrayList<>();
                for (JsonElement row : object.getAsJsonArray("chars")) rows.add(row.getAsString());
            }
        }
        if (rows == null) throw new IOException("No ascii provider in vanilla default font");
        int columns = rows.getFirst().codePointCount(0, rows.getFirst().length());
        int cellW = ascii.getWidth() / columns, cellH = ascii.getHeight() / rows.size();
        if (cellW != 8 || cellH != 8) throw new IOException("Unexpected ascii cell size " + cellW + "x" + cellH);

        Map<Integer, Integer> advance = new HashMap<>(), cells = new HashMap<>();
        advance.put((int) ' ', 4);
        List<String> atlasRows = rows;
        BufferedImage atlas =
                new BufferedImage(columns * TEXT_CELL_W, atlasRows.size() * TEXT_CELL_H, BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < rows.size(); row++) {
            int[] codepoints = rows.get(row).codePoints().toArray();
            for (int column = 0; column < codepoints.length; column++) {
                int codepoint = codepoints[column];
                if (codepoint == 0 || codepoint == ' ') continue;
                boolean[][] mask = new boolean[8][8];
                int width = 0;
                for (int y = 0; y < 8; y++)
                    for (int x = 0; x < 8; x++)
                        if ((ascii.getRGB(column * 8 + x, row * 8 + y) >>> 24) != 0) {
                            mask[y][x] = true;
                            width = Math.max(width, x + 1);
                        }
                if (width == 0) continue;
                paintGlyph(atlas, column * TEXT_CELL_W, row * TEXT_CELL_H, mask);
                advance.put(codepoint, width + 1);
                cells.put(codepoint, width + 1);
            }
        }
        png(out, "assets/create/textures/font/goggle_text.png", atlas);
        Map<Integer, String> padded = new HashMap<>();
        List<String> atlasRowsFinal = atlasRows;
        IntFunction<String> atlasFor = ascent -> {
            if (ascent <= TEXT_HEIGHT) return "create:font/goggle_text.png";
            return padded.computeIfAbsent(ascent, value -> {
                BufferedImage tall = new BufferedImage(
                        atlas.getWidth(), atlasRowsFinal.size() * value * 2, BufferedImage.TYPE_INT_ARGB);
                for (int row = 0; row < atlasRowsFinal.size(); row++)
                    for (int y = 0; y < TEXT_CELL_H; y++)
                        for (int x = 0; x < atlas.getWidth(); x++)
                            tall.setRGB(x, row * value * 2 + y, atlas.getRGB(x, row * TEXT_CELL_H + y));
                String file = "goggle_text_" + value;
                try {
                    png(out, "assets/create/textures/font/" + file + ".png", tall);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
                return "create:font/" + file + ".png";
            });
        };

        JsonObject spaces = PolymerGuiGlyphs.spaceProvider(SPACE_BASE);
        Set<String> written = new HashSet<>();
        for (int k = -Y_STEPS; k <= Y_STEPS; k++)
            for (int line = 0; line < MAX_LINES; line++) {
                String name = lineFont(line, k);
                if (!written.add(name)) continue;
                int ascent = 7 - (lineTop(line, k * ROW_SHIFT) - SUBTITLE_TOP) / 2;
                JsonArray providers = new JsonArray();
                providers.add(PolymerGuiGlyphs.bitmapRows(
                        atlasFor.apply(ascent), Math.max(TEXT_HEIGHT, ascent), ascent, atlasRows));
                providers.add(spaces);
                json(out, "assets/create/font/" + name + ".json", providers);
            }

        int baseBoxAscent = 7 - (TEXT_Y - 4 - SUBTITLE_TOP) / 2;
        for (int k = -Y_STEPS; k <= Y_STEPS; k++) {
            JsonArray boxProviders = new JsonArray();
            int boxAscent = 7 - (TEXT_Y - 4 + k * Y_STEP - SUBTITLE_TOP) / 2;
            for (int level = k == 0 ? 1 : FADE_LEVELS; level <= FADE_LEVELS; level++) {
                float fade = level / (float) FADE_LEVELS;
                for (int n = 1; n <= MAX_LINES; n++) {
                    int textHeight = 8 + (n > 1 ? 2 + (n - 1) * 10 : 0);
                    int baseRows = Math.max(textHeight + 8, 2 * baseBoxAscent);
                    int rowsTall = Math.max(baseRows, 2 * boxAscent);
                    String file = "goggle_box_" + level + "_" + n + (rowsTall == baseRows ? "" : "_p" + rowsTall);
                    if (written.add(file)) {
                        BufferedImage strip =
                                new BufferedImage(STRIP_CELL_W * STRIP_CELLS, rowsTall, BufferedImage.TYPE_INT_ARGB);
                        paintColumn(strip, 0, textHeight, 0, fade);
                        paintColumn(strip, 1, textHeight, 1, fade);
                        paintColumn(strip, STRIP_CELL_W, textHeight, 1, fade);
                        paintColumn(strip, STRIP_CELL_W + 1, textHeight, 0, fade);
                        for (int bit = 0; bit < TILE_BITS; bit++)
                            for (int x = 0; x < 1 << bit; x++)
                                paintColumn(strip, STRIP_CELL_W * (2 + bit) + x, textHeight, 2, fade);
                        png(out, "assets/create/textures/font/" + file + ".png", strip);
                    }
                    StringBuilder chars = new StringBuilder();
                    for (int i = 0; i < STRIP_CELLS; i++)
                        chars.appendCodePoint(BOX_BASE + (level - 1) * BOX_LEVEL_STRIDE + n * 16 + i);
                    boxProviders.add(PolymerGuiGlyphs.bitmapRows(
                            "create:font/" + file + ".png", rowsTall / 2, boxAscent, List.of(chars.toString())));
                }
            }
            int iconAscent = 7 - (ICON_Y + k * Y_STEP - SUBTITLE_TOP) / 2;
            int baseIconRows = Math.max(16, 2 * (7 - (ICON_Y - SUBTITLE_TOP) / 2));
            int iconRows = Math.max(baseIconRows, 2 * iconAscent);
            String iconFile = "goggle_icon" + (iconRows == baseIconRows ? "" : "_p" + iconRows);
            if (written.add(iconFile)) {
                BufferedImage icon = new BufferedImage(16, iconRows, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < 16; y++)
                    for (int x = 0; x < 16; x++)
                        icon.setRGB(x, y, goggles.getRGB(x * goggles.getWidth() / 16, y * goggles.getHeight() / 16));
                png(out, "assets/create/textures/font/" + iconFile + ".png", icon);
            }
            boxProviders.add(PolymerGuiGlyphs.bitmapRows(
                    "create:font/" + iconFile + ".png",
                    iconRows / 2,
                    iconAscent,
                    List.of(Character.toString(ICON_CHAR))));
            boxProviders.add(spaces);
            json(out, "assets/create/font/" + boxFont(k) + ".json", boxProviders);
        }
        advances = Map.copyOf(advance);
        cellWidths = Map.copyOf(cells);
    }

    private static void paintGlyph(BufferedImage atlas, int x0, int y0, boolean[][] mask) {
        for (int y = 0; y < mask.length; y++)
            for (int x = 0; x < mask[y].length; x++) if (mask[y][x]) atlas.setRGB(x0 + x + 1, y0 + y + 1, 0xFF3F3F3F);
        for (int y = 0; y < mask.length; y++)
            for (int x = 0; x < mask[y].length; x++) if (mask[y][x]) atlas.setRGB(x0 + x, y0 + y, 0xFFFFFFFF);
    }

    private static void paintColumn(BufferedImage image, int x, int textHeight, int kind, float fade) {
        int background = alpha(BOX_BG, fade), top = alpha(BORDER_TOP, fade), bottom = alpha(BORDER_BOTTOM, fade);
        for (int row = 0; row < textHeight + 8; row++) {
            int y = row - 4;
            int color = 0;
            boolean body = y >= -3 && y < textHeight + 3;
            boolean strips = y == -4 || y == textHeight + 3;
            if (kind == 0) color = body ? background : 0;
            else {
                if (body || strips) color = background;
                if (y == -3) color = over(top, color);
                else if (y == textHeight + 2) color = over(bottom, color);
                else if (kind == 1 && y > -3 && y < textHeight + 2) {
                    float t = (y + 2.5f) / (textHeight + 4);
                    color = over(lerp(top, bottom, t), color);
                }
            }
            image.setRGB(x, row, color);
        }
    }

    private static int alpha(int argb, float scale) {
        return Math.round((argb >>> 24) * scale) << 24 | argb & 0xFFFFFF;
    }

    private static int lerp(int a, int b, float t) {
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int ca = a >>> shift & 0xFF, cb = b >>> shift & 0xFF;
            result |= Math.round(ca + (cb - ca) * t) << shift;
        }
        return result;
    }

    private static int over(int top, int bottom) {
        float ta = (top >>> 24) / 255f, ba = (bottom >>> 24) / 255f;
        float oa = ta + ba * (1 - ta);
        if (oa == 0) return 0;
        int result = Math.round(oa * 255) << 24;
        for (int shift = 0; shift <= 16; shift += 8) {
            float c = ((top >>> shift & 0xFF) * ta + (bottom >>> shift & 0xFF) * ba * (1 - ta)) / oa;
            result |= Math.min(255, Math.round(c)) << shift;
        }
        return result;
    }

    private static void json(BiConsumer<String, byte[]> out, String path, JsonArray providers) {
        JsonObject root = new JsonObject();
        root.add("providers", providers);
        out.accept(path, root.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void png(BiConsumer<String, byte[]> out, String path, BufferedImage image) throws IOException {
        out.accept(path, PngCodec.encode(image));
    }
}
