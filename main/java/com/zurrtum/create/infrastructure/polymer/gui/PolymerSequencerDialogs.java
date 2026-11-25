package com.zurrtum.create.infrastructure.polymer.gui;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.compat.computercraft.AbstractComputerBehaviour;
import com.zurrtum.create.content.kinetics.transmission.sequencer.Instruction;
import com.zurrtum.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import com.zurrtum.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class PolymerSequencerDialogs {
    private PolymerSequencerDialogs() {}

    public static boolean open(ServerPlayer player, SequencedGearshiftBlockEntity gearshift) {
        if (!(gearshift.getLevel() instanceof ServerLevel level)
                || !canEdit(player, level, gearshift.getBlockPos(), gearshift)) {
            error(player, "The sequenced gearshift is unavailable or out of reach.");
            return true;
        }
        if (!validProgram(gearshift.getInstructions())) {
            error(player, "This gearshift has an invalid program and cannot be edited safely.");
            return true;
        }
        PolymerSequencerGui.open(player, gearshift);
        return true;
    }

    private static boolean validProgram(List<Instruction> steps) {
        if (steps.isEmpty() || steps.size() > 5) return false;
        for (int index = 0; index < steps.size(); index++) {
            Instruction step = steps.get(index);
            if (step == null || step.instruction == null || step.speedModifier == null) return false;
            int max = switch (step.instruction) {
                case TURN_ANGLE -> 360;
                case TURN_DISTANCE -> 128;
                case DELAY -> 600;
                default -> -1;
            };
            if (max > 0 && (step.value < 1 || step.value > max)) return false;
            if (step.instruction == SequencerInstructions.END && index != steps.size() - 1) return false;
        }
        return steps.size() == 5 || steps.getLast().instruction == SequencerInstructions.END;
    }

    private static boolean canEdit(
            ServerPlayer player, ServerLevel level, BlockPos pos, SequencedGearshiftBlockEntity gearshift) {
        return player.level() == level
                && !player.isSpectator()
                && player.mayBuild()
                && !gearshift.isRemoved()
                && level.isLoaded(pos)
                && level.getBlockEntity(pos) == gearshift
                && level.getBlockState(pos).is(AllBlocks.SEQUENCED_GEARSHIFT)
                && !AbstractComputerBehaviour.contains(gearshift)
                && PolymerDialogService.canReach(player, level, pos);
    }

    private static void error(ServerPlayer player, String message) {
        player.sendOverlayMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }
}
