package com.zurrtum.create.infrastructure.polymer.block;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.catnip.math.AngleHelper;
import com.zurrtum.create.catnip.math.VecHelper;
import com.zurrtum.create.content.contraptions.actors.contraptionControls.ContraptionControlsBlockEntity;
import com.zurrtum.create.content.contraptions.actors.roller.RollerBlockEntity;
import com.zurrtum.create.content.contraptions.bearing.ClockworkBearingBlockEntity;
import com.zurrtum.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.zurrtum.create.content.contraptions.chassis.ChassisBlockEntity;
import com.zurrtum.create.content.contraptions.mounted.CartAssemblerBlockEntity;
import com.zurrtum.create.content.contraptions.piston.MechanicalPistonBlockEntity;
import com.zurrtum.create.content.contraptions.pulley.PulleyBlockEntity;
import com.zurrtum.create.content.fluids.pipes.SmartFluidPipeBlockEntity;
import com.zurrtum.create.content.kinetics.base.IRotate;
import com.zurrtum.create.content.kinetics.crank.ValveHandleBlockEntity;
import com.zurrtum.create.content.kinetics.deployer.DeployerBlockEntity;
import com.zurrtum.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.zurrtum.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.zurrtum.create.content.kinetics.saw.SawBlockEntity;
import com.zurrtum.create.content.kinetics.simpleRelays.AbstractSimpleShaftBlock;
import com.zurrtum.create.content.kinetics.speedController.SpeedControllerBlockEntity;
import com.zurrtum.create.content.kinetics.steamEngine.SteamEngineBlockEntity;
import com.zurrtum.create.content.logistics.chute.SmartChuteBlockEntity;
import com.zurrtum.create.content.logistics.crate.CreativeCrateBlockEntity;
import com.zurrtum.create.content.logistics.depot.EjectorBlockEntity;
import com.zurrtum.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.zurrtum.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.zurrtum.create.content.logistics.funnel.FunnelBlockEntity;
import com.zurrtum.create.content.logistics.itemHatch.ItemHatchBlockEntity;
import com.zurrtum.create.content.logistics.tunnel.BrassTunnelBlockEntity;
import com.zurrtum.create.content.processing.basin.BasinBlockEntity;
import com.zurrtum.create.content.redstone.diodes.BrassDiodeBlockEntity;
import com.zurrtum.create.content.redstone.link.RedstoneLinkBlock;
import com.zurrtum.create.content.redstone.link.ServerLinkBehaviour;
import com.zurrtum.create.content.redstone.smartObserver.SmartObserverBlockEntity;
import com.zurrtum.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;
import com.zurrtum.create.content.trains.observer.TrackObserverBlockEntity;
import com.zurrtum.create.foundation.blockEntity.SmartBlockEntity;
import com.zurrtum.create.foundation.blockEntity.behaviour.filtering.ServerFilteringBehaviour;
import com.zurrtum.create.foundation.blockEntity.behaviour.filtering.ServerSidedFilteringBehaviour;
import com.zurrtum.create.foundation.blockEntity.behaviour.scrollValue.ServerBrassDiodeScrollValueBehaviour;
import com.zurrtum.create.foundation.blockEntity.behaviour.scrollValue.ServerKineticScrollValueBehaviour;
import com.zurrtum.create.foundation.blockEntity.behaviour.scrollValue.ServerScrollOptionBehaviour;
import com.zurrtum.create.foundation.blockEntity.behaviour.scrollValue.ServerScrollValueBehaviour;
import com.zurrtum.create.foundation.blockEntity.behaviour.scrollValue.ServerValveScrollValueBehaviour;
import com.zurrtum.create.infrastructure.polymer.AdapterInteractions;
import com.zurrtum.create.infrastructure.polymer.control.*;
import com.zurrtum.create.infrastructure.polymer.effects.PolymerVanillaEffects;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerMachineDialogs;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerSettingsBoard;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerGogglesOverlay;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import dev.veeda.createpoly.assets.CreateIds;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class PolymerBlockControls {
    private static final Map<Class<?>, Function<SmartBlockEntity, ValueBoxTransform>> FILTERS = new HashMap<>();
    private static final Map<Class<?>, Function<SmartBlockEntity, ValueBoxTransform>> VALUES = new HashMap<>();
    private static final Identifier FRAME_EMPTY = PolymerBlockParts.model("polymer/control_hover_4");
    private static final Identifier FRAME_FILLED = PolymerBlockParts.model("polymer/control_hover_6");

    private PolymerBlockControls() {}

    public static void register() {
        registerGeometry();
        AdapterInteractions.useBlock((player, level, hand, hit) -> {
            if (!(player instanceof ServerPlayer serverPlayer)
                    || !(level instanceof ServerLevel serverLevel)
                    || player.isSpectator()
                    || !player.mayBuild()
                    || player.isShiftKeyDown()
                    || !player.mayInteract(serverLevel, hit.getBlockPos())) return InteractionResult.PASS;
            if (!(level.getBlockEntity(hit.getBlockPos()) instanceof SmartBlockEntity be))
                return InteractionResult.PASS;
            ItemStack held = player.getItemInHand(hand);
            if (!PolymerNativeClients.isNative(serverPlayer)
                    && be.getBlockState().is(AllBlocks.REDSTONE_LINK)
                    && !held.is(AllItems.LINKED_CONTROLLER)
                    && !held.is(AllItems.WRENCH)
                    && be.getBehaviour(ServerLinkBehaviour.TYPE) instanceof ServerLinkBehaviour link) {
                Boolean first = redstoneLinkFrequencySlot(
                        be.getBlockState(),
                        hit.getDirection(),
                        hit.getLocation().subtract(Vec3.atLowerCornerOf(hit.getBlockPos())));
                if (first != null) {
                    link.setFrequency(first, held);
                    PolymerVanillaEffects.sourceSound(
                            serverLevel,
                            hit.getBlockPos(),
                            SoundEvents.ITEM_FRAME_ADD_ITEM,
                            SoundSource.BLOCKS,
                            .25f,
                            .1f);
                    PolymerBlockParts.tick(be);
                    return InteractionResult.SUCCESS;
                }
            }
            if (!PolymerNativeClients.isNative(serverPlayer)
                    && be instanceof FactoryPanelBlockEntity panelEntity
                    && !held.is(AllItems.FACTORY_GAUGE)) {
                var slot = FactoryPanelBlock.getTargetedSlot(hit.getBlockPos(), be.getBlockState(), hit.getLocation());
                var panel = slot == null ? null : panelEntity.panels.get(slot);
                if (panel != null && panel.active) {
                    panel.onShortInteract(player, hand, hit.getDirection(), hit);
                    PolymerBlockParts.tick(be);
                    return InteractionResult.SUCCESS;
                }
            }
            boolean wrenchBox = be instanceof ChassisBlockEntity;
            if (held.is(AllItems.WRENCH) != wrenchBox
                    || held.is(AllItems.MECHANICAL_ARM)
                    || held.is(AllItems.SUPER_GLUE)) return InteractionResult.PASS;
            Vec3 local = hit.getLocation().subtract(Vec3.atLowerCornerOf(hit.getBlockPos()));
            ValueBoxTransform filterSlot = geometry(FILTERS, be);
            ServerFilteringBehaviour filter = be.getBehaviour(ServerFilteringBehaviour.TYPE);
            if (filter != null && filter.isActive() && filter.mayInteract(player) && filterSlot != null) {
                setSide(filterSlot, hit.getDirection());
                ServerFilteringBehaviour actual = sidedFilter(filter, hit.getDirection());
                if (actual != null
                        && actual.isActive()
                        && actual.mayInteract(player)
                        && actual.canShortInteract(held)
                        && hits(filterSlot, be, hit.getDirection(), local)) {
                    filter.onShortInteract(player, hand, hit.getDirection(), hit);
                    PolymerBlockParts.tick(be);
                    return InteractionResult.SUCCESS;
                }
            }
            ValueBoxTransform valueSlot = geometry(VALUES, be);
            if (valueSlot != null && PolymerMachineDialogs.supportsValueSettings(be)) {
                setSide(valueSlot, hit.getDirection());
                if (hits(valueSlot, be, hit.getDirection(), local)) {
                    PolymerMachineDialogs.openValueSettings(serverPlayer, serverLevel, hit.getBlockPos(), be);
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        });
    }

    static Boolean redstoneLinkFrequencySlot(BlockState state, Direction hitFace, Vec3 localHit) {
        Direction facing = state.getValue(RedstoneLinkBlock.FACING);
        for (boolean first : new boolean[] {true, false}) {
            Vec3 location;
            if (facing.getAxis().isHorizontal()) {
                location = VecHelper.voxelSpace(8, 5.5f, 3.01f);
                if (first) location = location.add(0, 5 / 16.0f, 0);
                location = VecHelper.rotateCentered(location, AngleHelper.horizontalAngle(facing), Direction.Axis.Y);
            } else {
                location = VecHelper.voxelSpace(8, 3.01f, 5.5f);
                if (first) location = location.add(0, 0, 5 / 16.0f);
                location = VecHelper.rotateCentered(location, facing == Direction.DOWN ? 180 : 0, Direction.Axis.X);
            }
            Vec3 point = hitFace != facing
                    ? localHit
                    : switch (facing.getAxis()) {
                        case X -> new Vec3(location.x, localHit.y, localHit.z);
                        case Y -> new Vec3(localHit.x, location.y, localHit.z);
                        case Z -> new Vec3(localHit.x, localHit.y, location.z);
                    };
            if (point.distanceTo(location) < .4975f / 3.5f) return first;
        }
        return null;
    }

    private static void registerGeometry() {
        registerTypes();
    }

    static void collect(BlockEntity blockEntity, BlockState state, List<PolymerBlockParts.Part> out) {
        if (!(blockEntity instanceof SmartBlockEntity be)) return;
        ServerFilteringBehaviour filter = be.getBehaviour(ServerFilteringBehaviour.TYPE);
        ValueBoxTransform filterSlot = geometry(FILTERS, be);
        if (filter != null && filter.isActive() && filterSlot != null) {
            for (Direction side : sides(filterSlot)) {
                setSide(filterSlot, side);
                ServerFilteringBehaviour actual = sidedFilter(filter, side);
                if (actual == null || !actual.isActive() || !filterSlot.shouldRender(state)) continue;
                Matrix4f pose = pose(filterSlot, state);
                ItemStack stack = actual.getFilter();
                if (!stack.isEmpty()) {
                    float size = (stack.getItem() instanceof BlockItem ? 1 : .5f) + 1 / 64f;
                    out.add(PolymerBlockParts.itemPart(
                                    stack, new Matrix4f(pose).scale(size).translate(0, 0, filterItemOffset(stack)))
                            .identified(side)
                            .near(10));
                }
            }
        }
    }

    public static void collectHover(
            BlockEntity blockEntity,
            BlockState state,
            ServerPlayer player,
            BlockHitResult hit,
            List<PolymerBlockParts.Part> out) {
        if (!(blockEntity instanceof SmartBlockEntity be) || player.isShiftKeyDown()) return;
        Vec3 local = hit.getLocation().subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
        ValueBoxTransform filterSlot = geometry(FILTERS, be);
        ServerFilteringBehaviour filter = be.getBehaviour(ServerFilteringBehaviour.TYPE);
        if (filterSlot != null && filter != null && filter.isActive() && filter.mayInteract(player)) {
            setSide(filterSlot, hit.getDirection());
            ServerFilteringBehaviour actual = sidedFilter(filter, hit.getDirection());
            if (actual != null
                    && actual.isActive()
                    && actual.mayInteract(player)
                    && hits(filterSlot, be, hit.getDirection(), local)) {
                frame(out, pose(filterSlot, state), !actual.getFilter().isEmpty());
            }
        }
        ServerScrollValueBehaviour value = be.getBehaviour(ServerScrollValueBehaviour.TYPE);
        ValueBoxTransform valueSlot = geometry(VALUES, be);
        if (value == null || !value.acceptsValueSettings() || valueSlot == null) return;
        for (Direction side : sides(valueSlot)) {
            setSide(valueSlot, side);
            if (valueSlot instanceof ValueBoxTransform.Sided && side != hit.getDirection()) continue;
            if (!hits(valueSlot, be, hit.getDirection(), local)) continue;
            Matrix4f pose = pose(valueSlot, state);
            frame(out, pose, true);
            Matrix4f font = new Matrix4f(pose).scale(-valueSlot.getFontScale());
            int icon = PolymerSettingsBoard.iconCodepoint(be, value);
            if (value instanceof ServerScrollOptionBehaviour<?> && icon >= 0) {
                Matrix4f iconPose = new Matrix4f(font)
                        .scale(32)
                        .translate(-0.5f, -0.5f, 5 / 32f)
                        .scale(1 / 16f)
                        .translate(8, -7, 0);
                out.add(PolymerBlockParts.textPart(
                        Component.literal(Character.toString(icon))
                                .withStyle(style -> style.withColor(0xFFFFFF)
                                        .withFont(new FontDescription.Resource(CreateIds.id("vsb_7")))),
                        iconPose));
                continue;
            }
            String label = formatValue(be, value);
            int width = PolymerGogglesOverlay.width(label);
            float numberScale = 9f / Math.max(1, width);
            boolean singleDigit = width < 10;
            if (singleDigit) numberScale /= 2;
            float margin = (width - 9) / 2f;
            Matrix4f text = new Matrix4f(font)
                    .scale(3, 3, 1)
                    .translate(-4, -3.75f, 5)
                    .scale(numberScale)
                    .translate(singleDigit ? width / 2f : 0, singleDigit ? -margin : margin, 0)
                    .translate(width / 2f, 0, 0);
            out.add(PolymerBlockParts.textPart(Component.literal(label).withColor(0xEDEDED), text));
        }
    }

    private static String formatValue(SmartBlockEntity be, ServerScrollValueBehaviour value) {
        int v = value.getValue();
        if (value instanceof ServerBrassDiodeScrollValueBehaviour)
            return v < 60 ? v + "t" : v < 20 * 60 ? v / 20 + "s" : v / 20 / 60 + "m";
        if (value instanceof ServerKineticScrollValueBehaviour) return Integer.toString(Math.abs(v));
        if (value instanceof ServerValveScrollValueBehaviour) return Math.abs(v) + "°";
        if (be instanceof EjectorBlockEntity) return v == 0 ? "*" : Integer.toString(v);
        return Integer.toString(v);
    }

    static ServerFilteringBehaviour sidedFilter(ServerFilteringBehaviour filter, Direction side) {
        return filter instanceof ServerSidedFilteringBehaviour sided ? sided.get(side) : filter;
    }

    private static ValueBoxTransform geometry(
            Map<Class<?>, Function<SmartBlockEntity, ValueBoxTransform>> map, SmartBlockEntity be) {
        for (Class<?> type = be.getClass(); type != null; type = type.getSuperclass()) {
            var factory = map.get(type);
            if (factory != null) return factory.apply(be);
        }
        return null;
    }

    private static Direction[] sides(ValueBoxTransform slot) {
        return slot instanceof ValueBoxTransform.Sided ? Direction.values() : new Direction[] {Direction.UP};
    }

    private static void setSide(ValueBoxTransform slot, Direction side) {
        if (slot instanceof ValueBoxTransform.Sided sided) sided.fromSide(side);
    }

    static Matrix4f pose(ValueBoxTransform slot, BlockState state) {
        ControlPose pose = new ControlPose();
        slot.transform(state, pose);
        return pose.matrix;
    }

    private static float filterItemOffset(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item)) return -.15f;
        var block = item.getBlock();
        return block instanceof AbstractSimpleShaftBlock
                        || block instanceof FenceBlock
                        || block.builtInRegistryHolder().is(BlockTags.BUTTONS)
                        || block == Blocks.END_ROD
                ? -.1f
                : 0;
    }

    private static boolean hits(ValueBoxTransform slot, SmartBlockEntity be, Direction face, Vec3 local) {
        if (!slot.shouldRender(be.getBlockState())) return false;
        Vec3 offset = slot.getLocalOffset(be.getBlockState());
        if (offset == null) return false;
        Vec3 projected = switch (face.getAxis()) {
            case X -> new Vec3(offset.x, local.y, local.z);
            case Y -> new Vec3(local.x, offset.y, local.z);
            case Z -> new Vec3(local.x, local.y, offset.z);
        };
        return slot.testHit(be.getLevel(), be.getBlockPos(), be.getBlockState(), projected);
    }

    static void frame(List<PolymerBlockParts.Part> out, Matrix4f pose, boolean wide) {
        out.add(PolymerBlockParts.part(
                wide ? FRAME_FILLED : FRAME_EMPTY,
                new Matrix4f(pose).scale(-2.01f, -2.01f, 2.01f).translate(-.5f, -.5f, -.5f / 16)));
    }

    private static void registerTypes() {
        FILTERS.put(SawBlockEntity.class, be -> new SawFilterSlot());
        FILTERS.put(BasinBlockEntity.class, be -> new BasinValueBox());
        FILTERS.put(FunnelBlockEntity.class, be -> new FunnelFilterSlotPositioning());
        FILTERS.put(SmartChuteBlockEntity.class, be -> new SmartChuteFilterSlotPositioning());
        FILTERS.put(DeployerBlockEntity.class, be -> new DeployerFilterSlot());
        FILTERS.put(SmartFluidPipeBlockEntity.class, be -> new SmartPipeFilterSlot());
        FILTERS.put(SmartObserverBlockEntity.class, be -> new FilteredDetectorFilterSlot(false));
        FILTERS.put(ThresholdSwitchBlockEntity.class, be -> new FilteredDetectorFilterSlot(true));
        FILTERS.put(ContraptionControlsBlockEntity.class, be -> new ControlsSlot());
        FILTERS.put(CreativeCrateBlockEntity.class, be -> new CrateSlot());
        FILTERS.put(TrackObserverBlockEntity.class, be -> new ObserverFilterSlot());
        FILTERS.put(RollerBlockEntity.class, be -> new RollerValueBox(3));
        FILTERS.put(ItemHatchBlockEntity.class, be -> new HatchFilterSlot());
        FILTERS.put(BrassTunnelBlockEntity.class, be -> new BrassTunnelFilterSlot());
        VALUES.put(CreativeMotorBlockEntity.class, be -> new MotorValueBox());
        VALUES.put(SpeedControllerBlockEntity.class, be -> new ControllerValueBoxTransform());
        VALUES.put(CartAssemblerBlockEntity.class, be -> new CartAssemblerValueBoxTransform());
        VALUES.put(BrassDiodeBlockEntity.class, be -> new BrassDiodeScrollSlot());
        VALUES.put(ArmBlockEntity.class, be -> new SelectionModeValueBox());
        VALUES.put(ValveHandleBlockEntity.class, be -> new ValveHandleValueBox());
        VALUES.put(BrassTunnelBlockEntity.class, be -> new BrassTunnelModeSlot());
        VALUES.put(RollerBlockEntity.class, be -> new RollerValueBox(-3));
        VALUES.put(SteamEngineBlockEntity.class, be -> new SteamEngineValueBox());
        VALUES.put(EjectorBlockEntity.class, be -> new EjectorSlot((EjectorBlockEntity) be));
        VALUES.put(ChassisBlockEntity.class, be -> new CenteredSideValueBoxTransform());
        VALUES.put(
                PulleyBlockEntity.class,
                be -> new CenteredSideValueBoxTransform((state, side) -> side == Direction.UP));
        VALUES.put(
                MechanicalBearingBlockEntity.class,
                be -> new DirectionalExtenderScrollOptionSlot((state, side) -> side.getAxis()
                        != state.getValue(BlockStateProperties.FACING).getAxis()));
        VALUES.put(
                ClockworkBearingBlockEntity.class,
                be -> new DirectionalExtenderScrollOptionSlot((state, side) -> side.getAxis()
                        != state.getValue(BlockStateProperties.FACING).getAxis()));
        VALUES.put(
                MechanicalPistonBlockEntity.class,
                be -> new DirectionalExtenderScrollOptionSlot((state, side) -> side.getAxis()
                                != state.getValue(BlockStateProperties.FACING).getAxis()
                        && side.getAxis() != ((IRotate) state.getBlock()).getRotationAxis(state)));
    }
}
