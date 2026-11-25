package com.zurrtum.create.infrastructure.polymer.ponder;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;

final class PolymerPonderCarts {
    private PolymerPonderCarts() {}

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
            float px,
            float py,
            float pz,
            float rx,
            float ry,
            float rz) {}

    private static final float HALF_PI = (float) (Math.PI / 2);

    static final List<Box> PARTS = List.of(
            new Box("bottom", 0, 10, -10, -8, -1, 20, 16, 2, 0, 4, 0, HALF_PI, 0, 0),
            new Box("front", 0, 0, -8, -9, -1, 16, 8, 2, -9, 4, 0, 0, 3 * HALF_PI, 0),
            new Box("back", 0, 0, -8, -9, -1, 16, 8, 2, 9, 4, 0, 0, HALF_PI, 0),
            new Box("left", 0, 0, -8, -9, -1, 16, 8, 2, 0, 4, -7, 0, (float) Math.PI, 0),
            new Box("right", 0, 0, -8, -9, -1, 16, 8, 2, 0, 4, 7, 0, 0, 0));

    static final String TEXTURE = "create:block/p/minecart";

    static String model(String part) {
        return PolymerPonderScene.PART_BANK + "minecart/" + part;
    }

    static JsonObject partModel(Box b) {
        return PolymerPonderParrots.boxModel(TEXTURE, b.u, b.v, b.x, b.y, b.z, b.w, b.h, b.d, 64, 32);
    }

    static List<PolymerPonderParrots.Draw> draws(String type, float wx, float wy, float wz, float yawDeg) {
        Matrix4f root = new Matrix4f()
                .translation(wx, wy, wz)
                .rotateY((float) Math.toRadians(yawDeg))
                .translate(0, 0.375f, 0)
                .rotateY((float) Math.PI);
        List<PolymerPonderParrots.Draw> draws = new ArrayList<>();
        String block = switch (type) {
            case "minecraft:furnace_minecart" -> "minecraft:block/furnace";
            default -> null;
        };
        if (block != null)
            draws.add(new PolymerPonderParrots.Draw(
                    block,
                    new Matrix4f(root)
                            .scale(0.75f)
                            .translate(-0.5f, (6 - 8) / 16f, 0.5f)
                            .rotateY(HALF_PI)));
        Matrix4f flip = new Matrix4f().scaling(-1, -1, 1);
        Matrix4f model = new Matrix4f(root).mul(flip);
        for (Box b : PARTS)
            draws.add(new PolymerPonderParrots.Draw(
                    model(b.name),
                    new Matrix4f(model)
                            .mul(PolymerPonderParrots.pose(new float[] {b.px, b.py, b.pz, b.rx, b.ry, b.rz}))
                            .mul(flip)));
        return draws;
    }
}
