package com.zurrtum.create.infrastructure.polymer.gui;

import static dev.veeda.createpoly.assets.CreateIds.id;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class PolymerGuiTextures {
    private PolymerGuiTextures() {}

    public static Component title(String screen, Component name) {
        return title(screen, name, 0x714A40);
    }

    public static Component title(String screen, Component name, int color) {
        return Component.empty()
                .append(Component.literal("\uE101\uE100\uE102")
                        .withStyle(s -> s.withFont(new FontDescription.Resource(id(screen + "_gui")))
                                .withColor(0xFFFFFF)))
                .append(name.copy().withColor(color));
    }

    static Component iconLabel(String icon, Component label) {
        return Component.empty()
                .append(Component.literal("\uE100")
                        .withStyle(s -> s.withFont(new FontDescription.Resource(id("dialog_icon_" + icon)))
                                .withColor(0xFFFFFF)))
                .append(Component.literal(" "))
                .append(label);
    }

    public static Component actionLabel(String label) {
        String icon = label.startsWith("Confirm") || label.equals("Apply")
                ? "confirm"
                : label.equals("Save") || label.startsWith("Save ")
                        ? "config_save"
                        : label.equals("Cancel")
                                        || label.startsWith("Cancel ")
                                        || label.equals("Discard")
                                        || label.startsWith("Discard ")
                                ? "config_discard"
                                : label.startsWith("Add ") || label.equals("Add")
                                        ? "add"
                                        : label.startsWith("Delete")
                                                        || label.startsWith("Remove")
                                                        || label.equals("Clear")
                                                ? "trash"
                                                : label.equals("Refresh")
                                                        ? "refresh"
                                                        : label.startsWith("Reset")
                                                                ? "config_reset"
                                                                : label.equals("Back") || label.startsWith("Back ")
                                                                        ? "config_back"
                                                                        : label.startsWith("Previous")
                                                                                ? "config_prev"
                                                                                : label.startsWith("Next")
                                                                                        ? "config_next"
                                                                                        : label.equals("Play")
                                                                                                        || label.equals(
                                                                                                                "Start")
                                                                                                ? "play"
                                                                                                : label.equals("Pause")
                                                                                                        ? "pause"
                                                                                                        : label.equals(
                                                                                                                        "Stop")
                                                                                                                ? "stop"
                                                                                                                : label
                                                                                                                                .equals(
                                                                                                                                        "Close")
                                                                                                                        ? "mtd_close"
                                                                                                                        : null;
        Component text = Component.literal(label);
        return icon == null ? text : iconLabel(icon, text);
    }

    public static ItemStack icon(String name) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.ITEM_MODEL, id("ui/gui_" + name));
        return stack;
    }

    public static ItemStack icon(String name, Component label) {
        ItemStack stack = icon(name);
        stack.set(DataComponents.CUSTOM_NAME, label);
        return stack;
    }
}
