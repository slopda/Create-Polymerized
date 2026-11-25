package com.zurrtum.create.infrastructure.polymer.gui;

import com.zurrtum.create.AllHandle;
import com.zurrtum.create.content.equipment.toolbox.ToolboxBlockEntity;
import com.zurrtum.create.content.equipment.toolbox.ToolboxMenu;
import com.zurrtum.create.foundation.blockEntity.behaviour.animatedContainer.AnimatedContainerMenu;
import com.zurrtum.create.infrastructure.packet.c2s.ToolboxDisposeAllPacket;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.LinkedHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

public final class PolymerToolboxGui {
    static final int[] COMPARTMENTS = {2, 4, 6, 24, 42, 40, 38, 20};
    static final int DEPOSIT = 22, CONFIRM = 44, PREVIEW_HOST = 0;

    public static void open(ServerPlayer player, ToolboxBlockEntity toolbox) {
        RingGui gui = new RingGui(player, toolbox);
        if (!gui.open()) gui.onRemoved();
    }

    private static final class RingGui extends SimpleGui implements AnimatedContainerMenu {
        private final ToolboxBlockEntity toolbox;
        private final ServerLevel level;
        private final Block block;
        private final ToolboxMenu backend;
        private final PolymerGuiLifecycle lifecycle = new PolymerGuiLifecycle(5, false);

        private RingGui(ServerPlayer player, ToolboxBlockEntity toolbox) {
            super(MenuType.GENERIC_9x6, player, false);
            this.toolbox = toolbox;
            this.level = player.level();
            this.block = toolbox.getBlockState().getBlock();
            backend = new ToolboxMenu(-1, player.getInventory(), toolbox);
            PolymerGuiControls.configure(this, title(toolbox.getDisplayName()), false);
            refresh();
        }

        @Override
        public boolean stillValid() {
            return !lifecycle.isClosed()
                    && PolymerGuiControls.targetPresent(player, toolbox, level, block)
                    && !player.isSpectator()
                    && player.mayInteract(level, toolbox.getBlockPos());
        }

        private void refresh() {
            for (int slot = 0; slot < 54; slot++) clearSlot(slot);
            ItemStack host = PolymerGuiTextures.icon("toolbox_patch_0");
            PolymerGuiPreviews.variant(
                    host, new ItemStack(toolbox.getBlockState().getBlock()));
            host.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new LinkedHashSet<>()));
            setSlot(PREVIEW_HOST, PolymerGuiControls.builder(host).build());
            for (int compartment = 0; compartment < 8; compartment++) {
                ItemStack representative = backend.getSlot(compartment * 4).getItem();
                int count = backend.totalCountInCompartment(compartment);
                ItemStack filter = backend.getFilter(compartment);
                ItemStack icon = representative.isEmpty() ? filter : representative;
                if (!icon.isEmpty()) {
                    ItemStack shown = PolymerMarketItems.displayToolbox(icon, count);
                    setSlot(
                            COMPARTMENTS[compartment],
                            PolymerGuiControls.builder(shown)
                                    .setName(icon.getHoverName())
                                    .addLoreLine(Component.literal("Stored: " + count))
                                    .addLoreLine(Component.literal("F: equip to selected hotbar slot")
                                            .withStyle(ChatFormatting.DARK_GRAY))
                                    .build());
                }
            }
            setSlot(
                    DEPOSIT,
                    PolymerGuiControls.builder(PolymerGuiTextures.icon(
                                    "toolbox", Component.translatable("create.toolbox.depositBox")))
                            .addLoreLine(Component.literal("F: return the selected hotbar slot")
                                    .withStyle(ChatFormatting.DARK_GRAY))
                            .build());
            setSlot(
                    CONFIRM,
                    PolymerGuiControls.builder(
                                    PolymerGuiTextures.icon("confirm", Component.translatable("create.action.confirm")))
                            .build());
        }

        @Override
        public boolean onAnyClick(int slot, ClickType click, ContainerInput action) {
            if (!stillValid()) {
                close();
                return false;
            }
            int button = nativeButton(click, action);
            if (button < 0) return false;
            int compartment = -1;
            for (int i = 0; i < 8; i++) if (COMPARTMENTS[i] == slot) compartment = i;
            if (action == ContainerInput.SWAP && button == 40 && (compartment >= 0 || slot == DEPOSIT)) {
                int hotbar = player.getInventory().getSelectedSlot();
                AllHandle.setToolboxForHotbar(
                        player, compartment >= 0 ? toolbox.getBlockPos() : null, compartment, hotbar);
                refresh();
                wrappedMenu.sendAllDataToRemote();
                return false;
            }
            if (action == ContainerInput.PICKUP && click.isLeft && (slot == DEPOSIT || slot == CONFIRM)) {
                if (slot == CONFIRM) {
                    close();
                    return false;
                }
                AllHandle.onToolboxDisposeAll(player.connection, new ToolboxDisposeAllPacket(toolbox.getBlockPos()));
            } else {
                int mapped = compartment >= 0
                        ? compartment * 4
                        : slot >= 54 && slot < 90 ? 32 + slot - 54 : slot == -999 ? -999 : -1;
                if (mapped != -1 || action == ContainerInput.QUICK_CRAFT && slot == -999) {
                    backend.setCarried(wrappedMenu.getCarried());
                    try {
                        backend.clicked(mapped, button, action, player);
                    } finally {
                        wrappedMenu.setCarried(backend.getCarried());
                        backend.setCarried(ItemStack.EMPTY);
                    }
                }
            }
            refresh();
            wrappedMenu.sendAllDataToRemote();
            return false;
        }

        @Override
        public void onTick() {
            if (!stillValid()) {
                close();
                return;
            }
            lifecycle.tick(this::refresh, () -> {});
        }

        @Override
        public void onRemoved() {
            lifecycle.close(
                    () -> {
                        backend.setCarried(ItemStack.EMPTY);
                        backend.removed(player);
                    },
                    () -> {});
        }

        @Override
        public ToolboxBlockEntity getAnimatedContainerBlockEntity() {
            return toolbox;
        }
    }

    private static Component title(Component name) {
        return Component.empty()
                .append(PolymerGuiTextures.title("toolbox", Component.empty()))
                .append(PolymerArtKit.space(new FontDescription.Resource(CreateIds.id("value_board_space")), 4))
                .append(name.copy()
                        .withStyle(Style.EMPTY
                                .withColor(0x592424)
                                .withoutShadow()
                                .withFont(PolymerScreenArt.textFont(PolymerScreenArt.TOOLBOX_TITLE))));
    }

    static int nativeButton(ClickType click, ContainerInput action) {
        if (click == ClickType.UNKNOWN) return -1;
        return switch (action) {
            case PICKUP, QUICK_MOVE -> click.isRight ? 1 : click.isLeft ? 0 : -1;
            case SWAP ->
                click.numKey && click.value >= 1 && click.value <= 9
                        ? click.value - 1
                        : click == ClickType.OFFHAND_SWAP ? 40 : -1;
            case CLONE -> click.isMiddle ? 2 : -1;
            case THROW -> click == ClickType.CTRL_DROP ? 1 : click == ClickType.DROP ? 0 : -1;
            case QUICK_CRAFT ->
                click.isDragging && click.value >= 0 && click.value <= 2
                        ? click.value | (click.isRight ? 1 : click.isMiddle ? 2 : 0) << 2
                        : -1;
            case PICKUP_ALL -> click == ClickType.MOUSE_DOUBLE_CLICK ? 0 : -1;
        };
    }
}
