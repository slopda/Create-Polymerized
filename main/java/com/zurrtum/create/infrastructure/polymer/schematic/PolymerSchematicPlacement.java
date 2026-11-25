package com.zurrtum.create.infrastructure.polymer.schematic;

import com.zurrtum.create.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

final class PolymerSchematicPlacement {
    private PolymerSchematicPlacement() {}

    static BlockPos anchorAt(BlockPos selected, Vec3i size, Rotation rotation, Mirror mirror) {
        double xOrigin = size.getX() / 2.0f, zOrigin = size.getZ() / 2.0f;
        int frontBack = mirror == Mirror.FRONT_BACK ? -1 : 1;
        int leftRight = mirror == Mirror.LEFT_RIGHT ? -1 : 1;
        double offsetX = 0, offsetZ = 0;
        if (size.getX() % 2 != size.getZ() % 2) {
            offsetX = xOrigin > zOrigin ? 0 : .5 * frontBack;
            offsetZ = xOrigin > zOrigin ? .5 * leftRight : 0;
        }
        Vec3 first = new Vec3(.5 - xOrigin - offsetX, 0, .5 - zOrigin - offsetZ).multiply(frontBack, 1, leftRight);
        first = VecHelper.rotate(first, -rotation.ordinal() * 90, Direction.Axis.Y)
                .add(xOrigin, 0, zOrigin);
        BlockPos target = selected.offset(-(int) xOrigin, 0, -(int) zOrigin);
        return BlockPos.containing(first.add(target.getX(), target.getY(), target.getZ()));
    }
}
