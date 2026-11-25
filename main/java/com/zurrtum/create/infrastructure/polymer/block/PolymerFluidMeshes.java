package com.zurrtum.create.infrastructure.polymer.block;

import static dev.veeda.createpoly.assets.PackJson.array;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zurrtum.create.infrastructure.polymer.PolymerBlockProjections;
import dev.veeda.createpoly.assets.CreateIds;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class PolymerFluidMeshes {
    private static final float EPS = .0005f;
    private static final int MASK_SIZE = 64;

    record Patch(Identifier model, Matrix4f pose) {}

    static void apply(ItemDisplayElement display, Patch patch) {
        Matrix4f pose = patch.pose();
        Vector3f x = pose.getColumn(0, new Vector3f()), z = pose.getColumn(2, new Vector3f());
        double a = x.dot(x), b = x.dot(z), c = z.dot(z);
        double angle = .5 * Math.atan2(2 * b, a - c);
        float co = (float) Math.cos(angle), si = (float) Math.sin(angle);
        double discriminant = Math.sqrt((a - c) * (a - c) + 4 * b * b);
        float sx = (float) Math.sqrt((a + c + discriminant) / 2);
        float sz = (float) Math.sqrt((a + c - discriminant) / 2);
        Vector3f u = new Vector3f(x).mul(co).fma(si, z).div(sx);
        Vector3f v = new Vector3f(x).mul(-si).fma(co, z).div(sz);
        Vector3f normal = new Vector3f(v).cross(u).normalize();
        Quaternionf left = new Matrix3f()
                .setColumn(0, u)
                .setColumn(1, normal)
                .setColumn(2, v)
                .getNormalizedRotation(new Quaternionf());
        Vector3f translation = pose.getTranslation(new Vector3f())
                .fma(.5f, x)
                .fma(.5f, normal)
                .fma(.5f, z)
                .sub(.5f, .5f, .5f);
        display.setTranslation(translation);
        display.setLeftRotation(left);
        display.setScale(new Vector3f(sx, 1, sz));
        display.setRightRotation(new Quaternionf().rotationY((float) angle + (float) Math.PI));
    }

    static void registerModels() {
        for (String fluid : new String[] {"honey", "chocolate"})
            for (String material : materials())
                for (String shape : new String[] {"square", "a", "b"})
                    PolymerBlockProjections.DISPLAY_BLOCK_MODELS.add(model(fluid, material, shape));
    }

    private static List<String> materials() {
        List<String> out = new ArrayList<>(List.of("still", "flow"));
        for (int i = 0; i < 16; i++) out.add("top_flow_" + i);
        return out;
    }

    private static Identifier model(String fluid, String material, String shape) {
        return CreateIds.id("block/polymer/world_fluid/" + fluid + "_" + material + "_" + shape);
    }

    private static boolean visible(PolymerFluidVisuals.Surface surface, Direction side) {
        return (surface.mask() & (1 << side.ordinal())) != 0;
    }

    static List<Patch> patches(PolymerFluidVisuals.Surface s) {
        List<Patch> out = new ArrayList<>();
        float nw = s.nw() - EPS, ne = s.ne() - EPS, sw = s.sw() - EPS, se = s.se() - EPS;
        if (visible(s, Direction.UP)) {
            String material = s.flow() < 0 ? "still" : "top_flow_" + s.flow();
            if (Math.abs(nw + se - ne - sw) < .000001f)
                out.add(new Patch(model(s.fluid(), material, "square"), top(ne - nw, sw - nw, nw)));
            else {
                out.add(new Patch(model(s.fluid(), material, "a"), top(ne - nw, se - ne, nw)));
                out.add(new Patch(model(s.fluid(), material, "b"), top(se - sw, sw - nw, nw)));
            }
        }
        float bottom = visible(s, Direction.DOWN) ? EPS : 0;
        if (visible(s, Direction.DOWN))
            out.add(new Patch(model(s.fluid(), "still", "square"), new Matrix4f().translate(0, bottom, 0)));
        if (visible(s, Direction.NORTH))
            side(
                    out,
                    s.fluid(),
                    new Vector3f(0, bottom, EPS),
                    new Vector3f(1, 0, 0),
                    new Vector3f(0, 0, -1),
                    nw,
                    ne,
                    bottom);
        if (visible(s, Direction.SOUTH))
            side(
                    out,
                    s.fluid(),
                    new Vector3f(1, bottom, 1 - EPS),
                    new Vector3f(-1, 0, 0),
                    new Vector3f(0, 0, 1),
                    se,
                    sw,
                    bottom);
        if (visible(s, Direction.EAST))
            side(
                    out,
                    s.fluid(),
                    new Vector3f(1 - EPS, bottom, 0),
                    new Vector3f(0, 0, 1),
                    new Vector3f(1, 0, 0),
                    ne,
                    se,
                    bottom);
        if (visible(s, Direction.WEST))
            side(
                    out,
                    s.fluid(),
                    new Vector3f(EPS, bottom, 1),
                    new Vector3f(0, 0, -1),
                    new Vector3f(-1, 0, 0),
                    sw,
                    nw,
                    bottom);
        return out;
    }

    private static Matrix4f top(float xSlope, float zSlope, float height) {
        return new Matrix4f().m01(xSlope).m21(zSlope).m31(height);
    }

    private static Matrix4f sidePose(Vector3f origin, Vector3f along, Vector3f normal, float xSlope, float height) {
        return new Matrix4f()
                .m00(along.x)
                .m01(xSlope)
                .m02(along.z)
                .m10(normal.x)
                .m11(0)
                .m12(normal.z)
                .m20(0)
                .m21(height)
                .m22(0)
                .m30(origin.x)
                .m31(origin.y)
                .m32(origin.z);
    }

    private static void side(
            List<Patch> out,
            String fluid,
            Vector3f origin,
            Vector3f along,
            Vector3f normal,
            float left,
            float right,
            float bottom) {
        if (Math.abs(left - right) < .000001f)
            out.add(new Patch(model(fluid, "flow", "square"), sidePose(origin, along, normal, 0, left - bottom)));
        else {
            out.add(new Patch(model(fluid, "flow", "a"), sidePose(origin, along, normal, 0, right - bottom)));
            out.add(new Patch(model(fluid, "flow", "b"), sidePose(origin, along, normal, right - left, left - bottom)));
        }
    }

    static void addAssets(ResourcePackBuilder builder) {
        for (String fluid : new String[] {"honey", "chocolate"})
            for (String material : materials())
                for (String shape : new String[] {"square", "a", "b"}) {
                    Identifier model = model(fluid, material, shape);
                    String texture = "create:block/" + fluid + "_" + material;
                    if (!material.equals("still") || !shape.equals("square")) {
                        texture = "create:block/polymer/world_fluid/" + fluid + "_" + material + "_" + shape;
                        addMask(builder, fluid, material, shape, texture);
                    }
                    JsonObject definition = new JsonObject(), textures = new JsonObject();
                    textures.addProperty("fluid", texture);
                    textures.addProperty("particle", "create:block/" + fluid + "_still");
                    definition.add("textures", textures);
                    definition.addProperty("ambientocclusion", false);
                    JsonObject element = new JsonObject(), faces = new JsonObject();
                    element.add("from", array(0, 0, 0));
                    element.add("to", array(16, 0, 16));
                    element.addProperty("shade", false);
                    JsonObject front = new JsonObject();
                    front.addProperty("texture", "#fluid");
                    front.add("uv", array(0, 0, 16, 16));
                    JsonObject back = new JsonObject();
                    back.addProperty("texture", "#fluid");
                    back.add("uv", array(0, 16, 16, 0));
                    faces.add("up", front);
                    faces.add("down", back);
                    element.add("faces", faces);
                    JsonArray elements = new JsonArray();
                    elements.add(element);
                    definition.add("elements", elements);
                    builder.addStringData("assets/create/models/" + model.getPath() + ".json", definition.toString());
                }
    }

    private static void addMask(
            ResourcePackBuilder builder, String fluid, String material, String shape, String texture) {
        boolean flowTop = material.startsWith("top_flow_");
        String source = "assets/create/textures/block/" + fluid + "_" + (flowTop ? "flow" : material) + ".png";
        try {
            byte[] original = builder.getDataOrSource(source);
            if (original == null) throw new IOException("Missing original fluid texture " + source);
            BufferedImage input = PngCodec.decode(original);
            int width = input.getWidth(), frames = input.getHeight() / width;
            BufferedImage output = new BufferedImage(MASK_SIZE, MASK_SIZE * frames, BufferedImage.TYPE_INT_ARGB);
            for (int frame = 0; frame < frames; frame++)
                for (int y = 0; y < MASK_SIZE; y++)
                    for (int x = 0; x < MASK_SIZE; x++) {
                        boolean keep = shape.equals("square") || (shape.equals("a") ? x >= y : x < y);
                        if (!keep) continue;
                        int sx = material.equals("still") ? x * width / MASK_SIZE : x * width / (2 * MASK_SIZE);
                        int sy = material.equals("still")
                                ? y * width / MASK_SIZE
                                : (MASK_SIZE - 1 - y) * width / (2 * MASK_SIZE);
                        if (flowTop) {
                            double angle = Integer.parseInt(material.substring("top_flow_".length())) * Math.PI / 8;
                            double px = (x + .5) / MASK_SIZE - .5, pz = (y + .5) / MASK_SIZE - .5;
                            sx = (int) ((.5 + (px * Math.cos(angle) + pz * Math.sin(angle)) * .5) * width);
                            sy = (int) ((.5 + (pz * Math.cos(angle) - px * Math.sin(angle)) * .5) * width);
                        }
                        output.setRGB(x, frame * MASK_SIZE + y, input.getRGB(sx, frame * width + sy));
                    }
            byte[] encoded = PngCodec.encode(output);
            String target = "assets/create/textures/" + texture.substring("create:".length()) + ".png";
            builder.addData(target, encoded);
            byte[] animation = builder.getDataOrSource(source + ".mcmeta");
            if (animation != null) builder.addData(target + ".mcmeta", animation);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot generate fluid triangle material", error);
        }
    }
}
