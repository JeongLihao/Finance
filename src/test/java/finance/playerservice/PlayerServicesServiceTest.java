package finance.playerservice;

import static org.junit.jupiter.api.Assertions.*;

import finance.account.AccountManager;
import finance.bank.BankingManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import finance.governance.TakeoverService;
import finance.network.PlayerServicesActionPacket;
import finance.network.PlayerServicesResponsePacket;
import finance.playerservice.PlayerServicesService.Action;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** “更多操作”界面：每个动作都走与命令相同的服务方法，快照反映结果。 */
class PlayerServicesServiceTest {
    private final UUID player = UUID.fromString("00000000-0000-0000-0000-000000009001");
    private Company target;

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        PlayerServicesService.clearDirect();
        BankingManager.ensureDefaultBanks();
        AccountManager.deposit(player, 30_000_000);
        target = new Company(UUID.randomUUID(), "Target Corp", CompanyType.FOOD, 500_000, UUID.randomUUID());
        target.setPublic(true);
        CompanyManager.registerDirect(target);
        StockMarketManager.putStockDirect(
                new Stock("TGTC", "Target Corp", target.getCompanyId(), 10_000, 10_000, 0, 10, 10));
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
        PlayerServicesService.clearDirect();
    }

    private PlayerServicesService.Result act(Action action, String text, long a, long b, long c, long d) {
        return PlayerServicesService.act(null, player, action, text, a, b, c, d, 1);
    }

    private PlayerServicesResponsePacket snapshot() {
        return PlayerServicesService.snapshot(1, player, 1);
    }

    @Test
    void marginDepositShowsInSnapshot() {
        var r = act(Action.MARGIN_DEPOSIT, "", 5_000, 0, 0, 0);
        assertTrue(r.success(), r.message());
        assertEquals(5_000, snapshot().margin().cash());
        assertFalse(act(Action.MARGIN_BUY, "", 10, 0, 0, 0).success(), "没有股票代码时拒绝");
    }

    @Test
    void bankCharterNeedsCodeAndNameThenRatesAndDividendWork() {
        assertFalse(act(Action.BANK_CHARTER, "PBK", 10_000_000, 0, 0, 0).success());
        assertFalse(snapshot().bank().owned());
        var r = act(Action.BANK_CHARTER, "PBK Player Bank", 10_000_000, 0, 0, 0);
        assertTrue(r.success(), r.message());
        var bank = snapshot().bank();
        assertTrue(bank.owned());
        assertTrue(bank.summary().contains("PBK"), bank.summary());
        assertTrue(act(Action.BANK_RATES, "", 100, 200, 300, 0).success());
        var dividend = act(Action.BANK_DIVIDEND, "", 1_000, 0, 0, 0);
        assertTrue(dividend.success(), dividend.message());
    }

    @Test
    void tenderOfferByNameThenCancel() {
        assertTrue(StockPortfolioManager.addHolding(player, "TGTC", 3_000, 10)); // 30% 才能发起要约
        var r = act(Action.TENDER_OFFER, "Target Corp", 12, 2_100, 0, 7);
        assertTrue(r.success(), r.message());
        var rows = snapshot().tenders();
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).mine());
        assertEquals("你", rows.get(0).buyer());
        assertEquals(3_000, rows.get(0).myShares());
        var cancel = act(Action.TENDER_CANCEL, rows.get(0).id().toString(), 0, 0, 0, 0);
        assertTrue(cancel.success(), cancel.message());
        assertTrue(TakeoverService.openTenders().isEmpty());
        assertFalse(act(Action.TENDER_ACCEPT, "not-a-uuid", 10, 0, 0, 0).success());
        assertFalse(act(Action.TENDER_OFFER, "Nobody Inc", 12, 100, 0, 7).success());
    }

    @Test
    void squeezeListOnlyShowsNinetyPercentHolders() {
        assertTrue(StockPortfolioManager.addHolding(player, "TGTC", 8_000, 10));
        assertTrue(snapshot().squeezable().isEmpty());
        assertTrue(StockPortfolioManager.addHolding(player, "TGTC", 1_500, 10));
        var rows = snapshot().squeezable();
        assertEquals(1, rows.size());
        assertEquals(target.getCompanyId(), rows.get(0).companyId());
        assertEquals(950, rows.get(0).percentX10());
    }

    @Test
    void macroWageAndSelectionValidation() {
        Company own = new Company(UUID.randomUUID(), "My Farm", CompanyType.FOOD, 100_000, player);
        CompanyManager.registerDirect(own);
        var r = act(Action.MACRO_WAGE, "", 9, 0, 0, 0);
        assertTrue(r.success(), r.message());
        var macro = snapshot().macro();
        assertTrue(macro.active());
        assertEquals("My Farm", macro.company());
        assertEquals(9, macro.wage());
    }

    @Test
    void centralBankBondSubscribeRedeemAndEarlyExit() {
        var page = snapshot().centralBank();
        assertEquals(
                java.util.List.of(30, 90, 180),
                page.offers().stream()
                        .map(PlayerServicesResponsePacket.BondOffer::termDays)
                        .toList());
        assertTrue(page.offers().stream().allMatch(o -> o.yieldBps() > 0));
        long amount = Math.max(page.bondMinAmount(), 10_000);
        var r = act(Action.CB_BOND_SUBSCRIBE, "", 90, amount, 0, 0);
        assertTrue(r.success(), r.message());
        var bonds = snapshot().centralBank().bonds();
        assertEquals(1, bonds.size());
        assertEquals(amount, bonds.get(0).principal());
        assertFalse(act(Action.CB_BOND_SUBSCRIBE, "", 45, amount, 0, 0).success(), "只有 30/90/180 天");
        var redeem = act(Action.CB_BOND_REDEEM, bonds.get(0).id().toString(), 0, 0, 0, 0);
        assertTrue(redeem.success(), redeem.message());
        assertTrue(snapshot().centralBank().bonds().isEmpty());
        assertTrue(finance.macro.EconomyAchievements.pending(player)
                .contains(finance.macro.EconomyAchievements.EARLY_EXIT));
        assertFalse(act(Action.TIME_DEPOSIT_EARLY, "not-a-uuid", 0, 0, 0, 0).success());
    }

    @Test
    void earningsReportIsKeptAsDetailUntilNextQuery() {
        var r = act(Action.EARNINGS_REPORT, "tgtc", 0, 0, 0, 0);
        assertTrue(r.success(), r.message());
        assertTrue(snapshot().detailTitle().contains("TGTC"), snapshot().detailTitle());
        assertFalse(act(Action.EARNINGS_REPORT, "NOPE", 0, 0, 0, 0).success());
        assertFalse(snapshot().calendar().isEmpty());
    }

    @Test
    void responseAndActionPacketsRoundTrip() {
        assertTrue(StockPortfolioManager.addHolding(player, "TGTC", 3_000, 10));
        act(Action.TENDER_OFFER, "Target Corp", 12, 2_100, 0, 7);
        act(Action.EARNINGS_REPORT, "TGTC", 0, 0, 0, 0);
        PlayerServicesResponsePacket before = snapshot();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        PlayerServicesResponsePacket.encode(before, buf);
        assertEquals(before, PlayerServicesResponsePacket.decode(buf));

        PlayerServicesActionPacket action = new PlayerServicesActionPacket(Action.MARGIN_COVER, "TGTC", 1, 2, 3, 4);
        FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
        PlayerServicesActionPacket.encode(action, out);
        assertEquals(action, PlayerServicesActionPacket.decode(out));
    }
}
