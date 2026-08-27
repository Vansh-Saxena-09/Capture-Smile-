package capturesmile;

import java.util.List;

/**
 * The immutable character + movie database the {@link SmileAnalyzer} draws from. Everything here is
 * static, shared, and read-only, so it is safe to hand the same {@link List} to every request
 * thread without copying.
 *
 * <p><b>Encoding note:</b> this file (like {@link SmileAnalyzer}'s descriptions) contains UTF-8
 * emoji and em-dashes, so it must be compiled with {@code -encoding UTF-8} — otherwise a Windows
 * default (Cp1252) {@code javac} turns the emblem glyphs into mojibake. The provided
 * {@code build.bat} sets this, and {@link Main}'s documented command was updated to match.
 *
 * <p><b>Images.</b> No image paths appear in this file. {@link ImageAssets} derives them from each
 * character's id — {@code /static/img/actors/<id>.jpg} for the performer's portrait,
 * {@code /static/img/chars/<id>.jpg} for a genuinely in-character still — so adding an image means
 * dropping a file in, not editing Java. Both are normalised to 360x480 by
 * {@code tools/ImagePrep.java}. Hosting them ourselves rather than hotlinking means no rate limits,
 * no third-party outage, and the app still works offline — which is what actually keeps the
 * "character image is never broken" rule (spec s10) true.
 *
 * <p>Only a few characters have a {@code chars/} file, on purpose: in-costume stills for these roles
 * are non-free studio publicity stills, so there is no freely-licensed file to re-host. Those cards
 * fall back to the CSS character plate (duotone + glyph + monogram + universe wordmark), which is a
 * designed visual rather than a placeholder. Movie posters are likewise absent and render as styled
 * poster cards; real TMDB paths can be dropped into {@link Movie} later with no frontend change.
 *
 * <p>Vibe tags are drawn from {@link SmileAnalyzer}'s vocabulary: radiant, confident, playful,
 * mysterious, warm, chaotic, charming, energetic, curious, clever, cool, intense. The roster is
 * deliberately spread across the five smile dimensions so different smiles surface different faces.
 */
public final class Catalog {

    private Catalog() {}

    // Character emblem glyphs (UTF-8 source — compile with -encoding UTF-8; see class note).
    private static final String E_WEB     = "🕸️"; // spider web
    private static final String E_ARM     = "🦾";       // mechanical arm
    private static final String E_HEART   = "🖤";       // black heart
    private static final String E_BOLT    = "⚡";             // high voltage
    private static final String E_BOOKS   = "📚";       // books
    private static final String E_PHONES  = "🎧";       // headphone
    private static final String E_SHADES  = "🕶️"; // dark sunglasses
    private static final String E_WAFFLE  = "🧇";       // waffle
    private static final String E_STETH   = "🩺";       // stethoscope
    private static final String E_TRAIN   = "🚂";       // locomotive
    private static final String E_PANDA   = "🐼";       // panda
    private static final String E_SNOW    = "❄️";       // snowflake
    private static final String E_WAVE    = "🌊";       // water wave
    private static final String E_COMPASS = "🧭";       // compass
    private static final String E_SNAKE   = "🐍";       // snake
    private static final String E_BOW     = "🏹";       // bow and arrow

    private static final List<Character> ALL = List.of(

        new Character("spider-man", "Spider-Man", "Marvel Cinematic Universe", "Tom Holland", false,
            E_WEB, "#1E3A6E", "#2E77B0",
            List.of("playful", "warm", "energetic"),
            "There's a {vibe}, ready-for-anything grin in there — the kind of smile that turns an "
                + "ordinary Tuesday into a friendly-neighborhood adventure.",
            List.of(
                new Movie("Spider-Man: Homecoming", 315635, "/c24sv2weTHPsmDa7jEMN0m2P3RT.jpg", 2017,
                    List.of("playful", "energetic", "warm"), "All heart and high-fives — it grins the whole way through."),
                new Movie("Spider-Man: Far From Home", 429617, "/4q2NNj4S5dG2RLF9CpXsej7yXl.jpg", 2019,
                    List.of("playful", "warm"), "A sunny road-trip caper with jokes to spare — pure serotonin."),
                new Movie("Spider-Man: No Way Home", 634649, "/1g0dhYtq4irTY1GPXvft6k4YLjm.jpg", 2021,
                    List.of("warm", "energetic"), "The one that makes an entire theatre cheer at the same moment."))),

        new Character("iron-man", "Iron Man", "Marvel Cinematic Universe", "Robert Downey Jr.", false,
            E_ARM, "#23406B", "#4C84B8",
            List.of("confident", "clever", "charming"),
            "That's a {vibe} smirk with the receipts to back it up — effortlessly self-assured and "
                + "always three quips ahead of the room.",
            List.of(
                new Movie("Iron Man", 1726, "/fbeJ7f0aD4A112Bc1tnpzyn82xO.jpg", 2008,
                    List.of("confident", "clever"), "The blueprint for cool — swagger with a soul."),
                new Movie("The Avengers", 24428, "/RYMX2wcKCBAr24UyPD7xwmjaTn.jpg", 2012,
                    List.of("confident", "charming"), "Snappy, stylish and quotable in every single scene."),
                new Movie("Avengers: Endgame", 299534, "/ulzhLuWrPK07P1YkdWQLZnQh1JL.jpg", 2019,
                    List.of("confident", "warm"), "Big feelings, bigger heroics — it earns every cheer."))),

        new Character("wednesday-addams", "Wednesday Addams", "Wednesday", "Jenna Ortega", false,
            E_HEART, "#161F35", "#3B3D68",
            List.of("mysterious", "cool", "clever", "intense"),
            "Yours is a {vibe}, unbothered half-smile — the kind that says very little out loud and "
                + "implies a great deal underneath.",
            List.of(
                new Movie("Wednesday", 119051, "/9PFonBhy4cQy7Jz20NpMygczOkv.jpg", 2022,
                    List.of("mysterious", "clever", "cool"), "Deadpan, gothic and secretly hilarious."),
                new Movie("Beetlejuice Beetlejuice", 917496, "/ilsvBAZHOp91z8J7QVjedr0EokL.jpg", 2024,
                    List.of("mysterious", "playful"), "Spooky-season fun with a wicked wink."))),

        new Character("harry-potter", "Harry Potter", "Wizarding World", "Daniel Radcliffe", false,
            E_BOLT, "#1B2E52", "#43689A",
            List.of("curious", "warm", "clever"),
            "There's a {vibe}, wide-eyed wonder to your smile — like you've just spotted a door that "
                + "shouldn't exist and absolutely cannot wait to open it.",
            List.of(
                new Movie("Harry Potter and the Philosopher's Stone", 671, "/wuMc08IPKEatf9rnMNXvIDxqP4W.jpg", 2001,
                    List.of("curious", "warm"), "The one where a whole magical world clicks open."),
                new Movie("Harry Potter and the Prisoner of Azkaban", 673, "/aWxwnYoe8p2d2fcxOqtvAtJ72Rw.jpg", 2004,
                    List.of("mysterious", "curious"), "Moody, magical and endlessly rewatchable."),
                new Movie("Harry Potter and the Deathly Hallows: Part 2", 12445, "/c54HpQmuwXjHq2C9wmoACjxoom3.jpg", 2011,
                    List.of("confident", "warm"), "The victory-lap finale — goosebumps guaranteed."))),

        new Character("hermione-granger", "Hermione Granger", "Wizarding World", "Emma Watson", false,
            E_BOOKS, "#203352", "#4F7DAB",
            List.of("clever", "confident", "warm"),
            "That's a {vibe} smile that's already read three chapters ahead — quietly brilliant, and "
                + "generous enough to share the notes.",
            List.of(
                new Movie("Harry Potter and the Chamber of Secrets", 672, "/sdEOH0992YZ0QSxgXNIGLq1ToUi.jpg", 2002,
                    List.of("clever", "curious"), "Puzzle-box magic, solved with a raised eyebrow."),
                new Movie("Harry Potter and the Goblet of Fire", 674, "/fECBtHlr0RB3foNHDiCBXeg9Bv9.jpg", 2005,
                    List.of("confident", "warm"), "Big stakes, bigger heart."))),

        new Character("star-lord", "Star-Lord", "Guardians of the Galaxy", "Chris Pratt", false,
            E_PHONES, "#133147", "#2C7F92",
            List.of("playful", "charming", "chaotic"),
            "Yours is a {vibe}, mixtape-ready grin — equal parts swagger and goofball, and completely "
                + "impossible not to root for.",
            List.of(
                new Movie("Guardians of the Galaxy", 118340, "/r7vmZjiyZw9rpJMQJdXpjgiCOk9.jpg", 2014,
                    List.of("playful", "charming"), "A needle-drop classic with an enormous heart."),
                new Movie("Guardians of the Galaxy Vol. 2", 283995, "/y4MBh0EjBlMuOzv9axM4qJlmhzz.jpg", 2017,
                    List.of("playful", "warm"), "Colourful, funny and surprisingly tender."),
                new Movie("Guardians of the Galaxy Vol. 3", 447365, "/r2J02Z2OpNTctfOSN1Ydgii51I3.jpg", 2023,
                    List.of("warm", "energetic"), "The send-off that hugs you goodbye."))),

        new Character("steve-harrington", "Steve Harrington", "Stranger Things", "Joe Keery", false,
            E_SHADES, "#1C3556", "#4076AA",
            List.of("charming", "warm", "cool"),
            "That's a {vibe}, hair-flip smile that started out too-cool-for-school and quietly became the "
                + "most dependable one in the whole room.",
            List.of(
                new Movie("Stranger Things", 66732, "/uOOtwVbSr4QDjAGIifLDwpb2Pdl.jpg", 2016,
                    List.of("charming", "warm"), "'80s nostalgia, big scares and even bigger friendships."),
                new Movie("Stranger Things 4", 66732, "/uOOtwVbSr4QDjAGIifLDwpb2Pdl.jpg", 2022,
                    List.of("intense", "warm"), "Its most cinematic, edge-of-your-seat season yet."))),

        new Character("eleven", "Eleven", "Stranger Things", "Millie Bobby Brown", false,
            E_WAFFLE, "#17283F", "#365C82",
            List.of("mysterious", "intense", "warm"),
            "Behind that {vibe} smile is a quiet force — soft-spoken, fiercely loyal and absolutely "
                + "not to be underestimated.",
            List.of(
                new Movie("Stranger Things", 66732, "/uOOtwVbSr4QDjAGIifLDwpb2Pdl.jpg", 2016,
                    List.of("mysterious", "warm"), "The beating heart of the whole adventure."),
                new Movie("Stranger Things 4", 66732, "/uOOtwVbSr4QDjAGIifLDwpb2Pdl.jpg", 2022,
                    List.of("intense", "mysterious"), "Powers, payoffs and pure momentum."))),

        new Character("munna-bhai", "Munna Bhai", "Munnabhai", "Sanjay Dutt", false,
            E_STETH, "#1A3350", "#487BA2",
            List.of("warm", "charming", "playful"),
            "Yours is a big-hearted, {vibe} smile that could fix a bad mood faster than any prescription "
                + "— the original jadoo ki jhappi.",
            List.of(
                new Movie("Munna Bhai M.B.B.S.", 55774, "/jEpNKcCWBtWnqiIl1OfiqVAqpIn.jpg", 2003,
                    List.of("warm", "playful"), "The comedy that prescribes hugs over pills."),
                new Movie("Lage Raho Munna Bhai", 55775, "/pLLQNsPmzjcHsPYdECDk0fx2Lei.jpg", 2006,
                    List.of("warm", "charming"), "Gentle, funny and quietly wise."))),

        new Character("geet", "Geet", "Jab We Met", "Kareena Kapoor", false,
            E_TRAIN, "#223150", "#587BA1",
            List.of("radiant", "energetic", "playful", "warm"),
            "That's an unstoppable, {vibe} smile that talks a mile a minute and leaves everyone around it "
                + "happier — main character of your own story, and proud of it.",
            List.of(
                new Movie("Jab We Met", 14072, "/m8x6I2qf3R98HtF4DmJXcdxCU64.jpg", 2007,
                    List.of("radiant", "warm"), "A sunny, big-hearted journey you'll want to rewind."))),

        new Character("po", "Po", "Kung Fu Panda", "Jack Black", true,
            E_PANDA, "#1B3448", "#31738A",
            List.of("playful", "warm", "chaotic", "energetic"),
            "Yours is a {vibe}, noodle-loving grin — clumsy in the best possible way and secretly "
                + "capable of absolutely legendary things.",
            List.of(
                new Movie("Kung Fu Panda", 9502, "/xoYc0RYKSc3xC4S9OpPZxKocKtj.jpg", 2008,
                    List.of("playful", "energetic"), "Underdog joy with killer comic timing."),
                new Movie("Kung Fu Panda 2", 49444, "/mtqqD00vB4PGRt20gWtGqFhrkd0.jpg", 2011,
                    List.of("warm", "energetic"), "Funnier, bigger and surprisingly moving."),
                new Movie("Kung Fu Panda 3", 140300, "/oajNi4Su39WAByHI6EONu8G8HYn.jpg", 2016,
                    List.of("playful", "warm"), "Colourful, cosy and full of heart."))),

        new Character("elsa", "Elsa", "Frozen", "Idina Menzel", true,
            E_SNOW, "#1C3A5C", "#5D9EC7",
            List.of("mysterious", "confident", "cool", "radiant"),
            "That's a {vibe} smile that took a little while to let loose — and now that it's finally "
                + "out in the open, there's no holding it back.",
            List.of(
                new Movie("Frozen", 109445, "/2DdKGQYzE6D7pSvvHvM9FRzTh5r.jpg", 2013,
                    List.of("confident", "radiant"), "The one everyone still knows every word to."),
                new Movie("Frozen II", 330457, "/mINJaa34MtknCYl5AjtNJzWj8cD.jpg", 2019,
                    List.of("mysterious", "confident"), "Bigger, bolder and beautifully sung."))),

        new Character("moana", "Moana", "Moana", "Auli'i Cravalho", true,
            E_WAVE, "#123645", "#2B849C",
            List.of("radiant", "energetic", "confident", "warm"),
            "Yours is a {vibe}, horizon-chasing smile — the kind that hears a calling from somewhere "
                + "past the reef and simply goes.",
            List.of(
                new Movie("Moana", 277834, "/jjlmK4jA7XCFztNlmYkO4u0Mqv4.jpg", 2016,
                    List.of("radiant", "energetic"), "Gorgeous, rousing and impossible not to sing along to."),
                new Movie("Moana 2", 1241982, "/aLVkiINlIeCkcZIzb7XHzPYgO6L.jpg", 2024,
                    List.of("energetic", "warm"), "More ocean, more heart, more adventure."))),

        new Character("jack-sparrow", "Jack Sparrow", "Pirates of the Caribbean", "Johnny Depp", false,
            E_COMPASS, "#1E3148", "#406E87",
            List.of("chaotic", "playful", "mysterious", "charming"),
            "That's a {vibe}, why-is-he-grinning smile — always improvising, somehow always coming "
                + "out ahead. Savvy?",
            List.of(
                new Movie("Pirates of the Caribbean: The Curse of the Black Pearl", 22, "/zRBaZxS5YauLvRYjAdL4AUCwlht.jpg", 2003,
                    List.of("playful", "mysterious"), "The swashbuckler that made rum look cool."),
                new Movie("Pirates of the Caribbean: Dead Man's Chest", 58, "/uXEqmloGyP7UXAiphJUu2v2pcuE.jpg", 2006,
                    List.of("chaotic", "playful"), "Bigger seas, wilder escapes."))),

        new Character("loki", "Loki", "Marvel Cinematic Universe", "Tom Hiddleston", false,
            E_SNAKE, "#182B45", "#31717D",
            List.of("mysterious", "clever", "charming", "chaotic", "cool"),
            "Yours is a {vibe}, cat-that-got-the-cream smile — silver-tongued, endlessly entertaining "
                + "and always holding one more card than everybody else.",
            List.of(
                new Movie("Thor", 10195, "/1fGblAmaE2wU6ts2A83eWBgkmHs.jpg", 2011,
                    List.of("clever", "mysterious"), "The one that introduced everyone's favourite trickster."),
                new Movie("The Avengers", 24428, "/RYMX2wcKCBAr24UyPD7xwmjaTn.jpg", 2012,
                    List.of("confident", "mysterious"), "A gleefully theatrical villain turn."),
                new Movie("Loki", 84958, "/kEl2t3OhXc3Zb9FBh1AuYzRTgZp.jpg", 2021,
                    List.of("mysterious", "clever"), "Time-bending, stylish and full of twists."))),

        new Character("merida", "Merida", "Brave", "Kelly Macdonald", true,
            E_BOW, "#1F3352", "#4181A3",
            List.of("confident", "energetic", "playful", "radiant"),
            "Yours is a {vibe}, wind-in-your-hair smile — bold, free-spirited and more than ready to "
                + "change your own fate.",
            List.of(
                new Movie("Brave", 62177, null, 2012,
                    List.of("confident", "energetic"), "A fierce, funny, beautifully Scottish adventure.")))
    );

    /** The shared, immutable roster. Safe to read concurrently. */
    public static List<Character> all() {
        return ALL;
    }
}
