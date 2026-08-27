package capturesmile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server-side TMDB client: image-url construction plus the two lookups we need (a film's
 * {@code poster_path} and its cast credits).
 *
 * <p><b>The api key never leaves this process.</b> It is read from {@code secrets/tmdb.key} (or the
 * {@code TMDB_API_KEY} environment variable) and is only ever interpolated into an outbound request
 * url. It is never serialized into {@link JsonView}'s output, never written under the web root that
 * {@link StaticHandler} serves, and never logged — {@link #log} deliberately takes an endpoint
 * <em>label</em> rather than the full url, because the url contains the credential.
 *
 * <p><b>Why lookups are a fallback, not the primary source.</b> {@link Catalog} already carries a
 * curated {@code posterPath} for nearly every film, and every performer has a locally hosted
 * portrait. Hitting the network only for what is genuinely missing keeps {@code /api/analyze} fast
 * and keeps the app working offline — the property that makes "the character visual is never broken"
 * true. Results are cached (including misses), and a short circuit breaker stops retrying a network
 * that is clearly unavailable, so a blocked or rate-limited TMDB costs one slow request, not every
 * request.
 *
 * <p><b>Reading responses.</b> The app still has no general JSON parser. {@link #stringField} and
 * {@link #arrayObjects} are narrow scanners for the handful of known keys we read from one trusted
 * first-party API — enough to pull {@code poster_path}, {@code character}, {@code name} and
 * {@code profile_path}, and nothing more.
 */
public final class Tmdb {

    /** Public image CDN. Serving an image by path needs no credential; only the lookup does. */
    public static final String IMG_BASE = "https://image.tmdb.org/t/p/";
    public static final String POSTER_SIZE = "w500";
    public static final String PROFILE_SIZE = "w500";

    private static final String API_BASE = "https://api.themoviedb.org/3";

    /** One cast credit: the performer behind a named character. */
    public record Credit(String actorName, String profilePath) {}

    private Tmdb() {}

    // ---- image urls ----------------------------------------------------------------------

    /**
     * Builds a full CDN url from a TMDB relative path such as {@code "/abc123.jpg"}.
     * Returns null for a null/blank path so callers can fall straight through to their fallback.
     */
    public static String imageUrl(String path, String size) {
        if (path == null || path.isBlank()) return null;
        String s = (size == null || size.isBlank()) ? POSTER_SIZE : size;
        String p = path.startsWith("/") ? path : "/" + path;
        return IMG_BASE + s + p;
    }

    /** {@link #imageUrl(String, String)} at the default poster size. */
    public static String imageUrl(String path) {
        return imageUrl(path, POSTER_SIZE);
    }

    // ---- credential ---------------------------------------------------------------------

    private static volatile Path keyFile;
    private static volatile String cachedKey;
    private static volatile boolean keyChecked;

    /** Told once by {@link Main} where the key lives. Outside the web root, by design. */
    static void useKeyFile(Path p) {
        keyFile = (p == null) ? null : p.toAbsolutePath().normalize();
        cachedKey = null;
        keyChecked = false;
    }

    /** The api key, or null when none is configured. Never logged, never serialized. */
    private static String apiKey() {
        if (keyChecked) return cachedKey;
        synchronized (Tmdb.class) {
            if (keyChecked) return cachedKey;
            String k = System.getenv("TMDB_API_KEY");
            if (k == null || k.isBlank()) {
                Path f = keyFile;
                if (f != null) {
                    try {
                        if (Files.isRegularFile(f)) k = Files.readString(f, StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        log("key file unreadable", e.getClass().getSimpleName());
                    }
                }
            }
            cachedKey = (k == null || k.isBlank()) ? null : k.trim();
            keyChecked = true;
            log("credential", cachedKey == null ? "absent" : "loaded");
            return cachedKey;
        }
    }

    /** True when a lookup could in principle be attempted. */
    public static boolean configured() {
        return apiKey() != null;
    }

    // ---- transport ----------------------------------------------------------------------

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // Circuit breaker: an unreachable TMDB must not tax every subsequent request.
    private static final int TRIP_AFTER = 3;
    private static final long COOLDOWN_MS = 5 * 60 * 1000L;
    private static final AtomicInteger failures = new AtomicInteger();
    private static volatile long openUntil = 0L;

    private static boolean breakerOpen() {
        return System.currentTimeMillis() < openUntil;
    }

    private static void recordFailure() {
        if (failures.incrementAndGet() >= TRIP_AFTER) {
            openUntil = System.currentTimeMillis() + COOLDOWN_MS;
            failures.set(0);
            log("breaker", "open for " + (COOLDOWN_MS / 1000) + "s — TMDB unreachable");
        }
    }

    /**
     * GETs a TMDB endpoint and returns the raw body. {@code label} is what gets logged; the url is
     * not, because it carries the api key.
     */
    private static Optional<String> get(String pathAndQuery, String label) {
        String key = apiKey();
        if (key == null || breakerOpen()) return Optional.empty();
        try {
            String sep = pathAndQuery.contains("?") ? "&" : "?";
            HttpRequest req = HttpRequest.newBuilder(URI.create(API_BASE + pathAndQuery + sep + "api_key=" + key))
                    .timeout(Duration.ofSeconds(3))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() / 100 != 2) {
                log(label, "HTTP " + res.statusCode());
                return Optional.empty();
            }
            failures.set(0);
            return Optional.of(res.body());
        } catch (Exception e) {
            log(label, "unreachable (" + e.getClass().getSimpleName() + ")");
            recordFailure();
            return Optional.empty();
        }
    }

    // ---- lookups ------------------------------------------------------------------------

    private static final ConcurrentHashMap<Integer, Optional<String>> POSTERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Optional<Credit>> CREDITS = new ConcurrentHashMap<>();

    /** A film's {@code poster_path}, looked up once per id and cached (misses included). */
    public static Optional<String> posterPath(int tmdbId) {
        if (tmdbId <= 0) return Optional.empty();
        Optional<String> hit = POSTERS.get(tmdbId);
        if (hit != null) return hit;
        Optional<String> found = get("/movie/" + tmdbId, "movie " + tmdbId)
                .map(body -> stringField(body, "poster_path"))
                .filter(p -> p != null && !p.isBlank());
        log("movie " + tmdbId + " poster_path", found.orElse("null"));
        POSTERS.put(tmdbId, found);
        return found;
    }

    /**
     * The cast member playing {@code characterName} in a film. Exact (case-insensitive) match first,
     * then a deliberately conservative partial match — TMDB writes animated roles as
     * {@code "Merida (voice)"}, so a bare equality test would miss most of the animated roster.
     */
    public static Optional<Credit> credit(int tmdbId, String characterName) {
        if (tmdbId <= 0 || characterName == null || characterName.isBlank()) return Optional.empty();
        String cacheKey = tmdbId + "|" + characterName.toLowerCase(Locale.ROOT);
        Optional<Credit> hit = CREDITS.get(cacheKey);
        if (hit != null) return hit;
        Optional<Credit> found = get("/movie/" + tmdbId + "/credits", "credits " + tmdbId)
                .flatMap(body -> matchCast(arrayObjects(body, "cast"), characterName));
        log("credits " + tmdbId + " → " + characterName,
                found.map(c -> c.actorName() + " / " + c.profilePath()).orElse("no match"));
        CREDITS.put(cacheKey, found);
        return found;
    }

    /** Exact match, then whole-name containment, then a shared significant token. */
    static Optional<Credit> matchCast(List<String> cast, String characterName) {
        String want = norm(characterName);
        if (want.isEmpty() || cast.isEmpty()) return Optional.empty();

        List<String> normed = new ArrayList<>(cast.size());
        for (String entry : cast) normed.add(norm(stringField(entry, "character")));

        for (int i = 0; i < cast.size(); i++) {
            if (want.equals(normed.get(i))) return Optional.of(credit(cast.get(i)));
        }
        for (int i = 0; i < cast.size(); i++) {
            String have = normed.get(i);
            if (have.isEmpty()) continue;
            if (containsWord(have, want) || containsWord(want, have)) return Optional.of(credit(cast.get(i)));
        }
        for (int i = 0; i < cast.size(); i++) {
            String have = normed.get(i);
            if (have.isEmpty()) continue;
            for (String token : want.split(" ")) {
                if (token.length() < 3) continue;
                if (containsWord(have, token)) return Optional.of(credit(cast.get(i)));
            }
        }
        return Optional.empty();
    }

    private static Credit credit(String castEntry) {
        return new Credit(stringField(castEntry, "name"), stringField(castEntry, "profile_path"));
    }

    /** Lowercase, drop parentheticals like "(voice)", strip punctuation, collapse whitespace. */
    private static String norm(String s) {
        if (s == null) return "";
        String out = s.toLowerCase(Locale.ROOT).replaceAll("\\([^)]*\\)", " ");
        StringBuilder b = new StringBuilder(out.length());
        for (int i = 0; i < out.length(); i++) {
            char c = out.charAt(i);
            // java.lang.Character is qualified throughout: this package declares its own Character.
            b.append((java.lang.Character.isLetterOrDigit(c) || c == ' ') ? c : ' ');
        }
        return b.toString().replaceAll("\\s+", " ").trim();
    }

    /** Whole-word containment, so "moana" does not match "moanalike". */
    private static boolean containsWord(String haystack, String needle) {
        if (needle.isEmpty()) return false;
        int from = 0;
        while (true) {
            int i = haystack.indexOf(needle, from);
            if (i < 0) return false;
            boolean leftOk = (i == 0) || haystack.charAt(i - 1) == ' ';
            int end = i + needle.length();
            boolean rightOk = (end == haystack.length()) || haystack.charAt(end) == ' ';
            if (leftOk && rightOk) return true;
            from = i + 1;
        }
    }

    // ---- narrow response readers --------------------------------------------------------

    /**
     * The string value of {@code "key"}, or null when absent, JSON {@code null}, or not a string.
     * Not a general parser — it reads known scalar keys from one trusted API.
     */
    static String stringField(String json, String key) {
        if (json == null || key == null) return null;
        String needle = "\"" + key + "\"";
        for (int i = json.indexOf(needle); i >= 0; i = json.indexOf(needle, i + 1)) {
            int j = i + needle.length();
            while (j < json.length() && java.lang.Character.isWhitespace(json.charAt(j))) j++;
            if (j >= json.length() || json.charAt(j) != ':') continue;
            j++;
            while (j < json.length() && java.lang.Character.isWhitespace(json.charAt(j))) j++;
            if (j >= json.length() || json.charAt(j) != '"') return null; // null / number / object
            StringBuilder b = new StringBuilder();
            for (int k = j + 1; k < json.length(); k++) {
                char c = json.charAt(k);
                if (c == '\\' && k + 1 < json.length()) {
                    char n = json.charAt(++k);
                    switch (n) {
                        case 'n' -> b.append('\n');
                        case 't' -> b.append('\t');
                        case 'r' -> b.append('\r');
                        case 'b' -> b.append('\b');
                        case 'f' -> b.append('\f');
                        case 'u' -> {
                            if (k + 4 < json.length()) {
                                try {
                                    b.append((char) Integer.parseInt(json.substring(k + 1, k + 5), 16));
                                } catch (NumberFormatException ignored) { /* leave it out */ }
                                k += 4;
                            }
                        }
                        default -> b.append(n); // covers \" and \\
                    }
                } else if (c == '"') {
                    return b.toString();
                } else {
                    b.append(c);
                }
            }
            return null;
        }
        return null;
    }

    /** The top-level object chunks of the array at {@code "arrayKey"}, string-aware and brace-balanced. */
    static List<String> arrayObjects(String json, String arrayKey) {
        List<String> out = new ArrayList<>();
        if (json == null) return out;
        int at = json.indexOf("\"" + arrayKey + "\"");
        if (at < 0) return out;
        int start = json.indexOf('[', at);
        if (start < 0) return out;

        int depth = 0, objStart = -1;
        boolean inStr = false, esc = false;
        for (int k = start; k < json.length(); k++) {
            char c = json.charAt(k);
            if (inStr) {
                if (esc) esc = false;
                else if (c == '\\') esc = true;
                else if (c == '"') inStr = false;
                continue;
            }
            if (c == '"') { inStr = true; continue; }
            if (c == '{') { if (depth == 0) objStart = k; depth++; }
            else if (c == '}') {
                if (--depth == 0 && objStart >= 0) { out.add(json.substring(objStart, k + 1)); objStart = -1; }
            } else if (c == ']' && depth == 0) {
                break; // end of this array — don't wander into "crew"
            }
        }
        return out;
    }

    // ---- diagnostics --------------------------------------------------------------------

    /** {@code -Dtmdb.debug=true} traces the pipeline. Takes a label, never a url (urls carry the key). */
    private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("tmdb.debug", "false"));

    private static void log(String label, String value) {
        if (DEBUG) System.out.println("[tmdb] " + label + ": " + value);
    }

    private static void log(String label) {
        log(label, "");
    }
}
