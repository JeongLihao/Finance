package finance.simple;

import static org.junit.jupiter.api.Assertions.*;

import finance.bank.BankPolicy;
import finance.bank.BankStatus;
import finance.bank.BankingManager;
import finance.bank.CommercialBank;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 当老板：一键提案的参数合法、自动起的银行代码不撞车。 */
class SimpleBossServiceTest {

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        BankingManager.clearDirect();
    }

    @Test
    void raiseDividendStartsFromTheRatioActuallyPaid() {
        Company fresh = new Company(UUID.randomUUID(), "新公司", CompanyType.RAW_MATERIALS, 1_000L, UUID.randomUUID());
        assertTrue(fresh.getDividendRatio() < 0, "没单独定过分红比例的公司存的是 -1");
        long expected = Math.round(CompanyManager.getDividendRatio() * 100) + 10;
        assertEquals(expected, SimpleBossService.dividendRaise(fresh), "从全服默认比例往上加 10 个百分点");

        Company generous = new Company(UUID.randomUUID(), "大方", CompanyType.RAW_MATERIALS, 1_000L, UUID.randomUUID());
        generous.setDividendPolicy(0.95, 7);
        assertEquals(100, SimpleBossService.dividendRaise(generous), "最多提到 100%");
    }

    @Test
    void bankCodeAndNameGetANumberWhenTaken() {
        UUID id = UUID.randomUUID();
        assertArrayEquals(new String[] {"PSTE", "Steve银行"}, SimpleBossService.bankIdentity("Steve", id));

        BankingManager.putBankDirect(new CommercialBank(
                UUID.randomUUID(), "PSTE", "Steve银行", BankStatus.ACTIVE, 1_000, BankPolicy.standard()));
        assertArrayEquals(
                new String[] {"PSTE2", "Stevie银行"},
                SimpleBossService.bankIdentity("Stevie", id),
                "Stevie 和 Steve 的代码都是 PSTE，重了就加编号；名字没重就不加");
        assertArrayEquals(
                new String[] {"PSTE2", "Steve银行2"}, SimpleBossService.bankIdentity("Steve", id), "同一个人开第二家，名字也加编号");
    }
}
