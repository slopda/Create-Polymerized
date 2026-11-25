package com.zurrtum.create.infrastructure.polymer.block;

import com.zurrtum.create.content.logistics.box.PackageItem;
import com.zurrtum.create.content.logistics.box.PackageStyles;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

final class PolymerChainModels {
    record PackageModels(Identifier box, Identifier rigging) {}

    private static final Identifier[][] LINKS = links();
    private static final Identifier[][] ANIMATED = animated();

    private static final Map<PackageItem, PackageModels> PACKAGES = packages();

    private static Identifier[][] links() {
        Identifier[][] result = new Identifier[16][16];
        for (int phase = 0; phase < 16; phase++)
            for (int length = 1; length <= 16; length++)
                result[phase][length - 1] = PolymerBlockParts.model("polymer/chain_scroll/" + phase + "/" + length);
        return result;
    }

    private static Identifier[][] animated() {
        Identifier[][] result = new Identifier[PolymerScrollAnimations.BUCKETS.length * 16][16];
        for (int bucket = 0; bucket < PolymerScrollAnimations.BUCKETS.length; bucket++)
            for (int offset = 0; offset < 16; offset++)
                for (int length = 1; length <= 16; length++)
                    result[bucket * 16 + offset][length - 1] =
                            PolymerBlockParts.model(PolymerScrollAnimations.chainModelPath(bucket, offset, length));
        return result;
    }

    private static Map<PackageItem, PackageModels> packages() {
        Map<PackageItem, PackageModels> result = new HashMap<>();
        for (PackageItem item : PackageStyles.ALL_BOXES)
            result.put(
                    item,
                    new PackageModels(
                            PolymerBlockParts.model(item.style.getModel()),
                            PolymerBlockParts.model(item.style.getRiggingModel())));
        return Map.copyOf(result);
    }

    static void register() {}

    static int lengthBucket(double length) {
        return Math.max(1, Math.min(16, (int) Math.ceil(length * 16 - 1e-6)));
    }

    static Identifier link(int phase, double length) {
        return phase < PolymerScrollAnimations.CHAIN_STATIC
                ? LINKS[phase][lengthBucket(length) - 1]
                : ANIMATED[phase - PolymerScrollAnimations.CHAIN_STATIC][lengthBucket(length) - 1];
    }

    static PackageModels box(ItemStack item) {
        return PACKAGES.get(item.getItem());
    }
}
