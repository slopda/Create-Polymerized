package com.zurrtum.create.infrastructure.polymer.ponder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.veeda.createpoly.assets.PackJson;
import dev.veeda.createpoly.assets.PngCodec;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class PolymerPonderItems {
    private PolymerPonderItems() {}

    record Draw(String model, Matrix4f transform, At at, Spin spin, int tint) {
        Draw(String model, Matrix4f transform) {
            this(model, transform, null, null, -1);
        }

        Draw(String model, Matrix4f transform, At at) {
            this(model, transform, at, null, -1);
        }

        Draw(String model, Matrix4f transform, At at, Spin spin) {
            this(model, transform, at, spin, -1);
        }
    }

    static final String FONT = "assets/minecraft/textures/font/ascii.png", FONT_TEXTURE = "create:block/p/font_ascii";

    static String glyphModel(char c) {
        return PolymerPonderScene.PART_BANK + "glyph/" + Integer.toHexString(c);
    }

    static boolean hasGlyph(char c) {
        return c > ' ' && c < 127;
    }

    private static int[] advances;

    static synchronized int[] advances(Function<String, byte[]> resources) throws IOException {
        if (advances != null) return advances;
        byte[] data = resources.apply(FONT);
        if (data == null) throw new IOException("Missing " + FONT);
        BufferedImage sheet = PngCodec.decode(data);
        int cell = sheet.getWidth() / 16;
        int[] result = new int[128];
        for (int c = 0; c < 128; c++) {
            int width = 0;
            for (int x = cell - 1; x >= 0 && width == 0; x--)
                for (int y = 0; y < cell; y++)
                    if ((sheet.getRGB((c % 16) * cell + x, (c / 16) * cell + y) >>> 24) != 0) {
                        width = x + 1;
                        break;
                    }
            result[c] = width + 1;
        }
        result[' '] = 4;
        advances = result;
        return result;
    }

    static void glyphs(List<Draw> draws, String text, int color, Matrix4f pose, Function<String, byte[]> resources)
            throws IOException {
        int[] advance = advances(resources);
        int width = 0;
        for (char c : text.toCharArray()) width += c < 128 ? advance[c] : 0;
        float x = 1 - width / 2f;
        for (char c : text.toCharArray()) {
            if (hasGlyph(c))
                draws.add(new Draw(
                        glyphModel(c),
                        new Matrix4f(pose).translate(x, -9 + 8, 0).scale(8, -8, 1),
                        null,
                        null,
                        0xFF000000 | color));
            x += c < 128 ? advance[c] : 0;
        }
    }

    record Spin(float ax, float ay, float az, float px, float py, float pz, float deg) {}

    record At(int x, int y, int z) {}

    static JsonObject track(String id) throws IOException {
        try (var stream =
                PolymerPonderItems.class.getResourceAsStream("/polymerpatch/ponder-capture/" + id + ".json")) {
            if (stream == null) return null;
            return PackJson.object(stream.readAllBytes());
        }
    }

    static String current(String model) {
        return model.startsWith("create:block/polymer/ponder/")
                ? "create:block/p/" + model.substring("create:block/polymer/ponder/".length())
                : model;
    }

    static Map<Integer, List<Draw>> draws(JsonObject track, Function<String, byte[]> resources) throws IOException {
        Map<Integer, List<Draw>> result = new HashMap<>();
        Map<Draw, Draw> canonical = new HashMap<>();
        List<Draw> previous = List.of();
        Map<String, String> models = new HashMap<>();
        for (JsonElement tickElement : track.getAsJsonArray("ticks")) {
            JsonObject tick = tickElement.getAsJsonObject();
            List<Draw> draws = new ArrayList<>();
            for (JsonElement itemElement : tick.getAsJsonArray("items")) {
                JsonObject item = itemElement.getAsJsonObject();
                if (!item.has("item") && item.has("model")) {
                    draws.add(new Draw(
                            current(item.get("model").getAsString()),
                            matrix(item.getAsJsonArray("m")),
                            null,
                            null,
                            item.has("tint") ? item.get("tint").getAsInt() : -1));
                    continue;
                }
                if (item.has("text")) {
                    glyphs(
                            draws,
                            item.get("text").getAsString(),
                            item.get("color").getAsInt(),
                            matrix(item.getAsJsonArray("m")),
                            resources);
                    continue;
                }
                String id = item.get("item").getAsString();
                String model = models.get(id);
                if (model == null) {
                    model = itemModel(resources, id);
                    models.put(id, model);
                }
                String context = item.get("ctx").getAsString();
                Matrix4f display = display(resources, model, context);
                if (item.has("model")) {
                    model = current(item.get("model").getAsString());
                    display = new Matrix4f();
                }
                Matrix4f pose;
                if (context.equals("ground") && !item.has("m")) {
                    JsonArray p = item.getAsJsonArray("p");
                    float age = item.get("age").getAsFloat(),
                            bob = item.get("bob").getAsFloat();
                    float minY = minY(resources, model, display);
                    float hover = (float) Math.sin(age / 10 + bob) * 0.1f + 0.1f;
                    pose = new Matrix4f()
                            .translation(
                                    p.get(0).getAsFloat(),
                                    p.get(1).getAsFloat(),
                                    p.get(2).getAsFloat())
                            .translate(0, -minY + 0.0625f + hover, 0)
                            .rotateY(age / 20 + bob);
                } else {
                    pose = matrix(item.getAsJsonArray("m"));
                }
                draws.add(new Draw(model, pose.mul(display)));
            }
            if (tick.has("models"))
                for (JsonElement modelElement : tick.getAsJsonArray("models")) {
                    JsonObject model = modelElement.getAsJsonObject();
                    draws.add(new Draw(current(model.get("model").getAsString()), matrix(model.getAsJsonArray("m"))));
                }
            if (tick.has("parts"))
                for (JsonElement partElement : tick.getAsJsonArray("parts")) {
                    JsonObject part = partElement.getAsJsonObject();
                    JsonArray at = part.getAsJsonArray("at");
                    Spin spin = null;
                    if (part.has("spin")) {
                        JsonObject s = part.getAsJsonObject("spin");
                        JsonArray axis = s.getAsJsonArray("axis"), pivot = s.getAsJsonArray("pivot");
                        spin = new Spin(
                                axis.get(0).getAsFloat(),
                                axis.get(1).getAsFloat(),
                                axis.get(2).getAsFloat(),
                                pivot.get(0).getAsFloat(),
                                pivot.get(1).getAsFloat(),
                                pivot.get(2).getAsFloat(),
                                s.get("deg").getAsFloat());
                    }
                    draws.add(new Draw(
                            current(part.get("model").getAsString()),
                            matrix(part.getAsJsonArray("m")),
                            new At(
                                    at.get(0).getAsInt(),
                                    at.get(1).getAsInt(),
                                    at.get(2).getAsInt()),
                            spin));
                }
            draws.replaceAll(draw -> canonical.computeIfAbsent(draw, d -> d));
            if (!draws.equals(previous)) previous = List.copyOf(draws);
            result.put(tick.get("t").getAsInt(), previous);
        }
        return result;
    }

    private static Matrix4f matrix(JsonArray rowMajor) {
        float[] values = new float[16];
        for (int i = 0; i < 16; i++) values[i] = rowMajor.get(i).getAsFloat();
        return new Matrix4f().set(values).transpose();
    }

    private static JsonObject json(Function<String, byte[]> resources, String path) throws IOException {
        byte[] data = resources.apply(path);
        if (data == null) throw new IOException("Missing " + path);
        return PackJson.object(data);
    }

    static String itemModel(Function<String, byte[]> resources, String item) throws IOException {
        String ns = item.substring(0, item.indexOf(':')), path = item.substring(item.indexOf(':') + 1);
        String definition = "assets/" + ns + "/items/" + path + ".json";
        byte[] original = sourceDefinition(definition);
        JsonObject model =
                (original != null ? PackJson.object(original) : json(resources, definition)).getAsJsonObject("model");
        for (int depth = 0; depth < 8 && model != null; depth++) {
            String type = model.get("type").getAsString();
            switch (type) {
                case "minecraft:model", "model" -> {
                    String id = model.get("model").getAsString();
                    return id.contains(":") ? id : "minecraft:" + id;
                }
                case "minecraft:select", "select", "minecraft:range_dispatch", "range_dispatch" ->
                    model = model.has("fallback") ? model.getAsJsonObject("fallback") : null;
                case "minecraft:condition", "condition" -> model = model.getAsJsonObject("on_false");
                case "minecraft:composite", "composite" ->
                    model = model.getAsJsonArray("models").get(0).getAsJsonObject();
                case "create:model/wrench" -> {
                    return "create:item/wrench/item";
                }
                default -> {
                    if (model.has("model") && model.get("model").isJsonPrimitive()) {
                        String id = model.get("model").getAsString();
                        return id.contains(":") ? id : "minecraft:" + id;
                    }
                    model = null;
                }
            }
        }
        throw new IOException("Item without a plain model: " + item);
    }

    private static byte[] sourceDefinition(String path) {
        try {
            var loader = FabricLoader.getInstance();
            for (String mod : new String[] {"createpoly", "create"}) {
                var container = loader.getModContainer(mod);
                if (container.isEmpty()) return null;
                var file = container.get().findPath(path);
                if (file.isPresent()) return Files.readAllBytes(file.get());
            }
            return null;
        } catch (IOException | RuntimeException | LinkageError error) {
            return null;
        }
    }

    static Matrix4f display(Function<String, byte[]> resources, String model, String context) throws IOException {
        String id = model;
        for (int depth = 0; depth < 16 && id != null; depth++) {
            if (id.startsWith("minecraft:builtin/") || id.startsWith("builtin/")) break;
            String ns = id.contains(":") ? id.substring(0, id.indexOf(':')) : "minecraft",
                    path = id.substring(id.indexOf(':') + 1);
            byte[] data = resources.apply("assets/" + ns + "/models/" + path + ".json");
            if (data == null) break;
            JsonObject json = PackJson.object(data);
            if (json.has("display") && json.getAsJsonObject("display").has(context)) {
                JsonObject transform = json.getAsJsonObject("display").getAsJsonObject(context);
                Vector3f rotation = vector(transform, "rotation", 0),
                        translation = vector(transform, "translation", 0).mul(0.0625f),
                        scale = vector(transform, "scale", 1);
                translation.set(
                        Math.clamp(translation.x, -5, 5),
                        Math.clamp(translation.y, -5, 5),
                        Math.clamp(translation.z, -5, 5));
                scale.set(Math.clamp(scale.x, -4, 4), Math.clamp(scale.y, -4, 4), Math.clamp(scale.z, -4, 4));
                return new Matrix4f()
                        .translate(translation)
                        .rotateXYZ((float) Math.toRadians(rotation.x), (float) Math.toRadians(rotation.y), (float)
                                Math.toRadians(rotation.z))
                        .scale(scale)
                        .translate(-0.5f, -0.5f, -0.5f);
            }
            id = json.has("parent") ? json.get("parent").getAsString() : null;
            if (id != null && !id.contains(":")) id = "minecraft:" + id;
        }
        return new Matrix4f().translation(-0.5f, -0.5f, -0.5f);
    }

    private static Vector3f vector(JsonObject transform, String key, float fallback) {
        if (!transform.has(key)) return new Vector3f(fallback);
        JsonArray a = transform.getAsJsonArray(key);
        return new Vector3f(
                a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }

    static float minY(Function<String, byte[]> resources, String model, Matrix4f display) throws IOException {
        List<float[]> boxes = new ArrayList<>();
        String id = model;
        for (int depth = 0; depth < 16 && id != null && boxes.isEmpty(); depth++) {
            if (id.endsWith("builtin/generated")) {
                boxes.add(new float[] {0, 0, 7.5f, 16, 16, 8.5f});
                break;
            }
            String ns = id.contains(":") ? id.substring(0, id.indexOf(':')) : "minecraft",
                    path = id.substring(id.indexOf(':') + 1);
            byte[] data = resources.apply("assets/" + ns + "/models/" + path + ".json");
            if (data == null) break;
            JsonObject json = PackJson.object(data);
            if (json.has("elements"))
                for (JsonElement element : json.getAsJsonArray("elements")) {
                    JsonArray from = element.getAsJsonObject().getAsJsonArray("from"),
                            to = element.getAsJsonObject().getAsJsonArray("to");
                    boxes.add(new float[] {
                        from.get(0).getAsFloat(),
                        from.get(1).getAsFloat(),
                        from.get(2).getAsFloat(),
                        to.get(0).getAsFloat(),
                        to.get(1).getAsFloat(),
                        to.get(2).getAsFloat()
                    });
                }
            id = json.has("parent") ? json.get("parent").getAsString() : null;
            if (id != null && !id.contains(":") && !id.startsWith("builtin/")) id = "minecraft:" + id;
        }
        if (boxes.isEmpty()) throw new IOException("Item model without geometry: " + model);
        float min = Float.POSITIVE_INFINITY;
        for (float[] box : boxes)
            for (int c = 0; c < 8; c++) {
                Vector3f p = display.transformPosition(new Vector3f(
                                (c & 1) == 0 ? box[0] : box[3],
                                (c & 2) == 0 ? box[1] : box[4],
                                (c & 4) == 0 ? box[2] : box[5])
                        .div(16));
                min = Math.min(min, p.y);
            }
        return min;
    }
}
