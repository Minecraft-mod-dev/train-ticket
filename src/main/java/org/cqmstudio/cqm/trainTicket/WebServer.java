package org.cqmstudio.cqm.trainTicket;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;

public class WebServer {
    private final TrainTicket plugin;
    private HttpServer server;

    public WebServer(TrainTicket plugin) {
        this.plugin = plugin;
    }

    public void start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::handleRoot);
        server.createContext("/setprice", this::handleSetPrice);
        server.createContext("/sync", this::handleSync);
        server.createContext("/tickets", this::handleTickets);
        server.createContext("/tickets/action", this::handleTicketsAction);
        server.createContext("/routes/delete", this::handleDeleteRoute);
        server.createContext("/line", this::handleLinePage);
        server.createContext("/line/save", this::handleLineSave);
        server.createContext("/station/add", this::handleStationAdd);
        server.createContext("/station/edit", this::handleStationEdit);
        server.createContext("/station/delete", this::handleStationDelete);
        server.createContext("/lines/add", this::handleAddLine);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        plugin.getLogger().info("Web panel started on port " + port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            plugin.getLogger().info("Web panel stopped");
        }
    }

    private void handleRoot(HttpExchange ex) throws IOException {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("<html><head><meta charset=\"utf-8\">\n<style>");
            sb.append("body{font-family:Arial,Helvetica,sans-serif;background:#f7f7f8;color:#222;margin:20px;}table{border-collapse:collapse;width:100%;background:#fff}th,td{padding:8px;border:1px solid #ddd}th{background:#f0f0f0}button, input[type=submit]{background:#2b8ddb;color:#fff;border:0;padding:6px 10px;border-radius:4px}input[type=text], input[type=number]{padding:6px;border:1px solid #ccc;border-radius:4px}");
            sb.append(".header{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px}");
            sb.append(".box{background:#fff;padding:12px;border-radius:6px;box-shadow:0 1px 3px rgba(0,0,0,0.08)}");
            sb.append("</style><title>车站线路管理</title></head><body>");
            sb.append("<div class=\"header\"><h2>车站线路管理面板</h2><div><a href=\"/tickets\">车票管理</a> &nbsp; <a href=\"/\">线路管理</a></div></div>");
            sb.append("<div class=\"box\"><form method=\"post\" action=\"/sync\"><button type=\"submit\">手动同步云端数据</button></form></div><br/>");
            sb.append("<div class=\"box\"><h3>添加线路</h3><form method=\"post\" action=\"/lines/add\">名称: <input name=\"name\" /> 可选ID: <input name=\"id\" /> <input type=\"submit\" value=\"添加\"/></form></div><br/>");
            sb.append("<div class=\"box\"><h3>线路列表 / 价格</h3>");
            sb.append("<table><tr><th>线路ID</th><th>线路名</th><th>价格</th><th>操作</th></tr>");

            List<String> lines = plugin.getDbManager().listLines();
            for (String s : lines) {
                String[] parts = s.split(":", 2);
                String id = parts[0].trim();
                String name = parts.length > 1 ? parts[1].trim() : id;
                double price = 0.0;
                try { price = plugin.getDbManager().getLinePrice(Integer.parseInt(id)); } catch (SQLException ignore) {}
                sb.append("<tr>");
                sb.append("<td>").append(id).append("</td>");
                sb.append("<td>").append(escapeHtml(name)).append("</td>");
                sb.append("<td>")
                        .append("<form method=\"post\" action=\"/setprice\" style=\"display:inline\">")
                        .append("<input type=\"hidden\" name=\"lineId\" value=\"").append(id).append("\" />")
                        .append("<input name=\"price\" value=\"").append(price).append("\" />")
                        .append("<input type=\"submit\" value=\"设置价格\" />")
                        .append("</form>")
                        .append("</td>");
                sb.append("<td>")
                        .append("<a href=\"/line?lineId=").append(id).append("\">管理</a> &nbsp;")
                        .append("<form method=\"post\" action=\"/routes/delete\" style=\"display:inline\">")
                        .append("<input type=\"hidden\" name=\"lineId\" value=\"").append(id).append("\" />")
                        .append("<input type=\"submit\" value=\"删除线路\" style=\"background:#d9534f\" />")
                        .append("</form>")
                        .append("</td>");
                sb.append("</tr>");
            }

            sb.append("</table></div>");
            sb.append("</body></html>");

            byte[] resp = sb.toString().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, resp.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(resp); }
        } catch (SQLException e) {
            sendPlain(ex, 500, "读取线路失败: " + e.getMessage());
        }
    }

    private void handleSetPrice(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendPlain(ex, 405, "Method Not Allowed"); return; }
        String body = readRequestBody(ex.getRequestBody());
        Map<String,String> params = parseQuery(body);
        String lineIdStr = params.get("lineId");
        String priceStr = params.get("price");
        if (lineIdStr == null || priceStr == null) { sendPlain(ex, 400, "缺少参数"); return; }
        try {
            int lineId = Integer.parseInt(lineIdStr);
            double price = Double.parseDouble(priceStr);
            plugin.getDbManager().setLinePrice(lineId, price);
            sendPlain(ex, 200, "设置成功, <a href=\"/\">返回</a>");
        } catch (NumberFormatException | SQLException e) {
            sendPlain(ex, 500, "设置失败: " + e.getMessage());
        }
    }


    private void handleDeleteRoute(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendPlain(ex,405,"Method Not Allowed"); return; }
        String body = readRequestBody(ex.getRequestBody());
        Map<String,String> p = parseQuery(body);
        String lineIdS = p.get("lineId");
        if (lineIdS==null) { sendPlain(ex,400,"缺少lineId"); return; }
        try { plugin.getDbManager().deleteLine(Integer.parseInt(lineIdS)); sendPlain(ex,200,"已删除，<a href=\"/\">返回</a>"); } catch (Exception e) { sendPlain(ex,500,"删除失败: "+e.getMessage()); }
    }

    private void handleLinePage(HttpExchange ex) throws IOException {
        try {
            String q = ex.getRequestURI().getQuery();
            Map<String,String> params = parseQuery(q==null?"":q);
            String lineIdS = params.get("lineId");
            if (lineIdS==null) { sendPlain(ex,400,"缺少lineId"); return; }
            int lineId = Integer.parseInt(lineIdS);
            String lineName = plugin.getDbManager().getLineName(lineId);
            java.util.List<java.util.Map.Entry<Integer,String>> stations = plugin.getDbManager().listStationsForLine(lineId);
            StringBuilder sb = new StringBuilder();
            sb.append("<html><head><meta charset=\"utf-8\"><title>管理线路</title></head><body>");
            sb.append("<h2>管理线路 - "+escapeHtml(lineName==null?String.valueOf(lineId):lineName)+"</h2>");
            sb.append("<form method=\"post\" action=\"/line/save\">线路ID: <input name=\"lineId\" value=\""+lineId+"\" readonly/> 名称: <input name=\"name\" value=\""+escapeHtml(lineName==null?"":lineName)+"\" /> <input type=\"submit\" value=\"保存\"/></form>");
            sb.append("<h3>站点列表</h3><table><tr><th>ID</th><th>名称</th><th>操作</th></tr>");
            for (java.util.Map.Entry<Integer,String> e: stations) {
                sb.append("<tr><td>").append(e.getKey()).append("</td><td>").append(escapeHtml(e.getValue())).append("</td><td>");
                sb.append("<form method=\"post\" action=\"/station/delete\" style=\"display:inline\"><input type=\"hidden\" name=\"stationId\" value=\"").append(e.getKey()).append("\"/><input type=\"submit\" value=\"删除\"/></form>");
                sb.append("</td></tr>");
            }
            sb.append("</table>");
            sb.append("<h3>添加站点</h3><form method=\"post\" action=\"/station/add\">名称: <input name=\"name\" /> <input type=\"hidden\" name=\"lineId\" value=\""+lineId+"\"/> 可选ID: <input name=\"id\" /> <input type=\"submit\" value=\"添加\"/></form>");
            sb.append("<p><a href=\"/\">返回</a></p>");
            sb.append("</body></html>");
            byte[] resp = sb.toString().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type","text/html; charset=utf-8");
            ex.sendResponseHeaders(200, resp.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(resp); }
        } catch (SQLException e) { sendPlain(ex,500,"读取线路失败: "+e.getMessage()); }
    }

    private void handleLineSave(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendPlain(ex,405,"Method Not Allowed"); return; }
        String body = readRequestBody(ex.getRequestBody()); Map<String,String> p = parseQuery(body);
        String lineIdS = p.get("lineId"); String name = p.get("name"); if (lineIdS==null||name==null) { sendPlain(ex,400,"缺少参数"); return; }
        try { plugin.getDbManager().updateLineName(Integer.parseInt(lineIdS), name); sendPlain(ex,200,"已保存，<a href=\"/\">返回</a>"); } catch (Exception e) { sendPlain(ex,500,"保存失败: "+e.getMessage()); }
    }

    private void handleStationAdd(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendPlain(ex,405,"Method Not Allowed"); return; }
        String body = readRequestBody(ex.getRequestBody()); Map<String,String> p = parseQuery(body);
        String name = p.get("name"); String lineIdS = p.get("lineId"); String idS = p.get("id"); if (name==null||lineIdS==null) { sendPlain(ex,400,"缺少参数"); return; }
        try { Integer idOpt = (idS==null||idS.isEmpty())?null:Integer.parseInt(idS); plugin.getDbManager().insertStation(name, Integer.parseInt(lineIdS), idOpt); sendPlain(ex,200,"已添加，<a href=\"/line?lineId="+lineIdS+"\">返回</a>"); } catch (Exception e) { sendPlain(ex,500,"添加失败: "+e.getMessage()); }
    }

    private void handleStationEdit(HttpExchange ex) throws IOException {
        // For simplicity treat as POST update
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendPlain(ex,405,"Method Not Allowed"); return; }
        String body = readRequestBody(ex.getRequestBody()); Map<String,String> p = parseQuery(body);
        String stationIdS = p.get("stationId"); String name = p.get("name"); String lineIdS = p.get("lineId"); if (stationIdS==null||name==null||lineIdS==null) { sendPlain(ex,400,"缺少参数"); return; }
        try { plugin.getDbManager().updateStation(Integer.parseInt(stationIdS), name, Integer.parseInt(lineIdS)); sendPlain(ex,200,"已更新，<a href=\"/line?lineId="+lineIdS+"\">返回</a>"); } catch (Exception e) { sendPlain(ex,500,"更新失败: "+e.getMessage()); }
    }

    private void handleStationDelete(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendPlain(ex,405,"Method Not Allowed"); return; }
        String body = readRequestBody(ex.getRequestBody()); Map<String,String> p = parseQuery(body);
        String stationIdS = p.get("stationId"); if (stationIdS==null) { sendPlain(ex,400,"缺少stationId"); return; }
        try { plugin.getDbManager().deleteStation(Integer.parseInt(stationIdS)); sendPlain(ex,200,"已删除，<a href=\"/\">返回</a>"); } catch (Exception e) { sendPlain(ex,500,"删除失败: "+e.getMessage()); }
    }

    private void handleTickets(HttpExchange ex) throws IOException {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("<html><head><meta charset=\"utf-8\"><title>车票管理</title>");
            sb.append("<style>body{font-family:Arial,Helvetica,sans-serif;background:#f7f7f8;margin:20px}table{border-collapse:collapse;width:100%;background:#fff}th,td{padding:8px;border:1px solid #ddd}th{background:#f0f0f0}button, input[type=submit]{background:#2b8ddb;color:#fff;border:0;padding:6px 10px;border-radius:4px}input[type=text],input[type=number]{padding:6px;border:1px solid #ccc;border-radius:4px}</style>");
            sb.append("</head><body>");
            sb.append("<h2>车票管理</h2>");
            sb.append("<p><a href=\"/\">返回线路管理</a></p>");
            sb.append("<div style=\"background:#fff;padding:12px;border-radius:6px;box-shadow:0 1px 3px rgba(0,0,0,0.08)\"><h3>发放车票</h3>");
            sb.append("<form method=\"post\" action=\"/tickets/action\">玩家名: <input name=\"player\" /> 线路ID: <input name=\"lineId\" /> 起点ID: <input name=\"startId\" /> 终点ID: <input name=\"endId\" /> 价格: <input name=\"price\" /> <input type=\"hidden\" name=\"action\" value=\"issue\" /> <input type=\"submit\" value=\"发放\" /></form></div><br/>");
            sb.append("<div style=\"background:#fff;padding:12px;border-radius:6px;box-shadow:0 1px 3px rgba(0,0,0,0.08)\"><h3>已发放车票</h3>");
            sb.append("<table><tr><th>ID</th><th>玩家UUID</th><th>线路</th><th>起点</th><th>终点</th><th>价格</th><th>时间</th><th>状态</th><th>操作</th></tr>");
            List<DBManager.TicketRecord> tickets = plugin.getDbManager().listTickets();
            for (DBManager.TicketRecord t : tickets) {
                String player = t.playerUuid==null?"":escapeHtml(t.playerUuid);
                String line = String.valueOf(t.lineId);
                String start = String.valueOf(t.startId);
                String end = String.valueOf(t.endId);
                String issued = new java.util.Date(t.issuedAt).toString();
                String state = t.consumed?"已消费":"未消费";
                sb.append("<tr>");
                sb.append("<td>").append(t.id).append("</td>");
                sb.append("<td>").append(player).append("</td>");
                sb.append("<td>").append(line).append("</td>");
                sb.append("<td>").append(start).append("</td>");
                sb.append("<td>").append(end).append("</td>");
                sb.append("<td>").append(t.price).append("</td>");
                sb.append("<td>").append(escapeHtml(issued)).append("</td>");
                sb.append("<td>").append(state).append("</td>");
                sb.append("<td>");
                if (!t.consumed) {
                    sb.append("<form method=\"post\" action=\"/tickets/action\" style=\"display:inline\"><input type=\"hidden\" name=\"action\" value=\"consume\" /><input type=\"hidden\" name=\"ticketId\" value=\"").append(t.id).append("\" /><input type=\"submit\" value=\"标记已消费\" /></form>");
                }
                sb.append("<form method=\"post\" action=\"/tickets/action\" style=\"display:inline;margin-left:6px\"><input type=\"hidden\" name=\"action\" value=\"revoke\" /><input type=\"hidden\" name=\"ticketId\" value=\"").append(t.id).append("\" /><input type=\"submit\" value=\"撤销\" style=\"background:#d9534f\" /></form>");
                sb.append("</td>");
                sb.append("</tr>");
            }
            sb.append("</table></div>");
            sb.append("</body></html>");
            byte[] resp = sb.toString().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, resp.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(resp); }
        } catch (SQLException e) { sendPlain(ex,500,"读取车票失败: "+e.getMessage()); }
    }

    private void handleTicketsAction(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendPlain(ex,405,"Method Not Allowed"); return; }
        String body = readRequestBody(ex.getRequestBody());
        Map<String,String> params = parseQuery(body);
        String action = params.get("action");
        if (action == null) { sendPlain(ex,400,"缺少action"); return; }
        try {
            if (action.equals("consume")) {
                int tid = Integer.parseInt(params.get("ticketId"));
                plugin.getDbManager().setTicketConsumed(tid, true);
                sendPlain(ex,200,"已标记为已消费，<a href=\"/tickets\">返回</a>");
                return;
            } else if (action.equals("revoke")) {
                int tid = Integer.parseInt(params.get("ticketId"));
                plugin.getDbManager().deleteTicket(tid);
                sendPlain(ex,200,"已撤销车票，<a href=\"/tickets\">返回</a>");
                return;
            } else if (action.equals("issue")) {
                String playerName = params.get("player");
                String lineIdS = params.get("lineId");
                String startS = params.get("startId");
                String endS = params.get("endId");
                String priceS = params.get("price");
                if (playerName==null||lineIdS==null||startS==null||endS==null||priceS==null) { sendPlain(ex,400,"缺少参数"); return; }
                int lineId = Integer.parseInt(lineIdS); int startId = Integer.parseInt(startS); int endId = Integer.parseInt(endS); double price = Double.parseDouble(priceS);
                java.util.UUID uuid = plugin.getServer().getOfflinePlayer(playerName).getUniqueId();
                int nid = plugin.getDbManager().insertTicket(uuid.toString(), lineId, startId, endId, price);
                // if player online, give item
                org.bukkit.entity.Player online = plugin.getServer().getPlayer(uuid);
                if (online != null) {
                    String startName = plugin.getDbManager().getStationName(startId);
                    String endName = plugin.getDbManager().getStationName(endId);
                    String lineName = "线路#"+lineId;
                    org.bukkit.inventory.ItemStack ticket = TicketManager.createTicket(online, lineId, startId, endId, price, lineName, startName==null?"":startName, endName==null?"":endName);
                    online.getInventory().addItem(ticket);
                }
                sendPlain(ex,200,"已发放车票，<a href=\"/tickets\">返回</a>");
                return;
            }
        } catch (Exception e) { sendPlain(ex,500,"操作失败: "+e.getMessage()); return; }
        sendPlain(ex,400,"未知action");
    }

    private void handleSync(HttpExchange ex) throws IOException {
        // schedule async sync task
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            new SyncTask(plugin, plugin.getDbManager()).run();
        });
        sendPlain(ex, 200, "已触发同步任务，稍后生效。<a href=\"/\">返回</a>");
    }

    private void handleAddLine(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendPlain(ex,405,"Method Not Allowed"); return; }
        String body = readRequestBody(ex.getRequestBody()); Map<String,String> p = parseQuery(body);
        String name = p.get("name"); String idS = p.get("id"); if (name==null) { sendPlain(ex,400,"缺少名称"); return; }
        try {
            Integer idOpt = (idS==null||idS.isEmpty())?null:Integer.parseInt(idS);
            int nid = plugin.getDbManager().insertLine(name, idOpt);
            sendPlain(ex,200,"已添加线路，<a href=\"/line?lineId="+nid+"\">管理新线路</a>");
        } catch (Exception e) { sendPlain(ex,500,"添加失败: "+e.getMessage()); }
    }

    private void handleReorderLines(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendPlain(ex,405,"Method Not Allowed"); return; }
        String body = readRequestBody(ex.getRequestBody()); Map<String,String> p = parseQuery(body);
        String order = p.get("order"); if (order==null) { sendPlain(ex,400,"缺少order"); return; }
        try {
            String[] parts = order.split(",");
            for (int i=0;i<parts.length;i++) {
                String s = parts[i].trim(); if (s.isEmpty()) continue; plugin.getDbManager().setLinePosition(Integer.parseInt(s), i);
            }
            sendPlain(ex,200,"已保存排序，<a href=\"/\">返回</a>");
        } catch (Exception e) { sendPlain(ex,500,"保存失败: "+e.getMessage()); }
    }

    private void sendPlain(HttpExchange ex, int code, String msg) throws IOException {
        byte[] resp = ("<html><meta charset=\"utf-8\"><body>"+msg+"</body></html>").getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(code, resp.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(resp); }
    }

    private String readRequestBody(InputStream is) throws IOException {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    private Map<String,String> parseQuery(String q) {
        return java.util.Arrays.stream(q.split("&"))
                .map(s -> s.split("=",2))
                .filter(arr -> arr.length==2)
                .collect(Collectors.toMap(arr -> urlDecode(arr[0]), arr -> urlDecode(arr[1])));
    }

    private String urlDecode(String s) { try { return URLDecoder.decode(s, "UTF-8"); } catch (Exception e) { return s; } }

    private String escapeHtml(String s) {
        if (s==null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
}
