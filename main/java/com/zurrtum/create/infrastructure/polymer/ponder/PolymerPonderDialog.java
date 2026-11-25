package com.zurrtum.create.infrastructure.polymer.ponder;

import com.zurrtum.create.infrastructure.polymer.gui.PolymerArtKit;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerDialogForms;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerDialogSessions;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.CustomModelData;

public final class PolymerPonderDialog {
    static final Identifier ACTION = CreateIds.id("polymer_ponder_dialog");
    static final int BAR_LEVELS = 20;
    static final int SCENE_W = PolymerPonderLayout.SCENE_WIDTH, SCENE_H = PolymerPonderLayout.SCENE_HEIGHT;
    static final float SCALE = 1.8f;

    static final String IDENTITY_GUI = "{\"gui\":{\"rotation\":[0,0,0],\"translation\":[0,0,0],\"scale\":[1,1,1]}}";

    static int bar(PolymerPonderScene scene, int tick) {
        return Math.min(BAR_LEVELS, tick * BAR_LEVELS / scene.length);
    }

    static boolean enabled(Session s, String operation) {
        return switch (operation) {
            case "previous" -> s.index > 0;
            case "next" -> s.index < s.playlist.size() - 1;
            case "close", "replay" -> true;
            case "back" -> !s.history.isEmpty();
            default -> operation.startsWith("seek:");
        };
    }

    record Snapshot(
            PolymerPonderScenes.Tag tag,
            List<PolymerPonderScene> playlist,
            String item,
            int index,
            int tick,
            int view,
            int clock,
            boolean playing) {}

    static final class Session {
        final String token;
        List<PolymerPonderScene> playlist;
        String item;

        PolymerPonderScene scene;
        int index, tick, view, revision, clock;
        PolymerPonderScene switchFrom;

        int switchView, switchClock;
        boolean playing = true;
        String shown = "";
        PolymerPonderScenes.Tag tag;
        final ArrayDeque<Snapshot> history = new ArrayDeque<>();

        Snapshot snapshot() {
            return new Snapshot(tag, playlist, item, index, tick, view, clock, playing);
        }

        void restore(Snapshot snapshot) {
            tag = snapshot.tag;
            playlist = snapshot.playlist;
            item = snapshot.item;
            index = snapshot.index;
            scene = playlist.get(index);
            tick = snapshot.tick;
            view = snapshot.view;
            clock = snapshot.clock;
            playing = snapshot.playing;
            switchFrom = null;
        }

        Session(String token, PolymerPonderScene scene, int tick, int view, int revision) {
            this(token, List.of(scene), 0, tick, view, revision);
        }

        Session(String token, List<PolymerPonderScene> playlist, int index, int tick, int view, int revision) {
            this.token = token;
            this.playlist = playlist;
            this.index = index;
            this.scene = playlist.get(index);
            this.item = playlist.getFirst().item;
            this.tick = tick;
            this.view = view;
            this.revision = revision;
        }

        int switchFrame() {
            return switchFrom == null ? -1 : switchClock / 2;
        }

        int visualTick() {
            return Math.min(scene.renderLength, tick + (tick >= scene.length ? clock : 0));
        }

        int view() {
            return scene.viewAt(visualTick());
        }

        String sceneId() {
            int frame = switchFrame();
            return frame >= 0
                    ? PolymerPonderScene.switchId(switchFrom, scene, frame, switchView)
                    : scene.sceneId(visualTick(), view());
        }

        boolean finished() {
            return !playing && tick >= scene.length;
        }

        PolymerPonderScene next() {
            return index < playlist.size() - 1 ? playlist.get(index + 1) : null;
        }

        String state() {
            if (tag != null) return "tag:" + tag.id() + "/" + revision;
            int frame = scene.frame(visualTick());
            StringBuilder state = new StringBuilder()
                    .append(sceneId())
                    .append('/')
                    .append(frame >= 0 ? "t" + frame : "p" + scene.phase(visualTick()))
                    .append('/')
                    .append(scene.caption(visualTick()))
                    .append(':')
                    .append(scene.captionFade(visualTick()))
                    .append('/')
                    .append(bar(scene, tick))
                    .append('/')
                    .append(flash(this))
                    .append('/')
                    .append(view())
                    .append('/')
                    .append(revision)
                    .append('/')
                    .append(finishFlash(this))
                    .append('/')
                    .append(upNext(this))
                    .append("/i")
                    .append(scene.itemTick(visualTick()));
            for (int i = 1; i <= scene.captions.size(); i++)
                state.append("/c").append(scene.captionFade(i, visualTick()));
            for (var outline : scene.outlines)
                state.append('/').append(PolymerPonderScene.outlineState(outline, visualTick()));
            return state.toString();
        }
    }

    static int flash(Session s) {
        if (!s.finished() || s.next() == null) return 0;
        return Math.round((float) (0.5 + 0.5 * Math.sin(s.clock / 10.0)) * PolymerPonderLayout.FLASH_LEVELS);
    }

    static final int FINISH_FLASH_STEPS = 12;

    static int finishFlash(Session s) {
        if (!s.finished()) return 0;
        int n = s.clock - 29;
        return n < 0 || n / 2 >= FINISH_FLASH_STEPS ? 0 : n / 2 + 1;
    }

    static boolean upNext(Session s) {
        return s.finished() && s.next() != null && s.next().nextUpEnabled && s.clock >= 49;
    }

    private static final Map<UUID, Session> SESSIONS = PolymerDialogSessions.states();

    static {
        ServerTickEvents.END_SERVER_TICK.register(PolymerPonderDialog::tick);
    }

    private PolymerPonderDialog() {}

    private static List<PolymerPonderScene> storyboards(PolymerPonderScene.Definition definition) {
        return storyboards(PolymerPonderScenes.storyboardsOf(definition));
    }

    private static List<PolymerPonderScene> storyboards(List<PolymerPonderScene.Definition> definitions) {
        List<PolymerPonderScene> playlist = new ArrayList<>();
        for (var entry : definitions) {
            PolymerPonderScene scene = entry.scene();
            if (!PolymerPonderLayout.registered(scene)) return null;
            playlist.add(scene);
        }
        return List.copyOf(playlist);
    }

    public static boolean open(ServerPlayer player, PolymerPonderScene.Definition definition) {
        return open(player, definition, definition.builder.item);
    }

    public static boolean open(ServerPlayer player, PolymerPonderScene.Definition definition, String item) {
        var definitions = PolymerPonderScenes.storyboards(item);
        List<PolymerPonderScene> playlist = storyboards(
                definitions.contains(definition) ? definitions : PolymerPonderScenes.storyboardsOf(definition));
        if (playlist == null) return false;
        Session session =
                new Session(UUID.randomUUID().toString(), playlist, playlist.indexOf(definition.scene()), 0, 0, 0);
        if (definitions.contains(definition)) session.item = item;
        SESSIONS.put(player.getUUID(), session);
        show(player, session);
        return true;
    }

    private static void show(ServerPlayer player, Session session) {
        session.shown = session.state();
        PolymerDialogForms.open(player, dialog(session));
    }

    private static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        for (var entry : List.copyOf(SESSIONS.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Session session = entry.getValue();
            if (player == null || player.isRemoved() || !player.isAlive() || PolymerNativeClients.isNative(player)) {
                SESSIONS.remove(entry.getKey(), session);
                continue;
            }
            int length = session.scene.length;
            if (session.tag != null) continue;
            if (session.switchFrom != null && ++session.switchClock >= 2 * PolymerPonderScene.SWITCH_FRAMES)
                session.switchFrom = null;
            if (session.playing && ++session.tick >= length) {
                session.tick = length;
                session.playing = false;
            } else if (!session.playing && session.tick >= length) session.clock++;
            if (!session.state().equals(session.shown)) show(player, session);
        }
    }

    public static boolean handle(ServerPlayer player, Identifier id, Optional<Tag> payload) {
        if (!ACTION.equals(id)) return false;
        Session s = SESSIONS.get(player.getUUID());
        CompoundTag tag = PolymerDialogForms.payload(payload).orElse(null);
        if (s == null
                || tag == null
                || !tag.getString("session").orElse("").equals(s.token)
                || tag.getInt("revision").orElse(-1) != s.revision) return true;
        String operation = tag.getString("operation").orElse("");
        if (operation.equals("close")) {
            SESSIONS.remove(player.getUUID());
            player.connection.send(ClientboundClearDialogPacket.INSTANCE);
            return true;
        }
        if (operation.startsWith("tag:") && s.tag == null) {
            var opened = PolymerPonderScenes.tag(operation.substring(4)).orElse(null);
            if (opened == null || !PolymerPonderScenes.tagsOf(s.item).contains(opened)) return true;
            s.history.addLast(s.snapshot());
            s.tag = opened;
            s.revision++;
            show(player, s);
            return true;
        }
        if (operation.startsWith("item:") && s.tag != null) {
            String item = operation.substring(5);
            var definitions = PolymerPonderScenes.storyboards(item);
            List<PolymerPonderScene> playlist =
                    definitions.isEmpty() || !s.tag.items().contains(item) ? null : storyboards(definitions);
            if (playlist == null) return true;
            s.history.addLast(s.snapshot());
            s.tag = null;
            s.playlist = playlist;
            s.item = item;
            s.index = 0;
            s.scene = playlist.getFirst();
            s.tick = 0;
            s.clock = 0;
            s.view = 0;
            s.playing = true;
            s.switchFrom = null;
            s.revision++;
            show(player, s);
            return true;
        }
        if (operation.equals("back")) {
            if (s.history.isEmpty()) return true;
            s.restore(s.history.removeLast());
            s.revision++;
            show(player, s);
            return true;
        }
        if (s.tag != null) return true;
        if (operation.startsWith("scene:")) {
            var definition = PolymerPonderScenes.byId(operation.substring(6)).orElse(null);
            if (definition == null || definition.id().equals(s.scene.id)) return true;
            List<PolymerPonderScene> playlist = storyboards(definition);
            if (playlist == null) return true;
            s.playlist = playlist;
            s.index = playlist.indexOf(definition.scene());
            s.scene = playlist.get(s.index);
            s.item = playlist.getFirst().item;
            s.tick = 0;
            s.clock = 0;
            s.view = 0;
            s.playing = true;
            s.switchFrom = null;
            s.revision++;
            show(player, s);
            return true;
        }
        PolymerPonderScene scene = s.scene;
        if (!enabled(s, operation)) return true;
        if (operation.startsWith("seek:")) {
            int target;
            try {
                target = Integer.parseInt(operation.substring(5));
            } catch (NumberFormatException e) {
                return true;
            }
            boolean known = false;
            for (int[] zone : scene.seekZones) known |= zone[2] == target;
            if (!known) return true;
            s.tick = target;
        } else
            switch (operation) {
                case "previous", "next" -> {
                    s.switchFrom = s.scene;
                    s.switchView = s.view();
                    s.switchClock = 0;
                    s.index += operation.equals("next") ? 1 : -1;
                    s.scene = s.playlist.get(s.index);
                    s.tick = 0;
                }
                case "replay" -> s.tick = 0;
                default -> {
                    return true;
                }
            }
        if (!operation.equals("previous") && !operation.equals("next")) s.switchFrom = null;
        s.playing = s.tick < s.scene.length;
        s.clock = 0;
        s.revision++;
        show(player, s);
        return true;
    }

    private static Holder<Dialog> dialog(Session s) {
        ItemStack icon =
                BuiltInRegistries.ITEM.getValue(Identifier.parse(s.item)).getDefaultInstance();
        return dialog(s, icon);
    }

    static Holder<Dialog> dialog(Session s, ItemStack icon) {
        if (s.tag != null) return tagDialog(s, item(s.tag.icon()));
        PolymerPonderScene scene = s.scene;
        int finish = finishFlash(s);
        ItemStack sceneStack = PolymerArtKit.stack(s.sceneId(), null);
        if (s.switchFrame() < 0) {
            String key = scene.key(s.visualTick());
            sceneStack.set(
                    DataComponents.CUSTOM_MODEL_DATA,
                    new CustomModelData(
                            List.of(),
                            scene.stackFlags(key),
                            PolymerPonderScene.stackStrings(
                                    finish > 0 ? "f" + finish : "", scene.itemTick(s.visualTick()), key),
                            List.of()));
        }
        List<DialogBody> bodies = new ArrayList<>();
        bodies.add(new ItemBody(
                ItemStackTemplate.fromNonEmptyStack(icon),
                Optional.of(
                        new PlainMessage(PolymerPonderLayout.header(scene.title), PolymerPonderLayout.HEADER_MESSAGE)),
                false,
                true,
                16,
                16));
        for (var tag : PolymerPonderScenes.tagsOf(s.item))
            bodies.add(new ItemBody(
                    ItemStackTemplate.fromNonEmptyStack(item(tag.icon())),
                    Optional.of(new PlainMessage(
                            PolymerPonderLayout.tagButton(tag, operation -> payload(s, operation)),
                            PolymerPonderLayout.HEADER_MESSAGE)),
                    false,
                    false,
                    16,
                    16));
        bodies.addAll(List.of(
                new ItemBody(
                        ItemStackTemplate.fromNonEmptyStack(sceneStack),
                        Optional.of(new PlainMessage(
                                PolymerPonderLayout.callout(
                                        scene, s.visualTick(), s.view(), upNext(s) ? s.next() : null),
                                PolymerPonderLayout.SIDE_MESSAGE)),
                        false,
                        false,
                        SCENE_W,
                        SCENE_H),
                new PlainMessage(
                        PolymerPonderLayout.controls(
                                scene,
                                scene.caption(s.visualTick()),
                                bar(scene, s.tick),
                                flash(s),
                                operation -> enabled(s, operation),
                                operation -> payload(s, operation)),
                        PolymerPonderLayout.WIDTH)));
        CommonDialogData common = new CommonDialogData(
                Component.empty(),
                Optional.of(Component.literal("Ponder: " + scene.title)),
                true,
                false,
                DialogAction.NONE,
                bodies,
                List.of());
        ActionButton done = new ActionButton(
                new CommonButtonData(Component.empty(), Optional.of(Component.literal("Close")), 1),
                Optional.of(new CustomAll(ACTION, Optional.of(payload(s, "close")))));
        return Holder.direct(new NoticeDialog(common, done));
    }

    static ItemStack item(String id) {
        return ITEMS.apply(id);
    }

    static Function<String, ItemStack> ITEMS =
            id -> BuiltInRegistries.ITEM.getValue(Identifier.parse(id)).getDefaultInstance();

    static String tagGridId(PolymerPonderScenes.Tag tag) {
        return "ponder_tag_" + tag.id();
    }

    static Holder<Dialog> tagDialog(Session s, ItemStack icon) {
        var tag = s.tag;
        int lines = PolymerPonderLayout.gridLines(tag);
        List<DialogBody> bodies = new ArrayList<>();
        bodies.add(new ItemBody(
                ItemStackTemplate.fromNonEmptyStack(icon),
                Optional.of(
                        new PlainMessage(PolymerPonderLayout.header(tag.title()), PolymerPonderLayout.HEADER_MESSAGE)),
                false,
                true,
                16,
                16));
        bodies.add(new ItemBody(
                ItemStackTemplate.fromNonEmptyStack(PolymerArtKit.stack(tagGridId(tag), null)),
                Optional.of(new PlainMessage(
                        PolymerPonderLayout.tagGrid(
                                tag,
                                item -> !PolymerPonderScenes.storyboards(item).isEmpty(),
                                operation -> payload(s, operation),
                                item -> item(item).getHoverName()),
                        PolymerPonderLayout.GRID_MESSAGE)),
                false,
                false,
                1,
                lines * 9 + 8));
        bodies.add(new PlainMessage(PolymerPonderLayout.tagDescription(tag), PolymerPonderLayout.WIDTH));
        bodies.add(new PlainMessage(
                PolymerPonderLayout.tagFooter(enabled(s, "back"), operation -> payload(s, operation)),
                PolymerPonderLayout.WIDTH));
        CommonDialogData common = new CommonDialogData(
                Component.empty(),
                Optional.of(Component.literal("Ponder: " + tag.title())),
                true,
                false,
                DialogAction.NONE,
                bodies,
                List.of());
        ActionButton done = new ActionButton(
                new CommonButtonData(Component.empty(), Optional.of(Component.literal("Close")), 1),
                Optional.of(new CustomAll(ACTION, Optional.of(payload(s, "close")))));
        return Holder.direct(new NoticeDialog(common, done));
    }

    private static CompoundTag payload(Session s, String operation) {
        CompoundTag tag = new CompoundTag();
        tag.putString("session", s.token);
        tag.putInt("revision", s.revision);
        tag.putString("operation", operation);
        return tag;
    }

    static void addAssets(ResourcePackBuilder builder) {
        PolymerPonderDialogAssets.addAssets(builder);
    }
}
