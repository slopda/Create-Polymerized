package com.zurrtum.create.infrastructure.polymer;

import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerStaticCollision;
import eu.pb4.polymer.virtualentity.api.attachment.BlockAwareAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Brightness;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public abstract class PolymerLitModelHolder extends AdapterHolder {
    private final ServerLevel level;
    private final BlockPos pos;
    private Brightness brightness;
    private int lightAge;
    private List<VirtualElement> collisionCells;
    private List<AABB> collisionShape = List.of();

    public PolymerLitModelHolder(ServerLevel level, BlockPos pos) {
        this.level = level;
        this.pos = pos.immutable();
        brightness = PolymerBlockParts.neighbourBrightness(level, pos);
        collisionCells = List.of();
    }

    public final void staticCollision(BlockState state) {
        var boxes = PolymerStaticCollision.cells(state);
        if (boxes.equals(collisionShape)) return;
        collisionShape = boxes;
        for (VirtualElement cell : collisionCells) removeElement(cell);
        collisionCells = PolymerStaticCollision.add(this, level, pos, state);
    }

    @Override
    public void notifyUpdate(HolderAttachment.UpdateType type) {
        super.notifyUpdate(type);
        if (type == BlockAwareAttachment.BLOCK_STATE_UPDATE) {
            var attachment = BlockAwareAttachment.get(this);
            if (attachment != null) staticCollision(attachment.getBlockState());
        }
    }

    @Override
    public <T extends VirtualElement> T addElement(T element) {
        if (element instanceof DisplayElement display) {
            display.setBrightness(brightness);
        }
        return super.addElement(element);
    }

    @Override
    protected void onTick() {
        if (getWatchingPlayers().isEmpty()) return;
        if (++lightAge < 20) {
            return;
        }
        refreshLight();
    }

    @Override
    public boolean startWatching(ServerGamePacketListenerImpl player) {
        if (!getWatchingPlayers().contains(player)) refreshLight();
        return super.startWatching(player);
    }

    private void refreshLight() {
        if (!level.getServer().isSameThread())
            throw new IllegalStateException("Model watching must run on the server thread");
        lightAge = 0;
        Brightness updated = PolymerBlockParts.neighbourBrightness(level, pos);
        if (!updated.equals(brightness)) {
            brightness = updated;
            for (VirtualElement element : getElements()) {
                if (element instanceof DisplayElement display) {
                    display.setBrightness(brightness);
                }
            }
        }
    }
}
