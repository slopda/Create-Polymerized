package com.zurrtum.create.infrastructure.polymer.train;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zurrtum.create.AllHandle;
import com.zurrtum.create.Create;
import com.zurrtum.create.catnip.animation.LerpedFloat;
import com.zurrtum.create.catnip.animation.LerpedFloat.Chaser;
import com.zurrtum.create.catnip.math.AngleHelper;
import com.zurrtum.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.zurrtum.create.content.trains.entity.Carriage;
import com.zurrtum.create.content.trains.entity.CarriageContraptionEntity;
import com.zurrtum.create.content.trains.entity.Train;
import com.zurrtum.create.infrastructure.config.AllConfigs;
import com.zurrtum.create.infrastructure.packet.c2s.HonkPacket;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerGuiGlyphs;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerSchematicGuis;
import com.zurrtum.create.infrastructure.polymer.interaction.PolymerElevatorControls;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import dev.veeda.createpoly.assets.CreateIds;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

public final class PolymerTrainHud {
    private static final int REFRESH_TICKS = 20, PROMPT_KEEP_ALIVE = 30, FOREIGN_TICKS = 60;
    private static final int BAR_W = 182, BAR_H = 5;
    private static final int TEXT_COLOR = 0x544D45;
    private static final int FRAME = 0xE000,
            SPEED_BG = 0xE001,
            DIRECTION = 0xE002,
            POINTER = 0xE003,
            PROMPT_L = 0xE004,
            PROMPT_R = 0xE005,
            ARROW = 0xE010,
            STRIP = 0xE100,
            STRIP_STRIDE = 0x200,
            SPACE_BASE = 0xF000;
    private static final int ROW_SPEED = 0, ROW_THROTTLE = 1, ROW_PROMPT = 2;
    private static final FontDescription FONT = new FontDescription.Resource(CreateIds.id("train_hud"));
    private static final FontDescription VANILLA =
            new FontDescription.Resource(Identifier.withDefaultNamespace("default"));
    private static final Pattern FORMAT = Pattern.compile("%(?:(\\d+)\\$)?([s%])");

    private static volatile Map<Integer, Integer> advances = Map.of();

    private static final Map<UUID, Hud> SHOWN = new HashMap<>();
    private static final Map<UUID, Prompt> PENDING_PROMPTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Component> PENDING_FOREIGN = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> ACKS = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT_ACK = new AtomicInteger(0x54724800);

    private record Prompt(Component text, boolean shadow) {}

    private static final class Wheel {
        final int original;
        int reported;
        Integer ack;
        boolean dirty;

        Wheel(int original) {
            this.original = this.reported = original;
        }
    }

    private static final class Hud {
        final LerpedFloat speed = LerpedFloat.linear(),
                throttle = LerpedFloat.linear(),
                promptSize = LerpedFloat.linear();
        Prompt prompt;
        int promptKeepAlive;
        Component foreign;
        int foreignTicks;
        Wheel wheel;
        Component sent;
        int sentAt = Integer.MIN_VALUE;
        int honkCooldown = 5;
        boolean usedToHonk;
    }

    private static final class Whistle {
        int honkTicks;
        ServerPlayer emitter;
        boolean playing, low;
        int nextLoop;
    }

    private static final int WHISTLE_LOOP_TICKS = 18, WHISTLE_RANGE = 64;
    private static final Map<Train, Whistle> WHISTLES = new WeakHashMap<>();

    private PolymerTrainHud() {}

    static void register() {
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(PolymerTrainHud::addAssets);
        ServerTickEvents.END_SERVER_TICK.register(PolymerTrainHud::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SHOWN.clear();
            WHISTLES.clear();
            PENDING_PROMPTS.clear();
            PENDING_FOREIGN.clear();
            ACKS.clear();
        });
    }

    private static void forget(UUID id) {
        SHOWN.remove(id);
        PENDING_PROMPTS.remove(id);
        PENDING_FOREIGN.remove(id);
        ACKS.remove(id);
    }

    public static void onPrompt(PacketListener listener, Component text, boolean shadow) {
        if (listener instanceof ServerGamePacketListenerImpl game
                && NativePresentationPolicy.allowsPresentation(game.player))
            PENDING_PROMPTS.put(game.player.getUUID(), new Prompt(text, shadow));
    }

    public static boolean captureOverlay(PacketListener listener, Component text) {
        if (!(listener instanceof ServerGamePacketListenerImpl game)
                || !NativePresentationPolicy.allowsPresentation(game.player)) return false;
        UUID id = game.player.getUUID();
        if (!PolymerTrainControls.isDriver(id) || advances.isEmpty()) return false;
        PENDING_FOREIGN.put(id, text);
        return true;
    }

    public static boolean onSlotChange(ServerPlayer player, int slot) {
        if (!NativePresentationPolicy.allowsPresentation(player)) return false;
        if (slot < 0 || slot >= 9) return false;
        Hud hud = SHOWN.get(player.getUUID());
        Carriage carriage = PolymerTrainControls.drivenCarriage(player);
        if (hud == null || carriage == null) return false;
        Wheel wheel = hud.wheel;
        int selected = player.getInventory().getSelectedSlot();
        if (wheel != null && selected != wheel.original) {
            restore(player, selected);
            hud.wheel = null;
            return false;
        }
        int previous = wheel == null ? selected : wheel.reported;
        int step = PolymerElevatorControls.step(previous, slot);
        if (step == 0) return wheel != null;
        if (Math.abs(step) != 1) return false;
        if (wheel == null) hud.wheel = wheel = new Wheel(selected);
        Train train = carriage.train;
        train.throttle = Mth.clamp(train.throttle - step / 18.0, 1 / 18.0, 1);
        wheel.reported = slot;
        if (wheel.ack == null) wheel.dirty = true;
        player.resetLastActionTime();
        return true;
    }

    public static boolean onPong(ServerGamePacketListenerImpl listener, ServerboundPongPacket packet) {
        if (!NativePresentationPolicy.allowsPresentation(listener.player)) return false;
        UUID id = listener.player.getUUID();
        if (!Objects.equals(ACKS.get(id), packet.getId())) return false;
        PacketUtils.ensureRunningOnSameThread(packet, listener, listener.player.level());
        if (!ACKS.remove(id, packet.getId())) return false;
        Hud hud = SHOWN.get(id);
        Wheel wheel = hud == null ? null : hud.wheel;
        if (wheel == null || !Objects.equals(wheel.ack, packet.getId())) return true;
        wheel.ack = null;
        wheel.reported = wheel.original;
        if (!wheel.dirty) hud.wheel = null;
        return true;
    }

    private static void restore(ServerPlayer player, int slot) {
        NativePresentationPolicy.sendPresentation(player, new ClientboundSetHeldSlotPacket(slot));
    }

    static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        tickWhistles(now);
        if (advances.isEmpty()) return;
        for (var iterator = SHOWN.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            ServerPlayer player = NativePresentationPolicy.onlinePlayer(server.getPlayerList(), entry.getKey());
            boolean presentation = NativePresentationPolicy.allowsPresentation(player);
            if (presentation && PolymerTrainControls.drivenCarriage(player) != null) continue;
            if (presentation) {
                Wheel wheel = entry.getValue().wheel;
                if (wheel != null) restore(player, player.getInventory().getSelectedSlot());
                NativePresentationPolicy.sendPresentation(
                        player, new ClientboundSetActionBarTextPacket(Component.empty()));
            }
            PENDING_PROMPTS.remove(entry.getKey());
            PENDING_FOREIGN.remove(entry.getKey());
            ACKS.remove(entry.getKey());
            iterator.remove();
        }
        for (ServerPlayer player : NativePresentationPolicy.presentationPlayers(server.getPlayerList())) {
            Carriage carriage = PolymerTrainControls.drivenCarriage(player);
            if (carriage == null) continue;
            Hud hud = SHOWN.computeIfAbsent(player.getUUID(), unused -> new Hud());
            tick(player, carriage, hud);
            honkInput(player, carriage.train, hud);
            Component composed = compose(player, carriage, hud);
            if (composed == null) continue;
            if (!composed.equals(hud.sent) || now - hud.sentAt >= REFRESH_TICKS) {
                hud.sent = composed;
                hud.sentAt = now;
                NativePresentationPolicy.sendPresentation(player, new ClientboundSetActionBarTextPacket(composed));
            }
            Wheel wheel = hud.wheel;
            if (wheel != null && wheel.dirty && wheel.ack == null) {
                int ack = NEXT_ACK.getAndIncrement();
                wheel.ack = ack;
                wheel.dirty = false;
                ACKS.put(player.getUUID(), ack);
                restore(player, wheel.original);
                NativePresentationPolicy.sendPresentation(player, new ClientboundPingPacket(ack));
            }
        }
    }

    private static void honkInput(ServerPlayer player, Train train, Hud hud) {
        boolean sprint = player.getLastClientInput().sprint();
        if (sprint && hud.honkCooldown-- <= 0) {
            train.determineHonk(player.level());
            if (train.lowHonk != null) {
                honk(player, train, true);
                hud.honkCooldown = 5;
                hud.usedToHonk = true;
            }
        }
        if (!sprint && hud.usedToHonk) {
            honk(player, train, false);
            hud.honkCooldown = 0;
            hud.usedToHonk = false;
        }
    }

    private static void honk(ServerPlayer player, Train train, boolean isHonk) {
        AllHandle.onTrainHonk(player.connection, new HonkPacket(train, isHonk));
        Whistle whistle = WHISTLES.computeIfAbsent(train, unused -> new Whistle());
        whistle.emitter = player;
        if (isHonk) whistle.honkTicks = whistle.honkTicks == 0 ? 20 : 13;
        else whistle.honkTicks = whistle.honkTicks > 5 ? 6 : 0;
    }

    private static void tickWhistles(int now) {
        for (var iterator = WHISTLES.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            Train train = entry.getKey();
            Whistle whistle = entry.getValue();
            ServerPlayer emitter = whistle.emitter;
            boolean valid = emitter != null && !emitter.isRemoved();
            if (whistle.honkTicks == 0 || !valid) {
                if (whistle.playing && valid) stopWhistle(emitter, whistle.low);
                iterator.remove();
                continue;
            }
            whistle.honkTicks--;
            train.determineHonk(emitter.level());
            if (train.lowHonk == null) continue;
            if (whistle.playing && whistle.low != train.lowHonk) {
                stopWhistle(emitter, whistle.low);
                whistle.playing = false;
            }
            whistle.low = train.lowHonk;
            float pitch = (float) Math.pow(2, train.honkPitch / 12.0);
            String end = whistle.low ? "whistle_train_manual_low_end" : "whistle_train_manual_end";
            if (whistle.honkTicks == 5) playWhistle(emitter, end, 1, pitch);
            if (whistle.honkTicks == 19) playWhistle(emitter, end, 0.5f, pitch);
            if (whistle.honkTicks == 0) {
                if (whistle.playing) stopWhistle(emitter, whistle.low);
                whistle.playing = false;
                continue;
            }
            if (!whistle.playing || now >= whistle.nextLoop) {
                playWhistle(emitter, whistle.low ? "whistle_train_manual_low" : "whistle_train_manual", 1, pitch);
                whistle.playing = true;
                whistle.nextLoop = now + WHISTLE_LOOP_TICKS;
            }
        }
    }

    private static List<ServerPlayer> listeners(ServerPlayer emitter) {
        return emitter.level()
                .getPlayers(other -> NativePresentationPolicy.allowsPresentation(other)
                        && other.distanceToSqr(emitter) <= WHISTLE_RANGE * WHISTLE_RANGE);
    }

    private static void playWhistle(ServerPlayer emitter, String name, float volume, float pitch) {
        var sound = Holder.direct(SoundEvent.createFixedRangeEvent(CreateIds.id(name), WHISTLE_RANGE));
        long seed = emitter.level().getRandom().nextLong();
        for (ServerPlayer listener : listeners(emitter))
            NativePresentationPolicy.sendPresentation(
                    listener,
                    new ClientboundSoundEntityPacket(sound, SoundSource.NEUTRAL, emitter, volume, pitch, seed));
    }

    private static void stopWhistle(ServerPlayer emitter, boolean low) {
        Identifier id = CreateIds.id(low ? "whistle_train_manual_low" : "whistle_train_manual");
        for (ServerPlayer listener : listeners(emitter))
            NativePresentationPolicy.sendPresentation(
                    listener, new ClientboundStopSoundPacket(id, SoundSource.NEUTRAL));
    }

    private static void tick(ServerPlayer player, Carriage carriage, Hud hud) {
        Prompt prompt = PENDING_PROMPTS.remove(player.getUUID());
        if (prompt != null) {
            hud.prompt = prompt;
            hud.promptKeepAlive = PROMPT_KEEP_ALIVE;
        } else if (hud.promptKeepAlive > 0) {
            hud.promptKeepAlive--;
        } else {
            hud.prompt = null;
        }
        Component foreign = PENDING_FOREIGN.remove(player.getUUID());
        if (foreign != null) {
            hud.foreign = foreign.getString().isEmpty() ? null : foreign;
            hud.foreignTicks = FOREIGN_TICKS;
        } else if (hud.foreignTicks > 0 && --hud.foreignTicks == 0) {
            hud.foreign = null;
        }
        hud.promptSize.chase(
                hud.prompt != null ? width(runs(hud.prompt.text(), TEXT_COLOR)) + 17 : 0, 0.5f, Chaser.EXP);
        hud.promptSize.tickChaser();

        Train train = carriage.train;
        double value = Math.abs(train.speed)
                / (train.maxSpeed()
                        * AllConfigs.server().trains.manualTrainSpeedModifier.getF());
        value = Mth.clamp(value + 0.05f, 0, 1);
        hud.speed.chase((int) (value * 18) / 18.0f, 0.5f, Chaser.EXP);
        hud.speed.tickChaser();
        hud.throttle.chase(train.throttle, 0.75f, Chaser.EXP);
        hud.throttle.tickChaser();
    }

    static Component compose(ServerPlayer player, Carriage carriage, Hud hud) {
        CarriageContraptionEntity cce = PolymerTrainControls.drivenEntity(player);
        BlockPos localPos = PolymerTrainControls.drivenControls(player);
        if (cce == null || localPos == null || cce.getContraption() == null) return null;
        List<Integer> pressed = PolymerTrainControls.buttons(player.getLastClientInput());
        StructureBlockInfo info = cce.getContraption().getBlocks().get(localPos);
        Direction initialOrientation = cce.getInitialOrientation().getCounterClockWise();
        boolean inverted = false;
        if (info != null && info.state().hasProperty(ControlsBlock.FACING))
            inverted = !info.state().getValue(ControlsBlock.FACING).equals(initialOrientation);
        boolean reversing = pressed.contains(1);
        inverted ^= reversing;
        int angleOffset = (pressed.contains(2) ? -45 : 0) + (pressed.contains(3) ? 45 : 0);
        if (reversing) angleOffset *= -1;
        float diff = AngleHelper.getShortestAngleDiff(player.getYRot(), cce.yaw) + (inverted ? -90 : 90);
        if (Math.abs(diff) < 60) diff = 0;
        float angle = diff + angleOffset;
        int frame = Math.floorMod(Math.round(angle / 22.5f), 16);

        int promptSize = (int) hud.promptSize.getValue();
        List<Run> text = promptSize > 1 && hud.prompt != null ? runs(hud.prompt.text(), TEXT_COLOR) : List.of();
        Component line = layout(
                (int) (BAR_W * hud.speed.getValue()),
                (int) (BAR_W * (1 - hud.throttle.getValue())),
                promptSize,
                frame,
                text,
                hud.prompt != null && hud.prompt.shadow());
        if (hud.foreign == null) return line;
        List<Run> foreign = runs(hud.foreign, 0xFFFFFF);
        int width = width(foreign);
        MutableComponent result = Component.empty().append(line);
        result.append(space(Math.round(width / -2.0f)));
        for (Run run : foreign)
            result.append(Component.literal(run.text())
                    .withStyle(Style.EMPTY.withFont(VANILLA).withColor(run.color())));
        result.append(space(-(width - Math.round(width / 2.0f))));
        return result;
    }

    static Component layout(
            int speedWidth, int throttleWidth, int promptSize, int frame, List<Run> text, boolean shadow) {
        Pen pen = new Pen();
        pen.glyph(FRAME, -2, 0xFFFFFF);
        pen.glyph(SPEED_BG, 0, 0xFFFFFF);
        pen.strip(ROW_SPEED, 0, speedWidth, 0);
        if (promptSize > 1) {
            int x0 = Math.round(promptSize / -2.0f + 91);
            pen.glyph(PROMPT_L, x0 - 3, 0xFFFFFF);
            pen.glyph(PROMPT_R, x0 + promptSize, 0xFFFFFF);
            int start = Math.round(128 - promptSize / 2.0f);
            pen.strip(ROW_PROMPT, start, Math.min(256, start + promptSize), x0 - start);
            int width = width(text);
            if (!text.isEmpty() && width < promptSize - 10) pen.text(text, Math.round(width / -2.0f + 82 + 9), shadow);
        }
        pen.glyph(DIRECTION, 77, 0xFFFFFF);
        int invW = BAR_W - throttleWidth;
        pen.strip(ROW_THROTTLE, invW, BAR_W, 0);
        pen.glyph(POINTER, Math.max(1, BAR_W - throttleWidth) - 3, 0xFFFFFF);
        pen.glyph(ARROW + frame, 83, 0xFFFFFF);
        return pen.finish();
    }

    private record Run(String text, int color) {}

    private static final class Pen {
        final MutableComponent root = Component.empty();
        int x = 91;
        FontDescription runFont;
        int runColor = -1;
        boolean runShadow;
        StringBuilder run = new StringBuilder();

        void glyph(int codepoint, int x, int color) {
            Integer advance = advances.get(codepoint);
            if (advance == null) return;
            moveTo(x);
            append(FONT, color, false, Character.toString(codepoint));
            this.x += advance;
        }

        void strip(int row, int from, int to, int offset) {
            int a = Math.max(0, from);
            while (a < to) {
                int bit = 8;
                while (bit > 0 && (a % (1 << bit) != 0 || a + (1 << bit) > to)) bit--;
                glyph(STRIP + row * STRIP_STRIDE + (512 - (512 >> bit)) + (a >> bit), a + offset, 0xFFFFFF);
                a += 1 << bit;
            }
        }

        void text(List<Run> runs, int x, boolean shadow) {
            moveTo(x);
            for (Run part : runs) {
                StringBuilder chars = new StringBuilder();
                part.text().codePoints().forEach(cp -> {
                    int shown = advances.containsKey(cp) || cp == ' ' ? cp : '?';
                    chars.appendCodePoint(shown);
                    this.x += cp == ' ' ? 4 : advances.getOrDefault(shown, 6);
                });
                append(FONT, part.color(), shadow, chars.toString());
            }
        }

        void moveTo(int target) {
            int delta = target - x;
            if (delta == 0) return;
            append(FONT, runColor < 0 ? 0xFFFFFF : runColor, runShadow, spaces(delta));
            x = target;
        }

        private void append(FontDescription font, int color, boolean shadow, String text) {
            if (!font.equals(runFont) || color != runColor || shadow != runShadow) flush();
            runFont = font;
            runColor = color;
            runShadow = shadow;
            run.append(text);
        }

        private void flush() {
            if (run.isEmpty()) return;
            Style style = Style.EMPTY.withFont(runFont).withColor(runColor);
            root.append(Component.literal(run.toString()).withStyle(runShadow ? style : style.withoutShadow()));
            run = new StringBuilder();
        }

        Component finish() {
            moveTo(91);
            flush();
            return root;
        }
    }

    private static String spaces(int delta) {
        StringBuilder chars = new StringBuilder();
        int remaining = Math.abs(delta);
        for (int bit = 9; bit >= 0; bit--)
            while (remaining >= 1 << bit) {
                chars.appendCodePoint(SPACE_BASE + (delta < 0 ? 0x20 : 0) + bit + 1);
                remaining -= 1 << bit;
            }
        return chars.toString();
    }

    private static Component space(int delta) {
        return Component.literal(spaces(delta))
                .withStyle(Style.EMPTY.withFont(FONT).withoutShadow());
    }

    private static int width(List<Run> runs) {
        Map<Integer, Integer> table = advances;
        int width = 0;
        for (Run run : runs)
            for (int cp : run.text().codePoints().toArray())
                width += cp == ' ' ? 4 : table.getOrDefault(cp, table.getOrDefault((int) '?', 6));
        return width;
    }

    static List<Run> runs(Component component, int baseColor) {
        List<Run> out = new ArrayList<>();
        flatten(
                component,
                Optional.ofNullable(component.getStyle().getColor())
                        .map(TextColor::getValue)
                        .orElse(baseColor),
                out);
        return out;
    }

    private static void flatten(Component component, int inherited, List<Run> out) {
        int color = Optional.ofNullable(component.getStyle().getColor())
                .map(TextColor::getValue)
                .orElse(inherited);
        ComponentContents contents = component.getContents();
        if (contents instanceof PlainTextContents plain) {
            if (!plain.text().isEmpty()) out.add(new Run(plain.text(), color));
        } else if (contents instanceof KeybindContents keybind) {
            out.add(new Run(keyName(keybind.getName()), color));
        } else if (contents instanceof TranslatableContents translatable) {
            String pattern = PolymerSchematicGuis.all().get(translatable.getKey());
            if (pattern == null)
                pattern = translatable.getFallback() != null ? translatable.getFallback() : translatable.getKey();
            Object[] args = translatable.getArgs();
            Matcher matcher = FORMAT.matcher(pattern);
            int last = 0, next = 0;
            while (matcher.find()) {
                if (matcher.start() > last) out.add(new Run(pattern.substring(last, matcher.start()), color));
                last = matcher.end();
                if (matcher.group(2).equals("%")) {
                    out.add(new Run("%", color));
                    continue;
                }
                int index = matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) - 1 : next++;
                if (index < 0 || index >= args.length) continue;
                Object arg = args[index];
                if (arg instanceof Component argument) flatten(argument, color, out);
                else out.add(new Run(String.valueOf(arg), color));
            }
            if (last < pattern.length()) out.add(new Run(pattern.substring(last), color));
        } else {
            String text = component.plainCopy().getString();
            if (!text.isEmpty()) out.add(new Run(text, color));
        }
        for (Component sibling : component.getSiblings()) flatten(sibling, color, out);
    }

    private static String keyName(String key) {
        return switch (key) {
            case "key.jump" -> "Space";
            case "key.sneak" -> "Left Shift";
            case "key.sprint" -> "Left Control";
            case "key.forward" -> "W";
            case "key.back" -> "S";
            case "key.left" -> "A";
            case "key.right" -> "D";
            case "key.use" -> "Right Button";
            case "key.attack" -> "Left Button";
            default -> key;
        };
    }

    static void addAssets(ResourcePackBuilder builder) {
        try {
            byte[] defaultFont = data(builder, "assets/minecraft/font/include/default.json");
            BufferedImage ascii = image(builder, "assets/minecraft/textures/font/ascii.png");
            BufferedImage widgets = image(builder, "assets/create/textures/gui/widgets.png");
            BufferedImage arrows = image(builder, "assets/ponder/textures/gui/placement_indicator.png");
            Map<Integer, Integer> advance = new HashMap<>();
            writeAssets(
                    JsonParser.parseString(new String(defaultFont, StandardCharsets.UTF_8)),
                    ascii,
                    widgets,
                    arrows,
                    builder::addData,
                    advance);
            advances = Map.copyOf(advance);
        } catch (IOException | RuntimeException exception) {
            advances = Map.of();
            Create.LOGGER.warn("Train HUD disabled: cannot build its font", exception);
        }
    }

    private static byte[] data(ResourcePackBuilder builder, String path) throws IOException {
        byte[] bytes = builder.getDataOrSource(path);
        if (bytes != null) return bytes;
        var file = FabricLoader.getInstance()
                .getModContainer("create")
                .orElseThrow()
                .findPath(path);
        if (file.isPresent()) return Files.readAllBytes(file.get());
        throw new IOException("Missing pack source " + path);
    }

    private static BufferedImage image(ResourcePackBuilder builder, String path) throws IOException {
        BufferedImage image = PngCodec.decode(data(builder, path));
        BufferedImage argb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        argb.getGraphics().drawImage(image, 0, 0, null);
        return argb;
    }

    static int ascent(int sourceY) {
        return -36 - sourceY;
    }

    static void writeAssets(
            JsonElement defaultFont,
            BufferedImage ascii,
            BufferedImage widgets,
            BufferedImage arrows,
            BiConsumer<String, byte[]> out,
            Map<Integer, Integer> advance)
            throws IOException {
        if (widgets.getWidth() != 256 || widgets.getHeight() != 256)
            throw new IOException("Unexpected widgets.png size");
        if (arrows.getWidth() != 16 || arrows.getHeight() != 256)
            throw new IOException("Unexpected placement indicator size");
        JsonArray providers = new JsonArray();
        single(out, providers, advance, "frame", widgets.getSubimage(0, 200, 186, 7), FRAME, 1);
        single(out, providers, advance, "speed_bg", widgets.getSubimage(0, 190, 182, 5), SPEED_BG, 0);
        single(out, providers, advance, "direction", widgets.getSubimage(77, 165, 28, 20), DIRECTION, -20);
        single(out, providers, advance, "pointer", widgets.getSubimage(0, 209, 6, 9), POINTER, -2);
        single(out, providers, advance, "prompt_l", widgets.getSubimage(8, 209, 3, 16), PROMPT_L, -27);
        single(out, providers, advance, "prompt_r", widgets.getSubimage(11, 209, 3, 16), PROMPT_R, -27);
        strip(out, providers, advance, ROW_SPEED, widgets.getSubimage(0, 185, BAR_W, BAR_H), 0);
        strip(out, providers, advance, ROW_THROTTLE, widgets.getSubimage(0, 195, BAR_W, BAR_H), 0);
        strip(out, providers, advance, ROW_PROMPT, widgets.getSubimage(0, 230, 256, 16), -27);
        float size = 24 * 0.75f * 0.925f;
        BufferedImage sheet = new BufferedImage(16, 16 * 16, BufferedImage.TYPE_INT_ARGB);
        List<String> rows = new ArrayList<>();
        for (int frame = 0; frame < 16; frame++) {
            for (int y = 0; y < 16; y++)
                for (int x = 0; x < 16; x++) {
                    int u = (int) ((x - 8 + 0.5f + size / 2) / size * 16),
                            v = (int) ((y - 8 + 0.5f + size / 2) / size * 16);
                    sheet.setRGB(x, frame * 16 + y, arrows.getRGB(Math.min(15, u), frame * 16 + Math.min(15, v)));
                }
            rows.add(Character.toString(ARROW + frame));
            measure(sheet.getSubimage(0, frame * 16, 16, 16), ARROW + frame, advance);
        }
        png(out, "assets/create/textures/font/train_hud_arrows.png", sheet);
        providers.add(PolymerGuiGlyphs.bitmapRows("create:font/train_hud_arrows.png", 16, ascent(-17), rows));

        List<String> asciiRows = null;
        for (JsonElement provider : defaultFont.getAsJsonObject().getAsJsonArray("providers")) {
            JsonObject object = provider.getAsJsonObject();
            if ("bitmap".equals(object.get("type").getAsString())
                    && object.get("file").getAsString().endsWith("font/ascii.png")) {
                asciiRows = new ArrayList<>();
                for (JsonElement row : object.getAsJsonArray("chars")) asciiRows.add(row.getAsString());
            }
        }
        if (asciiRows == null) throw new IOException("No ascii provider in vanilla default font");
        int columns =
                asciiRows.getFirst().codePointCount(0, asciiRows.getFirst().length());
        if (ascii.getWidth() / columns != 8 || ascii.getHeight() / asciiRows.size() != 8)
            throw new IOException("Unexpected ascii cell size");
        for (int row = 0; row < asciiRows.size(); row++) {
            int[] codepoints = asciiRows.get(row).codePoints().toArray();
            for (int column = 0; column < codepoints.length; column++) {
                int codepoint = codepoints[column];
                if (codepoint == 0 || codepoint == ' ') continue;
                int width = 0;
                for (int y = 0; y < 8; y++)
                    for (int x = 0; x < 8; x++)
                        if ((ascii.getRGB(column * 8 + x, row * 8 + y) >>> 24) != 0) width = Math.max(width, x + 1);
                if (width > 0) advance.put(codepoint, width + 1);
            }
        }
        providers.add(PolymerGuiGlyphs.bitmapRows("minecraft:font/ascii.png", 8, ascent(-23), asciiRows));

        JsonObject spaces = new JsonObject();
        spaces.addProperty(" ", 4);
        for (int bit = 0; bit <= 9; bit++) {
            spaces.addProperty(Character.toString(SPACE_BASE + bit + 1), 1 << bit);
            spaces.addProperty(Character.toString(SPACE_BASE + 0x20 + bit + 1), -(1 << bit));
        }
        JsonObject space = new JsonObject();
        space.addProperty("type", "space");
        space.add("advances", spaces);
        JsonArray ordered = new JsonArray();
        ordered.add(space);
        ordered.addAll(providers);
        JsonObject root = new JsonObject();
        root.add("providers", ordered);
        out.accept("assets/create/font/train_hud.json", root.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void single(
            BiConsumer<String, byte[]> out,
            JsonArray providers,
            Map<Integer, Integer> advance,
            String name,
            BufferedImage image,
            int codepoint,
            int y)
            throws IOException {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        copy.getGraphics().drawImage(image, 0, 0, null);
        png(out, "assets/create/textures/font/train_hud_" + name + ".png", copy);
        providers.add(PolymerGuiGlyphs.bitmapRows(
                "create:font/train_hud_" + name + ".png",
                copy.getHeight(),
                ascent(y),
                List.of(Character.toString(codepoint))));
        measure(copy, codepoint, advance);
    }

    private static void strip(
            BiConsumer<String, byte[]> out,
            JsonArray providers,
            Map<Integer, Integer> advance,
            int row,
            BufferedImage image,
            int y)
            throws IOException {
        BufferedImage padded = new BufferedImage(256, image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        padded.getGraphics().drawImage(image, 0, 0, null);
        String file = "train_hud_strip_" + row;
        png(out, "assets/create/textures/font/" + file + ".png", padded);
        for (int bit = 0; bit <= 8; bit++) {
            int size = 1 << bit, base = STRIP + row * STRIP_STRIDE + (512 - (512 >> bit));
            StringBuilder chars = new StringBuilder();
            for (int k = 0; k < 256 >> bit; k++) {
                chars.appendCodePoint(base + k);
                measure(padded.getSubimage(k * size, 0, size, padded.getHeight()), base + k, advance);
            }
            providers.add(PolymerGuiGlyphs.bitmapRows(
                    "create:font/" + file + ".png", padded.getHeight(), ascent(y), List.of(chars.toString())));
        }
    }

    private static void measure(BufferedImage cell, int codepoint, Map<Integer, Integer> advance) {
        int width = 0;
        for (int y = 0; y < cell.getHeight(); y++)
            for (int x = 0; x < cell.getWidth(); x++)
                if ((cell.getRGB(x, y) >>> 24) != 0) width = Math.max(width, x + 1);
        if (width > 0) advance.put(codepoint, width + 1);
    }

    private static void png(BiConsumer<String, byte[]> out, String path, BufferedImage image) throws IOException {
        out.accept(path, PngCodec.encode(image));
    }
}
