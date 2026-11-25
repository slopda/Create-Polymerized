package com.zurrtum.create.infrastructure.polymer.block;

import com.google.gson.JsonParser;
import com.zurrtum.create.Create;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class PolymerDecorativeCulling {
    public static boolean ENABLED = true;
    private static final float ANCHOR_DEPTH = 4f;
    private static final float PADDING = .125f;
    private static final Map<String, Float> RADII = loadRadii();
    private static final Map<ItemDisplayElement, Envelope> ENVELOPES = new WeakHashMap<>();

    private PolymerDecorativeCulling() {}

    private static Map<String, Float> loadRadii() {
        try (var input =
                PolymerDecorativeCulling.class.getResourceAsStream("/polymerpatch/decorative-model-radii.json")) {
            if (input == null) return Map.of();
            var root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            Map<String, Float> result = new HashMap<>();
            for (var entry : root.getAsJsonObject("radii").entrySet()) {
                float radius = entry.getValue().getAsFloat();
                String key =
                        entry.getKey().replace("create:block/polymer/copycat/", "create:" + PolymerCopycatParts.FOLDER);
                if (Float.isFinite(radius) && radius > 0) result.put(key, radius);
            }
            return Map.copyOf(result);
        } catch (Exception invalidManifest) {
            Create.LOGGER.warn(
                    "[PolymerPatch] Decorative culling disabled: invalid model bounds manifest: {}",
                    invalidManifest.toString());
            return Map.of();
        }
    }

    static final float IN_BLOCK = 1.25f;

    public static void inBlock(DisplayElement display) {
        sized(display, IN_BLOCK, IN_BLOCK);
    }

    public static void sized(DisplayElement display, float width, float height) {
        if (ENABLED) display.setDisplaySize(width, height);
    }

    public static Matrix4f anchored(DisplayElement display, Vec3 offset, Matrix4f transform) {
        display.setOffset(offset);
        return new Matrix4f()
                .translation((float) -offset.x, (float) -offset.y, (float) -offset.z)
                .mul(transform);
    }

    public static synchronized void stationary(ItemDisplayElement display, Identifier mesh) {
        if (!ENABLED || ENVELOPES.containsKey(display)) return;
        Envelope envelope = initialize(display, mesh);
        ENVELOPES.put(display, envelope);
        if (envelope.compensated) {
            display.setTranslation(new Vector3f(display.getTranslation()).add(0, ANCHOR_DEPTH, 0));
        }
    }

    static synchronized void pose(DisplayElement display, Matrix4f rawPose, Identifier mesh) {
        if (!ENABLED || !(display instanceof ItemDisplayElement item)) {
            display.setTransformation(rawPose);
            return;
        }
        Envelope envelope = ENVELOPES.get(item);
        if (envelope == null) {
            item.setTransformation(rawPose);
            envelope = initialize(item, mesh);
            ENVELOPES.put(item, envelope);
            if (!envelope.compensated) return;
        }
        item.setTransformation(
                envelope.compensated
                        ? new Matrix4f().translation(0, ANCHOR_DEPTH, 0).mul(rawPose)
                        : rawPose);
        if (envelope.compensated && !envelope.disabled) update(item, mesh, envelope);
    }

    private static Envelope initialize(ItemDisplayElement display, Identifier mesh) {
        Envelope envelope = new Envelope();
        if (!display.getOffset().equals(Vec3.ZERO)
                || !measure(display, mesh, envelope, false)
                || radius(envelope) > ANCHOR_DEPTH) {
            envelope.disabled = true;
            return envelope;
        }
        envelope.compensated = true;
        display.setOffset(new Vec3(0, -ANCHOR_DEPTH, 0));
        display.setViewRange(display.getViewRange() + .25f);
        bounds(display, envelope);
        return envelope;
    }

    private static void update(ItemDisplayElement display, Identifier mesh, Envelope envelope) {
        if (!measure(display, mesh, envelope, true) || radius(envelope) > ANCHOR_DEPTH) {
            envelope.disabled = true;
            display.setDisplaySize(0, 0);
        } else bounds(display, envelope);
    }

    private static boolean measure(
            ItemDisplayElement display, Identifier mesh, Envelope envelope, boolean compensated) {
        Float meshRadius = mesh == null ? itemRadius(display) : RADII.get(mesh.toString());
        if (meshRadius == null) return false;
        var scale = display.getScale();
        float maxScale = Math.max(Math.abs(scale.x()), Math.max(Math.abs(scale.y()), Math.abs(scale.z())));
        var translation = new Vector3f(display.getTranslation());
        if (compensated) translation.sub(0, ANCHOR_DEPTH, 0);
        float displacement = translation.length();
        if (!Float.isFinite(maxScale) || !Float.isFinite(displacement)) return false;
        envelope.mesh = Math.max(envelope.mesh, meshRadius);
        envelope.scale = Math.max(envelope.scale, maxScale);
        envelope.translation = Math.max(envelope.translation, displacement);
        return true;
    }

    static final float ITEM_RADIUS = 1.5f;

    private static Float itemRadius(ItemDisplayElement display) {
        return display.getItem().isEmpty() ? null : ITEM_RADIUS;
    }

    private static float radius(Envelope envelope) {
        return (float) (Math.ceil((envelope.mesh * envelope.scale + envelope.translation + PADDING) * 4) / 4);
    }

    private static void bounds(ItemDisplayElement display, Envelope envelope) {
        float radius = radius(envelope);
        display.setDisplaySize(2 * radius, ANCHOR_DEPTH + radius);
    }

    private static final class Envelope {
        private float mesh, scale, translation;
        private boolean compensated, disabled;
    }
}
