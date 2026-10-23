package com.example.saga.it;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Minimal JSON-over-HTTP client for the tests (no checked exceptions, so it can be used inside lambdas). */
final class Api {

    static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    record Response(int status, JsonNode body, HttpHeaders headers) {
        String header(String name) {
            return headers.firstValue(name).orElse(null);
        }
    }

    private Api() {
    }

    static Response get(String url) {
        return send(HttpRequest.newBuilder(URI.create(url)).GET());
    }

    static Response post(String url, Map<String, String> headers, JsonNode body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(write(body)));
        headers.forEach(builder::header);
        return send(builder);
    }

    static Response put(String url, JsonNode body) {
        return send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(write(body))));
    }

    private static Response send(HttpRequest.Builder builder) {
        try {
            HttpResponse<String> response = HTTP.send(builder.timeout(Duration.ofSeconds(20)).build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode body = response.body() == null || response.body().isBlank()
                    ? JSON.missingNode()
                    : JSON.readTree(response.body());
            return new Response(response.statusCode(), body, response.headers());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", ex);
        }
    }

    private static String write(JsonNode node) {
        try {
            return JSON.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
