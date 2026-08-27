package capturesmile;

import java.util.Optional;

/**
 * Resolves the three images a casting card needs, keeping them strictly separate.
 *
 * <pre>
 *   characterImage  the fictional character, in costume/in role   local file, else Wikipedia article image
 *   actorImage      the performer's own face                      local file, else TMDB profile_path
 *   moviePoster     the film's poster                             catalog posterPath, else TMDB lookup
 * </pre>
 *
 * <p>The three come from three different sources, and that is the point: TMDB has no character-image
 * endpoint at all, so the character still comes from {@link CharacterImages} (Wikipedia) while the
 * performer's portrait comes from TMDB. They are never interchangeable.
 *
 * <p><b>The rule that matters:</b> TMDB's {@code profile_path} is a photograph of the <em>performer</em>,
 * not of the fictional character. It is therefore only ever used for {@link Resolved#actorImage()}.
 * When no in-character still can be found, {@link Resolved#characterImage()} stays null and the
 * frontend draws its CSS character plate — a designed fallback, not the actor's face wearing the
 * wrong label.
 * Conflating the two would misattribute a real person's photograph to a fictional role, so the
 * separation is enforced here rather than left to each caller.
 *
 * <p>Local files win over TMDB for both portraits: self-hosted images have no rate limit, survive a
 * TMDB outage, and work offline.
 */
public final class MatchImages {

    /** The frontend-facing shape: names, ids, and three independently-resolved image urls. */
    public record Resolved(
            String characterName,
            String characterImage,
            String actorName,
            String actorImage,
            String movieTitle,
            String moviePoster,
            int movieId
    ) {}

    private MatchImages() {}

    /**
     * The character still, in priority order: a local {@code chars/<id>} file, else a Wikipedia
     * character-article image resolved by {@link CharacterImages}, else null so the frontend draws its
     * CSS plate.
     *
     * <p>Local wins because a self-hosted file has no rate limit and works offline. The remote step
     * reads {@link CharacterImages#cached} only — never the network — because this runs on the request
     * path; the roster is resolved in the background at startup instead.
     *
     * <p>There is deliberately no actor fallback. {@code profile_path} is a photograph of a real
     * performer, and captioning it with a character's name would misattribute it.
     */
    public static String characterImage(Character c) {
        String local = ImageAssets.characterImage(c);
        if (local != null) return local;
        if (c == null) return null;
        // Same (name, film) key the prewarm used, so a hit is found rather than re-derived.
        Movie first = c.movies().isEmpty() ? null : c.movies().get(0);
        String title = first == null ? c.universe() : first.title();
        return CharacterImages.cached(c.name(), title).orElse(null);
    }

    /**
     * The performer's portrait: the local {@code actors/<id>} file, else the {@code profile_path} of
     * the cast member matched to this character in one of their films.
     */
    public static String actorImage(Character c) {
        String local = ImageAssets.actorImage(c);
        if (local != null) return local;
        return creditFor(c).map(cr -> Tmdb.imageUrl(cr.profilePath(), Tmdb.PROFILE_SIZE)).orElse(null);
    }

    /**
     * The performer's name as TMDB credits them for this role, falling back to the catalog's own
     * value. The catalog is authoritative for display; this only confirms the mapping.
     */
    public static String actorName(Character c) {
        if (c == null) return null;
        return creditFor(c).map(Tmdb.Credit::actorName)
                .filter(n -> n != null && !n.isBlank())
                .orElse(c.actor());
    }

    /** The poster url: catalog {@code posterPath} if present, else a TMDB lookup by film id. */
    public static String posterUrl(Movie m) {
        if (m == null) return null;
        if (m.hasPoster()) return Tmdb.imageUrl(m.posterPath(), Tmdb.POSTER_SIZE);
        return Tmdb.posterPath(m.tmdbId()).map(p -> Tmdb.imageUrl(p, Tmdb.POSTER_SIZE)).orElse(null);
    }

    /** Assembles the full structured match for one character + film pairing. */
    public static Resolved resolve(Character c, Movie m) {
        return new Resolved(
                c == null ? null : c.name(),
                characterImage(c),
                actorName(c),
                actorImage(c),
                m == null ? null : m.title(),
                posterUrl(m),
                m == null ? 0 : m.tmdbId());
    }

    /**
     * Finds this character's cast credit, trying each of their films in order. Only reached when a
     * local portrait is absent, and {@link Tmdb} caches both hits and misses.
     */
    private static Optional<Tmdb.Credit> creditFor(Character c) {
        if (c == null || !Tmdb.configured()) return Optional.empty();
        for (Movie m : c.movies()) {
            Optional<Tmdb.Credit> hit = Tmdb.credit(m.tmdbId(), c.name());
            if (hit.isPresent()) return hit;
        }
        return Optional.empty();
    }
}
