package com.zurrtum.create.infrastructure.polymer.gui;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.zurrtum.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.zurrtum.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.zurrtum.create.content.logistics.factoryBoard.PanelSlot;
import com.zurrtum.create.content.logistics.factoryBoard.ServerFactoryPanelBehaviour;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class PolymerFactoryPanelForm {
    private PolymerFactoryPanelForm() {}

    public static void openFactoryPanelSettings(ServerPlayer player, ServerFactoryPanelBehaviour behaviour) {
        ServerLevel level = player.level();
        FactoryPanelPosition target = behaviour.getPanelPosition();
        if (player.isSpectator()
                || !player.mayBuild()
                || !PolymerDialogService.canReach(player, level, target.pos())
                || ServerFactoryPanelBehaviour.at(level, target) != behaviour
                || !Create.LOGISTICS.mayInteract(behaviour.network, player)) {
            player.sendOverlayMessage(Component.translatable("create.polymer.factory_panel.error.no_target"));
            return;
        }
        if (behaviour.getFilter().isEmpty() && !behaviour.panelBE().restocker)
            PolymerFactoryPanelItemGui.open(player, behaviour);
        else PolymerFactoryPanelGui.open(player, behaviour);
    }

    public static int connectFactoryPanels(CommandSourceStack source, BlockPos sourcePos, String sourceSlotName)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.level();
        PanelSlot sourceSlot;
        try {
            sourceSlot = PanelSlot.valueOf(sourceSlotName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return PolymerDialogService.fail(source, "create.polymer.factory_panel.connection.error.slot");
        }

        BlockHitResult hit = PolymerDialogService.raycast(player);
        if (hit.getType() != HitResult.Type.BLOCK || !PolymerDialogService.canReach(player, level, hit.getBlockPos())) {
            return PolymerDialogService.fail(source, "create.polymer.factory_panel.error.no_target");
        }
        if (!level.isLoaded(sourcePos)
                || !sourcePos.closerThan(player.blockPosition(), 16)
                || player.isSpectator()
                || !player.mayBuild()
                || !player.mayInteract(level, sourcePos)) {
            return PolymerDialogService.fail(source, "create.polymer.factory_panel.connection.error.source");
        }

        BlockPos targetPos = hit.getBlockPos();
        BlockEntity sourceBlockEntity = level.getBlockEntity(sourcePos);
        BlockEntity targetBlockEntity = level.getBlockEntity(targetPos);
        if (!(sourceBlockEntity instanceof FactoryPanelBlockEntity sourcePanelEntity)
                || !(targetBlockEntity instanceof FactoryPanelBlockEntity targetPanelEntity)) {
            return PolymerDialogService.fail(source, "create.polymer.factory_panel.error.no_target");
        }

        ServerFactoryPanelBehaviour sourcePanel = sourcePanelEntity.panels.get(sourceSlot);
        PanelSlot targetSlot =
                FactoryPanelBlock.getTargetedSlot(targetPos, level.getBlockState(targetPos), hit.getLocation());
        ServerFactoryPanelBehaviour targetPanel = targetPanelEntity.panels.get(targetSlot);
        if (sourcePanel == null
                || !sourcePanel.isActive()
                || targetPanel == null
                || !targetPanel.isActive()
                || sourcePanel == targetPanel) {
            return PolymerDialogService.fail(source, "create.polymer.factory_panel.connection.error.source");
        }
        if (!Create.LOGISTICS.mayInteract(sourcePanel.network, player)
                || !Create.LOGISTICS.mayInteract(targetPanel.network, player)) {
            return PolymerDialogService.fail(source, "create.polymer.factory_panel.connection.error.permission");
        }

        if (level.getBlockState(targetPos)
                        .setValue(FactoryPanelBlock.WATERLOGGED, false)
                        .setValue(FactoryPanelBlock.POWERED, false)
                != level.getBlockState(sourcePos)
                        .setValue(FactoryPanelBlock.WATERLOGGED, false)
                        .setValue(FactoryPanelBlock.POWERED, false)) {
            return PolymerDialogService.fail(source, "create.factory_panel.same_orientation");
        }

        BlockPos diff = targetPos.subtract(sourcePos);
        Direction connectedDirection = FactoryPanelBlock.connectedDirection(level.getBlockState(targetPos));
        if (connectedDirection.getAxis().choose(diff.getX(), diff.getY(), diff.getZ()) != 0) {
            return PolymerDialogService.fail(source, "create.factory_panel.same_surface");
        }
        if (!diff.closerThan(BlockPos.ZERO, 16)) {
            return PolymerDialogService.fail(source, "create.factory_panel.too_far_apart");
        }
        if (targetPanel.panelBE().restocker) {
            return PolymerDialogService.fail(source, "create.factory_panel.input_in_restock_mode");
        }
        if (targetPanel.targetedBy.containsKey(sourcePanel.getPanelPosition())) {
            return PolymerDialogService.fail(source, "create.factory_panel.already_connected");
        }
        if (targetPanel.targetedBy.size() >= 9) {
            return PolymerDialogService.fail(source, "create.factory_panel.cannot_add_more_inputs");
        }
        if (sourcePanel.getFilter().isEmpty() || targetPanel.getFilter().isEmpty()) {
            return PolymerDialogService.fail(source, "create.factory_panel.no_item");
        }

        targetPanel.addConnection(sourcePanel.getPanelPosition());
        if (!targetPanel.targetedBy.containsKey(sourcePanel.getPanelPosition())) {
            return PolymerDialogService.fail(source, "create.polymer.factory_panel.connection.error.failed");
        }
        player.sendSystemMessage(Component.translatable(
                "create.polymer.factory_panel.connection.success",
                sourcePanel.getFilter().getHoverName(),
                targetPanel.getFilter().getHoverName()));
        return 1;
    }
}
