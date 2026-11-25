package com.zurrtum.create.infrastructure.polymer.hud;

import com.zurrtum.create.AllHandle;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.AllSynchedDatas;
import com.zurrtum.create.content.equipment.toolbox.ToolboxBlockEntity;
import com.zurrtum.create.content.equipment.toolbox.ToolboxHandler;
import com.zurrtum.create.content.equipment.toolbox.ToolboxInventory;
import com.zurrtum.create.infrastructure.packet.c2s.ToolboxDisposeAllPacket;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class PolymerToolbelt {
    private enum State {
        SELECT_BOX,
        SELECT_ITEM,
        SELECT_ITEM_UNEQUIP,
        DETACH
    }

    private static final int NONE = -1, UNEQUIP = -5, DEPOSIT = -7;

    private PolymerToolbelt() {}

    public static boolean open(ServerPlayer player) {
        if (!player.isShiftKeyDown() || player.isSpectator() || PolymerNativeClients.isNative(player)) return false;
        List<ToolboxBlockEntity> toolboxes = new ArrayList<>(ToolboxHandler.getNearest(player.level(), player, 8));
        toolboxes.sort(Comparator.comparing(ToolboxBlockEntity::getUniqueId));

        CompoundTag compound = AllSynchedDatas.TOOLBOX.get(player);
        String slotKey = String.valueOf(player.getInventory().getSelectedSlot());
        if (compound.contains(slotKey)) {
            CompoundTag slotCompound = compound.getCompoundOrEmpty(slotKey);
            BlockPos pos = slotCompound.read("Pos", BlockPos.CODEC).orElse(BlockPos.ZERO);
            double max = ToolboxHandler.getMaxRange(player);
            if (ToolboxHandler.distance(player.position(), pos) < max * max
                    && player.level().getBlockEntity(pos) instanceof ToolboxBlockEntity box)
                return PolymerHudRadial.open(player, new Menu(player, toolboxes, State.SELECT_ITEM_UNEQUIP, box));
            return PolymerHudRadial.open(player, new Menu(player, List.of(), State.DETACH, null));
        }
        if (toolboxes.isEmpty()) return false;
        if (toolboxes.size() == 1)
            return PolymerHudRadial.open(player, new Menu(player, toolboxes, State.SELECT_ITEM, toolboxes.getFirst()));
        return PolymerHudRadial.open(player, new Menu(player, toolboxes, State.SELECT_BOX, null));
    }

    private static int[] cell(int k) {
        double a = Math.toRadians(k * 45 - 45);
        return new int[] {(int) Math.round(40 * Math.sin(a)), PolymerHudRadial.evenY(-40 * Math.cos(a))};
    }

    private static int hover(float x, float y, boolean detach, boolean centre) {
        float d = x * x + y * y;
        int hovered = NONE;
        if (d > 25 && d < 10000)
            hovered = Math.floorMod((int) Math.floor(Math.toDegrees(Math.atan2(y, x)) + 360 + 180 - 22.5), 360) / 45;
        if (centre && d <= 150) hovered = UNEQUIP;
        if (detach) {
            if (x > -20 && x < 20 && y > -80 && y < -20) hovered = UNEQUIP;
        } else if (x > 60 && x < 100 && y > -20 && y < 20) hovered = DEPOSIT;
        return hovered;
    }

    private static final class Menu implements PolymerHudRadial.Session {
        private final List<ToolboxBlockEntity> toolboxes;
        private State state;
        private @Nullable ToolboxBlockEntity selectedBox;
        private final int hotbar;
        private final ServerPlayer owner;
        private int hovered = NONE, scrollSlot, age;
        private float pointerX, pointerY;
        private boolean scrollMode;

        private Menu(
                ServerPlayer player,
                List<ToolboxBlockEntity> toolboxes,
                State state,
                @Nullable ToolboxBlockEntity box) {
            this.toolboxes = toolboxes;
            this.state = state;
            this.selectedBox = box;
            this.hotbar = player.getInventory().getSelectedSlot();
            this.owner = player;
        }

        private boolean inRange(ServerPlayer player, ToolboxBlockEntity box) {
            return !box.isRemoved() && box.getLevel() == player.level() && ToolboxHandler.withinRange(player, box);
        }

        private int selected() {
            return scrollMode ? scrollSlot : hovered;
        }

        @Override
        public boolean valid(ServerPlayer player) {
            return !player.isSpectator()
                    && player.getInventory().getSelectedSlot() == hotbar
                    && (selectedBox == null || inRange(player, selectedBox));
        }

        @Override
        public boolean tick(ServerPlayer player, float x, float y, int age) {
            this.age = age;
            pointerX = x;
            pointerY = y;
            hovered = hover(x, y, state == State.DETACH, state == State.SELECT_ITEM_UNEQUIP);
            if (scrollMode && x * x + y * y > 150) scrollMode = false;
            if (!player.isShiftKeyDown()) {
                act(player);
                return true;
            }
            return false;
        }

        @Override
        public boolean leftClick(ServerPlayer player) {
            int selected = selected();
            if (selected == DEPOSIT) {
                act(player);
                return true;
            }
            if (state == State.SELECT_BOX && selected >= 0 && selected < toolboxes.size()) {
                state = State.SELECT_ITEM;
                selectedBox = toolboxes.get(selected);
                return false;
            }
            if (state != State.SELECT_BOX && (selected == UNEQUIP || selected >= 0)) {
                act(player);
                return true;
            }
            return false;
        }

        @Override
        public boolean rightClick(ServerPlayer player) {
            if (state == State.SELECT_ITEM && toolboxes.size() > 1) {
                state = State.SELECT_BOX;
                return false;
            }
            if (state == State.SELECT_ITEM_UNEQUIP && selected() == UNEQUIP) {
                if (toolboxes.size() > 1 && selectedBox != null) {
                    AllHandle.setToolboxForHotbar(player, selectedBox.getBlockPos(), UNEQUIP, hotbar);
                    state = State.SELECT_BOX;
                    selectedBox = null;
                    return false;
                }
                act(player);
                return true;
            }
            return false;
        }

        @Override
        public boolean swap(ServerPlayer player) {
            return true;
        }

        @Override
        public boolean scroll(ServerPlayer player, int steps) {
            scrollMode = true;
            scrollSlot = Math.floorMod(scrollSlot + steps, 8);
            for (int i = 0; i < 10 && !usable(scrollSlot); i++)
                scrollSlot = Math.floorMod(scrollSlot + Integer.signum(steps), 8);
            return false;
        }

        private boolean usable(int slot) {
            return switch (state) {
                case SELECT_BOX -> slot < toolboxes.size();
                case DETACH -> true;
                default ->
                    selectedBox != null
                            && !selectedBox.inventory.filters.get(slot).isEmpty()
                            && !selectedBox
                                    .inventory
                                    .getItem(slot * ToolboxInventory.STACKS_PER_COMPARTMENT)
                                    .isEmpty();
            };
        }

        private void act(ServerPlayer player) {
            int selected = selected();
            if (selected == DEPOSIT) {
                if (state == State.SELECT_BOX) {
                    toolboxes.forEach(box -> {
                        if (inRange(player, box))
                            AllHandle.onToolboxDisposeAll(
                                    player.connection, new ToolboxDisposeAllPacket(box.getBlockPos()));
                    });
                } else if (state != State.DETACH && selectedBox != null && inRange(player, selectedBox)) {
                    AllHandle.onToolboxDisposeAll(
                            player.connection, new ToolboxDisposeAllPacket(selectedBox.getBlockPos()));
                }
                return;
            }
            if (state == State.SELECT_BOX) return;
            if (state == State.DETACH) {
                if (selected == UNEQUIP) AllHandle.setToolboxForHotbar(player, null, UNEQUIP, hotbar);
                return;
            }
            ToolboxBlockEntity box = selectedBox;
            if (box == null || !inRange(player, box)) return;
            if (selected == UNEQUIP) AllHandle.setToolboxForHotbar(player, box.getBlockPos(), UNEQUIP, hotbar);
            if (selected < 0) return;
            ToolboxInventory inv = box.inventory;
            if (inv.filters.get(selected).isEmpty()
                    || inv.getItem(selected * ToolboxInventory.STACKS_PER_COMPARTMENT)
                            .isEmpty()) return;
            AllHandle.setToolboxForHotbar(player, box.getBlockPos(), selected, hotbar);
        }

        @Override
        public List<PolymerHudRadial.Glyph> draw() {
            List<PolymerHudRadial.Glyph> glyphs = new ArrayList<>();
            List<PolymerHudRadial.Glyph> icons = new ArrayList<>();
            int selected = selected();
            glyphs.add(PolymerHudRadial.dim(age));
            if (state == State.DETACH) {
                glyphs.add(PolymerHudRadial.frame(PolymerHudRadial.Frame.INACTIVE, 0, 0));
                icons.add(PolymerHudRadial.toolbeltIcon(AllItems.TOOLBOX.brown(), 0, 0));
                int[] top = cell(1);
                glyphs.add(PolymerHudRadial.frame(PolymerHudRadial.Frame.SLOT, top[0], top[1]));
                icons.add(PolymerHudRadial.toolbeltIcon("texture:create:item/ui/gui_toolbelt_unequip", top[0], top[1]));
                if (!scrollMode && selected == UNEQUIP)
                    icons.add(PolymerHudRadial.frame(PolymerHudRadial.Frame.HIGHLIGHT, top[0], top[1]));
            } else {
                glyphs.add(PolymerHudRadial.frame(PolymerHudRadial.Frame.SLOT, 80, 0));
                icons.add(PolymerHudRadial.toolbeltIcon("texture:create:item/ui/gui_toolbelt_deposit", 80, 0));
                if (!scrollMode && selected == DEPOSIT)
                    icons.add(PolymerHudRadial.frame(PolymerHudRadial.Frame.HIGHLIGHT, 80, 0));
                for (int k = 0; k < 8; k++) {
                    int[] c = cell(k);
                    if (state == State.SELECT_BOX) {
                        if (k >= toolboxes.size()) {
                            glyphs.add(PolymerHudRadial.frame(PolymerHudRadial.Frame.EMPTY, c[0], c[1]));
                            continue;
                        }
                        glyphs.add(PolymerHudRadial.frame(PolymerHudRadial.Frame.SLOT, c[0], c[1]));
                        icons.add(PolymerHudRadial.toolbeltIcon(
                                toolboxes.get(k).getBlockState().getBlock().asItem(), c[0], c[1]));
                        if (k == selected)
                            icons.add(PolymerHudRadial.frame(PolymerHudRadial.Frame.HIGHLIGHT, c[0], c[1]));
                        continue;
                    }
                    ToolboxInventory inv = selectedBox == null ? null : selectedBox.inventory;
                    ItemStack filter = inv == null ? ItemStack.EMPTY : inv.filters.get(k);
                    if (filter.isEmpty()) {
                        glyphs.add(PolymerHudRadial.frame(PolymerHudRadial.Frame.EMPTY, c[0], c[1]));
                        continue;
                    }
                    boolean empty = inv.getItem(k * ToolboxInventory.STACKS_PER_COMPARTMENT)
                            .isEmpty();
                    glyphs.add(PolymerHudRadial.frame(
                            empty ? PolymerHudRadial.Frame.INACTIVE : PolymerHudRadial.Frame.SLOT, c[0], c[1]));
                    icons.add(PolymerHudRadial.toolbeltIcon(filter.getItem(), c[0], c[1]));
                    if (k == selected && !empty)
                        icons.add(PolymerHudRadial.frame(PolymerHudRadial.Frame.HIGHLIGHT, c[0], c[1]));
                }
                if (state == State.SELECT_ITEM_UNEQUIP) {
                    glyphs.add(PolymerHudRadial.frame(PolymerHudRadial.Frame.SLOT, 0, 0));
                    icons.add(PolymerHudRadial.toolbeltIcon("texture:create:item/ui/gui_toolbelt_flip", 0, 0));
                    if (!scrollMode && selected == UNEQUIP)
                        icons.add(PolymerHudRadial.frame(PolymerHudRadial.Frame.HIGHLIGHT, 0, 0));
                }
            }
            glyphs.addAll(icons);
            glyphs.add(PolymerHudRadial.cursor(pointerX, pointerY));
            return glyphs;
        }

        @Override
        public Component tip() {
            int selected = selected();
            Component tip = null;
            if (state == State.DETACH) {
                tip = selected == UNEQUIP
                        ? Component.translatable("create.toolbox.detach").withStyle(ChatFormatting.GOLD)
                        : Component.translatable("create.toolbox.outOfRange");
            } else if (selected == DEPOSIT) {
                tip = Component.translatable(
                                state == State.SELECT_BOX ? "create.toolbox.depositAll" : "create.toolbox.depositBox")
                        .withStyle(ChatFormatting.GOLD);
            } else if (selected == UNEQUIP && state == State.SELECT_ITEM_UNEQUIP) {
                tip = Component.translatable(
                                "create.toolbox.unequip",
                                owner.getMainHandItem().getHoverName())
                        .withStyle(ChatFormatting.GOLD);
            } else if (selected >= 0 && state == State.SELECT_BOX && selected < toolboxes.size()) {
                tip = toolboxes.get(selected).getDisplayName().copy();
            } else if (selected >= 0 && selectedBox != null && state != State.SELECT_BOX) {
                ItemStack filter = selectedBox.inventory.filters.get(selected);
                if (!filter.isEmpty()) tip = filter.getHoverName().copy();
            }
            return tip == null ? Component.empty() : tip;
        }
    }
}
