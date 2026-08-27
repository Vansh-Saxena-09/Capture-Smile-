package capturesmile;

import java.util.Map;

/**
 * The derived, photo-free summary of one friend's result, sent from the browser to
 * {@code /api/showdown}. Deliberately carries NO image data — only the numbers needed to rank
 * the squad and award superlatives. This keeps the Friends-Match feature private: the server
 * never receives or stores anyone's photo.
 */
public record FriendSummary(
        String name,
        double score,
        int matchPct,
        String vibe,
        String characterName,
        Map<String, Double> dimensions
) {
    public double dim(String key) {
        Double d = dimensions.get(key);
        return d == null ? 0.0 : d;
    }
}
