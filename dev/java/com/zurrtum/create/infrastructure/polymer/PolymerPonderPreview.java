package com.zurrtum.create.infrastructure.polymer;

import com.zurrtum.create.infrastructure.polymer.gui.PolymerArtKit;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerGuiControls;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerGuiLifecycle;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import com.zurrtum.create.infrastructure.polymer.ponder.PolymerPonderDialog;
import com.zurrtum.create.infrastructure.polymer.ponder.PolymerPonderScenes;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.containerwrappers.SlotBasedWrapperMenu;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;

public final class PolymerPonderPreview implements ModInitializer {
    @Override
    public void onInitialize() {
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(PolymerPonderCogwheel::addAssets);
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(Commands.literal("createfly")
                        .then(Commands.literal("ponder")
                                .then(Commands.literal("cogwheel-preview").executes(context -> {
                                    ServerPlayer owner = context.getSource().getPlayerOrException();
                                    if (PolymerNativeClients.isNative(owner)
                                            || !owner.isAlive()
                                            || owner.isSpectator()
                                            || !owner.containerMenu.getCarried().isEmpty()) return 0;
                                    return new Screen(owner).open() ? 1 : 0;
                                }))
                                .then(Commands.literal("cogwheel-dialog").executes(context -> {
                                    ServerPlayer owner = context.getSource().getPlayerOrException();
                                    if (PolymerNativeClients.isNative(owner) || !owner.isAlive() || owner.isSpectator())
                                        return 0;
                                    PolymerPonderDialog.open(owner, PolymerPonderScenes.COGWHEEL);
                                    return 1;
                                })))));
    }

    static final class Playback {
        static final int LENGTH = 240, CHAPTER = 80;
        int tick, view;
        boolean playing = true;

        void advance() {
            if (playing && ++tick >= LENGTH - 1) {
                tick = LENGTH - 1;
                playing = false;
            }
        }

        void seek(int value) {
            tick = Math.clamp(value, 0, LENGTH - 1);
            playing = false;
        }

        int chapter() {
            return tick / CHAPTER;
        }

        int frame() {
            return (tick / 2) % PolymerPonderCogwheel.FRAMES;
        }
    }

    static final class Screen extends SimpleGui {
        static final int SIZE = 54;
        final Playback playback = new Playback();
        final PolymerGuiLifecycle lifecycle = new PolymerGuiLifecycle();
        private final ServerLevel level;
        private int shownFrame = -1, shownChapter = -1, shownView = -1;
        private boolean shownPlaying;

        Screen(ServerPlayer player) {
            super(MenuType.GENERIC_9x6, player, false);
            level = player.level();
            PolymerGuiControls.configure(this, Component.literal(" "), true);
            for (int slot = 0; slot < SIZE; slot++)
                setSlot(slot, PolymerGuiControls.decoration(PolymerArtKit.stack("ponder_cogwheel_empty", null), null));
            refresh();
        }

        @Override
        public boolean stillValid() {
            return player.isAlive()
                    && !player.isRemoved()
                    && !player.isSpectator()
                    && player.level() == level
                    && !PolymerNativeClients.isNative(player);
        }

        private void refresh() {
            if (shownFrame == playback.frame()
                    && shownChapter == playback.chapter()
                    && shownView == playback.view
                    && shownPlaying == playback.playing) return;
            shownFrame = playback.frame();
            shownChapter = playback.chapter();
            shownView = playback.view;
            shownPlaying = playback.playing;
            setSlot(0, PolymerGuiControls.decoration(PolymerPonderCogwheel.stack(playback), null));
            String[] names = {
                "Previous chapter",
                "Back half a second",
                playback.playing ? "Pause" : "Play",
                "Replay",
                "Turn view",
                "Forward half a second",
                "Next chapter",
                "",
                "Done"
            };
            for (int index = 0; index < names.length; index++) {
                if (names[index].isEmpty()) continue;
                setSlot(
                        45 + index,
                        PolymerGuiControls.builder(PolymerArtKit.stack("ponder_cogwheel_empty", null))
                                .setName(Component.literal(names[index]))
                                .build());
            }
        }

        @Override
        public boolean onAnyClick(int slot, ClickType click, ContainerInput input) {
            lifecycle.requestSync();
            if (!stillValid()) {
                close();
                return false;
            }
            if (!PolymerGuiControls.primaryClick(click, input) || input != ContainerInput.PICKUP) return false;
            switch (slot) {
                case 45 -> playback.seek((playback.chapter() - 1) * Playback.CHAPTER);
                case 46 -> playback.seek(playback.tick - 10);
                case 47 -> {
                    if (playback.tick == Playback.LENGTH - 1) playback.tick = 0;
                    playback.playing = !playback.playing;
                }
                case 48 -> {
                    playback.tick = 0;
                    playback.playing = true;
                }
                case 49 -> playback.view = (playback.view + (click.isRight ? 3 : 1)) % 4;
                case 50 -> playback.seek(playback.tick + 10);
                case 51 -> playback.seek((playback.chapter() + 1) * Playback.CHAPTER);
                case 53 -> close();
                default -> {
                    return false;
                }
            }
            if (!lifecycle.isClosed()) refresh();
            return false;
        }

        @Override
        public void onTick() {
            if (!stillValid()) {
                close();
                return;
            }
            if (lifecycle.isClosed()) return;
            playback.advance();
            refresh();
            if (player.containerMenu instanceof SlotBasedWrapperMenu menu && menu.getBackingGui() == this) {
                lifecycle.tick(() -> {}, menu::sendAllDataToRemote);
            }
        }

        private void cleanup() {
            lifecycle.close(() -> {}, () -> PolymerInventorySync.afterMenu(player));
        }

        @Override
        public void onManualClose() {
            cleanup();
        }

        @Override
        public void onPlayerClose(boolean close) {
            cleanup();
        }

        @Override
        public void onRemoved() {
            cleanup();
            super.onRemoved();
        }
    }
}
