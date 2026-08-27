package capturesmile;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code POST /api/showdown} — computes the Smile Showdown for a squad.
 *
 * <p>To keep the app parser-free, the body is a tiny tab-delimited text format (one friend per
 * line), NOT JSON:
 * <pre>name \t score \t matchPct \t vibe \t characterName \t d0,d1,d2,d3,d4</pre>
 * where d0..d4 are the five {@link SmileAnalyzer#DIMENSIONS} in order. Crucially this carries no
 * photo — only the already-derived numbers — so the server never sees a face.
 */
public final class ShowdownHandler implements HttpHandler {

    private static final int MAX_BYTES = 256 * 1024;

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                Http.json(ex, 405, JsonView.error("Use POST."));
                return;
            }
            byte[] body = Http.readBody(ex, MAX_BYTES);
            if (body == null) {
                Http.json(ex, 413, JsonView.error("Too much squad data."));
                return;
            }
            List<FriendSummary> friends = parse(new String(body, StandardCharsets.UTF_8));
            if (friends.isEmpty()) {
                Http.json(ex, 400, JsonView.error("No friends provided."));
                return;
            }
            Http.json(ex, 200, JsonView.showdown(Showdown.compute(friends)));
        } catch (Exception e) {
            Http.json(ex, 500, JsonView.error("Showdown hiccuped. Try again."));
        }
    }

    private static List<FriendSummary> parse(String text) {
        List<FriendSummary> list = new ArrayList<>();
        for (String line : text.split("\n")) {
            String ln = line.strip();
            if (ln.isEmpty()) continue;
            String[] f = ln.split("\t", -1);
            if (f.length < 6) continue;
            String name = f[0].strip();
            if (name.isEmpty()) name = "Friend " + (list.size() + 1);
            double score = parseD(f[1], 7.0);
            int matchPct = (int) Math.round(parseD(f[2], 80));
            String vibe = f[3].strip();
            String charName = f[4].strip();
            Map<String, Double> dims = new LinkedHashMap<>();
            String[] ds = f[5].split(",", -1);
            for (int i = 0; i < SmileAnalyzer.DIMENSIONS.size(); i++) {
                dims.put(SmileAnalyzer.DIMENSIONS.get(i), i < ds.length ? parseD(ds[i], 0.0) : 0.0);
            }
            list.add(new FriendSummary(name, score, matchPct, vibe, charName, dims));
        }
        return list;
    }

    private static double parseD(String s, double dflt) {
        try {
            return Double.parseDouble(s.strip());
        } catch (Exception e) {
            return dflt;
        }
    }
}
