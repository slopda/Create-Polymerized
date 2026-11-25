package com.zurrtum.create.infrastructure.polymer.block;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.catnip.math.AngleHelper;
import com.zurrtum.create.content.kinetics.simpleRelays.AbstractSimpleShaftBlock;
import com.zurrtum.create.content.redstone.link.RedstoneLinkBlock;
import com.zurrtum.create.content.redstone.link.ServerLinkBehaviour;
import com.zurrtum.create.foundation.blockEntity.SmartBlockEntity;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class PolymerFilterParts {
    private static final float DEG = Mth.DEG_TO_RAD;

    private PolymerFilterParts() {}

    static void registerAll() {
        PolymerBlockParts.<SmartBlockEntity>register(PolymerFilterParts::link, AllBlocks.REDSTONE_LINK);
    }

    private static void link(SmartBlockEntity be, BlockState state, float time, List<PolymerBlockParts.Part> out) {
        ServerLinkBehaviour link = be.getBehaviour(ServerLinkBehaviour.TYPE);
        if (link == null) {
            return;
        }
        linkSlot(out, state, link.frequencyFirst.getStack(), true);
        linkSlot(out, state, link.frequencyLast.getStack(), false);
    }

    private static void linkSlot(List<PolymerBlockParts.Part> out, BlockState state, ItemStack stack, boolean first) {
        if (stack.isEmpty()) {
            return;
        }
        Direction facing = state.getValue(RedstoneLinkBlock.FACING);
        Vector3f pos;
        if (facing.getAxis().isHorizontal()) {
            Vector3f location = voxel(8, 5.5f, 3.01f);
            if (first) {
                location.add(0, 5 / 16.0f, 0);
            }
            pos = rotateCentered(location, AngleHelper.horizontalAngle(facing), Axis.Y);
        } else {
            Vector3f location = voxel(8, 3.01f, 5.5f);
            if (first) {
                location.add(0, 0, 5 / 16.0f);
            }
            pos = rotateCentered(location, facing == Direction.DOWN ? 180 : 0, Axis.X);
        }
        float yRot = facing.getAxis().isVertical() ? 0 : AngleHelper.horizontalAngle(facing) + 180;
        float xRot = facing == Direction.UP ? 90 : facing == Direction.DOWN ? 270 : 0;
        valueBox(out, stack, slotPose(pos, yRot, xRot, 0, 0.4975f));
    }

    private static void valueBox(List<PolymerBlockParts.Part> out, ItemStack stack, Matrix4f slotPose) {
        boolean blockItem = stack.getItem() instanceof BlockItem;
        float scale = (blockItem ? 1.0f : 0.5f) + 1 / 64.0f;
        float zOffset = (blockItem ? 0 : -0.15f) + customZOffset(stack);
        Matrix4f pose = new Matrix4f(slotPose).scale(scale).translate(0, 0, zOffset);
        out.add(PolymerBlockParts.itemPart(stack, pose).near(10));
    }

    private static float customZOffset(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem item) {
            Block block = item.getBlock();
            if (block instanceof AbstractSimpleShaftBlock || block instanceof FenceBlock) {
                return -0.1f;
            }
            if (block.builtInRegistryHolder().is(BlockTags.BUTTONS) || block == Blocks.END_ROD) {
                return -0.1f;
            }
        }
        return 0;
    }

    private static Matrix4f slotPose(Vector3f pos, float yDeg, float xDeg, float zDeg, float scale) {
        return new Matrix4f()
                .translate(pos)
                .rotateY(DEG * yDeg)
                .rotateX(DEG * xDeg)
                .rotateZ(DEG * zDeg)
                .scale(scale);
    }

    private static Vector3f voxel(float x, float y, float z) {
        return new Vector3f(x / 16.0f, y / 16.0f, z / 16.0f);
    }

    private static Vector3f rotateCentered(Vector3f vec, float deg, Axis axis) {
        Matrix4f m = new Matrix4f().translate(0.5f, 0.5f, 0.5f);
        float rad = DEG * deg;
        switch (axis) {
            case X -> m.rotateX(rad);
            case Y -> m.rotateY(rad);
            default -> m.rotateZ(rad);
        }
        m.translate(-0.5f, -0.5f, -0.5f);
        return m.transformPosition(new Vector3f(vec));
    }
}
