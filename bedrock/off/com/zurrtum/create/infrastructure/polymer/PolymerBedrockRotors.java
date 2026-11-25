package com.zurrtum.create.infrastructure.polymer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

public final class PolymerBedrockRotors {
    public static boolean ENABLED = false;
    public static final int MODEL_COGWHEEL = -1,
            MODEL_LARGE_COGWHEEL = -1,
            MODEL_SHAFT = -1,
            MODEL_WATER_WHEEL = -1,
            MODEL_LARGE_WATER_WHEEL = -1,
            MODEL_LARGE_WATER_WHEEL_EXTENSION = -1,
            MODEL_FLYWHEEL = -1,
            MODEL_CRUSHING_WHEEL = -1;

    private PolymerBedrockRotors() {}

    public static int model(String name) {
        return -1;
    }

    public static int partRotor(@Nullable Identifier partModel) {
        return -1;
    }

    public static final class Rotor {
        public Rotor(BlockPos pos) {}

        public void update(
                ServerLevel level, BlockPos pos, Axis axis, int model, float speed, float angle, long gameTime) {}

        public void update(
                ServerLevel level,
                BlockPos pos,
                Axis axis,
                boolean flip,
                int model,
                float speed,
                float angle,
                long gameTime) {}

        public void destroy() {}
    }
}
