package com.zurrtum.create.content.logistics.packagePort;

import com.zurrtum.create.AllHandle;
import com.zurrtum.create.foundation.blockEntity.behaviour.animatedContainer.AnimatedContainerMenu;
import com.zurrtum.create.foundation.gui.menu.MenuSlot;
import com.zurrtum.create.infrastructure.packet.c2s.PackagePortConfigurationPacket;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerMenuArt;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerTextPrompt;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class PackagePortVanillaMenu extends ChestMenu implements AnimatedContainerMenu {
    public static final int ROWS = 5, PACKAGE_SLOTS = 18, CONTROL_START = 27, PLAYER_START = 45, PREVIEW_SLOT = 40;
    private final PackagePortBlockEntity blockEntity;
    private final Player owner;
    private final SimpleContainer controls;
    private String shownAddress;
    private boolean shownLinked;

    public PackagePortVanillaMenu(int id, Inventory inventory, PackagePortBlockEntity be, Player player) {
        super(MenuType.GENERIC_9x5, id, inventory, new SimpleContainer(PLAYER_START), ROWS);
        blockEntity = be;
        owner = player;
        controls = new SimpleContainer(PLAYER_START - PACKAGE_SLOTS);
        for (int i = 0; i < PLAYER_START; i++) {
            Slot slot = i < PACKAGE_SLOTS
                    ? new MenuSlot(be.inventory, i, 8 + i % 9 * 18, 18 + i / 9 * 18)
                    : new ControlSlot(controls, i - PACKAGE_SLOTS, 8 + i % 9 * 18, 18 + i / 9 * 18);
            slot.index = i;
            slots.set(i, slot);
        }
        refreshControls();
        shownAddress = be.addressFilter;
        shownLinked = be.target != null;
        if (!player.level().isClientSide() && be.openTracker != null) be.openTracker.startOpen(player);
    }

    private static final class ControlSlot extends Slot {
        ControlSlot(SimpleContainer c, int i, int x, int y) {
            super(c, i, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }

    private void refreshControls() {
        for (int slot = PACKAGE_SLOTS; slot < PLAYER_START; slot++) {
            ItemStack next = slot == PREVIEW_SLOT
                    ? PolymerMenuArt.foreground(blockEntity)
                    : PolymerMenuArt.control(blockEntity, slot - CONTROL_START);
            int index = slot - PACKAGE_SLOTS;
            if (!ItemStack.isSameItemSameComponents(controls.getItem(index), next)) controls.setItem(index, next);
        }
    }

    @Override
    public void broadcastChanges() {
        refreshControls();
        if ((!java.util.Objects.equals(shownAddress, blockEntity.addressFilter)
                        || shownLinked != (blockEntity.target != null))
                && owner instanceof ServerPlayer sp) {
            shownAddress = blockEntity.addressFilter;
            shownLinked = blockEntity.target != null;
            sp.connection.send(new ClientboundOpenScreenPacket(
                    containerId,
                    getType(),
                    PolymerMenuArt.title(
                            this, blockEntity.getBlockState().getBlock().getName())));
            sendAllDataToRemote();
        }
        super.broadcastChanges();
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof ControlSlot) && super.canDragTo(slot);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot instanceof ControlSlot) && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public void clicked(int slot, int button, ContainerInput input, Player player) {
        if (player != owner || !stillValid(player)) return;
        if (slot >= PACKAGE_SLOTS && slot < PLAYER_START) {
            if (input == ContainerInput.PICKUP
                    && (button == 0 || button == 1)
                    && player instanceof ServerPlayer sp
                    && sp.mayBuild()
                    && sp.mayInteract(sp.level(), blockEntity.getBlockPos())
                    && sp.level().mayInteract(sp, blockEntity.getBlockPos())) {
                if (slot == CONTROL_START + 7)
                    PolymerTextPrompt.open(
                            sp,
                            blockEntity.getBlockState().getBlock().getName(),
                            new PolymerTextPrompt.Field(Component.literal("Address"), blockEntity.addressFilter, 25),
                            address -> {
                                if (!stillValid(sp)) return;
                                AllHandle.onPackagePortConfiguration(
                                        sp.connection,
                                        new PackagePortConfigurationPacket(
                                                blockEntity.getBlockPos(), address, blockEntity.acceptsPackages));
                                refreshControls();
                            });
                else if ((slot == CONTROL_START + 1 || slot == CONTROL_START + 2) && blockEntity.target != null) {
                    AllHandle.onPackagePortConfiguration(
                            sp.connection,
                            new PackagePortConfigurationPacket(
                                    blockEntity.getBlockPos(), blockEntity.addressFilter, slot == CONTROL_START + 1));
                    refreshControls();
                } else if (slot == CONTROL_START + 8) sp.closeContainer();
            }
            sendAllDataToRemote();
            return;
        }
        super.clicked(slot, button, input, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || index >= PACKAGE_SLOTS && index < PLAYER_START || !stillValid(player))
            return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem().copy(), moved = stack.copy();
        if (!(index < PACKAGE_SLOTS
                ? moveItemStackTo(stack, PLAYER_START, slots.size(), true)
                : moveItemStackTo(stack, 0, PACKAGE_SLOTS, false))) return ItemStack.EMPTY;
        slot.setByPlayer(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        return moved;
    }

    @Override
    protected boolean moveItemStackTo(ItemStack stack, int start, int end, boolean reverse) {
        boolean changed = false;
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int n = 0; n < end - start && !stack.isEmpty(); n++) {
                Slot slot = slots.get(reverse ? end - 1 - n : start + n);
                if (!slot.mayPlace(stack)) continue;
                ItemStack old = slot.getItem();
                if (pass == 0 && (old.isEmpty() || !ItemStack.isSameItemSameComponents(old, stack))) continue;
                if (pass == 1 && !old.isEmpty()) continue;
                int amount = Math.min(
                        stack.getCount(),
                        Math.min(slot.getMaxStackSize(stack), stack.getMaxStackSize()) - old.getCount());
                if (amount <= 0) continue;
                slot.setByPlayer(stack.copyWithCount(old.getCount() + amount));
                stack.shrink(amount);
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public boolean stillValid(Player player) {
        return player == owner
                && !blockEntity.isRemoved()
                && player.level() == blockEntity.getLevel()
                && player.level().getBlockEntity(blockEntity.getBlockPos()) == blockEntity
                && player.isWithinBlockInteractionRange(blockEntity.getBlockPos(), 4);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide() && blockEntity.openTracker != null) blockEntity.openTracker.stopOpen(player);
    }

    @Override
    public PackagePortBlockEntity getAnimatedContainerBlockEntity() {
        return blockEntity;
    }
}
