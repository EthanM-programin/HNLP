package org.epiccure.mtg.rules;

import org.epiccure.mtg.model.ClinicalCondition;
import org.epiccure.mtg.model.Product;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * The deterministic clinical safety core. The LLM never calls into this decision: every
 * Green / Yellow / Red flag in the system (scan, recommendations, checkout hard-stop) comes from here.
 *
 *   RED    = contains one of the client's allergens, OR any nutrient above 100% of its ceiling
 *   YELLOW = any nutrient at 80-100% of its ceiling, OR a limited nutrient that the product doesn't list (fail safe)
 *   GREEN  = every limited nutrient below 80% of its ceiling
 */
@Component
public class RulesEngine {

    public static final double YELLOW_AT = 0.80;
    public static final double RED_ABOVE = 1.00;

    /** ICD-10 allergy codes that imply an allergen exclusion (in addition to the client's stated allergies). */
    private static final Map<String, String> ALLERGY_CODES = Map.of("Z91.01", "PEANUT");

    /** Conditions whose ICD-10 prefix matches any of the client's codes (E11 matches E11.9). */
    public List<ClinicalCondition> matchConditions(Collection<ClinicalCondition> all, Collection<String> clientCodes) {
        List<ClinicalCondition> out = new ArrayList<>();
        for (ClinicalCondition c : all) {
            String prefix = c.getIcd10Prefix().trim().toUpperCase(Locale.ROOT);
            for (String code : clientCodes) {
                if (code.trim().toUpperCase(Locale.ROOT).startsWith(prefix)) { out.add(c); break; }
            }
        }
        return out;
    }

    /** Strictest ceiling per nutrient across all conditions (MIN, ignoring "no limit"), union of allergens. */
    public Ruleset build(Collection<ClinicalCondition> conditions, Collection<String> clientCodes, Collection<String> clientAllergens) {
        Integer sodium = null, potassium = null, phosphorus = null;
        BigDecimal sugar = null, carbs = null, satFat = null;
        for (ClinicalCondition c : conditions) {
            sodium = min(sodium, c.getMaxSodiumMg());
            potassium = min(potassium, c.getMaxPotassiumMg());
            phosphorus = min(phosphorus, c.getMaxPhosphorusMg());
            sugar = min(sugar, c.getMaxSugarG());
            carbs = min(carbs, c.getMaxCarbsG());
            satFat = min(satFat, c.getMaxSatFatG());
        }
        Set<String> allergens = new TreeSet<>();
        for (String a : clientAllergens) {
            String tag = normaliseTag(a);
            if (!tag.isEmpty() && !tag.equals("NONE")) allergens.add(tag);
        }
        for (String code : clientCodes) {
            ALLERGY_CODES.forEach((prefix, tag) -> {
                if (code.trim().toUpperCase(Locale.ROOT).startsWith(prefix)) allergens.add(tag);
            });
        }
        return new Ruleset(sodium, sugar, carbs, potassium, phosphorus, satFat, Collections.unmodifiableSet(allergens));
    }

    public FlagResult evaluate(Product p, Ruleset r) {
        List<String> reasons = new ArrayList<>();

        Set<String> hit = new TreeSet<>(parseTags(p.getAllergens()));
        hit.retainAll(r.allergens());
        if (!hit.isEmpty()) {
            reasons.add("Contains " + String.join(", ", hit) + " (allergy)");
            return new FlagResult(Flag.RED, Double.POSITIVE_INFINITY, reasons);
        }

        double max = 0;
        boolean unverifiable = false;
        double[] checks = {
            check("Sodium", "mg", num(p.getSodiumMg()), num(r.maxSodiumMg()), reasons),
            check("Sugar", "g", num(p.getSugarG()), num(r.maxSugarG()), reasons),
            check("Carbs", "g", num(p.getCarbsG()), num(r.maxCarbsG()), reasons),
            check("Potassium", "mg", num(p.getPotassiumMg()), num(r.maxPotassiumMg()), reasons),
            check("Phosphorus", "mg", num(p.getPhosphorusMg()), num(r.maxPhosphorusMg()), reasons),
            check("Saturated fat", "g", num(p.getSatFatG()), num(r.maxSatFatG()), reasons),
        };
        for (double c : checks) {
            if (Double.isNaN(c)) unverifiable = true; else max = Math.max(max, c);
        }

        Flag flag = max > RED_ABOVE ? Flag.RED : (max >= YELLOW_AT || unverifiable ? Flag.YELLOW : Flag.GREEN);
        return new FlagResult(flag, max, reasons);
    }

    /** @return value/ceiling, 0 if no ceiling, NaN if there is a ceiling but the product doesn't list the value. */
    private static double check(String label, String unit, Double value, Double ceiling, List<String> reasons) {
        if (ceiling == null) return 0;
        if (value == null) {
            reasons.add(label + " not listed on this item, so it can't be verified");
            return Double.NaN;
        }
        if (ceiling <= 0) return value > 0 ? Double.POSITIVE_INFINITY : 0;
        double ratio = value / ceiling;
        if (ratio >= YELLOW_AT) {
            reasons.add(String.format(Locale.ROOT, "%s %s %s is %d%% of your %s %s limit",
                    label, fmt(value), unit, Math.round(ratio * 100), fmt(ceiling), unit));
        }
        return ratio;
    }

    public static Set<String> parseTags(String csv) {
        Set<String> out = new TreeSet<>();
        if (csv == null) return out;
        for (String s : csv.split(",")) {
            String t = normaliseTag(s);
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /** Normalise free-text allergen names to the product.allergens vocabulary. */
    public static String normaliseTag(String raw) {
        String t = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replaceAll("[\\[\\]\"]", "").replace(' ', '_');
        return switch (t) {
            case "TREE_NUTS", "NUTS", "TREENUT" -> "TREE_NUT";
            case "PEANUTS" -> "PEANUT";
            case "DAIRY", "LACTOSE" -> "MILK";
            case "EGGS" -> "EGG";
            case "GLUTEN" -> "WHEAT";
            default -> t;
        };
    }

    private static Integer min(Integer a, Integer b) {
        if (a == null) return b;
        if (b == null) return a;
        return Integer.valueOf(Math.min(a, b));   // explicit: a ternary here would unbox null and throw
    }
    private static BigDecimal min(BigDecimal a, BigDecimal b) { return a == null ? b : (b == null ? a : a.min(b)); }
    private static Double num(Integer v) { return v == null ? null : v.doubleValue(); }
    private static Double num(BigDecimal v) { return v == null ? null : v.doubleValue(); }
    private static String fmt(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v); }
}
