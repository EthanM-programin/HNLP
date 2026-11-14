
# AI Prompt History & Iteration Log: HNLP

**Team:** HNLP / HopefullyNotLastPlacers (Ethan Manuel & Aleksander Ara)
**Challenge:** 2026 UNF NestforAwhile AI-Augmented Software Engineering Challenge

| AI collaborator | Who used it | For |
|---|---|---|
| **Gemini** | Ethan (also the first seed SQL draft) | Architecture stress-testing ("GrillMe"), repo/role coordination, schema & rules verification, security hygiene, README |
| **Claude (web)** | Aleksander, with Ethan reviewing the outputs | Planning, design-doc review, schema & entities, backend and frontend code generation, debugging help, docs |
| **GitHub Copilot** | TODO: Ethan | Inline edits and debugging |
| **Groq `openai/gpt-oss-20b`** | Runtime | Recipe synthesis inside the app |

**Ownership:** Ethan owned the **backend, database and AI integration** (design, verification, testing, debugging, the Groq integration). Aleksander owned the **frontend, kiosk/scanner flows, security and deployment**. Code for both tracks was generated through the Claude web conversation; Ethan cross-checked the backend with Gemini and ran, tested and fixed it on his own machine. Tool tags match our commit messages: `[Claude web]`, `[Gemini]`, `[Copilot]`, `[Manual]`.

---

## Phase 1: Planning and architecture

### 1.1 Kickoff and epic plan
- **Tool / author:** Claude web, Aleksander
- **Prompt:** "im making an app help me make a springboot in vs code", then with the problem case PDF: "before i set it up lets plan looking at the cases/epics"
- **Result:** Stack recommendation (Spring Boot, Postgres on Neon, open-weight LLM, Render), epic dependency map (Epic 2 rules → Epic 1 → Epic 3 → Epic 4 → Epic 5), and a 24-hour build order. Flagged rules: no third-party AI extensions, keep a prompt log, safety must be deterministic.

### 1.2 Architectural stress-test ("GrillMe" method)
- **Tool / author:** Gemini, Ethan
- **Prompt:** *"Stress-test our initial architecture for the Epic Cure Medically Tailored Groceries (MTG) warehouse system. Challenge our assumptions around AI safety, clinical restrictions, warehouse concurrency, and offline/slow network resilience."*
- **Decisions that came out of it** (became the core of our design doc, `docs.md`):
  - **Zero-LLM clinical safety:** System of Record (deterministic rules engine) separated from System of Intelligence (open-weight LLM). The LLM never approves food; it only writes recipes from pre-approved Green items.
  - **ICD-10 nutrient intersection:** strictest (MIN) ceiling per nutrient across a client's conditions, union of allergens, per serving.
  - **Decoupled scanning and recipes:** scans are pure database lookups (< 50 ms); LLM calls are gated, with a timeout fallback.
  - **Self-healing queue and FIFO locking:** 45-minute inactivity expiry for ACTIVE sessions; atomic lot-level decrements with `SELECT … FOR UPDATE`.

---

## Phase 2: Repo, environment and role split

### 2.1 Repo, Neon and environment setup
- **Tool / author:** Claude web, Aleksander
- **Prompts:** "tell me how to put my project file to main in git…", `src refspec main does not match any`, "how do i make a neon database", "will i have to put the password and database keys in everytime…"
- **Result:** Git fixes (branch rename, wrong remote owner, unrelated histories), JDBC URL conversion, `${ENV}` placeholders, `setx` for permanent variables.
- **Iteration:** the first "it works" was wrong. The log showed `jdbc:h2:mem`, meaning Spring had silently fallen back to H2. We learned to check the actual database line in the log, not just "Started".

### 2.2 Repository sync and role division
- **Tool / author:** Gemini, Ethan
- **Prompts:** *"Is there anyway I can easily share all of the github files in our repository to you? https://github.com/EthanM-programin/HNLP — … while waiting for him to finish setting up the database files is there anything I can work on or clean up?"* then *"…walk me through get the repository opened on my VsCode then we can brainstorm how to split up the rest of the project?"*
- **Outcome:** Repo cloned locally; parallel tracks agreed: **Ethan, backend / database / AI**; **Aleksander, frontend / kiosk / security / deployment**.

---

## Phase 3: Design review and schema

### 3.1 Design doc review
- **Tool / author:** Claude web, Aleksander (doc written by both of us from the Phase 1 decisions)
- **Prompt:** pasted our "Doc Consistency Report".
- **Issues raised:** Red ">20% over" vs Yellow "80–100%" left 100–120% unflagged; ceilings are daily in clinical guidance but we compare per serving; a plain `client_conditions` junction table would expose diagnoses (conflicts with encrypted PHI); a recipe cache keyed only on the cart would share recipes between clients with different conditions; `JSON.parse()` should be Jackson in Spring; OpenRouter/GCP might create financial liability.
- **What we changed:** Red > 100%; encrypted profile + frozen ruleset snapshot instead of the junction table; cache key = cart + ruleset hash; Groq instead of OpenRouter; Render instead of GCP.

### 3.2 Extra requirements
- **Tool / author:** Claude web, Aleksander
- **Prompt:** "add stock for the product if someone tries to checkout something they shouldnt and also put in where the item is at, and also a way to check for if there is a peak capacity right now…"
- **Result:** lot-based stock, a bin-location grid, an audited checkout-override table, and three capacity options with trade-offs.

### 3.3 Schema generation folded with our queue/FIFO design
- **Tool / author:** Claude web, Aleksander, from Ethan's Phase 1 design
- **Prompts:** "now give me the file for this and where to put it and do it", then our "Self-Healing Capacity Queue / Lot-Level FIFO / Hard-Stop" section: "add these changes too"
- **Result:** `V1__schema.sql`, 15 entities, AES-GCM `EncryptedStringConverter`, FIFO and queue repositories. The second version replaced `queue_entry` with WAITING sessions + `queue_position` + `last_activity_at`, as our design specified. Applied on the first run.

---

## Phase 4: Seed data

### 4.1 Seed SQL (first draft)
- **Tool / author:** Gemini, team (TODO: who). Run manually in the Neon SQL editor.

### 4.2 Seed review and fixes
- **Tool / author:** Claude web, Aleksander
- **Prompt:** shared the Gemini SQL: "this is the sql"
- **Found (verified on a local Postgres):** limits were daily but compared per serving, so **all 100 products were Green for every persona**; only 4 products had stock; medical profiles were plaintext JSON; explicit ids without resetting sequences.
- **Changed:** `V2__seed_data.sql` (guarded, no-op on our seeded database), `V3__seed_fixes.sql` (per-serving ceilings, stock for every product, allergen vocabulary, restock thresholds, biometric history), `ProfileSeeder.java` (encrypted profiles).
- **Result:** CKD persona 37 Green / 19 Yellow / 44 Red; 17 lots within 7 days of expiry; 14 bins to restock.
- **Lesson:** `setx` only applies to new terminals, so the first re-seed used the old encryption key; we deleted and re-seeded with the shared key.

---

## Phase 5: Backend, database and AI integration (owner: Ethan)

### 5.1 Schema and rules-engine verification
- **Tool / author:** Gemini, Ethan
- **Prompts:** *"Can you access all of these files? Given everything that I've given you so far what would you recommend I work on or do right now…"*, *"…he just finished up another push with his claude ai and asked it to assign us with what we should both be working on, how does this look, and what new files do you need?"*, *"Okay which files do you need here…? [Shared V1__schema.sql, V2__seed_data.sql, V3__seed_fixes.sql, and Client.java]"*
- **Outcome:** Verified the Flyway migrations (V1–V3), the AES-GCM encrypted PHI columns, the per-serving ceilings, the allergen tags, and the 100-item catalog + 5 personas against our Phase 1 design before the services were built on top.

### 5.2 Backend generation
- **Tool / author:** Claude web, sent from Aleksander's session after Ethan agreed. Ethan owned review, testing and fixes.
- **Prompt:** "is there anyway you can do the entire backend rn?" (Claude asked how we'd handle Ethan's role and which LLM provider; answer: Ethan reviews and tests it; Groq.)
- **Result:** Rules engine, check-in/queue, scan, recommendations, Groq client + validator + fallback recipes, FIFO checkout with hard-stop, restock route, analytics, AI safety harness, 20 unit tests, `docs/API.md`.
- **Iteration:** the unit tests caught a real bug before delivery. `a == null ? b : (b == null ? a : Math.min(a, b))` unboxes `null` and throws, which would have crashed check-in for every client with a condition.

### 5.3 Running and verifying the backend
- **Tool / author:** Manual, Ethan
- **Result:** Clean start on his machine (56 sources, 13 repositories, schema at v3). Smoke test: check-in Marcus → `RENAL_RESTRICTED` with correct diet focus; scan almonds → `RED` "Contains TREE_NUT"; checkout validate → `HARD_STOP`; AI harness → passed (5/5 fallback before the Groq key was set).
- **Debugging:** after a VS Code restart, `'url' must start with "jdbc"`: environment variables set with `$env:` had been wiped. Fixed with `setx`.

### 5.4 Groq model retired mid-hackathon
- **Tool / author:** Manual + Claude web, Ethan
- **Symptom:** harness showed `HTTP_ERROR` in about 90 ms; a direct PowerShell call returned `404 model_not_found` for `llama-3.1-8b-instant`.
- **Fix (manual edit for integration):** listed the available models and chose `openai/gpt-oss-20b` (open weights, Apache 2.0, fast, JSON mode). Added `reasoning_effort: low` and raised `max_tokens` in `GroqClient` so the model's reasoning doesn't cut off the JSON; timeout 6 s.
- **Result:** harness passed with AI answers. TODO: paste the numbers (aiServed x/5, restrictedCaughtByValidator, avgLatencyMs).
- **Reflection:** the fallback design paid off. While the model was gone, the app still served safe recipes and the harness still passed.

### 5.5 Secrets and judge access
- **Tool / author:** Gemini, Ethan
- **Prompt:** *"Will the judges take points off if we put API keys and database passwords in the application.properties file, so they'll be able to use it? … where can we put the keys and passwords so the judges can access it to test the program capabilities…?"*
- **Outcome:** No secrets in the public repo: `application.properties` uses environment-variable placeholders. The live app runs on Render with variables set in the dashboard; staff demo credentials go privately in the submission notes.

---

## Phase 6: Frontend, security and deployment (owner: Aleksander)

### 6.1 Security and deploy
- **Tool / author:** Claude web, Aleksander
- **Result:** Role-based security (public kiosk/shopper, VOLUNTEER, MANAGER), landing page, `Dockerfile` sized for Render's 512 MB, deploy steps.

### 6.2 Visual design
- **Prompt:** "now the thing is i want to make it look prettier lowk"
- **Approach:** a design specific to Epic Cure rather than a template: Green/Yellow/Red flags as round **produce stickers** (icon + words, not color alone), locations as **kraft shelf tags**, Atkinson Hyperlegible for low-vision readers. Every page was rendered in headless Chrome against a mock API; screenshots caught a CSS class collision (`.stop`) that crushed the hard-stop banner, and a cramped scan result. Both fixed before delivery.
### 6.3 Iterations from using it
| Prompt (Aleksander) | Change |
|---|---|
| "add a add and minus into the cart option and recipie page" | `− qty +` stepper, recipe "Add" buttons, `POST /cart/{productId}` |
| "make it so the max client check in count is 4 so we can test it out" | `V4__floor_capacity_4.sql` |
| "make it so if someone can log out and check out without buying anything and … if they leave the tab they are kicked out" | "End visit" / "Leave the line", close-tab beacon with a 15 s grace (reloads don't kick people out), 10 s heartbeat |
| "if we are logged into the volunteer page it throws a white page error" | Friendly "managers only" page with "Sign in as a manager", JSON 403 for APIs, one-click sign out |
| "make the log in screens prettier" | Custom sign-in page; demo-account hint only while default passwords are in use |
| "make us an barcode example for this" | `/barcodes.html` + printable PDF: 12 Code 128 barcodes with expected flags per persona, decoded 12/12 from a screenshot |

### 6.4 Hosting decision
- **Prompts:** "can we set this up to work on a free google cloud", "is there anything i can use to make this run for free?"
- **Decision:** Google Cloud's free trial requires a card on file, which we judged risky under "zero financial liability". Render (no card) is primary; Azure for Students (no card, UNF email) is the backup.

---

## Phase 7: Documentation and submission

### 7.1 Documentation (Ethan)
- **Tool / author:** Gemini, Ethan
- **Prompts:** *"How can I share all the code we have so far… so you can help me to write the most accurate documentation as possible?"*, *"how do I get the most recent version on the github on my vscode through the terminal again? Can you give me a file of all the prompts I gave you for final submission purposes?"*
- **Outcome:** A README draft covering architecture, safety guardrails, stack and setup; Ethan's half of this log.

### 7.2 Documentation (Aleksander)
- **Tool / author:** Claude web, Aleksander
- **Prompts:** "what do i write in the read me", "Give me a read me thing i can copy, docs.md thing, and prompts.md from this chat", "combine this into the promptmd file…"
- **Outcome:** README sections required by the rules, the updated `docs.md` (assumptions, clinical matrix, personas), and this merged log.

---

## Reflections
- **What worked:** stress-testing the architecture before writing code (Ethan's GrillMe session), then having a second AI critique our design doc; splitting backend and frontend behind an agreed API contract (`docs/API.md`); treating every AI output, including Gemini's seed data and the runtime LLM, as untrusted and checking it with unit tests, a local database, a safety harness and screenshots.
- **What didn't:** short prompts like "seed all the data" produced plausible but wrong data (daily vs per-serving limits). Environment variables (`$env:` vs `setx`) and a retired model cost us more time than any feature.
- **How we adapted:** we asked for verification evidence with every generated piece, used two different AIs to cross-check each other, and logged each iteration here.
- TODO: 2–3 sentences in your own words (a team-authored reflection is required).
