package org.epiccure.mtg.service;

import org.epiccure.mtg.api.ApiModels.*;
import org.epiccure.mtg.model.BiometricReading;
import org.epiccure.mtg.model.CheckoutLine;
import org.epiccure.mtg.repository.BiometricReadingRepository;
import org.epiccure.mtg.repository.CheckoutLineRepository;
import org.epiccure.mtg.repository.CheckoutLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Epic 5. Outputs are population-level only: client ids are used to line readings up per person and are then
 * discarded; no names, phone numbers, client codes or ids leave this class. Cohorts smaller than
 * app.analytics.min-cohort are suppressed so a single person can't be singled out.
 */
@Service
public class AnalyticsService {

    private static final Map<String, String> UNITS = Map.of("A1C", "%", "SYSTOLIC_BP", "mmHg", "EGFR", "mL/min", "WEIGHT_LB", "lb");
    /** For these metrics a lower number is the improvement; for eGFR higher is better. */
    private static final Set<String> LOWER_IS_BETTER = Set.of("A1C", "SYSTOLIC_BP", "WEIGHT_LB");

    private final BiometricReadingRepository readings;
    private final CheckoutLineRepository checkoutLines;
    private final CheckoutLogRepository checkouts;
    private final int minCohort;

    public AnalyticsService(BiometricReadingRepository readings, CheckoutLineRepository checkoutLines,
                            CheckoutLogRepository checkouts, @Value("${app.analytics.min-cohort:2}") int minCohort) {
        this.readings = readings;
        this.checkoutLines = checkoutLines;
        this.checkouts = checkouts;
        this.minCohort = minCohort;
    }

    /** US-5.1: baseline vs 3-month vs 6-month averages per metric. */
    @Transactional(readOnly = true)
    public OutcomesView outcomes() {
        // metric -> anonymous person index -> readings (ids are dropped once grouped)
        Map<String, Map<Long, List<BiometricReading>>> grouped = new TreeMap<>();
        for (BiometricReading b : readings.findAllWithClient()) {
            grouped.computeIfAbsent(b.getMetric(), k -> new HashMap<>())
                   .computeIfAbsent(b.getClient().getId(), k -> new ArrayList<>()).add(b);
        }
        List<MetricTrend> out = new ArrayList<>();
        grouped.forEach((metric, perPerson) -> {
            List<BigDecimal> base = new ArrayList<>(), m3 = new ArrayList<>(), m6 = new ArrayList<>();
            for (List<BiometricReading> series : perPerson.values()) {
                series.sort(Comparator.comparing(BiometricReading::getRecordedOn));
                LocalDate start = series.get(0).getRecordedOn();
                base.add(series.get(0).getReadingValue());
                nearest(series, start.plusDays(90)).ifPresent(m3::add);
                nearest(series, start.plusDays(180)).ifPresent(m6::add);
            }
            int n = perPerson.size();
            if (n < minCohort) {
                out.add(new MetricTrend(metric, UNITS.getOrDefault(metric, ""), n, null, null, null, null, null, "SUPPRESSED"));
                return;
            }
            BigDecimal b = avg(base), a3 = avg(m3), a6 = avg(m6);
            BigDecimal end = a6 != null ? a6 : a3;
            BigDecimal change = end == null || b == null ? null : end.subtract(b);
            BigDecimal pct = change == null || b.signum() == 0 ? null
                    : change.multiply(BigDecimal.valueOf(100)).divide(b, 1, RoundingMode.HALF_UP);
            String direction = change == null || change.signum() == 0 ? "NO_CHANGE"
                    : ((change.signum() < 0) == LOWER_IS_BETTER.contains(metric) ? "IMPROVED" : "WORSENED");
            out.add(new MetricTrend(metric, UNITS.getOrDefault(metric, ""), n, b, a3, a6, change, pct, direction));
        });
        return new OutcomesView(out, minCohort,
                "Averages across clients with readings; identifiers removed; cohorts under " + minCohort + " suppressed.");
    }

    /** US-5.2: every pound distributed, attributed to the funding stream of the exact lot it came from. */
    @Transactional(readOnly = true)
    public GrantsView grants() {
        Map<String, BigDecimal> lbs = new TreeMap<>();
        Map<String, Integer> units = new TreeMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (CheckoutLine l : checkoutLines.findAllWithProduct()) {
            String key = (l.getFundingStream() == null ? "Unattributed" : l.getFundingStream()) + "\u0000" + l.getProduct().getCategory();
            lbs.merge(key, l.getWeightLb(), BigDecimal::add);
            units.merge(key, l.getQuantity(), Integer::sum);
            total = total.add(l.getWeightLb());
        }
        List<GrantRow> rows = new ArrayList<>();
        lbs.forEach((k, v) -> {
            String[] parts = k.split("\u0000");
            rows.add(new GrantRow(parts[0], parts[1], units.get(k), v));
        });
        return new GrantsView(rows, total, checkouts.count());
    }

    public String grantsCsv() {
        GrantsView g = grants();
        StringBuilder sb = new StringBuilder("funding_stream,category,units,pounds\n");
        for (GrantRow r : g.rows()) {
            sb.append(csv(r.fundingStream())).append(',').append(csv(r.category())).append(',')
              .append(r.units()).append(',').append(r.pounds().toPlainString()).append('\n');
        }
        sb.append("TOTAL,,,").append(g.totalPounds().toPlainString()).append('\n');
        return sb.toString();
    }

    public String outcomesCsv() {
        StringBuilder sb = new StringBuilder("metric,unit,clients,baseline,month_3,month_6,change,change_pct,direction\n");
        for (MetricTrend t : outcomes().metrics()) {
            sb.append(t.metric()).append(',').append(t.unit()).append(',').append(t.clients()).append(',')
              .append(n(t.baseline())).append(',').append(n(t.month3())).append(',').append(n(t.month6())).append(',')
              .append(n(t.change())).append(',').append(n(t.changePct())).append(',').append(t.direction()).append('\n');
        }
        return sb.toString();
    }

    /** Reading closest to the target date, within +/- 45 days. */
    private static Optional<BigDecimal> nearest(List<BiometricReading> series, LocalDate target) {
        BiometricReading best = null;
        long bestGap = Long.MAX_VALUE;
        for (BiometricReading r : series.subList(1, series.size())) {
            long gap = Math.abs(ChronoUnit.DAYS.between(target, r.getRecordedOn()));
            if (gap <= 45 && gap < bestGap) { best = r; bestGap = gap; }
        }
        return Optional.ofNullable(best).map(BiometricReading::getReadingValue);
    }

    private static BigDecimal avg(List<BigDecimal> xs) {
        if (xs.isEmpty()) return null;
        BigDecimal sum = xs.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(xs.size()), 2, RoundingMode.HALF_UP);
    }

    private static String n(BigDecimal b) { return b == null ? "" : b.toPlainString(); }
    private static String csv(String s) { return s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s; }
}
