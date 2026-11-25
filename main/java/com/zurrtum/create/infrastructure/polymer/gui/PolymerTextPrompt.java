package com.zurrtum.create.infrastructure.polymer.gui;

import dev.veeda.createpoly.assets.CreateIds;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

public final class PolymerTextPrompt {
    private static final Identifier ACTION = CreateIds.id("polymer_text_prompt");
    private static final PolymerDialogSessions<Pending> SESSIONS = new PolymerDialogSessions<>(Duration.ofMinutes(5));

    public record Field(Component label, String initial, int maxLength) {}

    private record Pending(List<PolymerDialogForms.TextField> fields, Consumer<List<String>> onSave) {}

    private PolymerTextPrompt() {}

    public static void open(ServerPlayer player, Component title, Field field, Consumer<String> onSave) {
        open(player, title, List.of(field), values -> onSave.accept(values.getFirst()));
    }

    static void open(ServerPlayer player, Component title, List<Field> fields, Consumer<List<String>> onSave) {
        SESSIONS.bindToServer();
        List<PolymerDialogForms.TextField> typed = new ArrayList<>();
        List<Input> inputs = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            Field field = fields.get(i);
            PolymerDialogForms.TextField text =
                    new PolymerDialogForms.TextField(Character.toString('a' + i), field.maxLength());
            typed.add(text);
            inputs.add(text.input(240, field.label(), field.initial()));
        }
        CompoundTag session = SESSIONS.open(player.getUUID(), new Pending(List.copyOf(typed), onSave));
        var common = new CommonDialogData(title, Optional.empty(), true, false, DialogAction.CLOSE, List.of(), inputs);
        Holder<Dialog> dialog = Holder.direct(new NoticeDialog(
                common,
                new ActionButton(
                        new CommonButtonData(PolymerGuiTextures.actionLabel("Confirm"), 200),
                        Optional.of(new CustomAll(ACTION, Optional.of(session))))));
        PolymerDialogForms.open(player, dialog, Map.of());
    }

    static boolean handleCustomAction(
            ServerGamePacketListenerImpl listener, ServerboundCustomClickActionPacket packet) {
        if (!ACTION.equals(packet.id())) return false;
        ServerPlayer player = listener.player;
        if (!PolymerDialogForms.validSubmission(player, packet.id(), packet.payload(), ACTION)) return true;
        CompoundTag data = PolymerDialogForms.payload(packet.payload()).orElse(null);
        if (data == null) return true;
        Pending pending = SESSIONS.consume(player.getUUID(), data).orElse(null);
        if (pending == null) return true;
        List<String> values = new ArrayList<>();
        for (PolymerDialogForms.TextField field : pending.fields()) {
            Optional<String> value = field.read(data);
            if (value.isEmpty()) return true;
            values.add(value.get());
        }
        pending.onSave().accept(List.copyOf(values));
        return true;
    }
}
