package com.zurrtum.create.infrastructure.polymer.block;

import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.model;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.part;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.progress;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.rotateCentered;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.rotateToFaceVertical;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.rotationOffset;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.spin;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.catnip.animation.LerpedFloat;
import com.zurrtum.create.catnip.math.AngleHelper;
import com.zurrtum.create.content.contraptions.actors.psi.PortableStorageInterfaceBlock;
import com.zurrtum.create.content.contraptions.actors.psi.PortableStorageInterfaceBlockEntity;
import com.zurrtum.create.content.contraptions.chassis.StickerBlock;
import com.zurrtum.create.content.contraptions.chassis.StickerBlockEntity;
import com.zurrtum.create.content.equipment.bell.AbstractBellBlockEntity;
import com.zurrtum.create.content.equipment.bell.HauntedBellBlockEntity;
import com.zurrtum.create.content.equipment.toolbox.ToolboxBlock;
import com.zurrtum.create.content.equipment.toolbox.ToolboxBlockEntity;
import com.zurrtum.create.content.logistics.depot.EjectorBlock;
import com.zurrtum.create.content.logistics.depot.EjectorBlockEntity;
import com.zurrtum.create.content.redstone.analogLever.AnalogLeverBlock;
import com.zurrtum.create.content.redstone.analogLever.AnalogLeverBlockEntity;
import com.zurrtum.create.content.redstone.deskBell.DeskBellBlock;
import com.zurrtum.create.content.redstone.deskBell.DeskBellBlockEntity;
import com.zurrtum.create.infrastructure.polymer.effects.PolymerVanillaEffects;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

final class PolymerDecorParts {
    private static final float RAD_90 = Mth.DEG_TO_RAD * 90;
    private static final float RAD_180 = Mth.DEG_TO_RAD * 180;

    private static final Identifier DESK_BELL_PLUNGER = model("desk_bell/plunger");
    private static final Identifier DESK_BELL_BELL = model("desk_bell/bell");

    private static final Identifier HAUNTED_BELL = model("haunted_bell");
    private static final Identifier PECULIAR_BELL = model("peculiar_bell");

    private static final Identifier TOOLBOX_DRAWER = model("toolbox/drawer");
    private static final Map<DyeColor, Identifier> TOOLBOX_LIDS = new EnumMap<>(DyeColor.class);

    private static final Identifier ANALOG_LEVER_HANDLE = model("analog_lever/handle");
    private static final Identifier ANALOG_LEVER_INDICATOR = model("analog_lever/indicator");

    private static final Identifier EJECTOR_TOP = model("weighted_ejector/top");
    private static final Identifier SHAFT = model("shaft");
    private static final float EJECTOR_PIVOT_Y = 11.25f / 16;
    private static final float EJECTOR_PIVOT_Z = 0.75f / 16;

    private static final Identifier STICKER_HEAD = model("sticker/head");

    private static final Identifier PSI_MIDDLE = model("portable_storage_interface/block_middle");
    private static final Identifier PSI_MIDDLE_POWERED = model("portable_storage_interface/block_middle_powered");
    private static final Identifier PSI_TOP = model("portable_storage_interface/block_top");
    private static final Identifier PFI_MIDDLE = model("portable_fluid_interface/block_middle");
    private static final Identifier PFI_MIDDLE_POWERED = model("portable_fluid_interface/block_middle_powered");
    private static final Identifier PFI_TOP = model("portable_fluid_interface/block_top");

    static {
        for (DyeColor color : DyeColor.values()) {
            TOOLBOX_LIDS.put(color, model("toolbox/lid/" + color.getSerializedName()));
        }
    }

    private PolymerDecorParts() {}

    static void registerAll() {
        PolymerBlockParts.<DeskBellBlockEntity>register(PolymerDecorParts::deskBell, AllBlocks.DESK_BELL);

        PolymerBlockParts.<AbstractBellBlockEntity>register(
                (be, state, time, out) -> {
                    hauntedBellParticles(be);
                    bell(be, state, HAUNTED_BELL, out);
                },
                AllBlocks.HAUNTED_BELL);
        PolymerBlockParts.<AbstractBellBlockEntity>register(
                (be, state, time, out) -> bell(be, state, PECULIAR_BELL, out), AllBlocks.PECULIAR_BELL);

        List<Block> toolboxes = new ArrayList<>();
        for (DyeColor color : DyeColor.values()) {
            ToolboxBlock toolbox = (ToolboxBlock) (Object) AllBlocks.TOOLBOX.pick(color);
            toolboxes.add(toolbox);
        }
        PolymerBlockParts.<ToolboxBlockEntity>register(PolymerDecorParts::toolbox, toolboxes.toArray(new Block[0]));

        PolymerBlockParts.<AnalogLeverBlockEntity>register(PolymerDecorParts::analogLever, AllBlocks.ANALOG_LEVER);

        PolymerBlockParts.<EjectorBlockEntity>register(PolymerDecorParts::ejector, AllBlocks.WEIGHTED_EJECTOR);

        PolymerBlockParts.<StickerBlockEntity>register(PolymerDecorParts::sticker, AllBlocks.STICKER);

        PolymerBlockParts.<PortableStorageInterfaceBlockEntity>register(
                PolymerDecorParts::portableInterface,
                AllBlocks.PORTABLE_STORAGE_INTERFACE,
                AllBlocks.PORTABLE_FLUID_INTERFACE);
    }

    private static Matrix4f yThenX(float yDegrees, float xDegrees) {
        return rotateCentered(new Quaternionf()
                .rotationY(Mth.DEG_TO_RAD * yDegrees)
                .mul(new Quaternionf().rotationX(Mth.DEG_TO_RAD * xDegrees)));
    }

    private static float swingAngle(float time) {
        float t = time / 1.5f;
        return 1.2f * Mth.sin(t / (float) Math.PI) / (2.5f + t / 3.0f);
    }

    private static final Map<DeskBellBlockEntity, Integer> BELL_TIMERS = new WeakHashMap<>();
    private static Field bellTimer;

    private static void deskBellDing(DeskBellBlockEntity be) {
        int timer;
        try {
            if (bellTimer == null) {
                bellTimer = DeskBellBlockEntity.class.getDeclaredField("blockStateTimer");
                bellTimer.setAccessible(true);
            }
            timer = bellTimer.getInt(be);
        } catch (ReflectiveOperationException e) {
            return;
        }
        Integer last = BELL_TIMERS.put(be, timer);
        if (last != null && timer > last) {
            be.animationOffset = be.getLevel().getRandom().nextFloat() * 2 * Mth.PI;
            be.animation.startWithValue(1).chase(0, 0.05, LerpedFloat.Chaser.LINEAR);
        }
    }

    private static void hauntedBellParticles(AbstractBellBlockEntity bell) {
        if (!(bell instanceof HauntedBellBlockEntity be)
                || be.effectTicks <= 0
                || !(be.getLevel() instanceof ServerLevel level)) return;
        var rand = level.getRandom();
        if (rand.nextFloat() > 0.25f) return;
        var pos = be.getBlockPos();
        PolymerVanillaEffects.sourceParticles(
                level,
                ParticleTypes.SOUL,
                pos.getX() + rand.nextDouble(),
                pos.getY() + 0.5,
                pos.getZ() + rand.nextDouble(),
                0,
                rand.nextDouble() * 0.04 - 0.02,
                0.1,
                rand.nextDouble() * 0.04 - 0.02,
                1);
    }

    private static void deskBell(
            DeskBellBlockEntity be, BlockState state, float time, List<PolymerBlockParts.Part> out) {
        deskBellDing(be);
        float p = be.animation.getValue();
        float f = (float) (1 - 4 * Math.pow(Math.max(p - 0.5, 0) - 0.5, 2));
        float f2 = (float) Math.pow(p, 1.25f);
        Direction facing = state.getValue(DeskBellBlock.FACING);
        Quaternionf orientation = new Quaternionf()
                .rotationY(Mth.DEG_TO_RAD * AngleHelper.horizontalAngle(facing))
                .mul(new Quaternionf().rotationX(Mth.DEG_TO_RAD * (AngleHelper.verticalAngle(facing) + 90)));

        float plungerOffset = f * -0.046875f;
        out.add(part(DESK_BELL_PLUNGER, new Matrix4f(rotateCentered(orientation)).translate(0, plungerOffset, 0)));

        float offset = p * Mth.PI * 4 + be.animationOffset;
        Quaternionf swingX = new Quaternionf().rotationX(Mth.DEG_TO_RAD * (f2 * 8 * Mth.sin(offset)));
        Quaternionf swingZ = new Quaternionf().rotationZ(Mth.DEG_TO_RAD * (f2 * 8 * Mth.cos(offset)));
        out.add(part(
                DESK_BELL_BELL,
                rotateCentered(new Quaternionf(orientation).mul(swingX).mul(swingZ))));
    }

    private static void bell(
            AbstractBellBlockEntity be, BlockState state, Identifier model, List<PolymerBlockParts.Part> out) {
        Quaternionf swing = new Quaternionf();
        if (be.isRinging && be.ringDirection != null) {
            Direction direction = be.ringDirection.getCounterClockWise();
            swing.setAngleAxis(
                    swingAngle(be.ringingTicks), direction.getStepX(), direction.getStepY(), direction.getStepZ());
        }
        Direction facing = state.getValue(BellBlock.FACING);
        BellAttachType attachment = state.getValue(BellBlock.ATTACHMENT);
        float yaw = AngleHelper.horizontalAngle(facing);
        if (attachment == BellAttachType.SINGLE_WALL || attachment == BellAttachType.DOUBLE_WALL) {
            yaw += 90;
        }
        Quaternionf up = new Quaternionf().rotationY(Mth.DEG_TO_RAD * yaw);
        out.add(part(model, rotateCentered(new Quaternionf(swing).mul(up))));
    }

    private static void toolbox(ToolboxBlockEntity be, BlockState state, float time, List<PolymerBlockParts.Part> out) {
        Direction facing = state.getValue(ToolboxBlock.FACING).getOpposite();
        Matrix4f body = rotateCentered(new Quaternionf().rotationY(Mth.DEG_TO_RAD * -facing.toYRot()));

        float offset1 = -be.drawers.getValue() * 0.175f;
        float offset2 = offset1 * 2;
        out.add(part(TOOLBOX_DRAWER, new Matrix4f(body).translate(0, 0.125f, offset1)));
        out.add(part(TOOLBOX_DRAWER, new Matrix4f(body).translate(0, 0, offset2)));

        Quaternionf lid = new Quaternionf().rotationX(Mth.DEG_TO_RAD * (be.lid.getValue() * 135));
        out.add(part(
                TOOLBOX_LIDS.get(be.getColor()),
                new Matrix4f(body).mul(PolymerBlockParts.pivot(lid, 0, 0.375f, 0.75f))));
    }

    private static void analogLever(
            AnalogLeverBlockEntity be, BlockState state, float time, List<PolymerBlockParts.Part> out) {
        float level = be.getState() / 15f;
        Direction facing = state.getValue(AnalogLeverBlock.FACING);
        AttachFace face = state.getValue(AnalogLeverBlock.FACE);

        Matrix4f orientation =
                rotateCentered(new Quaternionf().rotationY(Mth.DEG_TO_RAD * AngleHelper.horizontalAngle(facing)));
        if (face != AttachFace.FLOOR) {
            orientation.mul(rotateCentered(
                    new Quaternionf().setAngleAxis(face == AttachFace.WALL ? RAD_90 : RAD_180, 1, 0, 0)));
        }
        out.add(part(ANALOG_LEVER_INDICATOR, new Matrix4f(orientation)));

        Quaternionf handle = new Quaternionf().setAngleAxis((float) (level / 2 * Math.PI), 1, 0, 0);
        out.add(part(
                ANALOG_LEVER_HANDLE,
                new Matrix4f(orientation).mul(PolymerBlockParts.pivot(handle, 0.5f, 0.0625f, 0.5f))));
    }

    private static void ejector(EjectorBlockEntity be, BlockState state, float time, List<PolymerBlockParts.Part> out) {
        Direction facing = state.getValue(EjectorBlock.HORIZONTAL_FACING);
        Matrix4f yaw = rotateCentered(
                new Quaternionf().rotationY(Mth.DEG_TO_RAD * (180 + AngleHelper.horizontalAngle(facing))));
        Quaternionf lid = new Quaternionf().rotationX(Mth.DEG_TO_RAD * (be.getLidProgress(0) * -70));
        Matrix4f top = new Matrix4f(yaw).mul(PolymerBlockParts.pivot(lid, 0, EJECTOR_PIVOT_Y, EJECTOR_PIVOT_Z));
        out.add(part(EJECTOR_TOP, top));
        Matrix4f contents = new Matrix4f(top).mul(new Matrix4f(yaw).invert());
        PolymerLogisticsParts.depotItems(be.depotBehaviour, contents, out);

        Axis axis = facing.getClockWise().getAxis();
        float angle = progress(be.getSpeed(), time) + rotationOffset(state, axis, be.getBlockPos());
        out.add(part(SHAFT, spin(axis, angle).mul(rotateToFaceVertical(axis.getPositive())))
                .spinning(axis.getPositive(), be.getSpeed(), angle));
    }

    private static void sticker(StickerBlockEntity be, BlockState state, float time, List<PolymerBlockParts.Part> out) {
        Direction facing = state.getValue(StickerBlock.FACING);
        Matrix4f orientation = yThenX(AngleHelper.horizontalAngle(facing), AngleHelper.verticalAngle(facing) + 90);
        float offset = be.piston.getValue();
        offset = offset * offset * 0.25f;
        out.add(part(STICKER_HEAD, new Matrix4f(orientation).translate(0, offset, 0)));
    }

    private static void portableInterface(
            PortableStorageInterfaceBlockEntity be, BlockState state, float time, List<PolymerBlockParts.Part> out) {
        boolean fluid = state.is(AllBlocks.PORTABLE_FLUID_INTERFACE);
        boolean connected = be.isConnected();
        Direction facing = state.getValue(PortableStorageInterfaceBlock.FACING);

        Matrix4f orientation =
                rotateCentered(new Quaternionf().rotationY(Mth.DEG_TO_RAD * AngleHelper.horizontalAngle(facing)));
        if (facing != Direction.UP) {
            orientation.mul(rotateCentered(
                    new Quaternionf().rotationX(Mth.DEG_TO_RAD * (facing == Direction.DOWN ? 180 : 90))));
        }

        float offset = be.getExtensionDistance(1) * 0.5f;
        float middleOffset = offset + 0.375f;
        float topOffset = offset - 0.375f;

        Identifier middle =
                fluid ? (connected ? PFI_MIDDLE_POWERED : PFI_MIDDLE) : (connected ? PSI_MIDDLE_POWERED : PSI_MIDDLE);
        Identifier top = fluid ? PFI_TOP : PSI_TOP;

        out.add(part(middle, new Matrix4f(orientation).translate(0, middleOffset, 0)));
        out.add(part(top, new Matrix4f(orientation).translate(0, middleOffset + topOffset, 0)));
    }
}
