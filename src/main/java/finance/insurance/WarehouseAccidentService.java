package finance.insurance;

import finance.commodity.Commodity;
import finance.commodity.CommodityRegistry;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.config.FinanceConfig;
import finance.feedback.FeedbackSeverity;
import finance.feedback.PlayerNotices;
import finance.gameplay.company.CompanyInventoryFacade;
import finance.util.MoneyFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * 仓库事故：每家公司每天按配置的概率失一次火（或水淹、鼠患），毁掉公司仓库里某一种未质押库存的一部分。
 * 这是库存灾害险唯一的损失来源；买了保险的按保单理赔，没买的只能认亏。
 * 随机数由日期和公司 ID 决定，同一天重算结果不变。
 */
public final class WarehouseAccidentService {

    /** 事故原因，对应翻译键 finance.insurance.accident.(covered|uncovered).<原因>。 */
    private static final String[] CAUSES = {"fire", "flood", "rats"};

    private WarehouseAccidentService() {}

    public record Accident(UUID companyId, String commodityId, int lost, String cause, long payout) {}

    /** 当天的事故，在保单生效/到期之后、赔款发放之前调用。 */
    static List<Accident> processDay(long day) {
        double chance = FinanceConfig.insuranceAccidentChancePercent() / 100.0D;
        List<Accident> accidents = new ArrayList<>();
        if (chance <= 0) return accidents;
        List<Company> companies = new ArrayList<>(CompanyManager.getCompanies());
        companies.sort(Comparator.comparing(Company::getCompanyId));
        for (Company company : companies) {
            Accident accident = roll(company, day, chance);
            if (accident != null) accidents.add(accident);
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        for (Accident accident : accidents) notifyOwner(server, accident);
        return accidents;
    }

    private static Accident roll(Company company, long day, double chance) {
        long seed = day * 0x9E3779B97F4A7C15L ^ company.getCompanyId().getMostSignificantBits();
        SplittableRandom random = new SplittableRandom(seed);
        if (random.nextDouble() >= chance) return null;
        List<String> stocked = new ArrayList<>();
        for (Commodity commodity : CommodityRegistry.getAllCommodities()) {
            if (CompanyInventoryFacade.availableInsurableInventory(company, commodity.getId()) > 0)
                stocked.add(commodity.getId());
        }
        if (stocked.isEmpty()) return null;
        stocked.sort(String::compareTo);
        String commodityId = stocked.get(random.nextInt(stocked.size()));
        int available = CompanyInventoryFacade.availableInsurableInventory(company, commodityId);
        int maxPercent = Math.max(5, FinanceConfig.insuranceAccidentMaxLossPercent());
        int percent = 5 + random.nextInt(maxPercent - 4);
        int lost = Math.max(1, (int) ((long) available * percent / 100));
        String cause = CAUSES[random.nextInt(CAUSES.length)];
        InsuranceManager.Result result =
                InsuranceManager.createWarehouseAccident(company.getCompanyId(), commodityId, lost, day, seed);
        if (!result.success()) return null;
        return new Accident(
                company.getCompanyId(), commodityId, lost, cause, InsuranceManager.approvedFor(result.id()));
    }

    private static void notifyOwner(MinecraftServer server, Accident accident) {
        Company company = CompanyManager.getCompany(accident.companyId());
        if (server == null || company == null || !company.isPlayerOwned() || company.getOwnerId() == null) return;
        Commodity commodity = CommodityRegistry.getCommodity(accident.commodityId());
        String name = commodity == null ? accident.commodityId() : commodity.getDisplayName();
        if (accident.payout() > 0) {
            PlayerNotices.tell(
                    server,
                    company.getOwnerId(),
                    FeedbackSeverity.WARNING,
                    SoundEvents.NOTE_BLOCK_BELL.value(),
                    "finance.insurance.accident.covered." + accident.cause(),
                    company.getName(),
                    Integer.toString(accident.lost()),
                    name,
                    MoneyFormat.pounds(accident.payout()));
        } else {
            PlayerNotices.tell(
                    server,
                    company.getOwnerId(),
                    FeedbackSeverity.CRITICAL,
                    SoundEvents.ANVIL_LAND,
                    "finance.insurance.accident.uncovered." + accident.cause(),
                    company.getName(),
                    Integer.toString(accident.lost()),
                    name);
        }
    }
}
