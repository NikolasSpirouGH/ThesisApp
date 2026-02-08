-- =====================================================
-- Standalone Seed Script: Test Data for Sharing Features
-- =====================================================
--
-- USAGE:
--   1. Start the app at least once (so DataInitializer populates algorithms)
--   2. Run this script manually against thesis_db:
--      psql -U postgres -d thesis_db -f seed_sharing_test_data.sql
--
-- IDEMPOTENT: Safe to run multiple times (ON CONFLICT DO NOTHING).
--
-- PREREQUISITES:
--   - V1-V5 Flyway migrations applied
--   - DataInitializer has run (algorithms table populated by Weka reflection)
--
-- DATA CREATED:
-- | Entity    | ID | Owner    | Description                      |
-- |-----------|----|----------|----------------------------------|
-- | Dataset   | 1  | nickriz  | Iris dataset (classification)    |
-- | Dataset   | 2  | maria_ds | Housing dataset (regression)     |
-- | Dataset   | 3  | alex_ml  | Customer dataset (clustering)    |
-- | Training  | 1  | nickriz  | Completed RF on Iris             |
-- | Training  | 2  | maria_ds | Completed LR on Housing          |
-- | Training  | 3  | alex_ml  | Completed RF on Customer         |
-- | Model     | 1  | nickriz  | Iris Classifier (finalized)      |
-- | Model     | 2  | maria_ds | Housing Predictor (finalized)    |
-- | Model     | 3  | alex_ml  | Customer Segmenter (finalized)   |
--
-- SHARING TEST MATRIX:
-- ┌─────────────┬──────────────────┬────────────────────────────────┐
-- │ User        │ Owns             │ Can Share With                 │
-- ├─────────────┼──────────────────┼────────────────────────────────┤
-- │ nickriz     │ DS:1, M:1, T:1   │ Direct users, Group 1 (leader) │
-- │ maria_ds    │ DS:2, M:2, T:2   │ Direct users, Group 1 (member),│
-- │             │                  │ Group 3 (leader)               │
-- │ alex_ml     │ DS:3, M:3, T:3   │ Direct users, Group 1 (member),│
-- │             │                  │ Group 3 (member)               │
-- │ bigspy      │ (admin override) │ Can manage any share (ADMIN)   │
-- └─────────────┴──────────────────┴────────────────────────────────┘
-- =====================================================

-- ============================================
-- 1. ALGORITHM CONFIGURATIONS (one per user)
-- ============================================
-- Algorithms are auto-populated by DataInitializer (Weka reflection).
-- We reference them by class_name to get the correct dynamic IDs.

-- nickriz uses RandomForest (classification)
INSERT INTO algorithm_configurations (id, algorithm_id, options, algorithm_type_id, user_id)
SELECT 1,
       (SELECT id FROM algorithms WHERE class_name = 'weka.classifiers.trees.RandomForest' LIMIT 1),
       '-I 100 -K 0',
       (SELECT id FROM CONST_ALGORITHM_TYPES WHERE name = 'CLASSIFICATION'),
       (SELECT id FROM users WHERE username = 'nickriz')
WHERE EXISTS (SELECT 1 FROM algorithms WHERE class_name = 'weka.classifiers.trees.RandomForest')
ON CONFLICT (id) DO NOTHING;

-- maria_ds uses LinearRegression (regression)
INSERT INTO algorithm_configurations (id, algorithm_id, options, algorithm_type_id, user_id)
SELECT 2,
       (SELECT id FROM algorithms WHERE class_name = 'weka.classifiers.functions.LinearRegression' LIMIT 1),
       '-S 0 -R 1.0E-8',
       (SELECT id FROM CONST_ALGORITHM_TYPES WHERE name = 'REGRESSION'),
       (SELECT id FROM users WHERE username = 'maria_ds')
WHERE EXISTS (SELECT 1 FROM algorithms WHERE class_name = 'weka.classifiers.functions.LinearRegression')
ON CONFLICT (id) DO NOTHING;

-- alex_ml uses RandomForest (classification)
INSERT INTO algorithm_configurations (id, algorithm_id, options, algorithm_type_id, user_id)
SELECT 3,
       (SELECT id FROM algorithms WHERE class_name = 'weka.classifiers.trees.RandomForest' LIMIT 1),
       '-I 200 -K 0 -depth 15',
       (SELECT id FROM CONST_ALGORITHM_TYPES WHERE name = 'CLASSIFICATION'),
       (SELECT id FROM users WHERE username = 'alex_ml')
WHERE EXISTS (SELECT 1 FROM algorithms WHERE class_name = 'weka.classifiers.trees.RandomForest')
ON CONFLICT (id) DO NOTHING;

-- Reset sequence
SELECT setval('algorithm_configurations_id_seq', COALESCE((SELECT MAX(id) FROM algorithm_configurations), 1), true);

-- ============================================
-- 2. DATASETS (one per key user)
-- ============================================
INSERT INTO datasets (id, user_id, original_file_name, file_name, file_path, file_size, content_type, upload_date, accessibility_id, category_id, description)
VALUES
    -- nickriz's dataset
    (1,
     (SELECT id FROM users WHERE username = 'nickriz'),
     'iris_dataset.csv',
     'nickriz_iris_20250201_001.csv',
     'ml-datasets/nickriz_iris_20250201_001.csv',
     15360,
     'text/csv',
     NOW() - INTERVAL '7 days',
     (SELECT id FROM const_dataset_accessibilities WHERE name = 'PRIVATE'),
     1,
     'Iris flower classification dataset with sepal and petal measurements'),

    -- maria_ds's dataset
    (2,
     (SELECT id FROM users WHERE username = 'maria_ds'),
     'housing_prices.csv',
     'maria_ds_housing_20250202_001.csv',
     'ml-datasets/maria_ds_housing_20250202_001.csv',
     245760,
     'text/csv',
     NOW() - INTERVAL '5 days',
     (SELECT id FROM const_dataset_accessibilities WHERE name = 'PRIVATE'),
     1,
     'Housing price prediction dataset with area, rooms, and location features'),

    -- alex_ml's dataset
    (3,
     (SELECT id FROM users WHERE username = 'alex_ml'),
     'customer_segments.csv',
     'alex_ml_customers_20250203_001.csv',
     'ml-datasets/alex_ml_customers_20250203_001.csv',
     102400,
     'text/csv',
     NOW() - INTERVAL '3 days',
     (SELECT id FROM const_dataset_accessibilities WHERE name = 'PRIVATE'),
     1,
     'Customer segmentation dataset with demographics and purchase behavior')
ON CONFLICT (id) DO NOTHING;

-- Reset dataset sequences
SELECT setval('datasets_id_seq', COALESCE((SELECT MAX(id) FROM datasets), 1), true);
SELECT setval('datasets_seq', COALESCE((SELECT MAX(id) FROM datasets), 0) + 1, false);

-- ============================================
-- 3. DATASET CONFIGURATIONS
-- ============================================
INSERT INTO dataset_configurations (id, basic_attributes_columns, target_column, upload_date, status, dataset_id)
VALUES
    (1, 'sepal_length,sepal_width,petal_length,petal_width', 'species',
     NOW() - INTERVAL '7 days', 'CONFIGURED', 1),

    (2, 'area,rooms,bathrooms,location_score,age', 'price',
     NOW() - INTERVAL '5 days', 'CONFIGURED', 2),

    (3, 'age,income,spending_score,membership_years', 'segment',
     NOW() - INTERVAL '3 days', 'CONFIGURED', 3)
ON CONFLICT (id) DO NOTHING;

-- Reset sequence
SELECT setval('dataset_configurations_id_seq', COALESCE((SELECT MAX(id) FROM dataset_configurations), 1), true);

-- ============================================
-- 4. TRAININGS (all COMPLETED)
-- ============================================
INSERT INTO trainings (id, started_date, finished_date, status_id, algorithm_configuration_id, user_id, dataset_id, version, results)
VALUES
    -- nickriz's training (Random Forest on Iris)
    (1,
     NOW() - INTERVAL '6 days',
     NOW() - INTERVAL '6 days' + INTERVAL '15 minutes',
     (SELECT id FROM const_training_statuses WHERE name = 'COMPLETED'),
     1,
     (SELECT id FROM users WHERE username = 'nickriz'),
     1,
     1,
     '{"accuracy": 0.9533, "precision": 0.9545, "recall": 0.9533, "f1_score": 0.9533}'),

    -- maria_ds's training (Linear Regression on Housing)
    (2,
     NOW() - INTERVAL '4 days',
     NOW() - INTERVAL '4 days' + INTERVAL '25 minutes',
     (SELECT id FROM const_training_statuses WHERE name = 'COMPLETED'),
     2,
     (SELECT id FROM users WHERE username = 'maria_ds'),
     2,
     1,
     '{"r2_score": 0.8721, "mse": 0.0342, "rmse": 0.1850, "mae": 0.1423}'),

    -- alex_ml's training (Random Forest on Customers)
    (3,
     NOW() - INTERVAL '2 days',
     NOW() - INTERVAL '2 days' + INTERVAL '20 minutes',
     (SELECT id FROM const_training_statuses WHERE name = 'COMPLETED'),
     3,
     (SELECT id FROM users WHERE username = 'alex_ml'),
     3,
     1,
     '{"accuracy": 0.8912, "precision": 0.8934, "recall": 0.8890, "f1_score": 0.8912}')
ON CONFLICT (id) DO NOTHING;

-- Reset sequence
SELECT setval('trainings_id_seq', COALESCE((SELECT MAX(id) FROM trainings), 1), true);

-- ============================================
-- 5. MODELS (all finalized)
-- ============================================
INSERT INTO models (id, training_id, model_url, model_type_id, status_id, accessibility_id, model_name, model_description, data_description, created_at, finalized, finalization_date, category_id)
VALUES
    -- nickriz's model
    (1, 1,
     'ml-models/nickriz_iris_rf_model.pkl',
     (SELECT id FROM const_model_types WHERE name = 'PREDEFINED'),
     (SELECT id FROM const_model_statuses WHERE name = 'FINISHED'),
     (SELECT id FROM const_model_accessibilites WHERE name = 'PRIVATE'),
     'Iris Classifier',
     'Random Forest classifier trained on Iris dataset with 95.3% accuracy',
     'Iris flower measurements: sepal length/width, petal length/width',
     NOW() - INTERVAL '6 days',
     true,
     NOW() - INTERVAL '6 days',
     1),

    -- maria_ds's model
    (2, 2,
     'ml-models/maria_ds_housing_lr_model.pkl',
     (SELECT id FROM const_model_types WHERE name = 'PREDEFINED'),
     (SELECT id FROM const_model_statuses WHERE name = 'FINISHED'),
     (SELECT id FROM const_model_accessibilites WHERE name = 'PRIVATE'),
     'Housing Price Predictor',
     'Linear Regression model for housing price prediction with R2=0.87',
     'Housing features: area, rooms, bathrooms, location score, age',
     NOW() - INTERVAL '4 days',
     true,
     NOW() - INTERVAL '4 days',
     1),

    -- alex_ml's model
    (3, 3,
     'ml-models/alex_ml_customer_rf_model.pkl',
     (SELECT id FROM const_model_types WHERE name = 'PREDEFINED'),
     (SELECT id FROM const_model_statuses WHERE name = 'FINISHED'),
     (SELECT id FROM const_model_accessibilites WHERE name = 'PRIVATE'),
     'Customer Segmenter',
     'Random Forest model for customer segmentation with 89.1% accuracy',
     'Customer demographics: age, income, spending score, membership years',
     NOW() - INTERVAL '2 days',
     true,
     NOW() - INTERVAL '2 days',
     1)
ON CONFLICT (id) DO NOTHING;

-- Reset sequence
SELECT setval('models_id_seq', COALESCE((SELECT MAX(id) FROM models), 1), true);

-- ============================================
-- 6. MODEL KEYWORDS
-- ============================================
INSERT INTO model_keywords (model_id, keyword) VALUES
    (1, 'classification'), (1, 'iris'), (1, 'random-forest'),
    (2, 'regression'), (2, 'housing'), (2, 'linear-regression'),
    (3, 'classification'), (3, 'customers'), (3, 'segmentation')
ON CONFLICT DO NOTHING;

-- ============================================
-- VERIFICATION QUERIES (uncomment to check)
-- ============================================
-- SELECT 'Algorithm Configs' AS entity, count(*) FROM algorithm_configurations;
-- SELECT 'Datasets' AS entity, count(*) FROM datasets;
-- SELECT 'Dataset Configs' AS entity, count(*) FROM dataset_configurations;
-- SELECT 'Trainings' AS entity, count(*) FROM trainings;
-- SELECT 'Models' AS entity, count(*) FROM models;
-- SELECT u.username, d.id AS dataset_id, d.original_file_name
--   FROM datasets d JOIN users u ON d.user_id = u.id ORDER BY d.id;
-- SELECT u.username, m.id AS model_id, m.model_name
--   FROM models m JOIN trainings t ON m.training_id = t.id JOIN users u ON t.user_id = u.id ORDER BY m.id;

-- ============================================
-- SUMMARY - COMPLETE TEST ENVIRONMENT
-- ============================================
-- USERS (from V2 + V5):
-- | Username    | Password       | Role  | Groups (Leader/Member)                      |
-- |-------------|----------------|-------|---------------------------------------------|
-- | bigspy      | adminPassword  | ADMIN | Leader: Group 2 (AI Dev Team)               |
-- | johnken     | adminPassword  | ADMIN | -                                           |
-- | nickriz     | userPassword   | USER  | Leader: Group 1 (ML Research), Member: 2, 3 |
-- | maria_ds    | testPassword   | USER  | Leader: Group 3 (Data Science), Member: 1   |
-- | alex_ml     | testPassword   | USER  | Member: Groups 1, 3                         |
-- | elena_ai    | testPassword   | USER  | Member: Groups 1, 3                         |
-- | george_dev  | testPassword   | USER  | Member: Group 2                             |
-- | sophia_fe   | testPassword   | USER  | Member: Group 2                             |
--
-- GROUPS (from V5):
-- | ID | Name                | Leader   | Members                         |
-- |----|---------------------|----------|----------------------------------|
-- | 1  | ML Research Team    | nickriz  | maria_ds, alex_ml, elena_ai     |
-- | 2  | AI Development Team | bigspy   | nickriz, george_dev, sophia_fe  |
-- | 3  | Data Science Lab    | maria_ds | alex_ml, elena_ai, nickriz      |
--
-- RESOURCES (this script):
-- | Dataset ID | Owner    | Model ID | Training ID | Description              |
-- |------------|----------|----------|-------------|--------------------------|
-- | 1          | nickriz  | 1        | 1           | Iris Classification      |
-- | 2          | maria_ds | 2        | 2           | Housing Price Prediction |
-- | 3          | alex_ml  | 3        | 3           | Customer Segmentation    |
-- =====================================================
