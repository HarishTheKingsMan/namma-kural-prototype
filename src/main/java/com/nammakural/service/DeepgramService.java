package com.nammakural.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Slf4j
@Service
public class DeepgramService {

    // ⚠️ REPLACE WITH YOUR REAL KEY (Keep "Token " prefix)
    private final String DEEPGRAM_API_KEY = "Token YOUR_DEEPGRAM_API_KEY";
    // Simplified URL to rule out model issues
    private final String WS_URL = "wss://api.deepgram.com/v1/listen?encoding=mulaw&sample_rate=8000&model=nova-2";

    private final ObjectMapper mapper = new ObjectMapper();

    public WebSocket connect(Consumer<String> onTranscriptReceived) {
        HttpClient client = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();

        return client.newWebSocketBuilder()
                .header("Authorization", DEEPGRAM_API_KEY)
                .buildAsync(URI.create(WS_URL), new WebSocket.Listener() {

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        log.info("🟢 Deepgram WebSocket OPENED");
                        WebSocket.Listener.super.onOpen(webSocket);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        try {
                            JsonNode root = mapper.readTree(data.toString());

                            // 1. Check for Metadata (Connection success)
                            if (root.has("type") && root.get("type").asText().equals("Metadata")) {
                                log.info("✅ Deepgram Metadata Received (Connection Healthy)");
                            }

                            // 2. Check for Transcript
                            if (root.has("channel")) {
                                String transcript = root.path("channel").path("alternatives").get(0).path("transcript").asText();
                                if (!transcript.isBlank()) {
                                    log.info("👂 HEARD: " + transcript);
                                    if (root.path("is_final").asBoolean()) {
                                        onTranscriptReceived.accept(transcript);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.error("JSON Error", e);
                        }
                        return WebSocket.Listener.super.onText(webSocket, data, last);
                    }

                    // 👇 FIXED SIGNATURE: Returns CompletionStage<?> instead of void
                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        log.error("🔴 Deepgram Closed! Code: " + statusCode + ", Reason: " + reason);
                        return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        log.error("🔴 Deepgram Network Error", error);
                    }
                })
                .join();
    }
}