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
import com.zurrtum.create.catnip.math.AngleHelper;
import com.zurrtum.create.content.contraptions.AbstractContraptionEntity;
import com.zurrtum.create.content.contraptions.actors.harvester.HarvesterBlock;
import com.zurrtum.create.content.contraptions.actors.harvester.HarvesterBlockEntity;
import com.zurrtum.create.content.contraptions.elevator.ElevatorPulleyBlock;
import com.zurrtum.create.content.contraptions.elevator.ElevatorPulleyBlockEntity;
import com.zurrtum.create.content.contraptions.pulley.PulleyBlock;
import com.zurrtum.create.content.contraptions.pulley.PulleyBlockEntity;
import com.zurrtum.create.content.contraptions.pulley.PulleyContraption;
import com.zurrtum.create.content.fluids.hosePulley.HosePulleyBlock;
import com.zurrtum.create.content.fluids.hosePulley.HosePulleyBlockEntity;
import com.zurrtum.create.content.fluids.pipes.valve.FluidValveBlock;
import com.zurrtum.create.content.fluids.pipes.valve.FluidValveBlockEntity;
import com.zurrtum.create.content.kinetics.base.KineticBlockEntity;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.Part;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

final class PolymerPulleyParts {
    private static final int MAX_SEGMENTS = 64;
    private static final float PARTIAL = 0.5f;

    private static final Identifier ROPE_COIL = model("rope_pulley/rope_coil");
    private static final Identifier ROPE = model("rope_pulley/rope");
    private static final Identifier ROPE_HALF = model("rope_pulley/rope_half");
    private static final Identifier ROPE_HALF_MAGNET = model("rope_pulley/rope_half_magnet");
    private static final Identifier ROPE_MAGNET = model("rope_pulley/pulley_magnet");

    private static final Identifier HOSE_COIL = model("hose_pulley/hose_coil");
    private static final Identifier HOSE = model("hose_pulley/rope");
    private static final Identifier HOSE_HALF = model("hose_pulley/rope_half");
    private static final Identifier HOSE_HALF_MAGNET = model("hose_pulley/rope_half_magnet");
    private static final Identifier HOSE_MAGNET = model("hose_pulley/pulley_magnet");

    private static final Identifier ELEVATOR_COIL = model("elevator_pulley/rope_coil");
    private static final Identifier ELEVATOR_MAGNET = model("elevator_pulley/pulley_magnet");
    private static final Identifier ELEVATOR_BELT = model("elevator_pulley/rope");
    private static final Identifier ELEVATOR_BELT_HALF = model("elevator_pulley/rope_half");

    private static final Identifier SHAFT = model("shaft");
    private static final Identifier HARVESTER_BLADE = model("mechanical_harvester/blade");
    private static final Identifier FLUID_VALVE_POINTER = model("fluid_valve/pointer");

    private record PulleyModels(
            Identifier coil, Identifier rope, Identifier halfRope, Identifier magnet, Identifier halfMagnet) {}

    private static final PulleyModels ROPE_MODELS =
            new PulleyModels(ROPE_COIL, ROPE, ROPE_HALF, ROPE_MAGNET, ROPE_HALF_MAGNET);
    private static final PulleyModels HOSE_MODELS =
            new PulleyModels(HOSE_COIL, HOSE, HOSE_HALF, HOSE_MAGNET, HOSE_HALF_MAGNET);

    private PolymerPulleyParts() {}

    static void registerAll() {
        PolymerBlockParts.<PulleyBlockEntity>register(
                (be, state, time, out) -> pulley(
                        be,
                        state,
                        time,
                        out,
                        state.getValue(PulleyBlock.HORIZONTAL_AXIS),
                        pulleyOffset(be),
                        isPulleyRunning(be),
                        ROPE_MODELS),
                AllBlocks.ROPE_PULLEY);

        PolymerBlockParts.<HosePulleyBlockEntity>register(
                (be, state, time, out) -> pulley(
                        be,
                        state,
                        time,
                        out,
                        state.getValue(HosePulleyBlock.HORIZONTAL_FACING)
                                .getClockWise()
                                .getAxis(),
                        be.getInterpolatedOffset(PARTIAL),
                        true,
                        HOSE_MODELS),
                AllBlocks.HOSE_PULLEY);

        PolymerBlockParts.<ElevatorPulleyBlockEntity>register(PolymerPulleyParts::elevator, AllBlocks.ELEVATOR_PULLEY);

        PolymerBlockParts.<HarvesterBlockEntity>register(PolymerPulleyParts::harvester, AllBlocks.MECHANICAL_HARVESTER);

        PolymerBlockParts.<FluidValveBlockEntity>register(PolymerPulleyParts::valve, AllBlocks.FLUID_VALVE);
    }

    private static void pulley(
            KineticBlockEntity be,
            BlockState state,
            float time,
            List<Part> out,
            Axis axis,
            float offset,
            boolean running,
            PulleyModels m) {
        Direction direction = axis.getPositive();
        out.add(shaft(be, state, time, axis));
        out.add(part(m.coil(), rotateToFace(direction)));
        if (running || offset == 0) {
            Identifier magnet = offset > 0.25f ? m.magnet() : m.halfMagnet();
            out.add(part(magnet, translateY(-offset)));
        }
        if (offset > 0.75f) {
            float f = offset % 1;
            if (f < 0.25f || f > 0.75f) {
                float down = f > 0.75f ? f - 1 : f;
                out.add(part(m.halfRope(), translateY(-down)));
            }
        }
        if (!running || offset <= 1.25f) {
            return;
        }
        int size = Math.min((int) Math.ceil(offset - 1.25f), MAX_SEGMENTS);
        for (int i = 0; i < size; i++) {
            out.add(part(m.rope(), translateY(-offset + i + 1)));
        }
    }

    private static void elevator(ElevatorPulleyBlockEntity be, BlockState state, float time, List<Part> out) {
        Direction facing = state.getValue(ElevatorPulleyBlock.HORIZONTAL_FACING);
        Axis axis = facing.getClockWise().getAxis();
        out.add(shaft(be, state, time, axis));
        float offset = pulleyOffset(be);
        boolean running = isPulleyRunning(be);
        Matrix4f yRot = rotateCentered(
                new Quaternionf().rotationY(Mth.DEG_TO_RAD * (180 + AngleHelper.horizontalAngle(facing))));
        if (running || offset == 0) {
            out.add(part(ELEVATOR_MAGNET, new Matrix4f(yRot).mul(translateY(-offset))));
        }
        out.add(part(ELEVATOR_COIL, rotateToFace(facing)));
        if (offset == 0) {
            return;
        }
        float f = offset % 1;
        if (f < 0.25f || f > 0.75f) {
            float down = f > 0.75f ? f - 1 : f;
            out.add(part(ELEVATOR_BELT_HALF, new Matrix4f(yRot).mul(translateY(-down))));
        }
        if (!running || offset <= 0.25f) {
            return;
        }
        int size = Math.min((int) Math.ceil(offset - 0.25f), MAX_SEGMENTS);
        for (int i = 0; i < size; i++) {
            out.add(part(ELEVATOR_BELT, new Matrix4f(yRot).mul(translateY(-offset + i))));
        }
    }

    private static void harvester(HarvesterBlockEntity be, BlockState state, float time, List<Part> out) {
        Direction facing = state.getValue(HarvesterBlock.FACING);
        float bladeAngle = (float) Math.IEEEremainder((double) time / 20 * be.getAnimatedSpeed(), 360);
        Matrix4f blade = new Matrix4f()
                .translate(0, 0.375f, 0.5625f)
                .rotate(new Quaternionf().rotationX(Mth.DEG_TO_RAD * -bladeAngle))
                .translate(0, -0.375f, -0.5625f);
        out.add(part(
                HARVESTER_BLADE,
                spin(Direction.UP, AngleHelper.horizontalAngle(facing)).mul(blade)));
    }

    private static void valve(FluidValveBlockEntity be, BlockState state, float time, List<Part> out) {
        Axis shaftAxis = axisOf(state);
        out.add(shaft(be, state, time, shaftAxis));
        Direction facing = state.getValue(FluidValveBlock.FACING);
        Axis pipeAxis = FluidValveBlock.getPipeAxis(state);
        float pointerRotation = Mth.lerp(be.pointer.getValue(1), 0, -90);
        float pointerYaw = (pipeAxis.isHorizontal() && shaftAxis == Axis.X) || pipeAxis.isVertical()
                ? 90 + pointerRotation
                : pointerRotation;
        Matrix4f pointer = new Matrix4f()
                .translate(0.5f, 0.5f, 0.5f)
                .rotateY(Mth.DEG_TO_RAD * AngleHelper.horizontalAngle(facing));
        if (facing != Direction.UP) {
            pointer.rotateX(facing == Direction.DOWN ? (float) Math.PI : (float) (Math.PI / 2));
        }
        pointer.rotateY(Mth.DEG_TO_RAD * pointerYaw).translate(-0.5f, -0.5f, -0.5f);
        out.add(part(FLUID_VALVE_POINTER, pointer));
    }

    private static Part shaft(KineticBlockEntity be, BlockState state, float time, Axis axis) {
        float angle = progress(be.getSpeed(), time) + rotationOffset(state, axis, be.getBlockPos());
        return part(SHAFT, spin(axis, angle).mul(rotateToFaceVertical(axis.getPositive())));
    }

    private static float pulleyOffset(PulleyBlockEntity be) {
        float offset = be.getInterpolatedOffset(PARTIAL);
        AbstractContraptionEntity attached = be.getAttachedContraption();
        if (attached != null) {
            PulleyContraption c = (PulleyContraption) attached.getContraption();
            offset = (float) -(attached.getY() - c.anchor.getY() - c.getInitialOffset());
        }
        return offset;
    }

    private static boolean isPulleyRunning(PulleyBlockEntity be) {
        return be.running || be.getMirrorParent() != null || be.isVirtual();
    }

    private static Matrix4f translateY(float y) {
        return new Matrix4f().translate(0, y, 0);
    }
}
