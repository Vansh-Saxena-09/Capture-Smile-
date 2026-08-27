package capturesmile;

import java.util.List;

/**
 * One movie or series a matched character appears in.
 *
 * <p>{@code posterPath} is a TMDB poster path such as {@code "/abc123.jpg"} and is optional
 * (may be {@code null} or empty). The frontend builds the full CDN url and degrades gracefully
 * to a styled poster card when the image is missing or fails to load, so an inexact path is
 * never fatal.
 */
public record Movie(
        String title,
        int tmdbId,
        String posterPath,
        int year,
        List<String> vibes,
        String whyFit
) {
    public boolean hasPoster() {
        return posterPath != null && !posterPath.isBlank();
    }
}
