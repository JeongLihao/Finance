package finance.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A2：利率传导的纯函数——有边界、单调。 */
class RateTransmissionTest {

    @Test
    void neutralWhenRateIsNeutralAndInflationOnTarget() {
        assertEquals(0, RateTransmission.realRateGapBps(500, 200, 200, 500));
        assertEquals(RateTransmission.NEUTRAL_BPS, RateTransmission.consumptionBps(0, false, 2.0));
        assertEquals(RateTransmission.NEUTRAL_BPS, RateTransmission.referenceStockBps(0, 3.0));
        assertEquals(RateTransmission.NEUTRAL_BPS, RateTransmission.sellRatioBps(0, 2.0));
    }

    @Test
    void gapUsesHalfAnchoredExpectationsAndIsBounded() {
        // 通胀 −0.5%、目标 2%：预期 0.75%，实际利率 4.25%，中性实际利率 3% → 偏紧 125
        assertEquals(125, RateTransmission.realRateGapBps(500, -50, 200, 500));
        // 实际通胀计入预期时限制在 ±5%
        assertEquals(
                RateTransmission.realRateGapBps(500, 500, 200, 500),
                RateTransmission.realRateGapBps(500, 50_000, 200, 500));
        assertEquals(RateTransmission.MAX_GAP_BPS, RateTransmission.realRateGapBps(5_000, 0, 200, 500));
        assertEquals(-RateTransmission.MAX_GAP_BPS, RateTransmission.realRateGapBps(-5_000, 0, 200, 500));
    }

    @Test
    void tighterRatesMeanLessConsumptionLowerReferenceStockMoreSelling() {
        int prevC = Integer.MAX_VALUE, prevR = Integer.MAX_VALUE, prevS = Integer.MIN_VALUE;
        for (int gap = -1_000; gap <= 1_000; gap += 50) {
            int c = RateTransmission.consumptionBps(gap, false, 2.0);
            int r = RateTransmission.referenceStockBps(gap, 3.0);
            int s = RateTransmission.sellRatioBps(gap, 2.0);
            assertTrue(c <= prevC && r <= prevR && s >= prevS, "不单调：gap=" + gap);
            assertTrue(c >= RateTransmission.MIN_CONSUMPTION_BPS && c <= RateTransmission.MAX_CONSUMPTION_BPS);
            assertTrue(r >= RateTransmission.MIN_REFERENCE_BPS && r <= RateTransmission.MAX_REFERENCE_BPS);
            prevC = c;
            prevR = r;
            prevS = s;
        }
    }

    @Test
    void foodIsLessRateSensitiveAndZeroSensitivityDisables() {
        int other = RateTransmission.consumptionBps(500, false, 2.0);
        int food = RateTransmission.consumptionBps(500, true, 2.0);
        assertEquals(9_000, other);
        assertEquals(9_500, food);
        assertEquals(RateTransmission.NEUTRAL_BPS, RateTransmission.consumptionBps(800, false, 0));
        assertEquals(RateTransmission.NEUTRAL_BPS, RateTransmission.referenceStockBps(800, 0));
    }
}
