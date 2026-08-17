package finance.futures;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FuturesMathTest {
    @Test void notionalAndMarginUseExactSafeArithmetic(){assertEquals(3_000,FuturesMath.notional(100,10,3));assertEquals(600,FuturesMath.margin(100,10,3,2_000));assertEquals(-1,FuturesMath.notional(Long.MAX_VALUE,2,1));assertEquals(-1,FuturesMath.margin(0,10,1,2_000));}
    @Test void longAndShortPnlAreSymmetric(){assertEquals(200,FuturesMath.signedPnl(100,110,10,2));assertEquals(-200,FuturesMath.signedPnl(100,110,10,-2));assertThrows(ArithmeticException.class,()->FuturesMath.signedPnl(1,Long.MAX_VALUE,10,2));}
    @Test void marginRoundsUpAndMaxQuantityIsBounded(){assertEquals(1,FuturesMath.margin(1,1,1,1));assertEquals(5,FuturesMath.maxOpenQuantity(1_000,100,10,2_000,5));}
    @Test void contractRejectsBrokenDateRelationships(){assertThrows(IllegalArgumentException.class,()->new FuturesContract(java.util.UUID.randomUUID(),"X","iron",10,2,1,3,FuturesSettlementType.CASH,FuturesContractStatus.SCHEDULED));}
}
