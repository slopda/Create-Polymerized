package com.zurrtum.create.infrastructure.polymer.gui;

import com.zurrtum.create.AllDataComponents;
import com.zurrtum.create.AllHandle;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.AllSchedules;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.logistics.box.PackageStyles;
import com.zurrtum.create.content.logistics.filter.FilterItemStack;
import com.zurrtum.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.zurrtum.create.content.trains.schedule.Schedule;
import com.zurrtum.create.content.trains.schedule.ScheduleDataEntry;
import com.zurrtum.create.content.trains.schedule.ScheduleEntry;
import com.zurrtum.create.content.trains.schedule.condition.CargoThresholdCondition;
import com.zurrtum.create.content.trains.schedule.condition.FluidThresholdCondition;
import com.zurrtum.create.content.trains.schedule.condition.IdleCargoCondition;
import com.zurrtum.create.content.trains.schedule.condition.ItemThresholdCondition;
import com.zurrtum.create.content.trains.schedule.condition.PlayerPassengerCondition;
import com.zurrtum.create.content.trains.schedule.condition.RedstoneLinkCondition;
import com.zurrtum.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.zurrtum.create.content.trains.schedule.condition.ScheduledDelay;
import com.zurrtum.create.content.trains.schedule.condition.StationPoweredCondition;
import com.zurrtum.create.content.trains.schedule.condition.StationUnloadedCondition;
import com.zurrtum.create.content.trains.schedule.condition.TimeOfDayCondition;
import com.zurrtum.create.content.trains.schedule.condition.TimedWaitCondition;
import com.zurrtum.create.content.trains.schedule.destination.ChangeThrottleInstruction;
import com.zurrtum.create.content.trains.schedule.destination.ChangeTitleInstruction;
import com.zurrtum.create.content.trains.schedule.destination.DeliverPackagesInstruction;
import com.zurrtum.create.content.trains.schedule.destination.DestinationInstruction;
import com.zurrtum.create.content.trains.schedule.destination.FetchPackagesInstruction;
import com.zurrtum.create.content.trains.schedule.destination.ScheduleInstruction;
import com.zurrtum.create.content.trains.schedule.destination.TextScheduleInstruction;
import com.zurrtum.create.infrastructure.packet.c2s.ScheduleEditPacket;
import com.zurrtum.create.infrastructure.polymer.PolymerInventorySync;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerGogglesOverlay;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.SguiUtils;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.StringUtil;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import org.jspecify.annotations.Nullable;

public final class PolymerScheduleScreen extends SimpleGui {
    private static final Identifier TEXT_ACTION = CreateIds.id("polymer_schedule_text");
    private static final PolymerDialogSessions<TextEdit> TEXT_EDITS =
            new PolymerDialogSessions<>(Duration.ofMinutes(2));
    private static final PolymerDialogForms.TextField TEXT = new PolymerDialogForms.TextField("text", 32);

    private static final FontDescription SPACE_FONT = font("value_board_space");
    static final String EMPTY_ITEM = "schedule_empty", COVER_ITEM = "schedule_label_cover";
    static final int ART_X = -47, ART_Y = -1, CUT_X = 100, CUT_W = 16;
    static final int CARD_X = -22, CARD_W = 172, CARD_HEADER = 18, FIELD_X = 4;
    private static final int CLIP_L = -4, CLIP_R = 134, MAX_FIELD = 130, LIST_ROWS = 9;
    static final int FILLER_Y = 122, FILLER_H = 13, BUTTON_Y = 196, TITLE_CENTER = 69;
    static final int DIM_X = -47, DIM_Y = -18, EDITOR_X = -48, EDITOR_Y = 29, EDITOR_CUT_Y = 42, EDITOR_CUT_H = 5;
    private static final int INVENTORY_Y = 121, LINE_Y = 71, WIDGET_X = 31, SELECTOR_TEXT_X = 12, TRASH_Y = 89;
    private static final int TITLE_TOP = 3, EDITOR_TITLE_TOP = 33, SELECTOR_TOP = 58, WIDGET_TOP = 76;
    private static final int TEXT_COLOR = 0xF2F2EE, TITLE_COLOR = 0x505050, HEADER_RGB = 0x5391E1, HINT_RGB = 0x96B7E0;
    static final int COVER_SLOT = 53, COVER_X = 8, COVER_Y = 128, COVER_W = 140, COVER_H = 9;
    static final int STACKS = 0x25A4;

    enum Piece {
        PANEL(240, 226),
        HEAD_LIGHT(CARD_W, 18),
        HEAD_MEDIUM(CARD_W, 18),
        BODY(CARD_W, 18),
        BODY_LIGHT(CARD_W, 18),
        BOTTOM(CARD_W, 18),
        BOTTOM_LIGHT(CARD_W, 18),
        FILL(CARD_W, FILLER_H),
        FILL_LIGHT(CARD_W, FILLER_H),
        LINE(3, 18),
        LINE_FILL(3, FILLER_H),
        STRIP_TRAVEL(11, 16),
        STRIP_ACTION(11, 16),
        STRIP_WAIT(11, 16),
        STRIP_DOTTED(11, 16),
        STRIP_END(11, 16),
        CARD_NEW(16, 16),
        POINTER(21, 16),
        POINTER_OFF(13, 16),
        REMOVE(12, 12),
        DUPLICATE(12, 12),
        MOVE_UP(12, 12),
        MOVE_DOWN(12, 12),
        SCROLL_LEFT(4, 8),
        SCROLL_RIGHT(4, 8),
        COND_LEFT(6, 16),
        COND_LEFT_CLEAN(2, 16),
        COND_ITEM(18, 16),
        COND_RIGHT(2, 16),
        MID_1(1, 16),
        MID_2(2, 16),
        MID_4(4, 16),
        MID_8(8, 16),
        MID_16(16, 16),
        MID_32(32, 16),
        MID_64(64, 16),
        MID_128(128, 16),
        APPEND(10, 10),
        COND_NEW(19, 16),
        GRAD_TOP(204, 10),
        GRAD_BOTTOM(204, 10),
        ITEM_3X(48, 48),
        BTN_LOOP(18, 18),
        BTN_LOOP_ON(18, 18),
        BTN_RESET(18, 18),
        BTN_RESET_OFF(18, 18),
        BTN_SKIP(18, 18),
        BTN_SKIP_OFF(18, 18),
        BTN_UP(18, 18),
        BTN_UP_OFF(18, 18),
        BTN_DOWN(18, 18),
        BTN_DOWN_OFF(18, 18),
        BTN_CONFIRM(18, 18),
        BTN_TRASH(18, 18),
        DIM(256, 256),
        EDITOR(256, 89 - EDITOR_CUT_H),
        INVENTORY(176, 108),
        SLOT_ADDITIONAL(32, 18),
        SLOT_INACTIVE(18, 18),
        DA_START(2, 18),
        DA_SPEECH(5, 18),
        DA_END(2, 18),
        DA_1(1, 18),
        DA_2(2, 18),
        DA_4(4, 18),
        DA_8(8, 18),
        DA_16(16, 18),
        DA_32(32, 18),
        DA_64(64, 18),
        DA_128(128, 18);
        final int width, height;

        Piece(int width, int height) {
            this.width = width;
            this.height = height;
        }

        String glyph() {
            return Character.toString(0xE000 + ordinal());
        }

        String file() {
            return "create:font/schedule/" + name().toLowerCase() + ".png";
        }
    }

    static final Piece[] MIDDLE = {
        Piece.MID_1, Piece.MID_2, Piece.MID_4, Piece.MID_8, Piece.MID_16, Piece.MID_32, Piece.MID_64, Piece.MID_128
    };
    static final Piece[] DATA_AREA = {
        Piece.DA_1, Piece.DA_2, Piece.DA_4, Piece.DA_8, Piece.DA_16, Piece.DA_32, Piece.DA_64, Piece.DA_128
    };
    static final Map<Integer, Set<Piece>> PLACEMENTS = placements();
    static final Set<Integer> TEXT_TOPS = textTops();

    private enum RowKind {
        HEAD,
        COND,
        LAST,
        GAP,
        END
    }

    private record Row(RowKind kind, int entry, int index) {}

    private record Column(int index, int x, int width, int firstCell, int cells) {}

    private record Columns(List<Column> visible, boolean scrollable, int newCell) {}

    @FunctionalInterface
    private interface Action {
        void click(boolean right, boolean shift);
    }

    private record Control(
            @Nullable ItemStack icon, @Nullable List<Component> tooltip, Action action, boolean plainItem) {}

    private enum Kind {
        SCROLL,
        SELECT,
        TEXT,
        INTEGER
    }

    private record Widget(
            Kind kind,
            int x,
            int width,
            String key,
            int min,
            int max,
            int step,
            int shiftStep,
            boolean throttle,
            List<Component> options,
            @Nullable Component title,
            IntFunction<String> label,
            boolean labelVisible,
            Predicate<String> filter) {}

    private record EditorLine(
            List<Widget> widgets, @Nullable List<int[]> boxes) {}

    private record TextEdit(
            PolymerScheduleScreen gui,
            Editor editor,
            ScheduleDataEntry input,
            Widget widget,
            String query,
            PolymerScheduleStations.@Nullable Page stations) {}

    private final class Editor {
        ScheduleDataEntry input;
        final boolean instruction, allowDeletion;
        final Consumer<Boolean> onClose;
        final Map<String, Integer> ints = new HashMap<>();
        final Map<String, String> texts = new HashMap<>();
        EditorLine line;

        Editor(ScheduleDataEntry input, boolean instruction, Consumer<Boolean> onClose, boolean allowDeletion) {
            this.input = input;
            this.instruction = instruction;
            this.onClose = onClose;
            this.allowDeletion = allowDeletion;
            load();
        }

        void load() {
            ints.clear();
            texts.clear();
            line = editorLine(input);
            CompoundTag data = input.getData();
            for (Widget widget : line.widgets()) {
                if (widget.kind() == Kind.SCROLL || widget.kind() == Kind.SELECT)
                    ints.put(widget.key(), Mth.clamp(data.getIntOr(widget.key(), 0), widget.min(), widget.max() - 1));
                else {
                    String value = data.getStringOr(widget.key(), "");
                    texts.put(widget.key(), widget.filter().test(value) ? truncate(value) : "");
                }
            }
        }

        void save() {
            CompoundTag data = input.getData();
            for (Widget widget : line.widgets()) {
                if (widget.kind() == Kind.SCROLL || widget.kind() == Kind.SELECT)
                    data.putInt(widget.key(), ints.get(widget.key()));
                else data.putString(widget.key(), texts.get(widget.key()));
            }
        }
    }

    private final ItemStack held, initial;
    private final Schedule schedule;
    private final List<Integer> horizontal = new ArrayList<>();
    private final ItemStack[] ghost = {ItemStack.EMPTY, ItemStack.EMPTY};
    private final Map<Integer, Control> controls = new HashMap<>();
    private final PolymerGuiLifecycle lifecycle = new PolymerGuiLifecycle(20);
    private @Nullable Editor editor;
    private @Nullable Component shownTitle;
    private int scroll;

    private PolymerScheduleScreen(ServerPlayer player, ItemStack held, Schedule schedule) {
        super(MenuType.GENERIC_9x6, player, true);
        this.held = held;
        this.initial = held.copy();
        this.schedule = schedule;
        resetHorizontal();
        refresh();
    }

    public static boolean open(ServerPlayer player) {
        ItemStack stack = player.getInventory().getSelectedItem();
        if (PolymerNativeClients.isNative(player) || player.isSpectator() || !stack.is(AllItems.SCHEDULE)) return false;
        TEXT_EDITS.bindToServer();
        Schedule schedule = new Schedule();
        CompoundTag tag = stack.get(AllDataComponents.TRAIN_SCHEDULE);
        if (tag != null && !tag.isEmpty()) {
            try (ProblemReporter.ScopedCollector logging =
                    new ProblemReporter.ScopedCollector(() -> "ScheduleScreen", Create.LOGGER)) {
                schedule = Schedule.read(TagValueInput.create(logging, player.registryAccess(), tag));
            }
        }
        return new PolymerScheduleScreen(player, stack, schedule).open();
    }

    private boolean valid() {
        return !player.isRemoved()
                && !player.isSpectator()
                && player.getInventory().getSelectedItem() == held
                && held.is(AllItems.SCHEDULE)
                && ItemStack.isSameItemSameComponents(initial, held);
    }

    private record Summary(ItemStack item, @Nullable Component text) {}

    private static Summary summary(ScheduleDataEntry input) {
        if (input instanceof DeliverPackagesInstruction)
            return new Summary(
                    AllItems.POSTBOX.white().getDefaultInstance(), tr("schedule.instruction.package_delivery"));
        if (input instanceof FetchPackagesInstruction)
            return new Summary(PackageStyles.getDefaultBox(), tr("schedule.instruction.package_retrieval"));
        if (input instanceof DestinationInstruction destination)
            return new Summary(
                    AllItems.TRACK_STATION.getDefaultInstance(), Component.literal(destination.getLabelText()));
        if (input instanceof ChangeTitleInstruction title)
            return new Summary(new ItemStack(Items.NAME_TAG), Component.literal(title.getLabelText()));
        if (input instanceof ChangeThrottleInstruction throttle)
            return new Summary(
                    AllItems.TRAIN_CONTROLS.getDefaultInstance(), Component.literal(throttle.intData("Value") + "%"));
        if (input instanceof ScheduledDelay delay)
            return new Summary(ItemStack.EMPTY, tr("schedule.condition.delay_short", compactTime(delay)));
        if (input instanceof IdleCargoCondition idle)
            return new Summary(ItemStack.EMPTY, tr("schedule.condition.idle_short", compactTime(idle)));
        if (input instanceof TimeOfDayCondition time)
            return new Summary(
                    new ItemStack(Items.STRUCTURE_VOID),
                    time.getDigitalDisplay(time.intData("Hour"), time.intData("Minute"), false));
        if (input instanceof ItemThresholdCondition items)
            return new Summary(
                    items.stack.item(),
                    Component.literal(items.getOperator().formatted + " " + items.getThreshold())
                            .append(Component.literal(items.inStacks() ? "▤" : "")));
        if (input instanceof FluidThresholdCondition fluid)
            return new Summary(
                    fluid.compareStack.item(),
                    Component.literal(fluid.getOperator().formatted + " " + fluid.getThreshold())
                            .append(Component.literal("b")));
        if (input instanceof RedstoneLinkCondition link)
            return new Summary(
                    AllItems.REDSTONE_LINK.getDefaultInstance(),
                    tr(
                            link.lowActivation()
                                    ? "schedule.condition.redstone_link_off"
                                    : "schedule.condition.redstone_link_on"));
        if (input instanceof PlayerPassengerCondition players) {
            int target = players.getTarget();
            return new Summary(
                    AllItems.SEAT.yellow().getDefaultInstance(),
                    tr("schedule.condition.player_count." + (target == 1 ? "summary" : "summary_plural"), target));
        }
        if (input instanceof StationPoweredCondition)
            return new Summary(ItemStack.EMPTY, tr("schedule.condition.powered"));
        if (input instanceof StationUnloadedCondition)
            return new Summary(ItemStack.EMPTY, tr("schedule.condition.unloaded"));
        return new Summary(ItemStack.EMPTY, null);
    }

    private static Component compactTime(TimedWaitCondition input) {
        return Component.literal(input.getValue() + input.getUnit().suffix);
    }

    private static Component time(TimedWaitCondition input) {
        return Component.literal(input.getValue() + " ").append(tr(unitKey(input.getUnit())));
    }

    private static String unitKey(TimedWaitCondition.TimeUnit unit) {
        return "generic.unit." + unit.name().toLowerCase(Locale.ROOT);
    }

    private static final String[] ROTATIONS = {
        "every_24",
        "every_12",
        "every_6",
        "every_4",
        "every_3",
        "every_2",
        "every_1",
        "every_0_45",
        "every_0_30",
        "every_0_15"
    };

    private static List<Component> titleAs(ScheduleDataEntry input, String type) {
        Identifier id = input.getId();
        if (input instanceof DeliverPackagesInstruction)
            return List.of(
                    tr("schedule.instruction.package_delivery.summary").withStyle(ChatFormatting.GOLD),
                    tr("schedule.instruction.package_delivery.summary_1").withStyle(ChatFormatting.GRAY),
                    tr("schedule.instruction.package_delivery.summary_2").withStyle(ChatFormatting.GRAY));
        if (input instanceof FetchPackagesInstruction fetch)
            return List.of(
                    tr("schedule.instruction.package_retrieval.summary").withStyle(ChatFormatting.GOLD),
                    tr("generic.in_quotes", Component.literal(fetch.getLabelText())),
                    tr("schedule.instruction.package_retrieval.summary_1").withStyle(ChatFormatting.GRAY),
                    tr("schedule.instruction.package_retrieval.summary_2").withStyle(ChatFormatting.GRAY));
        if (input instanceof TextScheduleInstruction text)
            return List.of(
                    tr("schedule." + type + "." + id.getPath() + ".summary").withStyle(ChatFormatting.GOLD),
                    tr("generic.in_quotes", Component.literal(text.getLabelText())));
        if (input instanceof ChangeThrottleInstruction throttle)
            return List.of(tr(
                            "schedule." + type + "." + id.getPath() + ".summary",
                            Component.literal(throttle.intData("Value") + "%").withStyle(ChatFormatting.WHITE))
                    .withStyle(ChatFormatting.GOLD));
        if (input instanceof TimedWaitCondition timed)
            return List.of(
                    Component.translatable(id.getNamespace() + ".schedule." + type + "." + id.getPath()),
                    tr("schedule.condition.for_x_time", time(timed)).withStyle(ChatFormatting.DARK_AQUA));
        if (input instanceof TimeOfDayCondition clock)
            return List.of(
                    tr("schedule.condition.time_of_day.scheduled"),
                    clock.getDigitalDisplay(clock.intData("Hour"), clock.intData("Minute"), false)
                            .withStyle(ChatFormatting.DARK_AQUA)
                            .append(Component.literal(" -> ").withStyle(ChatFormatting.DARK_GRAY))
                            .append(tr("schedule.condition.time_of_day.rotation."
                                            + ROTATIONS[Math.floorMod(clock.intData("Rotation"), ROTATIONS.length)])
                                    .withStyle(ChatFormatting.GRAY)));
        if (input instanceof CargoThresholdCondition cargo) {
            boolean fluid = input instanceof FluidThresholdCondition;
            var filter =
                    fluid ? ((FluidThresholdCondition) input).compareStack : ((ItemThresholdCondition) input).stack;
            Component unit = tr("schedule.condition.threshold."
                    + (fluid ? "buckets" : ((ItemThresholdCondition) input).inStacks() ? "stacks" : "items"));
            Component what = filter.isEmpty()
                    ? tr("schedule.condition.threshold.anything")
                    : filter.isFilterItem()
                            ? tr("schedule.condition.threshold.matching_content")
                            : filter.item().getHoverName();
            return List.of(
                    tr(
                            "schedule.condition.threshold.train_holds",
                            tr("schedule.condition.threshold."
                                    + cargo.getOperator().name().toLowerCase(Locale.ROOT))),
                    tr("schedule.condition.threshold.x_units_of_item", cargo.getThreshold(), unit, what)
                            .withStyle(ChatFormatting.DARK_AQUA));
        }
        if (input instanceof RedstoneLinkCondition link)
            return List.of(
                    tr("schedule.condition.redstone_link.frequency_"
                            + (link.lowActivation() ? "unpowered" : "powered")),
                    Component.literal(" #1 ")
                            .withStyle(ChatFormatting.GRAY)
                            .append(link.freq
                                    .getFirst()
                                    .getStack()
                                    .getHoverName()
                                    .copy()
                                    .withStyle(ChatFormatting.DARK_AQUA)),
                    Component.literal(" #2 ")
                            .withStyle(ChatFormatting.GRAY)
                            .append(link.freq
                                    .getSecond()
                                    .getStack()
                                    .getHoverName()
                                    .copy()
                                    .withStyle(ChatFormatting.DARK_AQUA)));
        if (input instanceof PlayerPassengerCondition players) {
            int target = players.getTarget();
            return List.of(tr(
                    "schedule.condition.player_count.seated",
                    tr(
                            "schedule.condition.player_count." + (target == 1 ? "summary" : "summary_plural"),
                            Component.literal("" + target).withStyle(ChatFormatting.DARK_AQUA))));
        }
        return List.of(Component.translatable(id.getNamespace() + ".schedule." + type + "." + id.getPath()));
    }

    private static List<Identifier> typeIds(boolean instruction) {
        List<Identifier> ids = new ArrayList<>();
        if (instruction) for (var type : AllSchedules.INSTRUCTION_TYPES) ids.add(type.getFirst());
        else for (var type : AllSchedules.CONDITION_TYPES) ids.add(type.getFirst());
        return ids;
    }

    private static int slotsTargeted(ScheduleDataEntry input) {
        return input instanceof RedstoneLinkCondition ? 2 : input instanceof CargoThresholdCondition ? 1 : 0;
    }

    private static ItemStack getItem(ScheduleDataEntry input, int slot) {
        if (input instanceof RedstoneLinkCondition link)
            return link.freq.get(slot == 0).getStack();
        if (input instanceof ItemThresholdCondition items) return items.stack.item();
        if (input instanceof FluidThresholdCondition fluid) return fluid.compareStack.item();
        return ItemStack.EMPTY;
    }

    private static void setItem(ScheduleDataEntry input, int slot, ItemStack stack) {
        if (input instanceof RedstoneLinkCondition link) link.freq.set(slot == 0, Frequency.of(stack));
        else if (input instanceof ItemThresholdCondition items) items.stack = FilterItemStack.of(stack);
        else if (input instanceof FluidThresholdCondition fluid) fluid.compareStack = FilterItemStack.of(stack);
    }

    private static ItemStack secondLineIcon(ScheduleDataEntry input) {
        if (input instanceof ChangeThrottleInstruction) return AllItems.TRAIN_CONTROLS.getDefaultInstance();
        if (input instanceof ChangeTitleInstruction) return new ItemStack(Items.NAME_TAG);
        if (input instanceof DeliverPackagesInstruction)
            return AllItems.POSTBOX.white().getDefaultInstance();
        if (input instanceof FetchPackagesInstruction) return PackageStyles.getDefaultBox();
        if (input instanceof DestinationInstruction) return AllItems.TRACK_STATION.getDefaultInstance();
        if (input instanceof TimedWaitCondition) return new ItemStack(Items.REPEATER);
        return ItemStack.EMPTY;
    }

    private static @Nullable List<Component> secondLineTooltip(ScheduleDataEntry input, int slot) {
        if (input instanceof ChangeThrottleInstruction)
            return List.of(
                    tr("schedule.instruction.throttle_edit_box"),
                    tr("schedule.instruction.throttle_edit_box_1").withStyle(ChatFormatting.GRAY));
        if (input instanceof ChangeTitleInstruction)
            return List.of(
                    tr("schedule.instruction.name_edit_box"),
                    tr("schedule.instruction.name_edit_box_1").withStyle(ChatFormatting.GRAY),
                    tr("schedule.instruction.name_edit_box_2").withStyle(ChatFormatting.DARK_GRAY));
        if (input instanceof FetchPackagesInstruction)
            return List.of(
                    tr("schedule.instruction.address_filter_edit_box"),
                    tr("schedule.instruction.address_filter_edit_box_1").withStyle(ChatFormatting.GRAY),
                    tr("schedule.instruction.address_filter_edit_box_2").withStyle(ChatFormatting.DARK_GRAY),
                    tr("schedule.instruction.address_filter_edit_box_3").withStyle(ChatFormatting.DARK_GRAY));
        if (input instanceof DestinationInstruction)
            return List.of(
                    tr("schedule.instruction.filter_edit_box"),
                    tr("schedule.instruction.filter_edit_box_1").withStyle(ChatFormatting.GRAY),
                    tr("schedule.instruction.filter_edit_box_2").withStyle(ChatFormatting.DARK_GRAY),
                    tr("schedule.instruction.filter_edit_box_3").withStyle(ChatFormatting.DARK_GRAY));
        if (input instanceof TimedWaitCondition) return List.of(tr("generic.duration"));
        if (input instanceof CargoThresholdCondition)
            return List.of(
                    tr("schedule.condition.threshold.place_item"),
                    tr("schedule.condition.threshold.place_item_2").withStyle(ChatFormatting.GRAY),
                    tr("schedule.condition.threshold.place_item_3").withStyle(ChatFormatting.GRAY));
        if (input instanceof RedstoneLinkCondition)
            return List.of(tr(slot == 0 ? "logistics.firstFrequency" : "logistics.secondFrequency")
                    .withStyle(ChatFormatting.RED));
        return null;
    }

    private static EditorLine editorLine(ScheduleDataEntry input) {
        List<Widget> widgets = new ArrayList<>();
        Predicate<String> any = s -> true;
        Predicate<String> wildcards = s -> s.chars().filter(c -> c == '*').count() <= 3;
        Predicate<String> integer = s -> {
            if (s.isEmpty()) return true;
            try {
                Integer.parseInt(s);
                return true;
            } catch (NumberFormatException invalid) {
                return false;
            }
        };
        if (input instanceof ChangeThrottleInstruction) {
            widgets.add(scroll(
                    0, 50, "Value", 5, 101, 5, true, tr("schedule.instruction.throttle_edit_box"), i -> i + "%"));
        } else if (input instanceof DeliverPackagesInstruction) {
        } else if (input instanceof DestinationInstruction || input instanceof FetchPackagesInstruction) {
            widgets.add(text(Kind.TEXT, 0, 121, "Text", wildcards));
        } else if (input instanceof ChangeTitleInstruction) {
            widgets.add(text(Kind.TEXT, 0, 121, "Text", any));
        } else if (input instanceof TimedWaitCondition) {
            widgets.add(scroll(0, 31, "Value", 0, 121, 15, false, tr("generic.duration"), String::valueOf));
            List<Component> units = new ArrayList<>();
            for (TimedWaitCondition.TimeUnit unit : TimedWaitCondition.TimeUnit.values()) units.add(tr(unitKey(unit)));
            widgets.add(select(36, 85, "TimeUnit", units, tr("generic.timeUnit"), null));
        } else if (input instanceof CargoThresholdCondition) {
            List<Component> ops = new ArrayList<>();
            for (CargoThresholdCondition.Ops op : CargoThresholdCondition.Ops.values())
                ops.add(tr("schedule.condition.threshold." + op.name().toLowerCase(Locale.ROOT)));
            widgets.add(select(
                    0,
                    24,
                    "Operator",
                    ops,
                    tr("schedule.condition.threshold.train_holds", ""),
                    state -> " " + CargoThresholdCondition.Ops.values()[state].formatted));
            widgets.add(text(Kind.INTEGER, 29, 41, "Threshold", integer));
            if (input instanceof ItemThresholdCondition)
                widgets.add(select(
                        71,
                        50,
                        "Measure",
                        List.of(tr("schedule.condition.threshold.items"), tr("schedule.condition.threshold.stacks")),
                        tr("schedule.condition.threshold.item_measure"),
                        null));
            else
                widgets.add(select(71, 50, "Measure", List.of(tr("schedule.condition.threshold.buckets")), null, null));
        } else if (input instanceof PlayerPassengerCondition) {
            widgets.add(scroll(
                    0, 31, "Count", 0, 21, 5, false, tr("schedule.condition.player_count.players"), String::valueOf));
            widgets.add(select(
                    36,
                    85,
                    "Exact",
                    List.of(
                            tr("schedule.condition.player_count.exactly"),
                            tr("schedule.condition.player_count.or_above")),
                    tr("schedule.condition.player_count.condition"),
                    null));
        } else if (input instanceof RedstoneLinkCondition) {
            widgets.add(select(
                    20,
                    101,
                    "Inverted",
                    List.of(
                            tr("schedule.condition.redstone_link.powered"),
                            tr("schedule.condition.redstone_link.unpowered")),
                    tr("schedule.condition.redstone_link.frequency_state"),
                    null));
        } else if (input instanceof TimeOfDayCondition clock) {
            widgets.add(new Widget(
                    Kind.SCROLL,
                    0,
                    16,
                    "Hour",
                    0,
                    24,
                    1,
                    6,
                    false,
                    List.of(),
                    tr("generic.daytime.hour"),
                    hour -> resolve(clock.getDigitalDisplay(hour, Mth.clamp(clock.intData("Minute"), 0, 59), true)),
                    true,
                    s -> true));
            widgets.add(new Widget(
                    Kind.SCROLL,
                    18,
                    16,
                    "Minute",
                    0,
                    60,
                    1,
                    15,
                    false,
                    List.of(),
                    tr("generic.daytime.minute"),
                    String::valueOf,
                    false,
                    s -> true));
            List<Component> rotations = new ArrayList<>();
            for (String rotation : ROTATIONS) rotations.add(tr("schedule.condition.time_of_day.rotation." + rotation));
            widgets.add(select(52, 62, "Rotation", rotations, tr("schedule.condition.time_of_day.rotation"), null));
            return new EditorLine(widgets, List.of(new int[] {0, 52}, new int[] {52, 69}));
        }
        return new EditorLine(widgets, null);
    }

    private static Widget scroll(
            int x,
            int width,
            String key,
            int min,
            int max,
            int shift,
            boolean throttle,
            Component title,
            IntFunction<String> label) {
        return new Widget(
                Kind.SCROLL,
                x,
                width,
                key,
                min,
                max,
                1,
                throttle ? 5 : shift,
                throttle,
                List.of(),
                title,
                label,
                true,
                s -> true);
    }

    private static Widget select(
            int x,
            int width,
            String key,
            List<Component> options,
            @Nullable Component title,
            @Nullable IntFunction<String> label) {
        return new Widget(
                Kind.SELECT,
                x,
                width,
                key,
                0,
                options.size(),
                1,
                5,
                false,
                options,
                title,
                label != null ? label : state -> resolve(options.get(state)),
                true,
                s -> true);
    }

    private static Widget text(Kind kind, int x, int width, String key, Predicate<String> filter) {
        return new Widget(kind, x, width, key, 0, 0, 0, 0, false, List.of(), null, s -> "", true, filter);
    }

    private static int rowY(int row) {
        return row < 6 ? 18 + 18 * row : row < 9 ? 139 + 18 * (row - 6) : 197;
    }

    private static int slot(int column, int row) {
        return row < 6 ? row * 9 + column : row < 9 ? 54 + (row - 6) * 9 + column : 81 + column;
    }

    private static int maxRows(ScheduleEntry entry) {
        int rows = 0;
        for (List<ScheduleWaitCondition> column : entry.conditions) rows = Math.max(rows, column.size());
        return rows;
    }

    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        List<ScheduleEntry> entries = schedule.entries;
        for (int i = 0; i < entries.size(); i++) {
            ScheduleEntry entry = entries.get(i);
            rows.add(new Row(RowKind.HEAD, i, 0));
            if (entry.instruction.supportsConditions())
                for (int k = 0; k < maxRows(entry); k++) rows.add(new Row(RowKind.COND, i, k));
            rows.add(new Row(RowKind.LAST, i, entry.instruction.supportsConditions() ? maxRows(entry) : 0));
            rows.add(new Row(i + 1 < entries.size() ? RowKind.GAP : RowKind.END, i, 0));
        }
        if (entries.isEmpty()) rows.add(new Row(RowKind.END, -1, 0));
        return rows;
    }

    private static int fieldSize(int minSize, Summary summary) {
        int text = summary.text() == null ? 0 : width(clean(resolve(summary.text())));
        return Math.min(MAX_FIELD, Math.max(text + (summary.item().isEmpty() ? 0 : 20) + 16, minSize));
    }

    private static int columnWidth(List<ScheduleWaitCondition> column) {
        int width = 0;
        for (ScheduleWaitCondition condition : column) width = Math.max(width, fieldSize(32, summary(condition)));
        return width;
    }

    private static int cells(int width) {
        return Math.max(1, (width + 7 + 17) / 18);
    }

    private Columns columns(int entryIndex) {
        ScheduleEntry entry = schedule.entries.get(entryIndex);
        int total = 0;
        for (List<ScheduleWaitCondition> column : entry.conditions) total += cells(columnWidth(column));
        boolean scrollable = FIELD_X + 18 * total + 16 > CLIP_R;
        int first = scrollable ? horizontal(entryIndex) : 0, cell = 0;
        List<Column> visible = new ArrayList<>();
        for (int j = first; j < entry.conditions.size(); j++) {
            int x = FIELD_X + 18 * cell;
            if (x >= CLIP_R) break;
            int width = columnWidth(entry.conditions.get(j)), count = cells(width);
            visible.add(new Column(j, x, width, cell, count));
            cell += count;
        }
        int newCell = visible.size() + first == entry.conditions.size() && cell <= 6 ? cell : -1;
        return new Columns(visible, scrollable, newCell);
    }

    private int horizontal(int entry) {
        int max = Math.max(0, schedule.entries.get(entry).conditions.size() - 1);
        int value = Mth.clamp(horizontal.get(entry), 0, max);
        horizontal.set(entry, value);
        return value;
    }

    private void resetHorizontal() {
        horizontal.clear();
        for (int i = 0; i < schedule.entries.size(); i++) horizontal.add(0);
    }

    private static int arrowRow(ScheduleEntry entry) {
        int rows = maxRows(entry);
        return Math.min((1 + 9 * rows) / 18, Math.max(0, rows - 1));
    }

    private void refresh() {
        schedule.savedProgress =
                schedule.entries.isEmpty() ? 0 : Mth.clamp(schedule.savedProgress, 0, schedule.entries.size() - 1);
        while (horizontal.size() < schedule.entries.size()) horizontal.add(0);
        while (horizontal.size() > schedule.entries.size()) horizontal.removeLast();
        controls.clear();
        Pen pen = new Pen();
        String coverState = buildMain(pen);
        if (editor != null) {
            controls.clear();
            coverState = "";
            buildEditor(pen);
        }
        pen.glyph(Piece.ITEM_3X, 193, 169);
        for (int slot = 0; slot < 90; slot++) {
            if (editor != null && slot >= 54) {
                int index = slot < 81 ? slot - 54 + 9 : slot - 81;
                setSlot(slot, new Slot(player.getInventory(), index, 0, 0));
                continue;
            }
            Control control = controls.get(slot);
            ItemStack base = slot == COVER_SLOT
                    ? PolymerArtKit.stack(COVER_ITEM, List.of(coverState))
                    : control != null && control.icon() != null
                            ? control.icon().copyWithCount(1)
                            : PolymerArtKit.stack(EMPTY_ITEM, null);
            if (control != null && control.plainItem())
                setSlot(slot, PolymerGuiControls.builder(base).build());
            else setSlot(slot, PolymerGuiControls.decoration(base, control == null ? null : lines(control.tooltip())));
        }
        Component title = pen.out;
        if (!title.equals(shownTitle)) {
            shownTitle = title;
            setTitle(title);
        }
        lifecycle.requestSync();
    }

    private String buildMain(Pen pen) {
        List<Row> rows = rows();
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - LIST_ROWS));
        pen.glyph(Piece.PANEL, ART_X, ART_Y);
        String cover = "gap";
        Map<Integer, Columns> layouts = new HashMap<>();
        for (int k = 0; k < LIST_ROWS; k++) {
            int v = scroll + k;
            if (v >= rows.size()) break;
            Row row = rows.get(v);
            if (k == 6) cover = filler(pen, rows.get(v - 1), row);
            drawRow(pen, rows, v, k, layouts);
        }
        pointer(pen, rows);
        pen.glyph(Piece.GRAD_TOP, -31, 15);
        pen.glyph(Piece.GRAD_BOTTOM, -31, 178);
        String title = clean(resolve(held.getHoverName())).replace('▤', '?');
        pen.text(title, TITLE_CENTER - width(title) / 2, TITLE_TOP, TITLE_COLOR, false);
        if (editor == null) bottomBar(pen, rows);
        return cover;
    }

    private String filler(Pen pen, Row above, Row below) {
        boolean supports = above.entry() >= 0
                && schedule.entries.get(above.entry()).instruction.supportsConditions();
        String cover = "gap";
        if (above.kind() == RowKind.HEAD) {
            pen.glyph(supports ? Piece.FILL_LIGHT : Piece.FILL, CARD_X, FILLER_Y);
            cover = "body";
        } else if (above.kind() == RowKind.COND) {
            pen.glyph(Piece.FILL, CARD_X, FILLER_Y);
            cover = "body";
        } else if (below.kind() == RowKind.HEAD) cover = "gap_head";
        if (above.kind() != RowKind.END) pen.glyph(Piece.LINE_FILL, CARD_X + 8, FILLER_Y);
        return cover;
    }

    private void drawRow(Pen pen, List<Row> rows, int v, int k, Map<Integer, Columns> layouts) {
        Row row = rows.get(v);
        int y = rowY(k), i = row.entry();
        ScheduleEntry entry = i >= 0 ? schedule.entries.get(i) : null;
        boolean supports = entry != null && entry.instruction.supportsConditions();
        boolean lightTop = supports && v > 0 && rows.get(v - 1).kind() == RowKind.HEAD && k != 6;
        switch (row.kind()) {
            case HEAD -> pen.glyph(supports ? Piece.HEAD_LIGHT : Piece.HEAD_MEDIUM, CARD_X, y - 4);
            case COND -> pen.glyph(lightTop ? Piece.BODY_LIGHT : Piece.BODY, CARD_X, y - 4);
            case LAST -> pen.glyph(lightTop ? Piece.BOTTOM_LIGHT : Piece.BOTTOM, CARD_X, y - 4);
            default -> {}
        }
        pen.glyph(Piece.LINE, CARD_X + 8, y - 4);
        switch (row.kind()) {
            case HEAD -> {
                pen.glyph(supports ? Piece.STRIP_TRAVEL : Piece.STRIP_ACTION, CARD_X + 4, y + 1);
                instruction(pen, entry, i, k, y);
            }
            case COND, LAST -> {
                if (row.kind() == RowKind.COND && row.index() == 0) pen.glyph(Piece.STRIP_WAIT, CARD_X + 4, y - 1);
                if (row.kind() == RowKind.LAST) {
                    pen.glyph(Piece.DUPLICATE, CARD_X + CARD_W - 14, y);
                    control(7, k, null, List.of(tr("gui.schedule.duplicate")), (right, shift) -> {
                        if (right) return;
                        schedule.entries.add(schedule.entries.indexOf(entry), entry.clone(player.registryAccess()));
                        resetHorizontal();
                    });
                }
                if (rows.get(v - 1).kind() == RowKind.HEAD && i < schedule.entries.size() - 1) {
                    pen.glyph(Piece.MOVE_DOWN, CARD_X + CARD_W, y);
                    control(8, k, null, List.of(tr("gui.schedule.move_down")), (right, shift) -> {
                        if (right) return;
                        schedule.entries.remove(entry);
                        schedule.entries.add(i + 1, entry);
                        resetHorizontal();
                    });
                }
                if (supports)
                    conditions(
                            pen,
                            entry,
                            i,
                            row.kind() == RowKind.COND ? row.index() : maxRows(entry),
                            k,
                            y,
                            layouts.computeIfAbsent(i, this::columns));
            }
            case GAP -> pen.glyph(Piece.STRIP_DOTTED, CARD_X + 4, y - 3);
            case END -> {
                pen.glyph(Piece.STRIP_END, CARD_X + 4, y);
                pen.glyph(Piece.CARD_NEW, 8, y);
                control(0, k, null, List.of(tr("gui.schedule.add_entry")), (right, shift) -> {
                    if (right) return;
                    ScheduleInstruction instruction = AllSchedules.createScheduleInstruction(AllSchedules.DESTINATION);
                    startEditing(
                            instruction,
                            true,
                            confirmed -> {
                                if (!confirmed) return;
                                ScheduleEntry created = new ScheduleEntry();
                                ArrayList<ScheduleWaitCondition> initial = new ArrayList<>();
                                initial.add(AllSchedules.createScheduleWaitCondition(AllSchedules.DELAY));
                                created.instruction = (ScheduleInstruction) editor.input;
                                created.conditions.add(initial);
                                schedule.entries.add(created);
                            },
                            true);
                });
            }
        }
    }

    private void instruction(Pen pen, ScheduleEntry entry, int i, int k, int y) {
        ScheduleInstruction instruction = entry.instruction;
        Summary summary = summary(instruction);
        int width = fieldSize(100, summary);
        field(pen, FIELD_X, y, width, summary, false, CLIP_R);
        List<Component> tip = new ArrayList<>(titleAs(instruction, "instruction"));
        tip.add(Component.empty());
        tip.add(tr("gui.schedule.lmb_edit").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        ItemStack icon = summary.item().isEmpty() ? null : summary.item();
        Action edit = (right, shift) -> {
            if (right) return;
            startEditing(
                    instruction,
                    true,
                    confirmed -> {
                        if (confirmed) entry.instruction = (ScheduleInstruction) editor.input;
                    },
                    false);
        };
        for (int c = 0; 16 + 18 * c < FIELD_X + width && c < 7; c++) control(c, k, c == 0 ? icon : null, tip, edit);
        pen.glyph(Piece.REMOVE, CARD_X + CARD_W - 14, y - 2);
        control(7, k, null, List.of(tr("gui.schedule.remove_entry")), (right, shift) -> {
            if (right) return;
            schedule.entries.remove(entry);
            resetHorizontal();
        });
        if (i > 0) {
            pen.glyph(Piece.MOVE_UP, CARD_X + CARD_W, y);
            control(8, k, null, List.of(tr("gui.schedule.move_up")), (right, shift) -> {
                if (right) return;
                schedule.entries.remove(entry);
                schedule.entries.add(i - 1, entry);
                resetHorizontal();
            });
        }
    }

    private void conditions(Pen pen, ScheduleEntry entry, int i, int condRow, int k, int y, Columns layout) {
        List<List<ScheduleWaitCondition>> columns = entry.conditions;
        for (Column column : layout.visible()) {
            List<ScheduleWaitCondition> conditions = columns.get(column.index());
            if (condRow < conditions.size()) {
                ScheduleWaitCondition condition = conditions.get(condRow);
                Summary summary = summary(condition);
                field(pen, column.x(), y, column.width(), summary, condRow != 0, CLIP_R);
                boolean canRemove = conditions.size() > 1 || columns.size() > 1;
                List<Component> tip = new ArrayList<>();
                tip.add(tr("schedule.condition_type").withStyle(ChatFormatting.GRAY));
                tip.addAll(titleAs(condition, "condition"));
                tip.add(Component.empty());
                tip.add(tr("gui.schedule.lmb_edit").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                if (canRemove)
                    tip.add(tr("gui.schedule.rmb_remove").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                ItemStack icon = summary.item().isEmpty()
                        ? null
                        : summary.item().is(Items.STRUCTURE_VOID)
                                ? clock((TimeOfDayCondition) condition)
                                : summary.item();
                int row = condRow;
                Action action = (right, shift) -> {
                    if (right) {
                        if (!canRemove) return;
                        conditions.remove(row);
                        if (conditions.isEmpty()) columns.remove(conditions);
                        return;
                    }
                    startEditing(
                            condition,
                            false,
                            confirmed -> {
                                conditions.remove(row);
                                if (confirmed) {
                                    conditions.add(row, (ScheduleWaitCondition) editor.input);
                                    return;
                                }
                                if (conditions.isEmpty()) columns.remove(conditions);
                            },
                            canRemove);
                };
                for (int c = column.firstCell(); c < column.firstCell() + column.cells() && c < 7; c++)
                    if (16 + 18 * c < column.x() + column.width())
                        control(c, k, c == column.firstCell() && column.x() + 21 <= CLIP_R ? icon : null, tip, action);
            } else if (condRow == conditions.size()) {
                int x = column.x() + (column.width() - 10) / 2;
                if (x + 10 <= CLIP_R) {
                    pen.glyph(Piece.APPEND, x, y);
                    int c = Math.floorDiv(x + 5 - 7, 18);
                    if (c >= 0 && c < 7)
                        control(c, k, null, List.of(tr("gui.schedule.add_condition")), (right, shift) -> {
                            if (right) return;
                            ScheduleWaitCondition condition =
                                    AllSchedules.createScheduleWaitCondition(AllSchedules.DELAY);
                            startEditing(
                                    condition,
                                    false,
                                    confirmed -> {
                                        if (confirmed) conditions.add((ScheduleWaitCondition) editor.input);
                                    },
                                    true);
                        });
                }
            }
        }
        if (condRow == 0 && layout.newCell() >= 0) {
            pen.glyph(Piece.COND_NEW, FIELD_X + 18 * layout.newCell() - 3, y);
            control(layout.newCell(), k, null, List.of(tr("gui.schedule.alternative_condition")), (right, shift) -> {
                if (right) return;
                ScheduleWaitCondition condition = AllSchedules.createScheduleWaitCondition(AllSchedules.DELAY);
                startEditing(
                        condition,
                        false,
                        confirmed -> {
                            if (!confirmed) return;
                            ArrayList<ScheduleWaitCondition> created = new ArrayList<>();
                            created.add((ScheduleWaitCondition) editor.input);
                            columns.add(created);
                        },
                        true);
            });
        }
        if (layout.scrollable() && condRow == arrowRow(entry) && condRow < maxRows(entry)) {
            int h = horizontal(i);
            if (h > 0) pen.glyph(Piece.SCROLL_LEFT, CARD_X + 15, y + 4);
            if (h < columns.size() - 1) pen.glyph(Piece.SCROLL_RIGHT, CLIP_R + 1, y + 4);
            control(
                    7,
                    k,
                    null,
                    List.of(
                            Component.literal("Scroll conditions"),
                            Component.literal("Left-click: right   Right-click: left")
                                    .withStyle(s -> s.withColor(HINT_RGB))),
                    (right, shift) -> {
                        int value = horizontal(i);
                        if (right && value > 0) horizontal.set(i, value - 1);
                        if (!right && value < columns.size() - 1) horizontal.set(i, value + 1);
                    });
        }
    }

    private void field(Pen pen, int x, int y, int width, Summary summary, boolean clean, int clip) {
        boolean hasItem = !summary.item().isEmpty();
        int end = Math.min(x + width, clip);
        stretch(pen, MIDDLE, x, end - x, y);
        if (clean) pen.glyph(Piece.COND_LEFT_CLEAN, x, y);
        else pen.glyph(Piece.COND_LEFT, x - 3, y);
        if (x + width <= clip) pen.glyph(Piece.COND_RIGHT, x + width - 2, y);
        if (hasItem && x + 21 <= clip) pen.glyph(Piece.COND_ITEM, x + 3, y);
        if (summary.text() == null) return;
        int textX = x + (hasItem ? 28 : 8);
        String text = fit(clean(resolve(summary.text())), Math.min(120, Math.min(x + width, clip) - textX - 2));
        pen.text(text, textX, y + 4, TEXT_COLOR, true);
    }

    private static void stretch(Pen pen, Piece[] pieces, int x, int width, int top) {
        for (int bit = pieces.length - 1; bit >= 0; bit--)
            while (width >= 1 << bit) {
                pen.glyph(pieces[bit], x, top);
                x += 1 << bit;
                width -= 1 << bit;
            }
    }

    private void pointer(Pen pen, List<Row> rows) {
        if (schedule.entries.isEmpty()) return;
        for (int v = 0; v < rows.size(); v++) {
            Row row = rows.get(v);
            if (row.kind() != RowKind.HEAD || row.entry() != schedule.savedProgress) continue;
            if (v < scroll) pen.glyph(Piece.POINTER_OFF, ART_X, 17);
            else if (v >= scroll + LIST_ROWS) pen.glyph(Piece.POINTER_OFF, ART_X, 169);
            else pen.glyph(Piece.POINTER, ART_X, rowY(v - scroll));
            return;
        }
    }

    private void bottomBar(Pen pen, List<Row> rows) {
        List<Component> loop = new ArrayList<>();
        loop.add(tr("schedule.loop"));
        loop.add(
                schedule.cyclic
                        ? tr("gui.schematicannon.optionEnabled").withStyle(ChatFormatting.DARK_GREEN)
                        : tr("gui.schematicannon.optionDisabled").withStyle(ChatFormatting.RED));
        loop.add(tr("schedule.loop1").withStyle(ChatFormatting.GRAY));
        loop.add(tr("schedule.loop2").withStyle(ChatFormatting.GRAY));
        pen.glyph(schedule.cyclic ? Piece.BTN_LOOP_ON : Piece.BTN_LOOP, 7, BUTTON_Y);
        control(0, 9, null, loop, (right, shift) -> {
            if (!right) schedule.cyclic = !schedule.cyclic;
        });
        boolean reset = schedule.savedProgress > 0, skip = schedule.entries.size() > 1;
        pen.glyph(reset ? Piece.BTN_RESET : Piece.BTN_RESET_OFF, 25, BUTTON_Y);
        control(1, 9, null, List.of(tr("schedule.reset")), (right, shift) -> {
            if (!right && schedule.savedProgress > 0) schedule.savedProgress = 0;
        });
        pen.glyph(skip ? Piece.BTN_SKIP : Piece.BTN_SKIP_OFF, 43, BUTTON_Y);
        control(2, 9, null, List.of(tr("schedule.skip")), (right, shift) -> {
            if (right || schedule.entries.size() <= 1) return;
            schedule.savedProgress = (schedule.savedProgress + 1) % schedule.entries.size();
        });
        int max = Math.max(0, rows.size() - LIST_ROWS);
        pen.glyph(scroll > 0 ? Piece.BTN_UP : Piece.BTN_UP_OFF, 97, BUTTON_Y);
        control(
                5,
                9,
                null,
                List.of(
                        Component.literal("Scroll up"),
                        Component.literal("Shift: 4 rows").withStyle(s -> s.withColor(HINT_RGB))),
                (right, shift) -> scroll = Math.max(0, scroll - (shift ? 4 : 1)));
        pen.glyph(scroll < max ? Piece.BTN_DOWN : Piece.BTN_DOWN_OFF, 115, BUTTON_Y);
        control(
                6,
                9,
                null,
                List.of(
                        Component.literal("Scroll down"),
                        Component.literal("Shift: 4 rows").withStyle(s -> s.withColor(HINT_RGB))),
                (right, shift) -> scroll = Math.min(max, scroll + (shift ? 4 : 1)));
        pen.glyph(Piece.BTN_CONFIRM, 151, BUTTON_Y);
        control(8, 9, null, List.of(tr("action.confirm")), (right, shift) -> {
            if (!right) close();
        });
    }

    private void startEditing(
            ScheduleDataEntry input, boolean instruction, Consumer<Boolean> onClose, boolean allowDeletion) {
        for (int i = 0; i < slotsTargeted(input); i++)
            ghost[i] = getItem(input, i).copy();
        editor = new Editor(input, instruction, onClose, allowDeletion);
    }

    private void stopEditing() {
        Editor current = editor;
        if (current == null) return;
        for (int i = 0; i < slotsTargeted(current.input); i++) setItem(current.input, i, ghost[i].copy());
        current.save();
        editor = null;
        resetHorizontal();
    }

    private void buildEditor(Pen pen) {
        Editor current = editor;
        ScheduleDataEntry input = current.input;
        pen.glyph(Piece.DIM, DIM_X, DIM_Y);
        pen.glyph(Piece.EDITOR, EDITOR_X, EDITOR_Y);
        pen.glyph(Piece.INVENTORY, 0, INVENTORY_Y);
        String title = PolymerSchematicGuis.lang(
                current.instruction ? "create.schedule.instruction.editor" : "create.schedule.condition.editor");
        pen.text(title, TITLE_CENTER - width(title) / 2, EDITOR_TITLE_TOP, TITLE_COLOR, false);

        List<Component> options = new ArrayList<>();
        int state = 0;
        List<Identifier> types = typeIds(current.instruction);
        for (int i = 0; i < types.size(); i++) {
            Identifier id = types.get(i);
            options.add(Component.translatable(id.getNamespace() + ".schedule."
                    + (current.instruction ? "instruction." : "condition.") + id.getPath()));
            if (id.equals(input.getId())) state = i;
        }
        pen.text(clean(resolve(options.get(state))), SELECTOR_TEXT_X, SELECTOR_TOP, 0xFFFFFF, true);
        List<Component> selectorTip = selectionTooltip(
                tr(current.instruction ? "schedule.instruction_type" : "schedule.condition_type"), options, state);
        int typeState = state;
        for (int c = 0; c < 8; c++)
            control(c, 2, null, selectorTip, (right, shift) -> {
                int next = scrolled(typeState, 0, options.size(), 1, 5, right, shift);
                if (next == typeState) return;
                Identifier id = types.get(next);
                ScheduleDataEntry created = current.instruction
                        ? AllSchedules.createScheduleInstruction(id)
                        : AllSchedules.createScheduleWaitCondition(id);
                if (created == null || created.getId().equals(current.input.getId())) return;
                current.input = created;
                current.load();
            });

        int targeted = slotsTargeted(input);
        for (int i = 0; i < targeted; i++) {
            pen.glyph(Piece.SLOT_ADDITIONAL, 7 + 18 * i, LINE_Y);
            int index = i;
            List<Component> tip = ghost[i].isEmpty() ? secondLineTooltip(input, i) : null;
            controls.put(
                    slot(i, 3),
                    new Control(
                            ghost[i].isEmpty() ? null : ghost[i],
                            tip,
                            (right, shift) -> clickGhost(index, shift),
                            !ghost[i].isEmpty()));
        }
        if (targeted == 0) {
            ItemStack icon = input instanceof TimeOfDayCondition clock
                    ? clock(clock)
                    : summary(input).item();
            if (icon.isEmpty()) icon = secondLineIcon(input);
            if (icon.isEmpty()) pen.glyph(Piece.SLOT_INACTIVE, 7, LINE_Y);
            control(0, 3, icon.isEmpty() ? null : icon, secondLineTooltip(input, 0), (right, shift) -> {});
        }

        List<int[]> boxes = current.line.boxes();
        if (boxes == null) {
            boxes = new ArrayList<>();
            for (Widget widget : current.line.widgets()) boxes.add(new int[] {widget.x(), widget.width()});
        }
        boolean first = true;
        for (int[] box : boxes) {
            int x = WIDGET_X + box[0];
            stretch(pen, DATA_AREA, x, box[1], LINE_Y);
            if (first) pen.glyph(Piece.DA_SPEECH, x - 3, LINE_Y);
            else pen.glyph(Piece.DA_START, x, LINE_Y);
            pen.glyph(Piece.DA_END, x + box[1] - 2, LINE_Y);
            first = false;
        }
        for (Widget widget : current.line.widgets()) {
            int x = WIDGET_X + widget.x() + 5;
            if (widget.kind() == Kind.TEXT || widget.kind() == Kind.INTEGER)
                pen.text(
                        fit(clean(current.texts.get(widget.key())), widget.width() - 9), x, WIDGET_TOP, 0xFFFFFF, true);
            else if (widget.labelVisible())
                pen.text(clean(widget.label().apply(current.ints.get(widget.key()))), x, WIDGET_TOP, 0xFFFFFF, true);
            for (int c = Math.max(1, targeted); c < 8; c++) {
                int centre = 16 + 18 * c;
                if (centre >= WIDGET_X + widget.x() && centre < WIDGET_X + widget.x() + widget.width())
                    control(
                            c,
                            3,
                            null,
                            widgetTooltip(widget, current),
                            (right, shift) -> clickWidget(widget, right, shift));
            }
        }

        if (current.allowDeletion) {
            pen.glyph(Piece.BTN_TRASH, 7, TRASH_Y);
            control(0, 4, null, null, (right, shift) -> {
                if (right) return;
                current.onClose.accept(false);
                stopEditing();
            });
        }
        pen.glyph(Piece.BTN_CONFIRM, 151, LINE_Y);
        control(8, 3, null, null, (right, shift) -> {
            if (right) return;
            current.onClose.accept(true);
            stopEditing();
        });
    }

    private List<Component> widgetTooltip(Widget widget, Editor current) {
        if (widget.kind() == Kind.TEXT || widget.kind() == Kind.INTEGER)
            return List.of(Component.literal("Click to edit text").withStyle(s -> s.withColor(HINT_RGB)));
        if (widget.kind() == Kind.SELECT) {
            if (widget.title() == null) return null;
            return selectionTooltip(widget.title(), widget.options(), current.ints.get(widget.key()));
        }
        return List.of(
                widget.title().copy().withStyle(s -> s.withColor(HEADER_RGB)),
                Component.literal("Left-click: up   Right-click: down")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                Component.literal("Shift-click to change faster")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    private static List<Component> selectionTooltip(Component title, List<Component> options, int state) {
        List<Component> tip = new ArrayList<>();
        tip.add(title.copy().withStyle(s -> s.withColor(HEADER_RGB)));
        int size = options.size();
        int min = Math.max(Math.min(size - 16, state - 7), 0), max = Math.min(Math.max(16, state + 8), size);
        if (min == 1) min--;
        if (min > 0) tip.add(Component.literal("> ...").withStyle(ChatFormatting.GRAY));
        if (max == size - 1) max++;
        for (int i = min; i < max; i++)
            tip.add(
                    i == state
                            ? Component.empty()
                                    .append("-> ")
                                    .append(options.get(i))
                                    .withStyle(ChatFormatting.WHITE)
                            : Component.empty()
                                    .append("> ")
                                    .append(options.get(i))
                                    .withStyle(ChatFormatting.GRAY));
        if (max < size) tip.add(Component.literal("> ...").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Left-click: next   Right-click: previous")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        return tip;
    }

    private static int scrolled(int state, int min, int max, int step, int shiftStep, boolean right, boolean shift) {
        int next = state + (right ? -1 : 1) * (shift ? shiftStep : step);
        if (shift) next -= next % shiftStep;
        return Mth.clamp(next, min, max - 1);
    }

    private void clickWidget(Widget widget, boolean right, boolean shift) {
        Editor current = editor;
        if (current == null) return;
        if (widget.kind() == Kind.TEXT || widget.kind() == Kind.INTEGER) {
            editText(current, widget);
            return;
        }
        int state = current.ints.get(widget.key());
        int step = widget.throttle() ? shift ? 25 : 5 : shift ? widget.shiftStep() : widget.step();
        int next = state + (right ? -1 : 1) * step;
        if (shift) next -= next % widget.shiftStep();
        next = Mth.clamp(next, widget.min(), widget.max() - 1);
        current.ints.put(widget.key(), next);
        if (current.input instanceof TimeOfDayCondition
                && (widget.key().equals("Hour") || widget.key().equals("Minute")))
            current.input.getData().putInt(widget.key(), next);
    }

    private void clickGhost(int index, boolean shift) {
        ItemStack carried = player.containerMenu.getCarried();
        if (shift) ghost[index] = ItemStack.EMPTY;
        else ghost[index] = carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1);
    }

    private void editText(Editor current, Widget widget) {
        editText(current, widget, current.texts.get(widget.key()), 0);
    }

    private void editText(Editor current, Widget widget, String query, int page) {
        PolymerScheduleStations.Page stations = current.input instanceof DestinationInstruction destination
                ? PolymerScheduleStations.page(
                        Create.RAILWAYS.trackNetworks.values(),
                        schedule,
                        destination,
                        player.position(),
                        query,
                        page,
                        TEXT.maxLength())
                : null;
        CompoundTag action =
                TEXT_EDITS.open(player.getUUID(), new TextEdit(this, current, current.input, widget, query, stations));
        List<Component> about = secondLineTooltip(current.input, 0);
        Component label = widget.kind() == Kind.INTEGER
                ? Component.literal("Threshold")
                : about != null ? about.getFirst() : Component.literal(widget.key());
        MutableComponent body = Component.empty();
        if (about != null)
            for (int i = 0; i < about.size(); i++)
                body.append(i == 0 ? Component.empty() : Component.literal("\n"))
                        .append(about.get(i));
        if (stations != null) {
            body.append(
                    "\n\nType a prefix, then Find stations. Choose a name or confirm your own text / wildcard filter.");
            body.append(
                    stations.total() == 0
                            ? "\nNo matching stations."
                            : "\n" + stations.total() + " matching stations; page " + (stations.index() + 1) + "/"
                                    + ((stations.total() - 1) / PolymerScheduleStations.PAGE_SIZE + 1) + ".");
        }
        Input input = TEXT.input(240, label, query);
        var common = new CommonDialogData(
                Component.translatable(
                        current.instruction
                                ? "create.schedule.instruction.editor"
                                : "create.schedule.condition.editor"),
                Optional.empty(),
                true,
                false,
                DialogAction.CLOSE,
                List.of(new PlainMessage(body, 240)),
                List.of(input));
        if (stations == null) {
            PolymerDialogForms.open(
                    player,
                    Holder.direct(new NoticeDialog(common, textButton(tr("action.confirm"), action, "confirm", -1))));
            return;
        }
        var buttons = new ArrayList<ActionButton>();
        buttons.add(textButton(Component.literal("Find stations"), action, "search", -1));
        buttons.add(textButton(tr("action.confirm"), action, "confirm", -1));
        for (int i = 0; i < stations.names().size(); i++)
            buttons.add(textButton(Component.literal(stations.names().get(i)), action, "choose", i));
        if (stations.previous()) buttons.add(textButton(Component.literal("Previous"), action, "previous", -1));
        if (stations.next()) buttons.add(textButton(Component.literal("Next"), action, "next", -1));
        PolymerDialogForms.open(
                player,
                Holder.direct(new MultiActionDialog(
                        common,
                        buttons,
                        Optional.of(textButton(Component.translatable("gui.cancel"), action, "cancel", -1)),
                        2)));
    }

    private static ActionButton textButton(Component label, CompoundTag session, String operation, int choice) {
        CompoundTag action = session.copy();
        action.putString("operation", operation);
        if (choice >= 0) action.putInt("choice", choice);
        return new ActionButton(
                new CommonButtonData(label, Optional.of(label), 200),
                Optional.of(new CustomAll(TEXT_ACTION, Optional.of(action))));
    }

    static boolean handleCustomAction(
            ServerGamePacketListenerImpl listener, ServerboundCustomClickActionPacket packet) {
        if (!PolymerDialogForms.validSubmission(listener.player, packet.id(), packet.payload(), TEXT_ACTION))
            return true;
        if (!TEXT_ACTION.equals(packet.id())) return false;
        if (!listener.server.isSameThread()) {
            listener.server.execute(() -> handleCustomAction(listener, packet));
            return true;
        }
        ServerPlayer player = listener.player;
        var data = PolymerDialogForms.payload(packet.payload());
        if (data.isEmpty()) return true;
        var session = TEXT_EDITS.consume(player.getUUID(), data.get());
        if (session.isEmpty()) return true;
        TextEdit edit = session.get();
        PolymerScheduleScreen gui = edit.gui();
        if (SguiUtils.getCurrentGui(player) != gui
                || !gui.isOpen()
                || gui.editor != edit.editor()
                || edit.editor().input != edit.input()) return true;
        if (!gui.valid()) {
            gui.close();
            return true;
        }
        String operation = data.get().getStringOr("operation", "confirm");
        if (operation.equals("cancel")) {
            gui.refresh();
            return true;
        }
        Optional<String> text = TEXT.read(data.get());
        if (text.isEmpty()) return true;
        String value = truncate(StringUtil.filterText(text.get()));
        if (!edit.widget().filter().test(value)) {
            gui.editText(edit.editor(), edit.widget(), edit.query(), 0);
            return true;
        }
        var stations = edit.stations();
        if (stations != null
                && (operation.equals("search") || operation.equals("previous") || operation.equals("next"))) {
            int page = !operation.equals("search") && value.equals(edit.query()) ? stations.index() : 0;
            if (value.equals(edit.query()) && operation.equals("previous") && stations.previous()) page--;
            if (value.equals(edit.query()) && operation.equals("next") && stations.next()) page++;
            gui.editText(edit.editor(), edit.widget(), value, page);
            return true;
        }
        if (operation.equals("choose") && stations != null) {
            int choice = data.get().getIntOr("choice", -1);
            if (choice < 0 || choice >= stations.names().size()) {
                gui.editText(edit.editor(), edit.widget(), value, stations.index());
                return true;
            }
            value = stations.names().get(choice);
            if (!edit.widget().filter().test(value)) return true;
        } else if (!operation.equals("confirm")) return true;
        edit.editor().texts.put(edit.widget().key(), value);
        gui.refresh();
        return true;
    }

    private static String truncate(String value) {
        return value.length() > TEXT.maxLength() ? value.substring(0, TEXT.maxLength()) : value;
    }

    private void control(
            int column, int row, @Nullable ItemStack icon, @Nullable List<Component> tooltip, Action action) {
        controls.put(slot(column, row), new Control(icon, tooltip, action, false));
    }

    @Override
    public boolean onAnyClick(int index, ClickType click, ContainerInput action) {
        if (!valid()) {
            close();
            return false;
        }
        if (index < 0 || index >= 90) return editor != null && action != ContainerInput.QUICK_MOVE;
        if (editor != null && index >= 54) return inventoryClick(index, action);
        if (!PolymerGuiControls.primaryClick(click, action)) return false;
        Control control = controls.get(index);
        if (control == null) return false;
        control.action().click(click.isRight, click.shift);
        refresh();
        return false;
    }

    private boolean inventoryClick(int index, ContainerInput action) {
        int heldSlot = 81 + player.getInventory().getSelectedSlot();
        if (index == heldSlot && action != ContainerInput.THROW) return false;
        if (action == ContainerInput.SWAP) return false;
        if (action == ContainerInput.PICKUP_ALL
                && ItemStack.isSameItemSameComponents(player.containerMenu.getCarried(), this.held)) return false;
        if (action == ContainerInput.QUICK_MOVE) {
            ItemStack stack = player.getInventory().getItem(index < 81 ? index - 54 + 9 : index - 81);
            for (int i = 0; i < slotsTargeted(editor.input); i++)
                if (ghost[i].isEmpty()) {
                    ghost[i] = stack.copyWithCount(1);
                    break;
                }
            refresh();
            return false;
        }
        lifecycle.requestSync();
        return true;
    }

    @Override
    public void onTick() {
        if (!valid()) {
            close();
            return;
        }
        if (wrappedMenu == null) return;
        lifecycle.tick(() -> {}, () -> wrappedMenu.sendAllDataToRemote());
    }

    @Override
    public void onRemoved() {
        lifecycle.close(
                () -> {
                    if (valid()) AllHandle.onScheduleEdit(player.connection, new ScheduleEditPacket(schedule));
                },
                () -> {
                    TEXT_EDITS.forget(player.getUUID());
                    controls.clear();
                    PolymerInventorySync.afterMenu(player);
                });
    }

    private static final class Pen {
        final MutableComponent out = Component.empty();
        int pen = 8;

        void glyph(Piece piece, int x, int top) {
            if (!PLACEMENTS.getOrDefault(top, Set.of()).contains(piece))
                throw new IllegalStateException("Unregistered schedule glyph " + piece + " at " + top);
            out.append(PolymerArtKit.space(SPACE_FONT, x - pen));
            out.append(Component.literal(piece.glyph())
                    .withStyle(Style.EMPTY
                            .withFont(rowFont(top))
                            .withColor(0xFFFFFF)
                            .withoutShadow()));
            pen = x + piece.width + 1;
        }

        void text(String text, int x, int top, int color, boolean shadow) {
            if (text.isEmpty()) return;
            if (!TEXT_TOPS.contains(top)) throw new IllegalStateException("Unregistered schedule text row " + top);
            out.append(PolymerArtKit.space(SPACE_FONT, x - pen));
            Style style = Style.EMPTY.withFont(rowFont(top)).withColor(color);
            style = shadow ? style.withShadowColor(0xFF000000 | shadowColor(color)) : style.withoutShadow();
            out.append(Component.literal(text).withStyle(style));
            pen = x + width(text);
        }
    }

    private static int shadowColor(int color) {
        return (int) ((color >> 16 & 255) * .25f) << 16
                | (int) ((color >> 8 & 255) * .25f) << 8
                | (int) ((color & 255) * .25f);
    }

    private static String clean(String text) {
        StringBuilder out = new StringBuilder();
        text.codePoints().forEach(cp -> out.appendCodePoint(cp >= 32 && cp < 127 || cp == STACKS ? cp : '?'));
        return out.toString();
    }

    private static int width(String text) {
        int width = 0;
        for (int cp : text.codePoints().toArray())
            width += cp == ' ' ? 4 : cp == STACKS ? 8 : PolymerGogglesOverlay.width(Character.toString(cp));
        return width;
    }

    private static String fit(String text, int limit) {
        if (limit <= 0) return "";
        int end = 0, used = 0;
        int[] cps = text.codePoints().toArray();
        for (; end < cps.length; end++) {
            int advance = width(Character.toString(cps[end]));
            if (used + advance > limit) break;
            used += advance;
        }
        return new String(cps, 0, end);
    }

    private static ItemStack clock(TimeOfDayCondition input) {
        int displayHour = (input.intData("Hour") + 12) % 24;
        float progress = (displayHour * 60f + input.intData("Minute")) / (24 * 60);
        int frame = Mth.clamp((int) (progress * 64), 0, 63);
        ItemStack stack = new ItemStack(Items.CLOCK);
        stack.set(DataComponents.ITEM_MODEL, CreateIds.id("ui/schedule_clock_" + (frame < 10 ? "0" : "") + frame));
        return stack;
    }

    private static @Nullable List<Component> lines(@Nullable List<Component> tooltip) {
        if (tooltip == null) return null;
        List<Component> out = new ArrayList<>();
        for (Component line : tooltip)
            out.add(line.copy()
                    .withStyle(s -> s.withItalic(s.isItalic())
                            .withColor(s.getColor() == null ? TextColor.fromRgb(0xFFFFFF) : s.getColor())));
        return out;
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("create." + key, args);
    }

    static String resolve(Component component) {
        StringBuilder out = new StringBuilder();
        if (component.getContents() instanceof TranslatableContents translatable) {
            String pattern = PolymerSchematicGuis.all().getOrDefault(translatable.getKey(), translatable.getKey());
            Object[] args = translatable.getArgs();
            StringBuilder text = new StringBuilder();
            int next = 0;
            for (int i = 0; i < pattern.length(); i++) {
                char c = pattern.charAt(i);
                if (c != '%' || i + 1 >= pattern.length()) {
                    text.append(c);
                    continue;
                }
                char d = pattern.charAt(i + 1);
                if (d == '%') {
                    text.append('%');
                    i++;
                    continue;
                }
                int argument = -1, skip = 0;
                if (d == 's') {
                    argument = next++;
                    skip = 1;
                } else if (Character.isDigit(d)
                        && i + 3 < pattern.length()
                        && pattern.charAt(i + 2) == '$'
                        && pattern.charAt(i + 3) == 's') {
                    argument = d - '1';
                    skip = 3;
                }
                if (argument < 0) {
                    text.append(c);
                    continue;
                }
                if (argument < args.length)
                    text.append(
                            args[argument] instanceof Component arg ? resolve(arg) : String.valueOf(args[argument]));
                i += skip;
            }
            out.append(text);
        } else if (component.getContents() instanceof PlainTextContents plain) out.append(plain.text());
        for (Component sibling : component.getSiblings()) out.append(resolve(sibling));
        return out.toString();
    }

    private static FontDescription font(String path) {
        return new FontDescription.Resource(CreateIds.id(path));
    }

    private static FontDescription rowFont(int top) {
        return font("schedule/y" + (top < 0 ? "n" + -top : Integer.toString(top)));
    }

    private static Set<Integer> textTops() {
        Set<Integer> tops = new TreeSet<>(List.of(TITLE_TOP, EDITOR_TITLE_TOP, SELECTOR_TOP, WIDGET_TOP));
        for (int k = 0; k < LIST_ROWS; k++) tops.add(rowY(k) + 4);
        return Set.copyOf(tops);
    }

    private static Map<Integer, Set<Piece>> placements() {
        Map<Integer, Set<Piece>> map = new TreeMap<>();
        BiConsumer<Integer, Piece[]> add = (top, pieces) -> {
            Set<Piece> set = map.computeIfAbsent(top, t -> EnumSet.noneOf(Piece.class));
            set.addAll(List.of(pieces));
        };
        add.accept(ART_Y, new Piece[] {Piece.PANEL});
        for (int k = 0; k < LIST_ROWS; k++) {
            int y = rowY(k);
            add.accept(y - 4, new Piece[] {
                Piece.HEAD_LIGHT,
                Piece.HEAD_MEDIUM,
                Piece.BODY,
                Piece.BODY_LIGHT,
                Piece.BOTTOM,
                Piece.BOTTOM_LIGHT,
                Piece.LINE
            });
            add.accept(y + 1, new Piece[] {Piece.STRIP_TRAVEL, Piece.STRIP_ACTION});
            add.accept(y - 1, new Piece[] {Piece.STRIP_WAIT});
            add.accept(y - 3, new Piece[] {Piece.STRIP_DOTTED});
            add.accept(y - 2, new Piece[] {Piece.REMOVE});
            add.accept(y + 4, new Piece[] {Piece.SCROLL_LEFT, Piece.SCROLL_RIGHT});
            add.accept(y, new Piece[] {
                Piece.STRIP_END,
                Piece.CARD_NEW,
                Piece.POINTER,
                Piece.DUPLICATE,
                Piece.MOVE_UP,
                Piece.MOVE_DOWN,
                Piece.COND_LEFT,
                Piece.COND_LEFT_CLEAN,
                Piece.COND_ITEM,
                Piece.COND_RIGHT,
                Piece.APPEND,
                Piece.COND_NEW
            });
            add.accept(y, MIDDLE);
        }
        add.accept(FILLER_Y, new Piece[] {Piece.FILL, Piece.FILL_LIGHT, Piece.LINE_FILL});
        add.accept(17, new Piece[] {Piece.POINTER_OFF});
        add.accept(169, new Piece[] {Piece.POINTER_OFF, Piece.ITEM_3X});
        add.accept(15, new Piece[] {Piece.GRAD_TOP});
        add.accept(178, new Piece[] {Piece.GRAD_BOTTOM});
        add.accept(BUTTON_Y, new Piece[] {
            Piece.BTN_LOOP,
            Piece.BTN_LOOP_ON,
            Piece.BTN_RESET,
            Piece.BTN_RESET_OFF,
            Piece.BTN_SKIP,
            Piece.BTN_SKIP_OFF,
            Piece.BTN_UP,
            Piece.BTN_UP_OFF,
            Piece.BTN_DOWN,
            Piece.BTN_DOWN_OFF,
            Piece.BTN_CONFIRM
        });
        add.accept(DIM_Y, new Piece[] {Piece.DIM});
        add.accept(EDITOR_Y, new Piece[] {Piece.EDITOR});
        add.accept(INVENTORY_Y, new Piece[] {Piece.INVENTORY});
        add.accept(LINE_Y, new Piece[] {
            Piece.SLOT_ADDITIONAL, Piece.SLOT_INACTIVE, Piece.DA_START, Piece.DA_SPEECH, Piece.DA_END, Piece.BTN_CONFIRM
        });
        add.accept(LINE_Y, DATA_AREA);
        add.accept(TRASH_Y, new Piece[] {Piece.BTN_TRASH});
        Map<Integer, Set<Piece>> copy = new TreeMap<>();
        map.forEach((top, set) -> copy.put(top, Set.copyOf(set)));
        return Map.copyOf(copy);
    }
}
