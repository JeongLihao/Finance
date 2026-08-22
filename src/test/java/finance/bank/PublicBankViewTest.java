package finance.bank;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PublicBankViewTest {
    @Test void ordinaryViewContainsRatesAndStatusButNoPrivateLedger(){
        CommercialBank bank=new CommercialBank(UUID.randomUUID(),"TEST","Test Bank",BankStatus.ACTIVE,1_000,BankPolicy.standard());
        assertTrue(bank.ledger().post(0,UUID.randomUUID(),BankLedgerAccount.ASSET_RESERVE,BankLedgerAccount.EQUITY_PAID_IN,1_000,BankLedgerReason.INITIAL_CAPITAL));
        var ordinary=PublicBankViewService.crop(bank,false);var admin=PublicBankViewService.crop(bank,true);
        assertEquals(0,ordinary.assets());assertEquals(0,ordinary.reserves());assertEquals(0,ordinary.deposits());assertEquals(0,ordinary.capitalBps());assertEquals(0,ordinary.liquidityBps());assertEquals(0,ordinary.centralBorrowing());
        assertEquals(BankStatus.ACTIVE,ordinary.status());assertTrue(ordinary.timeRateBps()>0);
        assertEquals(1_000,admin.assets());assertEquals(1_000,admin.reserves());assertNotEquals(0,admin.capitalBps());
    }
}
