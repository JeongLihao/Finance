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
import finance.company.CompanyManager;
import finance.commodity.CommodityRegistry;

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
                                                                "暂无挂单。"
                                                        )
                                                );

                                                return 1;
                                            }

                                            player.sendSystemMessage(
                                                    Component.literal(
                                                            "=== 当前挂单 ==="
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
                                                                        + " 挂单者: "
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

                                                                                            if (CommodityRegistry.getCommodity(commodity) == null) {
                                                                                                player.sendSystemMessage(
                                                                                                        Component.literal(
                                                                                                                "未知商品: '" + commodity + "'。"
                                                                                                        )
                                                                                                );

                                                                                                return 0;
                                                                                            }

                                                                                            // 下单前检查余额是否足够
                                                                                            long totalCost =
                                                                                                    multiplyPriceQuantity(price, quantity);

                                                                                            if (totalCost <= 0) {
                                                                                                player.sendSystemMessage(
                                                                                                        Component.literal(
                                                                                                                "订单金额过大，无法提交。"
                                                                                                        )
                                                                                                );

                                                                                                return 0;
                                                                                            }

                                                                                            long balance =
                                                                                                    AccountManager.getBalance(
                                                                                                            player.getUUID()
                                                                                                    );

                                                                                            if (balance < totalCost) {

                                                                                                player.sendSystemMessage(
                                                                                                        Component.literal(
                                                                                                                "余额不足，"
                                                                                                                        + "需要: " + totalCost
                                                                                                                        + " 拥有: " + balance
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

                                                                                            if (!MarketManager.placeOrder(order)) {
                                                                                                player.sendSystemMessage(
                                                                                                        Component.literal(
                                                                                                                "买单提交失败，请检查余额、商品和数量。"
                                                                                                        )
                                                                                                );

                                                                                                return 0;
                                                                                            }

                                                                                            player.sendSystemMessage(
                                                                                                    Component.literal(
                                                                                                            "买单已挂: "
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

                                                                                            if (CommodityRegistry.getCommodity(commodity) == null) {
                                                                                                player.sendSystemMessage(
                                                                                                        Component.literal(
                                                                                                                "未知商品: '" + commodity + "'。"
                                                                                                        )
                                                                                                );

                                                                                                return 0;
                                                                                            }

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
                                                                                                                "库存不足，商品: " + commodity + "。"
                                                                                                                        + "拥有: " + owned
                                                                                                                        + " 需要: " + quantity
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

                                                                                            if (!MarketManager.placeOrder(order)) {
                                                                                                player.sendSystemMessage(
                                                                                                        Component.literal(
                                                                                                                "卖单提交失败，请检查库存、商品和数量。"
                                                                                                        )
                                                                                                );

                                                                                                return 0;
                                                                                            }

                                                                                            player.sendSystemMessage(
                                                                                                    Component.literal(
                                                                                                            "卖单已挂: "
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
                                                                                "取消失败，请检查编号是否是你的订单。"
                                                                        )
                                                                );

                                                                return 0;
                                                            }

                                                            player.sendSystemMessage(
                                                                    Component.literal(
                                                                            "订单 #"
                                                                                    + index
                                                                                    + " 已取消。"
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
                                                                "暂无成交记录。"
                                                        )
                                                );

                                                return 1;
                                            }

                                            player.sendSystemMessage(
                                                    Component.literal(
                                                            "=== 成交记录 ==="
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
                                                                player.sendSystemMessage(Component.literal("未知商品: '" + commodity + "'."));
                                                                return 0;
                                                            }
                                                            List<MarketPrice.PriceSnapshot> snaps = mp.getSnapshots();
                                                            if (snaps.isEmpty()) {
                                                                player.sendSystemMessage(Component.literal("暂无价格历史: " + commodity + "."));
                                                                return 1;
                                                            }
                                                            int start = Math.max(0, snaps.size() - 20);
                                                            player.sendSystemMessage(Component.literal("=== " + commodity + " 价格历史 ==="));
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
                                                                                                                "库存不足，"
                                                                                                                        + ctx.commodity()
                                                                                                                        + ". 拥有: " + owned
                                                                                                                        + " 需要: " + ctx.quantity()
                                                                                                                        + " (NPC买入价: "
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
                                                                                                                "NPC 暂时无法买入。"
                                                                                                        )
                                                                                                );

                                                                                                return 0;
                                                                                            }

                                                                                            long bidPrice = ctx.price().getBidPrice();
                                                                                            long received = multiplyPriceQuantity(bidPrice, ctx.quantity());

                                                                                            ctx.player().sendSystemMessage(
                                                                                                    Component.literal(
                                                                                                            "已卖出 "
                                                                                                                    + ctx.quantity() + "x "
                                                                                                                    + ctx.commodity()
                                                                                                                    + " 给 NPC，单价: "
                                                                                                                    + bidPrice
                                                                                                                    + "  收入: "
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
                                                                                                                "NPC 库存不足: "
                                                                                                                        + ctx.commodity()
                                                                                                                        + "  可用: "
                                                                                                                        + npcStock
                                                                                                        )
                                                                                                );

                                                                                                return 0;
                                                                                            }

                                                                                            // 检查玩家余额
                                                                                            long askPrice = ctx.price().getAskPrice();
                                                                                            long totalCost = multiplyPriceQuantity(askPrice, ctx.quantity());

                                                                                            if (totalCost <= 0) {
                                                                                                ctx.player().sendSystemMessage(
                                                                                                        Component.literal(
                                                                                                                "交易金额过大，无法提交。"
                                                                                                        )
                                                                                                );

                                                                                                return 0;
                                                                                            }

                                                                                            long balance =
                                                                                                    AccountManager.getBalance(
                                                                                                            ctx.player().getUUID()
                                                                                                    );

                                                                                            if (balance < totalCost) {

                                                                                                ctx.player().sendSystemMessage(
                                                                                                        Component.literal(
                                                                                                                "余额不足，"
                                                                                                                        + "需要: " + totalCost
                                                                                                                        + " 拥有: " + balance
                                                                                                                        + " (NPC卖出价: "
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
                                                                                                                "NPC 暂时无法卖出。"
                                                                                                        )
                                                                                                );

                                                                                                return 0;
                                                                                            }

                                                                                            ctx.player().sendSystemMessage(
                                                                                                    Component.literal(
                                                                                                            "已买入 "
                                                                                                                    + ctx.quantity() + "x "
                                                                                                                    + ctx.commodity()
                                                                                                                    + " 从 NPC，单价: "
                                                                                                                    + askPrice
                                                                                                                    + "  支付: "
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
                                                                                "暂无 NPC 报价。"
                                                                        )
                                                                );

                                                                return 1;
                                                            }

                                                            player.sendSystemMessage(
                                                                    Component.literal(
                                                                            "=== NPC 报价 ==="
                                                                    )
                                                            );

                                                            for (MarketPrice mp : allPrices) {

                                                                long bid = mp.getBidPrice();
                                                                long ask = mp.getAskPrice();

                                                                player.sendSystemMessage(
                                                                        Component.literal(
                                                                                mp.getCommodityId()
                                                                                        + "  买入: " + bid
                                                                                        + "  卖出: " + ask
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
                                                player.sendSystemMessage(Component.literal("暂无行情数据。"));
                                                return 1;
                                            }
                                            List<MarketPrice> sorted = new ArrayList<>(all);
                                            sorted.sort((a, b) -> Double.compare(b.getDayChange(), a.getDayChange()));
                                            player.sendSystemMessage(Component.literal("=== 涨幅排行 ==="));
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
                                                player.sendSystemMessage(Component.literal("（今日无上涨）"));
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
                                                player.sendSystemMessage(Component.literal("暂无行情数据。"));
                                                return 1;
                                            }
                                            List<MarketPrice> sorted = new ArrayList<>(all);
                                            sorted.sort((a, b) -> Double.compare(a.getDayChange(), b.getDayChange()));
                                            player.sendSystemMessage(Component.literal("=== 跌幅排行 ==="));
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
                                                player.sendSystemMessage(Component.literal("（今日无下跌）"));
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
                                                                "暂无行情数据。"
                                                        )
                                                );

                                                return 1;
                                            }

                                            player.sendSystemMessage(
                                                    Component.literal(
                                                            "=== 商品行情 ==="
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
                                                                        + "  买入:" + bid
                                                                        + "  卖出:" + ask
                                                                        + "  量:" + vol
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
                                                                                "未知商品: '"
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
                                                                            "价格: " + mp.getMidPrice()
                                                                                    + "  买价: " + bid
                                                                                    + "  卖价: " + ask
                                                                    )
                                                            );

                                                            player.sendSystemMessage(
                                                                    Component.literal(
                                                                            "24h 最高: " + mp.getDayHigh()
                                                                                    + "  最低: " + mp.getDayLow()
                                                                                    + "  成交量: " + mp.getDayVolume()
                                                                    )
                                                            );

                                                            player.sendSystemMessage(
                                                                    Component.literal(
                                                                            "涨跌: " + changeStr
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
                                                                            "NPC 库存: " + npcStock
                                                                                    + "  (参考: " + MarketPrice.REFERENCE_STOCK + ")"
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
                                                player.sendSystemMessage(Component.literal("暂无行情数据。"));
                                                return 1;
                                            }
                                            player.sendSystemMessage(Component.literal("=== 市场概览 ==="));
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
                                                            CompanyManager.tickAll();
                                                            context.getSource().sendSuccess(
                                                                    () -> Component.literal(
                                                                            "完成。" + EventManager.getTimerSummary()
                                                                                    + " 活跃事件:" + EventManager.getActiveEvents().size()),
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
                                                                                    + " 活跃事件:" + EventManager.getActiveEvents().size()),
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
            player.sendSystemMessage(Component.literal("未知商品: '" + commodity + "'."));
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
        if (momentum > 0.005) return "↑ 看涨";
        if (momentum < -0.005) return "↓ 看跌";
        return "→ 持平";
    }

    private static long multiplyPriceQuantity(long price, int quantity) {
        try {
            return Math.multiplyExact(price, (long) quantity);
        } catch (ArithmeticException ex) {
            return -1;
        }
    }
}
