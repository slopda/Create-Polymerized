package com.zurrtum.create.infrastructure.polymer;

import com.zurrtum.create.content.contraptions.AbstractContraptionEntity;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerContraptionLab;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class PolymerContraptionLabHooks implements ModInitializer, PolymerContraptionLab.Hooks {
    @Override
    public void onInitialize() {
        PolymerContraptionLab.install(this);
    }

    @Override
    public boolean prepare(AbstractContraptionEntity owner) {
        return PolymerContraptionTrialGate.prepare(owner);
    }

    @Override
    public void updated(AbstractContraptionEntity owner) {
        PolymerContraptionTrialGate.updated(owner);
    }

    @Override
    public void tick(MinecraftServer server) {
        PolymerContraptionTrialGate.tick(server);
    }

    @Override
    public boolean covers(AbstractContraptionEntity owner, BlockPos position) {
        return PolymerContraptionTrialGate.covers(owner, position);
    }

    @Override
    public boolean controls(ServerPlayer player) {
        return PolymerContraptionTrialGate.controls(player);
    }

    @Override
    public boolean active(AbstractContraptionEntity owner) {
        return PolymerContraptionTrialGate.active(owner);
    }

    @Override
    public int nativeHelperCount(MinecraftServer server) {
        return PolymerContraptionTrialGate.nativeHelperCount(server);
    }

    @Override
    public void display(AbstractContraptionEntity owner, DisplayElement display) {
        PolymerContraptionRenderSettings.apply(owner, display);
    }

    @Override
    public void removed(AbstractContraptionEntity owner) {
        PolymerContraptionTrialGate.remove(owner);
        PolymerContraptionRenderSettings.remove(owner);
    }

    @Override
    public void stopping() {
        PolymerContraptionTrialGate.clear();
        PolymerContraptionRenderSettings.clear();
    }
}
