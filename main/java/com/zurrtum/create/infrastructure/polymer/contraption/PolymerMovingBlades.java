package com.zurrtum.create.infrastructure.polymer.contraption;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.Create;
import com.zurrtum.create.catnip.math.AngleHelper;
import com.zurrtum.create.catnip.math.VecHelper;
import com.zurrtum.create.content.contraptions.AbstractContraptionEntity;
import com.zurrtum.create.content.contraptions.ControlledContraptionEntity;
import com.zurrtum.create.content.contraptions.OrientedContraptionEntity;
import com.zurrtum.create.content.contraptions.actors.contraptionControls.ContraptionControlsBlock;
import com.zurrtum.create.content.contraptions.actors.contraptionControls.ContraptionControlsMovement;
import com.zurrtum.create.content.contraptions.actors.harvester.HarvesterBlock;
import com.zurrtum.create.content.contraptions.actors.psi.PortableStorageInterfaceBlock;
import com.zurrtum.create.content.contraptions.actors.psi.PortableStorageInterfaceMovement;
import com.zurrtum.create.content.contraptions.actors.roller.RollerBlock;
import com.zurrtum.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.zurrtum.create.content.contraptions.behaviour.MovementContext;
import com.zurrtum.create.content.contraptions.elevator.ElevatorContraption;
import com.zurrtum.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.zurrtum.create.content.kinetics.base.IRotate;
import com.zurrtum.create.content.kinetics.deployer.DeployerBlock;
import com.zurrtum.create.content.kinetics.deployer.DeployerBlockEntity;
import com.zurrtum.create.content.kinetics.drill.DrillBlock;
import com.zurrtum.create.content.kinetics.saw.SawBlock;
import com.zurrtum.create.content.processing.burner.BlazeBurnerBlock;
import com.zurrtum.create.content.trains.entity.CarriageContraption;
import com.zurrtum.create.content.trains.entity.CarriageContraptionEntity;
import com.zurrtum.create.infrastructure.polymer.PolymerDisplays;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerGogglesOverlay;
import com.zurrtum.create.infrastructure.polymer.interaction.PolymerElevatorControls;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import java.util.*;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.MutablePair;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

final class PolymerMovingBlades {
    private static final Identifier SAW_BLADE_VERTICAL_ACTIVE =
            PolymerBlockParts.model("mechanical_saw/blade_vertical_active");
    private static final Identifier SAW_BLADE_VERTICAL_INACTIVE =
            PolymerBlockParts.model("mechanical_saw/blade_vertical_inactive");
    private static final Identifier SAW_BLADE_HORIZONTAL_ACTIVE =
            PolymerBlockParts.model("mechanical_saw/blade_horizontal_active");
    private static final Identifier SAW_BLADE_HORIZONTAL_INACTIVE =
            PolymerBlockParts.model("mechanical_saw/blade_horizontal_inactive");
    private static final Identifier HARVESTER_BLADE = PolymerBlockParts.model("mechanical_harvester/blade");
    private static final Identifier DRILL_HEAD = PolymerBlockParts.model("mechanical_drill/head");
    private static final Identifier
            PSI_MIDDLE = PolymerBlockParts.model("portable_storage_interface/block_middle_powered"),
            PSI_TOP = PolymerBlockParts.model("portable_storage_interface/block_top"),
            PFI_MIDDLE = PolymerBlockParts.model("portable_fluid_interface/block_middle_powered"),
            PFI_TOP = PolymerBlockParts.model("portable_fluid_interface/block_top");
    private static final Identifier BEARING_TOP = PolymerBlockParts.model("bearing/top");
    private static final Identifier ROLLER_FRAME = PolymerBlockParts.model("mechanical_roller/frame"),
            ROLLER_WHEEL = PolymerBlockParts.model("polymer/mechanical_roller_wheel");
    private static final Identifier BURNER_IDLE = PolymerBlockParts.model("blaze_burner/blaze/idle"),
            BURNER_SUPER = PolymerBlockParts.model("blaze_burner/blaze/super"),
            BURNER_GOGGLES = PolymerBlockParts.model("blaze_burner/goggles"),
            TRAIN_HAT = PolymerBlockParts.model(CreateIds.id("entity/train_hat")),
            BURNER_RODS = PolymerBlockParts.model("polymer/burner_rods_small"),
            BURNER_RODS2 = PolymerBlockParts.model("polymer/burner_rods_large"),
            BURNER_SUPER_RODS = PolymerBlockParts.model("polymer/burner_super_rods_small"),
            BURNER_SUPER_RODS2 = PolymerBlockParts.model("polymer/burner_super_rods_large");
    private static final Identifier SHAFT = PolymerBlockParts.model("shaft"),
            DEPLOYER_POLE = PolymerBlockParts.model("deployer/pole"),
            DEPLOYER_HAND_PUNCHING = PolymerBlockParts.model("deployer/hand_punching"),
            DEPLOYER_HAND_POINTING = PolymerBlockParts.model("deployer/hand_pointing");

    private final ElementHolder holder;
    private final AbstractContraptionEntity entity;

    private record Key(BlockPos pos, int part) {}

    private final Map<Key, ItemDisplayElement> blades = new HashMap<>();

    PolymerMovingBlades(ElementHolder holder, AbstractContraptionEntity entity) {
        this.holder = holder;
        this.entity = entity;
    }

    List<DisplayElement> displays() {
        List<DisplayElement> all = new ArrayList<>(blades.values());
        all.addAll(texts.values());
        return all;
    }

    private final Map<Key, TextDisplayElement> texts = new HashMap<>();
    private final Map<BlockPos, Floor> floors = new HashMap<>();

    private static final class Floor {
        final com.zurrtum
                        .create
                        .content
                        .contraptions
                        .actors
                        .contraptionControls
                        .ContraptionControlsMovement
                        .ElevatorFloorSelection
                selection = new com.zurrtum
                        .create
                        .content
                        .contraptions
                        .actors
                        .contraptionControls
                        .ContraptionControlsMovement
                        .ElevatorFloorSelection();
        Object names;
        Integer lastTarget;
    }

    private static final int WHITE_FIRST = 0xEDEAE5,
            WHITE_SECOND = 0xBBB6B0,
            ORANGE_FIRST = 0xEE9246,
            ORANGE_SECOND = 0xD94A27;

    private void floorLabel(
            Set<Key> live, Matrix4f world, StructureBlockInfo info, ElevatorContraption elevator, BlockState state) {
        Floor floor = floors.computeIfAbsent(info.pos(), p -> new Floor());
        var selection = floor.selection;
        Integer target = elevator.getCurrentTargetY(entity.level());
        if (target == null) target = (int) Math.round(entity.getY() + elevator.getContactYOffset());
        if (floor.names != elevator.namesList || !Objects.equals(floor.lastTarget, target)) {
            for (int i = 0; i < elevator.namesList.size(); i++)
                if (elevator.namesList.get(i).getFirst().equals(target)) {
                    selection.currentIndex = i;
                    break;
                }
            floor.names = elevator.namesList;
        }
        floor.lastTarget = target;
        ContraptionControlsMovement.tickFloorSelection(selection, elevator);
        if (!elevator.namesList.isEmpty())
            selection.targetYEqualsSelection = Objects.equals(target, selection.currentTargetY);
        var scrolled = PolymerElevatorControls.latestSelection(entity, info.pos());
        if (scrolled != null) selection = scrolled;
        String shortName = selection.currentShortName == null ? "" : selection.currentShortName;
        String longName = selection.currentLongName == null ? "" : selection.currentLongName;
        boolean white = selection.targetYEqualsSelection;
        int first = white ? WHITE_FIRST : ORANGE_FIRST, second = white ? WHITE_SECOND : ORANGE_SECOND;
        int color = mix(first, second, .125f), shadow = mix(second, 0, .35f);

        Direction facing = state.getValue(ContraptionControlsBlock.FACING);
        Matrix4f base = rotateCentered(new Matrix4f(), Mth.DEG_TO_RAD * AngleHelper.horizontalAngle(facing), 0, 1, 0)
                .translate(.4f, 1.125f, .5f)
                .rotate(Mth.DEG_TO_RAD * 67.5f, -1, 0, 0);
        var font = Component.literal(shortName);
        if (!shortName.isBlank()) {
            int width = PolymerGogglesOverlay.width(shortName), size = Math.max(width, 12);
            float scale = 1 / (5 * (size - .5f)), y = (size - 8) / 2f;
            Matrix4f text = new Matrix4f(base).translate(0, .15f, -.25f).scale(scale, -scale, scale);
            label(
                    live,
                    new Key(info.pos(), 10),
                    font.copy().withColor(shadow),
                    new Matrix4f(text).translate(.5f, .5f, -.0625f).translate(size / 2f - 1, y + 9, 0),
                    world,
                    info,
                    .16f);
            label(
                    live,
                    new Key(info.pos(), 11),
                    font.copy().withColor(color),
                    new Matrix4f(text).translate(size / 2f - 1, y + 9, 0),
                    world,
                    info,
                    .16f);
        }
        if (!longName.isBlank()) {
            int width = PolymerGogglesOverlay.width(longName), size = Math.max(width, 55);
            float scale = 1 / (3 * (size - .5f)), y = (size - 8) / 2f;
            Matrix4f text = new Matrix4f(base)
                    .translate(-.0635f, .06f, -.25f)
                    .scale(scale, -scale, scale)
                    .translate(size / 2f - 1, y + 9, 0);
            label(live, new Key(info.pos(), 12), Component.literal(longName).withColor(color), text, world, info, .07f);
        }
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255),
                g = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255),
                bl = (int) Mth.lerp(t, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }

    private void label(
            Set<Key> live,
            Key key,
            Component text,
            Matrix4f fontPose,
            Matrix4f world,
            StructureBlockInfo info,
            float viewRange) {
        live.add(key);
        var display = texts.get(key);
        boolean created = display == null;
        if (created) {
            display = new TextDisplayElement(text);
            display.setBackground(0);
            display.setLineWidth(1000);
            display.setShadow(false);
            display.setBrightness(new Brightness(15, 15));
            display.setViewRange(viewRange);
            display.setInterpolationDuration(PolymerContraptionVisuals.smoothTicks(entity));
            display.setTeleportDuration(PolymerContraptionVisuals.smoothTicks(entity));
            texts.put(key, display);
            holder.addElement(display);
        } else if (!text.equals(display.getText())) display.setText(text);
        display.setTransformation(new Matrix4f(world)
                .translate(info.pos().getX(), info.pos().getY(), info.pos().getZ())
                .mul(fontPose)
                .scale(40, -40, 40));
        if (created) display.startInterpolation();
        else display.startInterpolationIfDirty();
    }

    void update(Matrix4f world) {
        Set<Key> live = new HashSet<>();
        for (MutablePair<StructureBlockInfo, MovementContext> actor :
                entity.getContraption().getActors()) {
            StructureBlockInfo info = actor.getLeft();
            MovementContext context = actor.getRight();
            if (context == null) continue;
            try {
                actor(live, world, info, context);
            } catch (RuntimeException error) {
                if (FAILED.add(info.state().getBlock()))
                    Create.LOGGER.error(
                            "Moving actor part failed for {}", info.state().getBlock(), error);
            }
        }
        blades.entrySet().removeIf(entry -> {
            if (live.contains(entry.getKey())) return false;
            holder.removeElement(entry.getValue());
            if (entry.getKey().part() == 0) rotations.remove(entry.getKey().pos());
            return true;
        });
        texts.entrySet().removeIf(entry -> {
            if (live.contains(entry.getKey())) return false;
            holder.removeElement(entry.getValue());
            return true;
        });
        floors.keySet().removeIf(pos -> live.stream().noneMatch(key -> key.pos().equals(pos) && key.part() >= 10));
    }

    private void deployer(
            Set<Key> live, Matrix4f world, StructureBlockInfo info, MovementContext context, BlockState state) {
        DeployerBlockEntity.Mode mode = context.blockEntityData == null
                ? DeployerBlockEntity.Mode.PUNCH
                : context.blockEntityData
                        .read("Mode", DeployerBlockEntity.Mode.CODEC)
                        .orElse(DeployerBlockEntity.Mode.PUNCH);
        Identifier hand = mode == DeployerBlockEntity.Mode.PUNCH ? DEPLOYER_HAND_PUNCHING : DEPLOYER_HAND_POINTING;
        float speed = context.contraption.stalled ? 0 : context.getAnimationSpeed();
        double factor;
        if (context.contraption.stalled || context.position == null || context.data.contains("StationaryTimer")) {
            factor = Mth.sin((float) (entity.level().getGameTime() * .5 % (Math.PI * 2))) * .25f + .25f;
        } else {
            Vec3 center = VecHelper.getCenterOf(BlockPos.containing(context.position));
            factor = .5f - Mth.clamp(context.position.distanceTo(center), 0, 1);
        }
        Direction facing = state.getValue(DeployerBlock.FACING);
        Direction.Axis axis =
                state.getBlock() instanceof IRotate rotate ? rotate.getRotationAxis(state) : Direction.Axis.Y;
        Rotation r = rotations.computeIfAbsent(info.pos(), p -> new Rotation());
        r.value = (r.value + speed / 20) % 360;
        Matrix4f shaft = new Matrix4f()
                .translate(.5f, .5f, .5f)
                .rotateY(axis == Direction.Axis.Z ? Mth.HALF_PI : 0)
                .rotateZ(axis.isHorizontal() ? Mth.HALF_PI : 0)
                .translate(-.5f, -.5f, -.5f);
        rotateCentered(shaft, Mth.DEG_TO_RAD * r.value, 0, 1, 0);
        Matrix4f handPose = new Matrix4f();
        if (!context.disabled)
            handPose.translate((float) (facing.getStepX() * factor), (float) (facing.getStepY() * factor), (float)
                    (facing.getStepZ() * factor));
        rotateCentered(handPose, Mth.DEG_TO_RAD * AngleHelper.horizontalAngle(facing), 0, 1, 0);
        rotateCentered(
                handPose, Mth.DEG_TO_RAD * (facing == Direction.UP ? 270 : facing == Direction.DOWN ? 90 : 0), 1, 0, 0);
        Matrix4f pole = rotateCentered(
                new Matrix4f(handPose),
                Mth.DEG_TO_RAD
                        * (state.getValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE)
                                        ^ facing.getAxis() == Direction.Axis.Z
                                ? 90
                                : 0),
                0,
                0,
                1);
        place(live, new Key(info.pos(), 1), SHAFT, shaft, world, info);
        place(live, new Key(info.pos(), 2), DEPLOYER_POLE, pole, world, info);
        place(live, new Key(info.pos(), 3), hand, handPose, world, info);
    }

    private void psi(
            Set<Key> live, Matrix4f world, StructureBlockInfo info, MovementContext context, BlockState state) {
        boolean fluid = state.is(AllBlocks.PORTABLE_FLUID_INTERFACE);
        float top = PortableStorageInterfaceMovement.getAnimation(context).getChaseTarget();
        Direction facing = state.getValue(PortableStorageInterfaceBlock.FACING);
        Matrix4f base = new Matrix4f()
                .translate(.5f, .5f, .5f)
                .rotateY(Mth.DEG_TO_RAD * AngleHelper.horizontalAngle(facing))
                .rotateX(Mth.DEG_TO_RAD * (facing == Direction.UP ? 0 : facing == Direction.DOWN ? 180 : 90))
                .translate(-.5f, -.5f, -.5f);
        place(
                live,
                new Key(info.pos(), 1),
                fluid ? PFI_MIDDLE : PSI_MIDDLE,
                new Matrix4f(base).translate(0, top * .5f + .375f, 0),
                world,
                info);
        place(
                live,
                new Key(info.pos(), 2),
                fluid ? PFI_TOP : PSI_TOP,
                new Matrix4f(base).translate(0, top, 0),
                world,
                info);
    }

    private static float counterRotation(MovementContext context, Direction facing) {
        if (!context.contraption.canBeStabilized(facing, context.localPos)) return 0;
        AbstractContraptionEntity parent = context.contraption.entity;
        if (parent instanceof ControlledContraptionEntity controlled) return -controlled.getAngle(1);
        if (parent instanceof OrientedContraptionEntity oriented) {
            if (facing.getAxis().isVertical()) return -oriented.getViewYRot(1);
            if (oriented.isInitialOrientationPresent()
                    && oriented.getInitialOrientation().getAxis() == facing.getAxis()) return -oriented.getViewXRot(1);
        }
        return 0;
    }

    private static final Set<Block> FAILED = new HashSet<>();

    private void actor(Set<Key> live, Matrix4f world, StructureBlockInfo info, MovementContext context) {
        BlockState state = info.state();
        Identifier model;
        Matrix4f local;
        if (state.getBlock() instanceof SawBlock) {
            boolean horizontal = SawBlock.isHorizontal(state);
            Direction facing = state.getValue(SawBlock.FACING);
            boolean backwards = VecHelper.isVecPointingTowards(context.relativeMotion, facing.getOpposite());
            boolean moving = context.getAnimationSpeed() != 0;
            boolean shouldAnimate =
                    context.contraption.stalled && horizontal || !context.contraption.stalled && !backwards && moving;
            if (horizontal) {
                model = shouldAnimate ? SAW_BLADE_HORIZONTAL_ACTIVE : SAW_BLADE_HORIZONTAL_INACTIVE;
                local = PolymerBlockParts.rotateToFace(facing);
            } else {
                model = shouldAnimate ? SAW_BLADE_VERTICAL_ACTIVE : SAW_BLADE_VERTICAL_INACTIVE;
                local = PolymerBlockParts.rotateToFace(facing);
                if (state.getValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE)) {
                    local = PolymerBlockParts.rotateCentered(new Quaternionf().rotationY(Mth.DEG_TO_RAD * 90))
                            .mul(local);
                }
            }
        } else if (state.getBlock() instanceof HarvesterBlock) {
            if (context.contraption.stalled
                    || context.disabled
                    || VecHelper.isVecPointingTowards(
                            context.relativeMotion,
                            state.getValue(HarvesterBlock.FACING).getOpposite())) {
                return;
            }
            model = HARVESTER_BLADE;
            double arcLength = context.motion.length();
            float deg = AngleHelper.deg(arcLength * 16 / 6.5);
            deg = (int) (deg * 3000) / 3000f;
            Rotation r = rotations.computeIfAbsent(info.pos(), p -> new Rotation());
            r.value = (float) ((r.value + deg * 1.25) % 360);
            Direction facing = state.getValue(HarvesterBlock.FACING);
            float horizontalAngle = facing.toYRot() + (facing.getAxis() == Direction.Axis.X ? 180 : 0);
            local = new Matrix4f()
                    .translate(0.5f, 0.5f, 0.5f)
                    .rotateY(Mth.DEG_TO_RAD * horizontalAngle)
                    .translate(-0.5f, -0.5f, -0.5f)
                    .translate(0.5f, 0.375f, 0.5625f)
                    .rotateX(Mth.DEG_TO_RAD * r.value)
                    .translate(-0.5f, -0.375f, -0.5625f);
        } else if (state.getBlock() instanceof DrillBlock) {
            Direction facing = state.getValue(DrillBlock.FACING);
            float speed = context.disabled
                    ? 0
                    : context.contraption.stalled
                            ? 700
                            : !VecHelper.isVecPointingTowards(context.relativeMotion, facing.getOpposite())
                                    ? context.getAnimationSpeed()
                                    : 0;
            Rotation r = rotations.computeIfAbsent(info.pos(), p -> new Rotation());
            r.value = (r.value + speed / 20) % 360;
            model = DRILL_HEAD;
            local = new Matrix4f()
                    .translate(.5f, .5f, .5f)
                    .rotateY(Mth.DEG_TO_RAD * AngleHelper.horizontalAngle(facing))
                    .rotateX(Mth.DEG_TO_RAD * AngleHelper.verticalAngle(facing))
                    .rotateZ(Mth.DEG_TO_RAD * r.value)
                    .translate(-.5f, -.5f, -.5f);
        } else if (state.is(AllBlocks.MECHANICAL_BEARING)) {
            Direction facing = state.getValue(BlockStateProperties.FACING);
            float angle =
                    counterRotation(context, facing) * facing.getAxisDirection().getStep();
            Quaternionf orientation = facing.getAxis().isHorizontal()
                    ? new Quaternionf().rotationY(Mth.DEG_TO_RAD * AngleHelper.horizontalAngle(facing.getOpposite()))
                    : new Quaternionf();
            orientation.mul(new Quaternionf().rotationX(Mth.DEG_TO_RAD * (-90 - AngleHelper.verticalAngle(facing))));
            Quaternionf rotation = new Quaternionf()
                    .rotationAxis(Mth.DEG_TO_RAD * angle, facing.step())
                    .mul(orientation);
            model = BEARING_TOP;
            local = new Matrix4f().translate(.5f, .5f, .5f).rotate(rotation).translate(-.5f, -.5f, -.5f);
        } else if (state.getBlock() instanceof BlazeBurnerBlock) {
            burner(live, world, info, context, state);
            return;
        } else if (state.getBlock() instanceof ContraptionControlsBlock) {
            if (context.contraption instanceof ElevatorContraption elevator)
                floorLabel(live, world, info, elevator, state);
            return;
        } else if (state.getBlock() instanceof ControlsBlock) {
            return;
        } else if (state.getBlock() instanceof RollerBlock) {
            roller(live, world, info, context, state);
            return;
        } else if (state.getBlock() instanceof PortableStorageInterfaceBlock) {
            psi(live, world, info, context, state);
            return;
        } else if (state.getBlock() instanceof DeployerBlock) {
            deployer(live, world, info, context, state);
            return;
        } else {
            return;
        }
        place(live, new Key(info.pos(), 0), model, local, world, info);
    }

    private void burner(
            Set<Key> live, Matrix4f world, StructureBlockInfo info, MovementContext context, BlockState state) {
        var heat = BlazeBurnerBlock.getHeatLevelOf(state);
        if (heat == BlazeBurnerBlock.HeatLevel.NONE) return;
        boolean seething = heat == BlazeBurnerBlock.HeatLevel.SEETHING;
        boolean conductor = conductor(context);
        float target = 0;
        if (conductor
                && context.relativeMotion.length() > 1e-6
                && context.contraption.entity instanceof CarriageContraptionEntity carriage) {
            float angle = AngleHelper.deg(-Mth.atan2(context.relativeMotion.x, context.relativeMotion.z));
            target = carriage.getInitialOrientation().getAxis() == Direction.Axis.X ? angle + 180 : angle;
        } else if (context.position != null) {
            var nearest = entity.level()
                    .getNearestPlayer(
                            context.position.x,
                            context.position.y,
                            context.position.z,
                            32,
                            player -> !player.isInvisible() && !PolymerNativeClients.isNative((ServerPlayer) player));
            if (nearest != null) {
                Vec3 local = entity.reverseRotation(nearest.position().subtract(context.position), 1);
                target = AngleHelper.deg(-Mth.atan2(local.z, local.x)) - 90;
            }
        }
        float phase = entity.level().getGameTime() / 16f + Math.floorMod(context.hashCode(), 13);
        float offset = (float) Math.sin(phase) / 64;
        Matrix4f yaw = rotateCentered(new Matrix4f(), Mth.DEG_TO_RAD * target, 0, 1, 0);
        Matrix4f head = new Matrix4f(yaw).translate(0, offset, 0);
        place(live, new Key(info.pos(), 1), seething ? BURNER_SUPER : BURNER_IDLE, head, world, info);
        if (context.blockEntityData != null && context.blockEntityData.contains("Goggles"))
            place(live, new Key(info.pos(), 2), BURNER_GOGGLES, new Matrix4f(head).translate(0, .5f, 0), world, info);
        if (conductor || context.blockEntityData != null && context.blockEntityData.contains("TrainHat"))
            place(
                    live,
                    new Key(info.pos(), 3),
                    TRAIN_HAT,
                    new Matrix4f(yaw).translate(.5f, offset + .75f, .5f),
                    world,
                    info);
        place(
                live,
                new Key(info.pos(), 4),
                seething ? BURNER_SUPER_RODS : BURNER_RODS,
                new Matrix4f().translate(0, (float) Math.sin(phase + Math.PI) / 64 + .125f, 0),
                world,
                info);
        place(
                live,
                new Key(info.pos(), 5),
                seething ? BURNER_SUPER_RODS2 : BURNER_RODS2,
                new Matrix4f().translate(0, (float) Math.sin(phase + Math.PI / 2) / 64 - .1875f, 0),
                world,
                info);
    }

    private static boolean conductor(MovementContext context) {
        if (!(context.contraption.entity instanceof CarriageContraptionEntity carriage)
                || !carriage.hasSchedule()
                || !(context.contraption instanceof CarriageContraption contraption)) return false;
        Direction assembly = contraption.getAssemblyDirection();
        for (Direction direction : new Direction[] {
            Direction.fromAxisAndDirection(assembly.getAxis(), Direction.AxisDirection.POSITIVE),
            Direction.fromAxisAndDirection(assembly.getAxis(), Direction.AxisDirection.NEGATIVE)
        }) if (contraption.inControl(context.localPos, direction)) return true;
        return false;
    }

    private void roller(
            Set<Key> live, Matrix4f world, StructureBlockInfo info, MovementContext context, BlockState state) {
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        float speed = context.contraption.stalled
                ? 0
                : !VecHelper.isVecPointingTowards(context.relativeMotion, facing.getOpposite())
                        ? context.getAnimationSpeed()
                        : -context.getAnimationSpeed();
        Rotation r = rotations.computeIfAbsent(info.pos(), p -> new Rotation());
        r.value = (r.value + speed / 20) % 360;
        float angle = AngleHelper.horizontalAngle(facing);
        place(
                live,
                new Key(info.pos(), 1),
                ROLLER_FRAME,
                rotateCentered(new Matrix4f(), Mth.DEG_TO_RAD * (angle + 180), 0, 1, 0),
                world,
                info);
        Matrix4f wheel = new Matrix4f()
                .translate(0, -.25f, 0)
                .translate(facing.getStepX() * 17 / 16f, 0, facing.getStepZ() * 17 / 16f);
        rotateCentered(wheel, Mth.DEG_TO_RAD * angle, 0, 1, 0);
        wheel.rotate(Mth.DEG_TO_RAD * r.value, -1, 0, 0)
                .translate(0, -.5f, .5f)
                .rotateY(Mth.HALF_PI)
                .scale(4);
        place(live, new Key(info.pos(), 2), ROLLER_WHEEL, wheel, world, info);
    }

    private void place(
            Set<Key> live, Key key, Identifier model, Matrix4f local, Matrix4f world, StructureBlockInfo info) {
        live.add(key);
        ItemDisplayElement display = blades.get(key);
        boolean created = display == null;
        if (created) {
            display = PolymerDisplays.item(PolymerBlockParts.displayItem(model), ItemDisplayContext.NONE);
            display.setInterpolationDuration(PolymerContraptionVisuals.smoothTicks(entity));
            display.setTeleportDuration(PolymerContraptionVisuals.smoothTicks(entity));
            blades.put(key, display);
            holder.addElement(display);
        }
        var item = PolymerBlockParts.displayItem(model);
        if (!ItemStack.isSameItemSameComponents(display.getItem(), item)) display.setItem(item);
        Matrix4f pose = new Matrix4f(world)
                .translate(info.pos().getX(), info.pos().getY(), info.pos().getZ())
                .mul(local);
        PolymerContraptionPassengers.applyDisplayPose(display, pose);
        if (created) display.startInterpolation();
        else display.startInterpolationIfDirty();
        if ((entity.tickCount & 3) == 0 || display.getBrightness() == null) {
            var light = PolymerContraptionVisuals.light(
                    entity.level(), entity.toGlobalVector(Vec3.atCenterOf(info.pos()), 1));
            if (!light.equals(display.getBrightness())) display.setBrightness(light);
        }
    }

    private static Matrix4f rotateCentered(Matrix4f m, float radians, float x, float y, float z) {
        return m.translate(.5f, .5f, .5f).rotate(radians, x, y, z).translate(-.5f, -.5f, -.5f);
    }

    private final Map<BlockPos, Rotation> rotations = new HashMap<>();

    private static final class Rotation {
        float value;
    }
}
