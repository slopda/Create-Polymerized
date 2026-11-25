package com.zurrtum.create.infrastructure.polymer;

import com.zurrtum.create.Create;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagNetworkSerialization;

public final class PolymerRegistrySyncFilter {
    private PolymerRegistrySyncFilter() {}

    public static int removeCreateEntries(Map<Identifier, Object2IntMap<Identifier>> registryMap, String source) {
        if (registryMap == null) {
            return 0;
        }

        int removedEntries = 0;
        int removedRegistries = 0;
        List<String> removalSummary = new ArrayList<>();
        Iterator<Map.Entry<Identifier, Object2IntMap<Identifier>>> iterator =
                registryMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Identifier, Object2IntMap<Identifier>> entry = iterator.next();
            String registryId = entry.getKey().toString();
            int removedFromRegistry = 0;
            if (entry.getKey().getNamespace().equals(Create.MOD_ID)) {
                removedFromRegistry = entry.getValue().size();
                iterator.remove();
                removedRegistries++;
            } else {
                Iterator<Identifier> ids = entry.getValue().keySet().iterator();
                while (ids.hasNext()) {
                    if (ids.next().getNamespace().equals(Create.MOD_ID)) {
                        ids.remove();
                        removedFromRegistry++;
                    }
                }
                if (removedFromRegistry > 0 && entry.getValue().isEmpty()) {
                    iterator.remove();
                    removedRegistries++;
                }
            }

            if (removedFromRegistry > 0) {
                removedEntries += removedFromRegistry;
                removalSummary.add(registryId + "=" + removedFromRegistry);
            }
        }

        if (removedEntries > 0) {
            Create.LOGGER.info(
                    "Removed {} Create registry entries from {} across {} registries ({} registries omitted): {}",
                    removedEntries,
                    source,
                    removalSummary.size(),
                    removedRegistries,
                    String.join(", ", removalSummary));
        }
        return removedEntries;
    }

    public static Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload>
            removeCreateRegistryTags(
                    Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> registryTags) {
        if (registryTags == null || registryTags.isEmpty()) {
            return registryTags;
        }

        Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> filtered =
                new LinkedHashMap<>();
        List<String> removedRegistries = new ArrayList<>();
        for (Map.Entry<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> entry :
                registryTags.entrySet()) {
            ResourceKey<? extends Registry<?>> registryKey = entry.getKey();
            if (registryKey.identifier().getNamespace().equals(Create.MOD_ID)) {
                removedRegistries.add(registryKey.identifier().toString());
            } else {
                filtered.put(registryKey, entry.getValue());
            }
        }

        if (removedRegistries.isEmpty()) {
            return registryTags;
        }

        Create.LOGGER.info(
                "Omitted tag payloads for {} Create-owned registries from vanilla synchronization: {}",
                removedRegistries.size(),
                String.join(", ", removedRegistries));
        return filtered;
    }
}
