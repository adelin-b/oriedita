package oriedita.editor.swing.foldedpreview;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Extracts the bundled Origami Simulator web app (classpath resource {@code simulator/}) to a
 * temporary directory so that an embedded browser can load it through a {@code file://} URL.
 * <p>
 * The browser needs the assets as real files (relative {@code <script>}/{@code <link>} references
 * and {@code XHR} loads do not work from inside a jar), so on first use the whole {@code simulator/}
 * tree is copied out, transparently handling both the packaged (jar) and the exploded
 * ({@code target/classes}) layout.
 */
final class SimulatorAssets {

    /** Classpath root of the bundled simulator. */
    private static final String ROOT = "simulator";

    private static Path extractedDir;

    private SimulatorAssets() {
    }

    /**
     * Extract the simulator (once) and return the {@code index.html} entry point.
     *
     * @return path to the extracted {@code index.html}
     * @throws IOException if the bundled simulator is missing or cannot be copied
     */
    static synchronized Path ensureExtracted() throws IOException {
        if (extractedDir != null && Files.exists(extractedDir.resolve("index.html"))) {
            return extractedDir.resolve("index.html");
        }

        URL entry = SimulatorAssets.class.getClassLoader().getResource(ROOT + "/index.html");
        if (entry == null) {
            throw new FileNotFoundException("Bundled simulator not found on classpath: " + ROOT + "/index.html");
        }

        // Reuse one stable temp dir, re-extracted each run, instead of leaking a fresh
        // multi-MB temp directory per launch (deleteOnExit cannot remove a non-empty dir).
        Path target = Paths.get(System.getProperty("java.io.tmpdir"), "oriedita-folded-preview");
        Files.createDirectories(target);

        if ("jar".equals(entry.getProtocol())) {
            copyFromJar(entry, target);
        } else {
            copyFromDirectory(entry, target);
        }

        extractedDir = target;
        return target.resolve("index.html");
    }

    private static void copyFromJar(URL entry, Path target) throws IOException {
        JarURLConnection connection = (JarURLConnection) entry.openConnection();
        String prefix = ROOT + "/";
        try (JarFile jar = connection.getJarFile()) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry jarEntry = entries.nextElement();
                String name = jarEntry.getName();
                if (!name.startsWith(prefix) || name.equals(prefix)) {
                    continue;
                }
                Path dest = target.resolve(name.substring(prefix.length()));
                if (jarEntry.isDirectory()) {
                    Files.createDirectories(dest);
                    continue;
                }
                if (dest.getParent() != null) {
                    Files.createDirectories(dest.getParent());
                }
                try (InputStream in = jar.getInputStream(jarEntry)) {
                    Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void copyFromDirectory(URL entry, Path target) throws IOException {
        Path source;
        try {
            // entry points at simulator/index.html; its parent is the simulator root.
            source = Paths.get(entry.toURI()).getParent();
        } catch (URISyntaxException e) {
            throw new IOException("Cannot resolve simulator resource directory", e);
        }
        if (source == null) {
            throw new IOException("Cannot resolve simulator resource directory");
        }
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                Path dest = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    if (dest.getParent() != null) {
                        Files.createDirectories(dest.getParent());
                    }
                    Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
