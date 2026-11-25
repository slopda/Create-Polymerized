package com.zurrtum.create.infrastructure.polymer.contraption;

import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import java.util.function.ToIntFunction;
import net.minecraft.server.level.ServerPlayer;

public final class PolymerShiftCellLimit {
    public static int DEFAULT = 64;
    private static ToIntFunction<ServerPlayer> override;

    private PolymerShiftCellLimit() {}

    public static int limit(ServerPlayer player) {
        return PolymerNativeClients.isNative(player)
                ? PolymerShiftCarry.MAX_CELLS
                : override == null ? DEFAULT : orDefault(override.applyAsInt(player));
    }

    private static int orDefault(int trial) {
        return trial == 0 ? DEFAULT : trial;
    }

    public static void override(ToIntFunction<ServerPlayer> trial) {
        if (override != null) throw new IllegalStateException("Cell limit override already installed");
        override = trial;
    }
}
