package com.zurrtum.create.infrastructure.polymer;

import com.zurrtum.create.Create;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;

public final class NativeRoutingCounters implements ModInitializer {
    private static final String ADAPTER = "com.zurrtum.create.infrastructure.polymer.";
    private static final StackWalker WALKER = StackWalker.getInstance();
    private static final Map<String, LongAdder> COUNTS = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        NativePresentationPolicy.fallbackObserver = NativeRoutingCounters::record;
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> save());
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(Commands.literal("createfly")
                        .then(Commands.literal("routing")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.literal("stats").executes(context -> {
                                    new TreeMap<>(COUNTS).forEach((key, count) -> {
                                        Create.LOGGER.info("[routing] {} = {}", key, count.sum());
                                        context.getSource()
                                                .sendSuccess(() -> Component.literal(key + " = " + count.sum()), false);
                                    });
                                    Create.LOGGER.info("[routing] {} fallback rows", COUNTS.size());
                                    context.getSource()
                                            .sendSuccess(
                                                    () -> Component.literal(COUNTS.size() + " routing fallback rows"),
                                                    false);
                                    return COUNTS.size();
                                }))
                                .then(Commands.literal("reset").executes(context -> {
                                    COUNTS.clear();
                                    return 1;
                                })))));
    }

    private static void save() {
        StringBuilder out = new StringBuilder();
        new TreeMap<>(COUNTS)
                .forEach((key, count) ->
                        out.append(key).append(" = ").append(count.sum()).append('\n'));
        try {
            Files.writeString(Path.of("routing-counters.txt"), out);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot write routing-counters.txt", error);
        }
    }

    private static void record(String fence, Object subject) {
        String detail = subject instanceof Packet<?> packet
                ? packet.getClass().getSimpleName() + " from " + producer()
                : subject != null ? subject.toString() : producer();
        COUNTS.computeIfAbsent(fence + " " + detail, key -> new LongAdder()).increment();
    }

    private static String producer() {
        return WALKER.walk(frames -> frames.map(StackWalker.StackFrame::getClassName)
                .filter(name -> name.startsWith("com.zurrtum.create.")
                        && !(name.startsWith(ADAPTER)
                                && name.substring(name.lastIndexOf('.') + 1).startsWith("Native"))
                        && !name.equals(NativeRoutingCounters.class.getName()))
                .findFirst()
                .orElse("unknown"));
    }
}
