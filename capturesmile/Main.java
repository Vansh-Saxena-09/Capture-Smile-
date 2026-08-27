package capturesmile;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

/**
 * Entry point. Binds to 127.0.0.1 (localhost only — no Windows firewall prompt, and getUserMedia
 * treats localhost as a secure context) and uses a virtual-thread-per-request executor so
 * concurrent users aren't serialized.
 *
 * <p>Run:
 * <pre>
 *   javac -encoding UTF-8 -d out capturesmile\*.java
 *   java -Dwebroot="C:\Users\saxen\Capture Smile\web" -cp out capturesmile.Main
 * </pre>
 * Optional: {@code -Dport=8080}.
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        int port = Integer.getInteger("port", 8080);
        Path webroot = Paths.get(System.getProperty("webroot", "web")).toAbsolutePath().normalize();
        ImageAssets.useWebroot(webroot);   // portraits are found on disk by id, not declared in code

        // The TMDB key stays server-side. It lives beside the web root, never inside it, so
        // StaticHandler cannot serve it and no browser bundle can carry it.
        Path secrets = Paths.get(System.getProperty("secrets", webroot.getParent() == null
                ? "secrets" : webroot.getParent().resolve("secrets").toString()));
        Tmdb.useKeyFile(secrets.resolve("tmdb.key"));

        // Character stills come from Wikipedia (TMDB has no character-image endpoint). Resolving them
        // takes several round-trips each, so the roster is warmed in the background rather than on the
        // request path — until a lookup lands, the card shows its CSS plate exactly as before.
        CharacterImages.prewarm(Catalog.all());

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/api/analyze", new AnalyzeHandler());
        server.createContext("/api/showdown", new ShowdownHandler());
        server.createContext("/", new StaticHandler(webroot));   // longest-prefix match: /api/* win
        server.start();

        System.out.println("😀  Capture Smile is live");
        System.out.println("    URL:     http://localhost:" + port);
        System.out.println("    webroot: " + webroot);
        System.out.println("    (Ctrl+C to stop)");
    }
}
