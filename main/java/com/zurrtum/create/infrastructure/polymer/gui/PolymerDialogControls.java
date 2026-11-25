package com.zurrtum.create.infrastructure.polymer.gui;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.PlainMessage;

final class PolymerDialogControls {
    private PolymerDialogControls() {}

    static Holder<Dialog> form(
            String title, Component description, List<Input> inputs, List<ActionButton> actions, ActionButton cancel) {
        var common = new CommonDialogData(
                Component.literal(title),
                Optional.empty(),
                true,
                false,
                DialogAction.CLOSE,
                List.of(new PlainMessage(description, 300)),
                inputs);
        return Holder.direct(new MultiActionDialog(common, actions, Optional.of(cancel), 1));
    }

    static ActionButton button(
            String label, Identifier action, String session, int revision, String operation, int row) {
        CompoundTag additions = new CompoundTag();
        additions.putString("session", session);
        additions.putInt("revision", revision);
        additions.putString("operation", operation);
        additions.putInt("row", row);
        return new ActionButton(
                new CommonButtonData(PolymerGuiTextures.actionLabel(label), 240),
                Optional.of(new CustomAll(action, Optional.of(additions))));
    }

    static Input text(String key, String label, String initial, int maxLength) {
        return PolymerDialogForms.input(
                key,
                PolymerDialogForms.textControl(
                        240, Component.literal(label), true, initial, maxLength, Optional.empty()));
    }

    static boolean matches(CompoundTag payload, String session, int revision) {
        return payload.getString("session").filter(session::equals).isPresent()
                && payload.getInt("revision").filter(value -> value == revision).isPresent();
    }
}
