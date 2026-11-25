package com.zurrtum.create.infrastructure.polymer;

public final class PolymerBedrockItemPose {
    public static final String HOOK = "polymerpatch.itemHolderPose.v2";
    public static final int GLINT_BIT = 2048;
    public static final int MAX_TEXTURES = GLINT_BIT - 1;

    private PolymerBedrockItemPose() {}

    public static int visualIcon(int textureSlot, boolean glint) {
        if (textureSlot < 0 || textureSlot > MAX_TEXTURES)
            throw new IllegalArgumentException("Invalid icon texture slot");
        return textureSlot == 0 ? 0 : textureSlot | (glint ? GLINT_BIT : 0);
    }

    public static boolean hasIcon(long pose) {
        return (pose >>> 31 & 4095) != 0;
    }

    public static float carrierScale(long pose, float sourceScale) {
        return hasIcon(pose) ? 1f : sourceScale;
    }

    public static long withIcon(long original, int icon, int scaleMillis) {
        if (icon < 0 || icon > 4095 || scaleMillis < 0 || scaleMillis > 65535)
            throw new IllegalArgumentException("Invalid icon pose");
        return original & 0x7fffffffL | (long) icon << 31 | (long) (icon == 0 ? 0 : scaleMillis) << 43;
    }
}
