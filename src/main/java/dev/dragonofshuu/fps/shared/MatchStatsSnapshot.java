package dev.dragonofshuu.fps.shared;

import java.util.List;
import java.util.UUID;

/**
 * Immutable snapshot synced to clients for shared HUD/leaderboard UI.
 */
public record MatchStatsSnapshot(
        MatchPhase phase,
        String modeId,
        int timerSeconds,
        UUID localPlayerId,
        List<PlayerMatchStats> players,
        boolean shouldShowFinalResults) {

    public MatchStatsSnapshot(
            MatchPhase phase,
            String modeId,
            int timerSeconds,
            UUID localPlayerId,
            List<PlayerMatchStats> players) {
        this(phase, modeId, timerSeconds, localPlayerId, players, false);
    }

    public static MatchStatsSnapshot empty() {
        return new MatchStatsSnapshot(MatchPhase.IDLE, "", -1, null, List.of(), false);
    }
}
