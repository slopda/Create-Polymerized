package com.zurrtum.create.infrastructure.polymer.equipment;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zurrtum.create.AllHandle;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.catnip.data.Couple;
import com.zurrtum.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.zurrtum.create.content.redstone.link.controller.LecternControllerBlockEntity;
import com.zurrtum.create.content.redstone.link.controller.LinkedControllerItem;
import com.zurrtum.create.content.redstone.link.controller.LinkedControllerServerHandler;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerDialogService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class PolymerLinkedControllerCommands {
    private PolymerLinkedControllerCommands() {}

    public static int showLinkedControllerHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("create.polymer.linked_controller.help"), false);
        return 1;
    }

    public static int bindLinkedController(CommandSourceStack source, int button) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (player.isSpectator() || !player.mayBuild()) {
            return PolymerDialogService.fail(source, "create.polymer.linked_controller.error.no_permission");
        }
        if (heldLinkedController(player).isEmpty()) {
            return PolymerDialogService.fail(source, "create.polymer.linked_controller.error.no_controller");
        }

        BlockHitResult hit = PolymerDialogService.raycast(player);
        if (hit.getType() != HitResult.Type.BLOCK
                || !AllHandle.bindLinkedController(player, button - 1, hit.getBlockPos())) {
            return PolymerDialogService.fail(source, "create.polymer.linked_controller.error.no_link");
        }
        player.sendSystemMessage(Component.translatable("create.polymer.linked_controller.bound", button));
        return 1;
    }

    public static int toggleLinkedController(CommandSourceStack source, int button) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (player.isSpectator()) {
            return PolymerDialogService.fail(source, "create.polymer.linked_controller.error.no_permission");
        }
        ItemStack controller = controllerForControls(player);
        if (controller.isEmpty()) {
            return PolymerDialogService.fail(source, "create.polymer.linked_controller.error.no_controller");
        }

        Couple<Frequency> frequency = LinkedControllerItem.toFrequency(controller, button - 1);
        if (frequency.getFirst().getStack().isEmpty()
                && frequency.getSecond().getStack().isEmpty()) {
            return PolymerDialogService.fail(source, "create.polymer.linked_controller.error.empty_button", button);
        }

        boolean active = LinkedControllerServerHandler.togglePersistentButton(
                player.level(), player.blockPosition(), player.getUUID(), button - 1, frequency);
        player.sendSystemMessage(Component.translatable(
                active ? "create.polymer.linked_controller.button_on" : "create.polymer.linked_controller.button_off",
                button));
        return 1;
    }

    public static int releaseLinkedController(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        LinkedControllerServerHandler.releasePersistentInputs(player.level(), player.blockPosition(), player.getUUID());
        player.sendSystemMessage(Component.translatable("create.polymer.linked_controller.released"));
        return 1;
    }

    public static int stopLecternController(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BlockHitResult hit = PolymerDialogService.raycast(player);
        if (hit.getType() != HitResult.Type.BLOCK
                || !PolymerDialogService.canReach(player, player.level(), hit.getBlockPos())) {
            return PolymerDialogService.fail(source, "create.polymer.linked_controller.error.no_lectern");
        }
        BlockEntity blockEntity = player.level().getBlockEntity(hit.getBlockPos());
        if (!(blockEntity instanceof LecternControllerBlockEntity lectern) || !lectern.isUsedBy(player)) {
            return PolymerDialogService.fail(source, "create.polymer.linked_controller.error.no_lectern");
        }
        lectern.tryStopUsing(player);
        LinkedControllerServerHandler.releasePersistentInputs(player.level(), player.blockPosition(), player.getUUID());
        return 1;
    }

    private static ItemStack controllerForControls(ServerPlayer player) {
        BlockHitResult hit = PolymerDialogService.raycast(player);
        if (hit.getType() == HitResult.Type.BLOCK
                && PolymerDialogService.canReach(player, player.level(), hit.getBlockPos())
                && player.level().getBlockEntity(hit.getBlockPos()) instanceof LecternControllerBlockEntity lectern
                && lectern.isUsedBy(player)) {
            return lectern.getController();
        }
        return heldLinkedController(player);
    }

    private static ItemStack heldLinkedController(ServerPlayer player) {
        ItemStack controller = player.getMainHandItem();
        if (controller.is(AllItems.LINKED_CONTROLLER)) {
            return controller;
        }
        controller = player.getOffhandItem();
        return controller.is(AllItems.LINKED_CONTROLLER) ? controller : ItemStack.EMPTY;
    }
}
