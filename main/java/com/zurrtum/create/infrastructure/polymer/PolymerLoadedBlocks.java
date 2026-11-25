package com.zurrtum.create.infrastructure.polymer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class PolymerLoadedBlocks {
    private PolymerLoadedBlocks() {}

    public static BlockState stateOrAir(BlockGetter world, BlockPos pos) {
        if (world instanceof ServerLevel level) {
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos);
        }
        return world.getBlockState(pos);
    }
}
