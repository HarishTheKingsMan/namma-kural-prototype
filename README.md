🎙️ Namma Kural (நம்ம குரல்) - AI Voice Assistant

Bridging the Digital Divide with Voice. > A latency-optimized, "Tanglish"-speaking AI agent accessible via standard telephony.





[suspicious link removed]

💡 The Problem

Millions of users in Tier-2/3 India cannot use text-based AI (ChatGPT) due to literacy barriers and language complexity. Existing voice assistants (Alexa/Siri) struggle with "Tanglish" (Tamil-English code-mixing) and lack cultural context.

🚀 The Solution: "No App" Required

Namma Kural is a Voice-First AI Agent.

Zero UI: Users just dial a phone number.

Hyper-Local: Understands "Machi, enakku oru doubt" naturally.

Real-Time: Sub-second latency using Java Virtual Threads and Groq LPUs.

🏗️ Architecture

The system follows an Event-Driven, Stream-Processing architecture to minimize latency.

graph TD
    User((User)) -- "GSM/VoIP Call" --> Twilio[Twilio Telephony]
    Twilio -- "Mulaw Audio Stream (WebSocket)" --> Java[<b>Java Spring Boot</b>\n(Orchestrator)]
    
    subgraph "The AI Pipeline (Latency < 1.5s)"
        Java -- "Stream Audio" --> Ears[<b>Deepgram</b>\n(Nova-2 STT)]
        Ears -- "Transcript" --> Java
        Java -- "Context + Prompt" --> Brain[<b>Groq</b>\n(Llama 3.3 70B)]
        Brain -- "Tanglish Response" --> Java
        Java -- "Text" --> Mouth[<b>ElevenLabs</b>\n(Turbo v2.5 TTS)]
        Mouth -- "ULAW Audio" --> Java
    end
    
    Java -- "Audio Stream" --> Twilio
    Twilio -- "Voice" --> User


🛠️ Tech Stack

Component

Technology

Why?

Core Backend

Java 21 + Spring Boot 3

Uses Virtual Threads (Project Loom) to handle 1000s of concurrent calls efficiently.

Telephony

Twilio (or Exotel)

Handles the PSTN/VoIP connection via WebSockets.

Speech-to-Text

Deepgram Nova-2

Fastest transcription; handles Indian accents accurately.

Intelligence

Llama 3.3 (via Groq)

Runs at 500 tokens/sec for instant "human-like" thinking speed.

Text-to-Speech

ElevenLabs Turbo v2.5

Provides emotional, non-robotic voices (Indian English supported).

⚡ Quick Start

1. Prerequisites

Java 21 SDK

Maven

Ngrok (For local testing)

2. Clone & Config

git clone [https://github.com/YOUR_USERNAME/namma-kural-prototype.git](https://github.com/YOUR_USERNAME/namma-kural-prototype.git)
cd namma-kural-prototype


3. API Keys

Security Note: Keys are removed from the code. You must add them in the Service files or set them as Environment Variables.

GroqLlmService.java -> Add GROQ_API_KEY

DeepgramService.java -> Add DEEPGRAM_API_KEY

ElevenLabsService.java -> Add ELEVENLABS_API_KEY

4. Run

mvn spring-boot:run


5. Connect Phone

Run ngrok http 8080.

Configure Twilio TwiML Bin:

<Response>
    <Connect>
        <Stream url="wss://YOUR-NGROK-URL/voice-stream" />
    </Connect>
</Response>


Call the number!

🔮 Roadmap (For Investors)

Memory Layer: Implement Vector Database (PGVector) to remember user history across calls.

Regional Dialects: Fine-tune for Madurai/Coimbatore Tamil slangs.

B2B Integration: Allow businesses to plug in their own inventory/support data.

📄 License

Private Prototype. All rights reserved.
