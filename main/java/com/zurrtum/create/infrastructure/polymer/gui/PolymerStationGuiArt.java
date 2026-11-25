package com.zurrtum.create.infrastructure.polymer.gui;

import com.zurrtum.create.content.trains.entity.Train;
import com.zurrtum.create.content.trains.station.StationBlockEntity;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerGogglesOverlay;
import dev.veeda.createpoly.assets.CreateIds;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;

final class PolymerStationGuiArt {
    private static volatile Map<Integer, Integer> iconAdvances = Map.of();
    private static final String FONT = "station_sgui";
    private static final int BG = 0xE000, ICON = 0xE100, SPACES = 0xF000;

    record Text(int x, int y, String value, int color) {}

    static int panelY(boolean assembly) {
        return 0;
    }

    static Component title(StationBlockEntity be, Train train, boolean active, boolean schedule) {
        boolean assembly = be.isAssembling();
        int y = panelY(assembly);
        int mask = (assembly ? 8 : 0)
                | ((assembly ? be.lastException != null : train != null) ? 1 : 0)
                | (active ? 2 : 0)
                | (schedule && !assembly ? 4 : 0);
        var title = Component.empty();
        title.append(space(-20)).append(glyph(BG + mask, FONT)).append(space(-201));
        List<Text> text = new ArrayList<>();
        if (assembly) {
            centered(text, y + 4, "Train Assembly", 0x0E2233);
            if (be.lastException != null) {
                centered(text, y + 47, "Assembly Failed", 0x775B5B);
                int offset = y + 67;
                if (be.failedCarriageIndex != -1) {
                    text.add(new Text(30, offset, "Carriage " + be.failedCarriageIndex + ":", 0x7A7A7A));
                    offset += 10;
                }
                for (String line : wrap(translate(be.lastException.component), 134)) {
                    if (offset > y + 111) break;
                    text.add(new Text(30, offset, line, 0x775B5B));
                    offset += 9;
                }
                centered(text, y + 123, "Resolve this and retry", 0x7A7A7A);
            } else {
                centered(
                        text,
                        y + 47,
                        be.bogeyCount == 0 ? "No Bogeys" : be.bogeyCount == 1 ? "1 Bogey" : be.bogeyCount + " Bogeys",
                        0x7A7A7A);
                wrapped(text, 28, y + 62, "Use Train Casing on highlighted Tracks to create bogeys.", 134);
                wrapped(text, 28, y + 94, "Remove bogeys by breaking the block on top.", 134);
                wrapped(text, 28, y + 117, "Build carriages attached to one or two bogeys each.", 138);
            }
        } else {
            String name = fit(be.getStation().name, 172);
            centered(text, y + 4, name, 0x592424);
            if (train == null) centered(text, y + 47, "Station is Idle", 0x7A7A7A);
            else {
                train(title, be, train, y);
                centered(text, y + 47, fit(train.name.getString(), 134), 0xC6C6C6);
            }
            String mode = PolymerSchematicGuis.all()
                    .getOrDefault(
                            "create.contraption.door_control." + be.doorControls.mode.getSerializedName() + ".short",
                            be.doorControls.mode.getSerializedName());
            text.add(new Text(39, y + 108, mode, 0xEEEEEE));
        }
        for (Text row : text) {
            title.append(space(row.x()))
                    .append(Component.literal(row.value())
                            .withStyle(Style.EMPTY
                                    .withFont(font("station_text_" + row.y()))
                                    .withColor(row.color())
                                    .withoutShadow()));
            title.append(space(-row.x() - PolymerGogglesOverlay.width(row.value())));
        }
        title.append(space(20));
        return title;
    }

    private static void train(MutableComponent out, StationBlockEntity be, Train train, int y) {
        int type = train.icon.id().getPath().equals("electric")
                ? 1
                : train.icon.id().getPath().equals("modern") ? 2 : 0;
        int width = 19;
        for (int i = 1; i < train.carriages.size(); i++)
            width += (train.doubleEnded && i == train.carriages.size() - 1
                            ? 19
                            : iconWidth(train.carriages.get(i).bogeySpacing))
                    + 1;
        int target = 100 - width / 2;
        if (width > 130) target -= width - 130;
        int position = target;
        if (be.getStation().getPresentTrain() != train)
            position -= (int) ((target + width) * Math.min(1, train.navigation.distanceToDestination / 30.0));
        int x = position;
        for (int i = train.carriages.size() - 1; i > 0; i--) {
            int index = be.trainBackwards ? train.carriages.size() - i - 1 : i;
            int length = train.carriages.get(index).bogeySpacing;
            int piece = length < 3 ? 1 : length < 9 ? 2 : 3;
            if (train.doubleEnded && i == train.carriages.size() - 1) piece = 0;
            int w = piece == 1 ? 7 : piece == 2 ? 13 : 19;
            if (x >= 4 && x + w <= 166) icon(out, type * 5 + piece, x);
            x += w + 1;
        }
        if (x >= 4 && x + 19 <= 166) icon(out, type * 5 + 4, x);
    }

    private static int iconWidth(int length) {
        return length < 3 ? 7 : length < 9 ? 13 : 19;
    }

    private static void icon(MutableComponent out, int icon, int x) {
        out.append(space(x))
                .append(glyph(ICON + icon, "station_train_icons"))
                .append(space(-x - iconAdvances.getOrDefault(icon, 20)));
    }

    private static void centered(List<Text> text, int y, String label, int color) {
        text.add(new Text(100 - PolymerGogglesOverlay.width(label) / 2, y, label, color));
    }

    private static void wrapped(List<Text> out, int x, int y, String value, int width) {
        for (String line : wrap(value, width)) {
            out.add(new Text(x, y, line, 0x7A7A7A));
            y += 9;
        }
    }

    private static List<String> wrap(String value, int width) {
        List<String> lines = new ArrayList<>();
        String current = "";
        for (String word : value.split("\\s+")) {
            if (!current.isEmpty() && PolymerGogglesOverlay.width(current + " " + word) > width) {
                lines.add(current);
                current = "";
            }
            current += (current.isEmpty() ? "" : " ") + word;
        }
        if (!current.isEmpty()) lines.add(current);
        return lines;
    }

    private static String fit(String value, int width) {
        if (PolymerGogglesOverlay.width(value) <= width) return value;
        while (!value.isEmpty() && PolymerGogglesOverlay.width(value + "...") > width)
            value = value.substring(0, value.offsetByCodePoints(value.length(), -1));
        return value + "...";
    }

    private static String translate(Component value) {
        if (!(value.getContents() instanceof TranslatableContents translatable)) return value.getString();
        String pattern = PolymerSchematicGuis.all().getOrDefault(translatable.getKey(), translatable.getKey());
        Object[] args = Arrays.stream(translatable.getArgs())
                .map(arg -> arg instanceof Component c ? translate(c) : arg)
                .toArray();
        try {
            return String.format(Locale.ROOT, pattern, args);
        } catch (RuntimeException ignored) {
            return value.getString();
        }
    }

    private static FontDescription font(String name) {
        return new FontDescription.Resource(CreateIds.id(name));
    }

    private static Component glyph(int code, String name) {
        return Component.literal(Character.toString(code))
                .withStyle(Style.EMPTY.withFont(font(name)).withColor(0xFFFFFF).withoutShadow());
    }

    private static Component space(int delta) {
        StringBuilder text = new StringBuilder();
        int left = Math.abs(delta);
        for (int bit = 10; bit >= 0; bit--)
            while (left >= 1 << bit) {
                text.appendCodePoint(SPACES + (delta < 0 ? 32 : 0) + bit);
                left -= 1 << bit;
            }
        return Component.literal(text.toString())
                .withStyle(Style.EMPTY.withFont(font(FONT)).withoutShadow());
    }

    static void addAssets(ResourcePackBuilder builder) {
        try {
            PolymerStationGuiForeground.initialize(builder);
            BufferedImage schedule = image(builder, "schedule_2"),
                    assemble = image(builder, "assemble"),
                    widgets = image(builder, "widgets"),
                    icons = image(builder, "icons");
            for (int mask = 0; mask < 16; mask++) {
                boolean assembly = (mask & 8) != 0,
                        train = (mask & 1) != 0,
                        active = (mask & 2) != 0,
                        scheduled = (mask & 4) != 0;
                int y = panelY(assembly);
                BufferedImage canvas = new BufferedImage(200, assembly ? 178 : 132, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = canvas.createGraphics();
                g.drawImage(
                        assembly ? assemble.getSubimage(0, 0, 200, 178) : schedule.getSubimage(0, 111, 200, 127),
                        0,
                        y,
                        null);
                if (assembly) {
                    button(g, widgets, icons, 73, y + 154, 18, true, 8 * 16, 10 * 16);
                    wide(g, widgets, schedule, 94, y + 154, active, 64);
                    button(g, widgets, icons, 167, y + 154, 18, true, 4 * 16, 0);
                } else {
                    if (train) {
                        g.drawImage(assemble.getSubimage(1, 179, 150, 18), 21, y + 42, null);
                        for (int row = 0; row < 26; row++)
                            g.drawImage(assemble.getSubimage(1, 198, 150, 1), 21, y + 60 + row, null);
                        g.drawImage(assemble.getSubimage(1, 200, 150, 4), 21, y + 86, null);
                        button(g, widgets, icons, 73, y + 65, 18, scheduled, 12 * 16, 3 * 16);
                        wide(g, widgets, schedule, 94, y + 65, active, 39);
                    } else wide(g, widgets, schedule, 84, y + 65, active, 14);
                    button(g, widgets, icons, 167, y + 103, 18, true, 0, 16);
                }
                if (!assembly) {
                    BufferedImage footer = new BufferedImage(200, 8, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D footerGraphics = footer.createGraphics();
                    footerGraphics.drawImage(canvas.getSubimage(0, 119, 200, 8), 0, 0, null);
                    footerGraphics.dispose();
                    for (int row = 124; row < 129; row++) g.drawImage(footer.getSubimage(0, 4, 200, 1), 0, row, null);
                    g.drawImage(footer.getSubimage(0, 5, 200, 3), 0, 129, null);
                }
                g.dispose();
                PolymerStationGuiForeground.decorate(canvas, assembly, (mask & 1) != 0);
                if (assembly) {
                    BufferedImage compact = new BufferedImage(200, 168, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D compactGraphics = compact.createGraphics();
                    compactGraphics.drawImage(canvas.getSubimage(0, 0, 200, 20), 0, 0, null);
                    compactGraphics.drawImage(canvas.getSubimage(0, 30, 200, 148), 0, 20, null);
                    compactGraphics.dispose();
                    canvas = compact;
                }
                png(builder, "station_panel_" + mask, canvas);
            }
            Map<Integer, Integer> widths = new HashMap<>();
            for (int type = 0; type < 3; type++)
                for (int part = 0; part < 5; part++) {
                    int[] offsets = {0, 34, 20, 42, 62}, ws = {19, 7, 13, 19, 19};
                    BufferedImage crop = assemble.getSubimage(2 + offsets[part], 205 + type * 11, ws[part], 10);
                    int right = -1;
                    for (int xx = 0; xx < crop.getWidth(); xx++)
                        for (int yy = 0; yy < 10; yy++)
                            if ((crop.getRGB(xx, yy) >>> 24) != 0) right = Math.max(right, xx);
                    widths.put(type * 5 + part, right + 2);
                    png(builder, "station_train_" + (type * 5 + part), crop);
                }
            iconAdvances = Map.copyOf(widths);
            png(builder, "station_edit_name", schedule.getSubimage(0, 239, 13, 13));
            PolymerStationGuiForeground.addAssets(builder, widths);
            builder.addStringData(
                    "assets/create/items/ui/station_empty.json", "{\"model\":{\"type\":\"minecraft:empty\"}}");
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Cannot build station UI source art", e);
        }
    }

    private static void wide(
            Graphics2D g, BufferedImage widgets, BufferedImage schedule, int x, int y, boolean active, int u) {
        int bg = active ? 0 : 90;
        g.drawImage(widgets.getSubimage(bg, 0, 18, 18), x, y, null);
        g.drawImage(widgets.getSubimage(bg + 1, 0, 17, 18), x + 9, y, null);
        g.drawImage(schedule.getSubimage(u, 239, 24, 16), x + 1, y + 1, null);
    }

    private static void button(
            Graphics2D g,
            BufferedImage widgets,
            BufferedImage icons,
            int x,
            int y,
            int w,
            boolean active,
            int u,
            int v) {
        g.drawImage(widgets.getSubimage(active ? 0 : 90, 0, 18, 18), x, y, null);
        g.drawImage(icons.getSubimage(u, v, 16, 16), x + 1, y + 1, null);
    }

    private static BufferedImage image(ResourcePackBuilder b, String name) throws IOException {
        byte[] png = b.getDataOrSource("assets/create/textures/gui/" + name + ".png");
        if (png == null) throw new IOException("Missing " + name);
        return PngCodec.decode(png);
    }

    private static String png(ResourcePackBuilder b, String name, BufferedImage img) throws IOException {
        b.addData("assets/create/textures/font/" + name + ".png", PngCodec.encode(img));
        return "create:font/" + name + ".png";
    }
}
