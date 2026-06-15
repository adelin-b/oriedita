package oriedita.editor.swing.foldedpreview;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.tinylog.Logger;
import oriedita.editor.FrameProvider;
import oriedita.editor.canvas.CreasePattern_Worker;
import oriedita.editor.export.FoldExporter;
import oriedita.editor.save.Save;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Embeds Amanda Ghassaei's <a href="https://origamisimulator.org/">Origami Simulator</a> (a
 * WebGL/three.js folding engine, MIT licensed and bundled under {@code resources/simulator/}) in a
 * dedicated window so the current crease pattern can be previewed as a folded 3D shape.
 * <p>
 * The bridge is one-directional data + control: the crease pattern is exported to the FOLD format
 * (via {@link FoldExporter}) and pushed into the simulator with {@code globals.pattern.setFoldData},
 * while a Swing slider drives {@code globals.creasePercent} (the fold amount). Live edits in the
 * editor are mirrored into the preview with a short debounce.
 * <p>
 * The Chromium runtime is provided by jcefmaven, which downloads a native bundle (~100&nbsp;MB) on
 * first use; initialisation therefore runs off the EDT and the window shows progress until ready.
 */
@ApplicationScoped
public class FoldedPreviewService {

    private static final String SCRIPT_ORIGIN = "oriedita://folded-preview";

    /**
     * Injected into the page once it has loaded. Defines the data/control entry points and signals
     * readiness back to Java (through the message router) as soon as the engine globals exist.
     */
    private static final String BRIDGE_JS = ""
            + "(function(){"
            + "  if (window.__oriedita_bridge__) { return; }"
            + "  window.__oriedita_bridge__ = true;"
            + "  function decode(b){ var s = window.atob(b); try { return decodeURIComponent(escape(s)); } catch(e){ return s; } }"
            + "  window.orieditaSetFold = function(b){"
            + "    try { var fold = JSON.parse(decode(b));"
            + "      if (window.globals && globals.pattern) { globals.pattern.setFoldData(fold, true); } }"
            + "    catch(e){ console.error('[oriedita] setFold failed', e); } };"
            + "  window.orieditaSetPercent = function(p){"
            + "    try { if (window.globals) { globals.creasePercent = p/100;"
            + "        if (window.$) { try { $('#creasePercent>div').slider({value:p}); } catch(e2){}"
            + "          try { $('#creasePercent>input').val(p); } catch(e3){} } } }"
            + "    catch(e){ console.error('[oriedita] setPercent failed', e); } };"
            + "  (function ready(){"
            + "    if (window.globals && globals.pattern && window.cefQuery) {"
            + "      window.cefQuery({ request:'oriedita:ready', persistent:false, onSuccess:function(){}, onFailure:function(){} }); }"
            + "    else { window.setTimeout(ready, 150); } })();"
            + "})();";

    private final FrameProvider frameProvider;
    private final CreasePattern_Worker creasePatternWorker;
    private final FoldExporter foldExporter;

    private JFrame frame;
    private JLabel statusLabel;
    private JSlider foldSlider;

    private CefApp cefApp;
    private CefClient cefClient;
    private CefBrowser browser;

    private volatile boolean initStarted;
    private volatile boolean simReady;

    /** Debounce timer (EDT) coalescing rapid edits into a single push. */
    private Timer pushDebounce;

    @Inject
    public FoldedPreviewService(
            FrameProvider frameProvider,
            @Named("mainCreasePattern_Worker") CreasePattern_Worker creasePatternWorker,
            FoldExporter foldExporter
    ) {
        this.frameProvider = frameProvider;
        this.creasePatternWorker = creasePatternWorker;
        this.foldExporter = foldExporter;
    }

    /** Open (and lazily build/initialise) the folded preview window. Safe to call repeatedly. */
    public void show() {
        SwingUtilities.invokeLater(() -> {
            if (frame == null) {
                buildFrame();
            }
            frame.setVisible(true);
            frame.toFront();
            if (!initStarted) {
                initStarted = true;
                Thread initThread = new Thread(this::initCef, "oriedita-jcef-init");
                initThread.setDaemon(true);
                initThread.start();
            }
        });
    }

    private void buildFrame() {
        frame = new JFrame("Folded Preview — 3D");
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        frame.setSize(900, 700);
        frame.setLocationRelativeTo(frameProvider.get());

        statusLabel = new JLabel("Initialising 3D engine — first run downloads the Chromium runtime (~100 MB)…");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        toolBar.add(new JLabel("Fold: "));
        foldSlider = new JSlider(0, 100, 100);
        foldSlider.setEnabled(false);
        foldSlider.setPreferredSize(new Dimension(220, foldSlider.getPreferredSize().height));
        foldSlider.addChangeListener(e -> pushPercent(foldSlider.getValue()));
        toolBar.add(foldSlider);

        toolBar.add(Box.createHorizontalStrut(12));
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> pushCurrentPattern());
        toolBar.add(refresh);

        toolBar.add(Box.createHorizontalGlue());
        JLabel credit = new JLabel("Origami Simulator by Amanda Ghassaei (MIT)  ");
        toolBar.add(credit);

        JPanel content = new JPanel(new BorderLayout());
        content.add(toolBar, BorderLayout.NORTH);
        content.add(statusLabel, BorderLayout.CENTER);
        frame.setContentPane(content);
    }

    private void initCef() {
        try {
            Path indexHtml = SimulatorAssets.ensureExtracted();

            CefAppBuilder builder = new CefAppBuilder();
            builder.getCefSettings().windowless_rendering_enabled = false;
            builder.setAppHandler(new MavenCefAppHandlerAdapter() {
            });
            // Allow the bundled app (loaded over file://) to read its own sibling resources.
            builder.addJcefArgs("--allow-file-access-from-files");
            builder.setProgressHandler((state, percent) ->
                    Logger.info("JCEF install: {} {}", state, percent));

            cefApp = builder.build();
            cefClient = cefApp.createClient();

            CefMessageRouter messageRouter = CefMessageRouter.create();
            messageRouter.addHandler(new CefMessageRouterHandlerAdapter() {
                @Override
                public boolean onQuery(CefBrowser b, CefFrame f, long queryId, String request,
                                       boolean persistent, CefQueryCallback callback) {
                    if ("oriedita:ready".equals(request)) {
                        callback.success("");
                        SwingUtilities.invokeLater(() -> onSimulatorReady());
                        return true;
                    }
                    return false;
                }
            }, true);
            cefClient.addMessageRouter(messageRouter);

            cefClient.addLoadHandler(new CefLoadHandlerAdapter() {
                @Override
                public void onLoadEnd(CefBrowser b, CefFrame f, int httpStatusCode) {
                    if (f != null && f.getURL() != null && f.getURL().contains("index.html")) {
                        b.executeJavaScript(BRIDGE_JS, SCRIPT_ORIGIN, 0);
                    }
                }
            });

            String url = indexHtml.toUri().toString();
            browser = cefClient.createBrowser(url, false, false);

            SwingUtilities.invokeLater(this::attachBrowser);
        } catch (Throwable t) {
            Logger.error(t, "Failed to initialise embedded folding preview");
            SwingUtilities.invokeLater(() -> statusLabel.setText(
                    "<html>Could not start the 3D engine:<br/>" + escapeHtml(String.valueOf(t.getMessage()))
                            + "<br/><br/>See the log for details.</html>"));
        }
    }

    private void attachBrowser() {
        JPanel content = (JPanel) frame.getContentPane();
        content.remove(statusLabel);
        content.add(browser.getUIComponent(), BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }

    /** Called on the EDT once the page's engine is ready to accept data. */
    private void onSimulatorReady() {
        simReady = true;
        foldSlider.setEnabled(true);
        pushCurrentPattern();
        pushPercent(foldSlider.getValue());
        listenForEdits();
    }

    private void listenForEdits() {
        pushDebounce = new Timer(250, e -> pushCurrentPattern());
        pushDebounce.setRepeats(false);
        creasePatternWorker.addPropertyChangeListener(evt -> {
            if (simReady && frame.isVisible()) {
                pushDebounce.restart();
            }
        });
    }

    /** Export the live crease pattern to FOLD and push it into the simulator. EDT only. */
    private void pushCurrentPattern() {
        if (!simReady || browser == null) {
            return;
        }
        try {
            Save save = creasePatternWorker.getSave_for_export();
            String foldJson = foldExporter.toFoldString(save);
            String b64 = Base64.getEncoder().encodeToString(foldJson.getBytes(StandardCharsets.UTF_8));
            browser.executeJavaScript(
                    "window.orieditaSetFold && window.orieditaSetFold('" + b64 + "');",
                    SCRIPT_ORIGIN, 0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Logger.warn(e, "Could not push crease pattern to folded preview");
        }
    }

    private void pushPercent(int percent) {
        if (!simReady || browser == null) {
            return;
        }
        browser.executeJavaScript(
                "window.orieditaSetPercent && window.orieditaSetPercent(" + percent + ");",
                SCRIPT_ORIGIN, 0);
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
