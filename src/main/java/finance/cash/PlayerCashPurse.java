package finance.cash;

import finance.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 玩家背包里的现金和绿宝石；每日兑换额度存在玩家持久 NBT（finance_cash）里，死亡和重启都保留。 */
public final class PlayerCashPurse implements CashPurse {
    private static final String TAG = "finance_cash";

    private final ServerPlayer player;

    public PlayerCashPurse(ServerPlayer player) {
        this.player = player;
    }

    public static Item item(Denomination d) {
        return switch (d) {
            case POUND -> ModItems.POUND_NOTE.get();
            case TEN_PENCE -> ModItems.TEN_PENCE.get();
            case PENNY -> ModItems.PENNY.get();
        };
    }

    @Override
    public long count(Denomination d) {
        return count(item(d));
    }

    @Override
    public boolean remove(Denomination d, long n) {
        return remove(item(d), n);
    }

    @Override
    public void give(Denomination d, long n) {
        give(item(d), n);
    }

    @Override
    public long emeralds() {
        return count(Items.EMERALD);
    }

    @Override
    public boolean removeEmeralds(long n) {
        return remove(Items.EMERALD, n);
    }

    @Override
    public void giveEmeralds(long n) {
        give(Items.EMERALD, n);
    }

    @Override
    public long quotaUsed(String key, long day) {
        CompoundTag tag = player.getPersistentData().getCompound(TAG).getCompound(key);
        return tag.getLong("day") == day ? Math.max(0, tag.getLong("used")) : 0;
    }

    @Override
    public void setQuotaUsed(String key, long day, long used) {
        CompoundTag root = player.getPersistentData().getCompound(TAG);
        CompoundTag tag = new CompoundTag();
        tag.putLong("day", day);
        tag.putLong("used", Math.max(0, used));
        root.put(key, tag);
        player.getPersistentData().put(TAG, root);
    }

    private long count(Item item) {
        Inventory inv = player.getInventory();
        long n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private boolean remove(Item item, long n) {
        if (n <= 0) return n == 0;
        if (count(item) < n) return false;
        Inventory inv = player.getInventory();
        long rest = n;
        for (int i = 0; i < inv.getContainerSize() && rest > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(item)) continue;
            int take = (int) Math.min(rest, s.getCount());
            s.shrink(take);
            rest -= take;
        }
        inv.setChanged();
        return true;
    }

    private void give(Item item, long n) {
        long rest = n;
        int max = new ItemStack(item).getMaxStackSize();
        while (rest > 0) {
            int size = (int) Math.min(rest, max);
            ItemStack stack = new ItemStack(item, size);
            rest -= size;
            if (!player.getInventory().add(stack) || !stack.isEmpty()) {
                player.drop(stack, false);
            }
        }
        player.getInventory().setChanged();
    }
}
