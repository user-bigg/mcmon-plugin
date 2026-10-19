package ru.mcmon.plugin.core;

import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocketFactory;

public final class HttpSiteClient implements SiteClient {

    public static final String LOOKUP_PATH = "/api/plugin/v1/players/lookup";
    public static final String BANS_PATH = "/api/plugin/v1/bans";
    static final int MAX_BODY_BYTES = 64 * 1024;
    static final int MAX_BANS_BODY_BYTES = 4 * 1024 * 1024;
    static final Duration BANS_TIMEOUT = Duration.ofSeconds(10);
    static final Duration GRACE = Duration.ofMillis(500);

    private final ExecutorService executor;
    private final URL lookupUrl;
    private final URL bansUrl;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private final Duration timeout;
    private final SSLSocketFactory tls;
    private final SiteBackoff backoff = new SiteBackoff();

    public HttpSiteClient(Settings settings, String pluginVersion) {
        this.executor = Executors.newCachedThreadPool(new DaemonThreads());
        this.timeout = settings.timeout();
        this.lookupUrl = url(settings, LOOKUP_PATH);
        this.bansUrl = url(settings, BANS_PATH);
        this.tls = defaultTls();
        header("Authorization", "Bearer " + settings.serverKey());
        header("Content-Type", "application/json");
        header("Accept", "application/json");
        header("User-Agent", "MCMonitor-plugin/" + pluginVersion);
    }

    private static URL url(Settings settings, String path) {
        try {
            return URI.create(settings.siteUrl() + path).toURL();
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new SettingsException("site-url cannot be used for requests, copy it again");
        }
    }

    // Other mods may replace the JVM-wide HTTPS defaults with trust-all ones; this client keeps the JDK's own checks.
    private static SSLSocketFactory defaultTls() {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, null, null);
            return context.getSocketFactory();
        } catch (GeneralSecurityException e) {
            throw new SettingsException(
                    "this Java cannot open https connections (" + e.getClass().getName() + ")");
        }
    }

    private void header(String name, String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c > 0x7E) {
                throw new SettingsException(
                        "server-key or site-url contains characters that cannot be sent, copy them again");
            }
        }
        headers.put(name, value);
    }

    @Override
    public CompletableFuture<Lookup> lookup(String nick, LaunchCode code) {
        Optional<LookupOutcome> paused = backoff.paused();
        if (paused.isPresent()) {
            return CompletableFuture.completedFuture(Lookup.of(paused.get()));
        }
        byte[] body = body(nick, code).getBytes(StandardCharsets.UTF_8);
        return send(new Request(lookupUrl, "POST", body, timeout, MAX_BODY_BYTES))
                .handleAsync((reply, error) -> error == null ? parse(reply, nick) : failure(error), executor);
    }

    @Override
    public CompletableFuture<BansFetch> bans() {
        return send(new Request(bansUrl, "GET", null, BANS_TIMEOUT, MAX_BANS_BODY_BYTES))
                .handleAsync(
                        (reply, error) -> error == null ? parseBans(reply) : BansFetch.failed(failureOutcome(error)),
                        executor);
    }

    @Override
    public Executor executor() {
        return executor;
    }

    @Override
    public void shutdown() {
        executor.shutdownNow();
    }

    static String body(String nick, LaunchCode code) {
        JsonObject body = new JsonObject();
        body.addProperty("nick", nick);
        body.addProperty("code", code.value());
        return body.toString();
    }

    private CompletableFuture<Answer> send(Request request) {
        CompletableFuture<Answer> result = new CompletableFuture<>();
        AtomicReference<HttpURLConnection> open = new AtomicReference<>();
        // Closing a TLS connection can block, so the shared timer thread only settles the future.
        ScheduledFuture<?> cutoff = Futures.after(request.timeout.plus(GRACE), () -> {
            if (result.completeExceptionally(new TimeoutException("no answer from the site in time"))) {
                closeQuietly(open);
            }
        });
        try {
            executor.execute(() -> {
                try {
                    result.complete(exchange(request, open));
                } catch (IOException | RuntimeException e) {
                    result.completeExceptionally(e);
                } finally {
                    cutoff.cancel(false);
                }
            });
        } catch (RejectedExecutionException e) {
            cutoff.cancel(false);
            return CompletableFuture.failedFuture(e);
        }
        return result;
    }

    private void closeQuietly(AtomicReference<HttpURLConnection> open) {
        HttpURLConnection connection = open.get();
        if (connection == null) {
            return;
        }
        try {
            executor.execute(connection::disconnect);
        } catch (RejectedExecutionException ignored) {
            // The client is shut down; the request thread closes the connection itself when it gives up.
        }
    }

    private Answer exchange(Request request, AtomicReference<HttpURLConnection> open) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) request.url.openConnection();
        open.set(connection);
        try {
            if (connection instanceof HttpsURLConnection https) {
                https.setSSLSocketFactory(tls);
                https.setHostnameVerifier((host, session) -> false);
            }
            connection.setRequestMethod(request.method);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            int millis = (int) Math.min(Integer.MAX_VALUE, request.timeout.toMillis());
            connection.setConnectTimeout(millis);
            connection.setReadTimeout(millis);
            headers.forEach(connection::setRequestProperty);
            if (request.body != null) {
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(request.body.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(request.body);
                }
            }
            int status = connection.getResponseCode();
            if (connection.getContentLengthLong() > request.maxBytes) {
                throw new TooLargeException();
            }
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            byte[] body = stream == null ? new byte[0] : readLimited(stream, request.maxBytes);
            return new Answer(status, body, retryAfter(connection));
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readLimited(InputStream stream, int maxBytes) throws IOException {
        try (InputStream in = stream) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (bytes.size() + read > maxBytes) {
                    throw new TooLargeException();
                }
                bytes.write(buffer, 0, read);
            }
            return bytes.toByteArray();
        }
    }

    private static Optional<Duration> retryAfter(HttpURLConnection connection) {
        String value = connection.getHeaderField("Retry-After");
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Duration.ofSeconds(Long.parseLong(value.trim())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Lookup parse(Answer reply, String nick) {
        Lookup lookup = LookupResponse.parse(reply.status, new String(reply.body, StandardCharsets.UTF_8), nick);
        return backoff.record(lookup, reply.retryAfter);
    }

    private static BansFetch parseBans(Answer reply) {
        if (reply.status == 200) {
            Optional<BanList> list = BanList.parse(new String(reply.body, StandardCharsets.UTF_8));
            return list.isPresent() ? BansFetch.ok(list.get()) : BansFetch.failed(LookupOutcome.BAD_RESPONSE);
        }
        if (reply.status == 401) {
            return BansFetch.failed(LookupOutcome.INVALID_KEY);
        }
        if (reply.status == 429) {
            return BansFetch.failed(LookupOutcome.RATE_LIMITED);
        }
        return BansFetch.failed(reply.status >= 500 ? LookupOutcome.SITE_ERROR : LookupOutcome.BAD_RESPONSE);
    }

    private Lookup failure(Throwable error) {
        return backoff.record(Lookup.of(failureOutcome(error)), Optional.empty());
    }

    private static LookupOutcome failureOutcome(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        if (cause instanceof TooLargeException) {
            return LookupOutcome.BAD_RESPONSE;
        }
        if (cause instanceof SSLException) {
            return LookupOutcome.CERTIFICATE_REJECTED;
        }
        if (cause instanceof SocketTimeoutException
                || cause instanceof TimeoutException
                || cause instanceof CancellationException) {
            return LookupOutcome.TIMEOUT;
        }
        return LookupOutcome.UNREACHABLE;
    }

    private static final class Request {

        private final URL url;
        private final String method;
        private final byte[] body;
        private final Duration timeout;
        private final int maxBytes;

        Request(URL url, String method, byte[] body, Duration timeout, int maxBytes) {
            this.url = url;
            this.method = method;
            this.body = body;
            this.timeout = timeout;
            this.maxBytes = maxBytes;
        }
    }

    private static final class Answer {

        private final int status;
        private final byte[] body;
        private final Optional<Duration> retryAfter;

        Answer(int status, byte[] body, Optional<Duration> retryAfter) {
            this.status = status;
            this.body = body;
            this.retryAfter = retryAfter;
        }
    }

    static final class TooLargeException extends RuntimeException {

        TooLargeException() {
            super("response body is larger than allowed");
        }
    }

    private static final class DaemonThreads implements ThreadFactory {

        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "mcmonitor-http-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
