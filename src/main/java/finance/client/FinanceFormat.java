package finance.client;

import com.mojang.blaze3d.systems.RenderSystem;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.company.CompanyManagementAction;
import finance.company.CompanyType;
import finance.company.CompanyStrategy;
import finance.alert.PriceAlertDirection;
import finance.alert.PriceAlertType;
import finance.company.CompanyProposalType;
import finance.stock.ConditionalStockOrderType;
import finance.gui.FinanceMenu;
import finance.gameplay.FinanceScreenMode;
import finance.market.NpcMarketMaker;
import finance.network.*;
import finance.util.FormatUtil;
import finance.chart.MarketInstrumentType;
import finance.chart.MarketSummary;
import finance.client.chart.CandlestickChart;
import finance.client.chart.CandlestickClientCache;
import finance.client.chart.TechnicalIndicatorChart;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** 展示 / 解析辅助：从 FinanceScreen 抽取的无状态格式化工具类。
 * 需要界面状态的方法显式接收 FinanceScreen 参数，其余为纯函数。 */

public final class FinanceFormat {
    private FinanceFormat() {}

    public static String companyName(FinanceScreen screen, UUID companyId){
        for(FinanceMenu.CompanyInfo company:screen.menu().getAllCompanies())if(company.companyId().equals(companyId))return company.name();
        return companyId==null?"未知公司":companyId.toString().substring(0,8);
    }

    public static String displayProposalType(String type) {
        return switch (type) {
            case "DIVIDEND" -> "分红";
            case "SHARE_ISSUE" -> "增发";
            case "RENAME" -> "改名";
            case "FUND_USAGE" -> "用途";
            case "SHARE_BUYBACK" -> "回购";
            case "TREASURY_RETIREMENT" -> "注销库存股";
            case "TENDER_OFFER_RESPONSE" -> "要约响应";
            case "CONTROL_TRANSFER" -> "控制权";
            case "EMERGENCY_RECAPITALIZATION" -> "紧急再融资";
            case "MAJOR_ASSET_PURCHASE" -> "重大资产";
            case "CAPITAL_PROJECT" -> "实体资本项目";
            default -> type;
        };
    }

    public static String displayProposalValues(FinanceMenu.CompanyProposalRow row) {
        return switch (row.type()) {
            case "DIVIDEND" -> row.value1() + "%";
            case "SHARE_ISSUE" -> row.value1() + "股@" + row.value2();
            case "RENAME", "FUND_USAGE" -> row.textValue();
            case "CAPITAL_PROJECT" -> "预算" + row.value1() + "/" + (row.value2() == 0 ? "仓库" : "工厂");
            default -> "";
        };
    }

    public static String displayProposalStatus(FinanceMenu.CompanyProposalRow row) {
        if ("EXECUTED".equals(row.status())) {
            return "已执行";
        }
        if ("PASSED".equals(row.status())) {
            return "待执行";
        }
        if ("FAILED".equals(row.status())) {
            return "未过";
        }
        return "投票中";
    }

    public static String displayStockSymbol(FinanceMenu.StockRow row) {
        return FinanceFormat.displaySymbol(row.symbol());
    }

    public static String displayHoldingSymbol(String symbol) {
        return FinanceFormat.displaySymbol(symbol);
    }

    public static String displaySymbol(String symbol) {
        return switch (symbol) {
            case "IRONM", "MININ", "铁锭" -> "铁锭";
            case "STONE", "ENERG", "石头" -> "石头";
            case "WHEAT", "AGRIC", "小麦" -> "小麦";
            default -> symbol;
        };
    }

    public static String displayCompanyStrategy(FinanceScreen screen, FinanceMenu.CompanyInfo company) {
        return screen.companyStrategyOverride != null ? screen.companyStrategyOverride : company.strategy();
    }

    public static String formatRecordTime(long epochSeconds) {
        java.time.LocalDateTime time = java.time.LocalDateTime.ofEpochSecond(
                epochSeconds, 0, java.time.ZoneOffset.UTC);
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }

    public static String shortUuid(UUID uuid) {
        if (uuid == null) {
            return "-";
        }
        String text = uuid.toString();
        return text.length() <= 8 ? text : text.substring(0, 8);
    }

    public static String displayTransactionType(String type) {
        return switch (type) {
            case "TRANSFER" -> "转账";
            case "MARKET_TRADE" -> "商品成交";
            case "NPC_BUY", "COMMODITY_SELL" -> "卖出商品";
            case "NPC_SELL", "COMMODITY_BUY" -> "买入商品";
            case "STOCK_BUY" -> "买入股票";
            case "STOCK_SELL" -> "卖出股票";
            case "ORDER_CANCEL" -> "商品撤单";
            case "STOCK_ORDER_CANCEL" -> "股票撤单";
            case "COMPANY_ACTION" -> "公司操作";
            case "COMPANY_CREATE" -> "创建公司";
            case "COMPANY_IPO" -> "公司IPO";
            case "DIVIDEND" -> "分红";
            case "CONDITIONAL_STOCK_TRIGGER" -> "条件触发";
            case "CONDITIONAL_STOCK_CANCEL" -> "条件取消";
            case "COMPANY_FINANCING_START" -> "发起融资";
            case "COMPANY_FINANCING_SUBSCRIBE" -> "认购增发";
            case "COMPANY_FINANCING_SUCCESS" -> "融资成功";
            case "COMPANY_FINANCING_REFUND" -> "融资退款";
            case "COMPANY_PROPOSAL_CREATE" -> "创建提案";
            case "COMPANY_PROPOSAL_VOTE" -> "股东投票";
            case "COMPANY_PROPOSAL_RESULT" -> "提案结果";
            case "COMPANY_BANKRUPTCY" -> "破产退市";
            case "COMPANY_LIQUIDATION" -> "清算分配";
            case "ADMIN_GIVE" -> "管理员";
            case "ADMIN_COMMODITY" -> "商品管理";
            case "NPC_INVESTOR_FUNDING" -> "NPC 投资者注资";
            case "NPC_FLOAT_PLACEMENT" -> "NPC 残差配售";
            case "MACRO_WAGE" -> "工资";
            case "MACRO_WELFARE" -> "社会福利";
            case "MACRO_POLITICS" -> "政治捐款";
            case "MACRO_VILLAGE_SPENDING" -> "村民消费";
            case "MACRO_TAX" -> "利润税";
            case "MACRO_TARIFF" -> "关税";
            case "MACRO_ADMIN" -> "行政开支";
            case "MACRO_DEBT_BORROW" -> "国库借款";
            case "MACRO_DEBT_REPAY" -> "国库还款";
            case "MACRO_DEBT_INTEREST" -> "国债利息";
            case "MACRO_DEBT_ISSUANCE" -> "央行增发";
            case "MACRO_SURVEY" -> "商情调查";
            default -> type;
        };
    }

    public static int typeColor(String type) {
        return switch (type) {
            case "COMMODITY_BUY", "STOCK_BUY", "NPC_SELL" -> FinanceGui.COL_GOOD;
            case "COMMODITY_SELL", "STOCK_SELL", "NPC_BUY" -> FinanceGui.COL_BAD;
            case "ORDER_CANCEL", "STOCK_ORDER_CANCEL", "CONDITIONAL_STOCK_CANCEL" -> FinanceGui.COL_WARN;
            case "COMPANY_BANKRUPTCY" -> FinanceGui.COL_BAD;
            case "COMPANY_LIQUIDATION" -> FinanceGui.COL_WARN;
            case "CONDITIONAL_STOCK_TRIGGER", "COMPANY_FINANCING_START",
                    "COMPANY_FINANCING_SUBSCRIBE", "COMPANY_FINANCING_SUCCESS",
                    "COMPANY_PROPOSAL_CREATE", "COMPANY_PROPOSAL_VOTE",
                    "COMPANY_PROPOSAL_RESULT" -> FinanceGui.COL_ACCENT;
            case "COMPANY_FINANCING_REFUND" -> FinanceGui.COL_WARN;
            default -> FinanceGui.COL_TEXT_DIM;
        };
    }

    public static List<FinanceMenu.AssetRow> sortedAssetRows(FinanceScreen screen) {
        List<FinanceMenu.AssetRow> rows = new ArrayList<>(screen.menu().getAssetRows());
        if (screen.assetSortMode == 1) {
            rows.sort(Comparator.comparingLong(FinanceMenu.AssetRow::floatingProfit).reversed()
                    .thenComparing(Comparator.comparingLong(FinanceMenu.AssetRow::value).reversed()));
        } else {
            rows.sort(Comparator.comparingLong(FinanceMenu.AssetRow::value).reversed()
                    .thenComparing(Comparator.comparingLong(FinanceMenu.AssetRow::floatingProfit).reversed()));
        }
        return rows;
    }

    public static String signedLong(long value) {
        return value > 0 ? "+" + value : Long.toString(value);
    }

    public static String percentOf(long value, long total) {
        if (total <= 0) {
            return "0.0%";
        }
        return String.format(Locale.ROOT, "%.1f%%", (double) value / total * 100.0);
    }

    public static long safeMultiply(long price, int quantity) {
        if (price <= 0 || quantity <= 0) return 0;
        if (price > Long.MAX_VALUE / quantity) return Long.MAX_VALUE;
        return price * quantity;
    }

    public static long safeMultiplyLong(long price, long quantity) {
        if (price <= 0 || quantity <= 0) return 0;
        if (price > Long.MAX_VALUE / quantity) return Long.MAX_VALUE;
        return price * quantity;
    }

    /** 设施状态的中文名；未知状态原样返回。 */
    public static String facilityStatusLabel(String status) {
        if (status == null) return "";
        return switch (status) {
            case "ACTIVE" -> "生产中";
            case "MISSING_INPUT" -> "缺原料";
            case "OUTPUT_FULL" -> "仓库满";
            case "DISABLED" -> "已停用";
            case "BANKRUPTCY_HOLD" -> "破产冻结";
            case "ORPHANED" -> "失联";
            case "UNDERSTAFFED" -> "缺工人";
            case "EQUIPMENT_MISSING" -> "缺设备";
            default -> status;
        };
    }

    public static String getTypeDescription(CompanyType type) {
        return switch (type) {
            case RAW_MATERIALS -> "产出: 铁锭×100/天";
            case BUILDING_BLOCKS -> "产出: 石头×120/天";
            case FOOD -> "产出: 小麦×80/天";
            case FOOD_PROCESSING -> "产出: 面包×30/天 投入: 小麦×40";
            case FORESTRY -> "产出: 木头×100/天";
            case COAL_MINING -> "产出: 煤×80/天 投入: 铁锭×5";
            case TEXTILE -> "产出: 羊毛×40/天 投入: 小麦×10";
        };
    }

    public static long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return 1; }
    }

    public static int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 1; }
    }

    public static String commodityDisplayName(String commodityId) {
        Commodity c = CommodityRegistry.getCommodity(commodityId);
        return c != null ? c.getDisplayName() : commodityId;
    }
}
