package com.zurrtum.create.infrastructure.polymer.ponder;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;

final class PolymerPonderMobs {
    private PolymerPonderMobs() {}

    record Box(
            String name,
            int u,
            int v,
            float x,
            float y,
            float z,
            float w,
            float h,
            float d,
            float grow,
            boolean mirror,
            float px,
            float py,
            float pz,
            float rx,
            float ry,
            float rz) {}

    record Layer(String name, String source, int texW, int texH, boolean tinted, List<Box> boxes) {
        String texture() {
            return "create:block/p/mob_" + name;
        }

        String model(Box b) {
            return PolymerPonderScene.PART_BANK + "mob/" + name + "/" + b.name;
        }
    }

    private static final float HALF_PI = (float) (Math.PI / 2);

    static final Layer BLAZE;

    static {
        List<Box> boxes = new ArrayList<>();
        boxes.add(new Box("head", 0, 0, -4, -4, -4, 8, 8, 8, 0, false, 0, 0, 0, 0, 0, 0));
        boxes.add(new Box("rod", 0, 16, 0, 0, 0, 2, 8, 2, 0, false, 0, 0, 0, 0, 0, 0));
        BLAZE = new Layer(
                "blaze", "assets/minecraft/textures/entity/blaze/blaze.png", 64, 32, false, List.copyOf(boxes));
    }

    private static List<Box> sheepBoxes(float head, float body, float leg, boolean mirrorRight, int legHeight) {
        float legY = 24 - 12;
        return List.of(
                new Box(
                        "head",
                        0,
                        0,
                        -3,
                        -4,
                        head == 0 ? -6 : -4,
                        6,
                        6,
                        head == 0 ? 8 : 6,
                        head,
                        false,
                        0,
                        6,
                        -8,
                        0,
                        0,
                        0),
                new Box("body", 28, 8, -4, -10, -7, 8, 16, 6, body, false, 0, 5, 2, HALF_PI, 0, 0),
                new Box("right_hind_leg", 0, 16, -2, 0, -2, 4, legHeight, 4, leg, mirrorRight, -3, legY, 7, 0, 0, 0),
                new Box("left_hind_leg", 0, 16, -2, 0, -2, 4, legHeight, 4, leg, false, 3, legY, 7, 0, 0, 0),
                new Box("right_front_leg", 0, 16, -2, 0, -2, 4, legHeight, 4, leg, mirrorRight, -3, legY, -5, 0, 0, 0),
                new Box("left_front_leg", 0, 16, -2, 0, -2, 4, legHeight, 4, leg, false, 3, legY, -5, 0, 0, 0));
    }

    static final Layer SHEEP = new Layer(
            "sheep", "assets/minecraft/textures/entity/sheep/sheep.png", 64, 32, false, sheepBoxes(0, 0, 0, true, 12));
    static final Layer SHEEP_WOOL = new Layer(
            "sheep_wool",
            "assets/minecraft/textures/entity/sheep/sheep_wool.png",
            64,
            32,
            true,
            sheepBoxes(0.6f, 1.75f, 0.5f, false, 6));
    static final Layer SHEEP_UNDERCOAT = new Layer(
            "sheep_undercoat",
            "assets/minecraft/textures/entity/sheep/sheep_wool_undercoat.png",
            64,
            32,
            true,
            sheepBoxes(0, 0, 0, true, 12));

    static final List<Layer> LAYERS = List.of(BLAZE, SHEEP, SHEEP_WOOL, SHEEP_UNDERCOAT);

    static JsonObject partModel(Layer layer, Box b, int texW, int texH) {
        JsonObject model = PolymerPonderParrots.boxModel(
                layer.texture(),
                b.u,
                b.v,
                b.x - b.grow,
                b.y - b.grow,
                b.z - b.grow,
                b.w + 2 * b.grow,
                b.h + 2 * b.grow,
                b.d + 2 * b.grow,
                texW,
                texH,
                b.w,
                b.h,
                b.d,
                b.mirror);
        if (layer.tinted)
            for (var face : model.getAsJsonArray("elements")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("faces")
                    .entrySet()) face.getValue().getAsJsonObject().addProperty("tintindex", 0);
        return model;
    }

    record Draw(String model, Matrix4f transform, int tint) {}

    private static Matrix4f root(float x, float y, float z, float bodyRot) {
        return new Matrix4f()
                .translation(x, y, z)
                .rotateY((float) Math.toRadians(180 - bodyRot))
                .scale(-1, -1, 1)
                .translate(0, -1.501f, 0);
    }

    private static final Matrix4f FLIP = new Matrix4f().scaling(-1, -1, 1);

    static List<Draw> blaze(float x, float y, float z, float bodyRot, float headYaw, float headPitch, float age) {
        Matrix4f root = root(x, y, z, bodyRot);
        List<Draw> draws = new ArrayList<>();
        Box head = BLAZE.boxes.get(0), rod = BLAZE.boxes.get(1);
        draws.add(new Draw(
                BLAZE.model(head),
                new Matrix4f(root)
                        .mul(PolymerPonderParrots.pose(new float[] {
                            0, 0, 0, headPitch * (float) Math.PI / 180, headYaw * (float) Math.PI / 180, 0
                        }))
                        .mul(FLIP),
                -1));
        float f = age * (float) Math.PI * -0.1f;
        for (int i = 0; i < 12; i++) {
            if (i == 4) f = 0.7853982f + age * (float) Math.PI * 0.03f;
            if (i == 8) f = 0.47123894f + age * (float) Math.PI * -0.05f;
            float radius = i < 4 ? 9 : i < 8 ? 7 : 5;
            float py = i < 4
                    ? -2 + (float) Math.cos((i * 2 + age) * 0.25f)
                    : i < 8
                            ? 2 + (float) Math.cos((i * 2 + age) * 0.25f)
                            : 11 + (float) Math.cos((i * 1.5f + age) * 0.5f);
            draws.add(new Draw(
                    BLAZE.model(rod),
                    new Matrix4f(root)
                            .mul(PolymerPonderParrots.pose(
                                    new float[] {(float) Math.cos(f) * radius, py, (float) Math.sin(f) * radius, 0, 0, 0
                                    }))
                            .mul(FLIP),
                    -1));
            f += 1.5707964f;
        }
        return draws;
    }

    static List<Draw> sheep(
            float x,
            float y,
            float z,
            float bodyRot,
            float headYaw,
            float headPitch,
            float walkPos,
            float walkSpeed,
            float eatPosition,
            float eatAngle,
            int woolColor,
            boolean white,
            boolean sheared) {
        Matrix4f root = root(x, y, z, bodyRot);
        List<Draw> draws = new ArrayList<>();
        List<Layer> layers = new ArrayList<>(List.of(SHEEP));
        if (!white) layers.add(SHEEP_UNDERCOAT);
        if (!sheared) layers.add(SHEEP_WOOL);
        float legSwing = 0.6662f;
        for (Layer layer : layers)
            for (Box b : layer.boxes) {
                float[] p = {b.px, b.py, b.pz, b.rx, b.ry, b.rz};
                switch (b.name) {
                    case "head" -> {
                        p[4] = headYaw * (float) Math.PI / 180;
                        p[1] += eatPosition * 9;
                        p[3] = eatAngle;
                    }
                    case "right_hind_leg", "left_front_leg" ->
                        p[3] = (float) Math.cos(walkPos * legSwing) * 1.4f * walkSpeed;
                    case "left_hind_leg", "right_front_leg" ->
                        p[3] = (float) Math.cos(walkPos * legSwing + (float) Math.PI) * 1.4f * walkSpeed;
                    default -> {}
                }
                draws.add(new Draw(
                        layer.model(b),
                        new Matrix4f(root).mul(PolymerPonderParrots.pose(p)).mul(FLIP),
                        layer.tinted ? woolColor : -1));
            }
        return draws;
    }
}
