package finance.bank;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.debt.CompanyLoan;
import finance.debt.CompanyLoanManager;
import finance.debt.LoanLenderType;
import finance.debt.LoanStatus;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家创建银行：牌照、自定利率、关联交易防火墙、监管罚款、银行分红。
 *
 * 设计要点：
 * - 复用 {@link CommercialBank} 作为数据模型，复用 NPC 银行的全部监管/保险/处置链路
 *   （{@link BankingManager#processDay} 自动遍历所有银行，玩家银行天然在内）。
 * - {@link #ownerOf(UUID)} 为中性查询，不写死"NPC 银行"假设，便于将来接入
 *   feat/bank-runs 的恐慌度框架（玩家银行同样适用恐慌度）。
 * - 小号（女巫）攻击的诚实声明：关联判定按 owner UUID，玩家用小号开银行、
 *   大号公司贷款，模组层面无法识别；纵深防御靠集中度限制与保险共担。
 */
public final class PlayerBankService {
    /** bankId -> ownerPlayerId */
    private static final Map<UUID, UUID> OWNERS = new LinkedHashMap<>();
    private static final Map<UUID, Long> CHARTER_DAYS = new LinkedHashMap<>();
    /** 牌照费被销毁：审计记录的收款方记系统账户（UUID 0），而非银行，避免虚增银行流入。 */
    private static final UUID SYSTEM_ACCOUNT = new UUID(0L, 0L);
    private PlayerBankService() { }

    /** 是否为玩家银行；中性查询。 */
    public static UUID ownerOf(UUID bankId) { return bankId == null ? null : OWNERS.get(bankId); }
    public static boolean isPlayerBank(UUID bankId) { return ownerOf(bankId) != null; }
    /** 玩家名下的第一家银行（每人限开，config 可调）。 */
    public static UUID bankOfOwner(UUID playerId) {
        if (playerId == null) return null;
        for (Map.Entry<UUID, UUID> e : OWNERS.entrySet()) if (playerId.equals(e.getValue())) return e.getKey();
        return null;
    }
    /** 玩家名下银行数量（P1-15：上限校验以此计数与 config 比较，而非一刀切只许 1 家）。 */
    public static int countBanksOfOwner(UUID playerId) {
        if (playerId == null) return 0;
        int n = 0;
        for (UUID owner : OWNERS.values()) if (playerId.equals(owner)) n++;
        return n;
    }
    public static long charterDay(UUID bankId) { return CHARTER_DAYS.getOrDefault(bankId, -1L); }

    /**
     * 申请银行牌照：牌照费（销毁）＋实缴资本金（进银行账本），全部来自玩家钱包。
     * 注资来源检查：牌照时银行尚不存在，"资本金来自自家贷款"在构造上不可能。
     */
    public static synchronized BankingManager.Result charter(UUID player, String code, String name, long capital, long day) {
        if (!FinanceConfig.bankingEnabled() || player == null) return BankingManager.Result.fail("银行系统未启用");
        BankingManager.ensureDefaultBanks();
        String c = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        String n = name == null ? "" : name.trim();
        long minCapital = Math.max(1, FinanceConfig.playerBankMinCapital());
        long fee = Math.max(0, FinanceConfig.playerBankCharterFee());
        if (c.length() < 2 || c.length() > 16 || !c.matches("[A-Z0-9]+")) return BankingManager.Result.fail("银行代码须为2-16位字母数字");
        if (n.isBlank() || n.length() > 64) return BankingManager.Result.fail("银行名称无效");
        if (capital < minCapital || day < 0) return BankingManager.Result.fail("实缴资本金不足（最低 " + minCapital + "）");
        int maxBanks = Math.max(1, FinanceConfig.playerBankMaxPerPlayer());
        int owned = countBanksOfOwner(player);
        if (owned >= maxBanks) return BankingManager.Result.fail("每名玩家限开 " + maxBanks + " 家银行（名下已有 " + owned + " 家）");
        if (BankingManager.banks().size() >= BankingManager.MAX_BANKS) return BankingManager.Result.fail("银行总数已达上限");
        for (CommercialBank b : BankingManager.banks().values()) {
            if (b.code().equalsIgnoreCase(c)) return BankingManager.Result.fail("银行代码已存在");
            if (b.name().equalsIgnoreCase(n)) return BankingManager.Result.fail("银行名称已存在");
        }
        long total;
        try { total = Math.addExact(fee, capital); } catch (ArithmeticException e) { return BankingManager.Result.fail("金额溢出"); }
        if (AccountManager.getBalance(player) < total) return BankingManager.Result.fail("钱包余额不足（需牌照费 " + fee + " ＋资本金 " + capital + "）");
        UUID bankId = UUID.randomUUID();
        CommercialBank bank = new CommercialBank(bankId, c, n, BankStatus.ACTIVE, capital, BankPolicy.standard());
        UUID ref = UUID.randomUUID();
        if (!AccountManager.withdraw(player, total)) return BankingManager.Result.fail("钱包扣款失败");
        boolean posted = bank.ledger().post(day, ref, BankLedgerAccount.ASSET_RESERVE, BankLedgerAccount.EQUITY_PAID_IN,
                capital, BankLedgerReason.CHARTER_CAPITAL);
        if (!posted || !bank.ledger().balanceSheet().balanced()) {
            AccountManager.deposit(player, total);
            return BankingManager.Result.fail("银行建账失败，已退款");
        }
        BankingManager.putBankDirect(bank);
        if (BankingManager.bank(bankId) == null) {
            // P2：putBankDirect 在银行总数达 MAX_BANKS 时静默丢弃；落库失败则回滚，不登记 owner。
            AccountManager.deposit(player, total);
            return BankingManager.Result.fail("银行注册失败（银行总数达上限），牌照费与资本金已全额退款");
        }
        OWNERS.put(bankId, player);
        CHARTER_DAYS.put(bankId, day);
        BankRegulatoryService.evaluate(bank);
        record(player, SYSTEM_ACCOUNT, fee, TransactionType.BANK_CHARTER_FEE, "银行牌照费:" + c);
        record(player, bankId, capital, TransactionType.BANK_CHARTER, "银行开业:" + n);
        EconomySavedData.markDirty();
        return BankingManager.Result.ok(bankId, "银行「" + n + "」开业成功");
    }

    /** 所有者自定存贷利差；全部定价链路实时读 bank.policy()，自动生效。bankId 为空时默认名下第一家银行。 */
    public static synchronized BankingManager.Result setSpreads(UUID player, int demandSpreadBps, int timeSpreadBps, int loanSpreadBps) {
        return setSpreads(player, null, demandSpreadBps, timeSpreadBps, loanSpreadBps);
    }
    public static synchronized BankingManager.Result setSpreads(UUID player, UUID bankId, int demandSpreadBps, int timeSpreadBps, int loanSpreadBps) {
        UUID target = bankId != null ? bankId : bankOfOwner(player);
        CommercialBank bank = target == null ? null : BankingManager.bank(target);
        if (bank == null) return BankingManager.Result.fail(bankId != null ? "银行不存在" : "名下没有银行");
        if (!player.equals(ownerOf(target))) return BankingManager.Result.fail("该银行不属于你");
        if (!bank.acceptsNewBusiness()) return BankingManager.Result.fail("银行当前不可展业，无法调价");
        if (!validSpread(demandSpreadBps) || !validSpread(timeSpreadBps) || !validSpread(loanSpreadBps))
            return BankingManager.Result.fail("利差须在 0–10000 基点之间");
        BankPolicy old = bank.policy();
        bank.updatePolicy(new BankPolicy(demandSpreadBps, timeSpreadBps, loanSpreadBps,
                old.targetReserveBps(), old.singleBorrowerLimitBps()));
        EconomySavedData.markDirty();
        return BankingManager.Result.ok(target, "利率政策已更新");
    }

    /**
     * 银行分红（简化版退出机制）：所有者从银行 equity 中提取。
     * 条件：ACTIVE 状态；分红后资本充足率仍达标；分红后杠杆率仍达标（权益 ≥ 存款×config 比例，
     * 防范分红掏空转嫁保险基金）；准备金足够。
     * 分红按"先损益、后留存、最后实收"的顺序拆分多笔 draft 记账（P1-16：盈利部分也可分配），
     * 实收资本被冲减的部分同步扣减 {@link CommercialBank#paidInCapital()}（P1-17）。
     */
    public static synchronized BankingManager.Result dividend(UUID player, long amount, long day) {
        return dividend(player, null, amount, day);
    }
    public static synchronized BankingManager.Result dividend(UUID player, UUID bankId, long amount, long day) {
        UUID target = bankId != null ? bankId : bankOfOwner(player);
        CommercialBank bank = target == null ? null : BankingManager.bank(target);
        if (bank == null) return BankingManager.Result.fail(bankId != null ? "银行不存在" : "名下没有银行");
        if (!player.equals(ownerOf(target))) return BankingManager.Result.fail("该银行不属于你");
        if (amount <= 0 || day < 0) return BankingManager.Result.fail("分红金额无效");
        if (bank.status() != BankStatus.ACTIVE) return BankingManager.Result.fail("银行非正常经营状态，不可分红");
        if (!AccountManager.canDeposit(player, amount)) return BankingManager.Result.fail("钱包无法接收分红");
        BankBalanceSheet sheet = bank.ledger().balanceSheet();
        long equity = sheet.equity();
        long reserve = bank.ledger().balance(BankLedgerAccount.ASSET_RESERVE);
        if (amount > equity || amount > reserve) return BankingManager.Result.fail("可分红金额不足");
        long rwa = BankRegulatoryService.riskWeightedAssets(bank);
        long equityAfter;
        try { equityAfter = Math.subtractExact(equity, amount); } catch (ArithmeticException e) { return BankingManager.Result.fail("金额溢出"); }
        int carAfter = rwa == 0 ? (equityAfter >= 0 ? 100_000 : Integer.MIN_VALUE)
                : BigInteger.valueOf(equityAfter).multiply(BigInteger.valueOf(10_000)).divide(BigInteger.valueOf(rwa))
                        .max(BigInteger.valueOf(Integer.MIN_VALUE)).min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
        if (carAfter < FinanceConfig.bankMinimumCapitalBps()) return BankingManager.Result.fail("分红后资本充足率不达标");
        long deposits = BigInteger.valueOf(Math.max(0, sheet.demandDeposits())).add(BigInteger.valueOf(Math.max(0, sheet.timeDeposits())))
                .min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
        int leverageBps = Math.max(0, Math.min(10_000, FinanceConfig.playerBankDividendLeverageBps()));
        long minEquityAfter = BigInteger.valueOf(deposits).multiply(BigInteger.valueOf(leverageBps))
                .divide(BigInteger.valueOf(10_000)).longValue();
        if (equityAfter < minEquityAfter) return BankingManager.Result.fail("分红后杠杆率不达标（权益须 ≥ 存款总额 " + (leverageBps / 100) + "%）");
        // P1-16：拆分 draft——先冲减当期损益（INCOME_*）、再冲留存收益、最后冲实收资本。
        List<BankLedger.Draft> drafts = new ArrayList<>();
        long rest = amount, paidInTake = 0;
        for (BankLedgerAccount acct : new BankLedgerAccount[]{
                BankLedgerAccount.INCOME_INTEREST, BankLedgerAccount.INCOME_FEE,
                BankLedgerAccount.EQUITY_RETAINED, BankLedgerAccount.EQUITY_PAID_IN}) {
            if (rest <= 0) break;
            long take = Math.min(rest, Math.max(0, bank.ledger().balance(acct)));
            if (take <= 0) continue;
            drafts.add(new BankLedger.Draft(acct, BankLedgerAccount.ASSET_RESERVE, take, BankLedgerReason.BANK_DIVIDEND));
            if (acct == BankLedgerAccount.EQUITY_PAID_IN) paidInTake = take;
            rest -= take;
        }
        if (rest > 0 || drafts.isEmpty()) return BankingManager.Result.fail("银行账务失败");
        UUID ref = UUID.randomUUID();
        if (!bank.ledger().postBatch(day, ref, drafts)) return BankingManager.Result.fail("银行账务失败");
        // P1-17：分红实质减资，同步扣减账面实收资本（NBT Capital 标签与 closeDay 快照口径随之修正）。
        long paidInDelta = -Math.min(paidInTake, bank.paidInCapital());
        bank.adjustPaidInCapital(paidInDelta);
        if (!AccountManager.deposit(player, amount)) {
            // P2：玩家未到账则反向冲销，把扣款冲回银行，避免资金销毁。
            List<BankLedger.Draft> reverse = new ArrayList<>();
            for (BankLedger.Draft d : drafts) reverse.add(new BankLedger.Draft(d.credit(), d.debit(), d.amount(), BankLedgerReason.BANK_DIVIDEND));
            bank.adjustPaidInCapital(-paidInDelta);
            if (!bank.ledger().postBatch(day, UUID.randomUUID(), reverse)) throw new IllegalStateException("dividend reversal failed");
            return BankingManager.Result.fail("钱包入账失败，已撤销银行扣款");
        }
        BankRegulatoryService.evaluate(bank);
        record(target, player, amount, TransactionType.BANK_DIVIDEND, "银行分红:" + bank.code());
        EconomySavedData.markDirty();
        return BankingManager.Result.ok(target, "分红成功");
    }

    /**
     * 关联交易防火墙：银行所有者名下公司从自家银行贷款的上限（bps of equity）。
     * 默认 0＝硬拒绝；config 可调。
     */
    public static long relatedPartyLimit(CommercialBank bank) {
        if (bank == null || !isPlayerBank(bank.id())) return 0;
        long equity = Math.max(0, bank.ledger().balanceSheet().equity());
        int bps = Math.max(0, Math.min(10_000, FinanceConfig.playerBankRelatedPartyLimitBps()));
        return BigInteger.valueOf(equity).multiply(BigInteger.valueOf(bps)).divide(BigInteger.valueOf(10_000)).longValue();
    }

    /** 该银行已贷给某 owner 名下公司的未结清本金（关联敞口）。 */
    public static long relatedExposure(UUID bankId, UUID ownerId) {
        if (bankId == null || ownerId == null) return 0;
        Company company = CompanyManager.getCompanyByOwner(ownerId);
        if (company == null) return 0;
        UUID companyId = company.getCompanyId();
        long sum = 0;
        for (CompanyLoan l : CompanyLoanManager.loans().values()) {
            if (l.lenderType() == LoanLenderType.COMMERCIAL_BANK && bankId.equals(l.lenderId())
                    && companyId.equals(l.companyId())
                    && l.status() != LoanStatus.REPAID && l.status() != LoanStatus.CANCELLED) {
                sum = sum > Long.MAX_VALUE - l.outstandingPrincipal() ? Long.MAX_VALUE : sum + l.outstandingPrincipal();
            }
        }
        return sum;
    }

    /**
     * 日终监管罚款：RESTRICTED 的玩家银行每日罚金（WATCH 仅警告）。由 BankingManager.processDay 调用。
     * 幂等性说明：罚款引用是 (bankId:fine:day) 确定性 UUID，同一天重跑会被账本拒绝；
     * 且 processDay 受 lastDepositDay 守卫（day <= lastDepositDay 直接返回），同 day 不会二次进入——
     * 即使账本 compact 清空引用（基座另案修复），同 day 重跑 double-fine 仍不可达，故不改逻辑。
     */
    public static void applyDailyFine(CommercialBank bank, long day) {
        if (bank == null || !isPlayerBank(bank.id())) return;
        if (bank.status() != BankStatus.RESTRICTED) return;
        long fine = FinanceConfig.playerBankDailyFine();
        if (fine <= 0 || day < 0) return;
        long actual = Math.min(fine, bank.ledger().balance(BankLedgerAccount.ASSET_RESERVE));
        if (actual <= 0) return;
        UUID ref = UUID.nameUUIDFromBytes((bank.id() + ":fine:" + day).getBytes(StandardCharsets.UTF_8));
        if (bank.ledger().post(day, ref, BankLedgerAccount.EXPENSE_FINE, BankLedgerAccount.ASSET_RESERVE,
                actual, BankLedgerReason.REGULATORY_FINE)) {
            BankRegulatoryService.evaluate(bank);
            EconomySavedData.markDirty();
        }
    }

    // ---- 持久化直写（仅 BankingDataSerializer 调用） ----
    public static synchronized void putOwnerDirect(UUID bankId, UUID owner, long charterDay) {
        if (bankId != null && owner != null) {
            OWNERS.put(bankId, owner);
            CHARTER_DAYS.put(bankId, charterDay);
        }
    }
    /** P2：银行被 validateCustomerLedgers 移除时同步清理 owner 残留，避免玩家被永久挡在牌照门外。 */
    public static synchronized void removeOwnerDirect(UUID bankId) {
        if (bankId != null) {
            OWNERS.remove(bankId);
            CHARTER_DAYS.remove(bankId);
        }
    }
    public static synchronized void clearDirect() { OWNERS.clear(); CHARTER_DAYS.clear(); }
    public static synchronized Map<UUID, UUID> ownersView() { return Map.copyOf(OWNERS); }

    private static boolean validSpread(int bps) { return bps >= 0 && bps <= 10_000; }
    private static void record(UUID from, UUID to, long amount, TransactionType type, String object) {
        AccountManager.addTransactionRecord(new TransactionRecord(from, to, amount, type, null, object, 1));
    }
}
