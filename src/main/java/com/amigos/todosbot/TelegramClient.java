package com.amigos.todosbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

public final class TelegramClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final String baseUrl;

    public TelegramClient(String token) {
        this.baseUrl = "https://api.telegram.org/bot" + token + "/";
    }

    public JsonNode getUpdates(long offset) throws IOException, InterruptedException {
        return call("getUpdates", Map.of(
                "timeout", "30",
                "offset", String.valueOf(offset),
                "allowed_updates", "[\"message\"]"), Duration.ofSeconds(45));
    }

    public void sendMessage(long chatId, String html) throws IOException, InterruptedException {
        call("sendMessage", Map.of(
                "chat_id", String.valueOf(chatId),
                "parse_mode", "HTML",
                "text", html), Duration.ofSeconds(15));
    }

    private JsonNode call(String method, Map<String, String> params, Duration timeout)
            throws IOException, InterruptedException {
        var body = params.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
        var request = HttpRequest.newBuilder(URI.create(baseUrl + method))
                .timeout(timeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        var json = MAPPER.readTree(response.body());
        if (!json.path("ok").asBoolean()) {
            throw new IOException("Telegram rechazó " + method + ": " + json.path("description").asText());
        }
        return json.path("result");
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}