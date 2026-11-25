package com.zurrtum.create.infrastructure.polymer;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public final class PolymerPackWrites implements ModInitializer {
    private static final String ADAPTER = "com.zurrtum.create.infrastructure.polymer.";
    private static final StackWalker WALKER = StackWalker.getInstance();

    private record Write(String generator, int hash) {}

    private static final Map<String, List<Write>> WRITES = new ConcurrentHashMap<>();
    private static final AtomicLong TOTAL = new AtomicLong();

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> report());
    }

    public static void record(String path, byte[] bytes) {
        TOTAL.incrementAndGet();
        Write write = new Write(generator(), Arrays.hashCode(bytes));
        List<Write> list = WRITES.computeIfAbsent(path, key -> new ArrayList<>(1));
        synchronized (list) {
            list.add(write);
        }
    }

    private static String generator() {
        List<String> names = WALKER.walk(frames -> frames.map(StackWalker.StackFrame::getClassName)
                .filter(name -> (name.startsWith(ADAPTER) || name.startsWith("dev.veeda.createpoly."))
                        && !name.substring(name.lastIndexOf('.') + 1).startsWith("PolymerPackWrites")
                        && !name.contains(".devmixin."))
                .map(name -> name.substring(name.lastIndexOf('.') + 1).split("\\$")[0])
                .toList());
        if (names.isEmpty()) return "polymer";
        String inner = names.getFirst(), outer = names.getLast();
        return inner.equals(outer) ? inner : outer + " > " + inner;
    }

    private static void report() {
        JsonArray differing = new JsonArray(), same = new JsonArray();
        for (var entry : new TreeMap<>(WRITES).entrySet()) {
            List<Write> list = entry.getValue();
            if (list.size() < 2) continue;
            JsonObject row = new JsonObject();
            row.addProperty("path", entry.getKey());
            JsonArray writers = new JsonArray();
            list.forEach(write -> writers.add(write.generator()));
            row.add("writers", writers);
            (list.stream().map(Write::hash).distinct().count() > 1 ? differing : same).add(row);
        }
        JsonObject out = new JsonObject();
        out.addProperty("total_writes", TOTAL.get());
        out.addProperty("distinct_paths", WRITES.size());
        out.addProperty("rewritten_with_different_bytes", differing.size());
        out.addProperty("rewritten_with_same_bytes", same.size());
        out.add("different", differing);
        out.add("same", same);
        try {
            Files.writeString(
                    Path.of("pack-writes.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(out));
        } catch (IOException error) {
            throw new IllegalStateException("Cannot write pack-writes.json", error);
        }
    }
}
