package com.zurrtum.create.infrastructure.polymer.hud;

import com.zurrtum.create.AllItems;
import com.zurrtum.create.content.equipment.armor.BacktankUtil;
import com.zurrtum.create.content.equipment.armor.DivingHelmetItem;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerArtKit;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import dev.veeda.createpoly.assets.CreateIds;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.StringUtil;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public final class PolymerBacktankAir {
    private static final FontDescription SPACE = font("value_board_space"),
            HIGH = font("backtank_hud"),
            LOW = font("backtank_hud_low");
    private static final int ICON_X = 90, ICON_ADVANCE = 17, TEXT_X = 16;
    private static final Set<UUID> SHOWN = new HashSet<>();

    private PolymerBacktankAir() {}

    private static FontDescription font(String name) {
        return new FontDescription.Resource(CreateIds.id(name));
    }

    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SHOWN.remove(handler.player.getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 10 != 0) return;
            for (ServerPlayer player : NativePresentationPolicy.presentationPlayers(server.getPlayerList())) {
                if (PolymerNativeClients.isNative(player)) continue;
                int air = air(player);
                if (air > 0) {
                    SHOWN.add(player.getUUID());
                    player.sendOverlayMessage(line(player, air));
                } else if (SHOWN.remove(player.getUUID())) {
                    player.sendOverlayMessage(Component.empty());
                }
            }
        });
    }

    private static Component line(ServerPlayer player, int air) {
        List<ItemStack> tanks = BacktankUtil.getAllWithAir(player);
        ItemStack tank = tanks.isEmpty() ? AllItems.COPPER_BACKTANK.getDefaultInstance() : tanks.getFirst();
        boolean netherite = tank.is(AllItems.NETHERITE_BACKTANK);
        FontDescription row = tank.canBeHurtBy(player.level().damageSources().lava()) ? HIGH : LOW;
        String clock = StringUtil.formatTickDuration(
                Math.max(0, air - 1) * 20, player.level().tickRateManager().tickrate());
        int width = PolymerArtKit.width(clock);
        MutableComponent line = Component.empty();
        line.append(PolymerArtKit.space(SPACE, ICON_X));
        line.append(Component.literal(netherite ? "" : "")
                .withStyle(Style.EMPTY.withFont(row).withColor(0xFFFFFF).withoutShadow()));
        line.append(PolymerArtKit.space(SPACE, TEXT_X - ICON_ADVANCE));
        line.append(Component.literal(clock).withStyle(Style.EMPTY.withFont(row).withColor(color(air))));
        line.append(PolymerArtKit.space(SPACE, -(ICON_X + TEXT_X + width)));
        return line;
    }

    private static int color(int air) {
        if (air >= 60 || air % 2 != 0) return 0xFFFFFF;
        int fade = Math.round(255 * Math.max(air / 60f, .25f));
        return 0xFF0000 | fade << 8 | fade;
    }

    static int air(ServerPlayer player) {
        if (player.getAbilities().invulnerable || player.isCreative()) return 0;
        var level = player.level();
        boolean lava = player.isInLava();
        if (!lava
                && (!player.isEyeInFluid(FluidTags.WATER)
                        || level.getBlockState(BlockPos.containing(player.getX(), player.getEyeY(), player.getZ()))
                                .is(Blocks.BUBBLE_COLUMN)
                        || player.canBreatheUnderwater()
                        || MobEffectUtil.hasWaterBreathing(player))) return 0;
        ItemStack helmet = DivingHelmetItem.getWornItem(player);
        if (helmet.isEmpty() || lava && helmet.canBeHurtBy(level.damageSources().lava())) return 0;
        List<ItemStack> tanks = BacktankUtil.getAllWithAir(player);
        if (tanks.isEmpty()
                || lava
                        && tanks.stream()
                                .allMatch(tank ->
                                        tank.canBeHurtBy(level.damageSources().lava()))) return 0;
        int air = 0;
        for (ItemStack tank : tanks) air += BacktankUtil.getAir(tank);
        return air;
    }
}
