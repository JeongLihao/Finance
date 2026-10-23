package finance.client;

import finance.network.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;

/** 金融服务 · 银行：存款、定期、转账、银行管理。由 {@link ServiceTabs} 分发渲染和点击。 */
final class BankPanel {
    private final FinanceScreen screen;

    BankPanel(FinanceScreen screen) {
        this.screen = screen;
    }

    void render(GuiGraphics g) {
        var cache = BankClientCache.get();
        if (cache.state() == BankClientCache.State.NOT_REQUESTED) {
            request();
            FinanceGui.drawClippedString(
                    screen.font(), g, "正在请求银行数据…", 12, FinanceGui.CONTENT_Y + 28, 350, FinanceGui.COL_TEXT_DIM);
            return;
        }
        if (cache.state() == BankClientCache.State.LOADING || cache.state() == BankClientCache.State.SLOW) {
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    cache.state() == BankClientCache.State.SLOW ? "银行服务响应较慢" : "正在加载银行数据…",
                    12,
                    FinanceGui.CONTENT_Y + 28,
                    350,
                    FinanceGui.COL_TEXT_DIM);
            return;
        }
        var data = cache.data();
        int y = FinanceGui.CONTENT_Y + 20;
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                "隔夜利率 " + data.overnightRateBps() + "bp  " + (data.admin() ? "保险基金 " : "保障上限 ") + data.insuranceFund(),
                12,
                y,
                370,
                FinanceGui.COL_TEXT_DIM);
        y += 15;
        FinanceGui.drawClippedString(screen.font(), g, "银行/状态", 12, y, 100, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "存款利率", 116, y, 80, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(
                screen.font(), g, data.admin() ? "资本/流动" : "公开风险", 202, y, 86, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(
                screen.font(), g, data.admin() ? "准备金/存款" : "公开状态", 294, y, 98, FinanceGui.COL_TEXT_DIM);
        y += 14;
        for (var bank : data.banks()) {
            if (y > 156) break;
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    bank.code() + "/" + FinanceFormat.label(bank.status()),
                    12,
                    y,
                    100,
                    bank.status() == finance.bank.BankStatus.ACTIVE ? FinanceGui.COL_GOOD : FinanceGui.COL_WARN);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    "活期" + bank.demandRateBps() / 100.0 + "% 定期" + bank.timeRateBps() / 100.0 + "%",
                    116,
                    y,
                    80,
                    FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    data.admin() ? bank.capitalBps() + "/" + bank.liquidityBps() : FinanceFormat.label(bank.status()),
                    202,
                    y,
                    86,
                    FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    data.admin() ? bank.reserves() + "/" + bank.deposits() : "不公开总账",
                    294,
                    y,
                    98,
                    FinanceGui.COL_TEXT);
            y += 15;
        }
        long demand = data.accounts().stream()
                .filter(a -> a.type() == finance.bank.BankAccountType.DEMAND_DEPOSIT
                        && a.ownerType() == finance.bank.CustomerType.PLAYER)
                .mapToLong(BankResponsePacket.AccountRow::balance)
                .sum();
        long company = data.accounts().stream()
                .filter(a -> a.ownerType() == finance.bank.CustomerType.COMPANY)
                .mapToLong(BankResponsePacket.AccountRow::balance)
                .sum();
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                "我的活期 " + demand + "  公司存款 " + company + "  定期 "
                        + data.timeDeposits().size(),
                12,
                166,
                370,
                FinanceGui.COL_TEXT);
        FinanceGui.drawClippedString(screen.font(), g, "金额", 50, 181, 70, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "期限", 162, 181, 60, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "间隔", 282, 181, 60, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawButton(screen.font(), g, "转", 54, 210, 36, false);
        FinanceGui.drawButton(screen.font(), g, "存", 94, 210, 36, false);
        FinanceGui.drawButton(screen.font(), g, "取", 134, 210, 36, false);
        FinanceGui.drawButton(screen.font(), g, "定", 174, 210, 36, false);
        FinanceGui.drawButton(screen.font(), g, "兑", 214, 210, 36, false);
        FinanceGui.drawButton(screen.font(), g, "贷", 254, 210, 36, false);
        FinanceGui.drawButton(screen.font(), g, "公取", 294, 210, 42, false);
        if (screen.isAdmin) FinanceGui.drawButton(screen.font(), g, "处/压", 340, 210, 48, false);
    }

    boolean click(int mx, int my) {
        var cache = BankClientCache.get();
        var data = cache.data();
        if (data == null) return false;
        var bank = data.banks().stream()
                .filter(b ->
                        b.status() == finance.bank.BankStatus.ACTIVE || b.status() == finance.bank.BankStatus.WATCH)
                .findFirst()
                .orElse(null);
        var demands = data.accounts().stream()
                .filter(a -> a.ownerType() == finance.bank.CustomerType.PLAYER
                        && a.type() == finance.bank.BankAccountType.DEMAND_DEPOSIT)
                .toList();
        var demand = demands.stream().findFirst().orElse(null);
        var transferTarget = demands.stream()
                .filter(a -> demand != null && !a.id().equals(demand.id()))
                .findFirst()
                .orElse(null);
        var matured = data.timeDeposits().stream()
                .filter(t -> t.status() == finance.bank.TimeDepositStatus.MATURED)
                .findFirst()
                .orElse(null);
        java.util.function.LongSupplier amount = () -> screen.requireAmount(screen.stockPriceBox, "金额");
        java.util.function.IntSupplier term = () -> screen.requireInt(screen.stockQuantityBox, "期限", 2, 3650),
                interval = () -> screen.requireInt(screen.companyAmountBox, "间隔", 1, 3650);
        if (my >= 208 && my <= 230) {
            BankActionPacket packet;
            if (mx >= 54 && mx < 90 && demand != null && transferTarget != null)
                packet = new BankActionPacket(
                        BankActionPacket.Action.TRANSFER, demand.id(), transferTarget.id(), amount.getAsLong(), 0, 0);
            else if (mx >= 94 && mx < 130 && bank != null)
                packet = new BankActionPacket(
                        BankActionPacket.Action.DEPOSIT, bank.id(), null, amount.getAsLong(), 0, 0);
            else if (mx >= 134 && mx < 170 && demand != null)
                packet = new BankActionPacket(
                        BankActionPacket.Action.WITHDRAW, demand.id(), null, amount.getAsLong(), 0, 0);
            else if (mx >= 174 && mx < 210 && demand != null)
                packet = new BankActionPacket(
                        BankActionPacket.Action.OPEN_TIME, demand.id(), null, amount.getAsLong(), term.getAsInt(), 0);
            else if (mx >= 214 && mx < 250 && matured != null)
                packet = new BankActionPacket(BankActionPacket.Action.REDEEM_TIME, matured.id(), null, 0, 0, 0);
            else if (mx >= 254 && mx < 290 && bank != null)
                packet = new BankActionPacket(
                        BankActionPacket.Action.APPLY_COMPANY_LOAN,
                        bank.id(),
                        null,
                        amount.getAsLong(),
                        term.getAsInt(),
                        Math.min(interval.getAsInt(), term.getAsInt() - 1));
            else if (mx >= 294 && mx < 336 && bank != null)
                packet = new BankActionPacket(
                        BankActionPacket.Action.WITHDRAW_COMPANY, bank.id(), null, amount.getAsLong(), 0, 0);
            else if (mx >= 340 && screen.isAdmin) {
                var target = data.banks().stream()
                        .filter(b -> b.status() == finance.bank.BankStatus.RESOLUTION)
                        .findFirst()
                        .orElse(null);
                packet = target == null
                        ? new BankActionPacket(BankActionPacket.Action.ADMIN_STRESS_TEST, null, null, 0, 0, 0)
                        : new BankActionPacket(BankActionPacket.Action.ADMIN_RESOLVE, target.id(), null, 0, 0, 0);
            } else return false;
            FinancePacketHandler.CHANNEL.sendToServer(packet);
            request();
            return true;
        }
        return false;
    }

    void request() {
        long requestId = BankClientCache.begin();
        FinancePacketHandler.CHANNEL.sendToServer(new BankRequestPacket(requestId));
    }
}
