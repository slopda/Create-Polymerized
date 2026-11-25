package com.zurrtum.create.infrastructure.polymer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.kinetics.base.KineticBlockEntity;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public final class PolymerPartsCensus implements ModInitializer {
    private static final Path OUT = Path.of("polymerpatch", "parts_census.json");
    private static final Path CONTRACT = Path.of("polymerpatch", "bedrock_contract.json");

    private static final class Entry {
        String parts;
        boolean unstable;
        boolean kinetic;
        boolean itemsOrText;
        int samples;
        final java.util.Set<String> rotorModels = new java.util.TreeSet<>();
    }

    private static final Map<String, Entry> ENTRIES = new TreeMap<>();
    private static boolean dirty;

    @Override
    public void onInitialize() {
        if (!Boolean.getBoolean("polymerpatch.partsCensus")) return;
        PolymerBlockParts.censusHook = PolymerPartsCensus::record;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (dirty && server.getTickCount() % 1200 == 0) write();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> write());
        Create.LOGGER.info("Parts census on: writing {}", OUT);
    }

    private static void record(BlockEntity blockEntity, Collection<PolymerBlockParts.Part> parts) {
        BlockState state = blockEntity.getBlockState();
        JsonArray models = new JsonArray();
        java.util.Set<String> rotorModels = new java.util.TreeSet<>();
        boolean itemsOrText = false;
        for (PolymerBlockParts.Part part : parts) {
            if (part.model() == null || !part.item().isEmpty() || part.text() != null) {
                itemsOrText = true;
                continue;
            }
            if (com.zurrtum.create.infrastructure.polymer.block.PolymerBedrockParts.rotorMotion(part) != null)
                rotorModels.add(part.model().toString());
            JsonObject o = new JsonObject();
            o.addProperty("model", part.model().toString());
            float[] m = new float[16];
            part.transform().get(m);
            JsonArray matrix = new JsonArray();
            for (float v : m) matrix.add(Math.round(v * 10000f) / 10000f);
            o.add("matrix", matrix);
            models.add(o);
        }
        String signature = models.toString();
        Entry entry = ENTRIES.computeIfAbsent(stateKey(state), key -> new Entry());
        if (entry.parts == null) entry.parts = signature;
        else if (!entry.parts.equals(signature)) entry.unstable = true;
        entry.kinetic |= blockEntity instanceof KineticBlockEntity;
        entry.itemsOrText |= itemsOrText;
        entry.rotorModels.addAll(rotorModels);
        entry.samples++;
        dirty = true;
    }

    private static JsonObject contract() {
        JsonObject contract = new JsonObject();
        JsonArray names = new JsonArray();
        PolymerBedrockRotors.MODEL_NAMES.forEach(names::add);
        contract.add("rotorModels", names);
        JsonObject parts = new JsonObject();
        PolymerBedrockRotors.PART_ROTORS.forEach(parts::addProperty);
        contract.add("partRotors", parts);
        JsonArray meshParts = new JsonArray();
        com.zurrtum.create.infrastructure.polymer.block.PolymerBedrockParts.MESH_PARTS.forEach(meshParts::add);
        contract.add("meshParts", meshParts);
        return contract;
    }

    private static String stateKey(BlockState state) {
        StringBuilder key = new StringBuilder(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        var properties = state.getProperties().stream()
                .sorted(Comparator.comparing(Property::getName))
                .toList();
        if (!properties.isEmpty()) {
            key.append('[');
            for (int i = 0; i < properties.size(); i++) {
                if (i > 0) key.append(',');
                key.append(properties.get(i).getName()).append('=').append(valueName(properties.get(i), state));
            }
            key.append(']');
        }
        return key.toString();
    }

    private static <T extends Comparable<T>> String valueName(Property<T> property, BlockState state) {
        return property.getName(state.getValue(property));
    }

    private static synchronized void write() {
        JsonObject root = new JsonObject();
        ENTRIES.forEach((key, entry) -> {
            JsonObject o = new JsonObject();
            o.add("parts", com.google.gson.JsonParser.parseString(entry.parts));
            o.addProperty("unstable", entry.unstable);
            o.addProperty("kinetic", entry.kinetic);
            o.addProperty("itemsOrText", entry.itemsOrText);
            o.addProperty("samples", entry.samples);
            if (!entry.rotorModels.isEmpty()) {
                JsonArray rotors = new JsonArray();
                entry.rotorModels.forEach(rotors::add);
                o.add("rotorModels", rotors);
            }
            root.add(key, o);
        });
        try {
            Files.createDirectories(OUT.getParent());
            Files.writeString(OUT, root.toString());
            Files.writeString(CONTRACT, contract().toString());
            dirty = false;
        } catch (java.io.IOException e) {
            Create.LOGGER.warn("Could not write {}", OUT, e);
        }
    }
}
