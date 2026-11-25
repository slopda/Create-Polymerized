package com.zurrtum.create.infrastructure.polymer;

import com.zurrtum.create.Create;
import com.zurrtum.create.content.contraptions.AbstractContraptionEntity;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerContraptionFloors;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerContraptionPhysics;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class PolymerContraptionTrialGate {
    public interface Driver {
        default boolean replacesNativeFloor() {
            return true;
        }

        default int nativeHelperCount() {
            return 0;
        }

        void prepare();

        void updated();

        boolean covers(BlockPos position);

        void control(ServerPlayer player);

        void leave(ServerPlayer player);

        void close();
    }

    private record Entry(Driver driver, boolean previouslyDisabled) {}

    private static final Map<AbstractContraptionEntity, Entry> OWNERS = new IdentityHashMap<>();
    private static final Map<UUID, AbstractContraptionEntity> PLAYERS = new HashMap<>();

    private PolymerContraptionTrialGate() {}

    private static void validate(AbstractContraptionEntity owner) {
        if (!(owner.level() instanceof ServerLevel world)
                || world.getServer().getPort() != 25566
                || !world.getServer().isSameThread()
                || owner.isRemoved())
            throw new IllegalStateException("Contraption trials require a live lab25566 owner on its server thread");
    }

    public static void attach(AbstractContraptionEntity owner, Driver driver) {
        validate(owner);
        if (driver == null) throw new IllegalArgumentException("Trial driver required");
        remove(owner);
        boolean disabled = PolymerContraptionFloors.trialDisabled(owner);
        if (driver.replacesNativeFloor()) PolymerContraptionFloors.disable(owner);
        OWNERS.put(owner, new Entry(driver, disabled));
    }

    public static void enroll(ServerPlayer player, AbstractContraptionEntity owner) {
        validate(owner);
        if (player.level() != owner.level() || !OWNERS.containsKey(owner))
            throw new IllegalStateException("Player and registered trial owner must share the lab world");
        release(player);
        PLAYERS.put(player.getUUID(), owner);
        PolymerContraptionPhysics.forgetPlayer(player);
        PolymerContraptionFloors.forgetPlayer(player);
    }

    public static void release(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel world)
                || !world.getServer().isSameThread()
                || world.getServer().getPort() != 25566) throw new IllegalStateException("Lab server thread required");
        AbstractContraptionEntity owner = PLAYERS.remove(player.getUUID());
        Entry entry = owner == null ? null : OWNERS.get(owner);
        if (entry != null)
            try {
                entry.driver.leave(player);
            } catch (RuntimeException | AssertionError error) {
                Create.LOGGER.warn("Lab trial leave failed", error);
            }
        if (owner != null) {
            PolymerContraptionPhysics.forgetPlayer(player);
            PolymerContraptionFloors.forgetPlayer(player);
        }
    }

    static boolean controls(ServerPlayer player) {
        AbstractContraptionEntity owner = PLAYERS.get(player.getUUID());
        return owner != null && owner.level() == player.level() && OWNERS.containsKey(owner);
    }

    static boolean active(AbstractContraptionEntity owner) {
        return owner.level() instanceof ServerLevel world
                && world.getServer().getPort() == 25566
                && world.getServer().isSameThread()
                && OWNERS.containsKey(owner);
    }

    static int nativeHelperCount(MinecraftServer server) {
        if (server.getPort() != 25566 || !server.isSameThread()) return 0;
        int total = 0;
        for (AbstractContraptionEntity owner : new ArrayList<>(OWNERS.keySet())) {
            Entry entry = OWNERS.get(owner);
            if (entry == null) continue;
            try {
                int reserved = entry.driver.nativeHelperCount();
                if (reserved < 0 || reserved > 256)
                    throw new IllegalStateException("Invalid lab native-helper reservation");
                total = Math.addExact(total, reserved);
            } catch (RuntimeException | AssertionError error) {
                fail(owner, error);
            }
        }
        return total;
    }

    static boolean prepare(AbstractContraptionEntity owner) {
        Entry entry = OWNERS.get(owner);
        if (entry == null) return false;
        try {
            entry.driver.prepare();
            return entry.driver.replacesNativeFloor();
        } catch (RuntimeException | AssertionError error) {
            fail(owner, error);
            return false;
        }
    }

    static void updated(AbstractContraptionEntity owner) {
        Entry entry = OWNERS.get(owner);
        if (entry != null)
            try {
                entry.driver.updated();
            } catch (RuntimeException | AssertionError error) {
                fail(owner, error);
            }
    }

    static boolean covers(AbstractContraptionEntity owner, BlockPos position) {
        Entry entry = OWNERS.get(owner);
        if (entry == null) return false;
        try {
            return entry.driver.covers(position);
        } catch (RuntimeException | AssertionError error) {
            fail(owner, error);
            return false;
        }
    }

    static void tick(MinecraftServer server) {
        if (server.getPort() != 25566) return;
        for (UUID id : new ArrayList<>(PLAYERS.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            AbstractContraptionEntity owner = PLAYERS.get(id);
            Entry entry = OWNERS.get(owner);
            if (player == null) {
                PLAYERS.remove(id);
                continue;
            }
            if (entry == null || owner.isRemoved() || player.level() != owner.level()) {
                release(player);
                continue;
            }
            try {
                entry.driver.control(player);
            } catch (RuntimeException | AssertionError error) {
                fail(owner, error);
            }
        }
    }

    private static void fail(AbstractContraptionEntity owner, Throwable error) {
        Create.LOGGER.warn("Lab contraption trial disabled after failure; restoring default controller", error);
        remove(owner);
    }

    public static void remove(AbstractContraptionEntity owner) {
        Entry entry = OWNERS.get(owner);
        if (entry == null) return;
        if (!(owner.level() instanceof ServerLevel world)
                || !world.getServer().isSameThread()
                || world.getServer().getPort() != 25566) throw new IllegalStateException("Lab server thread required");
        for (UUID id : new ArrayList<>(PLAYERS.keySet()))
            if (PLAYERS.get(id) == owner) {
                ServerPlayer p = world.getServer().getPlayerList().getPlayer(id);
                if (p == null) PLAYERS.remove(id);
                else release(p);
            }
        OWNERS.remove(owner);
        try {
            entry.driver.close();
        } catch (RuntimeException | AssertionError error) {
            Create.LOGGER.warn("Lab trial cleanup failed", error);
        } finally {
            PolymerContraptionFloors.restoreTrial(owner, entry.previouslyDisabled);
        }
    }

    static void clear() {
        for (AbstractContraptionEntity owner : new ArrayList<>(OWNERS.keySet())) remove(owner);
        PLAYERS.clear();
    }
}
