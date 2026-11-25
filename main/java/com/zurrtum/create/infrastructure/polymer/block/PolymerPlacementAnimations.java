package com.zurrtum.create.infrastructure.polymer.block;

import com.zurrtum.create.Create;
import com.zurrtum.create.infrastructure.player.FakePlayerEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;

public final class PolymerPlacementAnimations {
    private PolymerPlacementAnimations() {}

    public static InteractionResult result(Player player, Item item, InteractionResult result) {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER
                || !(player instanceof ServerPlayer)
                || player instanceof FakePlayerEntity
                || !(item instanceof BlockItem || item instanceof BucketItem)
                || !BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(Create.MOD_ID)
                || !(result instanceof InteractionResult.Success success)
                || success.swingSource() != InteractionResult.SwingSource.PREDICTED) return result;
        return new InteractionResult.Success(InteractionResult.SwingSource.SERVER_ONLY, success.itemContext());
    }
}
