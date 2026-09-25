package org.cqmstudio.cqm.trainTicket;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.block.data.Openable;

import java.sql.SQLException;
import java.util.Map;

public class MachineListener implements Listener {
    private final TrainTicket plugin;

    public MachineListener(TrainTicket plugin) { this.plugin = plugin; }

    private Inventory makeLinesInventory() throws SQLException {
        Inventory inv = Bukkit.createInventory(null, 54, "车站: 线路列表");
        int slot = 0;
        for (String s : plugin.getDbManager().listLines()) {
            String[] parts = s.split(":",2);
            ItemStack it = new ItemStack(Material.PAPER);
            ItemMeta m = it.getItemMeta();
            m.setDisplayName(parts.length>1?parts[1].trim():parts[0].trim());
            double price = plugin.getDbManager().getLinePrice(Integer.parseInt(parts[0].trim()));
            m.setLore(java.util.Arrays.asList("线路ID:"+parts[0].trim(), "价格:"+price));
            it.setItemMeta(m);
            inv.setItem(slot++, it);
            if (slot >= 54) break;
        }
        return inv;
    }

    private Inventory makeStationsInventory(int lineId, String title) throws SQLException {
        Inventory inv = Bukkit.createInventory(null, 54, title);
        int slot=0;
        Map<Integer,String> map = plugin.getDbManager().getStationsByLine(lineId);
        for (Map.Entry<Integer,String> e : map.entrySet()) {
            ItemStack it = new ItemStack(Material.MAP);
            ItemMeta m = it.getItemMeta();
            m.setDisplayName(e.getValue());
            m.setLore(java.util.Arrays.asList("站点ID:"+e.getKey()));
            it.setItemMeta(m);
            inv.setItem(slot++, it);
            if (slot>=54) break;
        }
        return inv;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        Block b = e.getClickedBlock();
        if (b == null) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        // Station setter tool
        if (hand != null && hand.getType() == Material.WOODEN_HOE && hand.hasItemMeta() && "车站设置器".equals(hand.getItemMeta().getDisplayName())) {
            if (b.getType() == Material.OAK_FENCE_GATE) {
                try {
                    Inventory inv = makeLinesInventory();
                    p.openInventory(inv);
                    GateSelectSessionStore.setSelectingGate(p, b);
                } catch (SQLException ex) { p.sendMessage(ChatColor.RED + "加载线路失败: " + ex.getMessage()); }
                e.setCancelled(true);
                return;
            }
        }
        // ticket machine (dispenser)
        if (b.getType() == Material.DISPENSER) {
            try {
                String t = plugin.getDbManager().getMachineType(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
                if (t != null && t.equals("machine:dispenser")) {
                    Inventory inv = makeLinesInventory();
                    p.openInventory(inv);
                    PurchaseSessionStore.clear(p);
                    e.setCancelled(true);
                    return;
                } else {
                    // not a plugin ticket machine: allow normal dispenser behavior
                    return;
                }
            } catch (SQLException ex) { p.sendMessage(ChatColor.RED + "读取机器信息失败: " + ex.getMessage()); plugin.getLogger().warning("machine lookup failed: " + ex.getMessage()); return; }
        }
        // gate interaction for ticket use
        if (b.getType() == Material.OAK_FENCE_GATE) {
            e.setCancelled(true); // always cancel default gate opening
            ItemStack it = p.getInventory().getItemInMainHand();
            if (!TicketManager.isTicket(it)) {
                p.sendMessage(ChatColor.RED + "请出示车票才能开闸");
                return;
            }
            try {
                DBManager.GateConfig cfg = plugin.getDbManager().getGateConfig(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
                if (cfg == null) {
                    p.sendMessage(ChatColor.RED + "该闸机尚未配置车站信息");
                    return;
                }
                org.bukkit.persistence.PersistentDataContainer c = it.getItemMeta().getPersistentDataContainer();
                Integer lineId = c.get(new org.bukkit.NamespacedKey(plugin, "line_id"), PersistentDataType.INTEGER);
                Integer endId = c.get(new org.bukkit.NamespacedKey(plugin, "end_id"), PersistentDataType.INTEGER);
                Integer startId = c.get(new org.bukkit.NamespacedKey(plugin, "start_id"), PersistentDataType.INTEGER);
                boolean pass = false;
                boolean consume = false;
                if (cfg.mode != null && cfg.mode.equalsIgnoreCase("IN")) {
                    if (startId != null && cfg.stationId == startId) pass = true;
                } else {
                    if (endId != null && cfg.stationId == endId) { pass = true; consume = true; }
                }
                if (pass) {
                    p.sendMessage(ChatColor.GREEN + (consume?"出站检票通过，车票已消费":"入站检票通过，闸机打开"));
                    // open gate
                    Openable fg = (Openable) b.getBlockData();
                    fg.setOpen(true);
                    b.setBlockData(fg);
                    // schedule close in 30 ticks
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        try {
                            Openable fg2 = (Openable) b.getBlockData(); fg2.setOpen(false); b.setBlockData(fg2);
                        } catch (Exception ignored) {}
                    }, 30L);
                    if (consume) {
                        // mark DB consumed if ticket has id
                        try {
                            Integer ticketDbId = it.getItemMeta().getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "ticket_id"), PersistentDataType.INTEGER);
                            if (ticketDbId != null) plugin.getDbManager().setTicketConsumed(ticketDbId, true);
                        } catch (Exception ex) { plugin.getLogger().warning("标记车票已消费失败: " + ex.getMessage()); }
                        new BukkitRunnable() { public void run() { int amt = it.getAmount(); if (amt<=1) p.getInventory().setItemInMainHand(null); else it.setAmount(amt-1); } }.runTask(plugin);
                    }
                } else {
                    p.sendMessage(ChatColor.RED + "检票失败：车票与本站不符");
                }
            } catch (SQLException ex) { p.sendMessage(ChatColor.RED + "读取闸机配置失败: " + ex.getMessage()); }
            return;
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        ItemStack item = e.getItemInHand();
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String v = item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "tt_item"), PersistentDataType.STRING);
            if (v != null) {
                Block b = e.getBlockPlaced();
                try {
                    plugin.getDbManager().setMachine(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), v);
                    if (e.getPlayer() != null) e.getPlayer().sendMessage(ChatColor.GREEN + "已放置特殊方块: " + v);
                } catch (SQLException ex) { plugin.getLogger().warning("保存机具信息失败: " + ex.getMessage()); }
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        if (title == null) return;
        // 只阻止插件界面（标题以 "车站:" 开头），允许普通背包操作
        if (!title.startsWith("车站:")) return;
        Player p = (Player) e.getWhoClicked();
        e.setCancelled(true);
        if (title.equals("车站: 闸机模式")) {
            ItemStack it = e.getCurrentItem(); if (it==null||!it.hasItemMeta()) return;
            ItemMeta im = it.getItemMeta(); if (!im.hasLore() || im.getLore().isEmpty()) return;
            String lore = im.getLore().get(0);
            String modeStr = lore.contains(":") ? lore.split(":")[1].trim() : lore.trim();
            String dbMode = (modeStr.contains("出") || modeStr.equalsIgnoreCase("OUT")) ? "OUT" : "IN";
            Block selecting = GateSelectSessionStore.getSelectingGate(p);
            int stationIdSel = GateSelectSessionStore.getSelectedStation(p);
            int lineForGate = GateSelectSessionStore.getGateLine(p);
            if (selecting == null || stationIdSel == -1 || lineForGate == -1) { p.sendMessage("会话已过期"); GateSelectSessionStore.clearSelecting(p); GateSelectSessionStore.clearSelected(p); return; }
            try {
                plugin.getDbManager().setGateConfig(selecting.getWorld().getName(), selecting.getX(), selecting.getY(), selecting.getZ(), stationIdSel, lineForGate, dbMode);
                p.sendMessage(ChatColor.GREEN+"闸机绑定站点成功: stationId="+stationIdSel+" line="+lineForGate+" 模式="+dbMode);
            } catch (SQLException ex) { p.sendMessage(ChatColor.RED+"保存闸机配置失败: "+ex.getMessage()); }
            GateSelectSessionStore.clearSelecting(p);
            GateSelectSessionStore.clearSelected(p);
            return;
        }
        if (title.equals("车站: 线路价格")) {
            ItemStack it = e.getCurrentItem(); if (it==null||!it.hasItemMeta()) return; 
            ItemMeta im = it.getItemMeta(); if (!im.hasLore()||im.getLore().isEmpty()) return; 
            String lore = im.getLore().get(0); int lineId; try { lineId=Integer.parseInt(lore.split(":" )[1].trim()); } catch(Exception ex){return;} 
            PriceEditSessionStore.setEditing(p, lineId);
            p.closeInventory();
            p.sendMessage(ChatColor.YELLOW+"请输入价格（聊天中输入数字）为线路 " + lineId + " 设置价格");
            return;
        }

        if (title.equals("车站: 线路列表")) {
            ItemStack it = e.getCurrentItem(); if (it==null || !it.hasItemMeta()) return;
            ItemMeta im = it.getItemMeta();
            if (!im.hasLore() || im.getLore()==null || im.getLore().isEmpty()) return;
            String lore = im.getLore().get(0); // 线路ID:NN
            int lineId;
            try { lineId = Integer.parseInt(lore.split(":" )[1].trim()); } catch (Exception ex) { return; }
            Block selecting = GateSelectSessionStore.getSelectingGate(p);
            try {
                if (selecting != null) {
                    GateSelectSessionStore.setGateLine(p, lineId);
                    Inventory inv = makeStationsInventory(lineId, "车站: 选择站点");
                    p.openInventory(inv);
                    return;
                }
                Inventory inv = makeStationsInventory(lineId, "车站: 选择起点");
                PurchaseSessionStore.setSelectedLine(p, lineId);
                p.openInventory(inv);
            } catch (SQLException ex) { p.sendMessage(ChatColor.RED + "加载站点失败: " + ex.getMessage()); }
            return;
        }
        if (title.startsWith("车站: 选择")) {
            ItemStack it = e.getCurrentItem(); if (it==null||!it.hasItemMeta()) return;
            ItemMeta im = it.getItemMeta();
            if (!im.hasLore() || im.getLore()==null || im.getLore().isEmpty()) return;
            String lore = im.getLore().get(0); // 站点ID:NN
            int stationId;
            try { stationId = Integer.parseInt(lore.split(":" )[1].trim()); } catch (Exception ex) { return; }
            if (title.contains("起点")) {
                int line = PurchaseSessionStore.getSelectedLine(p);
                PurchaseSessionStore.setStartStation(p, stationId);
                try {
                    Inventory inv = makeStationsInventory(line, "车站: 选择终点");
                    p.openInventory(inv);
                } catch (SQLException ex) { p.sendMessage(ChatColor.RED+"加载站点失败: "+ex.getMessage()); }
                return;
            }
            if (title.contains("终点")) {
                int start = PurchaseSessionStore.getStartStation(p);
                int line = PurchaseSessionStore.getSelectedLine(p);
                int end = stationId;
                double price = 0.0;
                try {
                    int endLine = line; 
                    if (endLine == line) {
                        price = plugin.getDbManager().getLinePrice(line);
                    } else {
                        price = plugin.getDbManager().getLinePrice(line) + plugin.getDbManager().getLinePrice(endLine);
                    }
                } catch (SQLException ex) { p.sendMessage(ChatColor.RED+"读取价格失败: "+ex.getMessage()); }
                if (plugin.getEconomy() != null) {
                    plugin.getLogger().info("Charging player " + p.getName() + " amount=" + price);
                    net.milkbowl.vault.economy.EconomyResponse r = plugin.getEconomy().withdrawPlayer(plugin.getServer().getOfflinePlayer(p.getUniqueId()), price);
                    if (!r.transactionSuccess()) { p.sendMessage(ChatColor.RED+"余额不足，购买失败"); plugin.getLogger().warning("Withdraw failed: " + r.errorMessage); return; }
                }
                try {
                    String startName = plugin.getDbManager().getStationName(start);
                    String endName = plugin.getDbManager().getStationName(end);
                    String lineName = "线路#"+line;
                    ItemStack ticket = TicketManager.createTicket(p, line, start, end, price, lineName, startName==null?"":startName, endName==null?"":endName);
                    p.getInventory().addItem(ticket);
                    p.sendMessage(ChatColor.GREEN+"购票成功，价格: "+price);
                } catch (SQLException ex) { p.sendMessage(ChatColor.RED+"生成车票失败: "+ex.getMessage()); }
                return;
            }
            Block selecting = GateSelectSessionStore.getSelectingGate(p);
            if (selecting != null) {
                int lineForGate = GateSelectSessionStore.getGateLine(p);
                // store selected station and open a small inventory for choosing IN/OUT
                GateSelectSessionStore.setSelectedStation(p, stationId);
                Inventory modeInv = Bukkit.createInventory(null, 9, "车站: 闸机模式");
                ItemStack in = new ItemStack(Material.LIME_WOOL);
                ItemMeta imIn = in.getItemMeta();
                imIn.setDisplayName("入站");
                imIn.setLore(java.util.Arrays.asList("模式:入站"));
                in.setItemMeta(imIn);
                ItemStack out = new ItemStack(Material.RED_WOOL);
                ItemMeta imOut = out.getItemMeta();
                imOut.setDisplayName("出站");
                imOut.setLore(java.util.Arrays.asList("模式:出站"));
                out.setItemMeta(imOut);
                modeInv.setItem(3, in);
                modeInv.setItem(5, out);
                p.openInventory(modeInv);
                return;
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (PriceEditSessionStore.isEditing(p)) {
            e.setCancelled(true);
            Integer lineId = PriceEditSessionStore.getEditing(p);
            String msg = e.getMessage();
            try {
                double price = Double.parseDouble(msg.trim());
                plugin.getDbManager().setLinePrice(lineId, price);
                p.sendMessage(ChatColor.GREEN+"线路价格已设置: " + price);
            } catch (Exception ex) {
                p.sendMessage(ChatColor.RED+"价格格式错误: "+ex.getMessage());
            }
            PriceEditSessionStore.clear(p);
        }
    }
}

