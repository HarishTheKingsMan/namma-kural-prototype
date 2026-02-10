package com.nammakural.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class GroqLlmService implements LlmService {

    private final ObjectMapper mapper;

    // 👇 MAKE SURE THIS KEY IS CORRECT (No spaces, starts with gsk_)
    // Replace the real key with this:
    private final String apiKey = "YOUR_GROQ_API_KEY";
    private final String endpoint = "https://api.groq.com/openai/v1/chat/completions";

    // Set this to FALSE to use Real AI
    private final boolean TEST_MODE = false;

    private final HttpClient client = HttpClient.newBuilder()
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public GroqLlmService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String generateResponse(String userText) {
        if (TEST_MODE) {
            return "Test Mode: Vanakkam! (Update API Key)";
        }

        try {
            // 1. Create JSON Payload
            // Using a simple string format to avoid JSON object complexity errors
            String systemPrompt = "You are Raja, a helpful Tamil friend. Speak Tanglish. Keep it short.";

            String jsonBody = String.format("""
                {
                    "model": "llama-3.3-70b-versatile", 
                    "messages": [
                        {"role": "system", "content": "%s"},
                        {"role": "user", "content": "%s"}
                    ],
                    "temperature": 0.7,
                    "max_tokens": 100
                }
                """, escape(systemPrompt), escape(userText));

            // 2. Build Request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            // 3. Send Request
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // 👇 CRITICAL DEBUG LOGS: This will tell us why it failed
            if (response.statusCode() != 200) {
                log.error("🔴 GROQ ERROR CODE: " + response.statusCode());
                log.error("🔴 GROQ ERROR BODY: " + response.body());
                return "Aiyyo, Brain Error " + response.statusCode();
            }

            // 4. Parse Response
            JsonNode root = mapper.readTree(response.body());
            return root.path("choices").get(0).path("message").path("content").asText();

        } catch (Exception e) {
            log.error("AI Brain Failed", e);
            return "Aiyyo, network issue machi.";
        }
    }

    private String escape(String input) {
        if (input == null) return "";
        return input.replace("\"", "\\\"").replace("\n", " ");
    }
}