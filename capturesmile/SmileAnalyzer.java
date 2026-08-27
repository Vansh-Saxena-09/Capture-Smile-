package capturesmile;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * The deterministic "smile analysis" engine. There is no ML here (impossible offline in pure
 * Java) and none is needed: this is a novelty app, so the same photo bytes always produce the
 * same result. That determinism is also what makes the Friends-Match feature fair — every friend
 * runs through this exact same pipeline.
 *
 * <p><b>Fairness / stability:</b> a fresh {@link MessageDigest} and {@link SplittableRandom} are
 * created per call (neither is thread-safe), and values are drawn in a <b>frozen order</b>
 * (score → dimensions → vibe → description → ranking → movie). Reordering these draws would
 * silently change everyone's results, so the order must not change.
 */
public final class SmileAnalyzer {

    /** The five smile dimensions, in their frozen draw order. */
    public static final List<String> DIMENSIONS =
            List.of("radiance", "confidence", "playfulness", "mystery", "warmth");

    private SmileAnalyzer() {}

    public static AnalysisResult analyze(byte[] imageBytes) {
        long seed = seedFrom(imageBytes);
        SplittableRandom rng = new SplittableRandom(seed);

        // 1) Score — bounded to a flattering 7.0..9.9 by construction ("never insulting").
        double score = Math.round((7.0 + rng.nextDouble() * 2.9) * 10.0) / 10.0;

        // 2) Dimensions — one value each, in the frozen order above.
        Map<String, Double> dims = new LinkedHashMap<>();
        for (String d : DIMENSIONS) dims.put(d, rng.nextDouble());

        // 3) Vibe — the strongest dimension.
        String topDim = DIMENSIONS.get(0);
        for (String d : DIMENSIONS) if (dims.get(d) > dims.get(topDim)) topDim = d;
        String vibe = vibeLabel(topDim);

        // 4) One-sentence description — chosen from the pool for that vibe.
        List<String> pool = DESCRIPTIONS.getOrDefault(vibe, DESCRIPTIONS.get("RADIANT"));
        String description = pool.get(rng.nextInt(pool.size()));

        // 5) Rank characters by vibe overlap + deterministic jitter (separate RNG so the main
        //    draw order stays stable even if the catalog size changes). Headline = ranked[0].
        List<Character> catalog = Catalog.all();
        SplittableRandom jitterRng = new SplittableRandom(seed ^ 0x9E3779B97F4A7C15L);
        List<Scored> scored = new ArrayList<>(catalog.size());
        for (Character c : catalog) {
            double overlap = dot(dims, profile(c.vibes()));
            double jitter = jitterRng.nextDouble() * 0.55; // variety so one vibe != always one character
            scored.add(new Scored(c, overlap + jitter));
        }
        scored.sort((a, b) -> Double.compare(b.score(), a.score()));

        int n = Math.min(5, scored.size());
        List<AnalysisResult.Ranked> top = new ArrayList<>(n);
        int pct = 90 + rng.nextInt(9); // 90..98
        int prev = 101;
        for (int i = 0; i < n; i++) {
            if (pct >= prev) pct = prev - 1;      // guarantee strictly descending
            if (pct < 60) pct = 60;
            top.add(new AnalysisResult.Ranked(scored.get(i).character(), pct));
            prev = pct;
            pct -= 3 + rng.nextInt(5);            // next drop
        }
        Character character = top.get(0).character();
        int matchPct = top.get(0).matchPct();

        // 6) Best movie — of the matched character's movies, the best vibe overlap (seed tie-break).
        Movie best = pickMovie(character, dims, rng);

        return new AnalysisResult(score, vibe, description, character, matchPct, best, top, dims);
    }

    private record Scored(Character character, double score) {}

    // ---- movie selection -------------------------------------------------------------------

    private static Movie pickMovie(Character c, Map<String, Double> dims, SplittableRandom rng) {
        List<Movie> movies = c.movies();
        if (movies == null || movies.isEmpty()) return null;
        Movie best = null;
        double bestScore = -1;
        for (Movie m : movies) {
            double s = dot(dims, profile(m.vibes())) + rng.nextDouble() * 0.05; // deterministic tie-break
            if (s > bestScore) {
                bestScore = s;
                best = m;
            }
        }
        return best != null ? best : movies.get(0);
    }

    // ---- vibe/dimension math ---------------------------------------------------------------

    /** Maps a set of vibe tags to a normalized 5-dimension profile vector. Unknown tags ignored. */
    private static Map<String, Double> profile(List<String> vibeTags) {
        Map<String, Double> p = new HashMap<>();
        for (String d : DIMENSIONS) p.put(d, 0.0);
        if (vibeTags != null) {
            for (String tag : vibeTags) {
                Map<String, Double> v = VIBE_MAP.get(tag.toLowerCase());
                if (v != null) v.forEach((k, val) -> p.merge(k, val, Double::sum));
            }
        }
        double mag = Math.sqrt(p.values().stream().mapToDouble(x -> x * x).sum());
        if (mag > 0) p.replaceAll((k, val) -> val / mag);
        return p;
    }

    private static double dot(Map<String, Double> a, Map<String, Double> b) {
        double s = 0;
        for (String d : DIMENSIONS) s += a.getOrDefault(d, 0.0) * b.getOrDefault(d, 0.0);
        return s;
    }

    private static String vibeLabel(String dim) {
        return switch (dim) {
            case "radiance"    -> "RADIANT";
            case "confidence"  -> "CONFIDENT";
            case "playfulness" -> "PLAYFUL";
            case "mystery"     -> "MYSTERIOUS";
            case "warmth"      -> "WARM";
            default            -> "RADIANT";
        };
    }

    private static long seedFrom(byte[] data) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(data);
            long s = 0;
            for (int i = 0; i < 8; i++) s = (s << 8) | (h[i] & 0xFF);
            return s;
        } catch (NoSuchAlgorithmException e) {
            return Arrays.hashCode(data); // SHA-256 is always present; belt-and-braces only
        }
    }

    // ---- static data -----------------------------------------------------------------------

    /** Vibe tag → contribution to each dimension. Lets the catalog use a rich tag vocabulary. */
    private static final Map<String, Map<String, Double>> VIBE_MAP = buildVibeMap();

    private static Map<String, Map<String, Double>> buildVibeMap() {
        Map<String, Map<String, Double>> m = new HashMap<>();
        m.put("radiant",    Map.of("radiance", 1.0));
        m.put("confident",  Map.of("confidence", 1.0));
        m.put("playful",    Map.of("playfulness", 1.0));
        m.put("mysterious", Map.of("mystery", 1.0));
        m.put("warm",       Map.of("warmth", 1.0));
        m.put("chaotic",    Map.of("playfulness", 0.9, "radiance", 0.2));
        m.put("charming",   Map.of("warmth", 0.6, "confidence", 0.5));
        m.put("energetic",  Map.of("playfulness", 0.7, "radiance", 0.4));
        m.put("curious",    Map.of("warmth", 0.4, "playfulness", 0.3, "mystery", 0.2));
        m.put("clever",     Map.of("confidence", 0.5, "mystery", 0.4));
        m.put("cool",       Map.of("confidence", 0.6, "mystery", 0.5));
        m.put("intense",    Map.of("confidence", 0.5, "mystery", 0.6));
        return m;
    }

    /** One-sentence smile descriptions, keyed by vibe. Fun, occasionally cheeky, never insulting. */
    private static final Map<String, List<String>> DESCRIPTIONS = buildDescriptions();

    private static Map<String, List<String>> buildDescriptions() {
        Map<String, List<String>> m = new HashMap<>();
        m.put("RADIANT", List.of(
                "That smile has enough confidence to enter the room before you do.",
                "Certified serotonin — this smile could power a small city.",
                "This is the kind of smile that makes total strangers wave back.",
                "Warning: prolonged exposure to this smile may cause spontaneous good moods.",
                "That smile doesn't just light up a room, it files for property rights.",
                "Sunglasses recommended — this smile is operating at full brightness."
        ));
        m.put("PLAYFUL", List.of(
                "That smile looks like it already knows the punchline.",
                "There's a 90% chance this smile is planning something.",
                "This smile has strong 'main character with zero supervision' energy.",
                "That grin says the rules are more of a gentle suggestion.",
                "This smile is one wink away from causing friendly chaos.",
                "That's the smile of someone who is absolutely up to something fun."
        ));
        m.put("CONFIDENT", List.of(
                "That smile signed an autograph before you finished reading this.",
                "This smile walked in like it owns the place — and honestly, maybe it does.",
                "Cool, composed, and quietly running the entire scene.",
                "That smile has never once doubted itself, and it shows.",
                "This is a 'trust me, I've got this' smile in its purest form.",
                "That grin closes deals it hasn't even opened yet."
        ));
        m.put("MYSTERIOUS", List.of(
                "That smile knows a secret and it is absolutely not telling.",
                "Half a smile, twice the intrigue.",
                "This smile answers questions with better questions.",
                "There's a whole plot twist hiding behind that expression.",
                "That's the smile the detective hasn't quite cleared yet.",
                "Quietly magnetic, suspiciously calm, definitely three steps ahead."
        ));
        m.put("WARM", List.of(
                "That smile feels like a hug you didn't know you needed.",
                "This is the smile that remembers everyone's birthday.",
                "Instantly trustworthy, dangerously huggable.",
                "That grin could talk its way out of any argument, kindly.",
                "This smile makes the whole group photo better just by existing.",
                "The human equivalent of a warm cup of chai."
        ));
        return m;
    }
}
