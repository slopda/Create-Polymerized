package com.zurrtum.create.infrastructure.polymer.ponder;

import com.zurrtum.create.infrastructure.polymer.ponder.PolymerPonderScene.Fade;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;

public final class PolymerPonderScenes {
    private PolymerPonderScenes() {}

    public static final PolymerPonderScene.Definition COGWHEEL = PolymerPonderScene.builder(
                    "cogwheel", "create:cogwheel", "Relaying rotational force using Cogwheels", "create:cog/small")
            .model("create:speedometer", "create:block/gauge/speedometer/item")
            .showSection(s -> s.layer(0), Fade.UP)
            .showSection(s -> s.position(4, 1, 1), Fade.UP)
            .idle(5)
            .showSection(s -> s.fromTo(1, 1, 3, 1, 1, 5), Fade.DOWN)
            .idle(10)
            .idle(5)
            .showSection(s -> s.position(1, 1, 2), Fade.DOWN)
            .idle(5)
            .showSection(s -> s.position(0, 1, 2), Fade.DOWN)
            .showSection(s -> s.position(2, 1, 2), Fade.DOWN)
            .idle(5)
            .showSection(s -> s.position(3, 1, 2), Fade.DOWN)
            .idle(5)
            .showSection(s -> s.position(4, 1, 2), Fade.DOWN)
            .idle(10)
            .text(60, "Cogwheels will relay rotation to other adjacent cogwheels")
            .pointAt(1, 1.5f, 2.5f)
            .idle(60)
            .showSection(s -> s.fromTo(1, 1, 1, 2, 1, 1), Fade.SOUTH)
            .idle(10)
            .idle(20)
            .text(100, "Neighbouring shafts connected like this will rotate in opposite directions")
            .pointAt(1.5f, 1.5f, 2)
            .keyframe()
            .idle(70)
            .build();

    static final PolymerPonderScene.Definition SHAFT = PolymerPonderScene.builder(
                    "shaft", "create:shaft", "Relaying rotational force using Shafts", "create:shaft/relay")
            .model("create:speedometer", "create:block/gauge/speedometer/item")
            .showSection(s -> s.layer(0), Fade.UP)
            .showSection(s -> s.position(0, 1, 2), Fade.UP)
            .speed(s -> s.position(0, 1, 2), 0)
            .idle(5)
            .showSection(s -> s.position(5, 1, 2), Fade.DOWN)
            .idle(10)
            .idle(5)
            .showSection(s -> s.position(4, 1, 2), Fade.DOWN)
            .idle(5)
            .showSection(s -> s.position(3, 1, 2), Fade.DOWN)
            .rotateCamera(70)
            .idle(5)
            .showSection(s -> s.position(2, 1, 2), Fade.DOWN)
            .idle(5)
            .showSection(s -> s.position(1, 1, 2), Fade.DOWN)
            .speed(s -> s.position(0, 1, 2), 64)
            .idle(10)
            .text(1000, "Shafts will relay rotation in a straight line.")
            .pointAt(3, 1.5f, 2.5f)
            .idle(20)
            .build();

    static final PolymerPonderScene.Definition CREATIVE_MOTOR = PolymerPonderScene.builder(
                    "creative_motor",
                    "create:creative_motor",
                    "Generating Rotational Force using Creative Motors",
                    "create:creative_motor")
            .showSection(s -> s.layer(0), Fade.UP)
            .idle(5)
            .showSection(s -> s.position(1, 1, 2), Fade.DOWN)
            .idle(5)
            .showSection(s -> s.position(2, 1, 2), Fade.DOWN)
            .idle(5)
            .showSection(s -> s.position(3, 1, 2), Fade.DOWN)
            .idle(10)
            .text(50, "Creative motors are a compact and configurable source of Rotational Force")
            .pointAt(3.5f, 2, 2.5f)
            .idle(70)
            .showFilterSlotInput(3.5625f, 1.5f, 2.1875f, Fade.NORTH, 80)
            .idle(20)
            .text(60, "The generated speed can be configured on its input panels")
            .pointAt(3.5625f, 1.5f, 2.1875f)
            .keyframe()
            .idle(10)
            .idle(50)
            .multiplySpeed(s -> s.fromTo(1, 1, 2, 3, 1, 2), 4)
            .idle(10)
            .build();

    static final PolymerPonderScene.Definition SHAFT_ENCASING = PolymerPonderScene.builder(
                    "shaft_encasing", "create:shaft", "Encasing Shafts", "create:shaft/encasing")
            .showSection(s -> s.layer(0), Fade.UP)
            .showSection(s -> s.fromTo(0, 1, 2, 5, 1, 4), Fade.DOWN)
            .idle(20)
            .idle(7)
            .setBlocks(s -> s.position(3, 1, 2), "create:andesite_encased_shaft", "axis=x")
            .speed(s -> s.fromTo(0, 1, 2, 5, 1, 4), 32)
            .idle(10)
            .idle(7)
            .setBlocks(s -> s.position(1, 1, 2), "create:brass_encased_shaft", "axis=x")
            .speed(s -> s.fromTo(0, 1, 2, 5, 1, 4), 32)
            .idle(10)
            .text(100, "Brass or Andesite Casing can be used to decorate Shafts")
            .pointAt(1.5f, 2, 2.5f)
            .idle(70)
            .build();

    private static final PolymerPonderCatalog.Data CATALOG = PolymerPonderCatalog.create(Map.of(
            "shaft", SHAFT, "shaft_casing", SHAFT_ENCASING, "cogwheel", COGWHEEL, "creative_motor", CREATIVE_MOTOR));
    static final List<PolymerPonderScene.Definition> ALL = CATALOG.all();

    static Optional<PolymerPonderScene.Definition> byId(String id) {
        return ALL.stream().filter(scene -> scene.id().equals(id)).findFirst();
    }

    public record Summary(String id, String item, String title, int index) {}

    public static List<Summary> summaries() {
        return ALL.stream()
                .map(scene -> new Summary(
                        scene.id(),
                        scene.builder.item,
                        scene.builder.title,
                        storyboards(scene.builder.item).indexOf(scene)))
                .toList();
    }

    public static boolean openAt(ServerPlayer player, String id, int tick) {
        return byId(id).map(scene -> PolymerPonderDialog.openAt(player, scene, tick))
                .orElse(false);
    }

    public static boolean open(ServerPlayer player, String id) {
        return byId(id).map(scene -> PolymerPonderDialog.open(player, scene)).orElse(false);
    }

    static Optional<PolymerPonderScene.Definition> forItem(String item) {
        return storyboards(item).stream().findFirst();
    }

    private static final Map<String, List<PolymerPonderScene.Definition>> STORYBOARDS = CATALOG.storyboards();

    record Tag(String id, String title, String description, String icon, List<String> items) {}

    static final Tag KINETIC_RELAYS = new Tag(
            "kinetic_relays",
            "Kinetic Blocks",
            "Components which help relaying Rotational Force elsewhere",
            "create:cogwheel",
            List.of(
                    "create:shaft",
                    "create:cogwheel",
                    "create:large_cogwheel",
                    "create:belt_connector",
                    "create:gearbox",
                    "create:vertical_gearbox",
                    "create:clutch",
                    "create:gearshift",
                    "create:encased_chain_drive",
                    "create:adjustable_chain_gearshift",
                    "create:chain_conveyor",
                    "create:sequenced_gearshift",
                    "create:rotation_speed_controller"));
    static final Tag KINETIC_SOURCES = new Tag(
            "kinetic_sources",
            "Kinetic Sources",
            "Components which generate Rotational Force",
            "create:water_wheel",
            List.of(
                    "create:hand_crank",
                    "create:copper_valve_handle",
                    "create:water_wheel",
                    "create:large_water_wheel",
                    "create:windmill_bearing",
                    "create:steam_engine",
                    "create:creative_motor"));
    static final List<Tag> TAGS = List.of(KINETIC_RELAYS, KINETIC_SOURCES);

    static Optional<Tag> tag(String id) {
        return TAGS.stream().filter(tag -> tag.id().equals(id)).findFirst();
    }

    static List<Tag> tagsOf(String item) {
        return TAGS.stream().filter(tag -> tag.items().contains(item)).toList();
    }

    static Collection<List<PolymerPonderScene.Definition>> storyboardLists() {
        return new LinkedHashSet<>(STORYBOARDS.values());
    }

    static List<PolymerPonderScene.Definition> storyboards(String item) {
        return STORYBOARDS.getOrDefault(item, List.of());
    }

    static List<PolymerPonderScene.Definition> storyboardsOf(PolymerPonderScene.Definition definition) {
        List<PolymerPonderScene.Definition> list = storyboards(definition.builder.item);
        return list.contains(definition) ? list : List.of(definition);
    }
}
