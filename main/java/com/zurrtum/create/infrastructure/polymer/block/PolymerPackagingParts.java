package com.zurrtum.create.infrastructure.polymer.block;

import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.model;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.part;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.rotateCentered;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.catnip.math.AngleHelper;
import com.zurrtum.create.catnip.math.VecHelper;
import com.zurrtum.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.zurrtum.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.zurrtum.create.content.logistics.factoryBoard.PanelSlot;
import com.zurrtum.create.content.logistics.factoryBoard.ServerFactoryPanelBehaviour;
import com.zurrtum.create.content.logistics.packagePort.frogport.FrogportBlockEntity;
import com.zurrtum.create.content.logistics.packagePort.postbox.PostboxBlock;
import com.zurrtum.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import com.zurrtum.create.content.logistics.packager.PackagerBlock;
import com.zurrtum.create.content.logistics.packager.PackagerBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

final class PolymerPackagingParts {
    private static final Identifier FROGPORT_BODY = model("package_frogport/body");
    private static final Identifier FROGPORT_HEAD = model("package_frogport/head");
    private static final Identifier FROGPORT_HEAD_GOGGLES = model("package_frogport/head_goggles");
    private static final Identifier FROGPORT_TONGUE = model("package_frogport/tongue");

    private static final Identifier POSTBOX_FLAG = model("package_postbox/flag");
    private static final Identifier PACKAGER_HATCH_OPEN = model("packager/hatch_open"),
            PACKAGER_HATCH_CLOSED = model("packager/hatch_closed"),
            PACKAGER_TRAY = model("packager/tray"),
            REPACKAGER_TRAY = model("repackager/tray");

    private static final Identifier FACTORY_PANEL = model("factory_gauge/panel");
    private static final Identifier FACTORY_PANEL_WITH_BULB = model("factory_gauge/panel_with_bulb");
    private static final Identifier FACTORY_PANEL_RESTOCKER = model("factory_gauge/panel_restocker");
    private static final Identifier FACTORY_PANEL_RESTOCKER_WITH_BULB =
            model("factory_gauge/panel_restocker_with_bulb");
    private static final Identifier FACTORY_BULB_LIGHT = model("factory_gauge/bulb_light");
    private static final Identifier FACTORY_BULB_RED = model("factory_gauge/bulb_red");

    private PolymerPackagingParts() {}

    static void registerAll() {
        PolymerStationParts.register();
        PolymerBlockParts.<PackagerBlockEntity>register(
                PolymerPackagingParts::packager, AllBlocks.PACKAGER, AllBlocks.REPACKAGER);
        PolymerBlockParts.<FrogportBlockEntity>register(PolymerPackagingParts::frogport, AllBlocks.PACKAGE_FROGPORT);

        List<Block> postboxes = new ArrayList<>();
        for (DyeColor color : DyeColor.values()) {
            postboxes.add(AllBlocks.POSTBOX.pick(color));
        }
        PolymerBlockParts.<PostboxBlockEntity>register(PolymerPackagingParts::postbox, postboxes.toArray(new Block[0]));

        PolymerBlockParts.<FactoryPanelBlockEntity>register(
                PolymerPackagingParts::factoryGauge, AllBlocks.FACTORY_GAUGE);
    }

    private static void packager(
            PackagerBlockEntity be, BlockState state, float time, List<PolymerBlockParts.Part> out) {
        var facing = state.getValue(PackagerBlock.FACING).getOpposite();
        Vec3 axis = Vec3.atLowerCornerOf(facing.getUnitVec3i());
        Vec3 trayOffset = axis.scale(be.getTrayOffset(.5f));
        boolean open = be.animationTicks > (be.animationInward ? 1 : 5)
                && be.animationTicks < PackagerBlockEntity.CYCLE - (be.animationInward ? 5 : 1);
        Matrix4f hatch = new Matrix4f()
                .translate((float) (axis.x * .49999), (float) (axis.y * .49999), (float) (axis.z * .49999))
                .translate(.5f, .5f, .5f)
                .rotateY(AngleHelper.rad(AngleHelper.horizontalAngle(facing)))
                .rotateX(AngleHelper.rad(AngleHelper.verticalAngle(facing)))
                .translate(-.5f, -.5f, -.5f);
        out.add(part(open ? PACKAGER_HATCH_OPEN : PACKAGER_HATCH_CLOSED, hatch).identified("packager:hatch"));
        Matrix4f tray = new Matrix4f()
                .translate((float) trayOffset.x, (float) trayOffset.y, (float) trayOffset.z)
                .translate(.5f, .5f, .5f)
                .rotateY(AngleHelper.rad(facing.toYRot()))
                .translate(-.5f, -.5f, -.5f);
        out.add(part(state.is(AllBlocks.PACKAGER) ? PACKAGER_TRAY : REPACKAGER_TRAY, tray)
                .identified("packager:tray"));
        var box = be.getRenderedBox();
        if (!box.isEmpty()) {
            Matrix4f pose = new Matrix4f()
                    .translate((float) trayOffset.x, (float) trayOffset.y, (float) trayOffset.z)
                    .translate(.5f, .5f, .5f)
                    .rotateY(AngleHelper.rad(facing.toYRot()))
                    .translate(0, .125f, 0)
                    .scale(1.49f);
            out.add(PolymerBlockParts.itemPart(box, pose).identified("packager:box"));
        }
    }

    private static void frogport(
            FrogportBlockEntity be, BlockState state, float time, List<PolymerBlockParts.Part> out) {
        Level level = be.getLevel();
        BlockPos pos = be.getBlockPos();
        boolean animating = be.isAnimationInProgress();
        boolean depositing = be.currentlyDepositing;

        Vec3 diff = Vec3.ZERO;
        float itemDistance = 0, itemScale = 0;
        float tongueLength;
        float headPitch;
        float headPitchModifier;
        float tonguePitch = 0;
        if (be.target != null) {
            diff = be.target
                    .getExactTargetLocation(be, level, pos)
                    .subtract(0, animating && depositing ? 0 : 0.75, 0)
                    .subtract(Vec3.atCenterOf(pos));
            tonguePitch = (float) Mth.atan2(diff.y, diff.multiply(1, 0, 1).length() + 3 / 16.0f) * Mth.RAD_TO_DEG;
            tongueLength = Math.max((float) diff.length(), 1);
            headPitch = Mth.clamp(tonguePitch * 2, 60, 100);
        } else {
            tongueLength = 0;
            headPitch = 80;
        }

        if (animating) {
            float progress = (float) be.animationProgress.getValue();
            if (depositing) {
                float itemModifier = (float) Math.max(0, 1 - Math.pow((progress - .25f) * 4 - 1, 4));
                itemDistance = Math.max(tongueLength * Math.min(1, (progress - .25f) * 3), tongueLength * itemModifier);
                itemScale = .25f + progress * .75f;
                tongueLength *= (float) Math.max(0, 1 - Math.pow((progress * 1.25 - 0.25) * 4 - 1, 4));
                headPitchModifier = (float) Math.max(0, 1 - Math.pow(progress * 1.25 * 2 - 1, 4));
            } else {
                tongueLength *= (float) Math.pow(Math.max(0, 1 - progress * 1.25), 5);
                itemDistance = tongueLength;
                itemScale = Math.max(.5f, 1 - progress * 1.25f);
                headPitchModifier = 1 - (float) Math.min(1, Math.max(0, (Math.pow(progress * 1.5, 2) - 0.5) * 2));
            }
        } else {
            tongueLength = 0;
            float anticipation = (float) be.anticipationProgress.getValue();
            headPitchModifier = anticipation > 0 ? (float) Math.max(0, 1 - Math.pow(anticipation * 2.5 - 1, 4)) : 0;
        }
        headPitch *= headPitchModifier;
        float openProgress = (float) be.manualOpenAnimationProgress.getValue();
        headPitch = Math.max(headPitch, openProgress * 60);
        tongueLength = Math.max(tongueLength, openProgress * 0.25f);
        float tongueScale = tongueLength / 0.4375f;

        if (animating && be.animatedPackage != null && itemScale >= .45f) {
            PolymerChainModels.PackageModels box = PolymerChainModels.box(be.animatedPackage);
            if (box != null) {
                Vec3 offset = diff.normalize().scale(itemDistance).add(0, depositing ? -.5625 : .1875, 0);
                Matrix4f pose = new Matrix4f()
                        .translate((float) offset.x, (float) offset.y, (float) offset.z)
                        .translate(.5f, .5f, .5f)
                        .scale(itemScale)
                        .translate(-.5f, -.5f, -.5f);
                out.add(part(box.box(), pose).identified("frogport:box"));
                if (depositing) out.add(part(box.rigging(), new Matrix4f(pose)).identified("frogport:rigging"));
            }
        }
        Quaternionf yawRotation = new Quaternionf().rotationY(Mth.DEG_TO_RAD * be.getYaw());
        Matrix4f yaw = rotateCentered(yawRotation);
        Quaternionf head = new Quaternionf().rotationX(Mth.DEG_TO_RAD * headPitch);
        Quaternionf tongue = new Quaternionf().rotationX(Mth.DEG_TO_RAD * tonguePitch);

        out.add(part(FROGPORT_BODY, new Matrix4f(yaw)));
        out.add(part(
                FROGPORT_TONGUE,
                new Matrix4f(yaw)
                        .translate(0.5f, 0.625f, 0.6875f)
                        .rotate(tongue)
                        .scale(1, 1, tongueScale)
                        .translate(-0.5f, -0.625f, -0.6875f)));
        out.add(part(
                be.goggles ? FROGPORT_HEAD_GOGGLES : FROGPORT_HEAD,
                new Matrix4f(yaw).mul(PolymerBlockParts.pivot(head, 0.5f, 0.625f, 0.6875f))));
    }

    private static void postbox(PostboxBlockEntity be, BlockState state, float time, List<PolymerBlockParts.Part> out) {
        float yaw = 180 - state.getValue(PostboxBlock.FACING).toYRot();
        out.add(part(POSTBOX_FLAG, rotateCentered(new Quaternionf().rotationY(Mth.DEG_TO_RAD * yaw))));
    }

    private static void factoryGauge(
            FactoryPanelBlockEntity be, BlockState state, float time, List<PolymerBlockParts.Part> out) {
        Matrix4f orientation = rotateCentered(new Quaternionf().rotationY(FactoryPanelBlock.getYRot(state)))
                .mul(rotateCentered(new Quaternionf().rotationX(FactoryPanelBlock.getXRot(state) + Mth.PI / 2)))
                .mul(rotateCentered(new Quaternionf().rotationY(Mth.PI)));

        for (ServerFactoryPanelBehaviour behaviour : be.panels.values()) {
            if (!behaviour.isActive()) continue;
            PanelSlot slot = behaviour.slot;
            Matrix4f panel = new Matrix4f(orientation).translate(slot.xOffset * 0.5f, 0, slot.yOffset * 0.5f);

            boolean restocker = behaviour.panelBE().restocker;
            boolean noBulb = behaviour.count == 0;
            Identifier plate;
            if (restocker) {
                plate = noBulb ? FACTORY_PANEL_RESTOCKER : FACTORY_PANEL_RESTOCKER_WITH_BULB;
            } else {
                plate = noBulb ? FACTORY_PANEL : FACTORY_PANEL_WITH_BULB;
            }
            out.add(part(plate, new Matrix4f(panel)));

            if (behaviour.getAmount() > 0) {
                Identifier bulb = behaviour.redstonePowered || behaviour.isMissingAddress()
                        ? FACTORY_BULB_RED
                        : FACTORY_BULB_LIGHT;
                out.add(part(bulb, new Matrix4f(panel)));
            }

            ItemStack filter = behaviour.getFilter();
            if (!filter.isEmpty()) {
                Vec3 centre = new Vec3(0.25 + slot.xOffset * 0.5, 1.5 / 16.0f, 0.25 + slot.yOffset * 0.5);
                centre = VecHelper.rotateCentered(centre, 180, Direction.Axis.Y);
                centre = VecHelper.rotateCentered(
                        centre, Mth.RAD_TO_DEG * FactoryPanelBlock.getXRot(state) + 90, Direction.Axis.X);
                centre = VecHelper.rotateCentered(
                        centre, Mth.RAD_TO_DEG * FactoryPanelBlock.getYRot(state), Direction.Axis.Y);
                boolean blockItem = filter.getItem() instanceof BlockItem;
                float size = (blockItem ? 1 : .5f) + 1 / 64f;
                Matrix4f pose = new Matrix4f()
                        .translate((float) centre.x, (float) centre.y, (float) centre.z)
                        .rotateY(FactoryPanelBlock.getYRot(state) + Mth.PI)
                        .rotateX(-FactoryPanelBlock.getXRot(state))
                        .scale(0.5f)
                        .scale(size)
                        .translate(0, 0, blockItem ? 0 : -.15f);
                out.add(PolymerBlockParts.itemPart(filter, pose).identified("factory:filter:" + slot.name()));
            }
        }
    }
}
