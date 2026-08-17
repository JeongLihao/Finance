package finance.gui;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FinanceMenuDashboardCodecTest {

    @Test
    void dashboardCodecRoundTripsCurrentMetricsAndLatestThirtyTrends() {
        List<FinanceMenu.EconomyTrendRow> trends = new ArrayList<>();
        for (int day = 1; day <= 35; day++) {
            trends.add(new FinanceMenu.EconomyTrendRow(day, day * 2L, day * 3L, 100.0 + day));
        }
        FinanceMenu.EconomyDashboardRow expected = new FinanceMenu.EconomyDashboardRow(
                1, 2, 3, 4, 5, 15, 6, 7, 101.5, 2, "stable", trends);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        FinanceMenu.writeDashboard(buffer, expected);

        FinanceMenu.EconomyDashboardRow actual = FinanceMenu.readDashboard(buffer);
        assertEquals(1, actual.playerCash());
        assertEquals(15, actual.totalMoney());
        assertEquals(FinanceMenu.DASHBOARD_TREND_LIMIT, actual.trends().size());
        assertEquals(6, actual.trends().get(0).mcDay());
        assertEquals(35, actual.trends().get(29).mcDay());
    }

    @Test
    void oversizedTrendPayloadIsFullyConsumedBeforeFollowingFields() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        writeDashboardHeader(buffer);
        buffer.writeVarInt(31);
        for (int day = 1; day <= 31; day++) {
            buffer.writeLong(day);
            buffer.writeLong(day * 2L);
            buffer.writeLong(day * 3L);
            buffer.writeDouble(100.0 + day);
        }
        buffer.writeVarInt(4242);

        FinanceMenu.EconomyDashboardRow decoded = FinanceMenu.readDashboard(buffer);

        assertEquals(30, decoded.trends().size());
        assertEquals(2, decoded.trends().get(0).mcDay());
        assertEquals(31, decoded.trends().get(29).mcDay());
        assertEquals(4242, buffer.readVarInt());
    }

    @Test
    void invalidTrendCountIsRejected() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        writeDashboardHeader(buffer);
        buffer.writeVarInt(257);

        assertThrows(IllegalArgumentException.class, () -> FinanceMenu.readDashboard(buffer));
    }

    private static void writeDashboardHeader(FriendlyByteBuf buffer) {
        for (int index = 0; index < 8; index++) {
            buffer.writeLong(index);
        }
        buffer.writeDouble(100.0);
        buffer.writeVarInt(0);
        buffer.writeUtf("", 128);
    }
}
