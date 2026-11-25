package com.zurrtum.create.infrastructure.polymer;

import com.zurrtum.create.Create;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts;
import com.zurrtum.create.infrastructure.polymer.block.PolymerCopycatParts;
import com.zurrtum.create.infrastructure.polymer.block.PolymerFluidVisuals;
import com.zurrtum.create.infrastructure.polymer.block.PolymerStationParts;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerLadders;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerStaticCollision;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerWallCollision;
import com.zurrtum.create.infrastructure.polymer.effects.PolymerBlockParticles;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerGuiPackAssets;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerMarketItems;
import dev.veeda.createpoly.PatchAssets;
import dev.veeda.createpoly.PolymerPatchCreate;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;

/**
 * Pack contents and the first pack-generation listeners. Listener order decides which
 * generator wins a shared path, so keep this order unless the composed pack is rechecked.
 */
final class PolymerPackRegistration {
    private PolymerPackRegistration() {}

    static void register() {
        if (!PolymerResourcePackUtils.addModAssets(Create.MOD_ID)) {
            Create.LOGGER.warn("Polymer could not add Create assets to its generated server resource pack");
        }
        PolymerResourcePackUtils.addModAssets(PolymerPatchCreate.MOD_ID);
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(PatchAssets::addOverrides);
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(
                PolymerItemModelAssets::rewriteCreateItemModels);
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(
                PolymerItemModelAssets::addDisplayItemModels);
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(
                PolymerTintedModels::addItemModels);
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(PolymerBlockParts::addItemModels);
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(PolymerGuiPackAssets::addAssets);
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(PolymerFluidVisuals::addAssets);
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(PolymerCopycatParts::addAssets);
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(PolymerWallCollision::addAssets);
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(PolymerStaticCollision::addAssets);
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(PolymerBlockParticles::addAssets);
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(PolymerMarketItems::addAssets);
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(PolymerModelMaterials::addAssets);
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(PolymerLadders::addAssets);
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(PolymerStationParts::addAssets);
        PolymerResourcePackUtils.markAsRequired();
    }
}
