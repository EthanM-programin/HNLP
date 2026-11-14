package org.epiccure.mtg.rules;

import org.epiccure.mtg.model.ClinicalCondition;
import org.epiccure.mtg.model.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Pure unit tests for the deterministic safety core, using the V3 per-serving ceilings and real catalog values. */
class RulesEngineTest {

    private final RulesEngine engine = new RulesEngine();

    static ClinicalCondition cond(String icd, Integer na, String sugar, String carbs, Integer k, Integer p, String sat) {
        ClinicalCondition c = new ClinicalCondition();
        c.setIcd10Prefix(icd);
        c.setName(icd);
        c.setMaxSodiumMg(na);
        c.setMaxSugarG(sugar == null ? null : new BigDecimal(sugar));
        c.setMaxCarbsG(carbs == null ? null : new BigDecimal(carbs));
        c.setMaxPotassiumMg(k);
        c.setMaxPhosphorusMg(p);
        c.setMaxSatFatG(sat == null ? null : new BigDecimal(sat));
        return c;
    }

    static final ClinicalCondition E11 = cond("E11", null, "15", "45", null, null, "4");
    static final ClinicalCondition I10 = cond("I10", 300, null, null, null, null, "3");
    static final ClinicalCondition N18 = cond("N18", 300, null, null, 250, 200, null);
    static final List<ClinicalCondition> ALL = List.of(E11, I10, N18, cond("Z91.01", null, null, null, null, null, null));

    static Product product(String name, Integer na, String sugar, String carbs, Integer k, Integer p, String sat, String allergens) {
        Product x = new Product();
        x.setName(name);
        x.setSku(name);
        x.setCategory("Test");
        x.setSodiumMg(na);
        x.setSugarG(sugar == null ? null : new BigDecimal(sugar));
        x.setCarbsG(carbs == null ? null : new BigDecimal(carbs));
        x.setPotassiumMg(k);
        x.setPhosphorusMg(p);
        x.setSatFatG(sat == null ? null : new BigDecimal(sat));
        x.setAllergens(allergens);
        return x;
    }

    Ruleset rules(List<String> codes, List<String> allergies) {
        return engine.build(engine.matchConditions(ALL, codes), codes, allergies);
    }

    @Test
    void strictestCeilingWinsAcrossConditions() {
        Ruleset r = rules(List.of("N18", "E11.9"), List.of());
        assertEquals(Integer.valueOf(300), r.maxSodiumMg());
        assertEquals(Integer.valueOf(250), r.maxPotassiumMg());
        assertEquals(new BigDecimal("15"), r.maxSugarG());
        assertEquals(new BigDecimal("4"), r.maxSatFatG());
    }

    @Test
    void ckdClientGetsSpinachRedForPotassium() {
        Product spinach = product("Spinach", 25, "0.4", "2", 558, 49, "0", null);
        FlagResult f = engine.evaluate(spinach, rules(List.of("N18"), List.of()));
        assertEquals(Flag.RED, f.flag());
        assertTrue(f.summary().contains("Potassium"));
    }

    @Test
    void hypertensionClientGetsSameSpinachGreen() {
        Product spinach = product("Spinach", 25, "0.4", "2", 558, 49, "0", null);
        assertEquals(Flag.GREEN, engine.evaluate(spinach, rules(List.of("I10"), List.of())).flag());
    }

    @Test
    void eightyToHundredPercentIsYellow() {
        Product sardines = product("Sardines", 300, "0", "0", 350, 250, "3.0", "FISH");
        assertEquals(Flag.YELLOW, engine.evaluate(sardines, rules(List.of("I10"), List.of())).flag()); // sodium 100%, sat fat 100%
    }

    @Test
    void overHundredPercentIsRed() {
        Product cottage = product("Cottage cheese", 350, "5", "5", 150, 140, "1", "MILK");
        assertEquals(Flag.RED, engine.evaluate(cottage, rules(List.of("I10"), List.of())).flag());
    }

    @Test
    void allergenIsAlwaysRedEvenWhenNutrientsAreFine() {
        Product almonds = product("Almonds", 0, "1.2", "6", 210, 135, "1", "TREE_NUT");
        FlagResult f = engine.evaluate(almonds, rules(List.of(), List.of("Tree Nuts")));
        assertEquals(Flag.RED, f.flag());
        assertTrue(f.summary().contains("TREE_NUT"));
    }

    @Test
    void peanutAllergyCodeImpliesPeanutExclusion() {
        Product pb = product("Peanut butter", 5, "2", "6", 200, 130, "2.5", "PEANUT");
        assertEquals(Flag.RED, engine.evaluate(pb, rules(List.of("Z91.010"), List.of())).flag());
    }

    @Test
    void missingNutrientValueFailsSafeToYellowNeverGreen() {
        Product unknown = product("Donated soup", null, null, null, null, null, null, null);
        FlagResult f = engine.evaluate(unknown, rules(List.of("I10"), List.of()));
        assertEquals(Flag.YELLOW, f.flag());
    }

    @Test
    void noConditionsMeansEverythingGreenExceptAllergens() {
        Product honey = product("Honey", 1, "17", "17", 11, 1, "0", null);
        assertEquals(Flag.GREEN, engine.evaluate(honey, rules(List.of(), List.of("None"))).flag());
        assertEquals(Set.of(), rules(List.of(), List.of("None")).allergens());
    }

    @Test
    void rulesetSnapshotRoundTrips() {
        Ruleset r = rules(List.of("N18", "E11"), List.of("Dairy"));
        assertEquals(r, Ruleset.decode(r.encode()));
        assertTrue(r.allergens().contains("MILK"));
    }
}
