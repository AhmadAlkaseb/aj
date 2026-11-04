CREATE TABLE reservation_signals (
 order_guid uuid PRIMARY KEY, product_guid uuid NOT NULL, state varchar(20) NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now()
);
