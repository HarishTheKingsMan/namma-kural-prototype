package com.nammakural.service;

public interface LlmService {
    /**
     * Takes user input (e.g., "Enna machi news?")
     * Returns AI response (e.g., "Onnum illa pa, nee eppadi irukka?")
     */
    String generateResponse(String userText);
}