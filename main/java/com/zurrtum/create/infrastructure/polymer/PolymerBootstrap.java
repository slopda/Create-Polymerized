package com.zurrtum.create.infrastructure.polymer;

import com.zurrtum.create.Create;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts;
import com.zurrtum.create.infrastructure.polymer.block.PolymerChainConveyors;
import com.zurrtum.create.infrastructure.polymer.block.PolymerCopycatParts;
import com.zurrtum.create.infrastructure.polymer.block.PolymerKineticVisuals;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerProxyMining;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerContraptionVisuals;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerMinecartInteractions;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerObserverPlayers;
import com.zurrtum.create.infrastructure.polymer.equipment.PolymerCardboardDisguise;
import com.zurrtum.create.infrastructure.polymer.equipment.PolymerHangingPose;
import com.zurrtum.create.infrastructure.polymer.equipment.PolymerPlayerEffects;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerDialogService;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerGuiPreviews;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerThresholdGui;
import com.zurrtum.create.infrastructure.polymer.interaction.PolymerElevatorControls;
import com.zurrtum.create.infrastructure.polymer.ponder.PolymerPonderEntry;
import com.zurrtum.create.infrastructure.polymer.schematic.PolymerBlueprintVisuals;
import com.zurrtum.create.infrastructure.polymer.schematic.PolymerQuill;
import com.zurrtum.create.infrastructure.polymer.schematic.PolymerSchematicMenus;
import com.zurrtum.create.infrastructure.polymer.schematic.PolymerSchematicTools;
import com.zurrtum.create.infrastructure.polymer.train.PolymerTrackPlacementPreview;
import com.zurrtum.create.infrastructure.polymer.train.PolymerTrackVisuals;
import com.zurrtum.create.infrastructure.polymer.train.PolymerTrainMessages;
import com.zurrtum.create.infrastructure.polymer.train.PolymerTrainParticles;
import dev.veeda.createpoly.NativeMetadataSnapshots;

/**
 * The adapter's one ordered startup path. Order decides model-state allocation, pack
 * listener order and use-block callback order, so move a line only with a pack compare.
 * tools/verify_bootstrap_order.py checks it against the recorded baseline.
 */
public final class PolymerBootstrap {
    private static boolean registered;
    private static boolean featuresInitialized;

    private PolymerBootstrap() {}

    public static void register() {
        if (registered) return;
        registered = true;
        NativeMetadataSnapshots.register();
        PolymerPackRegistration.register();

        PolymerItemProjections.registerItems();
        PolymerItemProjections.registerBlockEntities();
        PolymerCopycatParts.registerCarriers();
        PolymerBlockProjections.registerBlocks();
        PolymerProxyMining.register();
        PolymerEntityProjections.registerEntities();
        PolymerContraptionVisuals.register();
        PolymerMinecartInteractions.register();
        PolymerElevatorControls.register();
        PolymerKineticVisuals.register();
        PolymerBlockParts.register();
        PolymerChainConveyors.register();
        PolymerTrackVisuals.register();
        PolymerTrackPlacementPreview.register();
        PolymerPlayerEffects.register();
        PolymerCardboardDisguise.register();
        PolymerHangingPose.register();
        PolymerBlueprintVisuals.register();
        PolymerHolderRange.register();
        PolymerThresholdGui.registerInteraction();
        PolymerDialogService.register();
        PolymerRegistrySync.markCreateEntriesAsServerOnly();
        Create.LOGGER.info("Polymer Patch: Create Fly registered as a separate server-side adapter");
    }

    public static void initializeFeatures() {
        if (featuresInitialized) return;
        featuresInitialized = true;
        new PolymerTrainParticles().onInitialize();
        new PolymerSchematicMenus().onInitialize();
        new PolymerQuill().onInitialize();
        new PolymerObserverPlayers().onInitialize();
        new PolymerSchematicTools().onInitialize();
        new PolymerTrainMessages().onInitialize();
        new PolymerGuiPreviews().onInitialize();
        new PolymerPonderEntry().onInitialize();
    }
}
