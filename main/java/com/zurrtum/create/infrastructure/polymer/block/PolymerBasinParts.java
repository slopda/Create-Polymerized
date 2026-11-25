package com.zurrtum.create.infrastructure.polymer.block;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.catnip.data.IntAttached;
import com.zurrtum.create.catnip.math.AngleHelper;
import com.zurrtum.create.catnip.math.VecHelper;
import com.zurrtum.create.content.processing.basin.BasinBlock;
import com.zurrtum.create.content.processing.basin.BasinBlockEntity;
import com.zurrtum.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.zurrtum.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour.TankSegment;
import com.zurrtum.create.infrastructure.fluids.BucketFluidInventory;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.Part;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

final class PolymerBasinParts {
    private PolymerBasinParts() {}

    private static final Set<String> FLUIDS = Set.of("water", "lava", "honey", "chocolate", "milk");

    static void register() {
        PolymerBlockParts.<BasinBlockEntity>register(PolymerBasinParts::basin, AllBlocks.BASIN);
    }

    private static void basin(BasinBlockEntity be, BlockState state, float time, List<Part> out) {
        float fluidLevel = fluids(be, out);
        ingredients(be, time, fluidLevel, out);
        outputs(be, state, out);
        out.replaceAll(part -> part.near(16));
    }

    private static float fluids(BasinBlockEntity be, List<Part> out) {
        float totalUnits = be.getTotalFluidUnits(0);
        if (totalUnits < 1) return 0;
        float fluidLevel = Mth.clamp(totalUnits / (BucketFluidInventory.CAPACITY * 2), 0, 1);
        fluidLevel = 1 - (1 - fluidLevel) * (1 - fluidLevel);
        float xMin = 2 / 16f,
                xMax = 2 / 16f,
                yMin = 2 / 16f,
                yMax = yMin + 12 / 16f * fluidLevel,
                zMin = 2 / 16f,
                zMax = 14 / 16f;
        int index = 0;
        for (SmartFluidTankBehaviour behaviour : new SmartFluidTankBehaviour[] {
            be.getBehaviour(SmartFluidTankBehaviour.INPUT), be.getBehaviour(SmartFluidTankBehaviour.OUTPUT)
        }) {
            if (behaviour == null) continue;
            for (TankSegment segment : behaviour.getTanks()) {
                FluidStack fluid = segment.getRenderedFluid();
                if (fluid.isEmpty()) continue;
                float units = segment.getTotalUnits(0);
                if (units < 1) continue;
                xMax += Mth.clamp(units / totalUnits, 0, 1) * 12 / 16f;
                Matrix4f box = new Matrix4f().translate(xMin, yMin, zMin).scale(xMax - xMin, yMax - yMin, zMax - zMin);
                out.add(PolymerBlockParts.part(fluidModel(fluid), box).identified("basin:fluid:" + index++));
                xMin = xMax;
            }
        }
        return index == 0 ? 0 : yMax;
    }

    static Identifier fluidModel(FluidStack fluid) {
        String name = BuiltInRegistries.FLUID.getKey(fluid.getFluid()).getPath().replace("flowing_", "");
        return PolymerBlockParts.model("polymer/fluid/" + (FLUIDS.contains(name) ? name : "water"));
    }

    private static void ingredients(BasinBlockEntity be, float time, float fluidLevel, List<Part> out) {
        if (be.itemCapability == null) return;
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < be.itemCapability.getContainerSize(); slot++) {
            ItemStack stack = be.itemCapability.getItem(slot);
            if (!stack.isEmpty()) stacks.add(stack);
        }
        int itemCount = stacks.size();
        if (itemCount == 0) return;
        float level = Mth.clamp(fluidLevel - 0.3f, 0.125f, 0.6f);
        RandomSource random = RandomSource.create(be.getBlockPos().hashCode());
        Vec3 baseVector = new Vec3(itemCount == 1 ? 0 : 0.125, level, 0);
        float anglePartition = 360f / itemCount;
        for (int i = 0, size = itemCount; i < size; i++) {
            ItemStack stack = stacks.get(i);
            Vec3 position = VecHelper.rotate(baseVector, anglePartition * itemCount, Direction.Axis.Y);
            if (fluidLevel > 0)
                position = position.add(0, (Mth.sin(time / 12f + anglePartition * itemCount) + 1.5f) * 0.03125f, 0);
            float yRot = Mth.DEG_TO_RAD * (anglePartition * itemCount + 35);
            int count = stack.getCount() / 8 + 1;
            for (int j = 0; j < count; j++) {
                Vec3 offset = VecHelper.offsetRandomly(Vec3.ZERO, random, 0.0625f);
                Matrix4f pose = new Matrix4f()
                        .translate(0.5f, 0.2f, 0.5f)
                        .translate((float) position.x, (float) position.y, (float) position.z)
                        .rotateY(yRot)
                        .rotateX(Mth.DEG_TO_RAD * 65)
                        .translate((float) offset.x, (float) offset.y, (float) offset.z);
                out.add(PolymerBlockParts.itemPart(stack, pose, ItemDisplayContext.GROUND)
                        .identified("basin:item:" + i + ":" + j));
            }
            itemCount--;
        }
    }

    private static void outputs(BasinBlockEntity be, BlockState state, List<Part> out) {
        if (!(state.getBlock() instanceof BasinBlock)) return;
        Direction direction = state.getValue(BasinBlock.FACING);
        if (direction == Direction.DOWN) return;
        Vec3 directionVec = Vec3.atLowerCornerOf(direction.getUnitVec3i());
        Vec3 outVec = VecHelper.getCenterOf(BlockPos.ZERO)
                .add(directionVec.scale(0.55).subtract(0, 0.5f, 0));
        boolean outToBasin = be.getLevel()
                        .getBlockState(be.getBlockPos().relative(direction))
                        .getBlock()
                instanceof BasinBlock;
        List<IntAttached<ItemStack>> items;
        synchronized (be.visualizedOutputItems) {
            items = new ArrayList<>(be.visualizedOutputItems);
        }
        int index = 0;
        for (IntAttached<ItemStack> item : items) {
            float progress = 1 - item.getFirst() / (float) BasinBlockEntity.OUTPUT_ANIMATION_TIME;
            if (!outToBasin && progress > 0.35f) continue;
            Vec3 offset = outVec.add(0, Math.max(-0.55f, -(progress * progress * 2)), 0)
                    .add(directionVec.scale(progress * 0.5f));
            Matrix4f pose = new Matrix4f()
                    .translate((float) offset.x, (float) offset.y, (float) offset.z)
                    .rotateY(Mth.DEG_TO_RAD * AngleHelper.horizontalAngle(direction))
                    .rotateX(Mth.DEG_TO_RAD * progress * 180);
            out.add(PolymerBlockParts.itemPart(item.getValue(), pose, ItemDisplayContext.GROUND)
                    .identified("basin:out:" + index++));
        }
    }
}
