ALTER TABLE quote
    ADD COLUMN service_level VARCHAR(16) NOT NULL DEFAULT 'STANDARD' AFTER weight_kg;

ALTER TABLE quote
    ADD CONSTRAINT ck_quote_service CHECK (service_level IN ('STANDARD', 'EXPRESS'));
