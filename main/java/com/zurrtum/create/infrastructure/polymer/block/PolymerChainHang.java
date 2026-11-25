package com.zurrtum.create.infrastructure.polymer.block;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

final class PolymerChainHang {
    private PolymerChainHang() {}

    static Vec3 clientDelta(ServerPlayer player, Vec3 moved) {
        float drag = (float) player.getAttributeValue(Attributes.AIR_DRAG_MODIFIER);
        double horizontal = Mth.clamp(1 - (1 - .91f) * drag, 0, 1);
        if (player.getAbilities().flying) return new Vec3(moved.x * horizontal, moved.y * .6, moved.z * horizontal);
        double gravity = player.getGravity();
        if (moved.y <= 0 && player.hasEffect(MobEffects.SLOW_FALLING)) gravity = Math.min(gravity, .01);
        double vertical = Mth.clamp(1 - (1 - .98f) * drag, 0, 1);
        return new Vec3(moved.x * horizontal, (moved.y - gravity) * vertical, moved.z * horizontal);
    }
}
