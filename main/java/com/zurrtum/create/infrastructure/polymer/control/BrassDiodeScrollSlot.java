package com.zurrtum.create.infrastructure.polymer.control;

import com.zurrtum.create.catnip.math.AngleHelper;
import com.zurrtum.create.catnip.math.VecHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public class BrassDiodeScrollSlot extends ValueBoxTransform {

    @Override
    public Vec3 getLocalOffset(BlockState state) {
        return VecHelper.voxelSpace(8, 2.6f, 8);
    }

    @Override
    public void rotate(BlockState state, ControlPose ms) {
        float yRot = AngleHelper.horizontalAngle(state.getValue(BlockStateProperties.HORIZONTAL_FACING)) + 180;
        ms.rotateYDegrees(yRot).rotateXDegrees(90);
    }

    @Override
    public int getOverrideColor() {
        return 0xFF592424;
    }
}
