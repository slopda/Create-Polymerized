package com.zurrtum.create.infrastructure.polymer.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zurrtum.create.Create;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerGuiGlyphs;
import com.zurrtum.create.infrastructure.polymer.ponder.PolymerPonderBaker;
import dev.veeda.createpoly.assets.PackJson;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

final class PolymerHudIcons {
    static final String NAMESPACE = "create";
    private static final int SS = 4;

    record Atlas(
            Map<String, Integer> codepoints,
            Map<Integer, Integer> advances,
            Map<Integer, Integer> widths,
            JsonArray chars,
            String file,
            int cellHeight) {
        Integer codepoint(String key) {
            return codepoints.get(key);
        }
    }

    private PolymerHudIcons() {}

    static String key(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    static Atlas write(
            ResourcePackBuilder builder,
            String name,
            List<String> keys,
            int cellHeight,
            int padTop,
            int fontHeight,
            Map<String, Integer> ascents,
            int base)
            throws IOException {
        Renderer renderer = new Renderer(builder);
        Map<String, Integer> unique = new LinkedHashMap<>();
        List<BufferedImage> images = new ArrayList<>();
        Map<String, Integer> slotOf = new LinkedHashMap<>();
        int failed = 0;
        for (String key : keys) {
            String primary = key, fallback = null;
            int bar = key.indexOf('|');
            if (bar >= 0) {
                primary = key.substring(0, bar);
                fallback = key.substring(bar + 1);
            }
            BufferedImage icon = tryIcon(renderer, primary);
            if (icon == null && fallback != null) icon = tryIcon(renderer, fallback);
            if (icon == null) {
                failed++;
                icon = tryIcon(renderer, key(Items.BARRIER));
            }
            if (icon == null) icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            int[] pixels = icon.getRGB(0, 0, 16, 16, null, 0, 16);
            String hash = Arrays.toString(pixels);
            Integer slot = unique.get(hash);
            if (slot == null) {
                slot = images.size();
                unique.put(hash, slot);
                images.add(icon);
            }
            slotOf.put(key, slot);
        }
        int columns = 64, rows = Math.max(1, (images.size() + columns - 1) / columns);
        BufferedImage atlas = new BufferedImage(columns * 16, rows * cellHeight, BufferedImage.TYPE_INT_ARGB);
        Map<String, Integer> codepoints = new LinkedHashMap<>();
        Map<Integer, Integer> advances = new HashMap<>(), widths = new HashMap<>();
        List<StringBuilder> charRows = new ArrayList<>();
        for (int r = 0; r < rows; r++) charRows.add(new StringBuilder());
        float scale = fontHeight / (float) cellHeight;
        for (int i = 0; i < images.size(); i++) {
            BufferedImage icon = images.get(i);
            int col = i % columns, row = i / columns, width = 0;
            for (int y = 0; y < 16; y++)
                for (int x = 0; x < 16; x++) {
                    int argb = icon.getRGB(x, y);
                    atlas.setRGB(col * 16 + x, row * cellHeight + padTop + y, argb);
                    if (argb >>> 24 != 0) width = Math.max(width, x + 1);
                }
            int codepoint = base + i;
            charRows.get(row).appendCodePoint(codepoint);
            advances.put(codepoint, (int) (.5f + width * scale) + 1);
            widths.put(codepoint, width);
        }
        for (var entry : slotOf.entrySet()) {
            codepoints.put(entry.getKey(), base + entry.getValue());
            int bar = entry.getKey().indexOf('|');
            if (bar >= 0) codepoints.putIfAbsent(entry.getKey().substring(0, bar), base + entry.getValue());
        }
        Create.LOGGER.info("HUD icons {}: {} keys, {} distinct images", name, keys.size(), images.size());
        for (StringBuilder row : charRows) while (row.codePointCount(0, row.length()) < columns) row.append('\u0000');
        String file = "font/hud/" + name + ".png";
        builder.addData("assets/" + NAMESPACE + "/textures/" + file, png(atlas));
        for (var font : ascents.entrySet()) {
            // 26.2 rejects the whole font when a bitmap provider's ascent exceeds its height.
            if (font.getValue() > fontHeight)
                throw new IOException(
                        "HUD font " + font.getKey() + ": ascent " + font.getValue() + " > height " + fontHeight);
            JsonObject provider = PolymerGuiGlyphs.bitmapProvider(NAMESPACE + ":" + file, fontHeight, font.getValue());
            JsonArray chars = new JsonArray();
            charRows.forEach(row -> chars.add(row.toString()));
            provider.add("chars", chars);
            JsonArray providers = new JsonArray();
            providers.add(provider);
            JsonObject json = new JsonObject();
            json.add("providers", providers);
            builder.addStringData("assets/" + NAMESPACE + "/font/" + font.getKey() + ".json", json.toString());
        }
        if (failed > 0)
            Create.LOGGER.info(
                    "HUD icons {}: {} of {} drawn as a barrier (no resolvable model)", name, failed, keys.size());
        JsonArray grid = new JsonArray();
        charRows.forEach(row -> grid.add(row.toString()));
        return new Atlas(
                Map.copyOf(codepoints),
                Map.copyOf(advances),
                Map.copyOf(widths),
                grid,
                NAMESPACE + ":" + file,
                cellHeight);
    }

    private static BufferedImage tryIcon(Renderer renderer, String key) {
        try {
            BufferedImage icon = renderer.icon(key);
            return icon == null || empty(icon) ? null : icon;
        } catch (IOException | RuntimeException error) {
            return null;
        }
    }

    private static boolean empty(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++) if (image.getRGB(x, y) >>> 24 != 0) return false;
        return true;
    }

    static byte[] png(BufferedImage image) throws IOException {
        return PngCodec.encode(image);
    }

    private static final class Renderer {
        private final ResourcePackBuilder builder;
        private final Map<String, BufferedImage> textures = new HashMap<>();

        Renderer(ResourcePackBuilder builder) {
            this.builder = builder;
        }

        BufferedImage icon(String key) throws IOException {
            if (key.startsWith("texture:")) return fit(texture(key.substring(8)));
            if (key.startsWith("state:")) return state(key.substring(6));
            Identifier id = Identifier.parse(key);
            JsonObject definition = json("assets/" + id.getNamespace() + "/items/" + id.getPath() + ".json");
            if (definition == null || !definition.has("model")) return null;
            Leaf leaf = leaf(definition.get("model"));
            if (leaf == null) return null;
            return render(leaf);
        }

        private record Leaf(String model, JsonArray tints, boolean special) {}

        private Leaf leaf(JsonElement element) {
            if (element == null || !element.isJsonObject()) return null;
            JsonObject node = element.getAsJsonObject();
            String type = node.has("type") ? node.get("type").getAsString().replace("minecraft:", "") : "";
            switch (type) {
                case "model":
                    return new Leaf(
                            node.get("model").getAsString(),
                            node.has("tints") ? node.getAsJsonArray("tints") : new JsonArray(),
                            false);
                case "special":
                    return new Leaf(node.get("base").getAsString(), new JsonArray(), true);
                case "composite": {
                    JsonArray models = node.getAsJsonArray("models");
                    for (JsonElement child : models) {
                        Leaf leaf = leaf(child);
                        if (leaf != null) return leaf;
                    }
                    return null;
                }
                case "condition": {
                    Leaf leaf = leaf(node.get("on_false"));
                    return leaf != null ? leaf : leaf(node.get("on_true"));
                }
                case "select": {
                    Leaf leaf = leaf(node.get("fallback"));
                    if (leaf != null) return leaf;
                    for (JsonElement c : node.getAsJsonArray("cases")) {
                        leaf = leaf(c.getAsJsonObject().get("model"));
                        if (leaf != null) return leaf;
                    }
                    return null;
                }
                case "range_dispatch": {
                    Leaf leaf = leaf(node.get("fallback"));
                    if (leaf != null) return leaf;
                    for (JsonElement c : node.getAsJsonArray("entries")) {
                        leaf = leaf(c.getAsJsonObject().get("model"));
                        if (leaf != null) return leaf;
                    }
                    return null;
                }
                default:
                    return null;
            }
        }

        private record Model(Map<String, String> textures, JsonArray elements, JsonObject gui, boolean generated) {}

        private Model model(String id) throws IOException {
            Map<String, String> textures = new HashMap<>();
            JsonArray elements = null;
            JsonObject gui = null;
            boolean generated = false;
            String current = id;
            for (int depth = 0; depth < 24 && current != null; depth++) {
                Identifier key = Identifier.parse(current.contains(":") ? current : "minecraft:" + current);
                if (key.getNamespace().equals("minecraft") && key.getPath().equals("builtin/generated")) {
                    generated = true;
                    break;
                }
                if (key.getNamespace().equals("minecraft") && key.getPath().startsWith("builtin/")) break;
                JsonObject json = json("assets/" + key.getNamespace() + "/models/" + key.getPath() + ".json");
                if (json == null) break;
                if (json.has("textures"))
                    for (var t : json.getAsJsonObject("textures").entrySet()) {
                        JsonElement value = t.getValue();
                        if (value.isJsonObject() && value.getAsJsonObject().has("sprite"))
                            value = value.getAsJsonObject().get("sprite");
                        if (value.isJsonPrimitive()) textures.putIfAbsent(t.getKey(), value.getAsString());
                    }
                if (elements == null && json.has("elements")) elements = json.getAsJsonArray("elements");
                if (gui == null
                        && json.has("display")
                        && json.getAsJsonObject("display").has("gui"))
                    gui = json.getAsJsonObject("display").getAsJsonObject("gui");
                current = json.has("parent") ? json.get("parent").getAsString() : null;
            }
            return new Model(textures, elements, gui, generated);
        }

        private String resolve(Map<String, String> textures, String ref) {
            for (int hop = 0; hop < 16 && ref != null && ref.startsWith("#"); hop++)
                ref = textures.get(ref.substring(1));
            return ref;
        }

        private BufferedImage render(Leaf leaf) throws IOException {
            Model model = model(leaf.model());
            if (model.elements() != null && !model.elements().isEmpty())
                return elements(List.of(new Part(model, 0, 0, leaf.tints())), model.gui());
            if (model.generated()) {
                BufferedImage out = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                boolean any = false;
                for (int layer = 0; layer < 8; layer++) {
                    String texture = resolve(model.textures(), model.textures().get("layer" + layer));
                    if (texture == null) continue;
                    BufferedImage image = fit(texture(texture));
                    if (image == null) continue;
                    any = true;
                    int tint = tint(leaf.tints(), layer);
                    for (int y = 0; y < 16; y++)
                        for (int x = 0; x < 16; x++) blend(out, x, y, multiply(image.getRGB(x, y), tint));
                }
                if (any) return out;
            }
            String particle = resolve(model.textures(), model.textures().get("particle"));
            return particle == null ? null : fit(texture(particle));
        }

        private static int tint(JsonArray tints, int index) {
            if (tints == null
                    || index < 0
                    || index >= tints.size()
                    || !tints.get(index).isJsonObject()) return 0xFFFFFFFF;
            JsonObject t = tints.get(index).getAsJsonObject();
            JsonElement value = t.has("value") ? t.get("value") : t.get("default");
            if (value == null) return 0xFFFFFFFF;
            if (value.isJsonPrimitive()) return 0xFF000000 | value.getAsInt();
            if (value.isJsonArray()) {
                JsonArray rgb = value.getAsJsonArray();
                return 0xFF000000
                        | Math.round(rgb.get(0).getAsFloat() * 255) << 16
                        | Math.round(rgb.get(1).getAsFloat() * 255) << 8
                        | Math.round(rgb.get(2).getAsFloat() * 255);
            }
            return 0xFFFFFFFF;
        }

        private static final String[] FACES = {"down", "up", "north", "south", "west", "east"};
        private static final float[] SHADE = {.5f, 1f, .8f, .8f, .6f, .6f};
        private static final Vector3fc[] NORMALS = {
            new Vector3f(0, -1, 0),
            new Vector3f(0, 1, 0),
            new Vector3f(0, 0, -1),
            new Vector3f(0, 0, 1),
            new Vector3f(-1, 0, 0),
            new Vector3f(1, 0, 0)
        };

        private static float shade(Vector3f n) {
            float ax = Math.abs(n.x), ay = Math.abs(n.y), az = Math.abs(n.z);
            if (ay >= ax && ay >= az) return n.y > 0 ? 1f : .5f;
            return az >= ax ? .8f : .6f;
        }

        private record Part(Model model, int x, int y, JsonArray tints) {}

        private static final JsonObject BLOCK_GUI = JsonParser.parseString(
                        "{\"rotation\":[30,225,0],\"translation\":[0,0,0],\"scale\":[0.625,0.625,0.625]}")
                .getAsJsonObject();

        private BufferedImage state(String spec) throws IOException {
            int open = spec.indexOf('[');
            Identifier block = Identifier.parse(open < 0 ? spec : spec.substring(0, open));
            Map<String, String> props = new HashMap<>();
            if (open >= 0)
                for (String pair : spec.substring(open + 1, spec.length() - 1).split(",")) {
                    int eq = pair.indexOf('=');
                    if (eq > 0) props.put(pair.substring(0, eq), pair.substring(eq + 1));
                }
            JsonObject states = json("assets/" + block.getNamespace() + "/blockstates/" + block.getPath() + ".json");
            if (states == null) return null;
            List<Part> parts = new ArrayList<>();
            if (states.has("variants")) {
                for (var variant : states.getAsJsonObject("variants").entrySet()) {
                    if (!matchesVariant(variant.getKey(), props)) continue;
                    addPart(parts, variant.getValue());
                    break;
                }
            } else if (states.has("multipart")) {
                for (JsonElement part : states.getAsJsonArray("multipart")) {
                    JsonObject p = part.getAsJsonObject();
                    if (!p.has("when") || matchesWhen(p.get("when").getAsJsonObject(), props))
                        addPart(parts, p.get("apply"));
                }
            }
            if (parts.isEmpty()) return null;
            return elements(parts, BLOCK_GUI);
        }

        private void addPart(List<Part> parts, JsonElement apply) throws IOException {
            if (apply.isJsonArray()) apply = apply.getAsJsonArray().get(0);
            JsonObject v = apply.getAsJsonObject();
            Model model = model(v.get("model").getAsString());
            if (model.elements() == null || model.elements().isEmpty()) return;
            parts.add(new Part(
                    model,
                    v.has("x") ? v.get("x").getAsInt() : 0,
                    v.has("y") ? v.get("y").getAsInt() : 0,
                    new JsonArray()));
        }

        private static boolean matchesVariant(String key, Map<String, String> props) {
            if (key.isEmpty()) return true;
            for (String pair : key.split(",")) {
                int eq = pair.indexOf('=');
                if (eq < 0 || !pair.substring(eq + 1).equals(props.get(pair.substring(0, eq)))) return false;
            }
            return true;
        }

        private static boolean matchesWhen(JsonObject when, Map<String, String> props) {
            if (when.has("OR")) {
                for (JsonElement c : when.getAsJsonArray("OR"))
                    if (matchesWhen(c.getAsJsonObject(), props)) return true;
                return false;
            }
            if (when.has("AND")) {
                for (JsonElement c : when.getAsJsonArray("AND"))
                    if (!matchesWhen(c.getAsJsonObject(), props)) return false;
                return true;
            }
            for (var entry : when.entrySet()) {
                String value = props.get(entry.getKey());
                boolean negate = false;
                String expected = entry.getValue().getAsString();
                if (expected.startsWith("!")) {
                    negate = true;
                    expected = expected.substring(1);
                }
                boolean any = Arrays.asList(expected.split("\\|")).contains(value);
                if (any == negate) return false;
            }
            return true;
        }

        private BufferedImage elements(List<Part> parts, JsonObject gui) throws IOException {
            int size = 16 * SS;
            int[] color = new int[size * size];
            float[] depth = new float[size * size];
            Arrays.fill(depth, Float.NEGATIVE_INFINITY);
            Vector3f rotation = vector(gui, "rotation", new Vector3f());
            Vector3f translation = vector(gui, "translation", new Vector3f());
            Vector3f scale = vector(gui, "scale", new Vector3f(1));
            Matrix4f view = new Matrix4f()
                    .translate(translation.x / 16, translation.y / 16, translation.z / 16)
                    .rotate(new Quaternionf()
                            .rotationXYZ((float) Math.toRadians(rotation.x), (float) Math.toRadians(rotation.y), (float)
                                    Math.toRadians(rotation.z)))
                    .scale(scale.x, scale.y, scale.z)
                    .translate(-.5f, -.5f, -.5f)
                    .scale(1 / 16f);
            for (Part part : parts) {
                Model model = part.model();
                JsonArray tints = part.tints();
                Quaternionf variant = new Quaternionf()
                        .rotationYXZ((float) Math.toRadians(-part.y()), (float) Math.toRadians(-part.x()), 0);
                Matrix4f placed =
                        new Matrix4f(view).translate(8, 8, 8).rotate(variant).translate(-8, -8, -8);
                for (JsonElement e : model.elements()) {
                    JsonObject element = e.getAsJsonObject();
                    Vector3f from = vec(element.getAsJsonArray("from")), to = vec(element.getAsJsonArray("to"));
                    Matrix4f m = new Matrix4f(placed).mul(elementRotation(element));
                    Matrix4f orientation = new Matrix4f().rotate(variant).mul(elementRotation(element));
                    JsonObject faces = element.getAsJsonObject("faces");
                    if (faces == null) continue;
                    for (int f = 0; f < 6; f++) {
                        JsonObject face = faces.getAsJsonObject(FACES[f]);
                        if (face == null) continue;
                        String texture =
                                resolve(model.textures(), face.get("texture").getAsString());
                        BufferedImage image = texture == null ? null : texture(texture);
                        if (image == null) continue;
                        JsonArray uv = face.has("uv")
                                ? face.getAsJsonArray("uv")
                                : PolymerPonderBaker.defaultUv(FACES[f], from, to);
                        int tint = face.has("tintindex")
                                ? tint(tints, face.get("tintindex").getAsInt())
                                : 0xFFFFFFFF;
                        int rotationSteps = face.has("rotation")
                                ? Math.floorMod(face.get("rotation").getAsInt() / 90, 4)
                                : 0;
                        rasterize(
                                color,
                                depth,
                                size,
                                m,
                                f,
                                from,
                                to,
                                image,
                                uv,
                                tint,
                                rotationSteps,
                                shade(orientation.transformDirection(NORMALS[f].get(new Vector3f()))));
                    }
                }
            }
            BufferedImage out = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 16; y++)
                for (int x = 0; x < 16; x++) {
                    long a = 0, r = 0, g = 0, b = 0;
                    for (int sy = 0; sy < SS; sy++)
                        for (int sx = 0; sx < SS; sx++) {
                            int c = color[(y * SS + sy) * size + x * SS + sx], alpha = c >>> 24;
                            a += alpha;
                            r += (long) (c >> 16 & 255) * alpha;
                            g += (long) (c >> 8 & 255) * alpha;
                            b += (long) (c & 255) * alpha;
                        }
                    if (a == 0) continue;
                    int alpha = (int) (a / (SS * SS));
                    out.setRGB(x, y, alpha << 24 | (int) (r / a) << 16 | (int) (g / a) << 8 | (int) (b / a));
                }
            return out;
        }

        private static Matrix4f elementRotation(JsonObject element) {
            Matrix4f m = new Matrix4f();
            if (!element.has("rotation")) return m;
            JsonObject r = element.getAsJsonObject("rotation");
            Vector3f origin = r.has("origin") ? vec(r.getAsJsonArray("origin")) : new Vector3f(8, 8, 8);
            m.translate(origin);
            if (r.has("axis")) {
                float angle = (float) Math.toRadians(r.get("angle").getAsFloat());
                switch (r.get("axis").getAsString()) {
                    case "x" -> m.rotateX(angle);
                    case "y" -> m.rotateY(angle);
                    default -> m.rotateZ(angle);
                }
            } else {
                m.rotateZ((float) Math.toRadians(r.has("z") ? r.get("z").getAsFloat() : 0))
                        .rotateY((float) Math.toRadians(r.has("y") ? r.get("y").getAsFloat() : 0))
                        .rotateX((float) Math.toRadians(r.has("x") ? r.get("x").getAsFloat() : 0));
            }
            return m.translate(new Vector3f(origin).negate());
        }

        private static void rasterize(
                int[] color,
                float[] depth,
                int size,
                Matrix4f m,
                int face,
                Vector3f f,
                Vector3f t,
                BufferedImage image,
                JsonArray uv,
                int tint,
                int rotation,
                float shade) {
            Vector3f p0, u, v;
            switch (face) {
                case 0 -> {
                    p0 = new Vector3f(f.x, f.y, t.z);
                    u = new Vector3f(t.x - f.x, 0, 0);
                    v = new Vector3f(0, 0, f.z - t.z);
                }
                case 1 -> {
                    p0 = new Vector3f(f.x, t.y, f.z);
                    u = new Vector3f(t.x - f.x, 0, 0);
                    v = new Vector3f(0, 0, t.z - f.z);
                }
                case 2 -> {
                    p0 = new Vector3f(t.x, t.y, f.z);
                    u = new Vector3f(f.x - t.x, 0, 0);
                    v = new Vector3f(0, f.y - t.y, 0);
                }
                case 3 -> {
                    p0 = new Vector3f(f.x, t.y, t.z);
                    u = new Vector3f(t.x - f.x, 0, 0);
                    v = new Vector3f(0, f.y - t.y, 0);
                }
                case 4 -> {
                    p0 = new Vector3f(f.x, t.y, f.z);
                    u = new Vector3f(0, 0, t.z - f.z);
                    v = new Vector3f(0, f.y - t.y, 0);
                }
                default -> {
                    p0 = new Vector3f(t.x, t.y, t.z);
                    u = new Vector3f(0, 0, f.z - t.z);
                    v = new Vector3f(0, f.y - t.y, 0);
                }
            }
            Vector3f a = m.transformPosition(new Vector3f(p0));
            Vector3f au = m.transformPosition(new Vector3f(p0).add(u)).sub(a);
            Vector3f av = m.transformPosition(new Vector3f(p0).add(v)).sub(a);
            float k = 16 * SS, ox = 8 * SS + a.x * k, oy = 8 * SS - a.y * k;
            float ux = au.x * k, uy = -au.y * k, vx = av.x * k, vy = -av.y * k;
            float det = ux * vy - uy * vx;
            if (Math.abs(det) < 1e-6f) return;
            float minX = Math.min(Math.min(ox, ox + ux), Math.min(ox + vx, ox + ux + vx)),
                    maxX = Math.max(Math.max(ox, ox + ux), Math.max(ox + vx, ox + ux + vx));
            float minY = Math.min(Math.min(oy, oy + uy), Math.min(oy + vy, oy + uy + vy)),
                    maxY = Math.max(Math.max(oy, oy + uy), Math.max(oy + vy, oy + uy + vy));
            int x0 = Math.max(0, (int) Math.floor(minX)), x1 = Math.min(size - 1, (int) Math.ceil(maxX));
            int y0 = Math.max(0, (int) Math.floor(minY)), y1 = Math.min(size - 1, (int) Math.ceil(maxY));
            float u0 = uv.get(0).getAsFloat(),
                    v0 = uv.get(1).getAsFloat(),
                    u1 = uv.get(2).getAsFloat(),
                    v1 = uv.get(3).getAsFloat();
            int tw = image.getWidth(), th = Math.min(image.getHeight(), image.getWidth());
            for (int py = y0; py <= y1; py++)
                for (int px = x0; px <= x1; px++) {
                    float dx = px + .5f - ox, dy = py + .5f - oy;
                    float s = (dx * vy - dy * vx) / det, tt = (ux * dy - uy * dx) / det;
                    if (s < 0 || s > 1 || tt < 0 || tt > 1) continue;
                    float z = a.z + s * au.z + tt * av.z;
                    int index = py * size + px;
                    if (z < depth[index]) continue;
                    float rs = s, rt = tt;
                    for (int r = 0; r < rotation; r++) {
                        float tmp = rs;
                        rs = rt;
                        rt = 1 - tmp;
                    }
                    float su = u0 + rs * (u1 - u0), sv = v0 + rt * (v1 - v0);
                    int tx = Math.clamp((int) Math.floor(su / 16 * tw), 0, tw - 1),
                            ty = Math.clamp((int) Math.floor(sv / 16 * th), 0, th - 1);
                    int texel = image.getRGB(tx, ty);
                    if (texel >>> 24 < 26) continue;
                    texel = multiply(texel, tint);
                    int r = Math.round((texel >> 16 & 255) * shade),
                            g = Math.round((texel >> 8 & 255) * shade),
                            b = Math.round((texel & 255) * shade);
                    color[index] = (texel & 0xFF000000) | r << 16 | g << 8 | b;
                    depth[index] = z;
                }
        }

        private static int multiply(int argb, int tint) {
            if (tint == 0xFFFFFFFF) return argb;
            int r = (argb >> 16 & 255) * (tint >> 16 & 255) / 255,
                    g = (argb >> 8 & 255) * (tint >> 8 & 255) / 255,
                    b = (argb & 255) * (tint & 255) / 255;
            return (argb & 0xFF000000) | r << 16 | g << 8 | b;
        }

        private static void blend(BufferedImage out, int x, int y, int src) {
            int sa = src >>> 24;
            if (sa == 0) return;
            int dst = out.getRGB(x, y), da = dst >>> 24;
            float a = sa / 255f, oa = a + da / 255f * (1 - a);
            if (oa <= 0) return;
            int r = Math.round(((src >> 16 & 255) * a + (dst >> 16 & 255) * da / 255f * (1 - a)) / oa);
            int g = Math.round(((src >> 8 & 255) * a + (dst >> 8 & 255) * da / 255f * (1 - a)) / oa);
            int b = Math.round(((src & 255) * a + (dst & 255) * da / 255f * (1 - a)) / oa);
            out.setRGB(x, y, Math.round(oa * 255) << 24 | r << 16 | g << 8 | b);
        }

        private static BufferedImage fit(BufferedImage image) {
            if (image == null) return null;
            int w = image.getWidth(), h = Math.min(image.getHeight(), w);
            BufferedImage out = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 16; y++)
                for (int x = 0; x < 16; x++) out.setRGB(x, y, image.getRGB(x * w / 16, y * h / 16));
            return out;
        }

        private BufferedImage texture(String id) throws IOException {
            Identifier key = Identifier.parse(id.contains(":") ? id : "minecraft:" + id);
            String path = "assets/" + key.getNamespace() + "/textures/" + key.getPath() + ".png";
            if (textures.containsKey(path)) return textures.get(path);
            byte[] bytes = builder.getDataOrSource(path);
            BufferedImage image = null;
            if (bytes != null) {
                BufferedImage read = PngCodec.decode(bytes);
                if (read != null) {
                    image = new BufferedImage(read.getWidth(), read.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    image.getGraphics().drawImage(read, 0, 0, null);
                }
            }
            textures.put(path, image);
            return image;
        }

        private JsonObject json(String path) {
            byte[] bytes = builder.getDataOrSource(path);
            if (bytes == null) return null;
            try {
                return PackJson.object(bytes);
            } catch (RuntimeException error) {
                return null;
            }
        }

        private static Vector3f vector(JsonObject o, String key, Vector3f fallback) {
            if (o == null || !o.has(key)) return fallback;
            return vec(o.getAsJsonArray(key));
        }

        private static Vector3f vec(JsonArray a) {
            return new Vector3f(
                    a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
        }
    }
}
