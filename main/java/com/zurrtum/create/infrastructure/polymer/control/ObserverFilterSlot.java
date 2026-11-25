package com.zurrtum.create.infrastructure.polymer.control;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class ObserverFilterSlot extends ValueBoxTransform {
    @Override
    public void rotate(BlockState state, ControlPose ms) {
        ms.rotateXDegrees(90);
    }

    @Override
    public Vec3 getLocalOffset(BlockState state) {
        return new Vec3(0.5, 15.5 / 16.0d, 0.5);
    }
}
