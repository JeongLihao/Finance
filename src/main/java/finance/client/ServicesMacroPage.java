package finance.client;

import static finance.client.PlayerServicesScreen.*;

import finance.client.PlayerServicesScreen.Btn;
import finance.network.PlayerServicesResponsePacket;
import finance.playerservice.PlayerServicesService.Action;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/** “更多操作” · 宏观页：设定公司日工资、领取《金融周报》。 */
final class ServicesMacroPage extends ServicesPage {
    ServicesMacroPage(PlayerServicesScreen screen) {
        super(screen);
    }

    @Override
    void render(GuiGraphics g, PlayerServicesResponsePacket data) {
        var m = data.macro();
        if (!m.active()) {
            line(g, "宏观经济未启用（配置 macroEconomy.enabled），不能设定工资", 0, FinanceGui.COL_WARN);
        } else {
            line(
                    g,
                    m.company().isEmpty()
                            ? "你还没有公司，不能设定工资"
                            : m.company() + "  日工资 " + m.wage() + "（保留工资 " + m.reservationWage() + "，低于它招不到人）",
                    0,
                    FinanceGui.COL_ACCENT);
        }
        line(g, m.newsEnabled() ? "《金融周报》每周一期，可以在这里领取，也可以到任意聚落贸易站右键领取。" : "《金融周报》未启用。", 2, FinanceGui.COL_TEXT_DIM);
        wrapped(
                g,
                "工资越高越容易从附近聚落招到工人；产量按在岗比例计算。物价和利率由中央银行调控，见“中央银行”页。",
                left() + 10,
                4,
                PANEL_WIDTH - 20,
                3,
                FinanceGui.COL_TEXT_DIM);
    }

    @Override
    List<Btn> buttons(PlayerServicesResponsePacket data) {
        List<Btn> list = new ArrayList<>();
        list.add(new Btn(
                "设定工资", () -> send(Action.MACRO_WAGE, "", PlayerServicesForm.number(box(0), "日工资", false), 0, 0, 0)));
        list.add(new Btn("领取周报", () -> send(Action.MACRO_NEWS, "", 0, 0, 0, 0)));
        list.add(new Btn("刷新", this::refresh));
        return list;
    }
}
