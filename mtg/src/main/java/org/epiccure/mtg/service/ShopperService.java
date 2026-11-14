package org.epiccure.mtg.service;

import org.epiccure.mtg.api.ApiModels.*;
import org.epiccure.mtg.common.ApiException;
import org.epiccure.mtg.model.CartItem;
import org.epiccure.mtg.model.DietaryTier;
import org.epiccure.mtg.model.Product;
import org.epiccure.mtg.model.ShoppingSession;
import org.epiccure.mtg.repository.CartItemRepository;
import org.epiccure.mtg.repository.ProductRepository;
import org.epiccure.mtg.rules.Flag;
import org.epiccure.mtg.rules.FlagResult;
import org.epiccure.mtg.rules.Ruleset;
import org.epiccure.mtg.rules.RulesEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/** Epic 3 deterministic half: recommendations (US-3.2 / US-1.2) and the aisle safety scan (US-3.1). No LLM here. */
@Service
public class ShopperService {

    private static final int PER_CATEGORY = 6;

    private final SessionService sessionService;
    private final ProductRepository products;
    private final CartItemRepository cart;
    private final StockService stock;
    private final RulesEngine engine;

    public ShopperService(SessionService sessionService, ProductRepository products, CartItemRepository cart,
                          StockService stock, RulesEngine engine) {
        this.sessionService = sessionService;
        this.products = products;
        this.cart = cart;
        this.stock = stock;
        this.engine = engine;
    }

    /** Best GREEN items currently on the floor, grouped by category, safest first. */
    @Transactional(readOnly = true)
    public RecommendationsView recommendations(String token) {
        ShoppingSession s = sessionService.requireActive(token);
        Ruleset r = sessionService.ruleset(s);
        Map<Long, StockService.FloorStock> floor = stock.floorStock();

        Map<String, List<Object[]>> byCategory = new TreeMap<>();
        for (Product p : products.findByActiveTrueOrderByIdAsc()) {
            StockService.FloorStock fs = floor.get(p.getId());
            if (fs == null || fs.quantity() <= 0) continue;
            FlagResult f = engine.evaluate(p, r);
            if (f.flag() != Flag.GREEN) continue;
            byCategory.computeIfAbsent(p.getCategory(), k -> new ArrayList<>()).add(new Object[]{p, f, fs});
        }
        List<CategoryGroup> groups = new ArrayList<>();
        byCategory.forEach((cat, list) -> {
            list.sort(Comparator.comparingDouble((Object[] o) -> ((FlagResult) o[1]).maxRatio())
                    .thenComparing(o -> fiber((Product) o[0]), Comparator.reverseOrder()));
            List<ItemView> items = new ArrayList<>();
            for (Object[] o : list.subList(0, Math.min(PER_CATEGORY, list.size()))) {
                items.add(StockService.item((Product) o[0], (FlagResult) o[1], (StockService.FloorStock) o[2]));
            }
            groups.add(new CategoryGroup(cat, items));
        });
        DietaryTier tier = sessionService.tierFor(s.getClient());
        return new RecommendationsView(s.getTierCode(), SessionService.targets(tier, s.getHouseholdSize()), r.dietFocus(), groups);
    }

    /** US-3.1: barcode or manual SKU -> flag, reasons, location; optionally adds to the cart manifest. */
    @Transactional
    public ScanView scan(String token, ScanRequest req) {
        ShoppingSession s = sessionService.requireActive(token);
        if (req == null || req.code() == null || req.code().isBlank()) throw ApiException.badRequest("CODE_REQUIRED", "Scan a barcode or type a SKU.");
        Product p = findProduct(req.code());
        FlagResult f = engine.evaluate(p, sessionService.ruleset(s));
        sessionService.touch(s);

        int cap = yellowCap(s);
        boolean add = req.addToCart() == null || req.addToCart();
        boolean added = false, capReached = false;
        String message = switch (f.flag()) {
            case GREEN -> "Great choice for your health plan.";
            case YELLOW -> "OK in limited amounts: up to " + cap + " for your household.";
            case RED -> "Not safe for your health plan. Please put this back (a volunteer will have to remove it at checkout).";
        };

        CartItem line = cart.findBySessionIdAndProductId(s.getId(), p.getId()).orElse(null);
        if (add) {   // RED items are recorded too: the manifest mirrors the physical cart, and checkout hard-stops on them
            if (f.flag() == Flag.YELLOW && line != null && line.getQuantity() >= cap) {
                capReached = true;
                message = "You've reached the limit of " + cap + " for this item.";
            } else {
                if (line == null) {
                    line = new CartItem();
                    line.setSession(s);
                    line.setProduct(p);
                    line.setQuantity(1);
                } else {
                    line.setQuantity(line.getQuantity() + 1);
                }
                line.setFlag(f.flag().name());
                cart.save(line);
                added = true;
            }
        }
        StockService.FloorStock fs = stock.floorStock().get(p.getId());
        return new ScanView(StockService.item(p, f, fs), added, line == null ? 0 : line.getQuantity(), capReached,
                f.flag() == Flag.YELLOW ? cap : null, message, cartView(s));
    }

    @Transactional(readOnly = true)
    public CartView cart(String token) {
        return cartView(sessionService.require(token));
    }

    @Transactional
    public CartView remove(String token, Long productId, Integer quantity) {
        ShoppingSession s = sessionService.requireActive(token);
        cart.findBySessionIdAndProductId(s.getId(), productId).ifPresent(line -> {
            int left = quantity == null ? 0 : line.getQuantity() - quantity;
            if (left <= 0) cart.delete(line); else { line.setQuantity(left); cart.save(line); }
        });
        sessionService.touch(s);
        return cartView(s);
    }

    CartView cartView(ShoppingSession s) {
        Ruleset r = sessionService.ruleset(s);
        Map<Long, StockService.FloorStock> floor = stock.floorStock();
        int cap = yellowCap(s);
        List<CartLine> lines = new ArrayList<>();
        int units = 0;
        long red = 0;
        BigDecimal weight = BigDecimal.ZERO;
        for (CartItem c : cart.findWithProduct(s.getId())) {
            Product p = c.getProduct();
            FlagResult f = engine.evaluate(p, r);
            if (f.flag() == Flag.RED) red++;
            units += c.getQuantity();
            weight = weight.add(p.getWeightLb().multiply(BigDecimal.valueOf(c.getQuantity())));
            lines.add(new CartLine(StockService.item(p, f, floor.get(p.getId())), c.getQuantity(), f.flag() == Flag.YELLOW ? cap : null));
        }
        return new CartView(lines, units, weight, red);
    }

    Product findProduct(String code) {
        String c = code.trim();
        return products.findByBarcode(c)
                .or(() -> products.findBySkuIgnoreCase(c))
                .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "We don't recognise that item. Ask a volunteer to check it."));
    }

    /** Capped (YELLOW) items: one unit per household member per visit. */
    static int yellowCap(ShoppingSession s) { return Math.max(1, s.getHouseholdSize() == null ? 1 : s.getHouseholdSize()); }

    private static BigDecimal fiber(Product p) { return p.getFiberG() == null ? BigDecimal.ZERO : p.getFiberG(); }
}
