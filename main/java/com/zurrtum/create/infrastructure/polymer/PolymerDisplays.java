package com.zurrtum.create.infrastructure.polymer;

import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class PolymerDisplays {
    private PolymerDisplays() {}

    public static ItemDisplayElement item(ItemStack stack, ItemDisplayContext context) {
        ItemDisplayElement element = new ItemDisplayElement(stack);
        element.setItemDisplayContext(context);
        return element;
    }
}
