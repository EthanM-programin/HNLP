package org.epiccure.mtg.api;

import org.epiccure.mtg.ai.AiModels.RecipesView;
import org.epiccure.mtg.ai.RecipeService;
import org.epiccure.mtg.api.ApiModels.*;
import org.epiccure.mtg.service.CheckInService;
import org.epiccure.mtg.service.QueueService;
import org.epiccure.mtg.service.ShopperService;
import org.springframework.web.bind.annotation.*;

/** Kiosk (Epic 1) and shopper phone (Epic 3) endpoints. */
@RestController
@RequestMapping("/api")
public class SessionController {

    private final CheckInService checkIn;
    private final QueueService queue;
    private final ShopperService shopper;
    private final RecipeService recipes;

    public SessionController(CheckInService checkIn, QueueService queue, ShopperService shopper, RecipeService recipes) {
        this.checkIn = checkIn;
        this.queue = queue;
        this.shopper = shopper;
        this.recipes = recipes;
    }

    @PostMapping("/checkin")
    public SessionView checkIn(@RequestBody CheckInRequest req) {
        return checkIn.checkIn(req == null ? null : req.clientCode());
    }

    @GetMapping("/capacity")
    public CapacityView capacity() { return queue.capacity(); }

    @GetMapping("/session/{token}")
    public SessionView session(@PathVariable String token) { return checkIn.status(token); }

    @GetMapping("/session/{token}/recommendations")
    public RecommendationsView recommendations(@PathVariable String token) { return shopper.recommendations(token); }

    @PostMapping("/session/{token}/scan")
    public ScanView scan(@PathVariable String token, @RequestBody ScanRequest req) { return shopper.scan(token, req); }

    @GetMapping("/session/{token}/cart")
    public CartView cart(@PathVariable String token) { return shopper.cart(token); }

    @DeleteMapping("/session/{token}/cart/{productId}")
    public CartView removeFromCart(@PathVariable String token, @PathVariable Long productId,
                                   @RequestParam(required = false) Integer quantity) {
        return shopper.remove(token, productId, quantity);
    }

    @GetMapping("/session/{token}/recipes")
    public RecipesView recipes(@PathVariable String token, @RequestParam(defaultValue = "false") boolean refresh) {
        return recipes.forSession(token, refresh);
    }
}
