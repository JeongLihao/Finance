package finance.macro;

import finance.account.AccountManager;
import finance.account.TransactionType;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import finance.market.CentralBank;
import finance.market.NpcMarketMaker;
import finance.settlement.SettlementManager;
import finance.settlement.SettlementRecord;
import finance.village.VillageRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 第四步：国家财政——税收、关税、开支、借款、利息、还债、紧缩。 */
class MacroFiscalServiceTest {
    private VillageRecord village;
    private UUID player;

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.resetToDefaults();
        CommodityRegistry.register(new Commodity("wheat", "minecraft:wheat", "Wheat", CommodityCategory.FOOD, 8));
        AccountManager.getOrCreateSystemAccount(NpcMarketMaker.NPC_UUID);
        AccountManager.deposit(NpcMarketMaker.NPC_UUID, 10_000_000L);
        AccountManager.getOrCreateSystemAccount(CentralBank.UUID);
        AccountManager.deposit(CentralBank.UUID, 1_000_000L);
        CommodityInventoryManager.setCommodity(NpcMarketMaker.NPC_UUID, "wheat", 100_000);
        SettlementRecord s = SettlementManager.registerOrRecover(UUID.randomUUID(), "minecraft:overworld", new BlockPos(0, 64, 0), "alpha");
        village = MacroEconomyServiceTest.makeVillage(s, 12, 9, 4, 5);
        MacroEconomyManager.getOrCreate(s.id());
        player = UUID.randomUUID();
        AccountManager.getOrCreateSystemAccount(player);
        AccountManager.deposit(player, 10_000L);
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.resetToDefaults();
    }

    private static boolean hasTx(TransactionType type) {
        return AccountManager.getTransactions().stream().anyMatch(t -> t.getType() == type);
    }

    @Test void profitTaxFollowsTheTaxLaw() {
        Company c = new Company(UUID.randomUUID(), "Mill", CompanyType.RAW_MATERIALS, 10_000, UUID.randomUUID());
        CompanyManager.registerDirect(c);
        c.recordGameplayRevenue(1_000);
        c.recordGameplayCost(200);
        assertEquals(120, MacroFiscalService.collectProfitTax(c), "中税 15% × 毛利 800");
        assertEquals(10_000 - 120, c.getCash());
        assertEquals(120, MacroFiscalService.balance());
        assertTrue(hasTx(TransactionType.MACRO_TAX));
        assertEquals(320, c.getDailyCost(), "税计入当日成本");

        MacroEconomyManager.setLaw(LawLevel.HIGH_TAX);
        Company d = new Company(UUID.randomUUID(), "Loss", CompanyType.FOOD, 10_000, UUID.randomUUID());
        d.recordGameplayRevenue(100);
        d.recordGameplayCost(500);
        assertEquals(0, MacroFiscalService.collectProfitTax(d), "亏损不交税");
        d.recordGameplayRevenue(1_400);
        assertEquals(300, MacroFiscalService.collectProfitTax(d), "高税 30% × 毛利 1000");
    }

    @Test void protectionistTariffGoesToTheTreasury() {
        MacroEconomyManager.setLaw(LawLevel.PROTECTIONISM);
        long npc = AccountManager.getBalance(NpcMarketMaker.NPC_UUID);
        MacroEconomyServiceTest.runDay(1);
        assertTrue(hasTx(TransactionType.MACRO_TARIFF), "村民向 NPC 行商买粮时交了关税");
        long tariff = AccountManager.getTransactions().stream().filter(t -> t.getType() == TransactionType.MACRO_TARIFF)
                .mapToLong(t -> t.getAmount()).sum();
        assertTrue(tariff > 0);
        assertTrue(MacroFiscalService.balance() >= 0);
        assertTrue(AccountManager.getBalance(NpcMarketMaker.NPC_UUID) < npc + 1_000_000, "NPC 只留下不含税的货款");
    }

    @Test void deficitIsFinancedByCentralBankReserveFirst() {
        long reserve = AccountManager.getBalance(CentralBank.UUID);
        MacroFiscalService.processDay(1);
        assertEquals(30, Treasury.debt(), "行政开支 1 元/人 × 30 人，国库空着只能借");
        assertEquals(reserve - 30, AccountManager.getBalance(CentralBank.UUID));
        assertEquals(0, Treasury.cumulativeIssuance(), "央行准备金够，不增发");
        assertTrue(hasTx(TransactionType.MACRO_DEBT_BORROW));
        assertTrue(hasTx(TransactionType.MACRO_ADMIN));
    }

    @Test void emptyCentralBankPrintsMoneyAndRecordsIt() {
        AccountManager.getAccounts().remove(CentralBank.UUID);
        AccountManager.getOrCreateSystemAccount(CentralBank.UUID);
        MacroFiscalService.processDay(1);
        assertEquals(30, Treasury.debt());
        assertEquals(30, Treasury.cumulativeIssuance());
        assertTrue(hasTx(TransactionType.MACRO_DEBT_ISSUANCE));
    }

    @Test void interestAccruesAndSurplusRepaysDebt() {
        MacroFiscalService.processDay(1);
        long debt = Treasury.debt();
        long interest = MacroFiscalService.dailyInterest(debt);
        assertTrue(interest >= 1, "有债就至少付 1 元利息");
        AccountManager.deposit(Treasury.UUID, 10_000);
        MacroFiscalService.processDay(2);
        assertEquals(0, Treasury.debt(), "结余超过 7 天支出时自动还清");
        assertTrue(hasTx(TransactionType.MACRO_DEBT_INTEREST));
        assertTrue(hasTx(TransactionType.MACRO_DEBT_REPAY));
        Treasury.Day yesterday = Treasury.lastDay();
        assertEquals(1, yesterday.day());
        assertEquals(30L, yesterday.flows().get(Treasury.Item.ADMIN));
    }

    @Test void hittingTheDebtCeilingMeansAusterity() {
        MacroEconomyManager.setLaw(LawLevel.PUBLIC_HEALTH);
        Treasury.setDebt(finance.config.FinanceConfig.macroDebtCeiling());
        MacroFiscalService.processDay(1);
        assertTrue(Treasury.austerity(), "到上限后付不起的开支停发");
        assertEquals(0, AccountManager.getBalance(village.budgetAccountId()), "福利没发出去");
        PoliticsService.recompute();
        int withAusterity = MacroEconomyManager.approval(InterestGroup.RURAL_FOLK);
        Treasury.setAusterity(false);
        PoliticsService.recompute();
        assertEquals(withAusterity + 2, MacroEconomyManager.approval(InterestGroup.RURAL_FOLK), "紧缩让乡村民众 −2");
    }

    @Test void welfareIsPaidFromTheTreasury() {
        MacroEconomyManager.setLaw(LawLevel.POOR_RELIEF);
        AccountManager.getOrCreateSystemAccount(Treasury.UUID);
        AccountManager.deposit(Treasury.UUID, 100_000);
        long npc = AccountManager.getBalance(NpcMarketMaker.NPC_UUID);
        MacroFiscalService.processDay(1);
        long welfare = finance.config.FinanceConfig.macroWelfareReliefPerCapita() * village.population();
        assertEquals(welfare, AccountManager.getBalance(village.budgetAccountId()));
        assertEquals(npc + 30, AccountManager.getBalance(NpcMarketMaker.NPC_UUID), "NPC 资金池只收到行政开支，不再出福利");
        assertEquals(welfare, MacroEconomyManager.settlement(village.settlementId()).incomeToday);
    }

    @Test void sameDayRunsOnce() {
        MacroFiscalService.processDay(1);
        long debt = Treasury.debt();
        MacroFiscalService.processDay(1);
        MacroFiscalService.processDay(0);
        assertEquals(debt, Treasury.debt());
    }

    @Test void proposalFeeGoesToTheTreasury() {
        assertTrue(PoliticsService.propose(player, LawLevel.LOW_TAX, 1).success());
        assertEquals(finance.config.FinanceConfig.macroLawProposalFee(), MacroFiscalService.balance());
    }

    @Test void financiersDislikeHeavyDebt() {
        PoliticsService.recompute();
        int calm = MacroEconomyManager.approval(InterestGroup.FINANCIERS);
        Treasury.setDebt(finance.config.FinanceConfig.macroDebtCeiling());
        PoliticsService.recompute();
        assertEquals(calm - 2, MacroEconomyManager.approval(InterestGroup.FINANCIERS));
    }

    @Test void fiscalStateSurvivesSaveLoad() {
        MacroFiscalService.processDay(1);
        MacroFiscalService.processDay(2);
        long debt = Treasury.debt();
        long balance = MacroFiscalService.balance();
        CompoundTag saved = new EconomySavedData().save(new CompoundTag());
        EconomySavedData.load(saved);
        assertEquals(debt, Treasury.debt());
        assertEquals(balance, MacroFiscalService.balance());
        assertEquals(2, Treasury.lastProcessedDay());
        assertEquals(1, Treasury.history().size());
        assertTrue(finance.diagnostic.ModuleHealthRegistry.mayWrite(finance.diagnostic.ModuleHealthRegistry.Module.MACRO_ECONOMY));
    }
}
