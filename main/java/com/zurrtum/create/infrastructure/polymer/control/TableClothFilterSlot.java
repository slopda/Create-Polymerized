package com.zurrtum.create.infrastructure.polymer.control;

import com.zurrtum.create.catnip.math.VecHelper;
import com.zurrtum.create.content.logistics.tableCloth.TableClothBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class TableClothFilterSlot extends ValueBoxTransform {
    private final TableClothBlockEntity be;

    public TableClothFilterSlot(TableClothBlockEntity be) {
        this.be = be;
    }

    @Override
    public Vec3 getLocalOffset(BlockState state) {
        Vec3 v = be.sideOccluded ? VecHelper.voxelSpace(8, 0.75, 15.25) : VecHelper.voxelSpace(12, -2.75, 16.75);
        return VecHelper.rotateCentered(v, -be.facing.toYRot(), Axis.Y);
    }

    @Override
    public boolean testHit(LevelAccessor level, BlockPos pos, BlockState state, Vec3 localHit) {
        Vec3 offset = getLocalOffset(state);
        return offset != null && localHit.distanceTo(offset) < 0.45;
    }

    @Override
    public void rotate(BlockState state, ControlPose ms) {
        ms.rotateYDegrees(180 - be.facing.toYRot()).rotateXDegrees(be.sideOccluded ? 90 : 0);
    }
}
