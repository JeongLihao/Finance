package finance.bank;

import finance.account.AccountManager;
import finance.account.TransactionType;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import finance.debt.CompanyLoanManager;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 玩家创建银行：牌照、关联交易防火墙、自定利率、监管罚款、分红、处置、持久化。
 */
class PlayerBankServiceTest {
    UUID player = UUID.fromString("00000000-0000-0000-0000-000000007001");
    UUID other = UUID.fromString("00000000-0000-0000-0000-000000007002");
    UUID companyId = UUID.fromString("00000000-0000-0000-0000-000000007011");
    UUID otherCompanyId = UUID.fromString("00000000-0000-0000-0000-000000007012");

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        BankingManager.ensureDefaultBanks();
        CompanyManager.registerDirect(new Company(companyId, "OwnerCo", CompanyType.RAW_MATERIALS, 1_000_000, player));
        CompanyManager.registerDirect(new Company(otherCompanyId, "OtherCo", CompanyType.RAW_MATERIALS, 1_000_000, other));
        AccountManager.deposit(player, 30_000_000);
        AccountManager.deposit(other, 30_000_000);
    }

    @AfterEach
    void end() { EconomySavedData.resetRuntimeState(); }

    private UUID charterPlayerBank() {
        var r = PlayerBankService.charter(player, "PBK", "Player Bank", 10_000_000, 0);
        assertTrue(r.success(), r.message());
        return r.id();
    }

    @Test
    void charterTakesFeeAndCapitalFromWalletAndBalancesLedger() {
        long before = AccountManager.getBalance(player);
        UUID bankId = charterPlayerBank();
        CommercialBank bank = BankingManager.bank(bankId);
        assertNotNull(bank);
        assertEquals(player, PlayerBankService.ownerOf(bankId));
        assertEquals(before - 1_000_000 - 10_000_000, AccountManager.getBalance(player));
        assertTrue(bank.ledger().balanceSheet().balanced());
        assertEquals(10_000_000, bank.ledger().balance(BankLedgerAccount.ASSET_RESERVE));
        assertEquals(10_000_000, bank.ledger().balanceSheet().equity());
        assertEquals(BankStatus.ACTIVE, bank.status());
        assertTrue(AccountManager.getTransactions().stream().anyMatch(t -> t.getType() == TransactionType.BANK_CHARTER_FEE));
        assertTrue(AccountManager.getTransactions().stream().anyMatch(t -> t.getType() == TransactionType.BANK_CHARTER));
    }

    @Test
    void charterFailsWhenPoorOrDuplicate() {
        AccountManager.withdraw(player, 29_500_000); // 只剩 500k
        var poor = PlayerBankService.charter(player, "PBK", "Player Bank", 10_000_000, 0);
        assertFalse(poor.success());
        AccountManager.deposit(player, 30_000_000);
        UUID first = charterPlayerBank();
        assertNotNull(first);
        var second = PlayerBankService.charter(player, "PBK2", "Second", 10_000_000, 0);
        assertFalse(second.success(), "每人限开一家");
        var dupCode = PlayerBankService.charter(other, "PBK", "Other Bank", 10_000_000, 0);
        assertFalse(dupCode.success(), "代码不可重复");
        var dupName = PlayerBankService.charter(other, "OTB", "Player Bank", 10_000_000, 0);
        assertFalse(dupName.success(), "名称不可重复");
    }

    @Test
    void relatedPartyLoanIsHardDeniedByDefault() {
        UUID bankId = charterPlayerBank();
        // 所有者名下公司从自家银行贷款：拒绝
        var denied = CompanyLoanManager.applyCommercial(player, companyId, bankId, 100_000, 0, 10, 3);
        assertFalse(denied.success(), denied.message());
        assertTrue(CompanyLoanManager.loans().isEmpty());
        // 同一家公司从 NPC 银行贷款：正常（防火墙只认自家银行）
        CommercialBank npc = BankingManager.banks().values().stream()
                .filter(b -> !PlayerBankService.isPlayerBank(b.id())).findFirst().orElseThrow();
        var ok = CompanyLoanManager.applyCommercial(player, companyId, npc.id(), 100_000, 0, 10, 3);
        assertTrue(ok.success(), ok.message());
    }

    @Test
    void unrelatedCompanyCanBorrowFromPlayerBank() {
        UUID bankId = charterPlayerBank();
        // 他人公司从玩家银行贷款：正常业务，不拦截
        var ok = CompanyLoanManager.applyCommercial(other, otherCompanyId, bankId, 100_000, 0, 10, 3);
        assertTrue(ok.success(), ok.message());
        // 注：小号（不同 UUID）贷款无法识别——设计文档 §4 已声明能力边界
    }

    @Test
    void ownerCanSetSpreadsAndAccrualFollows() {
        UUID bankId = charterPlayerBank();
        var r = PlayerBankService.setSpreads(player, 0, 0, 100);
        assertTrue(r.success(), r.message());
        CommercialBank bank = BankingManager.bank(bankId);
        assertEquals(0, bank.policy().demandSpreadBps());
        // 他人不能调
        assertFalse(PlayerBankService.setSpreads(other, 0, 0, 0).success());
        // 非法利差拒绝
        assertFalse(PlayerBankService.setSpreads(player, -1, 0, 0).success());
        assertFalse(PlayerBankService.setSpreads(player, 0, 0, 10_001).success());
    }

    @Test
    void restrictedPlayerBankPaysDailyFine() {
        UUID bankId = charterPlayerBank();
        CommercialBank bank = BankingManager.bank(bankId);
        bank.setStatus(BankStatus.RESTRICTED);
        long reserveBefore = bank.ledger().balance(BankLedgerAccount.ASSET_RESERVE);
        PlayerBankService.applyDailyFine(bank, 5);
        assertEquals(reserveBefore - 50_000, bank.ledger().balance(BankLedgerAccount.ASSET_RESERVE));
        assertTrue(bank.ledger().balanceSheet().balanced());
        // NPC 银行不受玩家罚金影响
        CommercialBank npc = BankingManager.banks().values().stream()
                .filter(b -> !PlayerBankService.isPlayerBank(b.id())).findFirst().orElseThrow();
        npc.setStatus(BankStatus.RESTRICTED);
        long npcReserve = npc.ledger().balance(BankLedgerAccount.ASSET_RESERVE);
        PlayerBankService.applyDailyFine(npc, 5);
        assertEquals(npcReserve, npc.ledger().balance(BankLedgerAccount.ASSET_RESERVE));
    }

    @Test
    void dividendRequiresHealthyBankAndKeepsCapitalAdequacy() {
        UUID bankId = charterPlayerBank();
        long walletBefore = AccountManager.getBalance(player);
        var ok = PlayerBankService.dividend(player, 1_000_000, 1);
        assertTrue(ok.success(), ok.message());
        assertEquals(walletBefore + 1_000_000, AccountManager.getBalance(player));
        assertEquals(9_000_000, BankingManager.bank(bankId).ledger().balanceSheet().equity());
        assertTrue(BankingManager.bank(bankId).ledger().balanceSheet().balanced());
        // 非 ACTIVE 不可分红
        BankingManager.bank(bankId).setStatus(BankStatus.WATCH);
        assertFalse(PlayerBankService.dividend(player, 100, 1).success());
        // 超额分红拒绝
        BankingManager.bank(bankId).setStatus(BankStatus.ACTIVE);
        assertFalse(PlayerBankService.dividend(player, 100_000_000, 1).success());
    }

    @Test
    void failedPlayerBankIsResolvedAndOwnerEquityWiped() {
        UUID bankId = charterPlayerBank();
        AccountManager.deposit(player, 200_000);
        var dep = BankingManager.depositPlayer(player, bankId, 130_000, 0);
        assertTrue(dep.success());
        BankingManager.bank(bankId).setStatus(BankStatus.RESOLUTION);
        assertTrue(DepositInsuranceService.resolve(bankId, 1));
        assertEquals(BankStatus.FAILED, BankingManager.bank(bankId).status());
        // 所有者股权清零：保险赔付上限内转入桥接银行，超额部分留在旧行残值
        long bridgeBalance = BankingManager.accounts().values().stream()
                .filter(a -> a.ownerId().equals(player) && !a.bankId().equals(bankId))
                .mapToLong(BankCustomerAccount::balance).sum();
        assertEquals(DepositInsuranceService.PER_CUSTOMER_LIMIT, bridgeBalance);
        assertEquals(0, BankingManager.bank(bankId).ledger().balanceSheet().equity());
    }

    @Test
    void playerBankPaysInsurancePremiumAutomatically() {
        UUID bankId = charterPlayerBank();
        var dep = BankingManager.depositPlayer(player, bankId, 1_000_000, 0);
        assertTrue(dep.success());
        long fundBefore = DepositInsuranceService.fund();
        BankingManager.processDay(1);
        assertTrue(DepositInsuranceService.fund() > fundBefore, "玩家银行强制参保并缴费");
    }

    @Test
    void ownerSurvivesSaveLoadRoundTrip() {
        UUID bankId = charterPlayerBank();
        CompoundTag saved = new EconomySavedData().save(new CompoundTag());
        EconomySavedData.load(saved);
        assertEquals(player, PlayerBankService.ownerOf(bankId));
        assertNotNull(BankingManager.bank(bankId));
        assertTrue(BankingManager.bank(bankId).ledger().balanceSheet().balanced());
    }

    @Test
    void charterIsIdempotentAgainstDoubleSpend() {
        long balance = AccountManager.getBalance(player);
        // 钱包刚好够一次：第二次因"已有银行"被拒，不会重复扣款
        AccountManager.withdraw(player, balance - 11_000_000);
        assertTrue(PlayerBankService.charter(player, "PBK", "Player Bank", 10_000_000, 0).success());
        long afterFirst = AccountManager.getBalance(player);
        assertFalse(PlayerBankService.charter(player, "PBK2", "Second", 10_000_000, 0).success());
        assertEquals(afterFirst, AccountManager.getBalance(player));
    }

    // ---- 以下为 bug-sweep 修复回归用例 ----

    @Test
    void perPlayerLimitCountsBanksAndReportsRealNumbers() {
        // P1-15：上限校验按计数与 config 比较，报错信息与实际行为一致
        assertEquals(0, PlayerBankService.countBanksOfOwner(player));
        charterPlayerBank();
        assertEquals(1, PlayerBankService.countBanksOfOwner(player));
        var second = PlayerBankService.charter(player, "PBK2", "Second", 10_000_000, 0);
        assertFalse(second.success());
        assertTrue(second.message().contains("1 家银行"), second.message());
    }

    @Test
    void dividendDistributesProfitFromIncomeAccounts() {
        // P1-16：盈利后分红金额超过 PAID_IN 余额时，应从 INCOME 科目先行冲减，而非"银行账务失败"
        UUID bankId = charterPlayerBank();
        CommercialBank bank = BankingManager.bank(bankId);
        assertTrue(bank.ledger().post(1, UUID.randomUUID(), BankLedgerAccount.ASSET_RESERVE,
                BankLedgerAccount.INCOME_INTEREST, 2_000_000, BankLedgerReason.DEMAND_DEPOSIT_INTEREST));
        assertEquals(12_000_000, bank.ledger().balanceSheet().equity());
        long walletBefore = AccountManager.getBalance(player);
        var r = PlayerBankService.dividend(player, 11_000_000, 2);
        assertTrue(r.success(), r.message());
        assertEquals(walletBefore + 11_000_000, AccountManager.getBalance(player));
        assertEquals(1_000_000, bank.ledger().balanceSheet().equity());
        assertTrue(bank.ledger().balanceSheet().balanced());
        // P1-17：实收资本被冲减 9M，账面同步
        assertEquals(1_000_000, bank.paidInCapital());
        assertEquals(0, bank.ledger().balance(BankLedgerAccount.INCOME_INTEREST));
        assertEquals(1_000_000, bank.ledger().balance(BankLedgerAccount.EQUITY_PAID_IN));
    }

    @Test
    void paidInCapitalSurvivesSaveLoadAfterDividend() {
        // P1-17：分红后 NBT Capital 标签不失真；closeDay 首次快照不再把分红算成亏损
        UUID bankId = charterPlayerBank();
        assertTrue(PlayerBankService.dividend(player, 1_000_000, 1).success());
        CompoundTag saved = new EconomySavedData().save(new CompoundTag());
        EconomySavedData.load(saved);
        CommercialBank reloaded = BankingManager.bank(bankId);
        assertNotNull(reloaded);
        assertEquals(9_000_000, reloaded.paidInCapital());
        assertEquals(9_000_000, reloaded.ledger().balanceSheet().equity());
        BankingManager.closeDay(2);
        assertEquals(0, reloaded.snapshots().get(reloaded.snapshots().size() - 1).dailyProfit());
    }

    @Test
    void dividendRespectsLeverageFloor() {
        // P2：分红掏空转嫁保险基金——分红后权益须 ≥ 存款总额 8%（默认）
        UUID bankId = charterPlayerBank();
        assertTrue(BankingManager.depositPlayer(player, bankId, 1_000_000, 0).success());
        // 分红至权益 50k（< 存款 1M 的 8%=80k）：拒绝
        assertFalse(PlayerBankService.dividend(player, 9_950_000, 1).success());
        // 分红至权益 100k（≥ 80k）：放行
        var ok = PlayerBankService.dividend(player, 9_900_000, 1);
        assertTrue(ok.success(), ok.message());
        // 公开视图不再恒显示 1000%：rwa==0 时展示真实杠杆口径（100k/1M=10% → 1000bps）
        var view = PublicBankViewService.crop(BankingManager.bank(bankId), true);
        assertEquals(1_000, view.capitalBps());
    }

    @Test
    void removedBankCleansOwnerSoPlayerCanRecharter() {
        // P2：validateCustomerLedgers 删银行时同步清理 owner，玩家不被永久锁死
        UUID bankId = charterPlayerBank();
        CommercialBank bank = BankingManager.bank(bankId);
        assertTrue(bank.ledger().post(1, UUID.randomUUID(), BankLedgerAccount.ASSET_RESERVE,
                BankLedgerAccount.LIABILITY_DEMAND_DEPOSIT, 100, BankLedgerReason.DEMAND_DEPOSIT));
        CompoundTag saved = new EconomySavedData().save(new CompoundTag());
        EconomySavedData.load(saved);
        assertNull(BankingManager.bank(bankId));
        assertNull(PlayerBankService.ownerOf(bankId));
        assertEquals(0, PlayerBankService.countBanksOfOwner(player));
        var r = PlayerBankService.charter(player, "PBK2", "Second Bank", 10_000_000, 1);
        assertTrue(r.success(), r.message());
    }

    @Test
    void charterFeeRecordUsesSystemAccount() {
        // P2：牌照费被销毁，审计记录收款方记系统账户而非银行
        charterPlayerBank();
        var feeRecord = AccountManager.getTransactions().stream()
                .filter(t -> t.getType() == TransactionType.BANK_CHARTER_FEE).findFirst().orElseThrow();
        assertEquals(new UUID(0L, 0L), feeRecord.getTo());
    }

    @Test
    void wipePlayerEquityUsesDedicatedReasonAndKeepsPaidInCapital() {
        // P2：EQUITY_WIPE reason；P1-17 注：亏损核销不得动 paidInCapital
        UUID bankId = charterPlayerBank();
        AccountManager.deposit(player, 200_000);
        assertTrue(BankingManager.depositPlayer(player, bankId, 130_000, 0).success());
        BankingManager.bank(bankId).setStatus(BankStatus.RESOLUTION);
        assertTrue(DepositInsuranceService.resolve(bankId, 1));
        CommercialBank bank = BankingManager.bank(bankId);
        assertTrue(bank.ledger().entries().stream().anyMatch(e -> e.reason() == BankLedgerReason.EQUITY_WIPE));
        assertEquals(10_000_000, bank.paidInCapital());
    }

    @Test
    void blankBankNameIsRejected() {
        // P2：空白银行名拒绝
        assertFalse(PlayerBankService.charter(player, "PBK", "   ", 10_000_000, 0).success());
    }

    private UUID registerSecondBankDirect() {
        UUID bankId2 = UUID.fromString("00000000-0000-0000-0000-000000007021");
        CommercialBank b2 = new CommercialBank(bankId2, "PBK2", "Second Bank", BankStatus.ACTIVE, 10_000_000, BankPolicy.standard());
        assertTrue(b2.ledger().post(0, UUID.randomUUID(), BankLedgerAccount.ASSET_RESERVE, BankLedgerAccount.EQUITY_PAID_IN,
                10_000_000, BankLedgerReason.CHARTER_CAPITAL));
        BankingManager.putBankDirect(b2);
        PlayerBankService.putOwnerDirect(bankId2, player, 0);
        return bankId2;
    }

    @Test
    void setSpreadsWithBankIdTargetsSecondBank() {
        UUID first = charterPlayerBank();
        UUID second = registerSecondBankDirect();
        var r = PlayerBankService.setSpreads(player, second, 111, 222, 333);
        assertTrue(r.success(), r.message());
        assertEquals(111, BankingManager.bank(second).policy().demandSpreadBps());
        assertEquals(150, BankingManager.bank(first).policy().demandSpreadBps());
        // 不填 bankId → 默认第一家（向后兼容）
        var r2 = PlayerBankService.setSpreads(player, 1, 2, 3);
        assertTrue(r2.success(), r2.message());
        assertEquals(1, BankingManager.bank(first).policy().demandSpreadBps());
        assertEquals(111, BankingManager.bank(second).policy().demandSpreadBps());
    }

    @Test
    void setSpreadsRejectsForeignOrMissingBankId() {
        charterPlayerBank();
        var rc = PlayerBankService.charter(other, "OTB", "Other Bank", 10_000_000, 0);
        assertTrue(rc.success(), rc.message());
        assertFalse(PlayerBankService.setSpreads(player, rc.id(), 0, 0, 0).success());
        assertFalse(PlayerBankService.setSpreads(player, UUID.randomUUID(), 0, 0, 0).success());
        UUID npc = BankingManager.banks().values().stream()
                .filter(b -> !PlayerBankService.isPlayerBank(b.id())).findFirst().orElseThrow().id();
        assertFalse(PlayerBankService.setSpreads(player, npc, 0, 0, 0).success());
    }

    @Test
    void dividendWithBankIdTargetsSecondBank() {
        UUID first = charterPlayerBank();
        UUID second = registerSecondBankDirect();
        long walletBefore = AccountManager.getBalance(player);
        var ok = PlayerBankService.dividend(player, second, 1_000_000, 1);
        assertTrue(ok.success(), ok.message());
        assertEquals(walletBefore + 1_000_000, AccountManager.getBalance(player));
        assertEquals(9_000_000, BankingManager.bank(second).ledger().balanceSheet().equity());
        assertEquals(10_000_000, BankingManager.bank(first).ledger().balanceSheet().equity());
        var rc = PlayerBankService.charter(other, "OTB", "Other Bank", 10_000_000, 0);
        assertTrue(rc.success(), rc.message());
        assertFalse(PlayerBankService.dividend(player, rc.id(), 100, 1).success());
    }
}
