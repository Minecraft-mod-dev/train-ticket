package org.cqmstudio.cqm.trainTicket;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;

public class SetLineCommand implements CommandExecutor {
    private final TrainTicket plugin;
    public SetLineCommand(TrainTicket plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("请在游戏内使用 /setline 来打开线路价格界面");
            return true;
        }
        Player p = (Player) sender;
        try {
            Inventory inv = Bukkit.createInventory(null, 54, "车站: 线路价格");
            int slot = 0;
            for (String s : plugin.getDbManager().listLines()) {
                String[] parts = s.split(":",2);
                org.bukkit.inventory.ItemStack it = new org.bukkit.inventory.ItemStack(org.bukkit.Material.PAPER);
                org.bukkit.inventory.meta.ItemMeta m = it.getItemMeta();
                String name = parts.length>1?parts[1].trim():parts[0].trim();
                m.setDisplayName(name);
                double price = plugin.getDbManager().getLinePrice(Integer.parseInt(parts[0].trim()));
                m.setLore(java.util.Arrays.asList("线路ID:"+parts[0].trim(), "价格:"+price));
                it.setItemMeta(m);
                inv.setItem(slot++, it);
                if (slot>=54) break;
            }
            p.openInventory(inv);
        } catch (SQLException ex) { p.sendMessage("加载线路失败: "+ex.getMessage()); }
        return true;
    }
}
