package com.zurrtum.create.infrastructure.polymer.contraption;

import com.zurrtum.create.catnip.animation.LerpedFloat;
import com.zurrtum.create.content.contraptions.AbstractContraptionEntity;
import com.zurrtum.create.content.decoration.slidingDoor.SlidingDoorBlock;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts;
import com.zurrtum.create.infrastructure.polymer.block.PolymerDoorParts;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

final class PolymerMovingDoors {
    private final ElementHolder holder;
    private final AbstractContraptionEntity entity;
    private final Map<BlockPos, Door> doors = new HashMap<>();

    PolymerMovingDoors(ElementHolder holder, AbstractContraptionEntity entity) {
        this.holder = holder;
        this.entity = entity;
    }

    List<DisplayElement> displays() {
        List<DisplayElement> result = new ArrayList<>();
        doors.values().forEach(door -> result.addAll(door.parts));
        return result;
    }

    void update(Matrix4f world) {
        var blocks = entity.getContraption().getBlocks();
        Set<BlockPos> live = new HashSet<>();
        for (var entry : blocks.entrySet()) {
            var state = entry.getValue().state();
            if (!(state.getBlock() instanceof SlidingDoorBlock)
                    || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;
            BlockPos pos = entry.getKey();
            live.add(pos);
            boolean open = state.getValue(DoorBlock.OPEN);
            Door door = doors.computeIfAbsent(pos, key -> new Door(open));
            door.animation.chase(open ? 1 : 0, .15f, LerpedFloat.Chaser.LINEAR);
            door.animation.tickChaser();
            List<PolymerBlockParts.Part> desired = new ArrayList<>();
            PolymerDoorParts.movingParts(state, door.animation.getValue(), desired);
            while (door.parts.size() > desired.size()) holder.removeElement(door.parts.removeLast());
            for (int i = 0; i < desired.size(); i++) {
                var part = desired.get(i);
                Matrix4f local = new Matrix4f(world)
                        .translate(pos.getX(), pos.getY(), pos.getZ())
                        .mul(part.transform());
                if (i == door.parts.size()) {
                    var display = new ItemDisplayElement(PolymerBlockParts.displayItem(part.model()));
                    display.setItemDisplayContext(ItemDisplayContext.NONE);
                    display.setInterpolationDuration(PolymerContraptionVisuals.smoothTicks(entity));
                    display.setTeleportDuration(PolymerContraptionVisuals.smoothTicks(entity));
                    PolymerContraptionPassengers.applyDisplayPose(display, local);
                    display.setBrightness(PolymerContraptionVisuals.light(
                            entity.level(), entity.toGlobalVector(Vec3.atCenterOf(pos), 1)));
                    door.parts.add(holder.addElement(display));
                }
                var display = door.parts.get(i);
                var item = PolymerBlockParts.displayItem(part.model());
                if (!ItemStack.isSameItemSameComponents(display.getItem(), item)) display.setItem(item);
                PolymerContraptionPassengers.applyDisplayPose(display, local);
                display.startInterpolationIfDirty();
                if ((entity.tickCount & 3) == 0 || display.getBrightness() == null) {
                    var light = PolymerContraptionVisuals.light(
                            entity.level(), entity.toGlobalVector(Vec3.atCenterOf(pos), 1));
                    if (!light.equals(display.getBrightness())) display.setBrightness(light);
                }
            }
        }
        doors.entrySet().removeIf(entry -> {
            if (live.contains(entry.getKey())) return false;
            entry.getValue().parts.forEach(holder::removeElement);
            return true;
        });
    }

    private static final class Door {
        final LerpedFloat animation = LerpedFloat.linear();
        final List<ItemDisplayElement> parts = new ArrayList<>();

        Door(boolean open) {
            animation.startWithValue(open ? 1 : 0);
        }
    }
}
