package com.zurrtum.create.infrastructure.polymer.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.veeda.createpoly.assets.PackJson;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class PolymerCopycatBaker {
    private PolymerCopycatBaker() {}

    static final List<String> FACES = List.of("down", "up", "north", "south", "west", "east");

    private static final Map<String, int[]> NORMALS = Map.of(
            "down", new int[] {0, -1, 0},
            "up", new int[] {0, 1, 0},
            "north", new int[] {0, 0, -1},
            "south", new int[] {0, 0, 1},
            "west", new int[] {-1, 0, 0},
            "east", new int[] {1, 0, 0});

    public static final List<String> SHAPES;

    static {
        List<String> shapes = new ArrayList<>();
        for (String face : FACES) shapes.add("panel_" + face);
        for (String face : List.of("north", "south", "west", "east"))
            for (String half : List.of("top", "bottom")) shapes.add("step_" + face + "_" + half);
        SHAPES = List.copyOf(shapes);
    }

    public static final class Unsupported extends RuntimeException {
        Unsupported(String reason) {
            super(reason, null, false, false);
        }
    }

    private static double[] nativeUv(String face, double[] p) {
        double x = p[0], y = p[1], z = p[2];
        return switch (face) {
            case "down" -> new double[] {x, 16 - z};
            case "up" -> new double[] {x, z};
            case "north" -> new double[] {16 - x, 16 - y};
            case "south" -> new double[] {x, 16 - y};
            case "west" -> new double[] {z, 16 - y};
            default -> new double[] {16 - z, 16 - y};
        };
    }

    private static double[] rotate(double[] p, int x, int y, boolean center) {
        double a = center ? p[0] - 8 : p[0], b = center ? p[1] - 8 : p[1], c = center ? p[2] - 8 : p[2];
        for (int i = 0; i < Math.floorMod(x, 360) / 90; i++) {
            double t = b;
            b = c;
            c = -t;
        }
        for (int i = 0; i < Math.floorMod(y, 360) / 90; i++) {
            double t = a;
            a = -c;
            c = t;
        }
        return center ? new double[] {a + 8, b + 8, c + 8} : new double[] {a, b, c};
    }

    private static int axis(String face) {
        int[] n = NORMALS.get(face);
        return n[0] != 0 ? 0 : n[1] != 0 ? 1 : 2;
    }

    private static String faceOf(double[] normal) {
        for (String face : FACES) {
            int[] n = NORMALS.get(face);
            if (n[0] == Math.round(normal[0]) && n[1] == Math.round(normal[1]) && n[2] == Math.round(normal[2]))
                return face;
        }
        throw new IllegalStateException("No face for normal");
    }

    private static List<double[]> corners(String face, double[] lo, double[] hi) {
        int axis = axis(face);
        double side = NORMALS.get(face)[axis] > 0 ? hi[axis] : lo[axis];
        int a1 = (axis + 1) % 3, a2 = (axis + 2) % 3;
        List<double[]> result = new ArrayList<>();
        for (double a : new double[] {lo[a1], hi[a1]})
            for (double b : new double[] {lo[a2], hi[a2]}) {
                double[] p = lo.clone();
                p[axis] = side;
                p[a1] = a;
                p[a2] = b;
                result.add(p);
            }
        result.sort(Comparator.<double[]>comparingDouble(p -> nativeUv(face, p)[0])
                .thenComparingDouble(p -> nativeUv(face, p)[1]));
        return result;
    }

    private static double[] bounds(String face, List<double[]> points) {
        double ulo = Double.MAX_VALUE, uhi = -Double.MAX_VALUE, vlo = Double.MAX_VALUE, vhi = -Double.MAX_VALUE;
        for (double[] p : points) {
            double[] uv = nativeUv(face, p);
            ulo = Math.min(ulo, uv[0]);
            uhi = Math.max(uhi, uv[0]);
            vlo = Math.min(vlo, uv[1]);
            vhi = Math.max(vhi, uv[1]);
        }
        return new double[] {ulo, vlo, uhi, vhi};
    }

    private static double[] sourceUv(String face, JsonObject data, double[] point, double[] low, double[] high) {
        double[] uv = nativeUv(face, point);
        double[] b = bounds(face, corners(face, low, high));
        double u = (uv[0] - b[0]) * 16 / (b[2] - b[0]), v = (uv[1] - b[1]) * 16 / (b[3] - b[1]);
        int turns = data.has("rotation") ? data.get("rotation").getAsInt() / 90 : 0;
        for (int i = 0; i < turns; i++) {
            double t = u;
            u = v;
            v = 16 - t;
        }
        double u0 = b[0], v0 = b[1], u1 = b[2], v1 = b[3];
        if (data.has("uv")) {
            JsonArray rect = data.getAsJsonArray("uv");
            u0 = rect.get(0).getAsDouble();
            v0 = rect.get(1).getAsDouble();
            u1 = rect.get(2).getAsDouble();
            v1 = rect.get(3).getAsDouble();
        }
        return new double[] {u0 + (u1 - u0) * u / 16, v0 + (v1 - v0) * v / 16};
    }

    private static final double[] ZERO = {0, 0, 0}, FULL = {16, 16, 16};

    private static JsonObject croppedFace(
            String face, String sourceFace, JsonObject data, double[] lo, double[] hi, int x, int y) {
        return croppedFace(face, sourceFace, data, lo, hi, x, y, ZERO, FULL);
    }

    private static JsonObject croppedFace(
            String face,
            String sourceFace,
            JsonObject data,
            double[] lo,
            double[] hi,
            int x,
            int y,
            double[] sourceLow,
            double[] sourceHigh) {
        List<double[]> points = corners(face, lo, hi);
        List<double[]> wanted = new ArrayList<>();
        for (double[] p : points) {
            double[] unrotated = rotate(rotate(p, 0, -y, true), -x, 0, true);
            wanted.add(sourceUv(sourceFace, data, unrotated, sourceLow, sourceHigh));
        }
        double minU = Double.MAX_VALUE, maxU = -Double.MAX_VALUE, minV = Double.MAX_VALUE, maxV = -Double.MAX_VALUE;
        for (double[] w : wanted) {
            minU = Math.min(minU, w[0]);
            maxU = Math.max(maxU, w[0]);
            minV = Math.min(minV, w[1]);
            maxV = Math.max(maxV, w[1]);
        }
        double[] defaults = bounds(face, points);
        for (int rotation : new int[] {0, 90, 180, 270})
            for (boolean flipU : new boolean[] {false, true})
                for (boolean flipV : new boolean[] {false, true}) {
                    double[] uv = {minU, minV, maxU, maxV};
                    if (flipU) {
                        double t = uv[0];
                        uv[0] = uv[2];
                        uv[2] = t;
                    }
                    if (flipV) {
                        double t = uv[1];
                        uv[1] = uv[3];
                        uv[3] = t;
                    }
                    boolean match = true;
                    for (int i = 0; i < points.size() && match; i++) {
                        double[] d = nativeUv(face, points.get(i));
                        double u = (d[0] - defaults[0]) / (defaults[2] - defaults[0]);
                        double v = (d[1] - defaults[1]) / (defaults[3] - defaults[1]);
                        for (int r = 0; r < rotation / 90; r++) {
                            double t = u;
                            u = v;
                            v = 1 - t;
                        }
                        double au = uv[0] + (uv[2] - uv[0]) * u, av = uv[1] + (uv[3] - uv[1]) * v;
                        match = Math.abs(au - wanted.get(i)[0]) < 1e-6 && Math.abs(av - wanted.get(i)[1]) < 1e-6;
                    }
                    if (!match) continue;
                    JsonObject out = new JsonObject();
                    out.add("texture", data.get("texture").deepCopy());
                    JsonArray rect = new JsonArray();
                    for (double value : uv) rect.add(new JsonPrimitive(value));
                    out.add("uv", rect);
                    if (rotation != 0) out.addProperty("rotation", rotation);
                    if (data.has("tintindex"))
                        out.add("tintindex", data.get("tintindex").deepCopy());
                    return out;
                }
        throw new Unsupported("Unable to encode native face UVs");
    }

    private record Cut(int[] lo, int[] hi, int[] offset, Set<String> skip) {}

    private static List<Cut> cuts(String shape) {
        String[] parts = shape.split("_");
        String kind = parts[0], face = parts[1];
        int[] normal = NORMALS.get(face);
        int axis = axis(face), sign = normal[axis];
        String opposite = faceOf(new double[] {-normal[0], -normal[1], -normal[2]});
        BiFunction<Integer, Integer, int[][]> region = (start, end) -> {
            int[] lo = {0, 0, 0}, hi = {16, 16, 16};
            lo[axis] = sign > 0 ? start : 16 - end;
            hi[axis] = sign > 0 ? end : 16 - start;
            return new int[][] {lo, hi};
        };
        List<Cut> out = new ArrayList<>();
        if (kind.equals("panel")) {
            int[][] a = region.apply(0, 1);
            out.add(new Cut(a[0], a[1], new int[] {0, 0, 0}, Set.of(face)));
            int[][] b = region.apply(14, 16);
            out.add(new Cut(
                    b[0], b[1], new int[] {-13 * normal[0], -13 * normal[1], -13 * normal[2]}, Set.of(opposite)));
            return out;
        }
        boolean top = parts[2].equals("top");
        for (boolean front : new boolean[] {true, false}) {
            int[][] r = front ? region.apply(0, 4) : region.apply(12, 16);
            for (boolean upper : new boolean[] {false, true}) {
                int[] low = r[0].clone(), high = r[1].clone();
                low[1] = upper ? 12 : 0;
                high[1] = upper ? 16 : 4;
                int[] offset = {front ? 8 * normal[0] : 0, front ? 8 * normal[1] : 0, front ? 8 * normal[2] : 0};
                offset[1] = top ? (upper ? 0 : 8) : (upper ? -8 : 0);
                out.add(new Cut(low, high, offset, Set.of(front ? face : opposite, upper ? "down" : "up")));
            }
        }
        return out;
    }

    private static double[] toDouble(int[] v) {
        return new double[] {v[0], v[1], v[2]};
    }

    public static Map<String, JsonObject> meshes(JsonObject cube, int x, int y) {
        Map<String, JsonObject> out = new LinkedHashMap<>();
        for (String shape : SHAPES) out.put(shape, mesh(cube, x, y, shape));
        return out;
    }

    static JsonObject mesh(JsonObject cube, int x, int y, String shape) {
        List<JsonObject> layers = new ArrayList<>();
        if (cube.has("layers"))
            for (JsonElement layer : cube.getAsJsonArray("layers")) layers.add(layer.getAsJsonObject());
        else layers.add(cube.getAsJsonObject("faces"));
        JsonArray elements = new JsonArray();
        for (JsonObject layer : layers) {
            Map<String, Map.Entry<String, JsonObject>> rotated = new LinkedHashMap<>();
            for (var entry : layer.entrySet()) {
                int[] n = NORMALS.get(entry.getKey());
                String world = faceOf(rotate(toDouble(n), x, y, false));
                rotated.put(world, Map.entry(entry.getKey(), entry.getValue().getAsJsonObject()));
            }
            for (Cut cut : cuts(shape)) {
                JsonObject faces = new JsonObject();
                for (var face : rotated.entrySet()) {
                    if (cut.skip().contains(face.getKey())) continue;
                    faces.add(
                            face.getKey(),
                            croppedFace(
                                    face.getKey(),
                                    face.getValue().getKey(),
                                    face.getValue().getValue(),
                                    toDouble(cut.lo()),
                                    toDouble(cut.hi()),
                                    x,
                                    y));
                }
                if (faces.isEmpty()) continue;
                JsonObject element = new JsonObject();
                JsonArray from = new JsonArray(), to = new JsonArray();
                for (int i = 0; i < 3; i++) {
                    from.add(cut.lo()[i] + cut.offset()[i]);
                    to.add(cut.hi()[i] + cut.offset()[i]);
                }
                element.add("from", from);
                element.add("to", to);
                element.add("faces", faces);
                elements.add(element);
            }
        }
        JsonObject out = new JsonObject();
        out.add("textures", cube.get("textures").deepCopy());
        out.add("elements", elements);
        return out;
    }

    public static JsonObject resolve(
            String identifier, Function<String, JsonObject> read, Map<String, JsonObject> cache) {
        JsonObject cached = cache.get(identifier);
        if (cached != null) return cached;
        int colon = identifier.indexOf(':');
        String ns = colon < 0 ? "minecraft" : identifier.substring(0, colon);
        String path = colon < 0 ? identifier : identifier.substring(colon + 1);
        JsonObject data = read.apply("assets/" + ns + "/models/" + path + ".json");
        if (data == null) throw new Unsupported("Missing model " + identifier);
        if (data.has("loader")) throw new Unsupported("Client model loader");
        JsonObject parent =
                data.has("parent") ? resolve(data.get("parent").getAsString(), read, cache) : new JsonObject();
        JsonObject merged = parent.deepCopy();
        for (var entry : data.entrySet())
            merged.add(entry.getKey(), entry.getValue().deepCopy());
        JsonObject textures =
                parent.has("textures") ? parent.getAsJsonObject("textures").deepCopy() : new JsonObject();
        if (data.has("textures"))
            for (var entry : data.getAsJsonObject("textures").entrySet())
                textures.add(entry.getKey(), entry.getValue().deepCopy());
        merged.add("textures", textures);
        cache.put(identifier, merged);
        return merged;
    }

    static final Set<String> BIOME_TINTED = Set.of(
            "minecraft:grass_block",
            "minecraft:oak_leaves",
            "minecraft:jungle_leaves",
            "minecraft:acacia_leaves",
            "minecraft:dark_oak_leaves",
            "minecraft:mangrove_leaves");

    public static JsonObject cubeOf(
            String model, String block, Function<String, JsonObject> read, Map<String, JsonObject> cache) {
        JsonObject data = resolve(model, read, cache);
        JsonArray elements = data.has("elements") ? data.getAsJsonArray("elements") : new JsonArray();
        if (elements.isEmpty()) throw new Unsupported("No cube layers");
        for (JsonElement raw : elements) {
            JsonObject element = raw.getAsJsonObject();
            if (!isVector(element.get("from"), 0) || !isVector(element.get("to"), 16) || element.has("rotation"))
                throw new Unsupported("Not a full cube");
        }
        JsonObject firstFaces = elements.get(0).getAsJsonObject().getAsJsonObject("faces");
        if (firstFaces == null || !firstFaces.keySet().equals(Set.copyOf(FACES)))
            throw new Unsupported("Incomplete base cube");
        JsonObject textures = data.getAsJsonObject("textures");
        Function<JsonElement, JsonElement> texture = value -> {
            Set<String> seen = new HashSet<>();
            while (value != null
                    && value.isJsonPrimitive()
                    && value.getAsString().startsWith("#")) {
                String name = value.getAsString();
                if (!seen.add(name)) throw new Unsupported("Texture cycle");
                value = textures.get(name.substring(1));
            }
            if (value == null) throw new Unsupported("Unbound texture");
            return value.deepCopy();
        };
        JsonObject bindings = new JsonObject();
        JsonElement particle = textures.has("particle")
                ? textures.get("particle")
                : firstFaces
                        .entrySet()
                        .iterator()
                        .next()
                        .getValue()
                        .getAsJsonObject()
                        .get("texture");
        bindings.add("particle", texture.apply(particle));
        boolean tinted = false;
        for (JsonElement e : elements)
            for (var f : e.getAsJsonObject().getAsJsonObject("faces").entrySet())
                tinted |= f.getValue().getAsJsonObject().has("tintindex");
        JsonArray tints = null;
        if (tinted) {
            int colon = block.indexOf(':');
            JsonObject item = read.apply(
                    "assets/" + block.substring(0, colon) + "/items/" + block.substring(colon + 1) + ".json");
            if (item == null
                    || !item.has("model")
                    || !item.getAsJsonObject("model").has("tints")) throw new Unsupported("Unknown tint");
            tints = item.getAsJsonObject("model").getAsJsonArray("tints").deepCopy();
            for (JsonElement e : elements)
                for (var f : e.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                    JsonObject face = f.getValue().getAsJsonObject();
                    int index = face.has("tintindex") ? face.get("tintindex").getAsInt() : 0;
                    if (tints.isEmpty() || index >= tints.size()) throw new Unsupported("Unknown tint");
                }
            if (BIOME_TINTED.contains(block)) {
                JsonObject first = tints.get(0).getAsJsonObject();
                JsonObject dynamic = new JsonObject();
                dynamic.addProperty("type", "minecraft:custom_model_data");
                dynamic.addProperty("index", 0);
                dynamic.add("default", first.has("value") ? first.get("value").deepCopy() : new JsonPrimitive(-1));
                tints = new JsonArray();
                tints.add(dynamic);
            }
        }
        JsonArray layers = new JsonArray();
        for (int index = 0; index < elements.size(); index++) {
            JsonObject faces = elements.get(index)
                    .getAsJsonObject()
                    .getAsJsonObject("faces")
                    .deepCopy();
            for (var face : faces.entrySet()) {
                JsonObject f = face.getValue().getAsJsonObject();
                String slot = elements.size() == 1 ? face.getKey() : "l" + index + "_" + face.getKey();
                bindings.add(slot, texture.apply(f.get("texture")));
                f.addProperty("texture", "#" + slot);
            }
            layers.add(faces);
        }
        JsonObject cube = new JsonObject();
        cube.add("textures", bindings);
        if (layers.size() == 1) cube.add("faces", layers.get(0));
        else cube.add("layers", layers);
        if (tints != null) cube.add("tints", tints);
        return cube;
    }

    public static JsonObject directMesh(JsonObject data, int x, int y, boolean uvlock) {
        if (uvlock && (x != 0 || y != 0)) throw new Unsupported("Rotated uvlock");
        JsonObject out = new JsonObject();
        out.add("textures", data.getAsJsonObject("textures").deepCopy());
        if (data.has("ambientocclusion"))
            out.add("ambientocclusion", data.get("ambientocclusion").deepCopy());
        JsonArray baked = new JsonArray();
        for (JsonElement raw : data.getAsJsonArray("elements")) {
            JsonObject element = raw.getAsJsonObject();
            if (element.has("rotation")
                    && element.getAsJsonObject("rotation").has("angle")
                    && element.getAsJsonObject("rotation").get("angle").getAsDouble() != 0)
                throw new Unsupported("Rotated element");
            double[] low = vector(element.get("from")), high = vector(element.get("to"));
            double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
            double[] hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            for (double a : new double[] {low[0], high[0]})
                for (double b : new double[] {low[1], high[1]})
                    for (double c : new double[] {low[2], high[2]}) {
                        double[] p = rotate(new double[] {a, b, c}, x, y, true);
                        for (int i = 0; i < 3; i++) {
                            lo[i] = Math.min(lo[i], p[i]);
                            hi[i] = Math.max(hi[i], p[i]);
                        }
                    }
            JsonObject result = new JsonObject();
            result.add("from", numbers(lo));
            result.add("to", numbers(hi));
            JsonObject faces = new JsonObject();
            result.add("faces", faces);
            for (String field : List.of("shade", "light_emission"))
                if (element.has(field)) result.add(field, element.get(field).deepCopy());
            for (var face : element.getAsJsonObject("faces").entrySet()) {
                JsonObject original = face.getValue().getAsJsonObject();
                if (original.has("tintindex")) throw new Unsupported("Tinted trapdoor");
                String target = faceOf(rotate(toDouble(NORMALS.get(face.getKey())), x, y, false));
                faces.add(target, croppedFace(target, face.getKey(), original, lo, hi, x, y, low, high));
            }
            baked.add(result);
        }
        out.add("elements", baked);
        return out;
    }

    private static double[] vector(JsonElement value) {
        JsonArray array = value.getAsJsonArray();
        return new double[] {
            array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()
        };
    }

    private static JsonArray numbers(double[] values) {
        JsonArray out = new JsonArray();
        for (double v : values)
            if (v == Math.rint(v) && Math.abs(v) < 1e9) out.add((long) v);
            else out.add(v);
        return out;
    }

    private static boolean isVector(JsonElement value, double expected) {
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() != 3) return false;
        for (JsonElement n : value.getAsJsonArray()) if (n.getAsDouble() != expected) return false;
        return true;
    }

    public static final class Catalogue {
        public final Map<String, Map<String, String>> blocks = new LinkedHashMap<>(), panels = new LinkedHashMap<>();
        public final Map<String, Map<String, JsonObject>> meshes = new LinkedHashMap<>();
        public final Map<String, JsonArray> tints = new HashMap<>();
        private final Map<String, JsonObject> cache = new HashMap<>();
        private final Function<String, JsonObject> read;

        public Catalogue(Function<String, JsonObject> read) {
            this.read = read;
        }

        public void addCubeMaterial(String block, JsonObject blockstate) {
            if (blockstate == null || !blockstate.has("variants")) return;
            Map<String, String> supported = new LinkedHashMap<>();
            for (var entry : blockstate.getAsJsonObject("variants").entrySet()) {
                try {
                    JsonObject variant = PackJson.firstVariant(entry.getValue());
                    int x = variant.has("x") ? variant.get("x").getAsInt() : 0;
                    int y = variant.has("y") ? variant.get("y").getAsInt() : 0;
                    if (variant.has("uvlock") && variant.get("uvlock").getAsBoolean() && (x != 0 || y != 0))
                        throw new Unsupported("Rotated uvlock");
                    JsonObject cube = cubeOf(variant.get("model").getAsString(), block, read, cache);
                    String key = "m" + hash(cube + "|" + x + "|" + y);
                    if (!meshes.containsKey(key)) {
                        meshes.put(key, PolymerCopycatBaker.meshes(cube, x, y));
                        if (cube.has("tints")) tints.put(key, cube.getAsJsonArray("tints"));
                    }
                    supported.put(entry.getKey(), key);
                } catch (Unsupported | IllegalStateException | ClassCastException | NullPointerException skipped) {
                }
            }
            if (!supported.isEmpty()) blocks.put(block, supported);
        }

        public void addTrapdoorPanel(String block, JsonObject blockstate) {
            if (blockstate == null || !blockstate.has("variants")) return;
            Map<String, String> supported = new LinkedHashMap<>();
            for (var entry : blockstate.getAsJsonObject("variants").entrySet()) {
                try {
                    JsonObject variant = PackJson.firstVariant(entry.getValue());
                    int x = variant.has("x") ? variant.get("x").getAsInt() : 0;
                    int y = variant.has("y") ? variant.get("y").getAsInt() : 0;
                    boolean uvlock =
                            variant.has("uvlock") && variant.get("uvlock").getAsBoolean();
                    JsonObject direct =
                            directMesh(resolve(variant.get("model").getAsString(), read, cache), x, y, uvlock);
                    String key = "t" + hash(direct.toString());
                    if (!meshes.containsKey(key)) {
                        Map<String, JsonObject> shapes = new LinkedHashMap<>();
                        for (String face : FACES) shapes.put("panel_" + face, direct);
                        meshes.put(key, shapes);
                    }
                    supported.put(entry.getKey(), key);
                } catch (Unsupported | IllegalStateException | ClassCastException | NullPointerException skipped) {
                }
            }
            if (!supported.isEmpty()) panels.put(block, supported);
        }

        public Map<String, String> canonical() {
            Map<String, String> result = new HashMap<>(), first = new HashMap<>();
            for (var material : meshes.entrySet()) {
                JsonArray tint = tints.get(material.getKey());
                for (var shape : material.getValue().entrySet()) {
                    String file = material.getKey() + "/" + shape.getKey();
                    String signature = (tint == null ? "" : tint.toString()) + "\n" + shape.getValue();
                    String owner = first.putIfAbsent(signature, file);
                    if (owner != null) result.put(file, owner);
                }
            }
            return result;
        }
    }

    static String hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
