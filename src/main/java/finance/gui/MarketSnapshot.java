package finance.gui;

import finance.market.MarketPrice;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record MarketSnapshot(
        String commodityId,
        long midPrice,
        long bidPrice,
        long askPrice,
        double dayChange,
        int dayVolume,
        int marketStock
) {

    public static void writeList(FriendlyByteBuf buffer, List<MarketSnapshot> snapshots) {
        buffer.writeVarInt(snapshots.size());
        for (MarketSnapshot snapshot : snapshots) {
            buffer.writeUtf(snapshot.commodityId());
            buffer.writeLong(snapshot.midPrice());
            buffer.writeLong(snapshot.bidPrice());
            buffer.writeLong(snapshot.askPrice());
            buffer.writeDouble(snapshot.dayChange());
            buffer.writeVarInt(snapshot.dayVolume());
            buffer.writeVarInt(snapshot.marketStock());
        }
    }

    public static List<MarketSnapshot> readList(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<MarketSnapshot> snapshots = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            snapshots.add(new MarketSnapshot(
                    buffer.readUtf(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readDouble(),
                    buffer.readVarInt(),
                    buffer.readVarInt()
            ));
        }
        return snapshots;
    }

    public static List<MarketSnapshot> sorted(List<MarketSnapshot> snapshots) {
        return snapshots.stream()
                .sorted(Comparator.comparing(MarketSnapshot::commodityId))
                .toList();
    }

    public static MarketSnapshot fromMarketPrice(MarketPrice price, int marketStock) {
        return new MarketSnapshot(
                price.getCommodityId(),
                price.getMidPrice(),
                price.getBidPrice(),
                price.getAskPrice(),
                price.getDayChange(),
                price.getDayVolume(),
                marketStock
        );
    }
}
