package com.zurrtum.create.infrastructure.polymer.gui;

import net.minecraft.server.level.ServerPlayer;

public final class PolymerFilterForms {
    private PolymerFilterForms() {}

    public static boolean openAttributeFilterSettings(ServerPlayer player) {
        return PolymerAttributeFilterGui.open(player);
    }

    public static boolean openListFilterSettings(ServerPlayer player) {
        return PolymerGhostItemGui.openFilter(player);
    }

    public static boolean openPackageFilterSettings(ServerPlayer player) {
        return PolymerPackageFilterGui.open(player);
    }
}
