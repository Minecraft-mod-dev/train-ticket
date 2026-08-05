package org.cqmstudio.cqm.trainTicket;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GateSelectSessionStore {
    private static final Map<UUID,Block> selecting = new ConcurrentHashMap<>();
    private static final Map<UUID,Integer> gateLine = new ConcurrentHashMap<>();

    private static final Map<UUID,Integer> selectedStation = new ConcurrentHashMap<>();

    public static void setSelectingGate(Player p, Block b) { selecting.put(p.getUniqueId(), b); }
    public static Block getSelectingGate(Player p) { return selecting.get(p.getUniqueId()); }
    public static void clearSelecting(Player p) { selecting.remove(p.getUniqueId()); gateLine.remove(p.getUniqueId()); selectedStation.remove(p.getUniqueId()); }
    public static void setGateLine(Player p, int lineId) { gateLine.put(p.getUniqueId(), lineId); }
    public static int getGateLine(Player p) { return gateLine.getOrDefault(p.getUniqueId(), -1); }
    public static void setSelectedStation(Player p, int stationId) { selectedStation.put(p.getUniqueId(), stationId); }
    public static int getSelectedStation(Player p) { return selectedStation.getOrDefault(p.getUniqueId(), -1); }
    public static void clearSelected(Player p) { selectedStation.remove(p.getUniqueId()); }
}
