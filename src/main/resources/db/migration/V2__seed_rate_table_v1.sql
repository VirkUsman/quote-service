INSERT INTO rate_table (version, effective_from, currency, fuel_surcharge_bps, express_multiplier_bps, min_charge_cents, created_at)
VALUES (1, '2026-01-01 00:00:00.000000', 'EUR', 800, 15000, 1500, '2026-01-01 00:00:00.000000');

INSERT INTO rate_band (rate_table_id, zone, up_to_kg, base_cents, per_kg_cents)
SELECT id, z.zone, z.up_to_kg, z.base_cents, z.per_kg_cents
FROM rate_table t
JOIN (
    SELECT 'EU-W' AS zone,   10.00 AS up_to_kg, 1200 AS base_cents, 90 AS per_kg_cents
    UNION ALL SELECT 'EU-W',  50.00, 2000, 70
    UNION ALL SELECT 'EU-W', 500.00, 4500, 45
    UNION ALL SELECT 'EU-E',  10.00, 1600, 110
    UNION ALL SELECT 'EU-E',  50.00, 2600, 85
    UNION ALL SELECT 'EU-E', 500.00, 5500, 55
    UNION ALL SELECT 'UK',    10.00, 2200, 140
    UNION ALL SELECT 'UK',    50.00, 3400, 105
    UNION ALL SELECT 'UK',   500.00, 7000, 70
) z
WHERE t.version = 1;
