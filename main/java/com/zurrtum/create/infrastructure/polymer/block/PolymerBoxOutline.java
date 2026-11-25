package com.zurrtum.create.infrastructure.polymer.block;

import com.zurrtum.create.infrastructure.polymer.AdapterHolder;
import com.zurrtum.create.infrastructure.polymer.train.PolymerTrackPlacementPreview;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PolymerBoxOutline {
    static final float LINE_WIDTH = 1 / 16f;

    private PolymerBoxOutline() {}

    public static ElementHolder show(ServerPlayer owner, AABB box, int color) {
        ElementHolder holder = new AdapterHolder();
        Vec3 origin = new Vec3(box.minX, box.minY, box.minZ);
        new ManualAttachment(holder, owner.level(), () -> origin);
        Vec3[] corners = new Vec3[8];
        for (int i = 0; i < 8; i++)
            corners[i] = new Vec3(
                    (i & 1) == 0 ? box.minX : box.maxX,
                    (i & 2) == 0 ? box.minY : box.maxY,
                    (i & 4) == 0 ? box.minZ : box.maxZ);
        for (int i = 0; i < 8; i++)
            for (int bit = 1; bit < 8; bit <<= 1)
                if ((i & bit) == 0)
                    PolymerTrackPlacementPreview.addLine(
                            holder,
                            origin,
                            new PolymerTrackPlacementPreview.Line(corners[i], corners[i | bit], LINE_WIDTH),
                            color);
        holder.startWatching(owner);
        holder.tick();
        return holder;
    }
}
