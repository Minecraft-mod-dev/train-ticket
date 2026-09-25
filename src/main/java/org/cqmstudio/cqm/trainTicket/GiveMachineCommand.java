package org.cqmstudio.cqm.trainTicket;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.Material;

public class GiveMachineCommand implements CommandExecutor {
    private final TrainTicket plugin;
    public GiveMachineCommand(TrainTicket plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) { sender.sendMessage("用法: /givemachine <dispenser|gate|setter> [player]"); return true; }
        String type = args[0].toLowerCase();
        Player target = null;
        if (args.length >= 2) {
            target = plugin.getServer().getPlayerExact(args[1]);
            if (target == null) { sender.sendMessage("玩家未在线: " + args[1]); return true; }
        } else {
            if (sender instanceof Player) target = (Player) sender;
            else { sender.sendMessage("必须指定玩家参数"); return true; }
        }

        ItemStack item;
        NamespacedKey key = new NamespacedKey(plugin, "tt_item");
        switch (type) {
            case "dispenser":
                item = new ItemStack(Material.DISPENSER);
                ItemMeta md = item.getItemMeta();
                md.setDisplayName("售票机");
                md.getPersistentDataContainer().set(key, PersistentDataType.STRING, "machine:dispenser");
                item.setItemMeta(md);
                break;
            case "gate":
                item = new ItemStack(Material.OAK_FENCE_GATE);
                md = item.getItemMeta();
                md.setDisplayName("检票闸机");
                md.getPersistentDataContainer().set(key, PersistentDataType.STRING, "machine:gate");
                item.setItemMeta(md);
                break;
            case "setter":
                item = new ItemStack(Material.WOODEN_HOE);
                md = item.getItemMeta();
                md.setDisplayName("车站设置器");
                md.getPersistentDataContainer().set(key, PersistentDataType.STRING, "tool:setter");
                item.setItemMeta(md);
                break;
            default:
                sender.sendMessage("未知类型: dispenser|gate|setter");
                return true;
        }

        target.getInventory().addItem(item);
        sender.sendMessage("已发放 " + type + " 给 " + target.getName());
        return true;
    }
}
