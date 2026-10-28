-- Konten von vor TOTP haben kein Secret (NULL) und richten es über POST /account/totp nach.
ALTER TABLE users ADD COLUMN totp_secret BYTEA CHECK (octet_length(totp_secret) = 20);
ALTER TABLE users ADD COLUMN totp_last_step BIGINT;         -- zuletzt benutzter Zeitschritt, jeder Code gilt nur einmal
