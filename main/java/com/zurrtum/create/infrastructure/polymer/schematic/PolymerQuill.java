package com.zurrtum.create.infrastructure.polymer.schematic;

import com.zurrtum.create.AllItems;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.schematics.SchematicExport;
import com.zurrtum.create.foundation.utility.CreatePaths;
import com.zurrtum.create.infrastructure.config.AllConfigs;
import com.zurrtum.create.infrastructure.polymer.AdapterInteractions;
import com.zurrtum.create.infrastructure.polymer.PolymerLoadedBlocks;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBoxOutline;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerQuillScreen;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerSchematicGuis;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerHudRadial;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import java.io.IOException;
import java.nio.file.*;
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
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.*;

public final class PolymerQuill implements ModInitializer {
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();
    private static final int MAX_BLOCKS = 65_536, HELD_USE_TICKS = 5;

    private static final class Selection {
        final ServerLevel level;
        BlockPos first, second;
        int range = 10;
        long lastUse = Long.MIN_VALUE / 2;

        AABB shown;
        ElementHolder outline;
        ElementHolder faceOutline;
        AABB shownFace;

        Selection(ServerLevel level) {
            this.level = level;
        }
    }

    @Override
    public void onInitialize() {
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(
                PolymerSchematicOutline::addAssets);
        var phase = Identifier.fromNamespaceAndPath("createpoly", "quill_selection");
        UseBlockCallback.EVENT.addPhaseOrdering(phase, Event.DEFAULT_PHASE);
        AdapterInteractions.useBlock(
                phase,
                (player, level, hand, hit) ->
                        player instanceof ServerPlayer p && eligible(p, hand) ? use(p) : InteractionResult.PASS);
        UseItemCallback.EVENT.addPhaseOrdering(phase, Event.DEFAULT_PHASE);
        AdapterInteractions.useItem(
                phase,
                (player, level, hand) ->
                        player instanceof ServerPlayer p && eligible(p, hand) ? use(p) : InteractionResult.PASS);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var p : NativePresentationPolicy.onlinePlayers(server.getPlayerList())) tick(p);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clear(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SELECTIONS.values().forEach(s -> {
                if (s.outline != null) s.outline.destroy();
                if (s.faceOutline != null) s.faceOutline.destroy();
            });
            SELECTIONS.clear();
        });
    }

    static boolean eligible(ServerPlayer p, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND
                && !PolymerNativeClients.isNative(p)
                && p.isAlive()
                && !p.isSpectator()
                && p.mayBuild()
                && p.getMainHandItem().is(AllItems.SCHEMATIC_AND_QUILL);
    }

    private static Selection selection(ServerPlayer p) {
        Selection s = SELECTIONS.get(p.getUUID());
        if (s != null && s.level != p.level()) {
            clear(p);
            s = null;
        }
        if (s == null) {
            s = new Selection(p.level());
            SELECTIONS.put(p.getUUID(), s);
        }
        return s;
    }

    static InteractionResult use(ServerPlayer p) {
        if (!eligible(p, InteractionHand.MAIN_HAND)) return InteractionResult.PASS;
        Selection s = selection(p);
        long now = p.level().getServer().getTickCount();
        boolean repeat = now - s.lastUse <= HELD_USE_TICKS;
        s.lastUse = now;
        if (repeat) return InteractionResult.SUCCESS;
        if (p.isShiftKeyDown()) {
            discard(p);
            return InteractionResult.SUCCESS;
        }
        if (s.second != null) {
            PolymerQuillScreen.open(p);
            return InteractionResult.SUCCESS;
        }
        BlockPos target = target(p, s);
        if (target == null) {
            p.sendOverlayMessage(Component.translatable("create.schematicAndQuill.noTarget"));
            return InteractionResult.SUCCESS;
        }
        if (s.first == null) s.first = target;
        else s.second = target;
        show(p, s);
        p.sendOverlayMessage(Component.translatable(
                s.second == null ? "create.schematicAndQuill.firstPos" : "create.schematicAndQuill.secondPos"));
        return InteractionResult.SUCCESS;
    }

    private static boolean airTarget(ServerPlayer p) {
        return p.getLastClientInput().sprint();
    }

    private static BlockPos target(ServerPlayer p, Selection s) {
        boolean air = airTarget(p);
        Vec3 start = p.getEyePosition(), end = start.add(p.getLookAngle().scale(air ? s.range : 75));
        if (air) return BlockPos.containing(end);
        BlockHitResult hit = new LoadedView(p.level())
                .clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = hit.getBlockPos();
        if (hit.getDirection().getAxis().isVertical()
                && !p.level().getBlockState(pos).canBeReplaced()) pos = pos.relative(hit.getDirection());
        return pos.immutable();
    }

    static AABB box(BlockPos first, BlockPos second) {
        return new AABB(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()),
                Math.max(first.getX(), second.getX()) + 1d,
                Math.max(first.getY(), second.getY()) + 1d,
                Math.max(first.getZ(), second.getZ()) + 1d);
    }

    record LoadedView(ServerLevel level) implements BlockGetter {
        @Override
        public BlockState getBlockState(BlockPos pos) {
            return PolymerLoadedBlocks.stateOrAir(level, pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getHeight() {
            return level.getHeight();
        }

        @Override
        public int getMinY() {
            return level.getMinY();
        }
    }

    private static void show(ServerPlayer p, Selection s) {
        BlockPos end = s.second != null ? s.second : target(p, s);
        AABB next = end == null
                ? (s.first == null ? null : box(s.first, s.first))
                : box(s.first == null ? end : s.first, end);
        if (!Objects.equals(next, s.shown)) {
            ElementHolder old = s.outline;
            s.shown = next;
            s.outline = next == null ? null : PolymerSchematicOutline.show(p, next);
            if (old != null) old.destroy();
        }
        AABB selectedFace = null;
        if (s.second != null && next != null) {
            Direction face = face(next, p.getEyePosition(), p.getLookAngle());
            if (face != null) {
                double[] lo = {next.minX, next.minY, next.minZ}, hi = {next.maxX, next.maxY, next.maxZ};
                int axis = face.getAxis().ordinal();
                double plane = face.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? lo[axis] : hi[axis];
                lo[axis] = plane - .002;
                hi[axis] = plane + .002;
                selectedFace = new AABB(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]);
            }
        }
        if (!Objects.equals(selectedFace, s.shownFace)) {
            ElementHolder old = s.faceOutline;
            s.shownFace = selectedFace;
            s.faceOutline = selectedFace == null ? null : PolymerBoxOutline.show(p, selectedFace, 0xc9e2ff);
            if (old != null) old.destroy();
        }
    }

    private static void tick(ServerPlayer p) {
        Selection s = SELECTIONS.get(p.getUUID());
        if (!eligible(p, InteractionHand.MAIN_HAND)) {
            if (s != null) {
                hide(s);
                if (s.first == null) SELECTIONS.remove(p.getUUID());
            }
            return;
        }
        if (s == null) s = selection(p);
        if (s.level != p.level()) {
            clear(p);
            return;
        }
        show(p, s);
    }

    public static boolean onSlotChange(ServerPlayer p, int slot) {
        return PolymerHudRadial.scroll(p, slot) || scroll(p, slot) || PolymerSchematicTools.scroll(p, slot);
    }

    private static boolean scroll(ServerPlayer p, int slot) {
        if (!eligible(p, InteractionHand.MAIN_HAND) || !airTarget(p)) return false;
        Selection s = selection(p);
        int from = p.getInventory().getSelectedSlot(), steps = Math.floorMod(slot - from + 4, 9) - 4;
        NativePresentationPolicy.sendPresentation(p, new ClientboundSetHeldSlotPacket(from));
        if (steps == 0) return true;
        int delta = -steps;
        if (s.second == null) {
            s.range = Math.clamp(s.range + delta, 1, 100);
            show(p, s);
            return true;
        }
        AABB b = box(s.first, s.second);
        Direction selected = face(b, p.getEyePosition(), p.getLookAngle());
        if (selected == null) return true;
        if (b.contains(p.getEyePosition())) delta = -delta;
        resize(s, selected, -delta);
        b = box(s.first, s.second);
        p.sendOverlayMessage(Component.translatable(
                "create.schematicAndQuill.dimensions", (int) b.getXsize(), (int) b.getYsize(), (int) b.getZsize()));
        show(p, s);
        return true;
    }

    static Direction face(AABB b, Vec3 eye, Vec3 ray) {
        double best = Double.POSITIVE_INFINITY;
        Direction face = null;
        for (Direction d : Direction.values()) {
            int axis = d.getAxis().ordinal();
            double[] e = {eye.x, eye.y, eye.z}, r = {ray.x, ray.y, ray.z};
            double[] lo = {b.minX, b.minY, b.minZ}, hi = {b.maxX, b.maxY, b.maxZ};
            if (Math.abs(r[axis]) < 1e-9) continue;
            double t = ((d.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? lo[axis] : hi[axis]) - e[axis])
                    / r[axis];
            if (t < 0 || t > 75 || t >= best) continue;
            boolean inside = true;
            for (int a = 0; a < 3; a++)
                if (a != axis) {
                    double v = e[a] + r[a] * t;
                    if (v < lo[a] - 1e-7 || v > hi[a] + 1e-7) inside = false;
                }
            if (inside) {
                best = t;
                face = d;
            }
        }
        return face;
    }

    private static void resize(Selection s, Direction face, int amount) {
        AABB b = box(s.first, s.second);
        int[] lo = {(int) b.minX, (int) b.minY, (int) b.minZ},
                hi = {(int) b.maxX - 1, (int) b.maxY - 1, (int) b.maxZ - 1};
        int axis = face.getAxis().ordinal();
        if (face.getAxisDirection() == Direction.AxisDirection.NEGATIVE)
            lo[axis] = Math.min(hi[axis], lo[axis] - amount);
        else hi[axis] = Math.max(lo[axis], hi[axis] + amount);
        s.first = new BlockPos(lo[0], lo[1], lo[2]);
        s.second = new BlockPos(hi[0], hi[1], hi[2]);
    }

    public static boolean openTools(ServerPlayer p) {
        if (!eligible(p, InteractionHand.MAIN_HAND)) return false;
        Selection s = SELECTIONS.get(p.getUUID());
        if (s == null || s.second == null || s.level != p.level()) return false;
        PolymerQuillScreen.open(p);
        return true;
    }

    public static boolean ready(ServerPlayer p) {
        Selection s = SELECTIONS.get(p.getUUID());
        return eligible(p, InteractionHand.MAIN_HAND) && s != null && s.second != null && s.level == p.level();
    }

    public static void discard(ServerPlayer p) {
        clear(p);
        p.sendOverlayMessage(Component.translatable("create.schematicAndQuill.abort"));
    }

    public static void saveToFile(ServerPlayer p, String name) {
        Selection s = SELECTIONS.get(p.getUUID());
        if (!ready(p)) return;
        String file = save(p, s, clean(name));
        if (file == null) return;
        p.sendOverlayMessage(Component.translatable("create.schematicAndQuill.saved", file));
        clear(p);
    }

    public static void convert(ServerPlayer p, String name) {
        Selection s = SELECTIONS.get(p.getUUID());
        if (!ready(p)) return;
        AABB b = box(s.first, s.second);
        BlockPos low = BlockPos.containing(b.minX, b.minY, b.minZ);
        if (regionError(p, s) != null) {
            p.sendOverlayMessage(Component.translatable("create.schematicAndQuill.failed"));
            return;
        }
        String file = clean(name.isBlank() ? PolymerSchematicGuis.lang("create.schematicAndQuill.fallbackName") : name)
                + ".nbt";
        Create.SCHEMATIC_RECEIVER.handleInstantSchematic(
                p, file, p.level(), low, new BlockPos((int) b.getXsize(), (int) b.getYsize(), (int) b.getZsize()));
        if (p.getMainHandItem().is(AllItems.SCHEMATIC)) clear(p);
    }

    private static String clean(String name) {
        String out = name.trim()
                .replaceAll("[^A-Za-z0-9_.-]", "_")
                .replaceAll("^[^A-Za-z0-9]+", "")
                .replaceAll("\\.+$", "");
        return out.length() > 35 ? out.substring(0, 35) : out;
    }

    private static String regionError(ServerPlayer p, Selection s) {
        if (s.first == null || s.second == null) return "Choose both corners first.";
        AABB b = box(s.first, s.second);
        BlockPos low = BlockPos.containing(b.minX, b.minY, b.minZ),
                high = BlockPos.containing(b.maxX - 1, b.maxY - 1, b.maxZ - 1);
        if (b.getXsize() * b.getYsize() * b.getZsize() > MAX_BLOCKS)
            return "Selection is too large (maximum 65536 blocks).";
        if (low.getY() < p.level().getMinY()
                || high.getY() >= p.level().getMinY() + p.level().getHeight()
                || !p.level().hasChunksAt(low, high)
                || !p.level().getWorldBorder().isWithinBounds(low)
                || !p.level().getWorldBorder().isWithinBounds(high))
            return "The selected region is outside the loaded world.";
        if (!p.mayInteract(p.level(), low) || !p.mayInteract(p.level(), high)) return "You cannot capture this region.";
        return null;
    }

    public static boolean action(ServerGamePacketListenerImpl listener, ServerboundCustomClickActionPacket packet) {
        return false;
    }

    private static String save(ServerPlayer p, Selection s, String name) {
        String error = regionError(p, s);
        if (error != null) {
            p.sendOverlayMessage(Component.literal(error));
            return null;
        }
        AABB b = box(s.first, s.second);
        BlockPos low = BlockPos.containing(b.minX, b.minY, b.minZ),
                high = BlockPos.containing(b.maxX - 1, b.maxY - 1, b.maxZ - 1);
        try {
            Path base = CreatePaths.UPLOADED_SCHEMATICS_DIR.toAbsolutePath().normalize();
            Path directory = base.resolve(p.getGameProfile().name()).normalize();
            if (!directory.getParent().equals(base) || Files.isSymbolicLink(base) || Files.isSymbolicLink(directory)) {
                p.sendOverlayMessage(Component.literal("Invalid schematic directory."));
                return null;
            }
            Files.createDirectories(directory);
            if (!directory.toRealPath().getParent().equals(base.toRealPath())) {
                p.sendOverlayMessage(Component.literal("Invalid schematic directory."));
                return null;
            }
            int limit = AllConfigs.server().schematics.maxSchematics.get();
            try (var files = Files.list(directory)) {
                if (limit <= 0
                        || files.filter(f -> Files.isRegularFile(f, LinkOption.NOFOLLOW_LINKS)
                                                && f.getFileName().toString().endsWith(".nbt"))
                                        .limit(limit)
                                        .count()
                                >= limit) {
                    p.sendOverlayMessage(
                            Component.literal(
                                    "Your server schematic limit is reached. Remove an older schematic before saving another."));
                    return null;
                }
            }
            var result = SchematicExport.saveSchematic(directory, name, false, p.level(), low, high);
            if (result == null) {
                p.sendOverlayMessage(Component.translatable("create.schematicAndQuill.failed"));
                return null;
            }
            if (PolymerSchematicFiles.blueprint(p, result.fileName()).isEmpty()) {
                Files.deleteIfExists(result.file());
                p.sendOverlayMessage(
                        Component.literal("The exported schematic exceeds file limits or could not be read."));
                return null;
            }
            return result.fileName();
        } catch (IOException | RuntimeException error2) {
            Create.LOGGER.warn(
                    "Could not save quill selection for {}", p.getGameProfile().name(), error2);
            p.sendOverlayMessage(Component.translatable("create.schematicAndQuill.failed"));
            return null;
        }
    }

    private static void hide(Selection s) {
        if (s.outline != null) s.outline.destroy();
        if (s.faceOutline != null) s.faceOutline.destroy();
        s.outline = null;
        s.faceOutline = null;
        s.shown = null;
        s.shownFace = null;
    }

    private static void clear(ServerPlayer p) {
        Selection s = SELECTIONS.remove(p.getUUID());
        if (s != null) hide(s);
    }
}
