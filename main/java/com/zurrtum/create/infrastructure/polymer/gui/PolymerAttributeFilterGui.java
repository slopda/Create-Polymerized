package com.zurrtum.create.infrastructure.polymer.gui;

import com.zurrtum.create.content.logistics.filter.AttributeFilterItem;
import com.zurrtum.create.content.logistics.filter.AttributeFilterMenu;
import com.zurrtum.create.content.logistics.item.filter.attribute.ItemAttribute;
import com.zurrtum.create.infrastructure.component.AttributeFilterWhitelistMode;
import com.zurrtum.create.infrastructure.component.ItemAttributeEntry;
import com.zurrtum.create.infrastructure.polymer.PolymerFrogArt;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class PolymerAttributeFilterGui extends SimpleGui {
    private static final String PREFIX = "create.gui.attribute_filter.";
    private static final int OX = -8, OY = -8, ROWS = 4;
    private static final int REFERENCE = 0,
            HOST = 2,
            ADD = 7,
            ADD_INVERTED = 8,
            TAGS = 18,
            ANY = 19,
            ALL = 20,
            DENY = 21,
            RESET = 25,
            CONFIRM = 26;
    private static final int LANE_X = 43 + OX, LANE_Y = 31 + OY, LANE_W = 94;

    private final AttributeFilterMenu source;
    private final ItemStack target, initial;
    private final PolymerGuiLifecycle lifecycle = new PolymerGuiLifecycle();
    private ItemStack scanned = ItemStack.EMPTY;
    private List<ItemAttribute> options = List.of();
    private int selected;

    private PolymerAttributeFilterGui(ServerPlayer player) {
        super(MenuType.GENERIC_9x4, player, false);
        target = player.getMainHandItem();
        initial = target.copy();
        source = new AttributeFilterMenu(0, player.getInventory(), target);
        PolymerGuiControls.configure(this, PolymerFrogArt.title("attribute_filter"), false);
        scan();
        refresh();
    }

    static boolean open(ServerPlayer player) {
        if (!(player.getMainHandItem().getItem() instanceof AttributeFilterItem)
                || !player.mayBuild()
                || player.isSpectator()) return false;
        return new PolymerAttributeFilterGui(player).open();
    }

    private void scan() {
        ItemStack reference = source.ghostInventory.getItem(0);
        if (ItemStack.matches(reference, scanned)) return;
        scanned = reference.copy();
        options = reference.isEmpty()
                ? List.of()
                : List.copyOf(ItemAttribute.getAllAttributes(reference, player.level()));
        selected = 0;
    }

    private boolean alreadySelected(ItemAttribute attribute) {
        var registries = player.registryAccess();
        var wanted = ItemAttribute.saveStatic(attribute, registries);
        for (ItemAttributeEntry entry : source.selectedAttributes)
            if (ItemAttribute.saveStatic(entry.attribute(), registries).equals(wanted)) return true;
        return false;
    }

    private void refresh() {
        scan();
        String label = options.isEmpty()
                ? PolymerSchematicGuis.lang(PREFIX + "add_reference_item")
                : PolymerScheduleScreen.resolve(options.get(selected).format(false));
        List<Boolean> pixels = PolymerArtKit.flags(LANE_W);
        PolymerArtKit.write(pixels, 0, LANE_X, LANE_W, LANE_X, label);
        List<Component> selectorTip = selectorTip();
        for (int slot = 1; slot <= 6; slot++)
            setSlot(
                    slot,
                    PolymerGuiControls.decoration(
                            slot == HOST
                                    ? PolymerArtKit.stack("attribute_filter_foreground", List.of(), pixels)
                                    : PolymerArtKit.stack("attribute_filter_empty", null),
                            selectorTip));
        ItemStack reference = source.ghostInventory.getItem(0);
        if (reference.isEmpty()) clearSlot(REFERENCE);
        else
            setSlot(
                    REFERENCE,
                    PolymerGuiControls.builder(reference.copyWithCount(1)).build());
        boolean addable = !options.isEmpty() && !alreadySelected(options.get(selected));
        setSlot(
                ADD,
                PolymerGuiControls.decoration(
                        PolymerGuiTextures.icon(addable ? "attribute_add" : "attribute_add_off"),
                        List.of(Component.translatable(PREFIX + "add_attribute"))));
        setSlot(
                ADD_INVERTED,
                PolymerGuiControls.decoration(
                        PolymerGuiTextures.icon(addable ? "attribute_add_not" : "attribute_add_not_off"),
                        List.of(Component.translatable(PREFIX + "add_inverted_attribute"))));
        setSlot(TAGS, PolymerGuiControls.decoration(tagsStack(), tagsTip()));
        mode(ANY, "any", AttributeFilterWhitelistMode.WHITELIST_DISJ, "allow_list_disjunctive");
        mode(ALL, "all", AttributeFilterWhitelistMode.WHITELIST_CONJ, "allow_list_conjunctive");
        mode(DENY, "deny", AttributeFilterWhitelistMode.BLACKLIST, "deny_list");
        setSlot(
                RESET,
                PolymerGuiControls.decoration(
                        PolymerGuiTextures.icon("attribute_reset"),
                        List.of(Component.translatable("create.action.reset"))));
        setSlot(
                CONFIRM,
                PolymerGuiControls.decoration(
                        PolymerGuiTextures.icon("attribute_confirm"),
                        List.of(Component.translatable("create.action.confirm"))));
        lifecycle.requestSync();
    }

    private ItemStack tagsStack() {
        ItemStack stack = new ItemStack(Items.NAME_TAG);
        stack.setCount(Math.clamp(source.selectedAttributes.size(), 1, 64));
        return stack;
    }

    private List<Component> tagsTip() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(PREFIX
                        + (source.selectedAttributes.isEmpty() ? "no_selected_attributes" : "selected_attributes"))
                .withStyle(ChatFormatting.YELLOW));
        for (ItemAttributeEntry entry : source.selectedAttributes)
            lines.add(Component.literal("- ")
                    .append(entry.attribute().format(entry.inverted()))
                    .withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private List<Component> selectorTip() {
        List<Component> lines = new ArrayList<>();
        if (options.isEmpty()) {
            lines.add(Component.translatable(PREFIX + "add_reference_item"));
            return lines;
        }
        lines.add(Component.literal(scanned.getHoverName().getString() + "...").withStyle(ChatFormatting.WHITE));
        int from = Math.max(0, Math.min(selected - 4, options.size() - 9));
        for (int i = from; i < Math.min(options.size(), from + 9); i++)
            lines.add(Component.literal(i == selected ? "-> " : "   ")
                    .append(options.get(i).format(false))
                    .withStyle(i == selected ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        lines.add(Component.literal("Left-click: next   Right-click: previous").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private void mode(int slot, String name, AttributeFilterWhitelistMode mode, String key) {
        setSlot(
                slot,
                PolymerGuiControls.decoration(
                        PolymerGuiTextures.icon("attribute_" + name + (source.whitelistMode == mode ? "_on" : "")),
                        List.of(
                                Component.translatable(PREFIX + key),
                                Component.translatable(PREFIX + key + ".description")
                                        .withStyle(ChatFormatting.GRAY))));
    }

    @Override
    public boolean onAnyClick(int slot, ClickType click, ContainerInput action) {
        lifecycle.requestSync();
        if (!stillValid()) {
            close();
            return false;
        }
        if (slot < 0) return true;
        if (slot >= getSize()) {
            if (slot == getSize() + 27 + player.getInventory().getSelectedSlot()
                    || action == ContainerInput.SWAP
                    || action == ContainerInput.PICKUP_ALL) return false;
            if (action == ContainerInput.QUICK_MOVE) {
                source.quickMoveStack(player, slot - getSize());
                refresh();
                return false;
            }
            return true;
        }
        if (slot == REFERENCE && (action == ContainerInput.PICKUP || action == ContainerInput.CLONE)) {
            source.setCarried(player.containerMenu.getCarried());
            source.clicked(36, click.isRight ? 1 : 0, action, player);
            player.containerMenu.setCarried(source.getCarried());
            source.setCarried(ItemStack.EMPTY);
            refresh();
            return false;
        }
        if (!PolymerGuiControls.primaryClick(click, action)) return false;
        if (slot >= 1 && slot <= 6) {
            if (!options.isEmpty()) selected = Math.floorMod(selected + (click.isLeft ? 1 : -1), options.size());
        } else if (!click.isLeft) return false;
        else
            switch (slot) {
                case ADD, ADD_INVERTED -> {
                    if (!options.isEmpty() && !alreadySelected(options.get(selected)))
                        source.appendSelectedAttribute(options.get(selected), slot == ADD_INVERTED);
                }
                case ANY -> source.whitelistMode = AttributeFilterWhitelistMode.WHITELIST_DISJ;
                case ALL -> source.whitelistMode = AttributeFilterWhitelistMode.WHITELIST_CONJ;
                case DENY -> source.whitelistMode = AttributeFilterWhitelistMode.BLACKLIST;
                case RESET -> source.clearContents();
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

    @Override
    public boolean stillValid() {
        return !player.isSpectator()
                && player.mayBuild()
                && source.stillValid(player)
                && ItemStack.isSameItemSameComponents(target, initial);
    }

    @Override
    public void onTick() {
        lifecycle.tickWhileValid(stillValid(), this::close, wrappedMenu);
    }

    private void save() {
        lifecycle.closeCarried(this::stillValid, source, player);
    }

    @Override
    public void onManualClose() {
        save();
    }

    @Override
    public void onPlayerClose(boolean close) {
        save();
    }

    @Override
    public void onRemoved() {
        save();
        super.onRemoved();
    }

    static void addAssets(ResourcePackBuilder builder) {
        try {
            var glyphs = PolymerArtKit.asciiGlyphs(builder);
            BufferedImage art =
                    PolymerArtKit.copy(PolymerArtKit.image(builder, "assets/create/textures/gui/filters.png")
                            .getSubimage(0, 99, 241, 85));
            int body = 190;
            for (int y = 18; y < 50; y++)
                for (int x = 140; x < 226; x++) art.setRGB(x, y, art.getRGB(x % 2 == 0 ? 6 : 7, y));
            for (int y = 24; y < 46; y++) art.setRGB(139, y, art.getRGB(176, y));
            PolymerFrogArt.erase(art, body - 24, 55, 64, 27, 120, 66);
            String title = PolymerSchematicGuis.lang("item.create.attribute_filter");
            PolymerArtKit.text(art, glyphs, (241 - 8) / 2 - PolymerArtKit.width(title) / 2, 4, title, 0x592424, false);
            PolymerFrogArt.panel(builder, "attribute_filter", art, OX, OY, ROWS);
            int rowTop = PolymerFrogArt.slotY(TAGS) - 1;
            PolymerFrogArt.item(
                    builder,
                    "attribute_add",
                    PolymerFrogArt.button(builder, 0, 0, 0),
                    ADD,
                    PolymerFrogArt.slotX(ADD) - 1,
                    18);
            PolymerFrogArt.item(
                    builder,
                    "attribute_add_off",
                    PolymerFrogArt.button(builder, 90, 0, 0),
                    ADD,
                    PolymerFrogArt.slotX(ADD) - 1,
                    18);
            PolymerFrogArt.item(
                    builder,
                    "attribute_add_not",
                    PolymerFrogArt.button(builder, 0, 7, 4),
                    ADD_INVERTED,
                    PolymerFrogArt.slotX(ADD_INVERTED) - 1,
                    18);
            PolymerFrogArt.item(
                    builder,
                    "attribute_add_not_off",
                    PolymerFrogArt.button(builder, 90, 7, 4),
                    ADD_INVERTED,
                    PolymerFrogArt.slotX(ADD_INVERTED) - 1,
                    18);
            int[][] modes = {{ANY, 10, 38}, {ALL, 11, 56}, {DENY, 12, 74}};
            String[] names = {"any", "all", "deny"};
            for (int i = 0; i < 3; i++) {
                PolymerFrogArt.item(
                        builder,
                        "attribute_" + names[i],
                        PolymerFrogArt.button(builder, 0, modes[i][1], 0),
                        modes[i][0],
                        modes[i][2] + OX,
                        61 + OY);
                PolymerFrogArt.item(
                        builder,
                        "attribute_" + names[i] + "_on",
                        PolymerFrogArt.button(builder, 72, modes[i][1], 0),
                        modes[i][0],
                        modes[i][2] + OX,
                        61 + OY);
            }
            PolymerFrogArt.item(
                    builder,
                    "attribute_reset",
                    PolymerFrogArt.button(builder, 0, 1, 0),
                    RESET,
                    PolymerFrogArt.slotX(RESET) - 1,
                    rowTop);
            PolymerFrogArt.item(
                    builder,
                    "attribute_confirm",
                    PolymerFrogArt.button(builder, 0, 0, 1),
                    CONFIRM,
                    PolymerFrogArt.slotX(CONFIRM) - 1,
                    rowTop);
            var kit = new PolymerArtKit(builder, "attribute_filter", 52, 26);
            kit.compactLane(0, LANE_X, LANE_Y, LANE_W, glyphs, 0xF3EBDE, true, .02f);
            kit.finish("attribute_filter_foreground");
            PolymerArtKit.empty(builder, "attribute_filter_empty");
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot build Attribute Filter screen art", error);
        }
    }
}
