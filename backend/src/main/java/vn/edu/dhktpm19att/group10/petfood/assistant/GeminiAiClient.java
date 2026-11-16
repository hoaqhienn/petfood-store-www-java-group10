package vn.edu.dhktpm19att.group10.petfood.assistant;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Gemini API over REST (generativelanguage.googleapis.com, v1beta). The API key stays in the backend and is
 * sent only in the x-goog-api-key header, never in a URL or log.
 */
@Component
class GeminiAiClient implements AiClient {
    private final boolean enabled;
    private final String chatModel;
    private final String embeddingModel;
    private final RestClient http;
    private final ObjectMapper json;

    GeminiAiClient(ObjectMapper json,
            @Value("${app.ai.enabled:false}") boolean enabled,
            @Value("${app.ai.api-key:}") String apiKey,
            @Value("${app.ai.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
            @Value("${app.ai.chat-model:gemini-3.8-flash}") String chatModel,
            @Value("${app.ai.embedding-model:gemini-embedding-001}") String embeddingModel,
            @Value("${app.ai.timeout-seconds:15}") int timeoutSeconds) {
        this.json = json;
        this.enabled = enabled && !apiKey.isBlank();
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(5));
        requests.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(requests)
                .defaultHeader("x-goog-api-key", apiKey).build();
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public String embeddingModel() {
        return embeddingModel + "@" + DIMENSIONS;
    }

    @Override
    public float[] embed(String text, Purpose purpose) {
        requireEnabled();
        var body = Map.of(
                "model", "models/" + embeddingModel,
                "content", Map.of("parts", List.of(Map.of("text", text))),
                "taskType", purpose == Purpose.QUERY ? "RETRIEVAL_QUERY" : "RETRIEVAL_DOCUMENT",
                "outputDimensionality", DIMENSIONS);
        JsonNode values = post("/models/" + embeddingModel + ":embedContent", body).path("embedding").path("values");
        if (!values.isArray() || values.size() != DIMENSIONS) {
            throw new Unavailable("Embedding có số chiều không đúng " + DIMENSIONS + ".");
        }
        float[] vector = new float[DIMENSIONS];
        double norm = 0;
        for (int i = 0; i < DIMENSIONS; i++) {
            vector[i] = (float) values.get(i).asDouble();
            norm += vector[i] * vector[i];
        }
        // gemini-embedding-001 only normalises full 3072-dimension vectors; shortened ones need it here.
        if (norm == 0) throw new Unavailable("Embedding rỗng.");
        double length = Math.sqrt(norm);
        for (int i = 0; i < DIMENSIONS; i++) vector[i] = (float) (vector[i] / length);
        return vector;
    }

    @Override
    public JsonNode answerJson(String systemInstruction, String userMessage, JsonNode schema) {
        requireEnabled();
        var body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userMessage)))),
                "generationConfig", Map.of("responseMimeType", "application/json", "responseJsonSchema", schema));
        var parts = post("/models/" + chatModel + ":generateContent", body)
                .path("candidates").path(0).path("content").path("parts");
        var text = new StringBuilder();
        for (JsonNode part : parts) text.append(part.path("text").asText(""));
        try {
            return json.readTree(text.toString());
        } catch (JsonProcessingException invalid) {
            throw new Unavailable("Model trả về JSON không hợp lệ.", invalid);
        }
    }

    private JsonNode post(String path, Object body) {
        try {
            JsonNode response = http.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(JsonNode.class);
            if (response == null) throw new Unavailable("Gemini không trả dữ liệu.");
            return response;
        } catch (RestClientException failure) {
            // Covers timeouts, 429 quota and 5xx; the message never includes the key.
            throw new Unavailable("Gemini tạm thời không phản hồi.", failure);
        }
    }

    private void requireEnabled() {
        if (!enabled) throw new Unavailable("AI đang tắt hoặc chưa có API key.");
    }
}
