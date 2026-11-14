package org.epiccure.mtg.ai;

/** The LLM couldn't give us a usable answer in time. reason is one of the RecipesView.failure codes. */
public class AiUnavailableException extends RuntimeException {
    private final String reason;

    public AiUnavailableException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    public String reason() { return reason; }
}
