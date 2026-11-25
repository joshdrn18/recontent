CREATE TABLE IF NOT EXISTS phase14_reward_claims (
    claim_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    reward_kind TEXT NOT NULL CHECK(reward_kind IN ('BINDER','GUILD_WEEK','GUILD_SEASON')),
    reward_key TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, reward_kind, reward_key)
);

CREATE TABLE IF NOT EXISTS phase14_guild_strikes (
    strike_id TEXT PRIMARY KEY,
    guild_id TEXT NOT NULL REFERENCES guilds(guild_id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    strike_day TEXT NOT NULL,
    week_start TEXT NOT NULL,
    damage INTEGER NOT NULL CHECK(damage BETWEEN 1 AND 400),
    UNIQUE(user_id, strike_day)
);
CREATE INDEX IF NOT EXISTS phase14_guild_strikes_by_week
ON phase14_guild_strikes(guild_id, week_start, user_id);

CREATE TABLE IF NOT EXISTS phase14_journey_tutorial (
    user_id TEXT PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    completed_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
