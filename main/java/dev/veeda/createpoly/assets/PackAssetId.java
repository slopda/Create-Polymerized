package dev.veeda.createpoly.assets;

import java.util.Objects;
import java.util.regex.Pattern;

public record PackAssetId(String namespace, String path) {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");

    public PackAssetId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (!NAMESPACE.matcher(namespace).matches()
                || namespace.equals(".")
                || namespace.equals("..")
                || !PATH.matcher(path).matches())
            throw new IllegalArgumentException("Invalid resource ID: " + namespace + ":" + path);
        for (String segment : path.split("/", -1))
            if (segment.isEmpty() || segment.equals(".") || segment.equals(".."))
                throw new IllegalArgumentException("Invalid resource path: " + path);
    }

    public static PackAssetId parse(String reference) {
        Objects.requireNonNull(reference, "reference");
        int separator = reference.indexOf(':');
        if (separator < 1 || separator != reference.lastIndexOf(':'))
            throw new IllegalArgumentException("Expected namespace:path: " + reference);
        return new PackAssetId(reference.substring(0, separator), reference.substring(separator + 1));
    }

    public String reference() {
        return namespace + ":" + path;
    }

    public String modelPath() {
        return "assets/" + namespace + "/models/" + path + ".json";
    }

    public String texturePath() {
        return "assets/" + namespace + "/textures/" + path + ".png";
    }

    public String itemPath() {
        return "assets/" + namespace + "/items/" + path + ".json";
    }
}
