package com.zurrtum.create.infrastructure.polymer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.veeda.createpoly.assets.PackJson;
import eu.pb4.polymer.resourcepack.api.PackResource;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PolymerPonderSmoothTrial implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("createpoly-dev");
    private static final String SPIN_BANK = "assets/create/textures/item/p/q/";

    @Override
    public void onInitialize() {
        Properties settings = settings();
        if (Boolean.parseBoolean(settings.getProperty("shaderSpike", "false"))) shaderSpike();
        if (!Boolean.parseBoolean(settings.getProperty("spinInterpolate", "false"))) return;
        AtomicInteger changed = new AtomicInteger();
        PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(builder ->
                builder.addResourceConverter((path, resource) -> {
                    if (!path.startsWith(SPIN_BANK) || !path.endsWith(".png.mcmeta")) return resource;
                    JsonObject meta =
                            JsonParser.parseString(resource.asString()).getAsJsonObject();
                    JsonObject animation = meta.getAsJsonObject("animation");
                    animation.addProperty("interpolate", true);
                    JsonArray frames = animation.getAsJsonArray("frames");
                    int slot = Integer.MAX_VALUE;
                    for (var frame : frames)
                        slot = Math.min(
                                slot, frame.getAsJsonObject().get("time").getAsInt());
                    JsonArray split = new JsonArray();
                    for (var element : frames) {
                        JsonObject frame = element.getAsJsonObject();
                        int time = frame.get("time").getAsInt(),
                                index = frame.get("index").getAsInt();
                        if (time > slot) {
                            split.add(PackJson.frame(index, time - slot));
                            split.add(PackJson.frame(index, slot));
                        } else split.add(frame);
                    }
                    animation.add("frames", split);
                    if (changed.incrementAndGet() == 1) LOGGER.info("Ponder trial: interpolating spin-bank sprites");
                    return PackResource.fromJson(meta);
                }));
        LOGGER.info("Ponder trial spinInterpolate enabled");
    }

    private static void shaderSpike() {
        PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(builder -> {
            builder.addStringData(
                    "assets/createpoly_dev/items/shader_spike.json",
                    "{\"oversized_in_gui\":true,\"model\":{\"type\":\"minecraft:composite\",\"models\":["
                            + "{\"type\":\"minecraft:model\",\"model\":\"minecraft:block/grass_block\","
                            + "\"tints\":[{\"type\":\"minecraft:constant\",\"value\":" + 0x010280 + "}]},"
                            + "{\"type\":\"minecraft:model\",\"model\":\"minecraft:block/prismarine\"}]}}");
            builder.addStringData("assets/minecraft/shaders/core/item.vsh", SPIKE_ITEM_VSH);
        });
        LOGGER.info("Ponder trial shaderSpike enabled");
    }

    private static final String SPIKE_ITEM_VSH = """
            #version 330

            #moj_import <minecraft:light.glsl>
            #moj_import <minecraft:fog.glsl>
            #moj_import <minecraft:dynamictransforms.glsl>
            #moj_import <minecraft:projection.glsl>
            #moj_import <minecraft:sample_lightmap.glsl>
            #moj_import <minecraft:globals.glsl>

            in vec3 Position;
            in vec4 Color;
            in vec2 UV0;
            in ivec2 UV1;
            in ivec2 UV2;
            in vec3 Normal;

            uniform sampler2D Sampler1;
            uniform sampler2D Sampler2;

            out float sphericalVertexDistance;
            out float cylindricalVertexDistance;
            out vec4 vertexColor;
            out vec4 lightMapColor;
            out vec4 overlayColor;

            out vec2 texCoord0;

            void main() {
                vec3 position = Position;
                vec4 color = Color;
                ivec3 code = ivec3(round(Color.rgb * 255.0));
                if (code.r == 1 && code.g == 2) {
                    float ticks = GameTime * 24000.0;
                    position.x += sin(ticks * 0.3) * 6.0;
                    color = vec4(1.0);
                }
                gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);

                sphericalVertexDistance = fog_spherical_distance(position);
                cylindricalVertexDistance = fog_cylindrical_distance(position);

                vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, color);
                lightMapColor = sample_lightmap(Sampler2, UV2);
                overlayColor = texelFetch(Sampler1, UV1, 0);

                texCoord0 = UV0;
            }
            """;

    private static Properties settings() {
        Properties settings = new Properties();
        Path file = FabricLoader.getInstance().getConfigDir().resolve("createpoly-dev-ponder.properties");
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                settings.load(reader);
            } catch (IOException failure) {
                LOGGER.warn("Could not read {}", file, failure);
            }
        }
        return settings;
    }
}
