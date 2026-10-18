package finance.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 进阶玩法的开关只拦新开仓，平仓、取回、还款永远放行。 */
class SimpleActionGateTest {

    @Test void openingAdvancedPositionsNeedsTheSwitch() {
        assertEquals("futures", SimpleActionPacket.gate(SimpleActionPacket.Action.LOCK_BUY, ""));
        assertEquals("margin", SimpleActionPacket.gate(SimpleActionPacket.Action.LEVER_BUY, "S"));
        assertEquals("margin", SimpleActionPacket.gate(SimpleActionPacket.Action.SHORT_SELL, "S"));
        assertEquals("bonds", SimpleActionPacket.gate(SimpleActionPacket.Action.BUY_PRODUCT, "cbond:30"));
        assertEquals("fund", SimpleActionPacket.gate(SimpleActionPacket.Action.BUY_PRODUCT, "fund:x"));
        assertEquals("shareholder", SimpleActionPacket.gate(SimpleActionPacket.Action.PROPOSE, "dividend"));
        assertEquals("takeover", SimpleActionPacket.gate(SimpleActionPacket.Action.TAKEOVER, ""));
        assertEquals("bank", SimpleActionPacket.gate(SimpleActionPacket.Action.CHARTER_BANK, ""));
        assertEquals("insurance", SimpleActionPacket.gate(SimpleActionPacket.Action.INSURE, ""));
        assertEquals("wage", SimpleActionPacket.gate(SimpleActionPacket.Action.SET_WAGE, ""));
    }

    @Test void coreActionsAndUnwindingAreNeverBlocked() {
        for (SimpleActionPacket.Action a : new SimpleActionPacket.Action[]{
                SimpleActionPacket.Action.QUICK_BUY, SimpleActionPacket.Action.QUICK_SELL, SimpleActionPacket.Action.PLACE_SELL_ORDER,
                SimpleActionPacket.Action.CLOSE_LOCK, SimpleActionPacket.Action.WITHDRAW_MARGIN, SimpleActionPacket.Action.LEVER_REPAY,
                SimpleActionPacket.Action.SHORT_COVER, SimpleActionPacket.Action.COLLATERAL_WITHDRAW, SimpleActionPacket.Action.REDEEM_PRODUCT,
                SimpleActionPacket.Action.COMPANY_LOAN, SimpleActionPacket.Action.SET_DIVIDEND}) {
            assertNull(SimpleActionPacket.gate(a, "cbond:1"), a.name());
        }
        assertNull(SimpleActionPacket.gate(SimpleActionPacket.Action.BUY_PRODUCT, "time:30"));
    }
}
