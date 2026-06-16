package finance.data;

import finance.account.Account;
import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.commodity.Commodity;
import finance.commodity.CommodityRegistry;
import finance.event.EventManager;
import finance.event.EventTier;
import finance.event.MarketEvent;
import finance.market.MarketManager;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.market.Order;
import finance.market.OrderType;
import finance.account.TransactionType;
import finance.market.Trade;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.level.storage.DimensionDataStorage;

/**
 * 经济数据持久化 —— 将账户余额、交易记录、市场订单和成交历史
 * 写入 Minecraft 的世界存档中，服务器重启后自动恢复。
 *
 * <h3>保存内容</h3>
 * <ul>
 *   <li>账户余额（含冻结金额）</li>
 *   <li>最近 500 条交易记录</li>
 *   <li>成交历史</li>
 *   <li>未成交的活跃订单</li>
 *   <li>NPC 做市商价格</li>
 * </ul>
 */
public class EconomySavedData extends SavedData {

    public static final String DATA_NAME = "finance_data";

    // ================================================================
    // 保存
    // ================================================================

    @Override
    public CompoundTag save(CompoundTag tag) {

        // ---- 保存账户余额 ----
        ListTag accountsTag = new ListTag();

        for (Map.Entry<UUID, Account> entry :
                AccountManager.getAccounts().entrySet()) {

            CompoundTag accountTag = new CompoundTag();

            accountTag.putUUID(
                    "PlayerUUID",
                    entry.getKey()
            );

            accountTag.putLong(
                    "Balance",
                    entry.getValue().getBalance()
            );

            accountTag.putLong(
                    "FrozenBalance",
                    entry.getValue().getFrozenBalance()
            );

            accountsTag.add(accountTag);
        }

        tag.put("Accounts", accountsTag);

        // ---- 保存交易记录（仅最近 500 条） ----
        ListTag transactionsTag = new ListTag();

        List<TransactionRecord> allTxns =
                AccountManager.getTransactions();

        int txStart = Math.max(
                0,
                allTxns.size() - 500
        );

        for (int i = txStart; i < allTxns.size(); i++) {

            TransactionRecord record = allTxns.get(i);

            CompoundTag txTag = new CompoundTag();

            txTag.putUUID("From", record.getFrom());
            txTag.putUUID("To", record.getTo());
            txTag.putLong("Amount", record.getAmount());
            txTag.putString("Type", record.getType().name());

            txTag.putLong(
                    "Timestamp",
                    record.getTimestamp()
                            .toEpochSecond(
                                    ZoneOffset.UTC
                            )
            );

            transactionsTag.add(txTag);
        }

        tag.put("Transactions", transactionsTag);

        // ---- 保存成交历史 ----
        ListTag tradesTag = new ListTag();

        for (Trade trade : MarketManager.getTradeHistory()) {

            CompoundTag tradeTag = new CompoundTag();

            tradeTag.putUUID("Buyer", trade.getBuyer());
            tradeTag.putUUID("Seller", trade.getSeller());
            tradeTag.putString(
                    "CommodityId",
                    trade.getCommodityId()
            );
            tradeTag.putLong("Price", trade.getPrice());
            tradeTag.putInt("Quantity", trade.getQuantity());

            tradeTag.putLong(
                    "Timestamp",
                    trade.getTimestamp()
                            .toEpochSecond(
                                    ZoneOffset.UTC
                            )
            );

            tradesTag.add(tradeTag);
        }

        tag.put("Trades", tradesTag);

        // ---- 保存活跃订单 ----
        ListTag ordersTag = new ListTag();

        for (Order order : MarketManager.getOrders()) {

            CompoundTag orderTag = new CompoundTag();

            orderTag.putUUID(
                    "PlayerUUID",
                    order.getPlayerId()
            );

            orderTag.putString(
                    "CommodityId",
                    order.getCommodityId()
            );

            orderTag.putString(
                    "Type",
                    order.getType().name()
            );

            orderTag.putLong(
                    "Price",
                    order.getPrice()
            );

            orderTag.putInt(
                    "Quantity",
                    order.getQuantity()
            );

            orderTag.putLong(
                    "Timestamp",
                    order.getTimestamp()
                            .toEpochSecond(
                                    ZoneOffset.UTC
                            )
            );

            ordersTag.add(orderTag);
        }

        tag.put("Orders", ordersTag);

        // ---- 保存 NPC 市场价格 ----
        ListTag pricesTag = new ListTag();

        for (MarketPrice mp :
                NpcMarketMaker.getAllMarketPrices().values()) {

            CompoundTag priceTag = new CompoundTag();
            priceTag.putString("CommodityId", mp.getCommodityId());
            priceTag.putLong("MidPrice", mp.getMidPrice());
            priceTag.putLong("BasePrice", mp.getBasePrice());
            priceTag.putDouble("Spread", mp.getSpread());

            // 24h 统计
            priceTag.putLong("DayHigh", mp.getDayHigh());
            priceTag.putLong("DayLow", mp.getDayLow());
            priceTag.putInt("DayVolume", mp.getDayVolume());
            priceTag.putLong("DayOpen", mp.getDayOpen());

            pricesTag.add(priceTag);
        }

        tag.put("MarketPrices", pricesTag);

        // ---- 保存价格快照 ----
        ListTag snapshotsTag = new ListTag();

        for (MarketPrice mp :
                NpcMarketMaker.getAllMarketPrices().values()) {

            CompoundTag commoditySnapTag = new CompoundTag();
            commoditySnapTag.putString("CommodityId", mp.getCommodityId());

            ListTag snapsList = new ListTag();
            for (MarketPrice.PriceSnapshot snap : mp.getSnapshots()) {
                CompoundTag snapTag = new CompoundTag();
                snapTag.putLong("Timestamp",
                        snap.getTimestamp().toEpochSecond(ZoneOffset.UTC));
                snapTag.putLong("Price", snap.getPrice());
                snapTag.putInt("Volume", snap.getVolume());
                snapsList.add(snapTag);
            }
            commoditySnapTag.put("Snapshots", snapsList);
            snapshotsTag.add(commoditySnapTag);
        }

        tag.put("PriceSnapshots", snapshotsTag);

        // ---- 保存事件状态 ----
        tag.putInt("TimerMinor", EventManager.getTimerMinor());
        tag.putInt("TimerMajor", EventManager.getTimerMajor());
        tag.putInt("TimerBlackSwan", EventManager.getTimerBlackSwan());

        ListTag eventsTag = new ListTag();
        for (MarketEvent ev : EventManager.getActiveEvents()) {
            CompoundTag evTag = new CompoundTag();
            evTag.putString("Name", ev.getName());
            evTag.putString("Description", ev.getDescription());
            evTag.putString("Tier", ev.getTier().name());
            if (ev.getCommodityId() != null) {
                evTag.putString("CommodityId", ev.getCommodityId());
            }
            evTag.putDouble("PriceMultiplier", ev.getPriceMultiplier());
            evTag.putInt("TotalTicks", ev.getTotalTicks());
            evTag.putInt("RemainingTicks", ev.getRemainingTicks());
            eventsTag.add(evTag);
        }
        tag.put("ActiveEvents", eventsTag);

        return tag;
    }

    // ================================================================
    // 加载
    // ================================================================

    /**
     * 从 NBT 数据恢复全部经济状态。
     * 注意：加载时如果发现 FrozenBalance，需要将 balance+frozen 设为总余额后重新冻结。
     */
    public static EconomySavedData load(CompoundTag tag) {

        EconomySavedData data = new EconomySavedData();

        // ---- 加载账户余额 ----
        ListTag accountsTag = tag.getList(
                "Accounts",
                Tag.TAG_COMPOUND
        );

        for (Tag rawTag : accountsTag) {

            CompoundTag accountTag = (CompoundTag) rawTag;

            UUID playerUUID = accountTag.getUUID("PlayerUUID");
            long balance = accountTag.getLong("Balance");

            Account account = AccountManager.getAccount(playerUUID);

            if (accountTag.contains("FrozenBalance")) {
                long frozen =
                        accountTag.getLong("FrozenBalance");

                // 已保存的 Balance 是可用余额，总余额 = 可用 + 冻结
                account.setBalance(balance + frozen);

                if (frozen > 0) {
                    account.freezeFunds(frozen);
                }

            } else {
                account.setBalance(balance);
            }
        }

        // ---- 加载交易记录 ----
        if (tag.contains("Transactions")) {

            AccountManager.clearTransactions();

            ListTag transactionsTag = tag.getList(
                    "Transactions",
                    Tag.TAG_COMPOUND
            );

            for (Tag rawTag : transactionsTag) {

                CompoundTag txTag = (CompoundTag) rawTag;

                UUID from = txTag.getUUID("From");
                UUID to = txTag.getUUID("To");
                long amount = txTag.getLong("Amount");
                TransactionType type = TransactionType.valueOf(
                        txTag.getString("Type"));

                long epochSeconds = txTag.getLong("Timestamp");

                TransactionRecord record =
                        new TransactionRecord(
                                from, to, amount, type,
                                LocalDateTime.ofEpochSecond(
                                        epochSeconds,
                                        0,
                                        ZoneOffset.UTC
                                )
                        );

                AccountManager.addTransactionRecord(record);
            }
        }

        // ---- 加载成交历史 ----
        if (tag.contains("Trades")) {

            MarketManager.clearTradeHistory();

            ListTag tradesTag = tag.getList(
                    "Trades",
                    Tag.TAG_COMPOUND
            );

            for (Tag rawTag : tradesTag) {

                CompoundTag tradeTag = (CompoundTag) rawTag;

                UUID buyer = tradeTag.getUUID("Buyer");
                UUID seller = tradeTag.getUUID("Seller");
                String commodityId =
                        tradeTag.getString("CommodityId");
                long price = tradeTag.getLong("Price");
                int quantity = tradeTag.getInt("Quantity");

                long epochSeconds =
                        tradeTag.getLong("Timestamp");

                Trade trade = new Trade(
                        buyer, seller, commodityId,
                        price, quantity,
                        LocalDateTime.ofEpochSecond(
                                epochSeconds,
                                0,
                                ZoneOffset.UTC
                        )
                );

                MarketManager.addTradeToHistory(trade);
            }
        }

        // ---- 加载活跃订单 ----
        if (tag.contains("Orders")) {

            MarketManager.clearOrders();

            ListTag ordersTag = tag.getList(
                    "Orders",
                    Tag.TAG_COMPOUND
            );

            for (Tag rawTag : ordersTag) {

                CompoundTag orderTag =
                        (CompoundTag) rawTag;

                UUID playerUUID =
                        orderTag.getUUID("PlayerUUID");

                String commodityId =
                        orderTag.getString("CommodityId");

                OrderType type = OrderType.valueOf(
                        orderTag.getString("Type")
                );

                long price = orderTag.getLong("Price");
                int quantity = orderTag.getInt("Quantity");

                long epochSeconds =
                        orderTag.getLong("Timestamp");

                Order order = new Order(
                        playerUUID,
                        commodityId,
                        type,
                        price,
                        quantity,
                        LocalDateTime.ofEpochSecond(
                                epochSeconds,
                                0,
                                ZoneOffset.UTC
                        )
                );

                // 使用 addOrderDirect 跳过资产冻结
                // （资产在上次运行时已冻结）
                MarketManager.addOrderDirect(order);
            }
        }

        // ---- 加载 NPC 市场价格 ----
        if (tag.contains("MarketPrices")) {

            NpcMarketMaker.clearMarketPrices();

            ListTag pricesTag = tag.getList(
                    "MarketPrices",
                    Tag.TAG_COMPOUND
            );

            for (Tag rawTag : pricesTag) {

                CompoundTag priceTag = (CompoundTag) rawTag;

                String commodityId =
                        priceTag.getString("CommodityId");

                // 跳过已从注册表中移除的商品
                Commodity commodity =
                        CommodityRegistry.getCommodity(commodityId);
                if (commodity == null) {
                    continue;
                }

                long midPrice = priceTag.getLong("MidPrice");
                double spread = priceTag.getDouble("Spread");

                // basePrice 始终从 CommodityRegistry 取最新值
                long basePrice = commodity.getBasePrice();

                MarketPrice mp = new MarketPrice(
                        commodityId, basePrice, spread
                );
                mp.setMidPrice(midPrice);

                // 恢复 24h 统计
                if (priceTag.contains("DayOpen")) {
                    mp.setDayOpen(priceTag.getLong("DayOpen"));
                }

                NpcMarketMaker.putMarketPrice(commodityId, mp);
            }
        }

        // ---- 加载事件状态 ----
        if (tag.contains("TimerMinor")) {
            EventManager.setTimers(
                    tag.getInt("TimerMinor"),
                    tag.getInt("TimerMajor"),
                    tag.getInt("TimerBlackSwan"));
        }

        if (tag.contains("ActiveEvents")) {
            EventManager.clearActiveEvents();
            ListTag eventsTag = tag.getList("ActiveEvents", Tag.TAG_COMPOUND);
            for (Tag rawTag : eventsTag) {
                CompoundTag evTag = (CompoundTag) rawTag;
                String name = evTag.getString("Name");
                String description = evTag.getString("Description");
                EventTier tier = EventTier.valueOf(evTag.getString("Tier"));
                String commodityId = evTag.contains("CommodityId")
                        ? evTag.getString("CommodityId") : null;
                double multiplier = evTag.getDouble("PriceMultiplier");
                int totalTicks = evTag.getInt("TotalTicks");
                int remainingTicks = evTag.getInt("RemainingTicks");

                MarketEvent ev = new MarketEvent(name, description, tier,
                        commodityId, multiplier, totalTicks, remainingTicks);
                EventManager.addActiveEventDirect(ev);

                // 重新应用到价格
                if (ev.affectsAll()) {
                    NpcMarketMaker.applyEventToAll(ev);
                } else {
                    NpcMarketMaker.applyEvent(ev.getCommodityId(), ev);
                }
            }
        }

        // 快照不跨 session 恢复，每次服务器启动从零开始
        // seedNpcIfNeeded() 会调用 resetDayStats() 初始化 24h 统计

        return data;
    }

    // ================================================================
    // 实例管理
    // ================================================================

    /** 获取或创建 EconomySavedData 实例（服务器启动时调用） */
    public static EconomySavedData get(MinecraftServer server) {

        DimensionDataStorage storage =
                server.overworld().getDataStorage();

        INSTANCE = storage.computeIfAbsent(
                EconomySavedData::load,
                EconomySavedData::new,
                DATA_NAME
        );

        return INSTANCE;
    }

    private static EconomySavedData INSTANCE;

    /** 标记数据已修改，下次存档时写入磁盘 */
    public static void markDirty() {
        if (INSTANCE != null) {
            INSTANCE.setDirty();
        }
    }
}
