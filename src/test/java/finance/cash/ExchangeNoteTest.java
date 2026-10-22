package finance.cash;

import static org.junit.jupiter.api.Assertions.*;

import finance.account.AccountManager;
import finance.data.EconomySavedData;
import finance.diagnostic.ModuleHealthRegistry;
import finance.diagnostic.ModuleRunState;
import finance.item.ExchangeNoteItem;
import finance.testutil.MinecraftTestBootstrap;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 等值兑换钞：签发从账户扣款、兑现给持有人入账，金额写在物品 NBT 上。 */
class ExchangeNoteTest {
    private final UUID alice = UUID.fromString("00000000-0000-0000-0000-00000000e0a1");
    private final UUID bob = UUID.fromString("00000000-0000-0000-0000-00000000e0b2");

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensureStarted();
    }

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        ModuleHealthRegistry.clear();
        CashService.restoreNotesOutstanding(0);
        AccountManager.deposit(alice, 50_000);
        AccountManager.getAccount(bob);
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
        ModuleHealthRegistry.clear();
        CashService.restoreNotesOutstanding(0);
    }

    @Test
    void issueTakesMoneyAndRedeemPaysTheHolder() {
        long a0 = AccountManager.getBalance(alice), b0 = AccountManager.getBalance(bob);
        var issued = CashService.issueExchangeNote(alice, 12_345);
        assertTrue(issued.success(), issued.message());
        assertEquals(a0 - 12_345, AccountManager.getBalance(alice));
        assertEquals(12_345, CashService.notesOutstanding(), "兑换钞算作流通中的钞票");

        var redeemed = CashService.redeemExchangeNote(bob, 12_345);
        assertTrue(redeemed.success(), redeemed.message());
        assertEquals(b0 + 12_345, AccountManager.getBalance(bob), "不记名：谁兑现钱就进谁的账户");
        assertEquals(0, CashService.notesOutstanding());
    }

    @Test
    void issueRejectsBadAmountsWithoutTouchingTheAccount() {
        long a0 = AccountManager.getBalance(alice);
        assertFalse(CashService.issueExchangeNote(alice, 0).success());
        assertFalse(CashService.issueExchangeNote(alice, -5).success());
        assertFalse(CashService.issueExchangeNote(alice, CashService.MAX_EXCHANGE_NOTE + 1)
                .success());
        assertFalse(CashService.issueExchangeNote(alice, a0 + 1).success(), "余额不够");
        assertEquals(a0, AccountManager.getBalance(alice));
        assertEquals(0, CashService.notesOutstanding());
    }

    @Test
    void redeemRejectsEmptyNotes() {
        long b0 = AccountManager.getBalance(bob);
        assertFalse(CashService.redeemExchangeNote(bob, 0).success(), "没写金额的钞票不能兑现");
        assertEquals(b0, AccountManager.getBalance(bob));
    }

    @Test
    void pausedAccountModuleBlocksIssueAndRedeem() {
        ModuleHealthRegistry.restrict(ModuleHealthRegistry.Module.ACCOUNT, ModuleRunState.READ_ONLY, "test", 1);
        long a0 = AccountManager.getBalance(alice);
        assertFalse(CashService.issueExchangeNote(alice, 100).success());
        assertFalse(CashService.redeemExchangeNote(alice, 100).success());
        assertEquals(a0, AccountManager.getBalance(alice));
    }

    @Test
    void poundsFormatting() {
        assertEquals("£0.05", CashService.pounds(5));
        assertEquals("£12.34", CashService.pounds(1234));
        assertEquals("£10000.00", CashService.pounds(CashService.MAX_EXCHANGE_NOTE));
    }

    @Test
    void noteNbtRoundTrip() {
        ItemStack note = ExchangeNoteItem.create(Items.PAPER, 2_500, "Alice_the_very_long_player_name_overflowing", 42);
        assertEquals(2_500, ExchangeNoteItem.amount(note));
        assertEquals(42, ExchangeNoteItem.day(note));
        assertEquals(32, ExchangeNoteItem.issuer(note).length(), "开票人名字截断到 32 字");
        assertEquals(0, ExchangeNoteItem.amount(new ItemStack(Items.PAPER)), "没有 NBT 的钞票金额为 0");
    }
}
