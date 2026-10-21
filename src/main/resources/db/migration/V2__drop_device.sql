ALTER TABLE sessions DROP COLUMN device_id;                 -- entfernt auch UNIQUE (user_id, device_id)
CREATE INDEX sessions_user_id ON sessions (user_id);        -- mehrere Sessions je Nutzer
