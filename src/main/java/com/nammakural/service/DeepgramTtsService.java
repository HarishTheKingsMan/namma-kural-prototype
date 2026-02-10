package com.nammakural.service;

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
public class DeepgramTtsService {

    // TODO: Move to application.yml
    private final String API_KEY = "Token 9da22f2581e8e6b387a96df8c625667bc3ff5352";

    // URL explanation:
    // model=aura-asteria-en  -> A friendly female voice (fastest)
    // encoding=mulaw         -> The format Twilio needs (CRITICAL)
    // sample_rate=8000       -> The speed Twilio needs
    // container=none         -> Raw bytes, no file headers
    private final String TTS_URL = "https://api.deepgram.com/v1/speak?model=aura-orion-en&encoding=mulaw&sample_rate=8000&container=none";
    private final HttpClient client;

    public DeepgramTtsService() {
        this.client = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    /**
     * Converts text to audio bytes compatible with Twilio.
     */
    public byte[] generateAudio(String text) {
        try {
            String jsonBody = String.format("{\"text\": \"%s\"}", escape(text));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TTS_URL))
                    .header("Authorization", API_KEY)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            // Send request to Deepgram
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                log.error("TTS Error: " + response.statusCode());
                return new byte[0];
            }

            return response.body();

        } catch (Exception e) {
            log.error("TTS Exception", e);
            return new byte[0];
        }
    }

    private String escape(String text) {
        return text.replace("\"", "\\\"").replace("\n", " ");
    }
}