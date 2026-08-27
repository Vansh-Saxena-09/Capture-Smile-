import capturesmile.CharacterImages;
import capturesmile.Character;
import capturesmile.Catalog;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Exercises the character-image lookup against the live Wikipedia API and prints what each
 * character resolved to. Temporary harness — the project has no test framework.
 *
 * <pre>
 *   javac -encoding UTF-8 -cp out -d out-tools tools/CharImageTest.java
 *   java -Dchars.debug=true -cp "out;out-tools" CharImageTest
 * </pre>
 */
public class CharImageTest {

    public static void main(String[] args) throws Exception {
        Method resolve = CharacterImages.class.getDeclaredMethod("resolve", String.class, String.class, String.class);
        resolve.setAccessible(true);

        int found = 0;
        System.out.println("=== character image resolution (live) ===");
        for (Character c : Catalog.all()) {
            String movie = c.movies().isEmpty() ? c.universe() : c.movies().get(0).title();
            @SuppressWarnings("unchecked")
            Optional<String> got = (Optional<String>) resolve.invoke(null, c.name(), movie, c.actor());
            if (got.isPresent()) found++;
            System.out.printf("%-20s %-28s %s%n", c.name(), "(" + movie + ")", got.orElse("— none, plate stands"));
        }
        System.out.println("=== " + found + "/" + Catalog.all().size() + " resolved ===");
    }
}
