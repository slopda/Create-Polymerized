package com.zurrtum.create.infrastructure.polymer.gui;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.content.contraptions.elevator.ElevatorContactBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class PolymerElevatorDialogs {
    private PolymerElevatorDialogs() {}

    public static boolean open(ServerPlayer player, ElevatorContactBlockEntity contact) {
        if (!(contact.getLevel() instanceof ServerLevel level)
                || !canEdit(player, level, contact.getBlockPos(), contact)) {
            player.sendOverlayMessage(Component.literal("The Elevator Contact is unavailable or out of reach.")
                    .withStyle(ChatFormatting.RED));
            return true;
        }
        PolymerElevatorGui.open(player, contact, level);
        return true;
    }

    private static boolean canEdit(
            ServerPlayer player, ServerLevel level, BlockPos pos, ElevatorContactBlockEntity contact) {
        return player.level() == level
                && !player.isSpectator()
                && player.mayBuild()
                && !contact.isRemoved()
                && level.isLoaded(pos)
                && level.getBlockEntity(pos) == contact
                && level.getBlockState(pos).is(AllBlocks.ELEVATOR_CONTACT)
                && PolymerDialogService.canReach(player, level, pos);
    }
}
