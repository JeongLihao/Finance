package finance.client;

import finance.network.FinancePacketHandler;
import finance.network.MacroRequestPacket;
import finance.network.MacroResponsePacket;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 宏观页签：顶部总览与中央银行，左侧聚落民生，右侧逐个聚落的市场表。只读；央行债券、定存等操作在“更多操作”界面（{@link PlayerServicesScreen}）。
 */
public final class MacroTabs {
    private static final String[] CATEGORY_SHORT = {"食", "住", "工", "暖", "衣"};
    private static final int LEFT_X = 10, RIGHT_X = 200, RIGHT_W = 190;

    private final FinanceScreen screen;
    private int selected;
    /** 下半区显示竞争对手列表（否则显示聚落民生和市场）。 */
    private boolean showRivals;

    public MacroTabs(FinanceScreen screen) {
        this.screen = screen;
    }

    public void request() {
        FinancePacketHandler.CHANNEL.sendToServer(new MacroRequestPacket(MacroClientCache.begin()));
    }

    public void render(GuiGraphics g) {
        var font = screen.font();
        var entry = MacroClientCache.get();
        if (entry.state() == MacroClientCache.State.NOT_REQUESTED) {
            request();
            FinanceGui.drawClippedString(
                    font, g, "正在请求宏观经济数据…", 12, FinanceGui.CONTENT_Y + 28, 350, FinanceGui.COL_TEXT_DIM);
            return;
        }
        MacroResponsePacket data = entry.data();
        if (data == null) {
            String msg = entry.state() == MacroClientCache.State.SLOW ? "宏观经济服务响应较慢" : "正在加载宏观经济数据…";
            FinanceGui.drawClippedString(font, g, msg, 12, FinanceGui.CONTENT_Y + 28, 350, FinanceGui.COL_TEXT_DIM);
            return;
        }
        if (!data.enabled()) {
            FinanceGui.drawClippedString(
                    font,
                    g,
                    "宏观经济未启用（配置 macroEconomy.enabled）",
                    12,
                    FinanceGui.CONTENT_Y + 28,
                    370,
                    FinanceGui.COL_WARN);
            return;
        }
        int y = FinanceGui.CONTENT_Y + 2;
        String cpi = data.cpiX100() <= 0 ? "—" : String.format(Locale.ROOT, "%.1f", data.cpiX100() / 100.0);
        String inflation = String.format(Locale.ROOT, "%+.1f%%", data.inflationX10() / 10.0);
        FinanceGui.drawClippedString(
                font,
                g,
                "CPI " + cpi + "  通胀 " + inflation + "  人口 " + data.population() + "  生活 " + pct(data.avgSol())
                        + "  就业 " + data.employed() + "  工资 " + data.wages() + "/天",
                LEFT_X,
                y,
                380,
                FinanceGui.COL_ACCENT);
        y += 12;
        var cb = data.centralBank();
        int gap = cb.inflationBps() - cb.targetBps();
        String bank = String.format(
                Locale.ROOT,
                "央行 利率 %.2f%%  通胀 %.1f%%（目标 %.1f%%）  准备金率 %.1f%%",
                cb.rateBps() / 100.0,
                cb.inflationBps() / 100.0,
                cb.targetBps() / 100.0,
                cb.reserveBps() / 100.0);
        FinanceGui.drawClippedString(
                font,
                g,
                bank,
                LEFT_X,
                y,
                380,
                Math.abs(gap) <= 100
                        ? FinanceGui.COL_GOOD
                        : Math.abs(gap) >= 500 ? FinanceGui.COL_BAD : FinanceGui.COL_WARN);
        y += 12;
        String meeting =
                cb.nextMeetingDay() < 0 ? "议息日程未排定" : "下次议息 第 " + cb.nextMeetingDay() + " 天，倾向" + cb.guidance();
        FinanceGui.drawClippedString(
                font,
                g,
                meeting + "  公开市场：银行持票 " + cb.absorbed() + " / 回购投放 " + cb.injected(),
                LEFT_X,
                y,
                380,
                FinanceGui.COL_TEXT_DIM);
        y += 12;
        String stabilization =
                cb.stabilization().isEmpty() ? "商品平准：昨日无操作" : "商品平准：" + String.join("  ", cb.stabilization());
        FinanceGui.drawClippedString(font, g, stabilization, LEFT_X, y, 380, FinanceGui.COL_TEXT_DIM);
        y += 14;
        FinanceGui.drawSimpleSeparator(g, LEFT_X, y - 3, 380);
        if (showRivals) {
            renderRivals(g, data.rivals(), y);
        } else {
            renderPeople(g, data.settlements(), y);
            renderSettlement(g, data.settlements(), y);
        }
        screen.button(g, showRivals ? "聚落" : "对手", 286, 228, 48, showRivals, () -> showRivals = !showRivals);
        screen.button(g, "刷新", 340, 228, 48, entry.state() == MacroClientCache.State.LOADING, this::request);
        List<Integer> tiers = data.factoryTiers();
        String footer = tiers.size() == MacroResponsePacket.FACTORY_TIER_BUCKETS
                ? "机器 1档" + tiers.get(0) + " 2档" + tiers.get(1) + " 3档" + tiers.get(2) + " 停工" + tiers.get(3)
                        + " · 央行债券/工资：更多操作"
                : "央行债券、定存、工资、周报、商情：标题栏“更多操作”";
        FinanceGui.drawClippedString(font, g, footer, LEFT_X, 231, 270, FinanceGui.COL_TEXT_DIM); // 右边留给“对手”和“刷新”按钮
    }

    /** 竞争对手列表：名称、主营、人格、策略、是否上市、最近动作。 */
    private void renderRivals(GuiGraphics g, List<MacroResponsePacket.RivalRow> rivals, int top) {
        var font = screen.font();
        FinanceGui.drawClippedString(font, g, "竞争对手", LEFT_X, top, 60, FinanceGui.COL_ACCENT);
        FinanceGui.drawClippedString(font, g, "主营", LEFT_X + 74, top, 36, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(font, g, "人格", LEFT_X + 114, top, 30, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(font, g, "策略", LEFT_X + 148, top, 52, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(font, g, "上市", LEFT_X + 204, top, 26, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(font, g, "最近动作", LEFT_X + 234, top, 140, FinanceGui.COL_TEXT_DIM);
        int y = top + 13;
        if (rivals.isEmpty()) {
            FinanceGui.drawClippedString(font, g, "还没有 NPC 竞争公司的数据，等一次日结", LEFT_X, y, 370, FinanceGui.COL_TEXT_DIM);
            return;
        }
        int index = 0;
        for (MacroResponsePacket.RivalRow r : rivals) {
            if (y > 216) break;
            g.fill(
                    LEFT_X - 2,
                    y - 1,
                    LEFT_X + 378,
                    y + 10,
                    index++ % 2 == 0 ? FinanceGui.COL_ROW_EVEN : FinanceGui.COL_ROW_ODD);
            FinanceGui.drawClippedString(font, g, r.name(), LEFT_X, y, 70, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(font, g, r.commodity(), LEFT_X + 74, y, 36, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(font, g, r.personality(), LEFT_X + 114, y, 30, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(font, g, r.strategy(), LEFT_X + 148, y, 52, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(
                    font,
                    g,
                    r.listed() ? "是" : "—",
                    LEFT_X + 204,
                    y,
                    26,
                    r.listed() ? FinanceGui.COL_GOOD : FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(
                    font, g, actionLabel(r.lastAction()), LEFT_X + 234, y, 142, FinanceGui.COL_TEXT_DIM);
            y += 12;
        }
    }

    /** 决策记录的动作代码转中文。 */
    public static String actionLabel(String raw) {
        if (raw == null || raw.isBlank()) return "—";
        int space = raw.indexOf(' ');
        String code = space < 0 ? raw : raw.substring(0, space);
        String detail = space < 0 ? "" : raw.substring(space + 1);
        String name =
                switch (code) {
                    case "SPAWN" -> "入场";
                    case "STRATEGY" -> "换策略";
                    case "UPGRADE" -> "升级";
                    case "CONTRACT" -> "发合同";
                    case "IPO" -> "上市";
                    case "HOSTILE_START" -> "开始建仓";
                    case "HOSTILE_ABANDON" -> "放弃收购";
                    case "HOSTILE_FAIL" -> "要约失败";
                    case "TENDER" -> "发起要约";
                    case "ACTIVISM" -> "股东提案";
                    default -> code;
                };
        return detail.isBlank() ? name : name + " " + detail;
    }

    /** 左栏：各聚落人口与生活水平一览，点右侧箭头切换详情。 */
    private void renderPeople(GuiGraphics g, List<MacroResponsePacket.SettlementRow> settlements, int top) {
        var font = screen.font();
        FinanceGui.drawClippedString(font, g, "聚落        人口   生活   就业", LEFT_X, top, 180, FinanceGui.COL_TEXT_DIM);
        int y = top + 12;
        int index = 0;
        for (MacroResponsePacket.SettlementRow row : settlements) {
            if (y > 216) break;
            boolean sel = index == Math.floorMod(selected, Math.max(1, settlements.size()));
            g.fill(
                    LEFT_X - 2,
                    y - 1,
                    LEFT_X + 180,
                    y + 10,
                    sel
                            ? FinanceGui.COL_ROW_SELECT
                            : index % 2 == 0 ? FinanceGui.COL_ROW_EVEN : FinanceGui.COL_ROW_ODD);
            FinanceGui.drawClippedString(font, g, row.name(), LEFT_X, y, 58, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    font, g, String.valueOf(row.population()), LEFT_X + 62, y, 34, FinanceGui.COL_TEXT);
            int color = row.sol() >= 7_000
                    ? FinanceGui.COL_GOOD
                    : row.sol() < 4_000 ? FinanceGui.COL_BAD : FinanceGui.COL_TEXT;
            FinanceGui.drawClippedString(font, g, pct(row.sol()), LEFT_X + 100, y, 36, color);
            FinanceGui.drawClippedString(
                    font, g, String.valueOf(row.employed()), LEFT_X + 140, y, 38, FinanceGui.COL_TEXT_DIM);
            y += 12;
            index++;
        }
        if (settlements.isEmpty())
            FinanceGui.drawClippedString(font, g, "还没有聚落", LEFT_X, y, 180, FinanceGui.COL_TEXT_DIM);
    }

    private void renderSettlement(GuiGraphics g, List<MacroResponsePacket.SettlementRow> settlements, int top) {
        var font = screen.font();
        if (settlements.isEmpty()) {
            FinanceGui.drawClippedString(font, g, "还没有聚落数据", RIGHT_X, top, RIGHT_W, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(font, g, "放置聚落贸易站，等一次日结", RIGHT_X, top + 12, RIGHT_W, FinanceGui.COL_TEXT_DIM);
            return;
        }
        selected = Math.floorMod(selected, settlements.size());
        MacroResponsePacket.SettlementRow s = settlements.get(selected);
        screen.button(g, "<", RIGHT_X, top - 2, 12, false, () -> selected--);
        FinanceGui.drawCenteredClippedString(
                font,
                g,
                s.name() + " " + (selected + 1) + "/" + settlements.size(),
                RIGHT_X + 14,
                top,
                RIGHT_W - 28,
                FinanceGui.COL_ACCENT);
        screen.button(g, ">", RIGHT_X + RIGHT_W - 12, top - 2, 12, false, () -> selected++);
        int y = top + 13;
        FinanceGui.drawClippedString(
                font,
                g,
                "人口 " + s.population() + "  生活 " + pct(s.sol()) + "  就业 " + s.employed() + "  收入 " + s.income(),
                RIGHT_X,
                y,
                RIGHT_W,
                FinanceGui.COL_TEXT);
        y += 11;
        StringBuilder f = new StringBuilder();
        for (int i = 0; i < s.fulfilmentPct().size() && i < CATEGORY_SHORT.length; i++) {
            int v = s.fulfilmentPct().get(i);
            f.append(CATEGORY_SHORT[i]).append(v < 0 ? "—" : v + "%").append(' ');
        }
        FinanceGui.drawClippedString(font, g, f.toString().trim(), RIGHT_X, y, RIGHT_W, FinanceGui.COL_TEXT_DIM);
        y += 12;
        FinanceGui.drawClippedString(font, g, "商品", RIGHT_X, y, 40, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(font, g, "需/供", RIGHT_X + 42, y, 60, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(font, g, "系数", RIGHT_X + 110, y, 40, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(font, g, "本地价", RIGHT_X + 152, y, 38, FinanceGui.COL_TEXT_DIM);
        y += 11;
        int shown = 0;
        for (MacroResponsePacket.GoodRow row : s.goods()) {
            if (shown++ >= 8 || y > 220) break;
            g.fill(
                    RIGHT_X - 2,
                    y - 1,
                    RIGHT_X + RIGHT_W,
                    y + 9,
                    shown % 2 == 0 ? FinanceGui.COL_ROW_EVEN : FinanceGui.COL_ROW_ODD);
            FinanceGui.drawClippedString(font, g, row.commodity(), RIGHT_X, y, 40, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    font, g, row.demand() + "/" + row.supply(), RIGHT_X + 42, y, 66, FinanceGui.COL_TEXT);
            int color = row.factorBps() > 11_000
                    ? FinanceGui.COL_BAD
                    : row.factorBps() < 9_000 ? FinanceGui.COL_GOOD : FinanceGui.COL_TEXT;
            FinanceGui.drawClippedString(font, g, pct(row.factorBps()), RIGHT_X + 110, y, 40, color);
            FinanceGui.drawClippedString(
                    font, g, String.valueOf(row.localPrice()), RIGHT_X + 152, y, 38, FinanceGui.COL_TEXT);
            y += 10;
        }
    }

    private static String pct(int bps) {
        return String.format(Locale.ROOT, "%.0f%%", bps / 100.0);
    }
}
