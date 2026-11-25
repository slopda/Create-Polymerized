package com.zurrtum.create.infrastructure.polymer.effects;

import com.zurrtum.create.AllParticleTypes;
import com.zurrtum.create.catnip.math.VecHelper;
import com.zurrtum.create.content.fluids.spout.SpoutBlockEntity;
import com.zurrtum.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.zurrtum.create.content.processing.basin.BasinBlock;
import com.zurrtum.create.content.processing.basin.BasinBlockEntity;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import com.zurrtum.create.infrastructure.particle.FluidParticleData;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import java.util.ArrayList;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.phys.Vec3;

public final class PolymerFluidParticles {
    private PolymerFluidParticles() {}

    private static boolean visible(ServerLevel level, Vec3 at) {
        return level.players().stream()
                .anyMatch(p -> !PolymerNativeClients.isNative(p) && p.distanceToSqr(at) < 32 * 32);
    }

    private static ParticleOptions particle(FluidStack fluid) {
        return new FluidParticleData(AllParticleTypes.FLUID_PARTICLE, fluid.getFluid(), fluid.getComponentChanges());
    }

    private static void emit(ServerLevel level, ParticleOptions particle, Vec3 at, Vec3 motion) {
        PolymerVanillaEffects.sourceParticles(
                level, particle, false, true, at.x, at.y, at.z, 0, motion.x, motion.y, motion.z, 1);
    }

    public static void spout(SpoutBlockEntity spout) {
        if (!(spout.getLevel() instanceof ServerLevel level) || spout.isVirtual() || spout.processingTicks < 8) return;
        FluidStack fluid = spout.tank.getPrimaryHandler().getFluid();
        Vec3 at = Vec3.atCenterOf(spout.getBlockPos()).add(0, -.5, 0);
        if (!fluid.isEmpty() && visible(level, at)) emit(level, particle(fluid), at, new Vec3(0, -.1, 0));
    }

    public static void splash(SpoutBlockEntity spout, FluidStack fluid) {
        if (!(spout.getLevel() instanceof ServerLevel level) || spout.isVirtual() || fluid.isEmpty()) return;
        Vec3 at = Vec3.atCenterOf(spout.getBlockPos()).add(0, -1.6875, 0);
        if (!visible(level, at)) return;
        ParticleOptions option = particle(fluid);
        for (int i = 0; i < 20; i++) {
            Vec3 m = VecHelper.offsetRandomly(Vec3.ZERO, level.getRandom(), .125f);
            emit(level, option, at, new Vec3(m.x, Math.abs(m.y), m.z));
        }
    }

    public static void mixer(MechanicalMixerBlockEntity mixer) {
        if (!(mixer.getLevel() instanceof ServerLevel level)
                || mixer.isVirtual()
                || !mixer.running
                || mixer.runningTicks != 20
                || !visible(level, Vec3.atCenterOf(mixer.getBlockPos()))) return;
        if (level.getBlockEntity(mixer.getBlockPos().below(2)) instanceof BasinBlockEntity basin) {
            var inv = basin.itemCapability;
            for (int slot = 0; slot < inv.getContainerSize(); slot++) {
                var stack = inv.getItem(slot);
                if (!stack.isEmpty())
                    spill(
                            level,
                            mixer,
                            new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(stack)));
            }
            for (var tank : basin.getTanks())
                if (tank != null)
                    for (var segment : tank.getTanks()) {
                        var fluid = segment.getFluid();
                        if (!fluid.isEmpty()) spill(level, mixer, particle(fluid));
                    }
        }
    }

    private static void spill(ServerLevel level, MechanicalMixerBlockEntity mixer, ParticleOptions option) {
        Vec3 offset = VecHelper.rotate(new Vec3(0, 0, .25), level.getRandom().nextFloat() * 360, Axis.Y);
        Vec3 motion = VecHelper.rotate(offset, mixer.getSpeed() > 0 ? 25 : -25, Axis.Y)
                .add(0, .25, 0)
                .subtract(offset);
        motion = VecHelper.offsetRandomly(motion, level.getRandom(), 1 / 128f);
        emit(level, option, Vec3.atCenterOf(mixer.getBlockPos()).add(offset).add(0, -1.75, 0), motion);
    }

    public static void basin(BasinBlockEntity basin) {
        if (!(basin.getLevel() instanceof ServerLevel level)
                || basin.isVirtual()
                || !visible(level, Vec3.atCenterOf(basin.getBlockPos()))) return;
        boolean moving = level.getBlockEntity(basin.getBlockPos().above(2)) instanceof MechanicalMixerBlockEntity mixer
                && mixer.running
                && mixer.runningTicks <= 20;
        if (!moving && level.getRandom().nextFloat() > 1 / 8f) return;
        var fluids = new ArrayList<FluidStack>();
        long amount = 0;
        for (var tank : basin.getTanks())
            if (tank != null)
                for (var segment : tank.getTanks()) {
                    var fluid = segment.getFluid();
                    if (!fluid.isEmpty()) {
                        fluids.add(fluid);
                        amount += fluid.getAmount();
                    }
                }
        if (fluids.size() < 2 || amount == 0) return;
        double surface = basin.getBlockPos().getY() + .125 + .75 * Math.clamp(amount / 162000.0, 0, 1) + 1 / 32.0;
        for (int i = 0; i < fluids.size(); i++) {
            Vec3 at = moving
                    ? Vec3.atCenterOf(basin.getBlockPos())
                            .add(VecHelper.rotate(
                                    new Vec3(1 / 16.0, 0, 0),
                                    (float) (360.0 / fluids.size() * (1 + i) + level.getGameTime() * 18 % 360),
                                    Axis.Y))
                    : Vec3.atLowerCornerOf(basin.getBlockPos())
                            .add(
                                    .125 + .75 * level.getRandom().nextFloat(),
                                    0,
                                    .125 + .75 * level.getRandom().nextFloat());
            emit(level, particle(fluids.get(i)), new Vec3(at.x, surface, at.z), Vec3.ZERO);
        }
    }

    public static void basinOutput(BasinBlockEntity basin, FluidStack fluid) {
        if (!(basin.getLevel() instanceof ServerLevel level)
                || basin.isVirtual()
                || basin.isRemoved()
                || fluid.isEmpty()) return;
        var state = basin.getBlockState();
        if (!(state.getBlock() instanceof BasinBlock)) return;
        var direction = state.getValue(BasinBlock.FACING);
        if (direction == Direction.DOWN) return;
        Vec3 axis = direction.getUnitVec3();
        Vec3 at = Vec3.atCenterOf(basin.getBlockPos()).add(axis.scale(.65)).add(0, -.25, 0);
        if (!visible(level, at)) return;
        Vec3 motion = axis.scale(1 / 16.0).add(0, -1 / 16.0, 0);
        for (int i = 0; i < 2; i++)
            emit(level, particle(fluid), at, VecHelper.offsetRandomly(motion, level.getRandom(), 1 / 16f));
    }
}
