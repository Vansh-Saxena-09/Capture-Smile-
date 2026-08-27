package capturesmile;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

/**
 * Entry point. Uses a virtual-thread-per-request executor so concurrent users aren't serialized.
 *
 * <p>Bind address and port adapt to where this runs:
 * <ul>
 *   <li>Host is {@code 0.0.0.0} whenever {@code PORT} is set (the convention every container
 *       platform — Render, Fly.io, Railway — uses to tell an app what to listen on) or {@code -Dhost}
 *       is given explicitly. Otherwise it's {@code 127.0.0.1}: on a local machine that avoids a
 *       Windows firewall prompt, and {@code getUserMedia} treats localhost as a secure context.</li>
 *   <li>Port is {@code PORT} (platform-injected), else {@code -Dport}, else {@code 8080}.</li>
 * </ul>
 *
 * <p>Local run:
 * <pre>
 *   javac -encoding UTF-8 -d out capturesmile\*.java
 *   java -Dwebroot="C:\Users\saxen\Capture Smile\web" -cp out capturesmile.Main
 * </pre>
 * Container run: {@code PORT} and {@code TMDB_API_KEY} come from the platform; see the
 * {@code Dockerfile} at the repo root.
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        String portEnv = System.getenv("PORT");
        int port = (portEnv != null && !portEnv.isBlank())
                ? Integer.parseInt(portEnv.trim())
                : Integer.getInteger("port", 8080);
        boolean containerHost = (portEnv != null && !portEnv.isBlank()) || System.getProperty("host") != null;
        String host = System.getProperty("host", containerHost ? "0.0.0.0" : "127.0.0.1");
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

        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/api/analyze", new AnalyzeHandler());
        server.createContext("/api/showdown", new ShowdownHandler());
        server.createContext("/", new StaticHandler(webroot));   // longest-prefix match: /api/* win
        server.start();

        System.out.println("😀  Capture Smile is live");
        System.out.println("    listening: " + host + ":" + port);
        System.out.println("    webroot:   " + webroot);
        System.out.println("    tmdb key:  " + (Tmdb.configured() ? "loaded" : "absent (posters/credits will fall back)"));
        System.out.println("    (Ctrl+C to stop)");
    }
}
