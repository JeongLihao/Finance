package finance.data.serializer;

import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.event.EventManager;
import finance.event.EventTier;
import finance.event.MarketEvent;
import finance.market.MarketManager;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.market.Order;
import finance.market.OrderType;
import finance.market.Trade;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Persists commodity definitions, market books, price history and active events. */
public final class MarketDataSerializer {

    private MarketDataSerializer() {
    }

    public static void save(CompoundTag tag) {
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

        ListTag ordersTag = new ListTag();

        for (Order order : MarketManager.getOrders()) {

            CompoundTag orderTag = new CompoundTag();

            orderTag.putUUID(
                    "OrderId",
                    order.getOrderId()
            );

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

            // 动量与噪音
            priceTag.putDouble("TradeMomentum", mp.getTradeMomentum());
            priceTag.putDouble("TrendMomentum", mp.getTrendMomentum());
            priceTag.putInt("NoiseOffset", mp.getNoiseOffset());

            pricesTag.add(priceTag);
        }

        tag.put("MarketPrices", pricesTag);

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

        ListTag commoditiesTag = new ListTag();
        for (Commodity commodity : CommodityRegistry.getAllCommodities()) {
            if (CommodityRegistry.isDefaultCommodity(commodity.getId())) {
                continue;
            }
            CompoundTag cTag = new CompoundTag();
            cTag.putString("Id", commodity.getId());
            cTag.putString("DisplayName", commodity.getDisplayName());
            cTag.putString("Category", commodity.getCategory().name());
            cTag.putLong("BasePrice", commodity.getBasePrice());
            if (commodity.getItemId() != null) {
                cTag.putString("ItemId", commodity.getItemId());
            }
            commoditiesTag.add(cTag);
        }
        tag.put("CommodityDefinitions", commoditiesTag);

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

    }

    public static void load(CompoundTag tag) {
        loadCommodityDefinitions(tag);
        loadMarketState(tag);
    }

    /**
     * Restores world-defined commodities before dependent domain objects, such as
     * company inventories, are restored.
     */
    public static void loadCommodityDefinitions(CompoundTag tag) {
        if (!tag.contains("CommodityDefinitions")) {
            return;
        }

        ListTag commoditiesTag = tag.getList("CommodityDefinitions", Tag.TAG_COMPOUND);
        for (Tag rawTag : commoditiesTag) {
            CompoundTag cTag = (CompoundTag) rawTag;
            String id = cTag.getString("Id");
            if (id.isBlank() || CommodityRegistry.isRegistered(id)) {
                continue;
            }

            String displayName = cTag.getString("DisplayName");
            CommodityCategory category = NbtDataSupport.safeEnum(
                    CommodityCategory.class,
                    cTag.getString("Category"),
                    CommodityCategory.MISCELLANEOUS);
            long basePrice = cTag.getLong("BasePrice");
            String itemId = cTag.contains("ItemId") ? cTag.getString("ItemId") : null;

            CommodityRegistry.register(new Commodity(id, itemId, displayName, category, basePrice));
        }
    }

    /** Restores commodity market state after all commodity-dependent data is available. */
    public static void loadMarketState(CompoundTag tag) {
        if (tag.contains("Trades")) {

            MarketManager.clearTradeHistory();

            ListTag tradesTag = tag.getList(
                    "Trades",
                    Tag.TAG_COMPOUND
            );

            for (Tag rawTag : tradesTag) {

                CompoundTag tradeTag = (CompoundTag) rawTag;

                UUID buyer = NbtDataSupport.readUuidOrNull(tradeTag, "Buyer");
                UUID seller = NbtDataSupport.readUuidOrNull(tradeTag, "Seller");
                if (buyer == null || seller == null) {
                    continue;
                }
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

                UUID orderId = NbtDataSupport.readUuidOrNull(orderTag, "OrderId");
                if (orderId == null) {
                    orderId = UUID.randomUUID();
                }

                UUID playerUUID = NbtDataSupport.readUuidOrNull(orderTag, "PlayerUUID");
                if (playerUUID == null) {
                    continue;
                }

                String commodityId =
                        orderTag.getString("CommodityId");

                OrderType type = NbtDataSupport.safeEnum(OrderType.class, orderTag.getString("Type"), null);
                if (type == null) {
                    continue;
                }

                long price = orderTag.getLong("Price");
                int quantity = orderTag.getInt("Quantity");

                long epochSeconds =
                        orderTag.getLong("Timestamp");

                Order order = new Order(
                        orderId,
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

        // ---- 加载系统公司 ----

        // ---- 加载国际市场价格 ----

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

                // 恢复动量与噪音（向后兼容：旧存档无此字段则保持默认 0）
                if (priceTag.contains("TradeMomentum")) {
                    mp.setTradeMomentum(priceTag.getDouble("TradeMomentum"));
                }
                if (priceTag.contains("TrendMomentum")) {
                    mp.setTrendMomentum(priceTag.getDouble("TrendMomentum"));
                }
                if (priceTag.contains("NoiseOffset")) {
                    mp.setNoiseOffset(priceTag.getInt("NoiseOffset"));
                }

                NpcMarketMaker.putMarketPrice(commodityId, mp);
            }
        }

        // ---- 加载价格快照 ----

        if (tag.contains("PriceSnapshots")) {
            ListTag snapshotsTag = tag.getList("PriceSnapshots", Tag.TAG_COMPOUND);
            for (Tag rawTag : snapshotsTag) {
                CompoundTag commoditySnapTag = (CompoundTag) rawTag;
                String commodityId = commoditySnapTag.getString("CommodityId");
                MarketPrice mp = NpcMarketMaker.getAllMarketPrices().get(commodityId);
                if (mp == null) continue;

                ListTag snapsList = commoditySnapTag.getList("Snapshots", Tag.TAG_COMPOUND);
                for (Tag snapRaw : snapsList) {
                    CompoundTag snapTag = (CompoundTag) snapRaw;
                    long epochSeconds = snapTag.getLong("Timestamp");
                    long price = snapTag.getLong("Price");
                    int volume = snapTag.getInt("Volume");
                    LocalDateTime ts = LocalDateTime.ofEpochSecond(epochSeconds, 0, ZoneOffset.UTC);
                    mp.addSnapshotDirect(new MarketPrice.PriceSnapshot(ts, price, volume));
                }
                mp.recomputeDayStats();
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
                EventTier tier = NbtDataSupport.safeEnum(EventTier.class, evTag.getString("Tier"), EventTier.MINOR);
                String commodityId = evTag.contains("CommodityId")
                        ? evTag.getString("CommodityId") : null;
                double multiplier = evTag.getDouble("PriceMultiplier");
                int totalTicks = evTag.getInt("TotalTicks");
                int remainingTicks = evTag.getInt("RemainingTicks");

                MarketEvent ev = new MarketEvent(name, description, tier,
                        commodityId, multiplier, totalTicks, remainingTicks);
                EventManager.addActiveEventDirect(ev);

                if (ev.affectsAll()) {
                    NpcMarketMaker.applyEventToAll(ev);
                } else {
                    NpcMarketMaker.applyEvent(ev.getCommodityId(), ev);
                }
            }
        }

        // ---- 加载股票 ----

    }
}
