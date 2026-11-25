package com.zurrtum.create.infrastructure.polymer.block;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.content.schematics.cannon.SchematicannonBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

final class PolymerCannonParts {
    private static final Identifier BASE = PolymerBlockParts.model("polymer/schematicannon");
    private static final Identifier CONNECTOR = PolymerBlockParts.model("polymer/schematicannon_connector");
    private static final Identifier PIPE = PolymerBlockParts.model("polymer/schematicannon_pipe");

    static void registerAll() {
        PolymerBlockParts.<SchematicannonBlockEntity>register(PolymerCannonParts::collect, AllBlocks.SCHEMATICANNON);
    }

    private static void collect(
            SchematicannonBlockEntity be, BlockState state, float time, List<PolymerBlockParts.Part> out) {
        float yaw = Mth.DEG_TO_RAD * (be.defaultYaw + 90), pitch = Mth.DEG_TO_RAD * 40;
        BlockPos target = be.printer.getCurrentTarget();
        if (target != null) {
            BlockPos diff = target.subtract(be.getBlockPos());
            yaw = (float) (Mth.atan2(diff.getX(), diff.getZ()) + Mth.HALF_PI);
            float distance =
                    Mth.sqrt((float) ((double) diff.getX() * diff.getX() + (double) diff.getZ() * diff.getZ()));
            pitch = (float) (Mth.atan2(distance, diff.getY() * 3 + distance * 2) + Mth.DEG_TO_RAD * 10);
        }
        float recoil = 0;
        for (var launched : be.flyingBlocks)
            if (launched.ticksRemaining != 0)
                recoil = Math.max(recoil, launched.ticksRemaining - launched.totalTicks + 10);
        out.add(PolymerBlockParts.part(BASE, new Matrix4f()));
        out.add(PolymerBlockParts.part(
                CONNECTOR, new Matrix4f().translate(.5f, 0, .5f).rotateY(yaw).translate(-.5f, 0, -.5f)));
        out.add(PolymerBlockParts.part(
                PIPE,
                new Matrix4f()
                        .translate(.5f, .9375f, .5f)
                        .rotateY(yaw)
                        .rotateZ(pitch)
                        .translate(-.5f, -recoil / 100 - .9375f, -.5f)));
    }
}
