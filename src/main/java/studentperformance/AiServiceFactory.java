package studentperformance;

import java.util.Locale;

/**
 * Factory for creating configured AI services.
 * Allows switching between Google Gemini (primary/free tier) and OpenAI via
 * environment variable or system property AI_PROVIDER ("gemini" or "openai").
 * Defaults to Google Gemini when not explicitly set.
 */
public final class AiServiceFactory {

    private AiServiceFactory() {}

    /**
     * Creates an AiService instance based on the current configuration.
     */
    public static AiService createService() {
        String provider = EnvConfig.get("AI_PROVIDER");

        if (provider != null && !provider.isBlank()) {
            String clean = provider.trim().toLowerCase(Locale.ROOT);
            if (clean.equals("openai")) {
                return new OpenAIService();
            } else if (clean.equals("gemini") || clean.equals("google")) {
                return new GeminiService();
            }
        }

        // Auto-detection strategy:
        // Prioritize Gemini as requested
        GeminiService gemini = new GeminiService();
        if (gemini.isConfigured()) {
            return gemini;
        }

        OpenAIService openAi = new OpenAIService();
        if (openAi.isConfigured()) {
            return openAi;
        }

        // Default to Google Gemini
        return gemini;
    }
}
