package com.zurrtum.create.infrastructure.polymer.equipment;

import com.zurrtum.create.content.equipment.armor.BacktankItem;
import com.zurrtum.create.infrastructure.polymer.AdapterHolder;
import com.zurrtum.create.infrastructure.polymer.PolymerPartItems;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class PolymerBacktankWear {
    private static final int COG_STEP = 10;
    private static final Map<UUID, Worn> WORN = new HashMap<>();

    private PolymerBacktankWear() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PolymerBacktankWear::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPING.register(
                server -> new HashMap<>(WORN).keySet().forEach(PolymerBacktankWear::remove));
    }

    private static void remove(UUID id) {
        Worn worn = WORN.remove(id);
        if (worn != null) worn.destroy();
    }

    private static void tick(MinecraftServer server) {
        WORN.keySet().removeIf(id -> {
            Worn worn = WORN.get(id);
            LivingEntity wearer = worn.player instanceof ServerPlayer
                    ? NativePresentationPolicy.onlinePlayer(server.getPlayerList(), id)
                    : worn.player;
            if (wearer != null
                    && !wearer.isRemoved()
                    && wearer == worn.player
                    && metal(wearer) == worn.metal
                    && wearer.getPose() != Pose.SLEEPING) return false;
            worn.destroy();
            return true;
        });
        for (ServerPlayer player : NativePresentationPolicy.onlinePlayers(server.getPlayerList())) wear(player, server);
        if (server.getTickCount() % 20 == 0)
            for (var level : server.getAllLevels())
                for (var stand :
                        level.getEntities(EntityTypeTest.forClass(ArmorStand.class), stand -> metal(stand) != null))
                    wear(stand, server);
        for (Worn worn : new ArrayList<>(WORN.values()))
            if (!(worn.player instanceof ServerPlayer)) worn.update(server.getTickCount());
    }

    private static void wear(LivingEntity wearer, MinecraftServer server) {
        String metal = metal(wearer);
        if (metal == null || wearer.isRemoved() || wearer.getPose() == Pose.SLEEPING) return;
        Worn worn = WORN.computeIfAbsent(wearer.getUUID(), id -> new Worn(wearer, metal));
        if (wearer instanceof ServerPlayer) worn.update(server.getTickCount());
    }

    private static String metal(LivingEntity player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof BacktankItem)) return null;
        return BuiltInRegistries.ITEM.getKey(chest.getItem()).getPath().startsWith("netherite")
                ? "netherite"
                : "copper";
    }

    private static final class Worn {
        final LivingEntity player;
        final String metal;
        final ElementHolder holder = new AdapterHolder();
        final ItemDisplayElement body, shaft, cogs;
        boolean crouching;
        int lastCog = Integer.MIN_VALUE;
        Object lastPose;

        private Object standPose() {
            return player instanceof ArmorStand stand ? List.of(stand.getBodyPose(), stand.isSmall()) : null;
        }

        Worn(LivingEntity player, String metal) {
            this.player = player;
            this.metal = metal;
            bodyYaw = player.getYRot();
            lastPos = player.position();
            body = element(metal + "_body");
            shaft = element(metal + "_shaft");
            cogs = element(metal + "_cogs");
            crouching = player.isCrouching();
            pose(0, true);
            for (ItemDisplayElement element : new ItemDisplayElement[] {body, shaft, cogs})
                element.setYaw(player.getYRot());
            EntityAttachment.ofTicking(holder, player);
            if (player instanceof ServerPlayer self) holder.startWatching(self.connection);
            VirtualEntityUtils.addVirtualPassenger(player, body.getEntityId(), shaft.getEntityId(), cogs.getEntityId());
        }

        void destroy() {
            VirtualEntityUtils.removeVirtualPassenger(
                    player, body.getEntityId(), shaft.getEntityId(), cogs.getEntityId());
            holder.destroy();
        }

        private ItemDisplayElement element(String name) {
            ItemStack stack = new ItemStack(Items.PAPER);
            PolymerPartItems.setItemModel(stack, CreateIds.id("polymer_part/backtank_worn/" + name));
            ItemDisplayElement element = new ItemDisplayElement(stack);
            element.setInvisible(true);
            element.setTeleportDuration(1);
            holder.addElement(element);
            return element;
        }

        float bodyYaw;

        Vec3 lastPos;

        private float bodyYaw() {
            if (!(player instanceof ServerPlayer)) return player.getYRot();
            float head = player.getYRot();
            Vec3 pos = player.position();
            double dx = pos.x - lastPos.x, dz = pos.z - lastPos.z;
            lastPos = pos;
            if (dx * dx + dz * dz > 0.0025) {
                float walk = (float) (Mth.atan2(dz, dx) * 180 / Math.PI) - 90;
                if (Math.abs(Mth.wrapDegrees(walk - head)) > 95) walk += 180;
                bodyYaw += Mth.wrapDegrees(walk - bodyYaw) * 0.3f;
            }
            float delta = Mth.wrapDegrees(head - bodyYaw);
            if (delta > 50) bodyYaw = head - 50;
            if (delta < -50) bodyYaw = head + 50;
            return bodyYaw;
        }

        void update(int now) {
            float yaw = bodyYaw();
            for (ItemDisplayElement element : new ItemDisplayElement[] {body, shaft, cogs}) element.setYaw(yaw);
            boolean crouch = player.isCrouching();
            int step = now / COG_STEP;
            Object pose = standPose();
            if (crouch != crouching || step != lastCog || !Objects.equals(pose, lastPose)) {
                boolean snap = crouch != crouching || !Objects.equals(pose, lastPose);
                crouching = crouch;
                lastPose = pose;
                lastCog = step;
                pose(step * COG_STEP, snap);
            }
        }

        void pose(int ticks, boolean snap) {
            rigid(
                    body,
                    transform(new Matrix4d()
                            .rotateAround(new Quaterniond().rotationY(Math.PI), .5, .5, .5)
                            .rotateAround(new Quaterniond().rotationY(Math.PI), .5, .5, .5)));
            rigid(shaft, transform(new Matrix4d().translate(0, -0.1875, 0)));
            Matrix4d spin = new Matrix4d()
                    .rotateAround(new Quaterniond().rotationY(Math.PI), .5, .5, .5)
                    .translate(0, .40625, .6875)
                    .rotateX(Math.toRadians(2.0 * ticks % 360))
                    .translate(0, -.40625, -.6875);
            rigid(cogs, transform(spin));
            int duration = snap ? 0 : COG_STEP;
            for (ItemDisplayElement element : new ItemDisplayElement[] {body, shaft, cogs}) {
                element.setInterpolationDuration(duration);
                element.startInterpolationIfDirty();
            }
        }

        private static void rigid(ItemDisplayElement element, Matrix4f matrix) {
            element.setTranslation(matrix.getTranslation(new Vector3f()));
            element.setLeftRotation(matrix.getNormalizedRotation(new Quaternionf()));
            element.setScale(new Vector3f(1, 1, 1));
            element.setRightRotation(new Quaternionf());
        }

        private Matrix4f transform(Matrix4d inner) {
            double anchor = player.getPassengerRidingPosition(player).y - player.getY();
            Matrix4d m = new Matrix4d()
                    .translate(0, -anchor, 0)
                    .rotateY(Math.PI)
                    .scale(player instanceof ArmorStand stand && stand.isSmall() ? .5 : 1)
                    .translate(0, crouching ? -0.125 : 0, 0)
                    .scale(-1, -1, 1)
                    .translate(0, -1.501, 0);
            if (crouching) m.translate(0, 3.2 / 16, 0).rotateX(0.5);
            if (player instanceof ArmorStand stand) {
                var body = stand.getBodyPose();
                m.rotate(new Quaterniond()
                        .rotationZYX(Math.toRadians(body.z()), Math.toRadians(body.y()), Math.toRadians(body.x())));
            }
            m.translate(-0.5, 0.625, 1.0)
                    .scale(1, -1, -1)
                    .mul(inner)
                    .translate(.5, .5, .5)
                    .rotateY(Math.PI);
            return new Matrix4f(m);
        }
    }

    public static void addAssets(ResourcePackBuilder builder) {
        for (String metal : new String[] {"copper", "netherite"}) {
            item(builder, metal + "_body", "create:block/polymer/" + metal + "_backtank");
            item(builder, metal + "_shaft", "create:block/polymer/" + metal + "_backtank_block_shaft_input");
            item(builder, metal + "_cogs", "create:block/polymer/" + metal + "_backtank_block_cogs");
        }
    }

    private static void item(ResourcePackBuilder builder, String name, String model) {
        builder.addStringData(
                "assets/create/items/polymer_part/backtank_worn/" + name + ".json",
                "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"" + model + "\"}}");
    }
}
