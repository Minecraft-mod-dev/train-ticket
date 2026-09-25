package org.cqmstudio.cqm.trainTicket;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;

public class TicketManager {
    private static JavaPlugin plugin;
    private static NamespacedKey keyPrefix;

    public static void init(JavaPlugin p) {
        plugin = p;
        keyPrefix = new NamespacedKey(plugin, "train_ticket");
    }

    public static ItemStack createTicket(org.bukkit.entity.Player player, int lineId, int startStationId, int endStationId, double price, String lineName, String startName, String endName) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        org.bukkit.inventory.meta.BookMeta meta = (org.bukkit.inventory.meta.BookMeta) book.getItemMeta();
        meta.setTitle("车票: " + lineName + " 打开书本看车票信息");
        meta.setAuthor("TrainTicket");
        meta.addPage("线路: " + lineName + "\n出发: " + startName + "\n到达: " + endName + "\n价格: " + price);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        PersistentDataContainer c = meta.getPersistentDataContainer();
        c.set(new NamespacedKey(plugin, "is_train_ticket"), PersistentDataType.INTEGER, 1);
        c.set(new NamespacedKey(plugin, "line_id"), PersistentDataType.INTEGER, lineId);
        c.set(new NamespacedKey(plugin, "start_id"), PersistentDataType.INTEGER, startStationId);
        c.set(new NamespacedKey(plugin, "end_id"), PersistentDataType.INTEGER, endStationId);
        c.set(new NamespacedKey(plugin, "price"), PersistentDataType.DOUBLE, price);
        // persist ticket to DB and attach ticket id
        try {
            DBManager db = TrainTicket.getInstance().getDbManager();
            int ticketId = db.insertTicket(player.getUniqueId().toString(), lineId, startStationId, endStationId, price);
            if (ticketId != -1) c.set(new NamespacedKey(plugin, "ticket_id"), PersistentDataType.INTEGER, ticketId);
        } catch (Exception ex) {
            TrainTicket.getInstance().getLogger().warning("无法将车票写入DB: " + ex.getMessage());
        }
        book.setItemMeta(meta);
        return book;
    }

    public static boolean isTicket(ItemStack it) {
        if (it == null) return false;
        if (!it.hasItemMeta()) return false;
        PersistentDataContainer c = it.getItemMeta().getPersistentDataContainer();
        Integer v = c.get(new NamespacedKey(plugin, "is_train_ticket"), PersistentDataType.INTEGER);
        return v != null && v == 1;
    }
}
