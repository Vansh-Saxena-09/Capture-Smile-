package capturesmile;

import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Small helpers for writing responses and safely reading request bodies. */
final class Http {
    private Http() {}

    static void json(HttpExchange ex, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, body.length);           // exact UTF-8 byte length
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    static void bytes(HttpExchange ex, int status, String contentType, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream os = ex.getResponseBody()) { if (body.length > 0) os.write(body); }
    }

    static void text(HttpExchange ex, int status, String message) throws IOException {
        bytes(ex, status, "text/plain; charset=utf-8", message.getBytes(StandardCharsets.UTF_8));
    }

    /** Reads the whole request body but refuses to buffer more than {@code maxBytes} (returns null). */
    static byte[] readBody(HttpExchange ex, int maxBytes) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int total = 0, n;
            while ((n = in.read(chunk)) != -1) {
                total += n;
                if (total > maxBytes) return null;
                buf.write(chunk, 0, n);
            }
            return buf.toByteArray();
        }
    }
}
