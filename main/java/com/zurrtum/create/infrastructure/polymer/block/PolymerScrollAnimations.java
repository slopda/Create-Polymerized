package com.zurrtum.create.infrastructure.polymer.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.veeda.createpoly.PatchAssets;
import dev.veeda.createpoly.assets.PackJson;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.function.Function;
import javax.imageio.ImageIO;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;

public final class PolymerScrollAnimations {
    static final int[][] BUCKETS = {
        {1, 8}, {1, 6}, {1, 4}, {1, 3}, {1, 2}, {2, 3}, {1, 1}, {5, 4}, {3, 2}, {2, 1}, {5, 2}, {3, 1},
        {4, 1}, {5, 1}, {6, 1}, {8, 1}, {10, 1}, {12, 1}, {16, 1}, {20, 1}, {24, 1}, {32, 1}, {40, 1}, {48, 1}
    };

    static final int CHAIN_SCALE = 4;
    public static final int BELT_SCALE = 2;
    static final String[] BELT_KINDS = {"flat", "flat_bottom", "diagonal"};

    private PolymerScrollAnimations() {}

    public static int bucket(double pixelsPerTick) {
        int best = 0;
        double target = Math.log(Math.max(1e-6, pixelsPerTick)), bestError = Double.MAX_VALUE;
        for (int i = 0; i < BUCKETS.length; i++) {
            double error = Math.abs(Math.log(BUCKETS[i][0] / (double) BUCKETS[i][1]) - target);
            if (error < bestError) {
                bestError = error;
                best = i;
            }
        }
        return best;
    }

    static Identifier beltModel(String partial, boolean diagonal, float speed, int color) {
        if (color != 0) return null;
        int period = diagonal ? 12 : 16;
        int bucket = bucket(Math.abs(speed) * period / 504.0 * BELT_SCALE);
        return PolymerBlockParts.model(beltModelPath(partial, color, bucket, speed > 0));
    }

    static void registerBeltModels(String[] partials) {
        for (String partial : partials)
            for (int bucket = 0; bucket < BUCKETS.length; bucket++)
                for (boolean forward : new boolean[] {true, false})
                    PolymerBlockParts.model(beltModelPath(partial, 0, bucket, forward));
    }

    public static String beltModelPath(String partial, int color, int bucket, boolean forward) {
        return "polymer/belt_anim/" + partial + "_" + color + "_" + bucket + (forward ? "_f" : "_r");
    }

    private static String beltTexture(String kind, int color, int bucket, boolean forward) {
        return "block/polymer/belt_anim/" + kind + "_" + color + "_" + bucket + (forward ? "_f" : "_r");
    }

    static void addBeltAssets(ResourcePackBuilder builder, String[] partials) {
        for (int color = 0; color < 1; color++)
            for (boolean diagonal : new boolean[] {false, true})
                for (String kind : diagonal ? new String[] {"diagonal"} : new String[] {"flat", "flat_bottom"})
                    for (int bucket = 0; bucket < BUCKETS.length; bucket++)
                        for (boolean forward : new boolean[] {true, false})
                            beltTexture(builder, kind, color, bucket, forward, PolymerScrollAnimations::read);
        for (String partial : partials)
            for (int color = 0; color < 1; color++)
                for (int bucket = 0; bucket < BUCKETS.length; bucket++)
                    for (boolean forward : new boolean[] {true, false})
                        beltModel(builder, partial, color, bucket, forward);
    }

    public static void addBeltVariant(
            ResourcePackBuilder builder, String partial, int color, int bucket, boolean forward, Set<String> written) {
        String kind = partial.startsWith("diagonal") ? "diagonal" : partial.endsWith("bottom") ? "flat_bottom" : "flat";
        if (written.add(beltTexture(kind, color, bucket, forward)))
            beltTexture(builder, kind, color, bucket, forward, path -> {
                try {
                    byte[] data = builder.getDataOrSource(path);
                    return data == null ? null : PngCodec.decode(data);
                } catch (IOException exception) {
                    throw new IllegalStateException("Reading " + path, exception);
                }
            });
        if (written.add(beltModelPath(partial, color, bucket, forward)))
            beltModel(builder, partial, color, bucket, forward);
    }

    private static void beltTexture(
            ResourcePackBuilder builder,
            String kind,
            int color,
            int bucket,
            boolean forward,
            Function<String, BufferedImage> reader) {
        boolean diagonal = kind.equals("diagonal");
        String original = color == 0
                ? "belt" + (diagonal ? "_diagonal" : "") + "_scroll"
                : "belt/" + DyeColor.values()[color - 1].getSerializedName() + (diagonal ? "_diagonal" : "")
                        + "_scroll";
        BufferedImage source = reader.apply("assets/create/textures/block/" + original + ".png");
        if (source == null) throw new IllegalStateException("Missing belt sprite " + original);
        int period = diagonal ? 12 : 16;
        int offset = kind.equals("flat_bottom") ? period / 2 : 0;
        String texture = beltTexture(kind, color, bucket, forward);
        int step = BUCKETS[bucket][0], finePeriod = period * BELT_SCALE;
        int frames = finePeriod / gcd(finePeriod, step);
        int size = source.getWidth(), height = size * BELT_SCALE;
        BufferedImage strip = new BufferedImage(size, height * frames, BufferedImage.TYPE_INT_ARGB);
        for (int k = 0; k < frames; k++) {
            int f = Math.floorMod(offset * BELT_SCALE + (forward ? 1 : -1) * k * step, finePeriod);
            for (int y = 0; y < height; y++)
                for (int x = 0; x < size; x++)
                    strip.setRGB(x, k * height + y, source.getRGB(x, ((y + f) / BELT_SCALE) % source.getHeight()));
        }
        png(builder, "assets/create/textures/" + texture + ".png", strip);
        builder.addStringData(
                "assets/create/textures/" + texture + ".png.mcmeta", animation(BUCKETS[bucket], size, height));
    }

    private static void beltModel(ResourcePackBuilder builder, String partial, int color, int bucket, boolean forward) {
        boolean diagonal = partial.startsWith("diagonal");
        boolean bottom = partial.endsWith("bottom");
        String kind = diagonal ? "diagonal" : bottom ? "flat_bottom" : "flat";
        JsonObject textures = new JsonObject();
        textures.addProperty(bottom ? "1" : "0", "create:" + beltTexture(kind, color, bucket, forward));
        JsonObject model = new JsonObject();
        model.addProperty("parent", "create:block/belt/" + partial);
        model.add("textures", textures);
        builder.addStringData(
                "assets/create/models/block/" + beltModelPath(partial, color, bucket, forward) + ".json",
                model.toString());
    }

    static final int CHAIN_STATIC = 16;
    static volatile boolean chainReady;

    static int chainCode(float rpm, double offsetUv, int staticPhase, boolean ready) {
        if (rpm == 0 || !ready) return staticPhase;
        int offset = Math.floorMod((int) Math.floor((offsetUv - Math.floor(offsetUv)) * 16 + 1e-7), 16);
        return CHAIN_STATIC + bucket(Math.abs((double) rpm) * 16 / 360 * CHAIN_SCALE) * 16 + offset;
    }

    static String chainModelPath(int bucket, int offset, int length) {
        return "polymer/chain_anim/" + bucket + "/" + offset + "/" + length;
    }

    private static String chainTexture(int bucket) {
        return "block/polymer/chain_anim/" + bucket;
    }

    static void addChainAssets(ResourcePackBuilder builder) throws IOException {
        byte[] png = builder.getDataOrSource("assets/minecraft/textures/block/iron_chain.png");
        byte[] template = builder.getDataOrSource("assets/minecraft/models/block/template_chain.json");
        if (png == null || template == null) throw new IOException("Vanilla chain assets unavailable");
        BufferedImage chain = PngCodec.decode(png);
        JsonObject model = PackJson.object(template);
        if (chain.getWidth() != 16 || chain.getHeight() != 16) throw new IOException("Unexpected iron_chain size");
        int finePeriod = 16 * CHAIN_SCALE, height = 2 * finePeriod;
        for (int bucket = 0; bucket < BUCKETS.length; bucket++) {
            int step = BUCKETS[bucket][0];
            int frames = finePeriod / gcd(finePeriod, step);
            BufferedImage strip = new BufferedImage(16, height * frames, BufferedImage.TYPE_INT_ARGB);
            for (int k = 0; k < frames; k++) {
                int phase = Math.floorMod(k * step, finePeriod);
                for (int y = 0; y < height; y++)
                    for (int x = 0; x < 16; x++)
                        strip.setRGB(x, k * height + y, chain.getRGB(x, ((y + phase) % finePeriod) / CHAIN_SCALE));
            }
            String texture = chainTexture(bucket);
            png(builder, "assets/create/textures/" + texture + ".png", strip);
            builder.addStringData(
                    "assets/create/textures/" + texture + ".png.mcmeta", animation(BUCKETS[bucket], 16, height));
            for (int offset = 0; offset < 16; offset++)
                for (int length = 1; length <= 16; length++)
                    builder.addStringData(
                            "assets/create/models/block/" + chainModelPath(bucket, offset, length) + ".json",
                            chainModel(model, texture, offset, length).toString());
        }
        chainReady = true;
    }

    private static JsonObject chainModel(JsonObject template, String texture, int offset, int length) {
        JsonObject definition = template.deepCopy();
        JsonObject textures = new JsonObject();
        textures.addProperty("all", "create:" + texture);
        textures.addProperty("particle", "minecraft:block/iron_chain");
        definition.add("textures", textures);
        JsonArray elements = definition.getAsJsonArray("elements");
        for (int index = 0; index < elements.size(); index++) {
            JsonObject element = elements.get(index).getAsJsonObject();
            element.getAsJsonArray("to").set(1, new JsonPrimitive(length));
            int shift = index == 0 ? 3 : -3;
            for (var face : element.getAsJsonObject("faces").entrySet()) {
                JsonArray uv = face.getValue().getAsJsonObject().getAsJsonArray("uv");
                uv.set(1, new JsonPrimitive(offset / 2.0));
                uv.set(3, new JsonPrimitive((offset + length) / 2.0));
                uv.set(0, new JsonPrimitive(uv.get(0).getAsDouble() + shift));
                uv.set(2, new JsonPrimitive(uv.get(2).getAsDouble() + shift));
            }
        }
        return definition;
    }

    private static String animation(int[] bucket, int width, int height) {
        boolean interpolate = bucket[0] == 1 && bucket[1] > 1;
        return "{\"animation\":{\"frametime\":" + bucket[1] + ",\"interpolate\":" + interpolate + ",\"width\":" + width
                + ",\"height\":" + height + "}}";
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    private static void png(ResourcePackBuilder builder, String path, BufferedImage image) {
        try {
            builder.addData(path, PngCodec.encode(image));
        } catch (IOException exception) {
            throw new IllegalStateException("Writing " + path, exception);
        }
    }

    private static BufferedImage read(String path) {
        try (InputStream stream = PatchAssets.open(path)) {
            return stream == null ? null : ImageIO.read(stream);
        } catch (IOException exception) {
            throw new IllegalStateException("Reading " + path, exception);
        }
    }
}
