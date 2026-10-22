package finance.macro;

import static org.junit.jupiter.api.Assertions.*;

import finance.account.AccountManager;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import finance.item.NewspaperData;
import finance.market.NpcMarketMaker;
import finance.settlement.SettlementManager;
import finance.settlement.SettlementRecord;
import finance.testutil.MinecraftTestBootstrap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 《金融周报》与商情调查。 */
class NewsServiceTest {
    private SettlementRecord settlement;
    private SettlementEconomy econ;
    private UUID player;

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensureStarted();
    }

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.resetToDefaults();
        CommodityRegistry.register(
                new Commodity("wool", "minecraft:white_wool", "羊毛", CommodityCategory.MISCELLANEOUS, 9));
        AccountManager.getOrCreateSystemAccount(NpcMarketMaker.NPC_UUID);
        AccountManager.deposit(NpcMarketMaker.NPC_UUID, 1_000_000L);
        settlement = SettlementManager.registerOrRecover(
                UUID.randomUUID(), "minecraft:overworld", new BlockPos(0, 64, 0), "alpha");
        MacroEconomyServiceTest.makeVillage(settlement, 24, 18, 8, 10);
        econ = MacroEconomyManager.getOrCreate(settlement.id());
        player = UUID.randomUUID();
        AccountManager.getOrCreateSystemAccount(player);
        AccountManager.deposit(player, 1_000L);
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.resetToDefaults();
    }

    @Test
    void weeklyIssueComesOutEverySevenDays() {
        NewsService.processDay(null, 1);
        for (int d = 2; d <= 7; d++) NewsService.processDay(null, d);
        assertTrue(NewsService.archive().isEmpty(), "不到一周不出刊");
        NewsService.report(40, "小事一件", "", false);
        NewsService.processDay(null, 8);
        assertEquals(1, NewsService.archive().size());
        NewsService.Issue issue = NewsService.archive().get(0);
        assertFalse(issue.extra());
        assertEquals("小事一件", issue.headline(), "头版是本周新闻价值最高的事");
        assertTrue(issue.pages().get(0).contains("《金融周报》第 1 期"));
        assertTrue(NewsService.pool().isEmpty(), "出刊后新闻池清空");
        assertTrue(MacroWorldFeedback.queued().stream().anyMatch(a -> a.key().equals("finance.macro.announce.weekly")));
    }

    @Test
    void majorEventsGetAnExtraButNotTooOften() {
        NewsService.processDay(null, 1);
        NewsService.report(90, "alpha闹饥荒", "粮食只够 20%", true);
        NewsService.processDay(null, 2);
        assertEquals(1, NewsService.archive().size());
        assertTrue(NewsService.archive().get(0).extra());
        assertTrue(NewsService.archive().get(0).pages().get(0).contains("【号外】"));

        NewsService.report(80, "工人大罢工！", "", true);
        NewsService.processDay(null, 3);
        assertEquals(1, NewsService.archive().size(), "号外间隔不足 3 天：只发快讯");
        assertTrue(MacroWorldFeedback.queued().stream().anyMatch(a -> a.key().equals("finance.macro.announce.brief")));

        NewsService.report(70, "议会通过：税制改为「高税」", "", true);
        NewsService.processDay(null, 5);
        assertEquals(2, NewsService.archive().size(), "隔够 3 天可以再出号外");

        NewsService.report(10, "小新闻", "", false);
        NewsService.processDay(null, 6);
        assertEquals(2, NewsService.archive().size(), "小事不出号外");
    }

    @Test
    void archiveKeepsOnlyRecentIssues() {
        NewsService.processDay(null, 0);
        for (int week = 1; week <= 12; week++) NewsService.processDay(null, week * 7L);
        assertEquals(NewsService.MAX_ARCHIVE, NewsService.archive().size());
        assertEquals(12, NewsService.archive().get(NewsService.MAX_ARCHIVE - 1).number());
        assertEquals(NewsService.MAX_ARCHIVE, NewsService.unclaimed(player).size());
    }

    @Test
    void newspaperHasMetadataSectionsAndPersonalPage() {
        Company company = new Company(UUID.randomUUID(), "Mill", CompanyType.FOOD, 10_000, player);
        CompanyManager.registerDirect(company);
        NewsService.processDay(null, 1);
        NewsService.recordPersonal(player, "商队抵达alpha，盈亏 +300");
        NewsService.processDay(null, 8);
        NewsService.Issue issue = NewsService.archive().get(0);
        String personal = NewsWriter.personalPage(player, issue);
        assertTrue(personal.contains("Mill"));
        assertTrue(personal.contains("商队抵达alpha"), "出版时记下的个人新闻进了你的生意版");
        NewspaperData.Paper paper = NewsService.paper(issue, personal);
        assertEquals(1, paper.number());
        assertEquals(8, paper.day());
        assertFalse(paper.extra());
        assertEquals(issue.headline(), paper.headline());
        assertTrue(paper.pages().size() >= issue.pages().size() + 1);
        // 写进物品再读回来，内容不变；版面从头版开始，最后是“你的生意”
        ItemStack stack = new ItemStack(Items.PAPER);
        NewspaperData.write(stack, paper);
        assertEquals(paper, NewspaperData.read(stack));
        List<NewspaperData.Section> sections = NewspaperData.sections(paper);
        assertEquals(NewspaperData.FRONT_PAGE, sections.get(0).title());
        assertEquals("你的生意", sections.get(sections.size() - 1).title());
        assertTrue(sections.get(sections.size() - 1).body().contains("商队抵达alpha"));
        assertTrue(sections.stream().anyMatch(sec -> sec.title().equals("市场")));
    }

    @Test
    void paginateRespectsPageLimits() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 40; i++) text.append("第").append(i).append("行新闻内容\n");
        List<String> pages = NewsWriter.paginate(text.toString());
        assertTrue(pages.size() >= 3);
        for (String p : pages) {
            assertTrue(p.split("\n").length <= NewsWriter.PAGE_LINES, p);
            assertTrue(p.length() <= NewsWriter.PAGE_CHARS + 20, p);
        }
    }

    @Test
    void radarFindsShortagesAndIdleWorkersButCostsMoneyAndCoolsDown() {
        GoodBook wool = econ.book("wool");
        wool.factorBps = 15_000;
        wool.lastDemand = 40;
        wool.lastSupply = 5;
        MacroRadar.Purchase first = MacroRadar.purchase(player, 10);
        assertTrue(first.success(), first.message());
        assertEquals(1_000L - finance.config.FinanceConfig.macroRadarCost(), AccountManager.getBalance(player));
        assertTrue(first.opportunities().stream()
                .anyMatch(o -> o.kind() == MacroRadar.Kind.SHORTAGE && o.text().contains("羊毛")));
        assertTrue(first.opportunities().stream().anyMatch(o -> o.kind() == MacroRadar.Kind.LABOR), "60 人没人雇");
        assertFalse(MacroRadar.purchase(player, 10).success(), "冷却期内不能再查");
        assertTrue(MacroRadar.purchase(player, 11).success());
        AccountManager.withdraw(player, AccountManager.getBalance(player));
        assertFalse(MacroRadar.purchase(player, 13).success(), "没钱查不了");
    }

    @Test
    void weeklyOpportunityIsFreeButStamped() {
        GoodBook wool = econ.book("wool");
        wool.factorBps = 15_000;
        wool.lastDemand = 40;
        wool.lastSupply = 5;
        String page = NewsWriter.opportunityPage(42);
        assertTrue(page.contains("调查于第 42 天"));
        assertTrue(page.contains("更多操作"), "提示玩家去金融中心做商情调查");
    }

    @Test
    void newsDeskSurvivesSaveLoad() {
        NewsService.processDay(null, 1);
        NewsService.report(90, "alpha闹饥荒", "粮食只够 20%", true);
        NewsService.processDay(null, 2);
        NewsService.report(30, "本周小事", "", false);
        NewsService.recordPersonal(player, "一条个人新闻");
        NewsService.claimedDirect().put(1, new java.util.LinkedHashSet<>(List.of(player)));
        MacroRadar.purchase(player, 2);
        CompoundTag saved = new EconomySavedData().save(new CompoundTag());
        EconomySavedData.load(saved);
        assertEquals(1, NewsService.counter());
        assertEquals(1, NewsService.archive().size());
        assertEquals("alpha闹饥荒", NewsService.archive().get(0).headline());
        assertTrue(NewsService.unclaimed(player).isEmpty(), "领过的不会再领");
        assertEquals(2, NewsService.pool().size(), "号外发过的大事留在池里，周刊再回顾");
        assertEquals(Map.of(player, List.of("一条个人新闻")), Map.copyOf(NewsService.personalDirect()));
        assertEquals(2, NewsService.lastRadarDay(player));
        assertEquals(2, NewsService.lastExtraDay());
        assertTrue(finance.diagnostic.ModuleHealthRegistry.mayWrite(
                finance.diagnostic.ModuleHealthRegistry.Module.MACRO_ECONOMY));
    }
}
