package com.zurrtum.create.content.redstone.link.controller;

import com.zurrtum.create.AllDataComponents;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.foundation.item.ItemHelper;
import com.zurrtum.create.infrastructure.items.ItemStackHandler;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

public final class LinkedControllerVanillaMenu extends ChestMenu {
    private static final int FREQUENCY_SLOT_COUNT = 12;

    private final ItemStack controller;

    public LinkedControllerVanillaMenu(int menuId, Inventory playerInventory, ItemStack controller) {
        this(menuId, playerInventory, controller, new FrequencyContainer(controller));
    }

    private LinkedControllerVanillaMenu(
            int menuId, Inventory playerInventory, ItemStack controller, FrequencyContainer frequencyContainer) {
        super(MenuType.GENERIC_9x3, menuId, playerInventory, frequencyContainer, 3);
        this.controller = controller;
    }

    @Override
    public boolean stillValid(Player player) {
        return super.stillValid(player) && isInInventory(player, controller);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex >= 27 && slotIndex < slots.size() && slots.get(slotIndex).getItem() == controller) {
            return ItemStack.EMPTY;
        }
        return super.quickMoveStack(player, slotIndex);
    }

    private static boolean isInInventory(Player player, ItemStack controller) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot) == controller) {
                return true;
            }
        }
        return false;
    }

    private static final class FrequencyContainer extends SimpleContainer {
        private final ItemStack controller;
        private boolean initializing = true;

        private FrequencyContainer(ItemStack controller) {
            super(27);
            this.controller = controller;
            ItemStackHandler frequencyItems = LinkedControllerItem.getFrequencyItems(controller);
            for (int slot = 0; slot < FREQUENCY_SLOT_COUNT; slot++) {
                super.setItem(slot, frequencyItems.getItem(slot).copy());
            }
            initializing = false;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return slot >= 0 && slot < FREQUENCY_SLOT_COUNT;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (slot >= 0 && slot < FREQUENCY_SLOT_COUNT) {
                super.setItem(slot, stack);
            }
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (initializing || !controller.is(AllItems.LINKED_CONTROLLER)) {
                return;
            }

            ItemStackHandler frequencyItems = new ItemStackHandler(FREQUENCY_SLOT_COUNT);
            for (int slot = 0; slot < FREQUENCY_SLOT_COUNT; slot++) {
                frequencyItems.setItem(slot, super.getItem(slot).copy());
            }
            controller.set(
                    AllDataComponents.LINKED_CONTROLLER_ITEMS, ItemHelper.containerContentsFromHandler(frequencyItems));
        }

        @Override
        public boolean stillValid(Player player) {
            return isInInventory(player, controller);
        }
    }
}
