package capturesmile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns a squad of {@link FriendSummary} into a Smile Showdown: an overall ranking plus six
 * superlative awards, so that even 2nd place still "wins" a category rather than just looking
 * worse. All tie-breaks are deterministic (higher score, then original capture order).
 *
 * <p>Every friend was scored by the exact same {@link SmileAnalyzer} pipeline, so the comparison
 * only reflects the smile vibe — never image quality, lighting, or camera.
 */
public final class Showdown {

    public record Rank(int place, String name, double score, int matchPct, String vibe, String characterName) {}

    public record Award(String key, String emoji, String title, String subtitle, String winnerName, String detail) {}

    public record Result(List<Rank> ranking, List<Award> awards) {}

    private Showdown() {}

    public static Result compute(List<FriendSummary> friends) {
        // --- ranking: score desc, then matchPct desc, then original order (stable & deterministic)
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < friends.size(); i++) order.add(i);
        order.sort((a, b) -> {
            FriendSummary fa = friends.get(a), fb = friends.get(b);
            int c = Double.compare(fb.score(), fa.score());
            if (c != 0) return c;
            c = Integer.compare(fb.matchPct(), fa.matchPct());
            if (c != 0) return c;
            return Integer.compare(a, b);
        });
        List<Rank> ranking = new ArrayList<>();
        for (int place = 0; place < order.size(); place++) {
            FriendSummary f = friends.get(order.get(place));
            ranking.add(new Rank(place + 1, f.name(), f.score(), f.matchPct(), f.vibe(), f.characterName()));
        }

        // --- superlative awards
        List<Award> awards = new ArrayList<>();
        awards.add(award(friends, "champion", "🏆", "SMILE CHAMPION", "Highest overall smile score",
                f -> f.score(), f -> String.format(Locale.ROOT, "%.1f / 10", f.score())));
        awards.add(award(friends, "chaotic", "😂", "MOST CHAOTIC", "Most playful, chaotic energy",
                f -> f.dim("playfulness"), f -> "Pure friendly chaos"));
        awards.add(award(friends, "radiant", "✨", "MOST RADIANT", "Brightest, most expressive smile",
                f -> f.dim("radiance"), f -> "Absolutely glowing"));
        awards.add(award(friends, "confident", "😎", "MOST CONFIDENT", "Strongest confident smile",
                f -> f.dim("confidence"), f -> "Owns every room"));
        awards.add(award(friends, "mysterious", "🕵️", "MOST MYSTERIOUS", "Most subtle, mysterious smile",
                f -> f.dim("mystery"), f -> "Keeping secrets"));
        awards.add(award(friends, "match", "🦸", "BEST FICTIONAL MATCH", "Highest character-match %",
                f -> (double) f.matchPct(), f -> f.matchPct() + "% match with " + f.characterName()));
        return new Result(ranking, awards);
    }

    @FunctionalInterface private interface Metric { double of(FriendSummary f); }
    @FunctionalInterface private interface Detail { String of(FriendSummary f); }

    private static Award award(List<FriendSummary> friends, String key, String emoji, String title,
                               String subtitle, Metric metric, Detail detail) {
        FriendSummary best = null;
        double bestV = Double.NEGATIVE_INFINITY;
        for (FriendSummary f : friends) {
            double v = metric.of(f);
            // strictly greater wins; on a tie prefer the higher score; else keep the earlier one
            if (best == null || v > bestV || (v == bestV && f.score() > best.score())) {
                bestV = v;
                best = f;
            }
        }
        return new Award(key, emoji, title, subtitle,
                best != null ? best.name() : "",
                best != null ? detail.of(best) : "");
    }
}
