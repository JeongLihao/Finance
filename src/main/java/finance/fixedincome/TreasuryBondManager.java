package finance.fixedincome;

import com.mojang.logging.LogUtils;
import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.market.CentralBank;
import finance.util.MathUtil;
import org.slf4j.Logger;

import java.math.BigInteger;
import java.util.*;

/**
 * 国债管理器 —— 央行长期限无风险债券（30/90/180 天），玩家认购持有到期。
 *
 * <p>与央行票据的区别：期限更长、利率更高（期限溢价单调递增）、支持提前赎回
 * （仅返还本金，利息全额罚没）。收益率 = 基准利率 ＋ 期限溢价，认购瞬间锁定。</p>
 *
 * <p>注：央行账户首次被触达时会按 {@link AccountManager} 通例获得 1000 新手启动资金
 * （每世界一次、金额微小，属预期行为）；兑付缺口投放按 {@code max(0, due − reserve)} 计算，
 * 该笔资金只会减少缺口投放、不会增加印钞量。</p>
 */
public final class TreasuryBondManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int MAX_BONDS = 4_096;
    /** 期限白名单（MC 天），与票据/定存错开定位；有意写死，期限相关的耗时/数值参数走 config。 */
    private static final Set<Integer> TERMS = Set.of(30, 90, 180);
    private static final Map<UUID, TreasuryBond> BONDS = new LinkedHashMap<>();
    private static long cumulativeIssuance;
    private static long lastIssuance;
    private static long lastIssuanceDay = -1;
    private TreasuryBondManager() { }

    public static Result subscribe(UUID playerId, int termDays, long principal, long currentDay) {
        if (playerId == null || playerId.equals(CentralBank.UUID) || !TERMS.contains(termDays)
                || principal <= 0 || currentDay < 0) return Result.fail("国债认购参数无效");
        if (principal < FinanceConfig.treasuryBondMinAmount()) return Result.fail("国债未达起认金额");
        long maturity;
        try { maturity = Math.addExact(currentDay, termDays); }
        catch (ArithmeticException ignored) { return Result.fail("到期日溢出"); }
        // 单人 ACTIVE 本金上限
        long playerActive = activePrincipalByPlayer(playerId);
        if (playerActive > Long.MAX_VALUE - principal
                || playerActive + principal > FinanceConfig.treasuryBondMaxPerPlayer())
            return Result.fail("国债已达个人持有上限");
        // 分期限档发行余额上限
        long termOutstanding = outstandingByTerm(termDays);
        if (termOutstanding > Long.MAX_VALUE - principal
                || termOutstanding + principal > FinanceConfig.treasuryBondMaxIssuePerTerm())
            return Result.fail("本期限国债发行额度已满");
        int rate = YieldCurveService.treasuryYieldBasisPoints(termDays);
        if (rate <= 0) return Result.fail("国债收益率异常");
        TreasuryBond bond = BONDS.values().stream().filter(b -> b.status() == TreasuryBondStatus.ACTIVE
                && b.issueDay() == currentDay && b.termDays() == termDays).findFirst().orElse(null);
        if (bond == null) {
            if (BONDS.size() >= MAX_BONDS) return Result.fail("国债数量达到上限");
            bond = new TreasuryBond(UUID.randomUUID(), termDays, rate, currentDay, maturity, TreasuryBondStatus.ACTIVE);
            BONDS.put(bond.id(), bond);
        }
        long old = bond.principalByPlayer().getOrDefault(playerId, 0L);
        if (old > Long.MAX_VALUE - principal || !AccountManager.moveFunds(playerId, CentralBank.UUID, principal)) {
            if (bond.principalByPlayer().isEmpty()) BONDS.remove(bond.id());
            return Result.fail("资金不足或国债持仓溢出");
        }
        if (!bond.addPrincipal(playerId, principal)) {
            AccountManager.moveFunds(CentralBank.UUID, playerId, principal);
            if (bond.principalByPlayer().isEmpty()) BONDS.remove(bond.id());
            return Result.fail("国债持仓结算失败");
        }
        // 审计 quantity 统一为 0：资金划转无物品数量概念（见 TransactionRecord 文档）
        AccountManager.addTransactionRecord(new TransactionRecord(playerId, CentralBank.UUID, principal,
                TransactionType.TREASURY_BOND_SUBSCRIBE, playerId, "国债/" + termDays + "日", 0));
        EconomySavedData.markDirty();
        return Result.ok(bond.id(), "国债认购成功");
    }

    /**
     * 提前赎回：仅返还本金（扣除手续费），已计利息全额罚没，恒无利可图。
     */
    public static Result earlyRedeem(UUID playerId, UUID bondId, long currentDay) {
        if (playerId == null || bondId == null || currentDay < 0) return Result.fail("国债赎回参数无效");
        TreasuryBond bond = BONDS.get(bondId);
        if (bond == null || bond.status() != TreasuryBondStatus.ACTIVE) return Result.fail("国债不存在或已到期");
        long principal = bond.removePrincipal(playerId);
        // removePrincipal 仅返回 -1（无持仓）或 >0（持仓本金恒 >0），==0 不可达
        if (principal <= 0) return Result.fail("未持有该国债");
        long fee = Math.min(Math.max(0L, FinanceConfig.treasuryBondEarlyFee()), principal);
        long payout = principal - fee;
        if (payout > 0 && !AccountManager.canDeposit(playerId, payout)) {
            bond.putPrincipalDirect(playerId, principal);
            return Result.fail("资金账户无法入账");
        }
        // P1-7：payout==0（手续费吃掉全部本金）时跳过转账直接视为成功，手续费留存央行
        if (payout > 0 && !AccountManager.moveFunds(CentralBank.UUID, playerId, payout)) {
            bond.putPrincipalDirect(playerId, principal);
            return Result.fail("国债赎回结算失败");
        }
        if (bond.principalByPlayer().isEmpty()) BONDS.remove(bond.id());
        AccountManager.addTransactionRecord(new TransactionRecord(CentralBank.UUID, playerId, payout,
                TransactionType.TREASURY_BOND_EARLY_REDEEM, playerId, "国债提前赎回/" + bond.termDays() + "日", 0));
        EconomySavedData.markDirty();
        return Result.ok(bondId, fee > 0 ? "国债提前赎回完成，已扣除手续费" : "国债提前赎回完成，仅返还本金");
    }

    public static void processDay(long day) {
        if (day < 0) return;
        // 拷贝后遍历：settle 成功后会从 BONDS 移除已兑付债券
        for (TreasuryBond bond : new ArrayList<>(BONDS.values())) {
            if (bond.status() == TreasuryBondStatus.ACTIVE && day >= bond.maturityDay()) settle(bond, day);
        }
    }

    private static boolean settle(TreasuryBond bond, long day) {
        Map<UUID, Long> payouts = new LinkedHashMap<>();
        BigInteger total = BigInteger.ZERO;
        BigInteger denominator = BigInteger.valueOf((long) FinanceConfig.annualMcDays() * 10_000L);
        int skipped = 0;
        for (Map.Entry<UUID, Long> holding : bond.principalByPlayer().entrySet()) {
            BigInteger principal = BigInteger.valueOf(holding.getValue());
            BigInteger interest = principal.multiply(BigInteger.valueOf(bond.annualRateBasisPoints()))
                    .multiply(BigInteger.valueOf(bond.termDays())).divide(denominator);
            BigInteger due = principal.add(interest);
            long amount = due.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 ? -1 : due.longValue();
            if (amount <= 0 || !AccountManager.canDeposit(holding.getKey(), amount)) {
                // 单个持有人暂时无法兑付：跳过，份额留待次日重试，不拖死整只债券
                skipped++;
                LOGGER.warn("国债 {} 持有人 {} 本期跳过兑付（份额留待次日）", bond.id(), holding.getKey());
                continue;
            }
            payouts.put(holding.getKey(), amount); total = total.add(due);
        }
        if (payouts.isEmpty()) return false; // 全部跳过：保持 ACTIVE，次日重试
        if (total.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) return false;
        long due = total.longValue(), reserve = AccountManager.getBalance(CentralBank.UUID);
        long issuance = Math.max(0, due - reserve);
        if (issuance > 0 && !AccountManager.deposit(CentralBank.UUID, issuance)) return false;
        List<Map.Entry<UUID, Long>> paid = new ArrayList<>();
        for (Map.Entry<UUID, Long> payout : payouts.entrySet()) {
            if (!AccountManager.moveFunds(CentralBank.UUID, payout.getKey(), payout.getValue())) {
                boolean rollbackOk = true;
                for (Map.Entry<UUID, Long> rollback : paid) {
                    if (!AccountManager.moveFunds(rollback.getKey(), CentralBank.UUID, rollback.getValue())) {
                        LOGGER.error("国债兑付回滚失败：债券 {} 持有人 {} 已收款 {} 无法追回，暂停自动重试",
                                bond.id(), rollback.getKey(), rollback.getValue());
                        rollbackOk = false;
                    }
                }
                if (issuance > 0 && !AccountManager.withdraw(CentralBank.UUID, issuance)) {
                    LOGGER.error("国债兑付回滚失败：债券 {} 缺口投放 {} 无法收回，暂停自动重试", bond.id(), issuance);
                    rollbackOk = false;
                }
                if (!rollbackOk) {
                    // 回滚再失败：债券已部分兑付，标记异常并暂停自动重试，防次日重复全额兑付
                    bond.setStatus(TreasuryBondStatus.SETTLEMENT_FAILED);
                    AccountManager.addTransactionRecord(new TransactionRecord(CentralBank.UUID, CentralBank.UUID, 0,
                            TransactionType.TREASURY_BOND_MATURITY, CentralBank.UUID,
                            "国债兑付异常/暂停自动重试:" + bond.id(), 0));
                    EconomySavedData.markDirty();
                }
                return false;
            }
            paid.add(payout);
        }
        if (issuance > 0) {
            cumulativeIssuance = MathUtil.saturatedAddNonNegative(cumulativeIssuance, issuance);
            lastIssuance = issuance; lastIssuanceDay = day;
            AccountManager.addTransactionRecord(new TransactionRecord(CentralBank.UUID, CentralBank.UUID, issuance,
                    TransactionType.CENTRAL_BANK_MONETARY_ISSUE, CentralBank.UUID, "国债兑付缺口投放", 0));
        }
        for (Map.Entry<UUID, Long> payout : payouts.entrySet()) {
            AccountManager.addTransactionRecord(new TransactionRecord(CentralBank.UUID, payout.getKey(), payout.getValue(),
                    TransactionType.TREASURY_BOND_MATURITY, payout.getKey(), "国债到期/" + bond.termDays() + "日", 0));
            bond.removePrincipal(payout.getKey());
        }
        if (bond.principalByPlayer().isEmpty()) {
            // P1-8：兑付完成且持仓清空后从注册表移除，MATURED 不再永久驻留（审计已在 TransactionRecord 保留）
            bond.setStatus(TreasuryBondStatus.MATURED);
            BONDS.remove(bond.id());
        } else {
            LOGGER.warn("国债 {} 本期部分兑付：{} 人已兑付，{} 人份额留待次日", bond.id(), payouts.size(), skipped);
        }
        EconomySavedData.markDirty(); return true;
    }

    /** 玩家在 ACTIVE 债券中的本金合计（用于个人上限）。 */
    public static long activePrincipalByPlayer(UUID playerId) {
        if (playerId == null) return 0;
        long sum = 0;
        for (TreasuryBond bond : BONDS.values()) {
            if (bond.status() != TreasuryBondStatus.ACTIVE) continue;
            sum = MathUtil.saturatedAddNonNegative(sum, bond.principalByPlayer().getOrDefault(playerId, 0L));
        }
        return sum;
    }

    /** 某期限档 ACTIVE 债券的发行余额合计（用于分档上限）。 */
    public static long outstandingByTerm(int termDays) {
        long sum = 0;
        for (TreasuryBond bond : BONDS.values()) {
            if (bond.status() != TreasuryBondStatus.ACTIVE || bond.termDays() != termDays) continue;
            for (long p : bond.principalByPlayer().values()) sum = MathUtil.saturatedAddNonNegative(sum, p);
        }
        return sum;
    }

    public static long expectedMaturityValue(TreasuryBond bond, UUID player) {
        if (bond == null || player == null) return 0;
        long principal = bond.principalByPlayer().getOrDefault(player, 0L);
        BigInteger interest = BigInteger.valueOf(principal).multiply(BigInteger.valueOf(bond.annualRateBasisPoints()))
                .multiply(BigInteger.valueOf(bond.termDays()))
                .divide(BigInteger.valueOf((long) FinanceConfig.annualMcDays() * 10_000L));
        return interest.add(BigInteger.valueOf(principal)).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }

    public static Map<UUID, TreasuryBond> bonds() { return Collections.unmodifiableMap(BONDS); }
    public static long cumulativeIssuance() { return cumulativeIssuance; }
    public static long lastIssuance() { return lastIssuance; }
    public static long lastIssuanceDay() { return lastIssuanceDay; }
    public static void restoreIssuance(long cumulative, long last, long day) {
        cumulativeIssuance = Math.max(0, cumulative);
        lastIssuance = Math.min(cumulativeIssuance, Math.max(0, last));
        lastIssuanceDay = lastIssuance > 0 ? Math.max(0, day) : -1;
    }
    public static void putDirect(TreasuryBond bond) {
        if (bond == null) return;
        if (BONDS.size() >= MAX_BONDS) {
            // P1-8：超 cap 丢弃必须留痕（该债券的认购审计见 TransactionRecord）
            LOGGER.warn("国债注册表已满（{}），数据恢复时丢弃债券 {}", MAX_BONDS, bond.id());
            return;
        }
        BONDS.put(bond.id(), bond);
    }
    public static void clearDirect() { BONDS.clear(); cumulativeIssuance = 0; lastIssuance = 0; lastIssuanceDay = -1; }
    public record Result(boolean success, UUID bondId, String message) {
        static Result ok(UUID id, String message) { return new Result(true, id, message); }
        static Result fail(String message) { return new Result(false, null, message); }
    }
}
