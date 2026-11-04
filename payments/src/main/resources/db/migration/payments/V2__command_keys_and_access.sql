CREATE TABLE IF NOT EXISTS command_keys (
    id varchar(255) PRIMARY KEY,
    fingerprint varchar(64) NOT NULL,
    response text NOT NULL,
    resource_guid uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE payments_details ADD COLUMN IF NOT EXISTS customer_guid uuid;
ALTER TABLE payments_details ALTER COLUMN product_guid DROP NOT NULL;
