package com.zurrtum.create.infrastructure.polymer.collision;

import com.google.gson.JsonObject;
import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.content.contraptions.actors.contraptionControls.ContraptionControlsBlock;
import com.zurrtum.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.zurrtum.create.content.contraptions.chassis.StickerBlock;
import com.zurrtum.create.content.contraptions.mounted.CartAssemblerBlock;
import com.zurrtum.create.content.decoration.MetalScaffoldingBlock;
import com.zurrtum.create.content.decoration.bracket.BracketBlock;
import com.zurrtum.create.content.decoration.girder.GirderBlock;
import com.zurrtum.create.content.decoration.placard.PlacardBlock;
import com.zurrtum.create.content.decoration.slidingDoor.SlidingDoorBlock;
import com.zurrtum.create.content.equipment.clipboard.ClipboardBlock;
import com.zurrtum.create.content.fluids.pipes.FluidPipeBlock;
import com.zurrtum.create.content.fluids.pipes.GlassFluidPipeBlock;
import com.zurrtum.create.content.fluids.pipes.SmartFluidPipeBlock;
import com.zurrtum.create.content.fluids.pipes.valve.FluidValveBlock;
import com.zurrtum.create.content.kinetics.clock.CuckooClockBlock;
import com.zurrtum.create.content.kinetics.crank.ValveHandleBlock;
import com.zurrtum.create.content.kinetics.flywheel.FlywheelBlock;
import com.zurrtum.create.content.kinetics.gantry.GantryShaftBlock;
import com.zurrtum.create.content.kinetics.simpleRelays.CogWheelBlock;
import com.zurrtum.create.content.kinetics.simpleRelays.ShaftBlock;
import com.zurrtum.create.content.kinetics.steamEngine.SteamEngineBlock;
import com.zurrtum.create.content.logistics.funnel.BeltFunnelBlock;
import com.zurrtum.create.content.logistics.funnel.FunnelBlock;
import com.zurrtum.create.content.logistics.packagePort.postbox.PostboxBlock;
import com.zurrtum.create.content.redstone.link.RedstoneLinkBlock;
import com.zurrtum.create.content.redstone.nixieTube.NixieTubeBlock;
import com.zurrtum.create.content.trains.track.TrackBlock;
import com.zurrtum.create.infrastructure.polymer.AdapterHolder;
import com.zurrtum.create.infrastructure.polymer.PolymerBedrockCarriers;
import com.zurrtum.create.infrastructure.polymer.PolymerDisplays;
import com.zurrtum.create.infrastructure.polymer.PolymerLitModelHolder;
import com.zurrtum.create.infrastructure.polymer.PolymerReach;
import com.zurrtum.create.infrastructure.polymer.block.PolymerDecorativeCulling;
import com.zurrtum.create.infrastructure.polymer.block.PolymerKineticVisuals;
import com.zurrtum.create.infrastructure.polymer.effects.PolymerBlockParticles;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerDialogService;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.SimpleEntityElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public final class PolymerStaticCollision {
    static final int CELL_LIMIT = 512;
    private static final Map<BlockState, List<AABB>> CELLS = new IdentityHashMap<>();
    private static final Map<BlockState, Boolean> EXTENDED = new IdentityHashMap<>();
    private static BlockState dryScaffold, wetScaffold;
    private static BlockState horizontalScaffold;
    private static BlockState cuckooCarrier;
    static final BlockState TRACK = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 2);

    public static boolean targets(BlockState state) {
        return state.getBlock() instanceof MetalScaffoldingBlock
                || state.getBlock() instanceof BracketBlock
                || state.getBlock() instanceof CuckooClockBlock
                || state.getBlock() instanceof SmartFluidPipeBlock
                || extended(state);
    }

    private static boolean extended(BlockState state) {
        boolean previousFamily = state.getBlock() instanceof FluidPipeBlock
                || state.getBlock() instanceof GirderBlock
                || state.getBlock() instanceof GantryShaftBlock
                || state.getBlock() instanceof FunnelBlock
                || state.getBlock() instanceof BeltFunnelBlock;
        if (!previousFamily && !stateDefinedFamily(state)) return false;
        return EXTENDED.computeIfAbsent(state, source -> {
            if (!previousFamily && PolymerBlockCollision.typeFor(source) != null) return false;
            for (AABB box : PolymerBlockCollision.shape(source).toAabbs())
                for (double value : new double[] {box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ})
                    if (Math.abs(Math.rint(value * 16) / 16 - value) > 1e-8) return false;
            if (!previousFamily) {
                List<AABB> boxes = tile(PolymerBlockCollision.shape(source));
                if (boxes.size() > 64) return false;
            }
            return true;
        });
    }

    private static boolean stateDefinedFamily(BlockState state) {
        return state.getBlock() instanceof CartAssemblerBlock
                || state.getBlock() instanceof GlassFluidPipeBlock
                || state.getBlock() instanceof FluidValveBlock
                || state.getBlock() instanceof CogWheelBlock
                || state.getBlock() instanceof ShaftBlock
                || state.getBlock() instanceof FlywheelBlock
                || state.getBlock() instanceof SteamEngineBlock
                || state.getBlock() instanceof RedstoneLinkBlock
                || state.getBlock() instanceof NixieTubeBlock
                || state.getBlock() instanceof ControlsBlock
                || state.getBlock() instanceof ContraptionControlsBlock
                || state.getBlock() instanceof PostboxBlock
                || state.getBlock() instanceof PlacardBlock
                || state.getBlock() instanceof SlidingDoorBlock
                || state.getBlock() instanceof ValveHandleBlock;
    }

    public static void register() {
        PolymerStaticProxyRange.register();
        dryScaffold = PolymerBlockResourceUtils.requestEmpty(BlockModelType.SCAFFOLDING_TOP);
        wetScaffold = PolymerBlockResourceUtils.requestEmpty(BlockModelType.SCAFFOLDING_TOP_WATERLOGGED);
        horizontalScaffold = PolymerBlockResourceUtils.requestEmpty(BlockModelType.VINES);
        if (dryScaffold == null || wetScaffold == null || horizontalScaffold == null)
            throw new IllegalStateException("No native scaffolding carriers");
        Identifier cuckooParticle = PolymerBlockParticles.particleModel(AllBlocks.CUCKOO_CLOCK.defaultBlockState());
        if (cuckooParticle != null)
            cuckooCarrier =
                    PolymerBlockResourceUtils.requestBlock(BlockModelType.CACTUS, PolymerBlockModel.of(cuckooParticle));
        for (var block : BuiltInRegistries.BLOCK)
            for (BlockState state : block.getStateDefinition().getPossibleStates())
                if (targets(state) || block instanceof TrackBlock) cells(state);
        for (var block : BuiltInRegistries.BLOCK)
            if (block instanceof SlidingDoorBlock)
                BlockWithElementHolder.registerOverlay(block, new BlockWithElementHolder() {
                    @Override
                    public ElementHolder createElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
                        PolymerLitModelHolder holder = new PolymerLitModelHolder(level, pos) {};
                        holder.staticCollision(state);
                        return holder;
                    }

                    @Override
                    public boolean tickElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
                        return true;
                    }
                });
        // A native host must never become a PolymerBlock (native wire ID preservation).
        BlockWithElementHolder.registerOverlay(Blocks.SNOW, new BlockWithElementHolder() {
            @Override
            public @Nullable ElementHolder createElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
                if (state.getValue(SnowLayerBlock.LAYERS) != 2) return null;
                ElementHolder holder = new AdapterHolder();
                ItemDisplayElement display = PolymerDisplays.item(
                        PolymerKineticVisuals.rotatingDisplayItem(Identifier.parse("create:polymer/native_snow_two")),
                        ItemDisplayContext.NONE);
                PolymerDecorativeCulling.inBlock(display);
                holder.addElement(display);
                return holder;
            }
        });
    }

    public static @Nullable BlockState carrier(BlockState source) {
        if (source.getBlock() instanceof StickerBlock && source.getValue(BlockStateProperties.FACING) == Direction.UP)
            return Blocks.SLIME_BLOCK.defaultBlockState();
        boolean wet = !source.getFluidState().isEmpty();
        if (source.getBlock() instanceof MetalScaffoldingBlock) {
            if (source.getValue(BlockStateProperties.BOTTOM))
                return wet ? Blocks.WATER.defaultBlockState() : horizontalScaffold;
            return wet ? wetScaffold : dryScaffold;
        }
        if (source.getBlock() instanceof SmartFluidPipeBlock
                || source.getBlock() instanceof ClipboardBlock
                || source.getBlock() instanceof BracketBlock)
            return Blocks.SCULK_VEIN.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, wet);
        if (source.getBlock() instanceof CuckooClockBlock)
            return wet
                    ? Blocks.WATER.defaultBlockState()
                    : cuckooCarrier != null ? cuckooCarrier : Blocks.AIR.defaultBlockState();
        if (extended(source))
            return Blocks.SCULK_VEIN.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, wet);
        if (source.getBlock() instanceof TrackBlock) {
            if (PolymerBlockCollision.shape(source).isEmpty())
                return wet ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
            return wet ? Blocks.WATER.defaultBlockState() : TRACK;
        }
        return null;
    }

    public static List<AABB> cells(BlockState state) {
        return CELLS.computeIfAbsent(state, source -> {
            VoxelShape shape;
            if (source.getBlock() instanceof MetalScaffoldingBlock) {
                if (!source.getValue(BlockStateProperties.BOTTOM)) return List.of();
                shape = PolymerBlockCollision.shape(source);
            } else if (source.getBlock() instanceof TrackBlock) {
                if (source.getFluidState().isEmpty()) return List.of();
                shape = PolymerBlockCollision.shape(source);
            } else if (source.getBlock() instanceof CuckooClockBlock
                    && cuckooCarrier != null
                    && source.getFluidState().isEmpty()) {
                shape = Shapes.join(
                        PolymerBlockCollision.shape(source),
                        cuckooCarrier.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
                        BooleanOp.ONLY_FIRST);
            } else if (targets(source)) shape = PolymerBlockCollision.shape(source);
            else return List.of();
            return tile(shape);
        });
    }

    static List<AABB> tile(VoxelShape shape) {
        if (shape.isEmpty()) return List.of();
        AABB bounds = shape.bounds();
        int ox = grid(bounds.minX), oy = grid(bounds.minY), oz = grid(bounds.minZ);
        int nx = grid(bounds.maxX) - ox, ny = grid(bounds.maxY) - oy, nz = grid(bounds.maxZ) - oz;
        boolean[][][] occupied = new boolean[nx][ny][nz];
        for (AABB box : shape.toAabbs()) {
            for (int x = grid(box.minX) - ox; x < grid(box.maxX) - ox; x++)
                for (int y = grid(box.minY) - oy; y < grid(box.maxY) - oy; y++)
                    for (int z = grid(box.minZ) - oz; z < grid(box.maxZ) - oz; z++) occupied[x][y][z] = true;
        }
        record Cube(int x, int y, int z, int side) {}
        List<Cube> candidates = new ArrayList<>();
        int[][][] full = prefix(occupied);
        for (int side = Math.min(nx, Math.min(ny, nz)); side >= 1; side--)
            for (int x = 0; x + side <= nx; x++)
                for (int y = 0; y + side <= ny; y++)
                    for (int z = 0; z + side <= nz; z++)
                        if (volume(full, x, y, z, side) == side * side * side) candidates.add(new Cube(x, y, z, side));
        List<AABB> cells = new ArrayList<>();
        while (true) {
            int[][][] remaining = prefix(occupied);
            Cube best = null;
            int covered = 0;
            for (Cube cube : candidates) {
                if (best != null && cube.side < best.side) break;
                int count = volume(remaining, cube.x, cube.y, cube.z, cube.side);
                if (count > covered) {
                    best = cube;
                    covered = count;
                }
            }
            if (best == null) break;
            double x = (ox + best.x) / 16.0,
                    y = (oy + best.y) / 16.0,
                    z = (oz + best.z) / 16.0,
                    side = best.side / 16.0;
            cells.add(new AABB(x, y, z, x + side, y + side, z + side));
            if (cells.size() > CELL_LIMIT) throw new IllegalStateException("Static collision cell budget exceeded");
            for (int ix = best.x; ix < best.x + best.side; ix++)
                for (int iy = best.y; iy < best.y + best.side; iy++)
                    for (int iz = best.z; iz < best.z + best.side; iz++) occupied[ix][iy][iz] = false;
        }
        VoxelShape union = Shapes.empty();
        for (AABB cell : cells) union = Shapes.or(union, Shapes.create(cell));
        if (!PolymerBlockCollision.sameShape(shape, union))
            throw new IllegalStateException("Cell union differs from source");
        return List.copyOf(cells);
    }

    private static int grid(double value) {
        int coordinate = (int) Math.round(value * 16);
        if (Math.abs(coordinate / 16.0 - value) > 1e-8)
            throw new IllegalStateException("Non-grid static shape: " + value);
        return coordinate;
    }

    private static int[][][] prefix(boolean[][][] cells) {
        int nx = cells.length, ny = cells[0].length, nz = cells[0][0].length;
        int[][][] p = new int[nx + 1][ny + 1][nz + 1];
        for (int x = 1; x <= nx; x++)
            for (int y = 1; y <= ny; y++)
                for (int z = 1; z <= nz; z++)
                    p[x][y][z] = (cells[x - 1][y - 1][z - 1] ? 1 : 0)
                            + p[x - 1][y][z]
                            + p[x][y - 1][z]
                            + p[x][y][z - 1]
                            - p[x - 1][y - 1][z]
                            - p[x - 1][y][z - 1]
                            - p[x][y - 1][z - 1]
                            + p[x - 1][y - 1][z - 1];
        return p;
    }

    private static int volume(int[][][] p, int x, int y, int z, int s) {
        return p[x + s][y + s][z + s]
                - p[x][y + s][z + s]
                - p[x + s][y][z + s]
                - p[x + s][y + s][z]
                + p[x][y][z + s]
                + p[x][y + s][z]
                + p[x + s][y][z]
                - p[x][y][z];
    }

    public static List<VirtualElement> add(ElementHolder holder, ServerLevel level, BlockPos pos, BlockState state) {
        return add(holder, level, pos, cells(state));
    }

    public static List<VirtualElement> add(ElementHolder holder, ServerLevel level, BlockPos pos, List<AABB> boxes) {
        List<VirtualElement> added = new ArrayList<>();
        for (AABB box : boxes) {
            AABB worldBox = box.move(pos);
            class Anchor extends ItemDisplayElement {
                Anchor() {
                    super(ItemStack.EMPTY);
                }

                @Override
                public void startWatching(ServerPlayer player, Consumer<Packet<ClientGamePacketListener>> packets) {}

                void spawn(ServerPlayer player, Consumer<Packet<ClientGamePacketListener>> packets) {
                    super.startWatching(player, packets);
                }
            }
            Anchor anchor = new Anchor();
            anchor.setViewRange(0);
            Vec3 offset = new Vec3((box.minX + box.maxX) / 2 - .5, box.minY - .5, (box.minZ + box.maxZ) / 2 - .5);
            anchor.setOffset(offset);
            SimpleEntityElement shulker = new SimpleEntityElement(EntityTypes.SHULKER) {
                private final Set<UUID> watching = new HashSet<>();

                private boolean near(ServerPlayer player) {
                    boolean watched = watching.contains(player.getUUID());
                    // A shulker cell spawned inside the player (block placed at their feet, door closing on them)
                    // makes the client shove the player out violently. Hold it back until the player has stepped
                    // clear, as the source block collision would.
                    if (!watched && player.getBoundingBox().intersects(worldBox.deflate(1e-3))) return false;
                    if (PolymerBedrockCarriers.bedrockNative(player, level.getBlockState(pos))) return false;
                    return player.level() == level
                            && !PolymerProxyMining.hidden(player, level, pos)
                            && PolymerStaticProxyRange.near(player, level, pos, worldBox, watched);
                }

                @Override
                public void startWatching(ServerPlayer player, Consumer<Packet<ClientGamePacketListener>> packets) {
                    if (!near(player) || !watching.add(player.getUUID())) return;
                    anchor.spawn(player, packets);
                    super.startWatching(player, packets);
                    AttributeInstance scale = new AttributeInstance(Attributes.SCALE, ignored -> {});
                    scale.setBaseValue(box.getXsize());
                    packets.accept(VirtualEntityUtils.createClientboundSetPassengersPacket(
                            anchor.getEntityId(), new int[] {getEntityId()}));
                    packets.accept(new ClientboundUpdateAttributesPacket(getEntityId(), List.of(scale)));
                }

                @Override
                public void stopWatching(ServerPlayer player, Consumer<Packet<ClientGamePacketListener>> packets) {
                    if (!watching.remove(player.getUUID())) return;
                    packets.accept(new ClientboundRemoveEntitiesPacket(getEntityId(), anchor.getEntityId()));
                    super.stopWatching(player, packets);
                    anchor.stopWatching(player, packets);
                }

                @Override
                public void tick() {
                    if (getHolder() != holder) return;
                    for (var connection : holder.getWatchingPlayers()) {
                        if (!connection.isAcceptingMessages()) continue;
                        ServerPlayer player = connection.getPlayer();
                        if (near(player)) startWatching(player, connection::send);
                        else if (watching.contains(player.getUUID())) stopWatching(player, connection::send);
                    }
                    super.tick();
                }
            };
            shulker.setOffset(offset);
            shulker.setInvisible(true);
            shulker.setNoGravity(true);
            PolymerCollisionShulkers.hide(shulker);
            shulker.setInteractionHandler(new VirtualElement.InteractionHandler() {
                @Override
                public void interact(ServerPlayer player, InteractionHand hand, Vec3 hitPosition, boolean secondary) {
                    BlockHitResult hit = hit(player, level, pos);
                    if (hit == null || hand == null) return;
                    var result = player.gameMode.useItemOn(player, level, player.getItemInHand(hand), hand, hit);
                    if (result.consumesAction()) player.swing(hand, SwingAnimation.DEFAULT, false);
                }

                @Override
                public void attack(ServerPlayer player) {
                    PolymerProxyMining.attack(player, level, pos);
                }

                @Override
                public void pickItem(ServerPlayer player, boolean includeData) {
                    if (PolymerNativeClients.isNative(player)
                            || hit(player, level, pos) == null
                            || !(level.getBlockState(pos).getBlock() instanceof SmartFluidPipeBlock
                                    || level.getBlockState(pos).getBlock() instanceof CuckooClockBlock)) return;
                    player.connection.handlePickItemFromBlock(new ServerboundPickItemFromBlockPacket(pos, includeData));
                }
            });
            holder.addElement(anchor);
            holder.addElement(shulker);
            added.add(anchor);
            added.add(shulker);
        }
        return added;
    }

    public static @Nullable BlockHitResult hit(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player.level() != level
                || player.isSpectator()
                || !level.hasChunkAt(pos)
                || !level.mayInteract(player, pos)
                || !player.mayInteract(level, pos)
                || !player.isWithinBlockInteractionRange(pos, PolymerDialogService.OPEN_REACH_BUFFER)) return null;
        BlockHitResult hit = PolymerReach.targetBlock(player);
        if (hit != null && hit.getBlockPos().equals(pos)) return hit;
        return new BlockHitResult(Vec3.atCenterOf(pos), player.getDirection().getOpposite(), pos, false);
    }

    public static void addAssets(ResourcePackBuilder builder) {
        JsonObject variants = new JsonObject();
        for (int layers = 1; layers <= 8; layers++) {
            JsonObject entry = new JsonObject();
            entry.addProperty(
                    "model",
                    layers == 2
                            ? "create:block/polymer/native_snow_empty"
                            : layers == 8 ? "minecraft:block/snow_block" : "minecraft:block/snow_height" + layers * 2);
            variants.add("layers=" + layers, entry);
        }
        JsonObject root = new JsonObject();
        root.add("variants", variants);
        builder.addStringData("assets/minecraft/blockstates/snow.json", root.toString());
        builder.addStringData(
                "assets/create/models/block/polymer/native_snow_empty.json",
                "{\"textures\":{\"particle\":\"minecraft:block/snow\"},\"elements\":[]}");
        builder.addStringData(
                "assets/create/models/block/polymer/native_snow_two.json",
                "{\"textures\":{\"particle\":\"minecraft:block/snow\",\"texture\":\"minecraft:block/snow\"},\"elements\":[{"
                        + "\"from\":[0.01,0.01,0.01],\"to\":[15.99,4,15.99],\"faces\":{"
                        + "\"up\":{\"uv\":[0,0,16,16],\"texture\":\"#texture\"},"
                        + "\"north\":{\"uv\":[0,12,16,16],\"texture\":\"#texture\"},"
                        + "\"south\":{\"uv\":[0,12,16,16],\"texture\":\"#texture\"},"
                        + "\"west\":{\"uv\":[0,12,16,16],\"texture\":\"#texture\"},"
                        + "\"east\":{\"uv\":[0,12,16,16],\"texture\":\"#texture\"}}}]}");
        builder.addStringData(
                "assets/create/items/polymer/native_snow_two.json",
                "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"create:block/polymer/native_snow_two\"}}");
    }
}
