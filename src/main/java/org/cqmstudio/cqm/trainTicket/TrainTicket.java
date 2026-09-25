package org.cqmstudio.cqm.trainTicket;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class TrainTicket extends JavaPlugin {

    private static TrainTicket instance;
    private Economy econ;
    private DBManager dbManager;
    private SyncTask syncTask;
    private WebServer webServer;

    @Override
    public void onEnable() {
        instance = this;
        // setup economy (Vault)
        if (!setupEconomy()) {
            getLogger().warning("Vault not found or economy not hooked. Payments will fail.");
        }

        // DB init
        dbManager = new DBManager(this);
        dbManager.init();

        // register listeners and commands
        getServer().getPluginManager().registerEvents(new MachineListener(this), this);
        if (getCommand("setline") != null) getCommand("setline").setExecutor(new SetLineCommand(this));
        if (getCommand("givemachine") != null) getCommand("givemachine").setExecutor(new GiveMachineCommand(this));
        if (getCommand("syncdata") != null) getCommand("syncdata").setExecutor(new SyncCommand(this));

        // ticket manager helper
        TicketManager.init(this);

        // sync task every 30 seconds (600 ticks)
        syncTask = new SyncTask(this, dbManager);
        syncTask.runTaskTimerAsynchronously(this, 0L, 600L);

        // ensure config has default web port and sync URLs (defaults)
        getConfig().addDefault("web.port", 2345);
        getConfig().addDefault("sync.stations_url", "");
        getConfig().addDefault("sync.lines_url", "");
        getConfig().options().copyDefaults(true);
        saveConfig();
        int webPort = getConfig().getInt("web.port", 2345);

        // start web admin panel on configured port
        webServer = new WebServer(this);
        try { webServer.start(webPort); } catch (Exception ex) { getLogger().warning("无法启动 Web 面板: " + ex.getMessage()); }

        getLogger().info("TrainTicket enabled");
    }

    @Override
    public void onDisable() {
        if (syncTask != null) syncTask.cancel();
        if (webServer != null) webServer.stop();
        if (dbManager != null) dbManager.close();
        getLogger().info("TrainTicket disabled");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    public static TrainTicket getInstance() { return instance; }
    public Economy getEconomy() { return econ; }
    public DBManager getDbManager() { return dbManager; }
}
