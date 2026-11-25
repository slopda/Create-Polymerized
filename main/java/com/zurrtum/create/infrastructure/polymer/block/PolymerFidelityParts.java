package com.zurrtum.create.infrastructure.polymer.block;

import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.*;

import com.google.gson.JsonObject;
import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.catnip.math.AngleHelper;
import com.zurrtum.create.content.decoration.placard.PlacardBlock;
import com.zurrtum.create.content.decoration.placard.PlacardBlockEntity;
import com.zurrtum.create.content.kinetics.waterwheel.LargeWaterWheelBlock;
import com.zurrtum.create.content.kinetics.waterwheel.LargeWaterWheelBlockEntity;
import com.zurrtum.create.content.kinetics.waterwheel.WaterWheelBlock;
import com.zurrtum.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import com.zurrtum.create.content.logistics.funnel.AbstractFunnelBlock;
import com.zurrtum.create.content.logistics.funnel.FunnelBlock;
import com.zurrtum.create.content.logistics.funnel.FunnelBlockEntity;
import com.zurrtum.create.content.logistics.tunnel.BeltTunnelBlockEntity;
import com.zurrtum.create.content.processing.burner.BlazeBurnerBlock;
import com.zurrtum.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.zurrtum.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.zurrtum.create.infrastructure.polymer.PolymerBlockProjections;
import com.zurrtum.create.infrastructure.polymer.equipment.PolymerDivingBoots;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerBacktankAir;
import dev.veeda.createpoly.PatchAssets;
import dev.veeda.createpoly.assets.CreateIds;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.imageio.ImageIO;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

final class PolymerFidelityParts {
    private static final Identifier SMALL_WHEEL = model("polymer/water_wheel_rotor");
    private static final Identifier SMALL_WHEEL_CASING = model("water_wheel/block");
    private static final Identifier LARGE_WHEEL = model("polymer/large_water_wheel");
    private static final Identifier LARGE_EXTENSION = model("polymer/large_water_wheel_extension");
    private static final Identifier FLAP = model("belt_tunnel/flap");
    private static final Identifier FUNNEL_FLAP = model("funnel/flap"), BELT_FUNNEL_FLAP = model("belt_funnel/flap");
    private static final Identifier INERT = model("blaze_burner/blaze/inert");
    private static final Identifier IDLE = model("blaze_burner/blaze/idle");
    private static final Identifier ACTIVE = model("blaze_burner/blaze/active");
    private static final Identifier SUPER = model("blaze_burner/blaze/super");
    private static final Identifier SUPER_ACTIVE = model("blaze_burner/blaze/super_active");
    private static final Identifier GOGGLES = model("blaze_burner/goggles");
    private static final Identifier SMALL_GOGGLES = model("blaze_burner/goggles_small");
    private static final Identifier HAT = model(CreateIds.id("entity/train_hat"));
    private static final Identifier LOGISTICS_HAT = model(CreateIds.id("entity/logistics_hat"));
    private static final Identifier RODS = model("polymer/burner_rods_small");
    private static final Identifier RODS2 = model("polymer/burner_rods_large");
    private static final Identifier SUPER_RODS = model("polymer/burner_super_rods_small");
    private static final Identifier SUPER_RODS2 = model("polymer/burner_super_rods_large");
    private static final Identifier FLAME = model("polymer/burner_flame");
    private static final Identifier FADING_FLAME = model("polymer/burner_fading_flame");
    private static final Identifier SUPER_FLAME = model("polymer/burner_super_flame");
    private static final Map<BlazeBurnerBlockEntity, BurnerAnimation> BURNER_ANIMATION = new WeakHashMap<>();

    static void registerAll() {
        PolymerBlockParts.<WaterWheelBlockEntity>register(
                PolymerFidelityParts::wheel, AllBlocks.WATER_WHEEL, AllBlocks.LARGE_WATER_WHEEL);
        PolymerBlockParts.<BeltTunnelBlockEntity>register(
                PolymerFidelityParts::tunnel, AllBlocks.ANDESITE_TUNNEL, AllBlocks.BRASS_TUNNEL);
        PolymerBlockParts.<FunnelBlockEntity>register(
                PolymerFidelityParts::funnel,
                AllBlocks.ANDESITE_FUNNEL,
                AllBlocks.BRASS_FUNNEL,
                AllBlocks.ANDESITE_BELT_FUNNEL,
                AllBlocks.BRASS_BELT_FUNNEL);
        PolymerBlockParts.<PlacardBlockEntity>register(PolymerFidelityParts::placard, AllBlocks.PLACARD);
        PolymerBlockParts.<BlazeBurnerBlockEntity>register(PolymerFidelityParts::burner, AllBlocks.BLAZE_BURNER);
        PolymerLinkBulbs.register();
        PolymerBacktankAir.register();
        PolymerDivingBoots.register();
        PolymerSpoutParts.register();
        PolymerFluidMachineParts.register();
        PolymerWhistleParts.register();
    }

    private static void wheel(WaterWheelBlockEntity be, BlockState state, float time, List<Part> out) {
        boolean large = be instanceof LargeWaterWheelBlockEntity;
        Direction facing = large ? axisOf(state).getPositive() : state.getValue(WaterWheelBlock.FACING);
        if (!large) {
            float x = facing == Direction.UP ? 0 : facing == Direction.DOWN ? 180 : 90;
            float y = switch (facing) {
                case EAST -> 90;
                case SOUTH -> 180;
                case WEST -> 270;
                default -> 0;
            };
            out.add(part(
                    SMALL_WHEEL_CASING,
                    rotateCentered(
                            new Quaternionf().rotateYXZ((float) Math.toRadians(-y), (float) Math.toRadians(-x), 0))));
        }
        Matrix4f pose = spin(
                        facing.getAxis(),
                        progress(be.getSpeed(), time) + rotationOffset(state, facing.getAxis(), be.getBlockPos()))
                .mul(rotateToFaceVertical(facing));
        if (large) pose.scale(2);
        Identifier wheel =
                large ? state.getValue(LargeWaterWheelBlock.EXTENSION) ? LARGE_EXTENSION : LARGE_WHEEL : SMALL_WHEEL;
        Identifier wood =
                PolymerWaterWheelWoods.model(wheel.getPath().substring("block/polymer/".length()), be.material);
        out.add(part(wood != null ? wood : wheel, pose).identified("water_wheel:rotor"));
    }

    private static void placard(PlacardBlockEntity be, BlockState state, float time, List<Part> out) {
        if (be.getHeldItem().isEmpty()) return;
        AttachFace face = state.getValue(PlacardBlock.FACE);
        float yaw = AngleHelper.horizontalAngle(state.getValue(PlacardBlock.FACING))
                + (face == AttachFace.CEILING ? 360 : 180);
        Matrix4f pose = new Matrix4f().translate(.5f, .5f, .5f).rotateY((float) Math.toRadians(yaw));
        if (face != AttachFace.WALL) pose.rotateX((float) (face == AttachFace.CEILING ? -Math.PI / 2 : Math.PI / 2));
        pose.translate(0, 0, .28125f).scale(be.getHeldItem().getItem() instanceof BlockItem ? .5f : .375f);
        out.add(itemPart(be.getHeldItem(), pose));
    }

    private static void tunnel(BeltTunnelBlockEntity be, BlockState state, float time, List<Part> out) {
        for (var flap : be.flaps.entrySet()) {
            Matrix4f base = spin(
                            Direction.UP,
                            AngleHelper.horizontalAngle(flap.getKey().getOpposite()))
                    .translate(.075f / 16, 0, 0);
            float f = flap.getValue().getValue();
            for (int segment = 0; segment < 4; segment++) {
                float intensity = segment == 3 ? 1.5f : segment + 1;
                float angle = (float) Math.sin((1 - Math.abs(f)) * Math.PI * intensity) * 30 * f * (f < 0 ? .5f : 1);
                Matrix4f pose = new Matrix4f(base)
                        .translate(0, 10f / 16, 1f / 16)
                        .rotateX((float) Math.toRadians(angle))
                        .translate(segment * -3.05f / 16, -10f / 16, -1f / 16);
                out.add(part(FLAP, pose));
            }
        }
    }

    private static void funnel(FunnelBlockEntity be, BlockState state, float time, List<Part> out) {
        if (!be.hasFlap() || !(state.getBlock() instanceof AbstractFunnelBlock block)) return;
        Identifier model = block instanceof FunnelBlock ? FUNNEL_FLAP : BELT_FUNNEL_FLAP;
        Matrix4f base = spin(
                        Direction.UP,
                        AngleHelper.horizontalAngle(block.getFacing(state).getOpposite()))
                .translate(.075f / 16, 0, -be.getFlapOffset());
        float f = be.flap.getValue();
        for (int segment = 0; segment < 4; segment++) {
            float intensity = segment == 3 ? 1.5f : segment + 1;
            float angle = (float) Math.sin((1 - Math.abs(f)) * Math.PI * intensity) * 30 * f * (f < 0 ? .5f : 1);
            Matrix4f pose = new Matrix4f(base)
                    .translate(0, 10f / 16, 9.5f / 16)
                    .rotateX((float) Math.toRadians(angle))
                    .translate(segment * -3.05f / 16, -10f / 16, -9.5f / 16);
            out.add(part(model, pose).identified("funnel:flap:" + segment));
        }
    }

    private static void burner(BlazeBurnerBlockEntity be, BlockState state, float time, List<Part> out) {
        HeatLevel heat = be.getHeatLevelForRender();
        if (heat == HeatLevel.NONE) return;
        boolean hot = heat.isAtLeast(HeatLevel.FADING);
        boolean above = hot && be.isValidBlockAbove();
        boolean seething = heat == HeatLevel.SEETHING;
        Direction facing = state.getValue(BlazeBurnerBlock.FACING);
        float facingAngle = (AngleHelper.horizontalAngle(facing) + 180) % 360;
        BurnerAnimation animationState =
                BURNER_ANIMATION.computeIfAbsent(be, ignored -> new BurnerAnimation(facingAngle));
        long tick = be.getLevel().getGameTime();
        long steps = animationState.lastTick == Long.MIN_VALUE ? 1 : Mth.clamp(tick - animationState.lastTick, 0, 20);
        animationState.lastTick = tick;
        for (long i = 0; i < steps; i++) {
            float target = above ? 1 : 0;
            animationState.head += Mth.clamp((target - animationState.head) * .25f, -.25f, .25f);
            float angleTarget = above ? facingAngle : animationState.angle;
            if (!above) {
                Player viewer = be.getLevel()
                        .getNearestPlayer(
                                be.getBlockPos().getX() + .5,
                                be.getBlockPos().getY() + .5,
                                be.getBlockPos().getZ() + .5,
                                64,
                                entity -> !entity.isInvisible() && !entity.isSpectator());
                if (viewer != null) {
                    double dx = viewer.getX() - (be.getBlockPos().getX() + .5),
                            dz = viewer.getZ() - (be.getBlockPos().getZ() + .5);
                    angleTarget = AngleHelper.deg(-Mth.atan2(dz, dx)) - 90;
                }
            }
            angleTarget = animationState.angle + AngleHelper.getShortestAngleDiff(animationState.angle, angleTarget);
            float delta = (angleTarget - animationState.angle) * (above ? .125f : .25f);
            animationState.angle += above ? delta : Mth.clamp(delta, -5, 5);
        }
        float animation = animationState.head * .175f;
        float phase = time / 16 + Math.floorMod(be.getBlockPos().hashCode(), 13);
        float offset = (float) Math.sin(phase) / (hot ? 64 : 16) - animation * .75f;
        Matrix4f yaw = spin(Direction.UP, animationState.angle);
        Identifier head = !hot
                ? INERT
                : seething ? above ? SUPER_ACTIVE : SUPER : above && heat.isAtLeast(HeatLevel.KINDLED) ? ACTIVE : IDLE;
        Matrix4f headPose = new Matrix4f(yaw).translate(0, offset, 0);
        out.add(part(head, headPose));
        if (be.goggles) out.add(part(hot ? GOGGLES : SMALL_GOGGLES, new Matrix4f(headPose).translate(0, .5f, 0)));
        if (be.hat || be.stockKeeper)
            out.add(part(
                    be.hat ? HAT : LOGISTICS_HAT,
                    new Matrix4f(yaw)
                            .translate(.5f, offset + (hot ? .75f : .5f), .5f)
                            .scale(hot ? 1 : .75f)));
        if (hot) {
            out.add(part(
                    seething ? SUPER_RODS : RODS,
                    new Matrix4f().translate(0, (float) Math.sin(phase + Math.PI) / 64 + animation + .125f, 0)));
            out.add(part(
                    seething ? SUPER_RODS2 : RODS2,
                    new Matrix4f().translate(0, (float) Math.sin(phase + Math.PI / 2) / 64 + animation - .1875f, 0)));
            if (above) out.add(part(seething ? SUPER_FLAME : heat == HeatLevel.FADING ? FADING_FLAME : FLAME, yaw));
        }
    }

    private static final class BurnerAnimation {
        float head;
        float angle;

        long lastTick = Long.MIN_VALUE;

        BurnerAnimation(float angle) {
            this.angle = angle;
        }
    }

    static void addAssets(ResourcePackBuilder builder) {
        for (HeatLevel heat : new HeatLevel[] {HeatLevel.FADING, HeatLevel.KINDLED, HeatLevel.SEETHING}) {
            boolean superheated = heat == HeatLevel.SEETHING;
            String sourceName = "blaze_burner_flame" + (superheated ? "_superheated" : "") + "_scroll";
            String texture = "block/polymer/burner_flame_" + heat.getSerializedName();
            try (var input = PatchAssets.open("assets/create/textures/block/" + sourceName + ".png")) {
                if (input == null) throw new IllegalStateException("Missing flame sprite " + sourceName);
                var source = ImageIO.read(input);
                int width = source.getWidth() / 2, height = source.getHeight() / 2;
                int numerator = 2 + heat.ordinal();
                int frames = 128 / Integer.lowestOneBit(numerator);
                var strip = new BufferedImage(width, height * frames, BufferedImage.TYPE_INT_ARGB);
                for (int f = 0; f < frames; f++) {
                    int dx = (f * numerator % 128) * width / 128;
                    int dy = (f * numerator % 64) * height / 64;
                    for (int y = 0; y < height; y++)
                        for (int x = 0; x < width; x++) strip.setRGB(x, f * height + y, source.getRGB(x + dx, y + dy));
                }
                builder.addData("assets/create/textures/" + texture + ".png", PngCodec.encode(strip));
                var animation = new JsonObject();
                animation.addProperty("frametime", 1);
                animation.addProperty("width", width);
                animation.addProperty("height", height);
                var meta = new JsonObject();
                meta.add("animation", animation);
                builder.addStringData("assets/create/textures/" + texture + ".png.mcmeta", meta.toString());
                var definition = PolymerBlockProjections.readAssetJson(
                                "/assets/create/models/block/polymer/burner_flame.json")
                        .deepCopy();
                var textures = definition.getAsJsonObject("textures");
                for (String key : List.copyOf(textures.keySet())) textures.addProperty(key, "create:" + texture);
                Identifier mesh = superheated ? SUPER_FLAME : heat == HeatLevel.FADING ? FADING_FLAME : FLAME;
                builder.addStringData("assets/create/models/" + mesh.getPath() + ".json", definition.toString());
            } catch (IOException error) {
                throw new IllegalStateException("Cannot build Burner flame", error);
            }
        }
    }
}
