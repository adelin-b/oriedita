package oriedita.editor.control;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.jboss.weld.environment.se.events.ContainerInitialized;
import org.tinylog.Logger;
import oriedita.editor.Canvas;
import oriedita.editor.action.ActionService;
import oriedita.editor.action.ActionType;
import oriedita.editor.action.OrieditaAction;
import oriedita.editor.canvas.CreasePattern_Worker;
import oriedita.editor.export.CpExporter;
import oriedita.editor.export.FoldExporter;
import oriedita.editor.export.api.FileExporter;
import oriedita.editor.save.Save;
import oriedita.editor.service.FileSaveService;
import origami.crease_pattern.element.LineColor;
import origami.crease_pattern.element.LineSegment;
import origami.crease_pattern.element.Point;

import jakarta.enterprise.event.Observes;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Opt-in local control server. Lets an external process (CLI / AI agent / MCP wrapper) drive the
 * <em>currently open</em> Oriedita instance over loopback HTTP.
 *
 * <p>Disabled by default. Enable by launching with {@code -Doriedita.control.port=7401} (or env
 * {@code ORIEDITA_CONTROL_PORT}). Binds to 127.0.0.1 only.
 *
 * <p>The action list is generated from the live action registry ({@link ActionService}), so it
 * always matches whatever the running build supports — no hand-maintained tool catalog.
 *
 * <p>Endpoints:
 * <ul>
 *     <li>{@code GET  /health}                  -> "ok"</li>
 *     <li>{@code GET  /actions}                 -> JSON array of every registered action name</li>
 *     <li>{@code POST /action?name=NAME}        -> fire that action on the EDT</li>
 *     <li>{@code POST /open?path=FILE}          -> open FILE (.cp/.fold/...) into the live editor</li>
 *     <li>{@code POST /save?path=FILE}          -> export current crease pattern to FILE (ext picks format)</li>
 *     <li>{@code GET  /cp}                       -> current crease pattern as .fold JSON</li>
 * </ul>
 *
 * <p>Immediate actions (new, save, undo, recolor, flat-foldable check, fold, ...) execute fully.
 * Mouse-mode actions only <em>arm</em> a tool; performing the geometric gesture from outside is a
 * later phase. Use {@code /open} + {@code /save} for whole-pattern IO in the meantime.
 */
@ApplicationScoped
public class ControlServer {
    private static final String PORT_PROPERTY = "oriedita.control.port";
    private static final String PORT_ENV = "ORIEDITA_CONTROL_PORT";

    @Inject
    ActionService actionService;
    @Inject
    @Named("mainCreasePattern_Worker")
    CreasePattern_Worker worker;
    @Inject
    FileSaveService fileSaveService;
    @Inject
    CpExporter cpExporter;
    @Inject
    FoldExporter foldExporter;
    @Inject
    Canvas canvas;

    private HttpServer server;

    public void onStart(@Observes ContainerInitialized event) {
        Integer port = resolvePort();
        if (port == null) {
            return; // opt-in: not enabled
        }
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
            server.createContext("/health", e -> respond(e, 200, "ok"));
            server.createContext("/actions", this::handleActions);
            server.createContext("/action", this::handleAction);
            server.createContext("/open", this::handleOpen);
            server.createContext("/save", this::handleSave);
            server.createContext("/cp", this::handleCp);
            server.createContext("/line", this::handleLine);
            server.createContext("/lines", this::handleLines);
            server.createContext("/circle", this::handleCircle);
            server.setExecutor(null); // default executor, requests are short
            server.start();
            Logger.info("Oriedita control server listening on http://127.0.0.1:{}", port);
        } catch (IOException e) {
            Logger.error(e, "Failed to start Oriedita control server");
        }
    }

    private Integer resolvePort() {
        String raw = System.getProperty(PORT_PROPERTY);
        if (raw == null || raw.isBlank()) {
            raw = System.getenv(PORT_ENV);
        }
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            Logger.warn("Invalid {} value '{}', control server disabled", PORT_PROPERTY, raw);
            return null;
        }
    }

    private void handleActions(HttpExchange e) throws IOException {
        String json = actionService.getAllRegisteredActions().keySet().stream()
                .map(ActionType::action)
                .sorted()
                .map(name -> '"' + name + '"')
                .collect(Collectors.joining(",", "[", "]"));
        respond(e, 200, json);
    }

    private void handleAction(HttpExchange e) throws IOException {
        String name = query(e).get("name");
        if (name == null) {
            respond(e, 400, "missing ?name=");
            return;
        }
        ActionType type = ActionType.fromAction(name);
        OrieditaAction action = type == null ? null : actionService.getAllRegisteredActions().get(type);
        if (action == null) {
            respond(e, 404, "unknown action: " + name);
            return;
        }
        SwingUtilities.invokeLater(() ->
                action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, name)));
        respond(e, 202, "fired " + name);
    }

    private void handleOpen(HttpExchange e) throws IOException {
        String path = query(e).get("path");
        if (path == null) {
            respond(e, 400, "missing ?path=");
            return;
        }
        File file = new File(path);
        Throwable err = onEdt(() -> fileSaveService.openFile(file));
        if (err != null) {
            respond(e, 500, "open failed: " + err.getMessage());
        } else {
            respond(e, 200, "opened " + file.getAbsolutePath());
        }
    }

    private void handleSave(HttpExchange e) throws IOException {
        String path = query(e).get("path");
        if (path == null) {
            respond(e, 400, "missing ?path=");
            return;
        }
        File file = new File(path);
        Throwable err = exportTo(file);
        if (err != null) {
            respond(e, 500, "save failed: " + err.getMessage());
        } else {
            respond(e, 200, "saved " + file.getAbsolutePath());
        }
    }

    private void handleCp(HttpExchange e) throws IOException {
        try {
            File tmp = Files.createTempFile("oriedita-cp", ".fold").toFile();
            tmp.deleteOnExit();
            Throwable err = exportToWith(foldExporter, tmp);
            if (err != null) {
                respond(e, 500, "export failed: " + err.getMessage());
                return;
            }
            respond(e, 200, Files.readString(tmp.toPath(), StandardCharsets.UTF_8));
        } catch (IOException ioe) {
            respond(e, 500, "export failed: " + ioe.getMessage());
        }
    }

    private void handleLine(HttpExchange e) throws IOException {
        Map<String, String> q = query(e);
        try {
            double x1 = Double.parseDouble(q.get("x1"));
            double y1 = Double.parseDouble(q.get("y1"));
            double x2 = Double.parseDouble(q.get("x2"));
            double y2 = Double.parseDouble(q.get("y2"));
            LineColor color = parseColor(q.get("color"));
            Throwable err = onEdt(() -> {
                worker.addLineSegment(new LineSegment(new Point(x1, y1), new Point(x2, y2), color));
                refresh();
            });
            respond(e, err == null ? 200 : 500, err == null ? "line added" : "failed: " + err.getMessage());
        } catch (NullPointerException | NumberFormatException ex) {
            respond(e, 400, "need numeric ?x1=&y1=&x2=&y2= (optional &color=)");
        }
    }

    /**
     * Batch line add. Request body: one segment per line, {@code x1,y1,x2,y2[,color]}.
     * Lets a caller draw a whole figure in a single round trip.
     */
    private void handleLines(HttpExchange e) throws IOException {
        String body = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        int[] added = {0};
        Throwable err = onEdt(() -> {
            for (String raw : body.split("\\R")) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] f = line.split(",");
                if (f.length < 4) {
                    continue;
                }
                LineColor color = f.length >= 5 ? parseColor(f[4].trim()) : LineColor.RED_1;
                worker.addLineSegment(new LineSegment(
                        new Point(Double.parseDouble(f[0].trim()), Double.parseDouble(f[1].trim())),
                        new Point(Double.parseDouble(f[2].trim()), Double.parseDouble(f[3].trim())),
                        color));
                added[0]++;
            }
            refresh();
        });
        respond(e, err == null ? 200 : 500, err == null ? ("added " + added[0] + " lines") : "failed: " + err.getMessage());
    }

    private void handleCircle(HttpExchange e) throws IOException {
        Map<String, String> q = query(e);
        try {
            double x = Double.parseDouble(q.get("x"));
            double y = Double.parseDouble(q.get("y"));
            double r = Double.parseDouble(q.get("r"));
            LineColor color = parseColor(q.get("color"));
            Throwable err = onEdt(() -> {
                worker.addCircle(x, y, r, color);
                refresh();
            });
            respond(e, err == null ? 200 : 500, err == null ? "circle added" : "failed: " + err.getMessage());
        } catch (NullPointerException | NumberFormatException ex) {
            respond(e, 400, "need numeric ?x=&y=&r= (optional &color=)");
        }
    }

    private LineColor parseColor(String name) {
        if (name == null || name.isBlank()) {
            return LineColor.RED_1; // mountain (red) by default
        }
        switch (name.trim().toLowerCase()) {
            case "mountain":
            case "red":
                return LineColor.RED_1;
            case "valley":
            case "blue":
                return LineColor.BLUE_2;
            case "edge":
            case "border":
            case "black":
                return LineColor.BLACK_0;
            case "aux":
            case "cyan":
                return LineColor.CYAN_3;
            default:
                try {
                    return LineColor.valueOf(name.trim().toUpperCase());
                } catch (IllegalArgumentException ex) {
                    return LineColor.RED_1;
                }
        }
    }

    /** Must be called on the EDT: commit to undo history and repaint the canvas. */
    private void refresh() {
        worker.record();
        canvas.getCanvasImpl().repaint();
    }

    private Throwable exportTo(File file) {
        FileExporter exporter = file.getName().toLowerCase().endsWith(".cp") ? cpExporter : foldExporter;
        return exportToWith(exporter, file);
    }

    private Throwable exportToWith(FileExporter exporter, File file) {
        return onEdt(() -> {
            Save save = worker.getSave_for_export();
            exporter.doExport(save, file);
        });
    }

    /** Run a throwing action on the EDT and return any error (null on success). */
    private Throwable onEdt(EdtTask task) {
        AtomicReference<Throwable> error = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    task.run();
                } catch (Throwable t) {
                    error.set(t);
                }
            });
        } catch (Exception t) {
            error.set(t);
        }
        return error.get();
    }

    private static Map<String, String> query(HttpExchange e) {
        Map<String, String> out = new HashMap<>();
        String raw = e.getRequestURI().getRawQuery();
        if (raw == null) {
            return out;
        }
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            if (i > 0) {
                out.put(decode(pair.substring(0, i)), decode(pair.substring(i + 1)));
            }
        }
        return out;
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange e, int code, String body) throws IOException {
        byte[] bytes = (body + "\n").getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        e.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = e.getResponseBody()) {
            os.write(bytes);
        }
    }

    @FunctionalInterface
    private interface EdtTask {
        void run() throws Exception;
    }
}
