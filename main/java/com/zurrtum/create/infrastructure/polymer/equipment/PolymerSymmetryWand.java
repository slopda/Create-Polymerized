package com.zurrtum.create.infrastructure.polymer.equipment;

import com.zurrtum.create.AllHandle;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.content.equipment.symmetryWand.SymmetryWandItem;
import com.zurrtum.create.content.equipment.symmetryWand.mirror.CrossPlaneMirror;
import com.zurrtum.create.content.equipment.symmetryWand.mirror.EmptyMirror;
import com.zurrtum.create.content.equipment.symmetryWand.mirror.PlaneMirror;
import com.zurrtum.create.content.equipment.symmetryWand.mirror.TriplePlaneMirror;
import com.zurrtum.create.infrastructure.component.SymmetryMirror;
import com.zurrtum.create.infrastructure.packet.c2s.ConfigureSymmetryWandPacket;
import com.zurrtum.create.infrastructure.polymer.AdapterHolder;
import com.zurrtum.create.infrastructure.polymer.PolymerDisplays;
import com.zurrtum.create.infrastructure.polymer.PolymerFrogArt;
import com.zurrtum.create.infrastructure.polymer.PolymerInventorySync;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerArtKit;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerDialogForms;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerDialogSessions;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerGuiControls;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerGuiLifecycle;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerGuiTextures;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerSchematicGuis;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerGogglesOverlay;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class PolymerSymmetryWand extends SimpleGui {
    private static final int ART_X = -6, ART_Y = -3, ART_W = 188, ART_H = 101, ROWS = 5;
    private static final int FOREGROUND_SLOT = 4, CONFIRM = 35;
    private static final String[] TYPES = {
        "create.symmetry.mirror.plane", "create.symmetry.mirror.doublePlane", "create.symmetry.mirror.triplePlane"
    };
    private static final String[][] ALIGN = {
        {"create.orientation.alongZ", "create.orientation.alongX"},
        {"create.orientation.orthogonal", "create.orientation.diagonal"},
        {"create.orientation.horizontal"}
    };
    private static final Identifier[] MODELS = {
        PolymerBlockParts.model("symmetry_effect/plane"),
        PolymerBlockParts.model("symmetry_effect/crossplane"),
        PolymerBlockParts.model("symmetry_effect/tripleplane")
    };
    private static final Map<Integer, String> CONTROLS = new HashMap<>();
    private static final Map<UUID, Map<String, Mirror>> MIRRORS = new HashMap<>();

    static {
        bind(45, 21, 109, "type");
        bind(45, 43, 109, "align");
        CONTROLS.put(CONFIRM, "confirm");
    }

    private final InteractionHand hand;
    private final ItemStack wand;
    private SymmetryMirror mirror;
    private final PolymerGuiLifecycle lifecycle = new PolymerGuiLifecycle(10);

    private PolymerSymmetryWand(ServerPlayer player, InteractionHand hand, ItemStack wand) {
        super(MenuType.GENERIC_9x5, player, false);
        this.hand = hand;
        this.wand = wand;
        mirror = SymmetryWandItem.getMirror(wand);
        if (mirror instanceof EmptyMirror) mirror = new PlaneMirror(Vec3.ZERO);
        PolymerGuiControls.configure(this, PolymerFrogArt.title("symmetry"), true);
        refresh();
    }

    public static void open(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(AllItems.WAND_OF_SYMMETRY) || player.isSpectator()) return;
        new PolymerSymmetryWand(player, hand, stack).open();
    }

    private static final Identifier DIALOG_ACTION = CreateIds.id("polymer_symmetry_wand");
    private static final Map<UUID, DialogSession> DIALOGS = PolymerDialogSessions.states();

    private static final class DialogSession {
        final InteractionHand hand;
        final ItemStack wand;
        final String token;
        SymmetryMirror mirror;
        int revision;

        DialogSession(InteractionHand hand, ItemStack wand, SymmetryMirror mirror, String token) {
            this.hand = hand;
            this.wand = wand;
            this.mirror = mirror;
            this.token = token;
        }

        int type() {
            return mirror instanceof TriplePlaneMirror ? 2 : mirror instanceof CrossPlaneMirror ? 1 : 0;
        }
    }

    private static Holder<Dialog> dialog(DialogSession s) {
        int type = s.type();
        String[] options = ALIGN[type];
        Component typeLabel = Component.literal(PolymerSchematicGuis.lang("create.gui.symmetryWand.mirrorType") + ": "
                + PolymerSchematicGuis.lang(TYPES[type]));
        Component alignLabel = Component.literal(PolymerSchematicGuis.lang("create.gui.symmetryWand.orientation") + ": "
                + PolymerSchematicGuis.lang(options[Math.floorMod(s.mirror.getOrientationIndex(), options.length)]));
        var buttons = new ArrayList<ActionButton>();
        buttons.add(button(s, typeLabel, "type"));
        if (options.length > 1) buttons.add(button(s, alignLabel, "align"));
        else buttons.add(new ActionButton(new CommonButtonData(alignLabel, 200), Optional.empty()));
        var common = new CommonDialogData(
                s.wand.getHoverName(), Optional.empty(), true, false, DialogAction.NONE, List.of(), List.of());
        return Holder.direct(new MultiActionDialog(
                common,
                buttons,
                Optional.of(button(
                        s,
                        PolymerGuiTextures.actionLabel(PolymerSchematicGuis.lang("create.action.confirm")),
                        "confirm")),
                1));
    }

    private static ActionButton button(DialogSession s, Component label, String operation) {
        CompoundTag tag = new CompoundTag();
        tag.putString("session", s.token);
        tag.putInt("revision", s.revision);
        tag.putString("operation", operation);
        return new ActionButton(
                new CommonButtonData(label, 200), Optional.of(new CustomAll(DIALOG_ACTION, Optional.of(tag))));
    }

    public static boolean handle(ServerPlayer player, Identifier id, Optional<Tag> payload) {
        if (!DIALOG_ACTION.equals(id)) return false;
        DialogSession s = DIALOGS.get(player.getUUID());
        CompoundTag tag = PolymerDialogForms.payload(payload).orElse(null);
        if (s == null
                || tag == null
                || !tag.getString("session").orElse("").equals(s.token)
                || tag.getInt("revision").orElse(-1) != s.revision) return true;
        if (player.getItemInHand(s.hand) != s.wand || player.isSpectator()) {
            DIALOGS.remove(player.getUUID());
            NativePresentationPolicy.sendPresentation(player, ClientboundClearDialogPacket.INSTANCE);
            return true;
        }
        switch (tag.getString("operation").orElse("")) {
            case "type" -> {
                Vec3 pos = s.mirror.getPosition();
                s.mirror = switch (Math.floorMod(s.type() + 1, 3)) {
                    case 1 -> new CrossPlaneMirror(pos);
                    case 2 -> new TriplePlaneMirror(pos);
                    default -> new PlaneMirror(pos);
                };
            }
            case "align" ->
                s.mirror.setOrientation(Math.floorMod(s.mirror.getOrientationIndex() + 1, ALIGN[s.type()].length));
            case "confirm" -> {
                AllHandle.onConfigureSymmetryWand(player.connection, new ConfigureSymmetryWandPacket(s.hand, s.mirror));
                DIALOGS.remove(player.getUUID());
                NativePresentationPolicy.sendPresentation(player, ClientboundClearDialogPacket.INSTANCE);
                return true;
            }
            default -> {
                return true;
            }
        }
        AllHandle.onConfigureSymmetryWand(player.connection, new ConfigureSymmetryWandPacket(s.hand, s.mirror));
        s.revision++;
        PolymerDialogForms.open(player, dialog(s));
        return true;
    }

    private static void bind(int x, int y, int w, String control) {
        for (int slot = 0; slot < 9 * ROWS; slot++) {
            int cx = PolymerFrogArt.slotX(slot) + 8 - ART_X, cy = PolymerFrogArt.slotY(slot) + 8 - ART_Y;
            if (cx >= x && cx < x + w && cy >= y && cy < y + 18) CONTROLS.put(slot, control);
        }
    }

    @Override
    public boolean stillValid() {
        return player.getItemInHand(hand) == wand && !player.isSpectator();
    }

    private int type() {
        return mirror instanceof TriplePlaneMirror ? 2 : mirror instanceof CrossPlaneMirror ? 1 : 0;
    }

    private void refresh() {
        ItemStack foreground = new ItemStack(Items.PAPER);
        foreground.set(DataComponents.ITEM_MODEL, CreateIds.id("ui/symmetry_foreground"));
        foreground.set(
                DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(
                        List.of(),
                        List.of(),
                        List.of(Integer.toString(type()), type() + "_" + mirror.getOrientationIndex()),
                        List.of()));
        for (int slot = 0; slot < getSize(); slot++) {
            String control = CONTROLS.get(slot);
            ItemStack stack;
            if (slot == FOREGROUND_SLOT) stack = foreground.copy();
            else if (slot == CONFIRM) stack = PolymerGuiTextures.icon("symmetry_confirm");
            else {
                stack = new ItemStack(Items.PAPER);
                stack.set(DataComponents.ITEM_MODEL, CreateIds.id("ui/symmetry_empty"));
            }
            if (control == null) {
                stack.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new LinkedHashSet<>()));
                setSlot(slot, PolymerGuiControls.builder(stack).build());
                continue;
            }
            GuiElementBuilder element = PolymerGuiControls.builder(stack);
            switch (control) {
                case "type" -> {
                    element.setName(
                            PolymerGuiControls.title(PolymerSchematicGuis.lang("create.gui.symmetryWand.mirrorType")));
                    for (int i = 0; i < TYPES.length; i++)
                        element.addLoreLine(
                                PolymerGuiControls.option(PolymerSchematicGuis.lang(TYPES[i]), i == type()));
                    element.addLoreLine(PolymerGuiControls.hint("Left-click: next   Right-click: previous"));
                }
                case "align" -> {
                    element.setName(
                            PolymerGuiControls.title(PolymerSchematicGuis.lang("create.gui.symmetryWand.orientation")));
                    String[] options = ALIGN[type()];
                    for (int i = 0; i < options.length; i++)
                        element.addLoreLine(PolymerGuiControls.option(
                                PolymerSchematicGuis.lang(options[i]), i == mirror.getOrientationIndex()));
                    element.addLoreLine(PolymerGuiControls.hint("Left-click: next   Right-click: previous"));
                }
                default ->
                    element.setName(PolymerGuiControls.title(PolymerSchematicGuis.lang("create.action.confirm")));
            }
            setSlot(slot, element.build());
        }
    }

    @Override
    public boolean onAnyClick(int slot, ClickType click, ContainerInput action) {
        lifecycle.requestSync();
        if (!stillValid()) {
            close();
            return false;
        }
        if (!PolymerGuiControls.primaryClick(click, action)) return false;
        String control = CONTROLS.get(slot);
        if (control == null) return false;
        int step = click.isLeft ? 1 : -1;
        switch (control) {
            case "type" -> {
                Vec3 pos = mirror.getPosition();
                mirror = switch (Math.floorMod(type() + step, 3)) {
                    case 1 -> new CrossPlaneMirror(pos);
                    case 2 -> new TriplePlaneMirror(pos);
                    default -> new PlaneMirror(pos);
                };
            }
            case "align" ->
                mirror.setOrientation(Math.floorMod(mirror.getOrientationIndex() + step, ALIGN[type()].length));
            default -> {
                close();
                return false;
            }
        }
        refresh();
        return false;
    }

    @Override
    public void onTick() {
        lifecycle.tickWhileValid(stillValid(), this::close, wrappedMenu);
    }

    @Override
    public void onRemoved() {
        lifecycle.close(
                () -> {
                    if (player.getItemInHand(hand) == wand)
                        AllHandle.onConfigureSymmetryWand(
                                player.connection, new ConfigureSymmetryWandPacket(hand, mirror));
                },
                () -> PolymerInventorySync.afterMenu(player));
    }

    private static final class Mirror {
        final ServerLevel level;
        final BlockPos pos;
        final ElementHolder holder;
        final ItemDisplayElement element;

        Mirror(UUID owner, ServerLevel level, BlockPos pos, int type) {
            this.level = level;
            this.pos = pos;
            holder = new AdapterHolder() {
                @Override
                public boolean startWatching(ServerGamePacketListenerImpl listener) {
                    return listener.player.getUUID().equals(owner) && super.startWatching(listener);
                }
            };
            element = PolymerDisplays.item(PolymerBlockParts.displayItem(MODELS[type]), ItemDisplayContext.NONE);
            element.setInterpolationDuration(2);
            element.setBrightness(new Brightness(
                    level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.SKY, pos)));
            holder.addElement(element);
            ChunkAttachment.ofTicking(holder, level, pos);
        }
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : NativePresentationPolicy.presentationPlayers(server.getPlayerList()))
                tickMirrors(player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clear(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPING.register(
                server -> List.copyOf(MIRRORS.keySet()).forEach(PolymerSymmetryWand::clear));
    }

    private static void clear(UUID player) {
        Map<String, Mirror> mirrors = MIRRORS.remove(player);
        if (mirrors != null) mirrors.values().forEach(m -> m.holder.destroy());
    }

    private static void tickMirrors(ServerPlayer player) {
        Map<String, Mirror> shown = MIRRORS.get(player.getUUID());
        Map<String, SymmetryMirror> wanted = new HashMap<>();
        Inventory inventory = player.getInventory();
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(AllItems.WAND_OF_SYMMETRY) || !SymmetryWandItem.isEnabled(stack)) continue;
            SymmetryMirror mirror = SymmetryWandItem.getMirror(stack);
            int type = mirror instanceof TriplePlaneMirror
                    ? 2
                    : mirror instanceof CrossPlaneMirror ? 1 : mirror instanceof PlaneMirror ? 0 : -1;
            if (type < 0) continue;
            wanted.put(
                    BlockPos.containing(mirror.getPosition()).asLong() + ":" + type + ":"
                            + mirror.getOrientationIndex(),
                    mirror);
        }
        if (wanted.isEmpty() && shown == null) return;
        if (shown == null) MIRRORS.put(player.getUUID(), shown = new HashMap<>());
        ServerLevel level = player.level();
        shown.entrySet().removeIf(entry -> {
            if (wanted.containsKey(entry.getKey()) && entry.getValue().level == level) return false;
            entry.getValue().holder.destroy();
            return true;
        });
        long time = level.getGameTime();
        for (Map.Entry<String, SymmetryMirror> entry : wanted.entrySet()) {
            SymmetryMirror mirror = entry.getValue();
            BlockPos pos = BlockPos.containing(mirror.getPosition());
            int type = mirror instanceof TriplePlaneMirror ? 2 : mirror instanceof CrossPlaneMirror ? 1 : 0;
            Mirror display = shown.computeIfAbsent(entry.getKey(), k -> new Mirror(player.getUUID(), level, pos, type));
            float yShift = Mth.sin(time / 16f) / 5f;
            Matrix4f local = new Matrix4f().translate(0, yShift + 0.2f, 0);
            if (mirror instanceof PlaneMirror && mirror.getOrientationIndex() != 0)
                local.mul(PolymerBlockParts.rotateCentered(new Quaternionf().rotationY(Mth.HALF_PI)));
            else if (mirror instanceof CrossPlaneMirror && mirror.getOrientationIndex() != 0)
                local.mul(PolymerBlockParts.rotateCentered(new Quaternionf().rotationY(Mth.PI / 4)));
            display.element.setTransformation(PolymerBlockParts.displayTransform(local));
            display.element.startInterpolationIfDirty();
            if (time % 10 == 0) {
                var random = level.getRandom();
                Vec3 at = mirror.getPosition()
                        .add(0.5 + (random.nextDouble() - 0.5) * 0.3, 0.25, 0.5 + (random.nextDouble() - 0.5) * 0.3);
                level.sendParticles(
                        player,
                        ParticleTypes.END_ROD,
                        false,
                        false,
                        at.x,
                        at.y,
                        at.z,
                        0,
                        0,
                        random.nextDouble() / 8,
                        0,
                        1);
            }
        }
        if (shown.isEmpty()) MIRRORS.remove(player.getUUID());
    }

    public static void addAssets(ResourcePackBuilder builder) {
        try {
            Map<Integer, BufferedImage> glyphs = PolymerArtKit.asciiGlyphs(builder);
            BufferedImage art = PolymerArtKit.copy(
                    image(builder, "assets/create/textures/gui/curiosities.png").getSubimage(0, 131, ART_W, ART_H));
            String title = PolymerSchematicGuis.lang("item.create.wand_of_symmetry");
            PolymerArtKit.rawText(
                    art, glyphs, (ART_W - PolymerGogglesOverlay.width(title)) / 2, 4, title, 0x592424, false);
            PolymerFrogArt.panel(builder, "symmetry", art, ART_X, ART_Y, ROWS);
            PolymerFrogArt.item(
                    builder,
                    "symmetry_confirm",
                    PolymerFrogArt.button(builder, 0, 0, 1),
                    CONFIRM,
                    ART_W - 33 + ART_X,
                    ART_H - 24 + ART_Y);
            var kit = new PolymerArtKit(builder, "symmetry", 88, 26);
            kit.allocate(2);
            Map<String, BufferedImage> types = new LinkedHashMap<>(), aligns = new LinkedHashMap<>();
            for (int t = 0; t < TYPES.length; t++) {
                types.put(Integer.toString(t), label(glyphs, PolymerSchematicGuis.lang(TYPES[t])));
                for (int a = 0; a < ALIGN[t].length; a++)
                    aligns.put(t + "_" + a, label(glyphs, PolymerSchematicGuis.lang(ALIGN[t][a])));
            }
            kit.layer(0, types, 51 + ART_X, 28 + ART_Y, .01f);
            kit.layer(1, aligns, 51 + ART_X, 50 + ART_Y, .01f);
            kit.finish("symmetry_foreground");
            PolymerArtKit.empty(builder, "symmetry_empty");
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Cannot build Wand of Symmetry UI source art", exception);
        }
    }

    private static BufferedImage label(Map<Integer, BufferedImage> glyphs, String text) {
        BufferedImage strip = new BufferedImage(128, 9, BufferedImage.TYPE_INT_ARGB);
        PolymerArtKit.rawText(strip, glyphs, 0, 0, text, 0xFFFFFF, true);
        return strip;
    }

    private static BufferedImage image(ResourcePackBuilder builder, String path) throws IOException {
        return PolymerArtKit.image(builder, path);
    }
}
