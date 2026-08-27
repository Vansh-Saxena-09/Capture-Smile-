package capturesmile;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Finds an image <em>of a fictional character</em> — not of the performer — from Wikipedia.
 *
 * <p><b>Why not TMDB.</b> TMDB has no character-image endpoint at all. Its {@code profile_path} is a
 * photograph of the <em>actor</em>, so using it here would put Joe Keery's face under the label
 * "Steve Harrington". Character articles on Wikipedia, by contrast, lead with an in-costume still of
 * the role, which is the image a casting card actually wants.
 *
 * <p><b>Why Wikipedia rather than a general image search.</b> No credential exists to leak: the
 * MediaWiki action API is public and anonymous, so there is nothing to keep out of the browser
 * bundle. It is also the one provider whose result we can attribute — arbitrary web-search thumbnails
 * are unattributable, frequently hotlink-blocked, and just as often the wrong person.
 *
 * <p><b>The lookup, in four hops.</b> Character name and film title are always used <em>together</em>
 * ({@code "Steve Harrington" "Stranger Things 4" fictional character}); the name alone is ambiguous
 * and, worse, tends to surface the actor.
 * <ol>
 *   <li>{@code list=search} for the character, scoring candidate titles (§{@link #scoreTitle}).</li>
 *   <li>{@code prop=wikitext&section=0} → the infobox's {@code image =} field.</li>
 *   <li>{@code prop=imageinfo} → a real thumbnail url at {@link #WIDTH}px.</li>
 *   <li>A HEAD request confirming 2xx <em>and</em> an {@code image/*} content type.</li>
 * </ol>
 * Deliberately not used: {@code prop=pageimages}. It omits non-free files, which is precisely the
 * category almost every character still falls into — it returns nothing for both Steve Harrington
 * and Merida while their articles plainly carry an image.
 *
 * <p><b>Nothing is hardcoded.</b> Every query is derived from {@link Catalog} data, so a character
 * added tomorrow resolves with no code change, and the same path serves 16 characters or 16,000.
 *
 * <p><b>Why resolution happens at startup, not per request.</b> Four round-trips per character, times
 * the six characters on a result page, would add seconds to {@code /api/analyze}. Instead
 * {@link #prewarm} resolves the whole roster in the background at boot and requests read the cache
 * only — a miss returns null and the CSS plate stands, exactly as before. Misses are cached too, so
 * a character with no usable image costs one lookup for the life of the process.
 *
 * <p><b>Licensing note.</b> These stills are non-free studio material that Wikipedia hosts under fair
 * use ({@code /wikipedia/en/}, not {@code /commons/}). We hotlink rather than re-host: no copy is
 * made, and the image stays served from the project that documented its own fair-use rationale.
 */
public final class CharacterImages {

    /** Thumbnail width requested from MediaWiki — matches the plate's rendered size. */
    private static final int WIDTH = 500;

    private static final String API = "https://en.wikipedia.org/w/api.php";

    /** MediaWiki asks that clients identify themselves; an anonymous default risks a block. */
    private static final String UA = "CaptureSmile/1.0 (character-image lookup; educational project)";

    /** Raster only. Infoboxes also carry logos and flags as .svg, which are not the character. */
    private static final String[] OK_EXT = {".jpg", ".jpeg", ".png", ".webp", ".gif"};

    private CharacterImages() {}

    // ---- transport ----------------------------------------------------------------------

    /**
     * HTTP/2 is pinned deliberately, not incidentally. MediaWiki throttles HTTP/1.1 hard — measured
     * here, eight sequential HTTP/2 requests all returned 200 while the same eight over HTTP/1.1
     * started returning 429 after the second. Since HTTP/2 multiplexes over one connection, the whole
     * roster resolves without tripping the limit.
     */
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // Same breaker shape as Tmdb: an unreachable Wikipedia must not tax every later lookup.
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
            log("breaker", "open for " + (COOLDOWN_MS / 1000) + "s — Wikipedia unreachable");
        }
    }

    /**
     * Set when a lookup failed for a transport reason (rate limit, timeout, 5xx) rather than because
     * the character genuinely has no image. The difference decides whether the result may be cached:
     * caching a 429 would strand that character on the plate for the life of the process.
     */
    private static final ThreadLocal<Boolean> transportFailed = ThreadLocal.withInitial(() -> false);

    /**
     * A courtesy gap between calls to a free, anonymous API — not a throttle workaround (see
     * {@link #HTTP}: the 429s were an HTTP/1.1 artefact). Spacing is global rather than per-thread
     * because any limit applies to the client as a whole, not to the caller.
     */
    private static final long MIN_INTERVAL_MS = 250;
    private static final Object PACE_LOCK = new Object();
    private static long lastCallAt = 0L;

    private static void pace() throws InterruptedException {
        synchronized (PACE_LOCK) {
            long wait = (lastCallAt + MIN_INTERVAL_MS) - System.currentTimeMillis();
            if (wait > 0) Thread.sleep(wait);
            lastCallAt = System.currentTimeMillis();
        }
    }

    private static Optional<String> get(String query, String label) {
        if (breakerOpen()) {
            transportFailed.set(true);
            return Optional.empty();
        }
        try {
            pace();
            HttpRequest req = HttpRequest.newBuilder(URI.create(API + "?" + query))
                    .timeout(Duration.ofSeconds(6))
                    .header("Accept", "application/json")
                    .header("User-Agent", UA)
                    .GET()
                    .build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = res.statusCode();
            if (status / 100 != 2) {
                // 429/5xx say "ask again later", not "no such image" — don't let them poison the cache.
                boolean retryable = status == 429 || status / 100 == 5;
                log(label, "HTTP " + status + (retryable ? " (retryable)" : ""));
                if (retryable) {
                    transportFailed.set(true);
                    backOff(res.headers().firstValue("retry-after").orElse(null));
                }
                return Optional.empty();
            }
            failures.set(0);
            return Optional.of(res.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log(label, "unreachable (" + e.getClass().getSimpleName() + ")");
            transportFailed.set(true);
            recordFailure();
            return Optional.empty();
        }
    }

    /** Honours {@code Retry-After} when the server sends one, else pauses for a default beat. */
    private static void backOff(String retryAfter) {
        long ms = 5000;
        if (retryAfter != null) {
            try {
                ms = Math.min(60_000, Math.max(1000, Long.parseLong(retryAfter.trim()) * 1000));
            } catch (NumberFormatException ignored) { /* a date form — the default is fine */ }
        }
        log("backoff", ms + "ms");
        synchronized (PACE_LOCK) {
            lastCallAt = System.currentTimeMillis() + ms;   // next pace() waits this out
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    // ---- cache --------------------------------------------------------------------------

    /** Keyed on name+title, because the pairing is what identifies the role. Misses cached too. */
    private static final ConcurrentHashMap<String, Optional<String>> CACHE = new ConcurrentHashMap<>();

    private static String cacheKey(String characterName, String movieTitle) {
        return (characterName + "|" + movieTitle).toLowerCase(Locale.ROOT);
    }

    /**
     * The cached image url for a role, or empty when it has not been resolved yet or has no usable
     * image. Never performs i/o, so it is safe on the request path.
     */
    public static Optional<String> cached(String characterName, String movieTitle) {
        Optional<String> hit = CACHE.get(cacheKey(characterName, movieTitle));
        return hit == null ? Optional.empty() : hit;
    }

    /**
     * Resolves the character image, blocking on network i/o. Returns empty when no image can be
     * confirmed — callers fall through to the CSS plate and must never substitute the actor's photo.
     */
    public static Optional<String> resolve(String characterName, String movieTitle, String actorName) {
        if (characterName == null || characterName.isBlank()) return Optional.empty();
        String key = cacheKey(characterName, movieTitle);
        Optional<String> hit = CACHE.get(key);
        if (hit != null) return hit;

        log("lookup", "character=\"" + characterName + "\" movie=\"" + movieTitle + "\"");
        transportFailed.set(false);
        Optional<String> found;
        try {
            found = lookUp(characterName, movieTitle, actorName);
        } finally {
            // Only a completed lookup is cacheable. A rate limit or timeout means "unknown", and
            // caching that would strand this character on the plate until the process restarts.
            if (transportFailed.get()) {
                log("resolved", "\"" + characterName + "\" → deferred (transport failure, not cached)");
            }
        }
        if (transportFailed.get()) {
            transportFailed.remove();
            return Optional.empty();
        }
        transportFailed.remove();
        log("resolved", "\"" + characterName + "\" → " + found.orElse("null (CSS plate stands)"));
        CACHE.put(key, found);
        return found;
    }

    private static Optional<String> lookUp(String characterName, String movieTitle, String actorName) {
        Optional<String> title = findArticle(characterName, movieTitle, actorName);
        if (title.isEmpty()) return Optional.empty();
        Optional<String> file = infoboxImage(title.get());
        if (file.isEmpty()) return Optional.empty();
        if (looksLikeActorPhoto(file.get(), characterName, actorName)) return Optional.empty();
        return fileUrl(file.get()).filter(CharacterImages::isLoadableImage);
    }

    /**
     * Rejects a file that is a portrait of the performer rather than of the role.
     *
     * <p>Necessary because the right article can still carry the wrong kind of image: the Loki article
     * illustrates its infobox with {@code Tom_Hiddleston_by_Gage_Skidmore.jpg}, a red-carpet photo of
     * the actor. Picking the article correctly is not enough — this is the last gate before a real
     * person's face would appear captioned as a fictional character.
     *
     * <p>The test is deliberately narrow: the filename must name the actor <em>and not</em> the
     * character. Harry Potter's still is {@code Daniel_Radcliffe_as_Harry_Potter.jpg}, which names both
     * and is exactly the in-costume image we want, so a blanket "contains the actor's name" rule would
     * throw away good images.
     */
    private static boolean looksLikeActorPhoto(String fileName, String characterName, String actorName) {
        String f = norm(fileName.replace('_', ' '));
        String actor = norm(actorName);
        if (f.isEmpty() || actor.isEmpty()) return false;
        if (!containsWords(f, actor)) return false;
        if (containsWords(f, norm(characterName))) return false;  // "<actor> as <character>" is fine
        log("reject", fileName + " — names the performer, not the role");
        return true;
    }

    // ---- 1. find the character's article ------------------------------------------------

    /**
     * Searches for the character <em>in the context of their film</em> and returns the best-scoring
     * article title. The film title is part of the query because "Merida" alone is a Mexican city and
     * "Steve Harrington" alone competes with coverage of the actor who plays him.
     */
    private static Optional<String> findArticle(String characterName, String movieTitle, String actorName) {
        String q = "\"" + characterName + "\" " + (movieTitle == null ? "" : movieTitle) + " fictional character";
        log("search", q);
        Optional<String> body = get("action=query&format=json&list=search&srlimit=6&srsearch=" + enc(q), "search");
        if (body.isEmpty()) return Optional.empty();

        String bestTitle = null;
        int bestScore = 0;
        for (String entry : Tmdb.arrayObjects(body.get(), "search")) {
            String t = Tmdb.stringField(entry, "title");
            int score = scoreTitle(t, characterName, movieTitle, actorName);
            if (score > bestScore) {
                bestScore = score;
                bestTitle = t;
            }
        }
        log("search best", bestTitle == null ? "no acceptable candidate" : bestTitle + " (score " + bestScore + ")");
        return Optional.ofNullable(bestTitle);
    }

    /**
     * Scores a candidate article. Zero means "reject", which is the important half of this method:
     * the actor's own biography is a plausible search hit and would put a real person's photograph on
     * a fictional character's card, so it is excluded explicitly rather than merely out-ranked.
     */
    private static int scoreTitle(String title, String characterName, String movieTitle, String actorName) {
        if (title == null || title.isBlank()) return 0;
        String t = norm(title);
        String want = norm(characterName);
        if (t.isEmpty() || want.isEmpty()) return 0;

        // Never the performer's article, however well it ranks.
        if (!t.isEmpty() && t.equals(norm(actorName))) return 0;
        // Wikipedia namespaces: "List of ...", "Category:...". Not a character article.
        if (t.startsWith("list of ") || title.contains(":") && !title.contains("(")) return 0;
        // The title must actually name the character.
        if (!containsWords(t, want)) return 0;

        int score = 1;
        if (t.equals(want)) score += 4;                       // "Steve Harrington"
        String universe = norm(movieTitle);
        if (!universe.isEmpty()) {
            // "Merida (Brave)" — the parenthetical ties the character to the right franchise.
            for (String token : universe.split(" ")) {
                if (token.length() >= 4 && containsWords(t, token)) { score += 3; break; }
            }
        }
        return score;
    }

    // ---- 2. the infobox image field -----------------------------------------------------

    /**
     * Pulls {@code image = Foo.jpg} out of the article's lead-section wikitext.
     *
     * <p>Reading wikitext rather than rendered HTML is deliberate: the rendered lead returns nothing
     * for Merida and returns maintenance-banner icons ({@code Ambox_important.svg}) for Eleven,
     * whereas the infobox field is unambiguous on both.
     */
    private static Optional<String> infoboxImage(String articleTitle) {
        Optional<String> body = get("action=parse&format=json&prop=wikitext&section=0&redirects=1&page="
                + enc(articleTitle), "wikitext " + articleTitle);
        if (body.isEmpty()) return Optional.empty();
        String wikitext = Tmdb.stringField(body.get(), "*");
        if (wikitext == null) return Optional.empty();

        for (String line : wikitext.split("\\r?\\n")) {
            String v = imageField(line);
            if (v != null) {
                log("infobox image", v);
                return Optional.of(v);
            }
        }
        log("infobox image", "none in lead section");
        return Optional.empty();
    }

    /** Returns the file name if this wikitext line is an infobox image assignment, else null. */
    private static String imageField(String line) {
        String s = line.trim();
        if (s.startsWith("|")) s = s.substring(1).trim();
        int eq = s.indexOf('=');
        if (eq <= 0) return null;
        String field = s.substring(0, eq).trim().toLowerCase(Locale.ROOT).replace('_', ' ');
        if (!field.equals("image") && !field.equals("image file")) return null;

        String value = s.substring(eq + 1);
        value = value.replaceAll("<!--.*?-->", " ")           // editor comments
                     .replaceAll("<[^>]*>", " ")              // <br />
                     .replaceAll("\\[\\[\\s*[Ff]ile\\s*:", "") // [[File:Foo.jpg|thumb]]
                     .replaceAll("\\]\\]", " ");
        int bar = value.indexOf('|');                          // drop caption/sizing params
        if (bar >= 0) value = value.substring(0, bar);
        int brace = value.indexOf("}}");
        if (brace >= 0) value = value.substring(0, brace);
        value = value.trim();
        return hasImageExtension(value) ? value : null;
    }

    // ---- 3. file name → real url --------------------------------------------------------

    /** Asks MediaWiki for a {@link #WIDTH}px thumbnail url for a {@code File:} page. */
    private static Optional<String> fileUrl(String fileName) {
        String title = fileName.startsWith("File:") || fileName.startsWith("file:") ? fileName : "File:" + fileName;
        Optional<String> body = get("action=query&format=json&prop=imageinfo&iiprop=url%7Cmime"
                + "&iiurlwidth=" + WIDTH + "&titles=" + enc(title), "imageinfo " + fileName);
        if (body.isEmpty()) return Optional.empty();

        String url = Tmdb.stringField(body.get(), "thumburl");
        if (url == null) url = Tmdb.stringField(body.get(), "url");
        if (url == null || url.isBlank()) {
            log("imageinfo", "no url for " + fileName);
            return Optional.empty();
        }
        // Strip MediaWiki's analytics query string; the bare url serves the same bytes.
        int q = url.indexOf('?');
        if (q > 0) url = url.substring(0, q);
        return Optional.of(url);
    }

    // ---- 4. validate --------------------------------------------------------------------

    /**
     * Confirms the url really serves an image before it is handed to the browser: 2xx status and an
     * {@code image/*} content type. Covers the failure modes that would otherwise reach the user as a
     * broken-image icon — 404s, redirects to an HTML error page, and hotlink refusals (403).
     */
    private static boolean isLoadableImage(String url) {
        if (url == null || url.isBlank() || !url.startsWith("https://")) return false;
        if (!hasImageExtension(url)) return false;
        if (breakerOpen()) return false;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .header("User-Agent", UA)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> res = HTTP.send(req, HttpResponse.BodyHandlers.discarding());
            String type = res.headers().firstValue("content-type").orElse("");
            boolean ok = res.statusCode() / 100 == 2 && type.toLowerCase(Locale.ROOT).startsWith("image/");
            log("validate", url + " → HTTP " + res.statusCode() + " " + type + (ok ? " OK" : " REJECTED"));
            return ok;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log("validate", url + " → failed (" + e.getClass().getSimpleName() + ")");
            // A validation timeout is "unknown", not "not an image" — keep it out of the cache.
            transportFailed.set(true);
            recordFailure();
            return false;
        }
    }

    private static boolean hasImageExtension(String s) {
        if (s == null) return false;
        String lower = s.toLowerCase(Locale.ROOT);
        int q = lower.indexOf('?');
        if (q > 0) lower = lower.substring(0, q);
        for (String ext : OK_EXT) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    // ---- prewarm ------------------------------------------------------------------------

    /**
     * Resolves every character in the roster on a background thread, so the request path only ever
     * reads the cache. Runs one lookup at a time on purpose — this is a courtesy to a free, anonymous
     * API, and there is no deadline: a character whose image lands after the first request simply
     * shows the plate until then.
     */
    static void prewarm(List<Character> roster) {
        if (roster == null || roster.isEmpty()) return;
        Thread.ofVirtual().name("character-images-prewarm").start(() -> {
            // Several passes, because a rate-limited lookup is deliberately left uncached: pass two
            // picks up whatever pass one deferred. Resolved characters are cache hits and cost nothing.
            for (int pass = 1; pass <= 3; pass++) {
                int pending = 0;
                for (Character c : roster) {
                    if (Thread.currentThread().isInterrupted()) return;
                    Movie m = c.movies().isEmpty() ? null : c.movies().get(0);
                    String title = m == null ? c.universe() : m.title();
                    if (CACHE.containsKey(cacheKey(c.name(), title))) continue;
                    if (resolve(c.name(), title, c.actor()).isEmpty()
                            && !CACHE.containsKey(cacheKey(c.name(), title))) pending++;
                }
                log("prewarm pass " + pass, CACHE.size() + "/" + roster.size() + " settled, " + pending + " deferred");
                if (pending == 0) break;
            }
            int found = 0;
            for (Optional<String> v : CACHE.values()) {
                if (v.isPresent()) found++;
            }
            log("prewarm", found + " of " + roster.size() + " characters have an image");
        });
    }

    // ---- diagnostics --------------------------------------------------------------------

    /** {@code -Dchars.debug=true} traces name, film, query, candidate, file, url and validation. */
    private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("chars.debug", "false"));

    private static void log(String label, String value) {
        if (DEBUG) System.out.println("[chars] " + label + ": " + value);
    }

    // ---- text helpers -------------------------------------------------------------------

    /** Lowercase, drop parentheticals and punctuation, collapse whitespace. */
    private static String norm(String s) {
        if (s == null) return "";
        String out = s.toLowerCase(Locale.ROOT).replaceAll("\\([^)]*\\)", " ");
        StringBuilder b = new StringBuilder(out.length());
        for (int i = 0; i < out.length(); i++) {
            char c = out.charAt(i);
            // java.lang.Character is qualified: this package declares its own Character record.
            b.append((java.lang.Character.isLetterOrDigit(c) || c == ' ') ? c : ' ');
        }
        return b.toString().replaceAll("\\s+", " ").trim();
    }

    /** True when every whitespace-separated word of {@code needle} appears in {@code haystack}. */
    private static boolean containsWords(String haystack, String needle) {
        for (String token : needle.split(" ")) {
            if (token.isEmpty()) continue;
            if (!containsWord(haystack, token)) return false;
        }
        return true;
    }

    /** Whole-word containment, so "merida" does not match "meridian". */
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
}
