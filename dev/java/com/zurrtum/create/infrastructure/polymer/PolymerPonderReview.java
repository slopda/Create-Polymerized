package com.zurrtum.create.infrastructure.polymer;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.JavaOps;
import com.zurrtum.create.Create;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerHudRadial;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import com.zurrtum.create.infrastructure.polymer.ponder.PolymerPonderScenes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.CommandTemplate;
import net.minecraft.server.dialog.action.ParsedTemplate;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

public final class PolymerPonderReview implements ModInitializer {
    private static final int OPEN_DELAY = 5;

    private static final class Session {
        int index;
        int openAt = -1, openedAt;
        boolean wasShift;
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<String, JsonObject> RESULTS = new LinkedHashMap<>();
    private static Path resultsFile;

    private static List<PolymerPonderScenes.Summary> scenes() {
        return PolymerPonderScenes.summaries();
    }

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(Commands.literal("pr")
                        .executes(c -> run(c, player -> {
                            Session session = SESSIONS.get(player.getUUID());
                            if (session == null) start(player, firstUnrated());
                            else panel(player, session);
                        }))
                        .then(Commands.literal("start").executes(c -> run(c, player -> start(player, firstUnrated()))))
                        .then(Commands.literal("restart").executes(c -> run(c, player -> start(player, 0))))
                        .then(Commands.literal("open")
                                .executes(c -> run(c, player -> withSession(player, s -> go(player, s, s.index)))))
                        .then(Commands.literal("next")
                                .executes(c -> run(c, player -> withSession(player, s -> advance(player, s, 1)))))
                        .then(Commands.literal("back")
                                .executes(c -> run(c, player -> withSession(player, s -> advance(player, s, -1)))))
                        .then(Commands.literal("stop").executes(c -> run(c, PolymerPonderReview::stop)))
                        .then(Commands.literal("list").executes(c -> run(c, PolymerPonderReview::list)))
                        .then(Commands.literal("wrong").executes(c -> run(c, PolymerPonderReview::listWrong)))
                        .then(Commands.literal("goto")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            scenes().forEach(s -> b.suggest(s.id()));
                                            return b.buildFuture();
                                        })
                                        .executes(c -> run(c, player -> {
                                            int index = indexOf(StringArgumentType.getString(c, "id"));
                                            if (index < 0) {
                                                player.sendSystemMessage(Component.literal("Unknown Ponder scene id")
                                                        .withStyle(ChatFormatting.RED));
                                                return;
                                            }
                                            start(player, index);
                                        }))))
                        .then(Commands.literal("mark").then(mark("correct")).then(mark("wrong")))
                        .then(Commands.literal("frame")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("tick", IntegerArgumentType.integer(0))
                                                .executes(c -> run(c, player -> {
                                                    String id = StringArgumentType.getString(c, "id");
                                                    int tick = IntegerArgumentType.getInteger(c, "tick");
                                                    if (!PolymerPonderScenes.openAt(player, id, tick))
                                                        player.sendSystemMessage(Component.literal(
                                                                        "PONDER-FRAME-FAILED " + id + " " + tick)
                                                                .withStyle(ChatFormatting.RED));
                                                })))))));
        ServerLifecycleEvents.SERVER_STARTED.register(PolymerPonderReview::load);
        ServerTickEvents.END_SERVER_TICK.register(PolymerPonderReview::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SESSIONS.remove(handler.player.getUUID()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> mark(String verdict) {
        return Commands.literal(verdict)
                .executes(c -> run(c, player -> withSession(player, s -> rate(player, s, verdict, ""))))
                .then(Commands.argument("note", StringArgumentType.greedyString())
                        .executes(c -> run(
                                c,
                                player -> withSession(player, s -> {
                                    String note = StringArgumentType.getString(c, "note");
                                    rate(
                                            player,
                                            s,
                                            verdict,
                                            note.startsWith("n:")
                                                    ? note.substring(2).trim()
                                                    : note.trim());
                                }))));
    }

    private static void rate(ServerPlayer player, Session session, String verdict, String note) {
        record(player, scenes().get(session.index), verdict, note);
        advance(player, session, 1);
    }

    @FunctionalInterface
    private interface PlayerAction {
        void run(ServerPlayer player) throws Exception;
    }

    private static int run(CommandContext<CommandSourceStack> context, PlayerAction action) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null
                || PolymerNativeClients.isNative(player)
                || !player.level().getServer().getPlayerList().isOp(player.nameAndId())) return 0;
        try {
            action.run(player);
        } catch (Exception failure) {
            Create.LOGGER.warn("Ponder review command failed", failure);
            player.sendSystemMessage(
                    Component.literal("Ponder review error: " + failure).withStyle(ChatFormatting.RED));
        }
        return Command.SINGLE_SUCCESS;
    }

    @FunctionalInterface
    private interface SessionAction {
        void run(Session session) throws Exception;
    }

    private static void withSession(ServerPlayer player, SessionAction action) throws Exception {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null)
            player.sendSystemMessage(Component.literal("No Ponder review running. Use /pr start.")
                    .withStyle(ChatFormatting.RED));
        else action.run(session);
    }

    private static int indexOf(String id) {
        List<PolymerPonderScenes.Summary> all = scenes();
        for (int i = 0; i < all.size(); i++) if (all.get(i).id().equals(id)) return i;
        return -1;
    }

    private static int firstUnrated() {
        List<PolymerPonderScenes.Summary> all = scenes();
        for (int i = 0; i < all.size(); i++) if (!RESULTS.containsKey(all.get(i).id())) return i;
        return 0;
    }

    private static void start(ServerPlayer player, int index) {
        Session session = SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session());
        go(player, session, index);
    }

    private static void advance(ServerPlayer player, Session session, int step) {
        int next = session.index + step;
        if (next >= scenes().size()) {
            player.sendSystemMessage(Component.literal("All " + scenes().size() + " Ponder scenes visited.")
                    .withStyle(ChatFormatting.GREEN));
            summary(player);
            stop(player);
            return;
        }
        go(player, session, Math.max(next, 0));
    }

    private static void go(ServerPlayer player, Session session, int index) {
        NativePresentationPolicy.sendPresentation(player, ClientboundClearDialogPacket.INSTANCE);
        player.closeContainer();
        session.index = index;
        session.openAt = player.level().getServer().getTickCount() + OPEN_DELAY;
        announce(player, index, scenes().get(index));
    }

    private static void announce(ServerPlayer player, int index, PolymerPonderScenes.Summary scene) {
        JsonObject previous = RESULTS.get(scene.id());
        MutableComponent line = Component.literal("[" + (index + 1) + "/" + scenes().size() + "] ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(scene.title()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("  " + scene.id() + " (" + scene.item() + ")")
                        .withStyle(ChatFormatting.GRAY));
        if (previous != null)
            line.append(Component.literal("  was " + previous.get("verdict").getAsString())
                    .withStyle(
                            "correct".equals(previous.get("verdict").getAsString())
                                    ? ChatFormatting.GREEN
                                    : ChatFormatting.RED));
        player.sendSystemMessage(line);
        player.sendSystemMessage(Component.empty()
                .append(chatButton("[Correct]", "pr mark correct", ChatFormatting.GREEN))
                .append(" ")
                .append(chatButton("[Wrong + note]", "pr", ChatFormatting.RED))
                .append(" ")
                .append(chatButton("[Replay]", "pr open", ChatFormatting.GRAY))
                .append(" ")
                .append(chatButton("[Skip]", "pr next", ChatFormatting.GRAY))
                .append(" ")
                .append(chatButton("[Back]", "pr back", ChatFormatting.GRAY)));
    }

    private static MutableComponent chatButton(String text, String command, ChatFormatting color) {
        return Component.literal(text)
                .withStyle(style -> style.withColor(color).withClickEvent(new ClickEvent.RunCommand(command)));
    }

    private static void stop(ServerPlayer player) {
        if (SESSIONS.remove(player.getUUID()) == null) return;
        NativePresentationPolicy.sendPresentation(player, ClientboundClearDialogPacket.INSTANCE);
        player.sendSystemMessage(
                Component.literal("Ponder review stopped. /pr start continues at the first unrated scene.")
                        .withStyle(ChatFormatting.GRAY));
    }

    private static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        int now = server.getTickCount();
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            Session session = SESSIONS.get(player.getUUID());
            if (session == null) continue;
            if (session.openAt >= 0 && now >= session.openAt) {
                session.openAt = -1;
                session.openedAt = now;
                PolymerPonderScenes.Summary scene = scenes().get(session.index);
                if (PolymerPonderScenes.open(player, scene.id()))
                    actionBar(player, "Watch, press Esc, then tap Shift to rate");
                else
                    player.sendSystemMessage(Component.literal("Could not open Ponder scene " + scene.id())
                            .withStyle(ChatFormatting.RED));
            }
            boolean shift = player.isShiftKeyDown();
            if (shift
                    && !session.wasShift
                    && session.openAt < 0
                    && now - session.openedAt > OPEN_DELAY
                    && player.containerMenu == player.inventoryMenu
                    && !PolymerHudRadial.isOpen(player)) panel(player, session);
            session.wasShift = shift;
        }
    }

    private static void actionBar(ServerPlayer player, String text) {
        NativePresentationPolicy.sendPresentation(
                player, new ClientboundSetActionBarTextPacket(Component.literal(text)));
    }

    private static void panel(ServerPlayer player, Session session) {
        PolymerPonderScenes.Summary scene = scenes().get(session.index);
        int correct = 0, wrong = 0;
        for (JsonObject result : RESULTS.values())
            if ("correct".equals(result.get("verdict").getAsString())) correct++;
            else wrong++;
        JsonObject previous = RESULTS.get(scene.id());
        String note =
                previous != null && previous.has("note") ? previous.get("note").getAsString() : "";
        String body = "Scene: " + scene.id() + "  (item " + scene.item() + ")"
                + "\nRated so far: " + correct + " correct, " + wrong + " wrong, of " + scenes().size()
                + (previous == null
                        ? ""
                        : "\nCurrent verdict: " + previous.get("verdict").getAsString());
        Input input = new Input(
                "note",
                new TextInput(
                        300, Component.literal("Note (what is wrong, and when)"), true, note, 200, Optional.empty()));
        var common = new CommonDialogData(
                Component.literal((session.index + 1) + "/" + scenes().size() + "  " + scene.title()),
                Optional.empty(),
                true,
                false,
                DialogAction.CLOSE,
                List.of(new PlainMessage(Component.literal(body), 300)),
                List.of(input));
        List<ActionButton> buttons = List.of(
                button("Correct", template("pr mark correct n:$(note)")),
                button("Wrong", template("pr mark wrong n:$(note)")),
                button("Skip", command("pr next")),
                button("Replay", command("pr open")),
                button("Back", command("pr back")),
                button("Stop", command("pr stop")));
        Holder<Dialog> dialog = Holder.direct(new MultiActionDialog(common, buttons, Optional.empty(), 3));
        player.openDialog(dialog);
    }

    private static ActionButton button(String label, Action action) {
        return new ActionButton(new CommonButtonData(Component.literal(label), 96), Optional.of(action));
    }

    private static Action command(String command) {
        return new StaticAction(new ClickEvent.RunCommand(command));
    }

    private static Action template(String template) {
        return new CommandTemplate(
                ParsedTemplate.CODEC.parse(JavaOps.INSTANCE, template).getOrThrow());
    }

    private static void record(ServerPlayer player, PolymerPonderScenes.Summary scene, String verdict, String note) {
        JsonObject result = new JsonObject();
        result.addProperty("title", scene.title());
        result.addProperty("item", scene.item());
        result.addProperty("verdict", verdict);
        result.addProperty("note", note);
        result.addProperty("player", player.getGameProfile().name());
        result.addProperty("time", Instant.now().toString());
        RESULTS.put(scene.id(), result);
        save();
        player.sendSystemMessage(
                Component.literal("Marked " + scene.id() + " " + verdict + (note.isEmpty() ? "" : ": " + note))
                        .withStyle("correct".equals(verdict) ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private static void summary(ServerPlayer player) {
        int correct = 0, wrong = 0;
        for (JsonObject result : RESULTS.values())
            if ("correct".equals(result.get("verdict").getAsString())) correct++;
            else wrong++;
        player.sendSystemMessage(Component.literal(correct + " correct, " + wrong + " wrong, "
                        + (scenes().size() - correct - wrong) + " unrated. /pr wrong lists the wrong ones.")
                .withStyle(ChatFormatting.YELLOW));
    }

    private static void list(ServerPlayer player) {
        for (PolymerPonderScenes.Summary scene : scenes()) line(player, scene);
        summary(player);
    }

    private static void listWrong(ServerPlayer player) {
        for (PolymerPonderScenes.Summary scene : scenes()) {
            JsonObject result = RESULTS.get(scene.id());
            if (result != null && "wrong".equals(result.get("verdict").getAsString())) line(player, scene);
        }
        summary(player);
    }

    private static void line(ServerPlayer player, PolymerPonderScenes.Summary scene) {
        JsonObject result = RESULTS.get(scene.id());
        String verdict = result == null ? "unrated" : result.get("verdict").getAsString();
        ChatFormatting color = result == null
                ? ChatFormatting.GRAY
                : "correct".equals(verdict) ? ChatFormatting.GREEN : ChatFormatting.RED;
        String note = result == null || result.get("note").getAsString().isEmpty()
                ? ""
                : "  " + result.get("note").getAsString();
        player.sendSystemMessage(Component.literal(scene.id() + ": " + verdict + note)
                .withStyle(style ->
                        style.withColor(color).withClickEvent(new ClickEvent.RunCommand("pr goto " + scene.id()))));
    }

    private static void exportScenes(MinecraftServer server) {
        JsonArray list = new JsonArray();
        for (PolymerPonderScenes.Summary scene : scenes()) {
            JsonObject row = new JsonObject();
            row.addProperty("id", scene.id());
            row.addProperty("item", scene.item());
            row.addProperty("title", scene.title());
            row.addProperty("index", scene.index());
            list.add(row);
        }
        Path file =
                server.getWorldPath(LevelResource.ROOT).resolve("polymerpatch").resolve("ponder_scenes.json");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(
                    file, new GsonBuilder().setPrettyPrinting().create().toJson(list), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            Create.LOGGER.warn("Could not write Ponder scene list {}", file, failure);
        }
    }

    private static void load(MinecraftServer server) {
        exportScenes(server);
        resultsFile =
                server.getWorldPath(LevelResource.ROOT).resolve("polymerpatch").resolve("ponder_review.json");
        RESULTS.clear();
        if (!Files.isRegularFile(resultsFile)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(resultsFile, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (var entry : root.entrySet())
                RESULTS.put(entry.getKey(), entry.getValue().getAsJsonObject());
        } catch (IOException | RuntimeException failure) {
            Create.LOGGER.warn("Ignoring unreadable Ponder review results {}", resultsFile, failure);
        }
    }

    private static void save() {
        if (resultsFile == null) return;
        JsonObject root = new JsonObject();
        RESULTS.forEach(root::add);
        try {
            Files.createDirectories(resultsFile.getParent());
            Path temporary = resultsFile.resolveSibling(resultsFile.getFileName() + ".tmp");
            Files.writeString(
                    temporary, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
            Files.move(temporary, resultsFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            Create.LOGGER.warn("Could not save Ponder review results {}", resultsFile, failure);
        }
    }
}
