package com.zurrtum.create.infrastructure.polymer.gui;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.content.decoration.slidingDoor.DoorControl;
import com.zurrtum.create.content.kinetics.belt.transport.TransportedItemStack;
import com.zurrtum.create.content.trains.entity.Train;
import com.zurrtum.create.content.trains.entity.TrainIconType;
import com.zurrtum.create.content.trains.station.GlobalStation;
import com.zurrtum.create.content.trains.station.StationBlock;
import com.zurrtum.create.content.trains.station.StationBlockEntity;
import com.zurrtum.create.infrastructure.polymer.ponder.PolymerPonderTopics;
import eu.pb4.sgui.api.SguiUtils;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public final class PolymerStationForm {
    private PolymerStationForm() {}

    static void register() {
        PolymerDialogSessions.bindStates(STATION_SESSIONS);
    }

    static final Map<UUID, StationSession> STATION_SESSIONS = PolymerDialogSessions.states();

    public static void openScheduleSettings(ServerPlayer player) {
        if (!PolymerScheduleScreen.open(player))
            player.sendOverlayMessage(Component.translatable("create.polymer.schedule.error.no_item"));
    }

    public static InteractionResult useSchedule(
            StationBlockEntity station, ItemStack stack, Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ItemStack autoSchedule = station.getAutoSchedule();
        if (!autoSchedule.isEmpty()) {
            player.getInventory().placeItemBackInInventory(autoSchedule.copy(), Prediction.SERVER_ONLY);
            station.depotBehaviour.removeHeldItem();
            station.notifyUpdate();
            player.level()
                    .playSound(
                            null,
                            player.blockPosition(),
                            SoundEvents.ITEM_PICKUP,
                            SoundSource.PLAYERS,
                            0.2f,
                            1.0f + player.level().getRandom().nextFloat());
            return InteractionResult.SUCCESS;
        }

        ItemStack offeredSchedule = stack.copyWithCount(1);
        ItemStack remainder = station.depotBehaviour.insert(new TransportedItemStack(offeredSchedule), false);
        if (!remainder.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("create.polymer.station.error.schedule_rejected"));
            return InteractionResult.FAIL;
        }

        if (!player.isCreative()) {
            stack.shrink(1);
            player.setItemInHand(hand, stack.isEmpty() ? ItemStack.EMPTY : stack);
        }
        station.notifyUpdate();
        return InteractionResult.SUCCESS;
    }

    public static boolean openStationSettings(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        UUID playerId = player.getUUID();
        if (player.isSpectator() || !player.mayBuild() || !canEditStation(player, level, pos)) {
            player.sendOverlayMessage(Component.translatable("create.polymer.station.error.no_target"));
            return false;
        }

        if (!level.hasChunkAt(pos)
                || !(level.getBlockEntity(pos) instanceof StationBlockEntity stationBlockEntity)
                || !(level.getBlockState(pos).getBlock() instanceof StationBlock)) {
            player.sendOverlayMessage(Component.translatable("create.polymer.station.error.no_station"));
            return false;
        }

        GlobalStation station = stationBlockEntity.getStation();
        if (station == null || station.getId() == null) {
            player.sendOverlayMessage(Component.translatable("create.polymer.station.error.no_track"));
            return false;
        }

        long now = System.nanoTime();
        Train presentTrain = station.getPresentTrain();
        STATION_SESSIONS.values().removeIf(session -> session.expiresAtNanos() <= now);
        PolymerMachineDialogs.clearSession(playerId);
        PolymerPonderTopics.PONDER_SESSIONS.remove(playerId);
        STATION_SESSIONS.put(
                playerId,
                new StationSession(
                        level.dimension(),
                        pos.immutable(),
                        stationBlockEntity,
                        station,
                        station.getId(),
                        station.name,
                        stationBlockEntity.doorControls.mode,
                        stationBlockEntity.isAssembling(),
                        presentTrain == null ? null : presentTrain.id,
                        presentTrain != null && presentTrain.runtime.getSchedule() != null,
                        presentTrain != null && presentTrain.canDisassemble(),
                        presentTrain == null ? null : presentTrain.name.getString(),
                        presentTrain == null ? null : presentTrain.icon.id(),
                        presentTrain == null ? -1 : presentTrain.mapColorIndex,
                        now + PolymerDialogService.SESSION_LIFETIME_NANOS));

        PolymerStationGui.open(player, stationBlockEntity);
        return true;
    }

    static void submitStationGui(ServerPlayer player, BlockPos pos, CompoundTag payload) {
        applyStationSettings(player, Optional.of(payload));
        openStationSettings(player, pos);
    }

    private static boolean canEditStation(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!player.mayInteract(level, pos) || !level.mayInteract(player, pos)) return false;
        return SguiUtils.getCurrentGui(player) instanceof PolymerStationGui gui
                        && gui.station.getBlockPos().equals(pos)
                        && gui.stillValid()
                || PolymerDialogService.canReach(player, level, pos);
    }

    private static void applyStationSettings(ServerPlayer player, Optional<Tag> optionalPayload) {
        StationSession session = STATION_SESSIONS.remove(player.getUUID());
        if (session == null || session.expiresAtNanos() <= System.nanoTime()) {
            player.sendOverlayMessage(Component.translatable("create.polymer.station.error.expired"));
            return;
        }

        ServerLevel level = player.level();
        if (player.isSpectator()
                || !player.mayBuild()
                || !session.dimension().equals(level.dimension())
                || !canEditStation(player, level, session.pos())
                || !level.hasChunkAt(session.pos())
                || level.getBlockEntity(session.pos()) != session.blockEntity()
                || !(level.getBlockState(session.pos()).getBlock() instanceof StationBlock)) {
            player.sendOverlayMessage(Component.translatable("create.polymer.station.error.stale"));
            return;
        }

        StationBlockEntity blockEntity = session.blockEntity();
        GlobalStation station = blockEntity.getStation();
        Train presentTrain = station == null ? null : station.getPresentTrain();
        UUID presentTrainId = presentTrain == null ? null : presentTrain.id;
        if (station != session.station()
                || station.getId() == null
                || !station.getId().equals(session.stationId())
                || !station.name.equals(session.initialName())
                || blockEntity.doorControls.mode != session.initialDoorControl()
                || blockEntity.isAssembling() != session.initialAssemblyMode()
                || !Objects.equals(presentTrainId, session.initialTrainId())
                || (presentTrain != null && presentTrain.runtime.getSchedule() != null)
                        != session.initialTrainHasSchedule()
                || (presentTrain != null && presentTrain.canDisassemble()) != session.initialTrainCanDisassemble()
                || !Objects.equals(
                        presentTrain == null ? null : presentTrain.name.getString(), session.initialTrainName())
                || !Objects.equals(presentTrain == null ? null : presentTrain.icon.id(), session.initialTrainIcon())
                || (presentTrain == null ? -1 : presentTrain.mapColorIndex) != session.initialTrainMapColor()) {
            player.sendOverlayMessage(Component.translatable("create.polymer.station.error.stale"));
            return;
        }

        Optional<CompoundTag> payload = PolymerDialogForms.payload(optionalPayload);
        String nameInput =
                payload.flatMap(tag -> tag.getString("name")).orElse("").trim();
        String doorInput =
                payload.flatMap(tag -> tag.getString("door_control")).orElse("").trim();
        String assemblyAction = payload.flatMap(tag -> tag.getString("assembly_action"))
                .orElse("")
                .trim()
                .toLowerCase(Locale.ROOT);
        String trainNameInput =
                payload.flatMap(tag -> tag.getString("train_name")).orElse("").trim();
        String trainIconInput = payload.flatMap(tag -> tag.getString("train_icon"))
                .orElse("")
                .trim()
                .toLowerCase(Locale.ROOT);
        String trainColorInput =
                payload.flatMap(tag -> tag.getString("train_color")).orElse("").trim();
        boolean trainEditRequested =
                !trainNameInput.isEmpty() || !trainIconInput.isEmpty() || !trainColorInput.isEmpty();
        if (nameInput.codePointCount(0, nameInput.length()) > 25
                || nameInput.codePoints().anyMatch(Character::isISOControl)
                || trainNameInput.codePointCount(0, trainNameInput.length()) > 35
                || trainNameInput.codePoints().anyMatch(Character::isISOControl)
                || !assemblyAction.isEmpty()
                        && !Set.of("enter", "exit", "assemble", "disassemble", "drop_schedule")
                                .contains(assemblyAction)) {
            player.sendOverlayMessage(Component.translatable("create.polymer.station.error.invalid"));
            return;
        }

        DoorControl doorControl = null;
        if (!doorInput.isEmpty()) {
            try {
                doorControl = DoorControl.valueOf(doorInput.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                player.sendOverlayMessage(Component.translatable("create.polymer.station.error.invalid"));
                return;
            }
        }

        TrainIconType trainIcon = switch (trainIconInput) {
            case "" -> null;
            case "traditional" -> TrainIconType.TRADITIONAL;
            case "electric" -> TrainIconType.ELECTRIC;
            case "modern" -> TrainIconType.MODERN;
            default -> {
                player.sendOverlayMessage(Component.translatable("create.polymer.station.error.invalid_train_icon"));
                yield null;
            }
        };
        if (!trainIconInput.isEmpty() && trainIcon == null) {
            return;
        }

        Integer trainColor = null;
        if (!trainColorInput.isEmpty()) {
            try {
                trainColor = Integer.parseInt(trainColorInput);
            } catch (NumberFormatException exception) {
                player.sendOverlayMessage(Component.translatable("create.polymer.station.error.invalid_train_color"));
                return;
            }
            if (trainColor < 0 || trainColor >= 16) {
                player.sendOverlayMessage(Component.translatable("create.polymer.station.error.invalid_train_color"));
                return;
            }
        }

        if (trainEditRequested && presentTrain == null) {
            player.sendOverlayMessage(Component.translatable("create.polymer.station.error.no_train"));
            return;
        }
        if (trainEditRequested && (assemblyAction.equals("assemble") || assemblyAction.equals("disassemble"))) {
            player.sendOverlayMessage(Component.translatable("create.polymer.station.error.invalid"));
            return;
        }

        if (assemblyAction.equals("enter")) {
            if (presentTrain != null) {
                player.sendOverlayMessage(Component.translatable("create.polymer.station.error.train_present"));
                return;
            }
            if (blockEntity.isAssembling() || !blockEntity.tryEnterAssemblyMode()) {
                player.sendOverlayMessage(Component.translatable("create.polymer.station.error.assembly_unavailable"));
                return;
            }
        } else if (assemblyAction.equals("exit") && !blockEntity.isAssembling()) {
            player.sendOverlayMessage(Component.translatable("create.polymer.station.error.not_assembling"));
            return;
        } else if (assemblyAction.equals("assemble")) {
            if (!blockEntity.isAssembling()) {
                player.sendOverlayMessage(Component.translatable("create.polymer.station.error.not_assembling"));
                return;
            }
            if (presentTrain != null) {
                player.sendOverlayMessage(Component.translatable("create.polymer.station.error.train_present"));
                return;
            }
        } else if (assemblyAction.equals("disassemble")) {
            if (presentTrain == null || !presentTrain.canDisassemble() || !blockEntity.edgePoint.isOrthogonal()) {
                player.sendOverlayMessage(
                        Component.translatable("create.polymer.station.error.no_disassemblable_train"));
                return;
            }
        } else if (assemblyAction.equals("drop_schedule")) {
            if (presentTrain == null || presentTrain.runtime.getSchedule() == null) {
                player.sendOverlayMessage(Component.translatable("create.polymer.station.error.no_train_schedule"));
                return;
            }
        }

        boolean changed = false;
        if (!nameInput.isEmpty() && !nameInput.equals(station.name)) {
            if (!blockEntity.updateName(nameInput)) {
                player.sendOverlayMessage(Component.translatable("create.polymer.station.error.no_track"));
                return;
            }
            changed = true;
        }
        if (doorControl != null && doorControl != blockEntity.doorControls.mode) {
            blockEntity.doorControls.set(doorControl);
            changed = true;
        }
        if (presentTrain != null) {
            if (!trainNameInput.isEmpty() && !trainNameInput.equals(presentTrain.name.getString())) {
                presentTrain.name = Component.literal(trainNameInput);
                changed = true;
            }
            if (trainIcon != null && trainIcon != presentTrain.icon) {
                presentTrain.icon = trainIcon;
                changed = true;
            }
            if (trainColor != null && trainColor != presentTrain.mapColorIndex) {
                presentTrain.mapColorIndex = trainColor;
                changed = true;
            }
        }

        if (!assemblyAction.isEmpty()) {
            switch (assemblyAction) {
                case "enter" -> changed = blockEntity.enterAssemblyMode(player) || changed;
                case "exit" -> changed = blockEntity.exitAssemblyMode() || changed;
                case "assemble" -> {
                    blockEntity.assemble(player.getUUID());
                    GlobalStation updatedStation = blockEntity.getStation();
                    if (updatedStation == null || updatedStation.getPresentTrain() == null) {
                        if (blockEntity.lastException != null) {
                            player.sendOverlayMessage(blockEntity.lastException.component);
                        } else {
                            player.sendOverlayMessage(
                                    Component.translatable("create.polymer.station.error.assembly_failed"));
                        }
                        return;
                    }
                    blockEntity.exitAssemblyMode();
                    changed = true;
                }
                case "disassemble" -> {
                    if (!blockEntity.enterAssemblyMode(player)) {
                        player.sendOverlayMessage(
                                Component.translatable("create.polymer.station.error.no_disassemblable_train"));
                        return;
                    }
                    changed = true;
                }
                case "drop_schedule" -> {
                    blockEntity.dropSchedule(player, presentTrain);
                    changed = true;
                }
            }
        }

        if (!changed) {
            return;
        }
    }

    public static int openHeldSchedule(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!player.getMainHandItem().is(AllItems.SCHEDULE)) {
            return PolymerDialogService.fail(source, "create.polymer.schedule.error.no_item");
        }
        openScheduleSettings(player);
        return 1;
    }

    private record StationSession(
            ResourceKey<Level> dimension,
            BlockPos pos,
            StationBlockEntity blockEntity,
            GlobalStation station,
            UUID stationId,
            String initialName,
            DoorControl initialDoorControl,
            boolean initialAssemblyMode,
            @Nullable UUID initialTrainId,
            boolean initialTrainHasSchedule,
            boolean initialTrainCanDisassemble,
            @Nullable String initialTrainName,
            @Nullable Identifier initialTrainIcon,
            int initialTrainMapColor,
            long expiresAtNanos) {}
}
