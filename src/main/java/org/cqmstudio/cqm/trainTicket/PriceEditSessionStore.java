package org.cqmstudio.cqm.trainTicket;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PriceEditSessionStore {
    private static final Map<UUID,Integer> editing = new ConcurrentHashMap<>();
    public static void setEditing(Player p, int lineId) { editing.put(p.getUniqueId(), lineId); }
    public static Integer getEditing(Player p) { return editing.get(p.getUniqueId()); }
    public static void clear(Player p) { editing.remove(p.getUniqueId()); }
    public static boolean isEditing(Player p) { return editing.containsKey(p.getUniqueId()); }
}
