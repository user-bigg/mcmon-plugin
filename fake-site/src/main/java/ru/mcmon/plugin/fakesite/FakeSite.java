package ru.mcmon.plugin.fakesite;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

public final class FakeSite implements AutoCloseable {

    public static final String LOOKUP_PATH = "/api/plugin/v1/players/lookup";
    public static final String BANS_PATH = "/api/plugin/v1/bans";

    private final HttpServer server;
    private final String key;
    private final List<RecordedRequest> requests = Collections.synchronizedList(new ArrayList<>());
    private volatile Function<RecordedRequest, Reply> policy = request -> Reply.notConfirmed();
    private volatile Reply bansReply = Reply.bans("2026-01-01T00:00:00Z");

    private FakeSite(HttpServer server, String key) {
        this.server = server;
        this.key = key;
    }

    public static FakeSite start(String key) throws IOException {
        return start(key, 0, InetAddress.getLoopbackAddress());
    }

    public static FakeSite start(String key, int port, InetAddress bind) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        FakeSite site = new FakeSite(server, key);
        server.createContext("/", site::handle);
        server.setExecutor(Executors.newCachedThreadPool(FakeSite::daemonThread));
        server.start();
        return site;
    }

    public URI baseUrl() {
        return URI.create("http://" + server.getAddress().getHostString() + ":" + port());
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void replyWith(Reply reply) {
        this.policy = request -> reply;
    }

    public void replyWith(Function<RecordedRequest, Reply> policy) {
        this.policy = policy;
    }

    public void replyToBansWith(Reply reply) {
        this.bansReply = reply;
    }

    public List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    public List<RecordedRequest> lookups() {
        return requests.stream().filter(r -> r.path().equals(LOOKUP_PATH)).toList();
    }

    public void clearRequests() {
        requests.clear();
    }

    public RecordedRequest lastRequest() {
        synchronized (requests) {
            return requests.get(requests.size() - 1);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        ((ExecutorService) server.getExecutor()).shutdownNow();
    }

    private static Thread daemonThread(Runnable task) {
        Thread thread = new Thread(task, "fake-site");
        thread.setDaemon(true);
        return thread;
    }

    private void handle(HttpExchange exchange) throws IOException {
        RecordedRequest request = RecordedRequest.of(exchange);
        requests.add(request);
        try {
            Reply reply = route(request);
            if (!reply.delay().isZero()) {
                Thread.sleep(reply.delay().toMillis());
            }
            if (reply.dropConnection()) {
                exchange.close();
                return;
            }
            send(exchange, reply);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            exchange.close();
        }
    }

    private Reply route(RecordedRequest request) {
        if (key != null && !("Bearer " + key).equals(request.authorization())) {
            return Reply.status(401, "{\"error\":\"unauthenticated\"}");
        }
        if (request.path().equals(BANS_PATH) && request.method().equals("GET")) {
            return bansReply;
        }
        if (!request.path().equals(LOOKUP_PATH) || !request.method().equals("POST")) {
            return Reply.status(404, "{\"error\":\"not_found\"}");
        }
        if (request.json() == null || request.nick() == null || request.code() == null) {
            return Reply.status(422, "{\"error\":\"invalid_request\"}");
        }
        return policy.apply(request);
    }

    private static void send(HttpExchange exchange, Reply reply) throws IOException {
        byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", reply.contentType());
        reply.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
        exchange.sendResponseHeaders(reply.status(), bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
