package finance.data;

import finance.account.Account;
import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.market.MarketManager;
import finance.market.Order;
import finance.market.OrderType;
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

public class EconomySavedData extends SavedData {

    public static final String DATA_NAME = "finance_data";

    @Override
    public CompoundTag save(CompoundTag tag) {

        // ---- Save account balances ----
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

        // ---- Save transaction records ----
        ListTag transactionsTag = new ListTag();

        List<TransactionRecord> allTxns =
                AccountManager.getTransactions();

        // Only persist the last 500 transactions
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
            txTag.putString("Type", record.getType());

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

        // ---- Save trade history ----
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

        // ---- Save open orders ----
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

        return tag;
    }

    public static EconomySavedData load(CompoundTag tag) {

        EconomySavedData data = new EconomySavedData();

        // ---- Load account balances ----
        ListTag accountsTag = tag.getList(
                "Accounts",
                Tag.TAG_COMPOUND
        );

        for (Tag rawTag : accountsTag) {

            CompoundTag accountTag = (CompoundTag) rawTag;

            UUID playerUUID = accountTag.getUUID("PlayerUUID");
            long balance = accountTag.getLong("Balance");

            Account account = AccountManager.getAccount(playerUUID);

            // Restore frozen balance alongside available balance.
            // The saved "Balance" is the available (unfrozen)
            // portion, so total = balance + frozen.
            if (accountTag.contains("FrozenBalance")) {
                long frozen =
                        accountTag.getLong("FrozenBalance");

                // Set total balance, then freeze the frozen portion
                account.setBalance(balance + frozen);

                if (frozen > 0) {
                    account.freezeFunds(frozen);
                }

            } else {
                account.setBalance(balance);
            }
        }

        // ---- Load transaction records ----
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
                String type = txTag.getString("Type");

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

        // ---- Load trade history ----
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

        // ---- Load open orders ----
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

                MarketManager.addOrderDirect(order);
            }
        }

        return data;
    }

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

    public static void markDirty() {
        if (INSTANCE != null) {
            INSTANCE.setDirty();
        }
    }
}
