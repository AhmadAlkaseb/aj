CREATE TABLE IF NOT EXISTS products (
    id BIGSERIAL PRIMARY KEY,
    guid UUID NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS products_details (
    product_id BIGINT PRIMARY KEY,
    brand VARCHAR(255),
    model VARCHAR(255),
    year INTEGER,
    description TEXT,
    price DECIMAL(10,2),
    currency VARCHAR(20),
    status VARCHAR(50),
    mileage INTEGER,
    color VARCHAR(100),
    fuel_type VARCHAR(100),
    transmission VARCHAR(100),
    engine_type VARCHAR(100),
    power_hp INTEGER,
    capacity_liters DECIMAL(8,2),
    top_speed_kmh INTEGER,
    zero_to_100_seconds DECIMAL(5,2),
    length_mm INTEGER,
    width_mm INTEGER,
    height_mm INTEGER,
    equipment JSONB,
    images JSONB,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_products_details_product FOREIGN KEY (product_id)
        REFERENCES products (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS products_deleted (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    deleted_at TIMESTAMP NOT NULL,
    reason VARCHAR(255),
    CONSTRAINT fk_products_deleted_product FOREIGN KEY (product_id)
        REFERENCES products (id)
        ON DELETE CASCADE
);
