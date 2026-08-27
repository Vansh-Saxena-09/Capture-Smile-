package capturesmile;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves character and performer portraits by convention rather than by declaration.
 *
 * <p>Every image lives at {@code /static/img/<kind>/<character-id>.<ext>}, so there is nothing to
 * register: dropping {@code loki.jpg} into {@code web/static/img/chars/} makes it appear on Loki's
 * card. That matters because the roster's missing character stills are missing for a licensing
 * reason, not a code reason — whoever obtains a usable still should be able to add it without
 * editing {@link Catalog} or recompiling.
 *
 * <p>Lookups hit the filesystem on every request instead of caching, deliberately: a dozen
 * {@link Files#isRegularFile} calls against the OS cache cost nothing next to hashing the upload,
 * and caching would mean a newly added file needed a server restart — exactly the friction this
 * class exists to remove.
 *
 * <p>A missing file is not an error. {@code null} flows through {@link JsonView} to the browser,
 * which then draws the always-on CSS character plate instead of an empty frame (spec s10: the
 * character visual is never broken).
 */
public final class ImageAssets {

    /** Tried in order, so a hand-optimised .webp can supersede a .jpg without renaming anything. */
    private static final String[] EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"};

    /** Set once by {@link Main}. Left null by unit tests, where every lookup returns null. */
    private static volatile Path webroot;

    private ImageAssets() {}

    static void useWebroot(Path root) {
        webroot = (root == null) ? null : root.toAbsolutePath().normalize();
    }

    /** The in-character still, or null when no file has been supplied for this character. */
    static String characterImage(Character c) {
        return lookup("chars", c == null ? null : c.id());
    }

    /** The performer's own portrait, or null. */
    static String actorImage(Character c) {
        return lookup("actors", c == null ? null : c.id());
    }

    private static String lookup(String kind, String id) {
        Path root = webroot;
        if (root == null || !isSafeId(id)) return null;
        Path dir = root.resolve("static").resolve("img").resolve(kind);
        for (String ext : EXTENSIONS) {
            if (Files.isRegularFile(dir.resolve(id + ext))) {
                return "/static/img/" + kind + "/" + id + ext;
            }
        }
        return null;
    }

    /**
     * Ids come from our own catalog, never from a request — but this keeps a future data edit from
     * turning a stray {@code ../} into a path that escapes the web root.
     */
    private static boolean isSafeId(String id) {
        if (id == null || id.isEmpty()) return false;
        for (int i = 0; i < id.length(); i++) {
            char ch = id.charAt(i);
            boolean ok = (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '-';
            if (!ok) return false;
        }
        return true;
    }
}
