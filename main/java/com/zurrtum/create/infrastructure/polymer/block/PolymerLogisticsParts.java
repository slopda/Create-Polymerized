package com.zurrtum.create.infrastructure.polymer.block;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.catnip.math.AngleHelper;
import com.zurrtum.create.content.kinetics.belt.BeltBlock;
import com.zurrtum.create.content.kinetics.belt.BeltBlockEntity;
import com.zurrtum.create.content.kinetics.belt.BeltHelper;
import com.zurrtum.create.content.kinetics.belt.BeltPart;
import com.zurrtum.create.content.kinetics.belt.BeltSlope;
import com.zurrtum.create.content.kinetics.belt.transport.BeltInventory;
import com.zurrtum.create.content.kinetics.belt.transport.TransportedItemStack;
import com.zurrtum.create.content.logistics.box.PackageItem;
import com.zurrtum.create.content.logistics.depot.DepotBehaviour;
import com.zurrtum.create.foundation.blockEntity.SmartBlockEntity;
import java.util.List;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

final class PolymerLogisticsParts {
    private static final float RAD_90 = Mth.HALF_PI;
    private static final Identifier BELT_PULLEY = PolymerBlockParts.model("belt_pulley");
    private static final Identifier BELT_START = PolymerBlockParts.model("belt/start");
    private static final Identifier BELT_MIDDLE = PolymerBlockParts.model("belt/middle");
    private static final Identifier BELT_END = PolymerBlockParts.model("belt/end");
    private static final Identifier BELT_START_BOTTOM = PolymerBlockParts.model("belt/start_bottom");
    private static final Identifier BELT_MIDDLE_BOTTOM = PolymerBlockParts.model("belt/middle_bottom");
    private static final Identifier BELT_END_BOTTOM = PolymerBlockParts.model("belt/end_bottom");
    private static final Identifier BELT_DIAGONAL_START = PolymerBlockParts.model("belt/diagonal_start");
    private static final Identifier BELT_DIAGONAL_MIDDLE = PolymerBlockParts.model("belt/diagonal_middle");
    private static final Identifier BELT_DIAGONAL_END = PolymerBlockParts.model("belt/diagonal_end");

    private PolymerLogisticsParts() {}

    static void registerAll() {
        PolymerBlockParts.<SmartBlockEntity>register(PolymerLogisticsParts::depot, AllBlocks.DEPOT);
        PolymerBlockParts.<BeltBlockEntity>register(PolymerLogisticsParts::belt, AllBlocks.BELT);
        BeltScroll.init();
    }

    private static void belt(BeltBlockEntity be, BlockState state, float time, List<PolymerBlockParts.Part> out) {
        BeltSlope slope = state.getValue(BeltBlock.SLOPE);
        BeltPart part = state.getValue(BeltBlock.PART);
        Direction facing = state.getValue(BeltBlock.HORIZONTAL_FACING);
        AxisDirection axisDirection = facing.getAxisDirection();
        boolean downward = slope == BeltSlope.DOWNWARD;
        boolean upward = slope == BeltSlope.UPWARD;
        boolean diagonal = downward || upward;
        boolean start = part == BeltPart.START;
        boolean end = part == BeltPart.END;
        boolean sideways = slope == BeltSlope.SIDEWAYS;
        if (downward || slope == BeltSlope.VERTICAL && axisDirection == AxisDirection.POSITIVE) {
            boolean b = start;
            start = end;
            end = b;
        }
        float degrees = AngleHelper.horizontalAngle(facing) + (upward ? 180 : 0) + (sideways ? 270 : 0);
        Matrix4f surface = new Matrix4f().translate(0.5f, 0.5f, 0.5f).rotateY(Mth.DEG_TO_RAD * degrees);
        if (sideways) {
            surface.rotateZ(RAD_90);
        }
        if (!diagonal && slope != BeltSlope.HORIZONTAL) {
            surface.rotateX(RAD_90);
        }
        surface.translate(-0.5f, -0.5f, -0.5f);
        float speed = be.getSpeed() * axisDirection.getStep();
        boolean alongX = facing.getAxis() == Axis.X;
        if (diagonal && downward ^ alongX
                || !sideways && !diagonal && alongX
                || sideways && axisDirection == AxisDirection.NEGATIVE) {
            speed = -speed;
        }
        out.add(PolymerBlockParts.part(
                        BeltScroll.model(
                                beltPartial(diagonal, start, end, false),
                                diagonal,
                                speed,
                                be.getLevel().getGameTime(),
                                be.color.orElse(null)),
                        surface)
                .identified("belt:top"));
        if (!diagonal) {
            out.add(PolymerBlockParts.part(
                            BeltScroll.model(
                                    beltPartial(false, start, end, true),
                                    false,
                                    speed,
                                    be.getLevel().getGameTime(),
                                    be.color.orElse(null)),
                            new Matrix4f(surface))
                    .identified("belt:bottom"));
        }

        if (be.hasPulley()) {
            Direction dir = sideways ? Direction.UP : facing.getClockWise();
            Axis axis = PolymerBlockParts.axisOf(state);
            float angle = PolymerBlockParts.progress(be.getSpeed(), time)
                    + PolymerBlockParts.rotationOffset(state, axis, be.getBlockPos());
            out.add(PolymerBlockParts.part(
                    BELT_PULLEY,
                    PolymerBlockParts.spin(Direction.get(AxisDirection.POSITIVE, axis), angle)
                            .mul(pulleyPose(dir))));
        }

        if (be.isController() && be.beltLength > 0) {
            BeltInventory inventory = be.getInventory();
            if (inventory != null) {
                for (TransportedItemStack transported : inventory.getTransportedItems()) {
                    beltItem(be, state, transported, out);
                }
            }
        }
    }

    private static Matrix4f pulleyPose(Direction facing) {
        Matrix4f pose = new Matrix4f().translate(0.5f, 0.5f, 0.5f);
        switch (facing.getAxis()) {
            case Y -> pose.rotateX(Mth.PI);
            case X -> pose.rotateY(RAD_90).rotateX(RAD_90);
            default -> pose.rotateX(RAD_90);
        }
        return pose.translate(-0.5f, -0.5f, -0.5f);
    }

    private static Identifier beltPartial(boolean diagonal, boolean start, boolean end, boolean bottom) {
        if (diagonal) {
            return start ? BELT_DIAGONAL_START : end ? BELT_DIAGONAL_END : BELT_DIAGONAL_MIDDLE;
        }
        if (bottom) {
            return start ? BELT_START_BOTTOM : end ? BELT_END_BOTTOM : BELT_MIDDLE_BOTTOM;
        }
        return start ? BELT_START : end ? BELT_END : BELT_MIDDLE;
    }

    private static void beltItem(
            BeltBlockEntity be, BlockState state, TransportedItemStack item, List<PolymerBlockParts.Part> out) {
        ItemStack stack = item.stack;
        if (stack == null || stack.isEmpty()) {
            return;
        }
        Direction beltFacing = be.getBeltFacing();
        Vec3i directionVec = beltFacing.getUnitVec3i();
        BeltSlope slope = state.getValue(BeltBlock.SLOPE);
        int verticality = slope == BeltSlope.DOWNWARD ? -1 : slope == BeltSlope.UPWARD ? 1 : 0;
        boolean slopeAlongX = beltFacing.getAxis() == Axis.X;
        Vec3 beltStartOffset = Vec3.atLowerCornerOf(directionVec).scale(-0.5).add(0.5, 15 / 16.0f, 0.5);

        float offset = item.beltPosition;
        float verticalMovement = offset < 0.5 ? 0 : verticality * (Math.min(offset, be.beltLength - 0.5f) - 0.5f);
        Vec3 offsetVec = Vec3.atLowerCornerOf(directionVec).scale(offset).add(0, verticalMovement, 0);
        boolean onSlope = slope != BeltSlope.HORIZONTAL && Mth.clamp(offset, 0.5f, be.beltLength - 0.5f) == offset;
        boolean tiltForward = (slope == BeltSlope.DOWNWARD ^ beltFacing.getAxisDirection() == AxisDirection.POSITIVE)
                == (beltFacing.getAxis() == Axis.Z);
        float slopeAngle = onSlope ? tiltForward ? -45 : 45 : 0;

        Matrix4f ms = new Matrix4f()
                .translate((float) beltStartOffset.x, (float) beltStartOffset.y, (float) beltStartOffset.z)
                .translate((float) offsetVec.x, (float) offsetVec.y, (float) offsetVec.z);
        boolean alongX = beltFacing.getClockWise().getAxis() == Axis.X;
        float sideOffset = alongX ? item.sideOffset : -item.sideOffset;
        ms.translate(alongX ? sideOffset : 0, 0, alongX ? 0 : sideOffset);

        boolean upright = BeltHelper.isItemUpright(stack);
        boolean blockItem = stack.getItem() instanceof BlockItem;
        boolean box = PackageItem.isPackage(stack);
        int count = Mth.log2(stack.getCount()) / 2;
        Random r = new Random(item.angle);
        if (!upright) {
            if (slopeAlongX) {
                ms.rotateZ(Mth.DEG_TO_RAD * slopeAngle);
            } else {
                ms.rotateX(Mth.DEG_TO_RAD * slopeAngle);
            }
        }
        if (onSlope) {
            ms.translate(0, 0.125f, 0);
        }
        if (upright) {
            ms.translate(0, 0.09375f, 0.0625f);
        }
        for (int i = 0; i <= count; i++) {
            Matrix4f layer = new Matrix4f(ms).rotateY(Mth.DEG_TO_RAD * item.angle);
            if (!blockItem && !upright) {
                layer.translate(0, -0.09375f, 0).rotateX(RAD_90);
            }
            if (blockItem && !box) {
                layer.translate(r.nextFloat() * 0.0625f * i, 0, r.nextFloat() * 0.0625f * i);
            }
            if (box) {
                layer.translate(0, 0.25f, 0).scale(1.5f);
            } else {
                layer.scale(0.5f);
            }
            out.add(PolymerBlockParts.itemPart(stack, layer, ItemDisplayContext.FIXED)
                    .identified(new BeltItemLayer(item, i)));
            if (!upright) {
                if (!blockItem) {
                    ms.rotateY(Mth.DEG_TO_RAD * 10);
                }
                ms.translate(0, blockItem ? 0.015625f : 0.0625f, 0);
            } else {
                ms.translate(0, 0, -0.0625f);
            }
        }
    }

    private record BeltItemLayer(TransportedItemStack item, int layer) {}

    private static void depot(SmartBlockEntity be, BlockState state, float time, List<PolymerBlockParts.Part> out) {
        DepotBehaviour behaviour = be.getBehaviour(DepotBehaviour.TYPE);
        if (behaviour == null) {
            return;
        }
        depotItems(behaviour, new Matrix4f(), out);
    }

    static void depotItems(DepotBehaviour behaviour, Matrix4f transform, List<PolymerBlockParts.Part> out) {
        if (behaviour == null) {
            return;
        }
        Matrix4f base = new Matrix4f(transform).translate(0.5f, 0.9375f, 0.5f);
        for (TransportedItemStack tis : behaviour.incoming) {
            transported(base, tis, out);
        }
        if (behaviour.heldItem != null) {
            transported(base, behaviour.heldItem, out);
        }
        int i = 0;
        for (ItemStack stack : behaviour.processingOutputBuffer) {
            if (stack.isEmpty()) {
                continue;
            }
            boolean upright = BeltHelper.isItemUpright(stack);
            Matrix4f pose = new Matrix4f(base)
                    .translate(0, i / 512f, 0)
                    .rotateY(Mth.DEG_TO_RAD * 45 * i)
                    .translate(0.35f, 0, 0);
            if (upright) {
                pose.rotateY(Mth.DEG_TO_RAD * -45 * i);
            }
            Random r = new Random(i + 1);
            int angle = (int) (360 * r.nextFloat());
            renderItem(pose, stack, upright ? angle + 90 : angle, upright, r, out);
            i++;
        }
    }

    private static void transported(Matrix4f base, TransportedItemStack tis, List<PolymerBlockParts.Part> out) {
        if (tis.stack == null || tis.stack.isEmpty()) {
            return;
        }
        Matrix4f pose = new Matrix4f(base);
        if (tis.insertedFrom.getAxis().isHorizontal()) {
            float sideOffset = tis.sideOffset;
            boolean alongX = tis.insertedFrom.getClockWise().getAxis() == Direction.Axis.X;
            if (!alongX) {
                sideOffset *= -1;
            }
            Vec3 offset = Vec3.atLowerCornerOf(tis.insertedFrom.getOpposite().getUnitVec3i())
                    .scale(0.5f - tis.beltPosition)
                    .add(alongX ? sideOffset : 0, 0, alongX ? 0 : sideOffset);
            pose.translate((float) offset.x, (float) offset.y, (float) offset.z);
        }
        renderItem(pose, tis.stack, tis.angle, BeltHelper.isItemUpright(tis.stack), new Random(0), out);
    }

    static void renderItem(
            Matrix4f origin, ItemStack stack, int angle, boolean upright, Random r, List<PolymerBlockParts.Part> out) {
        boolean blockItem = stack.getItem() instanceof BlockItem;
        boolean box = PackageItem.isPackage(stack);
        int count = Mth.log2(stack.getCount()) / 2;
        Matrix4f ms = new Matrix4f(origin).rotateY(Mth.DEG_TO_RAD * angle);
        if (upright) {
            ms.translate(0, 0.09375f, -0.0625f);
        }
        for (int i = 0; i <= count; i++) {
            Matrix4f layer = new Matrix4f(ms);
            if (blockItem) {
                layer.translate(r.nextFloat() * 0.0625f * i, 0, r.nextFloat() * 0.0625f * i);
            }
            if (box) {
                layer.translate(0, 0.25f, 0).scale(1.5f);
            } else {
                layer.scale(0.5f);
            }
            if (!blockItem && !upright) {
                layer.translate(0, -0.1875f, 0).rotateX(RAD_90);
            }
            out.add(PolymerBlockParts.itemPart(stack, layer, ItemDisplayContext.FIXED));
            if (!upright) {
                if (!blockItem) {
                    ms.rotateY(Mth.DEG_TO_RAD * 10);
                }
                ms.translate(0, blockItem ? 0.015625f : 0.0625f, 0);
            } else {
                ms.translate(0, 0, -0.0625f);
            }
        }
    }
}
