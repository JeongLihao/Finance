package finance.client;

import static finance.client.PlayerServicesScreen.*;

import finance.client.PlayerServicesForm.InvalidInput;
import finance.client.PlayerServicesScreen.Btn;
import finance.client.PlayerServicesScreen.Hit;
import finance.network.PlayerServicesResponsePacket;
import finance.network.PlayerServicesResponsePacket.BondHolding;
import finance.network.PlayerServicesResponsePacket.DepositRow;
import finance.playerservice.PlayerServicesService.Action;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;

/** “更多操作” · 中央银行页：利率和通胀概况、央行债券认购和赎回、定期存款提前支取。 */
final class ServicesCentralBankPage extends ServicesPage {
    private UUID selectedBond;
    private UUID selectedDeposit;

    ServicesCentralBankPage(PlayerServicesScreen screen) {
        super(screen);
    }

    @Override
    void render(GuiGraphics g, PlayerServicesResponsePacket data) {
        var cb = data.centralBank();
        var o = cb.overview();
        int gap = o.inflationBps() - o.targetBps();
        line(
                g,
                String.format(
                        "基准利率 %.2f%%   通胀 %.1f%%（目标 %.1f%%）   活期准备金率 %.1f%%",
                        o.rateBps() / 100.0, o.inflationBps() / 100.0, o.targetBps() / 100.0, o.reserveBps() / 100.0),
                0,
                Math.abs(gap) <= 100
                        ? FinanceGui.COL_GOOD
                        : Math.abs(gap) >= 500 ? FinanceGui.COL_BAD : FinanceGui.COL_WARN);
        line(
                g,
                (o.nextMeetingDay() < 0 ? "议息日程未排定" : "下次议息第 " + o.nextMeetingDay() + " 天，倾向" + o.guidance()) + "   "
                        + (o.stabilization().isEmpty() ? "昨日无平准" : "平准：" + String.join("、", o.stabilization())),
                1,
                FinanceGui.COL_TEXT_DIM);
        StringBuilder offers = new StringBuilder("央行债券今日收益率：");
        for (var f : cb.offers())
            offers.append(f.termDays())
                    .append("天 ")
                    .append(String.format("%.2f%%", f.yieldBps() / 100.0))
                    .append("   ");
        line(g, offers.toString().trim() + "   起认 " + cb.bondMinAmount(), 2, FinanceGui.COL_ACCENT);

        int y = top() + CONTENT_Y + 3 * LINE_H + 3;
        FinanceGui.drawClippedString(
                font(),
                g,
                "我的央行债券（提前赎回只退本金，手续费 " + cb.bondEarlyFee() + "）",
                left() + 10,
                y,
                360,
                FinanceGui.COL_TEXT_DIM);
        y += LINE_H;
        if (cb.bonds().isEmpty()) {
            FinanceGui.drawClippedString(font(), g, "暂无", left() + 10, y, 100, FinanceGui.COL_TEXT_DIM);
            y += LINE_H;
        }
        for (BondHolding h : cb.bonds()) {
            if (y > top() + CONTENT_Y + 6 * LINE_H) break;
            boolean sel = h.id().equals(selectedBond);
            g.fill(
                    left() + 8,
                    y - 1,
                    left() + PANEL_WIDTH - 8,
                    y + 10,
                    sel ? FinanceGui.COL_ROW_SELECT : FinanceGui.COL_ROW_EVEN);
            FinanceGui.drawClippedString(
                    font(),
                    g,
                    h.termDays() + " 天  本金 " + h.principal() + "  年利率 " + String.format("%.2f%%", h.rateBps() / 100.0)
                            + "  第 " + h.maturityDay() + " 天到期",
                    left() + 10,
                    y,
                    360,
                    FinanceGui.COL_TEXT);
            UUID id = h.id();
            hits().add(new Hit(left() + 8, y - 1, PANEL_WIDTH - 16, LINE_H, () -> {
                selectedBond = id;
                selectedDeposit = null;
            }));
            y += LINE_H;
        }
        y += 2;
        FinanceGui.drawClippedString(
                font(), g, "我的定期存款（提前支取只退本金，已计利息作废）", left() + 10, y, 360, FinanceGui.COL_TEXT_DIM);
        y += LINE_H;
        if (cb.deposits().isEmpty())
            FinanceGui.drawClippedString(
                    font(), g, "暂无（在金融中心“金融 → 银行”开定期）", left() + 10, y, 300, FinanceGui.COL_TEXT_DIM);
        for (DepositRow d : cb.deposits()) {
            if (y > top() + LABEL_Y - LINE_H) break;
            boolean sel = d.id().equals(selectedDeposit);
            g.fill(
                    left() + 8,
                    y - 1,
                    left() + PANEL_WIDTH - 8,
                    y + 10,
                    sel ? FinanceGui.COL_ROW_SELECT : FinanceGui.COL_ROW_EVEN);
            FinanceGui.drawClippedString(
                    font(),
                    g,
                    d.bank() + "  本金 " + d.principal() + "  年利率 " + String.format("%.2f%%", d.rateBps() / 100.0)
                            + "  已计利息 " + d.accrued() + "  第 " + d.maturityDay() + " 天到期",
                    left() + 10,
                    y,
                    360,
                    FinanceGui.COL_TEXT);
            UUID id = d.id();
            hits().add(new Hit(left() + 8, y - 1, PANEL_WIDTH - 16, LINE_H, () -> {
                selectedDeposit = id;
                selectedBond = null;
            }));
            y += LINE_H;
        }
    }

    @Override
    List<Btn> buttons(PlayerServicesResponsePacket data) {
        List<Btn> list = new ArrayList<>();
        list.add(new Btn(
                "认购央行债券",
                () -> send(
                        Action.CB_BOND_SUBSCRIBE,
                        "",
                        PlayerServicesForm.positive(box(0), "期限"),
                        PlayerServicesForm.positive(box(1), "认购金额"),
                        0,
                        0)));
        list.add(new Btn("提前赎回选中债券", () -> {
            if (selectedBond == null) throw new InvalidInput("请先在“我的央行债券”里选中一笔");
            send(Action.CB_BOND_REDEEM, selectedBond.toString(), 0, 0, 0, 0);
        }));
        list.add(new Btn("提前支取选中定存", () -> {
            if (selectedDeposit == null) throw new InvalidInput("请先在“我的定期存款”里选中一笔");
            send(Action.TIME_DEPOSIT_EARLY, selectedDeposit.toString(), 0, 0, 0, 0);
        }));
        list.add(new Btn("刷新", this::refresh));
        return list;
    }
}
