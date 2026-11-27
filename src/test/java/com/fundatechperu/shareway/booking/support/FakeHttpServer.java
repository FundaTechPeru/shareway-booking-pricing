package com.fundatechperu.shareway.booking.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Simulated remote service (Payments or Trip Execution) on a random local port. Every request
 * is recorded; the answer of a path is the next queued one, or the path's default.
 */
public final class FakeHttpServer implements AutoCloseable {

    /** One received request. */
    public record Request(String method, String path, String apiKey, String body) {
    }

    /** One answer. */
    public record Response(int status, String body) {
    }

    private final HttpServer server;
    private final List<Request> requests = new ArrayList<>();
    private final Map<String, Deque<Response>> queued = new ConcurrentHashMap<>();
    private final Map<String, Function<Request, Response>> defaults = new ConcurrentHashMap<>();

    public FakeHttpServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException exception) {
            throw new IllegalStateException("The simulated server could not start", exception);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    public String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /** Answer used for a path when nothing is queued for it (200 with an empty object otherwise). */
    public void respondByDefault(String path, Function<Request, Response> answer) {
        defaults.put(path, answer);
    }

    /** Answers queued for the next requests to the path, in order. */
    public void enqueue(String path, int status, String body) {
        queued.computeIfAbsent(path, key -> new ArrayDeque<>()).add(new Response(status, body));
    }

    public synchronized List<Request> requests() {
        return List.copyOf(requests);
    }

    public synchronized List<Request> requestsTo(String path) {
        return requests.stream().filter(request -> request.path().equals(path)).toList();
    }

    public synchronized void reset() {
        requests.clear();
        queued.clear();
        defaults.clear();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Request request = new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("X-Internal-Api-Key"), body);
        synchronized (this) {
            requests.add(request);
        }
        Deque<Response> next = queued.get(request.path());
        Response response = next != null && !next.isEmpty() ? next.poll()
                : defaults.getOrDefault(request.path(), ignored -> new Response(200, "{}")).apply(request);
        byte[] bytes = response.body() == null ? new byte[0] : response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
