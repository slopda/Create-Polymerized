package com.zurrtum.create.infrastructure.polymer;

/** Wire layout for a prebuilt mesh: two 16-bit model indices and three 9-bit encoded angles. */
public final class PolymerBedrockMeshPose {
    private PolymerBedrockMeshPose() {}

    public static long encode(int geometry, int material, int[] angles) {
        if (geometry < 0 || geometry > 65535 || material < 0 || material > 65535 || angles.length != 3)
            throw new IllegalArgumentException("Invalid Bedrock mesh indices or angle count");
        for (int angle : angles)
            if (angle < 0 || angle >= 360) throw new IllegalArgumentException("Invalid Bedrock mesh angle " + angle);
        return (geometry & 0xFFFFL)
                | ((long) material << 16)
                | ((long) angles[0] << 32)
                | ((long) angles[1] << 41)
                | ((long) angles[2] << 50);
    }
}
