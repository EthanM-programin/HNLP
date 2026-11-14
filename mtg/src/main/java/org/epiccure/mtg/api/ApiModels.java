package org.epiccure.mtg.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Request/response shapes for the REST API (see docs/API.md). Plain records, serialised by Spring's JSON support. */
public final class ApiModels {
    private ApiModels() {}

    // ---- shared ----
    public record LocationView(String zone, String aisle, Integer shelf, Integer bin, boolean floor, String label) {}
    public record Nutrients(Integer calories, BigDecimal proteinG, BigDecimal carbsG, BigDecimal sugarG, BigDecimal fiberG,
                            BigDecimal satFatG, Integer sodiumMg, Integer potassiumMg, Integer phosphorusMg) {}
    /** One product as seen by one client: flag + reasons + where to find it. percentOfLimit is null for allergens. */
    public record ItemView(Long productId, String sku, String barcode, String name, String category, String servingDesc,
                           String flag, List<String> reasons, Integer percentOfLimit, Set<String> allergens,
                           Nutrients nutrients, LocationView location, Integer floorStock) {}
    public record GroceryTargets(BigDecimal produceLb, BigDecimal proteinLb, BigDecimal dairyUnits, BigDecimal grainLb) {}

    // ---- Epic 1: check-in, capacity ----
    public record CheckInRequest(String clientCode) {}
    public record SessionView(String sessionToken, String status, String firstName, String tierCode, String tierName,
                              Integer householdSize, Integer queuePosition, GroceryTargets targets, List<String> dietFocus,
                              long activeShoppers, int maxCapacity) {}
    public record CapacityView(long activeShoppers, int maxCapacity, long waiting, boolean full, int sessionTimeoutMinutes) {}

    // ---- Epic 3: shopper ----
    public record CategoryGroup(String category, List<ItemView> items) {}
    public record RecommendationsView(String tierCode, GroceryTargets targets, List<String> dietFocus, List<CategoryGroup> groups) {}
    public record ScanRequest(String code, Boolean addToCart) {}
    public record CartLine(ItemView item, int quantity, Integer yellowCap) {}
    public record CartView(List<CartLine> lines, int totalUnits, BigDecimal totalWeightLb, long redCount) {}
    public record ScanView(ItemView item, boolean addedToCart, int cartQuantity, boolean capReached, Integer yellowCap,
                           String message, CartView cart) {}

    // ---- Epic 4: checkout, restock ----
    public record CheckoutRequest(List<String> scannedCodes, String volunteer) {}
    public record RemoveRequest(Long productId, String reason, String volunteer) {}
    public record CheckoutLineView(ItemView item, int quantity, boolean inCart, boolean scanned) {}
    /** status = OK or HARD_STOP. restricted = the RED lines the volunteer must remove before completing. */
    public record ValidationView(String status, List<CheckoutLineView> lines, List<CheckoutLineView> restricted,
                                 List<String> mismatches, int totalUnits, BigDecimal totalWeightLb) {}
    public record LotDraw(Long lotId, int quantity, String fundingStream, LocalDate expiresAt, String location) {}
    public record ReceiptLine(String sku, String name, int quantity, BigDecimal weightLb, List<LotDraw> lots, int shortfall) {}
    public record ExpiryAlert(Long lotId, String productName, String location, LocalDate expiresAt, long daysLeft, int quantityRemaining) {}
    public record Receipt(Long checkoutId, String sessionToken, String firstName, String volunteer, Instant completedAt,
                          List<ReceiptLine> lines, int totalUnits, BigDecimal totalWeightLb, List<ExpiryAlert> expiryAlerts,
                          int promotedFromQueue) {}
    public record RestockTask(int stop, Long productId, String sku, String name, LocationView floorLocation, int floorQty,
                              int threshold, int moveQty, LocationView fromStockroom, int stockroomQty) {}
    public record RestockView(List<RestockTask> route, List<ExpiryAlert> expiring) {}

    // ---- Epic 5: analytics (no names, codes or ids) ----
    public record MetricTrend(String metric, String unit, int clients, BigDecimal baseline, BigDecimal month3,
                              BigDecimal month6, BigDecimal change, BigDecimal changePct, String direction) {}
    public record OutcomesView(List<MetricTrend> metrics, int minCohort, String note) {}
    public record GrantRow(String fundingStream, String category, int units, BigDecimal pounds) {}
    public record GrantsView(List<GrantRow> rows, BigDecimal totalPounds, long checkouts) {}
}
