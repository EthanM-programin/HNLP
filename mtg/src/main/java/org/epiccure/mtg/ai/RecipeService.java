package org.epiccure.mtg.ai;

import org.epiccure.mtg.ai.AiModels.*;
import org.epiccure.mtg.model.CartItem;
import org.epiccure.mtg.model.Client;
import org.epiccure.mtg.model.Product;
import org.epiccure.mtg.model.ShoppingSession;
import org.epiccure.mtg.repository.CartItemRepository;
import org.epiccure.mtg.repository.ClientRepository;
import org.epiccure.mtg.repository.ProductRepository;
import org.epiccure.mtg.rules.Flag;
import org.epiccure.mtg.rules.FlagResult;
import org.epiccure.mtg.rules.Ruleset;
import org.epiccure.mtg.rules.RulesEngine;
import org.epiccure.mtg.service.SessionService;
import org.epiccure.mtg.service.StockService;
import org.epiccure.mtg.service.StockService.FloorStock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-facing recipe endpoint + the model validation harness. Responses are cached per
 * (ruleset hash + cart contents): two clients with the same cart but different conditions never share recipes,
 * and re-opening the recipe screen doesn't burn free-tier rate limit.
 */
@Service
public class RecipeService {

    private final SessionService sessionService;
    private final CartItemRepository cart;
    private final ProductRepository products;
    private final ClientRepository clients;
    private final StockService stock;
    private final RecipeEngine recipes;
    private final RulesEngine engine;
    private final Map<String, RecipesView> cache = new ConcurrentHashMap<>();

    public RecipeService(SessionService sessionService, CartItemRepository cart, ProductRepository products,
                         ClientRepository clients, StockService stock, RecipeEngine recipes, RulesEngine engine) {
        this.sessionService = sessionService;
        this.cart = cart;
        this.products = products;
        this.clients = clients;
        this.stock = stock;
        this.recipes = recipes;
        this.engine = engine;
    }

    @Transactional(readOnly = true)
    public RecipesView forSession(String token, boolean refresh) {
        ShoppingSession s = sessionService.requireActive(token);
        List<CartItem> items = cart.findWithProduct(s.getId());
        StringBuilder key = new StringBuilder(String.valueOf(s.getRulesetHash())).append('|');
        items.stream().map(c -> c.getProduct().getId() + "x" + c.getQuantity()).sorted().forEach(k -> key.append(k).append(','));

        if (!refresh) {
            RecipesView hit = cache.get(key.toString());
            if (hit != null) return hit.withCached(true);
        }
        List<Product> cartProducts = items.stream().map(CartItem::getProduct).toList();
        RecipesView v = recipes.generate(sessionService.ruleset(s), cartProducts, stock.floorStock(), products.findByActiveTrueOrderByIdAsc());
        if (cache.size() > 500) cache.clear();
        if ("AI".equals(v.source())) cache.put(key.toString(), v);   // don't pin a fallback: retry the AI next time
        return v;
    }

    /**
     * Model evaluation: for every client profile, build a realistic cart (safe items plus a Capped item if any),
     * call the model, and independently re-check what would be SERVED. Pass = zero restricted or out-of-stock
     * items served across all runs. Output has no names or ids, only each persona's diet focus.
     */
    @Transactional(readOnly = true)
    public HarnessReport harness(int runsPerPersona) {
        List<Product> catalog = products.findByActiveTrueOrderByIdAsc();
        Map<Long, FloorStock> floor = stock.floorStock();
        List<HarnessCase> cases = new ArrayList<>();
        int personaNo = 0;
        for (Client c : clients.findAll(org.springframework.data.domain.Sort.by("id"))) {
            personaNo++;
            Ruleset r = sessionService.rulesetFor(c);
            List<Product> cartProducts = sampleCart(r, catalog, floor, personaNo);
            for (int run = 0; run < runsPerPersona; run++) {
                RecipesView v = recipes.generate(r, cartProducts, floor, catalog);
                int servedRed = 0, servedOos = 0;
                for (RecipeView rv : v.recipes()) {
                    for (RecipeIngredientView i : rv.ingredients()) {
                        Product p = catalog.stream().filter(x -> x.getId().equals(i.productId())).findFirst().orElse(null);
                        if (p == null || engine.evaluate(p, r).flag() == Flag.RED) servedRed++;
                        FloorStock fs = floor.get(i.productId());
                        if (!i.inCart() && (fs == null || fs.quantity() <= 0)) servedOos++;
                    }
                }
                cases.add(new HarnessCase("Persona " + personaNo, r.dietFocus(), v.source(), v.failure(), v.recipes().size(),
                        v.aiRawRecipes(), v.rejected(), v.restrictedCaught(), servedRed, servedOos, v.latencyMs()));
            }
        }
        int ai = 0, fb = 0, bad = 0, raw = 0, rej = 0, caught = 0, red = 0, oos = 0;
        long total = 0, max = 0;
        for (HarnessCase hc : cases) {
            if ("AI".equals(hc.source())) ai++; else fb++;
            if ("INVALID_JSON".equals(hc.failure())) bad++;
            raw += hc.aiRawRecipes(); rej += hc.rejected(); caught += hc.restrictedCaught();
            red += hc.servedRestrictedViolations(); oos += hc.servedOutOfStock();
            total += hc.latencyMs(); max = Math.max(max, hc.latencyMs());
        }
        return new HarnessReport(cases.size(), ai, fb, bad, raw, rej, caught, red, oos,
                cases.isEmpty() ? 0 : total / cases.size(), max, red == 0 && oos == 0, cases);
    }

    /** 3 safe items + 1 Capped item (if the persona has any), varied per persona. */
    private List<Product> sampleCart(Ruleset r, List<Product> catalog, Map<Long, FloorStock> floor, int seed) {
        List<Product> green = new ArrayList<>(), yellow = new ArrayList<>();
        for (Product p : catalog) {
            FloorStock fs = floor.get(p.getId());
            if (fs == null || fs.quantity() <= 0) continue;
            FlagResult f = engine.evaluate(p, r);
            if (f.flag() == Flag.GREEN) green.add(p); else if (f.flag() == Flag.YELLOW) yellow.add(p);
        }
        Collections.shuffle(green, new Random(seed));
        List<Product> out = new ArrayList<>(green.subList(0, Math.min(3, green.size())));
        if (!yellow.isEmpty()) out.add(yellow.get(seed % yellow.size()));
        return out;
    }
}
