package finance.client;

import finance.network.FinancePacketHandler;
import finance.network.MacroRequestPacket;
import finance.network.MacroResponsePacket;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.Locale;

/**
 * 宏观页签：左侧总览与利益集团，右侧逐个聚落的市场表。只读；操作在“更多操作”界面（{@link PlayerServicesScreen}）。
 */
public final class MacroTabs {
    private static final String[] CATEGORY_SHORT = {"食", "住", "工", "暖", "衣"};
    private static final int LEFT_X = 10, RIGHT_X = 200, RIGHT_W = 190;

    private final FinanceScreen screen;
    private int selected;
    /** 下半区显示竞争对手列表（否则显示利益集团和聚落）。 */
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
            FinanceGui.drawClippedString(font, g, "正在请求宏观经济数据…", 12, FinanceGui.CONTENT_Y + 28, 350, FinanceGui.COL_TEXT_DIM);
            return;
        }
        MacroResponsePacket data = entry.data();
        if (data == null) {
            String msg = entry.state() == MacroClientCache.State.SLOW ? "宏观经济服务响应较慢" : "正在加载宏观经济数据…";
            FinanceGui.drawClippedString(font, g, msg, 12, FinanceGui.CONTENT_Y + 28, 350, FinanceGui.COL_TEXT_DIM);
            return;
        }
        if (!data.enabled()) {
            FinanceGui.drawClippedString(font, g, "宏观经济未启用（配置 macroEconomy.enabled）", 12, FinanceGui.CONTENT_Y + 28, 370, FinanceGui.COL_WARN);
            return;
        }
        int y = FinanceGui.CONTENT_Y + 2;
        String cpi = data.cpiX100() <= 0 ? "—" : String.format(Locale.ROOT, "%.1f", data.cpiX100() / 100.0);
        String inflation = String.format(Locale.ROOT, "%+.1f%%", data.inflationX10() / 10.0);
        FinanceGui.drawClippedString(font, g, "CPI " + cpi + "  通胀 " + inflation + "  人口 " + data.population()
                + "  生活 " + pct(data.avgSol()) + "  就业 " + data.employed() + "  工资 " + data.wages() + "/天",
                LEFT_X, y, 380, FinanceGui.COL_ACCENT);
        y += 12;
        FinanceGui.drawClippedString(font, g, String.join("  ", data.laws()), LEFT_X, y, 380, FinanceGui.COL_TEXT_DIM);
        y += 12;
        String budget = "国库 " + data.treasury() + "  债务 " + data.debt() + "  昨日收入 " + data.revenue()
                + "  支出 " + data.expense() + (data.austerity() ? "  紧缩中：福利停发" : "");
        FinanceGui.drawClippedString(font, g, budget, LEFT_X, y, 380,
                data.austerity() ? FinanceGui.COL_BAD : data.revenue() >= data.expense() ? FinanceGui.COL_GOOD : FinanceGui.COL_WARN);
        y += 12;
        String flags = (data.strike() ? "工会罢工：雇工产量减半  " : "") + (data.unrest() ? "民众骚乱：人口增长减半" : "");
        if (!flags.isEmpty()) {
            FinanceGui.drawClippedString(font, g, flags, LEFT_X, y, 380, FinanceGui.COL_BAD);
        } else if (!data.proposal().isEmpty()) {
            FinanceGui.drawClippedString(font, g, data.proposal(), LEFT_X, y, 380, FinanceGui.COL_WARN);
        }
        y += 14;
        FinanceGui.drawSimpleSeparator(g, LEFT_X, y - 3, 380);
        if (showRivals) {
            renderRivals(g, data.rivals(), y);
        } else {
            renderGroups(g, data.groups(), y);
            renderSettlement(g, data.settlements(), y);
        }
        FinanceGui.drawButton(font, g, showRivals ? "聚落" : "对手", 286, 228, 48, showRivals);
        FinanceGui.drawButton(font, g, "刷新", 340, 228, 48, entry.state() == MacroClientCache.State.LOADING);
        List<Integer> tiers = data.factoryTiers();
        String footer = tiers.size() == MacroResponsePacket.FACTORY_TIER_BUCKETS
                ? "工厂 手工" + tiers.get(0) + " 改良" + tiers.get(1) + " 工业" + tiers.get(2) + " 停产" + tiers.get(3)
                        + " · 工资/法律/游说：更多操作"
                : "工资、法律、游说、周报、商情：标题栏“更多操作”";
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
            g.fill(LEFT_X - 2, y - 1, LEFT_X + 378, y + 10, index++ % 2 == 0 ? FinanceGui.COL_ROW_EVEN : FinanceGui.COL_ROW_ODD);
            FinanceGui.drawClippedString(font, g, r.name(), LEFT_X, y, 70, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(font, g, r.commodity(), LEFT_X + 74, y, 36, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(font, g, r.personality(), LEFT_X + 114, y, 30, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(font, g, r.strategy(), LEFT_X + 148, y, 52, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(font, g, r.listed() ? "是" : "—", LEFT_X + 204, y, 26,
                    r.listed() ? FinanceGui.COL_GOOD : FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(font, g, actionLabel(r.lastAction()), LEFT_X + 234, y, 142, FinanceGui.COL_TEXT_DIM);
            y += 12;
        }
    }

    /** 决策记录的动作代码转中文。 */
    public static String actionLabel(String raw) {
        if (raw == null || raw.isBlank()) return "—";
        int space = raw.indexOf(' ');
        String code = space < 0 ? raw : raw.substring(0, space);
        String detail = space < 0 ? "" : raw.substring(space + 1);
        String name = switch (code) {
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

    private void renderGroups(GuiGraphics g, List<MacroResponsePacket.GroupRow> groups, int top) {
        var font = screen.font();
        FinanceGui.drawClippedString(font, g, "利益集团   影响力        满意", LEFT_X, top, 180, FinanceGui.COL_TEXT_DIM);
        int y = top + 12;
        for (MacroResponsePacket.GroupRow row : groups) {
            FinanceGui.drawClippedString(font, g, row.name(), LEFT_X, y, 56, FinanceGui.COL_TEXT);
            int barX = LEFT_X + 58, barW = 64;
            g.fill(barX, y + 1, barX + barW, y + 8, FinanceGui.COL_ROW_ODD);
            int filled = (int) Math.round(barW * Math.max(0, Math.min(10_000, row.cloutBps())) / 10_000.0);
            g.fill(barX, y + 1, barX + filled, y + 8, FinanceGui.COL_ACCENT);
            FinanceGui.drawClippedString(font, g, pct(row.cloutBps()), barX + barW + 3, y, 34, FinanceGui.COL_TEXT_DIM);
            int color = row.approval() > 0 ? FinanceGui.COL_GOOD : row.approval() <= -5 ? FinanceGui.COL_BAD
                    : row.approval() < 0 ? FinanceGui.COL_WARN : FinanceGui.COL_TEXT;
            FinanceGui.drawClippedString(font, g, String.format(Locale.ROOT, "%+d", row.approval()), LEFT_X + 164, y, 24, color);
            y += 12;
        }
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
        FinanceGui.drawButton(font, g, "<", RIGHT_X, top - 2, 12, false);
        FinanceGui.drawCenteredClippedString(font, g, s.name() + " " + (selected + 1) + "/" + settlements.size(),
                RIGHT_X + 14, top, RIGHT_W - 28, FinanceGui.COL_ACCENT);
        FinanceGui.drawButton(font, g, ">", RIGHT_X + RIGHT_W - 12, top - 2, 12, false);
        int y = top + 13;
        FinanceGui.drawClippedString(font, g, "人口 " + s.population() + "  生活 " + pct(s.sol()) + "  就业 " + s.employed()
                + "  收入 " + s.income(), RIGHT_X, y, RIGHT_W, s.unrest() ? FinanceGui.COL_BAD : FinanceGui.COL_TEXT);
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
            g.fill(RIGHT_X - 2, y - 1, RIGHT_X + RIGHT_W, y + 9, shown % 2 == 0 ? FinanceGui.COL_ROW_EVEN : FinanceGui.COL_ROW_ODD);
            FinanceGui.drawClippedString(font, g, row.commodity(), RIGHT_X, y, 40, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(font, g, row.demand() + "/" + row.supply(), RIGHT_X + 42, y, 66, FinanceGui.COL_TEXT);
            int color = row.factorBps() > 11_000 ? FinanceGui.COL_BAD : row.factorBps() < 9_000 ? FinanceGui.COL_GOOD : FinanceGui.COL_TEXT;
            FinanceGui.drawClippedString(font, g, pct(row.factorBps()), RIGHT_X + 110, y, 40, color);
            FinanceGui.drawClippedString(font, g, String.valueOf(row.localPrice()), RIGHT_X + 152, y, 38, FinanceGui.COL_TEXT);
            y += 10;
        }
    }

    public boolean handleClick(int mx, int my) {
        int top = FinanceGui.CONTENT_Y + 2 + 12 + 12 + 12 + 14;
        if (!showRivals && my >= top - 2 && my < top + 10) {
            if (mx >= RIGHT_X && mx < RIGHT_X + 12) { selected--; return true; }
            if (mx >= RIGHT_X + RIGHT_W - 12 && mx < RIGHT_X + RIGHT_W) { selected++; return true; }
        }
        if (mx >= 286 && mx < 334 && my >= 228 && my < 241) {
            showRivals = !showRivals;
            return true;
        }
        if (mx >= 340 && mx < 388 && my >= 228 && my < 241) {
            request();
            return true;
        }
        return false;
    }

    private static String pct(int bps) {
        return String.format(Locale.ROOT, "%.0f%%", bps / 100.0);
    }
}
