package com.zurrtum.create.infrastructure.polymer;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class PolymerReach {
    private PolymerReach() {}

    public static BlockHitResult targetBlock(Player player) {
        HitResult hit = player.pick(player.blockInteractionRange(), 1, false);
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK ? block : null;
    }
}
