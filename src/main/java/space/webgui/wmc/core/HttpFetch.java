package space.webgui.wmc.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** The HTTP conversation both sources have, in one place. */
final class HttpFetch {

    private HttpFetch() {
    }

    static HttpClient client() {
        // Redirects followed because release downloads are almost always a redirect to
        // storage somewhere else.
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Opens {@code uri}, asking to continue from {@code skipBytes} when that is not zero.
     *
     * <p>The timeout is on the response arriving, not on the body: three hundred megabytes
     * takes as long as it takes, and a slow connection is not an error.
     */
    static InputStream open(HttpClient client, URI uri, long skipBytes, String what) throws IOException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(2))
                .GET();
        if (skipBytes > 0) {
            request.header("Range", "bytes=" + skipBytes + "-");
        }

        HttpResponse<InputStream> response;
        try {
            response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while fetching " + what, e);
        }

        int status = response.statusCode();
        if (skipBytes > 0 && status == 200) {
            throw new RangeIgnoredException(response.body());
        }
        if (status != 200 && status != 206) {
            response.body().close();
            throw new IOException("unexpected status " + status + " fetching " + what + " from " + uri);
        }
        return response.body();
    }
}
