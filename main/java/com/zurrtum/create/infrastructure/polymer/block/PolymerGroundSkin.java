package com.zurrtum.create.infrastructure.polymer.block;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.content.decoration.copycat.CopycatPanelBlock;
import com.zurrtum.create.foundation.blockEntity.SmartBlockEntity;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerBlockCollision;
import eu.pb4.polymer.blocks.impl.BlockExtBlockMapper;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

final class PolymerGroundSkin {
    private static final float THICKNESS = .0001f;

    private PolymerGroundSkin() {}

    static void collect(SmartBlockEntity be, BlockState above, List<PolymerBlockParts.Part> out) {
        var level = be.getLevel();
        if (level == null) return;
        var pos = be.getBlockPos().below();
        BlockState ground = level.getBlockState(pos);
        if (!ground.isSolidRender() || !PolymerCopycatParts.hasMaterialMesh(ground)) return;
        BlockState nativeAbove = BlockExtBlockMapper.INSTANCE.toClientSideState(above, null);
        BlockState nativeGround = BlockExtBlockMapper.INSTANCE.toClientSideState(ground, null);
        if (Block.shouldRenderFace(nativeGround, nativeAbove, Direction.UP)) return;
        BlockState panel = AllBlocks.COPYCAT_PANEL.defaultBlockState().setValue(CopycatPanelBlock.FACING, Direction.UP);
        float top = (float) PolymerBlockCollision.shape(panel).bounds().maxY;
        Matrix4f pose =
                new Matrix4f().translate(.5f, (.5f - top) * THICKNESS, .5f).scale(1, THICKNESS, 1);
        out.add(PolymerBlockParts.itemPart(
                PolymerCopycatParts.displayItem(panel, ground, level, pos), pose, ItemDisplayContext.NONE));
    }
}
