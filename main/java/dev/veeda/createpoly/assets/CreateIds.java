package dev.veeda.createpoly.assets;

import net.minecraft.resources.Identifier;

public final class CreateIds {
    private CreateIds() {}

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("create", path);
    }
}
