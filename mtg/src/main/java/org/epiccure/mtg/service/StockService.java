package org.epiccure.mtg.service;

import org.epiccure.mtg.api.ApiModels.*;
import org.epiccure.mtg.model.BinLocation;
import org.epiccure.mtg.model.InventoryLot;
import org.epiccure.mtg.model.Product;
import org.epiccure.mtg.repository.InventoryLotRepository;
import org.epiccure.mtg.rules.FlagResult;
import org.epiccure.mtg.rules.RulesEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Floor stock + "where is it" lookups, and the shared ItemView builder. */
@Service
public class StockService {

    /** quantity on the floor, and the bin to send the shopper to (the bin holding the oldest lot). */
    public record FloorStock(int quantity, BinLocation location) {}

    public static final Comparator<InventoryLot> FIFO = Comparator
            .comparing(InventoryLot::getExpiresAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(InventoryLot::getReceivedAt);

    private final InventoryLotRepository lots;

    public StockService(InventoryLotRepository lots) { this.lots = lots; }

    @Transactional(readOnly = true)
    public Map<Long, FloorStock> floorStock() {
        Map<Long, List<InventoryLot>> byProduct = new HashMap<>();
        for (InventoryLot l : lots.findAllWithLocationAndProduct()) {
            if (Boolean.TRUE.equals(l.getLocation().getFloor())) {
                byProduct.computeIfAbsent(l.getProduct().getId(), k -> new ArrayList<>()).add(l);
            }
        }
        Map<Long, FloorStock> out = new HashMap<>();
        byProduct.forEach((productId, list) -> {
            list.sort(FIFO);
            int qty = 0;
            BinLocation where = list.get(0).getLocation();          // home bin even when empty
            boolean found = false;
            for (InventoryLot l : list) {
                if (l.getQuantityRemaining() > 0) {
                    qty += l.getQuantityRemaining();
                    if (!found) { where = l.getLocation(); found = true; }
                }
            }
            out.put(productId, new FloorStock(qty, where));
        });
        return out;
    }

    public static LocationView view(BinLocation b) {
        if (b == null) return null;
        boolean floor = Boolean.TRUE.equals(b.getFloor());
        String label = floor
                ? "Aisle " + b.getAisle() + " · Shelf " + b.getShelf() + " · Bin " + b.getBin()
                : "Stockroom " + b.getAisle() + "-" + b.getShelf() + "-" + b.getBin();
        return new LocationView(b.getZone(), b.getAisle(), b.getShelf(), b.getBin(), floor, label);
    }

    public static ItemView item(Product p, FlagResult f, FloorStock stock) {
        Integer pct = Double.isInfinite(f.maxRatio()) ? null : (int) Math.round(f.maxRatio() * 100);
        return new ItemView(p.getId(), p.getSku(), p.getBarcode(), p.getName(), p.getCategory(), p.getServingDesc(),
                f.flag().name(), f.reasons(), pct, RulesEngine.parseTags(p.getAllergens()),
                new Nutrients(p.getCalories(), p.getProteinG(), p.getCarbsG(), p.getSugarG(), p.getFiberG(),
                        p.getSatFatG(), p.getSodiumMg(), p.getPotassiumMg(), p.getPhosphorusMg()),
                stock == null ? null : view(stock.location()),
                stock == null ? 0 : stock.quantity());
    }
}
