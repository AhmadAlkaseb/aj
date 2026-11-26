-- Nulstil produkter og id-tæller ved hver opstart af Java-applikationen.
TRUNCATE TABLE products RESTART IDENTITY;

-- Opret 50 faste eksempelprodukter med id 1-50 og priser med to decimaler.
INSERT INTO products (name, price)
SELECT 'Produkt ' || number, (10 + number * 2.50)::NUMERIC(10,2)
FROM generate_series(1, 50) AS number;
