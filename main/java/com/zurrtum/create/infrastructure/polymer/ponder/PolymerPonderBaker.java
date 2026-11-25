package com.zurrtum.create.infrastructure.polymer.ponder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.veeda.createpoly.assets.PackJson;
import dev.veeda.createpoly.assets.PngCodec;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class PolymerPonderBaker {
    private record Resolved(JsonArray elements) {}

    private final Function<String, byte[]> resources;
    private final Map<String, JsonObject> generated;
    private final Map<String, Resolved> cache = new HashMap<>();

    PolymerPonderBaker(Function<String, byte[]> resources, Map<String, JsonObject> generated) {
        this.resources = resources;
        this.generated = generated;
    }

    record Entry(String model, Matrix4f transform) {}

    static Matrix4f transform(JsonObject entry) {
        float[] values = new float[16];
        JsonArray matrix = entry.getAsJsonArray("transformation");
        for (int i = 0; i < 16; i++) values[i] = matrix.get(i).getAsFloat();
        return new Matrix4f().set(values).transpose();
    }

    List<Entry> entries(JsonArray json) {
        List<Entry> result = new ArrayList<>();
        for (JsonElement element : json) {
            JsonObject entry = element.getAsJsonObject();
            if (!"minecraft:model".equals(entry.get("type").getAsString()) || entry.has("tints")) return null;
            String model = entry.get("model").getAsString();
            if (model.startsWith("create:block/p/") && !model.startsWith(PolymerPonderScene.stillModel("")))
                return null;
            if (model.contains("/polymer/belt_anim/") || model.contains("/polymer/belt_frame/")) return null;
            if (resolve(model) == null) return null;
            result.add(new Entry(model, transform(entry)));
        }
        return result;
    }

    JsonObject[] bake(String id, List<Entry> entries) {
        return bake(id, entries, null);
    }

    JsonObject[] bake(String id, List<Entry> entries, List<UnaryOperator<String>> remap) {
        if (entries.isEmpty()) return null;
        float[] lo = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
        float[] hi = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (Entry entry : entries)
            for (JsonElement element : resolve(entry.model).elements) {
                JsonObject e = element.getAsJsonObject();
                Vector3f from = PackJson.vector3f(e.getAsJsonArray("from")),
                        to = PackJson.vector3f(e.getAsJsonArray("to"));
                Matrix4f local = local(e);
                for (int c = 0; c < 8; c++) {
                    Vector3f p = new Vector3f(
                            (c & 1) == 0 ? from.x : to.x, (c & 2) == 0 ? from.y : to.y, (c & 4) == 0 ? from.z : to.z);
                    local.transformPosition(p).div(16);
                    entry.transform.transformPosition(p);
                    lo[0] = Math.min(lo[0], p.x);
                    lo[1] = Math.min(lo[1], p.y);
                    lo[2] = Math.min(lo[2], p.z);
                    hi[0] = Math.max(hi[0], p.x);
                    hi[1] = Math.max(hi[1], p.y);
                    hi[2] = Math.max(hi[2], p.z);
                }
            }
        float extent = Math.max(Math.max(hi[0] - lo[0], hi[1] - lo[1]), Math.max(hi[2] - lo[2], 1e-3f));
        for (float units : new float[] {2.9f, 2.5f, 2f, 1.5f, 1f}) {
            float size = extent / units;
            Vector3f origin = new Vector3f(lo[0] + hi[0], lo[1] + hi[1], lo[2] + hi[2])
                    .mul(.5f)
                    .sub(size / 2, size / 2, size / 2);
            Matrix4f toModel = new Matrix4f().scale(16 / size).translate(-origin.x, -origin.y, -origin.z);
            Map<String, String> textures = new LinkedHashMap<>();
            JsonArray elements = new JsonArray();
            for (int i = 0; i < entries.size(); i++) {
                Entry entry = entries.get(i);
                UnaryOperator<String> sprites = remap == null ? x -> x : remap.get(i);
                Matrix4f placement = new Matrix4f(toModel).mul(entry.transform).scale(1 / 16f);
                for (JsonElement element : resolve(entry.model).elements) {
                    JsonObject source = element.getAsJsonObject();
                    JsonObject baked =
                            bakeElement(source, new Matrix4f(placement).mul(local(source)), size, textures, sprites);
                    check(source, baked, entry.transform, origin, size);
                    elements.add(baked);
                }
            }
            if (!inRange(elements)) continue;
            return finish(id, elements, textures, origin, size);
        }
        return null;
    }

    private static void check(JsonObject source, JsonObject baked, Matrix4f transform, Vector3f origin, float size) {
        Vector3f sf = PackJson.vector3f(source.getAsJsonArray("from")),
                st = PackJson.vector3f(source.getAsJsonArray("to"));
        Vector3f bf = PackJson.vector3f(baked.getAsJsonArray("from")),
                bt = PackJson.vector3f(baked.getAsJsonArray("to"));
        Matrix4f sourceLocal = local(source), bakedLocal = local(baked);
        Matrix4f entry = new Matrix4f().translate(origin).scale(size);
        for (int c = 0; c < 8; c++) {
            Vector3f a =
                    new Vector3f((c & 1) == 0 ? sf.x : st.x, (c & 2) == 0 ? sf.y : st.y, (c & 4) == 0 ? sf.z : st.z);
            transform.transformPosition(sourceLocal.transformPosition(a).div(16));
            Vector3f b =
                    new Vector3f((c & 1) == 0 ? bf.x : bt.x, (c & 2) == 0 ? bf.y : bt.y, (c & 4) == 0 ? bf.z : bt.z);
            entry.transformPosition(bakedLocal.transformPosition(b).div(16));
            if (a.distance(b) > 1e-3f)
                throw new IllegalStateException(
                        "Baked element moved " + a.distance(b) + " blocks: " + source + " -> " + baked);
        }
    }

    private static boolean inRange(JsonArray elements) {
        for (JsonElement element : elements)
            for (String corner : new String[] {"from", "to"})
                for (JsonElement v : element.getAsJsonObject().getAsJsonArray(corner))
                    if (v.getAsFloat() < -16 || v.getAsFloat() > 32) return false;
        return true;
    }

    private JsonObject[] finish(
            String id, JsonArray elements, Map<String, String> textures, Vector3f origin, float size) {
        elements = cullTouching(elements, textures);
        JsonObject textureJson = new JsonObject();
        for (var texture : textures.entrySet()) textureJson.addProperty(texture.getValue(), texture.getKey());
        textureJson.addProperty("particle", textures.keySet().iterator().next());
        JsonObject model = new JsonObject();
        model.add("textures", textureJson);
        model.add("elements", elements);
        model.add(
                "display",
                JsonParser.parseString("{\"gui\":{\"rotation\":[0,0,0],\"translation\":[0,0,0],\"scale\":[1,1,1]}}"));
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:model");
        entry.addProperty("model", id);
        entry.add(
                "transformation",
                PolymerPonderScene.matrix(new Matrix4f().translate(origin).scale(size)));
        return new JsonObject[] {model, entry};
    }

    private static final String[] FACES = {"down", "up", "north", "south", "west", "east"};

    private JsonArray cullTouching(JsonArray elements, Map<String, String> textures) {
        Map<String, String> sprites = new HashMap<>();
        for (var texture : textures.entrySet()) sprites.put("#" + texture.getValue(), texture.getKey());
        Map<String, List<int[]>> planes = new HashMap<>();
        for (int i = 0; i < elements.size(); i++) {
            JsonObject element = elements.get(i).getAsJsonObject();
            if (!opaqueBox(element, sprites)) continue;
            for (int f = 0; f < 6; f++)
                planes.computeIfAbsent(plane(element, f), k -> new ArrayList<>())
                        .add(new int[] {i, f});
        }
        Map<Integer, Set<String>> hidden = new HashMap<>();
        for (var entry : planes.entrySet()) {
            String key = entry.getKey();
            if (!key.endsWith("+")) continue;
            List<int[]> other = planes.get(key.substring(0, key.length() - 1) + "-");
            if (other == null) continue;
            for (int[] a : entry.getValue())
                for (int[] b : other) {
                    if (a[0] == b[0]) continue;
                    hidden.computeIfAbsent(a[0], k -> new HashSet<>()).add(FACES[a[1]]);
                    hidden.computeIfAbsent(b[0], k -> new HashSet<>()).add(FACES[b[1]]);
                }
        }
        if (hidden.isEmpty()) return elements;
        JsonArray result = new JsonArray();
        for (int i = 0; i < elements.size(); i++) {
            JsonObject element = elements.get(i).getAsJsonObject();
            var drop = hidden.get(i);
            if (drop != null) {
                element = element.deepCopy();
                for (String face : drop) element.getAsJsonObject("faces").remove(face);
                if (element.getAsJsonObject("faces").isEmpty()) continue;
            }
            result.add(element);
        }
        return result;
    }

    private static String plane(JsonObject element, int face) {
        Vector3f from = PackJson.vector3f(element.getAsJsonArray("from")),
                to = PackJson.vector3f(element.getAsJsonArray("to"));
        int axis = face / 2 == 0 ? 1 : face / 2 == 1 ? 2 : 0;
        boolean positive = face % 2 == 1;
        float[] f = {from.x, from.y, from.z}, t = {to.x, to.y, to.z};
        StringBuilder key =
                new StringBuilder().append(axis).append(':').append(Math.round((positive ? t[axis] : f[axis]) * 1000));
        for (int a = 0; a < 3; a++)
            if (a != axis)
                key.append(':').append(Math.round(f[a] * 1000)).append(':').append(Math.round(t[a] * 1000));
        return key.append(positive ? '+' : '-').toString();
    }

    private boolean opaqueBox(JsonObject element, Map<String, String> sprites) {
        if (element.has("rotation")) return false;
        JsonObject faces = element.getAsJsonObject("faces");
        if (faces.size() != 6) return false;
        Vector3f from = PackJson.vector3f(element.getAsJsonArray("from")),
                to = PackJson.vector3f(element.getAsJsonArray("to"));
        if (to.x - from.x <= 0 || to.y - from.y <= 0 || to.z - from.z <= 0) return false;
        for (String name : FACES) {
            JsonObject face = faces.getAsJsonObject(name);
            if (face == null) return false;
            String sprite = sprites.get(face.get("texture").getAsString());
            if (sprite == null || !opaque(sprite, face.getAsJsonArray("uv"))) return false;
        }
        return true;
    }

    private final Map<String, BufferedImage> spriteImages = new HashMap<>();
    private final Map<String, Boolean> opaqueRegions = new HashMap<>();

    private boolean opaque(String sprite, JsonArray uv) {
        String key = sprite + uv;
        Boolean known = opaqueRegions.get(key);
        if (known != null) return known;
        boolean result = false;
        String ns = sprite.substring(0, sprite.indexOf(':')), path = sprite.substring(sprite.indexOf(':') + 1);
        String file = "assets/" + ns + "/textures/" + path + ".png";
        if (resources.apply(file + ".mcmeta") == null) {
            BufferedImage image = spriteImages.computeIfAbsent(sprite, s -> {
                byte[] data = resources.apply(file);
                try {
                    return data == null ? null : PngCodec.decode(data);
                } catch (IOException exception) {
                    return null;
                }
            });
            if (image != null && image.getWidth() == image.getHeight()) {
                float scale = image.getWidth() / 16f;
                float u0 = Math.min(uv.get(0).getAsFloat(), uv.get(2).getAsFloat()),
                        u1 = Math.max(uv.get(0).getAsFloat(), uv.get(2).getAsFloat());
                float v0 = Math.min(uv.get(1).getAsFloat(), uv.get(3).getAsFloat()),
                        v1 = Math.max(uv.get(1).getAsFloat(), uv.get(3).getAsFloat());
                int x0 = (int) Math.floor(u0 * scale),
                        x1 = (int) Math.ceil(u1 * scale),
                        y0 = (int) Math.floor(v0 * scale),
                        y1 = (int) Math.ceil(v1 * scale);
                result = x1 > x0 && y1 > y0 && x0 >= 0 && y0 >= 0 && x1 <= image.getWidth() && y1 <= image.getHeight();
                for (int y = y0; result && y < y1; y++)
                    for (int x = x0; result && x < x1; x++) result = (image.getRGB(x, y) >>> 24) == 255;
            }
        }
        opaqueRegions.put(key, result);
        return result;
    }

    private static JsonObject bakeElement(
            JsonObject source, Matrix4f m, float size, Map<String, String> textures, UnaryOperator<String> sprites) {
        Vector3f from = PackJson.vector3f(source.getAsJsonArray("from")),
                to = PackJson.vector3f(source.getAsJsonArray("to"));
        Vector3f centre = new Vector3f(from).add(to).mul(.5f);
        Vector3f target = m.transformPosition(new Vector3f(centre));
        float s = 1 / size;
        Matrix3f rotation = new Matrix3f();
        m.get3x3(rotation);
        rotation.scale(size);
        Vector3f half = new Vector3f(to).sub(from).mul(.5f * s);
        JsonObject element = new JsonObject();
        element.add("from", array(new Vector3f(target).sub(half)));
        element.add("to", array(new Vector3f(target).add(half)));
        Vector3f euler = eulerZYX(rotation);
        Number rx = round((float) Math.toDegrees(euler.x)),
                ry = round((float) Math.toDegrees(euler.y)),
                rz = round((float) Math.toDegrees(euler.z));
        if (rx.floatValue() != 0 || ry.floatValue() != 0 || rz.floatValue() != 0) {
            JsonObject rot = new JsonObject();
            rot.add("origin", array(target));
            rot.addProperty("x", rx);
            rot.addProperty("y", ry);
            rot.addProperty("z", rz);
            element.add("rotation", rot);
        }
        for (String key : new String[] {"shade", "light_emission"})
            if (source.has(key)) element.add(key, source.get(key));
        JsonObject faces = new JsonObject();
        for (var face : source.getAsJsonObject("faces").entrySet()) {
            JsonObject value = face.getValue().getAsJsonObject().deepCopy();
            if (!value.has("uv")) value.add("uv", defaultUv(face.getKey(), from, to));
            String sprite = sprites.apply(value.get("texture").getAsString());
            int bar = sprite.indexOf('|');
            if (bar >= 0) {
                String[] parts = sprite.split("\\|");
                int column = Integer.parseInt(parts[1]), columns = Integer.parseInt(parts[2]);
                value.add(
                        "uv",
                        new PolymerPonderDialogAssets.Pose(parts[0], column, columns).pack(value.getAsJsonArray("uv")));
                sprite = parts[0];
            }
            value.addProperty("texture", "#" + textures.computeIfAbsent(sprite, k -> "t" + textures.size()));
            value.remove("cullface");
            faces.add(face.getKey(), value);
        }
        element.add("faces", faces);
        return element;
    }

    static Vector3f eulerZYX(Matrix3f r) {
        double cy = Math.hypot(r.m12, r.m22), y = Math.atan2(-r.m02, cy);
        if (cy > 1e-6)
            return new Vector3f((float) Math.atan2(r.m12, r.m22), (float) y, (float) Math.atan2(r.m01, r.m00));
        double sign = -r.m02 >= 0 ? 1 : -1;
        return new Vector3f((float) Math.atan2(r.m10 * sign, r.m20 * sign), (float) y, 0);
    }

    public static JsonArray defaultUv(String face, Vector3f f, Vector3f t) {
        float[] uv = switch (face) {
            case "down" -> new float[] {f.x, 16 - t.z, t.x, 16 - f.z};
            case "up" -> new float[] {f.x, f.z, t.x, t.z};
            case "north" -> new float[] {16 - t.x, 16 - t.y, 16 - f.x, 16 - f.y};
            case "south" -> new float[] {f.x, 16 - t.y, t.x, 16 - f.y};
            case "west" -> new float[] {f.z, 16 - t.y, t.z, 16 - f.y};
            default -> new float[] {16 - t.z, 16 - t.y, 16 - f.z, 16 - f.y};
        };
        JsonArray array = new JsonArray();
        for (float v : uv) array.add(round(v));
        return array;
    }

    private static Matrix4f local(JsonObject element) {
        if (!element.has("rotation")) return new Matrix4f();
        JsonObject rotation = element.getAsJsonObject("rotation");
        Vector3f origin = PackJson.vector3f(rotation.getAsJsonArray("origin"));
        Matrix4f r;
        if (rotation.has("axis")) {
            float angle = (float) Math.toRadians(rotation.get("angle").getAsFloat());
            r = switch (rotation.get("axis").getAsString()) {
                case "x" -> new Matrix4f().rotationX(angle);
                case "y" -> new Matrix4f().rotationY(angle);
                default -> new Matrix4f().rotationZ(angle);
            };
        } else {
            float x = rotation.has("x") ? rotation.get("x").getAsFloat() : 0,
                    y = rotation.has("y") ? rotation.get("y").getAsFloat() : 0,
                    z = rotation.has("z") ? rotation.get("z").getAsFloat() : 0;
            r = new Matrix4f()
                    .rotationZYX((float) Math.toRadians(z), (float) Math.toRadians(y), (float) Math.toRadians(x));
        }
        return new Matrix4f().translation(origin).mul(r).translate(-origin.x, -origin.y, -origin.z);
    }

    boolean bakeable(String model) {
        return resolve(model) != null;
    }

    boolean symmetric(String model, Matrix4f turn) {
        Resolved resolved = resolve(model);
        if (resolved == null) return false;
        List<String> before = new ArrayList<>(), after = new ArrayList<>();
        for (JsonElement element : resolved.elements) {
            JsonObject e = element.getAsJsonObject();
            Vector3f from = PackJson.vector3f(e.getAsJsonArray("from")), to = PackJson.vector3f(e.getAsJsonArray("to"));
            Matrix4f local = local(e);
            List<String> a = new ArrayList<>(), b = new ArrayList<>();
            for (int c = 0; c < 8; c++) {
                Vector3f p = local.transformPosition(new Vector3f(
                        (c & 1) == 0 ? from.x : to.x, (c & 2) == 0 ? from.y : to.y, (c & 4) == 0 ? from.z : to.z));
                a.add(key(p));
                b.add(key(turn.transformPosition(new Vector3f(p))));
            }
            Collections.sort(a);
            Collections.sort(b);
            before.add(String.join(";", a));
            after.add(String.join(";", b));
        }
        Collections.sort(before);
        Collections.sort(after);
        return before.equals(after);
    }

    private static String key(Vector3f p) {
        return Math.round(p.x * 20) + "," + Math.round(p.y * 20) + "," + Math.round(p.z * 20);
    }

    private Resolved resolve(String model) {
        if (cache.containsKey(model)) return cache.get(model);
        Resolved result = resolveUncached(model);
        cache.put(model, result);
        return result;
    }

    private Resolved resolveUncached(String model) {
        Map<String, String> textures = new HashMap<>();
        JsonArray elements = null;
        String id = model;
        for (int depth = 0; depth < 16 && id != null; depth++) {
            if (!id.contains(":")) id = "minecraft:" + id;
            if (id.startsWith("minecraft:builtin/")) return null;
            JsonObject json = load(id);
            if (json == null) return null;
            if (json.has("textures"))
                for (var texture : json.getAsJsonObject("textures").entrySet()) {
                    JsonElement value = texture.getValue();
                    if (value.isJsonObject()) {
                        JsonObject material = value.getAsJsonObject();
                        if (material.size() != 1 || !material.has("sprite")) return null;
                        value = material.get("sprite");
                    }
                    if (!value.isJsonPrimitive()) return null;
                    textures.putIfAbsent(texture.getKey(), value.getAsString());
                }
            if (elements == null && json.has("elements")) elements = json.getAsJsonArray("elements");
            id = json.has("parent") ? json.get("parent").getAsString() : null;
        }
        if (elements == null || elements.isEmpty()) return null;
        JsonArray resolved = new JsonArray();
        for (JsonElement element : elements) {
            JsonObject copy = element.getAsJsonObject().deepCopy();
            if (copy.has("rotation")
                    && copy.getAsJsonObject("rotation").has("rescale")
                    && copy.getAsJsonObject("rotation").get("rescale").getAsBoolean()) return null;
            for (var face : copy.getAsJsonObject("faces").entrySet()) {
                JsonObject value = face.getValue().getAsJsonObject();
                String sprite = value.get("texture").getAsString();
                for (int hop = 0; hop < 16 && sprite.startsWith("#"); hop++)
                    sprite = textures.getOrDefault(sprite.substring(1), "#");
                if (sprite.startsWith("#")) return null;
                if (!sprite.contains(":")) sprite = "minecraft:" + sprite;
                if (!sprite.substring(sprite.indexOf(':') + 1).startsWith("block/")) return null;
                value.addProperty("texture", sprite);
            }
            resolved.add(copy);
        }
        return new Resolved(resolved);
    }

    private JsonObject load(String id) {
        if (generated.containsKey(id)) return generated.get(id);
        String ns = id.substring(0, id.indexOf(':')), path = id.substring(id.indexOf(':') + 1);
        if (id.startsWith(PolymerPonderScene.stillModel(""))) {
            String rest = path.substring(path.indexOf("/m/") + 3);
            JsonObject child = new JsonObject();
            child.addProperty(
                    "parent", rest.substring(0, rest.indexOf('/')) + ":" + rest.substring(rest.indexOf('/') + 1));
            return child;
        }
        byte[] data = resources.apply("assets/" + ns + "/models/" + path + ".json");
        return data == null ? null : PackJson.object(data);
    }

    private static JsonArray array(Vector3f v) {
        JsonArray array = new JsonArray();
        array.add(round(v.x));
        array.add(round(v.y));
        array.add(round(v.z));
        return array;
    }

    private static Number round(float value) {
        float rounded = Math.round(value * 1e4f) / 1e4f;
        return rounded == (int) rounded ? (Number) (int) rounded : (Number) rounded;
    }
}
