package com.zurrtum.create.infrastructure.polymer.gui;

import com.zurrtum.create.content.equipment.blueprint.BlueprintEntity;
import com.zurrtum.create.content.equipment.blueprint.BlueprintMenu;
import com.zurrtum.create.infrastructure.polymer.PolymerFrogArt;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.containerwrappers.SlotBasedWrapperMenu;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

public final class PolymerBlueprintGui extends SimpleGui {
    private static final int SOURCE_WINDOW = 120;

    private static final int[] GRID = {1, 2, 3, 10, 11, 12, 19, 20, 21};
    private static final int SIZE = 54, FOREGROUND = 0, RESULT = 15, SECONDARY = 25, CLEAR = 42, CONFIRM = 44;
    private final BlueprintEntity entity;
    private final BlueprintMenu source;
    private final PolymerGuiLifecycle lifecycle = new PolymerGuiLifecycle();

    private PolymerBlueprintGui(ServerPlayer player, BlueprintEntity entity, int section) {
        super(MenuType.GENERIC_9x6, player, false);
        this.entity = entity;
        source = new BlueprintMenu(SOURCE_WINDOW, player.getInventory(), entity.getSection(section));
        PolymerGuiControls.configure(this, PolymerFrogArt.title("blueprint"), false);
        refresh();
    }

    public static boolean isSourceWindowPacket(Packet<?> packet) {
        return packet instanceof ClientboundContainerSetSlotPacket slot && slot.getContainerId() == SOURCE_WINDOW;
    }

    public static boolean open(ServerPlayer player, BlueprintEntity entity, int section) {
        if (PolymerNativeClients.isNative(player)
                || player.isSpectator()
                || !player.mayBuild()
                || !entity.isAlive()
                || entity.level() != player.level()
                || section < 0
                || section >= entity.size * entity.size
                || !entity.canPlayerUse(player)) return false;
        return new PolymerBlueprintGui(player, entity, section).open();
    }

    private int ghostIndex(int slot) {
        for (int i = 0; i < GRID.length; i++) if (GRID[i] == slot) return i;
        return slot == RESULT ? 9 : slot == SECONDARY ? 10 : -1;
    }

    private boolean valid() {
        return !player.isSpectator() && !entity.isRemoved() && source.stillValid(player);
    }

    private void refresh() {
        for (int slot = 0; slot < SIZE; slot++) {
            int index = ghostIndex(slot);
            if (index >= 0) {
                ItemStack item = source.ghostInventory.getItem(index);
                if (item.isEmpty()) clearSlot(slot);
                else setSlot(slot, PolymerGuiControls.builder(item.copy()).build());
            } else if (slot == FOREGROUND) {
                setSlot(
                        slot,
                        PolymerGuiControls.builder(PolymerArtKit.stack("blueprint_foreground", List.of()))
                                .hideTooltip()
                                .build());
            } else if (slot == CLEAR || slot == CONFIRM) {
                setSlot(
                        slot,
                        PolymerGuiControls.builder(PolymerArtKit.stack("blueprint_empty", null))
                                .setName(Component.literal(slot == CLEAR ? "Clear" : "Confirm"))
                                .build());
            } else
                setSlot(
                        slot,
                        PolymerGuiControls.builder(PolymerArtKit.stack("blueprint_empty", null))
                                .hideTooltip()
                                .build());
        }
        lifecycle.requestSync();
    }

    @Override
    public boolean onAnyClick(int slot, ClickType click, ContainerInput action) {
        lifecycle.requestSync();
        if (!valid()) {
            close();
            return false;
        }
        if (slot < 0) return true;
        if (slot >= SIZE) {
            if (action == ContainerInput.QUICK_MOVE) {
                quickMove(slot);
                return false;
            }
            return action != ContainerInput.PICKUP_ALL;
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
            if (slot == CONFIRM) {
                close();
                return false;
            }
            if (slot == CLEAR) source.clearContents();
            refresh();
        }
        return false;
    }

    @Override
    public ItemStack quickMove(int slot) {
        if (!valid()) return ItemStack.EMPTY;
        int index = ghostIndex(slot);
        if (index >= 0) source.quickMoveStack(player, index + 36);
        else if (slot >= SIZE && slot < SIZE + 36) source.quickMoveStack(player, slot - SIZE);
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
                    if (entity.isRemoved()) return;
                    source.setCarried(ItemStack.EMPTY);
                    source.removed(player);
                },
                () -> {});
    }

    public static void addAssets(ResourcePackBuilder builder) {
        try {
            var glyphs = PolymerArtKit.asciiGlyphs(builder);
            BufferedImage atlas = PolymerArtKit.image(builder, "assets/create/textures/gui/curiosities_2.png");
            BufferedImage art = atlas.getSubimage(0, 109, 179, 109);
            BufferedImage canvas = new BufferedImage(179, 112, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 112; y++)
                for (int x = 0; x < 179; x++) {
                    int wy = y - 3;
                    int sourceY = wy <= 74 ? wy + 3 : wy <= 77 ? 76 + (wy & 1) : Math.min(108, wy);
                    canvas.setRGB(x, y, art.getRGB(x, sourceY));
                }
            BufferedImage result = art.getSubimage(118, 35, 26, 26), secondary = art.getSubimage(134, 57, 18, 17);
            for (int y = 34; y < 76; y++)
                for (int x = 116; x < 154; x++) canvas.setRGB(x, y, art.getRGB(110 + ((x + y) & 1), 48));
            paste(canvas, result, 118 - 3 - 3 + 3, 35 - 3 + 3);
            paste(canvas, secondary, 134 + 3 - 3 + 3, 57 + 1 - 3 + 3);
            for (int y = 84; y < 110; y++) for (int x = 3; x < 174; x++) canvas.setRGB(x, y, art.getRGB(60, 95));
            for (int y = 84; y < 110; y++) {
                canvas.setRGB(152, y, art.getRGB(140, 95));
                canvas.setRGB(153, y, art.getRGB(141, 95));
            }
            String title = PolymerSchematicGuis.lang("item.create.crafting_blueprint");
            PolymerArtKit.text(canvas, glyphs, 15, 4, title, 0xFFFFFF, false);
            canvas = widen(canvas, 162, 160, WIDEN);
            PolymerFrogArt.panel(builder, "blueprint", canvas, -3, -3, 6);
            var kit = new PolymerArtKit(builder, "blueprint", 16, 26);
            BufferedImage icons = PolymerArtKit.image(builder, "assets/create/textures/gui/icons.png");
            BufferedImage widgets = PolymerArtKit.image(builder, "assets/create/textures/gui/widgets.png");
            int[][] buttons = {{CLEAR, 1, 0}, {CONFIRM, 0, 1}};
            for (int[] button : buttons) {
                BufferedImage image = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics();
                g.drawImage(widgets.getSubimage(0, 0, 18, 18), 0, 0, null);
                g.drawImage(icons.getSubimage(button[1] * 16, button[2] * 16, 16, 16), 1, 1, null);
                g.dispose();
                kit.panel("button_" + button[0], image, 8 + button[0] % 9 * 18, 18 + button[0] / 9 * 18, .02f);
            }
            kit.finish("blueprint_foreground");
            PolymerArtKit.empty(builder, "blueprint_empty");
        } catch (IOException error) {
            throw new IllegalStateException("Cannot build crafting blueprint screen art", error);
        }
    }

    private static final int WIDEN = 8;

    static BufferedImage widen(BufferedImage image, int at, int pair, int count) {
        BufferedImage out = new BufferedImage(image.getWidth() + count, image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < out.getWidth(); x++) {
                int from = x < at ? x : x < at + count ? pair + (x - at) % 2 : x - count;
                out.setRGB(x, y, image.getRGB(from, y));
            }
        return out;
    }

    private static void paste(BufferedImage canvas, BufferedImage piece, int x, int y) {
        for (int py = 0; py < piece.getHeight(); py++)
            for (int px = 0; px < piece.getWidth(); px++) {
                int argb = piece.getRGB(px, py);
                if ((argb >>> 24) != 0
                        && x + px >= 0
                        && x + px < canvas.getWidth()
                        && y + py >= 0
                        && y + py < canvas.getHeight()) canvas.setRGB(x + px, y + py, argb);
            }
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
