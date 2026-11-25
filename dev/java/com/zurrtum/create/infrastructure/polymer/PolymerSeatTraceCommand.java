package com.zurrtum.create.infrastructure.polymer;

import com.zurrtum.create.infrastructure.polymer.contraption.PolymerShiftCarry;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class PolymerSeatTraceCommand implements ModInitializer {
    private static BufferedWriter out;

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(Commands.literal("createfly")
                        .then(Commands.literal("seattrace")
                                .then(Commands.literal("on").executes(context -> on(context.getSource())))
                                .then(Commands.literal("off").executes(context -> off(context.getSource()))))));
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(Commands.literal("createfly")
                        .then(Commands.literal("copies").executes(context -> copies(context.getSource())))));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> close());
    }

    private static int on(CommandSourceStack source) {
        close();
        Path file = Path.of("logs", "seattrace.log");
        try {
            out = Files.newBufferedWriter(
                    file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            out.write("---- seattrace on, game tick "
                    + source.getServer().overworld().getGameTime() + "\n");
            out.flush();
        } catch (IOException e) {
            source.sendFailure(Component.literal("seattrace: " + e));
            return 0;
        }
        PolymerShiftCarry.seatTrace = (player, line) -> {
            try {
                out.write(player.getName().getString());
                out.write(' ');
                out.write(line);
                out.write('\n');
                out.flush();
            } catch (IOException ignored) {
            }
        };
        source.sendSuccess(() -> Component.literal("Seat trace on: " + file.toAbsolutePath()), true);
        return 1;
    }

    private static int copies(CommandSourceStack source) {
        StringBuilder report = new StringBuilder("---- copies, game tick ")
                .append(source.getServer().overworld().getGameTime())
                .append('\n');
        for (var player : source.getServer().getPlayerList().getPlayers())
            report.append(player.getName().getString()).append(":\n").append(PolymerShiftCarry.copyReport(player));
        Path file = Path.of("logs", "copies.txt");
        try {
            Files.writeString(
                    file, report, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            source.sendFailure(Component.literal("copies: " + e));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Copy report written to " + file.toAbsolutePath()), false);
        return 1;
    }

    private static int off(CommandSourceStack source) {
        close();
        source.sendSuccess(() -> Component.literal("Seat trace off"), true);
        return 1;
    }

    private static void close() {
        PolymerShiftCarry.seatTrace = null;
        if (out == null) return;
        try {
            out.close();
        } catch (IOException ignored) {
        }
        out = null;
    }
}
