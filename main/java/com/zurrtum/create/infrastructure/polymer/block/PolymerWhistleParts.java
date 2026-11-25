package com.zurrtum.create.infrastructure.polymer.block;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.catnip.math.AngleHelper;
import com.zurrtum.create.content.decoration.steamWhistle.WhistleBlock;
import com.zurrtum.create.content.decoration.steamWhistle.WhistleBlockEntity;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.Part;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

final class PolymerWhistleParts {
    private static final Identifier SMALL = PolymerBlockParts.model("steam_whistle/small_mouth"),
            MEDIUM = PolymerBlockParts.model("steam_whistle/medium_mouth"),
            LARGE = PolymerBlockParts.model("steam_whistle/large_mouth");
    private static final Map<WhistleBlockEntity, Animation> ANIMATION = new WeakHashMap<>();

    private PolymerWhistleParts() {}

    static void register() {
        PolymerBlockParts.<WhistleBlockEntity>register(PolymerWhistleParts::collect, AllBlocks.STEAM_WHISTLE);
    }

    private static void collect(WhistleBlockEntity be, BlockState state, float time, List<Part> out) {
        var tank = be.getTank();
        boolean powered = be.isPowered()
                && tank != null
                && tank.boiler.isActive()
                && (tank.boiler.passiveHeat || tank.boiler.activeHeat > 0);
        Animation a = ANIMATION.computeIfAbsent(be, key -> new Animation());
        long tick = be.getLevel() == null ? (long) time : be.getLevel().getGameTime();
        long steps = a.lastTick == Long.MIN_VALUE ? 1 : Mth.clamp(tick - a.lastTick, 0, 20);
        a.lastTick = tick;
        for (long i = 0; i < steps; i++) {
            a.value = powered ? a.value + (1 - a.value) * .5f : Math.max(0, a.value - .4f);
        }
        WhistleBlock.WhistleSize size = state.getValue(WhistleBlock.SIZE);
        float offset = a.value;
        if (powered && a.value > .5f) offset -= (float) Math.sin(time / 8 * (2 * Mth.PI) * (4 - size.ordinal())) / 16;
        Identifier mouth = switch (size) {
            case LARGE -> LARGE;
            case MEDIUM -> MEDIUM;
            default -> SMALL;
        };
        Matrix4f pose = PolymerBlockParts.spin(
                        Direction.UP, AngleHelper.horizontalAngle(state.getValue(WhistleBlock.FACING)))
                .translate(0, offset * .25f, 0);
        out.add(PolymerBlockParts.part(mouth, pose).identified("whistle:mouth"));
    }

    private static final class Animation {
        float value;
        long lastTick = Long.MIN_VALUE;
    }
}
