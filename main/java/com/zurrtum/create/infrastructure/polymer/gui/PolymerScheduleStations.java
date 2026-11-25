package com.zurrtum.create.infrastructure.polymer.gui;

import com.zurrtum.create.content.trains.graph.EdgePointType;
import com.zurrtum.create.content.trains.graph.TrackGraph;
import com.zurrtum.create.content.trains.schedule.Schedule;
import com.zurrtum.create.content.trains.schedule.destination.DestinationInstruction;
import com.zurrtum.create.content.trains.station.GlobalStation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.util.StringUtil;
import net.minecraft.world.phys.Vec3;

final class PolymerScheduleStations {
    static final int PAGE_SIZE = 6;

    record Page(List<String> names, int index, int total) {
        boolean previous() {
            return index > 0;
        }

        boolean next() {
            return (index + 1) * PAGE_SIZE < total;
        }
    }

    private PolymerScheduleStations() {}

    static Page page(
            Collection<TrackGraph> networks,
            Schedule schedule,
            DestinationInstruction edited,
            Vec3 position,
            String prefix,
            int requestedPage,
            int maxLength) {
        var viable = new ArrayList<>(networks);
        for (var entry : schedule.entries) {
            if (!(entry.instruction instanceof DestinationInstruction destination) || destination == edited) continue;
            String regex = destination.getFilterForRegex();
            if (regex.isBlank()) continue;
            Pattern pattern = Pattern.compile(regex);
            viable.removeIf(graph -> graph.getPoints(EdgePointType.STATION).stream()
                    .noneMatch(station -> pattern.matcher(station.name).matches()));
        }
        if (viable.isEmpty()) viable.addAll(networks);

        var distances = new HashMap<String, Double>();
        String query = prefix.toLowerCase(Locale.ROOT);
        for (TrackGraph graph : viable)
            for (GlobalStation station : graph.getPoints(EdgePointType.STATION)) {
                String name = station.name;
                if (station.blockEntityPos == null
                        || name.isEmpty()
                        || name.length() > maxLength
                        || !name.equals(StringUtil.filterText(name))
                        || !name.toLowerCase(Locale.ROOT).startsWith(query)) continue;
                double distance = Vec3.atBottomCenterOf(station.blockEntityPos).distanceToSqr(position);
                distances.merge(name, distance, Math::min);
            }
        List<String> matches = distances.keySet().stream()
                .sorted(Comparator.<String>comparingDouble(distances::get).thenComparing(Comparator.naturalOrder()))
                .toList();
        int index = Math.clamp(requestedPage, 0, Math.max(0, (matches.size() - 1) / PAGE_SIZE));
        int from = index * PAGE_SIZE;
        return new Page(
                List.copyOf(matches.subList(from, Math.min(from + PAGE_SIZE, matches.size()))), index, matches.size());
    }
}
