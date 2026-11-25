package com.zurrtum.create.infrastructure.polymer.block;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.Create;
import com.zurrtum.create.catnip.math.AngleHelper;
import com.zurrtum.create.content.contraptions.actors.roller.RollerBlock;
import com.zurrtum.create.content.contraptions.actors.roller.RollerBlockEntity;
import com.zurrtum.create.content.kinetics.base.IRotate;
import com.zurrtum.create.content.kinetics.base.KineticBlockEntity;
import com.zurrtum.create.content.kinetics.crank.ValveHandleBlock;
import com.zurrtum.create.content.kinetics.crank.ValveHandleBlockEntity;
import com.zurrtum.create.content.kinetics.crusher.CrushingWheelBlockEntity;
import com.zurrtum.create.content.kinetics.flywheel.FlywheelBlockEntity;
import com.zurrtum.create.content.kinetics.waterwheel.LargeWaterWheelBlock;
import com.zurrtum.create.content.kinetics.waterwheel.LargeWaterWheelBlockEntity;
import com.zurrtum.create.content.kinetics.waterwheel.WaterWheelBlock;
import com.zurrtum.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import com.zurrtum.create.infrastructure.polymer.AdapterHolder;
import com.zurrtum.create.infrastructure.polymer.PolymerBlockProjections;
import com.zurrtum.create.infrastructure.polymer.PolymerDisplays;
import com.zurrtum.create.infrastructure.polymer.PolymerLitModelHolder;
import com.zurrtum.create.infrastructure.polymer.PolymerPartItems;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class PolymerKineticVisuals {
    private static final Map<KineticBlockEntity, ShaftDisplay> SHAFTS = new IdentityHashMap<>();
    private static final Map<CrushingWheelBlockEntity, CrushingWheelDisplay> CRUSHING_WHEELS = new IdentityHashMap<>();
    private static final Map<FlywheelBlockEntity, FlywheelDisplay> FLYWHEELS = new IdentityHashMap<>();
    private static final Map<WaterWheelBlockEntity, WaterWheelDisplay> WATER_WHEELS = new IdentityHashMap<>();
    private static final Map<ValveHandleBlockEntity, ValveHandleDisplay> VALVE_HANDLES = new IdentityHashMap<>();
    private static final Map<RollerBlockEntity, RollerDisplay> ROLLERS = new IdentityHashMap<>();
    private static final Vector3f BLOCK_CENTER = new Vector3f(0.5f, 0.5f, 0.5f);
    private static final Identifier SHAFT_ITEM_MODEL = CreateIds.id("polymer/shaft");
    private static final Identifier CRUSHING_WHEEL_MODEL =
            PolymerBlockParts.model(CreateIds.id("block/polymer/crushing_wheel"));
    private static final Identifier ROLLER_FRAME_MODEL = PolymerBlockParts.model("mechanical_roller/frame");
    private static final Identifier ROLLER_WHEEL_MODEL = PolymerBlockParts.model("polymer/mechanical_roller_wheel");
    private static final Identifier COGWHEEL_ITEM_MODEL = CreateIds.id("polymer/cogwheel");
    private static final Identifier LARGE_COGWHEEL_ITEM_MODEL = CreateIds.id("polymer/large_cogwheel");
    private static final Vector3f FLYWHEEL_MODEL_CENTER = new Vector3f(0.45f, 0.45f, 0.45f);
    private static boolean loggedFirstShaft;

    private PolymerKineticVisuals() {}

    public static void register() {
        for (String model : List.of(
                "polymer/water_wheel_rotor",
                "polymer/large_water_wheel",
                "polymer/large_water_wheel_extension",
                "polymer/valve_handle")) PolymerBlockParts.model(model);
        for (var block : BuiltInRegistries.BLOCK)
            if (block instanceof ValveHandleBlock valve && valve.color != null)
                PolymerBlockParts.model(
                        "polymer/" + BuiltInRegistries.BLOCK.getKey(block).getPath());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
    }

    public static void tick(KineticBlockEntity blockEntity) {
        if (!(blockEntity.getLevel() instanceof ServerLevel level) || blockEntity.isRemoved()) {
            remove(blockEntity);
            return;
        }
        PolymerBlockParts.tick(blockEntity);
        if (PolymerBlockParts.handles(blockEntity.getBlockState())) {
            return;
        }

        if (blockEntity instanceof FlywheelBlockEntity flywheel) {
            tickFlywheel(flywheel, level);
            return;
        }
        if (blockEntity instanceof CrushingWheelBlockEntity crushingWheel) {
            tickCrushingWheel(crushingWheel, level);
            return;
        }
        if (blockEntity instanceof WaterWheelBlockEntity waterWheel) {
            tickWaterWheel(waterWheel, level);
            return;
        }
        if (blockEntity instanceof ValveHandleBlockEntity valveHandle) {
            tickValveHandle(valveHandle, level);
            return;
        }

        BlockState sourceState = blockEntity.getBlockState();
        Identifier itemModel = rotatingItemModel(sourceState);
        if (itemModel == null) {
            remove(blockEntity);
            return;
        }
        if (!(sourceState.getBlock() instanceof IRotate rotate)) {
            remove(blockEntity);
            return;
        }

        Axis axis = rotate.getRotationAxis(sourceState);
        BlockPos pos = blockEntity.getBlockPos();
        if (!loggedFirstShaft) {
            loggedFirstShaft = true;
            Create.LOGGER.info(
                    "First Polymer shaft display: pos={}, source={}, axis={}, itemModel={}",
                    pos,
                    sourceState,
                    axis,
                    itemModel);
        }
        ShaftDisplay display = SHAFTS.get(blockEntity);
        if (display != null && !display.itemModel.equals(itemModel)) {
            display.destroy();
            display = null;
        }
        if (display == null) {
            display = new ShaftDisplay(
                    level,
                    pos,
                    sourceState,
                    itemModel,
                    shaftRotation(axis, pos, blockEntity.getSpeed(), level.getGameTime()));
            SHAFTS.put(blockEntity, display);
        }
        display.update(sourceState, axis, pos, blockEntity.getSpeed(), level.getGameTime());
    }

    public static void remove(KineticBlockEntity blockEntity) {
        PolymerBlockParts.remove(blockEntity);
        ShaftDisplay display = SHAFTS.remove(blockEntity);
        if (display != null) {
            display.destroy();
        }
        if (blockEntity instanceof FlywheelBlockEntity flywheel) {
            FlywheelDisplay flywheelDisplay = FLYWHEELS.remove(flywheel);
            if (flywheelDisplay != null) {
                flywheelDisplay.destroy();
            }
        }
        if (blockEntity instanceof CrushingWheelBlockEntity crushingWheel) {
            CrushingWheelDisplay crushingDisplay = CRUSHING_WHEELS.remove(crushingWheel);
            if (crushingDisplay != null) {
                crushingDisplay.destroy();
            }
        }
        if (blockEntity instanceof WaterWheelBlockEntity waterWheel) {
            WaterWheelDisplay waterWheelDisplay = WATER_WHEELS.remove(waterWheel);
            if (waterWheelDisplay != null) {
                waterWheelDisplay.destroy();
            }
        }
        if (blockEntity instanceof ValveHandleBlockEntity valveHandle) {
            ValveHandleDisplay valveDisplay = VALVE_HANDLES.remove(valveHandle);
            if (valveDisplay != null) {
                valveDisplay.destroy();
            }
        }
    }

    public static void tick(RollerBlockEntity blockEntity) {
        if (!(blockEntity.getLevel() instanceof ServerLevel level) || blockEntity.isRemoved()) {
            remove(blockEntity);
            return;
        }
        BlockState sourceState = blockEntity.getBlockState();
        if (sourceState.getBlock() != AllBlocks.MECHANICAL_ROLLER) {
            remove(blockEntity);
            return;
        }
        BlockPos pos = blockEntity.getBlockPos();
        RollerDisplay display = ROLLERS.get(blockEntity);
        if (display == null) {
            display = new RollerDisplay(level, pos);
            ROLLERS.put(blockEntity, display);
        }
        display.update(blockEntity, sourceState, level.getGameTime());
    }

    public static void remove(RollerBlockEntity blockEntity) {
        RollerDisplay display = ROLLERS.remove(blockEntity);
        if (display != null) {
            display.destroy();
        }
    }

    private static void tickFlywheel(FlywheelBlockEntity blockEntity, ServerLevel level) {
        BlockState sourceState = blockEntity.getBlockState();
        if (sourceState.getBlock() != AllBlocks.FLYWHEEL || !(sourceState.getBlock() instanceof IRotate rotate)) {
            remove(blockEntity);
            return;
        }

        Axis axis = rotate.getRotationAxis(sourceState);
        BlockPos pos = blockEntity.getBlockPos();
        FlywheelDisplay display = FLYWHEELS.get(blockEntity);
        if (display == null) {
            display = new FlywheelDisplay(level, pos, sourceState, axis, blockEntity.getSpeed(), level.getGameTime());
            FLYWHEELS.put(blockEntity, display);
        }
        display.update(sourceState, axis, pos, blockEntity.getSpeed(), level.getGameTime());
    }

    private static void tickCrushingWheel(CrushingWheelBlockEntity blockEntity, ServerLevel level) {
        BlockState sourceState = blockEntity.getBlockState();
        if (sourceState.getBlock() != AllBlocks.CRUSHING_WHEEL || !(sourceState.getBlock() instanceof IRotate rotate)) {
            remove(blockEntity);
            return;
        }

        Axis axis = rotate.getRotationAxis(sourceState);
        BlockPos pos = blockEntity.getBlockPos();
        CrushingWheelDisplay display = CRUSHING_WHEELS.get(blockEntity);
        if (display == null) {
            display = new CrushingWheelDisplay(
                    level, pos, flywheelRotation(axis, pos, blockEntity.getSpeed(), level.getGameTime()));
            CRUSHING_WHEELS.put(blockEntity, display);
        }
        display.update(axis, pos, blockEntity.getSpeed(), level.getGameTime());
    }

    private static void tickWaterWheel(WaterWheelBlockEntity blockEntity, ServerLevel level) {
        BlockState sourceState = blockEntity.getBlockState();
        boolean large = blockEntity instanceof LargeWaterWheelBlockEntity;
        if (sourceState.getBlock() != (large ? AllBlocks.LARGE_WATER_WHEEL : AllBlocks.WATER_WHEEL)
                || !(sourceState.getBlock() instanceof IRotate rotate)) {
            remove(blockEntity);
            return;
        }

        String modelPath;
        Direction direction;
        if (large) {
            boolean extension = sourceState.getValue(LargeWaterWheelBlock.EXTENSION);
            modelPath = extension ? "block/polymer/large_water_wheel_extension" : "block/polymer/large_water_wheel";
            direction = rotate.getRotationAxis(sourceState).getPositive();
        } else {
            modelPath = "block/polymer/water_wheel_rotor";
            direction = sourceState.getValue(WaterWheelBlock.FACING);
        }
        BlockState wheelState = PolymerBlockProjections.getPolymerModelState(modelPath);
        if (wheelState == null) {
            remove(blockEntity);
            return;
        }

        BlockPos pos = blockEntity.getBlockPos();
        WaterWheelDisplay display = WATER_WHEELS.get(blockEntity);
        if (display == null) {
            display = new WaterWheelDisplay(level, pos, wheelState, large);
            WATER_WHEELS.put(blockEntity, display);
        }
        Axis axis = rotate.getRotationAxis(sourceState);
        display.update(wheelState, direction, axis, pos, blockEntity.getSpeed(), level.getGameTime());
    }

    private static void tickValveHandle(ValveHandleBlockEntity blockEntity, ServerLevel level) {
        BlockState sourceState = blockEntity.getBlockState();
        if (!(sourceState.getBlock() instanceof ValveHandleBlock)) {
            remove(blockEntity);
            return;
        }

        ValveHandleBlock valve = (ValveHandleBlock) sourceState.getBlock();
        Identifier handleModel = CreateIds.id("block/polymer/"
                + (valve.color == null
                        ? "valve_handle"
                        : BuiltInRegistries.BLOCK.getKey(valve).getPath()));

        BlockPos pos = blockEntity.getBlockPos();
        ValveHandleDisplay display = VALVE_HANDLES.get(blockEntity);
        if (display == null) {
            display = new ValveHandleDisplay(level, pos, sourceState, handleModel);
            VALVE_HANDLES.put(blockEntity, display);
        }
        display.update(handleModel, blockEntity, sourceState, pos, level.getGameTime());
    }

    private static void clear() {
        SHAFTS.values().forEach(ShaftDisplay::destroy);
        SHAFTS.clear();
        CRUSHING_WHEELS.values().forEach(CrushingWheelDisplay::destroy);
        CRUSHING_WHEELS.clear();
        FLYWHEELS.values().forEach(FlywheelDisplay::destroy);
        FLYWHEELS.clear();
        WATER_WHEELS.values().forEach(WaterWheelDisplay::destroy);
        WATER_WHEELS.clear();
        VALVE_HANDLES.values().forEach(ValveHandleDisplay::destroy);
        VALVE_HANDLES.clear();
        ROLLERS.values().forEach(RollerDisplay::destroy);
        ROLLERS.clear();
    }

    private static float rotationOffset(BlockPos pos, Axis axis) {
        int otherAxes = switch (axis) {
            case X -> pos.getY() + pos.getZ();
            case Y -> pos.getX() + pos.getZ();
            case Z -> pos.getX() + pos.getY();
        };
        return Math.floorMod(otherAxes, 2) == 0 ? 22.5f : 0;
    }

    private static Quaternionf shaftRotation(Axis axis, BlockPos pos, float speed, long gameTime) {
        double degrees = Math.IEEEremainder((double) gameTime * speed * 0.3 + rotationOffset(pos, axis), 360);
        float radians = (float) Math.toRadians(degrees);
        Direction direction = axis.getPositive();
        Quaternionf align =
                new Quaternionf().rotationTo(0, 1, 0, direction.getStepX(), direction.getStepY(), direction.getStepZ());
        return align.mul(new Quaternionf().rotationY(radians));
    }

    static final int MAX_ROTATION_CADENCE = 4;

    static int rotationCadence(float speed) {
        return stepCadence(Math.abs(speed) * 0.3);
    }

    static int stepCadence(double perTick) {
        if (perTick < 1e-6) return 1;
        return Math.max(1, Math.min(MAX_ROTATION_CADENCE, (int) Math.floor(170 / perTick) - 1));
    }

    private static final class Pacing {
        private final int phase;
        private float speed = Float.NaN;
        private Object shape;

        private Pacing(BlockPos pos) {
            phase = Math.floorMod(pos.hashCode(), MAX_ROTATION_CADENCE);
        }

        private int lead(float nextSpeed, Object nextShape, long gameTime, int cadence, DisplayElement... displays) {
            boolean snap = nextSpeed != speed || !Objects.equals(nextShape, shape);
            speed = nextSpeed;
            shape = nextShape;
            if (!snap && cadence > 1 && Math.floorMod(gameTime + phase, cadence) != 0) return -1;
            int interpolation = snap ? 0 : cadence + 1;
            for (var display : displays)
                if (display.getInterpolationDuration() != interpolation)
                    display.setInterpolationDuration(interpolation);
            return snap ? 0 : cadence - 1;
        }
    }

    static final double MAX_CHORD_SAG = 1 / 32.0;

    static int stepCadence(double perTick, double radius) {
        int cadence = stepCadence(perTick);
        while (cadence > 1 && radius * (1 - Math.cos(Math.toRadians((cadence + 1) * perTick / 2))) > MAX_CHORD_SAG)
            cadence--;
        return cadence;
    }

    private static boolean rotationDiffers(Quaternionfc current, Quaternionf next) {
        return Math.abs(current.x() - next.x) > 1.0E-5
                || Math.abs(current.y() - next.y) > 1.0E-5
                || Math.abs(current.z() - next.z) > 1.0E-5
                || Math.abs(current.w() - next.w) > 1.0E-5;
    }

    private static boolean translationDiffers(Vector3fc current, Vector3f next) {
        return Math.abs(current.x() - next.x) > 1.0E-5
                || Math.abs(current.y() - next.y) > 1.0E-5
                || Math.abs(current.z() - next.z) > 1.0E-5;
    }

    private static void applyCenteredRotation(
            BlockDisplayElement display, Quaternionf rotation, Vector3fc modelCenter) {
        Vector3f transformedCenter = rotation.transform(new Vector3f(modelCenter));
        Vector3f translation = new Vector3f(BLOCK_CENTER).sub(transformedCenter);
        boolean changed = false;
        if (translationDiffers(display.getTranslation(), translation)) {
            display.setTranslation(translation);
            changed = true;
        }
        if (rotationDiffers(display.getLeftRotation(), rotation)) {
            display.setLeftRotation(rotation);
            changed = true;
        }
        if (changed) {
            display.startInterpolationIfDirty();
        }
    }

    private static void applyShaftRotation(ItemDisplayElement display, Quaternionf rotation) {
        Vector3f translation = new Vector3f(0, -(float) display.getOffset().y, 0);
        boolean changed = false;
        if (translationDiffers(display.getTranslation(), translation)) {
            display.setTranslation(translation);
            changed = true;
        }
        if (rotationDiffers(display.getLeftRotation(), rotation)) {
            display.setLeftRotation(rotation);
            changed = true;
        }
        if (changed) {
            display.startInterpolationIfDirty();
        }
    }

    public static Identifier movingItemModel(BlockState state) {
        if (state.getBlock() == AllBlocks.CRUSHING_WHEEL)
            return CreateIds.id("polymer_part/" + CRUSHING_WHEEL_MODEL.getPath());
        return rotatingItemModel(state);
    }

    public static Identifier rotatingItemModel(BlockState state) {
        if (state.getBlock() == AllBlocks.SHAFT || state.getBlock() == AllBlocks.POWERED_SHAFT) {
            return SHAFT_ITEM_MODEL;
        }
        if (state.getBlock() == AllBlocks.COGWHEEL) {
            return COGWHEEL_ITEM_MODEL;
        }
        if (state.getBlock() == AllBlocks.LARGE_COGWHEEL) {
            return LARGE_COGWHEEL_ITEM_MODEL;
        }
        return null;
    }

    public static Quaternionf staticAxisRotation(BlockState state, BlockPos pos) {
        if (!(state.getBlock() instanceof IRotate rotate)) {
            return new Quaternionf();
        }
        return shaftRotation(rotate.getRotationAxis(state), pos, 0, 0);
    }

    public static ItemStack rotatingDisplayItem(Identifier itemModel) {
        ItemStack item = new ItemStack(Items.PAPER);
        PolymerPartItems.setItemModel(item, itemModel);
        return item;
    }

    private static Quaternionf flywheelRotation(Axis axis, BlockPos pos, float speed, long gameTime) {
        float offset = rotationOffset(pos, axis);
        float degrees = (float) Math.IEEEremainder((double) gameTime * speed * 0.3 + offset, 360);
        Direction direction = axis.getPositive();
        Quaternionf align =
                new Quaternionf().rotationTo(0, 1, 0, direction.getStepX(), direction.getStepY(), direction.getStepZ());
        return align.mul(new Quaternionf().rotationY((float) Math.toRadians(degrees)));
    }

    private static Quaternionf wheelRotation(Direction direction, float degrees) {
        Quaternionf align =
                new Quaternionf().rotationTo(0, 1, 0, direction.getStepX(), direction.getStepY(), direction.getStepZ());
        return align.mul(new Quaternionf().rotationY((float) Math.toRadians(degrees)));
    }

    private static float valveHandleAngle(
            ValveHandleBlockEntity blockEntity, Direction facing, BlockPos pos, long gameTime) {
        Axis axis = facing.getAxis();
        if (blockEntity.inUse == 0 && blockEntity.source != null && blockEntity.getSpeed() != 0) {
            return (float) Math.IEEEremainder(
                    (double) gameTime * blockEntity.getSpeed() * 0.3 + rotationOffset(pos, axis), 360);
        }

        float angle = blockEntity.targetAngle;
        if (blockEntity.inUse > 0 && blockEntity.totalUseTicks > 0) {
            float elapsed = blockEntity.totalUseTicks - blockEntity.inUse + 0.5f;
            float progress = Mth.clamp(elapsed / blockEntity.totalUseTicks, 0, 1);
            angle = Mth.lerp(progress, blockEntity.startAngle, blockEntity.targetAngle);
        }
        return angle
                * (blockEntity.backwards ? -1 : 1)
                * facing.getAxisDirection().getStep();
    }

    private static final class ShaftDisplay {
        private final PolymerLitModelHolder holder;
        private final ItemDisplayElement display;
        private final Identifier itemModel;
        private final int phase;
        private Axis latestAxis;
        private BlockPos latestPos;
        private float latestSpeed;

        private ShaftDisplay(
                ServerLevel level,
                BlockPos pos,
                BlockState sourceState,
                Identifier itemModel,
                Quaternionf initialRotation) {
            phase = Math.floorMod(pos.hashCode(), MAX_ROTATION_CADENCE);
            holder = new PolymerLitModelHolder(level, pos) {
                @Override
                public boolean startWatching(ServerGamePacketListenerImpl player) {
                    if (!getWatchingPlayers().contains(player) && latestAxis != null)
                        applyShaftRotation(
                                display,
                                shaftRotation(
                                        latestAxis,
                                        latestPos,
                                        latestSpeed,
                                        level.getGameTime() + rotationCadence(latestSpeed) - 1));
                    return super.startWatching(player);
                }
            };
            holder.staticCollision(sourceState);
            this.itemModel = itemModel;
            display = PolymerDisplays.item(rotatingDisplayItem(itemModel), ItemDisplayContext.NONE);
            display.setScale(new Vector3f(1, 1, 1));
            display.setLeftRotation(initialRotation);
            display.setInterpolationDuration(2);
            display.setTeleportDuration(1);
            PolymerDisplayCulling.kinetic(display);
            holder.addElement(display);
            ChunkAttachment.ofTicking(holder, level, pos);
        }

        private void update(BlockState sourceState, Axis axis, BlockPos pos, float speed, long gameTime) {
            holder.staticCollision(sourceState);
            boolean snap = speed != latestSpeed || axis != latestAxis;
            latestAxis = axis;
            latestPos = pos;
            latestSpeed = speed;
            if (holder.getWatchingPlayers().isEmpty()) return;
            int cadence = rotationCadence(speed);
            if (!snap && cadence > 1 && Math.floorMod(gameTime + phase, cadence) != 0) return;
            int interpolation = snap ? 0 : cadence + 1;
            if (display.getInterpolationDuration() != interpolation) display.setInterpolationDuration(interpolation);
            applyShaftRotation(display, shaftRotation(axis, pos, speed, snap ? gameTime : gameTime + cadence - 1));
        }

        private void destroy() {
            holder.destroy();
        }
    }

    private static final Identifier FLYWHEEL_WHEEL = PolymerBlockParts.model("polymer/flywheel");

    private static final class FlywheelDisplay {
        private final PolymerLitModelHolder holder;
        private final ItemDisplayElement shaft;
        private final ItemDisplayElement wheel;

        private final Pacing pacing;

        private FlywheelDisplay(
                ServerLevel level, BlockPos pos, BlockState sourceState, Axis axis, float speed, long gameTime) {
            pacing = new Pacing(pos);
            holder = new PolymerLitModelHolder(level, pos) {};
            holder.staticCollision(sourceState);
            ChunkAttachment.ofTicking(holder, level, pos);
            shaft = PolymerDisplays.item(rotatingDisplayItem(SHAFT_ITEM_MODEL), ItemDisplayContext.NONE);
            shaft.setScale(new Vector3f(1, 1, 1));
            shaft.setLeftRotation(shaftRotation(axis, pos, speed, gameTime));
            shaft.setInterpolationDuration(2);
            shaft.setTeleportDuration(1);
            PolymerDisplayCulling.kinetic(shaft);
            holder.addElement(shaft);

            wheel = PolymerDisplays.item(PolymerBlockParts.displayItem(FLYWHEEL_WHEEL), ItemDisplayContext.NONE);
            wheel.setDisplaySize(4, 4);
            Matrix4f local = new Matrix4f()
                    .translate(0.5f, 0.5f, 0.5f)
                    .rotate(flywheelRotation(axis, pos, speed, gameTime))
                    .scale(16 / 14.4f)
                    .translate(-0.45f, -0.45f, -0.45f);
            wheel.setTransformation(PolymerBlockParts.displayTransform(local));
            wheel.setInterpolationDuration(2);
            wheel.setTeleportDuration(1);
            holder.addElement(wheel);
        }

        private void update(BlockState sourceState, Axis axis, BlockPos pos, float speed, long gameTime) {
            holder.staticCollision(sourceState);
            int lead = pacing.lead(speed, axis, gameTime, stepCadence(Math.abs(speed) * 0.3, 0.1), shaft, wheel);
            if (lead < 0) return;
            gameTime += lead;
            applyShaftRotation(shaft, shaftRotation(axis, pos, speed, gameTime));
            Matrix4f local = new Matrix4f()
                    .translate(0.5f, 0.5f, 0.5f)
                    .rotate(flywheelRotation(axis, pos, speed, gameTime))
                    .scale(16 / 14.4f)
                    .translate(-0.45f, -0.45f, -0.45f);
            wheel.setTransformation(PolymerBlockParts.displayTransform(local));
            wheel.startInterpolationIfDirty();
        }

        private void destroy() {
            holder.destroy();
        }
    }

    private static final class WaterWheelDisplay {
        private final ElementHolder holder = new AdapterHolder();
        private final BlockDisplayElement wheel;
        private final Pacing pacing;
        private final double pivot;

        private WaterWheelDisplay(ServerLevel level, BlockPos pos, BlockState state, boolean large) {
            pacing = new Pacing(pos);
            pivot = large ? 2 * Math.sqrt(.5) : Math.sqrt(.5);
            ChunkAttachment.ofTicking(holder, level, pos);
            wheel = new BlockDisplayElement(state);
            wheel.setOffset(new Vec3(-0.5, -0.5, -0.5));
            if (large) {
                wheel.setScale(new Vector3f(2, 2, 2));
                wheel.setDisplaySize(6, 6);
            } else {
                wheel.setDisplaySize(3, 3);
            }
            wheel.setInterpolationDuration(2);
            wheel.setTeleportDuration(1);
            holder.addElement(wheel);
        }

        private void update(
                BlockState state, Direction direction, Axis axis, BlockPos pos, float speed, long gameTime) {
            if (!wheel.getBlockState().equals(state)) {
                wheel.setBlockState(state);
            }
            int lead = pacing.lead(speed, direction, gameTime, stepCadence(Math.abs(speed) * 0.3, pivot), wheel);
            if (lead < 0) return;
            double degrees =
                    Math.IEEEremainder((double) (gameTime + lead) * speed * 0.3 + rotationOffset(pos, axis), 360);
            applyCenteredRotation(wheel, wheelRotation(direction, (float) degrees), BLOCK_CENTER);
        }

        private void destroy() {
            holder.destroy();
        }
    }

    private static final class CrushingWheelDisplay {
        private final ElementHolder holder = new AdapterHolder();
        private final ItemDisplayElement wheel;
        private Pacing pacing;

        private CrushingWheelDisplay(ServerLevel level, BlockPos pos, Quaternionf initial) {
            ChunkAttachment.ofTicking(holder, level, pos);
            wheel = PolymerDisplays.item(PolymerBlockParts.displayItem(CRUSHING_WHEEL_MODEL), ItemDisplayContext.NONE);
            wheel.setRightRotation(new Quaternionf().rotationY(Mth.PI));
            wheel.setLeftRotation(initial);
            wheel.setDisplaySize(3, 3);
            wheel.setInterpolationDuration(2);
            wheel.setTeleportDuration(1);
            holder.addElement(wheel);
        }

        private void update(Axis axis, BlockPos pos, float speed, long gameTime) {
            if (pacing == null) pacing = new Pacing(pos);
            int lead = pacing.lead(speed, axis, gameTime, rotationCadence(speed), wheel);
            if (lead < 0) return;
            Quaternionf rotation = flywheelRotation(axis, pos, speed, gameTime + lead);
            if (rotationDiffers(wheel.getLeftRotation(), rotation)) {
                wheel.setLeftRotation(rotation);
                wheel.startInterpolationIfDirty();
            }
        }

        private void destroy() {
            holder.destroy();
        }
    }

    private static final class ValveHandleDisplay {
        private final PolymerLitModelHolder holder;
        private final ItemDisplayElement handle;
        private Identifier shown;
        private int previousUse = -1;
        private float phase;
        private float previousAngle = Float.NaN;

        private ValveHandleDisplay(ServerLevel level, BlockPos pos, BlockState sourceState, Identifier model) {
            holder = new PolymerLitModelHolder(level, pos) {};
            holder.staticCollision(sourceState);
            shown = model;
            handle = PolymerDisplays.item(PolymerBlockParts.displayItem(model), ItemDisplayContext.NONE);
            handle.setDisplaySize(2, 2);
            handle.setInterpolationDuration(2);
            handle.setTeleportDuration(1);
            holder.addElement(handle);
            ChunkAttachment.ofTicking(holder, level, pos);
        }

        private void update(Identifier model, ValveHandleBlockEntity be, BlockState source, BlockPos pos, long time) {
            holder.staticCollision(source);
            if (!model.equals(shown)) {
                shown = model;
                handle.setItem(PolymerBlockParts.displayItem(model));
            }
            Direction facing = source.getValue(BlockStateProperties.FACING);
            float angle = valveHandleAngle(be, facing, pos, time);
            boolean powered = be.inUse == 0 && be.source != null && be.getSpeed() != 0;
            if (powered) phase = 0;
            else if (!Float.isNaN(previousAngle) && be.inUse > previousUse) {
                float start = be.startAngle
                        * (be.backwards ? -1 : 1)
                        * facing.getAxisDirection().getStep();
                phase = Math.round((previousAngle - start) / 90) * 90;
            }
            angle += phase;
            previousUse = be.inUse;
            previousAngle = angle;
            Matrix4f pose =
                    PolymerBlockParts.spin(facing.getAxis(), angle).mul(PolymerBlockParts.rotateToFaceVertical(facing));
            handle.setTransformation(PolymerBlockParts.displayTransform(pose));
            handle.startInterpolationIfDirty();
        }

        private void destroy() {
            holder.destroy();
        }
    }

    private static final class RollerDisplay {
        private final ElementHolder holder = new AdapterHolder();
        private final ItemDisplayElement wheel =
                new ItemDisplayElement(PolymerBlockParts.displayItem(ROLLER_WHEEL_MODEL));
        private final ItemDisplayElement frame =
                new ItemDisplayElement(PolymerBlockParts.displayItem(ROLLER_FRAME_MODEL));
        private float frameAngle = Float.NaN;

        private RollerDisplay(ServerLevel level, BlockPos pos) {
            ChunkAttachment.ofTicking(holder, level, pos);
            wheel.setItemDisplayContext(ItemDisplayContext.NONE);
            wheel.setDisplaySize(6, 6);
            wheel.setInterpolationDuration(2);
            holder.addElement(wheel);
            frame.setItemDisplayContext(ItemDisplayContext.NONE);
            holder.addElement(frame);
        }

        private void update(RollerBlockEntity blockEntity, BlockState sourceState, long gameTime) {
            Direction facing = sourceState.getValue(RollerBlock.FACING);
            Vec3 offset =
                    Vec3.atLowerCornerOf(facing.getUnitVec3i()).scale(1.0625).add(0, -0.25, 0);
            float facingAngle = AngleHelper.horizontalAngle(facing);
            if (facingAngle != frameAngle) {
                frameAngle = facingAngle;
                Matrix4f framePose = new Matrix4f()
                        .translate(.5f, .5f, .5f)
                        .rotateY((float) Math.toRadians(facingAngle + 180))
                        .translate(-.5f, -.5f, -.5f);
                frame.setTransformation(PolymerBlockParts.displayTransform(framePose));
            }
            float rollAngle = (float) (gameTime / 20.0 * blockEntity.getAnimatedSpeed() % 360);
            Matrix4f transform = new Matrix4f()
                    .translate((float) offset.x, (float) offset.y, (float) offset.z)
                    .translate(0.5f, 0.5f, 0.5f)
                    .rotateY((float) Math.toRadians(facingAngle))
                    .translate(-0.5f, -0.5f, -0.5f)
                    .rotate((float) Math.toRadians(rollAngle), -1, 0, 0)
                    .translate(0, -0.5f, 0.5f)
                    .rotateY((float) (Math.PI / 2))
                    .scale(4);
            wheel.setTransformation(PolymerBlockParts.displayTransform(transform));
            wheel.startInterpolationIfDirty();
        }

        private void destroy() {
            holder.destroy();
        }
    }
}
