package org.epiccure.mtg.ai;

import org.epiccure.mtg.ai.AiModels.AiIngredient;
import org.epiccure.mtg.ai.AiModels.AiRecipe;
import org.epiccure.mtg.ai.AiModels.AiRecipes;

import java.util.*;

/**
 * Deterministic gate between the probabilistic model and the shopper. A recipe is dropped whole if it:
 * references a restricted product, references a product we didn't offer (unknown / out of stock / hallucinated),
 * has an ingredient with no id, uses fewer than 2 listed items, has no steps, or (when the cart isn't empty)
 * doesn't use anything already in the cart.
 */
public final class RecipeValidator {

    public static final int MAX_RECIPES = 3;

    public record Result(List<AiRecipe> valid, List<String> rejections, int restrictedCaught) {}

    private RecipeValidator() {}

    /** Models sometimes wrap JSON in ```json fences or add a sentence around it: keep only the outer object. */
    public static String stripFences(String raw) {
        if (raw == null) return "";
        String s = raw.trim().replaceAll("(?s)^```[a-zA-Z]*\\s*", "").replaceAll("(?s)\\s*```\\s*$", "");
        int start = s.indexOf('{'), end = s.lastIndexOf('}');
        return start >= 0 && end > start ? s.substring(start, end + 1) : s;
    }

    public static Result validate(AiRecipes parsed, Set<Long> allowedIds, Set<Long> cartIds, Set<Long> restrictedIds) {
        List<AiRecipe> valid = new ArrayList<>();
        List<String> rejections = new ArrayList<>();
        int restrictedCaught = 0;
        if (parsed == null || parsed.recipes() == null) {
            rejections.add("Response had no \"recipes\" array");
            return new Result(valid, rejections, 0);
        }
        for (AiRecipe r : parsed.recipes()) {
            String title = r == null || r.title() == null || r.title().isBlank() ? "(untitled)" : r.title().trim();
            String problem = null;
            if (r == null || r.title() == null || r.title().isBlank()) problem = "missing title";
            else if (r.ingredients() == null || r.ingredients().size() < 2) problem = "fewer than 2 ingredients";
            else if (r.steps() == null || r.steps().stream().allMatch(s -> s == null || s.isBlank())) problem = "no steps";
            else {
                boolean usesCart = false;
                for (AiIngredient i : r.ingredients()) {
                    if (i == null || i.productId() == null) { problem = "ingredient without a product id"; break; }
                    if (restrictedIds.contains(i.productId())) {
                        problem = "uses RESTRICTED product " + i.productId();
                        restrictedCaught++;
                        break;
                    }
                    if (!allowedIds.contains(i.productId())) { problem = "uses product " + i.productId() + " that was not offered (unknown or out of stock)"; break; }
                    if (cartIds.contains(i.productId())) usesCart = true;
                }
                if (problem == null && !cartIds.isEmpty() && !usesCart) problem = "doesn't use anything in the cart";
            }
            if (problem != null) rejections.add("Rejected \"" + title + "\": " + problem);
            else if (valid.size() < MAX_RECIPES) valid.add(r);
        }
        return new Result(valid, rejections, restrictedCaught);
    }
}
