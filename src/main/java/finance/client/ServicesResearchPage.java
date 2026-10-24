package finance.client;

import static finance.client.PlayerServicesScreen.*;

import finance.client.PlayerServicesScreen.Btn;
import finance.network.PlayerServicesResponsePacket;
import finance.playerservice.PlayerServicesService.Action;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/** “更多操作” · 研究页：财报日历、查询季报和商情调查的结果。 */
final class ServicesResearchPage extends ServicesPage {
    ServicesResearchPage(PlayerServicesScreen screen) {
        super(screen);
    }

    @Override
    void render(GuiGraphics g, PlayerServicesResponsePacket data) {
        int row = 0;
        for (String s : data.calendar()) {
            if (row >= 5) break;
            line(g, s, row++, row == 1 ? FinanceGui.COL_ACCENT : FinanceGui.COL_TEXT);
        }
        int sepY = top() + CONTENT_Y + 5 * LINE_H + 2;
        FinanceGui.drawSimpleSeparator(g, left() + 8, sepY, PANEL_WIDTH - 16);
        row = 6;
        if (data.detailTitle().isEmpty()) {
            wrapped(
                    g,
                    "输入股票代码点“查询季报”；或点“商情调查”（收费 " + data.macro().radarCost() + "）看各地的价差和商机。结果显示在这里。",
                    left() + 10,
                    row,
                    PANEL_WIDTH - 20,
                    3,
                    FinanceGui.COL_TEXT_DIM);
            return;
        }
        line(g, data.detailTitle(), row++, FinanceGui.COL_ACCENT);
        for (String s : data.detail()) {
            if (row > 10) break;
            row += wrapped(g, s, left() + 10, row, PANEL_WIDTH - 20, 11 - row, FinanceGui.COL_TEXT);
        }
    }

    @Override
    List<Btn> buttons(PlayerServicesResponsePacket data) {
        List<Btn> list = new ArrayList<>();
        list.add(new Btn(
                "查询季报", () -> send(Action.EARNINGS_REPORT, PlayerServicesForm.text(box(0), "股票代码"), 0, 0, 0, 0)));
        list.add(new Btn("商情调查（" + data.macro().radarCost() + "）", () -> send(Action.MACRO_RADAR, "", 0, 0, 0, 0)));
        list.add(new Btn("刷新", this::refresh));
        return list;
    }
}
