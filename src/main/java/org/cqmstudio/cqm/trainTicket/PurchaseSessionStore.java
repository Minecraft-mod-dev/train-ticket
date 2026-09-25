package org.cqmstudio.cqm.trainTicket;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PurchaseSessionStore {
    private static final Map<UUID,Integer> selectedLine = new ConcurrentHashMap<>();
    private static final Map<UUID,Integer> startStation = new ConcurrentHashMap<>();

    public static void setSelectedLine(Player p, int lineId) { selectedLine.put(p.getUniqueId(), lineId); }
    public static int getSelectedLine(Player p) { return selectedLine.getOrDefault(p.getUniqueId(), -1); }
    public static void setStartStation(Player p, int stationId) { startStation.put(p.getUniqueId(), stationId); }
    public static int getStartStation(Player p) { return startStation.getOrDefault(p.getUniqueId(), -1); }
    public static void clear(Player p) { selectedLine.remove(p.getUniqueId()); startStation.remove(p.getUniqueId()); }
}
