package com.zurrtum.create.infrastructure.polymer.gui;

import static com.zurrtum.create.infrastructure.polymer.gui.PolymerGuiModels.caseOf;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.AllHandle;
import com.zurrtum.create.api.behaviour.display.DisplaySource;
import com.zurrtum.create.api.behaviour.display.DisplayTarget;
import com.zurrtum.create.api.registry.CreateRegistries;
import com.zurrtum.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.zurrtum.create.content.redstone.displayLink.DisplayLinkContext;
import com.zurrtum.create.content.redstone.displayLink.source.FillLevelDisplaySource;
import com.zurrtum.create.content.redstone.displayLink.source.ItemThroughputDisplaySource;
import com.zurrtum.create.content.redstone.displayLink.source.KineticSpeedDisplaySource;
import com.zurrtum.create.content.redstone.displayLink.source.KineticStressDisplaySource;
import com.zurrtum.create.content.redstone.displayLink.source.RedstonePowerDisplaySource;
import com.zurrtum.create.content.redstone.displayLink.source.ScoreboardDisplaySource;
import com.zurrtum.create.content.redstone.displayLink.source.SingleLineDisplaySource;
import com.zurrtum.create.content.redstone.displayLink.source.StatTrackingDisplaySource;
import com.zurrtum.create.content.redstone.displayLink.source.StationSummaryDisplaySource;
import com.zurrtum.create.content.redstone.displayLink.source.TimeOfDayDisplaySource;
import com.zurrtum.create.content.redstone.displayLink.source.ValueListDisplaySource;
import com.zurrtum.create.infrastructure.packet.c2s.DisplayLinkConfigurationPacket;
import com.zurrtum.create.infrastructure.polymer.PolymerInventorySync;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerGogglesOverlay;
import dev.veeda.createpoly.assets.CreateIds;
import dev.veeda.createpoly.assets.PackAssetWriter;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import org.jspecify.annotations.Nullable;

final class PolymerDisplayLinkGui extends SimpleGui {
    private static final int ART_X = -42, ART_W = 235, ART_H = 162, FILLER_AT = 127, FILLER = 24;
    private static final int CANVAS_W = 236, CANVAS_H = ART_H + FILLER;
    private static final int FOREGROUND_SLOT = 4, ITEM_CX = 88, ITEM_CY = 26;
    private static final PolymerGuiModels.Folder ASSETS =
            new PolymerGuiModels.Folder("display_link", ITEM_CX, ITEM_CY, true);
    private static final int WHITE = 0xFFFFFF, GREY = 0xD3D3D3;
    private static final PolymerGuiPixelText.Layout LANES = new PolymerGuiPixelText.Layout(
            new PolymerGuiPixelText.Lane(65, 30, 135, 0, 1),
            new PolymerGuiPixelText.Lane(60, 51, 140, 2, 3),
            new PolymerGuiPixelText.Lane(60, 72, 140, 4, 5),
            new PolymerGuiPixelText.Lane(65, 109, 135, 6, 7));

    private static volatile Map<Integer, BufferedImage> textGlyphs = Map.of();
    private static final Map<String, int[]> BOXES = new TreeMap<>(Map.of(
            "t137",
            new int[] {0, 137},
            "s120",
            new int[] {0, 120},
            "s95",
            new int[] {0, 95},
            "s80",
            new int[] {0, 80},
            "s75",
            new int[] {0, 75},
            "s60",
            new int[] {0, 60},
            "station",
            new int[] {0, 32, 36, 22}));

    private static final Identifier TEXT_ACTION = CreateIds.id("polymer_display_link_text");
    private static final PolymerDialogSessions<TextEdit> TEXT_EDITS =
            new PolymerDialogSessions<>(Duration.ofMinutes(2));
    private static final PolymerDialogForms.TextField TEXT = new PolymerDialogForms.TextField("text", 64);

    private enum Kind {
        SELECT,
        RANGE,
        TEXT
    }

    private record Widget(
            int line,
            int x,
            int width,
            String key,
            Kind kind,
            String @Nullable [] choices,
            int max,
            int shift,
            int initial,
            String suffix,
            String title) {}

    private enum Control {
        SOURCE,
        TARGET,
        CONFIRM,
        WIDGET
    }

    private record Binding(Control control, int widget) {}

    private record TextEdit(
            DisplayLinkBlockEntity link,
            ServerLevel level,
            String key,
            DisplaySource source,
            CompoundTag expectedConfig,
            int line) {}

    private final DisplayLinkBlockEntity link;
    private final ServerLevel level;
    private final List<DisplaySource> sources;
    private final @Nullable DisplayTarget target;
    private final int rows;
    private final CompoundTag config;
    private final Map<Integer, Binding> bindings = new HashMap<>();
    private List<Widget> widgets = List.of();
    private int source;
    private int line;
    private final PolymerGuiLifecycle lifecycle = new PolymerGuiLifecycle(10);

    private PolymerDisplayLinkGui(ServerPlayer player, DisplayLinkBlockEntity link, ServerLevel level) {
        super(MenuType.GENERIC_9x4, player, true);
        this.link = link;
        this.level = level;
        sources = List.copyOf(DisplaySource.getAll(level, link.getSourcePosition()));
        target = DisplayTarget.get(level, link.getTargetPosition());
        DisplayLinkContext context = new DisplayLinkContext(level, link);
        rows = target == null ? 0 : Math.max(1, target.provideStats(context).maxRows());
        line = target == null ? 0 : Math.clamp(link.targetLine, 0, rows - 1);
        config = link.getSourceConfig().copy();
        source = link.activeSource == null ? 0 : Math.max(sources.indexOf(link.activeSource), 0);
        PolymerGuiControls.configure(this);
        layout();
    }

    static void open(ServerPlayer player, DisplayLinkBlockEntity link) {
        TEXT_EDITS.bindToServer();
        TEXT_EDITS.forget(player.getUUID());
        if (link.getLevel() instanceof ServerLevel level
                && PolymerGuiControls.editableTarget(player, link, level, AllBlocks.DISPLAY_LINK))
            new PolymerDisplayLinkGui(player, link, level).open();
    }

    @Override
    public boolean stillValid() {
        return PolymerGuiControls.editableTarget(player, link, level, AllBlocks.DISPLAY_LINK);
    }

    private void layout() {
        bindings.clear();
        widgets = sources.isEmpty() ? List.of() : widgets(sources.get(source), new DisplayLinkContext(level, link));
        if (sources.size() > 1) bind(61, 26, 135, new Binding(Control.SOURCE, -1));
        for (int i = 0; i < widgets.size(); i++) {
            Widget widget = widgets.get(i);
            bind(60 + widget.x(), widget.line() == 0 ? 47 : 68, widget.width(), new Binding(Control.WIDGET, i));
        }
        if (rows > 1) bind(61, 105, 135, new Binding(Control.TARGET, -1));
        bind(202, 138 + FILLER, 18, new Binding(Control.CONFIRM, -1));
        refresh();
    }

    private void bind(int ax, int ay, int w, Binding binding) {
        for (int slot = 0; slot < getSize(); slot++) {
            int cx = 8 + slot % 9 * 18 + 8 - ART_X, cy = slotY(slot) + 8;
            if (cx >= ax && cx < ax + w && cy >= ay && cy < ay + 18) bindings.put(slot, binding);
        }
    }

    private static int slotY(int slot) {
        int rows = 4, top = rows * 9;
        return slot < top
                ? 18 + slot / 9 * 18
                : slot < top + 27 ? 31 + rows * 18 + (slot - top) / 9 * 18 : 89 + rows * 18;
    }

    private static List<Widget> widgets(DisplaySource source, DisplayLinkContext context) {
        List<Widget> list = new ArrayList<>();
        if (source instanceof SingleLineDisplaySource single && single.allowsLabeling(context))
            list.add(text(0, "Label", "create.display_source.label"));
        if (source instanceof FillLevelDisplaySource)
            list.add(select(
                    1,
                    120,
                    "Mode",
                    "create.display_source.fill_level.display",
                    "display_source.fill_level",
                    "percent",
                    "progress_bar"));
        else if (source instanceof RedstonePowerDisplaySource)
            list.add(select(
                    1,
                    120,
                    "Mode",
                    "create.display_source.redstone_power.display",
                    "display_source.redstone_power",
                    "number",
                    "progress_bar"));
        else if (source instanceof KineticStressDisplaySource)
            list.add(select(
                    1,
                    120,
                    "Mode",
                    "create.display_source.kinetic_stress.display",
                    "display_source.kinetic_stress",
                    "progress_bar",
                    "percent",
                    "current",
                    "max",
                    "remaining"));
        else if (source instanceof KineticSpeedDisplaySource)
            list.add(select(
                    1,
                    95,
                    "Directional",
                    "create.display_source.kinetic_speed.display",
                    "display_source.kinetic_speed",
                    "absolute",
                    "directional"));
        else if (source instanceof ItemThroughputDisplaySource)
            list.add(select(
                    1,
                    80,
                    "Interval",
                    "create.display_source.item_throughput.interval",
                    "display_source.item_throughput.interval",
                    "second",
                    "minute",
                    "hour"));
        else if (source instanceof TimeOfDayDisplaySource)
            list.add(select(
                    1, 60, "Cycle", "create.display_source.time.format", "display_source.time", "12_hour", "24_hour"));
        else if (source instanceof StationSummaryDisplaySource) {
            list.add(text(0, "Filter", "create.display_source.station_summary.filter"));
            list.add(new Widget(
                    1,
                    0,
                    32,
                    "NameColumn",
                    Kind.RANGE,
                    null,
                    73,
                    12,
                    50,
                    "%",
                    "create.display_source.station_summary.train_name_column"));
            list.add(new Widget(
                    1,
                    36,
                    22,
                    "PlatformColumn",
                    Kind.RANGE,
                    null,
                    16,
                    4,
                    3,
                    "",
                    "create.display_source.station_summary.platform_column"));
        } else if (source instanceof ScoreboardDisplaySource && !(source instanceof StatTrackingDisplaySource))
            list.add(text(0, "Objective", "create.display_source.scoreboard.objective"));
        else if (source instanceof ValueListDisplaySource && !(source instanceof ScoreboardDisplaySource))
            list.add(select(
                    0,
                    75,
                    "Format",
                    "create.display_source.value_list.display",
                    "display_source.value_list",
                    "shortened",
                    "full_number"));
        return list;
    }

    private static Widget text(int line, String key, String title) {
        return new Widget(line, 0, 137, key, Kind.TEXT, null, 0, 0, 0, "", title);
    }

    private static Widget select(int line, int width, String key, String title, String prefix, String... names) {
        String[] keys = new String[names.length];
        for (int i = 0; i < names.length; i++) keys[i] = "create." + prefix + "." + names[i];
        return new Widget(line, 0, width, key, Kind.SELECT, keys, keys.length - 1, 1, 0, "", title);
    }

    private int value(Widget widget) {
        return config.contains(widget.key())
                ? Math.clamp(config.getIntOr(widget.key(), widget.initial()), 0, widget.max())
                : widget.initial();
    }

    private String shown(Widget widget) {
        return switch (widget.kind()) {
            case TEXT -> config.getStringOr(widget.key(), "");
            case SELECT -> PolymerSchematicGuis.lang(widget.choices()[value(widget)]);
            case RANGE -> value(widget) + widget.suffix();
        };
    }

    private void refresh() {
        ItemStack foreground = foreground();
        for (int slot = 0; slot < getSize(); slot++) {
            ItemStack stack;
            if (slot == FOREGROUND_SLOT) stack = foreground.copy();
            else {
                stack = new ItemStack(Items.PAPER);
                stack.set(DataComponents.ITEM_MODEL, CreateIds.id("ui/display_link_empty"));
            }
            Binding binding = bindings.get(slot);
            List<Component> tooltip = binding == null ? null : tooltip(binding);
            setSlot(slot, PolymerGuiControls.decoration(stack, tooltip));
        }
    }

    private ItemStack foreground() {
        List<String> strings = List.of(boxKey(0), boxKey(1));
        List<Boolean> pixels = LANES.blank();
        List<Integer> colors = new ArrayList<>(Collections.nCopies(LANES.tints(), WHITE));
        if (sources.isEmpty())
            write(pixels, colors, 0, 65, PolymerSchematicGuis.lang("create.display_link.no_source"), GREY);
        else write(pixels, colors, 0, 65, resolve(sources.get(source).getName()), WHITE);
        for (Widget widget : widgets) {
            write(pixels, colors, 1 + widget.line(), 60 + widget.x() + 5, shown(widget), WHITE);
        }
        if (target == null)
            write(pixels, colors, 3, 65, PolymerSchematicGuis.lang("create.display_link.no_target"), GREY);
        else write(pixels, colors, 3, 65, resolve(target.getLineOptionText(line)), WHITE);
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.ITEM_MODEL, CreateIds.id("ui/display_link_foreground"));
        stack.set(
                DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.copyOf(pixels), strings, List.copyOf(colors)));
        return stack;
    }

    private String boxKey(int line) {
        List<Widget> on = widgets.stream().filter(w -> w.line() == line).toList();
        if (on.isEmpty()) return "";
        if (on.size() > 1) return "station";
        Widget widget = on.getFirst();
        return (widget.kind() == Kind.TEXT ? "t" : "s") + widget.width();
    }

    private static void write(List<Boolean> pixels, List<Integer> colors, int lane, int x, String text, int color) {
        LANES.color(colors, lane, color);
        LANES.write(pixels, lane, x, text, textGlyphs, Map.of());
    }

    private @Nullable List<Component> tooltip(Binding binding) {
        List<Component> lines = new ArrayList<>();
        switch (binding.control()) {
            case CONFIRM -> {
                lines.add(PolymerGuiControls.title(PolymerSchematicGuis.lang("create.action.confirm")));
                lines.add(PolymerGuiControls.hint("Saves and closes, like Escape"));
            }
            case SOURCE -> {
                lines.add(PolymerGuiControls.title(PolymerSchematicGuis.lang("create.display_link.information_type")));
                for (int i = 0; i < sources.size(); i++)
                    lines.add(PolymerGuiControls.option(resolve(sources.get(i).getName()), i == source));
                lines.add(PolymerGuiControls.hint("Left-click: next   Right-click: previous"));
            }
            case TARGET -> {
                if (target == null) return null;
                lines.add(PolymerGuiControls.title(PolymerSchematicGuis.lang(
                        sources.isEmpty() || sources.get(source) instanceof SingleLineDisplaySource
                                ? "create.display_link.display_on"
                                : "create.display_link.display_on_multiline")));
                lines.add(PolymerGuiControls.option(resolve(target.getLineOptionText(line)), true));
                lines.add(PolymerGuiControls.hint("Left-click: next   Right-click: previous"));
            }
            case WIDGET -> {
                Widget widget = widgets.get(binding.widget());
                lines.add(PolymerGuiControls.title(PolymerSchematicGuis.lang(widget.title())));
                switch (widget.kind()) {
                    case SELECT -> {
                        for (int i = 0; i < widget.choices().length; i++)
                            lines.add(PolymerGuiControls.option(
                                    PolymerSchematicGuis.lang(widget.choices()[i]), i == value(widget)));
                        lines.add(PolymerGuiControls.hint("Left-click: next   Right-click: previous"));
                    }
                    case RANGE -> {
                        lines.add(PolymerGuiControls.option(shown(widget), true));
                        lines.add(PolymerGuiControls.hint(
                                "Left-click: +1   Right-click: -1   Shift: ±" + widget.shift()));
                    }
                    case TEXT -> {
                        String value = shown(widget);
                        lines.add(PolymerGuiControls.option(value.isEmpty() ? "(empty)" : value, true));
                        lines.add(PolymerGuiControls.hint(PolymerSchematicGuis.lang("create.gui.schedule.lmb_edit")));
                    }
                }
            }
        }
        return lines;
    }

    @Override
    public boolean onAnyClick(int slot, ClickType click, ContainerInput action) {
        lifecycle.requestSync();
        if (!stillValid()) {
            close();
            return false;
        }
        if (!PolymerGuiControls.primaryClick(click, action)) return false;
        Binding binding = bindings.get(slot);
        if (binding == null) return false;
        int step = click.isLeft ? 1 : -1;
        switch (binding.control()) {
            case CONFIRM -> {
                close();
                return false;
            }
            case SOURCE -> {
                source = Math.floorMod(source + step, sources.size());
                layout();
                return false;
            }
            case TARGET -> line = Math.clamp(line + step, 0, rows - 1);
            case WIDGET -> {
                Widget widget = widgets.get(binding.widget());
                switch (widget.kind()) {
                    case SELECT -> config.putInt(widget.key(), Math.floorMod(value(widget) + step, widget.max() + 1));
                    case RANGE ->
                        config.putInt(
                                widget.key(),
                                Math.clamp(value(widget) + step * (click.shift ? widget.shift() : 1), 0, widget.max()));
                    case TEXT -> {
                        editText(widget);
                        return false;
                    }
                }
            }
        }
        refresh();
        return false;
    }

    private void editText(Widget widget) {
        close();
        if (!stillValid() || sources.isEmpty() || link.activeSource != sources.get(source)) return;
        CompoundTag action = TEXT_EDITS.open(
                player.getUUID(),
                new TextEdit(
                        link,
                        level,
                        widget.key(),
                        link.activeSource,
                        link.getSourceConfig().copy(),
                        link.targetLine));
        Input input = TEXT.input(
                240,
                Component.literal(PolymerSchematicGuis.lang(widget.title())),
                config.getStringOr(widget.key(), ""));
        var common = new CommonDialogData(
                Component.literal(PolymerSchematicGuis.lang("create.display_link.title")),
                Optional.empty(),
                true,
                false,
                DialogAction.CLOSE,
                List.of(new PlainMessage(Component.literal(PolymerSchematicGuis.lang(widget.title())), 240)),
                List.of(input));
        Holder<Dialog> dialog = Holder.direct(new NoticeDialog(
                common,
                new ActionButton(
                        new CommonButtonData(PolymerGuiTextures.actionLabel("Confirm"), 200),
                        Optional.of(new CustomAll(TEXT_ACTION, Optional.of(action))))));
        PolymerDialogForms.open(player, dialog);
    }

    static boolean handleText(ServerPlayer player, Identifier id, Optional<Tag> payload) {
        if (!TEXT_ACTION.equals(id)) return false;
        if (!PolymerDialogForms.validSubmission(player, id, payload, TEXT_ACTION)) return true;
        var data = PolymerDialogForms.payload(payload);
        if (data.isEmpty()) return true;
        var session = TEXT_EDITS.consume(player.getUUID(), data.get());
        if (session.isEmpty()) return true;
        TextEdit edit = session.get();
        DisplayLinkBlockEntity link = edit.link();
        if (!PolymerGuiControls.editableTarget(player, link, edit.level(), AllBlocks.DISPLAY_LINK)) return true;
        var text = TEXT.read(data.get());
        if (text.isPresent()
                && link.activeSource == edit.source()
                && link.targetLine == edit.line()
                && link.getSourceConfig().equals(edit.expectedConfig())) {
            CompoundTag config = new CompoundTag();
            Identifier source = CreateRegistries.DISPLAY_SOURCE.getKey(edit.source());
            if (source != null) config.putString("Id", source.toString());
            config.putString(edit.key(), text.get());
            AllHandle.onDisplayLinkConfiguration(
                    player.connection, new DisplayLinkConfigurationPacket(link.getBlockPos(), config, link.targetLine));
        }
        open(player, link);
        return true;
    }

    @Override
    public void onTick() {
        if (!stillValid()) {
            close();
            return;
        }
        lifecycle.tick(() -> {}, () -> wrappedMenu.sendAllDataToRemote());
    }

    @Override
    public void onRemoved() {
        lifecycle.close(
                () -> {
                    if (stillValid()) apply();
                },
                () -> {
                    bindings.clear();
                    PolymerInventorySync.afterMenu(player);
                });
    }

    private void apply() {
        if (level.getBlockEntity(link.getBlockPos()) != link || link.isRemoved()) return;
        CompoundTag data = new CompoundTag();
        if (!sources.isEmpty()) {
            Identifier id = CreateRegistries.DISPLAY_SOURCE.getKey(sources.get(source));
            if (id != null) data.putString("Id", id.toString());
            for (Widget widget : widgets) {
                if (widget.kind() == Kind.TEXT) data.putString(widget.key(), config.getStringOr(widget.key(), ""));
                else data.putInt(widget.key(), value(widget));
            }
        }
        AllHandle.onDisplayLinkConfiguration(
                player.connection, new DisplayLinkConfigurationPacket(link.getBlockPos(), data, rows > 1 ? line : 0));
    }

    private static String resolve(Component component) {
        if (component.getContents() instanceof TranslatableContents translatable) {
            String pattern = PolymerSchematicGuis.all()
                    .getOrDefault(
                            translatable.getKey(),
                            translatable.getFallback() != null ? translatable.getFallback() : translatable.getKey());
            Object[] args = translatable.getArgs();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i] instanceof Component c ? resolve(c) : String.valueOf(args[i]);
                pattern = pattern.replace("%" + (i + 1) + "$s", arg);
                pattern = pattern.replaceFirst("%s", Matcher.quoteReplacement(arg));
            }
            return pattern;
        }
        return component.getString();
    }

    static void addAssets(ResourcePackBuilder builder) {
        try {
            Map<Integer, BufferedImage> glyphs = PolymerArtKit.asciiGlyphs(builder);
            byte[] png = builder.getDataOrSource("assets/create/textures/gui/display_link.png");
            if (png == null) throw new IOException("Missing display_link.png");
            BufferedImage atlas = PngCodec.decode(png);

            BufferedImage art = atlas.getSubimage(0, 0, ART_W, ART_H);
            BufferedImage canvas = new BufferedImage(CANVAS_W, CANVAS_H, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = canvas.createGraphics();
            g.drawImage(art.getSubimage(0, 0, ART_W, FILLER_AT), 0, 0, null);
            for (int y = 0; y < FILLER; y++)
                g.drawImage(art.getSubimage(0, FILLER_AT - 2 + (y & 1), ART_W, 1), 0, FILLER_AT + y, null);
            g.drawImage(art.getSubimage(0, FILLER_AT, ART_W, ART_H - FILLER_AT), 0, FILLER_AT + FILLER, null);
            g.dispose();
            String title = PolymerSchematicGuis.lang("create.display_link.title");
            PolymerArtKit.rawText(
                    canvas, glyphs, ART_W / 2 - PolymerGogglesOverlay.width(title) / 2, 4, title, 0x592424, false);

            JsonArray models = new JsonArray();
            int w = CANVAS_W / 2, h = CANVAS_H / 2;
            for (int qy = 0; qy < 2; qy++)
                for (int qx = 0; qx < 2; qx++) {
                    String name = "panel_" + qx + "_" + qy;
                    ASSETS.png(builder, name, canvas.getSubimage(qx * w, qy * h, w, h));
                    ASSETS.quad(builder, name, w, h, 4);
                    JsonObject model = ASSETS.model(name);
                    model.add("transformation", ASSETS.centred(ART_X + qx * w + w / 2f, qy * h + h / 2f, 0, 4));
                    models.add(model);
                }

            BufferedImage start = atlas.getSubimage(0, 163, 2, 18),
                    body = atlas.getSubimage(3, 163, 1, 18),
                    end = atlas.getSubimage(5, 163, 2, 18);
            for (int line = 0; line < 2; line++) {
                JsonArray cases = new JsonArray();
                for (var entry : BOXES.entrySet()) {
                    String name = "box_" + entry.getKey();
                    BufferedImage strip = new BufferedImage(140, 18, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D sg = strip.createGraphics();
                    int[] boxes = entry.getValue();
                    for (int i = 0; i < boxes.length; i += 2) {
                        int x = boxes[i], width = boxes[i + 1];
                        for (int px = 0; px < width; px++) sg.drawImage(body, x + px, 0, null);
                        sg.drawImage(start, x, 0, null);
                        sg.drawImage(end, x + width - 2, 0, null);
                    }
                    sg.dispose();
                    if (line == 0) {
                        ASSETS.png(builder, name, strip);
                        ASSETS.quad(builder, name, 140, 18, 4);
                    }
                    cases.add(caseOf(entry.getKey(), ASSETS.model(name)));
                }
                JsonObject select = PolymerGuiModels.select(line, cases);
                select.add("transformation", ASSETS.centred(ART_X + 60 + 70, (line == 0 ? 46 : 67) + 9, 0.01f, 4));
                models.add(select);
            }

            textGlyphs = Map.copyOf(glyphs);
            PolymerGuiPixelText.addAssets(new PackAssetWriter(builder));
            LANES.models(models, ART_X, 0, 0.02f, ITEM_CX, ITEM_CY);

            JsonObject composite = new JsonObject();
            composite.addProperty("type", "minecraft:composite");
            composite.add("models", models);
            JsonObject item = new JsonObject();
            item.addProperty("oversized_in_gui", true);
            item.add("model", composite);
            builder.addStringData("assets/create/items/ui/display_link_foreground.json", item.toString());
            builder.addStringData(
                    "assets/create/items/ui/display_link_empty.json", "{\"model\":{\"type\":\"minecraft:empty\"}}");
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Cannot build Display Link UI source art", exception);
        }
    }
}
