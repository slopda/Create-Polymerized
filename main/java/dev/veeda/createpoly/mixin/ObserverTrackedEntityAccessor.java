package dev.veeda.createpoly.mixin;

import net.minecraft.server.level.ServerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public interface ObserverTrackedEntityAccessor {
    @Accessor("serverEntity")
    ServerEntity polymerpatch$observerTracker();
}
