package finance.fund;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FundMathTest {
    @Test void ratioUsesExactBigIntegerWithoutOverflow(){assertEquals(Long.MAX_VALUE,FundMath.ratioFloor(Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE));assertEquals(0,FundMath.feeFloor(1,1));}
    @Test void invalidRatiosAreRejected(){assertEquals(-1,FundMath.ratioFloor(1,1,0));assertEquals(-1,FundMath.ratioFloor(-1,1,1));}
}
