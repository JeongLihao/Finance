package finance.macro;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.market.NpcMarketMaker;
import finance.metrics.EconomyMetricsService;
import finance.village.VillageManager;
import finance.village.VillageProfession;
import finance.village.VillageRecord;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * 宏观经济第三步：利益集团与法律。
 *
 * <p>每天日结：发福利 → 计算各集团影响力与满意度 → 更新罢工/骚乱 → 游说加成衰减 → 到期提案表决。
 * 表决是确定性的：支持度 = Σ 影响力 × (新级别立场 − 现行级别立场) × 满意度系数，大于 0 即通过。</p>
 */
public final class PoliticsService {
    public static final int MIN_APPROVAL = -10, MAX_APPROVAL = 10;
    public static final int MAX_LOBBY = 5;
    public static final int UNREST_THRESHOLD = -5;

    private PoliticsService() { }

    public static boolean active() {
        return MacroEconomyService.active() && FinanceConfig.macroPoliticsEnabled();
    }

    public record Result(boolean success, String message) {
        static Result ok(String m) { return new Result(true, m); }
        static Result fail(String m) { return new Result(false, m); }
    }

    // ================================================================ 福利

    /** 按福利法律从 NPC 资金池给每个聚落预算账户拨款，计入聚落收入。 */
    static void payWelfare(long day) {
        if (!active()) return;
        LawLevel welfare = MacroEconomyManager.law(LawGroup.WELFARE);
        long perCapita = switch (welfare) {
            case POOR_RELIEF -> FinanceConfig.macroWelfareReliefPerCapita();
            case PUBLIC_HEALTH -> FinanceConfig.macroWelfareHealthPerCapita();
            default -> 0L;
        };
        if (perCapita <= 0) return;
        long floor = FinanceConfig.macroPoolReserveFloor();
        for (SettlementEconomy econ : MacroEconomyManager.settlements()) {
            VillageRecord village = VillageManager.get(econ.settlementId);
            if (village == null || village.budgetAccountId() == null) continue;
            long amount = finance.util.MathUtil.multiplyExactOrNegative1(perCapita, village.population());
            if (amount <= 0) continue;
            long pool = AccountManager.getBalance(NpcMarketMaker.NPC_UUID);
            if (pool - amount < floor) return;
            if (!AccountManager.moveFunds(NpcMarketMaker.NPC_UUID, village.budgetAccountId(), amount)) continue;
            AccountManager.addTransactionRecord(new TransactionRecord(NpcMarketMaker.NPC_UUID, village.budgetAccountId(),
                    amount, TransactionType.MACRO_WELFARE, village.budgetAccountId(), welfare.name(), 0));
            econ.addIncome(amount);
        }
    }

    // ================================================================ 日结

    static void processDay(long day) {
        if (!active()) {
            MacroEconomyManager.setUnrestFlags(false, false);
            return;
        }
        refreshPrivateCash();
        recompute();
        int unions = MacroEconomyManager.approval(InterestGroup.TRADE_UNIONS);
        boolean strike = unions <= UNREST_THRESHOLD && MacroEconomyManager.cloutBps(InterestGroup.TRADE_UNIONS) > 0;
        boolean unrest = MacroEconomyManager.approval(InterestGroup.RURAL_FOLK) <= UNREST_THRESHOLD
                || MacroEconomyManager.approval(InterestGroup.PETITE_BOURGEOISIE) <= UNREST_THRESHOLD;
        MacroEconomyManager.setUnrestFlags(strike, unrest);
        for (InterestGroup ig : InterestGroup.values()) {
            int lobby = MacroEconomyManager.lobby(ig);
            if (lobby != 0) MacroEconomyManager.setLobby(ig, lobby > 0 ? lobby - 1 : lobby + 1);
        }
        MacroEconomyManager.Proposal p = MacroEconomyManager.proposal();
        if (p != null && day >= p.endDay()) resolve(p, day);
    }

    /** 重新计算影响力与满意度（命令查询时也会调用，保证看到的是最新值）。 */
    public static void recompute() {
        Map<InterestGroup, Double> weight = cloutWeights();
        double total = weight.values().stream().mapToDouble(Double::doubleValue).sum();
        for (InterestGroup ig : InterestGroup.values()) {
            int bps = total <= 0 ? 0 : (int) Math.round(weight.getOrDefault(ig, 0.0) / total * 10_000);
            MacroEconomyManager.setCloutBps(ig, bps);
            MacroEconomyManager.setApproval(ig, approvalOf(ig));
        }
    }

    static Map<InterestGroup, Double> cloutWeights() {
        EnumMap<InterestGroup, Double> w = new EnumMap<>(InterestGroup.class);
        for (InterestGroup ig : InterestGroup.values()) w.put(ig, 0.0);
        for (SettlementEconomy econ : MacroEconomyManager.settlements()) {
            VillageRecord village = VillageManager.get(econ.settlementId);
            if (village == null) continue;
            double factor = 0.5 + econ.solBps / 10_000.0;
            Map<VillageProfession, Integer> mix = village.professionMix();
            w.merge(InterestGroup.RURAL_FOLK, mix.getOrDefault(VillageProfession.FARMER, 0) * factor, Double::sum);
            w.merge(InterestGroup.PETITE_BOURGEOISIE, mix.getOrDefault(VillageProfession.ARTISAN, 0) * factor, Double::sum);
            w.merge(InterestGroup.ARMED_FORCES, mix.getOrDefault(VillageProfession.GUARD, 0) * factor, Double::sum);
            w.merge(InterestGroup.TRADE_UNIONS, econ.employed * factor, Double::sum);
        }
        double scale = Math.max(1, FinanceConfig.macroCloutValueScale());
        long companyCash = 0;
        for (Company c : CompanyManager.getCompanies()) {
            if (c.isPlayerOwned()) companyCash = GoodBook.sat(companyCash, c.getCash());
        }
        // 资本按平方根折算影响力：钱越多影响力越大，但不会让一个富玩家压过全部人口
        w.put(InterestGroup.INDUSTRIALISTS, Math.sqrt(companyCash / scale));
        if (cachedPrivateCash < 0) refreshPrivateCash();
        w.put(InterestGroup.FINANCIERS, Math.sqrt(cachedPrivateCash / scale));
        return w;
    }

    /** 私人现金（金融家影响力的来源）：日结时刷新一次，命令和界面查询直接用缓存，避免每次扫全部账户。 */
    private static long cachedPrivateCash = -1;

    static void refreshPrivateCash() {
        // 玩家现金口径包含聚落预算这类系统账户，扣掉它们只留真正的私人资本
        long villageBudgets = 0;
        for (VillageRecord v : VillageManager.villages()) {
            if (v.budgetAccountId() != null && AccountManager.getAccounts().containsKey(v.budgetAccountId())) {
                villageBudgets = GoodBook.sat(villageBudgets, AccountManager.getBalance(v.budgetAccountId()));
            }
        }
        cachedPrivateCash = Math.max(0, EconomyMetricsService.getCurrentMetrics().playerCash() - villageBudgets);
    }

    static void clearCache() { cachedPrivateCash = -1; }

    /** 满意度 = 处境得分 + 现行法律立场 + 游说加成，钳制到 −10..+10。 */
    static int approvalOf(InterestGroup ig) {
        int score = situation(ig);
        for (LawLevel law : MacroEconomyManager.laws().values()) score += ig.stance(law);
        score += MacroEconomyManager.lobby(ig);
        // 紧缩：福利停发，乡村民众和工会不满
        if (Treasury.austerity() && (ig == InterestGroup.RURAL_FOLK || ig == InterestGroup.TRADE_UNIONS)) score -= 2;
        return Math.max(MIN_APPROVAL, Math.min(MAX_APPROVAL, score));
    }

    static int situation(InterestGroup ig) {
        return switch (ig) {
            case RURAL_FOLK -> solScore(VillageProfession.FARMER, 1_000);
            case PETITE_BOURGEOISIE -> solScore(VillageProfession.ARTISAN, 1_250);
            case ARMED_FORCES -> clamp((int) Math.round((avgFulfilment(NeedCategory.TOOLS) - 0.5) * 10), -5, 5);
            case TRADE_UNIONS -> {
                double gap = wageGap();
                yield Double.isNaN(gap) ? 0 : clamp((int) Math.round(gap * 3), -5, 5);
            }
            case INDUSTRIALISTS -> {
                double gap = wageGap();
                yield Double.isNaN(gap) ? 0 : clamp((int) Math.round(-gap * 2), -4, 4);
            }
            case FINANCIERS -> {
                int debt = MacroFiscalService.debtRatioBps();
                int debtPenalty = debt > 9_000 ? 2 : debt > 5_000 ? 1 : 0;
                yield clamp((int) Math.round(2 - Math.abs(MacroIndicators.annualizedInflationPct() - 2) / 2) - debtPenalty, -5, 2);
            }
        };
    }

    /** 某职业所在聚落的人口加权平均生活水平相对 5000 的得分。 */
    private static int solScore(VillageProfession profession, int perPoint) {
        double sum = 0, weight = 0;
        for (SettlementEconomy econ : MacroEconomyManager.settlements()) {
            VillageRecord village = VillageManager.get(econ.settlementId);
            if (village == null) continue;
            int n = village.professionMix().getOrDefault(profession, 0);
            sum += (double) econ.solBps * n;
            weight += n;
        }
        if (weight <= 0) return 0;
        return clamp((int) Math.round((sum / weight - 5_000) / perPoint), -5, 5);
    }

    private static double avgFulfilment(NeedCategory c) {
        double sum = 0;
        int n = 0;
        for (SettlementEconomy econ : MacroEconomyManager.settlements()) {
            Double f = econ.lastFulfilment.get(c);
            if (f != null) { sum += f; n++; }
        }
        return n == 0 ? 0.5 : sum / n;
    }

    /** 平均工资相对保留工资的差距比例；没有就业时返回 NaN。 */
    private static double wageGap() {
        long employed = 0;
        double wageSum = 0;
        for (Map.Entry<UUID, Long> e : MacroEconomyManager.employmentDirect().entrySet()) {
            var facility = finance.gameplay.company.CompanyFacilityManager.get(e.getKey());
            if (facility == null) continue;
            employed += e.getValue();
            wageSum += (double) e.getValue() * MacroLaborService.wageOf(facility.companyId());
        }
        if (employed <= 0) return Double.NaN;
        double reservation = Math.max(1, MacroLaborService.reservationWage());
        return (wageSum / employed - reservation) / reservation;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    // ================================================================ 提案与游说

    /** 支持度与反对度（影响力基点 × 立场差 × 满意度系数 ×100，取整）。 */
    public static long[] tally(LawLevel proposed) {
        LawLevel current = MacroEconomyManager.law(proposed.group());
        long support = 0, opposition = 0;
        for (InterestGroup ig : InterestGroup.values()) {
            int delta = ig.stance(proposed) - ig.stance(current);
            if (delta == 0) continue;
            double factor = 1.0 + MacroEconomyManager.approval(ig) / 20.0;
            long v = Math.round(MacroEconomyManager.cloutBps(ig) * Math.abs(delta) * factor);
            if (delta > 0) support += v; else opposition += v;
        }
        return new long[]{support, opposition};
    }

    public static Result propose(UUID player, LawLevel level, long day) {
        if (!active()) return Result.fail("政治系统未启用。");
        if (player == null || level == null) return Result.fail("参数错误。");
        if (MacroEconomyManager.proposal() != null) return Result.fail("已有进行中的提案，请等它表决后再提。");
        if (MacroEconomyManager.law(level.group()) == level) return Result.fail("该法律已经生效。");
        long fee = Math.max(0, FinanceConfig.macroLawProposalFee());
        if (fee > 0 && MacroFiscalService.active()) {
            if (!MacroFiscalService.collectFee(player, fee, "propose:" + level.name())) return Result.fail("余额不足，提案费 " + fee + "。");
        } else if (fee > 0) {
            if (!AccountManager.moveFunds(player, NpcMarketMaker.NPC_UUID, fee)) return Result.fail("余额不足，提案费 " + fee + "。");
            AccountManager.addTransactionRecord(new TransactionRecord(player, NpcMarketMaker.NPC_UUID, fee,
                    TransactionType.MACRO_POLITICS, player, "propose:" + level.name(), 0));
        }
        int days = Math.max(1, FinanceConfig.macroLawDebateDays());
        MacroEconomyManager.setProposal(new MacroEconomyManager.Proposal(level, player, day, day + days));
        EconomySavedData.markDirty();
        return Result.ok("已提交提案：" + level.group().displayName() + " → " + level.displayName() + "，" + days + " 天后表决。");
    }

    public static Result lobby(UUID player, InterestGroup ig, long amount) {
        if (!active()) return Result.fail("政治系统未启用。");
        if (player == null || ig == null || amount <= 0) return Result.fail("参数错误。");
        long perPoint = Math.max(1, FinanceConfig.macroLobbyPerPoint());
        int current = MacroEconomyManager.lobby(ig);
        int room = MAX_LOBBY - current;
        if (room <= 0) return Result.fail(ig.displayName() + " 的游说加成已达上限。");
        long points = Math.min(room, amount / perPoint);
        if (points <= 0) return Result.fail("每 +1 满意度需要 " + perPoint + "。");
        long cost = points * perPoint;
        if (!AccountManager.moveFunds(player, NpcMarketMaker.NPC_UUID, cost)) return Result.fail("余额不足。");
        AccountManager.addTransactionRecord(new TransactionRecord(player, NpcMarketMaker.NPC_UUID, cost,
                TransactionType.MACRO_POLITICS, player, "lobby:" + ig.name(), points));
        MacroEconomyManager.setLobby(ig, current + (int) points);
        EconomyAchievements.onLobbied(player, current + (int) points);
        MacroEconomyManager.setApproval(ig, approvalOf(ig));
        EconomySavedData.markDirty();
        return Result.ok("向 " + ig.displayName() + " 捐款 " + cost + "，满意度 +" + points + "（每天衰减 1）。");
    }

    static void resolve(MacroEconomyManager.Proposal p, long day) {
        recompute();
        long[] t = tally(p.level());
        boolean passed = t[0] > t[1];
        if (passed) MacroEconomyManager.setLaw(p.level());
        MacroEconomyManager.addLawResult(new MacroEconomyManager.LawResult(p.level(), passed, t[0], t[1], day));
        MacroEconomyManager.setProposal(null);
        EconomyAchievements.onProposalResolved(p.proposer(), passed);
        MacroWorldFeedback.onLawResolved(p.level(), passed, t[0], t[1]);
        if (passed) recompute();
        EconomySavedData.markDirty();
    }
}
