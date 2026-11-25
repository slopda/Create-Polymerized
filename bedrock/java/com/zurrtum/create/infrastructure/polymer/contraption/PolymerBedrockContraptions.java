package com.zurrtum.create.infrastructure.polymer.contraption;

import com.zurrtum.create.content.contraptions.AbstractContraptionEntity;
import com.zurrtum.create.content.contraptions.Contraption;
import com.zurrtum.create.infrastructure.polymer.PolymerBedrockCarriers;
import com.zurrtum.create.infrastructure.polymer.PolymerBedrockItems;
import com.zurrtum.create.infrastructure.polymer.PolymerBedrockMeshes;
import com.zurrtum.create.infrastructure.polymer.PolymerBedrockMotion;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBedrockCopycats;
import com.zurrtum.create.infrastructure.polymer.block.PolymerCopycatParts;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class PolymerBedrockContraptions {
    private static final Map<AbstractContraptionEntity, Map<BlockPos, PolymerBedrockItems.Holder>> BODIES =
            new IdentityHashMap<>();
    private static final Set<Class<?>> FAILED = new HashSet<>();
    private static final java.util.concurrent.ConcurrentMap<java.util.UUID, int[]> HOSTS =
            new java.util.concurrent.ConcurrentHashMap<>();

    static {
        PolymerBedrockRiderDeck.install(HOSTS::get);
    }

    private static final Map<AbstractContraptionEntity, PolymerBedrockMotion.State> MOTION = new IdentityHashMap<>();
    private static final Map<AbstractContraptionEntity, Map<BlockPos, PolymerBedrockMeshes.Holder>> SKINS =
            new IdentityHashMap<>();

    private PolymerBedrockContraptions() {}

    private static final boolean BODY_TABLE =
            java.nio.file.Files.isRegularFile(java.nio.file.Path.of("polymerpatch", "contraption_meshes.json"));
    private static final boolean COPYCAT_TABLE =
            java.nio.file.Files.isRegularFile(java.nio.file.Path.of("polymerpatch", "copycat_skins.json"));

    static boolean replaces(StructureBlockInfo info) {
        if (!com.zurrtum.create.infrastructure.polymer.PolymerBedrockRotors.ENABLED) return false;
        var copycat = PolymerCopycatParts.contraptionMesh(info);
        return copycat == null
                ? BODY_TABLE && PolymerBedrockBodyModels.mesh(info.state()) != null
                : COPYCAT_TABLE && PolymerBedrockCopycats.mesh(copycat) != null;
    }

    static void tick(AbstractContraptionEntity entity, ServerLevel level, Contraption contraption) {
        try {
            if (!bedrockNearby(level, entity)) {
                remove(entity);
                return;
            }
            Map<BlockPos, PolymerBedrockItems.Holder> holders =
                    BODIES.computeIfAbsent(entity, e -> new java.util.HashMap<>());
            Map<BlockPos, PolymerBedrockMeshes.Holder> skins =
                    SKINS.computeIfAbsent(entity, e -> new java.util.HashMap<>());
            BlockPos base = BlockPos.containing(entity.position());
            Quaternionf rotation = PolymerContraptionVisuals.rotationOf(entity);
            int[] meshAngles = PolymerBedrockItems.bedrockEuler(rotation);
            var motion = MOTION.computeIfAbsent(entity, e -> new PolymerBedrockMotion.State())
                    .sample(entity.toGlobalVector(Vec3.atCenterOf(BlockPos.ZERO), 1), rotation);
            long gameTime = level.getGameTime();
            Set<BlockPos> seen = new HashSet<>();
            for (Map.Entry<BlockPos, StructureBlockInfo> entry :
                    contraption.getBlocks().entrySet()) {
                BlockState state = entry.getValue().state();
                if (state.isAir()) continue;
                BlockPos local = entry.getKey().immutable();
                var copycatModel = PolymerCopycatParts.contraptionMesh(entry.getValue());
                if ((state.isSolidRender() || copycatModel != null) && enclosed(contraption, local)) continue;
                var mesh = copycatModel == null
                        ? PolymerBedrockBodyModels.mesh(state)
                        : PolymerBedrockCopycats.mesh(copycatModel);
                if (mesh != null) {
                    seen.add(local);
                    var old = holders.remove(local);
                    if (old != null) old.destroy();
                    long marker =
                            copycatModel == null ? PolymerBedrockBodyModels.MARKER : PolymerBedrockCopycats.MARKER;
                    String hook = copycatModel == null
                            ? PolymerBedrockBodyModels.POSE_HOOK
                            : PolymerBedrockCopycats.POSE_HOOK;
                    var previous = skins.get(local);
                    if (previous != null && !previous.uses(marker)) {
                        previous.destroy();
                        skins.remove(local);
                    }
                    skins.computeIfAbsent(local, key -> new PolymerBedrockMeshes.Holder(key, marker, hook, 160))
                            .update(
                                    level,
                                    base,
                                    entity.toGlobalVector(Vec3.atCenterOf(local), 1),
                                    meshAngles,
                                    mesh,
                                    gameTime,
                                    motion,
                                    local);
                    continue;
                }
                var oldSkin = skins.remove(local);
                if (oldSkin != null) oldSkin.destroy();
                ItemStack stack = new ItemStack(state.getBlock().asItem());
                if (stack.isEmpty()) continue;
                seen.add(local);
                Vec3 centre = entity.toGlobalVector(Vec3.atCenterOf(local), 1);
                Matrix4f transform = new Matrix4f()
                        .translate((float) (centre.x - base.getX()), (float) (centre.y - base.getY()), (float)
                                (centre.z - base.getZ()))
                        .rotate(rotation);
                holders.computeIfAbsent(local, l -> new PolymerBedrockItems.Holder(base))
                        .update(level, base, stack, transform, ItemDisplayContext.NONE, gameTime, 1);
            }
            holders.entrySet().removeIf(e -> {
                if (seen.contains(e.getKey())) return false;
                e.getValue().destroy();
                return true;
            });
            skins.entrySet().removeIf(e -> {
                if (seen.contains(e.getKey())) return false;
                e.getValue().destroy();
                return true;
            });
            HOSTS.put(
                    entity.getUUID(),
                    skins.values().stream()
                            .mapToInt(PolymerBedrockMeshes.Holder::entityId)
                            .filter(id -> id >= 0)
                            .toArray());
        } catch (RuntimeException e) {
            if (FAILED.add(entity.getClass()))
                com.zurrtum.create.Create.LOGGER.error("Bedrock contraption bodies failed for {}", entity, e);
            remove(entity);
        }
    }

    static void remove(AbstractContraptionEntity entity) {
        HOSTS.remove(entity.getUUID());
        MOTION.remove(entity);
        Map<BlockPos, PolymerBedrockItems.Holder> holders = BODIES.remove(entity);
        if (holders != null) holders.values().forEach(PolymerBedrockItems.Holder::destroy);
        var skins = SKINS.remove(entity);
        if (skins != null) skins.values().forEach(PolymerBedrockMeshes.Holder::destroy);
    }

    static void clear() {
        HOSTS.clear();
        MOTION.clear();
        BODIES.values().forEach(holders -> holders.values().forEach(PolymerBedrockItems.Holder::destroy));
        BODIES.clear();
        SKINS.values().forEach(skins -> skins.values().forEach(PolymerBedrockMeshes.Holder::destroy));
        SKINS.clear();
    }

    private static boolean bedrockNearby(ServerLevel level, AbstractContraptionEntity entity) {
        for (ServerPlayer player : level.players())
            if (PolymerBedrockCarriers.isBedrock(player.getUUID()) && player.distanceToSqr(entity) < 160 * 160)
                return true;
        return false;
    }

    private static boolean enclosed(Contraption contraption, BlockPos local) {
        for (Direction direction : Direction.values()) {
            StructureBlockInfo neighbour = contraption.getBlocks().get(local.relative(direction));
            if (neighbour == null || !neighbour.state().isSolidRender()) return false;
        }
        return true;
    }
}
