HNLP: Medically Tailored Grocery (MTG) System
Design, Verification & Team Report · 2026 NestforAwhile AI-Augmented Software Engineering Challenge · Team HNLP (HopefullyNotLastPlacers)
Evidence at a Glance
Paste screenshots here (images first, per judging emphasis on execution evidence).
Figure 1: Demo barcode sheet (paste the "Demo barcodes" page image here). Printable sheet used to demo the shopper scan flow. Each card shows what each of the five sample clients should see for that item.
Item
SKU
Eleanor
Marcus
Sarah
David
Aisha
Raw Unsalted Almonds
071234567855
Green
Red
Green
Green
Yellow
Fresh Organic Spinach
071234567801
Green
Red
Green
Green
Red
Cottage Cheese (Low-Fat)
071234567888
Red
Red
Red
Green
Red
Raw Honey
071234567872
Red
Green
Green
Red
Red
Bananas
071234567823
Yellow
Red
Green
Yellow
Red
Shrimp (Peeled & Deveined)
071234567894
Green
Green
Red
Green
Green
Low-Fat Plain Greek Yogurt
071234567879
Green
Yellow
Green
Green
Red
Low-Sodium Canned Black Beans
071234567826
Green
Red
Green
Green
Red
Chicken Breast (Boneless)
071234567877
Green
Green
Green
Green
Red
Roma Tomatoes
071234567802
Green
Green
Green
Green
Green
Cucumber
071234567812
Green
Green
Green
Green
Green
Organic Eggs (Large)
071234567886
Green
Green
Green
Green
Green
Tip: raise screen brightness and hold the phone 15–25 cm from the screen. Camera scanning needs Chrome on Android; on iPhone, type the number under the barcode.
Figure 2: Check-in, scan, and hard-stop run (paste the PowerShell screenshot). Client CLI-1002 (Marcus, Renal & Kidney Health tier) checks in, scans Raw Unsalted Almonds (flagged RED, tree nut), and checkout validation returns HARD_STOP.
Figure 3: AI harness results (paste the harness screenshots). Five persona runs; zero restricted items served; harness reports passed: True.
Figure 4: Running application (paste the Spring Boot startup / project structure screenshot).
1. Project Summary
The MTG system is an in-person, client-choice warehouse application for Epic Cure (St. Augustine, FL). It lets clients with chronic conditions (Type 2 Diabetes, Hypertension, Chronic Kidney Disease) shop for groceries while the software enforces clinical guardrails and offers culinary guidance.
It is built around two layers:
• System of Record: a deterministic Spring Boot web application and Postgres database that holds clients, encrypted medical profiles, inventory, sessions, and checkout logs.
• System of Intelligence: an open-source LLM (via Groq's free tier) that only writes recipe suggestions. It never decides whether a food is safe.
2. Core Design Principle: Safety Is Deterministic, AI Is Advisory
All clinical approve/restrict decisions are made by a backend rules engine, never by the LLM. A hallucination therefore cannot approve a dangerous food.
• The LLM receives only already-approved (Green), in-stock items plus the client's dietary constraints.
• Its output is parsed, schema-validated, and checked against the client's Red list and live stock before it reaches the UI.
• If the model is slow (over 3 seconds), rate-limited, or returns bad JSON, the system returns a deterministic fallback recipe so the warehouse floor never stalls.
3. Architecture
Layer
Technology
Notes
Presentation
Kiosk, shopper phone page, volunteer and manager screens
Kiosk and shopper pages public; volunteer and manager pages behind login
Application
Java / Spring Boot (Maven)
Controllers: SessionController, WarehouseController; services for check-in, queue, stock, restock, checkout, analytics
Rules engine
RulesEngine, Ruleset, Flag
Pure deterministic logic; unit-tested
AI integration
GroqClient, RecipeEngine, RecipeValidator, FallbackRecipes
Open-weight model via Groq; validator plus fallback
Database
Neon Postgres, Flyway migrations V1 (schema), V2 (seed data), V3 (seed fixes)
PHI columns encrypted (EncryptedStringConverter)
Deployment
Render (Docker), Neon
Secrets supplied only through environment variables
4. Clinical Rules Engine
At check-in, the engine takes all of a client's active conditions, applies the strictest (minimum) limit per nutrient, and unions all allergens into one session ruleset. Thresholds are defined per serving. Each catalog item is rated against that ruleset:
Flag
Rule
Effect
Red (Restricted)
Contains a restricted allergen, or exceeds 100% of any limit
Blocks checkout (hard stop)
Yellow (Capped)
Within 80–100% of a limit
Allowed up to a household-scaled quantity
Green (Approved)
Below 80% of all limits
Recommended
Example (CLI-1002, Marcus, Renal & Kidney Health tier, household of 4): sodium at most 300 mg per serving, potassium at most 250 mg per serving, phosphorus at most 200 mg per serving, avoid tree nuts. Grocery targets: 12 lb produce, 6 lb protein, 2 dairy units, 4 lb grain (tier amounts × household size).
Data model note: the earlier plan used a client_conditions junction table. The final implementation stores encrypted client medical profiles (ClientMedicalProfile) linked to ClinicalCondition and DietaryTier, with biometric readings tracked in BiometricReading.
5. Key Workflows
1.  Check-in (kiosk): verify the client is enrolled and not expired, expire sessions idle for 45 minutes, and count active shoppers. If the floor is at capacity (10), the client is queued as WAITING and shown a position ("You are #3 in line"); otherwise the session becomes ACTIVE with limits and the Red list saved on it.
2.  Recommendations: the best Green, in-stock items with aisle, shelf, and bin locations.
3.  Barcode scan: a pure database lookup (no AI call) returns color, reason, and location, updates the activity heartbeat, and adds the item to the cart. Manual SKU entry is also supported.
4.  Recipe suggestions: triggered by the "Suggest recipes" button (cached per cart), validated, and backed by a fallback.
5.  Checkout (volunteer): validate the cart; any Red item returns HARD_STOP and highlights the item. Removing an item is logged in checkout_override. Complete stays locked until no Red items remain. Completion decrements stock from the earliest-expiring lots in one atomic transaction, writes checkout_log and checkout_line, and promotes the next queued client.
6.  Restock list: products below shelf minimums plus lots expiring within 7 days, in aisle and bin order.
7.  Analytics (Epic 5): anonymized average change in blood sugar, blood pressure, and kidney function at baseline, 3 months, and 6 months; pounds distributed per funding stream; CSV export.
6. AI Tooling and Prompt Strategy
Tool
Used for
Claude
Architecture planning, rules-engine design, debugging, documentation
Gemini
Research and brainstorming
VS Code Copilot
Code generation and in-editor iteration
Groq (free tier, open-weight model)
Runtime recipe synthesis (replaced the originally planned OpenRouter)
Prompts are logged in prompts.md with the author of each prompt, covering evolution, iterations, and reflections.
7. Verification & Validation
Unit tests: RulesEngineTest (five personas, e.g. CLI-1002 gets almonds and spinach as Red), FallbackRecipesTest, RecipeValidatorTest.
AI safety harness: GET /api/admin/ai-harness?runs=N runs a sample cart for each persona and confirms no recipe contains a restricted item.
Metric
Result
Runs
5
AI-served responses
0
Fallbacks used
5
Restricted items served
0
Average latency
279 ms
Overall
Passed
Note: in this run every case used the fallback (HTTP_ERROR), because the AI endpoint was unavailable (API key not set during the test). This confirms the failure path works and stays safe. Before presenting, re-run the harness with the key configured so at least some AI-served recipes are validated.
Manual end-to-end check: check-in → scan almonds (RED) → checkout validation → HARD_STOP.
8. Team Roles
Member
Responsibilities
Ethan
Backend, database, AI layer: rules engine, check-in and queue, scan, recommendations, checkout and FIFO transactions, restock list, recipe engine, validation harness
Teammate (add name)
Frontend (kiosk, shopper, volunteer checkout, restock screens), analytics dashboard, security configuration, Dockerfile and Render deployment
Working agreements: agree the endpoint list (exact URLs and JSON) up front; log prompts with authors; pull before pushing; include tool and stage in every commit message.
9. Remaining Checklist
• Re-run the AI harness with the API key set and capture the output.
• Confirm the public Render URL and run the full demo end to end.
• Complete README sections, reflection, and slides; rehearse (7 min + 5 min Q&A).
• Make the GitHub repo public and share it with organizers.
• Upload the package to https://bit.ly/26NestforAwhileSol by 9:00 AM, Nov 15.
10. Security Note
The original notes contained live credentials (database password, encryption key, Groq API key). They have been removed from this document. Store them only as environment variables (DB_URL, DB_USER, DB_PASS, ENCRYPTION_KEY, GROQ_API_KEY) and rotate all of them, since they appeared in shared notes and screenshots.
Run locally: set the environment variables above, then from the mtg folder run .\mvnw.cmd spring-boot:run.

Tests for backend Invoke-RestMethod "http://localhost:8080/api/admin/ai-harness?runs=1" | Select-Object runs, aiServed, fallbacks, restrictedCaughtByValidator, servedRestrictedViolations, avgLatencyMs, passed

Tests for backend (Invoke-RestMethod "http://localhost:8080/api/admin/ai-harness?runs=1").cases | Select-Object persona, source, failure, latencyMs


