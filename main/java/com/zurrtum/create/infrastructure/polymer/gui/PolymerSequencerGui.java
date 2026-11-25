package com.zurrtum.create.infrastructure.polymer.gui;

import static com.zurrtum.create.infrastructure.polymer.gui.PolymerGuiModels.caseOf;
import static com.zurrtum.create.infrastructure.polymer.gui.PolymerGuiModels.select;
import static com.zurrtum.create.infrastructure.polymer.gui.PolymerGuiModels.translation;
import static dev.veeda.createpoly.assets.PackJson.array;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.compat.computercraft.AbstractComputerBehaviour;
import com.zurrtum.create.content.kinetics.transmission.sequencer.Instruction;
import com.zurrtum.create.content.kinetics.transmission.sequencer.InstructionSpeedModifiers;
import com.zurrtum.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import com.zurrtum.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import com.zurrtum.create.infrastructure.polymer.PolymerInventorySync;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerGogglesOverlay;
import dev.veeda.createpoly.assets.CreateIds;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Vector;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;

final class PolymerSequencerGui extends SimpleGui {
    private static final int ROWS = 5;
    private static final int PANEL_Y = -6;
    private static final int WIDEN = 12, SOURCE_W = 173;
    private static final int CANVAS_W = 186, CANVAS_H = 174;
    private static final int BAR_SOURCE_Y = 132, BAR_H = 29, BAR_CANVAS_Y = CANVAS_H - BAR_H;
    private static final int LANE_X = 86, LANE_W = 34;
    private static final int FOREGROUND_SLOT = 4, ITEM_CX = 88, ITEM_CY = 26;
    private static final PolymerGuiModels.Folder ASSETS =
            new PolymerGuiModels.Folder("sequencer", ITEM_CX, ITEM_CY, false);
    private static final int TEXT = 0xFFFFEE, SHADOW = 0x3F3F3B;
    private static final String[] VARIANTS = {"a0", "a1", "a2", "a3", "d0", "d1", "d2", "d3", "delay", "await", "end"};
    private static volatile Map<Integer, Integer> glyphCodes = Map.of();

    private enum Kind {
        TYPE,
        VALUE,
        SPEED,
        CONFIRM
    }

    private record Control(Kind kind, int row) {}

    private record Step(SequencerInstructions mode, int value, InstructionSpeedModifiers speed) {}

    private final SequencedGearshiftBlockEntity gearshift;
    private final ServerLevel level;
    private final List<Step> before;
    private final List<Step> draft;
    private final Map<Integer, Control> controls = new HashMap<>();
    private final PolymerGuiLifecycle lifecycle = new PolymerGuiLifecycle(10);

    private PolymerSequencerGui(
            ServerPlayer player, SequencedGearshiftBlockEntity gearshift, ServerLevel level, List<Step> before) {
        super(MenuType.GENERIC_9x3, player, true);
        this.gearshift = gearshift;
        this.level = level;
        this.before = before;
        this.draft = new ArrayList<>(before);
        PolymerGuiControls.configure(this);
        bindControls();
        refresh();
    }

    static void open(ServerPlayer player, SequencedGearshiftBlockEntity gearshift) {
        if (!(gearshift.getLevel() instanceof ServerLevel level)) return;
        List<Step> steps = new ArrayList<>();
        for (Instruction instruction : gearshift.getInstructions())
            steps.add(new Step(instruction.instruction, instruction.value, instruction.speedModifier));
        new PolymerSequencerGui(player, gearshift, level, List.copyOf(steps)).open();
    }

    @Override
    public boolean stillValid() {
        return PolymerGuiControls.editableTarget(player, gearshift, level, AllBlocks.SEQUENCED_GEARSHIFT)
                && !AbstractComputerBehaviour.contains(gearshift);
    }

    private void bindControls() {
        for (int row = 0; row < ROWS; row++) {
            int top = 20 + 22 * row + PANEL_Y;
            bind(30, top, 50, Kind.TYPE, row);
            bind(88, top, 28, Kind.VALUE, row);
            bind(118, top, 28, Kind.SPEED, row);
        }
        bind(140 + WIDEN, BAR_CANVAS_Y + PANEL_Y + (161 - 24 - BAR_SOURCE_Y), 18, Kind.CONFIRM, -1);
    }

    private void bind(int x, int y, int w, Kind kind, int row) {
        Control control = new Control(kind, row);
        for (int slot = 0; slot < getSize(); slot++) {
            int cx = 8 + slot % 9 * 18 + 8, cy = slotY(slot) + 8;
            if (cx >= x && cx < x + w && cy >= y && cy < y + 18) controls.put(slot, control);
        }
    }

    private static int slotY(int slot) {
        int rows = 3, top = rows * 9;
        return slot < top
                ? 18 + slot / 9 * 18
                : slot < top + 27 ? 31 + rows * 18 + (slot - top) / 9 * 18 : 89 + rows * 18;
    }

    private void refresh() {
        ItemStack foreground = foreground();
        for (int slot = 0; slot < getSize(); slot++) {
            ItemStack stack;
            if (slot == FOREGROUND_SLOT) stack = foreground.copy();
            else {
                stack = new ItemStack(Items.PAPER);
                stack.set(DataComponents.ITEM_MODEL, CreateIds.id("ui/sequencer_empty"));
            }
            Control control = controls.get(slot);
            List<Component> tooltip = control == null ? null : tooltip(control);
            setSlot(slot, PolymerGuiControls.decoration(stack, tooltip));
        }
    }

    private ItemStack foreground() {
        List<String> strings = new ArrayList<>(Collections.nCopies(ROWS + ROWS * LANE_W, ""));
        for (int row = 0; row < draft.size() && row < ROWS; row++) {
            Step step = draft.get(row);
            strings.set(row, variant(step));
            if (!hasValue(step.mode())) continue;
            String text = formatValue(step);
            int x = 102 - PolymerGogglesOverlay.width(text) / 2;
            for (int cp : text.codePoints().toArray()) {
                int lane = x - LANE_X;
                if (cp != ' ' && lane >= 0 && lane < LANE_W && glyphCodes.containsKey(cp))
                    strings.set(ROWS + row * LANE_W + lane, Character.toString(cp));
                x += PolymerGogglesOverlay.width(Character.toString(cp));
            }
        }
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.ITEM_MODEL, CreateIds.id("ui/sequencer_foreground"));
        stack.set(
                DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(), List.copyOf(strings), List.of()));
        return stack;
    }

    private static String variant(Step step) {
        return switch (step.mode()) {
            case TURN_ANGLE -> "a" + step.speed().ordinal();
            case TURN_DISTANCE -> "d" + step.speed().ordinal();
            case DELAY -> "delay";
            case AWAIT -> "await";
            case END -> "end";
        };
    }

    private List<Component> tooltip(Control control) {
        if (control.kind() == Kind.CONFIRM) {
            return List.of(
                    Component.literal(PolymerSchematicGuis.all().getOrDefault("create.action.confirm", "Confirm"))
                            .withStyle(ChatFormatting.WHITE),
                    PolymerGuiControls.hint("Saves the program and closes, like Escape"));
        }
        if (control.row() >= draft.size()) return null;
        Step step = draft.get(control.row());
        List<Component> lines = new ArrayList<>();
        switch (control.kind()) {
            case TYPE -> {
                lines.add(PolymerGuiControls.title(PolymerSchematicGuis.all()
                        .getOrDefault("create.gui.sequenced_gearshift.instruction", "Instruction")));
                for (SequencerInstructions mode : SequencerInstructions.values())
                    lines.add(PolymerGuiControls.option(
                            PolymerSchematicGuis.all()
                                    .getOrDefault(instructionKey(mode) + ".descriptive", mode.getSerializedName()),
                            mode == step.mode()));
                lines.add(PolymerGuiControls.hint("Left-click: next   Right-click: previous"));
            }
            case VALUE -> {
                if (!hasValue(step.mode())) return null;
                lines.add(PolymerGuiControls.title(
                        PolymerSchematicGuis.all().getOrDefault(parameterKey(step.mode()), "Value")));
                lines.add(Component.literal(formatValue(step))
                        .withStyle(s -> s.withColor(0xFFFFEE).withItalic(false)));
                lines.add(PolymerGuiControls.hint("Left-click: +" + step(step.mode(), step.value(), true, false)
                        + "   Right-click: -" + step(step.mode(), step.value(), false, false)));
                lines.add(PolymerGuiControls.hint("Shift: ±" + step(step.mode(), step.value(), true, true)
                        + "   Range 1-" + maxValue(step.mode())));
            }
            case SPEED -> {
                if (!step.mode().needsPropagation()) return null;
                lines.add(PolymerGuiControls.title(PolymerSchematicGuis.all()
                        .getOrDefault("create.gui.sequenced_gearshift.speed", "Speed, Direction")));
                for (InstructionSpeedModifiers speed : InstructionSpeedModifiers.values())
                    lines.add(PolymerGuiControls.option(
                            PolymerSchematicGuis.all()
                                    .getOrDefault(
                                            "create.gui.sequenced_gearshift.speed." + speed.getSerializedName(),
                                            speed.getSerializedName()),
                            speed == step.speed()));
                lines.add(PolymerGuiControls.hint("Left-click: next   Right-click: previous"));
            }
            default -> {
                return null;
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
        Control control = controls.get(slot);
        if (control == null) return false;
        if (control.kind() == Kind.CONFIRM) {
            close();
            return false;
        }
        if (control.row() >= draft.size()) return false;
        Step step = draft.get(control.row());
        boolean forward = click.isLeft;
        switch (control.kind()) {
            case TYPE -> {
                SequencerInstructions[] modes = SequencerInstructions.values();
                instructionUpdated(
                        control.row(), modes[Math.floorMod(step.mode().ordinal() + (forward ? 1 : -1), modes.length)]);
            }
            case VALUE -> {
                if (!hasValue(step.mode())) return false;
                int delta = step(step.mode(), step.value(), forward, click.shift);
                int value = Math.clamp(step.value() + (forward ? delta : -delta), 1, maxValue(step.mode()));
                draft.set(control.row(), new Step(step.mode(), value, step.speed()));
            }
            case SPEED -> {
                if (!step.mode().needsPropagation()) return false;
                InstructionSpeedModifiers[] speeds = InstructionSpeedModifiers.values();
                draft.set(
                        control.row(),
                        new Step(
                                step.mode(),
                                step.value(),
                                speeds[Math.floorMod(step.speed().ordinal() + (forward ? 1 : -1), speeds.length)]));
            }
            default -> {
                return false;
            }
        }
        refresh();
        return false;
    }

    private void instructionUpdated(int row, SequencerInstructions mode) {
        Step previous = draft.get(row);
        draft.set(row, new Step(mode, defaultValue(mode), previous.speed()));
        if (mode == SequencerInstructions.END) {
            draft.subList(row + 1, draft.size()).clear();
        } else if (row + 1 < ROWS && row + 1 == draft.size()) {
            draft.add(new Step(SequencerInstructions.END, 1, InstructionSpeedModifiers.FORWARD));
        }
    }

    @Override
    public void onTick() {
        lifecycle.tickWhileValid(stillValid(), this::close, wrappedMenu);
    }

    @Override
    public void onRemoved() {
        lifecycle.close(this::apply, () -> {
            controls.clear();
            PolymerInventorySync.afterMenu(player);
        });
    }

    private void apply() {
        if (draft.equals(before)) return;
        if (!stillValid()) {
            player.sendOverlayMessage(Component.literal("Sequenced Gearshift is out of reach; changes were not saved.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        List<Step> current = new ArrayList<>();
        for (Instruction instruction : gearshift.getInstructions())
            current.add(new Step(instruction.instruction, instruction.value, instruction.speedModifier));
        if (!current.equals(before)) {
            player.sendOverlayMessage(Component.literal(
                            "This Sequenced Gearshift was changed by someone else; your changes were not saved.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        Vector<Instruction> instructions = new Vector<>(ROWS);
        for (Step step : draft) instructions.add(new Instruction(step.mode(), step.speed(), step.value()));
        gearshift.run(-1);
        gearshift.instructions = instructions;
        gearshift.notifyUpdate();
    }

    private static boolean hasValue(SequencerInstructions mode) {
        return mode == SequencerInstructions.TURN_ANGLE
                || mode == SequencerInstructions.TURN_DISTANCE
                || mode == SequencerInstructions.DELAY;
    }

    private static int maxValue(SequencerInstructions mode) {
        return switch (mode) {
            case TURN_ANGLE -> 360;
            case TURN_DISTANCE -> 128;
            case DELAY -> 600;
            default -> 1;
        };
    }

    private static int defaultValue(SequencerInstructions mode) {
        return switch (mode) {
            case TURN_ANGLE -> 90;
            case TURN_DISTANCE -> 5;
            case DELAY -> 10;
            default -> -1;
        };
    }

    private static int step(SequencerInstructions mode, int value, boolean forward, boolean shift) {
        if (mode == SequencerInstructions.DELAY) {
            int v = forward ? value : value - 1;
            if (v < 20) return shift ? 20 : 1;
            return shift ? 100 : 20;
        }
        if (!shift) return 1;
        return mode == SequencerInstructions.TURN_ANGLE ? 45 : 5;
    }

    private static String formatValue(Step step) {
        return switch (step.mode()) {
            case TURN_ANGLE ->
                step.value() + PolymerSchematicGuis.all().getOrDefault("create.generic.unit.degrees", "°");
            case TURN_DISTANCE -> step.value() + "m";
            case DELAY -> step.value() >= 20 ? step.value() / 20 + "s" : step.value() + "t";
            default -> String.valueOf(step.value());
        };
    }

    private static String instructionKey(SequencerInstructions mode) {
        return "create.gui.sequenced_gearshift.instruction." + mode.name().toLowerCase(Locale.ROOT);
    }

    private static String parameterKey(SequencerInstructions mode) {
        return instructionKey(mode)
                + switch (mode) {
                    case TURN_ANGLE -> ".angle";
                    case TURN_DISTANCE -> ".distance";
                    default -> "";
                };
    }

    private static String speedLabel(InstructionSpeedModifiers speed) {
        return switch (speed) {
            case FORWARD_FAST -> ">>";
            case FORWARD -> "->";
            case BACK -> "<-";
            case BACK_FAST -> "<<";
        };
    }

    static void addAssets(ResourcePackBuilder builder) {
        try {
            Map<Integer, BufferedImage> glyphs = PolymerArtKit.asciiGlyphs(builder);
            byte[] png = builder.getDataOrSource("assets/create/textures/gui/sequencer.png");
            if (png == null) throw new IOException("Missing sequencer.png");
            BufferedImage atlas = PngCodec.decode(png);

            BufferedImage source = new BufferedImage(173, 161, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = source.createGraphics();
            g.drawImage(atlas.getSubimage(0, 0, 173, 161), 0, 0, null);
            for (int row = 0; row < ROWS; row++)
                g.drawImage(atlas.getSubimage(0, 104, 162, 22), 0, 16 + 22 * row, null);
            g.dispose();
            BufferedImage canvas = new BufferedImage(CANVAS_W, CANVAS_H, BufferedImage.TYPE_INT_ARGB);
            g = canvas.createGraphics();
            widened(g, source.getSubimage(0, 0, SOURCE_W, 130), 0, 150);
            for (int y = 130; y < BAR_CANVAS_Y - 2; y++)
                widened(g, source.getSubimage(0, 128 + (y & 1), SOURCE_W, 1), y, 150);
            widened(g, source.getSubimage(0, 130, SOURCE_W, 2), BAR_CANVAS_Y - 2, 150);
            widened(g, source.getSubimage(0, BAR_SOURCE_Y, SOURCE_W, BAR_H), BAR_CANVAS_Y, 100);
            g.dispose();
            String title = PolymerSchematicGuis.all()
                    .getOrDefault("create.gui.sequenced_gearshift.title", "Sequenced Gearshift");
            PolymerArtKit.rawText(
                    canvas,
                    glyphs,
                    (165 + WIDEN) / 2 - PolymerGogglesOverlay.width(title) / 2,
                    4,
                    title,
                    0x592424,
                    false);

            JsonArray models = new JsonArray();
            for (int qy = 0; qy < 2; qy++)
                for (int qx = 0; qx < 2; qx++) {
                    String name = "panel_" + qx + "_" + qy;
                    int w = CANVAS_W / 2, h = CANVAS_H / 2;
                    ASSETS.png(builder, name, canvas.getSubimage(qx * w, qy * h, w, h));
                    ASSETS.quad(builder, name, w, h, 4);
                    JsonObject model = ASSETS.model(name);
                    model.add("transformation", ASSETS.centred(qx * w + w / 2f, PANEL_Y + qy * h + h / 2f, 0, 4));
                    models.add(model);
                }

            for (String variant : VARIANTS) {
                SequencerInstructions mode = switch (variant) {
                    case "delay" -> SequencerInstructions.DELAY;
                    case "await" -> SequencerInstructions.AWAIT;
                    case "end" -> SequencerInstructions.END;
                    default ->
                        variant.charAt(0) == 'a'
                                ? SequencerInstructions.TURN_ANGLE
                                : SequencerInstructions.TURN_DISTANCE;
                };
                int v = switch (mode) {
                    case TURN_ANGLE, TURN_DISTANCE -> 16;
                    case DELAY -> 60;
                    case AWAIT -> 162;
                    default -> 82;
                };
                BufferedImage strip = new BufferedImage(162, 22, BufferedImage.TYPE_INT_ARGB);
                g = strip.createGraphics();
                g.drawImage(atlas.getSubimage(0, v, 162, 22), 0, 0, null);
                g.dispose();
                PolymerArtKit.rawText(
                        strip,
                        glyphs,
                        36,
                        9,
                        PolymerSchematicGuis.all().getOrDefault(instructionKey(mode), mode.getSerializedName()),
                        TEXT,
                        true);
                if (mode.needsPropagation())
                    PolymerArtKit.rawText(
                            strip,
                            glyphs,
                            127,
                            9,
                            speedLabel(InstructionSpeedModifiers.values()[variant.charAt(1) - '0']),
                            TEXT,
                            true);
                ASSETS.png(builder, "row_" + variant, strip);
                ASSETS.quad(builder, "row_" + variant, 162, 22, 4);
            }
            for (int row = 0; row < ROWS; row++) {
                JsonArray cases = new JsonArray();
                for (String variant : VARIANTS) cases.add(caseOf(variant, ASSETS.model("row_" + variant)));
                JsonObject select = select(row, cases);
                select.add("transformation", ASSETS.centred(81, PANEL_Y + 16 + 22 * row + 11, 0.01f, 4));
                models.add(select);
            }

            String alphabet =
                    "0123456789smt" + PolymerSchematicGuis.all().getOrDefault("create.generic.unit.degrees", "°");
            Map<Integer, Integer> codes = new HashMap<>();
            for (int cp : alphabet.codePoints().toArray()) {
                BufferedImage glyph = glyphs.get(cp);
                if (glyph == null) continue;
                BufferedImage shadowed = new BufferedImage(9, 9, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < 8; y++)
                    for (int x = 0; x < 8; x++)
                        if ((glyph.getRGB(x, y) >>> 24) != 0) {
                            shadowed.setRGB(x + 1, y + 1, 0xFF000000 | SHADOW);
                        }
                for (int y = 0; y < 8; y++)
                    for (int x = 0; x < 8; x++)
                        if ((glyph.getRGB(x, y) >>> 24) != 0) {
                            shadowed.setRGB(x, y, 0xFF000000 | TEXT);
                        }
                ASSETS.png(builder, "glyph_" + cp, shadowed);
                glyphQuad(builder, "glyph_" + cp);
                codes.put(cp, cp);
            }
            for (int row = 0; row < ROWS; row++) {
                JsonArray cases = new JsonArray();
                for (int cp : codes.keySet()) cases.add(caseOf(Character.toString(cp), ASSETS.model("glyph_" + cp)));
                for (int lane = 0; lane < LANE_W; lane++) {
                    JsonObject select = select(ROWS + row * LANE_W + lane, cases);
                    select.add(
                            "transformation",
                            translation(
                                    (LANE_X + lane - ITEM_CX) / 16f,
                                    (ITEM_CY - (PANEL_Y + 25 + 22 * row)) / 16f,
                                    0.02f));
                    models.add(select);
                }
            }
            glyphCodes = Map.copyOf(codes);

            JsonObject composite = new JsonObject();
            composite.addProperty("type", "minecraft:composite");
            composite.add("models", models);
            JsonObject item = new JsonObject();
            item.addProperty("oversized_in_gui", true);
            item.add("model", composite);
            builder.addStringData("assets/create/items/ui/sequencer_foreground.json", item.toString());
            builder.addStringData(
                    "assets/create/items/ui/sequencer_empty.json", "{\"model\":{\"type\":\"minecraft:empty\"}}");
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Cannot build Sequenced Gearshift UI source art", exception);
        }
    }

    private static void widened(Graphics2D g, BufferedImage part, int y, int at) {
        g.drawImage(part.getSubimage(0, 0, at + WIDEN, part.getHeight()), 0, y, null);
        g.drawImage(part.getSubimage(at, 0, part.getWidth() - at, part.getHeight()), at + WIDEN, y, null);
    }

    private static void glyphQuad(ResourcePackBuilder builder, String name) {
        ASSETS.quad(builder, name, array(8, -1, 8), array(17, 8, 8), 1);
    }
}
