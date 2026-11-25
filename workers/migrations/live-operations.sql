CREATE TABLE IF NOT EXISTS game_live_operations (
    config_id INTEGER PRIMARY KEY CHECK(config_id=1),
    revision INTEGER NOT NULL DEFAULT 0 CHECK(revision>=0),
    config_json TEXT NOT NULL DEFAULT '{}' CHECK(json_valid(config_json)),
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT OR IGNORE INTO game_live_operations(config_id) VALUES(1);
