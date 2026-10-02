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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
            List<String> lines = plugin.getDbManager().listLines();
            List<String> stationTemplates = buildStationTemplates(lines);

            StringBuilder sb = new StringBuilder();
            sb.append("<html><head><meta charset=\"utf-8\"><style>");
            sb.append("html,body{margin:0;padding:0;background:linear-gradient(180deg,#f6f8ff 0%,#eef3fb 100%);font-family:Segoe UI,Microsoft YaHei,Arial,sans-serif;color:#1f2a37;}");
            sb.append("*{box-sizing:border-box;}a{text-decoration:none;color:#2f6fed;}a:hover{text-decoration:underline;}");
            sb.append(".app{max-width:1250px;margin:28px auto;padding:0 18px 30px;} .topbar{display:flex;justify-content:space-between;align-items:center;gap:18px;margin-bottom:18px;} .brand{display:flex;align-items:center;gap:12px;}");
            sb.append(".brand-mark{width:38px;height:38px;border-radius:12px;background:linear-gradient(135deg,#62a0ff,#4f46e5);box-shadow:0 10px 24px rgba(79,70,229,.25);color:#fff;font-weight:700;display:flex;align-items:center;justify-content:center;} .brand h1{margin:0;font-size:28px;letter-spacing:.3px;}");
            sb.append(".nav{display:flex;gap:12px;align-items:center;flex-wrap:wrap;} .nav a{padding:8px 12px;border-radius:999px;background:rgba(255,255,255,.7);border:1px solid rgba(148,163,184,.2);font-weight:600;}");
            sb.append(".workspace{display:grid;grid-template-columns:minmax(0,2fr) minmax(310px,420px);gap:18px;} .panel{background:rgba(255,255,255,.88);border:1px solid rgba(148,163,184,.18);border-radius:20px;padding:18px;box-shadow:0 10px 28px rgba(15,23,42,.06);} .panel h3{margin:0 0 14px;font-size:20px;}");
            sb.append("table{width:100%;border-collapse:separate;border-spacing:0;background:#fff;border-radius:14px;overflow:hidden;border:1px solid #eaf0fb;} th,td{padding:12px 10px;border-bottom:1px solid #edf2fb;text-align:left;} th{background:#f8faff;color:#46596b;font-size:13px;font-weight:700;letter-spacing:.2px;text-transform:uppercase;} tr:last-child td{border-bottom:none;} ");
            sb.append("td .tiny{font-size:12px;color:#64748b}.stat-pill{display:inline-block;padding:4px 8px;border-radius:999px;background:#edf4ff;color:#2f6fed;font-size:12px;font-weight:700;} ");
            sb.append("form{margin:0;} .field{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin-bottom:10px;} .field label{font-size:13px;color:#475569;font-weight:600;min-width:70px;} .field input, .field select, .field textarea{padding:9px 10px;border:1px solid #d9e2f1;border-radius:10px;background:#fff;color:#1f2937;font-size:14px;outline:none;transition:.2s ease;} .field input:focus, .field select:focus, .field textarea:focus{border-color:#7aa7ff;box-shadow:0 0 0 4px rgba(79,112,255,.12);} .field input[type=text], .field input[type=number]{min-width:120px;} .field select[multiple]{min-height:180px;padding:8px;}");
            sb.append("button, input[type=submit]{border:none;border-radius:10px;padding:10px 14px;background:linear-gradient(135deg,#4f9cff,#3b82f6);color:#fff;font-weight:700;cursor:pointer;box-shadow:0 10px 18px rgba(59,130,246,.18);} button.secondary, input.secondary{background:#e2e8f0;color:#334155;box-shadow:none;} ");
            sb.append(".inline-form{display:flex;gap:8px;align-items:center;flex-wrap:wrap;} .metrics{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:14px;} .mini-card{display:inline-flex;align-items:center;padding:7px 10px;border-radius:10px;background:#f8faff;color:#374151;font-size:12px;font-weight:700;border:1px solid #ebf1fe;} ");
            sb.append(".muted{color:#64748b;font-size:12px;line-height:1.6;} .danger{background:linear-gradient(135deg,#f87171,#ef4444)!important;} .spacer{height:8px;} .action-row{display:flex;gap:8px;align-items:center;flex-wrap:wrap;} ");
            sb.append("@media (max-width:980px){.workspace{grid-template-columns:1fr;} .topbar{flex-direction:column;align-items:flex-start;} .brand h1{font-size:24px;}} ");
            sb.append("</style></head><body><div class=\"app\"><div class=\"topbar\"><div class=\"brand\"><div class=\"brand-mark\">T</div><h1>TrainTicket 管理</h1></div><div class=\"nav\"><a href=\"/\">首页</a><a href=\"/tickets\">车票管理</a><form method=\"post\" action=\"/sync\" style=\"margin:0;display:inline\"><button type=\"submit\">同步数据</button></form></div></div>");
            sb.append("<div class=\"workspace\"><div class=\"panel\"><h3>线路列表</h3><div class=\"metrics\"><div class=\"mini-card\">线路总数: <span class=\"stat-pill\">" ).append(lines.size()).append("</span></div><div class=\"mini-card\">站点模板: <span class=\"stat-pill\">" ).append(stationTemplates.size()).append("</span></div></div>");
            sb.append("<table><thead><tr><th style=\"width:80px\">ID</th><th>线路名</th><th style=\"width:170px\">价格</th><th style=\"width:240px\">操作</th></tr></thead><tbody>");
            for (String s : lines) {
                String[] parts = s.split(":", 2);
                String id = parts[0].trim();
                String name = parts.length > 1 ? parts[1].trim() : id;
                double price = 0.0;
                try { price = plugin.getDbManager().getLinePrice(Integer.parseInt(id)); } catch (SQLException ignore) {}
                sb.append("<tr>");
                sb.append("<td>").append(id).append("</td>");
                sb.append("<td>").append(escapeHtml(name)).append("</td>");
                sb.append("<td><form method=\"post\" action=\"/setprice\" class=\"inline-form\"><input type=\"hidden\" name=\"lineId\" value=\"").append(id).append("\" /><input type=\"number\" step=\"0.01\" name=\"price\" value=\"").append(price).append("\" /><input type=\"submit\" value=\"更新\" /></form></td>");
                sb.append("<td class=\"action-row\"><a href=\"/line?lineId=").append(id).append("\">管理</a><form method=\"post\" action=\"/routes/delete\" style=\"display:inline\"><input type=\"hidden\" name=\"lineId\" value=\"").append(id).append("\" /><button type=\"submit\" class=\"danger\">删除</button></form></td>");
                sb.append("</tr>");
            }
            sb.append("</tbody></table></div>");
            sb.append("<div class=\"panel\"><h3>新建线路</h3><form method=\"post\" action=\"/lines/add\"><div class=\"field\"><label>名称</label><input type=\"text\" name=\"name\" required placeholder=\"例如: 西门-总站\" /></div>");
            sb.append("<div class=\"field\"><label>ID</label><input type=\"number\" name=\"id\" min=\"1\" placeholder=\"可选\" /></div>");
            sb.append("<div class=\"field\" style=\"display:block\"><label style=\"display:block;margin-bottom:6px;\">站点模板（多选）</label><select name=\"stationName\" multiple size=\"8\" style=\"width:100%\">");
            if (stationTemplates.isEmpty()) {
                sb.append("<option value=\"\">暂无可复用站点模板</option>");
            } else {
                for (String name : stationTemplates) {
                    sb.append("<option value=\"").append(escapeHtml(name)).append("\">").append(escapeHtml(name)).append("</option>");
                }
            }
            sb.append("</select><div class=\"muted\" style=\"margin-top:8px;\">可按住 Ctrl / Command 多选；创建后会按所选站点名批量插入到新线路，保持旧版数据库结构不变。</div></div>");
            sb.append("<button type=\"submit\">创建线路</button></form></div></div></div></body></html>");

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
            sb.append("<html><head><meta charset=\"utf-8\"><title>管理线路</title>");
            sb.append("<style>html,body{margin:0;padding:0;background:linear-gradient(180deg,#f6f8ff 0%,#eef3fb 100%);font-family:Segoe UI,Microsoft YaHei,Arial,sans-serif;color:#1f2a37;} .app{max-width:900px;margin:30px auto;padding:0 18px 40px;} .panel{background:rgba(255,255,255,.9);border:1px solid rgba(148,163,184,.18);border-radius:18px;padding:18px;box-shadow:0 10px 28px rgba(15,23,42,.06);} input,select{padding:9px 10px;border:1px solid #d9e2f1;border-radius:10px;} input[type=submit],button{border:none;border-radius:10px;padding:10px 14px;background:linear-gradient(135deg,#4f9cff,#3b82f6);color:#fff;font-weight:700;cursor:pointer;} table{width:100%;border-collapse:collapse;} th,td{padding:10px 8px;border-bottom:1px solid #edf2fb;text-align:left;} th{background:#f8faff;} .row{margin-bottom:10px;display:flex;gap:8px;align-items:center;flex-wrap:wrap;} .muted{color:#64748b;font-size:12px;} .danger{background:linear-gradient(135deg,#f87171,#ef4444)!important;}</style></head><body>");
            sb.append("<div class=\"app\"><div class=\"panel\"><h2>管理线路 - "+escapeHtml(lineName==null?String.valueOf(lineId):lineName)+"</h2>");
            sb.append("<div class=\"row\"><form method=\"post\" action=\"/line/save\">线路ID: <input name=\"lineId\" value=\"").append(lineId).append("\" readonly/> 名称: <input name=\"name\" value=\"").append(escapeHtml(lineName==null?"":lineName)).append("\" /> <input type=\"submit\" value=\"保存\"/></form></div>");
            sb.append("<h3>站点列表</h3><table><tr><th style=\"width:90px\">ID</th><th>名称</th><th style=\"width:120px\">操作</th></tr>");
            for (java.util.Map.Entry<Integer,String> e: stations) {
                sb.append("<tr><td>").append(e.getKey()).append("</td><td>").append(escapeHtml(e.getValue())).append("</td><td>");
                sb.append("<form method=\"post\" action=\"/station/delete\" style=\"display:inline\"><input type=\"hidden\" name=\"stationId\" value=\"").append(e.getKey()).append("\"/><button type=\"submit\" class=\"danger\">删除</button></form>");
                sb.append("</td></tr>");
            }
            sb.append("</table>");
            sb.append("<h3 style=\"margin-top:14px\">添加站点</h3><form method=\"post\" action=\"/station/add\"><div class=\"row\">名称: <input name=\"name\" /> <input type=\"hidden\" name=\"lineId\" value=\"").append(lineId).append("\"/> 可选ID: <input name=\"id\" /> <input type=\"submit\" value=\"添加\"/></div></form>");
            sb.append("<p style=\"margin-top:14px\"><a href=\"/\">返回首页</a></p>");
            sb.append("</div></div></body></html>");
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
        try { Integer idOpt = (idS==null||idS.isEmpty())?null:Integer.parseInt(idS); plugin.getDbManager().insertStation(name, Integer.parseInt(lineIdS), idOpt); sendPlain(ex,200,"已添加，<a href=\"/line?lineId="+lineIdS+"\">查看</a>"); } catch (Exception e) { sendPlain(ex,500,"添加失败: "+e.getMessage()); }
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
            sb.append("<style>html,body{margin:0;padding:0;background:linear-gradient(180deg,#f6f8ff 0%,#eef3fb 100%);font-family:Segoe UI,Microsoft YaHei,Arial,sans-serif;color:#1f2a37;} .app{max-width:1120px;margin:28px auto;padding:0 18px 40px;} .panel{background:rgba(255,255,255,.9);border:1px solid rgba(148,163,184,.18);border-radius:18px;padding:18px;box-shadow:0 10px 28px rgba(15,23,42,.06);} table{width:100%;border-collapse:collapse;} th,td{padding:10px 8px;border-bottom:1px solid #edf2fb;text-align:left;} th{background:#f8faff;} input{padding:9px 10px;border:1px solid #d9e2f1;border-radius:10px;} input[type=submit]{border:none;border-radius:10px;padding:10px 14px;background:linear-gradient(135deg,#4f9cff,#3b82f6);color:#fff;font-weight:700;cursor:pointer;} .row{display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-bottom:10px;} .muted{color:#64748b;font-size:12px;} .danger{background:linear-gradient(135deg,#f87171,#ef4444)!important;} </style>");
            sb.append("</head><body><div class=\"app\"><div class=\"panel\"><h2>车票管理</h2><p><a href=\"/\">返回线路管理</a></p>");
            sb.append("<div class=\"row\"><form method=\"post\" action=\"/tickets/action\">玩家名: <input name=\"player\" /> 线路ID: <input name=\"lineId\" /> 起点ID: <input name=\"startId\" /> 终点ID: <input name=\"endId\" /> 价格: <input name=\"price\" /> <input type=\"hidden\" name=\"action\" value=\"issue\" /> <input type=\"submit\" value=\"发放\"/></form></div>");
            sb.append("<h3>已发放车票</h3><table><tr><th>ID</th><th>玩家UUID</th><th>线路</th><th>起点</th><th>终点</th><th>价格</th><th>时间</th><th>状态</th><th>操作</th></tr>");
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
                    sb.append("<form method=\"post\" action=\"/tickets/action\" style=\"display:inline\"><input type=\"hidden\" name=\"action\" value=\"consume\" /><input type=\"hidden\" name=\"ticketId\" value=\"").append(t.id).append("\" /><input type=\"submit\" value=\"消费\" /></form>");
                }
                sb.append("<form method=\"post\" action=\"/tickets/action\" style=\"display:inline;margin-left:6px\"><input type=\"hidden\" name=\"action\" value=\"revoke\" /><input type=\"hidden\" name=\"ticketId\" value=\"").append(t.id).append("\" /><button type=\"submit\" class=\"danger\" style=\"padding:8px 10px\">撤销</button></form>");
                sb.append("</td>");
                sb.append("</tr>");
            }
            sb.append("</table></div></div></body></html>");
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
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            new SyncTask(plugin, plugin.getDbManager()).run();
        });
        sendPlain(ex, 200, "已触发同步任务，稍后生效。<a href=\"/\">返回</a>");
    }

    private void handleAddLine(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendPlain(ex,405,"Method Not Allowed"); return; }
        Map<String, List<String>> p = parseQueryMulti(readRequestBody(ex.getRequestBody()));
        String name = firstValue(p, "name");
        String idS = firstValue(p, "id");
        if (name == null || name.trim().isEmpty()) { sendPlain(ex,400,"缺少名称"); return; }
        try {
            Integer idOpt = (idS==null||idS.trim().isEmpty())?null:Integer.parseInt(idS.trim());
            int nid = plugin.getDbManager().insertLine(name.trim(), idOpt);

            for (String stationName : p.getOrDefault("stationName", Collections.emptyList())) {
                String clean = stationName == null ? "" : stationName.trim();
                if (!clean.isEmpty()) {
                    plugin.getDbManager().insertStation(clean, nid, null);
                }
            }

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

    private Map<String, List<String>> parseQueryMulti(String q) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (q == null || q.isEmpty()) return out;
        for (String part : q.split("&")) {
            if (part.isEmpty()) continue;
            String[] kv = part.split("=", 2);
            if (kv.length != 2) continue;
            String key = urlDecode(kv[0]);
            String value = urlDecode(kv[1]);
            out.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
        }
        return out;
    }

    private String firstValue(Map<String, List<String>> values, String key) {
        List<String> list = values.get(key);
        if (list == null || list.isEmpty()) return null;
        return list.get(0);
    }

    private List<String> buildStationTemplates(List<String> lines) throws SQLException {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String s : lines) {
            String[] parts = s.split(":", 2);
            if (parts.length == 0) continue;
            String id = parts[0].trim();
            if (id.isEmpty()) continue;
            try {
                List<java.util.Map.Entry<Integer, String>> stations = plugin.getDbManager().listStationsForLine(Integer.parseInt(id));
                for (java.util.Map.Entry<Integer, String> entry : stations) {
                    if (entry.getValue() != null && !entry.getValue().trim().isEmpty()) {
                        set.add(entry.getValue().trim());
                    }
                }
            } catch (NumberFormatException ignored) {
                // ignore invalid id on legacy data
            }
        }
        return new ArrayList<>(set);
    }

    private String urlDecode(String s) { try { return URLDecoder.decode(s, "UTF-8"); } catch (Exception e) { return s; } }

    private String escapeHtml(String s) {
        if (s==null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
}
