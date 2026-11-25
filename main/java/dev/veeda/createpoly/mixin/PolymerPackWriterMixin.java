package dev.veeda.createpoly.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zurrtum.create.infrastructure.polymer.PolymerPackOptimizer;
import eu.pb4.polymer.resourcepack.api.PackResource;
import eu.pb4.polymer.resourcepack.impl.generation.DefaultRPBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = DefaultRPBuilder.class, remap = false)
public abstract class PolymerPackWriterMixin {
    private static final Set<String> DROPPED_NAMESPACES =
            Set.of("flywheel", "vanillin", "ponder", "fabric", "createpoly");
    private static final String TRAIN_HAT_INFO = "assets/minecraft/train_hat_info/";

    @ModifyVariable(method = "writeSingleZip", at = @At("HEAD"), argsOnly = true)
    private static Collection<Map.Entry<String, PackResource>> polymerpatch$trimEntries(
            Collection<Map.Entry<String, PackResource>> entries) {
        List<Map.Entry<String, PackResource>> kept = new ArrayList<>(entries.size());
        for (Map.Entry<String, PackResource> entry : entries) {
            String path = entry.getKey();
            if (path.endsWith("/")) continue;
            if (path.startsWith("assets/")) {
                int slash = path.indexOf('/', 7);
                if (slash < 0 || DROPPED_NAMESPACES.contains(path.substring(7, slash))) continue;
                if (path.startsWith("assets/create/")
                        && (path.endsWith(".nbt") || path.endsWith(".obj") || path.endsWith(".mtl"))) continue;
                if (path.startsWith(TRAIN_HAT_INFO)) continue;
                if (path.startsWith("assets/create/lang/") && path.endsWith(".json") && entry.getValue() != null) {
                    kept.add(new AbstractMap.SimpleImmutableEntry<>(
                            path, polymerpatch$withoutPonderKeys(path, entry.getValue())));
                    continue;
                }
            }
            kept.add(entry);
        }
        return PolymerPackOptimizer.optimize(kept);
    }

    private static PackResource polymerpatch$withoutPonderKeys(String path, PackResource resource) {
        try (InputStream stream = resource.getStream()) {
            JsonElement parsed = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) return resource;
            JsonObject trimmed = new JsonObject();
            // JsonObject keeps insertion order, so the output (and the pack hash) stays deterministic.
            for (Map.Entry<String, JsonElement> key : parsed.getAsJsonObject().entrySet())
                if (!key.getKey().startsWith("create.ponder.")) trimmed.add(key.getKey(), key.getValue());
            return PackResource.of(trimmed.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException error) {
            LoggerFactory.getLogger("createpoly").warn("Keeping untrimmed lang file {}", path, error);
            return resource;
        }
    }
}
