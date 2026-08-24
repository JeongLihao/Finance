package finance;

import net.minecraftforge.fml.common.Mod;
import finance.command.BalanceCommand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import finance.command.FinanceCommand;
import finance.command.PayCommand;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.command.MarketCommand;
import finance.command.CommodityCommand;
import finance.command.InventoryCommand;
import finance.command.CompaniesCommand;
import finance.command.CompanyCommand;
import finance.company.SystemCompanyInitializer;
import finance.config.FinanceConfig;
import finance.cycle.EconomyCycleService;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import finance.data.EconomySavedData;
import finance.data.CommodityInventorySavedData;
import finance.market.NpcMarketMaker;
import finance.network.FinancePacketHandler;
import finance.network.MarketDataRequestLimiter;
import finance.chart.CandlestickService;
import finance.registry.ModMenus;
import finance.registry.ModBlocks;
import finance.registry.ModItems;
import finance.registry.ModBlockEntities;
import finance.stock.StockMarketManager;
import finance.diagnostic.StartupSelfCheckService;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.item.ItemExpireEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.fml.ModLoadingContext;
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

    public FinanceMod(){
        finance.advancement.FinanceAdvancementTriggers.register();
        // 请勿修复这个错误，暂时未找到安全修复的方法，修复可能导致模组崩溃
        net.minecraftforge.eventbus.api.IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        modBus.addListener(ModItems::addCreative);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, FinanceConfig.COMMON_SPEC);
        MinecraftForge.EVENT_BUS.register(this);
        FinancePacketHandler.register();

        // ---- 注册默认商品 ----
        CommodityRegistry.registerDefault(
                new Commodity(
                        "iron",
                        "minecraft:iron_ingot",
                        "铁锭",
                        CommodityCategory.RAW_MATERIALS,
                        10
                )
        );

        CommodityRegistry.registerDefault(
                new Commodity(
                        "wheat",
                        "minecraft:wheat",
                        "小麦",
                        CommodityCategory.FOOD,
                        8
                )
        );

        CommodityRegistry.registerDefault(
                new Commodity(
                        "stone",
                        "minecraft:stone",
                        "石头",
                        CommodityCategory.BUILDING_BLOCKS,
                        3
                )
        );
    }

    /** 注册所有命令 */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event){

        BalanceCommand.register(event.getDispatcher());

        FinanceCommand.register(event.getDispatcher());

        PayCommand.register(event.getDispatcher());

        MarketCommand.register(event.getDispatcher());

        CommodityCommand.register(event.getDispatcher());

        InventoryCommand.register(event.getDispatcher());

        CompaniesCommand.register(event.getDispatcher());

        CompanyCommand.register(event.getDispatcher());
    }

    /** 服务器启动时加载持久化数据 */
    @SubscribeEvent
    public void onServerStarting(
            ServerStartingEvent event
    ) {

        CommodityRegistry.resetToDefaults();
        EconomySavedData.resetRuntimeState();
        CommodityInventorySavedData.resetRuntimeState();

        EconomySavedData.get(
                event.getServer()
        );
        CandlestickService.observeDay(EconomyCycleService.currentMcDay(event.getServer()));

        CommodityInventorySavedData.get(
                event.getServer()
        );

        // 初始化国际市场
        NpcMarketMaker.seedNpcIfNeeded();

        // 初始化系统公司
        SystemCompanyInitializer.initialize();

        // 初始化系统公司股票
        StockMarketManager.seedSystemStocksIfNeeded();
        StockMarketManager.updateFairValuesAndResetDay();
        StartupSelfCheckService.schedule(EconomyCycleService.currentMcDay(event.getServer()));
    }

    /** 服务器关闭/切换世界时释放所有世界级静态状态，避免下一个世界继承旧内存。 */
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        EconomySavedData.unload();
        CommodityInventorySavedData.unload();
        CommodityRegistry.resetToDefaults();
        MarketDataRequestLimiter.clear();
        finance.admin.AdminOperationGuard.clear();
    }

    /** Tick 调度 —— 驱动事件压力、动量衰减、噪音刷新和股价重算 */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent event) {
        if (event.phase == Phase.END) return;

        net.minecraft.server.MinecraftServer server = event.getServer();
        if (server == null) return;

        EconomyCycleService.tick(server);
        StartupSelfCheckService.tick();
    }

    /** Delivers bounded, persisted economy/price notifications exactly once after login. */
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            finance.feedback.WorldEconomyFeedbackService.deliverPending(player);
            finance.account.AccountManager.getAccount(player.getUUID());
            finance.company.Company playerCompany=finance.company.CompanyManager.getCompanyByOwner(player.getUUID());
            if(playerCompany==null)playerCompany=finance.company.CompanyManager.getCompanies().stream().filter(company->
                    finance.gameplay.company.CompanyMembershipService.hasPermission(company.getCompanyId(),player.getUUID(),
                            finance.gameplay.company.CompanyPermission.VIEW_COMPANY)).findFirst().orElse(null);
            if(playerCompany!=null){
                finance.advancement.FinanceAdvancementTriggers.trigger(player,"company_member");
                if(finance.gameplay.company.CompanyFacilityManager.forCompany(playerCompany.getCompanyId()).stream()
                        .anyMatch(facility->facility.lastProcessedDay()>=0))
                    finance.advancement.FinanceAdvancementTriggers.trigger(player,"company_production");
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
        if (!event.getLevel().isClientSide && event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item
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
        finance.settlement.SettlementService.nearest(level, event.getEntity().blockPosition(), 96).ifPresent(settlement -> {
            if (settlement.noteCasualty(level.getGameTime() / 24_000L)) {
                EconomySavedData.markDirty();
            }
        });
    }
}
