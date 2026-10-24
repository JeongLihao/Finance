package finance.client;

import static finance.client.PlayerServicesScreen.*;

import finance.client.PlayerServicesScreen.Btn;
import finance.network.PlayerServicesResponsePacket;
import finance.playerservice.PlayerServicesService.Action;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/** “更多操作” · 保证金页：融资融券的保证金率、负债和持仓，存取保证金、融资买入、融券卖出、平仓、还债。 */
final class ServicesMarginPage extends ServicesPage {
    ServicesMarginPage(PlayerServicesScreen screen) {
        super(screen);
    }

    @Override
    void render(GuiGraphics g, PlayerServicesResponsePacket data) {
        var m = data.margin();
        line(
                g,
                "维持保证金率 " + m.ratio() + (m.warning() ? "   低于预警线，请补保证金或减仓" : ""),
                0,
                m.warning() ? FinanceGui.COL_BAD : FinanceGui.COL_ACCENT);
        line(g, "现金保证金 " + m.cash() + "    融资负债 " + m.debt() + "    融资利息 " + m.interest(), 1, FinanceGui.COL_TEXT);
        line(
                g,
                m.badDebt() > 0 ? "不良债务 " + m.badDebt() + "（还清前不能新开仓）" : "没有不良债务",
                2,
                m.badDebt() > 0 ? FinanceGui.COL_BAD : FinanceGui.COL_TEXT_DIM);
        line(g, "融资持仓：" + m.financed(), 3, FinanceGui.COL_TEXT);
        line(g, "融券仓位：" + m.shorts(), 4, FinanceGui.COL_TEXT);
        line(g, "自由资金 " + m.freeCash(), 5, FinanceGui.COL_TEXT);
        wrapped(
                g,
                "融资买入：用保证金加杠杆买股票，按维持保证金率监控，过低会被强制平仓。融券卖出：借股票卖出，" + "之后买回平仓；平仓时数量留空表示全部平仓。",
                left() + 10,
                7,
                PANEL_WIDTH - 20,
                3,
                FinanceGui.COL_TEXT_DIM);
    }

    @Override
    List<Btn> buttons(PlayerServicesResponsePacket data) {
        List<Btn> list = new ArrayList<>();
        list.add(new Btn(
                "存入保证金", () -> send(Action.MARGIN_DEPOSIT, "", PlayerServicesForm.positive(box(2), "金额"), 0, 0, 0)));
        list.add(new Btn(
                "取出保证金", () -> send(Action.MARGIN_WITHDRAW, "", PlayerServicesForm.positive(box(2), "金额"), 0, 0, 0)));
        list.add(new Btn(
                "融资买入",
                () -> send(
                        Action.MARGIN_BUY,
                        PlayerServicesForm.text(box(0), "股票代码"),
                        PlayerServicesForm.positive(box(1), "数量"),
                        0,
                        0,
                        0)));
        list.add(new Btn(
                "偿还融资", () -> send(Action.MARGIN_REPAY, "", PlayerServicesForm.positive(box(2), "金额"), 0, 0, 0)));
        list.add(new Btn(
                "融券卖出",
                () -> send(
                        Action.MARGIN_SHORT,
                        PlayerServicesForm.text(box(0), "股票代码"),
                        PlayerServicesForm.positive(box(1), "数量"),
                        0,
                        0,
                        0)));
        list.add(new Btn(
                "买回平仓",
                () -> send(
                        Action.MARGIN_COVER,
                        PlayerServicesForm.text(box(0), "股票代码"),
                        PlayerServicesForm.number(box(1), "数量", true),
                        0,
                        0,
                        0)));
        list.add(new Btn(
                "偿还不良债务",
                () -> send(Action.MARGIN_REPAY_BAD_DEBT, "", PlayerServicesForm.positive(box(2), "金额"), 0, 0, 0)));
        list.add(new Btn("刷新", this::refresh));
        return list;
    }
}
