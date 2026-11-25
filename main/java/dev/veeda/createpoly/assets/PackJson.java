package dev.veeda.createpoly.assets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import org.joml.Vector3f;

public final class PackJson {
    private PackJson() {}

    public static JsonObject object(byte[] utf8) {
        return JsonParser.parseString(new String(utf8, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    public static JsonArray array(float... values) {
        JsonArray array = new JsonArray();
        for (float value : values) array.add(value);
        return array;
    }

    public static JsonObject frame(int index, int time) {
        JsonObject frame = new JsonObject();
        frame.addProperty("index", index);
        frame.addProperty("time", time);
        return frame;
    }

    public static JsonObject firstVariant(JsonElement value) {
        return value.isJsonArray() ? value.getAsJsonArray().get(0).getAsJsonObject() : value.getAsJsonObject();
    }

    public static Vector3f vector3f(JsonArray array) {
        return new Vector3f(
                array.get(0).getAsFloat(),
                array.get(1).getAsFloat(),
                array.get(2).getAsFloat());
    }
}
