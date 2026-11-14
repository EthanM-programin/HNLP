package org.epiccure.mtg.ai;

import org.epiccure.mtg.ai.AiModels.AiIngredient;
import org.epiccure.mtg.ai.AiModels.AiRecipe;
import org.epiccure.mtg.ai.AiModels.AiRecipes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The LLM's output never reaches a shopper without passing these checks. */
class RecipeValidatorTest {

    static AiRecipe recipe(String title, Long... ids) {
        List<AiIngredient> ing = java.util.Arrays.stream(ids).map(id -> new AiIngredient(id, "item " + id)).toList();
        return new AiRecipe(title, ing, List.of("Cook it."), "Fits.");
    }

    final Set<Long> allowed = Set.of(1L, 2L, 3L, 4L);
    final Set<Long> cart = Set.of(1L, 2L);
    final Set<Long> restricted = Set.of(99L);

    @Test
    void acceptsRecipeUsingOnlyOfferedItems() {
        var r = RecipeValidator.validate(new AiRecipes(List.of(recipe("Bowl", 1L, 3L))), allowed, cart, restricted);
        assertEquals(1, r.valid().size());
        assertTrue(r.rejections().isEmpty());
    }

    @Test
    void rejectsAndCountsRestrictedItem() {
        var r = RecipeValidator.validate(new AiRecipes(List.of(recipe("Bad", 1L, 99L))), allowed, cart, restricted);
        assertTrue(r.valid().isEmpty());
        assertEquals(1, r.restrictedCaught());
    }

    @Test
    void rejectsHallucinatedOrOutOfStockIds() {
        var r = RecipeValidator.validate(new AiRecipes(List.of(recipe("Ghost", 1L, 42L))), allowed, cart, restricted);
        assertTrue(r.valid().isEmpty());
        assertTrue(r.rejections().get(0).contains("not offered"));
    }

    @Test
    void rejectsRecipeThatIgnoresTheCart() {
        var r = RecipeValidator.validate(new AiRecipes(List.of(recipe("No cart", 3L, 4L))), allowed, cart, restricted);
        assertTrue(r.valid().isEmpty());
    }

    @Test
    void rejectsIngredientWithoutId() {
        AiRecipe r0 = new AiRecipe("Salted", List.of(new AiIngredient(1L, "beans"), new AiIngredient(null, "salt")), List.of("Mix"), "x");
        assertTrue(RecipeValidator.validate(new AiRecipes(List.of(r0)), allowed, cart, restricted).valid().isEmpty());
    }

    @Test
    void handlesMissingRecipesArray() {
        var r = RecipeValidator.validate(new AiRecipes(null), allowed, cart, restricted);
        assertTrue(r.valid().isEmpty());
        assertFalse(r.rejections().isEmpty());
    }

    @Test
    void stripsMarkdownFencesAndChatter() {
        assertEquals("{\"recipes\":[]}", RecipeValidator.stripFences("```json\n{\"recipes\":[]}\n```"));
        assertEquals("{\"a\":1}", RecipeValidator.stripFences("Sure! Here you go: {\"a\":1} Enjoy."));
    }

    @Test
    void capsAtThreeRecipes() {
        var many = List.of(recipe("a", 1L, 3L), recipe("b", 2L, 3L), recipe("c", 1L, 4L), recipe("d", 2L, 4L));
        assertEquals(3, RecipeValidator.validate(new AiRecipes(many), allowed, cart, restricted).valid().size());
    }
}
