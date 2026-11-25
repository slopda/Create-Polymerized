package com.zurrtum.create.infrastructure.polymer.gui;

import com.zurrtum.create.Create;
import com.zurrtum.create.infrastructure.polymer.PolymerGhostModels;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBacktankParts;
import com.zurrtum.create.infrastructure.polymer.block.PolymerCrafterParts;
import com.zurrtum.create.infrastructure.polymer.block.PolymerDiodeIndicators;
import com.zurrtum.create.infrastructure.polymer.block.PolymerWaterWheelWoods;
import com.zurrtum.create.infrastructure.polymer.equipment.PolymerBacktankWear;
import com.zurrtum.create.infrastructure.polymer.equipment.PolymerSymmetryWand;
import com.zurrtum.create.infrastructure.polymer.equipment.PolymerToolSpin;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerGogglesOverlay;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerShopOverlay;
import com.zurrtum.create.infrastructure.polymer.interaction.PolymerArmTargets;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;

public final class PolymerGuiPackAssets {
    private PolymerGuiPackAssets() {}

    public static void addAssets(ResourcePackBuilder builder) {
        BufferedImage transparent = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        try {
            builder.addData(
                    "assets/minecraft/textures/gui/sprites/container/slot_highlight_front.png",
                    PngCodec.encode(transparent));
        } catch (IOException exception) {
            Create.LOGGER.warn("Failed to generate transparent front slot highlight sprite", exception);
        }
        try {
            builder.addData(
                    "assets/minecraft/textures/gui/sprites/container/slot_highlight_back.png",
                    PngCodec.encode(transparent));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot generate transparent slot hover", exception);
        }
        PolymerValueBoard.addAssets(builder);
        PolymerGogglesOverlay.addAssets(builder);
        PolymerGhostModels.addAssets(builder);
        PolymerScreenArt.addAssets(builder);
        PolymerToolSpin.addAssets(builder);
        PolymerShopOverlay.addAssets(builder);
        PolymerBacktankWear.addAssets(builder);
        PolymerMenuArt.addAssets(builder);
        PolymerStationGuiArt.addAssets(builder);
        PolymerSequencerGui.addAssets(builder);
        PolymerArmTargets.addAssets(builder);
        PolymerDiodeIndicators.addAssets(builder);
        PolymerDisplayLinkGui.addAssets(builder);
        PolymerClipboardScreen.addAssets(builder);
        PolymerCrafterParts.addAssets(builder);
        PolymerSettingsBoard.addAssets(builder);
        PolymerThresholdGui.addAssets(builder);
        PolymerRedstoneRequesterGui.addAssets(builder);
        PolymerStockMarketGui.addAssets(builder);
        PolymerBacktankParts.addAssets(builder);
        PolymerSymmetryWand.addAssets(builder);
        PolymerWorldshaperScreen.addAssets(builder);
        PolymerWaterWheelWoods.addAssets(builder);
        PolymerScheduleScreenAssets.addAssets(builder);
        PolymerQuillScreen.addAssets(builder);
        PolymerPackageFilterGui.addAssets(builder);
        PolymerAttributeFilterGui.addAssets(builder);
        PolymerElevatorGui.addAssets(builder);
        PolymerGhostItemGui.addAssets(builder);
        PolymerStockCategoryGui.addAssets(builder);
        PolymerFactoryPanelItemGui.addAssets(builder);
    }
}
