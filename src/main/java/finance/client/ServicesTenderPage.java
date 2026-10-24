package finance.client;

import static finance.client.PlayerServicesScreen.*;

import finance.client.PlayerServicesForm.InvalidInput;
import finance.client.PlayerServicesScreen.Btn;
import finance.client.PlayerServicesScreen.Hit;
import finance.network.PlayerServicesResponsePacket;
import finance.network.PlayerServicesResponsePacket.SqueezeRow;
import finance.network.PlayerServicesResponsePacket.TenderRow;
import finance.playerservice.PlayerServicesService.Action;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;

/** “更多操作” · 要约收购页：进行中的要约和可强制私有化的公司，接受、撤回、发起要约和强制私有化。 */
final class ServicesTenderPage extends ServicesPage {
    private UUID selectedTender;
    private UUID selectedSqueeze;

    ServicesTenderPage(PlayerServicesScreen screen) {
        super(screen);
    }

    @Override
    void render(GuiGraphics g, PlayerServicesResponsePacket data) {
        int y = top() + CONTENT_Y;
        FinanceGui.drawClippedString(font(), g, "进行中的要约", left() + 10, y, 90, FinanceGui.COL_ACCENT);
        FinanceGui.drawClippedString(
                font(), g, "出价方      价格   已接受/目标   截止   我的持股", left() + 100, y, 270, FinanceGui.COL_TEXT_DIM);
        y += LINE_H + 1;
        if (data.tenders().isEmpty()) {
            FinanceGui.drawClippedString(font(), g, "当前没有进行中的要约", left() + 10, y, 200, FinanceGui.COL_TEXT_DIM);
            y += LINE_H;
        }
        int index = 0;
        for (TenderRow t : data.tenders()) {
            if (y > top() + CONTENT_Y + 6 * LINE_H) break;
            boolean sel = t.id().equals(selectedTender);
            g.fill(
                    left() + 8,
                    y - 1,
                    left() + PANEL_WIDTH - 8,
                    y + 10,
                    sel
                            ? FinanceGui.COL_ROW_SELECT
                            : index++ % 2 == 0 ? FinanceGui.COL_ROW_EVEN : FinanceGui.COL_ROW_ODD);
            FinanceGui.drawClippedString(
                    font(), g, t.target() + (t.squeeze() ? "（私有化）" : ""), left() + 10, y, 88, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    font(), g, t.buyer(), left() + 100, y, 56, t.mine() ? FinanceGui.COL_GOOD : FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    font(), g, String.valueOf(t.price()), left() + 160, y, 40, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    font(), g, t.accepted() + "/" + t.targetShares(), left() + 202, y, 70, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(font(), g, "D" + t.endDay(), left() + 276, y, 34, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(
                    font(), g, String.valueOf(t.myShares()), left() + 314, y, 56, FinanceGui.COL_TEXT);
            UUID id = t.id();
            hits().add(new Hit(left() + 8, y - 1, PANEL_WIDTH - 16, LINE_H, () -> {
                selectedTender = id;
                selectedSqueeze = null;
            }));
            y += LINE_H;
        }
        y += 3;
        FinanceGui.drawClippedString(font(), g, "可强制私有化（持股 ≥ 90%）", left() + 10, y, 200, FinanceGui.COL_ACCENT);
        y += LINE_H + 1;
        if (data.squeezable().isEmpty()) {
            FinanceGui.drawClippedString(font(), g, "暂无", left() + 10, y, 200, FinanceGui.COL_TEXT_DIM);
        }
        for (SqueezeRow s : data.squeezable()) {
            if (y > top() + LABEL_Y - LINE_H) break;
            boolean sel = s.companyId().equals(selectedSqueeze);
            g.fill(
                    left() + 8,
                    y - 1,
                    left() + PANEL_WIDTH - 8,
                    y + 10,
                    sel ? FinanceGui.COL_ROW_SELECT : FinanceGui.COL_ROW_EVEN);
            FinanceGui.drawClippedString(
                    font(),
                    g,
                    s.name() + "  持股 " + s.percentX10() / 10.0 + "%",
                    left() + 10,
                    y,
                    300,
                    FinanceGui.COL_TEXT);
            UUID id = s.companyId();
            hits().add(new Hit(left() + 8, y - 1, PANEL_WIDTH - 16, LINE_H, () -> {
                selectedSqueeze = id;
                selectedTender = null;
            }));
            y += LINE_H;
        }
    }

    @Override
    List<Btn> buttons(PlayerServicesResponsePacket data) {
        List<Btn> list = new ArrayList<>();
        list.add(new Btn("接受选中要约", () -> {
            if (selectedTender == null) throw new InvalidInput("请先在列表里选中一个要约");
            send(Action.TENDER_ACCEPT, selectedTender.toString(), PlayerServicesForm.positive(box(2), "股数"), 0, 0, 0);
        }));
        list.add(new Btn("撤回我的要约", () -> {
            if (selectedTender == null) throw new InvalidInput("请先选中你发起的要约");
            send(Action.TENDER_CANCEL, selectedTender.toString(), 0, 0, 0, 0);
        }));
        list.add(new Btn(
                "发起收购要约",
                () -> send(
                        Action.TENDER_OFFER,
                        PlayerServicesForm.text(box(0), "公司名称"),
                        PlayerServicesForm.positive(box(1), "价格"),
                        PlayerServicesForm.positive(box(2), "股数"),
                        0,
                        PlayerServicesForm.positive(box(3), "天数"))));
        list.add(new Btn("强制私有化", () -> {
            if (selectedSqueeze == null) throw new InvalidInput("请先在“可强制私有化”里选中一家公司");
            send(Action.SQUEEZE_OUT, selectedSqueeze.toString(), 0, 0, 0, 0);
        }));
        list.add(new Btn("刷新", this::refresh));
        return list;
    }
}
