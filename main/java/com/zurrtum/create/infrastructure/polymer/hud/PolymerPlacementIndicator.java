package com.zurrtum.create.infrastructure.polymer.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zurrtum.create.Create;
import com.zurrtum.create.catnip.animation.LerpedFloat;
import com.zurrtum.create.catnip.placement.PlacementHelpers;
import com.zurrtum.create.infrastructure.polymer.PolymerReach;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerGuiGlyphs;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import dev.veeda.createpoly.assets.CreateIds;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class PolymerPlacementIndicator {
    private static final int BASE = 0xE000, SPACE = 0xF000;
    private static final int DIRECTIONS = 16, FADE_TICKS = 10, SIZE = 18, CELL_HEIGHT = 34;
    private static final FontDescription FONT = new FontDescription.Resource(CreateIds.id("placement_indicator"));
    private static final Map<UUID, Animation> STATES = new HashMap<>();
    private static volatile int[] advances = new int[0];

    record Frame(int direction, int fade) {}

    private static final class Animation {
        final Object level;
        final LerpedFloat angle = LerpedFloat.angular();
        BlockPos target;
        int age;

        Animation(Object level) {
            this.level = level;
        }
    }

    private PolymerPlacementIndicator() {}

    static void clear(UUID player) {
        STATES.remove(player);
    }

    static void clearAll() {
        STATES.clear();
    }

    static Frame tick(ServerPlayer player) {
        if (advances.length == 0
                || player.isRemoved()
                || !player.isAlive()
                || player.isSpectator()
                || PolymerNativeClients.isNative(player)) {
            clear(player.getUUID());
            return null;
        }
        Animation animation = STATES.get(player.getUUID());
        if (animation != null && animation.level != player.level()) {
            clear(player.getUUID());
            animation = null;
        }
        BlockPos target = target(player);
        if (animation == null) {
            if (target == null) return null;
            animation = new Animation(player.level());
            STATES.put(player.getUUID(), animation);
        }
        animation.age = nextAge(animation.age, target != null);
        if (animation.age == 0) {
            clear(player.getUUID());
            return null;
        }
        if (target != null) animation.target = target;
        double angle = directionAngle(
                player.getEyePosition(),
                player.getViewYRot(1),
                player.getViewXRot(1),
                Vec3.atCenterOf(animation.target),
                animation.angle.getValue());
        if (animation.age < FADE_TICKS) animation.angle.setValue(angle);
        animation.angle.chase(angle, .25f, LerpedFloat.Chaser.EXP);
        animation.angle.tickChaser();
        return new Frame(Math.floorMod(Math.round(animation.angle.getValue(0) / 22.5f), DIRECTIONS), animation.age);
    }

    static BlockPos target(ServerPlayer player) {
        if (player.isShiftKeyDown()) return null;
        BlockHitResult hit = PolymerReach.targetBlock(player);
        if (hit == null) return null;
        BlockPos pos = hit.getBlockPos();
        if (!player.level().isLoaded(pos)) return null;
        var state = player.level().getBlockState(pos);
        for (InteractionHand hand : InteractionHand.values()) {
            var held = player.getItemInHand(hand);
            for (var helper : PlacementHelpers.getHelpersView()) {
                if (!helper.matchesItem(held) || !helper.matchesState(state)) continue;
                var offset = helper.getOffset(player, player.level(), state, pos, hit, held);
                if (offset.isSuccessful() && player.level().isLoaded(offset.getBlockPos())) return offset.getBlockPos();
            }
        }
        return null;
    }

    static int nextAge(int age, boolean matching) {
        return matching ? Math.min(FADE_TICKS, age + 1) : Math.max(0, age - 2);
    }

    static double directionAngle(Vec3 eye, float yaw, float pitch, Vec3 target, double fallback) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        Vec3 right = new Vec3(-Math.cos(y), 0, -Math.sin(y));
        Vec3 forward = new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
        Vec3 up = right.cross(forward);
        Vec3 delta = target.subtract(eye);
        double x = delta.dot(right), vertical = delta.dot(up);
        if (x * x + vertical * vertical < 1e-12) return fallback;
        return (Math.toDegrees(Math.atan2(x, vertical)) + 360) % 360;
    }

    static Component compose(Frame frame) {
        if (frame == null || advances.length == 0) return Component.empty();
        int index = (frame.fade() - 1) * DIRECTIONS + frame.direction();
        if (index < 0 || index >= advances.length)
            throw new IllegalArgumentException("Invalid placement indicator frame");
        float start = -SIZE / 4f;
        String text = spaces(start) + Character.toString(BASE + index) + spaces(-start - advances[index]);
        return Component.literal(text)
                .withStyle(Style.EMPTY.withFont(FONT).withColor(0xFFFFFF).withoutShadow());
    }

    private static String spaces(float delta) {
        return PolymerGuiGlyphs.spaces(SPACE, delta);
    }

    static void addAssets(ResourcePackBuilder builder) {
        try {
            String path = "assets/ponder/textures/gui/placement_indicator.png";
            byte[] source = builder.getDataOrSource(path);
            if (source == null) {
                var file = FabricLoader.getInstance()
                        .getModContainer("create")
                        .orElseThrow()
                        .findPath(path)
                        .orElseThrow();
                source = Files.readAllBytes(file);
            }
            writeAssets(PngCodec.decode(source), builder::addData);
        } catch (IOException | RuntimeException error) {
            advances = new int[0];
            Create.LOGGER.warn("Placement indicator disabled: cannot build its font", error);
        }
    }

    static void writeAssets(BufferedImage source, BiConsumer<String, byte[]> output) throws IOException {
        if (source == null || source.getWidth() != 16 || source.getHeight() != 256)
            throw new IOException("Unexpected Create placement indicator sheet");
        BufferedImage atlas =
                new BufferedImage(SIZE * DIRECTIONS, CELL_HEIGHT * FADE_TICKS, BufferedImage.TYPE_INT_ARGB);
        int[] widths = new int[DIRECTIONS * FADE_TICKS];
        JsonArray chars = new JsonArray();
        for (int fade = 1; fade <= FADE_TICKS; fade++) {
            StringBuilder row = new StringBuilder();
            float alpha = fade * fade / 100f;
            for (int frame = 0; frame < DIRECTIONS; frame++) {
                int index = (fade - 1) * DIRECTIONS + frame, width = 0;
                row.appendCodePoint(BASE + index);
                for (int y = 0; y < SIZE; y++)
                    for (int x = 0; x < SIZE; x++) {
                        int pixel =
                                source.getRGB((int) ((x + .5) * 16 / SIZE), frame * 16 + (int) ((y + .5) * 16 / SIZE));
                        int a = Math.round((pixel >>> 24) * alpha);
                        atlas.setRGB(
                                frame * SIZE + x, (fade - 1) * CELL_HEIGHT + y + 1, (a << 24) | (pixel & 0xFFFFFF));
                        if (a != 0) width = Math.max(width, x + 1);
                    }
                widths[index] = (int) (.5f + width * .5f) + 1;
            }
            chars.add(row.toString());
        }
        JsonObject bitmap = PolymerGuiGlyphs.bitmapProvider(
                "create:font/placement_indicator.png", CELL_HEIGHT / 2, CELL_HEIGHT / 2);
        bitmap.add("chars", chars);
        JsonObject spaceProvider = PolymerGuiGlyphs.spaceProvider(SPACE);
        JsonArray providers = new JsonArray();
        providers.add(bitmap);
        providers.add(spaceProvider);
        JsonObject font = new JsonObject();
        font.add("providers", providers);
        output.accept("assets/create/textures/font/placement_indicator.png", PngCodec.encode(atlas));
        output.accept(
                "assets/create/font/placement_indicator.json", font.toString().getBytes(StandardCharsets.UTF_8));
        advances = widths;
    }
}
