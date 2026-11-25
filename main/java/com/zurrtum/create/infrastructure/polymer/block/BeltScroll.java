package com.zurrtum.create.infrastructure.polymer.block;

import com.google.gson.JsonObject;
import com.zurrtum.create.Create;
import dev.veeda.createpoly.PatchAssets;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

final class BeltScroll {
    private static final String[] PARTIALS = {
        "start",
        "middle",
        "end",
        "start_bottom",
        "middle_bottom",
        "end_bottom",
        "diagonal_start",
        "diagonal_middle",
        "diagonal_end"
    };
    private static final Map<String, Identifier[][]> VARIANTS = new HashMap<>();

    static {
        for (String partial : PARTIALS) {
            int period = partial.startsWith("diagonal") ? 12 : 16;
            Identifier[][] variants = new Identifier[17][period];
            for (int c = 0; c < 17; c++)
                for (int f = 0; f < period; f++)
                    variants[c][f] = PolymerBlockParts.model("polymer/belt_frame/" + partial + "_" + c + "_" + f);
            VARIANTS.put(partial, variants);
        }
        PolymerScrollAnimations.registerBeltModels(PARTIALS);
    }

    private BeltScroll() {}

    static void init() {}

    static Identifier model(Identifier partial, boolean diagonal, float speed, long time, @Nullable DyeColor color) {
        if (speed == 0 && color == null) return partial;
        String name = partial.getPath().substring(partial.getPath().lastIndexOf('/') + 1);
        Identifier[][] variants = VARIANTS.get(name);
        if (variants == null) return partial;
        if (speed != 0) {
            Identifier animated =
                    PolymerScrollAnimations.beltModel(name, diagonal, speed, color == null ? 0 : color.ordinal() + 1);
            if (animated != null) return animated;
        }
        double scroll = (double) speed * time / 504 + (name.endsWith("bottom") ? .5 : 0);
        int frame = (int) Math.floor((scroll - Math.floor(scroll)) * variants[0].length);
        return variants[color == null ? 0 : color.ordinal() + 1][frame];
    }

    static void addAssets(ResourcePackBuilder builder) {
        for (int c = 0; c < 17; c++)
            for (boolean diagonal : new boolean[] {false, true}) {
                String original = c == 0
                        ? "belt" + (diagonal ? "_diagonal" : "") + "_scroll"
                        : "belt/" + DyeColor.values()[c - 1].getSerializedName() + (diagonal ? "_diagonal" : "")
                                + "_scroll";
                BufferedImage source = read("assets/create/textures/block/" + original + ".png");
                if (source == null) throw new IllegalStateException("Missing belt sprite " + original);
                int period = diagonal ? 12 : 16;
                int size = source.getWidth();
                for (int f = 0; f < period; f++) {
                    BufferedImage frame = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < size; y++)
                        for (int x = 0; x < size; x++)
                            frame.setRGB(x, y, source.getRGB(x, (y + f) % source.getHeight()));
                    try {
                        builder.addData(
                                "assets/create/textures/" + texture(c, diagonal, f) + ".png", PngCodec.encode(frame));
                    } catch (IOException e) {
                        throw new IllegalStateException("Writing belt frame", e);
                    }
                }
            }
        for (String partial : PARTIALS) {
            boolean diagonal = partial.startsWith("diagonal");
            String key = partial.endsWith("bottom") ? "1" : "0";
            for (int c = 0; c < 17; c++)
                for (int f = 0; f < (diagonal ? 12 : 16); f++) {
                    JsonObject textures = new JsonObject();
                    textures.addProperty(key, "create:" + texture(c, diagonal, f));
                    JsonObject model = new JsonObject();
                    model.addProperty("parent", "create:block/belt/" + partial);
                    model.add("textures", textures);
                    builder.addStringData(
                            "assets/create/models/" + VARIANTS.get(partial)[c][f].getPath() + ".json",
                            model.toString());
                }
        }
        PolymerScrollAnimations.addBeltAssets(builder, PARTIALS);
        try {
            PolymerScrollAnimations.addChainAssets(builder);
        } catch (IOException | RuntimeException exception) {
            Create.LOGGER.warn("Animated chain scroll unavailable; using server phases", exception);
        }
    }

    private static String texture(int color, boolean diagonal, int frame) {
        return "block/polymer/belt_frame/" + (diagonal ? "diagonal_" : "flat_") + color + "_" + frame;
    }

    private static BufferedImage read(String path) {
        try (InputStream stream = PatchAssets.open(path)) {
            return stream == null ? null : ImageIO.read(stream);
        } catch (IOException e) {
            throw new IllegalStateException("Reading " + path, e);
        }
    }
}
