package com.zurrtum.create.infrastructure.polymer.schematic;

import com.zurrtum.create.content.equipment.blueprint.BlueprintEntity;
import com.zurrtum.create.infrastructure.polymer.AdapterHolder;
import com.zurrtum.create.infrastructure.polymer.PolymerDisplays;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerBlueprintGui;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.InteractionElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class PolymerBlueprintVisuals {
    private static final Identifier[] BOARDS = {
        PolymerBlockParts.model(Identifier.parse("create:entity/crafting_blueprint_small")),
        PolymerBlockParts.model(Identifier.parse("create:entity/crafting_blueprint_medium")),
        PolymerBlockParts.model(Identifier.parse("create:entity/crafting_blueprint_large"))
    };
    private static final Map<BlueprintEntity, Visual> VISUALS = new IdentityHashMap<>();

    private PolymerBlueprintVisuals() {}

    public static void register() {
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(PolymerBlueprintGui::addAssets);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof BlueprintEntity blueprint && !VISUALS.containsKey(blueprint))
                VISUALS.put(blueprint, new Visual(blueprint));
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            Visual visual = entity instanceof BlueprintEntity blueprint ? VISUALS.remove(blueprint) : null;
            if (visual != null) visual.destroy();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            List.copyOf(VISUALS.values()).forEach(ElementHolder::destroy);
            VISUALS.clear();
        });
    }

    private static final class Visual extends AdapterHolder {
        private final BlueprintEntity entity;
        private final ItemDisplayElement board = new ItemDisplayElement();
        private final InteractionElement target;
        private final List<ItemDisplayElement> icons = new ArrayList<>();
        private List<ItemStack> shown = List.of();
        private float yaw = Float.NaN, pitch = Float.NaN;
        private int size = -1;
        private long lastClick = -1;

        Visual(BlueprintEntity entity) {
            this.entity = entity;
            board.setItemDisplayContext(ItemDisplayContext.NONE);
            addElement(board);
            target = new InteractionElement(new VirtualElement.InteractionHandler() {
                @Override
                public void interact(ServerPlayer player, InteractionHand hand, Vec3 hit, boolean secondary) {
                    use(player, hand);
                }

                @Override
                public void attack(ServerPlayer player) {
                    if (!entity.isRemoved() && player.isWithinEntityInteractionRange(entity.getBoundingBox(), 1))
                        player.attack(entity);
                }
            });
            addElement(target);
            update();
            EntityAttachment.ofTicking(this, entity);
        }

        private void use(ServerPlayer player, InteractionHand hand) {
            if (entity.isRemoved() || !player.isWithinEntityInteractionRange(entity.getBoundingBox(), 1)) return;
            long now = player.level().getGameTime();
            if (hand == InteractionHand.OFF_HAND && lastClick == now) return;
            lastClick = now;
            AABB box = entity.getBoundingBox().inflate(1e-3);
            Vec3 eye = player.getEyePosition();
            Vec3 end = eye.add(player.getViewVector(1).scale(player.blockInteractionRange() + 1));
            Vec3 hit = box.clip(eye, end).orElse(box.getCenter());
            entity.interact(player, hand, hit.subtract(entity.position()));
        }

        @Override
        protected void onTick() {
            super.onTick();
            update();
        }

        private void update() {
            if (entity.isRemoved()) return;
            float newYaw = entity.getYRot(), newPitch = entity.getXRot();
            int newSize = Mth.clamp(entity.size, 1, 3);
            boolean moved = newYaw != yaw || newPitch != pitch || newSize != size;
            if (moved) {
                yaw = newYaw;
                pitch = newPitch;
                size = newSize;
                board.setItem(PolymerBlockParts.displayItem(BOARDS[size - 1]));
                Vec3 offset = new Vec3(-.5, -.03125, -.5).add(size == 2 ? new Vec3(.5, 0, -.5) : Vec3.ZERO);
                board.setTransformation(new Matrix4f()
                        .rotateY(Mth.DEG_TO_RAD * -yaw)
                        .rotateX(Mth.DEG_TO_RAD * (90 + pitch))
                        .translate((float) offset.x, (float) offset.y, (float) offset.z)
                        .translate(.5f, .5f, .5f)
                        .rotateY(Mth.PI));
                AABB box = entity.getBoundingBox();
                Vec3 pos = entity.position();
                target.setSize((float) Math.max(box.getXsize(), box.getZsize()), (float) Math.max(box.getYsize(), .1));
                target.setOffset(new Vec3(box.getCenter().x - pos.x, box.minY - pos.y, box.getCenter().z - pos.z));
            }
            List<ItemStack> items = new ArrayList<>(size * size * 2);
            for (int section = 0; section < size * size; section++) {
                var display = entity.getSection(section).getDisplayItems();
                items.add(display.getFirst());
                items.add(display.getSecond());
            }
            if (!moved && sameItems(items)) return;
            shown = items.stream().map(ItemStack::copy).toList();
            icons.forEach(this::removeElement);
            icons.clear();
            int origin = size == 3 ? -1 : 0;
            Matrix4f base = new Matrix4f().rotateY(Mth.DEG_TO_RAD * -yaw);
            if (pitch != 0) base.rotateX(Mth.DEG_TO_RAD * pitch);
            base.translate(origin, origin, 1 / 32f + .001f);
            for (int section = 0; section < size * size; section++) {
                Matrix4f cell = new Matrix4f(base)
                        .translate(section % size, section / size, 0)
                        .scale(.5f, .5f, 1 / 1024f);
                icon(items.get(section * 2), new Matrix4f(cell));
                icon(
                        items.get(section * 2 + 1),
                        new Matrix4f(cell).translate(.325f, -.325f, 1).scale(.625f, .625f, 1));
            }
        }

        private boolean sameItems(List<ItemStack> items) {
            if (items.size() != shown.size()) return false;
            for (int i = 0; i < items.size(); i++) if (!ItemStack.matches(items.get(i), shown.get(i))) return false;
            return true;
        }

        private void icon(ItemStack stack, Matrix4f pose) {
            if (stack.isEmpty()) return;
            ItemDisplayElement icon = PolymerDisplays.item(stack.copyWithCount(1), ItemDisplayContext.GUI);
            icon.setTransformation(pose.rotateY(Mth.PI));
            icons.add(icon);
            addElement(icon);
        }
    }
}
