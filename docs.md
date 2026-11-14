NestForAWhile Design Doc: HNLP (HopefullyNotLastPlacers)

Medically Tailored Grocery (MTG) warehouse system for Epic Cure, St. Augustine, FL.

Team roles
Ethan Manuel: backend, database & AI integration (Neon, seed, check-in, scan, FIFO checkout, Groq AI integration and validation)
Aleksander Ara: frontend, kiosk/scanner flows & analytics (kiosk, shopper companion, volunteer checkout & restock terminal, program & grant dashboard, security, deployment)
Tools
Tool	Used for
Claude (web interface)	Planning, architecture review, schema, backend, frontend, debugging help
Gemini	First draft of the seed SQL (reviewed and corrected afterwards; see prompts.md)
GitHub Copilot (VS Code)	Inline edits / debugging
Groq free tier + openai/gpt-oss-20b	Runtime recipe synthesis (open weights, Apache 2.0)
Neon	Free PostgreSQL
Render	Free hosting (Docker), public URL
Considered, not used	Google Cloud (requires a card on file), DigitalOcean, OpenRouter (daily caps; credits would be paid use), Fly.io / Koyeb (card required)
Design choices and insights
1. Hard clinical rules are separate from LLM generation

100% of approval/restriction logic is deterministic (Java rules engine + the clinical rules matrix in the database). The LLM never decides whether a food is medically safe. It only receives items the rules engine already approved plus the client's cart, and only writes recipes and missing-ingredient suggestions. Its JSON is validated (schema, product ids, restricted list, live stock) and any failure (timeout, 429, bad JSON, every recipe rejected) falls back to deterministic recipes.

2. Multiple diagnoses become one conflict-free threshold profile
Each ICD-10 condition (clinical_condition) defines per-serving ceilings: max_sodium_mg, max_sugar_g, max_carbs_g, max_potassium_mg, max_phosphorus_mg, max_sat_fat_g (NULL = no limit).
At activation the client's ruleset = strictest (MIN) ceiling per nutrient across their conditions + union of allergens.
Flags:
Restricted (Red): contains one of the client's allergens, OR any nutrient above 100% of its ceiling → triggers the US-4.1 checkout hard-stop.
Capped (Yellow): any nutrient at 80–100%, OR a limited nutrient the product doesn't list (can't verify → never Green). Allowed up to household size units.
Approved (Green): every limited nutrient below 80%.
Change from our first draft: Red was "exceeds by >20%", which left 100–120% with no flag. Fixed to Red > 100%.
Change from our first draft: we planned a plain client_conditions junction table. Because PHI must be encrypted and an encrypted code can't be joined, ICD-10 codes and allergies are stored AES-GCM encrypted in client_medical_profile, decrypted once at check-in, and the resulting numbers are frozen on the session (ruleset_snapshot, restricted_skus). Floors (minimums) were dropped for the prototype.
3. Fast scans, safe AI
Zero-LLM barcode scans: a scan is a database lookup + rules engine evaluation; it updates the cart manifest and never calls the LLM.
On-demand recipes: the LLM is called only when the shopper taps "Suggest recipes"; successful results are cached per cart contents + ruleset hash (two clients with the same cart never share recipes).
Constrained payload & validation: only the top 20 Green, in-stock floor items (round-robin across categories) + the non-Red cart items are offered, by numeric id. response_format: json_object; markdown fences are stripped; Jackson parses into records; every returned id is checked against the offered set and the restricted set; bins/aisles are attached from our database, never taken from the model. Timeout 6 s (raised from our planned 3 s for free-tier latency).
No PHI to the LLM: the prompt contains a plain-language diet focus ("low potassium, max 250 mg per serving"), never diagnoses, names or ids.
4. Queue, inventory, and checkout guardrails
Self-healing capacity queue: sessions are WAITING / ACTIVE / COMPLETED / EXPIRED with a last_activity_at heartbeat updated on every scan. Active_Floor_Shoppers is counted (ACTIVE and heartbeat within 45 minutes), never stored. Check-in and promotion serialize on a row lock of the max_floor_capacity setting. Abandoned carts expire; a scheduler promotes the lowest queue_position every minute. Closing the shopper tab ends the visit after a 15 s grace period; "End visit" ends it immediately with no stock change.
Lot-level FIFO: product (catalog) is separate from inventory_lot (quantity, bin, received_at, expires_at, funding_stream). Checkout runs in one transaction with SELECT ... FOR UPDATE ordered by expires_at, received_at, floor bins first; products are locked in id order to avoid deadlocks. Lots expiring within 7 days are flagged.
Hard-stop checkout: the exit manifest (scanned codes, or the digital cart) is re-checked against the frozen ruleset and the Red SKU array. Any Red line blocks completion (the server returns 409 even if the UI were bypassed). Removing an item writes an audit row (checkout_override, action REMOVED, volunteer, reason).
Strategic research & engineering assumptions
Warehouse layout & spatial code
Floor aisles: 01 Produce, 02 Pantry dry goods, 03 Canned goods, 04 Cold & frozen (protein, dairy).
Each aisle: 4 shelves × 8 bins. Location = aisle / shelf / bin, shown to shoppers as a shelf tag.
Back stockroom: aisle SR, shelf = matching floor aisle number, bin = item slot. Restocking = move units from the item's stockroom bin to its floor bin.
Restock route: floor bins below min_floor_threshold (Produce 15, others 10), sorted aisle → shelf → bin (walking order); refill target = 2 × threshold.
Clinical rules matrix (per serving; DRAFT, to be reviewed by a dietitian)
ICD-10	Condition	Sodium	Sugar	Carbs	Potassium	Phosphorus	Sat. fat	Source
E11	Type 2 diabetes	–	15 g	45 g	–	–	4 g	ADA: ~45 g carbohydrate per meal, limit sugars
I10	Hypertension	300 mg	–	–	–	–	3 g	DASH/AHA: 1,500 mg sodium/day; sat. fat < 6% of calories
N18	CKD stage 3+	300 mg	–	–	250 mg	200 mg	–	NKF: sodium < 2,000 mg/day, low-K foods < 200–250 mg/serving, P 800–1,000 mg/day
Z91.01	Peanut allergy	allergen exclusion (PEANUT)						

Allergen vocabulary: PEANUT, TREE_NUT, MILK, EGG, WHEAT, SOY, FISH, SHELLFISH, SESAME.

Dietary tiers (grocery targets per person per visit; × household size)
Tier	Produce lb	Protein lb	Dairy units	Grain lb
DIABETIC_STANDARD	3.5	2.0	1.0	1.5
RENAL_RESTRICTED	3.0	1.5	0.5	1.0
CARDIO_LOW_SODIUM	4.0	2.0	1.0	2.0
GENERAL_WELLNESS	3.0	2.0	1.0	2.0
Patient personas (overlapping chronicity)
Code	Name	Household	Conditions	Allergies	Tier
CLI-1001	Eleanor	2	Diabetes + hypertension	–	Diabetic
CLI-1002	Marcus	4	CKD	Tree nuts	Renal
CLI-1003	Sarah	1	Hypertension	Shellfish	Cardio
CLI-1004	David	3	Diabetes	–	Diabetic
CLI-1005	Aisha	2	CKD + diabetes	Dairy	Renal

Each has 6 months of biometric history (A1C, systolic BP, eGFR) for the outcomes dashboard.

Inventory & product catalog
100 products (produce, canned goods, pantry & grains, protein, dairy) with macros, sodium, sugar, potassium, phosphorus, saturated fat, allergens, serving size, weight, barcode.
Every product has a floor lot and a stockroom lot; perishables get short expiries so the 7-day waste flag has data; every lot carries a funding stream (USDA TEFAP, Feeding Florida Grant, Local Grocery Rescue, Community Donations).
Floor capacity: 4 shoppers (set low so the queue can be demoed with 5 personas; app_setting.max_floor_capacity).
Open-source LLM & validation framework
Model: openai/gpt-oss-20b on Groq free tier (open weights, Apache 2.0). Originally llama-3.1-8b-instant, which Groq retired during the hackathon.
Validation harness: GET /api/admin/ai-harness (also a button on the manager dashboard). For each persona it builds a realistic cart, calls the model, and independently re-checks what would be served. Pass = 0 restricted and 0 out-of-stock items served.
Diagrams

Data model (core)

client 1 ── 1 client_medical_profile (encrypted ICD-10 + allergies) ── * dietary_tier
clinical_condition (ICD-10 prefix → per-serving ceilings)
client 1 ── * shopping_session (WAITING/ACTIVE/COMPLETED/EXPIRED, ruleset_snapshot, restricted_skus)
shopping_session 1 ── * cart_item * ── 1 product
product 1 ── * inventory_lot * ── 1 bin_location (floor or stockroom)
shopping_session 1 ── 1 checkout_log 1 ── * checkout_line ── 1 inventory_lot (funding_stream)
shopping_session 1 ── * checkout_override (REMOVED / OVERRIDDEN, volunteer, reason)
client 1 ── * biometric_reading

Request flow

Kiosk → POST /api/checkin → rules engine → session (ACTIVE or WAITING)
Phone → POST /scan → rules engine → flag + location (no LLM)
Phone → GET /recipes → offered safe items → Groq → validator → recipes or fallback
Volunteer → POST /checkout/validate → OK | HARD_STOP → /remove (audited) → /complete (FIFO, one transaction)
Prompt iteration & verification log

Maintained in prompts.md. API reference in docs/API.md.
