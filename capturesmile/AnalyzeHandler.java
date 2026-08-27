package capturesmile;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/**
 * {@code POST /api/analyze} — the request body is the raw image bytes (no base64, no multipart,
 * no JSON to parse on the way in). The image is hashed, analyzed, and then discarded: it is never
 * written to disk and never logged. This is what makes the privacy promise true even for solo use.
 */
public final class AnalyzeHandler implements HttpHandler {

    private static final int MAX_BYTES = 12 * 1024 * 1024; // 12 MB

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                Http.json(ex, 405, JsonView.error("Use POST with the image as the request body."));
                return;
            }
            byte[] image = Http.readBody(ex, MAX_BYTES);
            if (image == null) {
                Http.json(ex, 413, JsonView.error("That image is a bit too large (max 12 MB)."));
                return;
            }
            if (image.length < 16) {
                Http.json(ex, 400, JsonView.error("No image received."));
                return;
            }
            AnalysisResult result = SmileAnalyzer.analyze(image);
            Http.json(ex, 200, JsonView.analysis(result));
            // 'image' goes out of scope here — nothing persisted, nothing logged.
        } catch (Exception e) {
            Http.json(ex, 500, JsonView.error("Analysis hiccuped. Try again."));
        }
    }
}
