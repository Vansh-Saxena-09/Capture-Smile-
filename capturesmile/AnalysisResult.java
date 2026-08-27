package capturesmile;

import java.util.List;
import java.util.Map;

/**
 * The full deterministic result of analyzing one smile.
 *
 * <p>{@code dimensions} holds the five sub-scores (radiance, confidence, playfulness, mystery,
 * warmth) in the 0..1 range. The one-word {@code vibe} is derived from the strongest dimension,
 * and the same dimensions drive the Friends-Match superlative categories.
 *
 * <p>{@code character} is always {@code topMatches.get(0).character()} so the headline match and
 * the top-of-list match can never disagree.
 */
public record AnalysisResult(
        double score,
        String vibe,
        String description,
        Character character,
        int matchPct,
        Movie bestMovie,
        List<Ranked> topMatches,
        Map<String, Double> dimensions
) {
    /** A character plus its match percentage, used for the "Top Matches" list. */
    public record Ranked(Character character, int matchPct) {}
}
