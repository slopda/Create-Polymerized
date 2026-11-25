package com.zurrtum.create.infrastructure.polymer;

import com.zurrtum.create.infrastructure.polymer.block.PolymerDecorativeCulling;
import com.zurrtum.create.infrastructure.polymer.block.PolymerDisplayCulling;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerContraptionVisuals;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerShiftCarry;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerShiftCellLimit;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerShiftPacer;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.fabricmc.api.ModInitializer;

public final class PolymerDevSwitches implements ModInitializer {
    @Override
    public void onInitialize() {
        if (off("polymerpatch.nativeClients")) PolymerNativeClients.ENABLED = false;
        if (off("polymerpatch.culling.decorations")) PolymerDecorativeCulling.ENABLED = false;
        if (off("polymerpatch.culling.kinetics")) PolymerDisplayCulling.KINETICS_ENABLED = false;
        Integer smooth = Integer.getInteger("polymerpatch.train.smoothTicks");
        if (smooth != null) PolymerContraptionVisuals.TRAIN_SMOOTH = Math.clamp(smooth, 1, 3);
        Integer cells = Integer.getInteger("polymerpatch.carry.cellLimit");
        if (cells != null) PolymerShiftCellLimit.DEFAULT = Math.clamp(cells, 32, PolymerShiftCarry.MAX_CELLS);
        Long spin = Long.getLong("polymerpatch.carry.pacerSpinMicros");
        if (spin != null) PolymerShiftPacer.SPIN = spin * 1_000L;
        Integer png = Integer.getInteger("polymerpatch.pack.pngLevel");
        if (png != null) PolymerPackOptimizer.PNG_LEVEL = Math.clamp(png, 0, 9);
    }

    private static boolean off(String key) {
        return "false".equalsIgnoreCase(System.getProperty(key));
    }
}
