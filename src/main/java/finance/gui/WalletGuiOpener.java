package finance.gui;

import finance.account.Account;
import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.cycle.EconomyCycleService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraftforge.network.NetworkHooks;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class WalletGuiOpener {
    private WalletGuiOpener() {}

    public static void open(ServerPlayer player) {
        Account account = AccountManager.getAccount(player.getUUID());
        long totalAsset;
        try {
            totalAsset = Math.addExact(account.getBalance(), account.getFrozenBalance());
        } catch (ArithmeticException exception) {
            totalAsset = Long.MAX_VALUE;
        }
        long mcDay = EconomyCycleService.currentMcDay(player.server);
        List<WalletMenu.WalletTransaction> rows = recentTransactions(player.getUUID());
        long finalTotalAsset = totalAsset;
        MenuProvider provider = new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("screen.finance.wallet");
            }

            @Override
            public WalletMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory inventory,
                                         net.minecraft.world.entity.player.Player menuPlayer) {
                return new WalletMenu(containerId, account.getBalance(), account.getFrozenBalance(),
                        finalTotalAsset, mcDay, rows);
            }
        };
        NetworkHooks.openScreen(player, provider,
                buffer -> WalletMenu.write(buffer, account.getBalance(), account.getFrozenBalance(),
                        finalTotalAsset, mcDay, rows));
    }

    private static List<WalletMenu.WalletTransaction> recentTransactions(UUID playerId) {
        List<TransactionRecord> all = AccountManager.getTransactions();
        List<WalletMenu.WalletTransaction> result = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0 && result.size() < WalletMenu.MAX_TRANSACTIONS; i--) {
            TransactionRecord record = all.get(i);
            if (record.getPlayerId() == null || !record.getPlayerId().equals(playerId)) continue;
            result.add(new WalletMenu.WalletTransaction(
                    record.getTimestamp().toEpochSecond(ZoneOffset.UTC), record.getType().name(),
                    record.getAmount(), record.getQuantity(), record.getObjectName()));
        }
        return result;
    }
}
