package org.epiccure.mtg.api;

import org.epiccure.mtg.ai.AiModels.HarnessReport;
import org.epiccure.mtg.ai.RecipeService;
import org.epiccure.mtg.api.ApiModels.*;
import org.epiccure.mtg.service.AnalyticsService;
import org.epiccure.mtg.service.CheckoutService;
import org.epiccure.mtg.service.QueueService;
import org.epiccure.mtg.service.RestockService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/** Volunteer (Epic 4), manager/director (Epic 5) and admin endpoints. */
@RestController
@RequestMapping("/api")
public class WarehouseController {

    private final CheckoutService checkout;
    private final RestockService restock;
    private final AnalyticsService analytics;
    private final QueueService queue;
    private final RecipeService recipes;

    public WarehouseController(CheckoutService checkout, RestockService restock, AnalyticsService analytics,
                               QueueService queue, RecipeService recipes) {
        this.checkout = checkout;
        this.restock = restock;
        this.analytics = analytics;
        this.queue = queue;
        this.recipes = recipes;
    }

    // ---- checkout (US-4.1) ----
    @PostMapping("/checkout/{token}/validate")
    public ValidationView validate(@PathVariable String token, @RequestBody(required = false) CheckoutRequest req) {
        return checkout.validate(token, req == null ? null : req.scannedCodes());
    }

    /** Body: {"productId": 55, "reason": "...", "volunteer": "...", "scannedCodes": [...]} */
    @PostMapping("/checkout/{token}/remove")
    public ValidationView remove(@PathVariable String token, @RequestBody RemoveWithScans req, Principal principal) {
        return checkout.remove(token, new RemoveRequest(req.productId(), req.reason(), req.volunteer()),
                volunteer(principal, req.volunteer()), req.scannedCodes());
    }

    @PostMapping("/checkout/{token}/complete")
    public Receipt complete(@PathVariable String token, @RequestBody(required = false) CheckoutRequest req, Principal principal) {
        return checkout.complete(token, req == null ? null : req.scannedCodes(), volunteer(principal, req == null ? null : req.volunteer()));
    }

    public record RemoveWithScans(Long productId, String reason, String volunteer, List<String> scannedCodes) {}

    // ---- restock (US-4.2, US-4.3) ----
    @GetMapping("/restock")
    public RestockView restock() { return restock.restock(); }

    // ---- analytics (US-5.1, US-5.2) ----
    @GetMapping("/analytics/outcomes")
    public OutcomesView outcomes() { return analytics.outcomes(); }

    @GetMapping("/analytics/grants")
    public GrantsView grants() { return analytics.grants(); }

    @GetMapping("/analytics/grants.csv")
    public ResponseEntity<String> grantsCsv() { return csv("grant-attribution.csv", analytics.grantsCsv()); }

    @GetMapping("/analytics/outcomes.csv")
    public ResponseEntity<String> outcomesCsv() { return csv("program-outcomes.csv", analytics.outcomesCsv()); }

    // ---- admin ----
    @PostMapping("/admin/queue/tick")
    public CapacityView tick() {
        queue.promoteWaiting();
        return queue.capacity();
    }

    /** Model validation harness: GET /api/admin/ai-harness?runs=1 (each run = 1 Groq call per persona). */
    @GetMapping("/admin/ai-harness")
    public HarnessReport harness(@RequestParam(defaultValue = "1") int runs) {
        return recipes.harness(Math.max(1, Math.min(runs, 5)));
    }

    private static String volunteer(Principal principal, String fromBody) {
        if (principal != null) return principal.getName();
        return fromBody == null || fromBody.isBlank() ? "volunteer" : fromBody.trim();
    }

    private static ResponseEntity<String> csv(String filename, String body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(body);
    }
}
