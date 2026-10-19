package ru.mcmon.plugin.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JoinPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-13T12:00:00Z");
    private static final ConfirmedPlayer PLAYER =
            new ConfirmedPlayer(new PlayerId("7f3a91c202be4d10"), "Steve_Miner", Optional.empty());
    private static final Ban ACTIVE = new Ban("grief", Optional.of(NOW.plusSeconds(3600)));
    private static final Ban EXPIRED = new Ban("grief", Optional.of(NOW.minusSeconds(1)));
    private static final List<LookupOutcome> NOT_CONFIRMED =
            List.of(LookupOutcome.NOT_CONFIRMED, LookupOutcome.NO_CODE, LookupOutcome.INVALID_NICK);

    @Test
    void confirmedPlayerIsAllowedUnlessBanned() {
        assertEquals(JoinDecision.Kind.ALLOW, decide(Lookup.confirmed(PLAYER), false));
        assertEquals(JoinDecision.Kind.DENY_BANNED, decide(Lookup.confirmed(banned(ACTIVE)), false));
        assertEquals(
                JoinDecision.Kind.DENY_BANNED, decide(Lookup.confirmed(banned(new Ban("", Optional.empty()))), false));
        assertEquals(JoinDecision.Kind.ALLOW, decide(Lookup.confirmed(banned(EXPIRED)), false));
    }

    @Test
    void unconfirmedPlayerWithoutCodeOrWithInvalidNickPassesUnlessLauncherRequired() {
        for (LookupOutcome outcome : NOT_CONFIRMED) {
            assertEquals(JoinDecision.Kind.PASS, decide(Lookup.of(outcome), false), outcome.name());
            assertEquals(JoinDecision.Kind.DENY_UNCONFIRMED, decide(Lookup.of(outcome), true), outcome.name());
        }
    }

    @Test
    void anySiteOrPluginFailurePassesEvenWhenLauncherRequired() {
        for (LookupOutcome outcome : LookupOutcome.values()) {
            if (outcome == LookupOutcome.CONFIRMED || NOT_CONFIRMED.contains(outcome)) {
                continue;
            }
            assertEquals(JoinDecision.Kind.PASS, decide(Lookup.of(outcome), true), outcome.name());
        }
    }

    @Test
    void knownBanDeniesUnlessTheSiteConfirmedThePlayer() {
        JoinPolicy policy = new JoinPolicy(Settings.defaults());

        assertEquals(
                JoinDecision.Kind.ALLOW,
                policy.decide(Lookup.confirmed(PLAYER), Optional.of(ACTIVE), NOW)
                        .kind());
        assertEquals(
                JoinDecision.Kind.DENY_BANNED,
                policy.decide(Lookup.of(LookupOutcome.UNREACHABLE), Optional.of(ACTIVE), NOW)
                        .kind());
        assertEquals(
                JoinDecision.Kind.DENY_BANNED,
                policy.decide(Lookup.of(LookupOutcome.NOT_CONFIRMED), Optional.of(ACTIVE), NOW)
                        .kind());
        assertEquals(
                JoinDecision.Kind.PASS,
                policy.decide(Lookup.of(LookupOutcome.UNREACHABLE), Optional.of(EXPIRED), NOW)
                        .kind());
    }

    private static JoinDecision.Kind decide(Lookup lookup, boolean requireLauncher) {
        return new JoinPolicy(Settings.defaults().withRequireLauncher(requireLauncher))
                .decide(lookup, Optional.empty(), NOW)
                .kind();
    }

    private static ConfirmedPlayer banned(Ban ban) {
        return new ConfirmedPlayer(PLAYER.id(), PLAYER.nick(), Optional.of(ban));
    }
}
