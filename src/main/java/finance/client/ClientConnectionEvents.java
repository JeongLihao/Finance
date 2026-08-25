package finance.client;

import finance.FinanceMod;
import finance.client.chart.CandlestickClientCache;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FinanceMod.MOD_ID, value = Dist.CLIENT)
public final class ClientConnectionEvents {
    private ClientConnectionEvents() {}

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        CandlestickClientCache.clear();
        FinancialProductClientCache.clear();
        FuturesClientCache.clear();
        BankClientCache.clear();
        FundClientCache.clear();
        InsuranceClientCache.clear();GovernanceClientCache.clear();GovernanceTaskClientState.clear();
        TutorialClientState.clear();
    }
}
