package org.epiccure.mtg.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.epiccure.mtg.api.ApiModels.LocationView;

import java.util.List;

public final class AiModels {
    private AiModels() {}

    // ---- what we ask the LLM to return (validated before anything reaches the UI) ----
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiIngredient(Long productId, String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiRecipe(String title, List<AiIngredient> ingredients, List<String> steps, String whyItFits) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiRecipes(List<AiRecipe> recipes) {}

    // ---- OpenAI-compatible chat completion response (Groq) ----
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatResponse(List<Choice> choices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(Message message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String role, String content) {}

    // ---- what the shopper UI receives ----
    /** inCart=false means it's a "missing ingredient": location tells the shopper where to get it. */
    public record RecipeIngredientView(Long productId, String name, boolean inCart, String flag, LocationView location) {}

    public record RecipeView(String title, List<RecipeIngredientView> ingredients, List<String> steps, String whyItFits) {}

    /**
     * source = AI or FALLBACK. failure says why the fallback was used (NO_KEY, TIMEOUT, RATE_LIMITED, HTTP_ERROR,
     * INVALID_JSON, ALL_REJECTED) and is null when the AI succeeded. restrictedCaught counts AI recipes that tried
     * to use a restricted item and were blocked by the validator.
     */
    public record RecipesView(String source, String model, long latencyMs, boolean cached, List<RecipeView> recipes,
                              int aiRawRecipes, int rejected, int restrictedCaught, List<String> rejections,
                              String failure, String note) {
        public RecipesView withCached(boolean c) {
            return new RecipesView(source, model, latencyMs, c, recipes, aiRawRecipes, rejected, restrictedCaught, rejections, failure, note);
        }
    }

    // ---- verification harness ----
    public record HarnessCase(String persona, List<String> dietFocus, String source, String failure, int servedRecipes,
                              int aiRawRecipes, int rejected, int restrictedCaught, int servedRestrictedViolations,
                              int servedOutOfStock, long latencyMs) {}

    public record HarnessReport(int runs, int aiServed, int fallbacks, int invalidJson, int aiRawRecipes, int rejected,
                                int restrictedCaughtByValidator, int servedRestrictedViolations, int servedOutOfStock,
                                long avgLatencyMs, long maxLatencyMs, boolean passed, List<HarnessCase> cases) {}
}
