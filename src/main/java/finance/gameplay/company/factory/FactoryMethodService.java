package finance.gameplay.company.factory;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.gameplay.company.CompanyFacilityManager;
import finance.gameplay.company.CompanyFacilityRecord;
import finance.gameplay.company.CompanyGameplayActionResult;
import finance.gameplay.company.CompanyMembershipService;
import finance.gameplay.company.CompanyPermission;
import finance.gui.CompanyGameplayMenu;
import finance.macro.MacroEconomyManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** 控制器"厂房"页：生成界面数据，处理"切换到此方式"。 */
public final class FactoryMethodService {
    private FactoryMethodService() {}

    /**
     * 玩家选择目标方式。设备和等级都满足时才可选（按即时扫描校验），下一个日结生效。
     * 选择的是"目标"：之后设备不够会自动退化，补齐后自动恢复。
     */
    public static CompanyGameplayActionResult select(
            ServerPlayer player, UUID companyId, UUID facilityId, int tierIndex) {
        CompanyFacilityRecord facility = CompanyFacilityManager.get(facilityId);
        ProductionTier[] tiers = ProductionTier.values();
        if (player == null
                || facility == null
                || tierIndex < 0
                || tierIndex >= tiers.length
                || !facility.companyId().equals(companyId)
                || !FactoryService.operational(facility)) {
            return CompanyGameplayActionResult.fail("finance.company_gameplay.invalid_request");
        }
        if (!CompanyMembershipService.hasPermission(companyId, player.getUUID(), CompanyPermission.MANAGE_PRODUCTION)) {
            return CompanyGameplayActionResult.fail("finance.factory.permission_denied");
        }
        Company company = CompanyManager.getCompany(companyId);
        ProductionMethod method = company == null ? null : ProductionMethods.get(company.getType(), tiers[tierIndex]);
        if (method == null) return CompanyGameplayActionResult.fail("finance.company_gameplay.invalid_request");
        if (facility.productionLevel() < method.tier().requiredLevel()) {
            return CompanyGameplayActionResult.fail("finance.factory.level_too_low");
        }
        ServerLevel level = FactoryService.level(player.server, facility.dimensionId());
        if (!method.satisfiedBy(FactoryService.scanForDisplay(level, facility))) {
            return CompanyGameplayActionResult.fail("finance.factory.equipment_missing");
        }
        if (facility.factory().targetTier() == method.tier()) {
            return CompanyGameplayActionResult.ok("finance.factory.already_selected");
        }
        facility.factory().setTargetTier(method.tier());
        EconomySavedData.markDirty();
        return CompanyGameplayActionResult.ok("finance.factory.method_selected");
    }

    /** 控制器界面的厂房页数据（含即时扫描的设备清单）。 */
    public static CompanyGameplayMenu.FactoryView view(ServerLevel level, CompanyFacilityRecord facility, long day) {
        if (facility == null) return null;
        Company company = CompanyManager.getCompany(facility.companyId());
        if (company == null) return null;
        FactoryState state = facility.factory();
        Map<FactoryEquipment, Integer> counts = FactoryService.scanForDisplay(level, facility);
        List<CompanyGameplayMenu.MethodRow> methods = new ArrayList<>();
        for (ProductionMethod m : ProductionMethods.forType(company.getType())) {
            List<CompanyGameplayMenu.EquipRow> equipment = new ArrayList<>();
            m.required()
                    .forEach((e, need) -> equipment.add(
                            new CompanyGameplayMenu.EquipRow(e.name(), ProductionMethod.count(counts, e), need)));
            methods.add(new CompanyGameplayMenu.MethodRow(
                    m.tier().name(),
                    m.key(),
                    facility.productionLevel() >= m.tier().requiredLevel(),
                    ProductionMethods.available(m, facility.productionLevel(), counts),
                    m.jobsPerDevice(),
                    equipment,
                    flows(m.inputs()),
                    flows(m.outputs())));
        }
        int retoolLeft = state.retooling(day) ? (int) Math.min(365, state.retoolUntilDay() - day) : 0;
        int provisionalLeft = state.provisionalOrUnset(day)
                ? (int) Math.min(
                        365,
                        state.provisionalUntilDay() == FactoryState.PROVISIONAL_UNSET
                                ? FinanceConfig.facilityProvisionalDays()
                                : state.provisionalUntilDay() - day + 1)
                : -1;
        FactoryZone zone = FactoryService.zone();
        return new CompanyGameplayMenu.FactoryView(
                facility.facilityId(),
                facility.productionLevel(),
                facility.status().name(),
                state.activeTier() == null ? "" : state.activeTier().name(),
                state.targetTier().name(),
                retoolLeft,
                provisionalLeft,
                MacroEconomyManager.employment(facility.facilityId()),
                FactoryService.jobs(facility),
                ProductionMethods.jobsCap(facility.productionLevel(), FinanceConfig.facilityJobsCapPerLevel()),
                zone.radius(),
                zone.below(),
                zone.above(),
                methods);
    }

    private static List<CompanyGameplayMenu.FlowRow> flows(Map<String, Integer> map) {
        List<CompanyGameplayMenu.FlowRow> out = new ArrayList<>();
        map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> out.add(new CompanyGameplayMenu.FlowRow(e.getKey(), e.getValue())));
        return out;
    }

    /** 蹲下右键控制器的提示：当前方式 · 岗位 · 缺什么。 */
    public static net.minecraft.network.chat.Component inspect(
            ServerLevel level, CompanyFacilityRecord facility, long day) {
        Company company = facility == null ? null : CompanyManager.getCompany(facility.companyId());
        if (company == null)
            return net.minecraft.network.chat.Component.translatable("finance.company_gameplay.factory_denied");
        FactoryState state = facility.factory();
        ProductionMethod effective = state.effectiveMethod(company.getType(), day);
        net.minecraft.network.chat.Component method = effective == null
                ? net.minecraft.network.chat.Component.translatable("screen.finance.factory.no_method")
                : state.activeTier() == null
                        ? net.minecraft.network.chat.Component.translatable("screen.finance.factory.provisional")
                        : net.minecraft.network.chat.Component.translatable(effective.translationKey());
        // 缺什么：按目标档列出缺的设备
        ProductionMethod target = ProductionMethods.get(company.getType(), state.targetTier());
        Map<FactoryEquipment, Integer> counts = FactoryService.scanForDisplay(level, facility);
        net.minecraft.network.chat.MutableComponent missing = net.minecraft.network.chat.Component.empty();
        boolean any = false;
        if (target != null) {
            for (var e : target.required().entrySet()) {
                int lack = e.getValue() - ProductionMethod.count(counts, e.getKey());
                if (lack <= 0) continue;
                if (any) missing.append("、");
                missing.append(net.minecraft.network.chat.Component.translatable(
                                e.getKey().translationKey()))
                        .append("×" + lack);
                any = true;
            }
        }
        net.minecraft.network.chat.Component lackText = any
                ? missing
                : net.minecraft.network.chat.Component.translatable("screen.finance.factory.nothing_missing");
        return net.minecraft.network.chat.Component.translatable(
                "finance.factory.inspect",
                method,
                MacroEconomyManager.employment(facility.facilityId()),
                FactoryService.jobs(facility),
                lackText);
    }
}
