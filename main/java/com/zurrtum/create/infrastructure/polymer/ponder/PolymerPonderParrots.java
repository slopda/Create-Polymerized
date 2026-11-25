package com.zurrtum.create.infrastructure.polymer.ponder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;

final class PolymerPonderParrots {
    private PolymerPonderParrots() {}

    static final String[] VARIANTS = {"red_blue", "green", "yellow_blue", "grey"};

    record Box(
            String name,
            String parent,
            int u,
            int v,
            float x,
            float y,
            float z,
            float w,
            float h,
            float d,
            float px,
            float py,
            float pz,
            float rx,
            float ry,
            float rz) {}

    static final List<Box> PARTS = List.of(
            new Box("body", null, 2, 8, -1.5f, 0, -1.5f, 3, 6, 3, 0, 16.5f, -3, 0.4937f, 0, 0),
            new Box("tail", null, 22, 1, -1.5f, -1, -1, 3, 4, 1, 0, 21.07f, 1.16f, 1.015f, 0, 0),
            new Box(
                    "left_wing",
                    null,
                    19,
                    8,
                    -0.5f,
                    0,
                    -1.5f,
                    1,
                    5,
                    3,
                    1.5f,
                    16.94f,
                    -2.76f,
                    -0.6981f,
                    (float) -Math.PI,
                    0),
            new Box(
                    "right_wing",
                    null,
                    19,
                    8,
                    -0.5f,
                    0,
                    -1.5f,
                    1,
                    5,
                    3,
                    -1.5f,
                    16.94f,
                    -2.76f,
                    -0.6981f,
                    (float) -Math.PI,
                    0),
            new Box("head", null, 2, 2, -1, -1.5f, -1, 2, 3, 2, 0, 15.69f, -2.76f, 0, 0, 0),
            new Box("head2", "head", 10, 0, -1, -0.5f, -2, 2, 1, 4, 0, -2, -1, 0, 0, 0),
            new Box("beak1", "head", 11, 7, -0.5f, -1, -0.5f, 1, 2, 1, 0, -0.5f, -1.5f, 0, 0, 0),
            new Box("beak2", "head", 16, 7, -0.5f, 0, -0.5f, 1, 2, 1, 0, -1.75f, -2.45f, 0, 0, 0),
            new Box("feather", "head", 2, 18, 0, -4, -2, 0, 5, 4, 0, -2.15f, 0.15f, -0.2214f, 0, 0),
            new Box("left_leg", null, 14, 18, -0.5f, 0, -0.5f, 1, 2, 1, 1, 22, -1.05f, -0.0299f, 0, 0),
            new Box("right_leg", null, 14, 18, -0.5f, 0, -0.5f, 1, 2, 1, -1, 22, -1.05f, -0.0299f, 0, 0));

    enum Pose {
        FLYING,
        STANDING,
        PARTY
    }

    static String model(String variant, String part) {
        return PolymerPonderScene.PART_BANK + "parrot/" + variant + "/" + part;
    }

    static String texture(String variant) {
        return "create:block/p/parrot_" + variant;
    }

    static final String HAT = PolymerPonderScene.PART_BANK + "train_hat";
    static final String HAT_TEXTURE = "create:block/p/train_hat";

    static JsonObject partModel(String variant, Box b) {
        return boxModel(texture(variant), b.u, b.v, b.x, b.y, b.z, b.w, b.h, b.d, 32, 32);
    }

    static JsonObject boxModel(
            String texture,
            float u,
            float v,
            float x,
            float y,
            float z,
            float w,
            float h,
            float d,
            float texW,
            float texH) {
        return boxModel(texture, u, v, x, y, z, w, h, d, texW, texH, w, h, d, false);
    }

    static JsonObject boxModel(
            String texture,
            float u,
            float v,
            float x,
            float y,
            float z,
            float w,
            float h,
            float d,
            float texW,
            float texH,
            float uw,
            float uh,
            float ud,
            boolean mirror) {
        JsonObject faces = new JsonObject();
        float su = 16 / texW, sv = 16 / texH;
        float[][] regions = {
            {u + ud, v + ud, u + ud + uw, v + ud + uh},
            {u, v + ud, u + ud, v + ud + uh},
            {u + 2 * ud + uw, v + ud, u + 2 * ud + 2 * uw, v + ud + uh},
            {u + ud + uw, v + ud, u + 2 * ud + uw, v + ud + uh},
            {u + ud + uw, v + ud, u + ud, v},
            {u + ud + 2 * uw, v, u + ud + uw, v + ud}
        };
        if (mirror) {
            float[] east = regions[1];
            regions[1] = regions[3];
            regions[3] = east;
            for (float[] r : regions) {
                float t = r[0];
                r[0] = r[2];
                r[2] = t;
            }
        }
        String[] names = {"north", "east", "south", "west", "up", "down"};
        float[] areas = {w * h, d * h, w * h, d * h, w * d, w * d};
        for (int i = 0; i < 6; i++)
            face(faces, names[i], areas[i], regions[i][0], regions[i][1], regions[i][2], regions[i][3], su, sv);
        JsonObject element = new JsonObject();
        element.add("from", vector(-(x + w), -(y + h), z));
        element.add("to", vector(-x, -y, z + d));
        element.add("faces", faces);
        JsonArray elements = new JsonArray();
        elements.add(element);
        JsonObject model = new JsonObject();
        model.add("textures", JsonParser.parseString("{\"t\":\"" + texture + "\",\"particle\":\"" + texture + "\"}"));
        model.add("elements", elements);
        model.add("display", JsonParser.parseString(PolymerPonderDialog.IDENTITY_GUI));
        return model;
    }

    private static void face(
            JsonObject faces, String name, float area, float u0, float v0, float u1, float v1, float su, float sv) {
        if (area <= 0) return;
        JsonObject face = new JsonObject();
        face.add("uv", vector4(u0 * su, v0 * sv, u1 * su, v1 * sv));
        face.addProperty("texture", "#t");
        faces.add(name, face);
    }

    private static JsonArray vector(float x, float y, float z) {
        JsonArray array = new JsonArray();
        array.add(x);
        array.add(y);
        array.add(z);
        return array;
    }

    private static JsonArray vector4(float a, float b, float c, float d) {
        JsonArray array = new JsonArray();
        array.add(a);
        array.add(b);
        array.add(c);
        array.add(d);
        return array;
    }

    record Draw(String model, Matrix4f transform) {}

    static List<Draw> draws(
            String variant,
            float wx,
            float wy,
            float wz,
            float yawDeg,
            float pitchDeg,
            float flapAngle,
            float age,
            Pose pose,
            boolean hat,
            Matrix4f after) {
        float[][] p = new float[PARTS.size()][];
        for (int i = 0; i < PARTS.size(); i++) {
            Box b = PARTS.get(i);
            p[i] = new float[] {b.px, b.py, b.pz, b.rx, b.ry, b.rz};
        }
        int body = index("body"),
                tail = index("tail"),
                left = index("left_wing"),
                right = index("right_wing"),
                head = index("head"),
                leftLeg = index("left_leg"),
                rightLeg = index("right_leg");
        if (pose == Pose.FLYING) {
            p[leftLeg][3] += 0.6981317f;
            p[rightLeg][3] += 0.6981317f;
        }
        if (pose == Pose.PARTY) {
            p[leftLeg][5] = -0.34906584f;
            p[rightLeg][5] = 0.34906584f;
        }
        p[head][3] = pitchDeg * ((float) Math.PI / 180);
        p[head][4] = 0;
        if (pose == Pose.PARTY) {
            float c = (float) Math.cos(age), s = (float) Math.sin(age);
            p[head][0] += c;
            p[head][1] += s;
            p[head][3] = 0;
            p[head][4] = 0;
            p[head][5] = (float) Math.sin(age) * 0.4f;
            p[body][0] += c;
            p[body][1] += s;
            p[left][5] = -0.0873f - flapAngle;
            p[left][0] += c;
            p[left][1] += s;
            p[right][5] = 0.0873f + flapAngle;
            p[right][0] += c;
            p[right][1] += s;
            p[tail][0] += c;
            p[tail][1] += s;
        } else {
            float lift = flapAngle * 0.3f;
            for (int part : new int[] {head, tail, body, left, right, leftLeg, rightLeg}) p[part][1] += lift;
            p[left][5] = -0.0873f - flapAngle;
            p[right][5] = 0.0873f + flapAngle;
        }
        Matrix4f flip = new Matrix4f().scaling(-1, -1, 1);
        Matrix4f root = new Matrix4f()
                .translation(wx, wy, wz)
                .rotateY((float) Math.toRadians(yawDeg))
                .mul(after)
                .rotateY((float) Math.PI)
                .mul(flip)
                .translate(0, -1.501f, 0);
        List<Draw> draws = new ArrayList<>();
        for (int i = 0; i < PARTS.size(); i++) {
            Box b = PARTS.get(i);
            Matrix4f local = pose(p[i]);
            if (b.parent != null) local = pose(p[index(b.parent)]).mul(local);
            draws.add(new Draw(
                    model(variant, b.name), new Matrix4f(root).mul(local).mul(flip)));
        }
        if (hat) {
            Matrix4f chain = new Matrix4f(root)
                    .mul(pose(p[head]))
                    .translate(0, -3 / 16f, -1.5f / 16f)
                    .scale(0.25f)
                    .scale(1, -1, -1)
                    .translate(0, -2.25f / 16f, 0)
                    .rotateX((float) Math.toRadians(-8.5f));
            draws.add(new Draw(HAT, chain));
        }
        return draws;
    }

    static Matrix4f pose(float[] p) {
        return new Matrix4f().translation(p[0] / 16, p[1] / 16, p[2] / 16).rotateZYX(p[5], p[4], p[3]);
    }

    private static int index(String name) {
        for (int i = 0; i < PARTS.size(); i++) if (PARTS.get(i).name.equals(name)) return i;
        throw new IllegalArgumentException(name);
    }
}
