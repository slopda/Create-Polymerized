package com.zurrtum.create.infrastructure.polymer.block;

import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.textPart;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.catnip.data.Couple;
import com.zurrtum.create.catnip.math.AngleHelper;
import com.zurrtum.create.content.redstone.nixieTube.DoubleFaceAttachedBlock.DoubleAttachFace;
import com.zurrtum.create.content.redstone.nixieTube.NixieTubeBlock;
import com.zurrtum.create.content.redstone.nixieTube.NixieTubeBlockEntity;
import com.zurrtum.create.content.trains.display.FlapDisplayBlockEntity;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.Part;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

final class PolymerTextParts {
    private PolymerTextParts() {}

    private static final Identifier NIXIE_TUBE = PolymerBlockParts.model("nixie_tube/tube");
    private static final Identifier NIXIE_TUBE_SINGLE = PolymerBlockParts.model("nixie_tube/tube_single"),
            SIGNAL_PANEL = PolymerBlockParts.model("track_signal/panel"),
            SIGNAL_RED = PolymerBlockParts.model("track_signal/red_tube"),
            SIGNAL_YELLOW = PolymerBlockParts.model("track_signal/yellow_tube"),
            SIGNAL_WHITE = PolymerBlockParts.model("track_signal/white_tube"),
            SIGNAL_COMPUTER_WHITE = PolymerBlockParts.model("track_signal/computer_white");

    static void registerAll() {
        List<Block> nixies = new ArrayList<>();
        for (DyeColor color : DyeColor.values()) {
            nixies.add(AllBlocks.NIXIE_TUBE.pick(color));
        }
        PolymerBlockParts.<NixieTubeBlockEntity>register(PolymerTextParts::nixie, nixies.toArray(new Block[0]));

        PolymerBlockParts.<FlapDisplayBlockEntity>register(PolymerFlapDisplay::collect, AllBlocks.DISPLAY_BOARD);

        PolymerDiodeIndicators.register();
    }

    private static void nixie(NixieTubeBlockEntity be, BlockState state, float time, List<Part> out) {
        if (be.signalState != null || be.computerSignal != null) {
            signal(be, state, time, out);
            return;
        }
        be.updateDisplayedStrings();
        Couple<String> strings = be.getDisplayedStrings();

        DoubleAttachFace face = state.getValue(NixieTubeBlock.FACE);
        Direction facing = state.getValue(NixieTubeBlock.FACING);
        float yRot = AngleHelper.horizontalAngle(facing) - 90 + (face == DoubleAttachFace.WALL_REVERSED ? 180 : 0);
        float zRot = face == DoubleAttachFace.WALL ? -90 : face == DoubleAttachFace.WALL_REVERSED ? 90 : 0;
        float textY = face == DoubleAttachFace.CEILING ? -5 : -3;
        int color = brightColor(NixieTubeBlock.colorOf(state));

        Matrix4f base = new Matrix4f().translate(0.5f, 0.5f, 0.5f);
        if (yRot != 0) {
            base.rotateY(Mth.DEG_TO_RAD * yRot);
        }
        if (zRot != 0) {
            base.rotateZ(Mth.DEG_TO_RAD * zRot);
        }
        Matrix4f tube = new Matrix4f(base);
        if (face == DoubleAttachFace.CEILING || facing == Direction.DOWN) tube.rotateZ(Mth.PI);
        out.add(PolymerBlockParts.part(NIXIE_TUBE, tube).identified("nixie:tube"));
        if (strings == null) {
            return;
        }
        nixieDigit(base, -0.25f, textY, strings.getFirst(), color, out);
        nixieDigit(base, 0.25f, textY, strings.getSecond(), color, out);
    }

    private static void signal(NixieTubeBlockEntity be, BlockState state, float time, List<Part> out) {
        DoubleAttachFace face = state.getValue(NixieTubeBlock.FACE);
        Direction facing = NixieTubeBlock.getFacing(state);
        float yRot = AngleHelper.horizontalAngle(state.getValue(NixieTubeBlock.FACING))
                - 90
                + (face == DoubleAttachFace.WALL_REVERSED ? 180 : 0);
        int zRot = (face == DoubleAttachFace.WALL ? -90 : face == DoubleAttachFace.WALL_REVERSED ? 90 : 0)
                + (facing == Direction.DOWN ? 180 : 0);
        Matrix4f block = new Matrix4f()
                .translate(.5f, .5f, .5f)
                .rotateY(Mth.DEG_TO_RAD * yRot)
                .rotateZ(Mth.DEG_TO_RAD * zRot)
                .translate(-.5f, -.5f, -.5f);
        out.add(PolymerBlockParts.part(SIGNAL_PANEL, new Matrix4f(block)).identified("nixie:signal:panel"));
        float offset = facing == Direction.DOWN || face == DoubleAttachFace.WALL_REVERSED ? .25f : -.25f;
        Identifier left = NIXIE_TUBE_SINGLE, right = NIXIE_TUBE_SINGLE;
        if (be.signalState != null) {
            boolean yellow = be.signalState.isYellowLight(time);
            if (be.signalState.isRedLight(time)) left = SIGNAL_RED;
            if (yellow || be.signalState.isGreenLight(time)) right = yellow ? SIGNAL_YELLOW : SIGNAL_WHITE;
        } else {
            if (lit(be.computerSignal.first, time)) left = SIGNAL_COMPUTER_WHITE;
            if (lit(be.computerSignal.second, time)) right = SIGNAL_COMPUTER_WHITE;
        }
        tube(block, offset, left, "left", out);
        tube(block, -offset, right, "right", out);
    }

    private static boolean lit(NixieTubeBlockEntity.ComputerSignal.TubeDisplay tube, float time) {
        return !(tube.blinkPeriod == 0 || tube.blinkPeriod > 1 && time % tube.blinkPeriod < tube.blinkOffTime);
    }

    private static void tube(Matrix4f block, float offset, Identifier model, String side, List<Part> out) {
        Matrix4f pose = new Matrix4f(block).translate(.5f, .46875f, .5f).translate(offset, 0, 0);
        if (model != NIXIE_TUBE_SINGLE) pose.scale(1.0625f);
        out.add(PolymerBlockParts.part(model, pose).identified("nixie:signal:" + side));
    }

    private static void nixieDigit(Matrix4f base, float side, float textY, String text, int color, List<Part> out) {
        if (text == null || text.isEmpty() || text.charAt(0) == ' ') {
            return;
        }
        Matrix4f fontPose = new Matrix4f(base)
                .translate(side, 0, 0)
                .scale(0.05f, -0.05f, 0.05f)
                .translate(0.25f, textY, 0)
                .translate(-1, 9, 0);
        out.add(textPart(Component.literal(text.substring(0, 1)).withColor(color), fontPose));
    }

    static int brightColor(DyeColor color) {
        return switch (color) {
            case BLACK -> 0x45403B;
            case RED -> 0xB13937;
            case GREEN -> 0x208A46;
            case BROWN -> 0xAC855C;
            case BLUE -> 0x5391E1;
            case GRAY -> 0x5D666F;
            case LIGHT_GRAY -> 0x95969B;
            case PURPLE -> 0x9F54AE;
            case CYAN -> 0x3EABB4;
            case PINK -> 0xD5A8CB;
            case LIME -> 0xA3DF55;
            case YELLOW -> 0xE6D756;
            case LIGHT_BLUE -> 0x69CED2;
            case ORANGE -> 0xEE9246;
            case MAGENTA -> 0xF062B0;
            case WHITE -> 0xEDEAE5;
        };
    }
}
