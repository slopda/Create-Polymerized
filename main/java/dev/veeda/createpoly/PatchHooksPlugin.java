package dev.veeda.createpoly;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class PatchHooksPlugin implements IMixinConfigPlugin {
    private static final String BASELINE_SHA = "84c0d70c5d9c63d5d14a9539a5be9857045d8dd9b96315b1739c100fa1619144";
    private final Map<String, JsonObject> patches = new LinkedHashMap<>();

    @Override
    public void onLoad(String mixinPackage) {
        try {
            var create = FabricLoader.getInstance().getModContainer("create").orElseThrow();
            var paths = create.getOrigin().getPaths();
            if (paths.size() != 1 || !Files.isRegularFile(paths.getFirst()))
                throw new IllegalStateException("This adapter requires the original released Create Fly JAR");
            String hash = HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(paths.getFirst())));
            if (!BASELINE_SHA.equals(hash))
                throw new IllegalStateException(
                        "Unsupported Create Fly artifact: install create-fly-26.2-6.0.9-2.jar, SHA-256 "
                                + BASELINE_SHA);
            try (var input = getClass().getClassLoader().getResourceAsStream("polymerpatch/member-patches.json")) {
                if (input == null) throw new IllegalStateException("Missing adapter member manifest");
                JsonArray entries = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                        .getAsJsonArray();
                entries.forEach(
                        e -> patches.put(e.getAsJsonObject().get("target").getAsString(), e.getAsJsonObject()));
            }
        } catch (Exception error) {
            throw new IllegalStateException("Polymer Patch baseline validation failed", error);
        }
    }

    @Override
    public List<String> getMixins() {
        return patches.values().stream().map(e -> e.get("hook").getAsString()).toList();
    }

    @Override
    public void postApply(String targetName, ClassNode target, String mixinName, IMixinInfo info) {
        if (mixinName.endsWith("NativeProtocolMixin")) {
            return;
        }
        JsonObject spec = patches.get(targetName);
        if (spec == null || !mixinName.endsWith("." + spec.get("hook").getAsString())) return;
        try (var input = getClass()
                .getClassLoader()
                .getResourceAsStream(spec.get("resource").getAsString())) {
            if (input == null) throw new IllegalStateException("Missing member delta: " + targetName);
            ClassNode delta = new ClassNode();
            new ClassReader(input.readAllBytes()).accept(delta, 0);
            for (MethodNode method : delta.methods) {
                target.methods.removeIf(m -> m.name.equals(method.name) && m.desc.equals(method.desc));
                target.methods.add(method);
            }
            for (FieldNode field : delta.fields) {
                target.fields.removeIf(f -> f.name.equals(field.name) && f.desc.equals(field.desc));
                target.fields.add(field);
            }
            for (String itf : delta.interfaces) if (!target.interfaces.contains(itf)) target.interfaces.add(itf);
            if (delta.nestMembers != null) {
                if (target.nestMembers == null) target.nestMembers = new ArrayList<>();
                for (String nest : delta.nestMembers)
                    if (!target.nestMembers.contains(nest)) target.nestMembers.add(nest);
            }
            for (InnerClassNode inner : delta.innerClasses)
                if (target.innerClasses.stream().noneMatch(c -> c.name.equals(inner.name)))
                    target.innerClasses.add(inner);
            NativeMemberRoutes.apply(target, delta);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot apply Create compatibility members to " + targetName, error);
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
