package com.nammakural.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nammakural.service.DeepgramService;
import com.nammakural.service.ElevenLabsService; // <--- NEW IMPORT
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
public class VoiceStreamHandler extends TextWebSocketHandler {

    private final LlmService llmService;
    private final DeepgramService deepgramService;
    private final ElevenLabsService ttsService; // <--- CHANGED: Using ElevenLabs now
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Store active Deepgram connections: StreamSid -> WebSocket
    private final Map<String, WebSocket> deepgramSockets = new ConcurrentHashMap<>();

    // Store Twilio sessions so we can reply later
    private final Map<String, WebSocketSession> twilioSessions = new ConcurrentHashMap<>();

    // Inject ElevenLabsService instead of DeepgramTtsService
    public VoiceStreamHandler(LlmService llmService,
                              DeepgramService deepgramService,
                              ElevenLabsService ttsService) {
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

    private void handleStartEvent(String streamSid) {
        log.info("🚀 Audio Stream Started: " + streamSid);

        // Connect to Deepgram (Ears)
        WebSocket deepgramSocket = deepgramService.connect(transcript -> {
            log.info("👂 Heard: " + transcript);

            // 1. Brain Thinks (Groq)
            String aiReply = llmService.generateResponse(transcript);
            log.info("🤖 AI Brain Says: " + aiReply);

            // 2. Add a tiny human pause (0.5s) for realism
            try { Thread.sleep(500); } catch (Exception e) {}

            // 3. Mouth Speaks (ElevenLabs)
            // This calls the new service to generate high-quality audio
            byte[] audioBytes = ttsService.generateAudio(aiReply);

            // 4. Send Audio back to Twilio
            sendAudioToTwilio(streamSid, audioBytes);
        });

        deepgramSockets.put(streamSid, deepgramSocket);
    }

    private void handleMediaEvent(String streamSid, String base64Payload) {
        WebSocket deepgramSocket = deepgramSockets.get(streamSid);

        if (deepgramSocket != null) {
            // Forward audio bytes from Twilio -> Deepgram
            byte[] audioData = Base64.getDecoder().decode(base64Payload);
            deepgramSocket.sendBinary(ByteBuffer.wrap(audioData), true);
        }
    }

    private void sendAudioToTwilio(String streamSid, byte[] audioBytes) {
        try {
            WebSocketSession session = twilioSessions.get(streamSid);

            // Check if session is valid and we actually have audio
            if (session != null && session.isOpen() && audioBytes.length > 0) {
                String base64Audio = Base64.getEncoder().encodeToString(audioBytes);

                String msg = String.format(
                        "{\"event\":\"media\",\"streamSid\":\"%s\",\"media\":{\"payload\":\"%s\"}}",
                        streamSid, base64Audio
                );

                session.sendMessage(new TextMessage(msg));
            } else {
                log.warn("Cannot send audio, Twilio session closed or audio empty.");
            }
        } catch (IOException e) {
            log.error("Failed to send audio back to Twilio", e);
        }
    }

    private void closeDeepgram(String streamSid) {
        twilioSessions.remove(streamSid);
        WebSocket ws = deepgramSockets.remove(streamSid);
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "Call Ended");
        }
    }
}