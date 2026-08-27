package capturesmile;

/**
 * Serializes domain objects into the JSON the browser consumes. Centralizing this keeps the HTTP
 * handlers thin and keeps every string running through {@link Json}'s escaping.
 */
public final class JsonView {

    /**
     * Public TMDB image CDN base, kept for compatibility. Url construction now lives in
     * {@link Tmdb#imageUrl(String, String)} so poster and profile paths are built one way.
     */
    public static final String TMDB_IMG_BASE = Tmdb.IMG_BASE + Tmdb.POSTER_SIZE;

    private JsonView() {}

    /** Delegates to {@link MatchImages}, which falls back to a TMDB lookup when the catalog has no path. */
    static String posterUrl(Movie m) {
        return MatchImages.posterUrl(m);
    }

    public static String analysis(AnalysisResult r) {
        Json.Obj o = new Json.Obj()
                .dec("score", r.score(), 1)
                .str("vibe", r.vibe())
                .str("description", r.description())
                .raw("character", character(r.character(), r.matchPct(), r.vibe()))
                .raw("bestMovie", movie(r.bestMovie()))
                .raw("match", match(r.character(), r.bestMovie()))
                .raw("topMatches", topMatches(r.topMatches()))
                .raw("dimensions", dimensions(r));
        return o.end();
    }

    /**
     * The flat, explicitly-named match object. {@code characterImage} and {@code actorImage} are
     * separate fields resolved by separate rules — see {@link MatchImages}.
     */
    private static String match(Character c, Movie m) {
        MatchImages.Resolved r = MatchImages.resolve(c, m);
        return new Json.Obj()
                .str("characterName", r.characterName())
                .str("characterImage", r.characterImage())
                .str("actorName", r.actorName())
                .str("actorImage", r.actorImage())
                .str("movieTitle", r.movieTitle())
                .str("moviePoster", r.moviePoster())
                .num("movieId", r.movieId())
                .end();
    }

    private static String character(Character c, int matchPct, String vibe) {
        return new Json.Obj()
                .str("id", c.id())
                .str("name", c.name())
                .str("universe", c.universe())
                .str("actor", c.actor())
                .bool("animated", c.animated())
                .str("creditLabel", c.creditLabel())
                .str("emoji", c.emoji())
                .str("monogram", c.monogram())
                .str("colorA", c.colorA())
                .str("colorB", c.colorB())
                .str("characterImageUrl", MatchImages.characterImage(c))
                .str("actorImageUrl", MatchImages.actorImage(c))
                .str("why", c.whyResemble(vibe))
                .num("matchPct", matchPct)
                .end();
    }

    private static String movie(Movie m) {
        if (m == null) return "null";
        return new Json.Obj()
                .str("title", m.title())
                .num("year", m.year())
                .num("tmdbId", m.tmdbId())
                .str("posterUrl", posterUrl(m))
                .str("why", m.whyFit())
                .end();
    }

    private static String topMatches(java.util.List<AnalysisResult.Ranked> list) {
        Json.Arr a = new Json.Arr();
        for (AnalysisResult.Ranked r : list) {
            Character c = r.character();
            a.add(new Json.Obj()
                    .str("name", c.name())
                    .str("universe", c.universe())
                    .str("actor", c.actor())
                    .str("creditLabel", c.creditLabel())
                    .str("emoji", c.emoji())
                    .str("monogram", c.monogram())
                    .str("colorA", c.colorA())
                    .str("colorB", c.colorB())
                    .str("characterImageUrl", MatchImages.characterImage(c))
                    .str("actorImageUrl", MatchImages.actorImage(c))
                    .num("matchPct", r.matchPct())
                    .end());
        }
        return a.end();
    }

    private static String dimensions(AnalysisResult r) {
        Json.Obj o = new Json.Obj();
        for (String d : SmileAnalyzer.DIMENSIONS) {
            o.dec(d, r.dimensions().getOrDefault(d, 0.0), 3);
        }
        return o.end();
    }

    // ---- showdown ------------------------------------------------------------------------

    public static String showdown(Showdown.Result r) {
        Json.Arr ranking = new Json.Arr();
        for (Showdown.Rank rank : r.ranking()) {
            ranking.add(new Json.Obj()
                    .num("place", rank.place())
                    .str("name", rank.name())
                    .dec("score", rank.score(), 1)
                    .num("matchPct", rank.matchPct())
                    .str("vibe", rank.vibe())
                    .str("characterName", rank.characterName())
                    .end());
        }
        Json.Arr awards = new Json.Arr();
        for (Showdown.Award aw : r.awards()) {
            awards.add(new Json.Obj()
                    .str("key", aw.key())
                    .str("emoji", aw.emoji())
                    .str("title", aw.title())
                    .str("subtitle", aw.subtitle())
                    .str("winnerName", aw.winnerName())
                    .str("detail", aw.detail())
                    .end());
        }
        return new Json.Obj().raw("ranking", ranking.end()).raw("awards", awards.end()).end();
    }

    static String error(String message) {
        return new Json.Obj().bool("ok", false).str("error", message).end();
    }
}
