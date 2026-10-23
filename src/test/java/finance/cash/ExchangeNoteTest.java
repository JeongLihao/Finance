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

/** 等值兑换钞：签发从账户扣款并登记编号，兑现按编号核销、给持有人入账。 */
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
        CashService.restoreExchangeNotes(java.util.Map.of());
        AccountManager.deposit(alice, 50_000);
        AccountManager.getAccount(bob);
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
        ModuleHealthRegistry.clear();
        CashService.restoreNotesOutstanding(0);
        CashService.restoreExchangeNotes(java.util.Map.of());
    }

    @Test
    void issueTakesMoneyAndRedeemPaysTheHolder() {
        long a0 = AccountManager.getBalance(alice), b0 = AccountManager.getBalance(bob);
        UUID serial = UUID.randomUUID();
        var issued = CashService.issueExchangeNote(alice, 12_345, serial);
        assertTrue(issued.success(), issued.message());
        assertEquals(a0 - 12_345, AccountManager.getBalance(alice));
        assertEquals(12_345, CashService.notesOutstanding(), "兑换钞算作流通中的钞票");
        assertEquals(12_345L, CashService.exchangeNotes().get(serial), "签发时登记编号和金额");

        var redeemed = CashService.redeemExchangeNote(bob, serial);
        assertTrue(redeemed.success(), redeemed.message());
        assertEquals(b0 + 12_345, AccountManager.getBalance(bob), "不记名：谁兑现钱就进谁的账户");
        assertEquals(0, CashService.notesOutstanding());
        assertTrue(CashService.exchangeNotes().isEmpty(), "兑现后核销");
    }

    @Test
    void copiedNoteCanOnlyBeRedeemedOnce() {
        UUID serial = UUID.randomUUID();
        assertTrue(CashService.issueExchangeNote(alice, 5_000, serial).success());
        assertTrue(CashService.redeemExchangeNote(bob, serial).success());
        long b1 = AccountManager.getBalance(bob);
        assertFalse(CashService.redeemExchangeNote(bob, serial).success(), "复制出来的同号钞票不能再兑");
        assertFalse(CashService.redeemExchangeNote(alice, serial).success());
        assertEquals(b1, AccountManager.getBalance(bob));
    }

    @Test
    void issueRejectsBadAmountsWithoutTouchingTheAccount() {
        long a0 = AccountManager.getBalance(alice);
        assertFalse(CashService.issueExchangeNote(alice, 0, UUID.randomUUID()).success());
        assertFalse(CashService.issueExchangeNote(alice, -5, UUID.randomUUID()).success());
        assertFalse(CashService.issueExchangeNote(alice, CashService.MAX_EXCHANGE_NOTE + 1, UUID.randomUUID())
                .success());
        assertFalse(
                CashService.issueExchangeNote(alice, a0 + 1, UUID.randomUUID()).success(), "余额不够");
        assertFalse(CashService.issueExchangeNote(alice, 100, null).success(), "没有编号不签发");
        assertEquals(a0, AccountManager.getBalance(alice));
        assertEquals(0, CashService.notesOutstanding());
        assertTrue(CashService.exchangeNotes().isEmpty());
    }

    @Test
    void issueRejectsReusedSerial() {
        UUID serial = UUID.randomUUID();
        assertTrue(CashService.issueExchangeNote(alice, 100, serial).success());
        long a1 = AccountManager.getBalance(alice);
        assertFalse(CashService.issueExchangeNote(alice, 200, serial).success(), "编号不能重复登记");
        assertEquals(a1, AccountManager.getBalance(alice));
        assertEquals(100L, CashService.exchangeNotes().get(serial));
    }

    @Test
    void redeemRejectsUnregisteredNotes() {
        long b0 = AccountManager.getBalance(bob);
        assertFalse(CashService.redeemExchangeNote(bob, null).success(), "旧版没有编号的钞票不能兑现");
        assertFalse(CashService.redeemExchangeNote(bob, UUID.randomUUID()).success(), "没登记过的编号不能兑现");
        assertEquals(b0, AccountManager.getBalance(bob));
    }

    @Test
    void pausedAccountModuleBlocksIssueAndRedeem() {
        UUID serial = UUID.randomUUID();
        assertTrue(CashService.issueExchangeNote(alice, 100, serial).success());
        ModuleHealthRegistry.restrict(ModuleHealthRegistry.Module.ACCOUNT, ModuleRunState.READ_ONLY, "test", 1);
        long a0 = AccountManager.getBalance(alice);
        assertFalse(CashService.issueExchangeNote(alice, 100, UUID.randomUUID()).success());
        assertFalse(CashService.redeemExchangeNote(alice, serial).success());
        assertEquals(a0, AccountManager.getBalance(alice));
        assertEquals(100L, CashService.exchangeNotes().get(serial), "没兑成功不核销");
    }

    @Test
    void registrySurvivesSaveAndLoad() {
        UUID serial = UUID.randomUUID();
        assertTrue(CashService.issueExchangeNote(alice, 7_777, serial).success());
        net.minecraft.nbt.CompoundTag root = new net.minecraft.nbt.CompoundTag();
        finance.data.serializer.CentralBankDataSerializer.save(root);
        CashService.restoreExchangeNotes(java.util.Map.of());
        finance.data.serializer.CentralBankDataSerializer.load(root);
        assertEquals(7_777L, CashService.exchangeNotes().get(serial));
        assertTrue(CashService.redeemExchangeNote(bob, serial).success());
    }

    @Test
    void poundsFormatting() {
        assertEquals("£0.05", CashService.pounds(5));
        assertEquals("£12.34", CashService.pounds(1234));
        assertEquals("£10000.00", CashService.pounds(CashService.MAX_EXCHANGE_NOTE));
    }

    @Test
    void noteNbtRoundTrip() {
        UUID serial = UUID.randomUUID();
        ItemStack note =
                ExchangeNoteItem.create(Items.PAPER, serial, 2_500, "Alice_the_very_long_player_name_overflowing", 42);
        assertEquals(2_500, ExchangeNoteItem.amount(note));
        assertEquals(42, ExchangeNoteItem.day(note));
        assertEquals(serial, ExchangeNoteItem.serial(note));
        assertEquals(32, ExchangeNoteItem.issuer(note).length(), "开票人名字截断到 32 字");
        assertEquals(0, ExchangeNoteItem.amount(new ItemStack(Items.PAPER)), "没有 NBT 的钞票金额为 0");
        assertNull(ExchangeNoteItem.serial(new ItemStack(Items.PAPER)), "没有 NBT 的钞票没有编号");
    }
}
