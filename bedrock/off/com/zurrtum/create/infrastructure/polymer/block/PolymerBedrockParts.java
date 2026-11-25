package com.zurrtum.create.infrastructure.polymer.block;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix4fc;

public final class PolymerBedrockParts {
    public static boolean DEBUG = false;

    private PolymerBedrockParts() {}

    static void tick(BlockEntity blockEntity, ServerLevel level) {}

    public static void ownedMesh(BlockEntity blockEntity, ServerLevel level, Identifier model, Matrix4fc transform) {}

    public static void removeOwnedMesh(BlockEntity blockEntity) {}

    static void remove(BlockEntity blockEntity) {}

    static void clear() {}
}
