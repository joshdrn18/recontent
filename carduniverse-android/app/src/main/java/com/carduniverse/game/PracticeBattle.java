package com.carduniverse.game;

import java.util.HashMap;
import java.util.Map;

/** In-memory training rules. This class has no account, network, or persistence access. */
final class PracticeBattle {
    enum Move { STRIKE, GUARD, FOCUS, SKILL }
    enum Mode { DUMMY, AI, BOSS }
    enum Difficulty { EASY, NORMAL, HARD }

    final Mode mode;
    final Difficulty difficulty;
    final long teamPower;
    final long enemyPower;
    final int enemyMaxHp;
    int myHp = 100;
    int enemyHp;
    int focus;
    int enemyFocus;
    int turns;
    int damageDealt;
    boolean skillUsed;

    PracticeBattle(Mode mode, Difficulty difficulty, long teamPower, long enemyPower) {
        this.mode = mode;
        this.difficulty = difficulty;
        this.teamPower = teamPower;
        this.enemyPower = enemyPower;
        this.enemyMaxHp = mode == Mode.BOSS ? 180 : 100;
        this.enemyHp = enemyMaxHp;
    }

    static int synergyBonus(String[] elements) {
        Map<String, Integer> counts = new HashMap<>();
        int filled = 0;
        int dominant = 0;
        for (String element : elements) {
            if (element == null || element.isEmpty()) continue;
            int count = counts.getOrDefault(element, 0) + 1;
            counts.put(element, count);
            dominant = Math.max(dominant, count);
            filled++;
        }
        switch (dominant) {
            case 5: return 15;
            case 4: return 10;
            case 3: return 7;
            case 2: return 3;
            default: return filled == 5 && counts.size() == 5 ? 8 : 0;
        }
    }

    boolean finished() {
        return mode != Mode.DUMMY && (myHp == 0 || enemyHp == 0 || turns >= (mode == Mode.BOSS ? 10 : 5));
    }

    String outcome() {
        if (mode == Mode.DUMMY) return "SESSION COMPLETE";
        if (!finished()) return "IN PROGRESS";
        long ownShare = (long) myHp * enemyMaxHp;
        long enemyShare = (long) enemyHp * 100;
        return ownShare == enemyShare ? "DRAW" : ownShare > enemyShare ? "VICTORY" : "DEFEAT";
    }

    Round play(Move move, int skillPower) {
        if (finished() || (move == Move.SKILL && ((skillUsed && mode != Mode.DUMMY) || skillPower <= 0))) {
            throw new IllegalStateException("Move is unavailable.");
        }
        Move enemyMove = chooseEnemyMove();
        int edge = (int) Math.max(-3, Math.min(3, (teamPower - enemyPower) / 100));
        int outgoing = damage(move, focus, edge, skillPower);
        int incoming = enemyMove == null ? 0 : damage(enemyMove, enemyFocus, -edge, 0);
        if (mode == Mode.BOSS && enemyMove == Move.STRIKE) {
            incoming += (int) Math.max(0, Math.min(12, (enemyPower - teamPower) / 100));
        }
        if (enemyMove == Move.GUARD) outgoing = reduce(outgoing);
        if (move == Move.GUARD && incoming > 0) incoming = reduce(incoming);
        myHp = Math.max(0, myHp - incoming);
        if (mode != Mode.DUMMY) enemyHp = Math.max(0, enemyHp - outgoing);
        focus = nextFocus(move, focus);
        enemyFocus = nextFocus(enemyMove, enemyFocus);
        if (move == Move.SKILL) skillUsed = true;
        damageDealt += outgoing;
        turns++;
        return new Round(turns, move, enemyMove, outgoing, incoming, myHp, enemyHp);
    }

    private Move chooseEnemyMove() {
        if (mode == Mode.DUMMY) return null;
        if (mode == Mode.BOSS) {
            return turns % 3 == 0 ? Move.FOCUS : turns % 3 == 1 ? Move.GUARD : Move.STRIKE;
        }
        if (difficulty == Difficulty.EASY) return turns % 2 == 0 ? Move.STRIKE : Move.GUARD;
        if (enemyFocus > 0 || myHp <= 24) return Move.STRIKE;
        if (difficulty == Difficulty.NORMAL) return turns % 3 == 0 ? Move.FOCUS : Move.STRIKE;
        if (focus > 0) return Move.GUARD;
        return turns % 2 == 0 ? Move.FOCUS : Move.STRIKE;
    }

    private static int damage(Move move, int charge, int edge, int skillPower) {
        switch (move) {
            case STRIKE: return Math.max(12, 22 + edge + charge * 8);
            case GUARD: return 8;
            case FOCUS: return 5;
            case SKILL: return Math.max(12, 18 + edge + charge * 8 + skillPower * 4);
            default: throw new IllegalArgumentException("Unknown move");
        }
    }

    private static int reduce(int damage) {
        return Math.max(3, (int) Math.floor(damage * 0.42));
    }

    private static int nextFocus(Move move, int charge) {
        if (move == Move.FOCUS) return Math.min(2, charge + 1);
        if (move == Move.STRIKE || move == Move.SKILL) return 0;
        return charge;
    }

    static final class Round {
        final int turn;
        final Move playerMove;
        final Move enemyMove;
        final int damageDealt;
        final int damageTaken;
        final int playerHp;
        final int enemyHp;

        Round(int turn, Move playerMove, Move enemyMove, int damageDealt, int damageTaken,
              int playerHp, int enemyHp) {
            this.turn = turn;
            this.playerMove = playerMove;
            this.enemyMove = enemyMove;
            this.damageDealt = damageDealt;
            this.damageTaken = damageTaken;
            this.playerHp = playerHp;
            this.enemyHp = enemyHp;
        }
    }
}
