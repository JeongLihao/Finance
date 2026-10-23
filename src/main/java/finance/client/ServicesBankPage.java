package finance.client;

import static finance.client.PlayerServicesScreen.*;

import finance.client.PlayerServicesScreen.Btn;
import finance.network.PlayerServicesResponsePacket;
import finance.playerservice.PlayerServicesService.Action;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/** “更多操作” · 我的银行页：没有银行时申请牌照，有银行时看概况、设定利差、分红。 */
final class ServicesBankPage extends ServicesPage {
    ServicesBankPage(PlayerServicesScreen screen) {
        super(screen);
    }

    @Override
    void render(GuiGraphics g, PlayerServicesResponsePacket data) {
        var b = data.bank();
        if (!b.owned()) {
            line(g, "你名下还没有银行。", 0, FinanceGui.COL_ACCENT);
            line(g, "最低实缴资本 " + b.minCapital() + "    牌照费 " + b.charterFee(), 1, FinanceGui.COL_TEXT);
            wrapped(
                    g,
                    "在“代码 名称”里填写银行代码和名称（用空格隔开，例如：MYBK 我的银行），再填资本金，点“申请牌照”。" + "资本金和牌照费从你的账户扣除。",
                    left() + 10,
                    3,
                    PANEL_WIDTH - 20,
                    4,
                    FinanceGui.COL_TEXT_DIM);
            return;
        }
        line(g, b.summary(), 0, FinanceGui.COL_ACCENT);
        line(g, b.rates(), 1, FinanceGui.COL_TEXT);
        wrapped(
                g,
                "利差以基点计（1% = 100bps），活期、定期、贷款三项要一起设定，范围 0～10000；银行不可展业时不能调价。" + "分红从银行权益中提取到你的钱包，分红后资本充足率和杠杆率仍须达标。",
                left() + 10,
                3,
                PANEL_WIDTH - 20,
                5,
                FinanceGui.COL_TEXT_DIM);
    }

    @Override
    List<Btn> buttons(PlayerServicesResponsePacket data) {
        List<Btn> list = new ArrayList<>();
        if (data.bank().owned()) {
            list.add(new Btn(
                    "设定利率",
                    () -> send(
                            Action.BANK_RATES,
                            "",
                            PlayerServicesForm.number(box(0), "活期点差", false),
                            PlayerServicesForm.number(box(1), "定期点差", false),
                            PlayerServicesForm.number(box(2), "贷款点差", false),
                            0)));
            list.add(new Btn(
                    "分红", () -> send(Action.BANK_DIVIDEND, "", PlayerServicesForm.positive(box(3), "分红金额"), 0, 0, 0)));
        } else {
            list.add(new Btn(
                    "申请牌照",
                    () -> send(
                            Action.BANK_CHARTER,
                            PlayerServicesForm.text(box(0), "代码和名称"),
                            PlayerServicesForm.positive(box(1), "资本金"),
                            0,
                            0,
                            0)));
        }
        list.add(new Btn("刷新", this::refresh));
        return list;
    }
}
