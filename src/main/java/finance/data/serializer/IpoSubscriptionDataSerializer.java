package finance.data.serializer;

import finance.company.CompanyIPOService;
import finance.company.IpoSubscription;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * 上市认购持久化（DataVersion 45 新增）：进行中和最近结束的认购，含每个认购人的股数。
 * 托管里的钱在账户数据里，这里只存股数。旧存档没有该标签时为空，坏记录跳过。
 */
public final class IpoSubscriptionDataSerializer {
    public static final String ROOT = "IpoSubscriptions";

    private IpoSubscriptionDataSerializer() {}

    public static void save(CompoundTag root) {
        ListTag list = new ListTag();
        for (IpoSubscription s : CompanyIPOService.subscriptions()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", s.id());
            t.putUUID("Company", s.companyId());
            t.putUUID("Founder", s.founderAccount());
            t.putLong("Price", s.issuePrice());
            t.putLong("Quantity", s.issueQuantity());
            t.putLong("TotalShares", s.totalShares());
            t.putLong("StartDay", s.startDay());
            t.putLong("EndDay", s.endDay());
            t.putString("Status", s.status().name());
            ListTag subs = new ListTag();
            s.subscriptions().forEach((account, shares) -> {
                CompoundTag e = new CompoundTag();
                e.putUUID("Account", account);
                e.putLong("Shares", shares);
                subs.add(e);
            });
            t.put("Subscriptions", subs);
            list.add(t);
        }
        root.put(ROOT, list);
    }

    public static void load(CompoundTag root) {
        CompanyIPOService.clearDirect();
        if (root == null || !root.contains(ROOT)) return;
        for (Tag raw : root.getList(ROOT, Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            try {
                UUID id = NbtDataSupport.readUuidOrNull(t, "Id");
                UUID company = NbtDataSupport.readUuidOrNull(t, "Company");
                UUID founder = NbtDataSupport.readUuidOrNull(t, "Founder");
                IpoSubscription s = new IpoSubscription(
                        id,
                        company,
                        founder,
                        t.getLong("Price"),
                        t.getLong("Quantity"),
                        t.getLong("TotalShares"),
                        t.getLong("StartDay"),
                        t.getLong("EndDay"),
                        IpoSubscription.Status.valueOf(t.getString("Status")));
                for (Tag rawSub : t.getList("Subscriptions", Tag.TAG_COMPOUND)) {
                    CompoundTag e = (CompoundTag) rawSub;
                    UUID account = NbtDataSupport.readUuidOrNull(e, "Account");
                    if (account != null && e.getLong("Shares") > 0) s.restoreSubscription(account, e.getLong("Shares"));
                }
                CompanyIPOService.restoreDirect(s);
            } catch (IllegalArgumentException bad) {
                // 坏记录跳过
            }
        }
    }
}
