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
        FinanceGui.drawFilledButton(screen.font(), g, "添加手持物品", 100, y - 2, 90, FinanceGui.COL_GOOD);
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
        FinanceGui.drawButton(screen.font(), g, "< " + screen.adminCategory.getDisplayName() + " >", 152, y, 80, true);
        FinanceGui.drawFilledButton(screen.font(), g, "添加", 244, y, 44, FinanceGui.COL_GOOD);
    }

    public void renderAdminPriceHistoryTab(GuiGraphics g, int y) {
        g.fill(8, y - 4, screen.imageWidth() - 8, screen.imageHeight() - 8, 0xFFF1ECDD);
        FinanceGui.drawSimpleBorder(
                g, 8, y - 4, screen.imageWidth() - 16, screen.imageHeight() - y - 4, FinanceGui.COL_PANEL_BORDER);
        FinanceGui.drawSectionTitle(screen.font(), g, "价格历史清理", 10, y);
        FinanceGui.drawClippedString(
                screen.font(), g, "清理只删除趋势图历史点，不会修改当前价格、高低价或持仓。", 10, y + 18, 310, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawFilledButton(screen.font(), g, "清理商品历史", 20, y + 44, 90, FinanceGui.COL_WARN);
        FinanceGui.drawFilledButton(screen.font(), g, "清理股票历史", 126, y + 44, 90, FinanceGui.COL_WARN);
        FinanceGui.drawFilledButton(screen.font(), g, "全部清理", 232, y + 44, 76, FinanceGui.COL_BAD);
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
        FinanceGui.drawButton(screen.font(), g, "-10%", 134, y + 20, 44, false);
        FinanceGui.drawButton(screen.font(), g, "+10%", 184, y + 20, 44, false);
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                "分红周期: " + screen.menu().getDividendCycleDays() + " 天",
                16,
                y + 50,
                120,
                FinanceGui.COL_TEXT);
        FinanceGui.drawButton(screen.font(), g, "-1天", 134, y + 46, 44, false);
        FinanceGui.drawButton(screen.font(), g, "+1天", 184, y + 46, 44, false);
        FinanceGui.drawClippedString(
                screen.font(), g, "到期后，上市公司会按股东持股比例自动分红。", 16, y + 80, 280, FinanceGui.COL_TEXT_DIM);
    }

    /** 子页 1：常用物品分类列表（带图标） */
    public void renderAdminQuickTab(GuiGraphics g, int y) {
        FinanceGui.drawSectionTitle(screen.font(), g, "常用物品（点击添加，灰色已注册）", 10, y);
        int perPage = quickItemsPerPage();
        int maxPage = Math.max(0, (QUICK_ITEMS.length - 1) / perPage);
        screen.adminQuickPage = Math.min(screen.adminQuickPage, maxPage);
        FinanceGui.drawButton(screen.font(), g, "上一页", screen.imageWidth() - 110, y - 2, 48, false);
        FinanceGui.drawButton(screen.font(), g, "下一页", screen.imageWidth() - 56, y - 2, 48, false);
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
        FinanceGui.drawButton(screen.font(), g, "上一页", screen.imageWidth() - 110, y - 2, 48, false);
        FinanceGui.drawButton(screen.font(), g, "下一页", screen.imageWidth() - 56, y - 2, 48, false);
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
            FinanceGui.drawButton(screen.font(), g, "删除", 328, rowY + 1, 42, false);
        }
    }

    // ================================================================
    // 常用物品定义
    // ================================================================

    // ---- 管理标签点击 ----

    public boolean handleAdminClick(int mx, int my) {
        int y = FinanceGui.CONTENT_Y;

        int subX = 10;
        for (int i = 0; i < ADMIN_SUB_TABS.length; i++) {
            int w = adminSubTabWidth();
            if (mx >= subX && mx < subX + w && my >= y && my < y + 14) {
                screen.adminSubTab = i;
                screen.rememberUiState();
                screen.updateInputVisibility();
                return true;
            }
            subX += w + 3;
        }
        y += 16 + 6;

        return switch (screen.adminSubTab) {
            case HAND -> handleAdminHandClick(mx, my, y);
            case QUICK -> handleAdminQuickClick(mx, my, y);
            case REGISTERED -> handleAdminRegisteredClick(mx, my, y);
            case PRICE_HISTORY -> handleAdminPriceHistoryClick(mx, my, y);
            case DIVIDEND -> handleAdminDividendClick(mx, my, y);
            default -> false;
        };
    }

    public boolean handleAdminDividendClick(int mx, int my, int y) {
        if (my >= y + 20 && my < y + 33) {
            if (mx >= 134 && mx < 178) {
                double next = Math.max(0.0, screen.menu().getDividendRatio() - 0.10);
                screen.setStatus("已提交分红比例调整");
                FinancePacketHandler.CHANNEL.sendToServer(AdminActionPacket.setDividendRatio(next));
                return true;
            }
            if (mx >= 184 && mx < 228) {
                double next = Math.min(1.0, screen.menu().getDividendRatio() + 0.10);
                screen.setStatus("已提交分红比例调整");
                FinancePacketHandler.CHANNEL.sendToServer(AdminActionPacket.setDividendRatio(next));
                return true;
            }
        }
        if (my >= y + 46 && my < y + 59) {
            if (mx >= 134 && mx < 178) {
                int next = Math.max(1, screen.menu().getDividendCycleDays() - 1);
                screen.setStatus("已提交分红周期调整");
                FinancePacketHandler.CHANNEL.sendToServer(AdminActionPacket.setDividendCycle(next));
                return true;
            }
            if (mx >= 184 && mx < 228) {
                int next = Math.min(365, screen.menu().getDividendCycleDays() + 1);
                screen.setStatus("已提交分红周期调整");
                FinancePacketHandler.CHANNEL.sendToServer(AdminActionPacket.setDividendCycle(next));
                return true;
            }
        }
        return false;
    }

    public boolean handleAdminPriceHistoryClick(int mx, int my, int y) {
        if (my >= y + 44 && my < y + 57) {
            if (mx >= 20 && mx < 110) {
                screen.setStatus("已提交清理商品价格历史");
                FinancePacketHandler.CHANNEL.sendToServer(AdminActionPacket.clearCommodityPriceHistory());
                return true;
            }
            if (mx >= 126 && mx < 216) {
                screen.setStatus("已提交清理股票价格历史");
                FinancePacketHandler.CHANNEL.sendToServer(AdminActionPacket.clearStockPriceHistory());
                return true;
            }
            if (mx >= 232 && mx < 308) {
                screen.setStatus("已提交清理全部价格历史");
                FinancePacketHandler.CHANNEL.sendToServer(AdminActionPacket.clearAllPriceHistory());
                return true;
            }
        }
        return false;
    }

    public boolean handleAdminHandClick(int mx, int my, int y) {
        // "添加手持物品" 按钮在 (100, y-2) 到 (190, y+11)
        if (my >= y - 2 && my < y + 11 && mx >= 100 && mx < 190) {
            long basePrice = screen.requireAmount(screen.adminBasePriceBox, "基础价格");
            screen.setStatus("已提交添加手持物品");
            FinancePacketHandler.CHANNEL.sendToServer(AdminActionPacket.fromHand(basePrice));
            return true;
        }
        // screen.adminBasePriceBox 在 localY=88，此处 y=68，所以 y+20=88
        // 点击区域让 EditBox 自行处理，这里不拦截
        y += 18 + 6 + 14; // y = 106

        // "手动添加" 标题区域，不拦截
        y += 38; // y = 144，对应 label 和 EditBox 行

        // 分类选择按钮 "< RAW_MATERIALS >" 在 (152, y+18) = (152, 162)
        y += 18; // y = 162
        if (my >= y && my < y + 13 && mx >= 152 && mx < 232) {
            CommodityCategory[] cats = CommodityCategory.values();
            int idx = screen.adminCategory.ordinal();
            screen.adminCategory = cats[(idx + 1) % cats.length];
            return true;
        }

        // "添加" 按钮在 (244, y) = (244, 162)
        if (my >= y && my < y + 13 && mx >= 244 && mx < 288) {
            String id = screen.adminCommodityIdBox.getValue().trim().toLowerCase();
            String itemId = screen.adminItemIdBox.getValue().trim();
            String displayName = screen.adminDisplayNameBox.getValue().trim();
            if (id.isEmpty()) {
                screen.setStatus("请输入商品 ID");
                return true;
            }
            long basePrice = screen.requireAmount(screen.adminBasePriceBox, "基础价格");
            if (displayName.isEmpty()) displayName = id;
            if (itemId.isEmpty()) itemId = null;

            screen.setStatus("已提交添加商品");
            FinancePacketHandler.CHANNEL.sendToServer(
                    new AdminActionPacket(id, itemId, displayName, basePrice, screen.adminCategory));
            return true;
        }

        return false;
    }

    /** 子页 1 点击：常用物品分类列表 */
    public boolean handleAdminQuickClick(int mx, int my, int startY) {
        if (my >= startY - 2 && my < startY + 11) {
            int maxPage = Math.max(0, (QUICK_ITEMS.length - 1) / quickItemsPerPage());
            if (mx >= screen.imageWidth() - 110 && mx < screen.imageWidth() - 62) {
                screen.adminQuickPage = Math.max(0, screen.adminQuickPage - 1);
                return true;
            }
            if (mx >= screen.imageWidth() - 56 && mx < screen.imageWidth() - 8) {
                screen.adminQuickPage = Math.min(maxPage, screen.adminQuickPage + 1);
                return true;
            }
        }
        int y = startY + 14; // 跳过标题
        int listX = 10;
        int listW = screen.imageWidth() - 20;
        int maxYY = screen.imageHeight() - 4;

        int perPage = quickItemsPerPage();
        int start = screen.adminQuickPage * perPage;
        int end = Math.min(QUICK_ITEMS.length, start + perPage);
        CommodityCategory lastCat = null;
        for (int i = start; i < end; i++) {
            if (y + 14 > maxYY) break;

            CommodityCategory cat = CommodityCategory.valueOf(QUICK_ITEMS[i][4]);
            if (cat != lastCat) {
                y += 14; // 分类标题行
                lastCat = cat;
                if (y + 14 > maxYY) break;
            }

            boolean registered = CommodityRegistry.isRegistered(QUICK_ITEMS[i][0]);
            // 点击整行或"添加"按钮
            if (mx >= listX + 2 && mx < listX + listW - 2 && my >= y && my < y + 14) {
                if (!registered) {
                    String id = QUICK_ITEMS[i][0];
                    String itemId = QUICK_ITEMS[i][1];
                    String displayName = QUICK_ITEMS[i][2];
                    long basePrice = FinanceFormat.parseLong(QUICK_ITEMS[i][3]);
                    CommodityCategory category = CommodityCategory.valueOf(QUICK_ITEMS[i][4]);
                    screen.setStatus("已提交添加 " + displayName);
                    FinancePacketHandler.CHANNEL.sendToServer(
                            new AdminActionPacket(id, itemId, displayName, basePrice, category));
                } else {
                    screen.setStatus("该商品已注册");
                }
                return true;
            }
            y += 16;
        }
        return false;
    }

    public boolean handleAdminRegisteredClick(int mx, int my, int y) {
        if (my >= y - 2 && my < y + 11) {
            screen.ensureCommodityCache();
            int maxPage = Math.max(0, (screen.cachedCommodities.size() - 1) / registeredItemsPerPage());
            if (mx >= screen.imageWidth() - 110 && mx < screen.imageWidth() - 62) {
                screen.adminRegisteredPage = Math.max(0, screen.adminRegisteredPage - 1);
                return true;
            }
            if (mx >= screen.imageWidth() - 56 && mx < screen.imageWidth() - 8) {
                screen.adminRegisteredPage = Math.min(maxPage, screen.adminRegisteredPage + 1);
                return true;
            }
        }
        y += 14 + 12;
        screen.ensureCommodityCache();
        int perPage = registeredItemsPerPage();
        int start = screen.adminRegisteredPage * perPage;
        int end = Math.min(screen.cachedCommodities.size(), start + perPage);
        for (int i = start; i < end; i++) {
            int rowY = y + (i - start) * FinanceGui.ROW_HEIGHT;
            if (my >= rowY + 1 && my < rowY + 14 && mx >= 328 && mx < 370) {
                String commodityId = screen.cachedCommodities.get(i).getId();
                screen.setStatus("已提交删除 " + FinanceFormat.commodityDisplayName(commodityId));
                FinancePacketHandler.CHANNEL.sendToServer(new AdminActionPacket(commodityId));
                screen.commodityCacheDirty = true;
                return true;
            }
        }
        return false;
    }

    public int quickItemsPerPage() {
        return 7;
    }

    public int registeredItemsPerPage() {
        return Math.max(1, (screen.imageHeight() - (FinanceGui.CONTENT_Y + 22 + 26) - 4) / FinanceGui.ROW_HEIGHT);
    }
}
