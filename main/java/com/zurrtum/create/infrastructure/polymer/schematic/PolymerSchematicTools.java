package com.zurrtum.create.infrastructure.polymer.schematic;

import com.zurrtum.create.AllDataComponents;
import com.zurrtum.create.AllHandle;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.content.schematics.SchematicPrinter;
import com.zurrtum.create.infrastructure.packet.c2s.SchematicPlacePacket;
import com.zurrtum.create.infrastructure.packet.c2s.SchematicSyncPacket;
import com.zurrtum.create.infrastructure.polymer.AdapterHolder;
import com.zurrtum.create.infrastructure.polymer.AdapterInteractions;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerDialogForms;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerDialogSessions;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class PolymerSchematicTools implements ModInitializer {
    private static final Identifier ACTION = Identifier.fromNamespaceAndPath("createpoly", "schematic_tools");
    private static final Map<UUID, Session> SESSIONS = PolymerDialogSessions.states();
    private static final int MAX_VOLUME = 65_536, MAX_PREVIEW = 2_048;

    private enum Tool {
        POSITION,
        MOVE,
        MOVE_Y,
        ROTATE,
        FLIP,
        PRINT;
    }

    private static final Map<UUID, Tool> TOOLS = new HashMap<>();
    private static final Map<UUID, Integer> EXPECTED_SWINGS = new HashMap<>();

    private static final Map<UUID, Long> LAST_LEFT = new HashMap<>();

    private static final class Session {
        final ServerLevel level;
        final int slot;
        ItemStack expected;
        String token;
        int revision;
        long expires;
        ElementHolder preview;
        Vec3 previewCenter;

        List<Runnable> settle = new ArrayList<>();

        Session(ServerPlayer p) {
            level = p.level();
            slot = p.getInventory().getSelectedSlot();
            expected = p.getMainHandItem().copy();
        }
    }

    @Override
    public void onInitialize() {
        var phase = Identifier.fromNamespaceAndPath("createpoly", "schematic_tools");
        UseBlockCallback.EVENT.addPhaseOrdering(phase, Event.DEFAULT_PHASE);
        AdapterInteractions.useBlock(
                phase, (p, l, h, hit) -> p instanceof ServerPlayer owner ? use(owner, h) : InteractionResult.PASS);
        AdapterInteractions.attackBlock((p, l, h, pos, dir) ->
                p instanceof ServerPlayer owner && h == InteractionHand.MAIN_HAND && leftClick(owner)
                        ? InteractionResult.SUCCESS
                        : InteractionResult.PASS);
        UseItemCallback.EVENT.addPhaseOrdering(phase, Event.DEFAULT_PHASE);
        AdapterInteractions.useItem(
                phase, (p, l, h) -> p instanceof ServerPlayer owner ? use(owner, h) : InteractionResult.PASS);
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(PolymerSchematicHud::addAssets);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var p : NativePresentationPolicy.presentationPlayers(server.getPlayerList())) {
                Session s = SESSIONS.get(p.getUUID());
                if (s != null && (!valid(p, s) || System.nanoTime() > s.expires)) clear(p.getUUID());
                else if (s != null && !s.settle.isEmpty()) {
                    var steps = List.copyOf(s.settle);
                    s.settle.clear();
                    steps.forEach(Runnable::run);
                }
                if (deployed(p)) PolymerSchematicHud.show(p, tool(p).ordinal(), p.isCreative(), false);
                else {
                    PolymerSchematicHud.hide(p);
                    EXPECTED_SWINGS.remove(p.getUUID());
                    LAST_LEFT.remove(p.getUUID());
                }
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            clear(handler.player.getUUID());
            TOOLS.remove(handler.player.getUUID());
            EXPECTED_SWINGS.remove(handler.player.getUUID());
            LAST_LEFT.remove(handler.player.getUUID());
            PolymerSchematicHud.forget(handler.player.getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (var s : SESSIONS.values()) if (s.preview != null) s.preview.destroy();
            SESSIONS.clear();
        });
    }

    private static boolean eligible(ServerPlayer p) {
        return !PolymerNativeClients.isNative(p)
                && p.isAlive()
                && !p.isSpectator()
                && p.mayBuild()
                && p.getMainHandItem().is(AllItems.SCHEMATIC);
    }

    private static boolean valid(ServerPlayer p, Session s) {
        return eligible(p)
                && p.level() == s.level
                && p.getInventory().getSelectedSlot() == s.slot
                && ItemStack.matches(p.getMainHandItem(), s.expected);
    }

    private static InteractionResult use(ServerPlayer p, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !eligible(p)) return InteractionResult.PASS;
        expectSwing(p);
        if (deployed(p)) {
            apply(p, tool(p), 1);
            return InteractionResult.SUCCESS;
        }
        Session s = session(p);
        BlockPos target = deployTarget(p, s.expected);
        if (target == null) p.sendOverlayMessage(Component.literal("Aim at a loaded block to deploy the schematic."));
        else if (sync(
                p,
                s,
                target,
                s.expected.getOrDefault(AllDataComponents.SCHEMATIC_ROTATION, Rotation.NONE),
                s.expected.getOrDefault(AllDataComponents.SCHEMATIC_MIRROR, Mirror.NONE))) {
            preview(p, s, false, 0);
            hint(p, tool(p));
        }
        return InteractionResult.SUCCESS;
    }

    private static Session session(ServerPlayer p) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || !valid(p, s)) {
            clear(p.getUUID());
            s = new Session(p);
            SESSIONS.put(p.getUUID(), s);
        }
        s.expires = System.nanoTime() + 120_000_000_000L;
        return s;
    }

    public static boolean open(ServerPlayer p) {
        if (!eligible(p)) return false;
        Session s = session(p);
        s.token = UUID.randomUUID().toString();
        ItemStack stack = s.expected;
        BlockPos a = stack.getOrDefault(AllDataComponents.SCHEMATIC_ANCHOR, p.blockPosition());
        var inputs = List.of(coordinate("x", a.getX()), coordinate("y", a.getY()), coordinate("z", a.getZ()));
        var common = new CommonDialogData(
                Component.literal("Schematic: " + stack.getOrDefault(AllDataComponents.SCHEMATIC_FILE, "")),
                Optional.empty(),
                true,
                false,
                DialogAction.NONE,
                List.of(new PlainMessage(
                        Component.literal(
                                "Rotation: " + stack.getOrDefault(AllDataComponents.SCHEMATIC_ROTATION, Rotation.NONE)
                                        + "; Mirror: "
                                        + stack.getOrDefault(AllDataComponents.SCHEMATIC_MIRROR, Mirror.NONE)
                                        + ". Apply coordinates, rotate, mirror, or deploy at the aimed block. Once deployed, Swap Hands or Shift + scroll selects a tool. Right-click moves forward/up or rotates clockwise; left-click moves back/down or rotates counter-clockwise. Shift only focuses the tool menu. Preview blocks are private and have no collision."),
                        280)),
                inputs);
        PolymerDialogForms.open(
                p,
                Holder.direct(new MultiActionDialog(
                        common,
                        List.of(
                                button(s, "Apply coordinates", "apply"),
                                button(s, "Deploy at target", "deploy"),
                                button(s, "Rotate clockwise", "rotate"),
                                button(s, "Cycle mirror", "mirror"),
                                button(s, "Move up", "up"),
                                button(s, "Move down", "down"),
                                button(s, "Move north", "north"),
                                button(s, "Move south", "south"),
                                button(s, "Move west", "west"),
                                button(s, "Move east", "east"),
                                button(s, "Hide preview", "hide")),
                        Optional.of(button(s, "Apply and close", "done")),
                        2)));
        return true;
    }

    private static boolean deployed(ServerPlayer p) {
        return eligible(p) && p.getMainHandItem().getOrDefault(AllDataComponents.SCHEMATIC_DEPLOYED, false);
    }

    private static Tool tool(ServerPlayer p) {
        Tool tool = TOOLS.getOrDefault(p.getUUID(), Tool.POSITION);
        return tool == Tool.PRINT && !p.isCreative() ? Tool.POSITION : tool;
    }

    private static Tool step(ServerPlayer p, int delta) {
        Tool[] all = Tool.values();
        int n = p.isCreative() ? all.length : all.length - 1;
        Tool next = all[Math.floorMod(tool(p).ordinal() + delta, n)];
        TOOLS.put(p.getUUID(), next);
        return next;
    }

    public static boolean leftClick(ServerPlayer p) {
        if (!deployed(p)) return false;
        expectSwing(p);
        return stepLeft(p);
    }

    private static void expectSwing(ServerPlayer p) {
        EXPECTED_SWINGS.merge(p.getUUID(), 1, (a, b) -> Math.min(32, a + b));
    }

    public static boolean swing(ServerPlayer p) {
        if (!deployed(p)) return false;
        Integer pending = EXPECTED_SWINGS.get(p.getUUID());
        if (pending != null) {
            if (pending <= 1) EXPECTED_SWINGS.remove(p.getUUID());
            else EXPECTED_SWINGS.put(p.getUUID(), pending - 1);
            return true;
        }
        return stepLeft(p);
    }

    private static boolean stepLeft(ServerPlayer p) {
        long now = p.level().getGameTime();
        Long left = LAST_LEFT.get(p.getUUID());
        if (left != null && left == now) return true;
        LAST_LEFT.put(p.getUUID(), now);
        Tool t = tool(p);
        if (t != Tool.PRINT) apply(p, t, -1);
        return true;
    }

    public static boolean cycle(ServerPlayer p) {
        if (!deployed(p)) return false;
        hint(p, step(p, 1));
        return true;
    }

    private static void hint(ServerPlayer p, Tool tool) {
        PolymerSchematicHud.show(p, tool.ordinal(), p.isCreative(), true);
    }

    public static boolean scroll(ServerPlayer p, int slot) {
        if (!deployed(p) || !p.isShiftKeyDown() || slot < 0 || slot > 8) return false;
        int from = p.getInventory().getSelectedSlot(), offset = Math.floorMod(slot - from + 4, 9) - 4;
        if (offset == 0) return false;
        NativePresentationPolicy.sendPresentation(p, new ClientboundSetHeldSlotPacket(from));
        hint(p, step(p, offset > 0 ? 1 : -1));
        return true;
    }

    private static void apply(ServerPlayer p, Tool tool, int delta) {
        Session s = session(p);
        ItemStack stack = s.expected;
        BlockPos anchor = stack.get(AllDataComponents.SCHEMATIC_ANCHOR);
        Rotation rotation = stack.getOrDefault(AllDataComponents.SCHEMATIC_ROTATION, Rotation.NONE);
        Mirror mirror = stack.getOrDefault(AllDataComponents.SCHEMATIC_MIRROR, Mirror.NONE);
        switch (tool) {
            case POSITION -> anchor = deployTarget(p, stack);
            case MOVE -> {
                if (anchor != null) anchor = anchor.relative(p.getDirection(), delta);
            }
            case MOVE_Y -> {
                if (anchor != null) anchor = anchor.above(delta);
            }
            case ROTATE ->
                rotation = rotation.getRotated(delta > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90);
            case FLIP -> {
                Mirror axis = p.getDirection().getAxis() == Direction.Axis.X ? Mirror.FRONT_BACK : Mirror.LEFT_RIGHT;
                if (mirror == Mirror.NONE) mirror = axis;
                else if (mirror == axis) mirror = Mirror.NONE;
                else {
                    mirror = Mirror.NONE;
                    rotation = rotation.getRotated(Rotation.CLOCKWISE_180);
                }
            }
            case PRINT -> {
                if (p.isCreative()) {
                    AllHandle.onSchematicPlace(
                            p.connection,
                            new SchematicPlacePacket(p.getMainHandItem().copy()));
                    clear(p.getUUID());
                }
                return;
            }
        }
        if (anchor == null) {
            p.sendOverlayMessage(Component.literal("Aim at a loaded block."));
            return;
        }
        if (!sync(p, s, anchor, rotation, mirror)) {
            p.sendOverlayMessage(Component.literal("Schematic not changed: invalid or unavailable region."));
            return;
        }
        preview(p, s, false, 0);
        hint(p, tool);
    }

    private static Input coordinate(String axis, int value) {
        return PolymerDialogForms.input(
                axis,
                PolymerDialogForms.textControl(
                        90,
                        Component.literal(axis.toUpperCase(Locale.ROOT)),
                        true,
                        Integer.toString(value),
                        10,
                        Optional.empty()));
    }

    private static ActionButton button(Session s, String label, String op) {
        var tag = new CompoundTag();
        tag.putString("token", s.token);
        tag.putInt("revision", s.revision);
        tag.putString("operation", op);
        return new ActionButton(
                new CommonButtonData(Component.literal(label), 140),
                Optional.of(new CustomAll(ACTION, Optional.of(tag))));
    }

    public static boolean action(ServerGamePacketListenerImpl listener, ServerboundCustomClickActionPacket packet) {
        if (!PolymerDialogForms.validSubmission(listener.player, packet.id(), packet.payload(), ACTION)) return true;
        if (!ACTION.equals(packet.id())) return false;
        ServerPlayer p = listener.player;
        Session s = SESSIONS.get(p.getUUID());
        var tag = PolymerDialogForms.payload(packet.payload()).orElse(null);
        if (s == null
                || tag == null
                || !valid(p, s)
                || s.token == null
                || !s.token.equals(tag.getString("token").orElse(""))
                || s.revision != tag.getInt("revision").orElse(-1)
                || System.nanoTime() > s.expires) return true;
        String op = tag.getString("operation").orElse("");
        if (op.equals("hide")) {
            if (s.preview != null) s.preview.destroy();
            s.preview = null;
            s.token = null;
            NativePresentationPolicy.sendPresentation(p, ClientboundClearDialogPacket.INSTANCE);
            return true;
        }
        BlockPos anchor;
        try {
            String x = tag.getString("x").orElse(""),
                    y = tag.getString("y").orElse(""),
                    z = tag.getString("z").orElse("");
            if (!x.matches("-?[0-9]{1,8}") || !y.matches("-?[0-9]{1,8}") || !z.matches("-?[0-9]{1,8}")) return true;
            anchor = new BlockPos(Integer.parseInt(x), Integer.parseInt(y), Integer.parseInt(z));
        } catch (NumberFormatException error) {
            return true;
        }
        Rotation rotation = s.expected.getOrDefault(AllDataComponents.SCHEMATIC_ROTATION, Rotation.NONE);
        Mirror mirror = s.expected.getOrDefault(AllDataComponents.SCHEMATIC_MIRROR, Mirror.NONE);
        switch (op) {
            case "apply", "done" -> {}
            case "rotate" -> rotation = rotation.getRotated(Rotation.CLOCKWISE_90);
            case "mirror" -> mirror = Mirror.values()[(mirror.ordinal() + 1) % Mirror.values().length];
            case "deploy" -> {
                anchor = deployTarget(p, s.expected);
                if (anchor == null) return true;
            }
            case "up" -> anchor = anchor.above();
            case "down" -> anchor = anchor.below();
            case "north" -> anchor = anchor.north();
            case "south" -> anchor = anchor.south();
            case "west" -> anchor = anchor.west();
            case "east" -> anchor = anchor.east();
            default -> {
                return true;
            }
        }
        if (!sync(p, s, anchor, rotation, mirror)) {
            p.sendOverlayMessage(
                    Component.literal("Schematic settings were not changed: invalid or unavailable region/file."));
            return true;
        }
        preview(p, s, false, 0);
        if (op.equals("done")) {
            s.token = null;
            NativePresentationPolicy.sendPresentation(p, ClientboundClearDialogPacket.INSTANCE);
        } else open(p);
        return true;
    }

    private static BlockPos deployTarget(ServerPlayer p, ItemStack stack) {
        Vec3 eye = p.getEyePosition();
        var hit = new PolymerQuill.LoadedView(p.level())
                .clip(new ClipContext(
                        eye,
                        eye.add(p.getLookAngle().scale(75)),
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        p));
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) return null;
        var size = stack.getOrDefault(AllDataComponents.SCHEMATIC_BOUNDS, Vec3i.ZERO);
        BlockPos target = BlockPos.containing(block.getLocation());
        if (block.getDirection().getAxis().isVertical()
                && !p.level().getBlockState(target).canBeReplaced()) target = target.relative(block.getDirection());
        return PolymerSchematicPlacement.anchorAt(
                target,
                size,
                stack.getOrDefault(AllDataComponents.SCHEMATIC_ROTATION, Rotation.NONE),
                stack.getOrDefault(AllDataComponents.SCHEMATIC_MIRROR, Mirror.NONE));
    }

    private static boolean sync(ServerPlayer p, Session s, BlockPos anchor, Rotation rotation, Mirror mirror) {
        if (!valid(p, s)
                || anchor == null
                || !p.level().getWorldBorder().isWithinBounds(anchor)
                || anchor.getY() < p.level().getMinY()
                || anchor.getY() >= p.level().getMinY() + p.level().getHeight()) return false;
        var size = s.expected.getOrDefault(AllDataComponents.SCHEMATIC_BOUNDS, Vec3i.ZERO);
        long volume = (long) size.getX() * size.getY() * size.getZ();
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0 || volume > MAX_VOLUME) return false;
        ItemStack validated = PolymerSchematicFiles.blueprint(
                p, s.expected.get(AllDataComponents.SCHEMATIC_OWNER), s.expected.get(AllDataComponents.SCHEMATIC_FILE));
        if (validated.isEmpty() || !size.equals(validated.get(AllDataComponents.SCHEMATIC_BOUNDS))) return false;
        AllHandle.onSchematicSync(p.connection, new SchematicSyncPacket(-1, true, anchor, rotation, mirror));
        s.expected = p.getMainHandItem().copy();
        s.revision++;
        s.token = null;
        s.expires = System.nanoTime() + 120_000_000_000L;
        return true;
    }

    private static void preview(ServerPlayer p, Session s, boolean animate, int turns) {
        ElementHolder previous = s.preview;
        Vec3 previousCenter = s.previewCenter;
        s.preview = null;
        s.settle.clear();
        var printer = new SchematicPrinter();
        printer.loadSchematic(s.expected, p.level(), false);
        if (!printer.isLoaded() || printer.isErrored()) return;
        var holder = new AdapterHolder();
        Vec3 origin = Vec3.atLowerCornerOf(s.expected.get(AllDataComponents.SCHEMATIC_ANCHOR));
        new ManualAttachment(holder, p.level(), () -> origin);
        int count = 0, positions = 0;
        while (printer.advanceCurrentPos() && positions++ < MAX_VOLUME) {
            if (printer.getPrintStage() == SchematicPrinter.PrintStage.ENTITIES) continue;
            final int before = holder.getElements().size();
            printer.handleCurrentTarget(
                    (pos, state, be) -> {
                        PolymerSchematicPreview.add(holder, p, origin, pos, state, be);
                    },
                    (pos, entity) -> {});
            if (holder.getElements().size() > before && ++count > MAX_PREVIEW) {
                holder.destroy();
                if (previous != null) previous.destroy();
                s.previewCenter = null;
                p.sendOverlayMessage(
                        Component.literal(
                                "Preview exceeds 2048 blocks; settings are saved. Use a smaller schematic for the block preview."));
                return;
            }
        }
        printer.resetSchematic();
        double minX = Double.MAX_VALUE,
                minY = Double.MAX_VALUE,
                minZ = Double.MAX_VALUE,
                maxX = -Double.MAX_VALUE,
                maxY = -Double.MAX_VALUE,
                maxZ = -Double.MAX_VALUE;
        for (var element : holder.getElements()) {
            if (!(element instanceof DisplayElement display)) continue;
            Vec3 c = origin.add(display.getOffset())
                    .add(display instanceof BlockDisplayElement ? new Vec3(.5, .5, .5) : Vec3.ZERO);
            minX = Math.min(minX, c.x);
            minY = Math.min(minY, c.y);
            minZ = Math.min(minZ, c.z);
            maxX = Math.max(maxX, c.x);
            maxY = Math.max(maxY, c.y);
            maxZ = Math.max(maxZ, c.z);
        }
        Vec3 center =
                minX == Double.MAX_VALUE ? origin : new Vec3((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
        if (animate && previousCenter != null && previous != null) {
            Quaternionf back = new Quaternionf().rotationY((float) (Math.PI / 2 * turns));
            for (var element : holder.getElements()) {
                if (!(element instanceof DisplayElement display)) continue;
                boolean block = display instanceof BlockDisplayElement;
                Vector3f pivot = block ? new Vector3f(.5f, .5f, .5f) : new Vector3f();
                Vec3 now = origin.add(display.getOffset()).add(block ? new Vec3(.5, .5, .5) : Vec3.ZERO);
                Vector3f rel = now.subtract(center).toVector3f();
                Vector3f then = back.transform(new Vector3f(rel)).add(previousCenter.toVector3f());
                Vector3f shift = then.sub(now.toVector3f());
                Quaternionf targetLeft = new Quaternionf(display.getLeftRotation());
                Quaternionf startLeft = new Quaternionf(back).mul(targetLeft);
                Vector3f startTranslation =
                        new Vector3f(shift).add(pivot).sub(new Quaternionf(back).transform(new Vector3f(pivot)));
                display.setTranslation(startTranslation);
                display.setLeftRotation(startLeft);
                display.setInterpolationDuration(0);
                s.settle.add(() -> {
                    display.setInterpolationDuration(6);
                    display.setTranslation(new Vector3f());
                    display.setLeftRotation(targetLeft);
                    display.startInterpolation();
                    display.tick();
                });
            }
        }
        holder.startWatching(p);
        holder.tick();
        s.preview = holder;
        s.previewCenter = center;
        if (previous != null) previous.destroy();
    }

    private static void clear(UUID player) {
        Session s = SESSIONS.remove(player);
        if (s != null) {
            s.settle.clear();
            if (s.preview != null) s.preview.destroy();
        }
    }
}
