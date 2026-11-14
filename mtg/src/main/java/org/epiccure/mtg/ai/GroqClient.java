package org.epiccure.mtg.ai;

import org.epiccure.mtg.ai.AiModels.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Free-tier Groq endpoint (OpenAI-compatible) serving an open-weight Llama model. JSON mode is forced with
 * response_format=json_object; the whole call is bounded by groq.timeout-ms so a weak warehouse signal or a slow
 * model never blocks the shopper (the caller falls back to deterministic recipes).
 */
@Component
public class GroqClient {

    private static final String URL = "https://api.groq.com/openai/v1/chat/completions";

    private final String apiKey;
    private final String model;
    private final long timeoutMs;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public GroqClient(@Value("${groq.api.key:}") String apiKey,
                      @Value("${groq.model:llama-3.1-8b-instant}") String model,
                      @Value("${groq.timeout-ms:3000}") long timeoutMs,
                      ObjectMapper json) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.timeoutMs = timeoutMs;
        this.json = json;
    }

    public String model() { return model; }

    public boolean configured() { return !apiKey.isEmpty() && !apiKey.equalsIgnoreCase("placeholder"); }

    /** @return the assistant message content (expected to be a JSON object string). */
    public String chatJson(String system, String user) {
        if (!configured()) throw new AiUnavailableException("NO_KEY", "GROQ_API_KEY is not set");
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("model", model);
        payload.put("temperature", 0.3);
        payload.put("max_tokens", 3000);
        payload.put("response_format", Map.of("type", "json_object"));
        payload.put("messages", List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", user)));
        if (model.startsWith("openai/gpt-oss")) payload.put("reasoning_effort", "low");   // keep thinking short so the JSON fits
        String body = json.writeValueAsString(payload);

        HttpRequest req = HttpRequest.newBuilder(URI.create(URL))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new AiUnavailableException("TIMEOUT", "Groq did not answer within " + timeoutMs + " ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("TIMEOUT", "Interrupted");
        } catch (Exception e) {
            throw new AiUnavailableException("HTTP_ERROR", "Network error: " + e.getMessage());
        }
        if (resp.statusCode() == 429) throw new AiUnavailableException("RATE_LIMITED", "Groq free-tier rate limit hit");
        if (resp.statusCode() != 200) throw new AiUnavailableException("HTTP_ERROR", "Groq HTTP " + resp.statusCode());
        try {
            ChatResponse cr = json.readValue(resp.body(), ChatResponse.class);
            String content = cr.choices().get(0).message().content();
            if (content == null || content.isBlank()) throw new IllegalStateException("empty content");
            return content;
        } catch (Exception e) {
            throw new AiUnavailableException("INVALID_JSON", "Unexpected Groq response shape");
        }
    }
}
