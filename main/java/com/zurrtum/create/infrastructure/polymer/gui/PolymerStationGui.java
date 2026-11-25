package com.zurrtum.create.infrastructure.polymer.gui;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.decoration.slidingDoor.DoorControl;
import com.zurrtum.create.content.trains.entity.Train;
import com.zurrtum.create.content.trains.station.GlobalStation;
import com.zurrtum.create.content.trains.station.StationBlockEntity;
import com.zurrtum.create.infrastructure.polymer.PolymerInventorySync;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.SguiUtils;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class PolymerStationGui extends SimpleGui {
    private static final Identifier NAME_ACTION = CreateIds.id("polymer_station_name");
    final StationBlockEntity station;
    private final ServerLevel level;
    private final GlobalStation identity;
    private final boolean assemblyView;
    private final Map<Integer, Control> controls = new HashMap<>();
    private Component lastTitle;
    private boolean lastAutoSchedule;
    private final PolymerGuiLifecycle lifecycle = new PolymerGuiLifecycle(5, false);
    private String editToken;
    private boolean editTrain;
    private long editExpires;

    private record Control(String operation, Component label, boolean enabled) {}

    private PolymerStationGui(ServerPlayer player, StationBlockEntity station) {
        super(station.isAssembling() ? MenuType.GENERIC_9x3 : MenuType.GENERIC_9x1, player, true);
        this.station = station;
        this.level = player.level();
        identity = station.getStation();
        assemblyView = station.isAssembling();
        PolymerGuiControls.configure(this);
        refresh();
    }

    static void open(ServerPlayer player, StationBlockEntity station) {
        if (SguiUtils.getCurrentGui(player) instanceof PolymerStationGui gui
                && gui.station == station
                && gui.assemblyView == station.isAssembling()) {
            gui.refresh();
        } else {
            new PolymerStationGui(player, station).open();
        }
    }

    @Override
    public boolean stillValid() {
        return PolymerGuiControls.editableTarget(player, station, level, AllBlocks.TRACK_STATION)
                && station.getStation() == identity;
    }

    static Train displayedTrain(StationBlockEntity be) {
        GlobalStation global = be.getStation();
        Train present = global == null ? null : global.getPresentTrain();
        return present != null
                ? present
                : be.imminentTrain == null
                        ? null
                        : Create.RAILWAYS.sided(be.getLevel()).trains.get(be.imminentTrain);
    }

    static boolean canCreate(StationBlockEntity be) {
        return be.edgePoint.isOrthogonal() && !be.edgePoint.isOnCurve();
    }

    static boolean canDisassemble(StationBlockEntity be, Train train) {
        GlobalStation global = be.getStation();
        return train != null
                && global != null
                && global.getPresentTrain() == train
                && train.canDisassemble()
                && canCreate(be);
    }

    void refresh() {
        Train train = displayedTrain(station);
        boolean assembly = station.isAssembling();
        boolean primary = assembly
                ? station.bogeyCount > 0 || train != null
                : train == null ? canCreate(station) : canDisassemble(station, train);
        boolean scheduled = train != null && train.runtime.getSchedule() != null;
        Component title = PolymerStationGuiArt.title(station, train, primary, scheduled);
        if (title.equals(lastTitle) && lastAutoSchedule == station.trainHasAutoSchedule) return;
        lastTitle = title;
        lastAutoSchedule = station.trainHasAutoSchedule;
        controls.clear();
        int y = assembly ? -10 : 0;
        if (!assembly) {
            bind(0, y, 200, 14, "name", "Rename Station", true);
            if (train != null) bind(25, y + 44, 143, 14, "train_name", "Rename Train", true);
            bind(
                    train == null ? 84 : 94,
                    y + 65,
                    26,
                    18,
                    train == null ? "enter" : "disassemble",
                    train == null ? "Create new Train" : "Disassemble Train",
                    primary);
            if (train != null)
                bind(
                        73,
                        y + 65,
                        18,
                        18,
                        "drop_schedule",
                        station.trainHasAutoSchedule ? "Discard Auto-Schedule" : "Retrieve Schedule",
                        scheduled);
            bind(
                    14,
                    y + 102,
                    78,
                    18,
                    "door",
                    "Door Control: " + station.doorControls.mode.getSerializedName() + " (right-click for previous)",
                    true);
            bind(167, y + 103, 18, 18, "close", "Close Window", true);
        } else {
            bind(73, y + 154, 18, 18, "exit", "Cancel Assembly", true);
            bind(94, y + 154, 26, 18, "assemble", "Assemble Train", primary);
            bind(167, y + 154, 18, 18, "close", "Close Window", true);
        }
        for (int slot = 0; slot < getSize(); slot++) {
            Control control = controls.get(slot);
            ItemStack empty = new ItemStack(Items.PAPER);
            empty.set(DataComponents.ITEM_MODEL, CreateIds.id("ui/station_empty"));
            if (slot == 4) empty = PolymerStationGuiForeground.item(title, assembly);
            setSlot(
                    slot,
                    PolymerGuiControls.decoration(
                            empty,
                            control == null
                                    ? null
                                    : List.of(control.label()
                                            .copy()
                                            .withColor(control.enabled() ? 0xFFFFFF : 0x808080))));
        }
    }

    private void bind(int x, int y, int w, int h, String operation, String label, boolean enabled) {
        Control control = new Control(operation, Component.literal(label), enabled);
        int nearest = -1;
        double distance = Double.MAX_VALUE;
        for (int slot = 0; slot < getSize(); slot++) {
            int sx = slotX(slot) + 12 + 8, sy = slotY(slot) + 8;
            double d = Math.pow(sx - (x + w / 2.0), 2) + Math.pow(sy - (y + h / 2.0), 2);
            if (d < distance) {
                nearest = slot;
                distance = d;
            }
            if (sx >= x && sx < x + w && sy >= y && sy < y + h) controls.put(slot, control);
        }
        if (controls.values().stream().noneMatch(c -> c == control)) controls.put(nearest, control);
    }

    static int slotX(int slot) {
        return 8 + slot % 9 * 18;
    }

    int slotY(int slot) {
        int rows = assemblyView ? 3 : 1, top = rows * 9;
        return slot < top
                ? 18 + slot / 9 * 18
                : slot < top + 27 ? 31 + rows * 18 + (slot - top) / 9 * 18 : 89 + rows * 18;
    }

    @Override
    public boolean onAnyClick(int slot, ClickType click, ContainerInput action) {
        lifecycle.requestSync();
        if (!stillValid()) {
            close();
            return false;
        }
        if (action != ContainerInput.PICKUP || !click.isLeft && !click.isRight) return false;
        if (editToken != null) return false;
        if (assemblyView != station.isAssembling()) {
            PolymerStationForm.openStationSettings(player, station.getBlockPos());
            return false;
        }
        PolymerStationForm.openStationSettings(player, station.getBlockPos());
        Control control = controls.get(slot);
        if (control == null || !control.enabled()) return false;
        switch (control.operation()) {
            case "close" -> close();
            case "name", "train_name" -> editName(control.operation().equals("train_name"));
            case "door" -> {
                DoorControl[] modes = DoorControl.values();
                DoorControl next = modes[
                        Math.floorMod(station.doorControls.mode.ordinal() + (click.isRight ? -1 : 1), modes.length)];
                submit("door_control", next.getSerializedName());
            }
            default -> submit("assembly_action", control.operation());
        }
        return false;
    }

    private void submit(String key, String value) {
        CompoundTag payload = new CompoundTag();
        payload.putString(key, value);
        PolymerStationForm.submitStationGui(player, station.getBlockPos(), payload);
    }

    private void editName(boolean trainName) {
        Train train = displayedTrain(station);
        if (trainName && (identity.getPresentTrain() != train || train == null)) return;
        editTrain = trainName;
        editToken = UUID.randomUUID().toString();
        editExpires = System.nanoTime() + TimeUnit.MINUTES.toNanos(2);
        String initial = trainName ? train.name.getString() : identity.name;
        PolymerDialogForms.open(
                player,
                PolymerDialogControls.form(
                        trainName ? "Rename Train" : "Rename Station",
                        Component.literal(initial),
                        List.of(PolymerDialogControls.text("value", "Name", initial, trainName ? 35 : 25)),
                        List.of(PolymerDialogControls.button("Save", NAME_ACTION, editToken, 0, "save", 0)),
                        PolymerDialogControls.button("Cancel", NAME_ACTION, editToken, 0, "cancel", 0)));
    }

    static boolean handleCustomAction(
            ServerGamePacketListenerImpl listener, ServerboundCustomClickActionPacket packet) {
        if (!PolymerDialogForms.validSubmission(listener.player, packet.id(), packet.payload(), NAME_ACTION))
            return true;
        if (!packet.id().equals(NAME_ACTION)) return false;
        if (!listener.server.isSameThread()) {
            listener.server.execute(() -> handleCustomAction(listener, packet));
            return true;
        }
        if (!(SguiUtils.getCurrentGui(listener.player) instanceof PolymerStationGui gui)) return true;
        CompoundTag payload = PolymerDialogForms.payload(packet.payload()).orElse(null);
        if (payload == null || gui.editToken == null || !PolymerDialogControls.matches(payload, gui.editToken, 0))
            return true;
        gui.editToken = null;
        NativePresentationPolicy.sendPresentation(listener.player, ClientboundClearDialogPacket.INSTANCE);
        if (!gui.stillValid()) {
            gui.close();
            return true;
        }
        if (gui.editExpires <= System.nanoTime()) {
            PolymerStationForm.openStationSettings(listener.player, gui.station.getBlockPos());
            return true;
        }
        if (payload.getString("operation").filter("save"::equals).isPresent()) {
            gui.submit(
                    gui.editTrain ? "train_name" : "name",
                    payload.getString("value").orElse(""));
        } else {
            PolymerStationForm.openStationSettings(listener.player, gui.station.getBlockPos());
        }
        return true;
    }

    @Override
    public void onTick() {
        if (!stillValid()) {
            close();
            return;
        }
        if (assemblyView != station.isAssembling()) {
            PolymerStationForm.openStationSettings(player, station.getBlockPos());
            return;
        }
        if (editToken != null && editExpires <= System.nanoTime()) editToken = null;
        if (wrappedMenu != null)
            lifecycle.tick(
                    () -> {
                        if (editToken == null) refresh();
                    },
                    wrappedMenu::sendAllDataToRemote);
    }

    @Override
    public void onRemoved() {
        lifecycle.close(() -> {}, () -> {
            editToken = null;
            controls.clear();
            PolymerInventorySync.afterMenu(player);
        });
    }
}
