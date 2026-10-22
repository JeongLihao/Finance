package finance.client;

import finance.client.PlayerServicesForm.InvalidInput;
import finance.client.PlayerServicesForm.Page;
import finance.network.FinancePacketHandler;
import finance.network.PlayerServicesActionPacket;
import finance.network.PlayerServicesRequestPacket;
import finance.network.PlayerServicesResponsePacket;
import finance.network.PlayerServicesResponsePacket.BondHolding;
import finance.network.PlayerServicesResponsePacket.DepositRow;
import finance.network.PlayerServicesResponsePacket.SqueezeRow;
import finance.network.PlayerServicesResponsePacket.TenderRow;
import finance.playerservice.PlayerServicesService.Action;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * 更多操作：把原来只能用命令完成的玩家功能做成按钮——融资融券、我的银行、要约收购（接受/撤回/发起/强制私有化）、
 * 中央银行（央行概况、央行债券认购/赎回、定存提前支取）、宏观行动（工资、领报纸）、财报日历与季报、商情调查，以及实体现金的存取和绿宝石兑换。
 * 沿用金融中心的米色面板风格，关闭后回到上一个界面。
 */
public final class PlayerServicesScreen extends Screen {
    /** 高 240：原版“自动”界面尺寸下可用高度常为 240，超出部分会被裁掉。 */
    static final int PANEL_WIDTH = 384, PANEL_HEIGHT = 240;

    private static final int TITLE_H = 18, PAGE_Y = 20, CONTENT_Y = 40, LINE_H = 11;
    private static final int LABEL_Y = 162, BOX_Y = 172, BUTTON_Y1 = 192, BUTTON_Y2 = 208, STATUS_Y = 226;
    private static final int BOX_W = 88, BOX_GAP = 6;

    private static Page lastPage = Page.MARGIN;

    private record Hit(int x, int y, int w, int h, Runnable run) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private record Btn(String label, Runnable run) {}

    private final Screen parent;
    private Page page;
    private final EditBox[] boxes = new EditBox[PlayerServicesForm.BOXES];
    private final List<Hit> hits = new ArrayList<>();
    private String status = "";
    private UUID selectedTender;
    private UUID selectedSqueeze;
    private UUID selectedBond;
    private UUID selectedDeposit;
    private int left, top;

    public PlayerServicesScreen(Screen parent, Page page) {
        super(Component.literal("更多操作"));
        this.parent = parent;
        this.page = page == null ? lastPage : page;
    }

    public static void open() {
        open(null);
    }

    public static void open(Page page) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new PlayerServicesScreen(minecraft.screen, page));
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        for (int i = 0; i < boxes.length; i++) {
            String old = boxes[i] == null ? "" : boxes[i].getValue();
            EditBox box =
                    new EditBox(font, left + 8 + i * (BOX_W + BOX_GAP), top + BOX_Y, BOX_W, 14, Component.empty());
            box.setMaxLength(32);
            box.setValue(old);
            boxes[i] = addRenderableWidget(box);
        }
        updateBoxes();
        request();
    }

    private void request() {
        FinancePacketHandler.CHANNEL.sendToServer(new PlayerServicesRequestPacket(PlayerServicesClientCache.begin()));
    }

    public void setStatus(String message) {
        status = message == null ? "" : message;
    }

    private PlayerServicesResponsePacket data() {
        return PlayerServicesClientCache.get().data();
    }

    private void updateBoxes() {
        String[] labels = PlayerServicesForm.labels(page, data());
        for (int i = 0; i < boxes.length; i++) {
            boolean visible = labels[i] != null;
            boxes[i].visible = visible;
            boxes[i].active = visible;
            if (!visible && boxes[i].isFocused()) setFocused(null);
        }
    }

    private void switchPage(Page next) {
        if (next == page) return;
        page = next;
        lastPage = next;
        for (EditBox box : boxes) box.setValue("");
        setFocused(null);
        status = "";
        updateBoxes();
    }

    // ---------------------------------------------------------------- 渲染

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        hits.clear();
        updateBoxes();
        g.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, FinanceGui.COL_BG);
        FinanceGui.drawSimpleBorder(g, left, top, PANEL_WIDTH, PANEL_HEIGHT, FinanceGui.COL_PANEL_BORDER);
        g.fill(left + 1, top + 1, left + PANEL_WIDTH - 1, top + TITLE_H - 1, 0xFFF4F0E2);
        FinanceGui.drawClippedString(font, g, "更多操作", left + 10, top + 5, 80, FinanceGui.COL_TEXT);
        FinanceGui.drawClippedString(
                font, g, "原来要用命令的功能，都可以在这里点按钮完成", left + 70, top + 5, 250, FinanceGui.COL_TEXT_DIM);
        button(g, "返回", left + PANEL_WIDTH - 46, top + 2, 38, false, this::onClose);

        int x = left + 8;
        int pageW = (PANEL_WIDTH - 16 - (Page.values().length - 1) * 4) / Page.values().length;
        for (Page p : Page.values()) {
            button(g, p.label(), x, top + PAGE_Y, pageW, p == page, () -> switchPage(p));
            x += pageW + 4;
        }
        FinanceGui.drawSimpleSeparator(g, left + 6, top + PAGE_Y + 16, PANEL_WIDTH - 12);

        var entry = PlayerServicesClientCache.get();
        PlayerServicesResponsePacket data = entry.data();
        if (data == null) {
            String msg = entry.state() == PlayerServicesClientCache.State.SLOW ? "服务器响应较慢，可点“刷新”重试" : "正在加载…";
            line(g, msg, 0, FinanceGui.COL_TEXT_DIM);
        } else {
            switch (page) {
                case MARGIN -> renderMargin(g, data);
                case BANK -> renderBank(g, data);
                case TENDER -> renderTender(g, data);
                case CENTRAL_BANK -> renderCentralBank(g, data);
                case MACRO -> renderMacro(g, data);
                case RESEARCH -> renderResearch(g, data);
                case CASH -> renderCash(g, data);
            }
        }

        String[] labels = PlayerServicesForm.labels(page, data);
        for (int i = 0; i < labels.length; i++) {
            if (labels[i] != null) {
                FinanceGui.drawClippedString(
                        font,
                        g,
                        labels[i],
                        left + 8 + i * (BOX_W + BOX_GAP),
                        top + LABEL_Y,
                        BOX_W,
                        FinanceGui.COL_TEXT_DIM);
            }
        }
        layoutButtons(g, data == null ? List.of() : buttons(data));
        int color = status.isEmpty() ? FinanceGui.COL_TEXT_DIM : FinanceGui.COL_ACCENT;
        FinanceGui.drawClippedString(
                font,
                g,
                status.isEmpty() ? "提示：先选中列表里的一项，再填写数字并点按钮" : status,
                left + 8,
                top + STATUS_Y,
                PANEL_WIDTH - 16,
                color);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void line(GuiGraphics g, String text, int row, int color) {
        FinanceGui.drawClippedString(font, g, text, left + 10, top + CONTENT_Y + row * LINE_H, PANEL_WIDTH - 20, color);
    }

    /** 自动换行，返回占用的行数。 */
    private int wrapped(GuiGraphics g, String text, int x, int row, int width, int maxRows, int color) {
        List<FormattedCharSequence> parts = font.split(Component.literal(text), width);
        int n = Math.min(parts.size(), maxRows);
        for (int i = 0; i < n; i++)
            g.drawString(font, parts.get(i), x, top + CONTENT_Y + (row + i) * LINE_H, color, false);
        return Math.max(1, n);
    }

    private void button(GuiGraphics g, String label, int x, int y, int w, boolean active, Runnable run) {
        FinanceGui.drawButton(font, g, label, x, y, w, active);
        hits.add(new Hit(x, y, w, 13, run));
    }

    private void layoutButtons(GuiGraphics g, List<Btn> buttons) {
        int perRow = 5;
        for (int i = 0; i < buttons.size(); i++) {
            int row = i / perRow, col = i % perRow;
            int count = Math.min(perRow, buttons.size() - row * perRow);
            int w = (PANEL_WIDTH - 16 - (count - 1) * 4) / count;
            Btn b = buttons.get(i);
            button(g, b.label(), left + 8 + col * (w + 4), top + (row == 0 ? BUTTON_Y1 : BUTTON_Y2), w, false, () -> {
                try {
                    b.run().run();
                } catch (InvalidInput invalid) {
                    status = invalid.getMessage();
                }
            });
        }
    }

    private void renderMargin(GuiGraphics g, PlayerServicesResponsePacket data) {
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
                left + 10,
                7,
                PANEL_WIDTH - 20,
                3,
                FinanceGui.COL_TEXT_DIM);
    }

    private void renderBank(GuiGraphics g, PlayerServicesResponsePacket data) {
        var b = data.bank();
        if (!b.owned()) {
            line(g, "你名下还没有银行。", 0, FinanceGui.COL_ACCENT);
            line(g, "最低实缴资本 " + b.minCapital() + "    牌照费 " + b.charterFee(), 1, FinanceGui.COL_TEXT);
            wrapped(
                    g,
                    "在“代码 名称”里填写银行代码和名称（用空格隔开，例如：MYBK 我的银行），再填资本金，点“申请牌照”。" + "资本金和牌照费从你的账户扣除。",
                    left + 10,
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
                left + 10,
                3,
                PANEL_WIDTH - 20,
                5,
                FinanceGui.COL_TEXT_DIM);
    }

    private void renderTender(GuiGraphics g, PlayerServicesResponsePacket data) {
        int y = top + CONTENT_Y;
        FinanceGui.drawClippedString(font, g, "进行中的要约", left + 10, y, 90, FinanceGui.COL_ACCENT);
        FinanceGui.drawClippedString(
                font, g, "出价方      价格   已接受/目标   截止   我的持股", left + 100, y, 270, FinanceGui.COL_TEXT_DIM);
        y += LINE_H + 1;
        if (data.tenders().isEmpty()) {
            FinanceGui.drawClippedString(font, g, "当前没有进行中的要约", left + 10, y, 200, FinanceGui.COL_TEXT_DIM);
            y += LINE_H;
        }
        int index = 0;
        for (TenderRow t : data.tenders()) {
            if (y > top + CONTENT_Y + 6 * LINE_H) break;
            boolean sel = t.id().equals(selectedTender);
            g.fill(
                    left + 8,
                    y - 1,
                    left + PANEL_WIDTH - 8,
                    y + 10,
                    sel
                            ? FinanceGui.COL_ROW_SELECT
                            : index++ % 2 == 0 ? FinanceGui.COL_ROW_EVEN : FinanceGui.COL_ROW_ODD);
            FinanceGui.drawClippedString(
                    font, g, t.target() + (t.squeeze() ? "（私有化）" : ""), left + 10, y, 88, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    font, g, t.buyer(), left + 100, y, 56, t.mine() ? FinanceGui.COL_GOOD : FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(font, g, String.valueOf(t.price()), left + 160, y, 40, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    font, g, t.accepted() + "/" + t.targetShares(), left + 202, y, 70, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(font, g, "D" + t.endDay(), left + 276, y, 34, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(font, g, String.valueOf(t.myShares()), left + 314, y, 56, FinanceGui.COL_TEXT);
            UUID id = t.id();
            hits.add(new Hit(left + 8, y - 1, PANEL_WIDTH - 16, LINE_H, () -> {
                selectedTender = id;
                selectedSqueeze = null;
            }));
            y += LINE_H;
        }
        y += 3;
        FinanceGui.drawClippedString(font, g, "可强制私有化（持股 ≥ 90%）", left + 10, y, 200, FinanceGui.COL_ACCENT);
        y += LINE_H + 1;
        if (data.squeezable().isEmpty()) {
            FinanceGui.drawClippedString(font, g, "暂无", left + 10, y, 200, FinanceGui.COL_TEXT_DIM);
        }
        for (SqueezeRow s : data.squeezable()) {
            if (y > top + LABEL_Y - LINE_H) break;
            boolean sel = s.companyId().equals(selectedSqueeze);
            g.fill(
                    left + 8,
                    y - 1,
                    left + PANEL_WIDTH - 8,
                    y + 10,
                    sel ? FinanceGui.COL_ROW_SELECT : FinanceGui.COL_ROW_EVEN);
            FinanceGui.drawClippedString(
                    font, g, s.name() + "  持股 " + s.percentX10() / 10.0 + "%", left + 10, y, 300, FinanceGui.COL_TEXT);
            UUID id = s.companyId();
            hits.add(new Hit(left + 8, y - 1, PANEL_WIDTH - 16, LINE_H, () -> {
                selectedSqueeze = id;
                selectedTender = null;
            }));
            y += LINE_H;
        }
    }

    private void renderCentralBank(GuiGraphics g, PlayerServicesResponsePacket data) {
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

        int y = top + CONTENT_Y + 3 * LINE_H + 3;
        FinanceGui.drawClippedString(
                font, g, "我的央行债券（提前赎回只退本金，手续费 " + cb.bondEarlyFee() + "）", left + 10, y, 360, FinanceGui.COL_TEXT_DIM);
        y += LINE_H;
        if (cb.bonds().isEmpty()) {
            FinanceGui.drawClippedString(font, g, "暂无", left + 10, y, 100, FinanceGui.COL_TEXT_DIM);
            y += LINE_H;
        }
        for (BondHolding h : cb.bonds()) {
            if (y > top + CONTENT_Y + 6 * LINE_H) break;
            boolean sel = h.id().equals(selectedBond);
            g.fill(
                    left + 8,
                    y - 1,
                    left + PANEL_WIDTH - 8,
                    y + 10,
                    sel ? FinanceGui.COL_ROW_SELECT : FinanceGui.COL_ROW_EVEN);
            FinanceGui.drawClippedString(
                    font,
                    g,
                    h.termDays() + " 天  本金 " + h.principal() + "  年利率 " + String.format("%.2f%%", h.rateBps() / 100.0)
                            + "  第 " + h.maturityDay() + " 天到期",
                    left + 10,
                    y,
                    360,
                    FinanceGui.COL_TEXT);
            UUID id = h.id();
            hits.add(new Hit(left + 8, y - 1, PANEL_WIDTH - 16, LINE_H, () -> {
                selectedBond = id;
                selectedDeposit = null;
            }));
            y += LINE_H;
        }
        y += 2;
        FinanceGui.drawClippedString(font, g, "我的定期存款（提前支取只退本金，已计利息作废）", left + 10, y, 360, FinanceGui.COL_TEXT_DIM);
        y += LINE_H;
        if (cb.deposits().isEmpty())
            FinanceGui.drawClippedString(font, g, "暂无（在金融中心“金融 → 银行”开定期）", left + 10, y, 300, FinanceGui.COL_TEXT_DIM);
        for (DepositRow d : cb.deposits()) {
            if (y > top + LABEL_Y - LINE_H) break;
            boolean sel = d.id().equals(selectedDeposit);
            g.fill(
                    left + 8,
                    y - 1,
                    left + PANEL_WIDTH - 8,
                    y + 10,
                    sel ? FinanceGui.COL_ROW_SELECT : FinanceGui.COL_ROW_EVEN);
            FinanceGui.drawClippedString(
                    font,
                    g,
                    d.bank() + "  本金 " + d.principal() + "  年利率 " + String.format("%.2f%%", d.rateBps() / 100.0)
                            + "  已计利息 " + d.accrued() + "  第 " + d.maturityDay() + " 天到期",
                    left + 10,
                    y,
                    360,
                    FinanceGui.COL_TEXT);
            UUID id = d.id();
            hits.add(new Hit(left + 8, y - 1, PANEL_WIDTH - 16, LINE_H, () -> {
                selectedDeposit = id;
                selectedBond = null;
            }));
            y += LINE_H;
        }
    }

    private void renderMacro(GuiGraphics g, PlayerServicesResponsePacket data) {
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
                left + 10,
                4,
                PANEL_WIDTH - 20,
                3,
                FinanceGui.COL_TEXT_DIM);
    }

    private void renderResearch(GuiGraphics g, PlayerServicesResponsePacket data) {
        int row = 0;
        for (String s : data.calendar()) {
            if (row >= 5) break;
            line(g, s, row++, row == 1 ? FinanceGui.COL_ACCENT : FinanceGui.COL_TEXT);
        }
        int sepY = top + CONTENT_Y + 5 * LINE_H + 2;
        FinanceGui.drawSimpleSeparator(g, left + 8, sepY, PANEL_WIDTH - 16);
        row = 6;
        if (data.detailTitle().isEmpty()) {
            wrapped(
                    g,
                    "输入股票代码点“查询季报”；或点“商情调查”（收费 " + data.macro().radarCost() + "）看各地的价差和商机。结果显示在这里。",
                    left + 10,
                    row,
                    PANEL_WIDTH - 20,
                    3,
                    FinanceGui.COL_TEXT_DIM);
            return;
        }
        line(g, data.detailTitle(), row++, FinanceGui.COL_ACCENT);
        for (String s : data.detail()) {
            if (row > 10) break;
            row += wrapped(g, s, left + 10, row, PANEL_WIDTH - 20, 11 - row, FinanceGui.COL_TEXT);
        }
    }

    private void renderCash(GuiGraphics g, PlayerServicesResponsePacket data) {
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
                left + 10,
                4,
                PANEL_WIDTH - 20,
                3,
                FinanceGui.COL_TEXT_DIM);
        wrapped(
                g,
                "绿宝石用现金兑换（不经过账户）：先取出现金再买绿宝石，卖绿宝石得到的是现金。买卖有点差，" + "汇率随全服买卖浮动，没有每日额度。",
                left + 10,
                8,
                PANEL_WIDTH - 20,
                2,
                FinanceGui.COL_TEXT_DIM);
    }

    // ---------------------------------------------------------------- 按钮

    private String box(int i) {
        return boxes[i].getValue();
    }

    private void send(Action action, String text, long a, long b, long c, long d) {
        FinancePacketHandler.CHANNEL.sendToServer(new PlayerServicesActionPacket(action, text, a, b, c, d));
        status = "已提交，等待服务器处理…";
    }

    private List<Btn> buttons(PlayerServicesResponsePacket data) {
        List<Btn> list = new ArrayList<>();
        switch (page) {
            case MARGIN -> {
                list.add(new Btn(
                        "存入保证金",
                        () -> send(Action.MARGIN_DEPOSIT, "", PlayerServicesForm.positive(box(2), "金额"), 0, 0, 0)));
                list.add(new Btn(
                        "取出保证金",
                        () -> send(Action.MARGIN_WITHDRAW, "", PlayerServicesForm.positive(box(2), "金额"), 0, 0, 0)));
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
                        "偿还融资",
                        () -> send(Action.MARGIN_REPAY, "", PlayerServicesForm.positive(box(2), "金额"), 0, 0, 0)));
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
                        () -> send(
                                Action.MARGIN_REPAY_BAD_DEBT, "", PlayerServicesForm.positive(box(2), "金额"), 0, 0, 0)));
                list.add(new Btn("刷新", this::refresh));
            }
            case BANK -> {
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
                            "分红",
                            () -> send(
                                    Action.BANK_DIVIDEND, "", PlayerServicesForm.positive(box(3), "分红金额"), 0, 0, 0)));
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
            }
            case TENDER -> {
                list.add(new Btn("接受选中要约", () -> {
                    if (selectedTender == null) throw new InvalidInput("请先在列表里选中一个要约");
                    send(
                            Action.TENDER_ACCEPT,
                            selectedTender.toString(),
                            PlayerServicesForm.positive(box(2), "股数"),
                            0,
                            0,
                            0);
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
            }
            case CENTRAL_BANK -> {
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
            }
            case MACRO -> {
                list.add(new Btn(
                        "设定工资",
                        () -> send(Action.MACRO_WAGE, "", PlayerServicesForm.number(box(0), "日工资", false), 0, 0, 0)));
                list.add(new Btn("领取周报", () -> send(Action.MACRO_NEWS, "", 0, 0, 0, 0)));
                list.add(new Btn("刷新", this::refresh));
            }
            case RESEARCH -> {
                list.add(new Btn(
                        "查询季报",
                        () -> send(Action.EARNINGS_REPORT, PlayerServicesForm.text(box(0), "股票代码"), 0, 0, 0, 0)));
                list.add(new Btn(
                        "商情调查（" + data.macro().radarCost() + "）", () -> send(Action.MACRO_RADAR, "", 0, 0, 0, 0)));
                list.add(new Btn("刷新", this::refresh));
            }
            case CASH -> {
                list.add(new Btn(
                        "取出现金",
                        () -> send(Action.CASH_WITHDRAW, "", PlayerServicesForm.positive(box(0), "金额"), 0, 0, 0)));
                list.add(new Btn(
                        "存入账户",
                        () -> send(Action.CASH_DEPOSIT, "", PlayerServicesForm.number(box(0), "金额", true), 0, 0, 0)));
                list.add(new Btn(
                        "卖出绿宝石",
                        () -> send(Action.EMERALD_SELL, "", PlayerServicesForm.positive(box(1), "绿宝石数量"), 0, 0, 0)));
                list.add(new Btn(
                        "买入绿宝石",
                        () -> send(Action.EMERALD_BUY, "", PlayerServicesForm.positive(box(1), "绿宝石数量"), 0, 0, 0)));
                list.add(new Btn("刷新", this::refresh));
            }
        }
        return list;
    }

    private void refresh() {
        request();
        status = "正在刷新…";
    }

    // ---------------------------------------------------------------- 输入

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        setFocused(null);
        if (button != 0) return false;
        for (Hit hit : List.copyOf(hits)) {
            if (hit.contains(mouseX, mouseY)) {
                hit.run().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
