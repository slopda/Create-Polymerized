package com.zurrtum.create.infrastructure.polymer.gui;

import com.zurrtum.create.content.logistics.box.PackageStyles;
import com.zurrtum.create.content.logistics.filter.PackageFilterItem;
import com.zurrtum.create.content.logistics.filter.PackageFilterMenu;
import com.zurrtum.create.infrastructure.polymer.PolymerFrogArt;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

final class PolymerPackageFilterGui extends SimpleGui {
    private static final int OX = -8, OY = -5, ROWS = 4, BOX = 0, HOST = 2, RESET = 25, CONFIRM = 26;
    private static final int LANE_X = 44 + OX, LANE_Y = 28 + OY, LANE_W = 129, MAX_ADDRESS = 128;

    private final PackageFilterMenu source;
    private final ItemStack target, initial;
    private final PolymerGuiLifecycle lifecycle = new PolymerGuiLifecycle();

    private PolymerPackageFilterGui(ServerPlayer player) {
        super(MenuType.GENERIC_9x4, player, false);
        target = player.getMainHandItem();
        initial = target.copy();
        source = new PackageFilterMenu(0, player.getInventory(), target);
        PolymerGuiControls.configure(this, PolymerFrogArt.title("package_filter"), true);
        refresh();
    }

    static boolean open(ServerPlayer player) {
        if (!(player.getMainHandItem().getItem() instanceof PackageFilterItem)
                || !player.mayBuild()
                || player.isSpectator()) return false;
        return new PolymerPackageFilterGui(player).open();
    }

    private void refresh() {
        List<Boolean> pixels = PolymerArtKit.flags(LANE_W);
        PolymerArtKit.write(pixels, 0, LANE_X, LANE_W, LANE_X, source.address);
        List<Component> addressTip = List.of(Component.translatable("create.gui.schedule.lmb_edit"));
        setSlot(BOX, PolymerGuiControls.decoration(PackageStyles.getDefaultBox(), null));
        setSlot(
                HOST,
                PolymerGuiControls.decoration(
                        PolymerArtKit.stack("package_filter_foreground", List.of(), pixels), addressTip));
        for (int slot = 3; slot <= 8; slot++)
            setSlot(slot, PolymerGuiControls.decoration(PolymerArtKit.stack("package_filter_empty", null), addressTip));
        setSlot(
                RESET,
                PolymerGuiControls.decoration(
                        PolymerGuiTextures.icon("package_filter_reset"),
                        List.of(Component.translatable("create.action.reset"))));
        setSlot(
                CONFIRM,
                PolymerGuiControls.decoration(
                        PolymerGuiTextures.icon("package_filter_confirm"),
                        List.of(Component.translatable("create.action.confirm"))));
        lifecycle.requestSync();
    }

    @Override
    public boolean onAnyClick(int slot, ClickType click, ContainerInput action) {
        lifecycle.requestSync();
        if (!stillValid()) {
            close();
            return false;
        }
        if (!PolymerGuiControls.primaryClick(click, action) || !click.isLeft) return false;
        if (slot >= HOST && slot <= 8) {
            PolymerTextPrompt.open(
                    player,
                    target.getHoverName(),
                    new PolymerTextPrompt.Field(
                            Component.translatable("item.create.package_filter"), source.address, MAX_ADDRESS),
                    value -> {
                        source.address = value;
                        if (stillValid()) refresh();
                    });
        } else if (slot == RESET) {
            source.clearContents();
            source.address = "";
            refresh();
        } else if (slot == CONFIRM) close();
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
                    PolymerArtKit.copy(PolymerArtKit.image(builder, "assets/create/textures/gui/filters_2.png")
                            .getSubimage(0, 0, 218, 79));
            PolymerFrogArt.erase(art, 170, 53, 37, 23, 90, 64);
            String title = PolymerSchematicGuis.lang("item.create.package_filter");
            PolymerArtKit.text(art, glyphs, (218 - 8) / 2 - PolymerArtKit.width(title) / 2, 4, title, 0x3D3C48, false);
            PolymerFrogArt.panel(builder, "package_filter", art, OX, OY, ROWS);
            PolymerFrogArt.item(
                    builder,
                    "package_filter_reset",
                    PolymerFrogArt.button(builder, 0, 1, 0),
                    RESET,
                    PolymerFrogArt.slotX(RESET) - 1,
                    53);
            PolymerFrogArt.item(
                    builder,
                    "package_filter_confirm",
                    PolymerFrogArt.button(builder, 0, 0, 1),
                    CONFIRM,
                    PolymerFrogArt.slotX(CONFIRM) - 1,
                    53);
            var kit = new PolymerArtKit(builder, "package_filter", 52, 26);
            kit.compactLane(0, LANE_X, LANE_Y, LANE_W, glyphs, 0xFFFFFF, true, .02f);
            kit.finish("package_filter_foreground");
            PolymerArtKit.empty(builder, "package_filter_empty");
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot build Package Filter screen art", error);
        }
    }
}
