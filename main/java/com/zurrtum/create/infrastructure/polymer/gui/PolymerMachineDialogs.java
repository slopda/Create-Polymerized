package com.zurrtum.create.infrastructure.polymer.gui;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.kinetics.speedController.SpeedControllerBlockEntity;
import com.zurrtum.create.content.logistics.tableCloth.ServerTableClothFilteringBehaviour;
import com.zurrtum.create.content.logistics.tableCloth.TableClothBlockEntity;
import com.zurrtum.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.zurrtum.create.foundation.blockEntity.SmartBlockEntity;
import com.zurrtum.create.foundation.blockEntity.behaviour.ValueSettings;
import com.zurrtum.create.foundation.blockEntity.behaviour.ValueSettingsHandleBehaviour;
import com.zurrtum.create.foundation.blockEntity.behaviour.filtering.ServerFilteringBehaviour;
import com.zurrtum.create.foundation.blockEntity.behaviour.scrollValue.ServerKineticScrollValueBehaviour;
import com.zurrtum.create.foundation.blockEntity.behaviour.scrollValue.ServerScrollValueBehaviour;
import com.zurrtum.create.infrastructure.polymer.equipment.PolymerBacktankWear;
import com.zurrtum.create.infrastructure.polymer.equipment.PolymerLinkedControllerInput;
import com.zurrtum.create.infrastructure.polymer.equipment.PolymerSymmetryWand;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerGogglesOverlay;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerShopOverlay;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import com.zurrtum.create.infrastructure.polymer.ponder.PolymerPonderDialog;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public final class PolymerMachineDialogs {
    private static final Identifier VALUE_ACTION_ID =
            Identifier.fromNamespaceAndPath(Create.MOD_ID, "polymer_machine_value_settings");
    private static final long SESSION_LIFETIME_NANOS = TimeUnit.MINUTES.toNanos(2);
    private static final Map<UUID, ValueSession> VALUE_SESSIONS = PolymerDialogSessions.states();
    private static final Map<UUID, KineticDraft> KINETIC_DRAFTS = PolymerDialogSessions.states();

    private PolymerMachineDialogs() {}

    public static void register() {
        PolymerGogglesOverlay.register();
        PolymerShopOverlay.register();
        PolymerBacktankWear.register();
        PolymerLinkedControllerInput.register();
        PolymerDialogSessions.bindStates(VALUE_SESSIONS, KINETIC_DRAFTS);
    }

    public static void clearSession(UUID player) {
        VALUE_SESSIONS.remove(player);
        KINETIC_DRAFTS.remove(player);
    }

    public static void clearAllSessions() {
        VALUE_SESSIONS.clear();
        KINETIC_DRAFTS.clear();
    }

    public static boolean supportsValueSettings(SmartBlockEntity blockEntity) {
        return getValueSettingsBehaviour(blockEntity) != null;
    }

    public static int openValueSettings(
            ServerPlayer player, ServerLevel level, BlockPos pos, SmartBlockEntity blockEntity) {
        ValueSettingsHandleBehaviour behaviour = getValueSettingsBehaviour(blockEntity);
        if (behaviour == null
                || player.isSpectator()
                || !player.mayBuild()
                || !PolymerDialogService.canReach(player, level, pos)
                || !behaviour.acceptsValueSettings()
                || !behaviour.mayInteract(player)) {
            player.sendOverlayMessage(Component.translatable("create.polymer.settings.error.no_target"));
            return 0;
        }

        if (behaviour instanceof ServerScrollValueBehaviour scrollBehaviour
                && PolymerSettingsBoard.handles(scrollBehaviour)) {
            PolymerSettingsBoard.open(player, level, pos, blockEntity, scrollBehaviour);
            return 1;
        }
        if (behaviour instanceof ServerTableClothFilteringBehaviour) {
            PolymerSettingsBoard.open(player, level, pos, blockEntity, behaviour);
            return 1;
        }
        if (!(behaviour instanceof ServerKineticScrollValueBehaviour kinetic)) return 0;

        long now = System.nanoTime();
        VALUE_SESSIONS.values().removeIf(session -> session.expiresAtNanos() <= now);
        KINETIC_DRAFTS.keySet().retainAll(VALUE_SESSIONS.keySet());
        ValueSettings initial = kinetic.getValueSettings();
        ValueSession session = new ValueSession(
                level.dimension(),
                pos.immutable(),
                blockEntity,
                kinetic,
                kinetic.netId(),
                initial,
                kinetic.getValue(),
                UUID.randomUUID().toString(),
                now + SESSION_LIFETIME_NANOS);
        KineticDraft draft = new KineticDraft(
                Math.clamp(initial.row(), 0, 1), Math.clamp(initial.value(), 1, kineticMax(kinetic)), 1);
        VALUE_SESSIONS.put(player.getUUID(), session);
        KINETIC_DRAFTS.put(player.getUUID(), draft);
        PolymerDialogForms.open(player, kineticForm(session, kinetic, draft));
        return 1;
    }

    private static Holder<Dialog> kineticForm(
            ValueSession session, ServerKineticScrollValueBehaviour kinetic, KineticDraft draft) {
        String title =
                session.blockEntity() instanceof SpeedControllerBlockEntity ? "Targeted Speed" : "Generated Speed";
        return PolymerValueBoard.kineticForm(
                title + " in RPM",
                session.token(),
                draft.revision(),
                draft.row(),
                draft.value(),
                kineticMax(kinetic),
                VALUE_ACTION_ID);
    }

    public static int openDisplayLinkSettings(
            ServerPlayer player, ServerLevel level, BlockPos pos, DisplayLinkBlockEntity blockEntity) {
        if (player.isSpectator()
                || !player.mayBuild()
                || !PolymerDialogService.canReach(player, level, pos)
                || level.getBlockEntity(pos) != blockEntity
                || !level.getBlockState(pos).is(AllBlocks.DISPLAY_LINK)) {
            player.sendOverlayMessage(Component.translatable("create.polymer.display_link.error.no_target"));
            return 0;
        }
        if (!level.isLoaded(blockEntity.getSourcePosition()) || !level.isLoaded(blockEntity.getTargetPosition())) {
            player.sendOverlayMessage(Component.translatable("create.polymer.display_link.error.unloaded"));
            return 0;
        }
        PolymerDisplayLinkGui.open(player, blockEntity);
        return 1;
    }

    public static boolean handleCustomAction(
            ServerGamePacketListenerImpl listener, ServerboundCustomClickActionPacket packet) {
        if (!PolymerDialogForms.validSubmission(listener.player, packet.id(), packet.payload(), VALUE_ACTION_ID))
            return true;
        if (VALUE_ACTION_ID.equals(packet.id())) {
            UUID player = listener.player.getUUID();
            boolean board = KINETIC_DRAFTS.containsKey(player);
            applyValueSettings(listener.player, packet.payload());
            if (board && !VALUE_SESSIONS.containsKey(player)) {
                KINETIC_DRAFTS.remove(player);
                NativePresentationPolicy.sendPresentation(listener.player, ClientboundClearDialogPacket.INSTANCE);
            }
            return true;
        }
        return PolymerWorldshaperScreen.handle(listener.player, packet.id(), packet.payload())
                || PolymerPonderDialog.handle(listener.player, packet.id(), packet.payload())
                || PolymerSymmetryWand.handle(listener.player, packet.id(), packet.payload())
                || PolymerSettingsBoard.handle(listener.player, packet.id(), packet.payload())
                || PolymerClipboardScreen.handle(listener.player, packet.id(), packet.payload())
                || PolymerDisplayLinkGui.handleText(listener.player, packet.id(), packet.payload())
                || PolymerFactoryPanelGui.handleAddress(listener.player, packet.id(), packet.payload());
    }

    private static void applyValueSettings(ServerPlayer player, Optional<Tag> optionalPayload) {
        ValueSession session = VALUE_SESSIONS.remove(player.getUUID());
        if (session == null || session.expiresAtNanos() <= System.nanoTime()) {
            player.sendOverlayMessage(Component.translatable("create.polymer.machine.settings.error.expired"));
            return;
        }

        ServerLevel level = player.level();
        if (player.isSpectator()
                || !player.mayBuild()
                || !level.dimension().equals(session.dimension())
                || !PolymerDialogService.canReach(player, level, session.pos())) {
            player.sendOverlayMessage(Component.translatable("create.polymer.machine.settings.error.stale"));
            return;
        }

        ServerKineticScrollValueBehaviour kinetic = session.behaviour();
        if (level.getBlockEntity(session.pos()) != session.blockEntity()
                || getValueSettingsBehaviour(session.blockEntity()) != kinetic
                || kinetic.netId() != session.behaviourIndex()
                || !kinetic.acceptsValueSettings()
                || !kinetic.mayInteract(player)
                || !kinetic.getValueSettings().equals(session.initialSettings())
                || kinetic.getValue() != session.initialRawValue()) {
            player.sendOverlayMessage(Component.translatable("create.polymer.machine.settings.error.stale"));
            return;
        }

        Optional<CompoundTag> payload = PolymerDialogForms.payload(optionalPayload);
        KineticDraft draft = KINETIC_DRAFTS.get(player.getUUID());
        if (draft == null) return;
        if (payload.isEmpty() || !PolymerDialogControls.matches(payload.get(), session.token(), draft.revision())) {
            VALUE_SESSIONS.put(player.getUUID(), session);
            return;
        }
        String operation = payload.get().getString("operation").orElse("");
        if (operation.equals("step") || operation.equals("flip") || operation.equals("set")) {
            int delta = payload.get().getInt("delta").orElse(0);
            int max = kineticMax(kinetic);
            KineticDraft next = switch (operation) {
                case "flip" -> new KineticDraft(1 - draft.row(), draft.value(), draft.revision() + 1);
                case "set" ->
                    new KineticDraft(
                            Math.clamp(payload.get().getInt("row").orElse(draft.row()), 0, 1),
                            Math.clamp(payload.get().getInt("value").orElse(draft.value()), 1, max),
                            draft.revision() + 1);
                default ->
                    new KineticDraft(
                            draft.row(), Math.clamp((long) draft.value() + delta, 1, max), draft.revision() + 1);
            };
            KINETIC_DRAFTS.put(player.getUUID(), next);
            ValueSession renewed = session.renewed(System.nanoTime() + SESSION_LIFETIME_NANOS);
            VALUE_SESSIONS.put(player.getUUID(), renewed);
            PolymerDialogForms.open(player, kineticForm(renewed, kinetic, next));
            return;
        }
        if (!operation.equals("save")) return;

        ValueSettings selected = new ValueSettings(draft.row(), draft.value());
        if (selected.equals(session.initialSettings())) return;
        int signed = selected.row() == 0 ? -selected.value() : selected.value();
        if (signed < kinetic.getMin() || signed > kinetic.getMax()) {
            player.sendOverlayMessage(Component.translatable("create.polymer.machine.settings.error.invalid_value"));
            return;
        }
        kinetic.setValueSettings(player, selected, false);
    }

    private static @Nullable ValueSettingsHandleBehaviour getValueSettingsBehaviour(SmartBlockEntity blockEntity) {
        ServerScrollValueBehaviour scroll = blockEntity.getBehaviour(ServerScrollValueBehaviour.TYPE);
        if (scroll != null) {
            return scroll;
        }
        ServerFilteringBehaviour filter = blockEntity.getBehaviour(ServerFilteringBehaviour.TYPE);
        if (blockEntity instanceof TableClothBlockEntity tableCloth
                && tableCloth.isShop()
                && filter instanceof ServerTableClothFilteringBehaviour) {
            return filter;
        }
        return null;
    }

    private record ValueSession(
            ResourceKey<Level> dimension,
            BlockPos pos,
            SmartBlockEntity blockEntity,
            ServerKineticScrollValueBehaviour behaviour,
            int behaviourIndex,
            ValueSettings initialSettings,
            int initialRawValue,
            String token,
            long expiresAtNanos) {
        ValueSession renewed(long expiresAt) {
            return new ValueSession(
                    dimension,
                    pos,
                    blockEntity,
                    behaviour,
                    behaviourIndex,
                    initialSettings,
                    initialRawValue,
                    token,
                    expiresAt);
        }
    }

    private record KineticDraft(int row, int value, int revision) {}

    private static int kineticMax(ServerScrollValueBehaviour scroll) {
        int max = Math.max(Math.abs(scroll.getMin()), Math.abs(scroll.getMax()));
        return Math.clamp(max, 1, PolymerValueBoard.MAX_VALUE);
    }
}
