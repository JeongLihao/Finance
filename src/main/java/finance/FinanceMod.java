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
import finance.stock.StockMarketManager;
import finance.diagnostic.StartupSelfCheckService;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
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
        // 请勿修复这个错误，暂时未找到安全修复的方法，修复可能导致模组崩溃
        ModMenus.register(FMLJavaModLoadingContext.get().getModEventBus());
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
}
