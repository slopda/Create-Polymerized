package com.zurrtum.create.infrastructure.polymer.ponder;

import static com.zurrtum.create.infrastructure.polymer.ponder.PolymerPonderDialog.FINISH_FLASH_STEPS;
import static com.zurrtum.create.infrastructure.polymer.ponder.PolymerPonderDialog.IDENTITY_GUI;
import static com.zurrtum.create.infrastructure.polymer.ponder.PolymerPonderDialog.SCALE;
import static com.zurrtum.create.infrastructure.polymer.ponder.PolymerPonderDialog.SCENE_H;
import static com.zurrtum.create.infrastructure.polymer.ponder.PolymerPonderDialog.SCENE_W;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zurrtum.create.Create;
import com.zurrtum.create.infrastructure.polymer.block.PolymerScrollAnimations;
import dev.veeda.createpoly.assets.PackAssetId;
import dev.veeda.createpoly.assets.PackAssetWriter;
import dev.veeda.createpoly.assets.PackJson;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class PolymerPonderDialogAssets {
    private PolymerPonderDialogAssets() {}

    static void addAssets(ResourcePackBuilder builder) {
        try {
            var writer = new PackAssetWriter(builder);
            List<PolymerPonderScene> scenes = new ArrayList<>();
            for (PolymerPonderScene.Definition definition : PolymerPonderScenes.ALL)
                scenes.add(definition.compile(builder::getDataOrSource));
            Set<String> sprites = new HashSet<>(), banks = new LinkedHashSet<>(), stills = new LinkedHashSet<>();
            Map<String, JsonObject> generated = new HashMap<>();
            for (PolymerPonderScene scene : scenes)
                for (PolymerPonderScene.Part part : scene.parts) {
                    var spinner = part.spinner();
                    if (spinner != null
                            && part.dir() != 0
                            && banks.add(spinner.model() + "#" + part.ticksPerPose() + "#" + part.dir()))
                        generated.put(
                                PolymerPonderScene.spinModel(spinner, part.ticksPerPose(), part.dir()),
                                spinBank(builder, writer, sprites, spinner, part.ticksPerPose(), part.dir()));
                    if (spinner == null || spinner.housed())
                        for (PolymerPonderScene.ModelRef model : part.models()) stills.add(model.model());
                    if (spinner != null) stills.add(spinner.model());
                    if (spinner != null && spinner.block().equals(PolymerPonderScene.ROPE_PULLEY))
                        stills.addAll(PolymerPonderScene.ROPE_PULLEY_PARTS);
                    if (spinner != null && spinner.block().equals(PolymerPonderScene.ELEVATOR_PULLEY))
                        stills.addAll(PolymerPonderScene.ELEVATOR_PULLEY_PARTS);
                }
            var bankBaker = new PolymerPonderBaker(builder::getDataOrSource, generated);
            Map<String, Matrix4f> partBanks = new HashMap<>();
            for (PolymerPonderScene scene : scenes) {
                Map<List<PolymerPonderItems.Draw>, List<PolymerPonderItems.Draw>> swapped = new IdentityHashMap<>();
                Map<PolymerPonderItems.Draw, PolymerPonderItems.Draw> canonical = new HashMap<>();
                for (var tick : new ArrayList<>(scene.items.keySet())) {
                    List<PolymerPonderItems.Draw> list = scene.items.get(tick);
                    List<PolymerPonderItems.Draw> result = swapped.get(list);
                    if (result == null) {
                        List<PolymerPonderItems.Draw> draws = new ArrayList<>();
                        for (var draw : list)
                            draws.add(canonical.computeIfAbsent(
                                    draw.spin() == null
                                            ? draw
                                            : spinDraw(builder, writer, sprites, bankBaker, partBanks, draw),
                                    d -> d));
                        result = List.copyOf(draws);
                        swapped.put(list, result);
                    }
                    scene.items.put(tick, result);
                }
            }
            for (PolymerPonderScene scene : scenes)
                for (var draws : scene.items.values())
                    for (var draw : draws)
                        if (!draw.model().startsWith(PolymerPonderScene.PART_BANK)) stills.add(draw.model());
            Set<String> beltVariants = new HashSet<>();
            for (String model : stills) {
                Object[] belt = PolymerPonderBlockModels.beltAnimation(model);
                if (belt != null && (int) belt[1] != 0)
                    PolymerScrollAnimations.addBeltVariant(
                            builder, (String) belt[0], (int) belt[1], (int) belt[2], (boolean) belt[3], beltVariants);
            }
            for (PolymerPonderScenes.Tag tag : PolymerPonderScenes.TAGS)
                writer.item(
                        new PackAssetId(Create.MOD_ID, "ui/" + PolymerPonderDialog.tagGridId(tag)),
                        tagGrid(builder, tag, stills));
            for (String model : stills) {
                JsonObject child = new JsonObject();
                child.addProperty("parent", model);
                child.add("display", JsonParser.parseString(IDENTITY_GUI));
                writer.model(PackAssetId.parse(PolymerPonderScene.stillModel(model)), child);
            }
            plateWalls(writer);
            for (String variant : PolymerPonderParrots.VARIANTS) {
                writer.png(
                        PackAssetId.parse(PolymerPonderParrots.texture(variant)),
                        PngCodec.decode(require(
                                builder, "assets/minecraft/textures/entity/parrot/parrot_" + variant + ".png")));
                for (var part : PolymerPonderParrots.PARTS) {
                    JsonObject model = PolymerPonderParrots.partModel(variant, part);
                    writer.model(PackAssetId.parse(PolymerPonderParrots.model(variant, part.name())), model);
                    generated.put(PolymerPonderParrots.model(variant, part.name()), model);
                }
            }
            writer.png(
                    PackAssetId.parse(PolymerPonderCarts.TEXTURE),
                    PngCodec.decode(require(builder, "assets/minecraft/textures/entity/minecart/minecart.png")));
            for (var part : PolymerPonderCarts.PARTS) {
                JsonObject model = PolymerPonderCarts.partModel(part);
                writer.model(PackAssetId.parse(PolymerPonderCarts.model(part.name())), model);
                generated.put(PolymerPonderCarts.model(part.name()), model);
            }
            for (var layer : PolymerPonderMobs.LAYERS) {
                BufferedImage image = PngCodec.decode(require(builder, layer.source()));
                writer.png(PackAssetId.parse(layer.texture()), image);
                for (var box : layer.boxes()) {
                    JsonObject model = PolymerPonderMobs.partModel(layer, box, layer.texW(), layer.texH());
                    writer.model(PackAssetId.parse(layer.model(box)), model);
                    generated.put(layer.model(box), model);
                }
            }
            writer.png(
                    PackAssetId.parse(PolymerPonderItems.FONT_TEXTURE),
                    PngCodec.decode(require(builder, PolymerPonderItems.FONT)));
            for (char c = 0; c < 128; c++) {
                if (!PolymerPonderItems.hasGlyph(c)) continue;
                int u = c % 16, v = c / 16;
                JsonObject glyph = JsonParser.parseString("{\"textures\":{\"t\":\"" + PolymerPonderItems.FONT_TEXTURE
                                + "\",\"particle\":\"" + PolymerPonderItems.FONT_TEXTURE
                                + "\"},\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,0],\"shade\":false,\"faces\":{"
                                + "\"south\":{\"uv\":[" + u + "," + v + "," + (u + 1) + "," + (v + 1)
                                + "],\"texture\":\"#t\",\"tintindex\":0},"
                                + "\"north\":{\"uv\":[" + (u + 1) + "," + v + "," + u + "," + (v + 1)
                                + "],\"texture\":\"#t\",\"tintindex\":0}}}]}")
                        .getAsJsonObject();
                glyph.add("display", JsonParser.parseString(IDENTITY_GUI));
                writer.model(PackAssetId.parse(PolymerPonderItems.glyphModel(c)), glyph);
                generated.put(PolymerPonderItems.glyphModel(c), glyph);
            }
            writer.png(
                    PackAssetId.parse(PolymerPonderParrots.HAT_TEXTURE),
                    PngCodec.decode(require(builder, "assets/create/textures/entity/train_hat.png")));
            JsonObject hat = PackJson.object(require(builder, "assets/create/models/entity/train_hat.json"));
            hat.add(
                    "textures",
                    JsonParser.parseString("{\"0\":\"" + PolymerPonderParrots.HAT_TEXTURE + "\",\"particle\":\""
                            + PolymerPonderParrots.HAT_TEXTURE + "\"}"));
            hat.add("display", JsonParser.parseString(IDENTITY_GUI));
            writer.model(PackAssetId.parse(PolymerPonderParrots.HAT), hat);
            for (int stage = 0; stage < 10; stage++) {
                String sprite = "minecraft:block/destroy_stage_" + stage;
                JsonObject faces = new JsonObject();
                for (String face : new String[] {"down", "up", "north", "south", "west", "east"})
                    faces.add(face, JsonParser.parseString("{\"uv\":[0,0,16,16],\"texture\":\"#s\"}"));
                JsonObject element = new JsonObject();
                element.add("from", JsonParser.parseString("[0,0,0]"));
                element.add("to", JsonParser.parseString("[16,16,16]"));
                element.add("faces", faces);
                JsonArray elements = new JsonArray();
                elements.add(element);
                JsonObject model = new JsonObject();
                model.add(
                        "textures",
                        JsonParser.parseString("{\"s\":\"" + sprite + "\",\"particle\":\"" + sprite + "\"}"));
                model.add("elements", elements);
                model.add("display", JsonParser.parseString(IDENTITY_GUI));
                writer.model(PackAssetId.parse(PolymerPonderScene.breakingModel(stage)), model);
            }
            var baker = new PolymerPonderBaker(builder::getDataOrSource, generated);
            Map<String, String> bakedIds = new HashMap<>();
            for (PolymerPonderScene scene : scenes) {
                Map<Integer, PolymerPonderScene.Baked> baked = bakeWindows(writer, baker, scene, bakedIds);
                for (int view = 0; view < scene.views(); view++)
                    writer.item(
                            new PackAssetId(Create.MOD_ID, "ui/" + scene.sceneId(0, view)),
                            scene.sceneDefinition(view, scene.scale, SCENE_W, SCENE_H, baked));
                scene.releaseLayout();
            }
            Map<String, JsonArray> switchBlocks = new HashMap<>();
            PolymerPonderScene.SwitchBlocks bakedSwitch = (scene, outgoing, tick) -> {
                String key = scene.id + (outgoing ? "_out" : "_in" + tick);
                JsonArray cached = switchBlocks.get(key);
                if (cached == null) {
                    try {
                        cached = bakeSwitchBlocks(
                                writer,
                                baker,
                                "create:block/p/baked/switch_" + key,
                                PolymerPonderScene.switchBlocks(scene, outgoing, tick),
                                bakedIds);
                    } catch (IOException error) {
                        throw new UncheckedIOException(error);
                    }
                    switchBlocks.put(key, cached);
                }
                return cached;
            };
            for (var list : PolymerPonderScenes.storyboardLists())
                for (int i = 0; i + 1 < list.size(); i++)
                    for (int direction : new int[] {1, -1}) {
                        PolymerPonderScene
                                from = list.get(direction > 0 ? i : i + 1).scene(),
                                to = list.get(direction > 0 ? i + 1 : i).scene();
                        for (int view = 0; view < from.views(); view++)
                            for (int frame = 0; frame < PolymerPonderScene.SWITCH_FRAMES; frame++)
                                writer.item(
                                        new PackAssetId(
                                                Create.MOD_ID,
                                                "ui/" + PolymerPonderScene.switchId(from, to, frame, view)),
                                        PolymerPonderScene.switchDefinition(
                                                from,
                                                to,
                                                direction,
                                                frame,
                                                view,
                                                SCALE,
                                                SCENE_W,
                                                SCENE_H,
                                                bakedSwitch));
                    }
            PolymerPonderLayout.addAssets(builder, scenes, null);
        } catch (IOException | RuntimeException error) {
            Create.LOGGER.warn("Dialog Ponder assets unavailable", error);
        }
    }

    private static JsonArray bakeSwitchBlocks(
            PackAssetWriter writer,
            PolymerPonderBaker baker,
            String id,
            JsonArray entries,
            Map<String, String> bakedIds)
            throws IOException {
        List<PolymerPonderBaker.Entry> bakeable = new ArrayList<>();
        JsonArray kept = new JsonArray();
        for (JsonElement element : entries) {
            JsonArray single = new JsonArray();
            single.add(element);
            var parsed = baker.entries(single);
            if (parsed == null) kept.add(element);
            else bakeable.addAll(parsed);
        }
        if (bakeable.size() < 2) return entries;
        JsonObject[] model = baker.bake(id, bakeable);
        if (model == null) return entries;
        writeBaked(writer, bakedIds, id, model);
        JsonArray result = new JsonArray();
        result.add(model[1]);
        result.addAll(kept);
        return result;
    }

    private static void writeBaked(PackAssetWriter writer, Map<String, String> bakedIds, String id, JsonObject[] model)
            throws IOException {
        String key;
        try {
            key = HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(model[0].toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        String existing = bakedIds.putIfAbsent(key, id);
        if (existing != null) {
            model[1].addProperty("model", existing);
            return;
        }
        writer.model(PackAssetId.parse(id), model[0]);
    }

    static final int BAKE_MIN_FRAMES = 4;

    static final String TAG_CELL = "create:block/p/tag_cell";

    private static Map<Integer, PolymerPonderScene.Baked> bakeWindows(
            PackAssetWriter writer, PolymerPonderBaker baker, PolymerPonderScene scene, Map<String, String> bakedIds)
            throws IOException {
        Map<Integer, PolymerPonderScene.Baked> result = new HashMap<>();
        int[] frames = scene.frames;
        for (int start = 0; start < frames.length; ) {
            int end = start + 1;
            while (end < frames.length && frames[end] == frames[end - 1] + 2) end++;
            int count = end - start;
            if (count >= BAKE_MIN_FRAMES) {
                Map<String, List<PolymerPonderScene.Part>> groupParts = new LinkedHashMap<>();
                Map<String, List<PolymerPonderBaker.Entry>> groupEntries = new HashMap<>();
                Map<String, Matrix4f[]> groupMotion = new HashMap<>();
                for (PolymerPonderScene.Part part : scene.parts) {
                    List<List<PolymerPonderBaker.Entry>> perFrame = new ArrayList<>();
                    for (int i = start; i < end; i++) {
                        if (!scene.ownAt(part, frames[i])) {
                            perFrame.add(null);
                            continue;
                        }
                        JsonArray partEntries = new JsonArray();
                        scene.partEntries(partEntries, part, frames[i] + 1, false, false);
                        if (partEntries.isEmpty()) {
                            perFrame.add(null);
                            continue;
                        }
                        var parsed = baker.entries(partEntries);
                        if (parsed == null) {
                            perFrame = null;
                            break;
                        }
                        perFrame.add(parsed);
                    }
                    Matrix4f[] motion = perFrame == null ? null : rigidMotion(perFrame);
                    if (motion == null) continue;
                    StringBuilder key = new StringBuilder();
                    for (var m : motion) {
                        if (m == null) {
                            key.append('h');
                            continue;
                        }
                        for (float v : m.get(new float[16]))
                            key.append(Math.round(v * 1e3f)).append(',');
                    }
                    var reference = perFrame.stream()
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElseThrow();
                    groupParts
                            .computeIfAbsent(key.toString(), k -> new ArrayList<>())
                            .add(part);
                    groupEntries
                            .computeIfAbsent(key.toString(), k -> new ArrayList<>())
                            .addAll(reference);
                    groupMotion.putIfAbsent(key.toString(), motion);
                }
                Set<PolymerPonderScene.Part> baked = Collections.newSetFromMap(new IdentityHashMap<>());
                List<List<JsonObject>> entries = new ArrayList<>();
                for (int i = 0; i < count; i++) entries.add(new ArrayList<>());
                int group = 0;
                for (var g : groupParts.entrySet()) {
                    List<PolymerPonderBaker.Entry> first = groupEntries.get(g.getKey());
                    if (first.size() < 2) continue;
                    String id = "create:block/p/baked/" + scene.id + "_" + frames[start] + "_" + group++;
                    JsonObject[] model = baker.bake(id, first);
                    if (model == null) continue;
                    writeBaked(writer, bakedIds, id, model);
                    baked.addAll(g.getValue());
                    Matrix4f placement = PolymerPonderBaker.transform(model[1]);
                    Matrix4f[] motion = groupMotion.get(g.getKey());
                    for (int i = 0; i < count; i++) {
                        if (motion[i] == null) continue;
                        JsonObject entry = model[1].deepCopy();
                        entry.add("transformation", PolymerPonderScene.matrix(new Matrix4f(motion[i]).mul(placement)));
                        entries.get(i).add(entry);
                    }
                }
                if (!baked.isEmpty())
                    for (int i = 0; i < count; i++)
                        result.put(frames[start + i], new PolymerPonderScene.Baked(baked, List.copyOf(entries.get(i))));
            }
            start = end;
        }
        return result;
    }

    private static Matrix4f[] rigidMotion(List<List<PolymerPonderBaker.Entry>> perFrame) {
        var first = perFrame.stream().filter(Objects::nonNull).findFirst().orElse(null);
        if (first == null || first.isEmpty()) return null;
        Matrix4f inverse = new Matrix4f(first.getFirst().transform()).invert();
        Matrix4f[] motion = new Matrix4f[perFrame.size()];
        for (int i = 0; i < perFrame.size(); i++) {
            var frame = perFrame.get(i);
            if (frame == null) continue;
            if (frame.size() != first.size()) return null;
            motion[i] = new Matrix4f(frame.getFirst().transform()).mul(inverse);
            for (int j = 0; j < first.size(); j++) {
                if (!frame.get(j).model().equals(first.get(j).model())) return null;
                Matrix4f expected = new Matrix4f(motion[i]).mul(first.get(j).transform());
                float[] a = expected.get(new float[16]),
                        b = frame.get(j).transform().get(new float[16]);
                for (int c = 0; c < 16; c++) if (Math.abs(a[c] - b[c]) > 1e-3f) return null;
            }
        }
        return motion;
    }

    private static void plateWalls(PackAssetWriter writer) throws IOException {
        BufferedImage shadow = new BufferedImage(4, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            int alpha = Math.round(0x66 * 4 / 4.5f * (1 - (y + .5f) / 16));
            for (int x = 0; x < 4; x++) shadow.setRGB(x, y, alpha << 24);
        }
        wall(writer, PolymerPonderScene.PLATE_SHADOW, shadow);
        BufferedImage cell = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) cell.setRGB(x, y, 0xFF000000);
        wall(writer, TAG_CELL, cell);
        for (int step = 1; step <= FINISH_FLASH_STEPS; step++) {
            float alpha = PolymerPonderScene.finishFlash(step)[1];
            BufferedImage flash = new BufferedImage(4, 8, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 8; y++) {
                int a = Math.round(0xAA * alpha * (y + .5f) / 8);
                for (int x = 0; x < 4; x++) flash.setRGB(x, y, a << 24 | 0xC6FFC9);
            }
            wall(writer, PolymerPonderScene.plateFlash(step), flash);
        }
    }

    private static void wall(PackAssetWriter writer, String model, BufferedImage texture) throws IOException {
        PackAssetId id = PackAssetId.parse(model);
        writer.png(id, texture);
        JsonObject face = new JsonObject();
        JsonArray uv = new JsonArray();
        for (int v : new int[] {0, 0, 16, 16}) uv.add(v);
        face.add("uv", uv);
        face.addProperty("texture", "#wall");
        JsonObject faces = new JsonObject();
        faces.add("north", face);
        faces.add("south", face.deepCopy());
        JsonObject element = new JsonObject();
        element.add("from", JsonParser.parseString("[0,0,0]"));
        element.add("to", JsonParser.parseString("[16,16,0]"));
        element.addProperty("shade", false);
        element.add("faces", faces);
        JsonArray elements = new JsonArray();
        elements.add(element);
        JsonObject textures = new JsonObject();
        textures.addProperty("wall", id.reference());
        textures.addProperty("particle", id.reference());
        JsonObject json = new JsonObject();
        json.add("textures", textures);
        json.add("elements", elements);
        json.add("display", JsonParser.parseString(IDENTITY_GUI));
        writer.model(id, json);
        JsonArray ring = new JsonArray();
        String[][] sides = {
            {"[0,0,0]", "[16,16,0]", "north", "south"},
            {"[0,0,16]", "[16,16,16]", "north", "south"},
            {"[0,0,0]", "[0,16,16]", "west", "east"},
            {"[16,0,0]", "[16,16,16]", "west", "east"}
        };
        for (String[] side : sides) {
            JsonObject sideFaces = new JsonObject();
            sideFaces.add(side[2], face.deepCopy());
            sideFaces.add(side[3], face.deepCopy());
            JsonObject sideElement = new JsonObject();
            sideElement.add("from", JsonParser.parseString(side[0]));
            sideElement.add("to", JsonParser.parseString(side[1]));
            sideElement.addProperty("shade", false);
            sideElement.add("faces", sideFaces);
            ring.add(sideElement);
        }
        JsonObject ringJson = json.deepCopy();
        ringJson.add("elements", ring);
        writer.model(PackAssetId.parse(PolymerPonderScene.ring(model)), ringJson);
    }

    private static JsonObject tagGrid(ResourcePackBuilder builder, PolymerPonderScenes.Tag tag, Set<String> stills)
            throws IOException {
        JsonArray entries = new JsonArray();
        for (int index = 0; index < tag.items().size(); index++) {
            int[] cell = PolymerPonderLayout.buttonAt(tag, index);
            float cx = 1 + 2 + 4 + cell[0] + 10, cy = 4 + cell[1] + 10, inner = 18 / 16f;
            JsonObject fill = new JsonObject();
            fill.addProperty("type", "minecraft:model");
            fill.addProperty("model", TAG_CELL);
            fill.add(
                    "transformation",
                    PolymerPonderScene.matrix(new Matrix4f()
                            .translation(.5f + (cx - 8) / 16 - inner / 2, .5f - (cy - 8) / 16 - inner / 2, -2)
                            .scale(inner, inner, 1)));
            entries.add(fill);
            Identifier id = Identifier.parse(tag.items().get(index));
            byte[] definition =
                    builder.getDataOrSource("assets/" + id.getNamespace() + "/items/" + id.getPath() + ".json");
            if (definition == null) continue;
            JsonObject model = PackJson.object(definition).getAsJsonObject("model");
            if (model == null || !model.has("model")) continue;
            String modelId = model.get("model").getAsString();
            if (!modelId.contains(":")) modelId = "minecraft:" + modelId;
            float[] gui = guiTransform(builder, modelId);
            if (gui == null) continue;
            stills.add(modelId);
            int[] at = PolymerPonderLayout.buttonAt(tag, index);
            float gx = 1 + 2 + 4 + at[0] + 10, gy = 4 + at[1] + 10;
            Matrix4f transform = new Matrix4f()
                    .translation(.5f, .5f, .5f)
                    .translate((gx - 8) / 16, -(gy - 8) / 16, 0)
                    .translate(
                            Math.clamp(gui[0], -80, 80) / 16,
                            Math.clamp(gui[1], -80, 80) / 16,
                            Math.clamp(gui[2], -80, 80) / 16)
                    .rotateXYZ((float) Math.toRadians(gui[3]), (float) Math.toRadians(gui[4]), (float)
                            Math.toRadians(gui[5]))
                    .scale(gui[6], gui[7], gui[8])
                    .translate(-.5f, -.5f, -.5f);
            JsonObject entry = new JsonObject();
            entry.addProperty("type", "minecraft:model");
            entry.addProperty("model", PolymerPonderScene.stillModel(modelId));
            entry.add("transformation", PolymerPonderScene.matrix(transform));
            entries.add(entry);
        }
        JsonObject composite = new JsonObject();
        composite.addProperty("type", "minecraft:composite");
        composite.add("models", entries);
        JsonObject result = new JsonObject();
        result.addProperty("oversized_in_gui", true);
        result.add("model", composite);
        return result;
    }

    private static float[] guiTransform(ResourcePackBuilder builder, String modelId) {
        for (int depth = 0; depth < 16; depth++) {
            if (modelId.startsWith("builtin/") || modelId.startsWith("minecraft:builtin/")) break;
            Identifier id = Identifier.parse(modelId);
            byte[] data = builder.getDataOrSource("assets/" + id.getNamespace() + "/models/" + id.getPath() + ".json");
            if (data == null) return depth == 0 ? null : new float[] {0, 0, 0, 0, 0, 0, 1, 1, 1};
            JsonObject json = PackJson.object(data);
            if (json.has("display") && json.getAsJsonObject("display").has("gui")) {
                JsonObject gui = json.getAsJsonObject("display").getAsJsonObject("gui");
                float[] result = {0, 0, 0, 0, 0, 0, 1, 1, 1};
                String[] keys = {"translation", "rotation", "scale"};
                for (int k = 0; k < 3; k++)
                    if (gui.has(keys[k]))
                        for (int i = 0; i < 3; i++)
                            result[3 * k + i] =
                                    gui.getAsJsonArray(keys[k]).get(i).getAsFloat();
                return result;
            }
            if (!json.has("parent")) break;
            modelId = json.get("parent").getAsString();
            if (!modelId.contains(":")) modelId = "minecraft:" + modelId;
        }
        return new float[] {0, 0, 0, 0, 0, 0, 1, 1, 1};
    }

    private static JsonObject spinBank(
            ResourcePackBuilder builder,
            PackAssetWriter writer,
            Set<String> sprites,
            PolymerPonderScene.Spinner spinner,
            int ticks,
            int dir)
            throws IOException {
        Identifier model = Identifier.parse(spinner.model());
        JsonObject source = PackJson.object(
                require(builder, "assets/" + model.getNamespace() + "/models/" + model.getPath() + ".json"));
        JsonArray elements = source.getAsJsonArray("elements");
        if (elements == null) throw new IOException("Spinner model without its own elements: " + spinner.model());
        String axis = spinner.alongZ() ? "z" : "y";
        JsonObject textures = new JsonObject();
        JsonArray merged = new JsonArray();
        for (int k = 0; k < PolymerPonderScene.COPIES; k++) {
            Map<String, Pose> poses = new HashMap<>();
            for (String key : spinner.textures()) {
                Identifier texture = Identifier.parse(
                        source.getAsJsonObject("textures").get(key).getAsString());
                Pose pose = pose(builder, sprites, texture, ticks, k, PolymerPonderScene.COPIES);
                poses.put(key, pose);
                textures.addProperty("k" + k + "_" + key, pose.id());
            }
            for (JsonElement element : elements) {
                JsonObject copy = element.getAsJsonObject().deepCopy();
                float own = 0;
                if (copy.has("rotation")) {
                    JsonObject rotation = copy.getAsJsonObject("rotation");
                    JsonArray origin = rotation.getAsJsonArray("origin");
                    boolean centred = origin != null
                            && origin.size() == 3
                            && origin.get(0).getAsFloat() == 8
                            && origin.get(1).getAsFloat() == 8
                            && origin.get(2).getAsFloat() == 8;
                    if (!axis.equals(rotation.has("axis") ? rotation.get("axis").getAsString() : "")
                            || !centred
                            || rotation.has("rescale")
                                    && rotation.get("rescale").getAsBoolean())
                        throw new IOException("Spinner element not rotated about its spin axis: " + spinner.model());
                    own = rotation.get("angle").getAsFloat();
                }
                JsonObject rotation = new JsonObject();
                rotation.add("origin", JsonParser.parseString("[8,8,8]"));
                rotation.addProperty("axis", axis);
                rotation.addProperty("angle", own + dir * k * PolymerPonderScene.DEG_PER_COPY);
                copy.add("rotation", rotation);
                for (var face : copy.getAsJsonObject("faces").entrySet()) {
                    JsonObject value = face.getValue().getAsJsonObject();
                    String key = value.get("texture").getAsString().substring(1);
                    if (!spinner.textures().contains(key))
                        throw new IOException("Spinner face texture outside its bank: " + spinner.model() + " #" + key);
                    value.addProperty("texture", "#k" + k + "_" + key);
                    JsonArray uv = value.has("uv")
                            ? value.getAsJsonArray("uv")
                            : PolymerPonderBaker.defaultUv(
                                    face.getKey(),
                                    PackJson.vector3f(copy.getAsJsonArray("from")),
                                    PackJson.vector3f(copy.getAsJsonArray("to")));
                    value.add("uv", poses.get(key).pack(uv));
                }
                merged.add(copy);
            }
        }
        textures.add("particle", textures.get("k0_" + spinner.textures().getLast()));
        JsonObject json = new JsonObject();
        json.add("textures", textures);
        json.add("elements", merged);
        json.add("display", JsonParser.parseString(IDENTITY_GUI));
        writer.model(PackAssetId.parse(PolymerPonderScene.spinModel(spinner, ticks, dir)), json);
        return json;
    }

    private static PolymerPonderItems.Draw spinDraw(
            ResourcePackBuilder builder,
            PackAssetWriter writer,
            Set<String> sprites,
            PolymerPonderBaker baker,
            Map<String, Matrix4f> banks,
            PolymerPonderItems.Draw draw) {
        var spin = draw.spin();
        var still = new PolymerPonderItems.Draw(draw.model(), draw.transform(), draw.at());
        Matrix4f inverse = new Matrix4f(draw.transform()).invert();
        Vector3f scale = draw.transform().getScale(new Vector3f());
        if (!baker.bakeable(draw.model()) || Math.abs(scale.x - scale.y) > 1e-3f || Math.abs(scale.x - scale.z) > 1e-3f)
            return still;
        Vector3f axis = inverse.transformDirection(new Vector3f(spin.ax(), spin.ay(), spin.az()))
                .normalize();
        Vector3f pivot = inverse.transformPosition(new Vector3f(spin.px(), spin.py(), spin.pz()));
        int ticks = Math.clamp(Math.round(64 * .3f / Math.abs(spin.deg())), 1, 64);
        int loop = 4 * PolymerPonderScene.COPIES;
        for (int angle : SYMMETRY_ANGLES) {
            Matrix4f turn = new Matrix4f()
                    .translation(new Vector3f(pivot).mul(16))
                    .rotate((float) Math.toRadians(angle), axis)
                    .translate(new Vector3f(pivot).mul(-16));
            if (baker.symmetric(draw.model(), turn)) {
                loop = Math.round(angle / PolymerPonderScene.DEG_PER_COPY);
                break;
            }
        }
        int copies = loop;
        String key = draw.model() + "|" + Math.round(axis.x * 1e3) + "," + Math.round(axis.y * 1e3) + ","
                + Math.round(axis.z * 1e3) + "|" + Math.round(pivot.x * 1e3) + "," + Math.round(pivot.y * 1e3) + ","
                + Math.round(pivot.z * 1e3) + "|" + ticks + "|" + copies;
        String id = PolymerPonderScene.PART_BANK + Integer.toHexString(key.hashCode() & 0x7fffffff);
        if (!banks.containsKey(key)) {
            List<PolymerPonderBaker.Entry> entries = new ArrayList<>();
            List<UnaryOperator<String>> remap = new ArrayList<>();
            for (int k = 0; k < copies; k++) {
                entries.add(new PolymerPonderBaker.Entry(
                        draw.model(),
                        new Matrix4f()
                                .translation(pivot)
                                .rotate((float) Math.toRadians(k * PolymerPonderScene.DEG_PER_COPY), axis)
                                .translate(new Vector3f(pivot).negate())));
                int slot = k;
                remap.add(sprite -> {
                    try {
                        return sprite(builder, writer, sprites, Identifier.parse(sprite), ticks, slot, copies);
                    } catch (IOException error) {
                        throw new UncheckedIOException(error);
                    }
                });
            }
            JsonObject[] baked = baker.bake(id, entries, remap);
            if (baked == null) {
                banks.put(key, null);
                return still;
            }
            writer.model(PackAssetId.parse(id), baked[0]);
            banks.put(key, PolymerPonderBaker.transform(baked[1]));
        }
        Matrix4f placed = banks.get(key);
        return placed == null
                ? still
                : new PolymerPonderItems.Draw(id, new Matrix4f(draw.transform()).mul(placed), draw.at());
    }

    private static String sprite(
            ResourcePackBuilder builder,
            PackAssetWriter writer,
            Set<String> written,
            Identifier texture,
            int ticks,
            int k)
            throws IOException {
        return sprite(builder, writer, written, texture, ticks, k, PolymerPonderScene.COPIES);
    }

    private static final int[] SYMMETRY_ANGLES = {5, 10, 15, 20, 30, 40, 45, 60, 90, 120, 180};

    static final int SLOTS_PER_SPRITE = 3;

    record Pose(String id, int column, int columns) {
        String encoded() {
            return columns == 1 ? id : id + "|" + column + "|" + columns;
        }

        JsonArray pack(JsonArray uv) {
            if (columns == 1) return uv;
            float u0 = uv.get(0).getAsFloat(), u2 = uv.get(2).getAsFloat();
            float inset = 16f / 1000, low = column * 16 + inset, high = (column + 1) * 16 - inset;
            JsonArray packed = new JsonArray();
            for (int i = 0; i < 4; i++) {
                float value = uv.get(i).getAsFloat();
                if (i % 2 == 0) {
                    float u = column * 16 + value;
                    if (value == Math.min(u0, u2) && value == 0) u = Math.max(u, low);
                    if (value == Math.max(u0, u2) && value == 16) u = Math.min(u, high);
                    packed.add(u / columns);
                } else packed.add(value);
            }
            return packed;
        }
    }

    private static String sprite(
            ResourcePackBuilder builder,
            PackAssetWriter writer,
            Set<String> written,
            Identifier texture,
            int ticks,
            int k,
            int copies)
            throws IOException {
        return pose(builder, written, texture, ticks, k, copies).encoded();
    }

    private static Pose pose(
            ResourcePackBuilder builder, Set<String> written, Identifier texture, int ticks, int k, int copies)
            throws IOException {
        int group = k / SLOTS_PER_SPRITE, first = group * SLOTS_PER_SPRITE;
        int size = Math.min(SLOTS_PER_SPRITE, copies - first), column = k - first;
        Strips strips = strips(builder, texture);
        PackAssetId id = new PackAssetId(
                Create.MOD_ID,
                "item/p/q/" + strips.key() + "_" + Integer.toString(ticks, 36) + "_" + Integer.toString(copies, 36)
                        + "_" + Integer.toString(group, 36));
        Pose pose = new Pose(id.reference(), column, size);
        if (!written.add(id.reference())) return pose;
        boolean blank = size < copies, blankFirst = blank && group > 0;
        builder.addData(id.texturePath(), strips.packed(size, blank, blankFirst));
        if (copies == 1) return pose;
        int blankIndex = blankFirst ? 0 : size, offset = blankFirst ? 1 : 0;
        JsonArray frames = new JsonArray();
        for (int slot = 0; slot < copies; ) {
            int index = slot >= first && slot < first + size ? slot - first + offset : blankIndex, run = 1;
            if (index == blankIndex)
                while (slot + run < copies && !(slot + run >= first && slot + run < first + size)) run++;
            frames.add(PackJson.frame(index, run * ticks));
            slot += run;
        }
        JsonObject animation = new JsonObject();
        animation.addProperty("width", size * strips.width());
        animation.addProperty("height", strips.height());
        animation.add("frames", frames);
        JsonObject meta = new JsonObject();
        meta.add("animation", animation);
        builder.addStringData(id.texturePath() + ".mcmeta", meta.toString());
        return pose;
    }

    private static Strips strips(ResourcePackBuilder builder, Identifier texture) throws IOException {
        Strips strips = STRIPS.get(texture);
        if (strips == null) {
            BufferedImage image = capped(PngCodec.decode(
                    require(builder, "assets/" + texture.getNamespace() + "/textures/" + texture.getPath() + ".png")));
            BufferedImage argb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            argb.getGraphics().drawImage(image, 0, 0, null);
            String key;
            try {
                var digest = MessageDigest.getInstance("SHA-256");
                int[] pixels = argb.getRGB(0, 0, argb.getWidth(), argb.getHeight(), null, 0, argb.getWidth());
                ByteBuffer raw = ByteBuffer.allocate(8 + pixels.length * 4);
                raw.putInt(argb.getWidth())
                        .putInt(argb.getHeight())
                        .asIntBuffer()
                        .put(pixels);
                digest.update(raw.array());
                key = HexFormat.of().formatHex(digest.digest(), 0, 6);
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
            strips = new Strips(argb, key, new ConcurrentHashMap<>());
            STRIPS.put(texture, strips);
        }
        return strips;
    }

    static final int POSE_MAX = 32;

    static BufferedImage capped(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        if (Math.max(w, h) <= POSE_MAX) return image;
        double scale = (double) POSE_MAX / Math.max(w, h);
        int tw = Math.max(1, (int) Math.round(w * scale)), th = Math.max(1, (int) Math.round(h * scale));
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        for (int ty = 0; ty < th; ty++)
            for (int tx = 0; tx < tw; tx++) {
                int x0 = tx * w / tw,
                        x1 = Math.max(x0 + 1, (tx + 1) * w / tw),
                        y0 = ty * h / th,
                        y1 = Math.max(y0 + 1, (ty + 1) * h / th);
                long a = 0, r = 0, g = 0, b = 0, n = 0;
                boolean partial = false;
                for (int y = y0; y < y1; y++)
                    for (int x = x0; x < x1; x++) {
                        int p = image.getRGB(x, y), alpha = p >>> 24;
                        partial |= alpha != 0 && alpha != 255;
                        a += alpha;
                        r += (long) (p >> 16 & 255) * alpha;
                        g += (long) (p >> 8 & 255) * alpha;
                        b += (long) (p & 255) * alpha;
                        n++;
                    }
                int alpha = partial ? (int) (a / n) : a * 2 < 255 * n ? 0 : 255;
                if (alpha == 0 || a == 0) continue;
                out.setRGB(tx, ty, alpha << 24 | (int) (r / a) << 16 | (int) (g / a) << 8 | (int) (b / a));
            }
        return out;
    }

    private record Strips(BufferedImage image, String key, Map<String, byte[]> encoded) {
        int width() {
            return image.getWidth();
        }

        int height() {
            return image.getHeight();
        }

        byte[] packed(int size, boolean blank, boolean blankFirst) {
            return encoded.computeIfAbsent(size + (blank ? blankFirst ? "b" : "e" : "n"), layout -> {
                int w = width(), h = height(), frames = size + (blank ? 1 : 0), offset = blankFirst ? 1 : 0;
                BufferedImage strip = new BufferedImage(w * size, h * frames, BufferedImage.TYPE_INT_ARGB);
                for (int column = 0; column < size; column++)
                    for (int y = 0; y < h; y++)
                        for (int x = 0; x < w; x++)
                            strip.setRGB(column * w + x, (column + offset) * h + y, image.getRGB(x, y));
                try {
                    return PngCodec.encode(strip);
                } catch (IOException error) {
                    throw new UncheckedIOException(error);
                }
            });
        }
    }

    private static final Map<Identifier, Strips> STRIPS = new ConcurrentHashMap<>();

    private static byte[] require(ResourcePackBuilder builder, String path) throws IOException {
        byte[] data = builder.getDataOrSource(path);
        if (data == null) throw new IOException("Missing " + path);
        return data;
    }
}
