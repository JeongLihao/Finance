package finance.network;

import finance.simple.SimpleCenterService;
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
        LOCK_SELL, LOCK_BUY, CLOSE_LOCK, WITHDRAW_MARGIN
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
        };
        GuiFeedbackPacket.send(player, result.message(), result.moneyDelta());
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
