import capturesmile.Tmdb;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * Verifies the narrow TMDB response readers and the cast matcher against realistic fixtures.
 * Standalone (no test framework in this project) — run it, read the PASS/FAIL lines.
 */
public class TmdbParseTest {

    static int pass = 0, fail = 0;

    // Shaped like a real /movie/62177/credits body: "(voice)" suffixes, a null profile_path,
    // a decoy whose character contains a cast member's name, and a trailing "crew" array.
    static final String CREDITS = """
        {"id":62177,"cast":[
          {"adult":false,"id":8536,"name":"Kelly Macdonald","character":"Merida (voice)","profile_path":"/8Qb2Kq9Kxst2hDdZTX0Tc31eXvA.jpg","order":0},
          {"adult":false,"id":1234,"name":"Billy Connolly","character":"Fergus (voice)","profile_path":"/qMKspBGSCkKUuIzSpJHhNZ2rEDA.jpg","order":1},
          {"adult":false,"id":5678,"name":"Emma Thompson","character":"Elinor (voice)","profile_path":null,"order":2},
          {"adult":false,"id":9012,"name":"Julie Walters","character":"The Witch \\"Wise Woman\\" (voice)","profile_path":"/witch.jpg","order":3}
        ],"crew":[{"id":999,"name":"Mark Andrews","job":"Director","profile_path":"/director.jpg"}]}
        """;

    static final String MOVIE = """
        {"adult":false,"id":62177,"original_title":"Brave","poster_path":"/8Qb2Kq9Kxst2hDdZTX0Tc31eXvA.jpg","release_date":"2012-06-21","title":"Brave"}
        """;

    static final String NO_POSTER = """
        {"adult":false,"id":62177,"poster_path":null,"title":"Brave"}
        """;

    public static void main(String[] args) throws Exception {
        Method stringField = Tmdb.class.getDeclaredMethod("stringField", String.class, String.class);
        Method arrayObjects = Tmdb.class.getDeclaredMethod("arrayObjects", String.class, String.class);
        Method matchCast = Tmdb.class.getDeclaredMethod("matchCast", List.class, String.class);
        stringField.setAccessible(true);
        arrayObjects.setAccessible(true);
        matchCast.setAccessible(true);

        // ---- image url construction ----
        eq("/abc123.jpg builds a full url",
                "https://image.tmdb.org/t/p/w500/abc123.jpg", Tmdb.imageUrl("/abc123.jpg", "w500"));
        eq("a path missing its leading slash is still handled",
                "https://image.tmdb.org/t/p/w500/abc123.jpg", Tmdb.imageUrl("abc123.jpg", "w500"));
        eq("null path -> null (caller falls back)", null, Tmdb.imageUrl(null, "w500"));
        eq("blank path -> null", null, Tmdb.imageUrl("   ", "w500"));
        eq("profile size is honoured",
                "https://image.tmdb.org/t/p/w185/p.jpg", Tmdb.imageUrl("/p.jpg", "w185"));

        // ---- scalar reads ----
        eq("poster_path read from a movie body",
                "/8Qb2Kq9Kxst2hDdZTX0Tc31eXvA.jpg", stringField.invoke(null, MOVIE, "poster_path"));
        eq("JSON null poster_path -> null", null, stringField.invoke(null, NO_POSTER, "poster_path"));
        eq("absent key -> null", null, stringField.invoke(null, MOVIE, "backdrop_path"));

        // ---- array chunking ----
        @SuppressWarnings("unchecked")
        List<String> cast = (List<String>) arrayObjects.invoke(null, CREDITS, "cast");
        eq("cast array yields exactly its 4 entries (never wanders into crew)", 4, cast.size());

        // ---- Brave -> Merida, the case in the brief ----
        Optional<?> merida = (Optional<?>) matchCast.invoke(null, cast, "Merida");
        ok("Brave -> Merida resolves a credit", merida.isPresent());
        if (merida.isPresent()) {
            Tmdb.Credit c = (Tmdb.Credit) merida.get();
            eq("  actor is Kelly Macdonald", "Kelly Macdonald", c.actorName());
            eq("  profile_path is hers", "/8Qb2Kq9Kxst2hDdZTX0Tc31eXvA.jpg", c.profilePath());
            eq("  actor image url", "https://image.tmdb.org/t/p/w500/8Qb2Kq9Kxst2hDdZTX0Tc31eXvA.jpg",
                    Tmdb.imageUrl(c.profilePath(), Tmdb.PROFILE_SIZE));
        }

        // "(voice)" must not defeat matching — most of this roster is animated.
        Optional<?> fergus = (Optional<?>) matchCast.invoke(null, cast, "Fergus");
        ok("'(voice)' suffix still matches", fergus.isPresent()
                && ((Tmdb.Credit) fergus.get()).actorName().equals("Billy Connolly"));

        // An escaped quote inside a character name must not derail the scanner.
        Optional<?> witch = (Optional<?>) matchCast.invoke(null, cast, "The Witch");
        ok("escaped quotes in a character name survive", witch.isPresent()
                && ((Tmdb.Credit) witch.get()).actorName().equals("Julie Walters"));

        // A null profile_path is a clean miss, not a crash or a bogus url.
        Optional<?> elinor = (Optional<?>) matchCast.invoke(null, cast, "Elinor");
        ok("null profile_path -> credit found, image url null", elinor.isPresent()
                && ((Tmdb.Credit) elinor.get()).profilePath() == null
                && Tmdb.imageUrl(((Tmdb.Credit) elinor.get()).profilePath()) == null);

        // A character nobody plays must not silently match the wrong performer.
        Optional<?> nobody = (Optional<?>) matchCast.invoke(null, cast, "Hermione Granger");
        ok("an absent character does not match anyone", nobody.isEmpty());

        System.out.println("\n" + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    static void eq(String what, Object want, Object got) {
        ok(what + " (got: " + got + ")", want == null ? got == null : want.equals(got));
    }

    static void ok(String what, boolean condition) {
        if (condition) { pass++; System.out.println("PASS  " + what); }
        else { fail++; System.out.println("FAIL  " + what); }
    }
}
