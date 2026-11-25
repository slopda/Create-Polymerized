package com.zurrtum.create.infrastructure.polymer;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.zurrtum.create.Create;
import eu.pb4.polymer.resourcepack.api.PackResource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class PolymerPackOptimizer {
    public static int PNG_LEVEL = 0;

    private static final String[] SHARED_FOLDERS = {
        "assets/create/textures/block/polymer/source_faces/", "assets/create/textures/font/ponder_layout/"
    };
    private static final byte[][] SHARED_TOKENS = {
        "source_faces/".getBytes(StandardCharsets.US_ASCII), "ponder_layout/".getBytes(StandardCharsets.US_ASCII)
    };
    private static final Pattern REFERENCE = Pattern.compile("\"([a-z0-9_.-]+:[a-z0-9_./-]+)\"");
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
    private static final int MAX_RAW_PNG = 64 << 20;

    private PolymerPackOptimizer() {}

    public static List<Map.Entry<String, PackResource>> optimize(List<Map.Entry<String, PackResource>> entries) {
        long started = System.nanoTime();
        Stats stats = new Stats();
        List<Map.Entry<String, PackResource>> shared = entries;
        try {
            shared = shareTextures(entries, stats);
        } catch (RuntimeException failure) {
            Create.LOGGER.warn("Pack texture sharing skipped", failure);
        }
        List<Map.Entry<String, PackResource>> result = shared;
        int parallelism = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
        ForkJoinPool pool = new ForkJoinPool(parallelism);
        try {
            List<Map.Entry<String, PackResource>> input = shared;
            result = pool.submit(() -> input.parallelStream()
                            .map(entry -> compact(entry, stats))
                            .toList())
                    .get();
        } catch (Exception failure) {
            Create.LOGGER.warn("Pack compaction skipped", failure);
        } finally {
            pool.shutdown();
        }
        Create.LOGGER.info(
                "Pack write trims: {} identical textures shared ({} files repointed), {} JSON files compacted"
                        + " ({} -> {} bytes), {} PNGs recompressed ({} -> {} bytes of image data) in {} ms",
                stats.sharedTextures,
                stats.repointed,
                stats.json.get(),
                stats.jsonBefore.get(),
                stats.jsonAfter.get(),
                stats.png.get(),
                stats.pngBefore.get(),
                stats.pngAfter.get(),
                (System.nanoTime() - started) / 1_000_000);
        return result;
    }

    private static final class Stats {
        int sharedTextures, repointed;
        final AtomicLong json = new AtomicLong(), jsonBefore = new AtomicLong(), jsonAfter = new AtomicLong();
        final AtomicLong png = new AtomicLong(), pngBefore = new AtomicLong(), pngAfter = new AtomicLong();
    }

    private static List<Map.Entry<String, PackResource>> shareTextures(
            List<Map.Entry<String, PackResource>> entries, Stats stats) {
        Set<String> paths = new HashSet<>();
        for (var entry : entries) paths.add(entry.getKey());
        Map<String, List<String>> byContent = new HashMap<>();
        for (var entry : entries) {
            String path = entry.getKey();
            if (entry.getValue() == null || !path.endsWith(".png") || !shared(path) || paths.contains(path + ".mcmeta"))
                continue;
            byContent
                    .computeIfAbsent(sha256(entry.getValue().readAllBytes()), key -> new ArrayList<>())
                    .add(path);
        }
        Map<String, String> repoint = new HashMap<>();
        Map<String, String> copyOf = new HashMap<>();
        for (List<String> group : byContent.values()) {
            if (group.size() < 2) continue;
            Collections.sort(group);
            String canonical = group.getFirst();
            for (String copy : group.subList(1, group.size())) {
                repoint.put(modelId(copy), modelId(canonical));
                repoint.put(fontId(copy), fontId(canonical));
                copyOf.put(modelId(copy), copy);
                copyOf.put(fontId(copy), copy);
            }
        }
        if (repoint.isEmpty()) return entries;
        Set<String> drop = new HashSet<>(copyOf.values());
        Map<String, byte[]> rewritten = new HashMap<>();
        for (var entry : entries) {
            String path = entry.getKey();
            if (entry.getValue() == null || !(path.endsWith(".json") || path.endsWith(".mcmeta"))) continue;
            byte[] data = entry.getValue().readAllBytes();
            if (!containsAny(data)) continue;
            boolean ours = (path.contains("/models/") || path.contains("/font/")) && validUtf8(data);
            String text = new String(data, StandardCharsets.UTF_8);
            Matcher matcher = REFERENCE.matcher(text);
            StringBuilder out = new StringBuilder(text.length());
            boolean changed = false;
            while (matcher.find()) {
                String target = repoint.get(matcher.group(1));
                if (target == null) continue;
                if (!ours) {
                    drop.remove(copyOf.get(matcher.group(1)));
                    continue;
                }
                matcher.appendReplacement(out, Matcher.quoteReplacement("\"" + target + "\""));
                changed = true;
            }
            if (!ours || !changed) continue;
            matcher.appendTail(out);
            rewritten.put(path, out.toString().getBytes(StandardCharsets.UTF_8));
        }
        List<Map.Entry<String, PackResource>> result = new ArrayList<>(entries.size());
        for (var entry : entries) {
            String path = entry.getKey();
            if (drop.contains(path)) continue;
            byte[] replaced = rewritten.get(path);
            result.add(
                    replaced == null ? entry : new AbstractMap.SimpleImmutableEntry<>(path, PackResource.of(replaced)));
        }
        stats.sharedTextures = drop.size();
        stats.repointed = rewritten.size();
        return result;
    }

    private static boolean shared(String path) {
        for (String folder : SHARED_FOLDERS) if (path.startsWith(folder)) return true;
        return false;
    }

    private static String modelId(String path) {
        String rest = path.substring("assets/".length());
        int slash = rest.indexOf('/');
        String inside = rest.substring(slash + 1 + "textures/".length());
        return rest.substring(0, slash) + ":" + inside.substring(0, inside.length() - ".png".length());
    }

    private static String fontId(String path) {
        return modelId(path) + ".png";
    }

    private static boolean containsAny(byte[] data) {
        for (byte[] token : SHARED_TOKENS) if (indexOf(data, token) >= 0) return true;
        return false;
    }

    private static int indexOf(byte[] data, byte[] token) {
        outer:
        for (int i = 0; i + token.length <= data.length; i++) {
            if (data[i] != token[0]) continue;
            for (int j = 1; j < token.length; j++) if (data[i + j] != token[j]) continue outer;
            return i;
        }
        return -1;
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Map.Entry<String, PackResource> compact(Map.Entry<String, PackResource> entry, Stats stats) {
        PackResource resource = entry.getValue();
        if (resource == null) return entry;
        String path = entry.getKey();
        try {
            byte[] data = resource.readAllBytes();
            byte[] smaller = null;
            if (path.endsWith(".png")) {
                if (PNG_LEVEL > 0) smaller = recompressPng(data, stats);
            } else if ((path.endsWith(".json") || path.endsWith(".mcmeta")) && pretty(data)) {
                byte[] compact = encodeUtf8(minify(decodeUtf8(data)));
                if (compact.length < data.length) {
                    smaller = compact;
                    stats.json.incrementAndGet();
                    stats.jsonBefore.addAndGet(data.length);
                    stats.jsonAfter.addAndGet(compact.length);
                }
            }
            return smaller == null ? entry : new AbstractMap.SimpleImmutableEntry<>(path, PackResource.of(smaller));
        } catch (IOException | DataFormatException | RuntimeException kept) {
            return entry;
        }
    }

    private static boolean validUtf8(byte[] data) {
        try {
            decodeUtf8(data);
            return true;
        } catch (CharacterCodingException malformed) {
            return false;
        }
    }

    private static String decodeUtf8(byte[] data) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(data)).toString();
    }

    private static byte[] encodeUtf8(String text) throws CharacterCodingException {
        ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(text));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        return bytes;
    }

    private static boolean pretty(byte[] data) {
        for (byte b : data) if (b == '\n' || b == '\r') return true;
        return false;
    }

    static String minify(String json) throws IOException {
        JsonReader reader = new JsonReader(new StringReader(json));
        StringWriter out = new StringWriter(json.length());
        JsonWriter writer = new JsonWriter(out);
        writer.setSerializeNulls(true);
        int depth = 0;
        do {
            switch (reader.peek()) {
                case BEGIN_ARRAY -> {
                    reader.beginArray();
                    writer.beginArray();
                    depth++;
                }
                case END_ARRAY -> {
                    reader.endArray();
                    writer.endArray();
                    depth--;
                }
                case BEGIN_OBJECT -> {
                    reader.beginObject();
                    writer.beginObject();
                    depth++;
                }
                case END_OBJECT -> {
                    reader.endObject();
                    writer.endObject();
                    depth--;
                }
                case NAME -> writer.name(reader.nextName());
                case STRING -> writer.value(reader.nextString());
                case NUMBER -> writer.jsonValue(reader.nextString());
                case BOOLEAN -> writer.value(reader.nextBoolean());
                case NULL -> {
                    reader.nextNull();
                    writer.nullValue();
                }
                case END_DOCUMENT -> throw new IOException("Empty JSON");
            }
        } while (depth > 0);
        if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Trailing JSON content");
        writer.flush();
        return out.toString();
    }

    static byte[] recompressPng(byte[] png, Stats stats) throws DataFormatException {
        if (png.length < 8 + 25) return null;
        for (int i = 0; i < 8; i++) if (png[i] != PNG_SIGNATURE[i]) return null;
        ByteArrayOutputStream idat = new ByteArrayOutputStream();
        int firstIdat = -1, afterIdat = -1, pos = 8;
        while (pos + 12 <= png.length) {
            int length = readInt(png, pos);
            if (length < 0 || pos + 12L + length > png.length) return null;
            boolean isIdat = png[pos + 4] == 'I' && png[pos + 5] == 'D' && png[pos + 6] == 'A' && png[pos + 7] == 'T';
            if (isIdat) {
                if (afterIdat >= 0) return null;
                if (firstIdat < 0) firstIdat = pos;
                idat.write(png, pos + 8, length);
            } else if (firstIdat >= 0 && afterIdat < 0) afterIdat = pos;
            boolean end = png[pos + 4] == 'I' && png[pos + 5] == 'E' && png[pos + 6] == 'N' && png[pos + 7] == 'D';
            pos += 12 + length;
            if (end) break;
        }
        if (firstIdat < 0 || afterIdat < 0) return null;
        byte[] packed = idat.toByteArray();
        byte[] raw = inflate(packed);
        if (raw == null) return null;
        Deflater deflater = new Deflater(PNG_LEVEL);
        byte[] repacked;
        try {
            deflater.setInput(raw);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(packed.length);
            byte[] buffer = new byte[64 << 10];
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer));
            repacked = out.toByteArray();
        } finally {
            deflater.end();
        }
        if (repacked.length >= packed.length) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream(png.length);
        out.write(png, 0, firstIdat);
        writeInt(out, repacked.length);
        byte[] type = {'I', 'D', 'A', 'T'};
        out.write(type, 0, 4);
        out.write(repacked, 0, repacked.length);
        CRC32 crc = new CRC32();
        crc.update(type);
        crc.update(repacked);
        writeInt(out, (int) crc.getValue());
        out.write(png, afterIdat, png.length - afterIdat);
        stats.png.incrementAndGet();
        stats.pngBefore.addAndGet(packed.length);
        stats.pngAfter.addAndGet(repacked.length);
        return out.toByteArray();
    }

    private static byte[] inflate(byte[] packed) throws DataFormatException {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(packed);
            ByteArrayOutputStream out = new ByteArrayOutputStream(packed.length * 4);
            byte[] buffer = new byte[64 << 10];
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) return null;
                out.write(buffer, 0, n);
                if (out.size() > MAX_RAW_PNG) return null;
            }
            return out.toByteArray();
        } finally {
            inflater.end();
        }
    }

    private static int readInt(byte[] data, int at) {
        return (data[at] & 0xff) << 24 | (data[at + 1] & 0xff) << 16 | (data[at + 2] & 0xff) << 8 | data[at + 3] & 0xff;
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value >>> 24);
        out.write(value >>> 16 & 0xff);
        out.write(value >>> 8 & 0xff);
        out.write(value & 0xff);
    }
}
