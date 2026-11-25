package com.zurrtum.create.infrastructure.polymer.control;

import com.zurrtum.create.catnip.math.AngleHelper;
import java.util.function.BiPredicate;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public class DirectionalExtenderScrollOptionSlot extends CenteredSideValueBoxTransform {

    public DirectionalExtenderScrollOptionSlot(BiPredicate<BlockState, Direction> allowedDirections) {
        super(allowedDirections);
    }

    @Override
    public Vec3 getLocalOffset(BlockState state) {
        return super.getLocalOffset(state)
                .add(Vec3.atLowerCornerOf(
                                state.getValue(BlockStateProperties.FACING).getUnitVec3i())
                        .scale(-2 / 16.0f));
    }

    @Override
    public void rotate(BlockState state, ControlPose ms) {
        if (!getSide().getAxis().isHorizontal()) {
            ms.rotateYDegrees(AngleHelper.horizontalAngle(state.getValue(BlockStateProperties.FACING)) + 180);
        }
        super.rotate(state, ms);
    }
}
