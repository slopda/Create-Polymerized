package com.zurrtum.create.infrastructure.polymer.gui;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

public final class PolymerGuiControls {
    private PolymerGuiControls() {}

    public static void configure(SimpleGui gui) {
        configure(gui, Component.literal(" "), true);
    }

    public static void configure(SimpleGui gui, Component title, boolean lockInventory) {
        gui.setTitle(title);
        gui.setLockPlayerInventory(lockInventory);
    }

    static ItemStack nativeDecoration(ItemStack source, Component title, List<Component> lore) {
        ItemStack stack = source.copy();
        stack.set(DataComponents.CUSTOM_NAME, title);
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    static ItemStack nativeDecoration(ItemStack source, @Nullable List<Component> tooltip) {
        if (tooltip != null && !tooltip.isEmpty())
            return nativeDecoration(source, tooltip.getFirst(), tooltip.subList(1, tooltip.size()));
        ItemStack stack = source.copy();
        stack.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new LinkedHashSet<>()));
        return stack;
    }

    public static GuiElementBuilder builder(ItemStack source) {
        return new GuiElementBuilder(source.copy());
    }

    public static GuiElementBuilder builder(Item source) {
        return builder(new ItemStack(source));
    }

    public static GuiElement decoration(ItemStack source, @Nullable List<Component> tooltip) {
        ItemStack stack = source.copy();
        if (tooltip == null || tooltip.isEmpty()) {
            stack.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new LinkedHashSet<>()));
            return new GuiElementBuilder(stack).build();
        }
        var element = new GuiElementBuilder(stack).setName(tooltip.getFirst());
        for (Component line : tooltip.subList(1, tooltip.size())) element.addLoreLine(line);
        return element.build();
    }

    public static boolean primaryClick(ClickType click, ContainerInput action) {
        return (action == ContainerInput.PICKUP || action == ContainerInput.QUICK_MOVE)
                && !click.isDragging
                && (click.isLeft || click.isRight);
    }

    public static final double MENU_REACH_BUFFER = 4;

    static boolean editableTarget(ServerPlayer player, BlockEntity target, ServerLevel level, Block expectedBlock) {
        return targetPresent(player, target, level, expectedBlock)
                && !player.isSpectator()
                && player.mayBuild()
                && player.mayInteract(level, target.getBlockPos());
    }

    static boolean targetPresent(ServerPlayer player, BlockEntity target, ServerLevel level, Block expectedBlock) {
        if (target.getLevel() != level) return false;
        var pos = target.getBlockPos();
        return player.level() == level
                && !player.isRemoved()
                && !target.isRemoved()
                && level.hasChunkAt(pos)
                && level.getBlockEntity(pos) == target
                && level.getBlockState(pos).is(expectedBlock)
                && player.isWithinBlockInteractionRange(pos, MENU_REACH_BUFFER);
    }

    public static Component option(String text, boolean selected) {
        return Component.literal((selected ? "-> " : "   ") + text)
                .withStyle(s -> s.withColor(selected ? ChatFormatting.WHITE : ChatFormatting.GRAY)
                        .withItalic(false));
    }

    public static Component hint(String text) {
        return Component.literal(text)
                .withStyle(s -> s.withColor(ChatFormatting.DARK_GRAY).withItalic(false));
    }

    public static Component title(String text) {
        return Component.literal(text)
                .withStyle(s -> s.withColor(ChatFormatting.WHITE).withItalic(false));
    }
}
