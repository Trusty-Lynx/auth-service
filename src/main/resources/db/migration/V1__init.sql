CREATE TABLE users (
    user_id         TEXT        PRIMARY KEY,                -- UUID als String
    email           TEXT        NOT NULL UNIQUE,            -- normalisiert: trim + lowercase
    salt_login      BYTEA       NOT NULL CHECK (octet_length(salt_login) = 16),
    kdf_login       TEXT        NOT NULL,                   -- JSON, z. B. {"alg":"argon2id","m":65536,"t":3,"p":1,"len":32}
    salt_s_login    BYTEA       NOT NULL CHECK (octet_length(salt_s_login) = 16),
    login_verifier  BYTEA       NOT NULL CHECK (octet_length(login_verifier) = 32),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sessions (
    session_id  TEXT        PRIMARY KEY,                    -- UUID als String
    user_id     TEXT        NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    device_id   TEXT        NOT NULL,                       -- kein Fremdschlüssel, Geräte liegen in der Guard-DB
    token_hash  BYTEA       NOT NULL UNIQUE CHECK (octet_length(token_hash) = 32),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id, device_id)                             -- E5: eine Session je Nutzer und Gerät
);
