CREATE TABLE IF NOT EXISTS phase15_showcase (
    user_id TEXT PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    favorite_card_id TEXT REFERENCES cards(card_id) ON DELETE SET NULL,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS phase15_tower_clears (
    clear_id TEXT PRIMARY KEY,
    week_start TEXT NOT NULL,
    user_id TEXT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    floor INTEGER NOT NULL CHECK(floor BETWEEN 1 AND 20),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(week_start,user_id,floor)
);
CREATE INDEX IF NOT EXISTS phase15_tower_leaderboard
ON phase15_tower_clears(week_start,floor DESC,created_at,user_id);
