package com.zurrtum.create.infrastructure.polymer.collision;

import eu.pb4.polymer.virtualentity.api.elements.SimpleEntityElement;
import eu.pb4.polymer.virtualentity.mixin.accessors.EntityAccessor;
import java.util.List;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Mob;

public final class PolymerCollisionShulkers {
    private static final byte INVISIBLE = (byte) (1 << EntityAccessor.getFLAG_INVISIBLE());
    private static final byte NO_AI = 1;

    private PolymerCollisionShulkers() {}

    public static void hide(SimpleEntityElement shulker) {
        shulker.getSyncedData().set(EntityAccessor.getDATA_SHARED_FLAGS_ID(), INVISIBLE);
        shulker.getSyncedData().set(Mob.DATA_MOB_FLAGS_ID, NO_AI);
    }

    public static List<SynchedEntityData.DataValue<?>> hiddenData() {
        return List.of(
                SynchedEntityData.DataValue.create(EntityAccessor.getDATA_SHARED_FLAGS_ID(), INVISIBLE),
                SynchedEntityData.DataValue.create(Mob.DATA_MOB_FLAGS_ID, NO_AI));
    }
}
