package com.zurrtum.create.infrastructure.polymer.gui;

import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.input.BooleanInput;
import net.minecraft.server.dialog.input.InputControl;
import net.minecraft.server.dialog.input.NumberRangeInput;
import net.minecraft.server.dialog.input.SingleOptionInput;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;

public final class PolymerDialogForms {
    private record Schema(List<Input> fields, Set<String> cancelOperations, Map<String, IntegerField> integers) {}

    private static final Map<UUID, Map<Identifier, Schema>> SCHEMAS = PolymerDialogSessions.states();

    private PolymerDialogForms() {}

    public static Optional<CompoundTag> payload(Optional<Tag> value) {
        return value.filter(CompoundTag.class::isInstance).map(CompoundTag.class::cast);
    }

    public static void open(ServerPlayer player, Holder<Dialog> holder) {
        open(player, holder, Map.of());
    }

    public static void open(ServerPlayer player, Holder<Dialog> holder, Map<String, IntegerField> integers) {
        if (!NativePresentationPolicy.allowsPresentation(player)) return;
        Dialog dialog = holder.value();
        integers.forEach((key, field) -> {
            if (!key.equals(field.key())
                    || dialog.common().inputs().stream()
                            .noneMatch(input -> input.key().equals(key)))
                throw new IllegalArgumentException("Integer rule has no matching native field: " + key);
        });
        var schemas = new HashMap<>(SCHEMAS.getOrDefault(player.getUUID(), Map.of()));
        List<ActionButton> actions = dialog instanceof NoticeDialog notice
                ? List.of(notice.action())
                : dialog instanceof MultiActionDialog multi
                        ? Stream.concat(multi.actions().stream(), multi.exitAction().stream())
                                .toList()
                        : List.of();
        Map<Identifier, Set<String>> cancellations = new HashMap<>();
        for (ActionButton button : actions)
            button.action().ifPresent(action -> {
                if (action instanceof CustomAll custom) {
                    var cancel = cancellations.computeIfAbsent(custom.id(), id -> new HashSet<>());
                    custom.additions()
                            .flatMap(data -> data.getString("operation"))
                            .filter(Set.of("cancel", "back", "close")::contains)
                            .ifPresent(cancel::add);
                }
            });
        cancellations.forEach((id, cancel) -> schemas.put(
                id, new Schema(List.copyOf(dialog.common().inputs()), Set.copyOf(cancel), Map.copyOf(integers))));
        if (!schemas.isEmpty()) SCHEMAS.put(player.getUUID(), Map.copyOf(schemas));
        player.openDialog(PolymerGuiPreviews.dialog(holder));
    }

    public static boolean validSubmission(
            ServerPlayer player, Identifier action, Optional<Tag> value, Identifier... ownedActions) {
        for (Identifier owned : ownedActions) if (owned.equals(action)) return validSubmission(player, action, value);
        return true;
    }

    public static boolean validSubmission(ServerPlayer player, Identifier action, Optional<Tag> value) {
        if (!NativePresentationPolicy.allowsAction(player)) return false;
        Schema schema = SCHEMAS.getOrDefault(player.getUUID(), Map.of()).get(action);
        if (schema == null || schema.fields().isEmpty()) return true;
        var payload = payload(value);
        if (payload.isEmpty()) return false;
        CompoundTag data = payload.get();
        String operation = data.getStringOr("operation", "");
        if (schema.cancelOperations().contains(operation)) return true;
        return schema.fields().stream()
                .allMatch(field -> schema.integers().containsKey(field.key())
                        ? schema.integers().get(field.key()).read(data).isPresent()
                        : validValue(field, data));
    }

    static boolean validValue(Input field, CompoundTag data) {
        Tag value = data.get(field.key());
        var control = field.control();
        if (control instanceof TextInput text) {
            return value instanceof StringTag string
                    && string.value().length() <= text.maxLength()
                    && string.value()
                            .codePoints()
                            .noneMatch(cp -> Character.isISOControl(cp)
                                    && !(text.multiline().isPresent() && (cp == '\n' || cp == '\r' || cp == '\t')));
        }
        if (control instanceof SingleOptionInput options)
            return value instanceof StringTag string
                    && options.entries().stream().anyMatch(entry -> entry.id().equals(string.value()));
        if (control instanceof BooleanInput bool)
            return value instanceof StringTag string
                    && (bool.onTrue().equals(string.value()) || bool.onFalse().equals(string.value()));
        if (control instanceof NumberRangeInput number) {
            double submitted;
            if (value instanceof NumericTag numeric) submitted = numeric.doubleValue();
            else if (value instanceof StringTag string) {
                try {
                    submitted = Double.parseDouble(string.value());
                } catch (NumberFormatException invalid) {
                    return false;
                }
            } else return false;
            var range = number.rangeInfo();
            if (!Double.isFinite(submitted)
                    || submitted < Math.min(range.start(), range.end())
                    || submitted > Math.max(range.start(), range.end())) return false;
            if (range.step().isPresent() && range.step().get() > 0) {
                double units = (submitted - range.start()) / range.step().get();
                if (Math.abs(units - Math.rint(units)) > 0.0001) return false;
            }
            return true;
        }
        return false;
    }

    public static Input input(String key, InputControl control) {
        validKey(key);
        return new Input(key, Objects.requireNonNull(control));
    }

    public static TextInput textControl(
            int width,
            Component label,
            boolean labelVisible,
            String initial,
            int maxLength,
            Optional<TextInput.MultilineOptions> multiline) {
        Objects.requireNonNull(initial);
        if (maxLength < 1) throw new IllegalArgumentException("Text bound must be positive");
        StringBuilder shown = new StringBuilder();
        for (int offset = 0; offset < initial.length(); ) {
            int cp = initial.codePointAt(offset);
            offset += Character.charCount(cp);
            if (Character.isISOControl(cp) && !(multiline.isPresent() && (cp == '\n' || cp == '\r' || cp == '\t')))
                continue;
            if (shown.length() + Character.charCount(cp) > maxLength) break;
            shown.appendCodePoint(cp);
        }
        return new TextInput(width, label, labelVisible, shown.toString(), maxLength, multiline);
    }

    record TextField(String key, int maxLength) {
        TextField {
            validKey(key);
            if (maxLength < 1) throw new IllegalArgumentException("Text bound must be positive");
        }

        public Input input(int width, Component label, String initial) {
            return PolymerDialogForms.input(key, textControl(width, label, true, initial, maxLength, Optional.empty()));
        }

        Optional<String> read(CompoundTag data) {
            return data.get(key) instanceof StringTag value && valid(value.value())
                    ? Optional.of(value.value())
                    : Optional.empty();
        }

        private boolean valid(String value) {
            return value.length() <= maxLength && value.codePoints().noneMatch(Character::isISOControl);
        }
    }

    record IntegerField(String key, int min, int max, int textLength) {
        IntegerField {
            validKey(key);
            if (min > max || textLength < 1) throw new IllegalArgumentException("Invalid integer bounds");
        }

        Optional<Integer> read(CompoundTag data) {
            Tag value = data.get(key);
            if (value instanceof NumericTag number) {
                double exact = number.doubleValue();
                if (!Double.isFinite(exact) || exact != Math.rint(exact) || exact < min || exact > max)
                    return Optional.empty();
                return Optional.of((int) exact);
            }
            if (!(value instanceof StringTag text)) return Optional.empty();
            String raw = text.value();
            if (raw.isEmpty() || raw.length() > textLength || !raw.matches(min < 0 ? "-?[0-9]+" : "[0-9]+"))
                return Optional.empty();
            try {
                int result = Integer.parseInt(raw);
                return result >= min && result <= max ? Optional.of(result) : Optional.empty();
            } catch (NumberFormatException invalid) {
                return Optional.empty();
            }
        }
    }

    private static void validKey(String key) {
        if (key == null || !key.matches("[a-z][a-z0-9_]*"))
            throw new IllegalArgumentException("Invalid dialog field key");
    }
}
