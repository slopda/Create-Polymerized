package com.zurrtum.create.infrastructure.polymer;

import com.zurrtum.create.Create;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public final class PolymerRegistrySync {
    private static boolean markerUnavailableLogged;
    private static boolean syncAuditRegistered;
    private static int lastLoggedMarkedCount = -1;

    private PolymerRegistrySync() {}

    public static int markCreateEntriesAsServerOnly() {
        registerSyncAudit();
        try {
            Class<?> registrySyncUtils = Class.forName("eu.pb4.polymer.rsm.api.RegistrySyncUtils");
            Method setServerEntry = registrySyncUtils.getMethod("setServerEntry", Registry.class, Object.class);
            Method isServerEntry = registrySyncUtils.getMethod("isServerEntry", Registry.class, Object.class);
            int markedEntries = 0;
            for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
                markedEntries += markCreateEntries(registry, setServerEntry, isServerEntry);
            }

            if (markedEntries > 0 && markedEntries != lastLoggedMarkedCount) {
                lastLoggedMarkedCount = markedEntries;
                Create.LOGGER.info(
                        "Marked {} Create registry entries as server-only for Polymer registry sync", markedEntries);
            }
            return markedEntries;
        } catch (ReflectiveOperationException | LinkageError exception) {
            if (!markerUnavailableLogged) {
                markerUnavailableLogged = true;
                Create.LOGGER.warn(
                        "Polymer registry sync marker is unavailable; keeping the Fabric sync-map fallback filter",
                        exception);
            }
            return 0;
        }
    }

    public static int markRegistryEntriesAsServerOnly(Registry<?> registry) {
        try {
            Class<?> registrySyncUtils = Class.forName("eu.pb4.polymer.rsm.api.RegistrySyncUtils");
            Method setServerEntry = registrySyncUtils.getMethod("setServerEntry", Registry.class, Object.class);
            Method isServerEntry = registrySyncUtils.getMethod("isServerEntry", Registry.class, Object.class);
            int markedEntries = markCreateEntries(registry, setServerEntry, isServerEntry);
            if (markedEntries > 0) {
                Create.LOGGER.info(
                        "Marked {} late Create registry entries as server-only before registry {} froze",
                        markedEntries,
                        registry.key().identifier());
            }
            return markedEntries;
        } catch (ReflectiveOperationException | LinkageError exception) {
            if (!markerUnavailableLogged) {
                markerUnavailableLogged = true;
                Create.LOGGER.warn(
                        "Could not mark late Create registry entries before registry freeze; keeping the Fabric sync-map fallback filter",
                        exception);
            }
            return 0;
        }
    }

    private static void registerSyncAudit() {
        if (syncAuditRegistered) {
            return;
        }
        syncAuditRegistered = true;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                auditCreateEntryMarkers();
                Class<?> manager = Class.forName("net.fabricmc.fabric.impl.registry.sync.RegistrySyncManager");
                Map<?, ?> registryMap = (Map<?, ?>)
                        manager.getMethod("createAndPopulateRegistryMap").invoke(null);
                if (registryMap == null) {
                    Create.LOGGER.info(
                            "Fabric registry-sync audit passed: Fabric produced no client sync map, so no registry entries will be sent");
                    return;
                }
                int remainingEntries = 0;
                List<String> remainingByRegistry = new ArrayList<>();
                for (Map.Entry<?, ?> registry : registryMap.entrySet()) {
                    String registryId = String.valueOf(registry.getKey());
                    int foundInRegistry = 0;
                    if (registryId.startsWith(Create.MOD_ID + ":")
                            && registry.getValue() instanceof Map<?, ?> entries) {
                        foundInRegistry += entries.size();
                    } else if (registry.getValue() instanceof Map<?, ?> entries) {
                        for (Object entryId : entries.keySet()) {
                            if (String.valueOf(entryId).startsWith(Create.MOD_ID + ":")) {
                                foundInRegistry++;
                            }
                        }
                    }
                    if (foundInRegistry > 0) {
                        remainingEntries += foundInRegistry;
                        remainingByRegistry.add(registryId + "=" + foundInRegistry);
                    }
                }

                if (remainingEntries == 0) {
                    Create.LOGGER.info(
                            "Fabric registry-sync audit passed: no {} registry entries remain in the client sync map",
                            Create.MOD_ID + ":");
                } else {
                    Create.LOGGER.error(
                            "Fabric registry-sync audit found {} {} entries still eligible for client sync: {}",
                            remainingEntries,
                            Create.MOD_ID + ":",
                            String.join(", ", remainingByRegistry));
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
                Create.LOGGER.warn("Could not audit Fabric's client registry-sync map", exception);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static void auditCreateEntryMarkers() throws ReflectiveOperationException {
        Class<?> registrySyncUtils = Class.forName("eu.pb4.polymer.rsm.api.RegistrySyncUtils");
        Method isServerEntry = registrySyncUtils.getMethod("isServerEntry", Registry.class, Object.class);
        int totalEntries = 0;
        int markedEntries = 0;
        List<String> unmarkedByRegistry = new ArrayList<>();
        for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
            Registry<Object> typedRegistry = (Registry<Object>) registry;
            int totalInRegistry = 0;
            int markedInRegistry = 0;
            for (Identifier id : registry.keySet()) {
                if (id == null || !id.getNamespace().equals(Create.MOD_ID)) {
                    continue;
                }
                Object value = typedRegistry.getValue(id);
                if (value == null) {
                    continue;
                }
                totalInRegistry++;
                if (Boolean.TRUE.equals(isServerEntry.invoke(null, registry, value))) {
                    markedInRegistry++;
                }
            }
            totalEntries += totalInRegistry;
            markedEntries += markedInRegistry;
            if (markedInRegistry != totalInRegistry) {
                unmarkedByRegistry.add(registry.key().identifier() + "=" + (totalInRegistry - markedInRegistry) + "/"
                        + totalInRegistry);
            }
        }

        if (totalEntries == markedEntries) {
            Create.LOGGER.info(
                    "Polymer server-only registry audit passed: all {} Create entries across built-in registries are marked",
                    totalEntries);
        } else {
            Create.LOGGER.error(
                    "Polymer server-only registry audit found {} unmarked of {} Create entries: {}",
                    totalEntries - markedEntries,
                    totalEntries,
                    String.join(", ", unmarkedByRegistry));
        }
    }

    @SuppressWarnings("unchecked")
    private static int markCreateEntries(Registry<?> registry, Method setServerEntry, Method isServerEntry)
            throws ReflectiveOperationException {
        Registry<Object> typedRegistry = (Registry<Object>) registry;
        int markedEntries = 0;
        for (Identifier id : registry.keySet()) {
            if (id == null || !id.getNamespace().equals(Create.MOD_ID)) {
                continue;
            }
            Object value = typedRegistry.getValue(id);
            if (value == null) {
                continue;
            }

            if (Boolean.TRUE.equals(isServerEntry.invoke(null, registry, value))) {
                continue;
            }
            setServerEntry.invoke(null, registry, value);
            if (Boolean.TRUE.equals(isServerEntry.invoke(null, registry, value))) {
                markedEntries++;
            }
        }
        return markedEntries;
    }
}
