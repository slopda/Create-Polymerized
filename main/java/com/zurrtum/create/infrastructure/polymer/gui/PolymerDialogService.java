package com.zurrtum.create.infrastructure.polymer.gui;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zurrtum.create.content.contraptions.elevator.ElevatorContactBlockEntity;
import com.zurrtum.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import com.zurrtum.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.zurrtum.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.zurrtum.create.content.logistics.factoryBoard.PanelSlot;
import com.zurrtum.create.content.logistics.factoryBoard.ServerFactoryPanelBehaviour;
import com.zurrtum.create.content.logistics.redstoneRequester.RedstoneRequesterBlockEntity;
import com.zurrtum.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.zurrtum.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;
import com.zurrtum.create.content.schematics.cannon.SchematicannonBlockEntity;
import com.zurrtum.create.foundation.blockEntity.SmartBlockEntity;
import com.zurrtum.create.infrastructure.polymer.AdapterInteractions;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerSuperGlue;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import com.zurrtum.create.infrastructure.polymer.ponder.PolymerPonderTopics;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class PolymerDialogService {
    static final long SESSION_LIFETIME_NANOS = TimeUnit.MINUTES.toNanos(2);
    public static final double OPEN_REACH_BUFFER = 1;

    private PolymerDialogService() {}

    public static void register() {
        PolymerMachineDialogs.register();
        PolymerStockMarketGui.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("createfly")
                        .then(Commands.literal("settings")
                                .executes(AdapterInteractions.command(context -> openForLook(context.getSource()))))
                        .then(Commands.literal("ponder")
                                .executes(AdapterInteractions.command(
                                        context -> PolymerPonderTopics.openPonderDialog(context.getSource())))
                                .then(Commands.literal("list")
                                        .executes(AdapterInteractions.command(context ->
                                                PolymerPonderTopics.listPonderTopics(context.getSource(), 1)))
                                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                                .executes(AdapterInteractions.command(
                                                        context -> PolymerPonderTopics.listPonderTopics(
                                                                context.getSource(),
                                                                IntegerArgumentType.getInteger(context, "page"))))))
                                .then(Commands.argument("topic", StringArgumentType.word())
                                        .executes(AdapterInteractions.command(
                                                context -> PolymerPonderTopics.showPonderTopic(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "topic"))))))));
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> PolymerMachineDialogs.clearSession(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> PolymerMachineDialogs.clearAllSessions());
        PolymerPonderTopics.register();
        PolymerStationForm.register();
    }

    public static boolean handleCustomAction(
            ServerGamePacketListenerImpl listener, ServerboundCustomClickActionPacket packet) {
        if (!NativePresentationPolicy.allowsAction(listener.player)) return false;
        if (!PolymerDialogForms.validSubmission(
                listener.player, packet.id(), packet.payload(), PolymerPonderTopics.PONDER_ACTION_ID)) return true;
        if (PolymerTextPrompt.handleCustomAction(listener, packet)
                || PolymerStationGui.handleCustomAction(listener, packet)
                || PolymerScheduleScreen.handleCustomAction(listener, packet)
                || PolymerSuperGlue.handleCustomAction(listener, packet)
                || PolymerMachineDialogs.handleCustomAction(listener, packet)
                || PolymerStockMarketGui.handleCustomAction(listener, packet)
                || PolymerRedstoneRequesterGui.handleCustomAction(listener, packet)
                || PolymerScreenEditors.handleCustomAction(listener, packet)) {
            return true;
        }
        if (PolymerPonderTopics.PONDER_ACTION_ID.equals(packet.id())) {
            PolymerPonderTopics.handlePonderAction(listener.player, packet.payload());
            return true;
        }
        return false;
    }

    private static int openRedstoneRequesterSettings(
            ServerPlayer player, ServerLevel level, BlockPos pos, RedstoneRequesterBlockEntity blockEntity) {
        if (player.isSpectator()
                || !player.mayBuild()
                || !canReach(player, level, pos)
                || level.getBlockEntity(pos) != blockEntity
                || !blockEntity.behaviour.mayInteractMessage(player)) return 0;
        return PolymerRedstoneRequesterGui.open(player, blockEntity) ? 1 : 0;
    }

    private static int openForLook(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.level();
        BlockHitResult hit = raycast(player);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return fail(source, "create.polymer.settings.error.no_target");
        }

        BlockPos pos = hit.getBlockPos();
        if (!canReach(player, level, pos)) {
            return fail(source, "create.polymer.settings.error.no_target");
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SequencedGearshiftBlockEntity sequencer) {
            return PolymerSequencerDialogs.open(player, sequencer) ? 1 : 0;
        }
        if (blockEntity instanceof ElevatorContactBlockEntity contact) {
            return PolymerElevatorDialogs.open(player, contact) ? 1 : 0;
        }
        if (blockEntity instanceof DisplayLinkBlockEntity displayLink) {
            return PolymerMachineDialogs.openDisplayLinkSettings(player, level, pos, displayLink);
        }
        if (blockEntity instanceof SchematicannonBlockEntity schematicannon) {
            if (player.isSpectator() || !player.mayBuild() || !player.mayInteract(level, pos)) {
                return fail(source, "create.polymer.settings.error.no_target");
            }
            return PolymerSchematicannonGui.open(player, schematicannon) ? 1 : 0;
        }
        if (blockEntity instanceof ThresholdSwitchBlockEntity threshold) {
            if (player.isSpectator() || !player.mayBuild() || !player.mayInteract(level, pos)) {
                return fail(source, "create.polymer.settings.error.no_target");
            }
            return PolymerThresholdGui.open(player, threshold) ? 1 : 0;
        }
        if (blockEntity instanceof FactoryPanelBlockEntity factoryPanel) {
            PanelSlot slot = FactoryPanelBlock.getTargetedSlot(pos, level.getBlockState(pos), hit.getLocation());
            ServerFactoryPanelBehaviour behaviour = factoryPanel.panels.get(slot);
            if (behaviour == null || !behaviour.active) {
                return fail(source, "create.polymer.factory_panel.error.no_target");
            }
            PolymerFactoryPanelForm.openFactoryPanelSettings(player, behaviour);
            return 1;
        }
        if (blockEntity instanceof RedstoneRequesterBlockEntity requester) {
            return openRedstoneRequesterSettings(player, level, pos, requester);
        }
        if (!(blockEntity instanceof SmartBlockEntity smartBlockEntity)) {
            return fail(source, "create.polymer.settings.error.no_scroll_value");
        }

        if (!PolymerMachineDialogs.supportsValueSettings(smartBlockEntity)) {
            return fail(source, "create.polymer.settings.error.no_scroll_value");
        }
        return PolymerMachineDialogs.openValueSettings(player, level, pos, smartBlockEntity);
    }

    public static boolean canReach(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return player.mayInteract(level, pos) && player.isWithinBlockInteractionRange(pos, OPEN_REACH_BUFFER);
    }

    public static BlockHitResult raycast(ServerPlayer player) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1).scale(player.blockInteractionRange()));
        return player.level()
                .clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
    }

    public static int fail(CommandSourceStack source, String key) {
        source.sendFailure(Component.translatable(key));
        return 0;
    }

    public static int fail(CommandSourceStack source, String key, Object argument) {
        source.sendFailure(Component.translatable(key, argument));
        return 0;
    }
}
