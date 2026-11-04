CREATE TABLE payment_signals (
 order_guid uuid PRIMARY KEY, payment_guid uuid NOT NULL,
 topic varchar(40) NOT NULL, received_at timestamptz NOT NULL DEFAULT now()
);
