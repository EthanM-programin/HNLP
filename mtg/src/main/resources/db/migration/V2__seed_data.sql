-- V2__seed_data.sql : seed catalog, rules matrix, bins, starter lots, 5 personas.
-- Source: team seed generated with Gemini, first run by hand in the Neon SQL editor.
-- Guarded so it is a no-op on a database that was already seeded by hand (Neon),
-- and a full seed on a fresh database (teammates, Render, judges).
DO $seed$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM product) THEN
    -- =========================================================================
    -- 1. DIETARY TIERS MASTER
    -- =========================================================================
    INSERT INTO dietary_tier (id, code, name, description, produce_lb_per_person, protein_lb_per_person, dairy_units_per_person, grain_lb_per_person) VALUES
    (1, 'DIABETIC_STANDARD', 'Diabetic & Low-Glycemic Tier', 'Controls blood sugar with complex carbs and low sugar limits.', 3.50, 2.00, 1.00, 1.50),
    (2, 'RENAL_RESTRICTED', 'Renal & Kidney Health Tier', 'Restricts potassium, phosphorus, and sodium for chronic kidney disease.', 3.00, 1.50, 0.50, 1.00),
    (3, 'CARDIO_LOW_SODIUM', 'Cardiovascular & Low-Sodium Tier', 'Low sodium and saturated fat for hypertension and heart disease.', 4.00, 2.00, 1.00, 2.00),
    (4, 'GENERAL_WELLNESS', 'General Low-Barrier Wellness Tier', 'Standard nutritional support with no strict chronic medical restrictions.', 3.00, 2.00, 1.00, 2.00);

    -- =========================================================================
    -- 2. CLINICAL CONDITIONS MASTER MATRIX
    -- =========================================================================
    INSERT INTO clinical_condition (id, icd10_prefix, name, dietary_tier_id, max_sodium_mg, max_sugar_g, max_potassium_mg, max_phosphorus_mg, max_sat_fat_g, source_note) VALUES
    (1, 'E11', 'Type 2 Diabetes Mellitus', 1, 2300, 25.00, 4000, 1000, 15.00, 'AHA/ADA diabetes guidelines'),
    (2, 'I10', 'Essential (Primary) Hypertension', 3, 1500, 50.00, 3500, 1200, 12.00, 'DASH sodium restriction'),
    (3, 'N18', 'Chronic Kidney Disease (Stage 3+)', 2, 2000, 30.00, 2000, 800, 10.00, 'Renal nutrition restriction'),
    (4, 'Z91.01', 'Severe Peanut Allergy', 4, 4000, 100.00, 4000, 1500, 20.00, 'Allergen exclusion protocol');

    -- =========================================================================
    -- 3. WAREHOUSE BIN LOCATIONS
    -- =========================================================================
    INSERT INTO bin_location (id, zone, aisle, shelf, bin, is_floor) VALUES
    (1, 'Produce', '01', 1, 1, true),
    (2, 'Produce', '01', 1, 2, true),
    (3, 'Pantry Dry Goods', '02', 2, 3, false),
    (4, 'Pantry Dry Goods', '02', 2, 4, false),
    (5, 'Canned Goods', '03', 1, 1, false),
    (6, 'Canned Goods', '03', 1, 2, false),
    (7, 'Freezers', '04', 3, 1, false),
    (8, 'Freezers', '04', 3, 2, false);

    -- =========================================================================
    -- 4. 100-ITEM PRODUCT CATALOG (Full 1 to 100 Range)
    -- =========================================================================
    INSERT INTO product (id, sku, barcode, name, category, brand, serving_desc, serving_g, calories, protein_g, carbs_g, sugar_g, fiber_g, sat_fat_g, sodium_mg, potassium_mg, phosphorus_mg, allergens, ingredients, weight_lb) VALUES
    -- Produce (1 - 25)
    (1, 'SKU-PROD-001', '071234567801', 'Fresh Organic Spinach', 'Produce', 'Epic Farms', '2 cups', 60.0, 14, 2.0, 2.0, 0.4, 2.0, 0.0, 25, 558, 49, '', 'Organic Spinach', 1.0),
    (2, 'SKU-PROD-002', '071234567802', 'Roma Tomatoes', 'Produce', 'SunRipe', '1 medium', 60.0, 11, 0.5, 2.4, 1.5, 0.8, 0.0, 3, 145, 14, '', 'Fresh Tomatoes', 0.5),
    (3, 'SKU-PROD-003', '071234567803', 'Hass Avocados', 'Produce', 'Calavo', '1/2 fruit', 68.0, 114, 1.3, 6.0, 0.7, 4.6, 0.2, 5, 345, 37, '', 'Avocado', 0.3),
    (4, 'SKU-PROD-004', '071234567804', 'Carrots (Bag)', 'Produce', 'Earthbound', '1 cup sliced', 122.0, 50, 1.1, 12.0, 5.7, 3.6, 0.0, 88, 390, 43, '', 'Carrots', 2.0),
    (5, 'SKU-PROD-005', '071234567805', 'Broccoli Crowns', 'Produce', 'Green Giant', '1 cup chopped', 91.0, 31, 2.5, 6.0, 1.5, 2.4, 0.0, 30, 288, 60, '', 'Broccoli', 1.0),
    (6, 'SKU-PROD-006', '071234567806', 'Yellow Onions', 'Produce', 'Local Farm', '1 medium', 110.0, 44, 1.2, 10.3, 4.7, 1.9, 0.0, 4, 155, 32, '', 'Onion', 1.0),
    (7, 'SKU-PROD-007', '071234567807', 'Russet Potatoes', 'Produce', 'Idaho Gold', '1 medium baked', 173.0, 161, 4.3, 36.6, 1.2, 3.8, 0.1, 17, 926, 120, '', 'Potato', 3.0),
    (8, 'SKU-PROD-008', '071234567808', 'Sweet Potatoes', 'Produce', 'Southern Jewel', '1 medium baked', 114.0, 103, 2.3, 23.6, 7.4, 3.8, 0.1, 41, 475, 54, '', 'Sweet Potato', 2.0),
    (9, 'SKU-PROD-009', '071234567809', 'Celery Stalks', 'Produce', 'Dole', '1 stalk', 40.0, 6, 0.3, 1.2, 0.5, 0.6, 0.0, 32, 104, 10, '', 'Celery', 1.0),
    (10, 'SKU-PROD-010', '071234567810', 'Bell Peppers (Red)', 'Produce', 'SunSelect', '1 medium', 119.0, 37, 1.0, 7.0, 5.0, 2.5, 0.0, 4, 250, 26, '', 'Red Bell Pepper', 0.8),
    (11, 'SKU-PROD-011', '071234567811', 'Zucchini Squash', 'Produce', 'Farm Fresh', '1 medium', 196.0, 33, 2.4, 6.0, 4.2, 2.0, 0.0, 18, 512, 76, '', 'Zucchini', 1.0),
    (12, 'SKU-PROD-012', '071234567812', 'Cucumber', 'Produce', 'Local Harvest', '1/2 cup sliced', 52.0, 8, 0.3, 2.0, 0.9, 0.3, 0.0, 1, 76, 12, '', 'Cucumber', 0.5),
    (13, 'SKU-PROD-013', '071234567813', 'Iceberg Lettuce', 'Produce', 'SaladMaster', '1 wedge', 90.0, 13, 0.8, 2.6, 1.8, 1.0, 0.0, 9, 131, 18, '', 'Lettuce', 0.8),
    (14, 'SKU-PROD-014', '071234567814', 'Kale Greens', 'Produce', 'Organic Life', '1 cup chopped', 21.0, 7, 0.6, 1.4, 0.3, 1.0, 0.0, 8, 79, 11, '', 'Kale', 0.5),
    (15, 'SKU-PROD-015', '071234567815', 'Baby Carrots', 'Produce', 'Grimmway', '10 carrots', 85.0, 35, 0.8, 8.0, 4.0, 2.4, 0.0, 65, 270, 30, '', 'Carrots', 1.0),
    (16, 'SKU-PROD-016', '071234567816', 'Green Cabbage', 'Produce', 'Farm Fresh', '1 cup shredded', 70.0, 17, 1.0, 4.0, 2.2, 1.4, 0.0, 12, 135, 18, '', 'Cabbage', 1.5),
    (17, 'SKU-PROD-017', '071234567817', 'Cauliflower Florets', 'Produce', 'Green Giant', '1 cup', 100.0, 25, 2.0, 5.0, 2.0, 2.0, 0.0, 30, 300, 44, '', 'Cauliflower', 1.2),
    (18, 'SKU-PROD-018', '071234567818', 'Fresh Mushrooms (Cremini)', 'Produce', 'Mushroom Co', '4 mushrooms', 72.0, 22, 2.2, 3.3, 1.0, 1.0, 0.0, 4, 305, 86, '', 'Mushrooms', 0.5),
    (19, 'SKU-PROD-019', '071234567819', 'Asparagus Spears', 'Produce', 'SunRipe', '6 spears', 90.0, 20, 2.2, 3.7, 1.2, 1.8, 0.0, 0, 182, 51, '', 'Asparagus', 1.0),
    (20, 'SKU-PROD-020', '071234567820', 'Brussels Sprouts', 'Produce', 'Local Harvest', '1/2 cup', 78.0, 28, 2.0, 5.5, 1.2, 2.0, 0.0, 11, 290, 53, '', 'Brussels Sprouts', 1.0),
    (21, 'SKU-PROD-021', '071234567821', 'Fresh Strawberries', 'Produce', 'BerryBest', '1 cup sliced', 166.0, 53, 1.1, 12.7, 8.1, 3.3, 0.0, 2, 254, 35, '', 'Strawberries', 1.0),
    (22, 'SKU-PROD-022', '071234567822', 'Blueberries', 'Produce', 'BerryBest', '1/2 cup', 74.0, 42, 0.5, 10.7, 7.4, 1.8, 0.0, 1, 57, 9, '', 'Blueberries', 0.8),
    (23, 'SKU-PROD-023', '071234567823', 'Bananas', 'Produce', 'Dole', '1 medium', 118.0, 105, 1.3, 27.0, 14.4, 3.1, 0.0, 1, 422, 26, '', 'Banana', 0.5),
    (24, 'SKU-PROD-024', '071234567824', 'Honeycrisp Apples', 'Produce', 'TreeTop', '1 medium', 182.0, 95, 0.5, 25.0, 19.0, 4.4, 0.0, 2, 195, 20, '', 'Apple', 0.5),
    (25, 'SKU-PROD-025', '071234567825', 'Navel Oranges', 'Produce', 'CitrusSun', '1 medium', 131.0, 69, 1.2, 17.6, 12.0, 3.4, 0.0, 0, 232, 18, '', 'Orange', 0.5),

    -- Canned Goods (26 - 50)
    (26, 'SKU-PROD-026', '071234567826', 'Low-Sodium Canned Black Beans', 'Canned Goods', 'Healthy Harvest', '1/2 cup', 130.0, 110, 7.0, 20.0, 1.0, 6.0, 0.0, 140, 305, 120, '', 'Black Beans, Water', 1.2),
    (27, 'SKU-PROD-027', '071234567827', 'Low-Sodium Chickpeas', 'Canned Goods', 'Healthy Harvest', '1/2 cup', 130.0, 120, 6.0, 22.0, 1.0, 5.0, 0.0, 110, 240, 130, '', 'Chickpeas, Water', 1.2),
    (28, 'SKU-PROD-028', '071234567828', 'No-Salt Diced Tomatoes', 'Canned Goods', 'Muir Glen', '1/2 cup', 121.0, 25, 1.0, 5.0, 3.0, 1.0, 0.0, 15, 230, 35, '', 'Organic Tomatoes', 1.0),
    (29, 'SKU-PROD-029', '071234567829', 'Canned Tuna in Water (Low Sodium)', 'Canned Goods', 'Bumble Bee', '3 oz', 85.0, 70, 15.0, 0.0, 0.0, 0.0, 0.5, 180, 200, 180, 'Fish', 'Tuna, Water', 0.5),
    (30, 'SKU-PROD-030', '071234567830', 'Canned Pink Salmon', 'Canned Goods', 'Chicken of the Sea', '3 oz', 85.0, 90, 13.0, 0.0, 0.0, 0.0, 1.5, 210, 220, 200, 'Fish', 'Pink Salmon, Water', 0.6),
    (31, 'SKU-PROD-031', '071234567831', 'Low-Sodium Pinto Beans', 'Canned Goods', 'Healthy Harvest', '1/2 cup', 130.0, 105, 6.5, 19.0, 0.8, 5.5, 0.0, 120, 280, 110, '', 'Pinto Beans, Water', 1.2),
    (32, 'SKU-PROD-032', '071234567832', 'Low-Sodium Kidney Beans', 'Canned Goods', 'Healthy Harvest', '1/2 cup', 130.0, 110, 7.0, 20.0, 0.5, 6.0, 0.0, 130, 310, 130, '', 'Kidney Beans, Water', 1.2),
    (33, 'SKU-PROD-033', '071234567833', 'Canned Green Beans (No Salt)', 'Canned Goods', 'Del Monte', '1/2 cup', 120.0, 20, 1.0, 4.0, 1.0, 2.0, 0.0, 10, 110, 20, '', 'Green Beans, Water', 1.0),
    (34, 'SKU-PROD-034', '071234567834', 'Canned Sweet Corn (No Salt)', 'Canned Goods', 'Del Monte', '1/2 cup', 125.0, 60, 2.0, 14.0, 3.0, 2.0, 0.0, 15, 150, 40, '', 'Corn, Water', 1.0),
    (35, 'SKU-PROD-035', '071234567835', 'Canned Carrots (No Salt)', 'Canned Goods', 'Del Monte', '1/2 cup', 120.0, 25, 0.5, 6.0, 3.0, 2.0, 0.0, 25, 180, 25, '', 'Carrots, Water', 1.0),
    (36, 'SKU-PROD-036', '071234567836', 'Canned Pumpkin Puree', 'Canned Goods', 'Libbys', '1/2 cup', 122.0, 40, 1.5, 10.0, 4.0, 4.0, 0.0, 5, 250, 35, '', 'Pumpkin', 1.0),
    (37, 'SKU-PROD-037', '071234567837', 'Canned Crushed Tomatoes', 'Canned Goods', 'Muir Glen', '1/2 cup', 121.0, 30, 1.2, 6.0, 4.0, 1.5, 0.0, 20, 240, 30, '', 'Tomatoes', 1.0),
    (38, 'SKU-PROD-038', '071234567838', 'Tomato Paste (Low Sodium)', 'Canned Goods', 'Contadina', '2 tbsp', 32.0, 30, 1.0, 7.0, 4.0, 1.0, 0.0, 10, 300, 40, '', 'Tomato Paste', 0.5),
    (39, 'SKU-PROD-039', '071234567839', 'Canned Lentils', 'Canned Goods', 'Healthy Harvest', '1/2 cup', 130.0, 115, 8.0, 20.0, 1.0, 4.0, 0.0, 110, 369, 180, '', 'Lentils, Water', 1.2),
    (40, 'SKU-PROD-040', '071234567840', 'Canned Sardines in Olive Oil', 'Canned Goods', 'King Oscar', '3 oz', 85.0, 170, 18.0, 0.0, 0.0, 0.0, 3.0, 300, 350, 250, 'Fish', 'Sardines, Olive Oil, Salt', 0.5),
    (41, 'SKU-PROD-041', '071234567841', 'Canned Sliced Beets', 'Canned Goods', 'Del Monte', '1/2 cup', 120.0, 35, 1.0, 8.0, 6.0, 2.0, 0.0, 150, 200, 30, '', 'Beets, Water', 1.0),
    (42, 'SKU-PROD-042', '071234567842', 'Canned Mixed Vegetables', 'Canned Goods', 'Del Monte', '1/2 cup', 120.0, 35, 1.5, 7.0, 3.0, 2.0, 0.0, 140, 190, 35, '', 'Carrots, Corn, Peas', 1.0),
    (43, 'SKU-PROD-043', '071234567843', 'Canned Peas (No Salt)', 'Canned Goods', 'Del Monte', '1/2 cup', 120.0, 60, 4.0, 11.0, 4.0, 3.0, 0.0, 10, 150, 60, '', 'Peas, Water', 1.0),
    (44, 'SKU-PROD-044', '071234567844', 'Canned White Beans (Cannellini)', 'Canned Goods', 'Healthy Harvest', '1/2 cup', 130.0, 125, 8.0, 22.0, 0.8, 6.0, 0.0, 130, 340, 140, '', 'Cannellini Beans', 1.2),
    (45, 'SKU-PROD-045', '071234567845', 'Canned Chicken Breast', 'Canned Goods', 'Swanson', '3 oz', 85.0, 70, 15.0, 0.0, 0.0, 0.0, 1.0, 210, 180, 150, '', 'Chicken Breast, Water', 0.8),
    (46, 'SKU-PROD-046', '071234567846', 'Canned Peaches in Water', 'Canned Goods', 'Dole', '1/2 cup', 124.0, 35, 0.5, 9.0, 8.0, 1.5, 0.0, 5, 110, 15, '', 'Peaches, Water', 1.0),
    (47, 'SKU-PROD-047', '071234567847', 'Canned Pears in Water', 'Canned Goods', 'Dole', '1/2 cup', 124.0, 40, 0.4, 10.0, 9.0, 1.5, 0.0, 5, 100, 15, '', 'Pears, Water', 1.0),
    (48, 'SKU-PROD-048', '071234567848', 'Canned Mandarin Oranges', 'Canned Goods', 'Dole', '1/2 cup', 120.0, 35, 0.6, 9.0, 8.0, 1.0, 0.0, 5, 120, 15, '', 'Mandarin Oranges', 1.0),
    (49, 'SKU-PROD-049', '071234567849', 'Low-Sodium Vegetable Broth', 'Canned Goods', 'Swanson', '1 cup', 240.0, 15, 1.0, 3.0, 2.0, 0.0, 0.0, 140, 260, 40, '', 'Vegetable Broth', 2.0),
    (50, 'SKU-PROD-050', '071234567850', 'Low-Sodium Chicken Broth', 'Canned Goods', 'Swanson', '1 cup', 240.0, 15, 2.0, 1.0, 0.0, 0.0, 0.0, 140, 180, 50, '', 'Chicken Broth', 2.0),

    -- Pantry Grains & Dry Goods (51 - 75)
    (51, 'SKU-PROD-051', '071234567851', 'Whole Grain Brown Rice', 'Pantry', 'Harvest Gold', '1/4 cup dry', 45.0, 160, 3.5, 33.0, 0.5, 2.0, 0.2, 5, 154, 150, '', 'Brown Rice', 2.0),
    (52, 'SKU-PROD-052', '071234567852', 'Rolled Oats (Old Fashioned)', 'Pantry', 'Quaker Style', '1/2 cup dry', 40.0, 150, 5.0, 27.0, 1.0, 4.0, 0.5, 0, 150, 180, '', 'Rolled Oats', 2.0),
    (53, 'SKU-PROD-053', '071234567853', 'Organic Quinoa', 'Pantry', 'Ancient Harvest', '1/4 cup dry', 45.0, 160, 6.0, 29.0, 1.0, 3.0, 0.5, 0, 210, 200, '', 'Quinoa', 1.5),
    (54, 'SKU-PROD-054', '071234567854', 'Whole Wheat Pasta', 'Pantry', 'Barilla', '2 oz dry', 56.0, 200, 7.0, 37.0, 1.0, 6.0, 0.5, 0, 190, 160, 'Wheat', 'Whole Wheat Flour', 1.0),
    (55, 'SKU-PROD-055', '071234567855', 'Raw Unsalted Almonds', 'Pantry', 'Nature Select', '1 oz', 28.0, 160, 6.0, 6.0, 1.2, 3.5, 1.0, 0, 210, 135, 'Tree Nuts', 'Almonds', 0.75),
    (56, 'SKU-PROD-056', '071234567856', 'Raw Walnuts', 'Pantry', 'Nature Select', '1 oz', 28.0, 185, 4.3, 3.9, 0.7, 1.9, 1.5, 1, 125, 98, 'Tree Nuts', 'Walnuts', 0.75),
    (57, 'SKU-PROD-057', '071234567857', 'Chia Seeds', 'Pantry', 'Nutiva', '1 oz', 28.0, 138, 4.7, 12.0, 0.0, 10.0, 1.0, 5, 115, 244, '', 'Chia Seeds', 1.0),
    (58, 'SKU-PROD-058', '071234567858', 'Ground Flaxseed', 'Pantry', 'Bob Red Mill', '2 tbsp', 14.0, 70, 3.0, 4.0, 0.0, 3.0, 0.5, 5, 115, 90, '', 'Flaxseed', 1.0),
    (59, 'SKU-PROD-059', '071234567859', 'Dry Lentils (Brown)', 'Pantry', 'Bob Red Mill', '1/4 cup dry', 48.0, 160, 12.0, 28.0, 1.0, 8.0, 0.5, 5, 300, 180, '', 'Brown Lentils', 1.5),
    (60, 'SKU-PROD-060', '071234567860', 'Dry Green Split Peas', 'Pantry', 'Bob Red Mill', '1/4 cup dry', 48.0, 160, 12.0, 29.0, 2.0, 8.0, 0.5, 10, 310, 190, '', 'Split Peas', 1.5),
    (61, 'SKU-PROD-061', '071234567861', 'Whole Wheat Flour', 'Pantry', 'Gold Medal', '1/4 cup', 30.0, 100, 4.0, 21.0, 0.4, 3.0, 0.2, 0, 110, 100, 'Wheat', 'Whole Wheat Flour', 2.0),
    (62, 'SKU-PROD-062', '071234567862', 'Barley (Hulled)', 'Pantry', 'Bob Red Mill', '1/4 cup dry', 48.0, 170, 6.0, 34.0, 0.5, 8.0, 0.3, 5, 140, 110, 'Wheat', 'Barley', 1.5),
    (63, 'SKU-PROD-063', '071234567863', 'Bulgur Wheat', 'Pantry', 'Bob Red Mill', '1/4 cup dry', 38.0, 140, 5.0, 31.0, 0.2, 6.0, 0.1, 5, 120, 90, 'Wheat', 'Bulgur', 1.5),
    (64, 'SKU-PROD-064', '071234567864', 'Amaranth Grain', 'Pantry', 'Ancient Harvest', '1/4 cup dry', 38.0, 140, 5.0, 25.0, 1.0, 4.0, 0.5, 5, 150, 150, '', 'Amaranth', 1.0),
    (65, 'SKU-PROD-065', '071234567865', 'Millet Grain', 'Pantry', 'Bob Red Mill', '1/4 cup dry', 48.0, 160, 5.0, 30.0, 0.5, 3.0, 0.5, 2, 100, 140, '', 'Millet', 1.5),
    (66, 'SKU-PROD-066', '071234567866', 'Pumpkin Seeds (Pepitas)', 'Pantry', 'Enjoy Life', '1 oz', 28.0, 160, 9.0, 3.0, 1.0, 2.0, 2.5, 5, 230, 340, '', 'Pumpkin Seeds', 0.5),
    (67, 'SKU-PROD-067', '071234567867', 'Sunflower Seeds', 'Pantry', 'David Seeds', '1 oz', 28.0, 165, 5.5, 6.0, 1.0, 3.0, 1.5, 1, 180, 200, '', 'Sunflower Seeds', 0.5),
    (68, 'SKU-PROD-068', '071234567868', 'Extra Virgin Olive Oil', 'Pantry', 'California Olive', '1 tbsp', 14.0, 120, 0.0, 0.0, 0.0, 0.0, 2.0, 0, 0, 0, '', 'Olive Oil', 2.0),
    (69, 'SKU-PROD-069', '071234567869', 'Avocado Oil', 'Pantry', 'Chosen Foods', '1 tbsp', 14.0, 120, 0.0, 0.0, 0.0, 0.0, 1.5, 0, 0, 0, '', 'Avocado Oil', 2.0),
    (70, 'SKU-PROD-070', '071234567870', 'Apple Cider Vinegar', 'Pantry', 'Bragg', '1 tbsp', 15.0, 3, 0.0, 0.1, 0.0, 0.0, 0.0, 1, 11, 2, '', 'Apple Cider Vinegar', 1.0),
    (71, 'SKU-PROD-071', '071234567871', 'Balsamic Vinegar', 'Pantry', 'Colavita', '1 tbsp', 15.0, 20, 0.0, 4.0, 3.5, 0.0, 0.0, 4, 30, 5, '', 'Wine Vinegar', 1.0),
    (72, 'SKU-PROD-072', '071234567872', 'Raw Honey', 'Pantry', 'Local Bee', '1 tbsp', 21.0, 60, 0.0, 17.0, 17.0, 0.0, 0.0, 1, 11, 1, '', 'Honey', 1.0),
    (73, 'SKU-PROD-073', '071234567873', 'Natural Peanut Butter (No Salt)', 'Pantry', 'Smuckers Natural', '2 tbsp', 32.0, 200, 8.0, 6.0, 2.0, 2.0, 2.5, 5, 200, 130, 'Peanuts', 'Peanuts', 1.0),
    (74, 'SKU-PROD-074', '071234567874', 'Almond Butter (Unsalted)', 'Pantry', 'MaraNatha', '2 tbsp', 32.0, 190, 7.0, 6.0, 1.5, 3.0, 1.5, 0, 240, 150, 'Tree Nuts', 'Almonds', 1.0),
    (75, 'SKU-PROD-075', '071234567875', 'Brown Rice Cakes', 'Pantry', 'Lundberg', '1 cake', 9.0, 35, 1.0, 7.0, 0.0, 1.0, 0.0, 0, 25, 30, '', 'Brown Rice', 0.5),

    -- Proteins & Dairy Alternatives (76 - 100)
    (76, 'SKU-PROD-076', '071234567876', 'Wild Caught Salmon Fillet', 'Protein', 'Ocean Fresh', '4 oz', 113.0, 200, 22.0, 0.0, 0.0, 0.0, 1.5, 60, 490, 250, 'Fish', 'Salmon', 1.0),
    (77, 'SKU-PROD-077', '071234567877', 'Chicken Breast (Boneless)', 'Protein', 'Perdue', '4 oz', 113.0, 120, 26.0, 0.0, 0.0, 0.0, 0.5, 65, 300, 200, '', 'Chicken Breast', 1.5),
    (78, 'SKU-PROD-078', '071234567878', 'Extra Firm Organic Tofu', 'Protein', 'Nasoya', '3 oz', 85.0, 80, 9.0, 2.0, 0.0, 1.0, 0.5, 5, 120, 100, 'Soy', 'Soybeans, Calcium Sulfate', 1.0),
    (79, 'SKU-PROD-079', '071234567879', 'Low-Fat Plain Greek Yogurt', 'Dairy', 'Chobani', '3/4 cup', 170.0, 100, 15.0, 6.0, 6.0, 0.0, 0.0, 55, 220, 180, 'Dairy', 'Cultured Nonfat Milk', 1.0),
    (80, 'SKU-PROD-080', '071234567880', 'Unsweetened Almond Milk', 'Dairy', 'Silk', '1 cup', 240.0, 30, 1.0, 1.0, 0.0, 1.0, 0.0, 150, 160, 20, 'Tree Nuts', 'Filtered Water, Almonds', 2.0),
    (81, 'SKU-PROD-081', '071234567881', 'Lean Ground Turkey (93/7)', 'Protein', 'Butterball', '4 oz', 113.0, 170, 21.0, 0.0, 0.0, 0.0, 2.5, 85, 250, 180, '', 'Turkey', 1.5),
    (82, 'SKU-PROD-082', '071234567882', 'Cod Fillet', 'Protein', 'Ocean Fresh', '4 oz', 113.0, 90, 20.0, 0.0, 0.0, 0.0, 0.2, 70, 300, 170, 'Fish', 'Cod', 1.0),
    (83, 'SKU-PROD-083', '071234567883', 'Tilapia Fillet', 'Protein', 'Ocean Fresh', '4 oz', 113.0, 110, 22.0, 0.0, 0.0, 0.0, 0.5, 50, 380, 160, 'Fish', 'Tilapia', 1.0),
    (84, 'SKU-PROD-084', '071234567884', 'Tempeh (Organic Soybean)', 'Protein', 'Lightlife', '3 oz', 84.0, 140, 16.0, 8.0, 0.0, 3.0, 1.0, 10, 250, 200, 'Soy', 'Soybeans, Rice', 0.8),
    (85, 'SKU-PROD-085', '071234567885', 'Seitan Wheat Protein', 'Protein', 'Sweet Earth', '3 oz', 85.0, 120, 21.0, 4.0, 0.0, 1.0, 0.5, 280, 50, 60, 'Wheat', 'Vital Wheat Gluten', 0.8),
    (86, 'SKU-PROD-086', '071234567886', 'Organic Eggs (Large)', 'Protein', 'Vital Farms', '1 egg', 50.0, 70, 6.0, 0.4, 0.2, 0.0, 1.5, 70, 60, 95, 'Eggs', 'Egg', 1.0),
    (87, 'SKU-PROD-087', '071234567887', 'Kefir (Plain Low-Fat)', 'Dairy', 'LifeWay', '1 cup', 240.0, 110, 11.0, 12.0, 12.0, 0.0, 1.0, 125, 380, 300, 'Dairy', 'Cultured Pasteurized Lowfat Milk', 2.0),
    (88, 'SKU-PROD-088', '071234567888', 'Cottage Cheese (Low-Fat)', 'Dairy', 'Daisy', '1/2 cup', 113.0, 90, 14.0, 5.0, 5.0, 0.0, 1.0, 350, 150, 140, 'Dairy', 'Milk, Cream, Salt', 1.0),
    (89, 'SKU-PROD-089', '071234567889', 'Plain Soy Milk (Unsweetened)', 'Dairy', 'WestSoy', '1 cup', 240.0, 90, 9.0, 4.0, 1.0, 2.0, 0.5, 10, 300, 120, 'Soy', 'Filtered Water, Organic Soybeans', 2.0),
    (90, 'SKU-PROD-090', '071234567890', 'Oat Milk (Unsweetened)', 'Dairy', 'Oatly', '1 cup', 240.0, 80, 3.0, 12.0, 2.0, 2.0, 0.5, 100, 350, 110, '', 'Oat Base, Water', 2.0),
    (91, 'SKU-PROD-091', '071234567891', 'Chicken Thighs (Boneless)', 'Protein', 'Perdue', '4 oz', 113.0, 170, 20.0, 0.0, 0.0, 0.0, 3.5, 80, 230, 170, '', 'Chicken Thigh', 1.5),
    (92, 'SKU-PROD-092', '071234567892', 'Lean Pork Chops', 'Protein', 'Smithfield', '4 oz', 113.0, 160, 24.0, 0.0, 0.0, 0.0, 2.0, 60, 350, 220, '', 'Pork', 1.5),
    (93, 'SKU-PROD-093', '071234567893', 'Grass-Fed Sirloin Steak', 'Protein', 'Local Butcher', '4 oz', 113.0, 180, 26.0, 0.0, 0.0, 0.0, 2.5, 55, 410, 210, '', 'Beef Sirloin', 1.5),
    (94, 'SKU-PROD-094', '071234567894', 'Shrimp (Peeled & Deveined)', 'Protein', 'Ocean Fresh', '4 oz', 113.0, 80, 18.0, 0.0, 0.0, 0.0, 0.2, 140, 150, 120, 'Shellfish', 'Shrimp', 1.0),
    (95, 'SKU-PROD-095', '071234567895', 'Scallops', 'Protein', 'Ocean Fresh', '4 oz', 113.0, 100, 20.0, 3.0, 0.0, 0.0, 0.2, 280, 310, 190, 'Shellfish', 'Scallops', 1.0),
    (96, 'SKU-PROD-096', '071234567896', 'Edamame (Frozen Shelled)', 'Protein', 'Seapoint Farms', '1/2 cup', 78.0, 90, 9.0, 8.0, 2.0, 4.0, 0.5, 10, 300, 140, 'Soy', 'Edamame Soybeans', 1.0),
    (97, 'SKU-PROD-097', '071234567897', 'Black Eyed Peas (Dry)', 'Pantry', 'Bob Red Mill', '1/4 cup dry', 50.0, 160, 11.0, 29.0, 4.0, 7.0, 0.5, 10, 350, 160, '', 'Black Eyed Peas', 1.5),
    (98, 'SKU-PROD-098', '071234567898', 'Red Kidney Beans (Dry)', 'Pantry', 'Bob Red Mill', '1/4 cup dry', 50.0, 170, 11.0, 30.0, 2.0, 8.0, 0.5, 10, 400, 170, '', 'Red Kidney Beans', 1.5),
    (99, 'SKU-PROD-099', '071234567899', 'Green Split Peas (Dry)', 'Pantry', 'Bob Red Mill', '1/4 cup dry', 50.0, 170, 12.0, 30.0, 2.0, 8.0, 0.5, 10, 360, 180, '', 'Split Peas', 1.5),
    (100, 'SKU-PROD-100', '071234567800', 'Whole Grain Cornmeal', 'Pantry', 'Bob Red Mill', '1/4 cup', 38.0, 130, 3.0, 27.0, 1.0, 2.0, 0.5, 0, 110, 100, '', 'Cornmeal', 1.5);

    -- =========================================================================
    -- 5. FIFO INVENTORY LOTS
    -- =========================================================================
    INSERT INTO inventory_lot (product_id, location_id, quantity_remaining, received_at, expires_at, funding_stream) VALUES
    (1, 1, 75, CURRENT_TIMESTAMP, CURRENT_DATE + INTERVAL '5 days', 'USDA TEFAP'),
    (26, 5, 200, CURRENT_TIMESTAMP, CURRENT_DATE + INTERVAL '180 days', 'Feeding Florida Grant'),
    (51, 3, 150, CURRENT_TIMESTAMP, CURRENT_DATE + INTERVAL '365 days', 'USDA TEFAP'),
    (76, 7, 50, CURRENT_TIMESTAMP, CURRENT_DATE + INTERVAL '10 days', 'Local Seafood Rescue');

    -- =========================================================================
    -- 6. EXACTLY 5 SAMPLE CLIENTS & MEDICAL PROFILES
    -- =========================================================================
    INSERT INTO client (id, client_code, first_name, last_name, phone, household_size, enrollment_status, enrollment_expires_on) VALUES
    (1, 'CLI-1001', 'Eleanor', 'Vance', '904-555-0142', 2, 'ACTIVE', CURRENT_DATE + INTERVAL '6 months'),
    (2, 'CLI-1002', 'Marcus', 'Brody', '904-555-0198', 4, 'ACTIVE', CURRENT_DATE + INTERVAL '1 year'),
    (3, 'CLI-1003', 'Sarah', 'Jenkins', '904-555-0233', 1, 'ACTIVE', CURRENT_DATE + INTERVAL '6 months'),
    (4, 'CLI-1004', 'David', 'Ortiz', '904-555-0451', 3, 'ACTIVE', CURRENT_DATE + INTERVAL '1 year'),
    (5, 'CLI-1005', 'Aisha', 'Patel', '904-555-0789', 2, 'ACTIVE', CURRENT_DATE + INTERVAL '6 months');
    -- client_medical_profile rows are NOT inserted here: PHI must be AES-GCM encrypted,
    -- so ProfileSeeder.java writes them through the JPA converter at startup.
  END IF;
END
$seed$;
