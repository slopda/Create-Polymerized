package dev.veeda.createpoly;

import com.zurrtum.create.AllSynchedDatas;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public final class NativeMetadataSnapshots {
    private record Boundary(int start, int end) {
        boolean contains(int id) {
            return id >= start && id < end;
        }
    }

    private static final ReferenceQueue<Entity> DEAD = new ReferenceQueue<>();
    private static final Map<Integer, Entry> ENTITIES = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boundary> CLASSES = new ConcurrentHashMap<>();

    private static final class Entry extends WeakReference<Entity> {
        final int id;
        final Boundary boundary;

        Entry(Entity entity, Boundary boundary) {
            super(entity, DEAD);
            id = entity.getId();
            this.boundary = boundary;
        }
    }

    private NativeMetadataSnapshots() {}

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> capture(entity));
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> remove(entity));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
    }

    public static void capture(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) return;
        reap();
        if (!BuiltInRegistries.ENTITY_TYPE
                .getKey(entity.getType())
                .getNamespace()
                .equals("minecraft")) {
            ENTITIES.remove(entity.getId());
            return;
        }
        Boundary boundary = CLASSES.computeIfAbsent(entity.getClass(), ignored -> {
            int start = 256, end = 0;
            for (int id = 0; id < 256; id++)
                if (AllSynchedDatas.isCreateOnly(entity, id)) {
                    start = Math.min(start, id);
                    end = id + 1;
                }
            return new Boundary(start, end);
        });
        Entry current = ENTITIES.get(entity.getId());
        if (current != null && current.get() == entity) return;
        ENTITIES.put(entity.getId(), new Entry(entity, boundary));
    }

    public static boolean isCreateOnly(int entityId, int metadataId) {
        Entry snapshot = ENTITIES.get(entityId);
        return snapshot != null && snapshot.boundary.contains(metadataId);
    }

    public static void remove(Entity entity) {
        Entry value = ENTITIES.get(entity.getId());
        if (value != null && value.get() == entity) ENTITIES.remove(entity.getId(), value);
        reap();
    }

    private static void reap() {
        for (Reference<? extends Entity> value; (value = DEAD.poll()) != null; )
            if (value instanceof Entry entry) ENTITIES.remove(entry.id, entry);
    }

    private static void clear() {
        ENTITIES.clear();
        CLASSES.clear();
        while (DEAD.poll() != null) {}
    }
}
