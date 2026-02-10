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
public class ElevenLabsService {

    // 👇 PASTE YOUR NEW KEY HERE
// Replace real key:
    private final String API_KEY = "YOUR_ELEVENLABS_API_KEY";
    // VOICE ID:
    // "nPczCjzI2devNBz1zQrb" is 'Brian' (Standard American).
    // "JBFqnCBsd6RMkjVDRZzb" is 'George' (British).
    // You can find Indian Voice IDs in the ElevenLabs VoiceLab.
    private final String VOICE_ID = "nPczCjzI2devNBz1zQrb";
//            "OUBMjq0LvBjb07bhwD3H";

    // Twilio needs ULAW 8000Hz format
    private final String TTS_URL = "https://api.elevenlabs.io/v1/text-to-speech/" + VOICE_ID + "?output_format=ulaw_8000";

    private final HttpClient client;

    public ElevenLabsService() {
        // Use Virtual Threads for high concurrency
        this.client = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public byte[] generateAudio(String text) {
        try {
            // "eleven_turbo_v2_5" is their fastest, lowest latency model
            String jsonBody = String.format("""
                {
                    "text": "%s",
                    "model_id": "eleven_turbo_v2_5",
                    "voice_settings": {
                        "stability": 0.5,
                        "similarity_boost": 0.7
                    }
                }
                """, escape(text));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TTS_URL))
                    .header("xi-api-key", API_KEY) // Header for ElevenLabs
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            log.info("👄 Generating ElevenLabs Audio...");
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                log.error("ElevenLabs Error: " + response.statusCode());
                // log.error(new String(response.body())); // Uncomment to see error details
                return new byte[0];
            }

            return response.body();

        } catch (Exception e) {
            log.error("ElevenLabs Exception", e);
            return new byte[0];
        }
    }

    private String escape(String text) {
        // Simple JSON escaping
        return text.replace("\"", "\\\"").replace("\n", " ");
    }
}