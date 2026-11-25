package dev.veeda.createpoly;

import com.zurrtum.create.Create;
import com.zurrtum.create.infrastructure.polymer.PolymerBootstrap;
import net.fabricmc.api.ModInitializer;

public final class PolymerPatchCreate implements ModInitializer {
    public static final String MOD_ID = "createpoly";

    public static void register() {
        PolymerBootstrap.register();
    }

    @Override
    public void onInitialize() {
        if (Create.TORQUE_PROPAGATOR != null) register();
        PolymerBootstrap.initializeFeatures();
    }
}
