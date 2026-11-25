package com.zurrtum.create.infrastructure.polymer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zurrtum.create.Create;
import com.zurrtum.create.infrastructure.polymer.block.PolymerMultipartBlocks;
import com.zurrtum.create.infrastructure.polymer.effects.PolymerBlockParticles;
import eu.pb4.polymer.core.api.block.BlockMapper;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

public final class PolymerParticleCensus implements ModInitializer {
    @Override
    public void onInitialize() {
        PolymerResourcePackUtils.RESOURCE_PACK_FINISHED_EVENT.register(ignored -> run());
    }

    private static void run() {
        Path game = FabricLoader.getInstance().getGameDir();
        Path packPath = game.resolve("polymer/resource_pack.zip");
        if (!Files.exists(packPath)) packPath = game.resolve("polymer/resource_pack.zip_server.zip");
        Path vanillaPath;
        try (Stream<Path> jars = Files.list(game.resolve("polymer/cached_client_jars"))) {
            vanillaPath =
                    jars.filter(p -> p.toString().endsWith(".jar")).findFirst().orElse(null);
        } catch (IOException failed) {
            vanillaPath = null;
        }
        if (!Files.exists(packPath) || vanillaPath == null) {
            Create.LOGGER.warn("Block census: missing pack {} or vanilla jar {}", packPath, vanillaPath);
            return;
        }
        try (ZipFile pack = new ZipFile(packPath.toFile());
                ZipFile vanilla = new ZipFile(vanillaPath.toFile())) {
            census(pack, vanilla);
        } catch (IOException | RuntimeException failure) {
            Create.LOGGER.warn("Block census failed", failure);
        }
    }

    private static JsonObject read(ZipFile zip, String path) {
        ZipEntry entry = zip.getEntry(path);
        if (entry == null) return null;
        try {
            return JsonParser.parseString(new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException | RuntimeException invalid) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static void census(ZipFile packZip, ZipFile vanillaZip) {
        Map<BlockState, BlockState> proxies;
        try {
            Field field = PolymerBlockParticles.class.getDeclaredField("STATES");
            field.setAccessible(true);
            proxies = (Map<BlockState, BlockState>) field.get(null);
        } catch (ReflectiveOperationException failure) {
            Create.LOGGER.warn("Block census: no proxy table", failure);
            return;
        }
        Map<String, JsonObject> cache = new HashMap<>();
        Function<String, JsonObject> client = path -> cache.computeIfAbsent(path, p -> {
            JsonObject packed = read(packZip, p);
            return packed != null ? packed : read(vanillaZip, p);
        });
        Map<String, JsonObject> sourceCache = new HashMap<>();
        Function<String, JsonObject> source = path -> sourceCache.computeIfAbsent(path, p -> {
            JsonObject own = PolymerBlockProjections.readAssetJson("/" + p);
            return own != null ? own : read(vanillaZip, p);
        });
        BlockMapper mapper = BlockMapper.createDefault();
        PacketContext context = PacketContext.get();
        List<String> particleRows = new ArrayList<>();
        particleRows.add("block,state,client,flag,source_particle,hit_particle,break_proxy,break_particle");
        List<String> crackRows = new ArrayList<>();
        crackRows.add("block,state,client,reason");
        Map<String, int[]> perBlock = new TreeMap<>();
        Map<String, Integer> hitTextures = new TreeMap<>();
        Map<String, Integer> crackReasons = new TreeMap<>();
        int[] totals = new int[6];
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (!id.getNamespace().equals(Create.MOD_ID)) continue;
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                totals[5]++;
                BlockState carrier;
                try {
                    carrier = mapper.toClientSideState(state, context);
                } catch (RuntimeException failed) {
                    particleRows.add(id + "," + quote(state.toString()) + ",ERROR,,,,,");
                    continue;
                }
                String sourceParticle = particle(source, state);
                String hit = carrier.shouldSpawnTerrainParticles() ? particle(client, carrier) : "none";
                BlockState proxy = proxies.get(state);
                String breakParticle = proxy == null ? hit : particle(client, proxy);
                boolean breakWrong = sourceParticle != null && !same(breakParticle, sourceParticle);
                boolean hitWrong = sourceParticle != null && !"none".equals(hit) && !same(hit, sourceParticle);
                boolean hitNone = sourceParticle != null && ("none".equals(hit) || hit == null);
                int[] counts = perBlock.computeIfAbsent(id.getPath(), k -> new int[4]);
                if (breakWrong || hitWrong || hitNone) {
                    if (breakWrong) {
                        totals[0]++;
                        counts[0]++;
                    }
                    if (hitWrong) {
                        totals[1]++;
                        counts[1]++;
                        hitTextures.merge(hit, 1, Integer::sum);
                    }
                    if (hitNone) {
                        totals[2]++;
                        counts[2]++;
                    }
                    particleRows.add(id + "," + quote(state.toString()) + "," + quote(carrier.toString()) + ","
                            + (breakWrong ? "BREAK_WRONG " : "") + (hitWrong ? "HIT_WRONG " : "")
                            + (hitNone ? "HIT_NONE" : "") + "," + sourceParticle + "," + hit + ","
                            + quote(proxy == null ? "" : proxy.toString()) + "," + breakParticle);
                }
                if (state.getRenderShape() == RenderShape.MODEL && hasGeometry(source, state)) {
                    totals[4]++;
                    String reason = carrier.getRenderShape() != RenderShape.MODEL
                            ? "render shape " + carrier.getRenderShape()
                            : hasGeometry(client, carrier) ? null : "client model has no elements";
                    if (reason != null) {
                        totals[3]++;
                        counts[3]++;
                        crackReasons.merge(
                                reason + " ("
                                        + BuiltInRegistries.BLOCK
                                                .getKey(carrier.getBlock())
                                                .getPath() + ")",
                                1,
                                Integer::sum);
                        crackRows.add(id + "," + quote(state.toString()) + "," + quote(carrier.toString()) + ","
                                + quote(reason));
                    }
                }
            }
        }
        Path logs = FabricLoader.getInstance().getGameDir().resolve("logs");
        try {
            Files.createDirectories(logs);
            Files.write(logs.resolve("particles.csv"), particleRows, StandardCharsets.UTF_8);
            Files.write(logs.resolve("cracks.csv"), crackRows, StandardCharsets.UTF_8);
        } catch (IOException failed) {
            Create.LOGGER.warn("Block census: cannot write logs", failed);
        }
        StringBuilder blocks = new StringBuilder();
        perBlock.forEach((name, c) -> {
            if (c[0] + c[1] + c[2] + c[3] == 0) return;
            blocks.append(name)
                    .append(c[0] > 0 ? " break " + c[0] : "")
                    .append(c[1] > 0 ? " hit " + c[1] : "")
                    .append(c[2] > 0 ? " nohit " + c[2] : "")
                    .append(c[3] > 0 ? " nocrack " + c[3] : "")
                    .append("; ");
        });
        Create.LOGGER.info(
                "Block census: {} states; particles: {} wrong break, {} wrong hit, {} no hit; wrong hit textures {}",
                totals[5],
                totals[0],
                totals[1],
                totals[2],
                hitTextures);
        Create.LOGGER.info(
                "Block census: cracks: {} of {} source states that show cracks show none on vanilla; reasons {}",
                totals[3],
                totals[4],
                crackReasons);
        Create.LOGGER.info("Block census per block: {}", blocks);
    }

    private static boolean same(String a, String b) {
        if (a == null || b == null) return a == b;
        return normal(a).equals(normal(b));
    }

    private static String normal(String texture) {
        return texture.contains(":") ? texture : "minecraft:" + texture;
    }

    private static List<JsonObject> applied(Function<String, JsonObject> assets, BlockState state, boolean firstOnly) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        JsonObject blockstate = assets.apply("assets/" + id.getNamespace() + "/blockstates/" + id.getPath() + ".json");
        List<JsonObject> result = new ArrayList<>();
        if (blockstate == null) return result;
        if (blockstate.has("variants")) {
            JsonObject variants = blockstate.getAsJsonObject("variants");
            String key = PolymerBlockProjections.selectMatchingVariant(variants, state);
            if (key != null) result.add(first(variants.get(key)));
        } else if (blockstate.has("multipart")) {
            JsonArray parts = blockstate.getAsJsonArray("multipart");
            for (JsonElement value : parts) {
                JsonObject part = value.getAsJsonObject();
                if (firstOnly) {
                    result.add(first(part.get("apply")));
                    break;
                }
                if (part.has("when") && !PolymerMultipartBlocks.matches(part.getAsJsonObject("when"), state)) continue;
                result.add(first(part.get("apply")));
            }
        }
        result.removeIf(o -> o == null || !o.has("model"));
        return result;
    }

    private static JsonObject first(JsonElement apply) {
        if (apply == null) return null;
        if (apply.isJsonArray())
            return apply.getAsJsonArray().isEmpty()
                    ? null
                    : apply.getAsJsonArray().get(0).getAsJsonObject();
        return apply.getAsJsonObject();
    }

    private static String particle(Function<String, JsonObject> assets, BlockState state) {
        List<JsonObject> models = applied(assets, state, true);
        if (models.isEmpty()) return null;
        Map<String, String> textures = new HashMap<>();
        slots(assets, Identifier.parse(models.getFirst().get("model").getAsString()), textures, 0);
        String particle = textures.get("particle");
        for (int depth = 0; particle != null && particle.startsWith("#") && depth < 32; depth++)
            particle = textures.get(particle.substring(1));
        return particle;
    }

    private static boolean hasGeometry(Function<String, JsonObject> assets, BlockState state) {
        for (JsonObject model : applied(assets, state, false))
            if (elements(assets, Identifier.parse(model.get("model").getAsString()), 0)) return true;
        return false;
    }

    private static boolean elements(Function<String, JsonObject> assets, Identifier model, int depth) {
        if (depth > 32) return false;
        JsonObject json = assets.apply("assets/" + model.getNamespace() + "/models/" + model.getPath() + ".json");
        if (json == null) return false;
        if (json.has("elements")) return !json.getAsJsonArray("elements").isEmpty();
        if (json.has("parent")) {
            String parent = json.get("parent").getAsString();
            if (parent.endsWith("builtin/generated") || parent.endsWith("builtin/entity")) return false;
            return elements(assets, Identifier.parse(parent), depth + 1);
        }
        return false;
    }

    private static void slots(
            Function<String, JsonObject> assets, Identifier model, Map<String, String> textures, int depth) {
        if (depth > 32) return;
        JsonObject json = assets.apply("assets/" + model.getNamespace() + "/models/" + model.getPath() + ".json");
        if (json == null) return;
        if (json.has("textures"))
            for (var entry : json.getAsJsonObject("textures").entrySet())
                if (entry.getValue().isJsonPrimitive())
                    textures.putIfAbsent(entry.getKey(), entry.getValue().getAsString());
        if (json.has("parent"))
            slots(assets, Identifier.parse(json.get("parent").getAsString()), textures, depth + 1);
    }

    private static String quote(String value) {
        return "\"" + value.replace("\"", "'") + "\"";
    }
}
