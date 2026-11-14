-- V3__seed_fixes.sql : fixes found reviewing the V2 seed, so the demo flows actually work.
--   1. Reset ID sequences (V2 inserted explicit ids, so the next app insert would collide on id 1).
--   2. Remove plaintext PHI rows (ProfileSeeder.java re-creates them AES-GCM encrypted).
--   3. Convert clinical ceilings from DAILY limits to PER-SERVING limits (the rules engine compares per serving).
--   4. Normalise allergen tags to one vocabulary: PEANUT, TREE_NUT, MILK, EGG, WHEAT, SOY, FISH, SHELLFISH, SESAME.
--   5. Give every product a floor home bin + a back-stockroom bin, and stock (V2 only stocked 4 of 100).
--   6. Set restock thresholds so the US-4.2 restock route has items on it.
--   7. Seed biometric history (baseline, 3-month, 6-month) for the Epic 5 outcomes dashboard.

-- 1. Sequences --------------------------------------------------------------
SELECT setval(pg_get_serial_sequence('dietary_tier','id'),       COALESCE((SELECT MAX(id) FROM dietary_tier), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('clinical_condition','id'), COALESCE((SELECT MAX(id) FROM clinical_condition), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('bin_location','id'),       COALESCE((SELECT MAX(id) FROM bin_location), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('product','id'),            COALESCE((SELECT MAX(id) FROM product), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('client','id'),             COALESCE((SELECT MAX(id) FROM client), 0) + 1, false);

-- 2. Plaintext PHI ---------------------------------------------------------
-- Encrypted values are base64 and never start with '['. The hand-run seed stored raw JSON like ["E11","I10"].
DELETE FROM client_medical_profile
 WHERE icd10_codes_enc LIKE '[%' OR allergies_enc LIKE '[%';

-- 3. Per-serving clinical ceilings (DRAFT: team must verify against the cited guidance) ---------
-- Daily guidance -> per-serving ceiling. NULL = no limit for that nutrient. Strictest (MIN) wins across conditions.
-- Flags: GREEN < 80% of every ceiling, YELLOW 80-100%, RED > 100% or any allergen match.
UPDATE clinical_condition SET
  max_sodium_mg = NULL, max_sugar_g = 15.00, max_carbs_g = 45.00, max_potassium_mg = NULL,
  max_phosphorus_mg = NULL, max_sat_fat_g = 4.00,
  source_note = 'ADA Standards of Care: ~45 g carbohydrate per meal, limit added/total sugars; per-serving ceilings are team assumptions'
WHERE icd10_prefix = 'E11';

UPDATE clinical_condition SET
  max_sodium_mg = 300, max_sugar_g = NULL, max_carbs_g = NULL, max_potassium_mg = NULL,
  max_phosphorus_mg = NULL, max_sat_fat_g = 3.00,
  source_note = 'DASH / AHA: 1,500 mg sodium/day (~300 mg per food serving), saturated fat <6% of calories (~13 g/day)'
WHERE icd10_prefix = 'I10';

UPDATE clinical_condition SET
  max_sodium_mg = 300, max_sugar_g = NULL, max_carbs_g = NULL, max_potassium_mg = 250,
  max_phosphorus_mg = 200, max_sat_fat_g = NULL,
  source_note = 'NKF (CKD 3+): sodium <2,000 mg/day, low-potassium foods <200-250 mg/serving, phosphorus 800-1,000 mg/day'
WHERE icd10_prefix = 'N18';

-- Allergies are enforced from the client's allergen list, not as nutrient ceilings.
UPDATE clinical_condition SET
  max_sodium_mg = NULL, max_sugar_g = NULL, max_carbs_g = NULL, max_potassium_mg = NULL,
  max_phosphorus_mg = NULL, max_sat_fat_g = NULL,
  source_note = 'Allergy code: enforced as a PEANUT allergen exclusion (any match = RED), no nutrient ceilings'
WHERE icd10_prefix = 'Z91.01';

-- 4. Allergen vocabulary -----------------------------------------------------
UPDATE product SET allergens = CASE allergens
    WHEN 'Fish'      THEN 'FISH'
    WHEN 'Tree Nuts' THEN 'TREE_NUT'
    WHEN 'Wheat'     THEN 'WHEAT'
    WHEN 'Peanuts'   THEN 'PEANUT'
    WHEN 'Dairy'     THEN 'MILK'
    WHEN 'Soy'       THEN 'SOY'
    WHEN 'Eggs'      THEN 'EGG'
    WHEN 'Shellfish' THEN 'SHELLFISH'
    ELSE NULLIF(allergens, '')
  END;

-- 5. Bins and stock ------------------------------------------------------------
-- Warehouse grid: aisle 01 Produce, 02 Pantry Dry Goods, 03 Canned Goods, 04 Cold & Frozen (protein/dairy);
-- 4 shelves x 8 bins per aisle on the floor. Back stockroom = aisle 'SR', shelf = matching floor aisle.
UPDATE bin_location SET is_floor = TRUE WHERE aisle IN ('01','02','03','04') AND is_floor = FALSE;

CREATE TEMP TABLE product_home AS
SELECT p.id AS product_id, h.aisle, h.zone,
       (ROW_NUMBER() OVER (PARTITION BY h.aisle ORDER BY p.id) - 1) AS idx
  FROM product p
  JOIN (VALUES ('Produce','01','Produce'),
               ('Pantry','02','Pantry Dry Goods'),
               ('Canned Goods','03','Canned Goods'),
               ('Protein','04','Freezers'),
               ('Dairy','04','Freezers')) AS h(category, aisle, zone)
    ON h.category = p.category;

INSERT INTO bin_location (zone, aisle, shelf, bin, is_floor)
SELECT zone, aisle, idx / 8 + 1, idx % 8 + 1, TRUE FROM product_home
ON CONFLICT (aisle, shelf, bin, is_floor) DO NOTHING;

INSERT INTO bin_location (zone, aisle, shelf, bin, is_floor)
SELECT 'STOCKROOM', 'SR', aisle::int, idx + 1, FALSE FROM product_home
ON CONFLICT (aisle, shelf, bin, is_floor) DO NOTHING;

-- Move the 4 starter lots from V2 into their product's home floor bin.
UPDATE inventory_lot l SET location_id = b.id
  FROM product_home h
  JOIN bin_location b ON b.aisle = h.aisle AND b.shelf = h.idx / 8 + 1 AND b.bin = h.idx % 8 + 1 AND b.is_floor
 WHERE l.product_id = h.product_id;

-- Floor lot for every product that has none. Every 7th product is low on the floor (restock route demo).
-- Perishables get short expiries so several land inside the 7-day waste window (US-4.3).
INSERT INTO inventory_lot (product_id, location_id, quantity_remaining, received_at, expires_at, funding_stream)
SELECT p.id, b.id,
       CASE WHEN p.id % 7 = 0 THEN 3 ELSE 20 + (p.id * 13) % 40 END,
       now() - make_interval(days => (p.id % 10)::int),
       CASE p.category
         WHEN 'Produce'      THEN CURRENT_DATE + (2 + p.id % 12)::int
         WHEN 'Protein'      THEN CURRENT_DATE + (4 + p.id % 20)::int
         WHEN 'Dairy'        THEN CURRENT_DATE + (6 + p.id % 18)::int
         WHEN 'Canned Goods' THEN CURRENT_DATE + (180 + (p.id * 37) % 540)::int
         ELSE                     CURRENT_DATE + (120 + (p.id * 29) % 300)::int
       END,
       (ARRAY['USDA TEFAP','Feeding Florida Grant','Local Grocery Rescue','Community Donations'])[1 + p.id % 4]
  FROM product p
  JOIN product_home h ON h.product_id = p.id
  JOIN bin_location b ON b.aisle = h.aisle AND b.shelf = h.idx / 8 + 1 AND b.bin = h.idx % 8 + 1 AND b.is_floor
 WHERE NOT EXISTS (SELECT 1 FROM inventory_lot l WHERE l.product_id = p.id);

-- Back-stockroom lot for every product (newer arrivals, later expiry), the source for restocking.
INSERT INTO inventory_lot (product_id, location_id, quantity_remaining, received_at, expires_at, funding_stream)
SELECT p.id, b.id,
       40 + (p.id * 7) % 60,
       now() - make_interval(days => 1),
       CASE p.category
         WHEN 'Produce'      THEN CURRENT_DATE + (9 + p.id % 10)::int
         WHEN 'Protein'      THEN CURRENT_DATE + (14 + p.id % 20)::int
         WHEN 'Dairy'        THEN CURRENT_DATE + (14 + p.id % 18)::int
         WHEN 'Canned Goods' THEN CURRENT_DATE + (365 + (p.id * 37) % 540)::int
         ELSE                     CURRENT_DATE + (240 + (p.id * 29) % 300)::int
       END,
       (ARRAY['Feeding Florida Grant','USDA TEFAP','Community Donations','Local Grocery Rescue'])[1 + p.id % 4]
  FROM product p
  JOIN product_home h ON h.product_id = p.id
  JOIN bin_location b ON b.aisle = 'SR' AND b.shelf = h.aisle::int AND b.bin = h.idx + 1 AND NOT b.is_floor;

DROP TABLE product_home;

-- 6. Restock thresholds (floor units) -------------------------------------------
UPDATE product SET min_floor_threshold = CASE category WHEN 'Produce' THEN 15 ELSE 10 END;

-- 7. Biometric history for the 5 personas (A1C %, systolic BP mmHg, eGFR mL/min) ---------
INSERT INTO biometric_reading (client_id, metric, reading_value, recorded_on)
SELECT c.id, v.metric, v.reading_value, CURRENT_DATE - v.days_ago
  FROM (VALUES
    ('CLI-1001','A1C',8.4,180),('CLI-1001','A1C',7.9,90),('CLI-1001','A1C',7.3,0),
    ('CLI-1001','SYSTOLIC_BP',148,180),('CLI-1001','SYSTOLIC_BP',140,90),('CLI-1001','SYSTOLIC_BP',134,0),
    ('CLI-1002','EGFR',42,180),('CLI-1002','EGFR',43,90),('CLI-1002','EGFR',44,0),
    ('CLI-1002','SYSTOLIC_BP',138,180),('CLI-1002','SYSTOLIC_BP',134,90),('CLI-1002','SYSTOLIC_BP',131,0),
    ('CLI-1003','SYSTOLIC_BP',152,180),('CLI-1003','SYSTOLIC_BP',144,90),('CLI-1003','SYSTOLIC_BP',136,0),
    ('CLI-1004','A1C',9.1,180),('CLI-1004','A1C',8.2,90),('CLI-1004','A1C',7.6,0),
    ('CLI-1005','A1C',8.0,180),('CLI-1005','A1C',7.7,90),('CLI-1005','A1C',7.4,0),
    ('CLI-1005','EGFR',38,180),('CLI-1005','EGFR',38,90),('CLI-1005','EGFR',39,0)
  ) AS v(client_code, metric, reading_value, days_ago)
  JOIN client c ON c.client_code = v.client_code
 WHERE NOT EXISTS (SELECT 1 FROM biometric_reading);
