package com.zurrtum.create.infrastructure.polymer.gui;

import com.zurrtum.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.zurrtum.create.content.logistics.factoryBoard.FactoryPanelSetItemMenu;
import com.zurrtum.create.content.logistics.factoryBoard.ServerFactoryPanelBehaviour;
import com.zurrtum.create.infrastructure.polymer.PolymerFrogArt;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

public final class PolymerFactoryPanelItemGui extends SimpleGui {
    private static final int OX = -17, OY = -10, ROWS = 4, GHOST = 3, HOST = 0, CONFIRM = 25;

    private final ServerFactoryPanelBehaviour behaviour;
    private final ServerLevel level;
    private final FactoryPanelPosition position;
    private final FactoryPanelSetItemMenu source;
    private final PolymerGuiLifecycle lifecycle = new PolymerGuiLifecycle();

    private PolymerFactoryPanelItemGui(ServerPlayer player, ServerFactoryPanelBehaviour behaviour) {
        super(MenuType.GENERIC_9x4, player, false);
        this.behaviour = behaviour;
        level = player.level();
        position = behaviour.getPanelPosition();
        source = new FactoryPanelSetItemMenu(0, player.getInventory(), behaviour);
        PolymerGuiControls.configure(this, PolymerFrogArt.title("factory_item"), false);
        refresh();
    }

    public static boolean open(ServerPlayer player, ServerFactoryPanelBehaviour behaviour) {
        return new PolymerFactoryPanelItemGui(player, behaviour).open();
    }

    private void refresh() {
        ItemStack item = source.ghostInventory.getItem(0);
        if (item.isEmpty()) clearSlot(GHOST);
        else setSlot(GHOST, PolymerGuiControls.builder(item.copyWithCount(1)).build());
        setSlot(HOST, PolymerGuiControls.decoration(PolymerArtKit.stack("factory_item_host", null), null));
        setSlot(
                CONFIRM,
                PolymerGuiControls.decoration(
                        PolymerGuiTextures.icon("factory_item_confirm"),
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
        if (slot < 0) return true;
        if (slot >= getSize()) {
            if (action == ContainerInput.QUICK_MOVE) {
                source.quickMoveStack(player, slot - getSize());
                refresh();
                return false;
            }
            return action != ContainerInput.PICKUP_ALL;
        }
        if (slot == GHOST && (action == ContainerInput.PICKUP || action == ContainerInput.CLONE)) {
            source.setCarried(player.containerMenu.getCarried());
            source.clicked(36, click.isRight ? 1 : 0, action, player);
            player.containerMenu.setCarried(source.getCarried());
            source.setCarried(ItemStack.EMPTY);
            refresh();
        } else if (slot == CONFIRM && PolymerGuiControls.primaryClick(click, action) && click.isLeft) close();
        return false;
    }

    @Override
    public boolean stillValid() {
        return !player.isSpectator()
                && player.mayBuild()
                && player.level() == level
                && !player.isRemoved()
                && ServerFactoryPanelBehaviour.at(level, position) == behaviour
                && player.isWithinBlockInteractionRange(position.pos(), PolymerGuiControls.MENU_REACH_BUFFER);
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
                    PolymerArtKit.copy(PolymerArtKit.image(builder, "assets/create/textures/gui/requester.png")
                            .getSubimage(16, 160, 184, 88));
            PolymerFrogArt.erase(art, 128, 58, 48, 27, 60, 72);
            String title = PolymerSchematicGuis.lang("create.gui.factory_panel.place_item_to_monitor");
            PolymerArtKit.text(art, glyphs, 92 - PolymerArtKit.width(title) / 2, 4, title, 0x3D3C48, false);
            PolymerFrogArt.panel(builder, "factory_item", art, OX, OY, ROWS);
            PolymerFrogArt.item(
                    builder,
                    "factory_item_confirm",
                    PolymerFrogArt.button(builder, 0, 0, 1),
                    CONFIRM,
                    149 + OX,
                    63 + OY);
            PolymerArtKit.empty(builder, "factory_item_host");
            PolymerFrogArt.item(
                    builder,
                    "factory_relocate",
                    PolymerFrogArt.button(builder, 90, 4, 12),
                    28,
                    PolymerFrogArt.slotX(28) - 1,
                    PolymerFrogArt.slotY(28) - 1);
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot build Factory Panel item screen art", error);
        }
    }
}
