package finance.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import finance.event.EventTier;
import finance.event.MarketEvent;
import finance.market.MarketManager;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.market.Order;
import finance.market.OrderType;
import finance.market.Trade;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import finance.commodity.CommodityInventoryManager;
import finance.account.AccountManager;
import finance.event.EventManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.time.format.DateTimeFormatter;

/**
 * /market 命令 —— 市场交易系统入口。
 *
 * <h3>子命令</h3>
 * <ul>
 *   <li>/market orders —— 查看所有未成交订单</li>
 *   <li>/market buy ＜commodity＞ ＜price＞ ＜quantity＞ —— 挂买单</li>
 *   <li>/market sell ＜commodity＞ ＜price＞ ＜quantity＞ —— 挂卖单</li>
 *   <li>/market cancel ＜index＞ —— 取消指定订单</li>
 *   <li>/market history —— 查看最近 20 条成交历史</li>
 *   <li>/market npc sell ＜commodity＞ ＜quantity＞ —— 卖给 NPC</li>
 *   <li>/market npc buy ＜commodity＞ ＜quantity＞ —— 从 NPC 购买</li>
 *   <li>/market npc prices —— 查看 NPC 报价</li>
 *   <li>/market price —— 查看所有商品行情（价格 + 涨跌幅 + 成交量）</li>
 *   <li>/market price ＜commodity＞ —— 查看单个商品详情</li>
 * </ul>
 */
public class MarketCommand {

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {

        dispatcher.register(
                Commands.literal("market")

                        // ================================================
                        // /market orders —— 查看订单簿
                        // ================================================
                        .then(
                                Commands.literal("orders")
                                        .executes(context -> {

                                            ServerPlayer player =
                                                    context.getSource()
                                                            .getPlayerOrException();

                                            List<Order> orders =
                                                    MarketManager.getOrders();

                                            if (orders.isEmpty()) {

                                                player.sendSystemMessage(
                                                        Component.literal(
                                                                "No open orders."
                                                        )
                                                );

                                                return 1;
                                            }

                                            player.sendSystemMessage(
                                                    Component.literal(
                                                            "=== Open Orders ==="
                                                    )
                                            );

                                            for (int i = 0;
                                                 i < orders.size();
                                                 i++) {

                                                Order order =
                                                        orders.get(i);

                                                ServerPlayer ownerPlayer =
                                                        context.getSource()
                                                                .getServer()
                                                                .getPlayerList()
                                                                .getPlayer(
                                                                        order.getPlayerId()
                                                                );

                                                // 在线玩家显示名字，离线玩家显示 UUID 前 8 位
                                                String owner =
                                                        ownerPlayer != null
                                                                ? ownerPlayer.getName()
                                                                        .getString()
                                                                : order.getPlayerId()
                                                                        .toString()
                                                                        .substring(0, 8);

                                                player.sendSystemMessage(
                                                        Component.literal(
                                                                "#" + i
                                                                        + " ["
                                                                        + order.getType()
                                                                        + "] "
                                                                        + order.getCommodityId()
                                                                        + " x"
                                                                        + order.getQuantity()
                                                                        + " @"
                                                                        + order.getPrice()
                                                                        + " by "
                                                                        + owner
                                                        )
                                                );
                                            }

                                            return 1;
                                        })
                        )

                        // ================================================
                        // /market buy ＜commodity＞ ＜price＞ ＜quantity＞
                        // ================================================
                        .then(
                                Commands.literal("buy")

                                        .then(
                                                Commands.argument(
                                                                "commodity",
                                                                StringArgumentType.word()
                                                        )

                                                        .then(
                                                                Commands.argument(
                                                                                "price",
                                                                                LongArgumentType.longArg(1)
                                                                        )

                                                                        .then(
                                                                                Commands.argument(
                                                                                                "quantity",
                                                                                                IntegerArgumentType.integer(1)
                                                                                        )

                                                                                        .executes(context -> {

                                                                                            ServerPlayer player =
                                                                                                    context.getSource()
                                                                                                            .getPlayerOrException();

                                                                                            String commodity =
                                                                                                    StringArgumentType.getString(
                                                                                                            context,
                                                                                                            "commodity"
                                                                                                    );

                                                                                            long price =
                                                                                                    LongArgumentType.getLong(
                                                                                                            context,
                                                                                                            "price"
                                                                                                    );

                                                                                            int quantity =
                                                                                                    IntegerArgumentType.getInteger(
                                                                                                            context,
                                                                                                            "quantity"
                                                                                                    );

                                                                                            // 下单前检查余额是否足够
                                                                                            long totalCost =
                                                                                                    price * quantity;

                                                                                            long balance =
                                                                                                    AccountManager.getBalance(
                                                                                                            player.getUUID()
                                                                                                    );

                                                                                            if (balance < totalCost) {

                                                                                                player.sendSystemMessage(
                                                                                                        Component.literal(
                                                                                                                "Not enough balance. "
                                                                                                                        + "Need: " + totalCost
                                                                                                                        + " Have: " + balance
                                                                                                        )
                                                                                                );

                                                                                                return 0;
                                                                                            }

                                                                                            Order order =
                                                                                                    new Order(
                                                                                                            player.getUUID(),
                                                                                                            commodity,
                                                                                                            OrderType.BUY,
                                                                                                            price,
                                                                                                            quantity
                                                                                                    );

                                                                                            MarketManager.placeOrder(order);

                                                                                            player.sendSystemMessage(
                                                                                                    Component.literal(
                                                                                                            "Buy order placed: "
                                                                                                                    + quantity
                                                                                                                    + "x "
                                                                                                                    + commodity
                                                                                                                    + " @"
                                                                                                                    + price
                                                                                                    )
                                                                                            );

                                                                                            return 1;
                                                                                        })
                                                                        )
                                                        )
                                        )
                        )

                        // ================================================
                        // /market sell ＜commodity＞ ＜price＞ ＜quantity＞
                        // ================================================
                        .then(
                                Commands.literal("sell")

                                        .then(
                                                Commands.argument(
                                                                "commodity",
                                                                StringArgumentType.word()
                                                        )

                                                        .then(
                                                                Commands.argument(
                                                                                "price",
                                                                                LongArgumentType.longArg(1)
                                                                        )

                                                                        .then(
                                                                                Commands.argument(
                                                                                                "quantity",
                                                                                                IntegerArgumentType.integer(1)
                                                                                        )

                                                                                        .executes(context -> {

                                                                                            ServerPlayer player =
                                                                                                    context.getSource()
                                                                                                            .getPlayerOrException();

                                                                                            String commodity =
                                                                                                    StringArgumentType.getString(
                                                                                                            context,
                                                                                                            "commodity"
                                                                                                    );

                                                                                            long price =
                                                                                                    LongArgumentType.getLong(
                                                                                                            context,
                                                                                                            "price"
                                                                                                    );

                                                                                            int quantity =
                                                                                                    IntegerArgumentType.getInteger(
                                                                                                            context,
                                                                                                            "quantity"
                                                                                                    );

                                                                                            // 下单前检查库存是否足够
                                                                                            int owned =
                                                                                                    CommodityInventoryManager
                                                                                                            .getCommodityAmount(
                                                                                                                    player.getUUID(),
                                                                                                                    commodity
                                                                                                            );

                                                                                            if (owned < quantity) {

                                                                                                player.sendSystemMessage(
                                                                                                        Component.literal(
                                                                                                                "Not enough commodity. "
                                                                                                                        + "Have: " + owned
                                                                                                                        + " Need: " + quantity
                                                                                                        )
                                                                                                );

                                                                                                return 0;
                                                                                            }

                                                                                            Order order =
                                                                                                    new Order(
                                                                                                            player.getUUID(),
                                                                                                            commodity,
                                                                                                            OrderType.SELL,
                                                                                                            price,
                                                                                                            quantity
                                                                                                    );

                                                                                            MarketManager.placeOrder(order);

                                                                                            player.sendSystemMessage(
                                                                                                    Component.literal(
                                                                                                            "Sell order placed: "
                                                                                                                    + quantity
                                                                                                                    + "x "
                                                                                                                    + commodity
                                                                                                                    + " @"
                                                                                                                    + price
                                                                                                    )
                                                                                            );

                                                                                            return 1;
                                                                                        })
                                                                        )
                                                        )
                                        )
                        )

                        // ================================================
                        // /market cancel ＜index＞ —— 按索引取消订单
                        // ================================================
                        .then(
                                Commands.literal("cancel")

                                        .then(
                                                Commands.argument(
                                                                "index",
                                                                IntegerArgumentType.integer(0)
                                                        )

                                                        .executes(context -> {

                                                            ServerPlayer player =
                                                                    context.getSource()
                                                                            .getPlayerOrException();

                                                            int index =
                                                                    IntegerArgumentType.getInteger(
                                                                            context,
                                                                            "index"
                                                                    );

                                                            boolean success =
                                                                    MarketManager.cancelOrder(
                                                                            index,
                                                                            player.getUUID()
                                                                    );

                                                            if (!success) {

                                                                player.sendSystemMessage(
                                                                        Component.literal(
                                                                                "Cancel failed. "
                                                                                        + "Check the index "
                                                                                        + "or that it is your order."
                                                                        )
                                                                );

                                                                return 0;
                                                            }

                                                            player.sendSystemMessage(
                                                                    Component.literal(
                                                                            "Order #"
                                                                                    + index
                                                                                    + " cancelled."
                                                                    )
                                                            );

                                                            return 1;
                                                        })
                                        )
                        )

                        // ================================================
                        // /market history —— 查看最近 20 条成交记录
                        // ================================================
                        .then(
                                Commands.literal("history")
                                        .executes(context -> {

                                            ServerPlayer player =
                                                    context.getSource()
                                                            .getPlayerOrException();

                                            List<Trade> trades =
                                                    MarketManager.getTradeHistory();

                                            if (trades.isEmpty()) {

                                                player.sendSystemMessage(
                                                        Component.literal(
                                                                "No trade history."
                                                        )
                                                );

                                                return 1;
                                            }

                                            player.sendSystemMessage(
                                                    Component.literal(
                                                            "=== Trade History ==="
                                                    )
                                            );

                                            // 只显示最近 20 条
                                            int start = Math.max(
                                                    0,
                                                    trades.size() - 20
                                            );

                                            for (int i = start;
                                                 i < trades.size();
                                                 i++) {

                                                Trade trade =
                                                        trades.get(i);

                                                ServerPlayer buyerPlayer =
                                                        context.getSource()
                                                                .getServer()
                                                                .getPlayerList()
                                                                .getPlayer(
                                                                        trade.getBuyer()
                                                                );

                                                ServerPlayer sellerPlayer =
                                                        context.getSource()
                                                                .getServer()
                                                                .getPlayerList()
                                                                .getPlayer(
                                                                        trade.getSeller()
                                                                );

                                                String buyerName =
                                                        buyerPlayer != null
                                                                ? buyerPlayer.getName()
                                                                        .getString()
                                                                : trade.getBuyer()
                                                                        .toString()
                                                                        .substring(0, 8);

                                                String sellerName =
                                                        sellerPlayer != null
                                                                ? sellerPlayer.getName()
                                                                        .getString()
                                                                : trade.getSeller()
                                                                        .toString()
                                                                        .substring(0, 8);

                                                player.sendSystemMessage(
                                                        Component.literal(
                                                                trade.getCommodityId()
                                                                        + " x"
                                                                        + trade.getQuantity()
                                                                        + " @"
                                                                        + trade.getPrice()
                                                                        + " | "
                                                                        + buyerName
                                                                        + " ← "
                                                                        + sellerName
                                                        )
                                                );
                                            }

                                            return 1;
                                        })
                                        // /market history <commodity> —— 查看价格历史
                                        .then(
                                                Commands.argument("commodity", StringArgumentType.word())
                                                        .executes(context -> {
                                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                                            String commodity = StringArgumentType.getString(context, "commodity");
                                                            MarketPrice mp = NpcMarketMaker.getMarketPrice(commodity);
                                                            if (mp == null) {
                                                                player.sendSystemMessage(Component.literal("Unknown commodity: '" + commodity + "'."));
                                                                return 0;
                                                            }
                                                            List<MarketPrice.PriceSnapshot> snaps = mp.getSnapshots();
                                                            if (snaps.isEmpty()) {
                                                                player.sendSystemMessage(Component.literal("No price history for " + commodity + "."));
                                                                return 1;
                                                            }
                                                            int start = Math.max(0, snaps.size() - 20);
                                                            player.sendSystemMessage(Component.literal("=== " + commodity + " Price History ==="));
                                                            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm");
                                                            for (int i = start; i < snaps.size(); i++) {
                                                                MarketPrice.PriceSnapshot snap = snaps.get(i);
                                                                String t = snap.getTimestamp().format(fmt);
                                                                player.sendSystemMessage(Component.literal(
                                                                        t + "  " + snap.getPrice() + "  x" + snap.getVolume()));
                                                            }
                                                            return 1;
                                                        })
                                        )
                        )

                        // ================================================
                        // /market npc —— NPC 做市商交易
                        // ================================================
                        .then(
                                Commands.literal("npc")

                                        // /market npc sell <commodity> <quantity>
                                        .then(
                                                Commands.literal("sell")

                                                        .then(
                                                                Commands.argument(
                                                                                "commodity",
                                                                                StringArgumentType.word()
                                                                        )

                                                                        .then(
                                                                                Commands.argument(
                                                                                                "quantity",
                                                                                                IntegerArgumentType.integer(1)
                                                                                        )

                                                                                        .executes(context -> {

                                                                                            NpcTradeContext ctx = resolveNpcTrade(context);
                                                                                            if (ctx == null) return 0;

                                                                                            // 检查玩家库存
                                                                                            int owned =
                                                                                                    CommodityInventoryManager
                                                                                                            .getCommodityAmount(
                                                                                                                    ctx.player().getUUID(),
                                                                                                                    ctx.commodity()
                                                                                                            );

                                                                                            if (owned < ctx.quantity()) {

                                                                                                long bid = ctx.price().getBidPrice();

                                                                                                ctx.player().sendSystemMessage(
                                                                                                        Component.literal(
                                                                                                                "Not enough "
                                                                                                                        + ctx.commodity()
                                                                                                                        + ". Have: " + owned
                                                                                                                        + " Need: " + ctx.quantity()
                                                                                                                        + " (NPC buys @"
                                                                                                                        + bid + ")"
                                                                                                        )
                                                                                                );

                                                                                                return 0;
                                                                                            }

                                                                                            boolean success =
                                                                                                    NpcMarketMaker.npcBuy(
                                                                                                            ctx.player().getUUID(),
                                                                                                            ctx.commodity(),
                                                                                                            ctx.quantity()
                                                                                                    );

                                                                                            if (!success) {

                                                                                                ctx.player().sendSystemMessage(
                                                                                                        Component.literal(
                                                                                                                "NPC cannot buy right now."
                                                                                                        )
                                                                                                );

                                                                                                return 0;
                                                                                            }

                                                                                            long bidPrice = ctx.price().getBidPrice();
                                                                                            long received = bidPrice * ctx.quantity();

                                                                                            ctx.player().sendSystemMessage(
                                                                                                    Component.literal(
                                                                                                            "Sold "
                                                                                                                    + ctx.quantity() + "x "
                                                                                                                    + ctx.commodity()
                                                                                                                    + " to NPC @"
                                                                                                                    + bidPrice
                                                                                                                    + " each. Received: "
                                                                                                                    + received
                                                                                                    )
                                                                                            );

                                                                                            return 1;
                                                                                        })
                                                                        )
                                                        )
                                        )

                                        // /market npc buy <commodity> <quantity>
                                        .then(
                                                Commands.literal("buy")

                                                        .then(
                                                                Commands.argument(
                                                                                "commodity",
                                                                                StringArgumentType.word()
                                                                        )

                                                                        .then(
                                                                                Commands.argument(
                                                                                                "quantity",
                                                                                                IntegerArgumentType.integer(1)
                                                                                        )

                                                                                        .executes(context -> {

                                                                                            NpcTradeContext ctx = resolveNpcTrade(context);
                                                                                            if (ctx == null) return 0;

                                                                                            // 检查 NPC 库存
                                                                                            int npcStock =
                                                                                                    CommodityInventoryManager
                                                                                                            .getCommodityAmount(
                                                                                                                    NpcMarketMaker.NPC_UUID,
                                                                                                                    ctx.commodity()
                                                                                                            );

                                                                                            if (npcStock < ctx.quantity()) {

                                                                                                ctx.player().sendSystemMessage(
                                                                                                        Component.literal(
                                                                                                                "NPC doesn't have enough "
                                                                                                                        + ctx.commodity()
                                                                                                                        + ". Available: "
                                                                                                                        + npcStock
                                                                                                        )
                                                                                                );

                                                                                                return 0;
                                                                                            }

                                                                                            // 检查玩家余额
                                                                                            long askPrice = ctx.price().getAskPrice();
                                                                                            long totalCost = askPrice * ctx.quantity();

                                                                                            long balance =
                                                                                                    AccountManager.getBalance(
                                                                                                            ctx.player().getUUID()
                                                                                                    );

                                                                                            if (balance < totalCost) {

                                                                                                ctx.player().sendSystemMessage(
                                                                                                        Component.literal(
                                                                                                                "Not enough balance. "
                                                                                                                        + "Need: " + totalCost
                                                                                                                        + " Have: " + balance
                                                                                                                        + " (NPC sells @"
                                                                                                                        + askPrice + ")"
                                                                                                        )
                                                                                                );

                                                                                                return 0;
                                                                                            }

                                                                                            boolean success =
                                                                                                    NpcMarketMaker.npcSell(
                                                                                                            ctx.player().getUUID(),
                                                                                                            ctx.commodity(),
                                                                                                            ctx.quantity()
                                                                                                    );

                                                                                            if (!success) {

                                                                                                ctx.player().sendSystemMessage(
                                                                                                        Component.literal(
                                                                                                                "NPC cannot sell right now."
                                                                                                        )
                                                                                                );

                                                                                                return 0;
                                                                                            }

                                                                                            ctx.player().sendSystemMessage(
                                                                                                    Component.literal(
                                                                                                            "Bought "
                                                                                                                    + ctx.quantity() + "x "
                                                                                                                    + ctx.commodity()
                                                                                                                    + " from NPC @"
                                                                                                                    + askPrice
                                                                                                                    + " each. Paid: "
                                                                                                                    + totalCost
                                                                                                    )
                                                                                            );

                                                                                            return 1;
                                                                                        })
                                                                        )
                                                        )
                                        )

                                        // /market npc prices —— 查看所有 NPC 报价
                                        .then(
                                                Commands.literal("prices")
                                                        .executes(context -> {

                                                            ServerPlayer player =
                                                                    context.getSource()
                                                                            .getPlayerOrException();

                                                            Collection<MarketPrice> allPrices =
                                                                    NpcMarketMaker.getAllMarketPrices()
                                                                            .values();

                                                            if (allPrices.isEmpty()) {

                                                                player.sendSystemMessage(
                                                                        Component.literal(
                                                                                "No NPC prices available."
                                                                        )
                                                                );

                                                                return 1;
                                                            }

                                                            player.sendSystemMessage(
                                                                    Component.literal(
                                                                            "=== NPC Market Prices ==="
                                                                    )
                                                            );

                                                            for (MarketPrice mp : allPrices) {

                                                                long bid = mp.getBidPrice();
                                                                long ask = mp.getAskPrice();

                                                                player.sendSystemMessage(
                                                                        Component.literal(
                                                                                mp.getCommodityId()
                                                                                        + "  Buy: " + bid
                                                                                        + "  Sell: " + ask
                                                                        )
                                                                );
                                                            }

                                                            return 1;
                                                        })
                                        )
                        )

                        // ================================================
                        // /market top —— 涨幅排行
                        // ================================================
                        .then(
                                Commands.literal("top")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            Collection<MarketPrice> all = NpcMarketMaker.getAllMarketPrices().values();
                                            if (all.isEmpty()) {
                                                player.sendSystemMessage(Component.literal("No market data."));
                                                return 1;
                                            }
                                            List<MarketPrice> sorted = new ArrayList<>(all);
                                            sorted.sort((a, b) -> Double.compare(b.getDayChange(), a.getDayChange()));
                                            player.sendSystemMessage(Component.literal("=== TOP GAINERS ==="));
                                            int count = 0;
                                            for (MarketPrice mp : sorted) {
                                                if (count >= 5) break;
                                                double ch = mp.getDayChange();
                                                if (ch <= 0) break;
                                                player.sendSystemMessage(Component.literal(
                                                        (count + 1) + ". " + mp.getCommodityId()
                                                                + "  " + mp.getMidPrice()
                                                                + "  " + formatDayChange(ch)
                                                                + "  " + formatMomentum(mp.getTradeMomentum())));
                                                count++;
                                            }
                                            if (count == 0) {
                                                player.sendSystemMessage(Component.literal("(none up today)"));
                                            }
                                            return 1;
                                        })
                        )

                        // ================================================
                        // /market losers —— 跌幅排行
                        // ================================================
                        .then(
                                Commands.literal("losers")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            Collection<MarketPrice> all = NpcMarketMaker.getAllMarketPrices().values();
                                            if (all.isEmpty()) {
                                                player.sendSystemMessage(Component.literal("No market data."));
                                                return 1;
                                            }
                                            List<MarketPrice> sorted = new ArrayList<>(all);
                                            sorted.sort((a, b) -> Double.compare(a.getDayChange(), b.getDayChange()));
                                            player.sendSystemMessage(Component.literal("=== TOP LOSERS ==="));
                                            int count = 0;
                                            for (MarketPrice mp : sorted) {
                                                if (count >= 5) break;
                                                double ch = mp.getDayChange();
                                                if (ch >= 0) break;
                                                player.sendSystemMessage(Component.literal(
                                                        (count + 1) + ". " + mp.getCommodityId()
                                                                + "  " + mp.getMidPrice()
                                                                + "  " + formatDayChange(ch)
                                                                + "  " + formatMomentum(mp.getTradeMomentum())));
                                                count++;
                                            }
                                            if (count == 0) {
                                                player.sendSystemMessage(Component.literal("(none down today)"));
                                            }
                                            return 1;
                                        })
                        )

                        // ================================================
                        // /market price —— 行情查询
                        // ================================================
                        .then(
                                Commands.literal("price")

                                        // /market price —— 所有商品概览
                                        .executes(context -> {

                                            ServerPlayer player =
                                                    context.getSource()
                                                            .getPlayerOrException();

                                            Collection<MarketPrice> allPrices =
                                                    NpcMarketMaker.getAllMarketPrices()
                                                            .values();

                                            if (allPrices.isEmpty()) {

                                                player.sendSystemMessage(
                                                        Component.literal(
                                                                "No market data available."
                                                        )
                                                );

                                                return 1;
                                            }

                                            player.sendSystemMessage(
                                                    Component.literal(
                                                            "=== Market Prices ==="
                                                    )
                                            );

                                            for (MarketPrice mp : allPrices) {

                                                long bid = mp.getBidPrice();
                                                long ask = mp.getAskPrice();
                                                double change = mp.getDayChange();
                                                int vol = mp.getDayVolume();

                                                String changeStr = " " + formatDayChange(change);
                                                String eventMark = mp.hasActiveEvent()
                                                        ? " [事件:" + mp.getActiveEvent().getName() + "]"
                                                        : "";

                                                player.sendSystemMessage(
                                                        Component.literal(
                                                                mp.getCommodityId()
                                                                        + "  " + mp.getMidPrice()
                                                                        + changeStr
                                                                        + " " + formatMomentum(mp.getTradeMomentum())
                                                                        + eventMark
                                                                        + "  Buy:" + bid
                                                                        + "  Sell:" + ask
                                                                        + "  Vol:" + vol
                                                        )
                                                );
                                            }

                                            return 1;
                                        })

                                        // /market price <commodity> —— 单个商品详情
                                        .then(
                                                Commands.argument(
                                                                "commodity",
                                                                StringArgumentType.word()
                                                        )

                                                        .executes(context -> {

                                                            ServerPlayer player =
                                                                    context.getSource()
                                                                            .getPlayerOrException();

                                                            String commodity =
                                                                    StringArgumentType.getString(
                                                                            context,
                                                                            "commodity"
                                                                    );

                                                            MarketPrice mp =
                                                                    NpcMarketMaker.getMarketPrice(commodity);

                                                            if (mp == null) {

                                                                player.sendSystemMessage(
                                                                        Component.literal(
                                                                                "Unknown commodity: '"
                                                                                        + commodity + "'."
                                                                        )
                                                                );

                                                                return 0;
                                                            }

                                                            long bid = mp.getBidPrice();
                                                            long ask = mp.getAskPrice();
                                                            double change = mp.getDayChange();

                                                            String changeStr = formatDayChange(change);

                                                            player.sendSystemMessage(
                                                                    Component.literal(
                                                                            "=== " + commodity + " ==="
                                                                    )
                                                            );

                                                            player.sendSystemMessage(
                                                                    Component.literal(
                                                                            "Price: " + mp.getMidPrice()
                                                                                    + "  Bid: " + bid
                                                                                    + "  Ask: " + ask
                                                                    )
                                                            );

                                                            player.sendSystemMessage(
                                                                    Component.literal(
                                                                            "24h High: " + mp.getDayHigh()
                                                                                    + "  Low: " + mp.getDayLow()
                                                                                    + "  Volume: " + mp.getDayVolume()
                                                                    )
                                                            );

                                                            player.sendSystemMessage(
                                                                    Component.literal(
                                                                            "Change: " + changeStr
                                                                                    + "  " + formatMomentum(mp.getTradeMomentum())
                                                                    )
                                                            );

                                                            int npcStock =
                                                                    CommodityInventoryManager
                                                                            .getCommodityAmount(
                                                                                    NpcMarketMaker.NPC_UUID,
                                                                                    commodity
                                                                            );

                                                            player.sendSystemMessage(
                                                                    Component.literal(
                                                                            "NPC Stock: " + npcStock
                                                                                    + "  (Ref: " + MarketPrice.REFERENCE_STOCK + ")"
                                                                    )
                                                            );

                                                            if (mp.hasActiveEvent()) {
                                                                MarketEvent ev = mp.getActiveEvent();
                                                                String remain = ev.getRemainingDesc();
                                                                player.sendSystemMessage(
                                                                        Component.literal(
                                                                                "[事件] " + ev.getName()
                                                                                        + " " + ev.getChangePct()
                                                                                        + " 剩余:" + remain
                                                                        )
                                                                );
                                                            }

                                                            return 1;
                                                        })
                                        )
                        )

                        // ================================================
                        // /market overview —— 市场概览（全部商品一行一个）
                        // ================================================
                        .then(
                                Commands.literal("overview")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            Collection<MarketPrice> all = NpcMarketMaker.getAllMarketPrices().values();
                                            if (all.isEmpty()) {
                                                player.sendSystemMessage(Component.literal("No market data."));
                                                return 1;
                                            }
                                            player.sendSystemMessage(Component.literal("=== MARKET OVERVIEW ==="));
                                            for (MarketPrice mp : all) {
                                                String changeStr = formatDayChange(mp.getDayChange());
                                                player.sendSystemMessage(Component.literal(
                                                        mp.getCommodityId()
                                                                + "  " + mp.getMidPrice()
                                                                + "  " + changeStr
                                                                + "  " + formatMomentum(mp.getTradeMomentum())));
                                            }
                                            return 1;
                                        })
                        )

                        // ================================================
                        // /market debug nextday —— 手动推进一个 MC 天（测试用）
                        // ================================================
                        .then(
                                Commands.literal("debug")
                                        .requires(source -> source.hasPermission(2))
                                        .then(
                                                Commands.literal("nextday")
                                                        .executes(context -> {
                                                            EventManager.onDayTick(
                                                                    context.getSource().getServer());
                                                            context.getSource().sendSuccess(
                                                                    () -> Component.literal(
                                                                            "Done. " + EventManager.getTimerSummary()
                                                                                    + " Active:" + EventManager.getActiveEvents().size()),
                                                                    true);
                                                            return 1;
                                                        })
                                        )
                                        .then(
                                                Commands.literal("pressure")
                                                        .executes(context -> {
                                                            context.getSource().sendSuccess(
                                                                    () -> Component.literal(
                                                                            EventManager.getTimerSummary()
                                                                                    + " Active:" + EventManager.getActiveEvents().size()),
                                                                    false);
                                                            return 1;
                                                        })
                                        )
                                        .then(
                                                Commands.literal("fire")
                                                        .then(Commands.literal("minor")
                                                                .executes(context -> {
                                                                    EventManager.fireTestEvent(EventTier.MINOR, context.getSource().getServer());
                                                                    return 1;
                                                                }))
                                                        .then(Commands.literal("major")
                                                                .executes(context -> {
                                                                    EventManager.fireTestEvent(EventTier.MAJOR, context.getSource().getServer());
                                                                    return 1;
                                                                }))
                                                        .then(Commands.literal("blackswan")
                                                                .executes(context -> {
                                                                    EventManager.fireTestEvent(EventTier.BLACK_SWAN, context.getSource().getServer());
                                                                    return 1;
                                                                }))
                                        )
                        )
        );
    }

    // ---- helpers ----

    private record NpcTradeContext(ServerPlayer player, String commodity, int quantity, MarketPrice price) {}

    private static NpcTradeContext resolveNpcTrade(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String commodity = StringArgumentType.getString(context, "commodity");
        int quantity = IntegerArgumentType.getInteger(context, "quantity");
        MarketPrice price = NpcMarketMaker.getMarketPrice(commodity);
        if (price == null) {
            player.sendSystemMessage(Component.literal("Unknown commodity: '" + commodity + "'."));
            return null;
        }
        return new NpcTradeContext(player, commodity, quantity, price);
    }

    private static String formatDayChange(double change) {
        if (change > 0) {
            return "+" + String.format("%.0f", change) + "%";
        } else if (change < 0) {
            return String.format("%.0f", change) + "%";
        }
        return "0%";
    }

    private static String formatMomentum(double momentum) {
        if (momentum > 0.005) return "↑ Bullish";
        if (momentum < -0.005) return "↓ Bearish";
        return "→ Neutral";
    }
}
