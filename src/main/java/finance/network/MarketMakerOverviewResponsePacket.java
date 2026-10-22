package finance.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * 做市商总览快照（Phase 4，管理后台"做市商总览"子页数据）。
 *
 * <p>服务端 → 客户端，follow {@code FuturesResponsePacket} 的请求/响应模式。
 * 仅管理员可请求（服务端校验 2 级权限）。各数值均为快照时刻值；
 * P&L 为今日资金流口径（做市商卖出所得 − 买入支出，不含库存重估）。</p>
 */
public record MarketMakerOverviewResponsePacket(
        long requestId, long masterCash, long futuresLoanOutstanding, List<MarketRow> markets) {

    public static final int MAX_ROWS = 8;

    /**
     * 单市场行。
     *
     * @param market      市场键：commodity / stock / futures
     * @param funds       该市场可归属资金（商品/股票为做市商总账户余额；期货为 f002 保证金余额）
     * @param inventoryQty  库存数量（商品=实物单位总数；股票=库存股数；期货=0）
     * @param inventoryValue 库存市值估算（期货=0）
     * @param netPosition 期货做市商净持仓（Σ各合约有符号净持仓；其余市场=0）
     * @param todayVolumeQty 今日成交量（数量口径）
     * @param todayAmount 今日做市商成交额（金额口径）
     * @param todayPnl    今日 P&L（资金流口径：流入 − 流出）
     * @param manualPaused 管理员手动暂停中
     * @param haltedCount 熔断暂停中的标的数量（Phase 2 熔断）
     * @param cappedCount 库存触顶的标的数量（股票）
     */
    public record MarketRow(
            String market,
            long funds,
            long inventoryQty,
            long inventoryValue,
            long netPosition,
            long todayVolumeQty,
            long todayAmount,
            long todayPnl,
            boolean manualPaused,
            int haltedCount,
            int cappedCount) {}

    public MarketMakerOverviewResponsePacket {
        if (requestId <= 0) {
            throw new IllegalArgumentException("requestId 必须为正");
        }
        markets = bounded(markets);
    }

    private static List<MarketRow> bounded(List<MarketRow> rows) {
        if (rows == null) {
            return List.of();
        }
        return List.copyOf(rows.subList(0, Math.min(MAX_ROWS, rows.size())));
    }

    public static void encode(MarketMakerOverviewResponsePacket p, FriendlyByteBuf b) {
        b.writeLong(p.requestId);
        b.writeLong(p.masterCash);
        b.writeLong(p.futuresLoanOutstanding);
        b.writeVarInt(p.markets.size());
        for (MarketRow r : p.markets) {
            b.writeUtf(r.market, 16);
            b.writeLong(r.funds);
            b.writeLong(r.inventoryQty);
            b.writeLong(r.inventoryValue);
            b.writeLong(r.netPosition);
            b.writeLong(r.todayVolumeQty);
            b.writeLong(r.todayAmount);
            b.writeLong(r.todayPnl);
            b.writeBoolean(r.manualPaused);
            b.writeVarInt(r.haltedCount);
            b.writeVarInt(r.cappedCount);
        }
    }

    public static MarketMakerOverviewResponsePacket decode(FriendlyByteBuf b) {
        long id = b.readLong();
        long masterCash = b.readLong();
        long loanOutstanding = b.readLong();
        int n = b.readVarInt();
        if (n < 0 || n > MAX_ROWS) {
            throw new IllegalArgumentException("market row limit");
        }
        List<MarketRow> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rows.add(new MarketRow(
                    b.readUtf(16),
                    b.readLong(),
                    b.readLong(),
                    b.readLong(),
                    b.readLong(),
                    b.readLong(),
                    b.readLong(),
                    b.readLong(),
                    b.readBoolean(),
                    b.readVarInt(),
                    b.readVarInt()));
        }
        return new MarketMakerOverviewResponsePacket(id, masterCash, loanOutstanding, rows);
    }

    public static void handle(MarketMakerOverviewResponsePacket p, Supplier<NetworkEvent.Context> s) {
        var c = s.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT, () -> () -> finance.client.MarketMakerOverviewClientCache.accept(p)));
        c.setPacketHandled(true);
    }
}
