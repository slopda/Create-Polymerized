package com.zurrtum.create.infrastructure.polymer.block;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.content.fluids.spout.SpoutBlockEntity;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.Part;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

final class PolymerSpoutParts {
    private static final Identifier MIDDLE = PolymerBlockParts.model("spout/middle"),
            BOTTOM = PolymerBlockParts.model("spout/bottom");

    private PolymerSpoutParts() {}

    static void register() {
        PolymerBlockParts.<SpoutBlockEntity>register(PolymerSpoutParts::collect, AllBlocks.SPOUT);
    }

    private static void collect(SpoutBlockEntity be, BlockState state, float time, List<Part> out) {
        var tank = be.tank == null ? null : be.tank.getPrimaryTank();
        var fluid = tank == null ? null : tank.getFluid();
        float radius = 0, processing = be.processingTicks < 0 ? be.processingTicks : be.processingTicks - .5f;
        if (fluid != null && !fluid.isEmpty()) {
            float level = tank.getFluidLevel().getValue();
            if (level != 0) {
                float min = .15625f, n = .6875f, max = min + n, yOffset = n * Math.max(level, .175f);
                out.add(PolymerBlockParts.part(
                                PolymerBasinParts.fluidModel(fluid),
                                new Matrix4f().translate(min, min, min).scale(max - min, yOffset, max - min))
                        .identified("spout:tank"));
            }
            if (be.processingTicks != -1) {
                float progress = Mth.clamp(1 - (processing - 5) / 10, 0, 1);
                radius = (float) (Math.pow(2 * progress - 1, 2) - 1);
                float half = -radius / 32;
                if (half > 1e-4f)
                    out.add(PolymerBlockParts.part(
                                    PolymerBasinParts.fluidModel(fluid),
                                    new Matrix4f()
                                            .translate(.5f - half, -1.2f, .5f - half)
                                            .scale(2 * half, 1.2f, 2 * half))
                            .identified("spout:stream"));
            }
        }
        float squeeze =
                processing < 0 ? 0 : processing < 2 ? Mth.lerp(processing / 2, 0, -1) : processing < 10 ? -1 : radius;
        float bit = -3 * squeeze / 32;
        out.add(PolymerBlockParts.part(MIDDLE, new Matrix4f().translate(0, bit, 0))
                .identified("spout:middle"));
        out.add(PolymerBlockParts.part(BOTTOM, new Matrix4f().translate(0, 2 * bit, 0))
                .identified("spout:bottom"));
    }
}
