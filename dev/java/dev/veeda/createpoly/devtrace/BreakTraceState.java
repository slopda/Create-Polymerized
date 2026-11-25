package dev.veeda.createpoly.devtrace;

import net.minecraft.core.BlockPos;

public final class BreakTraceState {
    public static volatile BlockPos last = BlockPos.ZERO;
    public static volatile long until;

    private BreakTraceState() {}

    public static void mark(BlockPos pos) {
        last = pos.immutable();
        until = System.currentTimeMillis() + 2000;
    }

    public static boolean near(BlockPos pos) {
        return System.currentTimeMillis() < until && pos.distManhattan(last) <= 1;
    }
}
