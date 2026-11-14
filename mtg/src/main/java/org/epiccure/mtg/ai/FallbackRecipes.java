package org.epiccure.mtg.ai;

import org.epiccure.mtg.model.Product;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Deterministic recipe templates used whenever the LLM is slow, rate-limited, returns bad JSON, or every AI recipe
 * fails validation. Slots are filled from the cart first, then from safe in-stock items (the "missing ingredients").
 * Inputs are already filtered to non-restricted products, so a fallback recipe can't contain a RED item either.
 */
public final class FallbackRecipes {

    private FallbackRecipes() {}

    public record Built(String title, List<Product> ingredients, List<String> steps, String whyItFits) {}

    private record Slot(String category, Pattern name) {
        boolean fits(Product p) {
            return p.getCategory().equalsIgnoreCase(category) && (name == null || name.matcher(p.getName()).find());
        }
    }

    private record Template(String title, List<Slot> slots, List<String> steps, String why) {}

    private static Slot slot(String category, String regex) {
        return new Slot(category, regex == null ? null : Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
    }

    private static final String VEG = "spinach|broccoli|zucchini|pepper|carrot|cabbage|cauliflower|kale|asparagus|green beans|mushroom|onion|brussels|celery";
    private static final String FRUIT = "berr|banana|apple|orange|peach|pear|mandarin";

    private static final List<Template> TEMPLATES = List.of(
        new Template("Sheet-Pan Protein & Vegetables",
            List.of(slot("Protein", null), slot("Produce", VEG), slot("Produce", VEG), slot("Pantry", "oil")),
            List.of("Heat the oven to 425°F (220°C).", "Cut the vegetables into even pieces and toss with a little oil and salt-free herbs.",
                    "Spread the vegetables and protein on a sheet pan in one layer.", "Roast 20-25 minutes until the protein is cooked through."),
            "One pan, lean protein, and plenty of vegetables with no added salt."),
        new Template("Bean & Whole-Grain Power Bowl",
            List.of(slot("Canned Goods", "bean|chickpea|lentil"), slot("Pantry", "rice|quinoa|barley|bulgur|millet|amaranth"), slot("Produce", VEG), slot("Produce", null)),
            List.of("Cook the grain following the package directions.", "Rinse and drain the canned beans to lower sodium further.",
                    "Chop the vegetables; sauté or serve raw.", "Layer grain, beans, and vegetables in a bowl."),
            "Fiber-rich grains and beans help keep blood sugar steady."),
        new Template("Hearty Vegetable Soup",
            List.of(slot("Canned Goods", "broth"), slot("Produce", VEG), slot("Produce", VEG), slot("Canned Goods", "tomato|bean|peas|corn|carrot")),
            List.of("Chop the vegetables.", "Simmer the broth, vegetables, and canned item for 20 minutes.", "Season with pepper and salt-free herbs."),
            "Low-sodium broth and vegetables make a filling, heart-friendly meal."),
        new Template("Overnight Oats with Fruit",
            List.of(slot("Pantry", "oat"), slot("Produce", FRUIT), slot("Dairy", null)),
            List.of("Stir the oats into the milk or yogurt in a jar.", "Top with chopped fruit.", "Refrigerate overnight and eat cold or warmed."),
            "Whole-grain oats and fruit for a slow-release breakfast."),
        new Template("Quick Stir-Fry over Grains",
            List.of(slot("Protein", null), slot("Produce", VEG), slot("Produce", VEG), slot("Pantry", "rice|quinoa|millet")),
            List.of("Cook the grain.", "Slice the protein and vegetables thinly.", "Stir-fry the protein, then add vegetables until crisp-tender.", "Serve over the grain."),
            "Balanced plate: lean protein, vegetables, and a whole grain.")
    );

    /**
     * @param cart     non-restricted items already in the cart
     * @param pantry   safe in-stock items the shopper could add, best first
     */
    public static List<Built> build(List<Product> cart, List<Product> pantry, int max) {
        record Scored(Built b, int fromCart) {}
        List<Scored> scored = new ArrayList<>();
        for (Template t : TEMPLATES) {
            List<Product> used = new ArrayList<>();
            int fromCart = 0;
            boolean complete = true;
            for (Slot s : t.slots()) {
                Product pick = first(cart, s, used);
                if (pick != null) fromCart++;
                else pick = first(pantry, s, used);
                if (pick == null) { complete = false; break; }
                used.add(pick);
            }
            if (complete && (cart.isEmpty() || fromCart > 0)) {
                scored.add(new Scored(new Built(t.title(), used, t.steps(), t.why()), fromCart));
            }
        }
        scored.sort(Comparator.comparingInt((Scored s) -> s.fromCart()).reversed());
        List<Built> out = new ArrayList<>();
        for (Scored s : scored) if (out.size() < max) out.add(s.b());
        return out;
    }

    private static Product first(List<Product> from, Slot s, List<Product> used) {
        for (Product p : from) if (s.fits(p) && !used.contains(p)) return p;
        return null;
    }
}
