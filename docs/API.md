# MTG Backend API

Base URL locally: `http://localhost:8080`. All bodies are JSON. Errors always look like:

```json
{ "error": "SESSION_NOT_ACTIVE", "message": "Human-readable text you can show on screen", "details": { } }
```

Flags everywhere: `GREEN` (approved), `YELLOW` (capped: allowed up to household size per item), `RED` (restricted: checkout hard-stop).
Every item includes `reasons` (e.g. `"Potassium 558 mg is 223% of your 250 mg limit"`) and a `location`
(`{"zone":"Produce","aisle":"01","shelf":1,"bin":1,"floor":true,"label":"Aisle 01 · Shelf 1 · Bin 1"}`).

Demo client codes: `CLI-1001` … `CLI-1005`.

---

## Epic 1: Kiosk check-in and capacity

### `POST /api/checkin`
```json
{ "clientCode": "CLI-1002" }
```
Returns a **SessionView**:
```json
{
  "sessionToken": "9f2c…", "status": "ACTIVE", "firstName": "Marcus",
  "tierCode": "RENAL_RESTRICTED", "tierName": "Renal & Kidney Health Tier", "householdSize": 4,
  "queuePosition": null,
  "targets": { "produceLb": 12.00, "proteinLb": 6.00, "dairyUnits": 2.00, "grainLb": 4.00 },
  "dietFocus": ["low sodium (max 300 mg per serving)", "low potassium (max 250 mg per serving)", "…", "must avoid allergens: TREE_NUT"],
  "activeShoppers": 3, "maxCapacity": 4
}
```
- `status: "WAITING"` + `queuePosition: 3` when the floor is full → show "You are #3 in line" and poll `GET /api/session/{token}` every ~5 s until it turns `ACTIVE`.
- Checking in again while a visit is open returns the same session.
- Errors: `CLIENT_NOT_FOUND` (404), `ENROLLMENT_INACTIVE` (403).

### `GET /api/session/{token}`
Same SessionView (used for polling the queue).

### `GET /api/capacity`
```json
{ "activeShoppers": 3, "maxCapacity": 4, "waiting": 0, "full": false, "sessionTimeoutMinutes": 45 }
```

---

## Epic 3: Shopper phone

### `GET /api/session/{token}/recommendations`
Best GREEN items currently on the floor, grouped by category (up to 6 each), safest first.
```json
{ "tierCode": "…", "targets": { … }, "dietFocus": [ … ],
  "groups": [ { "category": "Produce", "items": [ ItemView, … ] }, … ] }
```

**ItemView**
```json
{ "productId": 5, "sku": "SKU-PROD-005", "barcode": "071234567805", "name": "Broccoli Crowns",
  "category": "Produce", "servingDesc": "1 cup chopped", "flag": "GREEN", "reasons": [],
  "percentOfLimit": 24, "allergens": [],
  "nutrients": { "calories": 31, "proteinG": 2.5, "carbsG": 6.0, "sugarG": 1.5, "fiberG": 2.4, "satFatG": 0.0,
                 "sodiumMg": 30, "potassiumMg": 288, "phosphorusMg": 60 },
  "location": { "label": "Aisle 01 · Shelf 1 · Bin 5", … }, "floorStock": 42 }
```
`percentOfLimit` is the highest nutrient as a % of its ceiling (null when the item is RED because of an allergen).

### `POST /api/session/{token}/scan`
```json
{ "code": "071234567855", "addToCart": true }
```
`code` = barcode **or** SKU (manual entry when the camera can't read it). `addToCart` defaults to true.
```json
{ "item": ItemView, "addedToCart": true, "cartQuantity": 1, "capReached": false, "yellowCap": null,
  "message": "Not safe for your health plan. Please put this back (a volunteer will have to remove it at checkout).",
  "cart": CartView }
```
RED items are still recorded in the cart (it mirrors the physical cart), so checkout will hard-stop on them.
YELLOW items stop incrementing at `yellowCap` (= household size) and return `capReached: true`.

### `GET /api/session/{token}/cart`
```json
{ "lines": [ { "item": ItemView, "quantity": 2, "yellowCap": null } ], "totalUnits": 2, "totalWeightLb": 2.4, "redCount": 0 }
```

### `POST /api/session/{token}/cart/{productId}`
Adds one unit by product id (the cart "+" button and the recipe "Add" button). Same rules as a scan:
returns a ScanView; YELLOW items stop at the household cap (`capReached: true`).

### `DELETE /api/session/{token}/cart/{productId}?quantity=1`
Removes one unit (or the whole line if `quantity` is omitted). Returns CartView.

### `POST /api/session/{token}/leave`
Shopper taps **End visit** (or **Leave the line** while WAITING). Empties the cart, ends the visit as `COMPLETED`
with no checkout (nothing is taken from stock), and lets the next person in line onto the floor.
`{ "status": "COMPLETED", "itemsReturned": 3, "promotedFromQueue": 1 }`

### `POST /api/session/{token}/away`
Sent by the shopper page with `navigator.sendBeacon` when the tab closes. If no request with that token arrives
within `app.session.leave-grace-seconds` (default 15 s), the visit ends as `EXPIRED` and the spot is freed.
A reload or the page's 10-second heartbeat cancels it.

### `GET /api/session/{token}/recipes?refresh=false`
```json
{
  "source": "AI", "model": "llama-3.1-8b-instant", "latencyMs": 812, "cached": false,
  "recipes": [ {
    "title": "Black Bean & Quinoa Bowl",
    "ingredients": [
      { "productId": 26, "name": "Low-Sodium Canned Black Beans", "inCart": true,  "flag": "GREEN", "location": null },
      { "productId": 53, "name": "Organic Quinoa",                "inCart": false, "flag": "GREEN",
        "location": { "label": "Aisle 02 · Shelf 1 · Bin 3", … } }
    ],
    "steps": ["…"], "whyItFits": "…" } ],
  "aiRawRecipes": 3, "rejected": 1, "restrictedCaught": 0, "rejections": ["Rejected \"…\": …"],
  "failure": null, "note": null
}
```
- `inCart: false` = missing ingredient: show its `location`.
- `source: "FALLBACK"` when the AI is unavailable or every AI recipe failed validation; `failure` says why
  (`NO_KEY`, `TIMEOUT`, `RATE_LIMITED`, `HTTP_ERROR`, `INVALID_JSON`, `ALL_REJECTED`) and `note` is a short message you can show.
- AI results are cached per (client ruleset + cart contents); `refresh=true` forces a new call.

---

## Epic 4: Volunteer checkout and restock

The manifest is the list of codes the volunteer scans at the exit (`scannedCodes`, one entry per unit).
If you send no codes, the session's cart is used.

### `POST /api/checkout/{token}/validate`
```json
{ "scannedCodes": ["071234567826", "071234567826", "071234567855"] }
```
```json
{ "status": "HARD_STOP",
  "lines":      [ { "item": ItemView, "quantity": 2, "inCart": true, "scanned": true }, … ],
  "restricted": [ { "item": { "name": "Raw Unsalted Almonds", "flag": "RED", "reasons": ["Contains TREE_NUT (allergy)"], … }, … } ],
  "mismatches": ["Raw Unsalted Almonds was not scanned in the aisle (not in the digital cart)"],
  "totalUnits": 3, "totalWeightLb": 3.15 }
```
`status` is `OK` or `HARD_STOP`. Keep the Complete button locked while it's `HARD_STOP`; highlight `restricted` lines in red.

### `POST /api/checkout/{token}/remove`
```json
{ "productId": 55, "reason": "Client allergy", "volunteer": "maria", "scannedCodes": [ …current list… ] }
```
Removes the item from the cart and the manifest, writes an audit row (`checkout_override`, action `REMOVED`),
and returns the re-validated **ValidationView**. Drop the item from your local scanned list too.

### `POST /api/checkout/{token}/complete`
```json
{ "scannedCodes": [ … ], "volunteer": "maria" }
```
Re-validates on the server: returns **409** `HARD_STOP` (with the ValidationView in `details`) if anything RED remains.
Otherwise decrements stock FIFO in one transaction and returns the receipt:
```json
{ "checkoutId": 12, "sessionToken": "…", "firstName": "Marcus", "volunteer": "maria", "completedAt": "…",
  "lines": [ { "sku": "SKU-PROD-026", "name": "…", "quantity": 2, "weightLb": 2.4,
               "lots": [ { "lotId": 31, "quantity": 2, "fundingStream": "Feeding Florida Grant", "expiresAt": "…", "location": "Aisle 03 · Shelf 1 · Bin 1" } ],
               "shortfall": 0 } ],
  "totalUnits": 2, "totalWeightLb": 2.4,
  "expiryAlerts": [ { "lotId": 3, "productName": "…", "location": "…", "expiresAt": "…", "daysLeft": 4, "quantityRemaining": 18 } ],
  "promotedFromQueue": 1 }
```

### `GET /api/restock`
```json
{ "route": [ { "stop": 1, "productId": 7, "sku": "…", "name": "Russet Potatoes",
               "floorLocation": { "label": "Aisle 01 · Shelf 1 · Bin 7", … }, "floorQty": 3, "threshold": 15,
               "moveQty": 27, "fromStockroom": { "label": "Stockroom SR-1-7", … }, "stockroomQty": 89 }, … ],
  "expiring": [ ExpiryAlert, … ] }
```
`route` is in walking order (aisle → shelf → bin). `expiring` = lots expiring within 7 days.

---

## Epic 5: Analytics (no names, codes or ids)

- `GET /api/analytics/outcomes` →
  `{ "metrics": [ { "metric": "A1C", "unit": "%", "clients": 3, "baseline": 8.50, "month3": 7.93, "month6": 7.43, "change": -1.07, "changePct": -12.6, "direction": "IMPROVED" } ], "minCohort": 2, "note": "…" }`
  (cohorts below `minCohort` come back as `"direction": "SUPPRESSED"` with null numbers)
- `GET /api/analytics/outcomes.csv`: CSV download
- `GET /api/analytics/grants` →
  `{ "rows": [ { "fundingStream": "USDA TEFAP", "category": "Produce", "units": 14, "pounds": 9.5 } ], "totalPounds": 31.2, "checkouts": 4 }`
- `GET /api/analytics/grants.csv`: CSV download for auditors

---

## Admin / verification

- `POST /api/admin/queue/tick`: expire abandoned sessions and promote the queue now (also runs every minute).
- `GET /api/admin/ai-harness?runs=1`: model validation harness. For each persona: builds a cart, calls the model,
  validates, and independently re-checks what would be served. `passed: true` means zero restricted and zero
  out-of-stock items were served. Each run is one Groq call per persona (mind the free-tier rate limit).

> Security: kiosk + shopper pages and `/api/checkin`, `/api/capacity`, `/api/session/**` are public (no ID, per Epic Cure policy).
> `/volunteer/**`, `/api/checkout/**`, `/api/restock` need the **volunteer** (or manager) login; `/manager/**`, `/api/analytics/**`,
> `/api/admin/**` need the **manager** login. Pages get a login form; scripts can use HTTP Basic, e.g.
> `Invoke-RestMethod http://localhost:8080/api/restock -Credential (Get-Credential volunteer)`.
> Passwords come from `VOLUNTEER_PASSWORD` / `MANAGER_PASSWORD` (local defaults: `volunteer` / `manager`).
