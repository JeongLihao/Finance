package finance;

import net.minecraftforge.fml.common.Mod;
import finance.command.BalanceCommand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import finance.command.FinanceCommand;
import finance.command.PayCommand;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.command.MarketCommand;
import finance.command.CommodityCommand;
import finance.command.InventoryCommand;
import net.minecraftforge.event.server.ServerStartingEvent;
import finance.data.EconomySavedData;
import finance.data.CommodityInventorySavedData;
import finance.market.NpcMarketMaker;

/**
 * Finance 模组入口。
 *
 * <h3>命令列表</h3>
 * <ul>
 *   <li>/balance —— 查询余额</li>
 *   <li>/pay —— 转账</li>
 *   <li>/finance give —— 管理员发钱（需要 OP）</li>
 *   <li>/market buy/sell/orders/cancel/history/npc —— 市场交易与 NPC 做市商</li>
 *   <li>/commodity give —— 管理员发商品（需要 OP）</li>
 *   <li>/inventory —— 查看商品库存</li>
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

        // 初始化 NPC 做市商（注入资金和初始库存）
        NpcMarketMaker.seedNpcIfNeeded();
    }
}
