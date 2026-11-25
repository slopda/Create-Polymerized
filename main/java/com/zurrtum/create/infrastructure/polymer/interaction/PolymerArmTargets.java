package com.zurrtum.create.infrastructure.polymer.interaction;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.zurrtum.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.zurrtum.create.content.kinetics.mechanicalArm.ArmInteractionPoint.Mode;
import com.zurrtum.create.infrastructure.packet.c2s.ArmPlacementPacket;
import com.zurrtum.create.infrastructure.polymer.AdapterHolder;
import com.zurrtum.create.infrastructure.polymer.AdapterInteractions;
import com.zurrtum.create.infrastructure.polymer.PolymerPartItems;
import com.zurrtum.create.infrastructure.polymer.PolymerReach;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts;
import com.zurrtum.create.infrastructure.polymer.block.PolymerKineticVisuals;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import dev.veeda.createpoly.assets.CreateIds;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Brightness;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

public final class PolymerArmTargets {
    private static final Identifier OUTLINE_MODEL = CreateIds.id("block/polymer/arm_outline");
    private static final Identifier OUTLINE_ITEM = CreateIds.id("polymer_part/block/polymer/arm_outline");
    private static final Identifier OUTLINE_TEXTURE = CreateIds.id("block/polymer/arm_outline");
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private static final class Session {
        final UUID owner;
        final List<ArmInteractionPoint> selection = new ArrayList<>();

        @Nullable
        ItemStack currentItem;

        long lastArm = Long.MIN_VALUE;
        final Map<BlockPos, Outline> outlines = new LinkedHashMap<>();

        Session(UUID owner) {
            this.owner = owner;
        }

        void clearOutlines() {
            outlines.values().forEach(Outline::destroy);
            outlines.clear();
        }
    }

    private PolymerArmTargets() {}

    public static void register() {
        AdapterInteractions.useBlock((player, level, hand, hit) -> {
            if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel)) return InteractionResult.PASS;
            return rightClickingBlocksSelectsThem(sp, hit) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
        AdapterInteractions.attackBlock((player, level, hand, pos, direction) -> {
            if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel)) return InteractionResult.PASS;
            return leftClickingBlocksDeselectsThem(sp, pos) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : NativePresentationPolicy.presentationPlayers(server.getPlayerList()))
                tick(player);
            SESSIONS.entrySet().removeIf(entry -> {
                if (NativePresentationPolicy.presentationPlayer(server.getPlayerList(), entry.getKey()) != null)
                    return false;
                entry.getValue().clearOutlines();
                return true;
            });
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Session session = SESSIONS.remove(handler.player.getUUID());
            if (session != null) session.clearOutlines();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SESSIONS.values().forEach(Session::clearOutlines);
            SESSIONS.clear();
        });
    }

    private static Session session(ServerPlayer player) {
        return SESSIONS.computeIfAbsent(player.getUUID(), Session::new);
    }

    private static boolean rightClickingBlocksSelectsThem(ServerPlayer player, BlockHitResult hit) {
        if (player.isSpectator()) return false;
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.currentItem == null || session.currentItem != player.getMainHandItem())
            return false;
        ServerLevel level = player.level();
        BlockPos pos = hit.getBlockPos();
        if (!level.isLoaded(pos) || !player.isWithinBlockInteractionRange(pos, 1)) return false;
        BlockState state = level.getBlockState(pos);
        ArmInteractionPoint selected = selected(session, pos);
        if (selected == null) {
            ArmInteractionPoint point = ArmInteractionPoint.create(level, pos, state);
            if (point == null) return false;
            selected = point;
            session.selection.add(point);
        }
        selected.cycleMode();
        Mode mode = selected.getMode();
        player.sendOverlayMessage(Component.translatable(
                        mode.getTranslationKey(), state.getBlock().getName().withStyle(ChatFormatting.WHITE))
                .withColor(mode.getColor()));
        return true;
    }

    private static boolean leftClickingBlocksDeselectsThem(ServerPlayer player, BlockPos pos) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.currentItem == null) return false;
        ArmInteractionPoint point = selected(session, pos);
        return point != null && session.selection.remove(point);
    }

    public static void onArmPlaced(ServerPlayer player, BlockPos pos) {
        if (!player.level().getServer().isSameThread()) return;
        Session session = session(player);
        List<ArmInteractionPoint> selection = session.selection;
        int removed = 0;
        for (Iterator<ArmInteractionPoint> iterator = selection.iterator(); iterator.hasNext(); ) {
            ArmInteractionPoint point = iterator.next();
            if (point.getPos().closerThan(pos, ArmBlockEntity.getRange())) continue;
            iterator.remove();
            removed++;
        }
        if (removed > 0) {
            player.sendOverlayMessage(Component.translatable("create.mechanical_arm.points_outside_range", removed)
                    .withStyle(ChatFormatting.RED));
        } else {
            int inputs = 0, outputs = 0;
            for (ArmInteractionPoint point : selection) {
                if (point.getMode() == Mode.DEPOSIT) outputs++;
                else inputs++;
            }
            if (inputs + outputs > 0) {
                player.sendOverlayMessage(Component.translatable("create.mechanical_arm.summary", inputs, outputs)
                        .withStyle(ChatFormatting.WHITE));
            }
        }
        ArmPlacementPacket packet = new ArmPlacementPacket(List.copyOf(selection), pos);
        if (player.level().isLoaded(pos) && player.level().getBlockEntity(pos) instanceof ArmBlockEntity arm) {
            arm.interactionPointTag = packet.tag();
        }
        selection.clear();
        session.currentItem = null;
    }

    private static void tick(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        Session session = SESSIONS.get(player.getUUID());
        List<ArmInteractionPoint> shown = List.of();
        if (held.is(AllItems.MECHANICAL_ARM)) {
            if (session == null) session = session(player);
            if (held != session.currentItem) {
                session.selection.clear();
                session.currentItem = held;
            }
            session.lastArm = Long.MIN_VALUE;
            shown = session.selection;
        } else {
            if (session != null) session.currentItem = null;
            if (held.is(AllItems.WRENCH)) {
                ArmBlockEntity arm = lookedAtArm(player);
                if (arm != null) {
                    if (session == null) session = session(player);
                    if (session.lastArm != arm.getBlockPos().asLong()) {
                        session.selection.clear();
                        session.selection.addAll(arm.inputs);
                        session.selection.addAll(arm.outputs);
                        session.lastArm = arm.getBlockPos().asLong();
                    }
                    shown = session.selection;
                } else if (session != null && session.lastArm != Long.MIN_VALUE) {
                    session.lastArm = Long.MIN_VALUE;
                    session.selection.clear();
                }
            }
        }
        if (session == null) return;
        sync(player, session, shown);
    }

    private static @Nullable ArmBlockEntity lookedAtArm(ServerPlayer player) {
        BlockHitResult block = PolymerReach.targetBlock(player);
        if (block == null) return null;
        return player.level().getBlockEntity(block.getBlockPos()) instanceof ArmBlockEntity arm ? arm : null;
    }

    private static void sync(ServerPlayer player, Session session, List<ArmInteractionPoint> shown) {
        ServerLevel level = player.level();
        Map<BlockPos, ArmInteractionPoint> wanted = new LinkedHashMap<>();
        for (Iterator<ArmInteractionPoint> iterator = shown.iterator(); iterator.hasNext(); ) {
            ArmInteractionPoint point = iterator.next();
            if (point.getLevel() != level || !level.isLoaded(point.getPos())) continue;
            if (!point.isValid()) {
                iterator.remove();
                continue;
            }
            wanted.put(point.getPos(), point);
        }
        session.outlines.entrySet().removeIf(entry -> {
            ArmInteractionPoint point = wanted.get(entry.getKey());
            if (point != null && entry.getValue().level == level) return false;
            entry.getValue().destroy();
            return true;
        });
        for (ArmInteractionPoint point : wanted.values()) {
            BlockPos pos = point.getPos();
            VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
            Outline outline = session.outlines.get(pos);
            if (shape.isEmpty()) {
                if (outline != null) session.outlines.remove(pos).destroy();
                continue;
            }
            int color = point.getMode().getColor();
            AABB bounds = shape.bounds();
            if (outline == null) {
                session.outlines.put(
                        pos.immutable(), new Outline(session.owner, level, pos.immutable(), bounds, color));
            } else {
                outline.update(bounds, color);
            }
        }
    }

    private static @Nullable ArmInteractionPoint selected(Session session, BlockPos pos) {
        for (ArmInteractionPoint point : session.selection) {
            if (point.getPos().equals(pos)) return point;
        }
        return null;
    }

    static final class Outline {
        final ServerLevel level;
        final ItemDisplayElement element = new ItemDisplayElement();
        final ElementHolder holder;
        AABB bounds;
        int color;

        Outline(UUID owner, ServerLevel level, BlockPos pos, AABB bounds, int color) {
            this.level = level;
            this.bounds = bounds;
            this.color = color;
            holder = new AdapterHolder() {
                @Override
                public boolean startWatching(ServerGamePacketListenerImpl listener) {
                    return listener.player.getUUID().equals(owner) && super.startWatching(listener);
                }
            };
            element.setItemDisplayContext(ItemDisplayContext.NONE);
            element.setBrightness(new Brightness(15, 15));
            element.setItem(stack(color));
            element.setTransformation(transform(bounds));
            holder.addElement(element);
            ChunkAttachment.ofTicking(holder, level, pos);
        }

        void update(AABB nextBounds, int nextColor) {
            if (nextColor != color) {
                color = nextColor;
                element.setItem(stack(color));
            }
            if (!nextBounds.equals(bounds)) {
                bounds = nextBounds;
                element.setTransformation(transform(bounds));
            }
        }

        void destroy() {
            holder.destroy();
        }

        private static ItemStack stack(int color) {
            ItemStack stack = PolymerKineticVisuals.rotatingDisplayItem(OUTLINE_ITEM);
            PolymerPartItems.setColors(stack, List.of(color));
            return stack;
        }

        private static Matrix4f transform(AABB box) {
            Matrix4f local = new Matrix4f()
                    .translate((float) box.minX, (float) box.minY, (float) box.minZ)
                    .scale((float) box.getXsize(), (float) box.getYsize(), (float) box.getZsize());
            return PolymerBlockParts.displayTransform(local);
        }
    }

    private static final Identifier TINT_CUBE_MODEL = CreateIds.id("block/polymer/tint_cube");
    private static final Identifier TINT_CUBE_ITEM = CreateIds.id("polymer_part/block/polymer/tint_cube");

    private static final Identifier GLUE_FACE_MODEL = CreateIds.id("block/polymer/glue_face");

    private static final Identifier GLUE_FACE_ITEM = CreateIds.id("polymer_part/block/polymer/glue_face");

    public static ItemStack glueFace(int color) {
        ItemStack stack = PolymerKineticVisuals.rotatingDisplayItem(GLUE_FACE_ITEM);
        PolymerPartItems.setColors(stack, List.of(color));
        return stack;
    }

    public static ItemStack tintCube(int color) {
        ItemStack stack = PolymerKineticVisuals.rotatingDisplayItem(TINT_CUBE_ITEM);
        PolymerPartItems.setColors(stack, List.of(color));
        return stack;
    }

    public static void addAssets(ResourcePackBuilder builder) {
        JsonObject cube = new JsonObject();
        cube.addProperty("ambientocclusion", false);
        JsonObject cubeTextures = new JsonObject();
        cubeTextures.addProperty("line", OUTLINE_TEXTURE.toString());
        cubeTextures.addProperty("particle", OUTLINE_TEXTURE.toString());
        cube.add("textures", cubeTextures);
        JsonArray cubeElements = new JsonArray();
        cubeElements.add(bar(new float[] {0, 0, 0}, new float[] {16, 16, 16}));
        cube.add("elements", cubeElements);
        builder.addStringData(
                "assets/" + TINT_CUBE_MODEL.getNamespace() + "/models/" + TINT_CUBE_MODEL.getPath() + ".json",
                cube.toString());
        JsonObject cubeInner = new JsonObject();
        cubeInner.addProperty("type", "minecraft:model");
        cubeInner.addProperty("model", TINT_CUBE_MODEL.toString());
        JsonObject cubeTint = new JsonObject();
        cubeTint.addProperty("type", "minecraft:custom_model_data");
        cubeTint.addProperty("index", 0);
        cubeTint.addProperty("default", 0xFFFFFF);
        JsonArray cubeTints = new JsonArray();
        cubeTints.add(cubeTint);
        cubeInner.add("tints", cubeTints);
        JsonObject cubeDefinition = new JsonObject();
        cubeDefinition.add("model", cubeInner);
        builder.addStringData(
                "assets/" + TINT_CUBE_ITEM.getNamespace() + "/items/" + TINT_CUBE_ITEM.getPath() + ".json",
                cubeDefinition.toString());

        String glue = "create:special/glue";
        builder.addStringData(
                "assets/" + GLUE_FACE_MODEL.getNamespace() + "/models/" + GLUE_FACE_MODEL.getPath() + ".json",
                "{\"ambientocclusion\":false,\"textures\":{\"face\":\"" + glue + "\",\"particle\":\"" + glue
                        + "\"},\"elements\":[{\"from\":[0,0,8],\"to\":[16,16,8],"
                        + "\"faces\":{\"north\":{\"uv\":[0,0,16,16],\"texture\":\"#face\",\"tintindex\":0},\"south\":{\"uv\":[0,0,16,16],\"texture\":\"#face\",\"tintindex\":0}}}]}");
        builder.addStringData(
                "assets/" + GLUE_FACE_ITEM.getNamespace() + "/items/" + GLUE_FACE_ITEM.getPath() + ".json",
                "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"" + GLUE_FACE_MODEL
                        + "\",\"tints\":[{\"type\":\"minecraft:custom_model_data\",\"index\":0,\"default\":16777215}]}}");

        JsonObject model = new JsonObject();
        model.addProperty("ambientocclusion", false);
        JsonObject textures = new JsonObject();
        textures.addProperty("line", OUTLINE_TEXTURE.toString());
        textures.addProperty("particle", OUTLINE_TEXTURE.toString());
        model.add("textures", textures);
        JsonArray elements = new JsonArray();
        float lo = -0.5f, hi = 16.5f, a = -0.5f, b = 0.5f, c = 15.5f, d = 16.5f;
        for (int axis = 0; axis < 3; axis++) {
            for (int i = 0; i < 4; i++) {
                float[] from = new float[3], to = new float[3];
                float u0 = (i & 1) == 0 ? a : c, u1 = (i & 1) == 0 ? b : d;
                float v0 = (i & 2) == 0 ? a : c, v1 = (i & 2) == 0 ? b : d;
                int p = (axis + 1) % 3, q = (axis + 2) % 3;
                from[axis] = lo;
                to[axis] = hi;
                from[p] = u0;
                to[p] = u1;
                from[q] = v0;
                to[q] = v1;
                elements.add(bar(from, to));
            }
        }
        model.add("elements", elements);
        builder.addStringData(
                "assets/" + OUTLINE_MODEL.getNamespace() + "/models/" + OUTLINE_MODEL.getPath() + ".json",
                model.toString());

        JsonObject inner = new JsonObject();
        inner.addProperty("type", "minecraft:model");
        inner.addProperty("model", OUTLINE_MODEL.toString());
        JsonObject tint = new JsonObject();
        tint.addProperty("type", "minecraft:custom_model_data");
        tint.addProperty("index", 0);
        tint.addProperty("default", 0xFFFFFF);
        JsonArray tints = new JsonArray();
        tints.add(tint);
        inner.add("tints", tints);
        JsonObject definition = new JsonObject();
        definition.add("model", inner);
        builder.addStringData(
                "assets/" + OUTLINE_ITEM.getNamespace() + "/items/" + OUTLINE_ITEM.getPath() + ".json",
                definition.toString());

        BufferedImage white = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) white.setRGB(x, y, 0xFFFFFFFF);
        try {
            builder.addData(
                    "assets/" + OUTLINE_TEXTURE.getNamespace() + "/textures/" + OUTLINE_TEXTURE.getPath() + ".png",
                    PngCodec.encode(white));
        } catch (IOException e) {
            Create.LOGGER.error("Failed to write the arm outline texture", e);
        }
    }

    private static JsonObject bar(float[] from, float[] to) {
        JsonObject element = new JsonObject();
        element.add("from", vec(from));
        element.add("to", vec(to));
        element.addProperty("shade", false);
        JsonObject faces = new JsonObject();
        for (String face : new String[] {"north", "south", "east", "west", "up", "down"}) {
            JsonObject f = new JsonObject();
            f.addProperty("texture", "#line");
            f.addProperty("tintindex", 0);
            JsonArray uv = new JsonArray();
            uv.add(0);
            uv.add(0);
            uv.add(1);
            uv.add(1);
            f.add("uv", uv);
            faces.add(face, f);
        }
        element.add("faces", faces);
        return element;
    }

    private static JsonArray vec(float[] v) {
        JsonArray array = new JsonArray();
        for (float f : v) array.add(f);
        return array;
    }
}
