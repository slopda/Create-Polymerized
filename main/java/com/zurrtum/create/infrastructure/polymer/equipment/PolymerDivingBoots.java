package com.zurrtum.create.infrastructure.polymer.equipment;

import com.zurrtum.create.AllItems;
import com.zurrtum.create.content.equipment.armor.DivingBootsItem;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import dev.veeda.createpoly.assets.CreateIds;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class PolymerDivingBoots {
    private static final Identifier ID = CreateIds.id("polymer_diving_boots");
    private static final Identifier LAVA_ID = CreateIds.id("polymer_diving_boots_lava");
    private static final double GRAVITY = 0.64, WATER_EFFICIENCY = 1.0, LAVA_GRAVITY = 1.095;

    private PolymerDivingBoots() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : NativePresentationPolicy.onlinePlayers(server.getPlayerList())) tick(player);
        });
    }

    private static void tick(ServerPlayer player) {
        boolean active = !PolymerNativeClients.isNative(player)
                && DivingBootsItem.isWornBy(player)
                && player.isInWater()
                && player.getPose() != Pose.SWIMMING
                && !player.getAbilities().flying
                && !player.isSpectator();
        set(player, Attributes.GRAVITY, active, GRAVITY);
        set(player, Attributes.WATER_MOVEMENT_EFFICIENCY, active, WATER_EFFICIENCY);
        jump(player, active, .5);
        boolean lava = !PolymerNativeClients.isNative(player)
                && DivingBootsItem.getWornItem(player).is(AllItems.NETHERITE_DIVING_BOOTS)
                && player.isInLava()
                && !player.getAbilities().flying
                && !player.isSpectator();
        set(player, Attributes.GRAVITY, LAVA_ID, lava, LAVA_GRAVITY);
        jump(player, lava, player.isEyeInFluid(FluidTags.LAVA) ? 1 : .5);
    }

    private static final int JUMP_COOLDOWN = 10;
    private static final Map<UUID, Long> LAST_JUMP = new HashMap<>();

    private static void jump(ServerPlayer player, boolean active, double lift) {
        if (!active || !player.getLastClientInput().jump() || !player.onGround()) return;
        long now = player.level().getGameTime();
        Long last = LAST_JUMP.get(player.getUUID());
        if (last != null && now - last < JUMP_COOLDOWN) return;
        LAST_JUMP.put(player.getUUID(), now);
        var motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, Math.max(motion.y, 0) + lift, motion.z);
        player.syncVelocity = true;
    }

    private static void set(ServerPlayer player, Holder<Attribute> attribute, boolean active, double amount) {
        set(player, attribute, ID, active, amount);
    }

    private static void set(
            ServerPlayer player, Holder<Attribute> attribute, Identifier id, boolean active, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null || instance.hasModifier(id) == active) return;
        if (active)
            instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
        else instance.removeModifier(id);
    }
}
