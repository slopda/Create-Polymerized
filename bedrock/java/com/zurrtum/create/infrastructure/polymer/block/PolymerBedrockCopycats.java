package com.zurrtum.create.infrastructure.polymer.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zurrtum.create.infrastructure.polymer.PolymerBedrockCarriers;
import com.zurrtum.create.infrastructure.polymer.PolymerBedrockMeshes;
import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class PolymerBedrockCopycats {
    /** UUID most significant bits: ASCII "ppcopyck" (bridge CopycatSkinEntities.MARKER). */
    public static final long MARKER = 0x7070636f7079636bL;

    public static final String POSE_HOOK = "polymerpatch.copycatSkinPose";

    static final double RANGE = 48;
    private static final int RECHECK_TICKS = 20;

    private static volatile @Nullable Map<String, int[]> table;
    private static boolean warned;
    private static final Map<Identifier, PolymerBedrockMeshes.Model> movingMeshes = new HashMap<>();

    private PolymerBedrockCopycats() {}

    public static PolymerBedrockMeshes.@Nullable Model mesh(Identifier model) {
        var cached = movingMeshes.get(model);
        if (cached != null) return cached;
        if (!"create".equals(model.getNamespace()) || !model.getPath().startsWith(PolymerCopycatParts.FOLDER))
            return null;
        int[] entry = table().get(model.getPath().substring(PolymerCopycatParts.FOLDER.length()));
        if (entry == null) {
            if (!warned && !table().isEmpty()) {
                warned = true;
                com.zurrtum.create.Create.LOGGER.warn("Bedrock copycat skins: no skin for mesh {}", model);
            }
            return null;
        }
        var mesh = new PolymerBedrockMeshes.Model(entry[0], entry[1]);
        movingMeshes.put(model, mesh);
        return mesh;
    }

    static Map<String, int[]> table() {
        Map<String, int[]> current = table;
        if (current != null) return current;
        synchronized (PolymerBedrockCopycats.class) {
            if (table != null) return table;
            Map<String, int[]> loaded = new HashMap<>();
            Path file = Path.of("polymerpatch", "copycat_skins.json");
            try {
                if (Files.isRegularFile(file)) {
                    JsonObject root =
                            JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> e :
                            root.getAsJsonObject("files").entrySet()) {
                        JsonArray pair = e.getValue().getAsJsonArray();
                        loaded.put(
                                e.getKey(),
                                new int[] {pair.get(0).getAsInt(), pair.get(1).getAsInt()});
                    }
                }
            } catch (Exception e) {
                com.zurrtum.create.Create.LOGGER.error("Bedrock copycat skins: cannot read {}", file, e);
            }
            com.zurrtum.create.Create.LOGGER.info("Bedrock copycat skins: {} mesh files from {}", loaded.size(), file);
            table = Map.copyOf(loaded);
            return table;
        }
    }

    public static final class Skin {
        private final Set<ServerGamePacketListenerImpl> viewers = new HashSet<>();
        private int entityId = -1;
        private long pose = -1;
        private int age;

        public void update(
                ServerLevel level, BlockPos pos, Identifier model, Collection<ServerGamePacketListenerImpl> watching) {
            String path = model.getPath();
            int[] entry = path.startsWith(PolymerCopycatParts.FOLDER)
                    ? table().get(path.substring(PolymerCopycatParts.FOLDER.length()))
                    : null;
            if (entry == null && !table().isEmpty() && !warned) {
                warned = true;
                com.zurrtum.create.Create.LOGGER.warn("Bedrock copycat skins: no skin for mesh {}", path);
            }
            long next = entry == null || enclosed(level, pos) ? -1 : encode(entry[0], entry[1]);
            if (next != pose) {
                despawnAll();
                pose = next;
                if (pose >= 0) entityId = VirtualEntityUtils.requestEntityId();
                age = RECHECK_TICKS;
            }
            if (pose < 0 || ++age < RECHECK_TICKS) return;
            age = 0;
            Vec3 centre = Vec3.atCenterOf(pos);
            viewers.removeIf(listener -> {
                boolean keep = watching.contains(listener)
                        && listener.player.position().distanceToSqr(centre) < RANGE * RANGE;
                if (!keep) listener.send(new ClientboundRemoveEntitiesPacket(entityId));
                return !keep;
            });
            for (ServerGamePacketListenerImpl listener : watching) {
                if (viewers.contains(listener) || !PolymerBedrockCarriers.isBedrock(listener.player.getUUID()))
                    continue;
                if (listener.player.position().distanceToSqr(centre) >= RANGE * RANGE) continue;
                viewers.add(listener);
                listener.send(new ClientboundAddEntityPacket(
                        entityId,
                        new UUID(MARKER, pose),
                        pos.getX() + 0.5,
                        pos.getY(),
                        pos.getZ() + 0.5,
                        0,
                        0,
                        EntityTypes.ARMOR_STAND,
                        0,
                        Vec3.ZERO,
                        0));
            }
        }

        public void stopWatching(ServerGamePacketListenerImpl listener) {
            if (viewers.remove(listener) && entityId >= 0) listener.send(new ClientboundRemoveEntitiesPacket(entityId));
        }

        public void destroy() {
            despawnAll();
            pose = -1;
        }

        private void despawnAll() {
            if (entityId >= 0)
                for (ServerGamePacketListenerImpl listener : viewers)
                    listener.send(new ClientboundRemoveEntitiesPacket(entityId));
            viewers.clear();
        }
    }

    private static boolean enclosed(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos next = pos.relative(direction);
            if (!level.getBlockState(next).isSolidRender()) return false;
        }
        return true;
    }

    /** UUID LSB (bridge CopycatSkinEntities.onSpawn): geo | mat << 16. */
    static long encode(int geo, int mat) {
        return (geo & 0xFFFL) | ((long) (mat & 0xFFF) << 16);
    }
}
