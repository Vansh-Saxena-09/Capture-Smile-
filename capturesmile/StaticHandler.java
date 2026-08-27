package capturesmile;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serves the static frontend from {@code webroot}. {@code GET /} → index.html; anything else →
 * the matching file under the web root. Rejects path traversal by normalizing and confirming the
 * resolved path stays inside the root.
 */
public final class StaticHandler implements HttpHandler {

    private final Path root;

    public StaticHandler(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                Http.text(ex, 405, "Method Not Allowed");
                return;
            }
            String path = ex.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) path = "/index.html";

            Path target = root.resolve(path.substring(1)).normalize();
            if (!target.startsWith(root) || !Files.exists(target) || Files.isDirectory(target)) {
                Http.text(ex, 404, "Not Found");
                return;
            }
            Http.bytes(ex, 200, contentType(target.getFileName().toString()), Files.readAllBytes(target));
        } catch (Exception e) {
            Http.text(ex, 500, "Server Error");
        }
    }

    private static String contentType(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".html")) return "text/html; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".js") || n.endsWith(".mjs")) return "text/javascript; charset=utf-8";
        if (n.endsWith(".json")) return "application/json; charset=utf-8";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }
}
