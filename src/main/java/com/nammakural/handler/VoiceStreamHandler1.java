package com.nammakural.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nammakural.service.DeepgramService;
import com.nammakural.service.DeepgramTtsService;
import com.nammakural.service.LlmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class VoiceStreamHandler1 extends TextWebSocketHandler {

    private int packetCount = 0;

    private final LlmService llmService;
    private final DeepgramService deepgramService;
    private final DeepgramTtsService ttsService; // Service for Speaking
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Store active Deepgram connections: StreamSid -> WebSocket
    private final Map<String, WebSocket> deepgramSockets = new ConcurrentHashMap<>();

    // Store Twilio sessions so we can reply later: StreamSid -> TwilioSession
    private final Map<String, WebSocketSession> twilioSessions = new ConcurrentHashMap<>();

    // Inject ALL services
    public VoiceStreamHandler1(LlmService llmService,
                               DeepgramService deepgramService,
                               DeepgramTtsService ttsService) {
        this.llmService = llmService;
        this.deepgramService = deepgramService;
        this.ttsService = ttsService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("📞 Twilio Call Connected: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode jsonMessage = objectMapper.readTree(message.getPayload());
            String event = jsonMessage.path("event").asText();

            switch (event) {
                case "start":
                    String streamSid = jsonMessage.path("start").path("streamSid").asText();
                    // CRITICAL: Save the session mapped to the Stream SID so we can talk back
                    twilioSessions.put(streamSid, session);
                    handleStartEvent(streamSid);
                    break;

                case "media":
                    String sid = jsonMessage.path("streamSid").asText();
                    String payload = jsonMessage.path("media").path("payload").asText();
                    handleMediaEvent(sid, payload);
                    break;

                case "stop":
                    String stopSid = jsonMessage.path("streamSid").asText();
                    closeDeepgram(stopSid);
                    break;
            }
        } catch (Exception e) {
            log.error("Error handling message", e);
        }
    }

// ... inside VoiceStreamHandler ...

    private void handleStartEvent(String streamSid) {
        log.info("🚀 Audio Stream Started: " + streamSid);

        WebSocket deepgramSocket = deepgramService.connect(transcript -> {
            log.info("👂 Heard: " + transcript);

            // 1. Brain Thinks
            String aiReply = llmService.generateResponse(transcript);
            log.info("🤖 AI Brain Says: " + aiReply);

            // 👇 ADD THIS BLOCK: The "Thinking" Pause
            try {
                // Wait 1 second before replying (Feels more human)
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // 2. Generate Audio (Mouth)
            byte[] audioBytes = ttsService.generateAudio(aiReply);

            // 3. Send Audio back to Twilio
            sendAudioToTwilio(streamSid, audioBytes);
        });

        deepgramSockets.put(streamSid, deepgramSocket);
    }

    private void handleMediaEvent(String streamSid, String base64Payload) {
        WebSocket deepgramSocket = deepgramSockets.get(streamSid);

        // 👇 DEBUG: Count packets. If this doesn't go up, Twilio is silent.
        packetCount++;
        if (packetCount % 50 == 0) {
            log.info("🎤 Pulse Check: Received " + packetCount + " audio packets from Twilio");
        }

        if (deepgramSocket != null) {
            byte[] audioData = Base64.getDecoder().decode(base64Payload);
            deepgramSocket.sendBinary(ByteBuffer.wrap(audioData), true);
        } else {
            log.warn("⚠️ Audio dropped! Deepgram socket missing for: " + streamSid);
        }
    }

    /**
     * Helper to send Audio back to Twilio.
     * Formats the JSON message Twilio expects.
     */
    private void sendAudioToTwilio(String streamSid, byte[] audioBytes) {
        try {
            WebSocketSession session = twilioSessions.get(streamSid);

            if (session != null && session.isOpen()) {
                // Encode to Base64 for Twilio
                String base64Audio = Base64.getEncoder().encodeToString(audioBytes);

                // Construct JSON message for Twilio
                // Format: { "event": "media", "streamSid": "...", "media": { "payload": "..." } }
                String msg = String.format(
                        "{\"event\":\"media\",\"streamSid\":\"%s\",\"media\":{\"payload\":\"%s\"}}",
                        streamSid, base64Audio
                );

                session.sendMessage(new TextMessage(msg));
            } else {
                log.warn("Cannot send audio, Twilio session is closed or not found for SID: " + streamSid);
            }
        } catch (IOException e) {
            log.error("Failed to send audio back to Twilio", e);
        }
    }

    private void closeDeepgram(String streamSid) {
        twilioSessions.remove(streamSid); // Clean up session map
        WebSocket ws = deepgramSockets.remove(streamSid);
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "Call Ended");
        }
    }
}