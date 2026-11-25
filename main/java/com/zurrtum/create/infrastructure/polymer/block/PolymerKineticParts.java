package com.zurrtum.create.infrastructure.polymer.block;

import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.axisOf;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.model;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.part;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.progress;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.rotateCentered;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.rotateToFace;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.rotateToFaceVertical;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.rotationOffset;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.spin;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.content.kinetics.base.KineticBlockEntity;
import com.zurrtum.create.content.kinetics.crank.HandCrankBlockEntity;
import com.zurrtum.create.content.kinetics.gauge.GaugeBlock;
import com.zurrtum.create.content.kinetics.gauge.GaugeBlockEntity;
import com.zurrtum.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.zurrtum.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.zurrtum.create.content.kinetics.press.PressingBehaviour;
import com.zurrtum.create.content.kinetics.transmission.SplitShaftBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

final class PolymerKineticParts {
    private static final Identifier SHAFT = model("shaft");
    private static final Identifier SHAFT_HALF = model("shaft_half");
    private static final Identifier ENCASED_SHAFT = model("encased_shaft/shaft");
    private static final Identifier ENCASED_FAN_INNER = model("encased_fan/propeller");
    private static final Identifier MILLSTONE_COG = model("millstone/inner");
    private static final Identifier DRILL_HEAD = model("mechanical_drill/head");
    private static final Identifier PRESS_HEAD = model("mechanical_press/head");
    private static final Identifier MIXER_POLE = model("mechanical_mixer/pole");
    private static final Identifier MIXER_HEAD = model("mechanical_mixer/head");
    private static final Identifier MIXER_COG = model("polymer/cogwheel_shaftless");
    private static final Identifier HAND_CRANK_BASE = model("hand_crank/block");
    private static final Identifier HAND_CRANK_HANDLE = PolymerBlockParts.continuous(model("hand_crank/handle"));
    private static final Identifier GAUGE_HEAD_SPEED = model("gauge/speedometer/head");
    private static final Identifier GAUGE_HEAD_STRESS = model("gauge/stressometer/head");
    private static final Identifier GAUGE_DIAL = model("gauge/dial");
    private static final Identifier TURNTABLE = model("turntable");

    private PolymerKineticParts() {}

    static void registerAll() {
        PolymerBacktankParts.register();
        PolymerBlockParts.<KineticBlockEntity>register(
                (be, state, time, out) -> out.add(fullShaft(be, state, time)),
                AllBlocks.ROTATION_SPEED_CONTROLLER,
                AllBlocks.ADJUSTABLE_CHAIN_GEARSHIFT,
                AllBlocks.MECHANICAL_PISTON,
                AllBlocks.STICKY_MECHANICAL_PISTON);

        PolymerBlockParts.<KineticBlockEntity>register(
                (be, state, time, out) -> {
                    Axis axis = axisOf(state);
                    out.add(part(
                            ENCASED_SHAFT,
                            spin(axis, angle(be, state, axis, time)).mul(rotateToFaceVertical(axis.getPositive()))));
                },
                AllBlocks.ANDESITE_ENCASED_SHAFT,
                AllBlocks.BRASS_ENCASED_SHAFT,
                AllBlocks.METAL_GIRDER_ENCASED_SHAFT);

        PolymerBlockParts.<KineticBlockEntity>register(
                (be, state, time, out) -> {
                    Direction facing = state.getValue(BlockStateProperties.FACING);
                    out.add(part(
                            SHAFT_HALF,
                            spin(facing.getAxis(), angle(be, state, facing.getAxis(), time))
                                    .mul(rotateToFace(facing))));
                },
                AllBlocks.CREATIVE_MOTOR);

        PolymerBlockParts.<SplitShaftBlockEntity>register(
                (be, state, time, out) -> {
                    Axis axis = axisOf(state);
                    Direction direction = axis.getPositive();
                    float offset = rotationOffset(state, axis, be.getBlockPos());
                    float progress = progress(be.getSpeed(), time);
                    Direction bottom = direction.getOpposite();
                    out.add(part(
                            SHAFT_HALF,
                            spin(direction, progress * be.getRotationSpeedModifier(direction) + offset)
                                    .mul(rotateToFace(direction))));
                    out.add(part(
                            SHAFT_HALF,
                            spin(direction, progress * be.getRotationSpeedModifier(bottom) + offset)
                                    .mul(rotateToFace(bottom))));
                },
                AllBlocks.CLUTCH,
                AllBlocks.GEARSHIFT,
                AllBlocks.SEQUENCED_GEARSHIFT);

        PolymerBlockParts.<KineticBlockEntity>register(PolymerKineticParts::gearbox, AllBlocks.GEARBOX);

        PolymerBlockParts.<KineticBlockEntity>register(
                (be, state, time, out) -> {
                    Direction facing = state.getValue(BlockStateProperties.FACING);
                    Direction opposite = facing.getOpposite();
                    Axis axis = facing.getAxis();
                    Direction direction = axis.getPositive();
                    float offset = rotationOffset(state, axis, be.getBlockPos());
                    float speed = be.getSpeed();
                    out.add(part(
                            SHAFT_HALF,
                            spin(direction, progress(speed, time) + offset).mul(rotateToFace(opposite))));
                    float fanSpeed = speed * 5;
                    if (fanSpeed > 0) {
                        fanSpeed = Mth.clamp(fanSpeed, 80, 1280);
                    } else if (fanSpeed < 0) {
                        fanSpeed = Mth.clamp(fanSpeed, -1280, -80);
                    }
                    out.add(part(
                            ENCASED_FAN_INNER,
                            spin(direction, progress(fanSpeed, time) % 360 + offset)
                                    .mul(rotateToFace(opposite))));
                },
                AllBlocks.ENCASED_FAN);

        PolymerBlockParts.<KineticBlockEntity>register(
                (be, state, time, out) -> {
                    Axis axis = axisOf(state);
                    out.add(part(MILLSTONE_COG, spin(axis, angle(be, state, axis, time))));
                },
                AllBlocks.MILLSTONE);

        PolymerBlockParts.<KineticBlockEntity>register(
                (be, state, time, out) -> {
                    Direction facing = state.getValue(BlockStateProperties.FACING);
                    out.add(part(
                            DRILL_HEAD,
                            spin(facing.getAxis(), angle(be, state, facing.getAxis(), time))
                                    .mul(rotateToFace(facing))));
                },
                AllBlocks.MECHANICAL_DRILL);

        PolymerBlockParts.<KineticBlockEntity>register(
                (be, state, time, out) -> {
                    Axis axis = axisOf(state);
                    out.add(part(
                            TURNTABLE,
                            spin(axis, angle(be, state, axis, time)).mul(rotateToFaceVertical(axis.getPositive()))));
                    PolymerGroundSkin.collect(be, state, out);
                },
                AllBlocks.TURNTABLE);

        PolymerBlockParts.<MechanicalPressBlockEntity>register(
                (be, state, time, out) -> {
                    out.add(fullShaft(be, state, time));
                    PressingBehaviour pressing = be.getPressingBehaviour();
                    float offset = -(pressing.getRenderedHeadOffset(0) * pressing.mode.headOffset);
                    Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
                    out.add(part(
                            PRESS_HEAD, new Matrix4f().translate(0, offset, 0).mul(rotateToFace(facing))));
                },
                AllBlocks.MECHANICAL_PRESS);

        PolymerBlockParts.<MechanicalMixerBlockEntity>register(
                (be, state, time, out) -> {
                    Axis axis = axisOf(state);
                    out.add(part(MIXER_COG, spin(axis, angle(be, state, axis, time))));
                    float headOffset = mixerHeadOffset(be);
                    Matrix4f lowered = new Matrix4f().translate(0, headOffset, 0);
                    out.add(part(MIXER_POLE, lowered));
                    float headSpeed =
                            be.running ? be.getSpeed() * (be.runningTicks >= 15 && be.runningTicks <= 20 ? 3 : 1) : 0;
                    out.add(part(MIXER_HEAD, new Matrix4f(lowered).mul(spin(Direction.UP, progress(headSpeed, time)))));
                },
                AllBlocks.MECHANICAL_MIXER);

        PolymerBlockParts.<HandCrankBlockEntity>register(
                (be, state, time, out) -> {
                    Direction facing = state.getValue(BlockStateProperties.FACING);
                    Axis axis = facing.getAxis();
                    Direction direction = axis.getPositive();
                    out.add(part(
                            HAND_CRANK_BASE,
                            spin(direction, angle(be, state, axis, time)).mul(rotateToFaceVertical(facing))));
                    out.add(part(
                            HAND_CRANK_HANDLE,
                            spin(direction, be.independentAngle).mul(rotateToFace(facing.getOpposite()))));
                },
                AllBlocks.HAND_CRANK);

        PolymerBlockParts.<GaugeBlockEntity>register(
                (be, state, time, out) -> gauge(be, state, time, out, GAUGE_HEAD_SPEED), AllBlocks.SPEEDOMETER);
        PolymerBlockParts.<GaugeBlockEntity>register(
                (be, state, time, out) -> gauge(be, state, time, out, GAUGE_HEAD_STRESS), AllBlocks.STRESSOMETER);
    }

    private static float angle(KineticBlockEntity be, BlockState state, Axis axis, float time) {
        return progress(be.getSpeed(), time) + rotationOffset(state, axis, be.getBlockPos());
    }

    private static PolymerBlockParts.Part fullShaft(KineticBlockEntity be, BlockState state, float time) {
        Axis axis = axisOf(state);
        return part(SHAFT, spin(axis, angle(be, state, axis, time)).mul(rotateToFaceVertical(axis.getPositive())));
    }

    private static void gearbox(KineticBlockEntity be, BlockState state, float time, List<PolymerBlockParts.Part> out) {
        Axis axis = state.getValue(BlockStateProperties.AXIS);
        float speed = be.getSpeed();
        boolean flag = true;
        if (speed != 0 && be.source != null) {
            BlockPos sourcePos = be.source.subtract(be.getBlockPos());
            Direction source = Direction.getApproximateNearest(sourcePos.getX(), sourcePos.getY(), sourcePos.getZ());
            Axis sourceAxis = source.getAxis();
            boolean positive = source.getAxisDirection() == AxisDirection.POSITIVE;
            flag = axis == Axis.Y ? (sourceAxis == Axis.Z) == positive : (sourceAxis == Axis.Y) == positive;
        }
        float angle = progress(speed, time) % 360;
        Direction first = axis == Axis.Y ? Direction.SOUTH : Direction.UP;
        Direction second = axis == Axis.Z ? Direction.EAST : axis == Axis.Y ? Direction.EAST : Direction.SOUTH;
        gearboxPair(state, be.getBlockPos(), first, angle, flag, false, out);
        gearboxPair(state, be.getBlockPos(), second, angle, flag, true, out);
    }

    private static void gearboxPair(
            BlockState state,
            BlockPos pos,
            Direction direction,
            float angle,
            boolean flag,
            boolean second,
            List<PolymerBlockParts.Part> out) {
        float offset = rotationOffset(state, direction.getAxis(), pos);
        boolean forward = flag != second;
        float near = forward ? angle + offset : offset - angle;
        float far = forward ? offset - angle : angle + offset;
        out.add(part(SHAFT_HALF, spin(direction, near).mul(rotateToFace(direction))));
        out.add(part(SHAFT_HALF, spin(direction, far).mul(rotateToFace(direction.getOpposite()))));
    }

    private static float mixerHeadOffset(MechanicalMixerBlockEntity be) {
        if (be.running) {
            if (be.runningTicks == 20) {
                return -1.4375f;
            }
            float runningTicks = be.runningTicks;
            if (runningTicks < 20) {
                return Mth.cos(runningTicks * 0.05f * Mth.PI) / 2 - 0.9375f;
            }
            return Mth.cos((41 - runningTicks) * 0.05f * Mth.PI) / 2 - 0.9375f;
        }
        return -0.4375f;
    }

    private static void gauge(
            GaugeBlockEntity be, BlockState state, float time, List<PolymerBlockParts.Part> out, Identifier head) {
        out.add(fullShaft(be, state, time));
        if (!(state.getBlock() instanceof GaugeBlock block)) {
            return;
        }
        float dialPivot = 5.75f / 16;
        Matrix4f dial = new Matrix4f()
                .translate(0, dialPivot, dialPivot)
                .rotate(new Quaternionf().setAngleAxis(Math.PI / 2 * -be.dialState, 1, 0, 0))
                .translate(0, -dialPivot, -dialPivot);
        for (Direction face : Direction.Plane.HORIZONTAL) {
            if (!block.shouldRenderHeadOnFace(be.getLevel(), be.getBlockPos(), state, face)) {
                continue;
            }
            Matrix4f faceYaw = rotateCentered(new Quaternionf().rotationY(Mth.DEG_TO_RAD * (-face.toYRot() - 90)));
            out.add(part(head, faceYaw));
            out.add(part(GAUGE_DIAL, new Matrix4f(faceYaw).mul(dial)));
        }
    }
}
