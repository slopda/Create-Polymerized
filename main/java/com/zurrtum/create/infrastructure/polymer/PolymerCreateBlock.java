package com.zurrtum.create.infrastructure.polymer;

import com.zurrtum.create.infrastructure.polymer.effects.PolymerBlockParticles;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

public interface PolymerCreateBlock extends PolymerBlock {
    @Override
    default boolean overridePlayerCollisionsWithPolymer(
            BlockGetter level, BlockPos pos, BlockState state, ServerPlayer player) {
        return false;
    }

    @Override
    default BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
        BlockState particles = PolymerBlockParticles.get(state);
        return particles == null ? getPolymerBlockState(state, context) : particles;
    }
}
