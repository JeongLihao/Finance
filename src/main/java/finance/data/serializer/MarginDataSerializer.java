package finance.data.serializer;

import finance.margin.MarginAccount;
import finance.margin.MarginPoolManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Map;
import java.util.UUID;

/**
 * 融资融券 NBT 序列化。
 *
 * <p>旧存档兼容：缺失 "Margin" section = 无两融业务；各字段用 contains 检查。
 */
public final class MarginDataSerializer {

    private static final int MAX_ACCOUNTS = 4096;
    private static final int MAX_ENTRIES = 1024;

    private MarginDataSerializer() {
    }

    public static void save(CompoundTag root) {
        CompoundTag section = new CompoundTag();

        ListTag accounts = new ListTag();
        int count = 0;
        for (MarginAccount acct : MarginPoolManager.accounts().values()) {
            if (count++ >= MAX_ACCOUNTS) break;
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Player", acct.getPlayerId());
            tag.putLong("CashCollateral", acct.getCashCollateral());
            tag.putLong("FinancedDebt", acct.getFinancedDebt());
            tag.putLong("FinancedInterest", acct.getFinancedInterestOwed());
            tag.putLong("BadDebt", acct.getBadDebt());
            tag.putLong("LastInterestDay", acct.getLastInterestDay());
            tag.put("FinancedShares", writeStringMap(acct.getFinancedShares()));
            tag.put("ShortPositions", writeStringMap(acct.getShortPositions()));
            tag.put("ShortProceeds", writeStringMap(acct.getShortProceeds()));
            tag.put("ShortInterest", writeStringMap(acct.getShortInterestOwed()));
            tag.put("ShortPrices", writeStringMap(acct.getShortSellPrices()));
            accounts.add(tag);
        }
        section.put("Accounts", accounts);

        ListTag pool = new ListTag();
        for (Map.Entry<String, Long> e : MarginPoolManager.borrowedSnapshot().entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Symbol", e.getKey());
            tag.putLong("Borrowed", e.getValue());
            pool.add(tag);
        }
        section.put("Pool", pool);

        root.put("Margin", section);
    }

    public static void load(CompoundTag root) {
        MarginPoolManager.clearDirect();
        if (!root.contains("Margin")) {
            return; // 旧存档：无两融业务
        }
        CompoundTag section = root.getCompound("Margin");

        if (section.contains("Accounts")) {
            ListTag accounts = section.getList("Accounts", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(accounts.size(), MAX_ACCOUNTS); i++) {
                CompoundTag tag = accounts.getCompound(i);
                if (!tag.contains("Player")) continue;
                MarginAccount acct = new MarginAccount(tag.getUUID("Player"));
                if (tag.contains("CashCollateral")) {
                    long v = tag.getLong("CashCollateral");
                    if (v > 0) acct.addCashCollateral(v);
                }
                if (tag.contains("FinancedDebt")) acct.addFinancedDebt(Math.max(0, tag.getLong("FinancedDebt")));
                if (tag.contains("FinancedInterest")) acct.addFinancedInterest(Math.max(0, tag.getLong("FinancedInterest")));
                if (tag.contains("BadDebt")) acct.addBadDebt(Math.max(0, tag.getLong("BadDebt")));
                if (tag.contains("LastInterestDay")) acct.setLastInterestDay(tag.getLong("LastInterestDay"));
                readStringMap(tag, "FinancedShares", acct.getFinancedShares());
                readStringMap(tag, "ShortPositions", acct.getShortPositions());
                readStringMap(tag, "ShortProceeds", acct.getShortProceeds());
                readStringMap(tag, "ShortInterest", acct.getShortInterestOwed());
                readStringMap(tag, "ShortPrices", acct.getShortSellPrices());
                MarginPoolManager.putAccountDirect(acct);
            }
        }

        if (section.contains("Pool")) {
            ListTag pool = section.getList("Pool", Tag.TAG_COMPOUND);
            for (int i = 0; i < pool.size(); i++) {
                CompoundTag tag = pool.getCompound(i);
                if (tag.contains("Symbol")) {
                    MarginPoolManager.restoreBorrowed(tag.getString("Symbol"), tag.getLong("Borrowed"));
                }
            }
        }
    }

    private static ListTag writeStringMap(Map<String, Long> map) {
        ListTag list = new ListTag();
        int count = 0;
        for (Map.Entry<String, Long> e : map.entrySet()) {
            if (count++ >= MAX_ENTRIES) break;
            if (e.getKey() == null || e.getValue() == null || e.getValue() <= 0) continue;
            CompoundTag tag = new CompoundTag();
            tag.putString("K", e.getKey());
            tag.putLong("V", e.getValue());
            list.add(tag);
        }
        return list;
    }

    private static void readStringMap(CompoundTag parent, String key, Map<String, Long> target) {
        if (!parent.contains(key)) return;
        ListTag list = parent.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(list.size(), MAX_ENTRIES); i++) {
            CompoundTag tag = list.getCompound(i);
            if (!tag.contains("K")) continue;
            long v = tag.getLong("V");
            if (v > 0) target.put(tag.getString("K"), v);
        }
    }
}
