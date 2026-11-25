package com.zurrtum.create.infrastructure.polymer.hud;

import com.zurrtum.create.AllBlockEntityTypes;
import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.Create;
import com.zurrtum.create.api.equipment.goggles.IProxyHoveringInformation;
import com.zurrtum.create.api.stress.BlockStressValues;
import com.zurrtum.create.content.contraptions.AssemblyException;
import com.zurrtum.create.content.contraptions.bearing.BearingBlock;
import com.zurrtum.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.zurrtum.create.content.contraptions.gantry.GantryCarriageBlockEntity;
import com.zurrtum.create.content.contraptions.mounted.CartAssemblerBlockEntity;
import com.zurrtum.create.content.contraptions.piston.LinearActuatorBlockEntity;
import com.zurrtum.create.content.decoration.steamWhistle.WhistleBlockEntity;
import com.zurrtum.create.content.equipment.goggles.GogglesItem;
import com.zurrtum.create.content.fluids.drain.ItemDrainBlockEntity;
import com.zurrtum.create.content.fluids.hosePulley.HosePulleyBlockEntity;
import com.zurrtum.create.content.fluids.spout.SpoutBlockEntity;
import com.zurrtum.create.content.fluids.tank.BoilerData;
import com.zurrtum.create.content.fluids.tank.FluidTankBlockEntity;
import com.zurrtum.create.content.kinetics.base.IRotate.SpeedLevel;
import com.zurrtum.create.content.kinetics.base.IRotate.StressImpact;
import com.zurrtum.create.content.kinetics.base.KineticBlockEntity;
import com.zurrtum.create.content.kinetics.deployer.DeployerBlockEntity;
import com.zurrtum.create.content.kinetics.gauge.SpeedGaugeBlockEntity;
import com.zurrtum.create.content.kinetics.gauge.StressGaugeBlockEntity;
import com.zurrtum.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.zurrtum.create.content.kinetics.steamEngine.SteamEngineBlockEntity;
import com.zurrtum.create.content.logistics.chute.ChuteBlockEntity;
import com.zurrtum.create.content.logistics.packagePort.frogport.FrogportBlockEntity;
import com.zurrtum.create.content.logistics.packager.InventorySummary;
import com.zurrtum.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.zurrtum.create.content.logistics.tunnel.BrassTunnelBlockEntity;
import com.zurrtum.create.content.processing.basin.BasinBlockEntity;
import com.zurrtum.create.content.redstone.analogLever.AnalogLeverBlockEntity;
import com.zurrtum.create.foundation.fluid.FluidHelper;
import com.zurrtum.create.infrastructure.fluids.FluidInventory;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import com.zurrtum.create.infrastructure.polymer.PolymerReach;
import java.text.BreakIterator;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

final class PolymerGogglesContent {
    enum Kind {
        KINETIC,
        GENERATING,
        SPEED_GAUGE,
        STRESS_GAUGE,
        ANALOG_LEVER,
        BASIN,
        BRASS_TUNNEL,
        CART_ASSEMBLER,
        CHUTE,
        DEPLOYER,
        FLUID_TANK,
        GANTRY_CARRIAGE,
        HOSE_PULLEY,
        ITEM_DRAIN,
        ARM,
        MECHANICAL_BEARING,
        LINEAR_ACTUATOR,
        FROGPORT,
        POWERED_SHAFT,
        SPOUT,
        STEAM_ENGINE,
        WHISTLE,
        STOCK_TICKER
    }

    private static final Map<BlockEntityType<?>, Kind> KINDS = kinds();

    private PolymerGogglesContent() {}

    private static Map<BlockEntityType<?>, Kind> kinds() {
        Map<BlockEntityType<?>, Kind> map = new IdentityHashMap<>();
        for (BlockEntityType<?> type : List.of(
                AllBlockEntityTypes.ADJUSTABLE_CHAIN_GEARSHIFT,
                AllBlockEntityTypes.BACKTANK,
                AllBlockEntityTypes.BELT,
                AllBlockEntityTypes.BRACKETED_KINETIC,
                AllBlockEntityTypes.CHAIN_CONVEYOR,
                AllBlockEntityTypes.CLUTCH,
                AllBlockEntityTypes.CLOCKWORK_BEARING,
                AllBlockEntityTypes.CRUSHING_WHEEL,
                AllBlockEntityTypes.CUCKOO_CLOCK,
                AllBlockEntityTypes.DRILL,
                AllBlockEntityTypes.ELEVATOR_PULLEY,
                AllBlockEntityTypes.ENCASED_COGWHEEL,
                AllBlockEntityTypes.ENCASED_FAN,
                AllBlockEntityTypes.ENCASED_LARGE_COGWHEEL,
                AllBlockEntityTypes.FLAP_DISPLAY,
                AllBlockEntityTypes.FLUID_VALVE,
                AllBlockEntityTypes.GANTRY_SHAFT,
                AllBlockEntityTypes.GEARBOX,
                AllBlockEntityTypes.GEARSHIFT,
                AllBlockEntityTypes.MECHANICAL_CRAFTER,
                AllBlockEntityTypes.MECHANICAL_MIXER,
                AllBlockEntityTypes.MECHANICAL_PRESS,
                AllBlockEntityTypes.MECHANICAL_PUMP,
                AllBlockEntityTypes.MILLSTONE,
                AllBlockEntityTypes.ROTATION_SPEED_CONTROLLER,
                AllBlockEntityTypes.SAW,
                AllBlockEntityTypes.SEQUENCED_GEARSHIFT,
                AllBlockEntityTypes.TURNTABLE,
                AllBlockEntityTypes.WEIGHTED_EJECTOR)) map.put(type, Kind.KINETIC);
        for (BlockEntityType<?> type : List.of(
                AllBlockEntityTypes.HAND_CRANK,
                AllBlockEntityTypes.LARGE_WATER_WHEEL,
                AllBlockEntityTypes.MOTOR,
                AllBlockEntityTypes.VALVE_HANDLE,
                AllBlockEntityTypes.WATER_WHEEL)) map.put(type, Kind.GENERATING);
        map.put(AllBlockEntityTypes.SPEEDOMETER, Kind.SPEED_GAUGE);
        map.put(AllBlockEntityTypes.STRESSOMETER, Kind.STRESS_GAUGE);
        map.put(AllBlockEntityTypes.ANALOG_LEVER, Kind.ANALOG_LEVER);
        map.put(AllBlockEntityTypes.BASIN, Kind.BASIN);
        map.put(AllBlockEntityTypes.BRASS_TUNNEL, Kind.BRASS_TUNNEL);
        map.put(AllBlockEntityTypes.CART_ASSEMBLER, Kind.CART_ASSEMBLER);
        map.put(AllBlockEntityTypes.CHUTE, Kind.CHUTE);
        map.put(AllBlockEntityTypes.SMART_CHUTE, Kind.CHUTE);
        map.put(AllBlockEntityTypes.DEPLOYER, Kind.DEPLOYER);
        map.put(AllBlockEntityTypes.FLUID_TANK, Kind.FLUID_TANK);
        map.put(AllBlockEntityTypes.GANTRY_PINION, Kind.GANTRY_CARRIAGE);
        map.put(AllBlockEntityTypes.HOSE_PULLEY, Kind.HOSE_PULLEY);
        map.put(AllBlockEntityTypes.ITEM_DRAIN, Kind.ITEM_DRAIN);
        map.put(AllBlockEntityTypes.MECHANICAL_ARM, Kind.ARM);
        map.put(AllBlockEntityTypes.MECHANICAL_BEARING, Kind.MECHANICAL_BEARING);
        map.put(AllBlockEntityTypes.WINDMILL_BEARING, Kind.MECHANICAL_BEARING);
        map.put(AllBlockEntityTypes.MECHANICAL_PISTON, Kind.LINEAR_ACTUATOR);
        map.put(AllBlockEntityTypes.ROPE_PULLEY, Kind.LINEAR_ACTUATOR);
        map.put(AllBlockEntityTypes.PACKAGE_FROGPORT, Kind.FROGPORT);
        map.put(AllBlockEntityTypes.POWERED_SHAFT, Kind.POWERED_SHAFT);
        map.put(AllBlockEntityTypes.SPOUT, Kind.SPOUT);
        map.put(AllBlockEntityTypes.STEAM_ENGINE, Kind.STEAM_ENGINE);
        map.put(AllBlockEntityTypes.STEAM_WHISTLE, Kind.WHISTLE);
        map.put(AllBlockEntityTypes.STOCK_TICKER, Kind.STOCK_TICKER);
        return map;
    }

    private static boolean hasGoggle(Kind kind) {
        return switch (kind) {
            case CART_ASSEMBLER, GANTRY_CARRIAGE, FROGPORT, STOCK_TICKER -> false;
            default -> true;
        };
    }

    private static boolean hasHover(Kind kind) {
        return switch (kind) {
            case KINETIC,
                    GENERATING,
                    SPEED_GAUGE,
                    STRESS_GAUGE,
                    DEPLOYER,
                    HOSE_PULLEY,
                    ARM,
                    MECHANICAL_BEARING,
                    LINEAR_ACTUATOR,
                    POWERED_SHAFT,
                    FROGPORT,
                    STOCK_TICKER -> true;
            default -> false;
        };
    }

    static List<Component> tooltip(ServerPlayer player) {
        BlockHitResult blockHit = PolymerReach.targetBlock(player);
        if (blockHit == null) return List.of();
        ServerLevel level = player.level();
        BlockPos pos = blockHit.getBlockPos();
        if (!level.isLoaded(pos)) return List.of();
        BlockState targeted = level.getBlockState(pos);
        if (targeted.getBlock() instanceof IProxyHoveringInformation proxy)
            pos = proxy.getInformationSource(level, pos, targeted);
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return List.of();
        Kind kind = KINDS.get(be.getType());
        if (kind == null) return List.of();

        boolean sneaking = player.isShiftKeyDown();
        boolean wearing = GogglesItem.isWearingGoggles(player);
        boolean hasGoggle = hasGoggle(kind), hasHover = hasHover(kind);
        boolean goggleAdded = false, hoverAdded = false;
        List<Component> tooltip = new ArrayList<>();
        if (hasGoggle && wearing) goggleAdded = goggle(kind, be, tooltip, sneaking);
        if (hasHover) {
            if (!tooltip.isEmpty()) tooltip.add(Component.empty());
            hoverAdded = hover(kind, be, tooltip, sneaking, player);
            if (goggleAdded && !hoverAdded) tooltip.removeLast();
        }
        AssemblyException exception = assemblyException(kind, be);
        if (exception != null) {
            if (!tooltip.isEmpty()) tooltip.add(Component.empty());
            goggles(tooltip, 0, lang("gui.assembly.exception").withStyle(ChatFormatting.GOLD));
            for (String line : exception.component.getString().split("\n"))
                for (Component part : cut(line)) goggles(tooltip, 0, part.copy());
            hasHover = hoverAdded = true;
        }
        if (hasGoggle && !goggleAdded && hasHover && !hoverAdded) return List.of();
        if (tooltip.isEmpty()) return List.of();
        int max = PolymerGogglesOverlay.MAX_LINES;
        return tooltip.size() > max ? List.copyOf(tooltip.subList(0, max)) : List.copyOf(tooltip);
    }

    private static @Nullable AssemblyException assemblyException(Kind kind, BlockEntity be) {
        return switch (kind) {
            case CART_ASSEMBLER -> ((CartAssemblerBlockEntity) be).getLastAssemblyException();
            case GANTRY_CARRIAGE -> ((GantryCarriageBlockEntity) be).getLastAssemblyException();
            case MECHANICAL_BEARING -> ((MechanicalBearingBlockEntity) be).getLastAssemblyException();
            case LINEAR_ACTUATOR -> ((LinearActuatorBlockEntity) be).getLastAssemblyException();
            default -> null;
        };
    }

    private static boolean goggle(Kind kind, BlockEntity be, List<Component> tooltip, boolean sneaking) {
        switch (kind) {
            case KINETIC, HOSE_PULLEY, ARM, LINEAR_ACTUATOR -> {
                boolean added = kineticGoggles((KineticBlockEntity) be, tooltip);
                if (kind == Kind.HOSE_PULLEY && ((HosePulleyBlockEntity) be).infinite)
                    hint(tooltip, "hint.hose_pulley");
                return added;
            }
            case GENERATING, MECHANICAL_BEARING -> {
                return generatingGoggles((KineticBlockEntity) be, tooltip);
            }
            case POWERED_SHAFT -> {
                return false;
            }
            case STEAM_ENGINE -> {
                var shaft = ((SteamEngineBlockEntity) be).getShaft();
                return shaft != null && generatingGoggles(shaft, tooltip);
            }
            case SPEED_GAUGE -> {
                SpeedGaugeBlockEntity gauge = (SpeedGaugeBlockEntity) be;
                goggles(tooltip, 0, lang("gui.gauge.info_header"));
                goggles(tooltip, 0, lang("gui.speedometer.title").withStyle(ChatFormatting.GRAY));
                goggles(tooltip, 0, speedText(gauge.getSpeed(), gauge.isOverStressed()));
                return true;
            }
            case STRESS_GAUGE -> {
                return stressGauge((StressGaugeBlockEntity) be, tooltip);
            }
            case ANALOG_LEVER -> {
                goggles(tooltip, 0, lang("tooltip.analogStrength", ((AnalogLeverBlockEntity) be).getState()));
                return true;
            }
            case BASIN -> {
                return basin((BasinBlockEntity) be, tooltip);
            }
            case BRASS_TUNNEL -> {
                List<ItemStack> stacks = ((BrassTunnelBlockEntity) be).grabAllStacksOfGroup(true);
                if (stacks.isEmpty()) return false;
                goggles(tooltip, 0, lang("tooltip.brass_tunnel.contains").withStyle(ChatFormatting.WHITE));
                for (ItemStack item : stacks)
                    goggles(
                            tooltip,
                            0,
                            lang(
                                            "tooltip.brass_tunnel.contains_entry",
                                            item.getHoverName().getString(),
                                            item.getCount())
                                    .withStyle(ChatFormatting.GRAY));
                goggles(tooltip, 0, lang("tooltip.brass_tunnel.retrieve").withStyle(ChatFormatting.DARK_GRAY));
                return true;
            }
            case CHUTE -> {
                return chute((ChuteBlockEntity) be, tooltip);
            }
            case DEPLOYER -> {
                var deployer = (DeployerBlockEntity) be;
                goggles(tooltip, 0, lang("tooltip.deployer.header"));
                goggles(
                        tooltip,
                        0,
                        lang("tooltip.deployer."
                                        + (deployer.mode == DeployerBlockEntity.Mode.USE ? "using" : "punching"))
                                .withStyle(ChatFormatting.YELLOW));
                if (!deployer.heldItem.isEmpty())
                    goggles(
                            tooltip,
                            0,
                            lang(
                                            "tooltip.deployer.contains",
                                            deployer.heldItem.getHoverName(),
                                            deployer.heldItem.getCount())
                                    .withStyle(ChatFormatting.GREEN));
                float stressAtBase = deployer.calculateStressApplied();
                if (StressImpact.isEnabled() && !Mth.equal(stressAtBase, 0)) {
                    tooltip.add(Component.empty());
                    stressImpact(deployer, tooltip, stressAtBase);
                }
                return true;
            }
            case FLUID_TANK -> {
                FluidTankBlockEntity controller = ((FluidTankBlockEntity) be).getControllerBE();
                if (controller == null) return false;
                if (boiler(controller.boiler, tooltip, controller.getTotalTankSize())) return true;
                return fluids(
                        tooltip,
                        FluidHelper.getFluidInventory(be.getLevel(), controller.getBlockPos(), null, be, null));
            }
            case ITEM_DRAIN -> {
                return fluids(tooltip, ((ItemDrainBlockEntity) be).internalTank.getCapability());
            }
            case SPOUT -> {
                return fluids(tooltip, ((SpoutBlockEntity) be).tank.getCapability());
            }
            case WHISTLE -> {
                String[] pitches = lang("generic.notes").getString().split(";");
                int pitch = ((WhistleBlockEntity) be).pitch;
                goggles(tooltip, 0, lang("generic.pitch", pitches[pitch % pitches.length]));
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static boolean kineticGoggles(KineticBlockEntity be, List<Component> tooltip) {
        if (!StressImpact.isEnabled()) return false;
        float stressAtBase = be.calculateStressApplied();
        if (Mth.equal(stressAtBase, 0)) return false;
        goggles(tooltip, 0, lang("gui.goggles.kinetic_stats"));
        stressImpact(be, tooltip, stressAtBase);
        return true;
    }

    private static void stressImpact(KineticBlockEntity be, List<Component> tooltip, float stressAtBase) {
        goggles(tooltip, 0, lang("tooltip.stressImpact").withStyle(ChatFormatting.GRAY));
        goggles(tooltip, 1, atCurrentSpeed(stressAtBase * Math.abs(be.getTheoreticalSpeed())));
    }

    private static boolean generatingGoggles(KineticBlockEntity be, List<Component> tooltip) {
        if (!StressImpact.isEnabled()) return false;
        boolean added = kineticGoggles(be, tooltip);
        float stressBase = be.calculateAddedStressCapacity();
        if (Mth.equal(stressBase, 0)) return added;
        goggles(tooltip, 0, lang("gui.goggles.generator_stats"));
        goggles(tooltip, 0, lang("tooltip.capacityProvided").withStyle(ChatFormatting.GRAY));
        float speed = be.getTheoreticalSpeed();
        if (speed != be.getGeneratedSpeed() && speed != 0) stressBase *= be.getGeneratedSpeed() / speed;
        goggles(tooltip, 1, atCurrentSpeed(Math.abs(stressBase * speed)));
        return true;
    }

    private static boolean stressGauge(StressGaugeBlockEntity gauge, List<Component> tooltip) {
        if (!StressImpact.isEnabled()) return false;
        goggles(tooltip, 0, lang("gui.gauge.info_header"));
        double capacity = gauge.getNetworkCapacity();
        float networkStress = gauge.getNetworkStress();
        double fraction = networkStress / (capacity == 0 ? 1 : capacity);
        goggles(tooltip, 0, lang("gui.stressometer.title").withStyle(ChatFormatting.GRAY));
        if (gauge.getTheoreticalSpeed() == 0) {
            goggles(
                    tooltip,
                    0,
                    Component.literal(progressBar(3, 0))
                            .append(lang("gui.stressometer.no_rotation"))
                            .withStyle(ChatFormatting.DARK_GRAY));
            return true;
        }
        StressImpact impact = StressImpact.of(fraction);
        goggles(
                tooltip,
                0,
                Component.literal(progressBar(3, Math.min(impact.ordinal() + 1, 3)))
                        .append(lang("tooltip.stressImpact." + impact.name().toLowerCase(Locale.ROOT)))
                        .append(String.format(" (%s%%) ", (int) (fraction * 100)))
                        .withStyle(impact.getRelativeColor()));
        goggles(tooltip, 0, lang("gui.stressometer.capacity").withStyle(ChatFormatting.GRAY));
        double remaining = capacity - networkStress;
        MutableComponent tip = Component.literal(number(remaining))
                .append(lang("generic.unit.stress"))
                .withStyle(impact.getRelativeColor());
        if (remaining != capacity)
            tip.append(Component.literal(" / ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(number(capacity))
                            .append(lang("generic.unit.stress"))
                            .withStyle(ChatFormatting.DARK_GRAY));
        goggles(tooltip, 1, tip);
        return true;
    }

    private static boolean basin(BasinBlockEntity basin, List<Component> tooltip) {
        goggles(tooltip, 0, lang("gui.goggles.basin_contents"));
        boolean empty = true;
        var items = basin.itemCapability;
        if (items != null)
            for (int i = 0, size = items.getContainerSize(); i < size; i++) {
                ItemStack stack = items.getItem(i);
                if (stack.isEmpty()) continue;
                goggles(
                        tooltip,
                        1,
                        Component.empty()
                                .append(stack.getItemName().copy().withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(" x" + stack.getCount())
                                        .withStyle(ChatFormatting.GREEN)));
                empty = false;
            }
        var fluids = basin.fluidCapability;
        if (fluids != null)
            for (int i = 0, size = fluids.size(); i < size; i++) {
                FluidStack fluid = fluids.getStack(i);
                if (fluid.isEmpty()) continue;
                goggles(
                        tooltip,
                        1,
                        Component.empty()
                                .append(fluid.getName().copy().append(" ").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(number((double) fluid.getAmount() / 81))
                                        .append(lang("generic.unit.millibuckets"))
                                        .withStyle(ChatFormatting.BLUE)));
                empty = false;
            }
        if (empty) tooltip.removeFirst();
        return true;
    }

    private static boolean chute(ChuteBlockEntity chute, List<Component> tooltip) {
        boolean downward = chute.getItemMotion() < 0;
        goggles(tooltip, 0, lang("tooltip.chute.header"));
        float pull = chute.pull, push = chute.push;
        if (pull == 0 && push == 0)
            goggles(tooltip, 0, lang("tooltip.chute.no_fans_attached").withStyle(ChatFormatting.GRAY));
        if (pull != 0)
            goggles(
                    tooltip,
                    0,
                    lang("tooltip.chute.fans_" + (pull > 0 ? "pull_up" : "push_down"))
                            .withStyle(ChatFormatting.GRAY));
        if (push != 0)
            goggles(
                    tooltip,
                    0,
                    lang("tooltip.chute.fans_" + (push > 0 ? "push_up" : "pull_down"))
                            .withStyle(ChatFormatting.GRAY));
        goggles(
                tooltip,
                0,
                Component.literal("-> ")
                        .append(lang("tooltip.chute.items_move_" + (downward ? "down" : "up")))
                        .withStyle(ChatFormatting.YELLOW));
        ItemStack item = chute.getItem();
        if (!item.isEmpty())
            goggles(
                    tooltip,
                    0,
                    lang("tooltip.chute.contains", item.getHoverName().getString(), item.getCount())
                            .withStyle(ChatFormatting.GREEN));
        return true;
    }

    private static boolean fluids(List<Component> tooltip, @Nullable FluidInventory handler) {
        if (handler == null) return false;
        int size = handler.size();
        if (size == 0) return false;
        goggles(tooltip, 0, lang("gui.goggles.fluid_container"));
        boolean empty = true;
        for (int i = 0; i < size; i++) {
            FluidStack fluid = handler.getStack(i);
            if (fluid.isEmpty()) continue;
            goggles(tooltip, 1, fluid.getName().copy().withStyle(ChatFormatting.GRAY));
            goggles(
                    tooltip,
                    1,
                    Component.literal(number((double) fluid.getAmount() / 81))
                            .append(lang("generic.unit.millibuckets"))
                            .withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(" / ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(number((double) handler.getMaxAmount(fluid) / 81))
                                    .append(lang("generic.unit.millibuckets"))
                                    .withStyle(ChatFormatting.DARK_GRAY)));
            empty = false;
        }
        if (size > 1) {
            if (empty) tooltip.removeLast();
            return true;
        }
        if (!empty) return true;
        goggles(
                tooltip,
                1,
                lang("gui.goggles.fluid_container.capacity")
                        .append(Component.literal(number((double) handler.getMaxAmountPerStack() / 81))
                                .append(lang("generic.unit.millibuckets"))
                                .withStyle(ChatFormatting.GOLD))
                        .withStyle(ChatFormatting.GRAY));
        return true;
    }

    private static boolean boiler(BoilerData data, List<Component> tooltip, int boilerSize) {
        if (!data.isActive()) return false;
        data.calcMinMaxForSize(boilerSize);
        goggles(
                tooltip,
                0,
                lang("boiler.status", data.getHeatLevelTextComponent().withStyle(ChatFormatting.GREEN)));
        goggles(tooltip, 1, data.getSizeComponent(true, false));
        goggles(tooltip, 1, data.getWaterComponent(true, false));
        goggles(tooltip, 1, data.getHeatComponent(true, false));
        if (data.attachedEngines == 0) return true;
        int boilerLevel = Math.min(data.activeHeat, Math.min(data.maxHeatForWater, data.maxHeatForSize));
        double totalSU = data.getEngineEfficiency(boilerSize)
                * 16
                * Math.max(boilerLevel, data.attachedEngines)
                * BlockStressValues.getCapacity(AllBlocks.STEAM_ENGINE);
        tooltip.add(Component.empty());
        if (data.attachedEngines > 0
                && data.maxHeatForSize > 0
                && data.maxHeatForWater == 0
                && (data.passiveHeat ? 1 : data.activeHeat) > 0) {
            goggles(tooltip, 0, lang("boiler.water_input_rate").withStyle(ChatFormatting.GRAY));
            MutableComponent mb = lang("generic.unit.millibuckets");
            goggles(
                    tooltip,
                    1,
                    Component.literal(number(data.waterSupply / 81))
                            .withStyle(ChatFormatting.BLUE)
                            .append(mb)
                            .append(Component.literal(" / ").withStyle(ChatFormatting.GRAY))
                            .append(lang(
                                            "boiler.per_tick",
                                            Component.literal(number((double) BoilerData.waterSupplyPerLevel / 81))
                                                    .append(lang("generic.unit.millibuckets")))
                                    .withStyle(ChatFormatting.DARK_GRAY)));
            return true;
        }
        goggles(tooltip, 0, lang("tooltip.capacityProvided").withStyle(ChatFormatting.GRAY));
        goggles(
                tooltip,
                1,
                Component.literal(number(totalSU))
                        .append(lang("generic.unit.stress"))
                        .withStyle(ChatFormatting.AQUA)
                        .append(" ")
                        .append((data.attachedEngines == 1
                                        ? lang("boiler.via_one_engine")
                                        : lang("boiler.via_engines", data.attachedEngines))
                                .withStyle(ChatFormatting.DARK_GRAY)));
        return true;
    }

    private static boolean hover(
            Kind kind, BlockEntity be, List<Component> tooltip, boolean sneaking, ServerPlayer player) {
        switch (kind) {
            case FROGPORT -> {
                if (!((FrogportBlockEntity) be).failedLastExport) return false;
                hint(tooltip, "hint.blocked_frogport");
                return true;
            }
            case STOCK_TICKER -> {
                return stockTicker((StockTickerBlockEntity) be, tooltip, player);
            }
            default -> {}
        }
        KineticBlockEntity kinetic = (KineticBlockEntity) be;
        if (kineticHover(kinetic, tooltip)) return true;
        switch (kind) {
            case DEPLOYER -> {
                var deployer = (DeployerBlockEntity) be;
                if (deployer.getSpeed() == 0 || deployer.overflowItems.isEmpty()) return false;
                hint(tooltip, "hint.full_deployer");
                return true;
            }
            case ARM -> {
                var arm = (ArmBlockEntity) be;
                if (sneaking || arm.tooltipWarmup > 0 || !arm.inputs.isEmpty() || !arm.outputs.isEmpty()) return false;
                hint(tooltip, "hint.mechanical_arm_no_targets");
                return true;
            }
            case MECHANICAL_BEARING -> {
                var bearing = (MechanicalBearingBlockEntity) be;
                if (sneaking || (!bearing.isWindmill() && bearing.getSpeed() == 0) || bearing.isRunning()) return false;
                BlockState state = bearing.getBlockState();
                if (!(state.getBlock() instanceof BearingBlock)) return false;
                BlockState attached = bearing.getLevel()
                        .getBlockState(bearing.getBlockPos().relative(state.getValue(BearingBlock.FACING)));
                if (attached.canBeReplaced()) return false;
                hint(tooltip, "hint.empty_bearing");
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static boolean kineticHover(KineticBlockEntity be, List<Component> tooltip) {
        boolean notFastEnough = !be.isSpeedRequirementFulfilled() && be.getSpeed() != 0;
        if (be.isOverStressed()) {
            goggles(tooltip, 0, lang("gui.stressometer.overstressed").withStyle(ChatFormatting.GOLD));
            for (Component line :
                    cut(lang("gui.contraptions.network_overstressed").getString())) goggles(tooltip, 0, line.copy());
            return true;
        }
        if (notFastEnough) {
            goggles(tooltip, 0, lang("tooltip.speedRequirement").withStyle(ChatFormatting.GOLD));
            String block = be.getBlockState().getBlock().getName().getString();
            for (Component line :
                    cut(lang("gui.contraptions.not_fast_enough", block).getString())) goggles(tooltip, 0, line.copy());
            return true;
        }
        return false;
    }

    private static boolean stockTicker(StockTickerBlockEntity ticker, List<Component> tooltip, ServerPlayer player) {
        var payments = ticker.receivedPayments;
        if (payments.isEmpty() || !ticker.behaviour.mayAdministrate(player)) return false;
        goggles(tooltip, 0, lang("stock_ticker.contains_payments").withStyle(ChatFormatting.WHITE));
        var summary = new InventorySummary();
        for (int i = 0, size = payments.getContainerSize(); i < size; i++) summary.add(payments.getItem(i));
        for (var entry : summary.getStacksByCount())
            goggles(
                    tooltip,
                    0,
                    Component.literal(entry.stack.getHoverName().getString() + " x" + entry.count)
                            .withStyle(ChatFormatting.GREEN));
        goggles(tooltip, 0, lang("stock_ticker.click_to_retrieve").withStyle(ChatFormatting.GRAY));
        return true;
    }

    private static Component speedText(float speed, boolean overstressed) {
        SpeedLevel level = SpeedLevel.of(speed);
        MutableComponent text = Component.literal(progressBar(3, level.ordinal()))
                .append(lang("tooltip.speedRequirement." + level.name().toLowerCase(Locale.ROOT)))
                .append(" (")
                .append(number(Math.abs(speed)))
                .append(" ")
                .append(lang("generic.unit.rpm"))
                .append(") ");
        return text.withStyle(overstressed ? ChatFormatting.DARK_GRAY : level.getTextColor());
    }

    private static MutableComponent atCurrentSpeed(float stress) {
        return Component.literal(number(stress))
                .append(lang("generic.unit.stress"))
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" "))
                .append(lang("gui.goggles.at_current_speed").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void hint(List<Component> tooltip, String key) {
        goggles(tooltip, 0, lang(key + ".title").withStyle(ChatFormatting.GOLD));
        for (Component line : cut(lang(key).getString())) goggles(tooltip, 0, line.copy());
    }

    private static void goggles(List<Component> tooltip, int indents, Component content) {
        tooltip.add(Component.literal(" ".repeat(4 + indents)).append(content));
    }

    private static MutableComponent lang(String key, Object... args) {
        return Component.translatable(Create.MOD_ID + "." + key, args);
    }

    private static String progressBar(int length, int filled) {
        return " " + "█".repeat(Math.max(0, filled)) + "▒".repeat(Math.max(0, length - filled)) + " ";
    }

    private static String number(double value) {
        if (Mth.equal(value, 0)) value = 0;
        NumberFormat format = NumberFormat.getInstance(Locale.US);
        format.setMaximumFractionDigits(2);
        format.setMinimumFractionDigits(0);
        format.setGroupingUsed(true);
        return format.format(value).replace(" ", " ");
    }

    private static List<Component> cut(String text) {
        BreakIterator iterator = BreakIterator.getLineInstance(Locale.US);
        iterator.setText(text);
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int width = 0;
        for (int start = iterator.first(), end = iterator.next();
                end != BreakIterator.DONE;
                start = end, end = iterator.next()) {
            String word = text.substring(start, end);
            int wordWidth = PolymerGogglesOverlay.width(word.replace("_", ""));
            if (width + wordWidth > 200) {
                if (width > 0) {
                    lines.add(current.toString());
                    current = new StringBuilder();
                    width = 0;
                } else {
                    lines.add(word);
                    continue;
                }
            }
            current.append(word);
            width += wordWidth;
        }
        if (width > 0) lines.add(current.toString());
        List<Component> result = new ArrayList<>();
        boolean highlighted = false;
        for (String line : lines) {
            MutableComponent component = Component.empty();
            for (String part : line.split("_", -1)) {
                component.append(
                        Component.literal(part).withStyle(highlighted ? ChatFormatting.WHITE : ChatFormatting.GRAY));
                highlighted = !highlighted;
            }
            result.add(component);
            highlighted = !highlighted;
        }
        return result;
    }
}
