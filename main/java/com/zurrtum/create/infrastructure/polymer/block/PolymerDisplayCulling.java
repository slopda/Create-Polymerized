package com.zurrtum.create.infrastructure.polymer.block;

import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class PolymerDisplayCulling {
    static final float KINETIC_RADIUS = 2f;
    public static boolean KINETICS_ENABLED = true;

    private PolymerDisplayCulling() {}

    static void kinetic(ItemDisplayElement display) {
        kinetic(display, KINETICS_ENABLED);
    }

    static void kinetic(ItemDisplayElement display, boolean enabled) {
        if (!enabled) return;
        display.setOffset(new Vec3(0, -KINETIC_RADIUS, 0));
        display.setTranslation(new Vector3f(0, KINETIC_RADIUS, 0));
        display.setDisplaySize(2 * KINETIC_RADIUS, 2 * KINETIC_RADIUS);
        display.setViewRange(Math.max(display.getViewRange(), 1.25f));
    }
}
