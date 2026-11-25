package com.zurrtum.create.infrastructure.polymer.block;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.catnip.math.AngleHelper;
import com.zurrtum.create.content.decoration.slidingDoor.SlidingDoorBlock;
import com.zurrtum.create.content.decoration.slidingDoor.SlidingDoorBlockEntity;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import org.joml.Matrix4f;

public final class PolymerDoorParts {
    private static final float DOOR_OFFSET = -1 / 512.0f;

    private PolymerDoorParts() {}

    static void registerAll() {
        sliding(AllBlocks.BRASS_DOOR, "brass_door");
        sliding(AllBlocks.TRAIN_DOOR, "train_door");
        sliding(AllBlocks.FRAMED_GLASS_DOOR, "framed_glass_door");
        folding(AllBlocks.ANDESITE_DOOR, "andesite_door");
        folding(AllBlocks.COPPER_DOOR, "copper_door");
    }

    private static void sliding(SlidingDoorBlock block, String path) {
        Identifier model = PolymerBlockParts.model(path + "/block");
        PolymerBlockParts.<SlidingDoorBlockEntity>register(
                (be, state, time, out) -> slidingParts(model, state, be.animation.getValue(), out), block);
    }

    public static void movingParts(BlockState state, float value, List<PolymerBlockParts.Part> out) {
        if (!(state.getBlock() instanceof SlidingDoorBlock block)) return;
        String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
        if (block.isFoldingDoor())
            foldingParts(
                    PolymerBlockParts.model(path + "/fold_left"),
                    PolymerBlockParts.model(path + "/fold_right"),
                    state,
                    value,
                    out);
        else slidingParts(PolymerBlockParts.model(path + "/block"), state, value, out);
    }

    private static void slidingParts(
            Identifier model, BlockState state, float value, List<PolymerBlockParts.Part> out) {
        Direction facing = state.getValue(DoorBlock.FACING);
        boolean isLeft = state.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT;
        Vec3i facingVec = facing.getUnitVec3i();
        float scale = Mth.clamp(value * 10, 0, 1) * 0.03125f;
        Vec3i movementVec = (isLeft ? facing.getCounterClockWise() : facing.getClockWise()).getUnitVec3i();
        float movementScale = value * value * 0.8125f;
        Matrix4f pose = new Matrix4f()
                .translate(
                        movementVec.getX() * movementScale + facingVec.getX() * scale,
                        movementVec.getY() * movementScale + facingVec.getY() * scale + DOOR_OFFSET,
                        movementVec.getZ() * movementScale + facingVec.getZ() * scale)
                .mul(PolymerBlockParts.rotateToFace(facing));
        out.add(PolymerBlockParts.part(model, pose));
    }

    private static void folding(SlidingDoorBlock block, String path) {
        Identifier foldLeft = PolymerBlockParts.model(path + "/fold_left");
        Identifier foldRight = PolymerBlockParts.model(path + "/fold_right");
        PolymerBlockParts.<SlidingDoorBlockEntity>register(
                (be, state, time, out) -> foldingParts(foldLeft, foldRight, state, be.animation.getValue(), out),
                block);
    }

    private static void foldingParts(
            Identifier foldLeft,
            Identifier foldRight,
            BlockState state,
            float value,
            List<PolymerBlockParts.Part> out) {
        Direction facing = state.getValue(DoorBlock.FACING);
        boolean isLeft = state.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT;
        Vec3i facingVec = facing.getUnitVec3i();
        float scale = Mth.clamp(value * 10, 0, 1) * 0.03125f;
        float v = value * value * (isLeft ? 1 : -1);
        boolean flip = !isLeft;
        Identifier left = isLeft ? foldLeft : foldRight;
        Identifier right = flip ? foldLeft : foldRight;

        Matrix4f m = new Matrix4f()
                .translate(facingVec.getX() * scale, facingVec.getY() * scale + DOOR_OFFSET, facingVec.getZ() * scale)
                .mul(PolymerBlockParts.spin(Direction.UP, AngleHelper.horizontalAngle(facing.getClockWise())));
        if (flip) {
            if (v != 0) {
                m.translate(0, 0, 1).rotateY(Mth.DEG_TO_RAD * 91 * v).translate(0, 0, -0.5f);
            } else {
                m.translate(0, 0, 0.5f);
            }
            out.add(PolymerBlockParts.part(left, new Matrix4f(m)));
            m.rotateY(Mth.DEG_TO_RAD * -181 * v).translate(0, 0, -0.5f);
            out.add(PolymerBlockParts.part(right, m));
        } else {
            m.rotateY(Mth.DEG_TO_RAD * 91 * v);
            out.add(PolymerBlockParts.part(left, new Matrix4f(m)));
            m.translate(0, 0, 0.5f).rotateY(Mth.DEG_TO_RAD * -181 * v);
            out.add(PolymerBlockParts.part(right, m));
        }
    }
}
