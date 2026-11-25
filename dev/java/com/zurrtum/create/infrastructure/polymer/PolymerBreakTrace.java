package com.zurrtum.create.infrastructure.polymer;

import com.zurrtum.create.Create;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class PolymerBreakTrace implements ModInitializer {
    private static BlockPos lastBreak;
    private static long lastTick;

    @Override
    public void onInitialize() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
            if (create(state)) log("before", level, player, pos, state);
            return true;
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
            if (!create(state)) return;
            log("after", level, player, pos, state);
            lastBreak = pos.immutable();
            lastTick = level.getGameTime();
        });
        PlayerBlockBreakEvents.CANCELED.register((level, player, pos, state, be) -> {
            if (create(state)) log("canceled", level, player, pos, state);
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ItemEntity item
                    && lastBreak != null
                    && level.getGameTime() - lastTick <= 1
                    && item.blockPosition().closerThan(lastBreak, 2))
                Create.LOGGER.info("[break-trace] drop {} at {}", item.getItem(), item.blockPosition());
        });
    }

    private static boolean create(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(Create.MOD_ID);
    }

    private static void log(String phase, Level level, Player player, BlockPos pos, BlockState state) {
        Create.LOGGER.info(
                "[break-trace] {} {} at {} by {} ({}, holding {}) tick {}",
                phase,
                state,
                pos,
                player.getName().getString(),
                player.isCreative() ? "creative" : "survival",
                player.getMainHandItem(),
                level.getGameTime());
    }
}
