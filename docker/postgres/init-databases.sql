-- Executed once by the postgres image on first start (mounted into /docker-entrypoint-initdb.d).
-- One database per service: services never share tables.
CREATE DATABASE order_db;
CREATE DATABASE inventory_db;
CREATE DATABASE payment_db;
