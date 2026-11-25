package com.zurrtum.create.infrastructure.polymer;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.zurrtum.create.infrastructure.polymer.equipment.PolymerLinkedControllerCommands;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerFactoryPanelForm;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerStationForm;
import com.zurrtum.create.infrastructure.polymer.interaction.PolymerEjectorCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;

public final class PolymerFixtureCommands implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("createfly")
                        .then(Commands.literal("schedule")
                                .executes(AdapterInteractions.command(
                                        context -> PolymerStationForm.openHeldSchedule(context.getSource()))))
                        .then(Commands.literal("ejector")
                                .executes(AdapterInteractions.command(
                                        context -> PolymerEjectorCommands.showEjectorHelp(context.getSource())))
                                .then(Commands.literal("target")
                                        .then(Commands.argument("destination", BlockPosArgument.blockPos())
                                                .executes(AdapterInteractions.command(
                                                        context -> PolymerEjectorCommands.setEjectorTarget(
                                                                context.getSource(),
                                                                BlockPosArgument.getBlockPos(
                                                                        context, "destination")))))))
                        .then(Commands.literal("controller")
                                .executes(AdapterInteractions.command(context ->
                                        PolymerLinkedControllerCommands.showLinkedControllerHelp(context.getSource())))
                                .then(Commands.literal("bind")
                                        .then(Commands.argument("button", IntegerArgumentType.integer(1, 6))
                                                .executes(AdapterInteractions.command(
                                                        context -> PolymerLinkedControllerCommands.bindLinkedController(
                                                                context.getSource(),
                                                                IntegerArgumentType.getInteger(context, "button"))))))
                                .then(Commands.literal("toggle")
                                        .then(Commands.argument("button", IntegerArgumentType.integer(1, 6))
                                                .executes(AdapterInteractions.command(context ->
                                                        PolymerLinkedControllerCommands.toggleLinkedController(
                                                                context.getSource(),
                                                                IntegerArgumentType.getInteger(context, "button"))))))
                                .then(Commands.literal("release")
                                        .executes(AdapterInteractions.command(
                                                context -> PolymerLinkedControllerCommands.releaseLinkedController(
                                                        context.getSource()))))
                                .then(Commands.literal("stop")
                                        .executes(AdapterInteractions.command(
                                                context -> PolymerLinkedControllerCommands.stopLecternController(
                                                        context.getSource())))))
                        .then(Commands.literal("panel")
                                .then(Commands.literal("connect")
                                        .then(Commands.argument("source", BlockPosArgument.blockPos())
                                                .then(Commands.argument("slot", StringArgumentType.word())
                                                        .executes(AdapterInteractions.command(
                                                                context -> PolymerFactoryPanelForm.connectFactoryPanels(
                                                                        context.getSource(),
                                                                        BlockPosArgument.getLoadedBlockPos(
                                                                                context, "source"),
                                                                        StringArgumentType.getString(
                                                                                context, "slot"))))))))));
    }
}
