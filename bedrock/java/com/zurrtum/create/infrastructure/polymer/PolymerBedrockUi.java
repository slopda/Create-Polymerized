package com.zurrtum.create.infrastructure.polymer;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
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

    public interface Sender {
        boolean isBedrock(ServerPlayer player);

        boolean sendCustom(
                ServerPlayer player,
                String title,
                List<Element> elements,
                Consumer<List<Object>> onSubmit,
                Runnable onClose);

        boolean sendSimple(
                ServerPlayer player,
                String title,
                String content,
                List<Button> buttons,
                IntConsumer onClick,
                Runnable onClose);
    }

    /**
     * Container skins in the Bedrock pack's {@code ui/chest_screen.json}, keyed on a nonprinting token at the start of
     * the Java container title. The pack copies {@code $container_title} and tests for the token, the same predicate
     * Geyser's integrated pack uses for its row-count prefix ({@code §z§5§r} for 9x5), so slots, grids, focus and
     * input stay native. Tokens are formatting codes, not ASCII: Geyser's converter keeps consecutive {@code §z} codes
     * and strips them from the visible title. The pack matches the token without its trailing reset, because Geyser
     * may drop a trailing reset on an empty name. Keep in sync with chest_screen.json (geyser-bridge/tools/gui-checks).
     */
    public enum ContainerRoute {
        FILTER("§z§z§1"),
        LINKED_CONTROLLER("§z§z§2");

        public final String token;

        ContainerRoute(String token) {
            this.token = token;
        }
    }

    public static net.minecraft.network.chat.Component containerTitle(
            ContainerRoute route, net.minecraft.network.chat.Component title) {
        return net.minecraft.network.chat.Component.literal(route.token + "§r").append(title);
    }

    private static @Nullable Sender sender;

    public static void register(Sender value) {
        sender = value;
    }

    public static boolean available(@Nullable ServerPlayer player) {
        Sender current = sender;
        return current != null && player != null && current.isBedrock(player);
    }

    public static boolean custom(
            ServerPlayer player, String title, List<Element> elements, Consumer<List<Object>> onSubmit) {
        Sender current = sender;
        return current != null && current.sendCustom(player, title, elements, onSubmit, () -> {});
    }

    public static boolean simple(
            ServerPlayer player, String title, String content, List<Button> buttons, IntConsumer onClick) {
        Sender current = sender;
        return current != null && current.sendSimple(player, title, content, buttons, onClick, () -> {});
    }
}
