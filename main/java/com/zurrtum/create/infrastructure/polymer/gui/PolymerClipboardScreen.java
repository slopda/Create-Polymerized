package com.zurrtum.create.infrastructure.polymer.gui;

import static com.zurrtum.create.infrastructure.polymer.gui.PolymerArtKit.image;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zurrtum.create.AllDataComponents;
import com.zurrtum.create.AllHandle;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.AllSoundEvents;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.equipment.clipboard.ClipboardBlockEntity;
import com.zurrtum.create.infrastructure.component.ClipboardContent;
import com.zurrtum.create.infrastructure.component.ClipboardEntry;
import com.zurrtum.create.infrastructure.component.ClipboardType;
import com.zurrtum.create.infrastructure.packet.c2s.ClipboardEditPacket;
import com.zurrtum.create.infrastructure.polymer.PolymerInventorySync;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerGogglesOverlay;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
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
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

final class PolymerClipboardScreen {
    private static final Identifier ACTION = CreateIds.id("polymer_clipboard_screen");
    private static final FontDescription ART_FONT = font("clipboard_art"), SPACE_FONT = font("value_board_space");
    private static final int ART = 256, ADVANCE = ART + 1, LINES = (ART + 8) / 9;
    private static final int TEXT = 0x311A00,
            CHECKED = 0x31B25D,
            FADED = 0xD1C9BA,
            BOX = 0x8D7F6B,
            INDICATOR = 0x5A5A5A;
    private static final String MARKER = "\uE200",
            BOX_CHAR = "\uE201",
            TICK = "\uE202",
            ADDRESS = "\uE203",
            ADDRESS_INACTIVE = "\uE204",
            CLEAR = "\uE205",
            CLOSE = "\uE206",
            FORWARD = "\uE207",
            BACKWARD = "\uE208";
    private static final int MIN_ROW = 0, MAX_ROW = 250;
    private static final long LIFETIME = 10L * 60 * 1_000_000_000L;
    private static final Map<UUID, Session> SESSIONS = PolymerDialogSessions.states();

    private PolymerClipboardScreen() {}

    private static final class Session {
        final String token = UUID.randomUUID().toString();
        final ServerLevel level;
        final int slot;
        final @Nullable BlockPos pos;
        final List<List<ClipboardEntry>> pages;
        final boolean readOnly;
        ClipboardContent content;
        int page, revision, editing = -1;
        long expires;

        Session(ServerLevel level, int slot, @Nullable BlockPos pos, ClipboardContent content) {
            this.level = level;
            this.slot = slot;
            this.pos = pos;
            this.content = content;
            pages = new ArrayList<>();
            for (List<ClipboardEntry> source : content.pages()) {
                List<ClipboardEntry> copy = new ArrayList<>();
                for (ClipboardEntry entry : source)
                    copy.add(new ClipboardEntry(entry.checked, entry.text.copy())
                            .displayItem(entry.icon.copy(), entry.itemAmount));
                pages.add(copy);
            }
            if (pages.isEmpty()) pages.add(new ArrayList<>());
            readOnly = content.readOnly();
            page = Math.clamp(content.previouslyOpenedPage(), 0, pages.size() - 1);
            if (pages.get(page).isEmpty()) pages.get(page).add(new ClipboardEntry(false, Component.empty()));
        }

        List<ClipboardEntry> entries() {
            return pages.get(page);
        }
    }

    static void openItem(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (player.isSpectator() || !stack.is(AllItems.CLIPBOARD)) return;
        open(
                player,
                new Session(
                        player.level(), player.getInventory().getSelectedSlot(), null, content(stack.getComponents())));
    }

    static void openBlock(ServerPlayer player, BlockPos pos, ClipboardBlockEntity blockEntity) {
        open(player, new Session(player.level(), 0, pos.immutable(), content(blockEntity.components())));
    }

    private static ClipboardContent content(DataComponentMap components) {
        return components.getOrDefault(AllDataComponents.CLIPBOARD_CONTENT, ClipboardContent.EMPTY);
    }

    private static void open(ServerPlayer player, Session session) {
        session.expires = System.nanoTime() + LIFETIME;
        SESSIONS.put(player.getUUID(), session);
        PolymerDialogForms.open(player, dialog(session));
    }

    private static boolean valid(ServerPlayer player, Session session) {
        if (player.level() != session.level || player.isSpectator() || session.expires < System.nanoTime())
            return false;
        if (session.pos == null)
            return player.getInventory().getItem(session.slot).is(AllItems.CLIPBOARD)
                    && player.getInventory().getSelectedSlot() == session.slot;
        return session.level.isLoaded(session.pos)
                && session.pos.closerThan(player.blockPosition(), 10)
                && session.level.getBlockEntity(session.pos) instanceof ClipboardBlockEntity;
    }

    private static Holder<Dialog> dialog(Session session) {
        Layout layout = layout(session);
        List<MutableComponent> lines = new ArrayList<>();
        for (int i = 0; i < LINES; i++) lines.add(Component.empty());

        Pen pen = new Pen(lines.getFirst());
        pen.glyph(
                Component.literal("\uE000")
                        .withStyle(
                                Style.EMPTY.withColor(0xFFFFFF).withoutShadow().withFont(ART_FONT)),
                ADVANCE);
        String indicator = "Page " + (session.page + 1) + " of " + session.pages.size();
        pen.text(
                150,
                9,
                Component.translatable("book.pageIndicator", session.page + 1, session.pages.size()),
                indicator,
                INDICATOR);
        List<ClipboardEntry> entries = session.entries();
        for (int i = 0; i < entries.size(); i++) {
            Layout.Entry placed = layout.entries.get(i);
            ClipboardEntry entry = entries.get(i);
            if (placed.address) {
                pen.symbol(44, placed.top, entry.checked ? ADDRESS_INACTIVE : ADDRESS, 8);
            } else {
                pen.symbol(45, placed.top + 1, BOX_CHAR, 7, entry.checked ? FADED : BOX);
                if (entry.checked) pen.symbol(45, placed.top, TICK, 7, CHECKED);
            }
            int color = entry.checked ? placed.address ? FADED : CHECKED : TEXT;
            for (int l = 0; l < placed.lines.size(); l++)
                pen.text(58 + placed.iconOffset, placed.top + 9 * l, null, placed.lines.get(l), color);
        }
        if (!session.readOnly) {
            pen.symbol(234, 153, CLEAR, 18);
        }
        pen.symbol(234, 175, CLOSE, 18);
        if (forwardVisible(session)) pen.symbol(176, 229, FORWARD, 23);
        if (session.page > 0) pen.symbol(53, 229, BACKWARD, 23);
        pen.finish();

        for (int line = 1; line < LINES; line++) {
            int y = line * 9 + 4;
            List<Run> runs = new ArrayList<>();
            int entry = layout.entryAt(y);
            if (entry >= 0) {
                if (!session.readOnly) runs.add(new Run(35, 55, "check", entry, null));
                if (!session.readOnly) runs.add(new Run(55, 218, "edit", entry, null));
            } else if (y >= Math.max(41, layout.bottom) && y < 231 && !session.readOnly && layout.bottom - 50 < 185) {
                runs.add(new Run(55, 218, "edit", entries.size(), null));
            }
            if (!session.readOnly && y >= 153 && y < 171)
                runs.add(new Run(234, 252, "erase", -1, "create.gui.clipboard.erase_checked"));
            if (y >= 175 && y < 193) runs.add(new Run(234, 252, "close", -1, "create.station.close"));
            if (y >= 229 && y < 242) {
                if (session.page > 0) runs.add(new Run(53, 76, "page", -1, null));
                if (forwardVisible(session)) runs.add(new Run(176, 199, "page", 1, null));
            }
            hits(lines.get(line), runs, session);
        }
        MutableComponent body = Component.empty();
        for (int i = 0; i < LINES; i++) {
            if (i > 0) body.append(Component.literal("\n"));
            body.append(lines.get(i));
        }
        var common = new CommonDialogData(
                Component.empty(),
                Optional.of(Component.translatable("block.create.clipboard")),
                true,
                false,
                DialogAction.NONE,
                List.of(new PlainMessage(body, ADVANCE + 8)),
                List.of());
        // MultiActionDialog's codec rejects an empty action list (encode failure disconnects the client).
        CompoundTag done = new CompoundTag();
        done.putString("session", session.token);
        done.putInt("revision", session.revision);
        done.putString("operation", "close");
        ActionButton close = new ActionButton(
                new CommonButtonData(PolymerGuiTextures.actionLabel("Done"), 120),
                Optional.of(new CustomAll(ACTION, Optional.of(done))));
        return Holder.direct(new MultiActionDialog(common, List.of(close), Optional.empty(), 1));
    }

    private static boolean forwardVisible(Session session) {
        return session.page < 50 && (!session.readOnly || session.page + 1 < session.pages.size());
    }

    private record Run(
            int x0,
            int x1,
            String operation,
            int index,
            @Nullable String tooltip) {}

    private static void hits(MutableComponent line, List<Run> runs, Session session) {
        runs.sort(Comparator.comparingInt(Run::x0));
        int pen = 0;
        line.append(marker());
        pen += 2;
        for (Run run : runs) {
            if (run.x0() < pen) continue;
            line.append(PolymerArtKit.space(SPACE_FONT, run.x0() - pen));
            CompoundTag tag = new CompoundTag();
            tag.putString("session", session.token);
            tag.putInt("revision", session.revision);
            tag.putString("operation", run.operation());
            tag.putInt("index", run.index());
            Style style = Style.EMPTY.withClickEvent(new ClickEvent.Custom(ACTION, Optional.of(tag)));
            if (run.tooltip() != null)
                style = style.withHoverEvent(new HoverEvent.ShowText(Component.translatable(run.tooltip())));
            line.append(
                    Component.empty().withStyle(style).append(PolymerArtKit.space(SPACE_FONT, run.x1() - run.x0())));
            pen = run.x1();
        }
        line.append(PolymerArtKit.space(SPACE_FONT, ADVANCE - 2 - pen));
        line.append(marker());
    }

    private static Component marker() {
        return Component.literal(MARKER)
                .withStyle(Style.EMPTY.withColor(0xFFFFFF).withoutShadow().withFont(ART_FONT));
    }

    private static final class Pen {
        final MutableComponent line;
        int pen;

        Pen(MutableComponent line) {
            this.line = line;
        }

        void glyph(Component component, int advance) {
            line.append(component);
            pen += advance;
        }

        void text(int x, int y, @Nullable Component translated, String text, int color) {
            if (y < MIN_ROW || y > MAX_ROW || text.isEmpty()) return;
            line.append(PolymerArtKit.space(SPACE_FONT, x - pen));
            Style style = Style.EMPTY.withColor(color).withoutShadow().withFont(rowFont(y));
            line.append(
                    translated == null
                            ? Component.literal(text).withStyle(style)
                            : translated.copy().withStyle(style));
            pen = x + PolymerGogglesOverlay.width(text);
        }

        void symbol(int x, int y, String symbol, int width) {
            symbol(x, y, symbol, width, 0xFFFFFF);
        }

        void symbol(int x, int y, String symbol, int width, int color) {
            if (y < MIN_ROW || y > MAX_ROW) return;
            line.append(PolymerArtKit.space(SPACE_FONT, x - pen));
            line.append(Component.literal(symbol)
                    .withStyle(Style.EMPTY.withColor(color).withoutShadow().withFont(rowFont(y))));
            pen = x + width + 1;
        }

        void finish() {
            line.append(PolymerArtKit.space(SPACE_FONT, ADVANCE - pen));
        }
    }

    private static final class Layout {
        record Entry(int top, int bottom, boolean address, int iconOffset, List<String> lines) {}

        final List<Entry> entries = new ArrayList<>();
        int bottom;

        int entryAt(int y) {
            for (int i = 0; i < entries.size(); i++)
                if (y >= entries.get(i).top() - 1 && y < entries.get(i).bottom() - 1) return i;
            return -1;
        }
    }

    private static Layout layout(Session session) {
        Layout layout = new Layout();
        int y = 50;
        for (ClipboardEntry entry : session.entries()) {
            String string = entry.text.getString();
            boolean address = string.startsWith("#") && !string.substring(1).isBlank();
            String shown = address ? string.substring(1).stripLeading() : string;
            int iconOffset = entry.icon.isEmpty() ? 0 : 16;
            List<String> wrapped = wrap(shown, 150 - iconOffset);
            int height = wrapped.isEmpty() ? 12 : wrapped.size() * 9 + 3;
            layout.entries.add(new Layout.Entry(y, y + height, address, iconOffset, wrapped));
            y += height;
        }
        layout.bottom = y;
        return layout;
    }

    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text.isEmpty()) return lines;
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ", -1)) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (PolymerGogglesOverlay.width(candidate) <= width) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }
            if (!current.isEmpty()) lines.add(current.toString());
            current.setLength(0);
            for (int cp : word.codePoints().toArray()) {
                String next = current + Character.toString(cp);
                if (PolymerGogglesOverlay.width(next) > width && !current.isEmpty()) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                current.appendCodePoint(cp);
            }
        }
        if (!current.isEmpty()) lines.add(current.toString());
        return lines;
    }

    private static boolean fits(List<ClipboardEntry> entries) {
        int total = 0;
        for (ClipboardEntry entry : entries)
            total += Math.max(12, wrap(entry.text.getString(), 150).size() * 9 + 3);
        return total < 185;
    }

    static boolean handle(ServerPlayer player, Identifier id, Optional<Tag> payload) {
        if (!PolymerDialogForms.validSubmission(player, id, payload, ACTION)) return true;
        if (!ACTION.equals(id)) return false;
        Session session = SESSIONS.get(player.getUUID());
        CompoundTag tag = PolymerDialogForms.payload(payload).orElse(null);
        if (session == null
                || tag == null
                || !tag.getString("session").orElse("").equals(session.token)) return true;
        if (!valid(player, session)) {
            SESSIONS.remove(player.getUUID());
            NativePresentationPolicy.sendPresentation(player, ClientboundClearDialogPacket.INSTANCE);
            return true;
        }
        String operation = tag.getString("operation").orElse("");
        boolean edited = operation.equals("edit_done") || operation.equals("edit_cancel");
        if (!edited && tag.getInt("revision").orElse(-1) != session.revision) return true;
        int index = tag.getInt("index").orElse(-1);
        List<ClipboardEntry> entries = session.entries();
        switch (operation) {
            case "check" -> {
                if (session.readOnly || index < 0 || index >= entries.size()) break;
                ClipboardEntry entry = entries.get(index);
                entry.checked ^= true;
                float pitch = 0.95f + player.getRandom().nextFloat() * 0.1f;
                NativePresentationPolicy.sendPresentation(
                        player,
                        new ClientboundSoundPacket(
                                Holder.direct((entry.checked
                                                ? AllSoundEvents.CLIPBOARD_CHECKMARK
                                                : AllSoundEvents.CLIPBOARD_ERASE)
                                        .getMainEvent()),
                                SoundSource.BLOCKS,
                                player.getX(),
                                player.getY(),
                                player.getZ(),
                                1,
                                pitch,
                                player.getRandom().nextLong()));
                send(player, session);
            }
            case "edit" -> {
                if (session.readOnly || index < 0 || index > entries.size()) break;
                if (index == entries.size()) entries.add(new ClipboardEntry(false, Component.empty()));
                session.editing = index;
                PolymerDialogForms.open(
                        player, editDialog(session, entries.get(index).text.getString()));
                return true;
            }
            case "edit_done" -> {
                if (session.editing < 0 || session.editing >= entries.size()) break;
                String text = tag.getString("text")
                        .orElse("")
                        .replaceAll("[\\p{Cntrl}&&[^\\n]]", "")
                        .replace('\n', ' ');
                ClipboardEntry entry = entries.get(session.editing);
                MutableComponent previous = entry.text;
                entry.text = Component.literal(text);
                while (!fits(entries) && !text.isEmpty()) {
                    text = text.substring(0, text.length() - 1);
                    entry.text = Component.literal(text);
                }
                if (!entry.text.getString().equals(previous.getString())) send(player, session);
                session.editing = -1;
            }
            case "edit_cancel" -> session.editing = -1;
            case "erase" -> {
                if (session.readOnly) break;
                entries.removeIf(entry -> entry.checked);
                if (entries.isEmpty()) entries.add(new ClipboardEntry(false, Component.empty()));
                send(player, session);
            }
            case "page" -> changePage(session, index > 0);
            case "close" -> {
                SESSIONS.remove(player.getUUID());
                if (!session.readOnly) send(player, session);
                NativePresentationPolicy.sendPresentation(player, ClientboundClearDialogPacket.INSTANCE);
                PolymerInventorySync.afterMenu(player);
                return true;
            }
            default -> {
                return true;
            }
        }
        session.revision++;
        open(player, session);
        return true;
    }

    private static void changePage(Session session, boolean next) {
        int previously = session.page;
        session.page = Math.clamp(session.page + (next ? 1 : -1), 0, 50);
        if (session.page == previously) return;
        if (session.pages.size() <= session.page) {
            if (session.readOnly) {
                session.page = previously;
                return;
            }
            session.pages.add(new ArrayList<>());
        }
        if (session.entries().isEmpty()) session.entries().add(new ClipboardEntry(false, Component.empty()));
        if (!next
                && session.pages.get(session.page + 1).stream()
                        .allMatch(entry -> entry.text.getString().isBlank())) session.pages.remove(session.page + 1);
    }

    private static Holder<Dialog> editDialog(Session session, String text) {
        Input input = PolymerDialogForms.input(
                "text",
                PolymerDialogForms.textControl(
                        240,
                        Component.translatable("block.create.clipboard"),
                        false,
                        text,
                        256,
                        Optional.of(new TextInput.MultilineOptions(Optional.of(6), Optional.of(64)))));
        CompoundTag done = new CompoundTag();
        done.putString("session", session.token);
        done.putString("operation", "edit_done");
        CompoundTag cancel = new CompoundTag();
        cancel.putString("session", session.token);
        cancel.putString("operation", "edit_cancel");
        var common = new CommonDialogData(
                Component.translatable("block.create.clipboard"),
                Optional.empty(),
                true,
                false,
                DialogAction.NONE,
                List.of(),
                List.of(input));
        return Holder.direct(new MultiActionDialog(
                common,
                List.of(new ActionButton(
                        new CommonButtonData(PolymerGuiTextures.actionLabel("Done"), 120),
                        Optional.of(new CustomAll(ACTION, Optional.of(done))))),
                Optional.of(new ActionButton(
                        new CommonButtonData(PolymerGuiTextures.actionLabel("Cancel"), 120),
                        Optional.of(new CustomAll(ACTION, Optional.of(cancel))))),
                2));
    }

    private static void send(ServerPlayer player, Session session) {
        List<List<ClipboardEntry>> cleaned = new ArrayList<>();
        int opened = 0;
        for (int i = 0; i < session.pages.size(); i++) {
            List<ClipboardEntry> page = new ArrayList<>();
            for (ClipboardEntry entry : session.pages.get(i))
                if (!entry.text.getString().isBlank()) page.add(entry);
            if (page.isEmpty()) continue;
            if (i <= session.page) opened = cleaned.size();
            cleaned.add(page);
        }
        ClipboardContent content = cleaned.isEmpty()
                ? null
                : session.content
                        .setPages(cleaned)
                        .setType(ClipboardType.WRITTEN)
                        .setPreviouslyOpenedPage(opened);
        if (content != null) session.content = content;
        AllHandle.onClipboardEdit(player.connection, new ClipboardEditPacket(session.slot, content, session.pos));
    }

    private static FontDescription font(String name) {
        return new FontDescription.Resource(CreateIds.id(name));
    }

    private static FontDescription rowFont(int y) {
        return font("clipboard_row_" + y);
    }

    static void addAssets(ResourcePackBuilder builder) {
        try {
            BufferedImage art =
                    image(builder, "assets/create/textures/gui/clipboard.png").getSubimage(0, 0, ART, ART);
            BufferedImage copy = new BufferedImage(ART, ART, BufferedImage.TYPE_INT_ARGB);
            copy.createGraphics().drawImage(art, 0, 0, null);
            copy.setRGB(ART - 1, 0, 0x01FFFFFF);
            PolymerGuiGlyphs.fontPng(builder, "clipboard_art", copy);
            BufferedImage marker = new BufferedImage(1, 9, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 9; y++) marker.setRGB(0, y, 0x01FFFFFF);
            PolymerGuiGlyphs.fontPng(builder, "clipboard_marker", marker);
            JsonArray artProviders = new JsonArray();
            artProviders.add(PolymerGuiGlyphs.bitmap("create:font/clipboard_art.png", ART, 7, "\uE000"));
            artProviders.add(PolymerGuiGlyphs.bitmap("create:font/clipboard_marker.png", 9, 7, MARKER));
            PolymerGuiGlyphs.writeFont(builder, "clipboard_art", artProviders);

            BufferedImage widgets = image(builder, "assets/create/textures/gui/widgets.png");
            BufferedImage icons = image(builder, "assets/create/textures/gui/icons.png");
            PolymerGuiGlyphs.fontPng(builder, "clipboard_box", box());
            PolymerGuiGlyphs.fontPng(builder, "clipboard_tick", tick());
            PolymerGuiGlyphs.fontPng(builder, "clipboard_address", widgets.getSubimage(116, 7, 8, 8));
            PolymerGuiGlyphs.fontPng(builder, "clipboard_address_inactive", widgets.getSubimage(125, 7, 8, 8));
            PolymerGuiGlyphs.fontPng(builder, "clipboard_clear", iconButton(widgets, icons, 4, 7));
            PolymerGuiGlyphs.fontPng(builder, "clipboard_close", iconButton(widgets, icons, 4, 0));
            BufferedImage forward = image(builder, "assets/minecraft/textures/gui/sprites/widget/page_forward.png");
            BufferedImage backward = image(builder, "assets/minecraft/textures/gui/sprites/widget/page_backward.png");
            PolymerGuiGlyphs.fontPng(builder, "clipboard_forward", fullAdvance(forward));
            PolymerGuiGlyphs.fontPng(builder, "clipboard_backward", fullAdvance(backward));

            JsonArray asciiRows = PolymerArtKit.asciiRows(builder);
            for (int row = MIN_ROW; row <= MAX_ROW; row++) {
                int ascent = 7 - row;
                JsonArray providers = new JsonArray();
                JsonObject space = new JsonObject();
                space.addProperty("type", "space");
                JsonObject advances = new JsonObject();
                advances.addProperty(" ", 4);
                space.add("advances", advances);
                providers.add(space);
                JsonObject ascii = PolymerGuiGlyphs.bitmap("minecraft:font/ascii.png", 8, ascent, null);
                ascii.add("chars", asciiRows.deepCopy());
                providers.add(ascii);
                providers.add(PolymerGuiGlyphs.bitmap("create:font/clipboard_box.png", 8, ascent, BOX_CHAR));
                providers.add(PolymerGuiGlyphs.bitmap("create:font/clipboard_tick.png", 8, ascent, TICK));
                providers.add(PolymerGuiGlyphs.bitmap("create:font/clipboard_address.png", 8, ascent, ADDRESS));
                providers.add(PolymerGuiGlyphs.bitmap(
                        "create:font/clipboard_address_inactive.png", 8, ascent, ADDRESS_INACTIVE));
                providers.add(PolymerGuiGlyphs.bitmap("create:font/clipboard_clear.png", 18, ascent, CLEAR));
                providers.add(PolymerGuiGlyphs.bitmap("create:font/clipboard_close.png", 18, ascent, CLOSE));
                providers.add(PolymerGuiGlyphs.bitmap(
                        "create:font/clipboard_forward.png", forward.getHeight(), ascent, FORWARD));
                providers.add(PolymerGuiGlyphs.bitmap(
                        "create:font/clipboard_backward.png", backward.getHeight(), ascent, BACKWARD));
                PolymerGuiGlyphs.writeFont(builder, "clipboard_row_" + row, providers);
            }
        } catch (IOException | RuntimeException exception) {
            Create.LOGGER.warn("Clipboard screen art unavailable", exception);
        }
    }

    private static BufferedImage iconButton(BufferedImage widgets, BufferedImage icons, int column, int row) {
        BufferedImage button = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
        var g = button.createGraphics();
        g.drawImage(widgets.getSubimage(0, 0, 18, 18), 0, 0, null);
        g.drawImage(icons.getSubimage(column * 16, row * 16, 16, 16), 1, 1, null);
        g.dispose();
        return button;
    }

    private static BufferedImage fullAdvance(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        copy.createGraphics().drawImage(source, 0, 0, null);
        if ((copy.getRGB(copy.getWidth() - 1, 0) >>> 24) == 0) copy.setRGB(copy.getWidth() - 1, 0, 0x01FFFFFF);
        return copy;
    }

    private static BufferedImage box() {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i <= 5; i++) {
            image.setRGB(i, 1, -1);
            image.setRGB(i, 6, -1);
            image.setRGB(0, 1 + i, -1);
            image.setRGB(5, 1 + i, -1);
        }
        return image;
    }

    private static BufferedImage tick() {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        int[][] pixels = {
            {0, 4}, {1, 5}, {2, 6}, {2, 5}, {3, 4}, {4, 3}, {5, 2}, {6, 1}, {1, 4}, {3, 5}, {4, 4}, {5, 3}, {6, 2}
        };
        for (int[] p : pixels) image.setRGB(p[0], p[1], -1);
        return image;
    }
}
