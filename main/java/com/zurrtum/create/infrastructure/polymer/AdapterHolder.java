package com.zurrtum.create.infrastructure.polymer;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

public class AdapterHolder extends ElementHolder {
    private Set<VirtualElement> scaled;

    protected float displayScale() {
        return getAttachment() instanceof ChunkAttachment ? PolymerAdapterConfig.blockScale() : 1;
    }

    private void scaleDisplays() {
        for (VirtualElement element : getElements()) scale(element);
    }

    private void scale(VirtualElement element) {
        float scale = displayScale();
        if (scale == 1 || !(element instanceof DisplayElement display)) return;
        if (scaled == null) scaled = Collections.newSetFromMap(new IdentityHashMap<>());
        if (scaled.add(display)) display.setViewRange(display.getViewRange() * scale);
    }

    @Override
    public <T extends VirtualElement> T addElement(T element) {
        if (getAttachment() != null) scale(element);
        return super.addElement(element);
    }

    @Override
    public void removeElement(VirtualElement element) {
        if (scaled != null) scaled.remove(element);
        super.removeElement(element);
    }

    @Override
    public boolean startWatching(ServerGamePacketListenerImpl player) {
        if (!PolymerHolderRange.allows(this, player.player)) return false;
        scaleDisplays();
        return super.startWatching(player);
    }
}
