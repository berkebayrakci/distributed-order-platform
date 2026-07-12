ALTER TABLE product_code_mapping ADD COLUMN product_type VARCHAR(30);
ALTER TABLE product_code_mapping ADD COLUMN product_version INTEGER;
ALTER TABLE product_code_mapping ADD COLUMN validity_type VARCHAR(30);
ALTER TABLE product_code_mapping ADD COLUMN validity_amount INTEGER;
ALTER TABLE product_code_mapping ADD COLUMN validity_unit VARCHAR(20);
ALTER TABLE product_code_mapping ADD COLUMN renewable BOOLEAN;
ALTER TABLE product_code_mapping ADD COLUMN stackable BOOLEAN;
ALTER TABLE product_code_mapping ADD COLUMN requires_primary_tariff BOOLEAN;

INSERT INTO product_code_mapping (
    source_product_code, target_product_code, product_type, product_version,
    validity_type, validity_amount, validity_unit,
    renewable, stackable, requires_primary_tariff
) VALUES
    ('1823', '100074238', 'TARIFF', 1, 'FIXED_DURATION', 1, 'YEARS', FALSE, FALSE, FALSE),
    ('1893', '100074239', 'TARIFF', 1, 'FIXED_DURATION', 1, 'YEARS', FALSE, FALSE, FALSE),
    ('41001', '90041001', 'ADDON', 1, 'FIXED_DURATION', 3, 'MONTHS', FALSE, TRUE, TRUE),
    ('50001', '80050001', 'ADDON', 1, 'FIXED_DURATION', 3, 'MONTHS', FALSE, TRUE, TRUE)
ON CONFLICT (source_product_code) DO UPDATE SET
    target_product_code = EXCLUDED.target_product_code,
    product_type = EXCLUDED.product_type,
    product_version = EXCLUDED.product_version,
    validity_type = EXCLUDED.validity_type,
    validity_amount = EXCLUDED.validity_amount,
    validity_unit = EXCLUDED.validity_unit,
    renewable = EXCLUDED.renewable,
    stackable = EXCLUDED.stackable,
    requires_primary_tariff = EXCLUDED.requires_primary_tariff;

ALTER TABLE product_code_mapping ALTER COLUMN product_type SET NOT NULL;
ALTER TABLE product_code_mapping ALTER COLUMN product_version SET NOT NULL;
ALTER TABLE product_code_mapping ALTER COLUMN validity_type SET NOT NULL;
ALTER TABLE product_code_mapping ALTER COLUMN renewable SET NOT NULL;
ALTER TABLE product_code_mapping ALTER COLUMN stackable SET NOT NULL;
ALTER TABLE product_code_mapping ALTER COLUMN requires_primary_tariff SET NOT NULL;

ALTER TABLE product_code_mapping ADD CONSTRAINT ck_catalog_product_type
    CHECK (product_type IN ('TARIFF', 'ADDON'));
ALTER TABLE product_code_mapping ADD CONSTRAINT ck_catalog_product_version
    CHECK (product_version > 0);
ALTER TABLE product_code_mapping ADD CONSTRAINT ck_catalog_validity_type
    CHECK (validity_type IN ('FIXED_DURATION', 'NON_EXPIRING'));
ALTER TABLE product_code_mapping ADD CONSTRAINT ck_catalog_validity_unit
    CHECK (validity_unit IS NULL OR validity_unit IN ('DAYS', 'MONTHS', 'YEARS'));
ALTER TABLE product_code_mapping ADD CONSTRAINT ck_catalog_validity_shape
    CHECK (
        (validity_type = 'FIXED_DURATION' AND validity_amount > 0 AND validity_unit IS NOT NULL)
        OR
        (validity_type = 'NON_EXPIRING' AND validity_amount IS NULL AND validity_unit IS NULL)
    );
