package com.zurrtum.create.infrastructure.polymer.gui;

import static com.zurrtum.create.infrastructure.polymer.gui.PolymerArtKit.image;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zurrtum.create.AllHandle;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.contraptions.chassis.RadialChassisBlock;
import com.zurrtum.create.content.logistics.depot.EjectorBlockEntity;
import com.zurrtum.create.content.logistics.tableCloth.ServerTableClothFilteringBehaviour;
import com.zurrtum.create.foundation.blockEntity.SmartBlockEntity;
import com.zurrtum.create.foundation.blockEntity.behaviour.ValueSettings;
import com.zurrtum.create.foundation.blockEntity.behaviour.ValueSettingsHandleBehaviour;
import com.zurrtum.create.foundation.blockEntity.behaviour.scrollValue.ServerBrassDiodeScrollValueBehaviour;
import com.zurrtum.create.foundation.blockEntity.behaviour.scrollValue.ServerChassisScrollValueBehaviour;
import com.zurrtum.create.foundation.blockEntity.behaviour.scrollValue.ServerKineticScrollValueBehaviour;
import com.zurrtum.create.foundation.blockEntity.behaviour.scrollValue.ServerScrollOptionBehaviour;
import com.zurrtum.create.foundation.blockEntity.behaviour.scrollValue.ServerScrollValueBehaviour;
import com.zurrtum.create.foundation.blockEntity.behaviour.scrollValue.ServerValveScrollValueBehaviour;
import com.zurrtum.create.infrastructure.packet.c2s.ValueSettingsPacket;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerGogglesOverlay;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

public final class PolymerSettingsBoard {
    static final Identifier ACTION = CreateIds.id("polymer_settings_board");
    private static final FontDescription SPACE_FONT = font("value_board_space"), MARKER_FONT = font("vsb_marker");
    private static final int OX = 8, OY = 12, MIN_Y = 7, MAX_Y = 72;
    private static final int TEXT = 0x442000,
            CURSOR_TEXT = 0x442000,
            ICON_TINT = 0x431F00,
            LIGHT = 0xDDDDDD,
            OPTION = 0xFBDC7D;
    private static final int BAR = 0xE000,
            LABEL = 0xE100,
            FRAME_TOP = 0xE200,
            FRAME_BOTTOM = 0xE300,
            CURSOR_MID = 0xE400,
            BAR_BG = 0xE500,
            FRAME_LEFT = 0xE510,
            FRAME_RIGHT = 0xE513,
            TL = 0xE520,
            TR = 0xE521,
            BL = 0xE522,
            BR = 0xE523,
            MILESTONE = 0xE530,
            WIDE_MILESTONE = 0xE531,
            CURSOR_LEFT = 0xE540,
            CURSOR_RIGHT = 0xE541,
            CURSOR_ICON = 0xE542,
            ICON = 0xE600;
    private static final Map<String, int[]> ICONS = new HashMap<>();

    private static final Map<String, String> OPTION_KEYS = new HashMap<>();
    private static final List<String> ICON_ORDER = new ArrayList<>();
    private static final Map<UUID, Session> SESSIONS = PolymerDialogSessions.states();

    static {
        option("SelectionMode", "ROUND_ROBIN", 4, 4, "create.mechanical_arm.selection_mode.round_robin");
        option("SelectionMode", "FORCED_ROUND_ROBIN", 5, 4, "create.mechanical_arm.selection_mode.forced_round_robin");
        option("SelectionMode", "PREFER_FIRST", 6, 4, "create.mechanical_arm.selection_mode.prefer_first");
        option("CartMovementMode", "ROTATE", 12, 1, "create.contraptions.cart_movement_mode.rotate");
        option("CartMovementMode", "ROTATE_PAUSED", 13, 1, "create.contraptions.cart_movement_mode.rotate_paused");
        option("CartMovementMode", "ROTATION_LOCKED", 14, 1, "create.contraptions.cart_movement_mode.rotation_locked");
        option("ClockHands", "HOUR_FIRST", 5, 5, "create.contraptions.clockwork.hour_first");
        option("ClockHands", "MINUTE_FIRST", 6, 5, "create.contraptions.clockwork.minute_first");
        option("ClockHands", "HOUR_FIRST_24", 7, 5, "create.contraptions.clockwork.hour_first_24");
        option("MovementMode", "MOVE_PLACE", 9, 1, "create.contraptions.movement_mode.move_place");
        option("MovementMode", "MOVE_PLACE_RETURNED", 10, 1, "create.contraptions.movement_mode.move_place_returned");
        option("MovementMode", "MOVE_NEVER_PLACE", 11, 1, "create.contraptions.movement_mode.move_never_place");
        option("RollingMode", "TUNNEL_PAVE", 9, 4, "create.contraptions.roller_mode.tunnel_pave");
        option("RollingMode", "STRAIGHT_FILL", 10, 4, "create.contraptions.roller_mode.straight_fill");
        option("RollingMode", "WIDE_FILL", 11, 4, "create.contraptions.roller_mode.wide_fill");
        option("RotationDirection", "CLOCKWISE", 3, 1, "create.generic.clockwise");
        option("RotationDirection", "COUNTER_CLOCKWISE", 4, 5, "create.generic.counter_clockwise");
        option("RotationMode", "ROTATE_PLACE", 6, 1, "create.contraptions.movement_mode.rotate_place");
        option(
                "RotationMode",
                "ROTATE_PLACE_RETURNED",
                7,
                1,
                "create.contraptions.movement_mode.rotate_place_returned");
        option("RotationMode", "ROTATE_NEVER_PLACE", 8, 1, "create.contraptions.movement_mode.rotate_never_place");
        option("SelectionMode", "SPLIT", 4, 3, "create.tunnel.selection_mode.split");
        option("SelectionMode", "FORCED_SPLIT", 5, 3, "create.tunnel.selection_mode.forced_split");
        option("SelectionMode", "PREFER_NEAREST", 8, 3, "create.tunnel.selection_mode.prefer_nearest");
        option("SelectionMode", "RANDOMIZE", 9, 3, "create.tunnel.selection_mode.randomize");
        option("SelectionMode", "SYNCHRONIZE", 10, 3, "create.tunnel.selection_mode.synchronize");
        for (String icon : new String[] {"6,3", "7,3"}) if (!ICON_ORDER.contains(icon)) ICON_ORDER.add(icon);
    }

    private static void option(String type, String name, int column, int row, String key) {
        String id = type + "." + name;
        ICONS.putIfAbsent(id, new int[] {column, row});
        OPTION_KEYS.putIfAbsent(id, key);
        String icon = column + "," + row;
        if (!ICON_ORDER.contains(icon)) ICON_ORDER.add(icon);
    }

    private record Board(
            String title,
            List<String> rows,
            int max,
            int interval,
            boolean iconMode,
            Function<ValueSettings, String> format,
            Function<ValueSettings, int[]> icon) {}

    private static final class Session {
        final String token = UUID.randomUUID().toString();
        final ServerLevel level;
        final BlockPos pos;
        final SmartBlockEntity be;
        final ValueSettingsHandleBehaviour behaviour;
        final Board board;
        int row, value, revision;

        Session(
                ServerLevel level,
                BlockPos pos,
                SmartBlockEntity be,
                ValueSettingsHandleBehaviour behaviour,
                Board board,
                ValueSettings initial) {
            this.level = level;
            this.pos = pos;
            this.be = be;
            this.behaviour = behaviour;
            this.board = board;
            row = Math.clamp(initial.row(), 0, board.rows().size() - 1);
            value = Math.clamp(initial.value(), 0, board.max());
        }
    }

    private PolymerSettingsBoard() {}

    static boolean handles(@Nullable ServerScrollValueBehaviour behaviour) {
        return behaviour != null && !(behaviour instanceof ServerKineticScrollValueBehaviour);
    }

    public static int iconCodepoint(SmartBlockEntity be, ServerScrollValueBehaviour behaviour) {
        if (!(behaviour instanceof ServerScrollOptionBehaviour<?>)) return -1;
        Board board = board(be, behaviour);
        int[] icon = board.icon().apply(behaviour.getValueSettings());
        if (icon == null) return -1;
        int index = ICON_ORDER.indexOf(icon[0] + "," + icon[1]);
        return index < 0 ? -1 : ICON + index;
    }

    static void open(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos,
            SmartBlockEntity be,
            ValueSettingsHandleBehaviour behaviour) {
        Board board = board(be, behaviour);
        Session session = new Session(level, pos.immutable(), be, behaviour, board, behaviour.getValueSettings());
        SESSIONS.put(player.getUUID(), session);
        PolymerDialogForms.open(player, dialog(session));
    }

    private static Board board(SmartBlockEntity be, ValueSettingsHandleBehaviour behaviour) {
        if (behaviour instanceof ServerTableClothFilteringBehaviour) {
            return new Board(
                    PolymerSchematicGuis.lang("create.table_cloth.price_per_order"),
                    List.of(PolymerSchematicGuis.lang("create.table_cloth.amount")),
                    100,
                    10,
                    false,
                    vs -> Integer.toString(Math.max(1, vs.value())),
                    vs -> null);
        }
        if (behaviour instanceof ServerScrollOptionBehaviour<?> options) {
            Enum<?>[] values = options.options;
            String type = values[0].getClass().getSimpleName();
            boolean tunnel = values[0].getClass().getName().contains(".tunnel.");
            Function<ValueSettings, String> id =
                    vs -> type + "." + values[Math.clamp(vs.value(), 0, values.length - 1)].name();
            String title = switch (type) {
                case "SelectionMode" -> "create.logistics.when_multiple_outputs_available";
                case "CartMovementMode" -> "create.contraptions.cart_movement_mode";
                case "ClockHands" -> "create.contraptions.clockwork.clock_hands";
                case "RollingMode" -> "create.contraptions.roller_mode";
                case "RotationDirection" -> "create.contraptions.windmill.rotation_direction";
                default -> "create.contraptions.movement_mode";
            };
            return new Board(
                    PolymerSchematicGuis.lang(title),
                    List.of("Select"),
                    options.getMax(),
                    1,
                    true,
                    vs -> {
                        String key = tunnel
                                ? tunnelKey(values[Math.clamp(vs.value(), 0, values.length - 1)].name())
                                : OPTION_KEYS.get(id.apply(vs));
                        return key == null
                                ? values[Math.clamp(vs.value(), 0, values.length - 1)].name()
                                : PolymerSchematicGuis.lang(key);
                    },
                    vs -> {
                        String name = values[Math.clamp(vs.value(), 0, values.length - 1)].name();
                        return tunnel ? tunnelIcon(name) : ICONS.get(type + "." + name);
                    });
        }
        if (behaviour instanceof ServerBrassDiodeScrollValueBehaviour) {
            return new Board(
                    PolymerSchematicGuis.lang("create.logistics.redstone_interval"),
                    List.of(
                            PolymerSchematicGuis.lang("create.generic.unit.ticks"),
                            PolymerSchematicGuis.lang("create.generic.unit.seconds"),
                            PolymerSchematicGuis.lang("create.generic.unit.minutes")),
                    60,
                    10,
                    false,
                    vs -> {
                        int value = Math.max(1, vs.value());
                        return switch (vs.row()) {
                            case 0 -> Math.max(2, value) + "t";
                            case 1 -> "0:" + (value < 10 ? "0" : "") + value;
                            default -> value + ":00";
                        };
                    },
                    vs -> null);
        }
        if (behaviour instanceof ServerValveScrollValueBehaviour) {
            return new Board(
                    PolymerSchematicGuis.lang("create.kinetics.valve_handle.rotated_angle"),
                    List.of("CW", "CCW"),
                    180,
                    45,
                    false,
                    vs -> Math.max(1, Math.abs(vs.value())) + "°",
                    vs -> null);
        }
        if (behaviour instanceof ServerChassisScrollValueBehaviour) {
            String title = be.getBlockState().getBlock() instanceof RadialChassisBlock
                    ? "create.contraptions.chassis.radius"
                    : "create.contraptions.chassis.range";
            return new Board(
                    PolymerSchematicGuis.lang(title),
                    List.of(PolymerSchematicGuis.lang("create.contraptions.chassis.distance")),
                    ((ServerScrollValueBehaviour) behaviour).getMax() - 1,
                    1,
                    false,
                    vs -> Integer.toString(vs.value() + 1),
                    vs -> null);
        }
        String title = be instanceof EjectorBlockEntity
                ? PolymerSchematicGuis.lang("create.weighted_ejector.stack_size")
                : be.getBlockState().getBlock().getName().getString();
        return new Board(
                title,
                List.of("Value"),
                ((ServerScrollValueBehaviour) behaviour).getMax(),
                10,
                false,
                vs -> Integer.toString(vs.value()),
                vs -> null);
    }

    private static String tunnelKey(String name) {
        return "create.tunnel.selection_mode." + name.toLowerCase(Locale.ROOT);
    }

    private static int[] tunnelIcon(String name) {
        return switch (name) {
            case "SPLIT" -> new int[] {4, 3};
            case "FORCED_SPLIT" -> new int[] {5, 3};
            case "ROUND_ROBIN" -> new int[] {6, 3};
            case "FORCED_ROUND_ROBIN" -> new int[] {7, 3};
            case "PREFER_NEAREST" -> new int[] {8, 3};
            case "RANDOMIZE" -> new int[] {9, 3};
            default -> new int[] {10, 3};
        };
    }

    private record Geometry(
            int labelWidth, int milestoneSize, int scale, int milestoneCount, int barWidth, int width, int height) {
        static Geometry of(Board board) {
            int label = 0;
            for (String row : board.rows()) label = Math.max(label, PolymerGogglesOverlay.width(row));
            if (board.iconMode()) label = -18;
            int milestoneSize = board.iconMode() ? 8 : 4;
            int scale = board.max() > 128 ? 1 : 2;
            int count = board.max() / board.interval() + 1;
            int bar = (board.max() + 1) * scale + 1 + count * milestoneSize;
            return new Geometry(
                    label,
                    milestoneSize,
                    scale,
                    count,
                    bar,
                    label + 14 + bar + 10,
                    board.rows().size() * 11);
        }

        float x(Board board, int column) {
            float x = (Math.max(1, column) - 1) / board.interval() * milestoneSize + column * scale + 1.5f;
            x += labelWidth + 14 + 4;
            if (column % board.interval() == 0) x += milestoneSize / 2f;
            if (column > 0) x += milestoneSize;
            return x;
        }

        float y(int row) {
            return (row + 0.5f) * 11 - 0.5f;
        }
    }

    private static Holder<Dialog> dialog(Session session) {
        Board board = session.board;
        Geometry g = Geometry.of(board);
        int advance = OX + g.width() + 12;
        List<MutableComponent> lines = new ArrayList<>();
        int lineCount = (OY + g.height() + 24) / 9 + 1;
        for (int i = 0; i < lineCount; i++) lines.add(Component.empty());

        Pen pen = new Pen(lines.getFirst());
        int rows = board.rows().size();
        int frameH = rows * 11 + 5, barX = g.labelWidth() + 14 + 4;
        frame(pen, g.labelWidth() + 14, -3, g.barWidth() + 8, frameH, rows);
        pen.repeat(g.labelWidth() + 17, 0, BAR_BG + rows - 1, g.barWidth() + 2);
        for (int r = 0; r < rows; r++) {
            int y = r * 11;
            if (!board.iconMode()) {
                pen.columns(-4, y, LABEL, Math.min(g.labelWidth() + 8, 161));
                for (int w = 0; w < g.barWidth(); w += 248)
                    pen.columns(barX + w, y + 1, BAR, Math.min(248, g.barWidth() - w));
                pen.text(0, y + 1, board.rows().get(r), TEXT);
            }
            int mx = barX;
            for (int m = 0; m < g.milestoneCount(); m++) {
                pen.tile(mx, y + 1, board.iconMode() ? WIDE_MILESTONE : MILESTONE, board.iconMode() ? 13 : 7, 0xFFFFFF);
                mx += g.milestoneSize() + board.interval() * g.scale();
            }
        }
        if (!board.iconMode()) frame(pen, -7, -3, g.labelWidth() + 14, frameH, rows);

        ValueSettings current = new ValueSettings(session.row, session.value);
        float cx = g.x(board, session.value), cy = g.y(session.row);
        String cursorText = board.format().apply(current);
        int[] icon = board.iconMode() ? board.icon().apply(current) : null;
        int cursorWidth = (icon != null ? 16 : PolymerGogglesOverlay.width(cursorText)) / 2 * 2 + 3;
        int cursorX = (int) cx - cursorWidth / 2, cursorY = (int) cy - 7;
        if (icon != null) {
            pen.tile(cursorX - 2, cursorY - 3, CURSOR_ICON, 22, 0xFFFFFF);
            int index = ICON_ORDER.indexOf(icon[0] + "," + icon[1]);
            if (index >= 0) pen.tile(cursorX + 1, cursorY - 1, ICON + index, 16, ICON_TINT);
        } else {
            pen.tile(cursorX - 3, cursorY, CURSOR_LEFT, 3, 0xFFFFFF);
            pen.columns(cursorX, cursorY, CURSOR_MID, Math.min(cursorWidth, 56));
            pen.tile(cursorX + cursorWidth, cursorY, CURSOR_RIGHT, 3, 0xFFFFFF);
            pen.text(cursorX + 2, cursorY + 3, cursorText, CURSOR_TEXT);
        }
        pen.finish(advance);

        for (int line = 1; line < lineCount; line++) {
            int y = line * 9 + 4 - OY;
            int row = Math.floorDiv(y, 11);
            if (y < -2 || row >= rows) {
                lines.get(line).append(PolymerArtKit.space(SPACE_FONT, advance));
                continue;
            }
            row = Math.clamp(row, 0, rows - 1);
            hits(lines.get(line), session, g, row, advance);
        }
        MutableComponent body = Component.empty();
        for (int i = 0; i < lineCount; i++) {
            if (i > 0) body.append(Component.literal("\n"));
            body.append(lines.get(i));
        }
        Style plain = Style.EMPTY.withColor(LIGHT);
        int textWidth = Math.max(advance + 8, 240);
        List<DialogBody> bodies = new ArrayList<>();
        bodies.add(new PlainMessage(Component.literal(board.title()).withStyle(plain), textWidth));
        bodies.add(new PlainMessage(body, advance + 8));
        if (board.iconMode())
            bodies.add(new PlainMessage(
                    Component.literal(cursorText).withStyle(Style.EMPTY.withColor(OPTION)), textWidth));
        bodies.add(new PlainMessage(
                Component.literal("Click the board or use the buttons, then Confirm")
                        .withStyle(plain),
                textWidth));
        var common = new CommonDialogData(
                Component.empty(),
                Optional.of(Component.literal(board.title())),
                true,
                false,
                DialogAction.NONE,
                bodies,
                List.of());
        List<ActionButton> actions = new ArrayList<>();
        if (board.interval() > 1) actions.add(button("-" + board.interval(), 44, session, "step", -board.interval()));
        actions.add(button("-1", 44, session, "step", -1));
        actions.add(button("+1", 44, session, "step", 1));
        if (board.interval() > 1) actions.add(button("+" + board.interval(), 44, session, "step", board.interval()));
        if (rows > 1) actions.add(button(board.rows().get((session.row + 1) % rows), 70, session, "row", 1));
        actions.add(button("Confirm", 100, session, "save", 0));
        ActionButton cancel = button("Cancel", 100, session, "cancel", 0);
        return Holder.direct(new MultiActionDialog(common, actions, Optional.of(cancel), Math.min(actions.size(), 5)));
    }

    private static void frame(Pen pen, int x, int y, int w, int h, int rows) {
        pen.tile(x, y, TL, 4, 0xFFFFFF);
        pen.tile(x + w - 4, y, TR, 4, 0xFFFFFF);
        pen.tile(x, y + h - 4, BL, 4, 0xFFFFFF);
        pen.tile(x + w - 4, y + h - 4, BR, 4, 0xFFFFFF);
        pen.tile(x, y + 4, FRAME_LEFT + rows - 1, 3, 0xFFFFFF);
        pen.tile(x + w - 3, y + 4, FRAME_RIGHT + rows - 1, 3, 0xFFFFFF);
        pen.columns(x + 4, y, FRAME_TOP, Math.min(w - 8, 256));
        pen.columns(x + 4, y + h - 3, FRAME_BOTTOM, Math.min(w - 8, 256));
    }

    private static void hits(MutableComponent line, Session session, Geometry g, int row, int advance) {
        Board board = session.board;
        line.append(marker());
        int pen = 2;
        int start = OX + g.labelWidth() + 14 + 4, end = start + g.barWidth();
        int runStart = start, runValue = closest(board, g, start - OX + 0.5f);
        for (int x = start + 1; x <= end; x++) {
            int value = x < end ? closest(board, g, x - OX + 0.5f) : -1;
            if (value == runValue) continue;
            line.append(PolymerArtKit.space(SPACE_FONT, runStart - pen));
            CompoundTag tag = payload(session, "set", 0);
            tag.putInt("row", row);
            tag.putInt("value", runValue);
            line.append(Component.empty()
                    .withStyle(Style.EMPTY.withClickEvent(new ClickEvent.Custom(ACTION, Optional.of(tag))))
                    .append(PolymerArtKit.space(SPACE_FONT, x - runStart)));
            pen = x;
            runStart = x;
            runValue = value;
        }
        line.append(PolymerArtKit.space(SPACE_FONT, advance - 2 - pen)).append(marker());
    }

    private static int closest(Board board, Geometry g, float x) {
        int best = 0;
        for (int column = 0; column <= board.max(); column++)
            if (Math.abs(g.x(board, column) - x) < Math.abs(g.x(board, best) - x)) best = column;
        return best;
    }

    private static ActionButton button(String label, int width, Session session, String operation, int delta) {
        return new ActionButton(
                new CommonButtonData(PolymerGuiTextures.actionLabel(label), width),
                Optional.of(new CustomAll(ACTION, Optional.of(payload(session, operation, delta)))));
    }

    private static CompoundTag payload(Session session, String operation, int delta) {
        CompoundTag tag = new CompoundTag();
        tag.putString("session", session.token);
        tag.putInt("revision", session.revision);
        tag.putString("operation", operation);
        tag.putInt("delta", delta);
        return tag;
    }

    static boolean handle(ServerPlayer player, Identifier id, Optional<Tag> payload) {
        if (!PolymerDialogForms.validSubmission(player, id, payload, ACTION)) return true;
        if (!ACTION.equals(id)) return false;
        Session session = SESSIONS.get(player.getUUID());
        CompoundTag tag = PolymerDialogForms.payload(payload).orElse(null);
        if (session == null
                || tag == null
                || !tag.getString("session").orElse("").equals(session.token)
                || tag.getInt("revision").orElse(-1) != session.revision) return true;
        if (player.level() != session.level
                || session.be.isRemoved()
                || session.level.getBlockEntity(session.pos) != session.be
                || !session.pos.closerThan(player.blockPosition(), 20)) {
            SESSIONS.remove(player.getUUID());
            NativePresentationPolicy.sendPresentation(player, ClientboundClearDialogPacket.INSTANCE);
            return true;
        }
        Board board = session.board;
        switch (tag.getString("operation").orElse("")) {
            case "set" -> {
                session.row = Math.clamp(
                        tag.getInt("row").orElse(session.row), 0, board.rows().size() - 1);
                session.value = Math.clamp(tag.getInt("value").orElse(session.value), 0, board.max());
            }
            case "step" ->
                session.value = Math.clamp(session.value + tag.getInt("delta").orElse(0), 0, board.max());
            case "row" -> session.row = (session.row + 1) % board.rows().size();
            case "save" -> {
                SESSIONS.remove(player.getUUID());
                AllHandle.onValueSettings(
                        player.connection,
                        new ValueSettingsPacket(
                                session.pos,
                                session.row,
                                session.value,
                                null,
                                null,
                                Direction.UP,
                                false,
                                session.behaviour.netId()));
                NativePresentationPolicy.sendPresentation(player, ClientboundClearDialogPacket.INSTANCE);
                return true;
            }
            case "cancel" -> {
                SESSIONS.remove(player.getUUID());
                NativePresentationPolicy.sendPresentation(player, ClientboundClearDialogPacket.INSTANCE);
                return true;
            }
            default -> {
                return true;
            }
        }
        session.revision++;
        PolymerDialogForms.open(player, dialog(session));
        return true;
    }

    private static final class Pen {
        final MutableComponent line;
        int pen;

        Pen(MutableComponent line) {
            this.line = line;
        }

        void tile(int x, int y, int codepoint, int width, int color) {
            int lx = x + OX, ly = y + OY;
            if (ly < MIN_Y || ly > MAX_Y) return;
            line.append(PolymerArtKit.space(SPACE_FONT, lx - pen));
            line.append(Component.literal(Character.toString(codepoint))
                    .withStyle(Style.EMPTY.withColor(color).withoutShadow().withFont(rowFont(ly))));
            pen = lx + width + 1;
        }

        void columns(int x, int y, int first, int count) {
            strip(x, y, count, i -> first + i);
        }

        void repeat(int x, int y, int codepoint, int count) {
            strip(x, y, count, i -> codepoint);
        }

        private void strip(int x, int y, int count, IntUnaryOperator glyph) {
            int lx = x + OX, ly = y + OY;
            if (ly < MIN_Y || ly > MAX_Y || count <= 0) return;
            for (int parity = 0; parity < 2 && parity < count; parity++) {
                StringBuilder chars = new StringBuilder();
                for (int i = parity; i < count; i += 2) chars.appendCodePoint(glyph.applyAsInt(i));
                line.append(PolymerArtKit.space(SPACE_FONT, lx + parity - pen));
                line.append(Component.literal(chars.toString())
                        .withStyle(
                                Style.EMPTY.withColor(0xFFFFFF).withoutShadow().withFont(rowFont(ly))));
                pen = lx + parity + 2 * ((count - parity + 1) / 2);
            }
        }

        void text(int x, int y, String text, int color) {
            int lx = x + OX, ly = y + OY;
            if (ly < MIN_Y || ly > MAX_Y || text.isEmpty()) return;
            line.append(PolymerArtKit.space(SPACE_FONT, lx - pen));
            line.append(Component.literal(text)
                    .withStyle(Style.EMPTY.withColor(color).withoutShadow().withFont(rowFont(ly))));
            pen = lx + PolymerGogglesOverlay.width(text);
        }

        void finish(int advance) {
            line.append(PolymerArtKit.space(SPACE_FONT, advance - pen));
        }
    }

    private static Component marker() {
        return Component.literal("\uE000")
                .withStyle(Style.EMPTY.withColor(0xFFFFFF).withoutShadow().withFont(MARKER_FONT));
    }

    private static FontDescription font(String name) {
        return new FontDescription.Resource(CreateIds.id(name));
    }

    private static FontDescription rowFont(int y) {
        return font("vsb_" + y);
    }

    static void addAssets(ResourcePackBuilder builder) {
        try {
            BufferedImage sheet = image(builder, "assets/create/textures/gui/value_settings.png");
            BufferedImage icons = image(builder, "assets/create/textures/gui/icons.png");
            List<Tile> tiles = new ArrayList<>();
            tiles.add(Tile.columns(sheet.getSubimage(7, 0, 248, 8), BAR));
            tiles.add(Tile.columns(sheet.getSubimage(0, 31, 161, 11), LABEL));
            tiles.add(Tile.columns(sheet.getSubimage(0, 24, 256, 3), FRAME_TOP));
            tiles.add(Tile.columns(sheet.getSubimage(0, 27, 256, 3), FRAME_BOTTOM));
            tiles.add(Tile.columns(sheet.getSubimage(4, 9, 56, 14), CURSOR_MID));
            for (int rows = 1; rows <= 3; rows++) {
                tiles.add(Tile.single(stretch(sheet.getSubimage(75, 9, 1, 1), 1, rows * 11 - 1), BAR_BG + rows - 1));
                tiles.add(Tile.single(
                        stretch(sheet.getSubimage(65, 14, 3, 4), 3, rows * 11 + 5 - 8), FRAME_LEFT + rows - 1));
                tiles.add(Tile.single(
                        stretch(sheet.getSubimage(71, 14, 3, 4), 3, rows * 11 + 5 - 8), FRAME_RIGHT + rows - 1));
            }
            tiles.add(Tile.single(sheet.getSubimage(65, 9, 4, 4), TL));
            tiles.add(Tile.single(sheet.getSubimage(70, 9, 4, 4), TR));
            tiles.add(Tile.single(sheet.getSubimage(65, 19, 4, 4), BL));
            tiles.add(Tile.single(sheet.getSubimage(70, 19, 4, 4), BR));
            tiles.add(Tile.single(sheet.getSubimage(0, 0, 7, 8), MILESTONE));
            tiles.add(Tile.single(sheet.getSubimage(75, 14, 13, 8), WIDE_MILESTONE));
            tiles.add(Tile.single(sheet.getSubimage(0, 9, 3, 14), CURSOR_LEFT));
            tiles.add(Tile.single(sheet.getSubimage(61, 9, 3, 14), CURSOR_RIGHT));
            tiles.add(Tile.single(sheet.getSubimage(0, 44, 22, 20), CURSOR_ICON));
            for (int i = 0; i < ICON_ORDER.size(); i++) {
                String[] cr = ICON_ORDER.get(i).split(",");
                tiles.add(Tile.single(
                        icons.getSubimage(Integer.parseInt(cr[0]) * 16, Integer.parseInt(cr[1]) * 16, 16, 16),
                        ICON + i));
            }
            for (int i = 0; i < tiles.size(); i++)
                PolymerGuiGlyphs.fontPng(builder, "vsb_tile_" + i, tiles.get(i).image());
            JsonArray ascii = PolymerArtKit.asciiRows(builder);
            for (int y = MIN_Y; y <= MAX_Y; y++) {
                int ascent = 7 - y;
                JsonArray providers = new JsonArray();
                JsonObject space = new JsonObject();
                space.addProperty("type", "space");
                JsonObject advances = new JsonObject();
                advances.addProperty(" ", 4);
                space.add("advances", advances);
                providers.add(space);
                JsonObject text = bitmap("minecraft:font/ascii.png", 8, ascent);
                text.add("chars", ascii.deepCopy());
                providers.add(text);
                for (int i = 0; i < tiles.size(); i++) {
                    Tile tile = tiles.get(i);
                    JsonObject provider = bitmap(
                            "create:font/vsb_tile_" + i + ".png", tile.image().getHeight(), ascent);
                    JsonArray chars = new JsonArray();
                    chars.add(tile.chars());
                    provider.add("chars", chars);
                    providers.add(provider);
                }
                PolymerGuiGlyphs.writeFont(builder, "vsb_" + y, providers);
            }
            BufferedImage marker = new BufferedImage(1, 9, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 9; y++) marker.setRGB(0, y, 0x01FFFFFF);
            PolymerGuiGlyphs.fontPng(builder, "vsb_marker", marker);
            JsonArray markerProviders = new JsonArray();
            JsonObject markerProvider = bitmap("create:font/vsb_marker.png", 9, 7);
            JsonArray markerChars = new JsonArray();
            markerChars.add("\uE000");
            markerProvider.add("chars", markerChars);
            markerProviders.add(markerProvider);
            PolymerGuiGlyphs.writeFont(builder, "vsb_marker", markerProviders);
        } catch (IOException | RuntimeException exception) {
            Create.LOGGER.warn("Value settings board art unavailable", exception);
        }
    }

    private record Tile(BufferedImage image, String chars) {
        static Tile single(BufferedImage source, int codepoint) {
            return new Tile(keepAdvance(PolymerArtKit.copy(source)), Character.toString(codepoint));
        }

        static Tile columns(BufferedImage source, int first) {
            BufferedImage image = PolymerArtKit.copy(source);
            StringBuilder chars = new StringBuilder();
            for (int x = 0; x < image.getWidth(); x++) {
                boolean visible = false;
                for (int y = 0; y < image.getHeight(); y++) visible |= (image.getRGB(x, y) >>> 24) != 0;
                if (!visible) image.setRGB(x, 0, 0x01FFFFFF);
                chars.appendCodePoint(first + x);
            }
            return new Tile(image, chars.toString());
        }

        private static BufferedImage keepAdvance(BufferedImage image) {
            int x = image.getWidth() - 1;
            boolean visible = false;
            for (int y = 0; y < image.getHeight(); y++) visible |= (image.getRGB(x, y) >>> 24) != 0;
            if (!visible) image.setRGB(x, 0, 0x01FFFFFF);
            return image;
        }
    }

    private static BufferedImage stretch(BufferedImage source, int width, int height) {
        BufferedImage image = new BufferedImage(width, Math.max(1, height), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < width; x++)
                image.setRGB(
                        x, y, source.getRGB(x * source.getWidth() / width, y * source.getHeight() / image.getHeight()));
        return image;
    }

    private static JsonObject bitmap(String file, int height, int ascent) {
        JsonObject provider = PolymerGuiGlyphs.bitmapProvider(file, height, ascent);
        return provider;
    }
}
