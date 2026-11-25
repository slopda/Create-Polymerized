package com.zurrtum.create.infrastructure.polymer.equipment;

import java.util.EnumSet;
import java.util.Optional;

public final class ControlFreezeState {
    public enum Owner {
        TRAIN,
        LINKED_CONTROLLER
    }

    public record Speeds(float walking, float flying) {
        static final Speeds HELD = new Speeds(0, 0);

        Speeds restoreInto(Speeds current) {
            return new Speeds(
                    current.walking == 0 ? walking : current.walking, current.flying == 0 ? flying : current.flying);
        }
    }

    public interface PlayerState {
        Speeds speeds();

        void speeds(Speeds speeds);

        Optional<Speeds> saved();

        void save(Speeds speeds);

        void clearSaved();

        void modifiers(boolean held);

        void sync();
    }

    private final EnumSet<Owner> owners = EnumSet.noneOf(Owner.class);

    public void acquire(Owner owner, PlayerState player) {
        if (owners.contains(owner)) return;
        if (owners.isEmpty()) {
            recover(player);
            player.save(player.speeds());
            player.modifiers(true);
            player.speeds(Speeds.HELD);
            player.sync();
        }
        owners.add(owner);
    }

    public void release(Owner owner, PlayerState player) {
        if (!owners.contains(owner)) return;
        if (owners.size() == 1) recover(player);
        owners.remove(owner);
    }

    public boolean empty() {
        return owners.isEmpty();
    }

    public static void recover(PlayerState player) {
        Optional<Speeds> saved = player.saved();
        if (saved.isEmpty()) return;
        player.speeds(saved.get().restoreInto(player.speeds()));
        player.modifiers(false);
        player.sync();
        player.clearSaved();
    }
}
