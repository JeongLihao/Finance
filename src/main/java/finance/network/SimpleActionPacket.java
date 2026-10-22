package finance.network;

import finance.simple.SimpleCenterService;
import finance.simple.SimpleUnlocks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 简单模式金融中心的一键操作（客户端 → 服务端）。结果用 {@link GuiFeedbackPacket} 回给玩家并触发刷新。 */
public record SimpleActionPacket(Action action, String target, long amount, long price) {

    public enum Action {
        QUICK_SELL, QUICK_BUY, UPGRADE_FACTORY, QUICK_IPO, BANK_DEPOSIT, BANK_WITHDRAW,
        /** 挂单：target = 商品，amount = 数量，price = 单价。 */
        PLACE_SELL_ORDER, PLACE_BUY_ORDER,
        /** target = 订单 ID。 */
        CANCEL_ORDER,
        /** 把暂存在市场的货取回背包：target = 商品。 */
        TAKE_GOODS,
        /** 锁价合同：target = 合约 ID，amount = 份数。 */
        LOCK_SELL, LOCK_BUY, CLOSE_LOCK, WITHDRAW_MARGIN,
        /** 理财：target = 产品 key，amount = 金额；取回：target = 持有 key。 */
        BUY_PRODUCT, REDEEM_PRODUCT,
        /** 股票进阶：target = 股票代码，amount = 股数。 */
        LEVER_BUY, LEVER_REPAY, SHORT_SELL, SHORT_COVER, COLLATERAL_WITHDRAW,
        /** 自动卖出：target = 股票代码，amount = 股数，price = 触发价；取消：target = 条件单 ID。 */
        AUTO_SELL_UP, AUTO_SELL_DOWN, CANCEL_AUTO,
        /** 当老板：target = 险种 / 提案模板 / 提案 ID / 公司 ID；amount = 金额。 */
        INSURE, PROPOSE, VOTE_YES, VOTE_NO, COMPANY_LOAN, TAKEOVER, SQUEEZE, CHARTER_BANK, SET_WAGE,
        /** 分红比例：amount = 百分比。 */
        SET_DIVIDEND,
        /** 实体现金：amount = 便士或绿宝石颗数。 */
        CASH_DEPOSIT, CASH_WITHDRAW, EMERALD_SELL, EMERALD_BUY,
        /** 押注：target = s:股票代码 或 f:商品ID，amount = 投入；结束：target = 押注 key。新动作只往后加，编码按序号。 */
        BET_UP, BET_DOWN, BET_END
    }

    public SimpleActionPacket(Action action, String target, long amount) {
        this(action, target, amount, 0);
    }

    static final int MAX_TARGET = 64;
    /** 一次最多卖/买 36 组，避免一个包清空整个市场。 */
    static final long MAX_ITEMS = 64L * 36;

    public static void encode(SimpleActionPacket p, FriendlyByteBuf buf) {
        buf.writeEnum(p.action);
        buf.writeUtf(p.target == null ? "" : p.target, MAX_TARGET);
        buf.writeVarLong(p.amount);
        buf.writeVarLong(p.price);
    }

    public static SimpleActionPacket decode(FriendlyByteBuf buf) {
        return new SimpleActionPacket(buf.readEnum(Action.class), buf.readUtf(MAX_TARGET), buf.readVarLong(), buf.readVarLong());
    }

    public static void handle(SimpleActionPacket p, Supplier<NetworkEvent.Context> ctx) {
        var c = ctx.get();
        c.enqueueWork(() -> run(p, c.getSender()));
        c.setPacketHandled(true);
    }

    private static void run(SimpleActionPacket p, ServerPlayer player) {
        if (player == null || p.action == null) return;
        if (!MarketDataRequestLimiter.allow(player.getUUID(), player.server.getTickCount())) {
            GuiFeedbackPacket.send(player, "点得太快了，稍等一下。");
            return;
        }
        String gate = gate(p.action, safe(p.target));
        if (gate != null && !SimpleUnlocks.allowed(player, gate)) {
            SimpleUnlocks.Group group = SimpleUnlocks.Group.of(gate);
            GuiFeedbackPacket.send(player, SimpleUnlocks.disabled(gate) || group == null ? "这个玩法服务器没有开启。"
                    : "还没解锁「" + group.label + "」：" + group.howTo + "。");
            return;
        }
        String commodity = NetworkValidation.normalizeCommodityId(p.target);
        int items = (int) Math.max(0, Math.min(MAX_ITEMS, p.amount));
        SimpleCenterService.Result result = switch (p.action) {
            case QUICK_SELL -> NetworkValidation.isValidCommodityId(commodity)
                    ? SimpleCenterService.quickSell(player, commodity, items) : invalid();
            case QUICK_BUY -> NetworkValidation.isValidCommodityId(commodity)
                    ? SimpleCenterService.quickBuy(player, commodity, items) : invalid();
            case UPGRADE_FACTORY -> SimpleCenterService.upgradeFactory(player.getUUID());
            case QUICK_IPO -> SimpleCenterService.quickIpo(player.getUUID());
            case BANK_DEPOSIT -> SimpleCenterService.bankDeposit(player, p.amount);
            case BANK_WITHDRAW -> SimpleCenterService.bankWithdraw(player, p.amount);
            case PLACE_SELL_ORDER, PLACE_BUY_ORDER -> NetworkValidation.isValidCommodityId(commodity)
                    ? SimpleCenterService.placeOrder(player, commodity, p.action == Action.PLACE_SELL_ORDER, p.price, items) : invalid();
            case CANCEL_ORDER -> SimpleCenterService.cancelOrder(player, uuid(p.target));
            case TAKE_GOODS -> NetworkValidation.isValidCommodityId(commodity)
                    ? SimpleCenterService.takeGoods(player, commodity) : invalid();
            case LOCK_SELL, LOCK_BUY -> SimpleCenterService.lockPrice(player, uuid(p.target), p.action == Action.LOCK_SELL, p.amount);
            case CLOSE_LOCK -> SimpleCenterService.closeLock(player, uuid(p.target));
            case WITHDRAW_MARGIN -> SimpleCenterService.withdrawMargin(player);
            case BUY_PRODUCT -> invest(finance.simple.SimpleInvestService.buy(player, safe(p.target), p.amount));
            case REDEEM_PRODUCT -> invest(finance.simple.SimpleInvestService.redeem(player, safe(p.target)));
            case LEVER_BUY -> invest(finance.simple.SimpleInvestService.leverBuy(player, safe(p.target), Math.max(1, Math.min(MAX_ITEMS, p.amount))));
            case LEVER_REPAY -> invest(finance.simple.SimpleInvestService.leverRepay(player));
            case SHORT_SELL -> invest(finance.simple.SimpleInvestService.shortSell(player, safe(p.target), Math.max(1, Math.min(MAX_ITEMS, p.amount))));
            case SHORT_COVER -> invest(finance.simple.SimpleInvestService.shortCover(player, safe(p.target)));
            case COLLATERAL_WITHDRAW -> invest(finance.simple.SimpleInvestService.withdrawCollateral(player));
            case AUTO_SELL_UP, AUTO_SELL_DOWN -> invest(finance.simple.SimpleInvestService.autoSell(player, safe(p.target),
                    p.action == Action.AUTO_SELL_UP, p.price, Math.max(1, p.amount)));
            case CANCEL_AUTO -> invest(finance.simple.SimpleInvestService.cancelAuto(player, uuid(p.target)));
            case INSURE -> invest(finance.simple.SimpleBossService.insure(player, safe(p.target)));
            case PROPOSE -> invest(finance.simple.SimpleBossService.propose(player, safe(p.target)));
            case VOTE_YES, VOTE_NO -> invest(finance.simple.SimpleBossService.vote(player, uuid(p.target), p.action == Action.VOTE_YES));
            case COMPANY_LOAN -> invest(finance.simple.SimpleBossService.loan(player, Math.max(0, p.amount)));
            case TAKEOVER -> invest(finance.simple.SimpleBossService.takeover(player, uuid(p.target)));
            case SQUEEZE -> invest(finance.simple.SimpleBossService.squeeze(player, uuid(p.target)));
            case CHARTER_BANK -> invest(finance.simple.SimpleBossService.charterBank(player, Math.max(0, p.amount)));
            case SET_WAGE -> invest(finance.simple.SimpleBossService.setWage(player, Math.max(0, p.amount)));
            case SET_DIVIDEND -> invest(finance.simple.SimpleBossService.setDividend(player, p.amount));
            case CASH_DEPOSIT, CASH_WITHDRAW, EMERALD_SELL, EMERALD_BUY -> cash(player, p.action, Math.max(0, p.amount));
            case BET_UP, BET_DOWN -> betTarget(safe(p.target)) == null ? invalid()
                    : invest(finance.simple.SimpleBetService.open(player, betTarget(safe(p.target)), p.action == Action.BET_UP,
                            Math.max(0, Math.min(MAX_STAKE, p.amount))));
            case BET_END -> invest(finance.simple.SimpleBetService.close(player, safe(p.target)));
        };
        int tone = p.action != Action.BET_END || !result.success() ? GuiFeedbackPacket.TONE_NORMAL
                : result.message().contains(finance.simple.SimpleBetService.WIN_MARK) ? GuiFeedbackPacket.TONE_WIN
                : result.message().contains(finance.simple.SimpleBetService.LOSS_MARK) ? GuiFeedbackPacket.TONE_LOSS
                : GuiFeedbackPacket.TONE_NORMAL;
        GuiFeedbackPacket.send(player, result.message(), result.moneyDelta(), tone);
    }

    /** 一次押注最多投入 1000 万磅。 */
    static final long MAX_STAKE = 1_000_000_000L;

    /** 押注目标：s:股票代码 或 f:合法商品ID；不合法返回 null。 */
    static String betTarget(String target) {
        if (target.startsWith("s:") && target.length() > 2) return target;
        if (target.startsWith("f:")) {
            String commodity = NetworkValidation.normalizeCommodityId(target.substring(2));
            return NetworkValidation.isValidCommodityId(commodity) ? "f:" + commodity : null;
        }
        return null;
    }

    /** 新开仓位的进阶玩法要解锁（或服务器直接打开）；平仓、取回、还款、结束押注永远可以做。 */
    static String gate(Action action, String target) {
        return switch (action) {
            case LOCK_SELL, LOCK_BUY -> "futures";
            case LEVER_BUY, SHORT_SELL -> "margin";
            case BET_UP, BET_DOWN -> target.startsWith("f:") ? "futures" : "margin";
            case BUY_PRODUCT -> target.startsWith("cbond:") ? "bonds" : target.startsWith("fund:") || "tier:growth".equals(target) ? "fund" : null;
            case PROPOSE, VOTE_YES, VOTE_NO -> "shareholder";
            case TAKEOVER, SQUEEZE -> "takeover";
            case CHARTER_BANK -> "bank";
            case INSURE -> "insurance";
            case SET_WAGE -> "wage";
            default -> null;
        };
    }

    private static SimpleCenterService.Result cash(ServerPlayer player, Action action, long amount) {
        var service = switch (action) {
            case CASH_DEPOSIT -> finance.playerservice.PlayerServicesService.Action.CASH_DEPOSIT;
            case CASH_WITHDRAW -> finance.playerservice.PlayerServicesService.Action.CASH_WITHDRAW;
            case EMERALD_SELL -> finance.playerservice.PlayerServicesService.Action.EMERALD_SELL;
            default -> finance.playerservice.PlayerServicesService.Action.EMERALD_BUY;
        };
        long before = finance.account.AccountManager.getBalance(player.getUUID());
        var r = finance.playerservice.PlayerServicesService.act(player, player.getUUID(), service, "", amount, 0, 0, 0,
                finance.cycle.EconomyCycleService.currentMcDay(player.server));
        return new SimpleCenterService.Result(r.success(), r.message(), finance.account.AccountManager.getBalance(player.getUUID()) - before);
    }

    private static SimpleCenterService.Result invest(finance.simple.SimpleInvestService.Result r) {
        return new SimpleCenterService.Result(r.success(), r.message(), r.delta());
    }

    private static String safe(String text) {
        return text == null ? "" : text.trim();
    }

    private static java.util.UUID uuid(String text) {
        try {
            return java.util.UUID.fromString(text);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static SimpleCenterService.Result invalid() {
        return new SimpleCenterService.Result(false, "请求无效。", 0);
    }
}
