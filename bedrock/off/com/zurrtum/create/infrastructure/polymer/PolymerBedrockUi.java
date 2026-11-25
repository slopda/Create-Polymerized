package com.zurrtum.create.infrastructure.polymer;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

public final class PolymerBedrockUi {
    private PolymerBedrockUi() {}

    public sealed interface Element permits Label, Dropdown, Slider, Toggle, Input {}

    public record Label(String text) implements Element {}

    public record Dropdown(String label, List<String> options, int selected) implements Element {}

    public record Slider(String label, float min, float max, float step, float value) implements Element {}

    public record Toggle(String label, boolean value) implements Element {}

    public record Input(String label, String placeholder, String value) implements Element {}

    public record Button(String text, @Nullable String image) {}

    public enum ContainerRoute {
        FILTER,
        LINKED_CONTROLLER
    }

    public static Component containerTitle(ContainerRoute route, Component title) {
        return title;
    }

    public static boolean available(@Nullable ServerPlayer player) {
        return false;
    }

    public static boolean custom(
            ServerPlayer player, String title, List<Element> elements, Consumer<List<Object>> onSubmit) {
        return false;
    }

    public static boolean simple(
            ServerPlayer player, String title, String content, List<Button> buttons, IntConsumer onClick) {
        return false;
    }
}
