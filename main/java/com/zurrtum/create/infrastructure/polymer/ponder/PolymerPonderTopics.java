package com.zurrtum.create.infrastructure.polymer.ponder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerDialogForms;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerDialogService;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerDialogSessions;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerMachineDialogs;
import dev.veeda.createpoly.PatchAssets;
import dev.veeda.createpoly.assets.CreateIds;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.level.ServerPlayer;

public final class PolymerPonderTopics {
    private PolymerPonderTopics() {}

    public static void register() {
        PolymerDialogSessions.bindStates(PONDER_SESSIONS);
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> LAST_PONDER_OUTPUT.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> LAST_PONDER_OUTPUT.clear());
    }

    public static final Identifier PONDER_ACTION_ID = CreateIds.id("polymer_ponder");
    private static final ResourceKey<Dialog> PONDER_DIALOG =
            ResourceKey.create(Registries.DIALOG, CreateIds.id("polymer_ponder"));
    private static final long PONDER_SESSION_LIFETIME_NANOS = TimeUnit.MINUTES.toNanos(5);
    private static final int PONDER_TOPICS_PER_PAGE = 10;
    public static final Map<UUID, Long> PONDER_SESSIONS = PolymerDialogSessions.states();
    static final Map<UUID, Long> LAST_PONDER_OUTPUT = new HashMap<>();
    private static final PonderCatalog PONDER_CATALOG = loadPonderCatalog();

    public static int openPonderDialog(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<Holder.Reference<Dialog>> dialog =
                player.registryAccess().lookupOrThrow(Registries.DIALOG).get(PONDER_DIALOG);
        if (dialog.isEmpty()) {
            return PolymerDialogService.fail(source, "create.polymer.ponder.error.dialog_missing");
        }

        long now = System.nanoTime();
        PONDER_SESSIONS.entrySet().removeIf(entry -> entry.getValue() <= now);
        PolymerMachineDialogs.clearSession(player.getUUID());
        PONDER_SESSIONS.put(player.getUUID(), now + PONDER_SESSION_LIFETIME_NANOS);
        PolymerDialogForms.open(player, dialog.get());
        return 1;
    }

    public static int listPonderTopics(CommandSourceStack source, int page) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        int pageCount =
                Math.max(1, (PONDER_CATALOG.topics().size() + PONDER_TOPICS_PER_PAGE - 1) / PONDER_TOPICS_PER_PAGE);
        if (page > pageCount) {
            return PolymerDialogService.fail(source, "create.polymer.ponder.error.page", pageCount);
        }

        player.sendSystemMessage(Component.translatable("create.polymer.ponder.list.title", page, pageCount));
        int start = (page - 1) * PONDER_TOPICS_PER_PAGE;
        PONDER_CATALOG.topics().values().stream()
                .skip(start)
                .limit(PONDER_TOPICS_PER_PAGE)
                .forEach(topic -> player.sendSystemMessage(
                        Component.literal("/createfly ponder " + topic.key() + " — " + topic.title())));
        return Math.min(PONDER_TOPICS_PER_PAGE, PONDER_CATALOG.topics().size() - start);
    }

    public static int showPonderTopic(CommandSourceStack source, String topicKey) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PonderTalk topic = PONDER_CATALOG.aliases().get(topicKey);
        if (topic == null && topicKey.startsWith("create:")) {
            topic = PONDER_CATALOG.aliases().get(topicKey.substring("create:".length()));
        }
        if (topic == null) {
            return PolymerDialogService.fail(source, "create.polymer.ponder.error.unknown_topic");
        }
        return sendPonderText(player, topic);
    }

    public static void handlePonderAction(ServerPlayer player, Optional<Tag> optionalPayload) {
        Long expiresAt = PONDER_SESSIONS.remove(player.getUUID());
        if (expiresAt == null || expiresAt <= System.nanoTime()) {
            player.sendSystemMessage(Component.translatable("create.polymer.ponder.error.expired"));
            return;
        }

        Optional<String> rawTopic =
                PolymerDialogForms.payload(optionalPayload).flatMap(payload -> payload.getString("topic"));
        if (rawTopic.isEmpty() || !rawTopic.get().matches("[a-z0-9_:/.-]{1,96}")) {
            player.sendSystemMessage(Component.translatable("create.polymer.ponder.error.unknown_topic"));
            return;
        }
        String topicKey = rawTopic.get();
        PonderTalk topic = PONDER_CATALOG.aliases().get(topicKey);
        if (topic == null && topicKey.startsWith("create:")) {
            topic = PONDER_CATALOG.aliases().get(topicKey.substring("create:".length()));
        }
        if (topic == null) {
            player.sendSystemMessage(Component.translatable("create.polymer.ponder.error.unknown_topic"));
            return;
        }
        sendPonderText(player, topic);
    }

    private static int sendPonderText(ServerPlayer player, PonderTalk topic) {
        long now = System.nanoTime();
        Long lastOutput = LAST_PONDER_OUTPUT.get(player.getUUID());
        if (lastOutput != null && now - lastOutput < TimeUnit.MILLISECONDS.toNanos(500)) {
            player.sendSystemMessage(Component.translatable("create.polymer.ponder.error.rate_limited"));
            return 0;
        }
        LAST_PONDER_OUTPUT.put(player.getUUID(), now);
        player.sendSystemMessage(Component.translatable("create.polymer.ponder.output.title", topic.title()));
        if (topic.messages().isEmpty()) {
            player.sendSystemMessage(Component.translatable("create.polymer.ponder.output.no_text"));
        }
        for (String message : topic.messages()) {
            player.sendSystemMessage(Component.literal("• " + message));
        }
        return topic.messages().size() + (topic.messages().isEmpty() ? 2 : 1);
    }

    private static PonderCatalog loadPonderCatalog() {
        InputStream resource = PatchAssets.open("/data/create/polymer_ponder_talks.json");
        if (resource == null) {
            throw new IllegalStateException("Missing bundled Polymer Ponder tutorial catalog");
        }

        Map<String, PonderTalk> topics = new LinkedHashMap<>();
        Map<String, PonderTalk> aliases = new HashMap<>();
        try (InputStream stream = resource;
                InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                JsonObject value = entry.getValue().getAsJsonObject();
                JsonArray messageArray = value.getAsJsonArray("messages");
                JsonArray aliasArray = value.getAsJsonArray("aliases");
                List<String> messages = new ArrayList<>(messageArray.size());
                messageArray.forEach(message -> messages.add(message.getAsString()));
                PonderTalk topic =
                        new PonderTalk(entry.getKey(), value.get("title").getAsString(), List.copyOf(messages));
                topics.put(topic.key(), topic);
                aliasArray.forEach(alias -> aliases.put(alias.getAsString(), topic));
            }
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Failed to read bundled Polymer Ponder tutorial catalog", exception);
        }
        if (topics.size() != 159) {
            throw new IllegalStateException("Expected 159 bundled Ponder topics, found " + topics.size());
        }
        return new PonderCatalog(Collections.unmodifiableMap(new LinkedHashMap<>(topics)), Map.copyOf(aliases));
    }

    private record PonderTalk(String key, String title, List<String> messages) {}

    private record PonderCatalog(Map<String, PonderTalk> topics, Map<String, PonderTalk> aliases) {}
}
