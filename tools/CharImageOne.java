import capturesmile.CharacterImages;

import java.lang.reflect.Method;
import java.util.Optional;

/** Traces a single (character, movie, actor) lookup with -Dchars.debug=true. */
public class CharImageOne {
    public static void main(String[] args) throws Exception {
        Method resolve = CharacterImages.class.getDeclaredMethod("resolve", String.class, String.class, String.class);
        resolve.setAccessible(true);
        @SuppressWarnings("unchecked")
        Optional<String> got = (Optional<String>) resolve.invoke(null, args[0], args[1], args.length > 2 ? args[2] : "");
        System.out.println("RESULT: " + got.orElse("null"));
    }
}
