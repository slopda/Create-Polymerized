package com.zurrtum.create.infrastructure.polymer.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.Command;
import com.zurrtum.create.Create;
import com.zurrtum.create.infrastructure.polymer.AdapterInteractions;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerGogglesOverlay;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import dev.veeda.createpoly.assets.CreateIds;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

public final class PolymerScreenEditors {
    private static final Identifier ACTION = CreateIds.id("polymer_screen_editors");
    private static final int GOGGLE_X_LIMIT = 400;
    private static final int PREVIEW_TIMEOUT = 20 * 60;

    private static final class Session {
        final String token = UUID.randomUUID().toString();
        int revision;
        int lastAction;

        Session(int now) {
            this.lastAction = now;
        }
    }

    private static final Map<UUID, Session> SESSIONS = PolymerDialogSessions.states();
    private static final Map<UUID, PolymerGogglesOverlay.Offset> OFFSETS = new ConcurrentHashMap<>();
    private static @Nullable Path offsetsFile;

    private PolymerScreenEditors() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("createfly")
                        .then(Commands.literal("overlay")
                                .executes(AdapterInteractions.command(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    if (!PolymerNativeClients.isNative(player)) openGoggles(player);
                                    return Command.SINGLE_SUCCESS;
                                }))
                                .then(Commands.literal("reset").executes(AdapterInteractions.command(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    setOffset(player, new PolymerGogglesOverlay.Offset(0, 0));
                                    player.sendSystemMessage(Component.literal(
                                            "Create Goggle Overlay has been reset to default position"));
                                    return Command.SINGLE_SUCCESS;
                                }))))));
        ServerLifecycleEvents.SERVER_STARTED.register(PolymerScreenEditors::loadOffsets);
        ServerTickEvents.END_SERVER_TICK.register(PolymerScreenEditors::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            SESSIONS.remove(handler.player.getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SESSIONS.clear();
            OFFSETS.clear();
            offsetsFile = null;
        });
    }

    public static PolymerGogglesOverlay.Offset goggleOffset(ServerPlayer player) {
        return OFFSETS.getOrDefault(player.getUUID(), PolymerGogglesOverlay.Offset.DEFAULT);
    }

    public static @Nullable List<Component> gogglePreview(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return null;
        return List.of(
                Component.literal("    Hi :)"),
                Component.literal("    This is a sample overlay").withStyle(ChatFormatting.GRAY),
                Component.empty(),
                Component.literal("    Use the buttons"),
                Component.literal("    to move this preview"),
                Component.empty(),
                Component.literal("    Press Done or ESC to exit").withStyle(ChatFormatting.GRAY),
                Component.literal("    and save the new position").withStyle(ChatFormatting.GRAY),
                Component.empty(),
                Component.literal("    Run /createfly overlay reset"),
                Component.literal("    to reset to the default position"));
    }

    private static void openGoggles(ServerPlayer player) {
        Session session = new Session(player.level().getServer().getTickCount());
        SESSIONS.put(player.getUUID(), session);
        PolymerDialogForms.open(player, gogglesDialog(player, session));
    }

    private static Holder<Dialog> gogglesDialog(ServerPlayer player, Session session) {
        PolymerGogglesOverlay.Offset offset = goggleOffset(player);
        Component body = Component.literal("Overlay offset: X " + offset.x() + " px, Y "
                + offset.k() * PolymerGogglesOverlay.Y_STEP + " px\nVertical moves use "
                + PolymerGogglesOverlay.Y_STEP + " px steps.");
        List<ActionButton> buttons = List.of(
                button(session, "◀ 10", "left10", 60), button(session, "◀ 1", "left1", 60),
                button(session, "1 ▶", "right1", 60), button(session, "10 ▶", "right10", 60),
                button(session, "▲", "up", 60), button(session, "▼", "down", 60));
        return dialog(Component.literal("Goggle Overlay"), body, List.of(), buttons, done(session, "Done"), 2);
    }

    private static void gogglesAction(ServerPlayer player, Session session, String operation) {
        PolymerGogglesOverlay.Offset offset = goggleOffset(player);
        int x = offset.x(), k = offset.k();
        switch (operation) {
            case "left10" -> x -= 10;
            case "left1" -> x -= 1;
            case "right1" -> x += 1;
            case "right10" -> x += 10;
            case "up" -> k -= 1;
            case "down" -> k += 1;
            case "done" -> {
                close(player);
                return;
            }
            default -> {
                return;
            }
        }
        setOffset(
                player,
                new PolymerGogglesOverlay.Offset(
                        Mth.clamp(x, -GOGGLE_X_LIMIT, GOGGLE_X_LIMIT),
                        Mth.clamp(k, -PolymerGogglesOverlay.Y_STEPS, PolymerGogglesOverlay.Y_STEPS)));
        reopen(player, session, gogglesDialog(player, session));
    }

    private static void setOffset(ServerPlayer player, PolymerGogglesOverlay.Offset offset) {
        if (offset.equals(PolymerGogglesOverlay.Offset.DEFAULT)) OFFSETS.remove(player.getUUID());
        else OFFSETS.put(player.getUUID(), offset);
        saveOffsets();
    }

    private static void loadOffsets(MinecraftServer server) {
        offsetsFile =
                server.getWorldPath(LevelResource.ROOT).resolve("polymerpatch").resolve("goggle_overlay.json");
        OFFSETS.clear();
        if (!Files.isRegularFile(offsetsFile)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(offsetsFile, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (var entry : root.entrySet()) {
                JsonObject value = entry.getValue().getAsJsonObject();
                OFFSETS.put(
                        UUID.fromString(entry.getKey()),
                        new PolymerGogglesOverlay.Offset(
                                Mth.clamp(value.get("x").getAsInt(), -GOGGLE_X_LIMIT, GOGGLE_X_LIMIT),
                                Mth.clamp(
                                        value.get("k").getAsInt(),
                                        -PolymerGogglesOverlay.Y_STEPS,
                                        PolymerGogglesOverlay.Y_STEPS)));
            }
        } catch (IOException | RuntimeException exception) {
            Create.LOGGER.warn("Ignoring unreadable goggle overlay offsets {}", offsetsFile, exception);
        }
    }

    private static void saveOffsets() {
        if (offsetsFile == null) return;
        JsonObject root = new JsonObject();
        OFFSETS.forEach((id, offset) -> {
            JsonObject value = new JsonObject();
            value.addProperty("x", offset.x());
            value.addProperty("k", offset.k());
            root.add(id.toString(), value);
        });
        try {
            Files.createDirectories(offsetsFile.getParent());
            Path temporary = offsetsFile.resolveSibling(offsetsFile.getFileName() + ".tmp");
            Files.writeString(temporary, root.toString(), StandardCharsets.UTF_8);
            Files.move(temporary, offsetsFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            Create.LOGGER.warn("Could not save goggle overlay offsets {}", offsetsFile, exception);
        }
    }

    private static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        SESSIONS.entrySet().removeIf(entry -> {
            ServerPlayer player = NativePresentationPolicy.presentationPlayer(server.getPlayerList(), entry.getKey());
            return player == null || now - entry.getValue().lastAction > PREVIEW_TIMEOUT;
        });
    }

    public static boolean handleCustomAction(
            ServerGamePacketListenerImpl listener, ServerboundCustomClickActionPacket packet) {
        if (!PolymerDialogForms.validSubmission(listener.player, packet.id(), packet.payload(), ACTION)) return true;
        if (!ACTION.equals(packet.id())) return false;
        ServerPlayer player = listener.player;
        Session session = SESSIONS.get(player.getUUID());
        CompoundTag tag = PolymerDialogForms.payload(packet.payload()).orElse(null);
        if (session == null
                || tag == null
                || !tag.getString("session").orElse("").equals(session.token)
                || tag.getInt("revision").orElse(-1) != session.revision) return true;
        session.lastAction = player.level().getServer().getTickCount();
        String operation = tag.getString("operation").orElse("");
        gogglesAction(player, session, operation);
        return true;
    }

    private static void reopen(ServerPlayer player, Session session, Holder<Dialog> dialog) {
        session.revision++;
        PolymerDialogForms.open(player, dialog);
    }

    private static void close(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
        NativePresentationPolicy.sendPresentation(player, ClientboundClearDialogPacket.INSTANCE);
    }

    private static CompoundTag payload(Session session, String operation) {
        CompoundTag tag = new CompoundTag();
        tag.putString("session", session.token);
        tag.putInt("revision", session.revision);
        tag.putString("operation", operation);
        return tag;
    }

    private static ActionButton button(Session session, String label, String operation, int width) {
        return new ActionButton(
                new CommonButtonData(Component.literal(label), width),
                Optional.of(new CustomAll(ACTION, Optional.of(payload(session, operation)))));
    }

    private static ActionButton done(Session session, String label) {
        return new ActionButton(
                new CommonButtonData(PolymerGuiTextures.actionLabel(label), 120),
                Optional.of(new CustomAll(ACTION, Optional.of(payload(session, "done")))));
    }

    private static Holder<Dialog> dialog(
            Component title,
            Component body,
            List<Input> inputs,
            List<ActionButton> buttons,
            ActionButton exit,
            int columns) {
        var common = new CommonDialogData(
                title,
                Optional.empty(),
                true,
                false,
                DialogAction.NONE,
                body.getString().isEmpty() ? List.of() : List.of(new PlainMessage(body, 260)),
                inputs);
        return Holder.direct(new MultiActionDialog(common, buttons, Optional.of(exit), columns));
    }
}
