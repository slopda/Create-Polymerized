package com.zurrtum.create.infrastructure.polymer.schematic;

import com.zurrtum.create.AllHandle;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.catnip.placement.PlacementHelpers;
import com.zurrtum.create.content.contraptions.glue.SuperGlueEntity;
import com.zurrtum.create.content.decoration.copycat.CopycatBlock;
import com.zurrtum.create.content.equipment.toolbox.ToolboxBlockEntity;
import com.zurrtum.create.infrastructure.polymer.AdapterHolder;
import com.zurrtum.create.infrastructure.polymer.PolymerDisplays;
import com.zurrtum.create.infrastructure.polymer.PolymerGhostModels;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockControls;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts;
import com.zurrtum.create.infrastructure.polymer.block.PolymerKineticVisuals;
import com.zurrtum.create.infrastructure.polymer.block.PolymerTableClothPrice;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerSuperGlue;
import com.zurrtum.create.infrastructure.polymer.interaction.PolymerArmTargets;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.*;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class PolymerWorldPreviews {
    private static final Map<UUID, Preview> PREVIEWS = new HashMap<>();

    private record Preview(String key, ElementHolder holder, ToolboxBlockEntity toolbox, long lastAim) {}

    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clear(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            PREVIEWS.values().forEach(p -> p.holder().destroy());
            PREVIEWS.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : NativePresentationPolicy.presentationPlayers(server.getPlayerList()))
                tick(player);
        });
    }

    public static void clear(UUID player) {
        Preview old = PREVIEWS.remove(player);
        if (old != null) old.holder().destroy();
    }

    private static void tick(ServerPlayer player) {
        if (player.isSpectator()) {
            clear(player.getUUID());
            return;
        }
        double reach = player.blockInteractionRange();
        HitResult ray = player.pick(reach, 1, false);
        BlockHitResult hit = ray instanceof BlockHitResult h && h.getType() == HitResult.Type.BLOCK ? h : null;
        BlockPos aim = hit == null ? null : hit.getBlockPos();
        ItemStack held = player.getMainHandItem();
        boolean glue = held.is(AllItems.SUPER_GLUE) || player.getOffhandItem().is(AllItems.SUPER_GLUE);
        List<SuperGlueEntity> glueEntities = glue
                ? player.level()
                        .getEntitiesOfClass(
                                SuperGlueEntity.class, player.getBoundingBox().inflate(32, 16, 32))
                : List.of();
        List<AABB> glueBoxes =
                glueEntities.stream().map(SuperGlueEntity::getBoundingBox).toList();
        AABB selection = glue ? PolymerSuperGlue.preview(player, aim) : null;
        AABB aimedGlue = glue && selection == null ? PolymerSuperGlue.aimed(player, glueEntities) : null;
        int selectionColor = glue && selection != null ? PolymerSuperGlue.previewColor(player, aim) : 0;
        ToolboxBlockEntity toolbox = null;
        Preview old = PREVIEWS.get(player.getUUID());
        long now = player.level().getGameTime();
        if (toolbox == null
                && old != null
                && old.toolbox() != null
                && (now - old.lastAim() < 40 || looksAtToolboxUI(player, old.toolbox()))
                && old.toolbox().getLevel() == player.level()
                && !old.toolbox().isRemoved()
                && player.getEyePosition()
                                .distanceToSqr(Vec3.atCenterOf(old.toolbox().getBlockPos()))
                        < reach * reach) toolbox = old.toolbox();

        BlockPos ghostPos = null;
        BlockState ghostState = null;
        if (!glue && toolbox == null && hit != null && held.getItem() instanceof BlockItem item) {
            BlockState target = player.level().getBlockState(aim);
            if (!player.isShiftKeyDown())
                for (var helper : PlacementHelpers.getHelpersView()) {
                    if (!helper.matchesItem(held) || !helper.matchesState(target)) continue;
                    var offset = helper.getOffset(player, player.level(), target, aim, hit, held);
                    if (!offset.isSuccessful() || !offset.isReplaceable(player.level())) continue;
                    if (offset.hasGhostState()) {
                        ghostPos = offset.getBlockPos();
                        ghostState = offset.getTransform().apply(offset.getGhostState());
                    }
                    break;
                }
            if (ghostPos != null
                    && (!player.level().isLoaded(ghostPos) || !player.mayInteract(player.level(), ghostPos))) {
                ghostPos = null;
                ghostState = null;
            }
        }
        List<PolymerBlockParts.Part> controls = new ArrayList<>();
        if (hit != null)
            PolymerBlockControls.collectHover(
                    player.level().getBlockEntity(aim), player.level().getBlockState(aim), player, hit, controls);
        if (hit != null) PolymerTableClothPrice.collectHover(player, aim, controls);
        if (selection == null && glueBoxes.isEmpty() && toolbox == null && ghostPos == null && controls.isEmpty()) {
            clear(player.getUUID());
            return;
        }
        StringBuilder inventory = new StringBuilder();
        if (toolbox != null)
            for (int i = 0; i < 8; i++)
                inventory
                        .append(toolboxStack(toolbox, i))
                        .append(toolboxStack(toolbox, i).getComponents());
        boolean lookingAtToolbox = toolbox != null
                && (aim != null && aim.equals(toolbox.getBlockPos()) || looksAtToolboxUI(player, toolbox));
        String toolboxAngle = toolbox == null
                ? ""
                : Integer.toString((int) (Math.atan2(
                                player.getX() - toolbox.getBlockPos().getX() - .5,
                                player.getZ() - toolbox.getBlockPos().getZ() - .5)
                        * 12
                        / Math.PI));
        boolean aimAnchored =
                !controls.isEmpty() || selection == null && toolbox == null && ghostPos == null && glueBoxes.isEmpty();
        Vec3 origin = selection != null
                ? selection.getCenter()
                : toolbox != null
                        ? Vec3.atCenterOf(toolbox.getBlockPos())
                        : ghostPos != null
                                ? Vec3.atCenterOf(ghostPos)
                                : aimAnchored && aim != null
                                        ? Vec3.atCenterOf(aim)
                                        : !glueBoxes.isEmpty()
                                                ? glueBoxes.get(0).getCenter()
                                                : player.position();
        String key = player.level().dimension().identifier() + ":" + (aimAnchored ? aim : "") + ":" + ghostPos + ":"
                + ghostState + ":" + held + ":" + held.getComponents() + ":" + selection + ":" + selectionColor + ":"
                + aimedGlue + ":" + glueBoxes + ":" + controls + ":" + toolboxAngle + ":"
                + (toolbox == null ? "" : toolbox.getBlockPos().toString() + inventory);
        if (old != null && key.equals(old.key())) {
            long refreshed = lookingAtToolbox ? now : old.lastAim();
            PREVIEWS.put(player.getUUID(), new Preview(key, old.holder(), toolbox, refreshed));
            old.holder().tick();
            return;
        }
        Preview previous = PREVIEWS.remove(player.getUUID());
        ElementHolder holder = new AdapterHolder();
        new ManualAttachment(holder, player.level(), () -> origin);
        for (PolymerBlockParts.Part control : controls) addControl(holder, origin, aim, control);
        for (AABB box : glueBoxes)
            if (!box.equals(aimedGlue)) tintedWire(holder, origin, box, PolymerSuperGlue.PASSIVE, 1 / 64f);
        if (aimedGlue != null) {
            tintedWire(holder, origin, aimedGlue, PolymerSuperGlue.HIGHLIGHT, 1 / 16f);
            gluePattern(holder, origin, aimedGlue, PolymerSuperGlue.HIGHLIGHT);
        }
        if (selection != null) {
            tintedWire(holder, origin, selection, selectionColor, 1 / 16f);
            gluePattern(holder, origin, selection, selectionColor);
        }
        if (ghostPos != null && ghostState != null) {
            addGhost(holder, player, origin, ghostPos, ghostState, held);
        }
        if (toolbox != null) toolbox(holder, origin, toolbox, player);
        holder.startWatching(player);
        holder.tick();
        if (previous != null) previous.holder().destroy();
        long lastAim = lookingAtToolbox ? now : old != null ? old.lastAim() : now;
        PREVIEWS.put(player.getUUID(), new Preview(key, holder, toolbox, lastAim));
    }

    private static ItemStack toolboxStack(ToolboxBlockEntity be, int slot) {
        ItemStack actual = be.inventory.getItem(slot * 4);
        return actual.isEmpty() ? be.inventory.filters.get(slot) : actual;
    }

    private static void addControl(ElementHolder holder, Vec3 origin, BlockPos pos, PolymerBlockParts.Part part) {
        Matrix4f pose = new Matrix4f()
                .translate(Vec3.atLowerCornerOf(pos).subtract(origin).toVector3f())
                .mul(part.transform());
        DisplayElement element;
        if (part.text() != null) {
            TextDisplayElement text = new TextDisplayElement(part.text());
            text.setBackground(0);
            text.setLineWidth(1024);
            pose.translate(-1, 9, 0).scale(40, -40, 40);
            element = text;
        } else {
            ItemDisplayElement item = PolymerDisplays.item(
                    part.item().isEmpty() ? PolymerBlockParts.displayItem(part.model()) : part.item(), part.context());
            if (part.item().isEmpty()) pose.translate(.5f, .5f, .5f);
            pose.rotateY((float) Math.PI);
            element = item;
        }
        element.setTransformation(pose);
        element.setBrightness(new Brightness(15, 15));
        holder.addElement(element);
    }

    private static void toolbox(ElementHolder holder, Vec3 origin, ToolboxBlockEntity be, ServerPlayer owner) {
        Vec3 center = Vec3.atCenterOf(be.getBlockPos());
        Vec3 toward = center.subtract(owner.getEyePosition());
        Vec3 right = new Vec3(-toward.z, 0, toward.x).normalize();
        if (right.lengthSqr() < .01) right = new Vec3(1, 0, 0);
        for (int slot = 0; slot < 8; slot++) {
            int compartment = slot;
            Vec3 position = center.add(right.scale((slot % 4 - 1.5) * .38)).add(0, 1.1 + (slot / 4) * .4, 0);
            Vec3 relative = position.subtract(origin);
            ItemStack stack = toolboxStack(be, slot);
            if (!stack.isEmpty()) {
                ItemDisplayElement item = PolymerDisplays.item(stack.copyWithCount(1), ItemDisplayContext.FIXED);
                item.setBillboardMode(Display.BillboardConstraints.CENTER);
                item.setTransformation(new Matrix4f()
                        .translate(relative.toVector3f())
                        .scale(.28f)
                        .rotateY((float) Math.PI));
                item.setBrightness(new Brightness(15, 15));
                holder.addElement(item);
            }
            TextDisplayElement number = new TextDisplayElement(Component.literal(Integer.toString(slot + 1)));
            number.setOffset(relative.add(0, -.2, 0));
            number.setBillboardMode(Display.BillboardConstraints.CENTER);
            number.setScale(new Vector3f(.45f));
            number.setBackground(0x80000000);
            holder.addElement(number);
            InteractionElement interaction = new InteractionElement();
            interaction.setSize(.35f, .35f);
            interaction.setResponse(true);
            interaction.setOffset(relative.add(0, -.175, 0));
            interaction.setInteractionHandler(new VirtualElement.InteractionHandler() {
                @Override
                public void interact(ServerPlayer player, InteractionHand hand, Vec3 hit, boolean secondary) {
                    if (player != owner
                            || player.isSpectator()
                            || !player.mayBuild()
                            || be.isRemoved()
                            || be.getLevel() != player.level()
                            || player.level().getBlockEntity(be.getBlockPos()) != be
                            || !player.mayInteract(player.level(), be.getBlockPos())
                            || player.getEyePosition().distanceToSqr(position)
                                    > Math.pow(
                                            Math.min(player.blockInteractionRange(), player.entityInteractionRange())
                                                    + .1,
                                            2)) return;
                    if (player.level()
                                    .clip(new ClipContext(
                                            player.getEyePosition(),
                                            position,
                                            ClipContext.Block.COLLIDER,
                                            ClipContext.Fluid.NONE,
                                            player))
                                    .getType()
                            != HitResult.Type.MISS) return;
                    AllHandle.setToolboxForHotbar(
                            player,
                            be.getBlockPos(),
                            compartment,
                            player.getInventory().getSelectedSlot());
                }
            });
            holder.addElement(interaction);
        }
    }

    private static boolean looksAtToolboxUI(ServerPlayer player, ToolboxBlockEntity be) {
        Vec3 c = Vec3.atCenterOf(be.getBlockPos());
        return new AABB(c.x - .85, c.y + .85, c.z - .85, c.x + .85, c.y + 1.75, c.z + .85)
                .clip(
                        player.getEyePosition(),
                        player.getEyePosition().add(player.getViewVector(1).scale(player.blockInteractionRange())))
                .isPresent();
    }

    private static void addGhost(
            ElementHolder holder, ServerPlayer player, Vec3 origin, BlockPos pos, BlockState state, ItemStack held) {
        boolean create =
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("create");
        Vec3 relative = Vec3.atCenterOf(pos).subtract(origin);
        ItemStack cube = state.getBlock() instanceof CopycatBlock ? PolymerGhostModels.copycatCube() : null;
        if (cube != null) {
            var shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            for (AABB box : shape.toAabbs()) {
                ItemDisplayElement part = PolymerDisplays.item(cube.copy(), ItemDisplayContext.NONE);
                Vec3 center = box.getCenter().subtract(.5, .5, .5);
                part.setTransformation(new Matrix4f()
                        .translate(relative.toVector3f())
                        .scale(.85f)
                        .translate(center.toVector3f())
                        .scale((float) box.getXsize(), (float) box.getYsize(), (float) box.getZsize())
                        .rotateY((float) Math.PI));
                part.setBrightness(new Brightness(15, 15));
                holder.addElement(part);
            }
            return;
        }
        PolymerGhostModels.Variant variant = create ? PolymerGhostModels.variant(state) : null;
        if (variant != null) {
            ItemDisplayElement item = PolymerDisplays.item(variant.stack(), ItemDisplayContext.NONE);
            item.setTransformation(new Matrix4f()
                    .translate(relative.toVector3f())
                    .rotate(variant.rotation())
                    .scale(.85f)
                    .rotateY((float) Math.PI));
            item.setBrightness(new Brightness(15, 15));
            holder.addElement(item);
            return;
        }
        ItemStack ghost = create ? PolymerGhostModels.ghost(state.getBlock().asItem()) : null;
        if (ghost != null) {
            ItemDisplayElement item = PolymerDisplays.item(ghost, ItemDisplayContext.NONE);
            Quaternionf rotation = PolymerKineticVisuals.rotatingItemModel(state) != null
                    ? PolymerKineticVisuals.staticAxisRotation(state, pos)
                    : stateRotation(state);
            item.setTransformation(new Matrix4f()
                    .translate(relative.toVector3f())
                    .rotate(rotation)
                    .scale(.85f)
                    .rotateY((float) Math.PI));
            item.setBrightness(new Brightness(15, 15));
            holder.addElement(item);
            return;
        }
        BlockState carrier = state;
        if (create) {
            try {
                carrier = PacketContext.supplyWithContext(
                        player.connection, () -> PolymerBlockUtils.getPolymerBlockState(state, PacketContext.get()));
            } catch (RuntimeException exception) {
                carrier = Blocks.AIR.defaultBlockState();
            }
        }
        var kineticModel = create ? PolymerKineticVisuals.rotatingItemModel(state) : null;
        boolean holderDrawn = create && kineticModel == null && BlockWithElementHolder.get(state) != null;
        boolean visibleCarrier = !holderDrawn
                && !carrier.isAir()
                && !carrier.is(Blocks.BARRIER)
                && !carrier.is(Blocks.STRUCTURE_VOID)
                && carrier.getRenderShape() != RenderShape.INVISIBLE;
        if (visibleCarrier) {
            BlockDisplayElement block = new BlockDisplayElement(carrier);
            block.setTransformation(
                    new Matrix4f().translate(relative.toVector3f()).scale(.85f).translate(-.5f, -.5f, -.5f));
            block.setBrightness(new Brightness(15, 15));
            holder.addElement(block);
        }
        if (!create) return;
        if (kineticModel != null) {
            ItemDisplayElement item = PolymerDisplays.item(
                    PolymerKineticVisuals.rotatingDisplayItem(kineticModel), ItemDisplayContext.NONE);
            Matrix4f pose = new Matrix4f()
                    .translate(relative.toVector3f())
                    .rotate(PolymerKineticVisuals.staticAxisRotation(state, pos));
            item.setTransformation(pose.scale(.85f).rotateY((float) Math.PI));
            item.setBrightness(new Brightness(15, 15));
            holder.addElement(item);
            return;
        }
        if (visibleCarrier) return;
        ItemDisplayElement item = PolymerDisplays.item(held.copyWithCount(1), ItemDisplayContext.NONE);
        Matrix4f pose = new Matrix4f().translate(relative.toVector3f()).rotate(stateRotation(state));
        item.setTransformation(pose.scale(.85f).rotateY((float) Math.PI));
        item.setBrightness(new Brightness(15, 15));
        holder.addElement(item);
    }

    private static Quaternionf stateRotation(BlockState state) {

        Direction facing = null;
        if (state.hasProperty(BlockStateProperties.FACING)) facing = state.getValue(BlockStateProperties.FACING);
        else if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
            facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        if (facing != null) {
            return switch (facing) {
                case NORTH -> new Quaternionf();
                case SOUTH -> new Quaternionf().rotateY((float) Math.PI);
                case WEST -> new Quaternionf().rotateY((float) (Math.PI / 2));
                case EAST -> new Quaternionf().rotateY((float) (-Math.PI / 2));
                case UP -> new Quaternionf().rotateX((float) (Math.PI / 2));
                case DOWN -> new Quaternionf().rotateX((float) (-Math.PI / 2));
            };
        }
        if (state.hasProperty(BlockStateProperties.AXIS)) {
            return switch (state.getValue(BlockStateProperties.AXIS)) {
                case Y -> new Quaternionf();
                case X -> new Quaternionf().rotateZ((float) (Math.PI / 2));
                case Z -> new Quaternionf().rotateX((float) (Math.PI / 2));
            };
        }
        return new Quaternionf();
    }

    private static void tintedWire(ElementHolder holder, Vec3 origin, AABB box, int color, float width) {
        double[] xs = {box.minX, box.maxX}, ys = {box.minY, box.maxY}, zs = {box.minZ, box.maxZ};
        double h = width / 2;
        for (int i = 0; i < 2; i++)
            for (int j = 0; j < 2; j++) {
                tintedBar(
                        holder,
                        origin,
                        new AABB(xs[0] - h, ys[i] - h, zs[j] - h, xs[1] + h, ys[i] + h, zs[j] + h),
                        color);
                tintedBar(
                        holder,
                        origin,
                        new AABB(xs[i] - h, ys[0] - h, zs[j] - h, xs[i] + h, ys[1] + h, zs[j] + h),
                        color);
                tintedBar(
                        holder,
                        origin,
                        new AABB(xs[i] - h, ys[j] - h, zs[0] - h, xs[i] + h, ys[j] + h, zs[1] + h),
                        color);
            }
    }

    private static void gluePattern(ElementHolder holder, Vec3 origin, AABB box, int color) {
        var item = PolymerArmTargets.glueFace(color);
        double eps = .004;
        for (Direction face : Direction.values()) {
            Direction.Axis axis = face.getAxis();
            Direction.Axis[] plane = switch (axis) {
                case X -> new Direction.Axis[] {Direction.Axis.Z, Direction.Axis.Y};
                case Y -> new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z};
                case Z -> new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Y};
            };
            double fixed = face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                    ? box.max(axis) + eps
                    : box.min(axis) - eps;
            double u0 = box.min(plane[0]), u1 = box.max(plane[0]), v0 = box.min(plane[1]), v1 = box.max(plane[1]);
            int nu = Math.max(1, (int) Math.ceil(u1 - u0 - 1e-6)), nv = Math.max(1, (int) Math.ceil(v1 - v0 - 1e-6));
            boolean stretch = nu * nv > 256;
            if (stretch) {
                nu = 1;
                nv = 1;
            }
            for (int i = 0; i < nu; i++)
                for (int j = 0; j < nv; j++) {
                    double a0 = stretch ? u0 : u0 + i, a1 = stretch ? u1 : Math.min(u1, a0 + 1);
                    double b0 = stretch ? v0 : v0 + j, b1 = stretch ? v1 : Math.min(v1, b0 + 1);
                    double[] center = new double[3], size = {1e-3, 1e-3, 1e-3};
                    center[axis.ordinal()] = fixed;
                    center[plane[0].ordinal()] = (a0 + a1) / 2;
                    size[plane[0].ordinal()] = a1 - a0;
                    center[plane[1].ordinal()] = (b0 + b1) / 2;
                    size[plane[1].ordinal()] = b1 - b0;
                    Matrix4f pose = new Matrix4f()
                            .translate((float) (center[0] - origin.x), (float) (center[1] - origin.y), (float)
                                    (center[2] - origin.z));
                    if (axis == Direction.Axis.X) pose.rotateY((float) Math.PI / 2);
                    else if (axis == Direction.Axis.Y) pose.rotateX((float) Math.PI / 2);
                    float su = (float) size[plane[0].ordinal()], sv = (float) size[plane[1].ordinal()];
                    pose.scale(axis == Direction.Axis.X ? su : su, axis == Direction.Axis.Y ? sv : sv, 1);
                    ItemDisplayElement element = PolymerDisplays.item(item, ItemDisplayContext.NONE);
                    element.setTransformation(pose);
                    element.setBrightness(new Brightness(15, 15));
                    holder.addElement(element);
                }
        }
    }

    private static void tintedBar(ElementHolder holder, Vec3 origin, AABB bar, int color) {
        ItemDisplayElement element = PolymerDisplays.item(PolymerArmTargets.tintCube(color), ItemDisplayContext.NONE);
        element.setTransformation(new Matrix4f()
                .translate(bar.getCenter().subtract(origin).toVector3f())
                .scale((float) bar.getXsize(), (float) bar.getYsize(), (float) bar.getZsize()));
        element.setBrightness(new Brightness(15, 15));
        holder.addElement(element);
    }
}
