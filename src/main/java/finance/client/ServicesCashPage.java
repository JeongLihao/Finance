package finance.client;

import static finance.client.PlayerServicesScreen.*;

import finance.client.PlayerServicesScreen.Btn;
import finance.network.PlayerServicesResponsePacket;
import finance.playerservice.PlayerServicesService.Action;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/** “更多操作” · 现金页：实体现金存取和绿宝石买卖。 */
final class ServicesCashPage extends ServicesPage {
    ServicesCashPage(PlayerServicesScreen screen) {
        super(screen);
    }

    @Override
    void render(GuiGraphics g, PlayerServicesResponsePacket data) {
        var c = data.cash();
        if (!c.enabled()) {
            line(g, "实体现金未启用（配置 cash.enabled）", 0, FinanceGui.COL_WARN);
            return;
        }
        line(
                g,
                "账户余额 " + c.balance() + "    随身现金 " + c.cashOnHand() + "    绿宝石 " + c.emeralds() + " 颗",
                0,
                FinanceGui.COL_ACCENT);
        line(
                g,
                "卖出 1 颗绿宝石得 " + c.emeraldToCash() + " 便士    买入 1 颗绿宝石付 " + c.cashToEmerald() + " 便士",
                1,
                FinanceGui.COL_TEXT);
        line(g, c.trendText(), 2, c.deviationPercent() < -30 ? FinanceGui.COL_WARN : FinanceGui.COL_TEXT);
        wrapped(
                g,
                "现金有 1 便士铜币、10 便士硬币、1 磅纸币三种（100 便士 = 1 磅），和账户里的钱 1:1 互换。取现：填金额后点“取出现金”；"
                        + "存入：金额留空则存入身上全部现金，面额凑不齐会自动找零。现金不记名，丢了或被人捡走就是别人的。",
                left() + 10,
                4,
                PANEL_WIDTH - 20,
                3,
                FinanceGui.COL_TEXT_DIM);
        wrapped(
                g,
                "绿宝石用现金兑换（不经过账户）：先取出现金再买绿宝石，卖绿宝石得到的是现金。买卖有点差，" + "汇率随全服买卖浮动，没有每日额度。",
                left() + 10,
                8,
                PANEL_WIDTH - 20,
                2,
                FinanceGui.COL_TEXT_DIM);
    }

    @Override
    List<Btn> buttons(PlayerServicesResponsePacket data) {
        List<Btn> list = new ArrayList<>();
        list.add(new Btn(
                "取出现金", () -> send(Action.CASH_WITHDRAW, "", PlayerServicesForm.positive(box(0), "金额"), 0, 0, 0)));
        list.add(new Btn(
                "存入账户", () -> send(Action.CASH_DEPOSIT, "", PlayerServicesForm.number(box(0), "金额", true), 0, 0, 0)));
        list.add(new Btn(
                "卖出绿宝石", () -> send(Action.EMERALD_SELL, "", PlayerServicesForm.positive(box(1), "绿宝石数量"), 0, 0, 0)));
        list.add(new Btn(
                "买入绿宝石", () -> send(Action.EMERALD_BUY, "", PlayerServicesForm.positive(box(1), "绿宝石数量"), 0, 0, 0)));
        list.add(new Btn("刷新", this::refresh));
        return list;
    }
}
