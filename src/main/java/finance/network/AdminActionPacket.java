package finance.network;

import finance.account.AccountManager;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.market.MarketPrice;
import finance.data.CommodityInventorySavedData;
import finance.data.EconomySavedData;
import finance.market.MarketManager;
import finance.market.NpcMarketMaker;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * 管理员操作数据包 —— 添加/删除商品，支持从手持物品自动识别。
 */
public class AdminActionPacket {

    public enum ActionType {
        ADD_COMMODITY,
        REMOVE_COMMODITY,
        ADD_FROM_HAND,
        CLEAR_COMMODITY_PRICE_HISTORY,
        CLEAR_STOCK_PRICE_HISTORY,
        CLEAR_ALL_PRICE_HISTORY,
        SET_DIVIDEND_RATIO,
        SET_DIVIDEND_CYCLE
    }

    private final ActionType actionType;
    private final String commodityId;
    private final String itemId;
    private final String displayName;
    private final long basePrice;
    private final CommodityCategory category;

    private AdminActionPacket(ActionType actionType) {
        this(actionType, 0);
    }

    private AdminActionPacket(ActionType actionType, long value) {
        this.actionType = actionType;
        this.commodityId = "";
        this.itemId = null;
        this.displayName = null;
        this.basePrice = value;
        this.category = null;
    }

    /** 添加商品（手动填写） */
    public AdminActionPacket(String commodityId, String itemId, String displayName,
                             long basePrice, CommodityCategory category) {
        this.actionType = ActionType.ADD_COMMODITY;
        this.commodityId = commodityId;
        this.itemId = itemId;
        this.displayName = displayName;
        this.basePrice = basePrice;
        this.category = category;
    }

    /** 删除商品 */
    public AdminActionPacket(String commodityId) {
        this.actionType = ActionType.REMOVE_COMMODITY;
        this.commodityId = commodityId;
        this.itemId = null;
        this.displayName = null;
        this.basePrice = 0;
        this.category = null;
    }

    /** 从手中添加（客户端发送，commodityId 此时无意义） */
    private AdminActionPacket(long basePrice) {
        this.actionType = ActionType.ADD_FROM_HAND;
        this.commodityId = "";
        this.itemId = null;
        this.displayName = null;
        this.basePrice = basePrice;
        this.category = null;
    }

    /** 创建一个"从手中添加"的数据包 */
    public static AdminActionPacket fromHand(long basePrice) {
        return new AdminActionPacket(basePrice);
    }

    public static AdminActionPacket clearCommodityPriceHistory() {
        return new AdminActionPacket(ActionType.CLEAR_COMMODITY_PRICE_HISTORY);
    }

    public static AdminActionPacket clearStockPriceHistory() {
        return new AdminActionPacket(ActionType.CLEAR_STOCK_PRICE_HISTORY);
    }

    public static AdminActionPacket clearAllPriceHistory() {
        return new AdminActionPacket(ActionType.CLEAR_ALL_PRICE_HISTORY);
    }

    public static AdminActionPacket setDividendRatio(double ratio) {
        return new AdminActionPacket(ActionType.SET_DIVIDEND_RATIO, Math.round(ratio * 10_000));
    }

    public static AdminActionPacket setDividendCycle(int days) {
        return new AdminActionPacket(ActionType.SET_DIVIDEND_CYCLE, days);
    }

    public static void encode(AdminActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.actionType);
        switch (packet.actionType) {
            case ADD_COMMODITY -> {
                buffer.writeUtf(packet.commodityId, NetworkValidation.MAX_COMMODITY_ID_LENGTH);
                buffer.writeBoolean(packet.itemId != null);
                if (packet.itemId != null) {
                    buffer.writeUtf(packet.itemId, NetworkValidation.MAX_ITEM_ID_LENGTH);
                }
                buffer.writeUtf(packet.displayName, NetworkValidation.MAX_DISPLAY_NAME_LENGTH);
                buffer.writeLong(packet.basePrice);
                buffer.writeEnum(packet.category);
            }
            case REMOVE_COMMODITY -> {
                buffer.writeUtf(packet.commodityId, NetworkValidation.MAX_COMMODITY_ID_LENGTH);
            }
            case ADD_FROM_HAND -> {
                buffer.writeLong(packet.basePrice);
            }
            case CLEAR_COMMODITY_PRICE_HISTORY, CLEAR_STOCK_PRICE_HISTORY, CLEAR_ALL_PRICE_HISTORY -> {
            }
            case SET_DIVIDEND_RATIO, SET_DIVIDEND_CYCLE -> {
                buffer.writeLong(packet.basePrice);
            }
        }
    }

    public static AdminActionPacket decode(FriendlyByteBuf buffer) {
        ActionType type = buffer.readEnum(ActionType.class);
        switch (type) {
            case ADD_COMMODITY -> {
                String commodityId = buffer.readUtf(NetworkValidation.MAX_COMMODITY_ID_LENGTH);
                boolean hasItemId = buffer.readBoolean();
                String itemId = hasItemId ? buffer.readUtf(NetworkValidation.MAX_ITEM_ID_LENGTH) : null;
                String displayName = buffer.readUtf(NetworkValidation.MAX_DISPLAY_NAME_LENGTH);
                long basePrice = buffer.readLong();
                CommodityCategory category = buffer.readEnum(CommodityCategory.class);
                return new AdminActionPacket(commodityId, itemId, displayName, basePrice, category);
            }
            case REMOVE_COMMODITY -> {
                return new AdminActionPacket(buffer.readUtf(NetworkValidation.MAX_COMMODITY_ID_LENGTH));
            }
            case ADD_FROM_HAND -> {
                return AdminActionPacket.fromHand(buffer.readLong());
            }
            case CLEAR_COMMODITY_PRICE_HISTORY -> {
                return AdminActionPacket.clearCommodityPriceHistory();
            }
            case CLEAR_STOCK_PRICE_HISTORY -> {
                return AdminActionPacket.clearStockPriceHistory();
            }
            case CLEAR_ALL_PRICE_HISTORY -> {
                return AdminActionPacket.clearAllPriceHistory();
            }
            case SET_DIVIDEND_RATIO -> {
                return new AdminActionPacket(ActionType.SET_DIVIDEND_RATIO, buffer.readLong());
            }
            case SET_DIVIDEND_CYCLE -> {
                return new AdminActionPacket(ActionType.SET_DIVIDEND_CYCLE, buffer.readLong());
            }
            default -> throw new IllegalStateException("未知 ActionType");
        }
    }

    public static void handle(AdminActionPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            if (!player.hasPermissions(2)) {
                GuiFeedbackPacket.send(player, "权限不足，需要管理员权限。");
                return;
            }
            if (!isValidRequest(packet)) {
                GuiFeedbackPacket.send(player, "管理员请求参数无效。");
                return;
            }

            switch (packet.actionType) {
                case ADD_COMMODITY -> handleAdd(player, packet);
                case REMOVE_COMMODITY -> handleRemove(player, packet);
                case ADD_FROM_HAND -> handleAddFromHand(player, packet.basePrice);
                case CLEAR_COMMODITY_PRICE_HISTORY -> handleClearCommodityPriceHistory(player);
                case CLEAR_STOCK_PRICE_HISTORY -> handleClearStockPriceHistory(player);
                case CLEAR_ALL_PRICE_HISTORY -> handleClearAllPriceHistory(player);
                case SET_DIVIDEND_RATIO -> handleSetDividendRatio(player, packet.basePrice);
                case SET_DIVIDEND_CYCLE -> handleSetDividendCycle(player, packet.basePrice);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /** 从玩家手持物品自动识别并添加 */
    private static void handleAddFromHand(ServerPlayer player, long basePrice) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            GuiFeedbackPacket.send(player, "请先手持一个物品。");
            return;
        }
        if (basePrice <= 0) {
            GuiFeedbackPacket.send(player, "基础价格必须大于 0。");
            return;
        }

        // 获取物品注册名
        Item item = held.getItem();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        String fullItemId = itemId.toString(); // 如 "minecraft:iron_ingot" 或 "modid:item_name"

        // 生成商品 ID：从物品名中提取
        String commodityId = generateCommodityId(fullItemId);
        if (CommodityRegistry.isRegistered(commodityId)) {
            GuiFeedbackPacket.send(player, "商品已存在: " + commodityId + "（对应物品: " + fullItemId + "）");
            return;
        }

        // 获取显示名
        String displayName = held.getHoverName().getString();

        // 自动推断分类
        CommodityCategory category = guessCategory(item);

        // 注册商品
        Commodity commodity = new Commodity(commodityId, fullItemId, displayName, category, basePrice);
        CommodityRegistry.register(commodity);

        seedInitialStock(commodityId);

        EconomySavedData.markDirty();
        CommodityInventorySavedData.markDirty();

        GuiFeedbackPacket.send(player,
                "已从手中添加: " + commodityId + " (" + displayName + ") 物品: " + fullItemId + " 分类: " + category.getDisplayName());

    }

    /** 从物品注册名生成简短的商品 ID */
    private static String generateCommodityId(String fullItemId) {
        // "minecraft:iron_ingot" → "iron_ingot"
        // "modid:item_name" → "modid_item_name"
        String[] parts = fullItemId.split(":");
        if (parts.length == 2) {
            String namespace = parts[0];
            String path = parts[1];
            if ("minecraft".equals(namespace)) {
                return path;
            }
            // 模组物品加上命名空间前缀避免冲突
            return namespace + "_" + path;
        }
        return fullItemId.replaceAll("[^a-z0-9_]", "_").toLowerCase(Locale.ROOT);
    }

    /** 根据物品类型猜测分类 */
    private static CommodityCategory guessCategory(Item item) {
        // 方块类物品 → 建筑方块
        if (item instanceof BlockItem) {
            return CommodityCategory.BUILDING_BLOCKS;
        }

        String name = BuiltInRegistries.ITEM.getKey(item).getPath().toLowerCase(Locale.ROOT);

        // 食物相关
        if (item.getFoodProperties() != null) {
            return CommodityCategory.FOOD;
        }

        // 工具类
        if (name.contains("sword") || name.contains("bow") || name.contains("crossbow")
                || name.contains("trident") || name.contains("shield") || name.contains("armor")
                || name.contains("helmet") || name.contains("chestplate") || name.contains("leggings")
                || name.contains("boots")) {
            return CommodityCategory.COMBAT;
        }
        if (name.contains("pickaxe") || name.contains("axe") || name.contains("shovel")
                || name.contains("hoe") || name.contains("shears") || name.contains("fishing")
                || name.contains("flint_and_steel") || name.contains("compass") || name.contains("clock")
                || name.contains("spyglass") || name.contains("brush")) {
            return CommodityCategory.TOOLS;
        }

        // 红石相关
        if (name.contains("redstone") || name.contains("piston") || name.contains("observer")
                || name.contains("hopper") || name.contains("dispenser") || name.contains("dropper")
                || name.contains("repeater") || name.contains("comparator") || name.contains("lever")
                || name.contains("button") || name.contains("pressure_plate") || name.contains("tripwire")
                || name.contains("daylight") || name.contains("note_block")) {
            return CommodityCategory.REDSTONE;
        }

        // 药水相关
        if (name.contains("potion") || name.contains("brewing") || name.contains("blaze")
                || name.contains("nether_wart") || name.contains("glowstone") || name.contains("fermented")
                || name.contains("spider_eye") || name.contains("ghast") || name.contains("magma_cream")) {
            return CommodityCategory.BREWING;
        }

        // 交通运输
        if (name.contains("minecart") || name.contains("boat") || name.contains("rail")
                || name.contains("saddle") || name.contains("elytra") || name.contains("carpet")) {
            return CommodityCategory.TRANSPORTATION;
        }

        // 默认原材料
        return CommodityCategory.RAW_MATERIALS;
    }

    private static void handleAdd(ServerPlayer player, AdminActionPacket packet) {
        String id = NetworkValidation.normalizeCommodityId(packet.commodityId);
        if (CommodityRegistry.isRegistered(id)) {
            GuiFeedbackPacket.send(player, "商品已存在: " + id);
            return;
        }

        Commodity commodity = new Commodity(id,
                packet.itemId == null ? null : packet.itemId.trim(),
                packet.displayName.trim(),
                packet.category, packet.basePrice);
        CommodityRegistry.register(commodity);

        seedInitialStock(id);

        EconomySavedData.markDirty();
        CommodityInventorySavedData.markDirty();

        GuiFeedbackPacket.send(player, "已添加商品: " + id + " (" + packet.displayName + ")");

    }

    private static void handleRemove(ServerPlayer player, AdminActionPacket packet) {
        String id = NetworkValidation.normalizeCommodityId(packet.commodityId);
        if (!CommodityRegistry.isRegistered(id)) {
            GuiFeedbackPacket.send(player, "商品不存在: " + id);
            return;
        }

        // 1. 检查依赖该商品的公司并强制退市
        int cancelledOrders = MarketManager.cancelOrdersForCommodity(id);
        int clearedInventories = CommodityInventoryManager.removeCommodityFromAll(id);
        int liquidatedHoldings = 0;

        List<Company> toDelist = new ArrayList<>();
        for (Company company : CompanyManager.getCompanies()) {
            if (company.getType().getCommodityIds().contains(id)) {
                toDelist.add(company);
            }
        }

        if (!toDelist.isEmpty()) {
            MinecraftServer server = player.getServer();
            List<ServerPlayer> onlinePlayers = server.getPlayerList().getPlayers();
            int playerCount = onlinePlayers.size();

            for (Company company : toDelist) {
                long companyFunds = company.getCash();
                long distAmount = (playerCount > 0) ? (companyFunds / 10) / playerCount : 0;

                if (distAmount > 0 && playerCount > 0) {
                    for (ServerPlayer p : onlinePlayers) {
                        AccountManager.deposit(p.getUUID(), distAmount);
                    }
                }

                Stock removedStock = StockMarketManager.removeStockByCompanyId(company.getCompanyId());
                if (removedStock != null) {
                    liquidatedHoldings += StockPortfolioManager.liquidateHolding(
                            removedStock.getSymbol(),
                            removedStock.getLastPrice()
                    );
                }
                CompanyManager.removeCompany(company.getCompanyId());

                // 全服广播
                String msg = "§c[金融] 由于商品 " + id + " 被移除，公司「" + company.getName() + "」已强制退市。"
                        + (distAmount > 0 ? "每位在线玩家获得补偿 §a" + distAmount + "§c 金币。" : "");
                for (ServerPlayer p : onlinePlayers) {
                    p.sendSystemMessage(Component.literal(msg));
                }
            }
        }

        // 2. 删除商品
        CommodityRegistry.removeCommodity(id);
        NpcMarketMaker.getAllMarketPrices().remove(id);

        EconomySavedData.markDirty();
        CommodityInventorySavedData.markDirty();

        GuiFeedbackPacket.send(player, "已删除商品: " + id
                + (toDelist.isEmpty() ? "" : "（同时强制退市 " + toDelist.size() + " 家公司）"));

    }

    private static void seedInitialStock(String commodityId) {
        MarketPrice mp = NpcMarketMaker.getMarketPrice(commodityId);
        int currentStock = CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, commodityId);
        if (currentStock < MarketPrice.REFERENCE_STOCK) {
            CommodityInventoryManager.setCommodity(NpcMarketMaker.NPC_UUID, commodityId, (int) MarketPrice.REFERENCE_STOCK);
        }
        if (mp != null) {
            mp.recomputePrice(CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, commodityId));
            mp.resetDayStats();
        }
    }

    private static void handleClearCommodityPriceHistory(ServerPlayer player) {
        int cleared = NpcMarketMaker.clearPriceHistory();
        GuiFeedbackPacket.send(player, "已清理商品价格历史 " + cleared + " 条。");
    }

    private static void handleClearStockPriceHistory(ServerPlayer player) {
        int cleared = StockMarketManager.clearPriceHistory();
        GuiFeedbackPacket.send(player, "已清理股票价格历史 " + cleared + " 条。");
    }

    private static void handleClearAllPriceHistory(ServerPlayer player) {
        int commodity = NpcMarketMaker.clearPriceHistory();
        int stock = StockMarketManager.clearPriceHistory();
        GuiFeedbackPacket.send(player, "已清理价格历史：商品 " + commodity + " 条，股票 " + stock + " 条。");
    }

    private static void handleSetDividendRatio(ServerPlayer player, long basisPoints) {
        double ratio = basisPoints / 10_000.0;
        CompanyManager.setDividendPolicy(ratio, CompanyManager.getDividendCycleDays());
        GuiFeedbackPacket.send(player, "分红比例已设置为 " + Math.round(CompanyManager.getDividendRatio() * 100) + "%。");
    }

    private static void handleSetDividendCycle(ServerPlayer player, long days) {
        CompanyManager.setDividendPolicy(CompanyManager.getDividendRatio(), (int) days);
        GuiFeedbackPacket.send(player, "分红周期已设置为 " + CompanyManager.getDividendCycleDays() + " 天。");
    }

    private static boolean isValidRequest(AdminActionPacket packet) {
        if (packet.actionType == null) {
            return false;
        }
        return switch (packet.actionType) {
            case ADD_COMMODITY -> NetworkValidation.isValidCommodityId(packet.commodityId)
                    && NetworkValidation.isValidItemId(packet.itemId)
                    && NetworkValidation.isValidDisplayName(packet.displayName)
                    && NetworkValidation.isPositive(packet.basePrice)
                    && packet.category != null;
            case REMOVE_COMMODITY -> NetworkValidation.isValidCommodityId(packet.commodityId);
            case ADD_FROM_HAND -> NetworkValidation.isPositive(packet.basePrice);
            case CLEAR_COMMODITY_PRICE_HISTORY, CLEAR_STOCK_PRICE_HISTORY, CLEAR_ALL_PRICE_HISTORY -> true;
            case SET_DIVIDEND_RATIO -> packet.basePrice >= 0 && packet.basePrice <= 10_000;
            case SET_DIVIDEND_CYCLE -> packet.basePrice >= 1 && packet.basePrice <= 365;
        };
    }
}
