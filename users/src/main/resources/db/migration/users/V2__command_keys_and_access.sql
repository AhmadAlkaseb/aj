CREATE TABLE IF NOT EXISTS command_keys (
    id varchar(255) PRIMARY KEY,
    fingerprint varchar(64) NOT NULL,
    response text NOT NULL,
    resource_guid uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
