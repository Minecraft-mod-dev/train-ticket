package org.cqmstudio.cqm.trainTicket;

import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
public class DBManager {
    private final JavaPlugin plugin;
    private Connection conn;
    private File dbFile;
    public DBManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), "stations.db");
    }
    public void init() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS lines(id INTEGER PRIMARY KEY, name TEXT, raw_json TEXT);");
                st.execute("CREATE TABLE IF NOT EXISTS stations(id INTEGER PRIMARY KEY, name TEXT, line_id INTEGER, raw_json TEXT);");
                st.execute("CREATE TABLE IF NOT EXISTS line_prices(line_id INTEGER PRIMARY KEY, price REAL);");
                st.execute("CREATE TABLE IF NOT EXISTS gates(world TEXT, x INTEGER, y INTEGER, z INTEGER, station_id INTEGER, line_id INTEGER, mode TEXT, PRIMARY KEY(world,x,y,z));");
                st.execute("CREATE TABLE IF NOT EXISTS machines(world TEXT, x INTEGER, y INTEGER, z INTEGER, type TEXT, PRIMARY KEY(world,x,y,z));");
                st.execute("CREATE TABLE IF NOT EXISTS tickets(id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid TEXT, line_id INTEGER, start_id INTEGER, end_id INTEGER, price REAL, issued_at INTEGER, consumed INTEGER DEFAULT 0);");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to open sqlite DB: " + e.getMessage());
        }
    }
    public synchronized void upsertLines(JSONArray lines) throws SQLException {
        String sql = "INSERT OR REPLACE INTO lines(id,name,raw_json) VALUES(?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < lines.length(); i++) {
                JSONObject line = lines.getJSONObject(i);
                ps.setInt(1, line.getInt("id"));
                ps.setString(2, line.optString("name", ""));
                ps.setString(3, line.toString());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
    public synchronized void upsertStations(JSONArray stations) throws SQLException {
        String sql = "INSERT OR REPLACE INTO stations(id,name,line_id,raw_json) VALUES(?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < stations.length(); i++) {
                JSONObject s = stations.getJSONObject(i);
                ps.setInt(1, s.getInt("id"));
                ps.setString(2, s.optString("name", ""));
                ps.setInt(3, s.optInt("line_id", 0));
                ps.setString(4, s.toString());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public synchronized java.util.Map<Integer,String> getStationsByLine(int lineId) throws SQLException {
        java.util.Map<Integer,String> out = new java.util.HashMap<>();
        String sql = "SELECT id,name FROM stations WHERE line_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lineId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.put(rs.getInt(1), rs.getString(2));
            }
        }
        return out;
    }

    public synchronized String getStationName(int stationId) throws SQLException {
        String sql = "SELECT name FROM stations WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, stationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        return null;
    }

    public synchronized String getLineName(int lineId) throws SQLException {
        String sql = "SELECT name FROM lines WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lineId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        return null;
    }

    public static class GateConfig {
        public final String world; public final int x,y,z; public final int stationId; public final int lineId; public final String mode;
        public GateConfig(String world,int x,int y,int z,int stationId,int lineId,String mode){this.world=world;this.x=x;this.y=y;this.z=z;this.stationId=stationId;this.lineId=lineId;this.mode=mode;}
    }

    public synchronized void setGateConfig(String world, int x, int y, int z, int stationId, int lineId, String mode) throws SQLException {
        String sql = "INSERT OR REPLACE INTO gates(world,x,y,z,station_id,line_id,mode) VALUES(?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world); ps.setInt(2,x); ps.setInt(3,y); ps.setInt(4,z); ps.setInt(5,stationId); ps.setInt(6,lineId); ps.setString(7,mode);
            ps.executeUpdate();
        }
    }

    public synchronized GateConfig getGateConfig(String world, int x, int y, int z) throws SQLException {
        String sql = "SELECT station_id,line_id,mode FROM gates WHERE world=? AND x=? AND y=? AND z=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world); ps.setInt(2,x); ps.setInt(3,y); ps.setInt(4,z);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new GateConfig(world,x,y,z,rs.getInt(1), rs.getInt(2), rs.getString(3));
            }
        }
        return null;
    }
    public synchronized void setMachine(String world, int x, int y, int z, String type) throws SQLException {
        String sql = "INSERT OR REPLACE INTO machines(world,x,y,z,type) VALUES(?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world); ps.setInt(2,x); ps.setInt(3,y); ps.setInt(4,z); ps.setString(5,type);
            ps.executeUpdate();
        }
    }

    public synchronized String getMachineType(String world, int x, int y, int z) throws SQLException {
        String sql = "SELECT type FROM machines WHERE world=? AND x=? AND y=? AND z=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world); ps.setInt(2,x); ps.setInt(3,y); ps.setInt(4,z);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        return null;
    }

    public synchronized void setLinePrice(int lineId, double price) throws SQLException {
        String sql = "INSERT OR REPLACE INTO line_prices(line_id,price) VALUES(?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lineId);
            ps.setDouble(2, price);
            ps.executeUpdate();
        }
    }
    public synchronized double getLinePrice(int lineId) throws SQLException {
        String sql = "SELECT price FROM line_prices WHERE line_id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lineId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        }
        return 0.0;
    }
    public synchronized List<String> listLines() throws SQLException {
        List<String> out = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT id,name FROM lines ORDER BY id")) {
            while (rs.next()) out.add(rs.getInt(1) + ": " + rs.getString(2));
        }
        return out;
    }

    public synchronized int insertLine(String name, Integer idOpt) throws SQLException {
        if (idOpt != null && idOpt > 0) {
            try (PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO lines(id,name,raw_json) VALUES(?,?,?)")) {
                ps.setInt(1, idOpt);
                ps.setString(2, name);
                ps.setString(3, "{}");
                ps.executeUpdate();
            }
            return idOpt;
        } else {
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO lines(name,raw_json) VALUES(?,?)")) {
                ps.setString(1, name);
                ps.setString(2, "{}");
                ps.executeUpdate();
            }
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) { if (rs.next()) return rs.getInt(1); }
            return -1;
        }
    }

    public synchronized void setLinePosition(int lineId, int position) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE lines SET position=? WHERE id=?")) { ps.setInt(1, position); ps.setInt(2, lineId); ps.executeUpdate(); }
    }
    // tickets management
    public static class TicketRecord {
        public final int id; public final String playerUuid; public final int lineId, startId, endId; public final double price; public final long issuedAt; public final boolean consumed;
        public TicketRecord(int id,String playerUuid,int lineId,int startId,int endId,double price,long issuedAt,boolean consumed){this.id=id;this.playerUuid=playerUuid;this.lineId=lineId;this.startId=startId;this.endId=endId;this.price=price;this.issuedAt=issuedAt;this.consumed=consumed;}
    }

    public synchronized int insertTicket(String playerUuid, int lineId, int startId, int endId, double price) throws SQLException {
        String sql = "INSERT INTO tickets(player_uuid,line_id,start_id,end_id,price,issued_at,consumed) VALUES(?,?,?,?,?,?,0)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            ps.setInt(2, lineId);
            ps.setInt(3, startId);
            ps.setInt(4, endId);
            ps.setDouble(5, price);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        }
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            if (rs.next()) return rs.getInt(1);
        }
        return -1;
    }

    public synchronized List<TicketRecord> listTickets() throws SQLException {
        List<TicketRecord> out = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT id,player_uuid,line_id,start_id,end_id,price,issued_at,consumed FROM tickets ORDER BY issued_at DESC")) {
            while (rs.next()) out.add(new TicketRecord(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getDouble(6), rs.getLong(7), rs.getInt(8) != 0));
        }
        return out;
    }

    public synchronized void setTicketConsumed(int ticketId, boolean consumed) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE tickets SET consumed=? WHERE id=?")) {
            ps.setInt(1, consumed?1:0);
            ps.setInt(2, ticketId);
            ps.executeUpdate();
        }
    }

    public synchronized void deleteTicket(int ticketId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM tickets WHERE id=?")) {
            ps.setInt(1, ticketId);
            ps.executeUpdate();
        }
    }

    public synchronized void deleteLine(int lineId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM lines WHERE id=?")) { ps.setInt(1,lineId); ps.executeUpdate(); }
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM line_prices WHERE line_id=?")) { ps.setInt(1,lineId); ps.executeUpdate(); }
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM stations WHERE line_id=?")) { ps.setInt(1,lineId); ps.executeUpdate(); }
    }

    public synchronized void updateLineName(int lineId, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO lines(id,name,raw_json) VALUES(?,?,COALESCE((SELECT raw_json FROM lines WHERE id=?),'{}'))")) {
            ps.setInt(1, lineId); ps.setString(2, name); ps.setInt(3, lineId); ps.executeUpdate();
        }
    }

    public synchronized int insertStation(String name, int lineId, Integer idOpt) throws SQLException {
        if (idOpt != null && idOpt > 0) {
            try (PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO stations(id,name,line_id,raw_json) VALUES(?,?,?,?)")) {
                ps.setInt(1, idOpt); ps.setString(2, name); ps.setInt(3, lineId); ps.setString(4, "{}"); ps.executeUpdate();
            }
            return idOpt;
        } else {
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO stations(name,line_id,raw_json) VALUES(?,?,?)")) {
                ps.setString(1, name); ps.setInt(2, lineId); ps.setString(3, "{}"); ps.executeUpdate();
            }
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) { if (rs.next()) return rs.getInt(1); }
            return -1;
        }
    }

    public synchronized void updateStation(int stationId, String name, int lineId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO stations(id,name,line_id,raw_json) VALUES(?,?,?,COALESCE((SELECT raw_json FROM stations WHERE id=?),'{}'))")) {
            ps.setInt(1, stationId); ps.setString(2, name); ps.setInt(3, lineId); ps.setInt(4, stationId); ps.executeUpdate();
        }
    }

    public synchronized void deleteStation(int stationId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM stations WHERE id=?")) { ps.setInt(1, stationId); ps.executeUpdate(); }
    }

    // helper to list stations by line with IDs and names
    public synchronized java.util.List<java.util.Map.Entry<Integer,String>> listStationsForLine(int lineId) throws SQLException {
        java.util.List<java.util.Map.Entry<Integer,String>> out = new java.util.ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT id,name FROM stations WHERE line_id=? ORDER BY id")) {
            ps.setInt(1, lineId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new java.util.AbstractMap.SimpleEntry<>(rs.getInt(1), rs.getString(2)));
            }
        }
        return out;
    }

    public void close() {
        try { if (conn != null) conn.close(); } catch (SQLException ignored) {}
    }
}
