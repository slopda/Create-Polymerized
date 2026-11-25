package com.zurrtum.create.infrastructure.polymer.block;

import com.zurrtum.create.content.logistics.box.PackageEntity;
import com.zurrtum.create.infrastructure.polymer.AdapterHolder;
import com.zurrtum.create.infrastructure.polymer.PolymerDisplays;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public final class PolymerPackageVisuals {
    private static final Map<PackageEntity, BoxDisplay> BOXES = new WeakHashMap<>();

    private PolymerPackageVisuals() {}

    public static void tick(PackageEntity entity) {
        if (entity.level().isClientSide()) {
            return;
        }
        BoxDisplay display = BOXES.get(entity);
        if (entity.isRemoved()) {
            if (display != null) {
                display.holder.destroy();
                BOXES.remove(entity);
            }
            return;
        }
        if (display == null) {
            display = new BoxDisplay(entity);
            BOXES.put(entity, display);
        }
        display.update(entity);
    }

    private static final class BoxDisplay {
        private final ElementHolder holder = new AdapterHolder();
        private final ItemDisplayElement box;
        private ItemStack shown;

        private BoxDisplay(PackageEntity entity) {
            shown = entity.getBox().copy();
            box = PolymerDisplays.item(shown, ItemDisplayContext.NONE);
            box.setOffset(new Vec3(0, 0.5, 0));
            box.setInterpolationDuration(1);
            box.setTeleportDuration(1);
            box.setLeftRotation(yawRotation(entity));
            holder.addElement(box);
            EntityAttachment.ofTicking(holder, entity);
        }

        private void update(PackageEntity entity) {
            ItemStack current = entity.getBox();
            if (!ItemStack.matches(current, shown)) {
                shown = current.copy();
                box.setItem(shown);
            }
            Quaternionf rotation = yawRotation(entity);
            if (!rotation.equals(box.getLeftRotation(), 1.0E-4f)) {
                box.setLeftRotation(rotation);
                box.startInterpolationIfDirty();
            }
        }

        private static Quaternionf yawRotation(PackageEntity entity) {
            return new Quaternionf().rotationY((float) Math.toRadians(-entity.getYRot()));
        }
    }
}
