package dev.veeda.createpoly;

import dev.veeda.createpoly.assets.PackAssetWriter;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import net.fabricmc.loader.api.FabricLoader;

public final class PatchAssets {
    private PatchAssets() {}

    public static InputStream open(String name) {
        String path = name.startsWith("/") ? name.substring(1) : name;
        for (String mod : new String[] {PolymerPatchCreate.MOD_ID, "create"}) {
            var found = FabricLoader.getInstance()
                    .getModContainer(mod)
                    .orElseThrow()
                    .findPath(path);
            if (found.isPresent()) {
                try {
                    return Files.newInputStream(found.get());
                } catch (IOException error) {
                    throw new IllegalStateException("Cannot read " + mod + ":" + path, error);
                }
            }
        }
        return PatchAssets.class.getClassLoader().getResourceAsStream(path);
    }

    public static void addOverrides(ResourcePackBuilder builder) {
        var mod = FabricLoader.getInstance()
                .getModContainer(PolymerPatchCreate.MOD_ID)
                .orElseThrow();
        for (var root : mod.getRootPaths()) {
            var assets = root.resolve("assets");
            if (Files.isDirectory(assets)) PackAssetWriter.copyAssetTree(builder, assets);
        }
    }
}
