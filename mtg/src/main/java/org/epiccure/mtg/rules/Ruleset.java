package org.epiccure.mtg.rules;

import java.math.BigDecimal;
import java.util.*;

/**
 * A client's effective per-serving limits for one visit: the strictest (minimum) ceiling across all of
 * their conditions, plus the union of their allergens. Computed once at activation and frozen on the
 * shopping session, so scans never re-read PHI and rule edits mid-visit can't change a client's flags.
 *
 * Stored on shopping_session.ruleset_snapshot as "key=value;..." (no PHI: only numbers and allergen tags).
 */
public record Ruleset(Integer maxSodiumMg, BigDecimal maxSugarG, BigDecimal maxCarbsG, Integer maxPotassiumMg,
                      Integer maxPhosphorusMg, BigDecimal maxSatFatG, Set<String> allergens) {

    public static final Ruleset NONE = new Ruleset(null, null, null, null, null, null, Set.of());

    public String encode() {
        return "sodium=" + str(maxSodiumMg) + ";sugar=" + str(maxSugarG) + ";carbs=" + str(maxCarbsG)
             + ";potassium=" + str(maxPotassiumMg) + ";phosphorus=" + str(maxPhosphorusMg)
             + ";satfat=" + str(maxSatFatG) + ";allergens=" + String.join(",", new TreeSet<>(allergens));
    }

    public static Ruleset decode(String s) {
        if (s == null || s.isBlank()) return NONE;
        Map<String, String> m = new HashMap<>();
        for (String part : s.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0) m.put(part.substring(0, eq), part.substring(eq + 1));
        }
        return new Ruleset(i(m.get("sodium")), d(m.get("sugar")), d(m.get("carbs")), i(m.get("potassium")),
                i(m.get("phosphorus")), d(m.get("satfat")), RulesEngine.parseTags(m.get("allergens")));
    }

    /** Plain-language diet focus sent to the LLM instead of diagnoses (no PHI leaves the system). */
    public List<String> dietFocus() {
        List<String> out = new ArrayList<>();
        if (maxSodiumMg != null) out.add("low sodium (max " + maxSodiumMg + " mg per serving)");
        if (maxSugarG != null) out.add("low sugar (max " + maxSugarG.stripTrailingZeros().toPlainString() + " g per serving)");
        if (maxCarbsG != null) out.add("controlled carbohydrate (max " + maxCarbsG.stripTrailingZeros().toPlainString() + " g per serving)");
        if (maxPotassiumMg != null) out.add("low potassium (max " + maxPotassiumMg + " mg per serving)");
        if (maxPhosphorusMg != null) out.add("low phosphorus (max " + maxPhosphorusMg + " mg per serving)");
        if (maxSatFatG != null) out.add("low saturated fat (max " + maxSatFatG.stripTrailingZeros().toPlainString() + " g per serving)");
        if (!allergens.isEmpty()) out.add("must avoid allergens: " + String.join(", ", new TreeSet<>(allergens)));
        if (out.isEmpty()) out.add("general healthy eating");
        return out;
    }

    private static String str(Object o) { return o == null ? "" : (o instanceof BigDecimal b ? b.toPlainString() : o.toString()); }
    private static Integer i(String s) { return s == null || s.isBlank() ? null : Integer.valueOf(s); }
    private static BigDecimal d(String s) { return s == null || s.isBlank() ? null : new BigDecimal(s); }
}
