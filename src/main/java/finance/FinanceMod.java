package finance;

import finance.chart.CandlestickService;
import finance.command.BalanceCommand;
import finance.command.CommodityCommand;
import finance.command.CompaniesCommand;
import finance.command.CompanyCommand;
import finance.command.FinanceCommand;
import finance.command.InventoryCommand;
import finance.command.MarginCommand;
import finance.command.MarketCommand;
import finance.command.PayCommand;
import finance.command.PlayerBankCommand;
import finance.command.TakeoverCommand;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.company.SystemCompanyInitializer;
import finance.config.FinanceConfig;
import finance.cycle.EconomyCycleService;
import finance.data.CommodityInventorySavedData;
import finance.data.EconomySavedData;
import finance.diagnostic.StartupSelfCheckService;
import finance.market.MarketMakerLedger;
import finance.market.NpcMarketMaker;
import finance.network.FinancePacketHandler;
import finance.network.MarketDataRequestLimiter;
import finance.registry.ModBlockEntities;
import finance.registry.ModBlocks;
import finance.registry.ModItems;
import finance.registry.ModMenus;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.item.ItemExpireEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Finance 模组入口。
 *
 * <h3>命令列表</h3>
 * <ul>
 *   <li>/balance —— 查询余额</li>
 *   <li>/pay —— 转账</li>
 *   <li>/finance give —— 管理员发钱（需要 OP）</li>
 *   <li>/market buy | sell | orders | cancel | history [commodity] | international —— P2P 与国际市场交易</li>
 *   <li>/market price [commodity] | top | losers —— 行情查询、涨跌排行</li>
 *   <li>/commodity give —— 管理员发商品（需要 OP）</li>
 *   <li>/inventory —— 查看商品库存</li>
 *   <li>/companies —— 查看注册公司</li>
 *   <li>/company info <name> —— 查询公司详情与估值</li>
 * </ul>
 */
@Mod(FinanceMod.MOD_ID)
public class FinanceMod {

    public static final String MOD_ID = "finance";
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public FinanceMod() {
        finance.advancement.FinanceAdvancementTriggers.register();
        // 请勿修复这个错误，暂时未找到安全修复的方法，修复可能导致模组崩溃
        net.minecraftforge.eventbus.api.IEventBus modBus =
                FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        modBus.addListener(ModItems::addCreative);
        // 请勿修复这个错误，暂时未找到安全修复的方法，修复可能导致模组崩溃
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, FinanceConfig.COMMON_SPEC);
        MinecraftForge.EVENT_BUS.register(this);
        FinancePacketHandler.register();
        // 商品押注到期结算时通知玩家、退回押金
        finance.futures.FuturesClearingService.onFinalSettle = finance.simple.BetNotices::futuresSettled;

        // ---- 注册默认商品 ----
        CommodityRegistry.registerDefault(
                new Commodity("iron", "minecraft:iron_ingot", "铁锭", CommodityCategory.RAW_MATERIALS, 10));

        CommodityRegistry.registerDefault(new Commodity("wheat", "minecraft:wheat", "小麦", CommodityCategory.FOOD, 8));

        CommodityRegistry.registerDefault(
                new Commodity("stone", "minecraft:stone", "石头", CommodityCategory.BUILDING_BLOCKS, 3));

        // ---- 宏观经济：需求类别的替代品（见 docs/MACRO_ECONOMY.md）----
        CommodityRegistry.registerDefault(new Commodity("bread", "minecraft:bread", "面包", CommodityCategory.FOOD, 30));
        CommodityRegistry.registerDefault(
                new Commodity("wood", "minecraft:oak_log", "木头", CommodityCategory.BUILDING_BLOCKS, 4));
        CommodityRegistry.registerDefault(
                new Commodity("coal", "minecraft:coal", "煤", CommodityCategory.RAW_MATERIALS, 6));
        CommodityRegistry.registerDefault(
                new Commodity("wool", "minecraft:white_wool", "羊毛", CommodityCategory.MISCELLANEOUS, 9));

        // ---- 央行储备分档：1 开服即有，2/3 随全服成交额解锁，0 = 只有玩家卖了才上架 ----
        java.util.Map.of("wood", 1, "stone", 1, "coal", 1, "wheat", 1, "iron", 2, "wool", 2, "bread", 0)
                .forEach((id, tier) -> CommodityRegistry.getCommodity(id).reserveTier(tier));
        CommodityRegistry.registerDefault(
                new Commodity("sand", "minecraft:sand", "沙子", CommodityCategory.BUILDING_BLOCKS, 2).reserveTier(1));
        CommodityRegistry.registerDefault(
                new Commodity("potato", "minecraft:potato", "土豆", CommodityCategory.FOOD, 6).reserveTier(1));
        CommodityRegistry.registerDefault(
                new Commodity("carrot", "minecraft:carrot", "胡萝卜", CommodityCategory.FOOD, 6).reserveTier(1));
        CommodityRegistry.registerDefault(
                new Commodity("sugar_cane", "minecraft:sugar_cane", "甘蔗", CommodityCategory.FOOD, 5).reserveTier(1));
        CommodityRegistry.registerDefault(
                new Commodity("copper", "minecraft:copper_ingot", "铜锭", CommodityCategory.RAW_MATERIALS, 7)
                        .reserveTier(2));
        CommodityRegistry.registerDefault(
                new Commodity("leather", "minecraft:leather", "皮革", CommodityCategory.MISCELLANEOUS, 12)
                        .reserveTier(2));
        CommodityRegistry.registerDefault(
                new Commodity("gold", "minecraft:gold_ingot", "金锭", CommodityCategory.RAW_MATERIALS, 40)
                        .reserveTier(3));
        CommodityRegistry.registerDefault(
                new Commodity("redstone", "minecraft:redstone", "红石", CommodityCategory.REDSTONE, 8).reserveTier(3));
        CommodityRegistry.registerDefault(
                new Commodity("lapis", "minecraft:lapis_lazuli", "青金石", CommodityCategory.RAW_MATERIALS, 15)
                        .reserveTier(3));
        CommodityRegistry.registerDefault(
                new Commodity("glass", "minecraft:glass", "玻璃", CommodityCategory.BUILDING_BLOCKS, 6).reserveTier(0));
        CommodityRegistry.registerDefault(
                new Commodity("paper", "minecraft:paper", "纸", CommodityCategory.MISCELLANEOUS, 6).reserveTier(0));
        CommodityRegistry.registerDefault(
                new Commodity("book", "minecraft:book", "书", CommodityCategory.MISCELLANEOUS, 35).reserveTier(0));
        CommodityRegistry.registerDefault(
                new Commodity("cooked_beef", "minecraft:cooked_beef", "牛排", CommodityCategory.FOOD, 18).reserveTier(0));
        CommodityRegistry.registerDefault(
                new Commodity("obsidian", "minecraft:obsidian", "黑曜石", CommodityCategory.BUILDING_BLOCKS, 30)
                        .reserveTier(0));
        CommodityRegistry.registerDefault(
                new Commodity("ender_pearl", "minecraft:ender_pearl", "末影珍珠", CommodityCategory.MISCELLANEOUS, 60)
                        .reserveTier(0));
        CommodityRegistry.registerDefault(
                new Commodity("diamond", "minecraft:diamond", "钻石", CommodityCategory.RAW_MATERIALS, 300)
                        .reserveTier(0));
        // 绿宝石是普通商品、价格浮动：村民交易刷得越多，它就越便宜，不会冲垮货币（央行不供应）
        CommodityRegistry.registerDefault(new Commodity(
                        finance.cash.CashService.EMERALD,
                        "minecraft:emerald",
                        "绿宝石",
                        CommodityCategory.MISCELLANEOUS,
                        50)
                .reserveTier(0));
    }

    /** 注册所有命令 */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {

        BalanceCommand.register(event.getDispatcher());

        FinanceCommand.register(event.getDispatcher());

        PayCommand.register(event.getDispatcher());

        finance.command.CashCommand.register(event.getDispatcher());

        MarketCommand.register(event.getDispatcher());

        CommodityCommand.register(event.getDispatcher());

        InventoryCommand.register(event.getDispatcher());

        CompaniesCommand.register(event.getDispatcher());

        CompanyCommand.register(event.getDispatcher());

        TakeoverCommand.register(event.getDispatcher());

        MarginCommand.register(event.getDispatcher());
        finance.caravan.CaravanCommand.register(event.getDispatcher());
        finance.macro.MacroCommand.register(event.getDispatcher());
        finance.command.EarningsCommand.register(event.getDispatcher());
        PlayerBankCommand.register(event.getDispatcher());
        finance.command.SellCommand.register(event.getDispatcher());
    }

    /** 服务器启动时加载持久化数据 */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {

        CommodityRegistry.resetToDefaults();
        EconomySavedData.resetRuntimeState();
        CommodityInventorySavedData.resetRuntimeState();

        EconomySavedData.get(event.getServer());
        CandlestickService.observeDay(EconomyCycleService.currentMcDay(event.getServer()));

        CommodityInventorySavedData.get(event.getServer());
        // 旧版本玩家公司的货可能留在公司旧库存里，两份存档都读完后并进托管账户
        int migrated = finance.gameplay.company.CompanyGameplayManager.migratePlayerLegacyInventory();
        if (migrated > 0) LOGGER.info("Moved legacy inventory of {} player companies into company custody", migrated);

        // 初始化国际市场
        NpcMarketMaker.seedNpcIfNeeded();

        // 初始化系统公司
        SystemCompanyInitializer.initialize();

        // 初始化系统公司股票
        StockMarketManager.seedSystemStocksIfNeeded();
        // 开服初始化只刷新公允价值，不递减创始人锁仓（P1-2：锁仓递减仅由日结算 tickNewDay 驱动）
        StockMarketManager.updateFairValuesAndResetDay();

        // 股票做市商建仓：已上市但子账本无库存的股票，按 fairValue 从做市商总账户
        // 拨款建仓至目标库存（幂等；余额不足时跳过，不阻断启动）
        for (Stock stock : StockMarketManager.getStocks()) {
            MarketMakerLedger.seedStockInventory(stock);
        }

        StartupSelfCheckService.schedule(EconomyCycleService.currentMcDay(event.getServer()));
    }

    /** 服务器关闭/切换世界时释放所有世界级静态状态，避免下一个世界继承旧内存。 */
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        EconomySavedData.unload();
        CommodityInventorySavedData.unload();
        CommodityRegistry.resetToDefaults();
        MarketDataRequestLimiter.clear();
        finance.network.FinanceMenuRefresher.clear();
        finance.admin.AdminOperationGuard.clear();
    }

    /** Tick 调度 —— 驱动事件压力、动量衰减、噪音刷新和股价重算 */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent event) {
        if (event.phase == Phase.END) return;

        net.minecraft.server.MinecraftServer server = event.getServer();
        if (server == null) return;

        EconomyCycleService.tick(server);
        finance.macro.MacroWorldFeedback.tick(server);
        StartupSelfCheckService.tick();
        if (server.getTickCount() % finance.network.PriceTablePacket.INTERVAL_TICKS == 0) {
            finance.network.PriceTablePacket.broadcast(server);
        }
    }

    /** Delivers bounded, persisted economy/price notifications exactly once after login. */
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            finance.feedback.WorldEconomyFeedbackService.deliverPending(player);
            finance.simple.SimpleBetService.applyPending(player);
            finance.network.PriceTablePacket.send(player);
            finance.macro.EconomyAchievements.deliverPending(player);
            finance.macro.NewsService.notifyOnLogin(player);
            finance.account.AccountManager.getAccount(player.getUUID());
            finance.company.Company playerCompany = finance.company.CompanyManager.getCompanyByOwner(player.getUUID());
            if (playerCompany == null)
                playerCompany = finance.company.CompanyManager.getCompanies().stream()
                        .filter(company -> finance.gameplay.company.CompanyMembershipService.hasPermission(
                                company.getCompanyId(),
                                player.getUUID(),
                                finance.gameplay.company.CompanyPermission.VIEW_COMPANY))
                        .findFirst()
                        .orElse(null);
            if (playerCompany != null) {
                finance.advancement.FinanceAdvancementTriggers.trigger(player, "company_member");
                if (finance.gameplay.company.CompanyFacilityManager.forCompany(playerCompany.getCompanyId()).stream()
                        .anyMatch(
                                facility -> facility.status() == finance.gameplay.company.CompanyFacilityStatus.ACTIVE))
                    finance.advancement.FinanceAdvancementTriggers.trigger(player, "company_production");
            }
        }
    }

    /** Expired ground cargo becomes recoverable; authoritative units remain in transport custody. */
    @SubscribeEvent
    public void onCargoExpire(ItemExpireEvent event) {
        finance.item.SealedCargoCrateItem.markLost(event.getEntity(), "cargo item expired");
    }

    /** Vanilla discards item entities far below the world before their normal lifespan expires. */
    @SubscribeEvent
    public void onCargoLeaveLevel(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide
                && event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item
                && item.getY() < event.getLevel().getMinBuildHeight() - 16) {
            finance.item.SealedCargoCrateItem.markLost(item, "cargo item fell into the void");
        }
    }

    /** Aggregates nearby villager/golem losses; only the threshold changes settlement state. */
    @SubscribeEvent
    public void onSettlementCasualty(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof net.minecraft.server.level.ServerLevel level)
                || !(event.getEntity() instanceof net.minecraft.world.entity.npc.Villager
                        || event.getEntity() instanceof net.minecraft.world.entity.animal.IronGolem)) return;
        finance.settlement.SettlementService.nearest(level, event.getEntity().blockPosition(), 96)
                .ifPresent(settlement -> {
                    if (settlement.noteCasualty(level.getGameTime() / 24_000L)) {
                        EconomySavedData.markDirty();
                    }
                });
    }
}
