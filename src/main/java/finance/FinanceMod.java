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
import finance.company.CompanyManager;
import finance.company.SystemCompanyInitializer;
import net.minecraftforge.event.server.ServerStartingEvent;
import finance.data.EconomySavedData;
import finance.data.CommodityInventorySavedData;
import finance.market.NpcMarketMaker;
import finance.event.EventManager;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.ServerTickEvent;

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

        MinecraftForge.EVENT_BUS.register(this);

        // ---- 注册默认商品 ----
        CommodityRegistry.register(
                new Commodity(
                        "iron",
                        "Iron",
                        CommodityCategory.RAW_MATERIAL,
                        10
                )
        );

        CommodityRegistry.register(
                new Commodity(
                        "wheat",
                        "Wheat",
                        CommodityCategory.FOOD,
                        8
                )
        );

        CommodityRegistry.register(
                new Commodity(
                        "coal",
                        "Coal",
                        CommodityCategory.ENERGY,
                        5
                )
        );

        CommodityRegistry.register(
                new Commodity(
                        "steel",
                        "Steel",
                        CommodityCategory.INDUSTRIAL,
                        20
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

        EconomySavedData.get(
                event.getServer()
        );

        CommodityInventorySavedData.get(
                event.getServer()
        );

        // 初始化国际市场
        NpcMarketMaker.seedNpcIfNeeded();

        // 初始化系统公司
        SystemCompanyInitializer.initialize();
    }

    /** Tick 调度 —— 驱动事件压力、动量衰减和噪音刷新 */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent event) {
        if (event.phase == Phase.END) return;

        net.minecraft.server.MinecraftServer server = event.getServer();
        if (server == null) return;

        int tick = server.getTickCount();
        if (tick <= 0) return;

        // 每个MC天发出一轮事件脉冲 + 公司经营（24000 ticks）
        if (tick % 24000 == 0) {
            EventManager.onDayTick(server);
            CompanyManager.tickAll();
            NpcMarketMaker.naturalConsumeAll();
        }

        // 每3分钟刷新噪音（3600 ticks）
        if (tick % 3600 == 0) {
            NpcMarketMaker.tickAllNoise();
        }

        // 每分钟衰减动能（1200 ticks）
        if (tick % 1200 == 0) {
            NpcMarketMaker.tickAllMomentum();
        }
    }
}
