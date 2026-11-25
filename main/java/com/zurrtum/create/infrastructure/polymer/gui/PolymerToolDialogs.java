package com.zurrtum.create.infrastructure.polymer.gui;

import com.zurrtum.create.AllItems;
import com.zurrtum.create.content.equipment.clipboard.ClipboardBlockEntity;
import com.zurrtum.create.infrastructure.polymer.equipment.PolymerSymmetryWand;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class PolymerToolDialogs {
    private PolymerToolDialogs() {}

    public static void openWorldshaper(ServerPlayer player, InteractionHand hand) {
        if (player.getItemInHand(hand).is(AllItems.WORLDSHAPER)) {
            PolymerWorldshaperScreen.open(player, hand);
            return;
        }
        player.sendOverlayMessage(Component.literal("Hold the matching tool to open its settings."));
    }

    public static void openSymmetry(ServerPlayer player, InteractionHand hand) {
        if (player.getItemInHand(hand).is(AllItems.WAND_OF_SYMMETRY)) {
            PolymerSymmetryWand.open(player, hand);
            return;
        }
        player.sendOverlayMessage(Component.literal("Hold the matching tool to open its settings."));
    }

    public static void openClipboardItem(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSpectator() || hand != InteractionHand.MAIN_HAND || !stack.is(AllItems.CLIPBOARD)) {
            player.sendOverlayMessage(Component.literal("Hold a clipboard in your main hand to edit it."));
            return;
        }
        PolymerClipboardScreen.openItem(player);
    }

    public static void openClipboardBlock(ServerPlayer player, BlockPos pos, ClipboardBlockEntity blockEntity) {
        ServerLevel level = player.level();
        if (player.isSpectator()
                || !level.isLoaded(pos)
                || level.getBlockEntity(pos) != blockEntity
                || !player.mayBuild()
                || !PolymerDialogService.canReach(player, level, pos)) {
            player.sendOverlayMessage(Component.literal("The clipboard is out of reach or cannot be edited."));
            return;
        }
        PolymerClipboardScreen.openBlock(player, pos, blockEntity);
    }
}
