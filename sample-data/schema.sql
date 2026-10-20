-- =====================================================================
-- QuerySleuth Synthetic Test Database Schema & Data Generator
-- Creates tables and deliberately inefficient scenarios for testing
-- =====================================================================

DROP TABLE IF EXISTS staging_updates CASCADE;
DROP TABLE IF EXISTS shipment_records CASCADE;
DROP TABLE IF EXISTS tariff CASCADE;
DROP TABLE IF EXISTS port CASCADE;

-- 1. Ports Reference Table
CREATE TABLE port (
    code CHAR(3) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    country VARCHAR(50) NOT NULL,
    active BOOLEAN DEFAULT TRUE
);

-- Seed initial ports
INSERT INTO port (code, name, country, active) VALUES
('ANR', 'Antwerp Port', 'Belgium', TRUE),
('NYC', 'Port of New York & New Jersey', 'USA', TRUE),
('RTM', 'Port of Rotterdam', 'Netherlands', TRUE),
('SIN', 'Port of Singapore', 'Singapore', TRUE),
('SHA', 'Port of Shanghai', 'China', TRUE),
('HAM', 'Port of Hamburg', 'Germany', TRUE),
('LAX', 'Port of Los Angeles', 'USA', TRUE);

-- 2. Tariffs Table (100,000 rows without initial composite index on ports to demonstrate sequential scan filter discard)
CREATE TABLE tariff (
    id SERIAL PRIMARY KEY,
    origin_port CHAR(3) REFERENCES port(code),
    destination_port CHAR(3) REFERENCES port(code),
    rate NUMERIC(10, 2) NOT NULL,
    effective_date DATE NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE'
);

-- Generate 100,000 synthetic tariff records with skewed distribution
INSERT INTO tariff (origin_port, destination_port, rate, effective_date, status)
SELECT 
    CASE (i % 6)
        WHEN 0 THEN 'ANR'
        WHEN 1 THEN 'NYC'
        WHEN 2 THEN 'RTM'
        WHEN 3 THEN 'SIN'
        WHEN 4 THEN 'SHA'
        ELSE 'HAM'
    END,
    CASE ((i + 1) % 6)
        WHEN 0 THEN 'NYC'
        WHEN 1 THEN 'ANR'
        WHEN 2 THEN 'SIN'
        WHEN 3 THEN 'RTM'
        WHEN 4 THEN 'HAM'
        ELSE 'SHA'
    END,
    ROUND((100.0 + (random() * 2500.0))::numeric, 2),
    CURRENT_DATE - (i % 365),
    CASE WHEN (i % 10 = 0) THEN 'EXPIRED' ELSE 'ACTIVE' END
FROM generate_series(1, 100000) AS i;

-- 3. Shipment Records (100,000 rows referencing tariffs)
CREATE TABLE shipment_records (
    id SERIAL PRIMARY KEY,
    tariff_id INT REFERENCES tariff(id),
    customer_id INT NOT NULL,
    weight_kg NUMERIC(10, 2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO shipment_records (tariff_id, customer_id, weight_kg, created_at)
SELECT 
    (1 + (i % 100000)),
    (1000 + (i % 500)),
    ROUND((50.0 + (random() * 5000.0))::numeric, 2),
    CURRENT_TIMESTAMP - (INTERVAL '1 minute' * (i % 50000))
FROM generate_series(1, 100000) AS i;

-- 4. Staging Updates Table (for cardinality mismatch simulation)
CREATE TABLE staging_updates (
    id SERIAL PRIMARY KEY,
    tariff_id INT,
    old_rate NUMERIC(10, 2),
    new_rate NUMERIC(10, 2),
    status VARCHAR(20),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO staging_updates (tariff_id, old_rate, new_rate, status)
SELECT 
    i,
    ROUND((100.0 + (random() * 1000.0))::numeric, 2),
    ROUND((100.0 + (random() * 1200.0))::numeric, 2),
    CASE WHEN i <= 45000 THEN 'PENDING' ELSE 'APPLIED' END
FROM generate_series(1, 50000) AS i;

-- Run ANALYZE to update base statistics
ANALYZE;

-- =====================================================================
-- EXAMPLES OF QUERIES TO RUN WITH EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)
-- =====================================================================

-- Scenario 1: Seq Scan examining 100,000 rows but discarding 99.8% by filter
-- EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)
-- SELECT * FROM tariff WHERE origin_port = 'ANR' AND destination_port = 'NYC';

-- Scenario 2: Nested loop execution with 1,200 repeated loops
-- EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)
-- SELECT s.id, s.weight_kg, t.rate 
-- FROM (SELECT * FROM shipment_records LIMIT 1200) s
-- JOIN tariff t ON t.id = s.tariff_id;

-- Scenario 3: Sort spilling to disk with low work_mem
-- SET work_mem = '64kB';
-- EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)
-- SELECT customer_id, created_at, weight_kg FROM shipment_records ORDER BY customer_id, created_at DESC;
