package com.zurrtum.create.infrastructure.polymer.hud;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.AllHandle;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.content.kinetics.base.DirectionalKineticBlock;
import com.zurrtum.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.zurrtum.create.content.kinetics.base.HorizontalKineticBlock;
import com.zurrtum.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.zurrtum.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlock;
import com.zurrtum.create.content.redstone.DirectedDirectionalBlock;
import com.zurrtum.create.infrastructure.packet.c2s.RadialWrenchMenuSubmitPacket;
import com.zurrtum.create.infrastructure.polymer.PolymerReach;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerGuiControls;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;

public final class PolymerRadialWrench {
    private static final Map<Property<?>, String> PROPERTIES = new LinkedHashMap<>();

    static {
        PROPERTIES.putIfAbsent(RotatedPillarKineticBlock.AXIS, "Axis");
        PROPERTIES.putIfAbsent(DirectionalKineticBlock.FACING, "Facing");
        PROPERTIES.putIfAbsent(HorizontalAxisKineticBlock.HORIZONTAL_AXIS, "Axis");
        PROPERTIES.putIfAbsent(HorizontalKineticBlock.HORIZONTAL_FACING, "Facing");
        PROPERTIES.putIfAbsent(HopperBlock.FACING, "Facing");
        PROPERTIES.putIfAbsent(DirectedDirectionalBlock.TARGET, "Target");
        PROPERTIES.putIfAbsent(SequencedGearshiftBlock.VERTICAL, "Vertical");
    }

    private static final Set<Block> BLACKLIST = Set.of(AllBlocks.LARGE_WATER_WHEEL, AllBlocks.WATER_WHEEL_STRUCTURAL);

    private PolymerRadialWrench() {}

    static boolean wrenchProperty(Property<?> property) {
        return PROPERTIES.containsKey(property);
    }

    public static boolean wrenchable(Item item) {
        if (item == AllItems.WRENCH) return true;
        return item instanceof BlockItem blockItem
                && !properties(blockItem.getBlock().defaultBlockState()).isEmpty();
    }

    public static boolean open(ServerPlayer player) {
        if (PolymerNativeClients.isNative(player)
                || player.isSpectator()
                || !player.getMainHandItem().is(AllItems.WRENCH)) return false;
        BlockHitResult blockHit = PolymerReach.targetBlock(player);
        if (blockHit == null) return false;
        ServerLevel level = player.level();
        BlockPos pos = blockHit.getBlockPos().immutable();
        if (!level.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        List<Map.Entry<Property<?>, String>> properties = properties(state);
        if (properties.isEmpty()) return false;
        if (!player.mayBuild() || !player.mayInteract(level, pos)) return true;
        return PolymerHudRadial.open(player, new Menu(player, level, pos, state.getBlock(), properties)) || true;
    }

    private static List<Map.Entry<Property<?>, String>> properties(BlockState state) {
        if (BLACKLIST.contains(state.getBlock())) return List.of();
        return PROPERTIES.entrySet().stream()
                .filter(entry -> state.hasProperty(entry.getKey()))
                .toList();
    }

    private static <T extends Comparable<T>> List<BlockState> states(BlockState state, Property<T> property) {
        List<BlockState> states = new ArrayList<>();
        for (T value : property.getPossibleValues()) states.add(state.setValue(property, value));
        return states;
    }

    private static <T extends Comparable<T>> String rawValue(BlockState state, Property<T> property) {
        return state.getValue(property).toString();
    }

    private static final class Menu implements PolymerHudRadial.Session {
        private final ServerLevel level;
        private final BlockPos pos;
        private final Block block;
        private final List<Map.Entry<Property<?>, String>> properties;
        private int property, hovered, age;
        private float pointerX, pointerY;

        private Menu(
                ServerPlayer player,
                ServerLevel level,
                BlockPos pos,
                Block block,
                List<Map.Entry<Property<?>, String>> properties) {
            this.level = level;
            this.pos = pos;
            this.block = block;
            this.properties = properties;
            hovered = Math.max(0, states().indexOf(level.getBlockState(pos)));
        }

        private List<BlockState> states() {
            return PolymerRadialWrench.states(
                    level.getBlockState(pos), properties.get(property).getKey());
        }

        private int count() {
            return Math.max(2, Math.min(states().size(), 8));
        }

        @Override
        public boolean valid(ServerPlayer player) {
            return player.level() == level
                    && !player.isSpectator()
                    && player.getMainHandItem().is(AllItems.WRENCH)
                    && level.hasChunkAt(pos)
                    && level.getBlockState(pos).is(block)
                    && player.isWithinBlockInteractionRange(pos, PolymerGuiControls.MENU_REACH_BUFFER)
                    && player.mayBuild()
                    && player.mayInteract(level, pos);
        }

        @Override
        public boolean tick(ServerPlayer player, float x, float y, int age) {
            this.age = age;
            pointerX = x;
            pointerY = y;
            if (Math.hypot(x, y) > 45) {
                double size = 360.0 / count();
                double deg = -Math.toDegrees(Math.atan2(x, y)) + 180 + size / 2;
                hovered = Math.min(count() - 1, (int) Math.floor((deg % 360 + 360) % 360 / size));
            }
            return false;
        }

        private boolean submit(ServerPlayer player) {
            List<BlockState> states = states();
            if (hovered < states.size()) {
                BlockState chosen = states.get(hovered);
                if (chosen != level.getBlockState(pos))
                    AllHandle.onRadialWrenchMenuSubmit(
                            player.connection, new RadialWrenchMenuSubmitPacket(pos, chosen));
            }
            return true;
        }

        @Override
        public boolean leftClick(ServerPlayer player) {
            return submit(player);
        }

        @Override
        public boolean swap(ServerPlayer player) {
            return submit(player);
        }

        @Override
        public boolean rightClick(ServerPlayer player) {
            return true;
        }

        @Override
        public boolean scroll(ServerPlayer player, int steps) {
            int next = Math.clamp(property + Integer.signum(steps), 0, properties.size() - 1);
            if (next != property) {
                property = next;
                hovered = Math.max(0, states().indexOf(level.getBlockState(pos)));
            }
            return false;
        }

        @Override
        public List<PolymerHudRadial.Glyph> draw() {
            List<PolymerHudRadial.Glyph> glyphs = new ArrayList<>();
            int n = count();
            List<BlockState> states = states();
            Map.Entry<Property<?>, String> entry = properties.get(property);
            glyphs.add(PolymerHudRadial.dim(age));
            if (Math.hypot(pointerX, pointerY) > 45) glyphs.add(PolymerHudRadial.indicator(pointerX, pointerY));
            glyphs.add(PolymerHudRadial.wrenchRing(n, Math.min(hovered, n - 1)));
            for (int i = 0; i < n && i < states.size(); i++) {
                int[] c = PolymerHudRadial.wrenchCell(i, n);
                glyphs.add(PolymerHudRadial.wrenchStateIcon(states.get(i), c[0], c[1]));
            }
            if (hovered < states.size() && hovered < n) {
                int[] c = PolymerHudRadial.wrenchCell(hovered, n);
                glyphs.addAll(PolymerHudRadial.text(
                        rawValue(states.get(hovered), entry.getKey()), c[0], c[1] + 15, PolymerHudRadial.TEXT_COLOR));
            }
            glyphs.add(PolymerHudRadial.streaks());
            if (property > 0) {
                glyphs.add(PolymerHudRadial.uiIcon("scroll", -14, -46));
                glyphs.add(PolymerHudRadial.uiIcon("up", -1, -46));
                glyphs.addAll(PolymerHudRadial.text(
                        properties.get(property - 1).getValue(), 0, -30, PolymerHudRadial.TEXT_COLOR));
            }
            if (property < properties.size() - 1) {
                glyphs.add(PolymerHudRadial.uiIcon("scroll", -14, 30));
                glyphs.add(PolymerHudRadial.uiIcon("down", -1, 30));
                glyphs.addAll(PolymerHudRadial.text(
                        properties.get(property + 1).getValue(), 0, 22, PolymerHudRadial.TEXT_COLOR));
            }
            glyphs.addAll(PolymerHudRadial.text("Currently", 0, -13, PolymerHudRadial.TEXT_COLOR));
            glyphs.addAll(PolymerHudRadial.text("Changing:", 0, -3, PolymerHudRadial.TEXT_COLOR));
            glyphs.addAll(PolymerHudRadial.text(entry.getValue(), 0, 7, PolymerHudRadial.TEXT_COLOR));
            glyphs.add(PolymerHudRadial.cursor(pointerX, pointerY));
            return glyphs;
        }

        @Override
        public Component tip() {
            return Component.empty();
        }
    }
}
