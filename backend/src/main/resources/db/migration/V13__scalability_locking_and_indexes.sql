-- V13: Optimistic locking version columns for concurrent stock/loyalty updates
-- and performance indexes for high-concurrency sales reporting & token cleanup.

ALTER TABLE product
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE product_batch
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE customer
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_sale_created_at_desc
    ON sale (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_sale_shop_created_at
    ON sale (shop_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_revoked_token_expires_at
    ON revoked_token (expires_at);

CREATE INDEX IF NOT EXISTS idx_product_barcode_product_id
    ON product_barcode (product_id);
