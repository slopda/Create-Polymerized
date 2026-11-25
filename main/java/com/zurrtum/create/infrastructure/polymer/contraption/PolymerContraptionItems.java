package com.zurrtum.create.infrastructure.polymer.contraption;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class PolymerContraptionItems {
    static void tick(List<PolymerContraptionPhysics.Surface> surfaces) {
        Set<ItemEntity> handled = new HashSet<>();
        for (var surface : surfaces) {
            var owner = surface.entity();
            if (!PolymerContraptionFloors.owns(owner)
                    || owner.isRemoved()
                    || !(owner.level() instanceof ServerLevel level)
                    || level.tickRateManager().isEntityFrozen(owner)
                    || Math.abs(surface.current().y().y - 1) > 1e-6
                    || surface.cells().isEmpty()) continue;
            AABB region = surface.cells().getFirst().bounds();
            for (var cell : surface.cells()) region = region.minmax(cell.bounds());
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, region.inflate(.35))) {
                if (handled.contains(item)
                        || !item.isAlive()
                        || item.isPassenger()
                        || item.noPhysics
                        || level.tickRateManager().isEntityFrozen(item)) continue;
                var contactAge = owner.collidingEntities.get(item);
                if (contactAge != null && contactAge.intValue() == 0) {
                    handled.add(item);
                    continue;
                }
                PolymerContraptionPhysics.Cell best = null;
                for (var cell : surface.cells()) {
                    double tolerance = Math.min(.3, Math.abs(cell.bounds().maxY - cell.previousTop()) + .05);
                    if (!PolymerContraptionPhysics.supports(item.getBoundingBox(), cell.bounds(), tolerance)) continue;
                    if (best == null
                            || Math.abs(item.getY() - cell.bounds().maxY) < Math.abs(item.getY() - best.bounds().maxY))
                        best = cell;
                }
                if (best != null && carry(item, surface, best)) handled.add(item);
            }
        }
    }

    static boolean carry(
            ItemEntity item, PolymerContraptionPhysics.Surface surface, PolymerContraptionPhysics.Cell cell) {
        if (!item.onGround() && item.getDeltaMovement().y > .02) return false;
        Vec3 motion = surface.current().motionFrom(surface.previous(), item.position());
        if (!Double.isFinite(motion.lengthSqr()) || motion.lengthSqr() > .64) return false;
        item.move(MoverType.SELF, motion.multiply(1, 0, 1));
        item.setPos(item.getX(), cell.bounds().maxY, item.getZ());
        item.setDeltaMovement(item.getDeltaMovement().multiply(.5, 1, .5));
        item.setOnGround(true);
        item.fallDistance = 0;
        item.syncVelocity = true;
        item.needsSync = true;
        surface.entity().registerColliding(item);
        return true;
    }
}
