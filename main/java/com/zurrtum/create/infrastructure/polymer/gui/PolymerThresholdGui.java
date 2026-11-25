package com.zurrtum.create.infrastructure.polymer.gui;

import static com.zurrtum.create.infrastructure.polymer.gui.PolymerArtKit.image;
import static com.zurrtum.create.infrastructure.polymer.gui.PolymerGuiModels.caseOf;
import static com.zurrtum.create.infrastructure.polymer.gui.PolymerGuiModels.select;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.AllHandle;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;
import com.zurrtum.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity.ThresholdType;
import com.zurrtum.create.content.redstone.thresholdSwitch.ThresholdSwitchObservable;
import com.zurrtum.create.infrastructure.fluids.BucketFluidInventory;
import com.zurrtum.create.infrastructure.packet.c2s.ConfigureThresholdSwitchPacket;
import com.zurrtum.create.infrastructure.polymer.AdapterInteractions;
import com.zurrtum.create.infrastructure.polymer.PolymerFrogArt;
import com.zurrtum.create.infrastructure.polymer.PolymerInventorySync;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerGogglesOverlay;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import dev.veeda.createpoly.assets.CreateIds;
import dev.veeda.createpoly.assets.PackAssetWriter;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import org.joml.Matrix4f;

public final class PolymerThresholdGui extends SimpleGui {
    private static final int ART_X = -3, ART_Y = -5, ART_W = 182, ART_H = 103, ROWS = 5;
    private static final int FOREGROUND_SLOT = 4, ITEM_CX = 88, ITEM_CY = 26, INVERT = 33, CONFIRM = 35, TARGET = 27;
    private static final PolymerGuiModels.Folder ASSETS =
            new PolymerGuiModels.Folder("threshold", ITEM_CX, ITEM_CY, false);
    private static final PolymerGuiPixelText.Layout LANES = new PolymerGuiPixelText.Layout(
            new PolymerGuiPixelText.Lane(53, 28, 120, 0, 1), new PolymerGuiPixelText.Lane(53, 52, 120, 0, 1));
    private static final int GE = 0x2265, LE = 0x2264;
    private static volatile Map<Integer, BufferedImage> textGlyphs = Map.of();

    private static final Map<Integer, Control> CONTROLS = new HashMap<>();

    private enum Control {
        UPPER,
        LOWER,
        UNIT,
        INVERT,
        CONFIRM,
        TARGET
    }

    private final ThresholdSwitchBlockEntity be;
    private final ServerLevel level;
    private int onAbove, offBelow;
    private boolean stacks;
    private final PolymerGuiLifecycle lifecycle = new PolymerGuiLifecycle(10);

    static {
        bind(48, 23, 48, Control.UPPER);
        bind(48, 47, 48, Control.LOWER);
        bind(100, 23, 52, 42, Control.UNIT);
        CONTROLS.put(INVERT, Control.INVERT);
        CONTROLS.put(CONFIRM, Control.CONFIRM);
        CONTROLS.put(TARGET, Control.TARGET);
    }

    private PolymerThresholdGui(ServerPlayer player, ThresholdSwitchBlockEntity be, ServerLevel level) {
        super(MenuType.GENERIC_9x5, player, false);
        this.be = be;
        this.level = level;
        onAbove = be.onWhenAbove;
        offBelow = be.offWhenBelow;
        stacks = be.inStacks;
        PolymerGuiControls.configure(this, PolymerFrogArt.title("threshold"), true);
        clampInputs();
        refresh();
    }

    static boolean open(ServerPlayer player, ThresholdSwitchBlockEntity be) {
        return be.getLevel() instanceof ServerLevel level
                && PolymerGuiControls.editableTarget(player, be, level, AllBlocks.THRESHOLD_SWITCH)
                && new PolymerThresholdGui(player, be, level).open();
    }

    @Override
    public boolean stillValid() {
        return PolymerGuiControls.editableTarget(player, be, level, AllBlocks.THRESHOLD_SWITCH);
    }

    private static void bind(int x, int y, int w, Control control) {
        bind(x, y, w, 18, control);
    }

    private static void bind(int x, int y, int w, int h, Control control) {
        for (int slot = 0; slot < 9 * ROWS; slot++) {
            int cx = PolymerFrogArt.slotX(slot) + 8 - ART_X, cy = PolymerFrogArt.slotY(slot) + 8 - ART_Y;
            if (cx >= x && cx < x + w && cy >= y && cy < y + h) CONTROLS.put(slot, control);
        }
    }

    private int step() {
        if (be.getTypeOfCurrentTarget() == ThresholdType.FLUID) return BucketFluidInventory.CAPACITY;
        return stacks ? 64 : 1;
    }

    private void clampInputs() {
        int step = step();
        int min = be.currentMinLevel + step, max = be.currentMaxLevel;
        onAbove = Mth.clamp(onAbove / step * step, min, Math.max(min, max));
        min = be.currentMinLevel;
        max = be.currentMaxLevel - step;
        offBelow = Mth.clamp(offBelow / step * step, min, Math.max(min, max));
    }

    private void changeUpper(int state) {
        int step = step();
        onAbove = Mth.clamp(state, be.currentMinLevel + step, Math.max(be.currentMinLevel + step, be.currentMaxLevel));
        if (!(offBelow / step == 0 && onAbove / step == 0) && offBelow / step >= onAbove / step)
            offBelow = (onAbove - step) / step * step;
    }

    private void changeLower(int state) {
        int step = step();
        offBelow = Mth.clamp(state, be.currentMinLevel, Math.max(be.currentMinLevel, be.currentMaxLevel - step));
        if (!(onAbove / step == 0 && offBelow / step == 0) && onAbove / step <= offBelow / step)
            onAbove = (offBelow + step) / step * step;
    }

    private String format(int value) {
        ThresholdType type = be.getTypeOfCurrentTarget();
        if (type == ThresholdType.CUSTOM
                && level.getBlockEntity(be.getTargetPos()) instanceof ThresholdSwitchObservable observable)
            return resolve(observable.format(value));
        String suffix = type == ThresholdType.ITEM
                ? stacks ? "create.schedule.condition.threshold.stacks" : "create.schedule.condition.threshold.items"
                : "create.schedule.condition.threshold.buckets";
        return value + " " + PolymerSchematicGuis.lang(suffix);
    }

    private void refresh() {
        ItemStack foreground = foreground();
        for (int slot = 0; slot < getSize(); slot++) {
            Control control = CONTROLS.get(slot);
            ItemStack stack;
            if (slot == FOREGROUND_SLOT) stack = foreground.copy();
            else if (control == Control.TARGET) {
                ItemStack display = be.getDisplayItemForScreen();
                stack = display.isEmpty() ? new ItemStack(Items.BARRIER) : display.copyWithCount(1);
            } else if (control == Control.INVERT) stack = PolymerGuiTextures.icon("threshold_invert");
            else if (control == Control.CONFIRM) stack = PolymerGuiTextures.icon("threshold_confirm");
            else {
                stack = new ItemStack(Items.PAPER);
                stack.set(DataComponents.ITEM_MODEL, CreateIds.id("ui/threshold_empty"));
            }
            List<Component> tooltip = control == null ? null : tooltip(control);
            setSlot(slot, PolymerGuiControls.decoration(stack, tooltip));
        }
    }

    private ItemStack foreground() {
        ThresholdType type = be.getTypeOfCurrentTarget();
        boolean items = type == ThresholdType.ITEM;
        List<String> strings = new ArrayList<>(Collections.nCopies(3, ""));
        List<Boolean> pixels = LANES.blank();
        strings.set(0, items ? "items" : "misc");
        strings.set(1, be.isInverted() ^ be.isPowered() ? "top" : "bottom");
        strings.set(2, be.isInverted() ? "inverted" : "normal");
        int step = items ? (stacks ? 64 : 1) : type == ThresholdType.FLUID ? BucketFluidInventory.CAPACITY : 1;
        boolean supported = type != ThresholdType.UNSUPPORTED;
        String upper = supported ? items ? Integer.toString(onAbove / step) : format(onAbove / step) : "";
        String lower = supported ? items ? Integer.toString(offBelow / step) : format(offBelow / step) : "";
        write(pixels, 0, 53, Character.toString(GE) + " " + upper);
        write(pixels, 1, 53, Character.toString(LE) + " " + lower);
        if (items) {
            String suffix = PolymerSchematicGuis.lang(
                    stacks
                            ? "create.schedule.condition.threshold.stacks"
                            : "create.schedule.condition.threshold.items");
            write(pixels, 0, 105, suffix);
            write(pixels, 1, 105, suffix);
        }
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.ITEM_MODEL, CreateIds.id("ui/threshold_foreground"));
        stack.set(
                DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(
                        List.of(),
                        List.copyOf(pixels),
                        List.copyOf(strings),
                        List.of(0xFFFFFF, PolymerGuiPixelText.shadow(0xFFFFFF))));
        return stack;
    }

    private static void write(List<Boolean> pixels, int lane, int x, String text) {
        LANES.write(pixels, lane, x, text, textGlyphs, Map.of(GE, 6, LE, 6));
    }

    private List<Component> tooltip(Control control) {
        List<Component> lines = new ArrayList<>();
        ThresholdType type = be.getTypeOfCurrentTarget();
        int step = step();
        switch (control) {
            case UPPER, LOWER -> {
                if (type == ThresholdType.UNSUPPORTED) return null;
                lines.add(PolymerGuiControls.title(PolymerSchematicGuis.lang(
                        control == Control.UPPER
                                ? "create.gui.threshold_switch.upper_threshold"
                                : "create.gui.threshold_switch.lower_threshold")));
                lines.add(PolymerGuiControls.hint("Left-click: +1   Right-click: -1   Shift: ±10"));
            }
            case UNIT -> {
                if (type != ThresholdType.ITEM) return null;
                lines.add(PolymerGuiControls.title(
                        PolymerSchematicGuis.lang("create.schedule.condition.threshold.item_measure")));
                lines.add(PolymerGuiControls.option(
                        PolymerSchematicGuis.lang("create.schedule.condition.threshold.items"), !stacks));
                lines.add(PolymerGuiControls.option(
                        PolymerSchematicGuis.lang("create.schedule.condition.threshold.stacks"), stacks));
                lines.add(PolymerGuiControls.hint("Click to switch"));
            }
            case INVERT ->
                lines.add(PolymerGuiControls.title(
                        PolymerSchematicGuis.lang("create.gui.threshold_switch.invert_signal")));
            case CONFIRM -> lines.add(PolymerGuiControls.title(PolymerSchematicGuis.lang("create.action.confirm")));
            case TARGET -> {
                ItemStack display = be.getDisplayItemForScreen();
                if (display.isEmpty()) {
                    lines.add(PolymerGuiControls.title(
                            PolymerSchematicGuis.lang("create.gui.threshold_switch.not_attached")));
                    return lines;
                }
                lines.add(Component.empty().append(display.getHoverName()).withStyle(s -> s.withItalic(false)));
                if (type == ThresholdType.UNSUPPORTED) {
                    lines.add(Component.literal(PolymerSchematicGuis.lang("create.gui.threshold_switch.incompatible"))
                            .withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false)));
                    return lines;
                }
                lines.add(Component.literal(PolymerSchematicGuis.lang("create.gui.threshold_switch.currently")
                                .replace("%1$s", format(be.currentLevel / step)))
                        .withStyle(s -> s.withColor(ChatFormatting.DARK_AQUA).withItalic(false)));
                String range = be.currentMinLevel / step == 0
                        ? PolymerSchematicGuis.lang("create.gui.threshold_switch.range_max")
                                .replace("%1$s", format(be.currentMaxLevel / step))
                        : PolymerSchematicGuis.lang("create.gui.threshold_switch.range")
                                .replace("%1$s", Integer.toString(be.currentMinLevel / step))
                                .replace("%2$s", format(be.currentMaxLevel / step));
                lines.add(Component.literal(range)
                        .withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false)));
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
        Control control = CONTROLS.get(slot);
        if (control == null) return false;
        boolean supported = be.getTypeOfCurrentTarget() != ThresholdType.UNSUPPORTED;
        int delta = (click.isLeft ? 1 : -1) * (click.shift ? 10 : 1) * step();
        switch (control) {
            case UPPER -> {
                if (supported) changeUpper(onAbove + delta);
            }
            case LOWER -> {
                if (supported) changeLower(offBelow + delta);
            }
            case UNIT -> {
                if (be.getTypeOfCurrentTarget() != ThresholdType.ITEM) return false;
                stacks = !stacks;
                clampInputs();
            }
            case INVERT -> send(!be.isInverted());
            case CONFIRM -> {
                close();
                return false;
            }
            default -> {
                return false;
            }
        }
        refresh();
        return false;
    }

    private void send(boolean invert) {
        AllHandle.onConfigureThresholdSwitch(
                player.connection,
                new ConfigureThresholdSwitchPacket(be.getBlockPos(), offBelow, onAbove, invert, stacks));
    }

    @Override
    public void onTick() {
        if (!stillValid()) {
            close();
            return;
        }
        lifecycle.tick(
                () -> {
                    clampInputs();
                    refresh();
                },
                () -> wrappedMenu.sendAllDataToRemote());
    }

    @Override
    public void onRemoved() {
        lifecycle.close(
                () -> {
                    if (stillValid()) send(be.isInverted());
                },
                () -> PolymerInventorySync.afterMenu(player));
    }

    private static String resolve(Component component) {
        if (component.getContents() instanceof TranslatableContents translatable) {
            String pattern = PolymerSchematicGuis.all().getOrDefault(translatable.getKey(), translatable.getKey());
            Object[] args = translatable.getArgs();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i] instanceof Component c ? resolve(c) : String.valueOf(args[i]);
                pattern = pattern.replace("%" + (i + 1) + "$s", arg).replaceFirst("%s", Matcher.quoteReplacement(arg));
            }
            StringBuilder out = new StringBuilder(pattern);
            for (Component sibling : component.getSiblings()) out.append(resolve(sibling));
            return out.toString();
        }
        StringBuilder out =
                new StringBuilder(component.getContents() instanceof PlainTextContents plain ? plain.text() : "");
        for (Component sibling : component.getSiblings()) out.append(resolve(sibling));
        return out.length() == 0 ? component.getString() : out.toString();
    }

    public static void registerInteraction() {
        AdapterInteractions.useBlock((player, level, hand, hit) -> {
            if (!(player instanceof ServerPlayer owner)
                    || level.isClientSide()
                    || hand != InteractionHand.MAIN_HAND
                    || PolymerNativeClients.isNative(owner)
                    || owner.isSpectator()
                    || !owner.mayBuild()) return InteractionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            ItemStack held = owner.getItemInHand(hand);
            if (!level.getBlockState(pos).is(AllBlocks.THRESHOLD_SWITCH)
                    || held.is(AllItems.WRENCH)
                    || !(level instanceof ServerLevel server)
                    || !owner.mayInteract(server, pos)) return InteractionResult.PASS;
            if (!(level.getBlockEntity(pos) instanceof ThresholdSwitchBlockEntity be)) return InteractionResult.PASS;
            return PolymerThresholdGui.open(owner, be) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
    }

    static void addAssets(ResourcePackBuilder builder) {
        try {
            Map<Integer, BufferedImage> glyphs = PolymerArtKit.asciiGlyphs(builder);
            glyphs.put(GE, symbol(new String[] {"#....", ".##..", "...##", ".##..", "#....", ".....", "#####"}));
            glyphs.put(LE, symbol(new String[] {"....#", "..##.", "##...", "..##.", "....#", ".....", "#####"}));
            BufferedImage atlas = image(builder, "assets/create/textures/gui/threshold_switch.png");
            BufferedImage art = PolymerArtKit.copy(atlas.getSubimage(0, 0, ART_W, ART_H));
            BufferedImage frame = PolymerArtKit.copy(art.getSubimage(12, 79, 18, 18));
            PolymerFrogArt.erase(art, 12, 79, 18, 18, 40, 85);
            art.createGraphics().drawImage(frame, 10, 76, null);
            String title = PolymerSchematicGuis.lang("create.gui.threshold_switch.title");
            PolymerArtKit.rawText(
                    art, glyphs, ART_W / 2 - PolymerGogglesOverlay.width(title) / 2, 4, title, 0x592424, false);
            PolymerFrogArt.panel(builder, "threshold", art, ART_X, ART_Y, ROWS);
            PolymerFrogArt.item(
                    builder,
                    "threshold_invert",
                    PolymerFrogArt.button(builder, 0, FLIP_ICON[0], FLIP_ICON[1]),
                    INVERT,
                    ART_W - 62 + ART_X,
                    ART_H - 24 + ART_Y);
            PolymerFrogArt.item(
                    builder,
                    "threshold_confirm",
                    PolymerFrogArt.button(builder, 0, 0, 1),
                    CONFIRM,
                    ART_W - 33 + ART_X,
                    ART_H - 24 + ART_Y);

            JsonArray models = new JsonArray();
            ASSETS.png(builder, "inputs_items", atlas.getSubimage(0, 105, 115, 22));
            ASSETS.png(builder, "inputs_misc", atlas.getSubimage(0, 129, 115, 22));
            ASSETS.quad(builder, "inputs_items", 115, 22, 4);
            ASSETS.quad(builder, "inputs_misc", 115, 22, 4);
            for (int row = 0; row < 2; row++) {
                JsonArray cases = new JsonArray();
                cases.add(caseOf("items", ASSETS.model("inputs_items")));
                cases.add(caseOf("misc", ASSETS.model("inputs_misc")));
                JsonObject select = select(0, cases);
                select.add("transformation", ASSETS.centred(ART_X + 44 + 57.5f, ART_Y + 21 + 24 * row + 11, 0.01f, 4));
                models.add(select);
            }
            ASSETS.png(builder, "state", atlas.getSubimage(0, 153, 24, 24));
            ASSETS.quad(builder, "state", 24, 24, 4);
            JsonArray states = new JsonArray();
            JsonObject top = ASSETS.model("state"), bottom = ASSETS.model("state");
            top.add("transformation", ASSETS.centred(ART_X + 20 + 12, ART_Y + 20 + 12, 0.01f, 4));
            bottom.add("transformation", ASSETS.centred(ART_X + 20 + 12, ART_Y + 44 + 12, 0.01f, 4));
            states.add(caseOf("top", top));
            states.add(caseOf("bottom", bottom));
            models.add(select(1, states));
            JsonArray torches = new JsonArray();
            torches.add(caseOf(
                    "normal",
                    composite(
                            torch(builder, "torch_on_top", "minecraft:block/redstone_torch", 19),
                            torch(builder, "torch_off_bottom", "minecraft:block/redstone_torch_off", 43))));
            torches.add(caseOf(
                    "inverted",
                    composite(
                            torch(builder, "torch_on_bottom", "minecraft:block/redstone_torch", 43),
                            torch(builder, "torch_off_top", "minecraft:block/redstone_torch_off", 19))));
            models.add(select(2, torches));
            textGlyphs = Map.copyOf(glyphs);
            PolymerGuiPixelText.addAssets(new PackAssetWriter(builder));
            LANES.models(models, ART_X, ART_Y, 0.02f, ITEM_CX, ITEM_CY);
            JsonObject composite = new JsonObject();
            composite.addProperty("type", "minecraft:composite");
            composite.add("models", models);
            JsonObject item = new JsonObject();
            item.addProperty("oversized_in_gui", true);
            item.add("model", composite);
            builder.addStringData("assets/create/items/ui/threshold_foreground.json", item.toString());
            builder.addStringData(
                    "assets/create/items/ui/threshold_empty.json", "{\"model\":{\"type\":\"minecraft:empty\"}}");
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Cannot build Threshold Switch UI source art", exception);
        }
    }

    private static final int[] FLIP_ICON = {8, 4};

    private static JsonObject torch(ResourcePackBuilder builder, String name, String parent, int y) throws IOException {
        Matrix4f matrix = new Matrix4f()
                .translation((ART_X + 22 + 10 - ITEM_CX) / 16f, (ITEM_CY - (ART_Y + y + 10)) / 16f, 1)
                .scale(1.25f)
                .rotateX((float) Math.toRadians(22.5))
                .rotateY((float) Math.toRadians(45));
        return PolymerGuiPreviews.raw(builder, "threshold_" + name, parent, matrix);
    }

    private static JsonObject composite(JsonObject... parts) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "minecraft:composite");
        JsonArray list = new JsonArray();
        for (JsonObject part : parts) list.add(part);
        o.add("models", list);
        return o;
    }

    private static BufferedImage symbol(String[] rows) {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < rows.length; y++)
            for (int x = 0; x < rows[y].length(); x++) if (rows[y].charAt(x) == '#') image.setRGB(x, y, -1);
        return image;
    }
}
