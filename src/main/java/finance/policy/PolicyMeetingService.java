package finance.policy;

import finance.company.CompanyManager;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.feedback.FeedbackAudience;
import finance.feedback.FeedbackSeverity;
import finance.feedback.WorldEconomyEvent;
import finance.feedback.WorldEconomyFeedbackService;
import finance.feedback.WorldFeedbackType;
import finance.metrics.EconomyMetricsService;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 议息日历与政策沟通 —— 议息日前瞻指引、议息日执行、GLOBAL 公告。
 *
 * <p>三种模式（{@code policyRateMode}）：AUTO 议息日按规则自动调息；
 * ADVISORY 规则只给建议，管理员在议息日前手动调息即视为干预、会议尊重人工设定，
 * 无人干预则按建议执行；MANUAL 纯手动，日历只推进不打扰。</p>
 *
 * <p>干预检测不只依赖前瞻快照：发过前瞻则比对前瞻发出时利率，
 * 未发前瞻（leadDays=0 / 中途切换模式）则退化为比对本轮议息周期起点利率，
 * 两种口径任一发现利率被人工改过即视为干预，会议维持现状并如实公告。</p>
 */
public final class PolicyMeetingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyMeetingService.class);

    private static long lastMeetingDay = -1;
    private static long nextMeetingDay = -1;
    /** 已为哪一次议息日发送过前瞻（-1 = 无）。 */
    private static long guidanceSentForMeeting = -1;
    /** 前瞻发出时的基准利率：议息日若已不同，说明管理员人工干预过。 */
    private static int guidanceBaselineBps = -1;
    /**
     * 本轮议息周期起点利率：无前瞻快照时的干预检测退化口径。
     * 在首次排期与每次议息结束后刷新为决议后利率（会议自己的调整不算干预）。
     */
    private static int cycleBaselineBps = -1;

    private PolicyMeetingService() {}

    /** 测试用模式覆盖（null = 读配置）。 */
    private static String modeOverrideForTest = null;

    static void setModeOverrideForTest(String mode) {
        modeOverrideForTest = mode;
    }

    private static String mode() {
        if (modeOverrideForTest != null) return modeOverrideForTest;
        return FinanceConfig.policyRateMode();
    }

    public record PolicyOutlook(
            long nextMeetingDay, String mode, String guidanceDirection, int currentBps, int recommendedBps) {}

    public static long lastMeetingDay() {
        return lastMeetingDay;
    }

    public static long nextMeetingDay() {
        return nextMeetingDay;
    }

    public static long guidanceSentForMeeting() {
        return guidanceSentForMeeting;
    }

    public static int guidanceBaselineBps() {
        return guidanceBaselineBps;
    }

    public static int cycleBaselineBps() {
        return cycleBaselineBps;
    }

    public static PolicyOutlook outlook() {
        int current = MonetaryPolicyService.benchmarkRateBasisPoints();
        PolicyRuleService.Evaluation eval = evaluateNow(current);
        return new PolicyOutlook(
                nextMeetingDay,
                mode(),
                eval == null ? "—" : PolicyRuleService.directionLabel(eval.direction()),
                current,
                eval == null ? current : eval.recommendedBps());
    }

    /** 每个 MC 天调用一次（由 EconomyCycleService.tickDay 驱动）。 */
    public static void onDayTick(MinecraftServer server, long mcDay) {
        if (mcDay < 0) return;
        int interval = Math.max(1, FinanceConfig.policyMeetingIntervalDays());
        if (nextMeetingDay < 0) {
            // 首次：把第一次议息安排在未来，不在读档当天立刻开火。
            nextMeetingDay = mcDay + interval;
            cycleBaselineBps = MonetaryPolicyService.benchmarkRateBasisPoints();
            EconomySavedData.markDirty();
            return;
        }
        if (mcDay >= nextMeetingDay) {
            holdMeeting(server, mcDay);
            return;
        }
        int lead = Math.max(0, FinanceConfig.policyGuidanceLeadDays());
        if (lead > 0 && mcDay >= nextMeetingDay - lead && guidanceSentForMeeting != nextMeetingDay) {
            sendGuidance(server, mcDay);
        }
    }

    /** 管理员手动确认 ADVISORY 建议（命令/后台调用）：把当前建议直接设为干预值。 */
    public static boolean confirmAdvisory(long mcDay) {
        PolicyRuleService.Evaluation eval = evaluateNow(MonetaryPolicyService.benchmarkRateBasisPoints());
        if (eval == null) return false;
        guidanceBaselineBps = MonetaryPolicyService.benchmarkRateBasisPoints();
        return MonetaryPolicyService.setBenchmarkRate(mcDay, eval.recommendedBps(), "议息确认：" + eval.explanation());
    }

    private static void holdMeeting(MinecraftServer server, long mcDay) {
        int interval = Math.max(1, FinanceConfig.policyMeetingIntervalDays());
        String mode = mode();
        int current = MonetaryPolicyService.benchmarkRateBasisPoints();
        PolicyRuleService.Evaluation eval = evaluateNow(current);
        if (eval == null) {
            // 评估异常（已在 evaluateNow 打 warn）：不推进日历，次日 tick 重试，
            // 避免议息被静默跳过。
            LOGGER.warn("[议息] 第 {} 天议息评估失败，日历未推进，将于次日重试", mcDay);
            return;
        }
        long overdue = mcDay - nextMeetingDay;
        if (overdue > 0) {
            // 停服跳天：只补执行最近一次议息，但必须让服主知道错过了几次。
            long missed = overdue / interval;
            LOGGER.warn("[议息] 错过 {} 次议息日（原定第 {} 天，当前第 {} 天），仅执行本次", missed, nextMeetingDay, mcDay);
        }

        if (!"MANUAL".equals(mode)) {
            // P1-20：AUTO 与 ADVISORY 统一做干预检测。被手动干预则走人工保留分支，
            // 广播真实结果，杜绝"公告声称 X%→Y% 而实际未变"的虚假公告。
            boolean intervened = detectIntervention(current);
            if (intervened) {
                broadcast(
                        server,
                        mcDay,
                        "policy-decision-keep",
                        "finance.feedback.policy_decision_manual",
                        List.of(formatBps(current), eval.explanation()),
                        FeedbackSeverity.NOTICE);
            } else {
                String reason = "议息决议（" + mode + "）：" + eval.explanation();
                boolean changed = MonetaryPolicyService.setBenchmarkRate(mcDay, eval.recommendedBps(), reason);
                if (!changed && eval.direction() != PolicyRuleService.Direction.HOLD) {
                    // 极端残留：人工改后又改回原值，干预检测看不出，
                    // 但 setBenchmarkRate 因"同日唯一"拒绝——如实广播维持现状，不谎称已调息。
                    broadcast(
                            server,
                            mcDay,
                            "policy-decision-keep",
                            "finance.feedback.policy_decision_manual",
                            List.of(formatBps(current), "议息日已有人工调息，决议未执行：" + eval.explanation()),
                            FeedbackSeverity.NOTICE);
                } else {
                    broadcast(
                            server,
                            mcDay,
                            "policy-decision",
                            "finance.feedback.policy_decision",
                            List.of(
                                    formatBps(current), formatBps(eval.recommendedBps()),
                                    PolicyRuleService.directionLabel(eval.direction()), eval.explanation()),
                            eval.direction() == PolicyRuleService.Direction.HOLD
                                    ? FeedbackSeverity.NOTICE
                                    : FeedbackSeverity.WARNING);
                }
            }
        }
        finance.centralbank.CentralBankService.onMeeting(
                mcDay, eval, current, MonetaryPolicyService.benchmarkRateBasisPoints(), "MANUAL".equals(mode));
        lastMeetingDay = mcDay;
        nextMeetingDay = mcDay + interval;
        guidanceSentForMeeting = -1;
        guidanceBaselineBps = -1;
        // 下轮周期起点 = 本次决议后利率：会议自己的调整不算"干预"。
        cycleBaselineBps = MonetaryPolicyService.benchmarkRateBasisPoints();
        EconomySavedData.markDirty();
    }

    /**
     * 干预检测：不只依赖前瞻快照。
     * <ul>
     *   <li>发过前瞻：比对"前瞻发出时利率"与当前；</li>
     *   <li>未发前瞻（leadDays=0 / 中途切换模式导致快照缺失）：退化为比对本轮议息周期起点利率。</li>
     * </ul>
     */
    private static boolean detectIntervention(int current) {
        if (guidanceBaselineBps >= 0) return current != guidanceBaselineBps;
        return cycleBaselineBps >= 0 && current != cycleBaselineBps;
    }

    private static void sendGuidance(MinecraftServer server, long mcDay) {
        int current = MonetaryPolicyService.benchmarkRateBasisPoints();
        PolicyRuleService.Evaluation eval = evaluateNow(current);
        if (eval == null) return;
        guidanceSentForMeeting = nextMeetingDay;
        guidanceBaselineBps = current;
        broadcast(
                server,
                mcDay,
                "policy-guidance",
                "finance.feedback.policy_guidance",
                List.of(
                        String.valueOf(nextMeetingDay - mcDay), String.valueOf(nextMeetingDay),
                        PolicyRuleService.directionLabel(eval.direction()), formatBps(current)),
                FeedbackSeverity.NOTICE);
        EconomySavedData.markDirty();
    }

    /** 按当前利率和实时经济度量做一次评估（央行面板、准备金率和公开市场操作共用）；异常时返回 null。 */
    public static PolicyRuleService.Evaluation currentEvaluation() {
        return evaluateNow(MonetaryPolicyService.benchmarkRateBasisPoints());
    }

    /** 用实时经济度量评估；无可用数据时返回 null（保持静默）。 */
    static PolicyRuleService.Evaluation evaluateNow(int currentBps) {
        try {
            List<EconomyMetricsService.DailySnapshot> snapshots = EconomyMetricsService.getDailySnapshots();
            double now = 100.0;
            double base = -1.0;
            int riskCompanies = 0;
            if (!snapshots.isEmpty()) {
                EconomyMetricsService.DailySnapshot latest = snapshots.get(snapshots.size() - 1);
                now = latest.priceIndex();
                riskCompanies = latest.bankruptcyRiskCompanies();
                long cutoff = latest.mcDay() - 30;
                for (EconomyMetricsService.DailySnapshot s : snapshots) {
                    if (s.mcDay() <= cutoff) base = s.priceIndex();
                    else break;
                }
                if (base < 0) base = snapshots.get(0).priceIndex();
            }
            int companies = Math.max(1, CompanyManager.getCompanies().size());
            // 衰退指标口径说明：分母 = 全部注册公司（玩家公司＋系统 NPC 公司）。
            // 系统 NPC 公司（铁锭原料/石材工坊/麦田农庄）每日执行 produce/autoTrade，
            // 有实际经营，并非纯壳，计入分母合理；分子 riskCompanies 同样按全部公司
            // 口径统计（见 EconomyMetricsService），分子分母一致，稀释效应有限。
            // 若未来出现大量"注册后零经营"的纯壳公司，可将分母收窄为 isPlayerOwned()。
            double slack = Math.min(1.0, (double) Math.max(0, riskCompanies) / companies);
            return PolicyRuleService.evaluate(currentBps, now, base, slack);
        } catch (Exception e) {
            LOGGER.warn("[议息] 实时经济度量评估异常，本次议息跳过（日历不推进，次日重试）", e);
            return null;
        }
    }

    private static void broadcast(
            MinecraftServer server,
            long mcDay,
            String eventKey,
            String translationKey,
            List<String> args,
            FeedbackSeverity severity) {
        if (server == null) return;
        WorldEconomyEvent event = new WorldEconomyEvent(
                eventKey,
                WorldFeedbackType.POLICY_RATE,
                severity,
                "central-bank",
                mcDay,
                "",
                null,
                translationKey,
                args,
                FeedbackAudience.GLOBAL,
                Set.of());
        WorldEconomyFeedbackService.publish(server, event);
    }

    /** 基点转百分比文案（命令回显复用，公开）。 */
    public static String formatBps(int bps) {
        return String.format(Locale.ROOT, "%.2f%%", bps / 100.0);
    }

    // ---------- 持久化 / 测试 ----------

    public static void restore(
            long lastMeeting, long nextMeeting, long guidanceFor, int baselineBps, int cycleBaseline) {
        lastMeetingDay = lastMeeting;
        nextMeetingDay = nextMeeting;
        guidanceSentForMeeting = guidanceFor;
        guidanceBaselineBps = baselineBps;
        cycleBaselineBps = cycleBaseline;
    }

    public static void clearDirect() {
        modeOverrideForTest = null;
        lastMeetingDay = -1;
        nextMeetingDay = -1;
        guidanceSentForMeeting = -1;
        guidanceBaselineBps = -1;
        cycleBaselineBps = -1;
    }
}
