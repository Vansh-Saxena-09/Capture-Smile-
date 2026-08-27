package capturesmile;

import java.util.List;

/**
 * A fictional character the user's smile can match to.
 *
 * <p>No image paths live here. Two images can exist per character — the character themself (in
 * costume, in role) and the performer's own portrait, shown as a small inset beside the character's
 * name so the credit has a face — and {@link ImageAssets} finds both from {@link #id()} by
 * convention. Under them sits an always-on CSS character plate built from {@code emoji},
 * {@code colorA} and {@code colorB}, so a card is never blank and a missing file degrades to the
 * plate instead of a gap.
 *
 * <p><b>Why most characters have no character image:</b> in-costume stills for these roles are
 * non-free studio publicity material — Wikipedia hosts them under fair use and Commons won't carry
 * them, so there is nothing we can legitimately re-host. Only the handful with a genuinely
 * free-licensed in-character photo have a file; the rest rely on the plate.
 *
 * <p>{@code whyTemplate} is phrased about the smile/vibe (never physical appearance) and contains
 * the literal token {@code {vibe}}, which {@link SmileAnalyzer} replaces with the user's vibe word.
 */
public record Character(
        String id,
        String name,
        String universe,
        String actor,
        boolean animated,
        String emoji,
        String colorA,
        String colorB,
        List<String> vibes,
        String whyTemplate,
        List<Movie> movies
) {
    /** Fills {@code {vibe}} in the template with the user's (lowercased) vibe word. */
    public String whyResemble(String vibe) {
        String v = vibe == null ? "" : vibe.toLowerCase();
        return whyTemplate.replace("{vibe}", v);
    }

    /** "Played by" for live action, "Voiced by" for animated. */
    public String creditLabel() {
        return animated ? "Voiced by" : "Played by";
    }

    /** Two initials for the plate monogram, e.g. "Jack Sparrow" -> "JS", "Eleven" -> "EL". */
    public String monogram() {
        String[] parts = name.trim().split("[\\s-]+");
        if (parts.length >= 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
            return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
        }
        String one = parts.length > 0 ? parts[0] : name;
        return one.length() >= 2 ? one.substring(0, 2).toUpperCase() : one.toUpperCase();
    }
}
