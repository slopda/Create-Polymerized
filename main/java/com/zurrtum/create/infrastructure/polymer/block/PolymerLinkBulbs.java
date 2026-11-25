package com.zurrtum.create.infrastructure.polymer.block;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.AllSoundEvents;
import com.zurrtum.create.catnip.math.AngleHelper;
import com.zurrtum.create.content.redstone.displayLink.LinkWithBulbBlockEntity;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.Part;
import com.zurrtum.create.infrastructure.polymer.effects.PolymerVanillaEffects;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class PolymerLinkBulbs {
    private static final Identifier TUBE = PolymerBlockParts.model("display_link/tube"),
            GLOW = PolymerBlockParts.model("display_link/glow");
    private static final Map<LinkWithBulbBlockEntity, Long> PULSES = new WeakHashMap<>();

    private PolymerLinkBulbs() {}

    static void register() {
        PolymerBlockParts.<LinkWithBulbBlockEntity>register(
                PolymerLinkBulbs::collect, AllBlocks.DISPLAY_LINK, AllBlocks.STOCK_LINK);
    }

    public static synchronized void pulse(LinkWithBulbBlockEntity be) {
        if (be.getLevel() != null && !be.getLevel().isClientSide())
            PULSES.put(be, be.getLevel().getGameTime());
    }

    public static void wifi(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof LinkWithBulbBlockEntity be)) return;
        pulse(be);
        Vec3 at = Vec3.atCenterOf(pos);
        PolymerVanillaEffects.sound(
                level, at, AllSoundEvents.STOCK_LINK.getMainEvent(), SoundSource.BLOCKS, .75f, 1.25f);
        PolymerVanillaEffects.particles(level, ParticleTypes.ELECTRIC_SPARK, at, 3, .1, .1, .1, .02);
    }

    private static synchronized long pulsedAt(LinkWithBulbBlockEntity be) {
        Long at = PULSES.get(be);
        return at == null ? Long.MIN_VALUE / 2 : at;
    }

    private static void collect(LinkWithBulbBlockEntity be, BlockState state, float time, List<Part> out) {
        Direction face = be.getBulbFacing(state);
        Vec3 offset = be.getBulbOffset(state);
        Matrix4f pose = new Matrix4f()
                .translate(.5f, .5f, .5f)
                .rotateY(Mth.DEG_TO_RAD * (AngleHelper.horizontalAngle(face) + 180))
                .rotateX(Mth.DEG_TO_RAD * (-AngleHelper.verticalAngle(face) - 90))
                .translate(-.5f, -.5f, -.5f)
                .translate((float) offset.x, (float) offset.y, (float) offset.z);
        out.add(PolymerBlockParts.part(TUBE, pose).identified("bulb:tube"));
        double glow = 2 * Math.pow(.5, time - pulsedAt(be));
        if (glow >= .125)
            out.add(PolymerBlockParts.part(GLOW, new Matrix4f(pose)).identified("bulb:glow"));
    }
}
