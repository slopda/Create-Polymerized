package com.zurrtum.create.infrastructure.polymer.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zurrtum.create.Create;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerGogglesOverlay;
import dev.veeda.createpoly.assets.CreateIds;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

final class PolymerScreenArt {
    record Layer(int u, int v, int w, int h, int x, int y) {}

    record Art(String id, String texture, int width, int height, List<Layer> layers) {}

    record Text(int x, int y, Component text, int color, boolean shadow) {}

    record StripKind(String texture, int u, int v, int w, int h) {}

    record Strip(StripKind kind, int x, int y) {}

    static final StripKind SEQ_INSTRUCTION = new StripKind("create:textures/gui/sequencer.png", 0, 16, 162, 22),
            SEQ_DELAY = new StripKind("create:textures/gui/sequencer.png", 0, 60, 162, 22),
            SEQ_END = new StripKind("create:textures/gui/sequencer.png", 0, 82, 162, 22),
            SEQ_EMPTY = new StripKind("create:textures/gui/sequencer.png", 0, 104, 162, 22),
            SEQ_AWAIT = new StripKind("create:textures/gui/sequencer.png", 0, 162, 162, 22);

    private static final List<StripKind> STRIP_KINDS =
            List.of(SEQ_INSTRUCTION, SEQ_DELAY, SEQ_END, SEQ_EMPTY, SEQ_AWAIT);
    private static final Set<Integer> STRIP_ROWS = new TreeSet<>(List.of(16, 38, 60, 82, 104));

    static final Art THRESHOLD_ITEMS = new Art(
            "threshold_items",
            "create:textures/gui/threshold_switch.png",
            182,
            103,
            List.of(
                    new Layer(0, 0, 182, 103, 0, 0),
                    new Layer(0, 105, 115, 22, 44, 21),
                    new Layer(0, 105, 115, 22, 44, 45)));
    static final Art THRESHOLD_MISC = new Art(
            "threshold_misc",
            "create:textures/gui/threshold_switch.png",
            182,
            103,
            List.of(
                    new Layer(0, 0, 182, 103, 0, 0),
                    new Layer(0, 129, 115, 22, 44, 21),
                    new Layer(0, 129, 115, 22, 44, 45)));
    static final Art SEQUENCER = new Art(
            "sequencer", "create:textures/gui/sequencer.png", 173, 161, List.of(new Layer(0, 0, 173, 161, 0, 0)));
    static final Art DISPLAY_LINK = new Art(
            "display_link", "create:textures/gui/display_link.png", 235, 162, List.of(new Layer(0, 0, 235, 162, 0, 0)));

    static final Art ELEVATOR_CONTACT = new Art(
            "elevator_contact",
            "create:textures/gui/display_link.png",
            233,
            82,
            List.of(new Layer(20, 172, 233, 82, 0, 0)));
    private static final List<Art> ARTS =
            List.of(THRESHOLD_ITEMS, THRESHOLD_MISC, ELEVATOR_CONTACT, SEQUENCER, DISPLAY_LINK);
    static final int MENU_TITLE_RAISED_2 = -2;
    static final int TOOLBOX_TITLE = -18;

    private static final Set<Integer> ROWS =
            new TreeSet<>(List.of(TOOLBOX_TITLE, MENU_TITLE_RAISED_2, 4, 6, 25, 28, 30, 47, 52, 69, 91, 109, 113));

    private static final FontDescription SPACE_FONT = new FontDescription.Resource(CreateIds.id("value_board_space"));
    private static final Map<String, Integer> CHARS = new LinkedHashMap<>();

    private PolymerScreenArt() {}

    static Component body(Art art, List<Text> texts) {
        return body(art, List.of(), texts);
    }

    static Component body(Art art, List<Strip> strips, List<Text> texts) {
        int index = ARTS.indexOf(art);
        int advance = art.width() + 1;
        Style plain = Style.EMPTY.withColor(0xFFFFFF).withoutShadow();
        MutableComponent line = Component.empty();
        line.append(
                Component.literal(Character.toString(0xE000 + index)).withStyle(plain.withFont(font("screen_art"))));
        int pen = advance;
        for (Strip strip : strips) {
            if (!STRIP_ROWS.contains(strip.y()))
                throw new IllegalArgumentException("Unregistered strip row " + strip.y());
            line.append(PolymerArtKit.space(SPACE_FONT, strip.x() - pen));
            line.append(Component.literal(Character.toString(0xE100 + STRIP_KINDS.indexOf(strip.kind())))
                    .withStyle(plain.withFont(font("screen_strip_" + strip.y()))));
            pen = strip.x() + strip.kind().w() + 1;
        }
        for (Text text : texts) {
            if (!ROWS.contains(text.y())) throw new IllegalArgumentException("Unregistered text row " + text.y());
            line.append(PolymerArtKit.space(SPACE_FONT, text.x() - pen));
            Style style = Style.EMPTY.withColor(text.color() & 0xFFFFFF).withFont(font("screen_text_" + text.y()));
            if (!text.shadow()) style = style.withoutShadow();
            String string = text.text().getString();
            line.append(Component.literal(string).withStyle(style));
            pen = text.x() + PolymerGogglesOverlay.width(string);
        }
        line.append(PolymerArtKit.space(SPACE_FONT, advance - pen));
        for (int i = 0; i < (art.height() + 8) / 9; i++) line.append(Component.literal("\n "));
        return line;
    }

    static FontDescription textFont(int row) {
        if (!ROWS.contains(row)) throw new IllegalArgumentException("Unregistered text row " + row);
        return font("screen_text_" + row);
    }

    private static FontDescription font(String path) {
        return new FontDescription.Resource(CreateIds.id(path));
    }

    static void addAssets(ResourcePackBuilder builder) {
        try {
            JsonArray providers = new JsonArray();
            for (int i = 0; i < ARTS.size(); i++) {
                Art art = ARTS.get(i);
                if (art.width() > 256 || art.height() > 256)
                    throw new IOException("Art exceeds font page: " + art.id());
                Identifier texture = Identifier.parse(art.texture());
                byte[] png = builder.getDataOrSource("assets/" + texture.getNamespace() + "/" + texture.getPath());
                if (png == null) throw new IOException("Missing " + art.texture());
                BufferedImage source = PngCodec.decode(png);
                BufferedImage image =
                        new BufferedImage(art.width(), Math.max(art.height(), 7), BufferedImage.TYPE_INT_ARGB);
                var graphics = image.createGraphics();
                for (Layer layer : art.layers())
                    graphics.drawImage(
                            source.getSubimage(layer.u(), layer.v(), layer.w(), layer.h()), layer.x(), layer.y(), null);
                graphics.dispose();
                String file = "create:font/screen_art_" + art.id() + ".png";
                builder.addData("assets/create/textures/font/screen_art_" + art.id() + ".png", PngCodec.encode(image));
                JsonObject provider = PolymerGuiGlyphs.bitmapProvider(file, image.getHeight(), 7);
                JsonArray chars = new JsonArray();
                chars.add(Character.toString(0xE000 + i));
                provider.add("chars", chars);
                providers.add(provider);
            }
            PolymerGuiGlyphs.writeFont(builder, "screen_art", providers);

            List<String> stripFiles = new ArrayList<>();
            for (int i = 0; i < STRIP_KINDS.size(); i++) {
                StripKind kind = STRIP_KINDS.get(i);
                Identifier texture = Identifier.parse(kind.texture());
                byte[] png = builder.getDataOrSource("assets/" + texture.getNamespace() + "/" + texture.getPath());
                if (png == null) throw new IOException("Missing " + kind.texture());
                BufferedImage strip = PngCodec.decode(png).getSubimage(kind.u(), kind.v(), kind.w(), kind.h());
                builder.addData("assets/create/textures/font/screen_strip_" + i + ".png", PngCodec.encode(strip));
                stripFiles.add("create:font/screen_strip_" + i + ".png");
            }
            for (int row : STRIP_ROWS) {
                JsonArray rowProviders = new JsonArray();
                for (int i = 0; i < STRIP_KINDS.size(); i++) {
                    JsonObject provider = PolymerGuiGlyphs.bitmapProvider(
                            stripFiles.get(i), STRIP_KINDS.get(i).h(), 7 - row);
                    JsonArray chars = new JsonArray();
                    chars.add(Character.toString(0xE100 + i));
                    provider.add("chars", chars);
                    rowProviders.add(provider);
                }
                PolymerGuiGlyphs.writeFont(builder, "screen_strip_" + row, rowProviders);
            }

            JsonArray asciiRows = PolymerArtKit.asciiRows(builder);
            for (int row : ROWS) {
                JsonObject ascii = new JsonObject();
                ascii.addProperty("type", "bitmap");
                if (row < 0) {
                    ascii.addProperty("file", paddedAscii(builder, -row));
                    ascii.addProperty("height", 8 - row);
                } else {
                    ascii.addProperty("file", "minecraft:font/ascii.png");
                    ascii.addProperty("height", 8);
                }
                ascii.addProperty("ascent", 7 - row);
                ascii.add("chars", asciiRows.deepCopy());
                JsonObject space = new JsonObject();
                space.addProperty("type", "space");
                JsonObject advances = new JsonObject();
                advances.addProperty(" ", 4);
                space.add("advances", advances);
                JsonArray list = new JsonArray();
                list.add(space);
                list.add(ascii);
                PolymerGuiGlyphs.writeFont(builder, "screen_text_" + row, list);
            }
        } catch (IOException | RuntimeException exception) {
            Create.LOGGER.warn("Screen art unavailable", exception);
        }
    }

    private static String paddedAscii(ResourcePackBuilder builder, int pad) throws IOException {
        byte[] png = builder.getDataOrSource("assets/minecraft/textures/font/ascii.png");
        if (png == null) throw new IOException("Vanilla ascii.png unavailable");
        BufferedImage source = PngCodec.decode(png);
        int cell = source.getWidth() / 16;
        if (cell != 8 || source.getHeight() != 128) throw new IOException("Unexpected ascii.png size");
        BufferedImage padded = new BufferedImage(128, 16 * (8 + pad), BufferedImage.TYPE_INT_ARGB);
        var graphics = padded.createGraphics();
        for (int gridRow = 0; gridRow < 16; gridRow++)
            graphics.drawImage(source.getSubimage(0, gridRow * 8, 128, 8), 0, gridRow * (8 + pad), null);
        graphics.dispose();
        String name = "screen_ascii_pad" + pad;
        builder.addData("assets/create/textures/font/" + name + ".png", PngCodec.encode(padded));
        return "create:font/" + name + ".png";
    }
}
