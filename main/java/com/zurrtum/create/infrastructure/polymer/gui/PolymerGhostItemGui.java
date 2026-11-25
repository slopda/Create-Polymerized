package com.zurrtum.create.infrastructure.polymer.gui;

import com.zurrtum.create.content.logistics.filter.FilterMenu;
import com.zurrtum.create.content.logistics.filter.ListFilterItem;
import com.zurrtum.create.content.redstone.link.controller.LinkedControllerMenu;
import com.zurrtum.create.foundation.gui.menu.HeldItemGhostItemMenu;
import com.zurrtum.create.infrastructure.polymer.PolymerFrogArt;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.containerwrappers.SlotBasedWrapperMenu;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.awt.Graphics2D;
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

public final class PolymerGhostItemGui extends SimpleGui {
    private static final int FILTER_DENY = 27,
            FILTER_ALLOW = 28,
            FILTER_RESPECT = 29,
            FILTER_IGNORE = 30,
            FILTER_RESET = 34,
            FILTER_CONFIRM = 35,
            FILTER_PREVIEW_HOST = 20;
    private static final int CONTROLLER_RESET = 34, CONTROLLER_CONFIRM = 35, CONTROLLER_HOST = 8;
    private static final int[] CONTROLLER_COLUMNS = {0, 1, 2, 3, 5, 6};

    private final HeldItemGhostItemMenu source;
    private final ItemStack target;
    private final ItemStack initial;
    private final String skin;
    private final boolean filter;
    private final int containerSize;
    private final PolymerGuiLifecycle lifecycle = new PolymerGuiLifecycle();

    private PolymerGhostItemGui(ServerPlayer player, boolean filter) {
        super(MenuType.GENERIC_9x5, player, false);
        this.filter = filter;
        this.containerSize = 45;
        target = player.getMainHandItem();
        initial = target.copy();
        skin = filter ? "filter" : "linked_controller";
        source = filter
                ? new FilterMenu(0, player.getInventory(), target)
                : new LinkedControllerMenu(0, player.getInventory(), target);
        PolymerGuiControls.configure(
                this,
                filter ? PolymerGuiTextures.title(skin, target.getHoverName()) : PolymerFrogArt.title("controller"),
                false);
        refresh();
    }

    public static boolean openFilter(ServerPlayer player) {
        if (!(player.getMainHandItem().getItem() instanceof ListFilterItem)
                || !player.mayBuild()
                || player.isSpectator()) return false;
        return new PolymerGhostItemGui(player, true).open();
    }

    public static void openController(ServerPlayer player) {
        new PolymerGhostItemGui(player, false).open();
    }

    private int ghostIndex(int slot) {
        if (slot < 0 || slot >= 18) return -1;
        if (source instanceof FilterMenu) return slot;
        int column = slot % 9;
        for (int pair = 0; pair < CONTROLLER_COLUMNS.length; pair++)
            if (CONTROLLER_COLUMNS[pair] == column) return pair * 2 + slot / 9;
        return -1;
    }

    private int playerStart() {
        return containerSize;
    }

    private int selectedHotbarSlot() {
        return containerSize + 27 + player.getInventory().getSelectedSlot();
    }

    private boolean valid() {
        return !player.isSpectator()
                && player.mayBuild()
                && source.stillValid(player)
                && ItemStack.isSameItemSameComponents(target, initial);
    }

    private void refresh() {
        for (int slot = 0; slot < containerSize; slot++) {
            int index = ghostIndex(slot);
            if (index >= 0) {
                ItemStack item = source.ghostInventory.getItem(index);
                if (item.isEmpty()) clearSlot(slot);
                else if (filter)
                    setSlot(
                            slot,
                            PolymerGuiControls.builder(item.copyWithCount(1)).build());
                else setSlot(slot, PolymerGuiControls.decoration(item.copyWithCount(1), frequencyTip(index, item)));
                if (!filter && item.isEmpty())
                    setSlot(
                            slot,
                            PolymerGuiControls.decoration(
                                    PolymerArtKit.stack("controller_empty", null), frequencyTip(index, item)));
            } else if (filter) {
                if (slot == FILTER_PREVIEW_HOST)
                    setSlot(
                            slot,
                            PolymerGuiControls.builder(Items.PAPER)
                                    .model(CreateIds.id("ui/gui_filter_patch_20"))
                                    .hideTooltip()
                                    .build());
                else clearSlot(slot);
            } else if (slot == CONTROLLER_HOST)
                setSlot(slot, PolymerGuiControls.decoration(PolymerArtKit.stack("controller_host", null), null));
            else clearSlot(slot);
        }
        if (source instanceof FilterMenu menu) {
            filterButton(
                    FILTER_DENY, "blacklist", Component.translatable("create.gui.filter.deny_list"), menu.blacklist);
            filterButton(
                    FILTER_ALLOW, "whitelist", Component.translatable("create.gui.filter.allow_list"), !menu.blacklist);
            filterButton(
                    FILTER_RESPECT,
                    "respect_nbt",
                    Component.translatable("create.gui.filter.respect_data"),
                    menu.respectNBT);
            filterButton(
                    FILTER_IGNORE,
                    "ignore_nbt",
                    Component.translatable("create.gui.filter.ignore_data"),
                    !menu.respectNBT);
            filterButton(FILTER_RESET, "trash", Component.translatable("create.action.reset"), false);
            filterButton(FILTER_CONFIRM, "confirm", Component.translatable("create.action.confirm"), false);
        } else {
            setSlot(
                    CONTROLLER_RESET,
                    PolymerGuiControls.decoration(
                            PolymerGuiTextures.icon("controller_reset"),
                            List.of(Component.translatable("create.action.reset"))));
            setSlot(
                    CONTROLLER_CONFIRM,
                    PolymerGuiControls.decoration(
                            PolymerGuiTextures.icon("controller_confirm"),
                            List.of(Component.translatable("create.action.confirm"))));
        }
        lifecycle.requestSync();
    }

    private void filterButton(int slot, String name, Component label, boolean active) {
        setSlot(
                slot,
                PolymerGuiControls.builder(Items.PAPER)
                        .model(CreateIds.id("ui/gui_filter_button_" + name + (active ? "_on" : "")))
                        .setName(label)
                        .build());
    }

    private static List<Component> frequencyTip(int index, ItemStack item) {
        List<Component> lines = new ArrayList<>();
        if (!item.isEmpty()) lines.add(item.getHoverName());
        lines.add(Component.translatable(
                        "create.linked_controller.frequency_slot_" + (index % 2 + 1),
                        Component.translatable(CONTROLS[index / 2]))
                .withStyle(ChatFormatting.GOLD));
        return lines;
    }

    private static final String[] CONTROLS = {
        "key.forward", "key.back", "key.left", "key.right", "key.jump", "key.sneak"
    };

    static void addAssets(ResourcePackBuilder builder) {
        try {
            var glyphs = PolymerArtKit.asciiGlyphs(builder);
            BufferedImage sheet = PolymerArtKit.image(builder, "assets/create/textures/gui/curiosities_2.png");
            BufferedImage art = PolymerArtKit.copy(sheet.getSubimage(0, 0, 179, 109));
            int[] sourceX = {12, 36, 60, 84, 119, 143};
            int ox = -4, oy = -16;
            for (int y = 17; y < 76; y++) for (int x = 6; x < 166; x++) art.setRGB(x, y, sheet.getRGB(10, y));
            Graphics2D g = art.createGraphics();
            for (int pair = 0; pair < 6; pair++) {
                BufferedImage column = sheet.getSubimage(sourceX[pair] - 1, 18, 18, 54);
                g.drawImage(column, 8 + CONTROLLER_COLUMNS[pair] * 18 - ox - 1, 18, null);
            }
            g.dispose();
            PolymerFrogArt.erase(art, 126, 81, 42, 24, 60, 92);
            String title = PolymerSchematicGuis.lang("item.create.linked_controller");
            PolymerArtKit.text(art, glyphs, 15, 4, title, 0x592424, false);
            PolymerFrogArt.panel(builder, "controller", art, ox, oy, 5);
            int y = PolymerFrogArt.slotY(CONTROLLER_RESET) - 1;
            PolymerFrogArt.item(
                    builder,
                    "controller_reset",
                    PolymerFrogArt.button(builder, 0, 1, 0),
                    CONTROLLER_RESET,
                    PolymerFrogArt.slotX(CONTROLLER_RESET) - 1,
                    y);
            PolymerFrogArt.item(
                    builder,
                    "controller_confirm",
                    PolymerFrogArt.button(builder, 0, 0, 1),
                    CONTROLLER_CONFIRM,
                    PolymerFrogArt.slotX(CONTROLLER_CONFIRM) - 1,
                    y);
            PolymerArtKit.empty(builder, "controller_host");
            PolymerArtKit.empty(builder, "controller_empty");
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot build Linked Controller screen art", error);
        }
    }

    @Override
    public boolean onAnyClick(int slot, ClickType click, ContainerInput action) {
        lifecycle.requestSync();
        if (!valid()) {
            close();
            return false;
        }
        if (slot < 0) return true;
        if (slot >= playerStart()) {
            if (slot == selectedHotbarSlot() || action == ContainerInput.SWAP || action == ContainerInput.PICKUP_ALL)
                return false;
            if (action == ContainerInput.QUICK_MOVE) {
                quickMove(slot);
                return false;
            }
            return true;
        }
        int index = ghostIndex(slot);
        if (index >= 0 && (action == ContainerInput.PICKUP || action == ContainerInput.CLONE)) {
            source.setCarried(player.containerMenu.getCarried());
            source.clicked(36 + index, click.isRight ? 1 : 0, action, player);
            player.containerMenu.setCarried(source.getCarried());
            source.setCarried(ItemStack.EMPTY);
            refresh();
        } else if (action == ContainerInput.QUICK_MOVE) {
            quickMove(slot);
        } else if (action == ContainerInput.PICKUP && click.isLeft) {
            if (slot == (filter ? FILTER_CONFIRM : CONTROLLER_CONFIRM)) {
                close();
                return false;
            }
            if (slot == (filter ? FILTER_RESET : CONTROLLER_RESET)) source.clearContents();
            if (source instanceof FilterMenu menu)
                switch (slot) {
                    case FILTER_ALLOW -> menu.blacklist = false;
                    case FILTER_DENY -> menu.blacklist = true;
                    case FILTER_RESPECT -> menu.respectNBT = true;
                    case FILTER_IGNORE -> menu.respectNBT = false;
                    default -> {}
                }
            refresh();
        }
        return false;
    }

    @Override
    public ItemStack quickMove(int slot) {
        if (!valid()) return ItemStack.EMPTY;
        int index = ghostIndex(slot);
        if (index >= 0) source.quickMoveStack(player, index + 36);
        else if (slot >= playerStart() && slot < playerStart() + 36 && slot != selectedHotbarSlot())
            source.quickMoveStack(player, slot - playerStart());
        refresh();
        return ItemStack.EMPTY;
    }

    @Override
    public void onTick() {
        if (!valid()) {
            close();
            return;
        }
        if (player.containerMenu instanceof SlotBasedWrapperMenu menu && menu.getBackingGui() == this)
            lifecycle.tick(() -> {}, menu::sendAllDataToRemote);
    }

    private void saveOnClose() {
        lifecycle.close(
                () -> {
                    if (!valid()) return;
                    source.setCarried(ItemStack.EMPTY);
                    source.removed(player);
                },
                () -> {});
    }

    @Override
    public void onManualClose() {
        saveOnClose();
    }

    @Override
    public void onPlayerClose(boolean close) {
        saveOnClose();
    }

    @Override
    public void onRemoved() {
        saveOnClose();
        super.onRemoved();
    }
}
