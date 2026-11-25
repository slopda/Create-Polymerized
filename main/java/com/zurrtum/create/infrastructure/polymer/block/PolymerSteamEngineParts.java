package com.zurrtum.create.infrastructure.polymer.block;

import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.part;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.catnip.animation.LerpedFloat;
import com.zurrtum.create.catnip.animation.LerpedFloat.Chaser;
import com.zurrtum.create.catnip.math.AngleHelper;
import com.zurrtum.create.content.fluids.tank.BoilerData;
import com.zurrtum.create.content.fluids.tank.FluidTankBlockEntity;
import com.zurrtum.create.content.kinetics.base.IRotate;
import com.zurrtum.create.content.kinetics.steamEngine.PoweredShaftBlockEntity;
import com.zurrtum.create.content.kinetics.steamEngine.SteamEngineBlock;
import com.zurrtum.create.content.kinetics.steamEngine.SteamEngineBlockEntity;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

final class PolymerSteamEngineParts {
    private static final Identifier PISTON = PolymerBlockParts.model("steam_engine/piston"),
            LINKAGE = PolymerBlockParts.model("steam_engine/linkage"),
            CONNECTOR = PolymerBlockParts.model("steam_engine/shaft_connector"),
            GAUGE = PolymerBlockParts.model("steam_engine/gauge"),
            DIAL = PolymerBlockParts.model("steam_engine/gauge_dial");
    private static final Direction[] GAUGE_SIDES = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
    private static final Map<FluidTankBlockEntity, Boiler> BOILERS = new WeakHashMap<>();

    private static final class Boiler {
        final LerpedFloat gauge = LerpedFloat.linear();
        final boolean[] occluded = {true, true, true, true};
        long ticked = Long.MIN_VALUE, occlusionChecked = Long.MIN_VALUE;
    }

    private PolymerSteamEngineParts() {}

    static void register() {
        PolymerBlockParts.<SteamEngineBlockEntity>register(PolymerSteamEngineParts::engine, AllBlocks.STEAM_ENGINE);
    }

    private static void engine(
            SteamEngineBlockEntity be, BlockState state, float time, List<PolymerBlockParts.Part> out) {
        PoweredShaftBlockEntity shaft = be.getShaft();
        if (shaft == null) return;
        Direction facing = SteamEngineBlock.getFacing(state);
        Axis facingAxis = facing.getAxis();
        BlockState shaftState = shaft.getBlockState();
        Axis axis = ((IRotate) shaftState.getBlock()).getRotationAxis(shaftState);
        if (axis == facingAxis) return;
        float offset = PolymerBlockParts.rotationOffset(shaftState, axis, shaft.getBlockPos())
                + shaft.getRotationAngleOffset(axis);
        float angle = (PolymerBlockParts.progress(shaft.getSpeed(), time) + offset) % 360 * Mth.DEG_TO_RAD;
        Matrix4f[] poses = poses(facing, axis, angle);
        out.add(part(PISTON, poses[0]).identified("engine:piston"));
        out.add(part(LINKAGE, poses[1]).identified("engine:linkage"));
        out.add(part(CONNECTOR, poses[2]).identified("engine:connector"));
    }

    static Matrix4f[] poses(Direction facing, Axis axis, float angle) {
        Axis facingAxis = facing.getAxis();
        Quaternionf roll = facingAxis.isHorizontal() && axis == Axis.Y || facingAxis.isVertical() && axis == Axis.Z
                ? new Quaternionf().rotationY(Mth.DEG_TO_RAD * -90)
                : null;
        if (axis.isHorizontal() && facingAxis == Axis.X ^ facing.getAxisDirection() == AxisDirection.POSITIVE)
            angle *= -1;
        if (axis == Axis.X && facing == Direction.DOWN) angle *= -1;
        float sinAngle = Mth.sin(angle), cosAngle = Mth.cos(angle);
        float piston = 0.375f * sinAngle - Mth.sqrt(Mth.square(0.875f) - Mth.square(0.375f) * Mth.square(cosAngle));
        float distance = Mth.sqrt(Mth.square(piston - 0.375f * sinAngle)) / 0.875f;
        Quaternionf linkageRotate =
                new Quaternionf().rotationX((float) (cosAngle >= 0 ? Math.acos(distance) : -Math.acos(distance)));
        Quaternionf connectorRotate = new Quaternionf().rotationX(-(angle + Mth.HALF_PI));

        Matrix4f base = new Matrix4f().translate(0.5f, 0.5f, 0.5f);
        float yRot = AngleHelper.horizontalAngle(facing), xRot = AngleHelper.verticalAngle(facing) + 90;
        if (yRot != 0) base.rotate(new Quaternionf().rotationY(Mth.DEG_TO_RAD * yRot));
        if (xRot != 0) base.rotate(new Quaternionf().rotationX(Mth.DEG_TO_RAD * xRot));
        if (roll != null) base.rotate(roll);
        Matrix4f pistonPose = new Matrix4f(base).translate(-0.5f, piston + 0.75f, -0.5f);
        Matrix4f linkagePose = new Matrix4f(pistonPose).translate(0, 1, 0).rotateAround(linkageRotate, 0, 0.25f, 0.5f);
        Matrix4f connectorPose =
                new Matrix4f(base).translate(-0.5f, 1.5f, -0.5f).rotateAround(connectorRotate, 0.5f, 0.5f, 0.5f);
        return new Matrix4f[] {pistonPose, linkagePose, connectorPose};
    }

    static void boiler(FluidTankBlockEntity be, List<PolymerBlockParts.Part> out) {
        if (!be.isController() || be.window || !be.boiler.isActive()) {
            BOILERS.remove(be);
            return;
        }
        Level level = be.getLevel();
        if (level == null) return;
        Boiler boiler = BOILERS.computeIfAbsent(be, unused -> new Boiler());
        long now = level.getGameTime();
        BoilerData data = be.boiler;
        if (boiler.ticked != now) {
            boiler.ticked = now;
            int size = be.getTotalTankSize();
            int forSize = data.getMaxHeatLevelForBoilerSize(size);
            int actual = Math.min(data.activeHeat, Math.min(data.getMaxHeatLevelForWaterSupply(), forSize));
            float target = data.isPassive(size) ? 1 / 8.0f : forSize == 0 ? 0 : actual / (float) forSize;
            boiler.gauge.chase(target, 0.125f, Chaser.EXP);
            boiler.gauge.tickChaser();
        }
        if (now - boiler.occlusionChecked >= 20) {
            boiler.occlusionChecked = now;
            occlusion(be, boiler.occluded);
        }
        boolean[] occluded = boiler.occluded;
        if (occluded[0] && occluded[1] && occluded[2] && occluded[3]) return;
        float half = be.getWidth() / 2.0f;
        float dial = -145 * boiler.gauge.getValue() + 90;
        for (int i = 0; i < 4; i++) {
            if (occluded[i]) continue;
            Matrix4f gauge = new Matrix4f()
                    .translate(half, 0.5f, half)
                    .rotateY(Mth.DEG_TO_RAD * -90 * (i + 1))
                    .translate(half - 0.875f, -0.5f, -0.5f);
            out.add(part(GAUGE, gauge).identified("boiler:gauge:" + GAUGE_SIDES[i]));
            Matrix4f needle = new Matrix4f(gauge);
            if (dial != 0) needle.rotateAround(new Quaternionf().rotationX(Mth.DEG_TO_RAD * dial), 0, 0.375f, 0.5f);
            out.add(part(DIAL, needle).identified("boiler:dial:" + GAUGE_SIDES[i]));
        }
    }

    private static void occlusion(FluidTankBlockEntity controller, boolean[] occluded) {
        Level level = controller.getLevel();
        float width = controller.getWidth();
        for (Direction d : GAUGE_SIDES) {
            AABB aabb = new AABB(controller.getBlockPos())
                    .move(width / 2.0f - 0.5f, 0, width / 2.0f - 0.5f)
                    .deflate(5.0f / 8);
            aabb = aabb.move(d.getStepX() * (width / 2.0f + 1 / 4.0f), 0, d.getStepZ() * (width / 2.0f + 1 / 4.0f));
            aabb = aabb.inflate(Math.abs(d.getStepZ()) / 2.0f, 0.25f, Math.abs(d.getStepX()) / 2.0f);
            occluded[d.get2DDataValue()] = !level.noCollision(aabb);
        }
    }
}
