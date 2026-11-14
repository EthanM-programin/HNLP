package org.epiccure.mtg.ai;

import org.epiccure.mtg.model.Product;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FallbackRecipesTest {

    static Product p(long id, String name, String category) {
        Product x = new Product();
        x.setId(id);
        x.setName(name);
        x.setCategory(category);
        return x;
    }

    @Test
    void buildsRecipeFromCartAndFillsMissingFromPantry() {
        List<Product> cart = List.of(p(77, "Chicken Breast (Boneless)", "Protein"));
        List<Product> pantry = List.of(p(5, "Broccoli Crowns", "Produce"), p(10, "Bell Peppers (Red)", "Produce"),
                p(68, "Extra Virgin Olive Oil", "Pantry"), p(51, "Whole Grain Brown Rice", "Pantry"));
        var out = FallbackRecipes.build(cart, pantry, 3);
        assertFalse(out.isEmpty());
        assertTrue(out.get(0).ingredients().contains(cart.get(0)), "best recipe uses the cart item");
    }

    @Test
    void neverUsesItemsOutsideTheInputs() {
        List<Product> cart = List.of(p(26, "Low-Sodium Canned Black Beans", "Canned Goods"));
        List<Product> pantry = List.of(p(53, "Organic Quinoa", "Pantry"), p(5, "Broccoli Crowns", "Produce"), p(2, "Roma Tomatoes", "Produce"));
        for (var r : FallbackRecipes.build(cart, pantry, 3)) {
            for (Product i : r.ingredients()) assertTrue(cart.contains(i) || pantry.contains(i));
        }
    }
}
