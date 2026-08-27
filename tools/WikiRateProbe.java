import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Compares HTTP/2 vs HTTP/1.1 status codes against the MediaWiki search endpoint. */
public class WikiRateProbe {
    public static void main(String[] args) throws Exception {
        run(HttpClient.Version.HTTP_2);
        Thread.sleep(3000);
        run(HttpClient.Version.HTTP_1_1);
    }

    static void run(HttpClient.Version v) throws Exception {
        HttpClient c = HttpClient.newBuilder().version(v)
                .connectTimeout(Duration.ofSeconds(4)).build();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            HttpRequest req = HttpRequest.newBuilder(URI.create(
                    "https://en.wikipedia.org/w/api.php?action=query&format=json&list=search&srlimit=3"
                            + "&srsearch=probe" + v + i + Math.random()))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "CaptureSmile/1.0 (character-image lookup; educational project)")
                    .GET().build();
            HttpResponse<Void> r = c.send(req, HttpResponse.BodyHandlers.discarding());
            sb.append(r.statusCode()).append("(").append(r.version()).append(") ");
        }
        System.out.println(v + " → " + sb);
    }
}
