package com.zurrtum.create.infrastructure.polymer.control;

import com.zurrtum.create.catnip.math.VecHelper;
import net.minecraft.world.phys.Vec3;

public class BrassTunnelFilterSlot extends ValueBoxTransform.Sided {

    @Override
    protected Vec3 getSouthLocation() {
        return VecHelper.voxelSpace(8, 13, 15.5f);
    }
}
