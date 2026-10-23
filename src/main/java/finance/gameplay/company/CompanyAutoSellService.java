package finance.gameplay.company;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.data.EconomySavedData;
import java.util.UUID;

/** 公司经营界面的“自动卖出比例”按钮：每点一次在 25% / 50% / 75% / 95% / 5% 之间循环。 */
public final class CompanyAutoSellService {
    private CompanyAutoSellService() {}

    public static synchronized CompanyGameplayActionResult cycleAutoSell(UUID actor, UUID companyId, String key) {
        Company company = CompanyManager.getCompany(companyId);
        CompanyGameplayProfile profile = CompanyGameplayManager.get(companyId);
        if (company == null
                || profile == null
                || actor == null
                || key == null
                || key.isBlank()
                || key.length() > 64
                || !CompanyMembershipService.hasPermission(companyId, actor, CompanyPermission.MANAGE_PRODUCTION))
            return CompanyGameplayActionResult.fail("finance.company_gameplay.mode_denied");
        String scoped = actor + ":" + key;
        if (profile.hasOperation(scoped))
            return CompanyGameplayActionResult.fail("finance.company_gameplay.duplicate_operation");
        double current = company.getAutoSellRatio();
        company.setAutoSellRatio(
                current < .25 ? .25 : current < .50 ? .50 : current < .75 ? .75 : current < .95 ? .95 : .05);
        profile.recordOperation(scoped);
        EconomySavedData.markDirty();
        return CompanyGameplayActionResult.ok("finance.company_gameplay.autosell_changed");
    }
}
