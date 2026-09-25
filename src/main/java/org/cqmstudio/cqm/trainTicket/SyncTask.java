package org.cqmstudio.cqm.trainTicket;

import org.bukkit.scheduler.BukkitRunnable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;

public class SyncTask extends BukkitRunnable {
    private final TrainTicket plugin;
    private final DBManager db;
    private final HttpClient client = HttpClient.newHttpClient();

    public SyncTask(TrainTicket plugin, DBManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    @Override
    public void run() {
        try {
            String stationsUrl = plugin.getConfig().getString("sync.stations_url", "");
            String linesUrl = plugin.getConfig().getString("sync.lines_url", "");

            // stations
            if (stationsUrl != null && !stationsUrl.isBlank()) {
                HttpRequest reqS = HttpRequest.newBuilder().uri(URI.create(stationsUrl)).GET().build();
                HttpResponse<String> respS = client.send(reqS, HttpResponse.BodyHandlers.ofString());
                if (respS.statusCode() == 200) {
                    JSONArray arr = new JSONArray(respS.body());
                    db.upsertStations(arr);
                }
            } else {
                plugin.getLogger().info("跳过 stations 同步：未配置同步地址");
            }
            // lines
            if (linesUrl != null && !linesUrl.isBlank()) {
                HttpRequest reqL = HttpRequest.newBuilder().uri(URI.create(linesUrl)).GET().build();
                HttpResponse<String> respL = client.send(reqL, HttpResponse.BodyHandlers.ofString());
                if (respL.statusCode() == 200) {
                    JSONArray arr = new JSONArray(respL.body());
                    db.upsertLines(arr);
                }
            } else {
                plugin.getLogger().info("跳过 lines 同步：未配置同步地址");
            }
        } catch (IOException | InterruptedException | SQLException e) {
            plugin.getLogger().warning("Sync failed: " + e.getMessage());
        }
    }
}
