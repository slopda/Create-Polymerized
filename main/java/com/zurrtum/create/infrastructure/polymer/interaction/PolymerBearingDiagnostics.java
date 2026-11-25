package com.zurrtum.create.infrastructure.polymer.interaction;

import com.zurrtum.create.content.contraptions.AssemblyException;
import com.zurrtum.create.content.contraptions.bearing.BearingBlock;
import com.zurrtum.create.content.contraptions.bearing.ClockworkBearingBlockEntity;
import com.zurrtum.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.zurrtum.create.infrastructure.polymer.AdapterInteractions;
import com.zurrtum.create.infrastructure.polymer.PolymerReach;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerDialogForms;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;

public final class PolymerBearingDiagnostics {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(Commands.literal("createfly")
                        .then(Commands.literal("inspect").executes(AdapterInteractions.command(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            BlockHitResult hit = PolymerReach.targetBlock(player);
                            if (hit == null) return 0;
                            return open(player, player.level().getBlockEntity(hit.getBlockPos()));
                        })))));
    }

    static int open(ServerPlayer player, BlockEntity be) {
        if (be == null
                || be.isRemoved()
                || be.getLevel() != player.level()
                || player.level().getBlockEntity(be.getBlockPos()) != be
                || !player.isWithinBlockInteractionRange(be.getBlockPos(), 0)) return 0;
        AssemblyException exception;
        boolean hint = false;
        if (be instanceof MechanicalBearingBlockEntity bearing) {
            exception = bearing.getLastAssemblyException();
            hint = !player.isShiftKeyDown()
                    && (bearing.isWindmill() || bearing.getSpeed() != 0)
                    && !bearing.isRunning()
                    && be.getBlockState().getBlock() instanceof BearingBlock
                    && !player.level()
                            .getBlockState(
                                    be.getBlockPos().relative(be.getBlockState().getValue(BearingBlock.FACING)))
                            .canBeReplaced();
        } else if (be instanceof ClockworkBearingBlockEntity bearing) exception = bearing.getLastAssemblyException();
        else {
            player.sendSystemMessage(Component.literal("Look at a bearing to inspect it."));
            return 0;
        }
        List<DialogBody> body = new ArrayList<>();
        if (hint)
            body.add(new PlainMessage(
                    Component.literal(
                            "Right-click the bearing with an empty hand to attach the structure you just built in front of it."),
                    340));
        if (exception != null) {
            body.add(new PlainMessage(
                    Component.translatable("create.gui.assembly.exception").withColor(0xFFAA00), 340));
            body.add(new PlainMessage(exception.component.copy().withColor(0xAAAAAA), 340));
        }
        if (body.isEmpty())
            body.add(new PlainMessage(Component.literal("No assembly error is recorded for this bearing."), 340));
        var common = new CommonDialogData(
                hint
                        ? Component.translatable("create.hint.empty_bearing.title")
                        : be.getBlockState().getBlock().getName(),
                Optional.empty(),
                true,
                false,
                DialogAction.CLOSE,
                body,
                List.of());
        PolymerDialogForms.open(
                player,
                Holder.direct(new NoticeDialog(
                        common,
                        new ActionButton(new CommonButtonData(Component.literal("Close"), 150), Optional.empty()))));
        return 1;
    }
}
