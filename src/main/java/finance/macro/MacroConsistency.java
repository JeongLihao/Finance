package finance.macro;

import finance.diagnostic.DiagnosticIssue;
import finance.diagnostic.DiagnosticSeverity;
import finance.gameplay.company.CompanyFacilityManager;
import finance.gameplay.company.CompanyFacilityRecord;
import finance.settlement.SettlementManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 宏观经济模块的一致性检查（{@code /finance resume macro} 前的诊断）。 */
public final class MacroConsistency {
    private static final String MODULE = "MACRO_ECONOMY";

    private MacroConsistency() {}

    public static List<DiagnosticIssue> check() {
        List<DiagnosticIssue> out = new ArrayList<>();
        for (SettlementEconomy econ : MacroEconomyManager.settlements()) {
            if (SettlementManager.get(econ.settlementId) == null) {
                out.add(new DiagnosticIssue(
                        DiagnosticSeverity.WARN,
                        MODULE,
                        "ORPHAN_SETTLEMENT",
                        econ.settlementId.toString(),
                        "宏观经济状态指向不存在的聚落"));
            }
            if (econ.solBps < 0 || econ.solBps > MacroFormulas.MAX_SOL || econ.employed < 0) {
                out.add(new DiagnosticIssue(
                        DiagnosticSeverity.ERROR, MODULE, "STATE_RANGE", econ.settlementId.toString(), "生活水平或就业率超出范围"));
            }
            for (Map.Entry<String, GoodBook> e : econ.goods.entrySet()) {
                int f = e.getValue().factorBps;
                if (f < MacroFormulas.MIN_FACTOR_BPS || f > MacroFormulas.MAX_FACTOR_BPS) {
                    out.add(new DiagnosticIssue(
                            DiagnosticSeverity.ERROR,
                            MODULE,
                            "PRICE_FACTOR",
                            econ.settlementId + "/" + e.getKey(),
                            "本地物价系数超出范围"));
                }
            }
        }
        for (Map.Entry<UUID, Long> e : MacroEconomyManager.employmentDirect().entrySet()) {
            CompanyFacilityRecord facility = CompanyFacilityManager.get(e.getKey());
            if (facility == null) {
                out.add(new DiagnosticIssue(
                        DiagnosticSeverity.WARN,
                        MODULE,
                        "ORPHAN_EMPLOYMENT",
                        e.getKey().toString(),
                        "不存在的设施记录了雇工"));
            } else if (e.getValue() > MacroLaborService.jobs(facility)) {
                out.add(new DiagnosticIssue(
                        DiagnosticSeverity.ERROR,
                        MODULE,
                        "OVER_EMPLOYED",
                        e.getKey().toString(),
                        "雇工人数超过设施岗位数"));
            }
        }
        return out;
    }
}
