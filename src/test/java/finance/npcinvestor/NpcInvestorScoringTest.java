package finance.npcinvestor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NpcInvestorScoringTest {

    private static NpcInvestorScoring.Profile profile(long price, long fps, double change, List<Long> profits,
                                                      long divPerShare, int divCount, boolean risk) {
        return new NpcInvestorScoring.Profile(price, fps, change, profits, divPerShare, divCount, risk);
    }

    @Test void dividendInvestorPenalisesMissingDividendsAndRewardsYield() {
        var none = NpcInvestorScoring.score(NpcInvestorArchetype.DIVIDEND, profile(100, 100, 0, List.of(), 0, 0, false));
        assertEquals(NpcReason.NO_DIVIDEND, none.reason());
        assertTrue(none.value() <= 1_000);
        // 每股 5 / 股价 100 = 5% 股息率，连续两期 → 满分
        var rich = NpcInvestorScoring.score(NpcInvestorArchetype.DIVIDEND, profile(100, 100, 0, List.of(), 5, 2, false));
        assertEquals(NpcInvestorScoring.MAX_SCORE, rich.value());
        var single = NpcInvestorScoring.score(NpcInvestorArchetype.DIVIDEND, profile(100, 100, 0, List.of(), 5, 1, false));
        assertTrue(single.value() < rich.value());
    }

    @Test void growthInvestorFollowsProfitTrend() {
        var rising = NpcInvestorScoring.score(NpcInvestorArchetype.GROWTH, profile(10, 10, 0, List.of(10L, 10L, 30L, 30L), 0, 0, false));
        assertEquals(NpcReason.PROFIT_GROWTH, rising.reason());
        assertTrue(rising.value() > 4_000);
        var losing = NpcInvestorScoring.score(NpcInvestorArchetype.GROWTH, profile(10, 10, 0, List.of(10L, 10L, -5L, -5L), 0, 0, false));
        assertEquals(0, losing.value());
        var flat = NpcInvestorScoring.score(NpcInvestorArchetype.GROWTH, profile(10, 10, 0, List.of(20L, 20L, 20L, 20L), 0, 0, false));
        assertEquals(NpcReason.PROFIT_FLAT, flat.reason());
        var young = NpcInvestorScoring.score(NpcInvestorArchetype.GROWTH, profile(10, 10, 0, List.of(5L), 0, 0, false));
        assertEquals(NpcReason.INSUFFICIENT_HISTORY, young.reason());
    }

    @Test void speculatorOnlyBuysUpwardMomentum() {
        assertEquals(0, NpcInvestorScoring.score(NpcInvestorArchetype.SPECULATOR, profile(10, 10, -1.0, List.of(), 0, 0, false)).value());
        assertEquals(NpcInvestorScoring.MAX_SCORE, NpcInvestorScoring.score(NpcInvestorArchetype.SPECULATOR, profile(10, 10, 5.0, List.of(), 0, 0, false)).value());
        assertEquals(5_000, NpcInvestorScoring.score(NpcInvestorArchetype.SPECULATOR, profile(10, 10, 2.5, List.of(), 0, 0, false)).value());
    }

    @Test void valueInvestorBuysDiscountsOnly() {
        assertEquals(NpcInvestorScoring.MAX_SCORE, NpcInvestorScoring.score(NpcInvestorArchetype.VALUE, profile(70, 100, 0, List.of(), 0, 0, false)).value());
        assertEquals(0, NpcInvestorScoring.score(NpcInvestorArchetype.VALUE, profile(120, 100, 0, List.of(), 0, 0, false)).value());
        assertEquals(NpcReason.NO_FUNDAMENTAL, NpcInvestorScoring.score(NpcInvestorArchetype.VALUE, profile(10, 0, 0, List.of(), 0, 0, false)).reason());
    }

    @Test void bankruptcyRiskAndBadPriceZeroEveryArchetype() {
        for (NpcInvestorArchetype a : NpcInvestorArchetype.values()) {
            assertEquals(0, NpcInvestorScoring.score(a, profile(70, 100, 5, List.of(1L, 9L), 5, 2, true)).value(), a.name());
            assertEquals(0, NpcInvestorScoring.score(a, profile(0, 100, 5, List.of(1L, 9L), 5, 2, false)).value(), a.name());
        }
    }

    @Test void targetBpsIsClampedAndSpeculatorGetsHalfCap() {
        assertEquals(2_000, NpcInvestorScoring.targetBps(NpcInvestorArchetype.VALUE, NpcInvestorScoring.MAX_SCORE, 2_000));
        assertEquals(1_000, NpcInvestorScoring.targetBps(NpcInvestorArchetype.SPECULATOR, NpcInvestorScoring.MAX_SCORE, 2_000));
        assertEquals(1_000, NpcInvestorScoring.targetBps(NpcInvestorArchetype.VALUE, 5_000, 2_000));
        assertEquals(2_000, NpcInvestorScoring.targetBps(NpcInvestorArchetype.VALUE, 99_999, 2_000));
        assertEquals(0, NpcInvestorScoring.targetBps(NpcInvestorArchetype.VALUE, -5, 2_000));
        assertEquals(0, NpcInvestorScoring.targetBps(NpcInvestorArchetype.VALUE, 5_000, 0));
    }

    @Test void sentimentMapsScoreToPlusMinusHundred() {
        assertEquals(-100, NpcInvestorScoring.sentiment(0));
        assertEquals(0, NpcInvestorScoring.sentiment(5_000));
        assertEquals(100, NpcInvestorScoring.sentiment(NpcInvestorScoring.MAX_SCORE));
    }

    @Test void archetypeAccountsAreDeterministicAndDistinct() {
        assertEquals(NpcInvestorArchetype.VALUE, NpcInvestorArchetype.fromAccount(NpcInvestorArchetype.VALUE.accountId()));
        assertEquals(4, java.util.Arrays.stream(NpcInvestorArchetype.values()).map(NpcInvestorArchetype::accountId).distinct().count());
        assertNull(NpcInvestorArchetype.fromAccount(java.util.UUID.randomUUID()));
    }
}
