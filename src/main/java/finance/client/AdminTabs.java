package finance.client;

import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.network.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/** 管理后台（含 5 个子页签）：从 FinanceScreen 抽取的页签行为类。
 * 通过 screen 反向引用共享界面状态；未来做跨模组页签注册表时，此类即天然的注册单元。 */
public final class AdminTabs {
    private final FinanceScreen screen;

    public AdminTabs(FinanceScreen screen) {
        this.screen = screen;
    }

    /** 子页签编号（{@code FinanceScreen.adminSubTab}），顺序与 {@link #ADMIN_SUB_TABS} 一致。 */
    static final int HAND = 0, QUICK = 1, REGISTERED = 2, PRICE_HISTORY = 3, DIVIDEND = 4;

    private static final String[] ADMIN_SUB_TABS = {"从手中添加", "常用物品", "已注册商品", "价格历史", "分红设置"};

    /** 管理后台快捷物品表（原 FinanceScreen 中置常量，随管理页签迁移）。 */
    static final String[][] QUICK_ITEMS = {
        {"gold", "minecraft:gold_ingot", "金锭", "50", "RAW_MATERIALS"},
        {"diamond", "minecraft:diamond", "钻石", "200", "RAW_MATERIALS"},
        {"emerald", "minecraft:emerald", "绿宝石", "150", "RAW_MATERIALS"},
        {"copper", "minecraft:copper_ingot", "铜锭", "6", "RAW_MATERIALS"},
        {"netherite", "minecraft:netherite_ingot", "下界合金锭", "500", "RAW_MATERIALS"},
        {"coal", "minecraft:coal", "煤炭", "5", "RAW_MATERIALS"},
        {"leather", "minecraft:leather", "皮革", "10", "RAW_MATERIALS"},
        {"redstone", "minecraft:redstone", "红石", "8", "REDSTONE"},
        {"lapis", "minecraft:lapis_lazuli", "青金石", "12", "MISCELLANEOUS"},
        {"bread", "minecraft:bread", "面包", "15", "FOOD"},
        {"apple", "minecraft:apple", "苹果", "12", "FOOD"},
        {"carrot", "minecraft:carrot", "胡萝卜", "6", "FOOD"},
        {"potato", "minecraft:potato", "土豆", "6", "FOOD"},
        {"oak_log", "minecraft:oak_log", "橡木原木", "3", "BUILDING_BLOCKS"},
        {"sand", "minecraft:sand", "沙子", "1", "BUILDING_BLOCKS"},
        {"glass", "minecraft:glass", "玻璃", "4", "BUILDING_BLOCKS"},
        {"brick", "minecraft:brick", "砖块", "5", "BUILDING_BLOCKS"},
    };

    public void renderAdminTab(GuiGraphics g) {
        int y = FinanceGui.CONTENT_Y;

        int subX = 10;
        for (int i = 0; i < ADMIN_SUB_TABS.length; i++) {
            int w = adminSubTabWidth();
            boolean active = (i == screen.adminSubTab);
            int index = i;
            screen.contentClicks.add(subX, y, w, 14, () -> {
                screen.adminSubTab = index;
                screen.rememberUiState();
                screen.updateInputVisibility();
            });
            if (active) {
                g.fill(subX, y - 1, subX + w, y + 13, 0xFFECE6D5);
                g.fill(subX + 2, y + 12, subX + w - 2, y + 13, FinanceGui.COL_ACCENT);
            }
            FinanceGui.drawCenteredClippedString(
                    screen.font(),
                    g,
                    ADMIN_SUB_TABS[i],
                    subX,
                    y + 2,
                    w,
                    active ? FinanceGui.COL_TEXT : FinanceGui.COL_TEXT_DIM);
            subX += w + 3;
        }
        y += 16;
        FinanceGui.drawSimpleSeparator(g, 8, y, screen.imageWidth() - 16);
        y += 6;

        switch (screen.adminSubTab) {
            case HAND -> renderAdminHandTab(g, y);
            case QUICK -> renderAdminQuickTab(g, y);
            case REGISTERED -> renderAdminRegisteredTab(g, y);
            case PRICE_HISTORY -> renderAdminPriceHistoryTab(g, y);
            case DIVIDEND -> renderAdminDividendTab(g, y);
        }
    }

    public int adminSubTabWidth() {
        return 62;
    }

    /** 子页 0：从手中添加 + 手动添加 */
    public void renderAdminHandTab(GuiGraphics g, int y) {
        g.fill(8, y - 4, screen.imageWidth() - 8, screen.imageHeight() - 8, 0xFFF1ECDD);
        FinanceGui.drawSimpleBorder(
                g, 8, y - 4, screen.imageWidth() - 16, screen.imageHeight() - y - 4, FinanceGui.COL_PANEL_BORDER);
        FinanceGui.drawSectionTitle(screen.font(), g, "从手中添加", 10, y);
        screen.filledButton(g, "添加手持物品", 100, y - 2, 90, FinanceGui.COL_GOOD, () -> {
            long basePrice = screen.requireAmount(screen.adminBasePriceBox, "基础价格");
            screen.setStatus("已提交添加手持物品");
            FinancePacketHandler.CHANNEL.sendToServer(AdminActionPacket.fromHand(basePrice));
        });
        // screen.adminBasePriceBox 在 localY=88 (FinanceGui.CONTENT_Y+88=46+88=134 但 localY=88)
        g.drawString(screen.font(), "价格:", 200, y + 20, FinanceGui.COL_TEXT_DIM, false);
        y += 18;

        FinanceGui.drawSimpleSeparator(g, 8, y, screen.imageWidth() - 16);
        y += 6;

        FinanceGui.drawSectionTitle(screen.font(), g, "手动添加", 10, y);
        y += 14;

        // 输入框与标签共用 localY=144，避免输入框掉到说明文字下方。
        g.drawString(screen.font(), "ID:", 10, y + 38, FinanceGui.COL_TEXT_DIM, false);
        g.drawString(screen.font(), "物品:", 120, y + 38, FinanceGui.COL_TEXT_DIM, false);
        g.drawString(screen.font(), "名称:", 270, y + 38, FinanceGui.COL_TEXT_DIM, false);
        y += 56;

        FinanceGui.drawClippedString(screen.font(), g, "价格使用上方设置", 10, y + 2, 100, FinanceGui.COL_TEXT_DIM);
        g.drawString(screen.font(), "分类:", 120, y + 2, FinanceGui.COL_TEXT_DIM, false);
        screen.button(g, "< " + screen.adminCategory.getDisplayName() + " >", 152, y, 80, true, () -> {
            CommodityCategory[] cats = CommodityCategory.values();
            int idx = screen.adminCategory.ordinal();
            screen.adminCategory = cats[(idx + 1) % cats.length];
        });
        screen.filledButton(g, "添加", 244, y, 44, FinanceGui.COL_GOOD, this::addManualCommodity);
    }

    public void renderAdminPriceHistoryTab(GuiGraphics g, int y) {
        g.fill(8, y - 4, screen.imageWidth() - 8, screen.imageHeight() - 8, 0xFFF1ECDD);
        FinanceGui.drawSimpleBorder(
                g, 8, y - 4, screen.imageWidth() - 16, screen.imageHeight() - y - 4, FinanceGui.COL_PANEL_BORDER);
        FinanceGui.drawSectionTitle(screen.font(), g, "价格历史清理", 10, y);
        FinanceGui.drawClippedString(
                screen.font(), g, "清理只删除趋势图历史点，不会修改当前价格、高低价或持仓。", 10, y + 18, 310, FinanceGui.COL_TEXT_DIM);
        screen.filledButton(g, "清理商品历史", 20, y + 44, 90, FinanceGui.COL_WARN, () -> {
            screen.setStatus("已提交清理商品价格历史");
            FinancePacketHandler.CHANNEL.sendToServer(AdminActionPacket.clearCommodityPriceHistory());
        });
        screen.filledButton(g, "清理股票历史", 126, y + 44, 90, FinanceGui.COL_WARN, () -> {
            screen.setStatus("已提交清理股票价格历史");
            FinancePacketHandler.CHANNEL.sendToServer(AdminActionPacket.clearStockPriceHistory());
        });
        screen.filledButton(g, "全部清理", 232, y + 44, 76, FinanceGui.COL_BAD, () -> {
            screen.setStatus("已提交清理全部价格历史");
            FinancePacketHandler.CHANNEL.sendToServer(AdminActionPacket.clearAllPriceHistory());
        });
    }

    public void renderAdminDividendTab(GuiGraphics g, int y) {
        g.fill(8, y - 4, screen.imageWidth() - 8, screen.imageHeight() - 8, 0xFFF1ECDD);
        FinanceGui.drawSimpleBorder(
                g, 8, y - 4, screen.imageWidth() - 16, screen.imageHeight() - y - 4, FinanceGui.COL_PANEL_BORDER);
        FinanceGui.drawSectionTitle(screen.font(), g, "公司分红设置", 10, y);
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                "分红比例: " + Math.round(screen.menu().getDividendRatio() * 100) + "%",
                16,
                y + 24,
                110,
                FinanceGui.COL_TEXT);
        screen.button(g, "-10%", 134, y + 20, 44, false, () -> setDividendRatio(-0.10));
        screen.button(g, "+10%", 184, y + 20, 44, false, () -> setDividendRatio(0.10));
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                "分红周期: " + screen.menu().getDividendCycleDays() + " 天",
                16,
                y + 50,
                120,
                FinanceGui.COL_TEXT);
        screen.button(g, "-1天", 134, y + 46, 44, false, () -> setDividendCycle(-1));
        screen.button(g, "+1天", 184, y + 46, 44, false, () -> setDividendCycle(1));
        FinanceGui.drawClippedString(
                screen.font(), g, "到期后，上市公司会按股东持股比例自动分红。", 16, y + 80, 280, FinanceGui.COL_TEXT_DIM);
    }

    /** 子页 1：常用物品分类列表（带图标） */
    public void renderAdminQuickTab(GuiGraphics g, int y) {
        FinanceGui.drawSectionTitle(screen.font(), g, "常用物品（点击添加，灰色已注册）", 10, y);
        int perPage = quickItemsPerPage();
        int maxPage = Math.max(0, (QUICK_ITEMS.length - 1) / perPage);
        screen.adminQuickPage = Math.min(screen.adminQuickPage, maxPage);
        screen.button(
                g,
                "上一页",
                screen.imageWidth() - 110,
                y - 2,
                48,
                false,
                () -> screen.adminQuickPage = Math.max(0, screen.adminQuickPage - 1));
        screen.button(
                g,
                "下一页",
                screen.imageWidth() - 56,
                y - 2,
                48,
                false,
                () -> screen.adminQuickPage = Math.min(maxPage, screen.adminQuickPage + 1));
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                (screen.adminQuickPage + 1) + "/" + (maxPage + 1),
                screen.imageWidth() - 158,
                y + 1,
                42,
                FinanceGui.COL_TEXT_DIM);
        y += 14;

        int listX = 10;
        int listW = screen.imageWidth() - 20;
        int maxYY = screen.imageHeight() - 4;

        int start = screen.adminQuickPage * perPage;
        int end = Math.min(QUICK_ITEMS.length, start + perPage);
        CommodityCategory lastCat = null;
        for (int i = start; i < end; i++) {
            if (y + 14 > maxYY) break;

            CommodityCategory cat = CommodityCategory.valueOf(QUICK_ITEMS[i][4]);
            if (cat != lastCat) {
                // 分类标题
                g.fill(listX, y, listX + listW, y + 12, FinanceGui.COL_CATEGORY);
                g.drawString(screen.font(), "▸ " + cat.getDisplayName(), listX + 4, y + 2, FinanceGui.COL_TEXT, false);
                y += 14;
                lastCat = cat;
                if (y + 14 > maxYY) break;
            }

            boolean registered = CommodityRegistry.isRegistered(QUICK_ITEMS[i][0]);
            String[] quick = QUICK_ITEMS[i];
            // 点整行（含“添加”按钮）都算添加
            screen.contentClicks.add(listX + 2, y, listW - 4, 14, () -> addQuickItem(quick));
            int rowBg = registered ? 0xFFD0D0D0 : ((i % 2 == 0) ? FinanceGui.COL_ROW_EVEN : FinanceGui.COL_ROW_ODD);
            g.fill(listX + 2, y, listX + listW - 2, y + 14, rowBg);

            // 物品图标
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(QUICK_ITEMS[i][1]));
            FinanceGui.renderItemIcon(g, item, listX + 4, y + 2);

            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    QUICK_ITEMS[i][2],
                    listX + 22,
                    y + 3,
                    80,
                    registered ? FinanceGui.COL_TEXT_DIM : FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    screen.font(), g, "基础价: " + QUICK_ITEMS[i][3], listX + 110, y + 3, 80, FinanceGui.COL_TEXT_DIM);
            if (registered) {
                FinanceGui.drawClippedString(
                        screen.font(), g, "已注册", listX + listW - 46, y + 3, 40, FinanceGui.COL_TEXT_DIM);
            } else {
                FinanceGui.drawFilledButton(screen.font(), g, "添加", listX + listW - 44, y, 36, FinanceGui.COL_GOOD);
            }
            y += 16;
        }
    }

    /** 子页 2：已注册商品列表 */
    public void renderAdminRegisteredTab(GuiGraphics g, int y) {
        FinanceGui.drawSectionTitle(screen.font(), g, "已注册商品", 10, y);
        screen.ensureCommodityCache();
        int perPage = registeredItemsPerPage();
        int maxPage = Math.max(0, (screen.cachedCommodities.size() - 1) / perPage);
        screen.adminRegisteredPage = Math.min(screen.adminRegisteredPage, maxPage);
        screen.button(
                g,
                "上一页",
                screen.imageWidth() - 110,
                y - 2,
                48,
                false,
                () -> screen.adminRegisteredPage = Math.max(0, screen.adminRegisteredPage - 1));
        screen.button(
                g,
                "下一页",
                screen.imageWidth() - 56,
                y - 2,
                48,
                false,
                () -> screen.adminRegisteredPage = Math.min(maxPage, screen.adminRegisteredPage + 1));
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                (screen.adminRegisteredPage + 1) + "/" + (maxPage + 1),
                screen.imageWidth() - 158,
                y + 1,
                42,
                FinanceGui.COL_TEXT_DIM);
        y += 14;

        FinanceGui.drawHeader(screen.font(), g, "显示名", 10, y);
        FinanceGui.drawHeader(screen.font(), g, "物品ID", 86, y);
        FinanceGui.drawHeader(screen.font(), g, "价格", 222, y);
        FinanceGui.drawHeader(screen.font(), g, "分类", 262, y);
        FinanceGui.drawHeader(screen.font(), g, "操作", 328, y);
        y += 12;

        int start = screen.adminRegisteredPage * perPage;
        int end = Math.min(screen.cachedCommodities.size(), start + perPage);
        for (int i = start; i < end; i++) {
            Commodity c = screen.cachedCommodities.get(i);
            int rowY = y + (i - start) * FinanceGui.ROW_HEIGHT;
            g.fill(
                    8,
                    rowY,
                    screen.imageWidth() - 8,
                    rowY + FinanceGui.ROW_HEIGHT,
                    (i % 2 == 0) ? FinanceGui.COL_ROW_EVEN : FinanceGui.COL_ROW_ODD);
            FinanceGui.drawClippedString(screen.font(), g, c.getDisplayName(), 10, rowY + 3, 70, FinanceGui.COL_TEXT);
            String itemId = c.getItemId();
            FinanceGui.drawClippedString(
                    screen.font(), g, itemId != null ? itemId : c.getId(), 86, rowY + 3, 130, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(
                    screen.font(), g, Long.toString(c.getBasePrice()), 222, rowY + 3, 36, FinanceGui.COL_GOOD);
            FinanceGui.drawClippedString(
                    screen.font(), g, c.getCategory().getDisplayName(), 262, rowY + 3, 60, FinanceGui.COL_TEXT_DIM);
            String commodityId = c.getId();
            screen.button(g, "删除", 328, rowY + 1, 42, false, () -> {
                screen.setStatus("已提交删除 " + FinanceFormat.commodityDisplayName(commodityId));
                FinancePacketHandler.CHANNEL.sendToServer(new AdminActionPacket(commodityId));
                screen.commodityCacheDirty = true;
            });
        }
    }

    // ================================================================
    // 常用物品定义
    // ================================================================

    // ---- 管理标签点击 ----

    private void setDividendRatio(double delta) {
        double next = delta < 0
                ? Math.max(0.0, screen.menu().getDividendRatio() + delta)
                : Math.min(1.0, screen.menu().getDividendRatio() + delta);
        screen.setStatus("已提交分红比例调整");
        FinancePacketHandler.CHANNEL.sendToServer(AdminActionPacket.setDividendRatio(next));
    }

    private void setDividendCycle(int delta) {
        int next = delta < 0
                ? Math.max(1, screen.menu().getDividendCycleDays() + delta)
                : Math.min(365, screen.menu().getDividendCycleDays() + delta);
        screen.setStatus("已提交分红周期调整");
        FinancePacketHandler.CHANNEL.sendToServer(AdminActionPacket.setDividendCycle(next));
    }

    private void addManualCommodity() {
        String id = screen.adminCommodityIdBox.getValue().trim().toLowerCase();
        String itemId = screen.adminItemIdBox.getValue().trim();
        String displayName = screen.adminDisplayNameBox.getValue().trim();
        if (id.isEmpty()) {
            screen.setStatus("请输入商品 ID");
            return;
        }
        long basePrice = screen.requireAmount(screen.adminBasePriceBox, "基础价格");
        if (displayName.isEmpty()) displayName = id;
        if (itemId.isEmpty()) itemId = null;
        screen.setStatus("已提交添加商品");
        FinancePacketHandler.CHANNEL.sendToServer(
                new AdminActionPacket(id, itemId, displayName, basePrice, screen.adminCategory));
    }

    private void addQuickItem(String[] quick) {
        if (CommodityRegistry.isRegistered(quick[0])) {
            screen.setStatus("该商品已注册");
            return;
        }
        String displayName = quick[2];
        screen.setStatus("已提交添加 " + displayName);
        FinancePacketHandler.CHANNEL.sendToServer(new AdminActionPacket(
                quick[0],
                quick[1],
                displayName,
                FinanceFormat.parseLong(quick[3]),
                CommodityCategory.valueOf(quick[4])));
    }

    public int quickItemsPerPage() {
        return 7;
    }

    public int registeredItemsPerPage() {
        return Math.max(1, (screen.imageHeight() - (FinanceGui.CONTENT_Y + 22 + 26) - 4) / FinanceGui.ROW_HEIGHT);
    }
}
