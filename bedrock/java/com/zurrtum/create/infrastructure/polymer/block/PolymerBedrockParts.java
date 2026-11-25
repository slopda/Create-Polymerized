package com.zurrtum.create.infrastructure.polymer.block;

import com.zurrtum.create.infrastructure.polymer.PolymerAdapterConfig;
import com.zurrtum.create.infrastructure.polymer.PolymerBedrockCarriers;
import com.zurrtum.create.infrastructure.polymer.PolymerBedrockItems;
import com.zurrtum.create.infrastructure.polymer.PolymerBedrockMeshes;
import com.zurrtum.create.infrastructure.polymer.PolymerBedrockMotion;
import com.zurrtum.create.infrastructure.polymer.PolymerBedrockPartPose;
import com.zurrtum.create.infrastructure.polymer.PolymerBedrockRotors;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerBedrockBodyModels;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public final class PolymerBedrockParts {
    private PolymerBedrockParts() {}

    /**
     * Block-part models that move and are always placed rigidly (no scale), drawn on Bedrock as mesh entities. The
     * single source of truth: the census writes it to {@code bedrock_contract.json} ({@code meshParts}); the pack
     * builder meshes these models and the bake leaves them out of native looks. Adding a model here needs a new bake,
     * pack and fixture install together.
     */
    public static final List<String> MESH_PARTS = List.of(
            "create:block/mechanical_press/head",
            "create:block/andesite_door/fold_left",
            "create:block/andesite_door/fold_right",
            "create:block/copper_door/fold_left",
            "create:block/copper_door/fold_right",
            "create:block/brass_door/block",
            "create:block/train_door/block",
            "create:block/framed_glass_door/block",
            "create:block/deployer/pole",
            "create:block/deployer/hand_pointing",
            "create:block/deployer/hand_punching",
            "create:block/deployer/hand_holding",
            "create:block/mechanical_arm/base",
            "create:block/mechanical_arm/lower_body",
            "create:block/mechanical_arm/upper_body",
            "create:block/mechanical_arm/claw_base",
            "create:block/mechanical_arm/claw_base_goggles",
            "create:block/mechanical_arm/lower_claw_grip",
            "create:block/mechanical_arm/upper_claw_grip",
            "create:block/polymer/schematicannon_connector",
            "create:block/polymer/schematicannon_pipe",
            "create:block/polymer/valve_handle",
            "create:block/polymer/copper_valve_handle",
            "create:block/polymer/white_valve_handle",
            "create:block/polymer/orange_valve_handle",
            "create:block/polymer/magenta_valve_handle",
            "create:block/polymer/light_blue_valve_handle",
            "create:block/polymer/yellow_valve_handle",
            "create:block/polymer/lime_valve_handle",
            "create:block/polymer/pink_valve_handle",
            "create:block/polymer/gray_valve_handle",
            "create:block/polymer/light_gray_valve_handle",
            "create:block/polymer/cyan_valve_handle",
            "create:block/polymer/purple_valve_handle",
            "create:block/polymer/blue_valve_handle",
            "create:block/polymer/brown_valve_handle",
            "create:block/polymer/green_valve_handle",
            "create:block/polymer/red_valve_handle",
            "create:block/polymer/black_valve_handle");

    private static final java.util.Set<String> MESH_PART_SET = java.util.Set.copyOf(MESH_PARTS);

    private static final Map<BlockEntity, List<PolymerBedrockRotors.Rotor>> ROTORS = new IdentityHashMap<>();
    private static final Map<BlockEntity, Map<Object, PolymerBedrockItems.Holder>> ITEMS = new IdentityHashMap<>();
    private static final Map<BlockEntity, Map<Object, MeshPart>> MESHES = new IdentityHashMap<>();
    private static final Map<BlockEntity, MeshPart> BLOCK_MESHES = new IdentityHashMap<>();
    private static final Map<BlockEntity, MeshPart> OWNED_MESHES = new IdentityHashMap<>();
    private static final java.util.Set<String> NOT_RIGID = new java.util.HashSet<>();

    private static final java.util.Set<Block> FAILED = new java.util.HashSet<>();
    public static boolean DEBUG = false;

    public static PolymerBlockParts.@org.jspecify.annotations.Nullable Motion rotorMotion(PolymerBlockParts.Part part) {
        if (part.motion() == null || !part.item().isEmpty() || part.text() != null) return null;
        return PolymerBedrockRotors.partRotor(part.model()) < 0 ? null : part.motion();
    }

    /** Never throws: like PolymerBlockParts' own collection, a broken Bedrock path must not take down the server tick. */
    static void tick(BlockEntity blockEntity, ServerLevel level) {
        try {
            if (!bedrockPresent(level)) return;
            List<PolymerBlockParts.Part> all =
                    PolymerBlockParts.allParts(blockEntity, PolymerBlockParts.providerTime(level));
            List<PolymerBlockParts.Part> rotorParts = new ArrayList<>(),
                    itemParts = new ArrayList<>(),
                    meshParts = new ArrayList<>();
            for (PolymerBlockParts.Part part : all) {
                if (rotorMotion(part) != null) rotorParts.add(part);
                else if (!part.item().isEmpty()) itemParts.add(part);
                else if (meshPart(part)) meshParts.add(part);
            }
            if (PolymerBedrockRotors.ENABLED) tickRotors(blockEntity, level, rotorParts);
            tickItems(blockEntity, level, itemParts);
            tickMeshes(blockEntity, level, meshParts);
            tickBlockMesh(blockEntity, level);
        } catch (RuntimeException e) {
            if (FAILED.add(blockEntity.getBlockState().getBlock()))
                com.zurrtum.create.Create.LOGGER.error(
                        "Bedrock block parts failed for {}",
                        blockEntity.getBlockState().getBlock(),
                        e);
            remove(blockEntity);
        }
    }

    private static void tickItems(BlockEntity blockEntity, ServerLevel level, List<PolymerBlockParts.Part> parts) {
        if (parts.isEmpty()) {
            Map<Object, PolymerBedrockItems.Holder> holders = ITEMS.remove(blockEntity);
            if (holders != null) holders.values().forEach(PolymerBedrockItems.Holder::destroy);
            return;
        }
        Map<Object, PolymerBedrockItems.Holder> holders = ITEMS.computeIfAbsent(blockEntity, be -> new HashMap<>());
        Map<Object, Integer> occurrences = new HashMap<>();
        Set<Object> seen = new HashSet<>();
        long gameTime = level.getGameTime();
        for (PolymerBlockParts.Part part : parts) {
            Object token =
                    part.identity() != null ? part.identity() : part.item().getItem();
            Object key = new ItemKey(token, occurrences.merge(token, 1, Integer::sum) - 1);
            seen.add(key);
            holders.computeIfAbsent(key, k -> new PolymerBedrockItems.Holder(blockEntity.getBlockPos()))
                    .update(level, blockEntity.getBlockPos(), part.item(), part.transform(), part.context(), gameTime);
        }
        holders.entrySet().removeIf(entry -> {
            if (seen.contains(entry.getKey())) return false;
            entry.getValue().destroy();
            return true;
        });
    }

    private record ItemKey(Object token, int occurrence) {}

    public static boolean meshPart(PolymerBlockParts.Part part) {
        return part.model() != null
                && part.motion() == null
                && part.item().isEmpty()
                && part.text() == null
                && MESH_PART_SET.contains(part.model().toString());
    }

    private static final class MeshPart {
        final PolymerBedrockMeshes.Holder holder;
        final PolymerBedrockMotion.State motion = new PolymerBedrockMotion.State();

        MeshPart(BlockPos pos) {
            holder = new PolymerBedrockMeshes.Holder(
                    pos,
                    PolymerBedrockBodyModels.MARKER,
                    PolymerBedrockBodyModels.POSE_HOOK,
                    PolymerAdapterConfig.blockDisplayDistance);
        }
    }

    private static void tickMeshes(BlockEntity blockEntity, ServerLevel level, List<PolymerBlockParts.Part> parts) {
        if (parts.isEmpty()) {
            Map<Object, MeshPart> meshes = MESHES.remove(blockEntity);
            if (meshes != null) meshes.values().forEach(mesh -> mesh.holder.destroy());
            return;
        }
        BlockPos pos = blockEntity.getBlockPos();
        Map<Object, MeshPart> meshes = MESHES.computeIfAbsent(blockEntity, be -> new HashMap<>());
        Map<Object, Integer> occurrences = new HashMap<>();
        Set<Object> seen = new HashSet<>();
        long gameTime = level.getGameTime();
        for (PolymerBlockParts.Part part : parts) {
            PolymerBedrockMeshes.Model model = PolymerBedrockBodyModels.partMesh(part.model());
            PolymerBedrockPartPose.Pose pose =
                    PolymerBedrockPartPose.of(part.transform(), pos.getX(), pos.getY(), pos.getZ());
            if (pose == null && NOT_RIGID.add(part.model().toString()))
                com.zurrtum.create.Create.LOGGER.warn(
                        "Bedrock mesh part {} at {} has a scaled or mirrored pose; not drawn on Bedrock",
                        part.model(),
                        pos);
            if (model == null || pose == null) continue;
            Object token = part.identity() != null ? part.identity() : "mesh";
            Object key = new ItemKey(token, occurrences.merge(token, 1, Integer::sum) - 1);
            seen.add(key);
            MeshPart mesh = meshes.computeIfAbsent(key, k -> new MeshPart(pos));
            Vec3 centre = new Vec3(pose.x(), pose.y(), pose.z());
            mesh.holder.update(
                    level,
                    pos,
                    centre,
                    PolymerBedrockItems.bedrockEuler(pose.rotation()),
                    model,
                    gameTime,
                    mesh.motion.sample(centre, pose.rotation()),
                    BlockPos.ZERO);
        }
        meshes.entrySet().removeIf(entry -> {
            if (seen.contains(entry.getKey())) return false;
            entry.getValue().holder.destroy();
            return true;
        });
    }

    public static void ownedMesh(
            BlockEntity blockEntity,
            ServerLevel level,
            net.minecraft.resources.Identifier model,
            org.joml.Matrix4fc transform) {
        try {
            PolymerBedrockMeshes.Model mesh = bedrockPresent(level) ? PolymerBedrockBodyModels.partMesh(model) : null;
            BlockPos pos = blockEntity.getBlockPos();
            PolymerBedrockPartPose.Pose pose =
                    mesh == null ? null : PolymerBedrockPartPose.of(transform, pos.getX(), pos.getY(), pos.getZ());
            if (pose == null) {
                removeOwnedMesh(blockEntity);
                return;
            }
            MeshPart part = OWNED_MESHES.computeIfAbsent(blockEntity, be -> new MeshPart(pos));
            Vec3 centre = new Vec3(pose.x(), pose.y(), pose.z());
            part.holder.update(
                    level,
                    pos,
                    centre,
                    PolymerBedrockItems.bedrockEuler(pose.rotation()),
                    mesh,
                    level.getGameTime(),
                    part.motion.sample(centre, pose.rotation()),
                    BlockPos.ZERO);
        } catch (RuntimeException e) {
            if (FAILED.add(blockEntity.getBlockState().getBlock()))
                com.zurrtum.create.Create.LOGGER.error(
                        "Bedrock owned mesh failed for {}",
                        blockEntity.getBlockState().getBlock(),
                        e);
            removeOwnedMesh(blockEntity);
        }
    }

    public static void removeOwnedMesh(BlockEntity blockEntity) {
        MeshPart part = OWNED_MESHES.remove(blockEntity);
        if (part != null) part.holder.destroy();
    }

    private static void tickBlockMesh(BlockEntity blockEntity, ServerLevel level) {
        PolymerBedrockMeshes.Model model = PolymerBedrockBodyModels.blockMesh(blockEntity.getBlockState());
        if (model == null) {
            MeshPart stale = BLOCK_MESHES.remove(blockEntity);
            if (stale != null) stale.holder.destroy();
            return;
        }
        BlockPos pos = blockEntity.getBlockPos();
        MeshPart mesh = BLOCK_MESHES.computeIfAbsent(blockEntity, be -> new MeshPart(pos));
        Vec3 centre = Vec3.atCenterOf(pos);
        org.joml.Quaternionf upright = new org.joml.Quaternionf();
        mesh.holder.update(
                level,
                pos,
                centre,
                PolymerBedrockItems.bedrockEuler(upright),
                model,
                level.getGameTime(),
                mesh.motion.sample(centre, upright),
                BlockPos.ZERO);
    }

    private static void tickRotors(BlockEntity blockEntity, ServerLevel level, List<PolymerBlockParts.Part> parts) {
        if (parts.isEmpty()) {
            List<PolymerBedrockRotors.Rotor> rotors = ROTORS.remove(blockEntity);
            if (rotors != null) rotors.forEach(PolymerBedrockRotors.Rotor::destroy);
            return;
        }
        if (DEBUG && !ROTORS.containsKey(blockEntity))
            com.zurrtum.create.Create.LOGGER.info(
                    "Bedrock part rotors at {} {}: {}",
                    blockEntity.getBlockPos(),
                    blockEntity.getBlockState(),
                    parts.stream().map(p -> p.model() + " " + p.motion()).toList());
        List<PolymerBedrockRotors.Rotor> rotors = ROTORS.computeIfAbsent(blockEntity, be -> new ArrayList<>());
        while (rotors.size() > parts.size()) rotors.remove(rotors.size() - 1).destroy();
        while (rotors.size() < parts.size()) rotors.add(new PolymerBedrockRotors.Rotor(blockEntity.getBlockPos()));
        long gameTime = level.getGameTime();
        for (int i = 0; i < parts.size(); i++) {
            PolymerBlockParts.Part part = parts.get(i);
            PolymerBlockParts.Motion motion = part.motion();
            boolean flip = motion.facing().getAxisDirection() == AxisDirection.NEGATIVE;
            float sign = flip ? -1 : 1;
            rotors.get(i)
                    .update(
                            level,
                            blockEntity.getBlockPos(),
                            motion.facing().getAxis(),
                            flip,
                            PolymerBedrockRotors.partRotor(part.model()),
                            sign * motion.speed(),
                            sign * motion.angle(),
                            gameTime);
        }
    }

    private static long presenceTick = Long.MIN_VALUE;
    private static final Map<ServerLevel, Boolean> PRESENT = new IdentityHashMap<>();

    private static boolean bedrockPresent(ServerLevel level) {
        long now = level.getServer().getTickCount();
        if (now != presenceTick) {
            presenceTick = now;
            PRESENT.clear();
        }
        return PRESENT.computeIfAbsent(level, l -> {
            for (var player : l.players()) if (PolymerBedrockCarriers.isBedrock(player.getUUID())) return true;
            return false;
        });
    }

    static void remove(BlockEntity blockEntity) {
        List<PolymerBedrockRotors.Rotor> rotors = ROTORS.remove(blockEntity);
        if (rotors != null) rotors.forEach(PolymerBedrockRotors.Rotor::destroy);
        Map<Object, PolymerBedrockItems.Holder> holders = ITEMS.remove(blockEntity);
        if (holders != null) holders.values().forEach(PolymerBedrockItems.Holder::destroy);
        Map<Object, MeshPart> meshes = MESHES.remove(blockEntity);
        if (meshes != null) meshes.values().forEach(mesh -> mesh.holder.destroy());
        MeshPart block = BLOCK_MESHES.remove(blockEntity);
        if (block != null) block.holder.destroy();
        removeOwnedMesh(blockEntity);
    }

    static void clear() {
        ROTORS.values().forEach(rotors -> rotors.forEach(PolymerBedrockRotors.Rotor::destroy));
        ROTORS.clear();
        ITEMS.values().forEach(holders -> holders.values().forEach(PolymerBedrockItems.Holder::destroy));
        ITEMS.clear();
        MESHES.values().forEach(meshes -> meshes.values().forEach(mesh -> mesh.holder.destroy()));
        MESHES.clear();
        BLOCK_MESHES.values().forEach(mesh -> mesh.holder.destroy());
        BLOCK_MESHES.clear();
        OWNED_MESHES.values().forEach(mesh -> mesh.holder.destroy());
        OWNED_MESHES.clear();
        PRESENT.clear();
    }
}
