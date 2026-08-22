package finance.gui;

import finance.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class WalletMenu extends AbstractContainerMenu {
    public static final int MAX_TRANSACTIONS = 10;
    public static final int MAX_TYPE_LENGTH = 32;
    public static final int MAX_OBJECT_LENGTH = 64;

    public record WalletTransaction(long timestamp, String type, long amount, long quantity, String objectName) {}

    private final long balance;
    private final long frozenBalance;
    private final long totalAsset;
    private final long mcDay;
    private final List<WalletTransaction> transactions;

    public WalletMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, buffer.readLong(), buffer.readLong(), buffer.readLong(), buffer.readLong(), readTransactions(buffer));
    }

    public WalletMenu(int containerId, long balance, long frozenBalance, long totalAsset,
                      long mcDay, List<WalletTransaction> transactions) {
        super(ModMenus.WALLET.get(), containerId);
        this.balance = Math.max(0, balance);
        this.frozenBalance = Math.max(0, frozenBalance);
        this.totalAsset = Math.max(0, totalAsset);
        this.mcDay = Math.max(0, mcDay);
        this.transactions = List.copyOf(transactions == null ? List.of()
                : transactions.subList(0, Math.min(MAX_TRANSACTIONS, transactions.size())));
    }

    public static void write(FriendlyByteBuf buffer, long balance, long frozenBalance,
                             long totalAsset, long mcDay, List<WalletTransaction> transactions) {
        buffer.writeLong(Math.max(0, balance));
        buffer.writeLong(Math.max(0, frozenBalance));
        buffer.writeLong(Math.max(0, totalAsset));
        buffer.writeLong(Math.max(0, mcDay));
        List<WalletTransaction> safe = transactions == null ? List.of() : transactions;
        int size = Math.min(MAX_TRANSACTIONS, safe.size());
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            WalletTransaction row = safe.get(i);
            buffer.writeLong(row.timestamp());
            buffer.writeUtf(limit(row.type(), MAX_TYPE_LENGTH), MAX_TYPE_LENGTH);
            buffer.writeLong(row.amount());
            buffer.writeLong(row.quantity());
            buffer.writeUtf(limit(row.objectName(), MAX_OBJECT_LENGTH), MAX_OBJECT_LENGTH);
        }
    }

    static List<WalletTransaction> readTransactions(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_TRANSACTIONS) {
            throw new IllegalArgumentException("Invalid wallet transaction count: " + size);
        }
        List<WalletTransaction> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new WalletTransaction(buffer.readLong(), buffer.readUtf(MAX_TYPE_LENGTH),
                    buffer.readLong(), buffer.readLong(), buffer.readUtf(MAX_OBJECT_LENGTH)));
        }
        return rows;
    }

    private static String limit(String value, int max) {
        String safe = value == null ? "" : value;
        return safe.length() <= max ? safe : safe.substring(0, max);
    }

    public long balance() { return balance; }
    public long frozenBalance() { return frozenBalance; }
    public long totalAsset() { return totalAsset; }
    public long mcDay() { return mcDay; }
    public List<WalletTransaction> transactions() { return transactions; }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
