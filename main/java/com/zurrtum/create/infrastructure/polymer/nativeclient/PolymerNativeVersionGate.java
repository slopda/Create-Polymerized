package com.zurrtum.create.infrastructure.polymer.nativeclient;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.KnownPack;

public final class PolymerNativeVersionGate {
    private PolymerNativeVersionGate() {}

    public static String requiredVersion() {
        return FabricLoader.getInstance()
                .getModContainer("create")
                .orElseThrow()
                .getMetadata()
                .getVersion()
                .getFriendlyString();
    }

    private static boolean enabled() {
        return PolymerNativeClients.ENABLED;
    }

    public static List<String> probeVersions() {
        try (var stream =
                PolymerNativeVersionGate.class.getResourceAsStream("/polymerpatch/native-create-version-probes.json")) {
            if (stream == null) throw new IllegalStateException("Missing native Create version probe catalog");
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            var versions = new LinkedHashSet<String>();
            for (var value : json.getAsJsonArray("versions")) {
                String version = value.getAsString();
                if (version.isBlank() || version.length() > 128)
                    throw new IllegalStateException("Invalid Create version probe");
                versions.add(version);
            }
            return List.copyOf(versions);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read native Create version probes", e);
        }
    }

    public static List<KnownPack> offeredPacks(List<KnownPack> original) {
        if (!enabled()) return original;
        var versions = new ArrayList<>(probeVersions());
        versions.add(requiredVersion());
        return offeredPacks(original, versions);
    }

    public static List<KnownPack> offeredPacks(List<KnownPack> original, List<String> versions) {
        var result = new ArrayList<>(original);
        for (String version : versions) {
            var probe = new KnownPack("vanilla", "create", version);
            if (!result.contains(probe)) result.add(probe);
        }
        return List.copyOf(result);
    }

    public static Optional<String> mismatch(List<KnownPack> selected) {
        return enabled() ? mismatch(selected, requiredVersion()) : Optional.empty();
    }

    public static Optional<String> mismatch(List<KnownPack> selected, String required) {
        return selected.stream()
                .filter(pack -> pack.namespace().equals("vanilla")
                        && pack.id().equals("create")
                        && !pack.version().equals(required))
                .map(KnownPack::version)
                .findFirst();
    }

    public static Component warning(String required, String reported) {
        return Component.literal(
                "Incompatible Create Fly version.\n\nThis server requires exactly Create Fly " + required
                        + ".\nYour client has " + reported + ".\n\nInstall the exact version and restart Minecraft.");
    }
}
