-- Additive migration only: no currency or existing game data updates.
CREATE TABLE IF NOT EXISTS phase17_achievement_unlocks (
 user_id TEXT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
 achievement_key TEXT NOT NULL CHECK(achievement_key IN ('legendary50','adventure_all','ranked100','raid50','sets_all','mastery10')),
 unlocked_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
 slot INTEGER CHECK(slot BETWEEN 1 AND 3),
 PRIMARY KEY(user_id,achievement_key), UNIQUE(user_id,slot)
);
CREATE TABLE IF NOT EXISTS phase17_ranked_cards (
 match_id TEXT NOT NULL REFERENCES pvp_matches(match_id) ON DELETE CASCADE,
 user_id TEXT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
 card_id TEXT NOT NULL REFERENCES cards(card_id),
 PRIMARY KEY(match_id,user_id,card_id)
);
CREATE TRIGGER IF NOT EXISTS phase17_capture_ranked_formation
AFTER INSERT ON pvp_matches
WHEN NEW.mode='RANKED' AND NEW.status='ACTIVE'
 AND EXISTS(SELECT 1 FROM users WHERE user_id=NEW.player_one_id AND role='collector' AND is_bot=0 AND is_beta_tester=0)
 AND EXISTS(SELECT 1 FROM users WHERE user_id=NEW.player_two_id AND role='collector' AND is_bot=0 AND is_beta_tester=0)
BEGIN
 INSERT OR IGNORE INTO phase17_ranked_cards(match_id,user_id,card_id)
 SELECT NEW.match_id,user_id,card_id FROM user_formation_slots
 WHERE user_id IN(NEW.player_one_id,NEW.player_two_id);
END;
CREATE INDEX IF NOT EXISTS phase17_ranked_card_index ON phase17_ranked_cards(card_id,match_id);
