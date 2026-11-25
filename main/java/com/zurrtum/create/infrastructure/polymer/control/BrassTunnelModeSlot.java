package com.zurrtum.create.infrastructure.polymer.control;

import net.minecraft.core.Direction;

public class BrassTunnelModeSlot extends CenteredSideValueBoxTransform {

    public BrassTunnelModeSlot() {
        super((state, d) -> d == Direction.UP);
    }

    @Override
    public int getOverrideColor() {
        return 0xFF592424;
    }
}
