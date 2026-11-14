package org.epiccure.mtg.rules;

import java.util.List;

/** Result of evaluating one product against one client's ruleset. maxRatio = highest value/ceiling seen. */
public record FlagResult(Flag flag, double maxRatio, List<String> reasons) {
    public String summary() {
        return reasons.isEmpty() ? "Within all of your limits" : String.join("; ", reasons);
    }
}
