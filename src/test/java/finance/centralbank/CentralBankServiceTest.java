package finance.centralbank;

import static org.junit.jupiter.api.Assertions.*;

import finance.account.AccountManager;
import finance.account.TransactionType;
import finance.bank.BankLedgerAccount;
import finance.bank.BankRegulatoryService;
import finance.bank.BankingManager;
import finance.bank.CommercialBank;
import finance.bank.PlayerBankService;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.fixedincome.TreasuryBondManager;
import finance.macro.EconomyAchievements;
import finance.market.CentralBank;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.policy.PolicyRuleService;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 中央银行：物价目标、准备金率、公开市场操作、商品平准、成就与存档。 */
class CentralBankServiceTest {
    private final UUID player = UUID.fromString("00000000-0000-0000-0000-00000000c001");

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        CommodityInventoryManager.clearInventoriesDirect();
        CommodityRegistry.resetToDefaults();
        CommodityRegistry.register(new Commodity("wheat", "minecraft:wheat", "Wheat", CommodityCategory.FOOD, 8));
        BankingManager.ensureDefaultBanks();
        AccountManager.deposit(player, 50_000_000);
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
        CommodityInventoryManager.clearInventoriesDirect();
        CommodityRegistry.resetToDefaults();
    }

    private static PolicyRuleService.Evaluation eval(double inflationPct, double slack) {
        return new PolicyRuleService.Evaluation(
                500, inflationPct, slack, 500, 500, PolicyRuleService.Direction.HOLD, "test");
    }

    @Test
    void inflationTargetPullsTheRuleTowardsEasingWhenPricesAreFlat() {
        var flat = PolicyRuleService.evaluate(500, 100.0, 100.0, 0.0);
        int expected = (int) Math.round(FinanceConfig.policyNeutralRateBps()
                - FinanceConfig.policyInflationResponse() * FinanceConfig.cbInflationTargetBps());
        assertEquals(
                Math.max(FinanceConfig.minBenchmarkRateBasisPoints(), expected),
                flat.targetBps(),
                "物价不涨时通胀比 2% 目标低，规则建议降息");
        assertTrue(flat.explanation().contains("目标"));
    }

    @Test
    void reserveRatioRuleMovesOneStepWithinBounds() {
        int band = FinanceConfig.cbInflationBandBps(), step = FinanceConfig.cbReserveStepBps();
        assertEquals(1_000 + step, CentralBankService.nextReserve(1_000, 2 * band + 1, 0));
        assertEquals(1_000 - step, CentralBankService.nextReserve(1_000, -2 * band - 1, 0));
        assertEquals(1_000 - step, CentralBankService.nextReserve(1_000, 0, 0.5), "经济疲软也降准");
        assertEquals(1_000, CentralBankService.nextReserve(1_000, band, 0), "区间内不动");
        assertEquals(
                FinanceConfig.cbReserveMaxBps(),
                CentralBankService.nextReserve(FinanceConfig.cbReserveMaxBps(), 9_999, 0));
        assertEquals(
                FinanceConfig.cbReserveMinBps(),
                CentralBankService.nextReserve(FinanceConfig.cbReserveMinBps(), -9_999, 0));
    }

    @Test
    void meetingRaisesReserveRatioAndRequiredReservesFollow() {
        CommercialBank bank = BankingManager.banks().values().iterator().next();
        assertTrue(BankingManager.depositPlayer(player, bank.id(), 1_000_000, 1).success());
        long before = BankRegulatoryService.requiredReserves(bank);
        int ratio = CentralBankService.demandReserveBps();
        CentralBankService.onMeeting(30, eval(10.0, 0), 500, 600, false);
        assertEquals(ratio + FinanceConfig.cbReserveStepBps(), CentralBankService.demandReserveBps());
        assertEquals(CentralBankService.demandReserveBps() / 2, CentralBankService.timeReserveBps());
        assertTrue(BankRegulatoryService.requiredReserves(bank) >= before, "法定准备金随准备金率上调");
        assertEquals(1, CentralBankService.decisions().size());
        CentralBankService.onMeeting(60, eval(10.0, 0), 600, 600, true);
        assertEquals(ratio + FinanceConfig.cbReserveStepBps(), CentralBankService.demandReserveBps(), "手动模式不自动调准备金率");
    }

    @Test
    void reserveCutRewardsBankOwnersAndRateCutRewardsEarlyBondBuyers() {
        var charter = PlayerBankService.charter(player, "PBK", "Player Bank", 10_000_000, 0);
        assertTrue(charter.success(), charter.message());
        long amount = Math.max(FinanceConfig.treasuryBondMinAmount(), 10_000);
        assertTrue(TreasuryBondManager.subscribe(player, 90, amount, 29).success());
        CentralBankService.onMeeting(30, eval(-10.0, 0), 500, 400, false);
        assertTrue(EconomyAchievements.pending(player).contains(EconomyAchievements.RESERVE_CUT));
        assertTrue(EconomyAchievements.pending(player).contains(EconomyAchievements.RATE_CALL), "降息前一天认购债券");
    }

    @Test
    void openMarketAbsorbsExcessReservesWhenInflationIsHighAndReleasesThemLater() {
        CommercialBank bank = BankingManager.banks().values().iterator().next();
        assertTrue(BankingManager.depositPlayer(player, bank.id(), 5_000_000, 1).success());
        long reserves = bank.ledger().balance(BankLedgerAccount.ASSET_RESERVE);
        long excess = reserves - BankRegulatoryService.requiredReserves(bank);
        assertTrue(excess > 0, "存款进来后银行有超额准备金");
        CentralBankService.openMarket(2, 1_000); // 通胀比目标高 10 个百分点
        long position = CentralBankService.positions().getOrDefault(bank.id(), 0L);
        assertTrue(position > 0, "央行卖出票据吸收准备金");
        assertEquals(reserves - position, bank.ledger().balance(BankLedgerAccount.ASSET_RESERVE));
        assertTrue(
                bank.ledger().balance(BankLedgerAccount.ASSET_RESERVE) >= BankRegulatoryService.requiredReserves(bank),
                "只吸收超额部分，不让银行跌破法定准备金");
        assertTrue(bank.ledger().balanceSheet().balanced());
        // 银行把剩下的超额准备金都放了贷款，再碰上通缩：央行买回票据投放准备金
        long spare =
                bank.ledger().balance(BankLedgerAccount.ASSET_RESERVE) - BankRegulatoryService.requiredReserves(bank);
        assertTrue(bank.ledger()
                .post(
                        2,
                        UUID.randomUUID(),
                        BankLedgerAccount.ASSET_COMPANY_LOAN,
                        BankLedgerAccount.ASSET_RESERVE,
                        spare,
                        finance.bank.BankLedgerReason.COMPANY_LOAN_ISSUE));
        CentralBankService.openMarket(3, -2_000);
        assertTrue(CentralBankService.positions().getOrDefault(bank.id(), 0L) < position, "通缩时买回票据投放准备金");
        assertTrue(bank.ledger().balanceSheet().balanced());
        assertTrue(CentralBankService.absorbedTotal() > 0 && CentralBankService.injectedTotal() > 0);
    }

    @Test
    void stabilizationSellsReservesWhenAConsumerGoodIsTooExpensive() {
        NpcMarketMaker.resetSeedState();
        NpcMarketMaker.seedNpcIfNeeded();
        CentralBank.seedIfNeeded();
        MarketPrice wheat = NpcMarketMaker.getMarketPrice("wheat");
        assertNotNull(wheat);
        wheat.setMidPrice(wheat.getBasePrice() * 2);
        int npcBefore = CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, "wheat");
        int cbBefore = CommodityInventoryManager.getCommodityAmount(CentralBank.UUID, "wheat");
        PriceStabilizationService.processDay(5);
        var actions = PriceStabilizationService.lastActions();
        assertEquals(1, actions.size());
        assertTrue(actions.get(0).sold());
        int qty = actions.get(0).quantity();
        assertEquals(npcBefore + qty, CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, "wheat"));
        assertEquals(cbBefore - qty, CommodityInventoryManager.getCommodityAmount(CentralBank.UUID, "wheat"));
        assertTrue(
                qty <= CentralBankService.bps(MarketPrice.REFERENCE_STOCK, FinanceConfig.cbStabilizationMaxDailyBps()));
        assertTrue(AccountManager.getTransactions().stream()
                .anyMatch(t -> t.getType() == TransactionType.CENTRAL_BANK_STABILIZATION));
        assertTrue(PriceStabilizationService.isConsumerGood("wheat"));
        assertFalse(PriceStabilizationService.isConsumerGood("diamond"));
    }

    @Test
    void legacyTreasuryCashMovesIntoTheCentralBankOnce() {
        AccountManager.getOrCreateSystemAccount(LegacyTreasuryMigration.LEGACY_TREASURY);
        AccountManager.deposit(LegacyTreasuryMigration.LEGACY_TREASURY, 12_345);
        AccountManager.getOrCreateSystemAccount(CentralBank.UUID);
        long cb = AccountManager.getBalance(CentralBank.UUID);
        assertEquals(12_345, LegacyTreasuryMigration.run());
        assertEquals(0, AccountManager.getBalance(LegacyTreasuryMigration.LEGACY_TREASURY));
        assertEquals(cb + 12_345, AccountManager.getBalance(CentralBank.UUID));
        assertEquals(0, LegacyTreasuryMigration.run(), "第二次什么也不做");
    }

    @Test
    void stateSurvivesSaveLoad() {
        CommercialBank bank = BankingManager.banks().values().iterator().next();
        assertTrue(BankingManager.depositPlayer(player, bank.id(), 5_000_000, 1).success());
        CentralBankService.onMeeting(30, eval(10.0, 0), 500, 600, false);
        CentralBankService.openMarket(31, 1_000);
        int ratio = CentralBankService.demandReserveBps();
        var positions = CentralBankService.positions();
        CompoundTag saved = new EconomySavedData().save(new CompoundTag());
        EconomySavedData.load(saved);
        assertEquals(ratio, CentralBankService.demandReserveBps());
        assertEquals(positions, CentralBankService.positions());
        assertEquals(1, CentralBankService.decisions().size());
    }
}
