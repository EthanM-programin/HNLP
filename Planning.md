NestForAWhile Doc Consistency Report - 

AI Tools:
Gemini
Claude
VsCode Copilot

Potential/Used Tools:
GCP (Google Cloud)
Digital Ocean (Probably not using)
GrillMe
OpenRouter (Free LLM models)

To Do:
Plan use cases

GitHub Repository:
HNLP (HopefullyNotLastPlacers)

Design Choices and Insights:
To separate hard clinical safety rules from LLM generation so a hallucination can never approve a dangerous food item and the UI stays responsive if the AI endpoint lags or returns bad JSON in the middle of the warehouse we will keep 100% of clinical approval/restriction logic deterministic in the backend rules engine and SQL/database dietary matrix - never letting the LLM decide if a food is medically safe. Pass only the already-approved inventory items and client dietary constraints into the open-source LLM strictly for natural-language recipe synthesis and missing-ingredient suggestions, wrapped in a strict JSON schema validator with a 3-second timeout fallback to pre-cached static recipes if the warehouse signal drops or the model stalls.

When structuring our relational database tables so that multiple ICD-10 diagnoses and allergies dynamically intersect into a single, conflict-free nutrient threshold profile for each shopper-and how we map those thresholds to Approved(Green), Capped(Yellow), and Restricted(Red) flags across our 100-item plus catalog we model a many-to-many junction table (client_conditions) linking clients to clinical_conditions (keyed by ICD-10 code), where each condition defines strict numeric upper/lower nutrient bounds per serving (e.g., max_sodium_mg, max_sugar_g, max_potassium_mg, max_phosphorus_mg) plus restricted allergen tags. At check-in (US-1.1), compute the client’s active session ruleset by taking the strictest(minimum) ceiling across all their active conditions (MIN(max_sodium_mg), MIN(max_sugar_g), etc.) and the union of all allergens. We’ll compare each catalog item’s nutritional profile against that computed ceiling:
Restricted (Red): Contains an allergen OR above 100%(triggers the US-4.1 checkout hard-stop).
Capped (Yellow): Falls within 80%-100% of a ceiling (allowed, up to a household-scaled quantity limit).
Approved (Green): Safely below 80% of all thresholds.

We’ll structure our API request/response pipeline so that barcode scans feel instantaneous, rate limits aren’t blown, and the LLM’s recipe/missing-ingredient output is guaranteed to parse cleanly and point only to in-stock bins by decouple item scanning (US-3.1) from Recipe Synthesis (US-3.3):
1. Zero-LLM Barcode Scans: When a shopper scans a barcode, execute a pure database lookup (<50ms) to return the deterministic Green/Yellow/Red status and update Active_Cart_Manifest - never call the LLM on a raw scan.
2. On-Demand / Debounced Recipe Generation: Trigger the OpenRouter LLM call only at session initialization (US-3.2) and when the user explicitly taps “Suggest Recipes for My Cart” (or after a 5-second cart-change debounce), caching the response per cart-hash in the database.
3. Constrained Payload & Schema Validation: Only pass the top 15-20 in-stock, Green-approved warehouse items (with their exact bin_id ‘s) into the system prompt so the model cannot hallucinate out-of-stock ingredients. Enforce response_format: { type: “json_object” } in the OpenRouter API payload, strip any stray markdown fences before JSON.parse(), validate every returned bin_id against live stock, and fall back to a deterministic SQL ingredient-match recipe if the API times out (>3s) or rate-limits (429).

We will model our Neon Postgres tables and transaction hooks so that floor capacity queues don’t get stuck if a shopper abandons their cart, inventory decrements atomically in FIFO lot order without race conditions, and contraindicated checkout items trigger an auditable volunteer override/removal flow by:
1. Self-Healing Capacity Queue: Track sessions in a shopping_sessions table (status: WAITING, ACTIVE, COMPLETED, EXPIRED) with a last_activity_at heartbeat updated on every barcode scan. Compute Active_Floor_Shoppers dynamically via a query counting ACTIVE sessions where last_activity_ at > NOW() - INTERVAL ‘45 minutes’ (auto-expiring abandoned carts so the floor never deadlocks), and promote the lowest queue_position WAITING session inside a transaction whenever a session completes or expires.
2. Lot-Level Inventory & FIFO Locking: Split products (the 100-item nutritional catalog) from inventory_lots (lot_id, product_id, bin_location, quantity_remaining, received_at, expires_at). Flag lots where expires_at <= CURRENT_DATE + INTERVAL '7 days' for the restocking route (US-4.2, sorted by aisle ASC, bin ASC), and execute checkout decrements inside a single BEGIN ... COMMIT Postgres transaction using SELECT ... FOR UPDATE ordered by expires_at ASC, received_at ASC so concurrent checkouts never oversell a lot.
3. Hard-Stop Checkout Guardrail (US-4.1): Before executing the decrement transaction at the exit terminal, validate every scanned SKU against the session's Restricted (Red) array—if any match, block checkout completion, highlight the offending SKU in red on the volunteer screen, and require it to be removed from the manifest before the commit button unlocks.
Verification & Testing strategy:
1. Database Schema Migration & Seeding:
Run our SQL migrations on Neon to create the core tables: clients, clinical_conditions, client_conditions (junction table), products, inventory_lots, shopping_sessions, and cart_items.
Insert your required seed data: 100-item product catalog with macronutrients and bin locations, Clinical Rules Matrix for core chronic conditions (Diabetes, CKD, Hypertension), and 5 Sample Patient Personas.
2. Backend API Routes (The System of Record):
Check-In Endpoint (US-1.1): Verify client status, check Active_Floor_Shoppers capacity, and compute the active session nutrient ceilings via the MIN() intersection query.
Barcode Scan Endpoint (US-3.1): Pure SQL lookup (<50ms) returning Green / Yellow / Red status flags without touching the LLM.
Atomic Checkout & Hard-Stop (US-4.1): Execute inventory decrement transactions in FIFO lot order (SELECT ... FOR UPDATE) with a hard block if any Restricted (Red) items are present.
3. AI Integration Layer: Implement the OpenRouter API call for In-Cart Recipe Synthesis (US-3.3), wrapped in JSON schema validation and a 3-second fallback timer. 

Team Roles:
	Ethan: backend, database and AI
Rules engine and unit tests (first, ~2 hrs). Take the client’s conditions, find the strictest limit for each nutrient, add their allergens, then rate each product Red (over 100% or an allergen), Yellow (80–100%) or Green (under 80%). Check-in, scan and checkout all use this. Unit-test it with the 5 personas, for example that CLI-1002 gets almonds as Red and spinach as Red.
Check-in (US-1.1, 1.2, 1.3):
Check the client is enrolled and not expired.
Expire sessions with no activity for 45 minutes.
Count active shoppers. If the floor is full, add the client to the queue as WAITING.
Otherwise set the session ACTIVE and save the client’s limits and Red item list on it.
Return grocery targets: the tier amounts × household size.
Barcode scan (US-3.1). Look up the product, use the saved limits to return its color, a reason and its location, update last_activity_at, and add it to the cart. No AI call.
Recommendations (US-3.2). The best Green items that are in stock on the floor, with aisle, shelf and bin.
Checkout (US-4.1, 4.3):
Validate the cart and return HARD_STOP if anything is Red.
Removing an item logs it in checkout_override.
Completing checkout takes stock from the lots that expire first, all in one transaction, and writes checkout_log and checkout_line.
Then let the first person in the queue onto the floor.
Restock list (US-4.2). Products low on the shelves, plus lots expiring within 7 days, in aisle and bin order.
OpenRouter recipes (US-3.3):
Send only Green items that are in stock, plus what’s in the cart.
Strip code fences from the reply, parse it with Jackson, and check every ingredient against the client’s Red list and every bin against live stock.
If it takes longer than 3 seconds or gets rate-limited, return a fallback recipe.
Use only free open-weight models, and never add credits to the account.
Validation script. Run sample carts for each persona and confirm no recipe uses a restricted item. This is your evidence for the Verification score.
You: frontend, kiosk and scanner screens, analytics
Your role leaves two jobs unassigned: security and deployment. Take both now, because nothing else shows up without them.
Security config and Render deploy (first, ~1.5 hrs):
Logins for client, volunteer and manager.
The kiosk and shopper pages public, the volunteer and manager pages behind login.
A Dockerfile, and the environment variables set on Render.
A working public URL.
Check-in kiosk. Enter a client code, then show their name, tier and grocery targets, or “You are #3 in line” if the floor is full.
Shopper phone page:
Recommendations with locations.
Camera barcode scan plus a box to type the SKU by hand.
A big Green/Yellow/Red result with the reason.
The cart, and a “Suggest recipes” button.
Volunteer checkout screen. Scan the cart, show Red items highlighted with a Remove button, and keep Complete locked until nothing Red is left. Then show the receipt.
Restock screen. The route list in walking order, with expiring lots flagged.
Program and grant dashboard (Epic 5), backend and page:
Average change in blood sugar, blood pressure and kidney function at baseline, 3 months and 6 months, with no names or IDs shown.
Pounds given out per funding stream, from checkout_line.
A CSV export.
It doesn’t depend on Ethan’s code, so build it whenever you’re waiting on him.
Until his endpoints are ready, build your pages with fake data.
Together
Now (20 min): agree on the endpoint list, with the exact URLs and JSON for each, and put it in the doc. Then you can work at the same time without waiting on each other.
Two corrections to your doc. First, it still describes a client_conditions link table, but you built encrypted profiles instead, so update the Design Choices section. Second, your thresholds are now per serving, from V3.
Throughout:
Log prompts in prompts.md with who wrote each one.
Pull before you push.
Put the tool and stage in every commit message.
After midnight: test the full demo together, then do the README, reflection, slides and rehearsal.

Diagrams:
Junction table (Database Representation): [clients] 1 --< [client_conditions] >-- 1 [clinical_conditions]

Prompt Iteration & Verification Log:
Maintained in prompts.md to track chronological prompt evolution, iterations, and team reflections for judging compliance.

cd C:\Users\username\HNLP\HNLP\mtg
.\mvnw.cmd spring-boot:run









Tests for backend Invoke-RestMethod "http://localhost:8080/api/admin/ai-harness?runs=1" | Select-Object runs, aiServed, fallbacks, restrictedCaughtByValidator, servedRestrictedViolations, avgLatencyMs, passed

Tests for backend (Invoke-RestMethod "http://localhost:8080/api/admin/ai-harness?runs=1").cases | Select-Object persona, source, failure, latencyMs


        



https://bit.ly/26NestforAwhileSol 
