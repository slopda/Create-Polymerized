package com.zurrtum.create.infrastructure.polymer.train;

import com.google.gson.*;
import com.zurrtum.create.content.contraptions.AbstractContraptionEntity;
import com.zurrtum.create.content.trains.entity.CarriageContraption;
import com.zurrtum.create.content.trains.entity.CarriageContraptionEntity;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerContraptionPassengers;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerContraptionVisuals;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.io.*;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class PolymerTrainHats {
    private static final float RAD = (float) Math.PI / 180;
    private static final Map<String, Profile> PROFILES = profiles();
    private static Identifier model;
    private final ElementHolder holder;
    private final AbstractContraptionEntity owner;
    private final Display.ItemDisplay nativeHat;
    private final Map<Integer, ItemDisplayElement> hats = new HashMap<>();
    private volatile Map<Integer, Link> links = Map.of();

    public record Link(int hat, List<Integer> children) {}

    private record Profile(Matrix4f head, float height, float width, float x, float y, float z, float scale) {}

    public static void register() {
        model = PolymerBlockParts.model(Identifier.parse("create:entity/train_hat"));
    }

    public PolymerTrainHats(ElementHolder holder, AbstractContraptionEntity owner) {
        this.holder = holder;
        this.owner = owner;
        nativeHat = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, owner.level());
    }

    static boolean eligible(AbstractContraptionEntity owner, Entity passenger) {
        if (!(passenger instanceof LivingEntity living)
                || passenger instanceof Player
                || passenger instanceof ArmorStand
                || passenger.getType() == EntityTypes.SHULKER
                || !living.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return false;
        if (!(owner instanceof CarriageContraptionEntity carriage)
                || !carriage.hasSchedule()
                || !(owner.getContraption() instanceof CarriageContraption contraption)) return false;
        var seat = contraption.getSeatOf(passenger.getUUID());
        return seat != null && contraption.conductorSeats.containsKey(seat);
    }

    public void update() {
        Set<Integer> live = new HashSet<>();
        Map<Integer, Link> snapshot = new HashMap<>();
        for (Entity passenger : owner.getPassengers()) {
            if (!(passenger instanceof LivingEntity living) || !eligible(owner, passenger)) continue;
            int id = passenger.getId();
            live.add(id);
            boolean fresh = !hats.containsKey(id);
            Vec3 rideOffset = living.getPassengerRidingPosition(nativeHat)
                    .subtract(living.position())
                    .subtract(nativeHat.getVehicleAttachmentPoint(living));
            Vec3 at = living.position().add(rideOffset).subtract(owner.position());
            Matrix4f pose = new Matrix4f()
                    .translate((float) -rideOffset.x, (float) -rideOffset.y, (float) -rideOffset.z)
                    .mul(headPose(living));
            var light = PolymerContraptionVisuals.light(living.level(), living.getEyePosition());
            var hat = hats.computeIfAbsent(id, key -> {
                var display = new ItemDisplayElement(PolymerBlockParts.displayItem(model));
                display.setItemDisplayContext(ItemDisplayContext.NONE);
                display.setInterpolationDuration(1);
                display.setTeleportDuration(1);
                display.setOffset(at);
                PolymerContraptionPassengers.applyDisplayPose(display, pose);
                display.setBrightness(light);
                return holder.addElement(display);
            });
            hat.setOffset(at);
            PolymerContraptionPassengers.applyDisplayPose(hat, pose);
            hat.startInterpolationIfDirty();
            if (!light.equals(hat.getBrightness())) hat.setBrightness(light);
            var link = new Link(
                    hat.getEntityId(),
                    living.getPassengers().stream().map(Entity::getId).toList());
            snapshot.put(id, link);
            if (fresh) sendLink(id, link, holder::sendPacket);
        }
        for (var iterator = hats.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            if (live.contains(entry.getKey())) continue;
            Link old = links.get(entry.getKey());
            if (old != null)
                holder.sendPacket(VirtualEntityUtils.createClientboundSetPassengersPacket(
                        entry.getKey(),
                        old.children.stream().mapToInt(Integer::intValue).toArray()));
            holder.removeElement(entry.getValue());
            iterator.remove();
        }
        links = Map.copyOf(snapshot);
    }

    public void remove(Entity passenger) {
        int id = passenger.getId();
        var hat = hats.remove(id);
        if (hat == null) return;
        holder.sendPacket(VirtualEntityUtils.createClientboundSetPassengersPacket(
                id, passenger.getPassengers().stream().mapToInt(Entity::getId).toArray()));
        holder.removeElement(hat);
        var remaining = new HashMap<>(links);
        remaining.remove(id);
        links = Map.copyOf(remaining);
    }

    public Link link(int rider) {
        return links.get(rider);
    }

    public void link(int rider, Consumer<Packet<ClientGamePacketListener>> packets) {
        var link = links.get(rider);
        if (link != null) sendLink(rider, link, packets);
    }

    private static void sendLink(int rider, Link link, Consumer<Packet<ClientGamePacketListener>> packets) {
        int[] children = new int[link.children.size() + 1];
        for (int i = 0; i < link.children.size(); i++) children[i] = link.children.get(i);
        children[children.length - 1] = link.hat;
        packets.accept(VirtualEntityUtils.createClientboundSetPassengersPacket(rider, children));
    }

    static Matrix4f headPose(LivingEntity living) {
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()).toString();
        Profile p = PROFILES.get(id + (living.isBaby() ? "/baby" : ""));
        if (p == null)
            return new Matrix4f()
                    .translate(0, living.getEyeHeight() + living.getBbWidth() * .15f, 0)
                    .rotateY(-living.getYHeadRot() * RAD)
                    .rotateX(living.getXRot() * RAD)
                    .scale(Math.max(.3f, Math.min(1.5f, living.getBbWidth())))
                    .rotateX(-8.5f * RAD);
        float scale = p.width / 8 * p.scale;
        return new Matrix4f()
                .scale(living.getScale())
                .translate(0, 1.501f, 0)
                .rotateY((180 - living.yBodyRot) * RAD)
                .scale(-1, -1, 1)
                .mul(p.head)
                .rotateY((living.getYHeadRot() - living.yBodyRot) * RAD)
                .rotateX(living.getXRot() * RAD)
                .translate(p.x / 16, (p.y - p.height) / 16, p.z / 16)
                .scale(scale, -scale, -scale)
                .translate(0, -2.25f / 16, 0)
                .rotateX(-8.5f * RAD);
    }

    private static Map<String, Profile> profiles() {
        try (var stream = PolymerTrainHats.class.getResourceAsStream("/assets/createpoly/train_hat_profiles.json")) {
            if (stream == null) throw new IllegalStateException("Missing verified train hat profiles");
            var data = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            Map<String, Profile> result = new HashMap<>();
            for (var entry : data.entrySet()) {
                var p = entry.getValue().getAsJsonObject();
                var matrix = p.getAsJsonArray("head");
                float[] m = new float[16];
                for (int i = 0; i < 16; i++) m[i] = matrix.get(i).getAsFloat();
                var offset = p.getAsJsonArray("offset");
                result.put(
                        entry.getKey(),
                        new Profile(
                                new Matrix4f().set(m),
                                p.get("height").getAsFloat(),
                                p.get("width").getAsFloat(),
                                offset.get(0).getAsFloat(),
                                offset.get(1).getAsFloat(),
                                offset.get(2).getAsFloat(),
                                p.get("scale").getAsFloat()));
            }
            return Map.copyOf(result);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
