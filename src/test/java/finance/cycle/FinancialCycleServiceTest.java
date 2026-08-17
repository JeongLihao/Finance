package finance.cycle;

import finance.data.EconomySavedData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.index.MarketIndexService;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.account.AccountManager;
import finance.diagnostic.ModuleHealthRegistry;
import finance.diagnostic.ModuleRunState;
import finance.governance.BuybackPlan;
import finance.governance.CapitalActionStatus;
import finance.governance.CorporateActionManager;
import java.util.UUID;

class FinancialCycleServiceTest {
    @AfterEach void cleanup() { EconomySavedData.resetRuntimeState(); }

    @Test void dayZeroIsProcessedOnceAndRollbackIsIgnored() {
        FinancialCycleService.clearDirect();
        assertEquals(1, FinancialCycleService.advanceTo(0));
        assertEquals(0, FinancialCycleService.advanceTo(0));
        assertEquals(0, FinancialCycleService.advanceTo(-1));
        assertEquals(0, FinancialCycleService.lastProcessedDay());
    }

    @Test void timeJumpProcessesEveryMissingDayOnce() {
        FinancialCycleService.restoreLastProcessedDay(2);
        assertEquals(4, FinancialCycleService.advanceTo(6));
        assertEquals(6, FinancialCycleService.lastProcessedDay());
        assertEquals(0, FinancialCycleService.advanceTo(4));
    }

    @Test void dayZeroIndexClosesOnlyAfterDayZeroHasFinished() {
        UUID companyId = UUID.randomUUID();
        Company company = new Company(companyId, "IndexCo", CompanyType.FOOD, 1_000);
        company.setPublic(true); CompanyManager.registerDirect(company);
        StockMarketManager.putStockDirect(new Stock("IDX", "IndexCo", companyId, 100, 100, 0, 10, 10));

        assertFalse(FinancialCycleService.observeMarketDay(0));
        FinancialCycleService.advanceTo(0);
        assertTrue(MarketIndexService.state(MarketIndexService.STOCK_COMPOSITE).history().isEmpty());
        assertTrue(FinancialCycleService.observeMarketDay(1));
        assertEquals(0, MarketIndexService.state(MarketIndexService.STOCK_COMPOSITE).latest().mcDay());
        assertFalse(FinancialCycleService.closeMarketDay(0));
    }

    @Test void marketTimeJumpClosesLastObservedDayButNeverSyntheticMissingDays() {
        UUID companyId = UUID.randomUUID();
        Company company = new Company(companyId, "JumpCo", CompanyType.FOOD, 1_000);
        company.setPublic(true); CompanyManager.registerDirect(company);
        StockMarketManager.putStockDirect(new Stock("JMP", "JumpCo", companyId, 100, 100, 0, 10, 10));
        FinancialCycleService.observeMarketDay(2);
        assertTrue(FinancialCycleService.observeMarketDay(8));
        assertEquals(2, MarketIndexService.state(MarketIndexService.STOCK_COMPOSITE).latest().mcDay());
        assertEquals(1, MarketIndexService.state(MarketIndexService.STOCK_COMPOSITE).history().size());
    }

    @Test void pausedStockModuleAlsoPausesCorporateActionSettlement() {
        UUID companyId=UUID.randomUUID(),escrow=UUID.randomUUID();
        Company company=new Company(companyId,"Paused",CompanyType.FOOD,1_000);company.setPublic(true);CompanyManager.registerDirect(company);
        StockMarketManager.putStockDirect(new Stock("PAUS","Paused",companyId,100,100,0,10,10));
        AccountManager.deposit(escrow,100);
        BuybackPlan plan=new BuybackPlan(UUID.randomUUID(),companyId,escrow,"PAUS",10,10,0,1,100,CapitalActionStatus.OPEN);
        CorporateActionManager.putBuybackDirect(plan);
        ModuleHealthRegistry.restrict(ModuleHealthRegistry.Module.STOCK,ModuleRunState.PAUSED,"test",0);
        FinancialCycleService.clearDirect();FinancialCycleService.advanceTo(1);
        assertEquals(CapitalActionStatus.OPEN,plan.status());
        ModuleHealthRegistry.resume(ModuleHealthRegistry.Module.STOCK);FinancialCycleService.advanceTo(2);
        assertEquals(CapitalActionStatus.COMPLETED,plan.status());
    }
}
