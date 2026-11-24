CREATE TABLE rate_table (
    id                     BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    version                INT          NOT NULL,
    effective_from         DATETIME(6)  NOT NULL,
    currency               VARCHAR(3)   NOT NULL,
    fuel_surcharge_bps     INT          NOT NULL,
    express_multiplier_bps INT          NOT NULL,
    min_charge_cents       BIGINT       NOT NULL,
    created_at             DATETIME(6)  NOT NULL,
    CONSTRAINT uq_rate_table_version UNIQUE (version)
);

CREATE TABLE rate_band (
    id            BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    rate_table_id BIGINT        NOT NULL,
    zone          VARCHAR(16)   NOT NULL,
    up_to_kg      DECIMAL(10,2) NOT NULL,
    base_cents    BIGINT        NOT NULL,
    per_kg_cents  BIGINT        NOT NULL,
    CONSTRAINT fk_rate_band_table FOREIGN KEY (rate_table_id) REFERENCES rate_table (id),
    CONSTRAINT uq_rate_band UNIQUE (rate_table_id, zone, up_to_kg)
);

CREATE TABLE quote (
    id                 BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_email     VARCHAR(254)  NOT NULL,
    destination_zone   VARCHAR(16)   NOT NULL,
    weight_kg          DECIMAL(10,2) NOT NULL,
    status             VARCHAR(16)   NOT NULL,
    price_cents        BIGINT        NOT NULL,
    currency           VARCHAR(3)    NOT NULL,
    rate_table_version INT           NOT NULL,
    created_at         DATETIME(6)   NOT NULL,
    expires_at         DATETIME(6)   NOT NULL,
    accepted_at        DATETIME(6)   NULL,
    version            BIGINT        NOT NULL,
    CONSTRAINT fk_quote_rate_version FOREIGN KEY (rate_table_version) REFERENCES rate_table (version),
    CONSTRAINT ck_quote_status CHECK (status IN ('OPEN', 'ACCEPTED', 'EXPIRED')),
    CONSTRAINT ck_quote_accepted CHECK ((status = 'ACCEPTED') = (accepted_at IS NOT NULL))
);

-- The expiry sweep reads by status and deadline.
CREATE INDEX idx_quote_status_expires ON quote (status, expires_at);
