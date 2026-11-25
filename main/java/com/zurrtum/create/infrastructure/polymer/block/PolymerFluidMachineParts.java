package com.zurrtum.create.infrastructure.polymer.block;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.content.fluids.drain.ItemDrainBlockEntity;
import com.zurrtum.create.content.fluids.transfer.GenericItemEmptying;
import com.zurrtum.create.content.kinetics.belt.BeltHelper;
import com.zurrtum.create.content.kinetics.belt.transport.TransportedItemStack;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.Part;
import java.util.List;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

final class PolymerFluidMachineParts {
    private PolymerFluidMachineParts() {}

    static void register() {
        PolymerBlockParts.<ItemDrainBlockEntity>register(PolymerFluidMachineParts::itemDrain, AllBlocks.ITEM_DRAIN);
    }

    private static void itemDrain(ItemDrainBlockEntity be, BlockState state, float time, List<Part> out) {
        var segment = be.internalTank == null ? null : be.internalTank.getPrimaryTank();
        FluidStack tankFluid = segment == null ? FluidStack.EMPTY : segment.getFluid();
        if (segment != null && !tankFluid.isEmpty()) {
            float level = segment.getFluidLevel().getValue();
            float height = .4375f * level;
            if (height > 1e-4f) {
                out.add(PolymerBlockParts.part(
                                PolymerBasinParts.fluidModel(tankFluid),
                                new Matrix4f().translate(.125f, .3125f, .125f).scale(.75f, height, .75f))
                        .identified("item_drain:tank"));
            }
        }

        ItemStack held = be.getHeldItemStack();
        if (!held.isEmpty() && be.processingTicks != -1) {
            FluidStack process =
                    GenericItemEmptying.emptyItem(be.getLevel(), held, true).getFirst();
            if (process.isEmpty()) process = tankFluid;
            if (!process.isEmpty()) {
                float progress = Mth.clamp(1 - ((be.processingTicks - .5f) - 5) / 10f, 0, 1);
                float radius = (float) (Math.pow(2 * progress - 1, 2) - 1);
                float half = -radius / 32f;
                if (half > 1e-4f) {
                    out.add(PolymerBlockParts.part(
                                    PolymerBasinParts.fluidModel(process),
                                    new Matrix4f()
                                            .translate(.5f - half, .25f, .5f - half)
                                            .scale(2 * half, .75f, 2 * half))
                            .identified("item_drain:stream"));
                }
            }
        }

        transportedItem(be, out);
    }

    private static void transportedItem(ItemDrainBlockEntity be, List<Part> out) {
        TransportedItemStack transported = be.heldItem;
        if (transported == null || transported.stack.isEmpty()) return;
        Direction insertedFrom = transported.insertedFrom;
        if (!insertedFrom.getAxis().isHorizontal()) return;

        float offset = transported.beltPosition;
        float sideOffset = transported.sideOffset;
        Direction travel = insertedFrom.getOpposite();
        float x = .5f + travel.getStepX() * (.5f - offset);
        float z = .5f + travel.getStepZ() * (.5f - offset);
        boolean alongX = insertedFrom.getClockWise().getAxis() == Direction.Axis.X;
        if (!alongX) sideOffset *= -1;
        x += alongX ? sideOffset : 0;
        z += alongX ? 0 : sideOffset;

        boolean upright = BeltHelper.isItemUpright(transported.stack);
        int direction = insertedFrom.getAxisDirection().getStep();
        float angle = direction * offset * 360f;
        int copies = Math.min(4, Mth.log2(Math.max(1, transported.stack.getCount())) / 2 + 1);
        Random random = new Random(0);
        for (int i = 0; i < copies; i++) {
            Matrix4f pose = new Matrix4f().translate(x, .9375f + (upright ? .09375f : 0), z);
            if (insertedFrom.getAxis() != Direction.Axis.X) pose.rotateX(Mth.DEG_TO_RAD * angle);
            if (insertedFrom.getAxis() != Direction.Axis.Z) pose.rotateZ(Mth.DEG_TO_RAD * -angle);
            if (upright) {
                pose.rotateY(Mth.DEG_TO_RAD * (insertedFrom.toYRot() + 180)).translate(0, 0, -.0625f * (i + 1));
            } else {
                if (!(transported.stack.getItem() instanceof BlockItem)) pose.rotateY(Mth.DEG_TO_RAD * 10 * i);
                pose.translate(0, (transported.stack.getItem() instanceof BlockItem ? .015625f : .0625f) * i, 0);
            }
            if (transported.stack.getItem() instanceof BlockItem && i > 0)
                pose.translate(random.nextFloat() * .0625f * i, 0, random.nextFloat() * .0625f * i);
            pose.scale(.5f);
            if (!upright && !(transported.stack.getItem() instanceof BlockItem)) pose.rotateX(Mth.HALF_PI);
            out.add(PolymerBlockParts.itemPart(transported.stack, pose, ItemDisplayContext.FIXED)
                    .identified("item_drain:item:" + i));
        }
    }
}
