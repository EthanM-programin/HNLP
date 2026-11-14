package org.epiccure.mtg.ai;

import org.epiccure.mtg.ai.AiModels.*;
import org.epiccure.mtg.model.Product;
import org.epiccure.mtg.rules.Flag;
import org.epiccure.mtg.rules.FlagResult;
import org.epiccure.mtg.rules.Ruleset;
import org.epiccure.mtg.rules.RulesEngine;
import org.epiccure.mtg.service.StockService;
import org.epiccure.mtg.service.StockService.FloorStock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * US-3.3 In-Cart Recipe Synthesis. The rules engine decides what is safe BEFORE the model sees anything:
 *  - the prompt only contains non-restricted cart items + the top safe, in-stock (GREEN, floor stock > 0) items;
 *  - no diagnoses or identifiers are sent, only a plain-language diet focus;
 *  - every returned recipe is re-checked by RecipeValidator against those ids and the restricted set;
 *  - bins/locations come from our database, never from the model.
 * Any failure (no key, timeout, 429, bad JSON, everything rejected) falls back to FallbackRecipes.
 */
@Component
public class RecipeEngine {

    private static final Logger log = LoggerFactory.getLogger(RecipeEngine.class);
    private static final int OFFERED = 20;

    static final String SYSTEM_PROMPT = """
        You are a recipe assistant for a medically tailored food pantry. Clients have chronic conditions, so you must follow these rules exactly:
        1. Use ONLY products from the CART and AVAILABLE lists, referenced by their numeric id. Never invent products or ids.
        2. Water, black pepper, and salt-free dried herbs/spices may be used freely and are NOT listed as ingredients.
        3. Never add salt, sugar, butter, or any other ingredient that is not listed.
        4. Every recipe must use at least one CART product, and 2 to 6 listed products in total.
        5. Respect the diet focus you are given.
        Respond with a single JSON object and nothing else (no markdown), exactly in this shape:
        {"recipes":[{"title":"string","ingredients":[{"productId":123,"name":"string"}],"steps":["string"],"whyItFits":"one sentence"}]}
        Return 1 to 3 recipes with 3 to 6 short steps each.
        """;

    private final GroqClient groq;
    private final RulesEngine engine;
    private final ObjectMapper json;

    public RecipeEngine(GroqClient groq, RulesEngine engine, ObjectMapper json) {
        this.groq = groq;
        this.engine = engine;
        this.json = json;
    }

    public RecipesView generate(Ruleset r, List<Product> cartProducts, Map<Long, FloorStock> floor, List<Product> catalog) {
        long start = System.currentTimeMillis();
        Map<Long, FlagResult> flags = new HashMap<>();
        Set<Long> restricted = new HashSet<>();
        for (Product p : catalog) {
            FlagResult f = engine.evaluate(p, r);
            flags.put(p.getId(), f);
            if (f.flag() == Flag.RED) restricted.add(p.getId());
        }
        for (Product p : cartProducts) flags.computeIfAbsent(p.getId(), k -> engine.evaluate(p, r));

        List<Product> cart = new ArrayList<>();
        for (Product p : cartProducts) if (flags.get(p.getId()).flag() != Flag.RED) cart.add(p);
        Set<Long> cartIds = new HashSet<>();
        for (Product p : cart) cartIds.add(p.getId());
        List<Product> offered = offered(catalog, flags, floor, cartIds);

        Map<Long, Product> byId = new HashMap<>();
        for (Product p : catalog) byId.put(p.getId(), p);
        for (Product p : cartProducts) byId.put(p.getId(), p);
        Set<Long> allowed = new HashSet<>(cartIds);
        for (Product p : offered) allowed.add(p.getId());

        String failure = null, note;
        List<String> rejections = new ArrayList<>();
        int raw = 0, caught = 0;
        try {
            String content = groq.chatJson(SYSTEM_PROMPT, userPrompt(r, cart, offered));
            AiRecipes parsed;
            try {
                parsed = json.readValue(RecipeValidator.stripFences(content), AiRecipes.class);
            } catch (Exception e) {
                throw new AiUnavailableException("INVALID_JSON", "Model output was not valid JSON for the recipe schema");
            }
            raw = parsed.recipes() == null ? 0 : parsed.recipes().size();
            RecipeValidator.Result v = RecipeValidator.validate(parsed, allowed, cartIds, restricted);
            rejections.addAll(v.rejections());
            caught = v.restrictedCaught();
            if (!v.valid().isEmpty()) {
                List<RecipeView> views = new ArrayList<>();
                for (AiRecipe ar : v.valid()) {
                    List<RecipeIngredientView> ing = new ArrayList<>();
                    Set<Long> seen = new HashSet<>();
                    for (AiIngredient i : ar.ingredients()) {
                        if (seen.add(i.productId())) ing.add(ingredient(byId.get(i.productId()), cartIds, flags, floor));
                    }
                    views.add(new RecipeView(ar.title().trim(), ing, ar.steps(), ar.whyItFits()));
                }
                return new RecipesView("AI", groq.model(), System.currentTimeMillis() - start, false, views, raw,
                        rejections.size(), caught, rejections, null, null);
            }
            failure = "ALL_REJECTED";
            note = "The AI suggestions didn't pass our safety checks, so these are standard recipes.";
        } catch (AiUnavailableException e) {
            failure = e.reason();
            note = "Showing standard recipes (" + e.getMessage() + ").";
            log.info("Recipe fallback: {} - {}", e.reason(), e.getMessage());
        }

        List<RecipeView> views = new ArrayList<>();
        for (FallbackRecipes.Built b : FallbackRecipes.build(cart, offered, RecipeValidator.MAX_RECIPES)) {
            List<RecipeIngredientView> ing = new ArrayList<>();
            for (Product p : b.ingredients()) ing.add(ingredient(p, cartIds, flags, floor));
            views.add(new RecipeView(b.title(), ing, b.steps(), b.whyItFits()));
        }
        return new RecipesView("FALLBACK", groq.model(), System.currentTimeMillis() - start, false, views, raw,
                rejections.size(), caught, rejections, failure, note);
    }

    /** Top safe in-stock items, round-robin across categories so the model sees a balanced pantry. */
    List<Product> offered(List<Product> catalog, Map<Long, FlagResult> flags, Map<Long, FloorStock> floor, Set<Long> exclude) {
        Map<String, Deque<Product>> byCat = new TreeMap<>();
        List<Product> sorted = new ArrayList<>(catalog);
        sorted.sort(Comparator.comparingDouble(p -> flags.get(p.getId()).maxRatio()));
        for (Product p : sorted) {
            FloorStock fs = floor.get(p.getId());
            if (exclude.contains(p.getId()) || fs == null || fs.quantity() <= 0) continue;
            if (flags.get(p.getId()).flag() != Flag.GREEN) continue;
            byCat.computeIfAbsent(p.getCategory(), k -> new ArrayDeque<>()).add(p);
        }
        List<Product> out = new ArrayList<>();
        while (out.size() < OFFERED && byCat.values().stream().anyMatch(d -> !d.isEmpty())) {
            for (Deque<Product> d : byCat.values()) if (!d.isEmpty() && out.size() < OFFERED) out.add(d.poll());
        }
        return out;
    }

    static String userPrompt(Ruleset r, List<Product> cart, List<Product> offered) {
        StringBuilder sb = new StringBuilder("Diet focus: ").append(String.join("; ", r.dietFocus())).append("\n\nCART (already selected):\n");
        if (cart.isEmpty()) sb.append("(empty - suggest starter recipes from AVAILABLE)\n");
        for (Product p : cart) sb.append("- id ").append(p.getId()).append(": ").append(p.getName()).append(" (").append(p.getCategory()).append(")\n");
        sb.append("\nAVAILABLE in the warehouse (may be suggested as missing ingredients):\n");
        for (Product p : offered) sb.append("- id ").append(p.getId()).append(": ").append(p.getName()).append(" (").append(p.getCategory()).append(")\n");
        return sb.toString();
    }

    private static RecipeIngredientView ingredient(Product p, Set<Long> cartIds, Map<Long, FlagResult> flags, Map<Long, FloorStock> floor) {
        boolean inCart = cartIds.contains(p.getId());
        FloorStock fs = floor.get(p.getId());
        return new RecipeIngredientView(p.getId(), p.getName(), inCart, flags.get(p.getId()).flag().name(),
                inCart || fs == null ? null : StockService.view(fs.location()));
    }
}
