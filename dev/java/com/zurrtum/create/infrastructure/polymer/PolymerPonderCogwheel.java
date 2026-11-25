package com.zurrtum.create.infrastructure.polymer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerArtKit;
import dev.veeda.createpoly.assets.PackAssetId;
import dev.veeda.createpoly.assets.PackAssetWriter;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

final class PolymerPonderCogwheel {
    static final int FRAMES = 75;
    private static final int DEMO_RPM = 8;
    private static final String MODEL = "create:item/ui/ponder/cogwheel";

    private PolymerPonderCogwheel() {}

    static ItemStack stack(PolymerPonderPreview.Playback playback) {
        return PolymerArtKit.stack(
                "ponder_cogwheel_foreground",
                List.of(
                        Integer.toString(playback.frame()),
                        Integer.toString(playback.view),
                        Integer.toString(playback.chapter()),
                        playback.playing ? "play" : "pause"));
    }

    static void addAssets(ResourcePackBuilder builder) {
        try {
            var writer = new PackAssetWriter(builder);
            JsonObject mesh = new JsonObject();
            mesh.addProperty("parent", "create:block/polymer/cogwheel");
            mesh.add(
                    "display",
                    JsonParser.parseString(
                            "{\"gui\":{\"rotation\":[0,0,0],\"translation\":[0,0,0],\"scale\":[1,1,1]}}"));
            writer.model(new PackAssetId("create", "item/ui/ponder/cogwheel"), mesh);

            var glyphs = PolymerArtKit.asciiGlyphs(builder);
            var art = new PolymerArtKit(builder, "ponder", 16, 26);
            BufferedImage panel = new BufferedImage(176, 126, BufferedImage.TYPE_INT_ARGB);
            var paint = panel.createGraphics();
            paint.setColor(new Color(0x242934));
            paint.fillRect(0, 0, 176, 126);
            paint.setColor(new Color(0x67717E));
            paint.drawRect(0, 0, 175, 125);
            paint.setColor(new Color(0x111722));
            paint.fillRect(7, 30, 162, 52);
            paint.dispose();
            PolymerArtKit.text(panel, glyphs, 8, 6, "Cogwheels", 0xF4E4BA, true);
            PolymerArtKit.text(panel, glyphs, 8, 17, "Relaying rotational force", 0xB7C7D9, false);
            art.panel("panel", panel, 0, 0, 0);

            var captions = new LinkedHashMap<String, BufferedImage>();
            captions.put("0", caption(glyphs, "Relaying rotational force", "through Cogwheels."));
            captions.put("1", caption(glyphs, "Cogwheels will relay rotation", "to other adjacent cogwheels."));
            captions.put("2", caption(glyphs, "Neighbouring shafts rotate", "in opposite directions."));
            art.layer(2, captions, 8, 85, .02f);

            BufferedImage widgets = PolymerArtKit.image(builder, "assets/create/textures/gui/widgets.png");
            BufferedImage ponder = PolymerArtKit.image(builder, "assets/ponder/textures/gui/widgets.png");
            for (int i : new int[] {0, 1, 3, 4, 5, 6, 8}) {
                BufferedImage button = button(widgets);
                var g = button.createGraphics();
                int icon = switch (i) {
                    case 0 -> 0;
                    case 3 -> 4;
                    case 6 -> 2;
                    case 8 -> 1;
                    default -> -1;
                };
                if (icon >= 0) g.drawImage(ponder.getSubimage(icon * 16, 32, 16, 16), 1, 1, null);
                g.dispose();
                if (icon < 0)
                    PolymerArtKit.text(
                            button, glyphs, i == 4 ? 6 : 5, 5, i == 1 ? "<" : i == 5 ? ">" : "R", 0xFFFFFF, true);
                art.panel("control_" + i, button, 8 + i * 18, 108, .03f);
            }
            var play = new LinkedHashMap<String, BufferedImage>();
            for (String state : List.of("play", "pause")) {
                BufferedImage button = button(widgets);
                PolymerArtKit.text(button, glyphs, 5, 5, state.equals("play") ? "||" : ">", 0xFFFFFF, true);
                play.put(state, button);
            }
            art.layer(3, play, 44, 108, .03f);
            art.models.add(scene());
            art.finish("ponder_cogwheel_foreground");
            PolymerArtKit.empty(builder, "ponder_cogwheel_empty");
        } catch (IOException error) {
            throw new IllegalStateException("Could not generate cogwheel Ponder assets", error);
        }
    }

    private static BufferedImage button(BufferedImage widgets) {
        BufferedImage image = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.drawImage(widgets.getSubimage(0, 0, 18, 18), 0, 0, null);
        g.dispose();
        return image;
    }

    private static BufferedImage caption(Map<Integer, BufferedImage> glyphs, String first, String second) {
        BufferedImage image = new BufferedImage(160, 20, BufferedImage.TYPE_INT_ARGB);
        PolymerArtKit.text(image, glyphs, 0, 0, first, 0xFFFFFF, true);
        PolymerArtKit.text(image, glyphs, 0, 10, second, 0xFFFFFF, true);
        return image;
    }

    static JsonObject scene() {
        JsonArray frames = new JsonArray();
        for (int frame = 0; frame < FRAMES; frame++) {
            float angle = (float) Math.toRadians(frame * 2 * DEMO_RPM * .3);
            JsonArray cogs = new JsonArray();
            cogs.add(cog(-.5f, angle + (float) Math.toRadians(22.5)));
            JsonObject output = select(2, new JsonArray(), cog(.5f, -angle));
            JsonObject hidden = new JsonObject();
            hidden.addProperty("type", "minecraft:empty");
            output.getAsJsonArray("cases").add(choice("0", hidden));
            cogs.add(output);
            JsonObject composite = new JsonObject();
            composite.addProperty("type", "minecraft:composite");
            composite.add("models", cogs);
            frames.add(choice(Integer.toString(frame), composite));
        }
        JsonObject animation = select(0, frames, frames.get(0).getAsJsonObject().getAsJsonObject("model"));
        JsonArray views = new JsonArray();
        for (int view = 0; view < 4; view++) {
            JsonObject posed = animation.deepCopy();
            posed.add(
                    "transformation",
                    matrix(new Matrix4f()
                            .translation((88 - 16) / 16f, (26 - 56) / 16f, .15f)
                            .scale(2f)
                            .rotateX((float) Math.toRadians(35))
                            .rotateY((float) Math.toRadians(25 + view * 90))));
            views.add(choice(Integer.toString(view), posed));
        }
        return select(1, views, views.get(0).getAsJsonObject().getAsJsonObject("model"));
    }

    private static JsonObject cog(float x, float angle) {
        JsonObject result = new JsonObject();
        result.addProperty("type", "minecraft:model");
        result.addProperty("model", MODEL);
        result.add("transformation", matrix(new Matrix4f().translation(x, 0, 0).rotateY(angle)));
        return result;
    }

    private static JsonArray matrix(Matrix4f value) {
        JsonArray result = new JsonArray();
        for (float f : new Matrix4f(value).transpose().get(new float[16])) result.add(f);
        return result;
    }

    private static JsonObject choice(String value, JsonObject model) {
        JsonObject result = new JsonObject();
        result.addProperty("when", value);
        result.add("model", model);
        return result;
    }

    private static JsonObject select(int index, JsonArray cases, JsonObject fallback) {
        JsonObject result = new JsonObject();
        result.addProperty("type", "minecraft:select");
        result.addProperty("property", "minecraft:custom_model_data");
        result.addProperty("index", index);
        result.add("cases", cases);
        result.add("fallback", fallback);
        return result;
    }
}
