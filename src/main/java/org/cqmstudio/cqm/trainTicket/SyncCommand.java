package org.cqmstudio.cqm.trainTicket;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SyncCommand implements CommandExecutor {
    private final TrainTicket plugin;
    public SyncCommand(TrainTicket plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("train.sync") && !(sender instanceof Player && ((Player)sender).isOp())) {
            sender.sendMessage("没有权限执行此命令");
            return true;
        }
        sender.sendMessage("开始从云端同步站点与线路数据...");
        // run async sync task
        new SyncTask(plugin, plugin.getDbManager()).runTaskAsynchronously(plugin);
        sender.sendMessage("同步任务已提交，完成后会在服务器日志中记录结果。");
        return true;
    }
}
