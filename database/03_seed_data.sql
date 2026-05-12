INSERT INTO catalog.product_code_mapping(source_product_code, target_product_code)
VALUES
('1823', '100074238'),
('41001', '90041001'),
('1893', '100074239'),
('50001', '80050001')
ON CONFLICT DO NOTHING;
