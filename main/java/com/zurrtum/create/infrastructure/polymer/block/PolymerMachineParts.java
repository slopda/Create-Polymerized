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
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.catnip.math.AngleHelper;
import com.zurrtum.create.content.contraptions.bearing.IBearingBlockEntity;
import com.zurrtum.create.content.fluids.pump.PumpBlockEntity;
import com.zurrtum.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.zurrtum.create.content.kinetics.base.KineticBlockEntity;
import com.zurrtum.create.content.kinetics.deployer.DeployerBlockEntity;
import com.zurrtum.create.content.kinetics.deployer.DeployerBlockEntity.Mode;
import com.zurrtum.create.content.kinetics.deployer.DeployerBlockEntity.State;
import com.zurrtum.create.content.kinetics.mechanicalArm.ArmAngleTarget;
import com.zurrtum.create.content.kinetics.mechanicalArm.ArmBlock;
import com.zurrtum.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.zurrtum.create.content.kinetics.mechanicalArm.ArmBlockEntity.Phase;
import com.zurrtum.create.content.kinetics.saw.SawBlockEntity;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerBeltPlayers;
import com.zurrtum.create.infrastructure.polymer.equipment.PolymerSymmetryWand;
import com.zurrtum.create.infrastructure.polymer.interaction.PolymerArmTargets;
import com.zurrtum.create.infrastructure.polymer.interaction.PolymerBeltConnectorPreview;
import com.zurrtum.create.infrastructure.polymer.interaction.PolymerEjectorTargets;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

final class PolymerMachineParts {
    private static final Identifier SHAFT = model("shaft");
    private static final Identifier SHAFT_HALF = model("shaft_half");
    private static final Identifier BEARING_TOP = model("bearing/top");
    private static final Identifier BEARING_TOP_WOODEN = model("bearing/top_wooden");
    private static final Identifier SAW_BLADE_VERTICAL_ACTIVE = model("mechanical_saw/blade_vertical_active");
    private static final Identifier SAW_BLADE_VERTICAL_INACTIVE = model("mechanical_saw/blade_vertical_inactive");
    private static final Identifier SAW_BLADE_VERTICAL_REVERSED = model("mechanical_saw/blade_vertical_reversed");
    private static final Identifier SAW_BLADE_HORIZONTAL_ACTIVE = model("mechanical_saw/blade_horizontal_active");
    private static final Identifier SAW_BLADE_HORIZONTAL_INACTIVE = model("mechanical_saw/blade_horizontal_inactive");
    private static final Identifier SAW_BLADE_HORIZONTAL_REVERSED = model("mechanical_saw/blade_horizontal_reversed");
    private static final Identifier DEPLOYER_POLE = model("deployer/pole");
    private static final Identifier DEPLOYER_HAND_POINTING = model("deployer/hand_pointing");
    private static final Identifier DEPLOYER_HAND_PUNCHING = model("deployer/hand_punching");
    private static final Identifier DEPLOYER_HAND_HOLDING = model("deployer/hand_holding");
    private static final Identifier ARM_COG = model("mechanical_arm/cog");
    private static final Identifier ARM_BASE = model("mechanical_arm/base");
    private static final Identifier ARM_LOWER_BODY = model("mechanical_arm/lower_body");
    private static final Identifier ARM_UPPER_BODY = model("mechanical_arm/upper_body");
    private static final Identifier ARM_CLAW_BASE = model("mechanical_arm/claw_base");
    private static final Identifier ARM_CLAW_BASE_GOGGLES = model("mechanical_arm/claw_base_goggles");
    private static final Identifier ARM_CLAW_GRIP_UPPER = model("mechanical_arm/upper_claw_grip");
    private static final Identifier ARM_CLAW_GRIP_LOWER = model("mechanical_arm/lower_claw_grip");
    private static final Identifier MECHANICAL_PUMP_COG = model("mechanical_pump/cog");

    private static final float RAD_90 = Mth.HALF_PI;
    private static final float RAD_270 = Mth.DEG_TO_RAD * 270;

    private PolymerMachineParts() {}

    static void registerAll() {
        PolymerArmTargets.register();
        PolymerBasinParts.register();
        PolymerBeltConnectorPreview.register();
        PolymerWaterWheelWoods.register();
        PolymerBeltPlayers.register();
        PolymerTableClothPrice.register();
        PolymerNativeClients.register();
        PolymerCrafterParts.register();
        PolymerEjectorTargets.register();
        PolymerSymmetryWand.register();
        PolymerCuckooClockParts.register();
        PolymerBlockParts.<KineticBlockEntity>register(
                PolymerMachineParts::bearing,
                AllBlocks.MECHANICAL_BEARING,
                AllBlocks.WINDMILL_BEARING,
                AllBlocks.CLOCKWORK_BEARING);

        PolymerBlockParts.<SawBlockEntity>register(PolymerMachineParts::saw, AllBlocks.MECHANICAL_SAW);

        PolymerBlockParts.<DeployerBlockEntity>register(PolymerMachineParts::deployer, AllBlocks.DEPLOYER);

        PolymerBlockParts.<ArmBlockEntity>register(PolymerMachineParts::arm, AllBlocks.MECHANICAL_ARM);

        PolymerBlockParts.<PumpBlockEntity>register(PolymerMachineParts::pump, AllBlocks.MECHANICAL_PUMP);
    }

    private static float angle(KineticBlockEntity be, BlockState state, Axis axis, float time) {
        return progress(be.getSpeed(), time) + rotationOffset(state, axis, be.getBlockPos());
    }

    private static void bearing(KineticBlockEntity be, BlockState state, float time, List<PolymerBlockParts.Part> out) {
        IBearingBlockEntity bearing = (IBearingBlockEntity) be;
        Direction facing = state.getValue(FACING);
        Axis axis = facing.getAxis();
        Direction direction = axis.getPositive();
        Direction opposite = facing.getOpposite();

        float topAngle = bearing.getInterpolatedAngle(0);
        int[] generation = BEARING_SNAPS.computeIfAbsent(be, k -> new int[3]);
        float lastTop = Float.intBitsToFloat(generation[2]);
        if (Math.abs(Mth.wrapDegrees(topAngle - lastTop)) > 30) generation[0]++;
        generation[2] = Float.floatToIntBits(topAngle);
        float speed = be.getSpeed();
        if (Float.floatToIntBits(speed) != BEARING_SPEEDS.getOrDefault(be, Float.floatToIntBits(speed)))
            generation[1]++;
        BEARING_SPEEDS.put(be, Float.floatToIntBits(speed));

        float shaftAngle = angle(be, state, axis, time);
        out.add(part(SHAFT_HALF, spin(direction, shaftAngle).mul(rotateToFace(opposite)))
                .spinning(opposite, be.getSpeed(), shaftAngle)
                .identified("bearing:shaft:" + generation[1]));

        Matrix4f top = spin(direction, topAngle);
        if (axis != Axis.Y) {
            top.mul(spin(Direction.UP, AngleHelper.horizontalAngle(opposite)));
        }
        top.mul(spin(Direction.EAST, -90 - AngleHelper.verticalAngle(facing)));
        out.add(part(bearing.isWoodenTop() ? BEARING_TOP_WOODEN : BEARING_TOP, top)
                .identified("bearing:top:" + generation[0]));
    }

    private static final Map<KineticBlockEntity, int[]> BEARING_SNAPS = new WeakHashMap<>();
    private static final Map<KineticBlockEntity, Integer> BEARING_SPEEDS = new WeakHashMap<>();

    private static void saw(SawBlockEntity be, BlockState state, float time, List<PolymerBlockParts.Part> out) {
        Direction facing = state.getValue(FACING);
        float speed = be.getSpeed();
        if (facing.getAxis() == Axis.Y) {
            boolean alongFirst = state.getValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE);
            Axis axis = alongFirst ? Axis.X : Axis.Z;
            Identifier blade = speed > 0
                    ? SAW_BLADE_VERTICAL_ACTIVE
                    : speed < 0 ? SAW_BLADE_VERTICAL_REVERSED : SAW_BLADE_VERTICAL_INACTIVE;
            Matrix4f bladeTransform = rotateToFace(facing);
            if (alongFirst) {
                bladeTransform = rotateCentered(new Quaternionf().setAngleAxis(RAD_90, 0, 1, 0))
                        .mul(bladeTransform);
            }
            out.add(part(blade, bladeTransform));
            float shaftAngle = angle(be, state, axis, time);
            out.add(part(SHAFT, spin(axis, shaftAngle).mul(rotateToFaceVertical(axis.getPositive())))
                    .spinning(axis.getPositive(), be.getSpeed(), shaftAngle));
        } else {
            Axis axis = facing.getAxis();
            Identifier blade = speed > 0
                    ? SAW_BLADE_HORIZONTAL_ACTIVE
                    : speed < 0 ? SAW_BLADE_HORIZONTAL_REVERSED : SAW_BLADE_HORIZONTAL_INACTIVE;
            float shaftAngle = angle(be, state, axis, time);
            out.add(part(SHAFT_HALF, spin(axis, shaftAngle).mul(rotateToFace(facing.getOpposite())))
                    .spinning(facing.getOpposite(), be.getSpeed(), shaftAngle));
            out.add(part(blade, rotateToFace(facing)));
        }
    }

    private static void deployer(
            DeployerBlockEntity be, BlockState state, float time, List<PolymerBlockParts.Part> out) {
        Direction facing = state.getValue(FACING);
        Axis axis = axisOf(state);
        float shaftAngle = angle(be, state, axis, time);
        out.add(part(SHAFT, spin(axis, shaftAngle).mul(rotateToFaceVertical(axis.getPositive())))
                .spinning(axis.getPositive(), be.getSpeed(), shaftAngle));

        float distance = deployerHandDistance(be);
        Matrix4f offset = new Matrix4f()
                .translate(facing.getStepX() * distance, facing.getStepY() * distance, facing.getStepZ() * distance);
        Matrix4f pose = new Matrix4f(offset).mul(spin(Direction.UP, AngleHelper.horizontalAngle(facing)));
        if (facing == Direction.UP) {
            pose.mul(rotateCentered(new Quaternionf().setAngleAxis(RAD_270, 1, 0, 0)));
        } else if (facing == Direction.DOWN) {
            pose.mul(rotateCentered(new Quaternionf().setAngleAxis(RAD_90, 1, 0, 0)));
        }
        out.add(part(getDeployerHandPose(be), new Matrix4f(pose)));
        if (state.getValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE) ^ facing.getAxis() == Axis.Z) {
            pose.mul(rotateCentered(new Quaternionf().setAngleAxis(RAD_90, 0, 0, 1)));
        }
        out.add(part(DEPLOYER_POLE, pose));
        deployerItem(be, facing, distance, time, out);
    }

    private static void deployerItem(
            DeployerBlockEntity be, Direction facing, float distance, float time, List<PolymerBlockParts.Part> out) {
        if (heldItem(be).isEmpty()) {
            return;
        }
        Matrix4f pose = new Matrix4f()
                .translate(
                        0.5f + facing.getStepX() * distance,
                        0.5f + facing.getStepY() * distance,
                        0.5f + facing.getStepZ() * distance)
                .rotateY(Mth.DEG_TO_RAD * (AngleHelper.horizontalAngle(facing) + 180));
        boolean blockItem = heldItem(be).getItem() instanceof BlockItem;
        ItemDisplayContext context;
        if (be.mode == Mode.PUNCH) {
            context = ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            boolean spear = heldItem(be).is(ItemTags.SPEARS);
            if (spear) {
                pose.rotateX(Mth.DEG_TO_RAD * (facing == Direction.UP ? 20 : facing == Direction.DOWN ? 200 : -70));
            } else if (facing.getAxis() == Axis.Y) {
                pose.rotateX(facing == Direction.UP ? RAD_90 : RAD_270);
            }
            pose.translate(0, 0.125f, -0.75f);
            if (spear) {
                pose.translate(0, 0.6f, 0.6f);
            }
            pose.scale(0.75f);
        } else if (facing == Direction.UP && be.getSpeed() == 0) {
            context = ItemDisplayContext.GROUND;
            pose.translate(0, blockItem ? 0.5625f : 0.6875f, 0);
            if (blockItem) {
                pose.scale(1.25f);
            }
            pose.rotateY(Mth.DEG_TO_RAD * (time % 360));
        } else {
            context = ItemDisplayContext.FIXED;
            if (facing.getAxis() == Axis.Y) {
                pose.rotateX(facing == Direction.UP ? RAD_90 : RAD_270);
            }
            pose.translate(0, 0, -0.6875f).scale(blockItem ? 0.734375f : 0.5f);
        }
        out.add(PolymerBlockParts.itemPart(heldItem(be), pose, context));
    }

    private static Identifier getDeployerHandPose(DeployerBlockEntity be) {
        if (be.mode == Mode.PUNCH) {
            return DEPLOYER_HAND_PUNCHING;
        }
        return heldItem(be).isEmpty() ? DEPLOYER_HAND_POINTING : DEPLOYER_HAND_HOLDING;
    }

    private static float deployerHandDistance(DeployerBlockEntity be) {
        if (be.isVirtual()) return be.animatedOffset.getValue(1);
        float progress = 0;
        if (be.state == State.EXPANDING) {
            progress = 1 - be.timer / 1000.0f;
            if (be.fistBump) {
                progress *= progress;
            }
        }
        if (be.state == State.RETRACTING) {
            progress = be.timer / 1000.0f;
        }
        float handLength = be.mode == Mode.PUNCH ? 0.1875f : heldItem(be).isEmpty() ? 0 : 0.25f;
        return Math.min(Mth.clamp(progress, 0, 1) * (be.reach + handLength), 1.3125f);
    }

    private static ItemStack heldItem(DeployerBlockEntity be) {
        var player = be.getPlayer();
        return player == null ? be.heldItem : player.cast().getMainHandItem();
    }

    private static void arm(ArmBlockEntity be, BlockState state, float time, List<PolymerBlockParts.Part> out) {
        Axis axis = axisOf(state);
        out.add(part(ARM_COG, spin(axis, angle(be, state, axis, time)))
                .spinning(axis.getPositive(), be.getSpeed(), angle(be, state, axis, time)));

        float baseDeg;
        float lowerDeg;
        float upperDeg;
        float headDeg;
        if (be.phase != Phase.DANCING || be.getSpeed() == 0) {
            float[] angles = ArmMotion.angles(be);
            baseDeg = angles[0];
            lowerDeg = angles[1];
            upperDeg = angles[2] - 180;
            headDeg = angles[3] - 45;
        } else {
            float renderTick = time + be.hashCode() % 64;
            baseDeg = renderTick * 10 % 360;
            float lowerArm = Mth.lerp((Mth.sin(renderTick / 4) + 1) / 2, -45, 15);
            lowerDeg = lowerArm + 135;
            upperDeg = Mth.lerp((Mth.sin(renderTick / 8) + 1) / 4, -45, 95) - 90;
            headDeg = -lowerArm - 45;
        }

        boolean ceiling = state.getValue(ArmBlock.CEILING);
        Matrix4f m = new Matrix4f().translate(0.5f, 0.5f, 0.5f);
        if (ceiling) {
            m.rotateX(Mth.PI);
        }
        m.translate(0, 0.25f, 0).rotateY(Mth.DEG_TO_RAD * baseDeg);
        out.add(part(ARM_BASE, new Matrix4f(m)));
        m.translate(0, 0.125f, 0).rotateX(Mth.DEG_TO_RAD * lowerDeg);
        out.add(part(ARM_LOWER_BODY, new Matrix4f(m)));
        m.translate(0, 0, -0.875f).rotateX(Mth.DEG_TO_RAD * upperDeg);
        out.add(part(ARM_UPPER_BODY, new Matrix4f(m)));
        m.translate(0, 0, -0.9375f).rotateX(Mth.DEG_TO_RAD * headDeg);

        boolean goggles = be.goggles;
        Matrix4f claw = new Matrix4f(m);
        if (ceiling && goggles) {
            claw.rotateZ(Mth.PI);
        }
        out.add(part(goggles ? ARM_CLAW_BASE_GOGGLES : ARM_CLAW_BASE, claw));

        float clawOffset;
        if (be.heldItem.isEmpty()) {
            clawOffset = 0.0625f;
        } else if (be.heldItem.getItem() instanceof BlockItem) {
            clawOffset = 0.1875f;
        } else {
            clawOffset = 0.078125f;
        }
        out.add(part(ARM_CLAW_GRIP_LOWER, new Matrix4f(m).translate(0, -clawOffset, -0.375f)));
        out.add(part(ARM_CLAW_GRIP_UPPER, new Matrix4f(m).translate(0, clawOffset, -0.375f)));

        if (!be.heldItem.isEmpty()) {
            boolean block = be.heldItem.getItem() instanceof BlockItem;
            float scale = block ? 0.5f : 0.625f;
            Matrix4f item = new Matrix4f(m)
                    .rotateX(Mth.HALF_PI)
                    .translate(0, block ? -0.5625f : -0.625f, 0)
                    .scale(scale);
            out.add(PolymerBlockParts.itemPart(be.heldItem, item).identified("arm:held"));
        }
    }

    private static final class ArmMotion {
        private static final Map<ArmBlockEntity, ArmMotion> STATES = new WeakHashMap<>();
        private static final Field PROGRESS = field(ArmBlockEntity.class, "chasedPointProgress"),
                INDEX = field(ArmBlockEntity.class, "chasedPointIndex");
        private static final Field[] TARGET = {
            field(ArmAngleTarget.class, "baseAngle"),
            field(ArmAngleTarget.class, "lowerArmAngle"),
            field(ArmAngleTarget.class, "upperArmAngle"),
            field(ArmAngleTarget.class, "headAngle")
        };
        private static final float[] NO_TARGET = {0, 135, 45, 0};

        int index = Integer.MIN_VALUE;
        Phase phase;
        float[] previous = NO_TARGET;
        float previousBase;

        static float[] angles(ArmBlockEntity be) {
            ArmMotion motion = STATES.computeIfAbsent(be, k -> new ArmMotion());
            int index = getInt(INDEX, be);
            float progress = getFloat(PROGRESS, be);
            boolean ceiling = be.getBlockState().getValueOrElse(ArmBlock.CEILING, false);
            if (motion.index != index || motion.phase != be.phase) {
                if (motion.index != Integer.MIN_VALUE) {
                    com.zurrtum.create.content.kinetics.mechanicalArm.ArmInteractionPoint previousPoint =
                            point(be, motion.phase, motion.index);
                    motion.previous = previousPoint == null ? NO_TARGET : target(previousPoint, be, ceiling);
                    if (previousPoint != null) motion.previousBase = motion.previous[0];
                }
                motion.index = index;
                motion.phase = be.phase;
            }
            com.zurrtum.create.content.kinetics.mechanicalArm.ArmInteractionPoint point = point(be, be.phase, index);
            float[] target = point == null ? NO_TARGET : target(point, be, ceiling);
            float[] previous = motion.previous;
            float base = AngleHelper.angleLerp(
                    progress, motion.previousBase, target == NO_TARGET ? motion.previousBase : target[0]);
            if (progress < 0.5f) target = NO_TARGET;
            else previous = NO_TARGET;
            float t = progress == 1 ? 1 : progress % 0.5f * 2;
            return new float[] {
                base,
                Mth.lerp(t, previous[1], target[1]),
                Mth.lerp(t, previous[2], target[2]),
                AngleHelper.angleLerp(t, previous[3] % 360, target[3] % 360)
            };
        }

        private static com.zurrtum.create.content.kinetics.mechanicalArm.@org.jspecify.annotations.Nullable
                ArmInteractionPoint point(
                ArmBlockEntity be, @org.jspecify.annotations.Nullable Phase phase, int index) {
            if (index < 0) return null;
            if (phase == Phase.MOVE_TO_INPUT && index < be.inputs.size()) return be.inputs.get(index);
            if (phase == Phase.MOVE_TO_OUTPUT && index < be.outputs.size()) return be.outputs.get(index);
            return null;
        }

        private static float[] target(
                com.zurrtum.create.content.kinetics.mechanicalArm.ArmInteractionPoint point,
                ArmBlockEntity be,
                boolean ceiling) {
            Object angles = point.getTargetAngles(be.getBlockPos(), ceiling);
            float[] out = new float[4];
            for (int i = 0; i < 4; i++) out[i] = getFloat(TARGET[i], angles);
            return out;
        }

        private static Field field(Class<?> type, String name) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Mechanical arm field missing: " + name, e);
            }
        }

        private static float getFloat(Field field, Object owner) {
            try {
                return field.getFloat(owner);
            } catch (IllegalAccessException e) {
                return 0;
            }
        }

        private static int getInt(Field field, Object owner) {
            try {
                return field.getInt(owner);
            } catch (IllegalAccessException e) {
                return -1;
            }
        }
    }

    private static void pump(PumpBlockEntity be, BlockState state, float time, List<PolymerBlockParts.Part> out) {
        Direction facing = state.getValue(BlockStateProperties.FACING);
        Axis axis = facing.getAxis();
        float cogAngle = angle(be, state, axis, time);
        out.add(part(MECHANICAL_PUMP_COG, spin(axis, cogAngle).mul(rotateToFace(facing)))
                .spinning(facing, be.getSpeed(), cogAngle));
    }
}
