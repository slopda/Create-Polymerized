package dev.veeda.createpoly.assets;

import com.google.gson.JsonElement;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class PackAssetWriter {
    private final ResourcePackBuilder builder;
    private final Map<String, byte[]> fingerprints = new HashMap<>();
    private final MessageDigest digest;
    private long uncompressedBytes;
    private int duplicateWritesSkipped;

    public PackAssetWriter(ResourcePackBuilder builder) {
        this.builder = Objects.requireNonNull(builder, "builder");
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    public void model(PackAssetId id, JsonElement model) {
        write(id.modelPath(), model.toString().getBytes(StandardCharsets.UTF_8));
    }

    public void item(PackAssetId id, JsonElement definition) {
        write(id.itemPath(), definition.toString().getBytes(StandardCharsets.UTF_8));
    }

    public void png(PackAssetId id, BufferedImage image) throws IOException {
        write(id.texturePath(), PngCodec.encode(Objects.requireNonNull(image, "image")));
    }

    public void itemPng(PackAssetId id, BufferedImage image) throws IOException {
        if (!id.path().startsWith("item/"))
            throw new IllegalArgumentException("Item atlas texture must use item/: " + id.reference());
        png(id, image);
    }

    private void write(String path, byte[] data) {
        byte[] fingerprint = digest.digest(data), previous = fingerprints.get(path);
        if (previous != null) {
            if (!Arrays.equals(previous, fingerprint))
                throw new IllegalStateException("Conflicting generated asset: " + path);
            duplicateWritesSkipped++;
            return;
        }
        if (!builder.addData(path, data)) throw new IllegalStateException("Cannot write generated asset: " + path);
        fingerprints.put(path, fingerprint);
        uncompressedBytes += data.length;
    }

    public int filesWritten() {
        return fingerprints.size();
    }

    public int duplicateWritesSkipped() {
        return duplicateWritesSkipped;
    }

    public long uncompressedBytes() {
        return uncompressedBytes;
    }

    public static void copyAssetTree(ResourcePackBuilder builder, Path assetsRoot) {
        if (!builder.copyFromPath(assetsRoot, "assets/", true))
            throw new IllegalStateException("Cannot add adapter resource overrides from " + assetsRoot);
    }
}
