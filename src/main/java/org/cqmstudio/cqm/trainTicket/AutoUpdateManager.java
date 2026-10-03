package org.cqmstudio.cqm.trainTicket;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class AutoUpdateManager {
    private final TrainTicket plugin;
    private BukkitTask scheduledTask;

    public AutoUpdateManager(TrainTicket plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("update.auto_enable", false)) {
            plugin.getLogger().info("AutoUpdate: disabled in config");
            return;
        }

        int minutes = Math.max(1, plugin.getConfig().getInt("update.check_interval_minutes", 60));
        long intervalTicks = 20L * 60L * minutes;
        scheduledTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::runOnce, 20L * 10L, intervalTicks);
        plugin.getLogger().info("AutoUpdate: scheduled every " + minutes + " minutes");
    }

    public void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
        }
    }

    public void runOnce() {
        try {
            String projectId = plugin.getConfig().getString("update.modrinth_project", "H8DKfMWO");
            boolean includeBeta = plugin.getConfig().getBoolean("update.include_beta", false);
            boolean includeAlpha = plugin.getConfig().getBoolean("update.include_alpha", false);
            boolean autoApply = plugin.getConfig().getBoolean("update.auto_apply", false);
            String targetMc = plugin.getConfig().getString("update.target_mc_version", "");
            if (targetMc == null || targetMc.trim().isEmpty()) {
                plugin.getLogger().warning("AutoUpdate: update.target_mc_version not configured; skipping update check");
                return;
            }

            List<ModrinthVersion> versions = fetchModrinthVersions(projectId);
            if (versions.isEmpty()) {
                plugin.getLogger().info("AutoUpdate: no Modrinth versions found for project " + projectId);
                return;
            }

            List<ModrinthVersion> candidates = versions.stream()
                    .filter(v -> v.gameVersions.contains(targetMc))
                    .filter(v -> {
                        String type = v.versionType == null ? "" : v.versionType.toLowerCase();
                        if ("release".equals(type)) return true;
                        if ("beta".equals(type)) return includeBeta;
                        if ("alpha".equals(type)) return includeAlpha;
                        return false;
                    })
                    .collect(Collectors.toList());

            if (candidates.isEmpty()) {
                plugin.getLogger().info("AutoUpdate: no release/beta/alpha versions match MC " + targetMc + " and current filters");
                return;
            }

            candidates.sort((a, b) -> b.datePublished.compareTo(a.datePublished));
            ModrinthVersion latest = candidates.get(0);
            String currentVersion = plugin.getDescription().getVersion();
            plugin.getLogger().info("AutoUpdate: current=" + currentVersion + ", latest=" + latest.versionNumber + " (" + latest.versionType + ")");

            if (!isNewer(latest.versionNumber, currentVersion)) {
                plugin.getLogger().info("AutoUpdate: already latest or remote version is not newer");
                return;
            }

            String jarUrl = latest.getPrimaryJarUrl();
            if (jarUrl == null || jarUrl.trim().isEmpty()) {
                plugin.getLogger().warning("AutoUpdate: no jar file was found in the latest Modrinth version");
                return;
            }

            try {
                File data = plugin.getDataFolder();
                if (!data.exists()) data.mkdirs();
            } catch (Exception ignored) {}

            File pluginsDir = plugin.getDataFolder().getParentFile();
            if (pluginsDir == null || !pluginsDir.exists()) {
                plugin.getLogger().severe("AutoUpdate: plugins directory not found");
                return;
            }

            String currentJarName = findCurrentJarName();
            if (currentJarName == null) {
                String safeName = plugin.getDescription().getName().replaceAll("[^A-Za-z0-9._-]", "-").toLowerCase();
                currentJarName = safeName + "-" + latest.versionNumber + ".jar";
            }
            File targetJar = new File(pluginsDir, currentJarName);
            File backupJar = new File(pluginsDir, currentJarName + ".bak");
            try {
                if (targetJar.exists()) {
                    Files.copy(targetJar.toPath(), backupJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "AutoUpdate: backup failed: " + ex.getMessage(), ex);
            }

            File tempJar = new File(pluginsDir, currentJarName + ".download." + System.nanoTime());
            try {
                if (tempJar.exists()) Files.delete(tempJar.toPath());
            } catch (Exception ignored) {}

            if (!downloadFile(jarUrl, tempJar)) {
                plugin.getLogger().warning("AutoUpdate: download failed");
                try { Files.deleteIfExists(tempJar.toPath()); } catch (Exception ignored) {}
                return;
            }

            try {
                try {
                    Files.move(tempJar.toPath(), targetJar.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (Exception atomicEx) {
                    plugin.getLogger().log(Level.WARNING, "AutoUpdate: atomic move failed, fallback to normal move: " + atomicEx.getMessage(), atomicEx);
                    Files.move(tempJar.toPath(), targetJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "AutoUpdate: move to target failed: " + ex.getMessage(), ex);
                try { Files.deleteIfExists(tempJar.toPath()); } catch (Exception ignored) {}
                return;
            }

            plugin.getLogger().info("AutoUpdate: new jar saved to " + targetJar.getAbsolutePath());

            if (autoApply) {
                applyHotReload(targetJar, latest.versionNumber);
            } else {
                plugin.getLogger().info("AutoUpdate: auto_apply=false, update downloaded successfully. Restart the server to apply it.");
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "AutoUpdate: unexpected error: " + ex.getMessage(), ex);
        }
    }

    private boolean isNewer(String remote, String current) {
        if (remote == null || remote.trim().isEmpty()) return false;
        if (current == null || current.trim().isEmpty()) return true;
        if (remote.equalsIgnoreCase(current)) return false;

        String remoteNormal = remote.replaceAll("[^0-9.]", "");
        String currentNormal = current.replaceAll("[^0-9.]", "");
        if (!remoteNormal.isEmpty() && !currentNormal.isEmpty()) {
            String[] remoteParts = remoteNormal.split("\\.");
            String[] currentParts = currentNormal.split("\\.");
            int max = Math.max(remoteParts.length, currentParts.length);
            for (int i = 0; i < max; i++) {
                int remoteVal = i < remoteParts.length && !remoteParts[i].isEmpty() ? Integer.parseInt(remoteParts[i]) : 0;
                int currentVal = i < currentParts.length && !currentParts[i].isEmpty() ? Integer.parseInt(currentParts[i]) : 0;
                if (remoteVal != currentVal) {
                    return remoteVal > currentVal;
                }
            }
        }

        return remote.compareToIgnoreCase(current) > 0;
    }

    private String findCurrentJarName() {
        File pluginsDir = plugin.getDataFolder().getParentFile();
        if (pluginsDir == null || !pluginsDir.exists()) return null;

        File[] jars = pluginsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jars == null) return null;

        String pluginName = plugin.getDescription().getName().toLowerCase();
        for (File jar : jars) {
            String name = jar.getName().toLowerCase();
            if (name.contains(pluginName)) return jar.getName();
        }

        for (File jar : jars) {
            String name = jar.getName().toLowerCase();
            if (name.contains("train") || name.contains("ticket")) return jar.getName();
        }
        return null;
    }

    private boolean downloadFile(String urlString, File outputFile) {
        try {
            Path parent = outputFile.toPath().getParent();
            if (parent != null && !Files.exists(parent)) Files.createDirectories(parent);

            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("User-Agent", "TrainTicket-Updater");
            int code = conn.getResponseCode();
            if (code != 200) {
                plugin.getLogger().warning("AutoUpdate: Modrinth responded with HTTP " + code + " for " + urlString);
                return false;
            }

            try (InputStream is = new BufferedInputStream(conn.getInputStream());
                 FileOutputStream fos = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                }
                fos.flush();
                return true;
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "AutoUpdate: failed to download file: " + ex.getMessage(), ex);
            return false;
        }
    }

    private void applyHotReload(File downloadedJar, String newVersion) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Plugin p = Bukkit.getPluginManager().getPlugin(plugin.getDescription().getName());
                if (p == null) {
                    plugin.getLogger().warning("AutoUpdate: plugin instance not found, cannot hot reload");
                    return;
                }

                plugin.getLogger().info("AutoUpdate: disabling plugin to apply update");
                Bukkit.getPluginManager().disablePlugin(p);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    try {
                        Bukkit.getPluginManager().enablePlugin(p);
                        plugin.getLogger().info("AutoUpdate: hot reload attempt finished for version " + newVersion + ".");
                    } catch (Exception ex) {
                        plugin.getLogger().log(Level.SEVERE, "AutoUpdate: hot reload failed: " + ex.getMessage(), ex);
                    }
                }, 20L);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "AutoUpdate: failed to apply update: " + ex.getMessage(), ex);
            }
        });
    }

    private List<ModrinthVersion> fetchModrinthVersions(String projectId) {
        try {
            String url = "https://api.modrinth.com/v2/project/" + projectId + "/version";
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "TrainTicket-Updater");

            if (conn.getResponseCode() != 200) {
                plugin.getLogger().warning("AutoUpdate: Modrinth API response: " + conn.getResponseCode());
                return Collections.emptyList();
            }

            try (InputStream is = conn.getInputStream(); Scanner scanner = new Scanner(is, "UTF-8")) {
                String json = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                JSONArray arr = new JSONArray(json);
                List<ModrinthVersion> list = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    list.add(ModrinthVersion.fromJson(arr.getJSONObject(i)));
                }
                return list;
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "AutoUpdate: failed to fetch Modrinth versions: " + ex.getMessage(), ex);
            return Collections.emptyList();
        }
    }

    private static class ModrinthVersion {
        private final String versionNumber;
        private final String versionType;
        private final List<String> gameVersions;
        private final List<ModrinthFile> files;
        private final Date datePublished;

        private ModrinthVersion(String versionNumber, String versionType, List<String> gameVersions, List<ModrinthFile> files, Date datePublished) {
            this.versionNumber = versionNumber;
            this.versionType = versionType;
            this.gameVersions = gameVersions;
            this.files = files;
            this.datePublished = datePublished;
        }

        private static ModrinthVersion fromJson(JSONObject obj) {
            String versionNumber = obj.optString("version_number", "");
            String versionType = obj.optString("version_type", "release");

            JSONArray versions = obj.optJSONArray("game_versions");
            List<String> gameVersions = new ArrayList<>();
            if (versions != null) {
                for (int i = 0; i < versions.length(); i++) {
                    gameVersions.add(versions.getString(i));
                }
            }

            JSONArray fileArray = obj.optJSONArray("files");
            List<ModrinthFile> files = new ArrayList<>();
            if (fileArray != null) {
                for (int i = 0; i < fileArray.length(); i++) {
                    JSONObject fileObj = fileArray.getJSONObject(i);
                    files.add(new ModrinthFile(fileObj.optString("url", ""), fileObj.optString("filename", "")));
                }
            }

            String published = obj.optString("date_published", "");
            Date date;
            try {
                if (published == null || published.isEmpty()) {
                    date = Date.from(Instant.now());
                } else {
                    date = Date.from(Instant.parse(published));
                }
            } catch (Exception ex) {
                date = Date.from(Instant.now());
            }

            return new ModrinthVersion(versionNumber, versionType, gameVersions, files, date);
        }

        private String getPrimaryJarUrl() {
            for (ModrinthFile file : files) {
                if (file.url != null && file.url.toLowerCase().endsWith(".jar")) {
                    return file.url;
                }
            }
            return files.isEmpty() ? null : files.get(0).url;
        }
    }

    private static class ModrinthFile {
        private final String url;
        private final String filename;

        private ModrinthFile(String url, String filename) {
            this.url = url;
            this.filename = filename;
        }
    }
}
