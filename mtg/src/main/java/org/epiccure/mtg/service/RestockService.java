package org.epiccure.mtg.service;

import org.epiccure.mtg.api.ApiModels.*;
import org.epiccure.mtg.model.BinLocation;
import org.epiccure.mtg.model.InventoryLot;
import org.epiccure.mtg.model.Product;
import org.epiccure.mtg.repository.InventoryLotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * US-4.2: aisle-sequenced restock route for floor bins below min_floor_threshold (one pass: aisle, shelf, bin,
 * like walking the floor in a serpentine). US-4.3: lots expiring within 7 days, in the same walking order.
 */
@Service
public class RestockService {

    private final InventoryLotRepository lots;

    public RestockService(InventoryLotRepository lots) { this.lots = lots; }

    @Transactional(readOnly = true)
    public RestockView restock() {
        Map<Long, List<InventoryLot>> floor = new HashMap<>();
        Map<Long, List<InventoryLot>> back = new HashMap<>();
        Map<Long, Product> productsById = new HashMap<>();
        List<ExpiryAlert> expiring = new ArrayList<>();
        LocalDate cutoff = LocalDate.now().plusDays(7);

        for (InventoryLot l : lots.findAllWithLocationAndProduct()) {
            Long pid = l.getProduct().getId();
            productsById.put(pid, l.getProduct());
            (Boolean.TRUE.equals(l.getLocation().getFloor()) ? floor : back).computeIfAbsent(pid, k -> new ArrayList<>()).add(l);
            if (l.getQuantityRemaining() > 0 && l.getExpiresAt() != null && !l.getExpiresAt().isAfter(cutoff)) {
                expiring.add(CheckoutService.alert(l));
            }
        }

        record Pending(Product p, BinLocation floorBin, int floorQty, int threshold, int move, BinLocation from, int backQty) {}
        List<Pending> pending = new ArrayList<>();
        for (Product p : productsById.values()) {
            List<InventoryLot> f = floor.getOrDefault(p.getId(), List.of());
            if (f.isEmpty()) continue;
            int floorQty = f.stream().mapToInt(InventoryLot::getQuantityRemaining).sum();
            int threshold = p.getMinFloorThreshold() == null ? 0 : p.getMinFloorThreshold();
            if (floorQty >= threshold) continue;

            List<InventoryLot> b = new ArrayList<>(back.getOrDefault(p.getId(), List.of()));
            b.removeIf(l -> l.getQuantityRemaining() <= 0);
            b.sort(StockService.FIFO);                          // send the oldest stockroom lot out first
            int backQty = b.stream().mapToInt(InventoryLot::getQuantityRemaining).sum();
            int target = threshold * 2;                         // refill to twice the minimum
            int move = Math.min(target - floorQty, backQty);
            BinLocation home = f.stream().min(StockService.FIFO).get().getLocation();
            pending.add(new Pending(p, home, floorQty, threshold, move, b.isEmpty() ? null : b.get(0).getLocation(), backQty));
        }
        pending.sort(Comparator.comparing((Pending x) -> x.floorBin().getAisle())
                .thenComparing(x -> x.floorBin().getShelf())
                .thenComparing(x -> x.floorBin().getBin()));

        List<RestockTask> route = new ArrayList<>();
        int stop = 1;
        for (Pending x : pending) {
            route.add(new RestockTask(stop++, x.p().getId(), x.p().getSku(), x.p().getName(), StockService.view(x.floorBin()),
                    x.floorQty(), x.threshold(), x.move(), StockService.view(x.from()), x.backQty()));
        }
        expiring.sort(Comparator.comparing(ExpiryAlert::location).thenComparing(ExpiryAlert::expiresAt));
        return new RestockView(route, expiring);
    }
}
