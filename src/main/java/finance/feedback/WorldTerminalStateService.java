package finance.feedback;

import finance.bank.BankStatus;
import finance.bank.BankingManager;
import finance.block.FinanceTerminalBlock;
import finance.contract.ContractManager;
import finance.diagnostic.ModuleHealthRegistry;
import finance.gameplay.FinanceTerminalType;
import finance.gameplay.WorldTerminalRegistry;
import finance.market.MarketOpportunityService;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Low-frequency audit for indexed and already-loaded venue blocks; never scans chunks. */
public final class WorldTerminalStateService {
    private static long lastAuditDay=-1;
    private static boolean lastShortage;
    private static FinanceTerminalBlock.Indicator lastBankIndicator=FinanceTerminalBlock.Indicator.NORMAL;
    private WorldTerminalStateService(){}

    public static void auditDay(MinecraftServer server,long day){
        if(server==null||day<0||day<=lastAuditDay)return;
        lastAuditDay=day;
        boolean marketOnline=ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.MARKET);
        var opportunity=MarketOpportunityService.summary(day,id->0);
        boolean shortage=marketOnline&&!opportunity.shortageCommodity().isBlank()
                &&opportunity.shortageStock()<ContractManager.lowStockThreshold();
        FinanceTerminalBlock.Indicator marketIndicator=!marketOnline?FinanceTerminalBlock.Indicator.OFFLINE
                :shortage?FinanceTerminalBlock.Indicator.SHORTAGE:FinanceTerminalBlock.Indicator.NORMAL;
        update(server,FinanceTerminalType.MARKET_TERMINAL,marketIndicator);
        if(marketOnline&&shortage!=lastShortage){
            WorldEconomyFeedbackService.publish(server,new WorldEconomyEvent(shortage?"shortage":"recovery",
                    shortage?WorldFeedbackType.MARKET_SHORTAGE:WorldFeedbackType.MARKET_RECOVERY,
                    shortage?FeedbackSeverity.WARNING:FeedbackSeverity.INFO,opportunity.shortageCommodity(),day,"",null,
                    shortage?"finance.feedback.market_shortage":"finance.feedback.market_recovery",
                    List.of(opportunity.shortageCommodity(),Integer.toString(opportunity.shortageStock())),
                    FeedbackAudience.LOCAL,Set.of()));
        }
        lastShortage=shortage;

        BankStatus worst=BankingManager.banks().values().stream().map(bank->bank.status())
                .max(Comparator.comparingInt(WorldTerminalStateService::rank)).orElse(BankStatus.ACTIVE);
        FinanceTerminalBlock.Indicator bankIndicator=worst==BankStatus.RESOLUTION||worst==BankStatus.FAILED
                ?FinanceTerminalBlock.Indicator.RESOLUTION:worst==BankStatus.RESTRICTED
                ?FinanceTerminalBlock.Indicator.RESTRICTED:FinanceTerminalBlock.Indicator.NORMAL;
        update(server,FinanceTerminalType.BANK_COUNTER,bankIndicator);
        if(bankIndicator!=lastBankIndicator&&(bankIndicator==FinanceTerminalBlock.Indicator.RESTRICTED
                ||bankIndicator==FinanceTerminalBlock.Indicator.RESOLUTION)){
            WorldEconomyFeedbackService.publish(server,new WorldEconomyEvent("bank-state:"+bankIndicator,
                    bankIndicator==FinanceTerminalBlock.Indicator.RESOLUTION?WorldFeedbackType.BANK_RESOLUTION:WorldFeedbackType.BANK_RESTRICTED,
                    bankIndicator==FinanceTerminalBlock.Indicator.RESOLUTION?FeedbackSeverity.CRITICAL:FeedbackSeverity.WARNING,
                    "bank-system",day,"",null,bankIndicator==FinanceTerminalBlock.Indicator.RESOLUTION
                    ?"finance.feedback.bank_resolution":"finance.feedback.bank_restricted",List.of(),FeedbackAudience.LOCAL,Set.of()));
        }
        lastBankIndicator=bankIndicator;
    }

    private static int rank(BankStatus status){return switch(status){case ACTIVE,MERGED->0;case WATCH->1;case RESTRICTED->2;case RESOLUTION->3;case FAILED->4;};}
    private static void update(MinecraftServer server,FinanceTerminalType type,FinanceTerminalBlock.Indicator indicator){
        for(WorldTerminalRegistry.TerminalRecord record:WorldTerminalRegistry.byType(type)){
            ResourceLocation location=ResourceLocation.tryParse(record.dimensionId());if(location==null)continue;
            ServerLevel level=server.getLevel(ResourceKey.create(Registries.DIMENSION,location));
            if(level==null||!level.isLoaded(record.position()))continue;
            var state=level.getBlockState(record.position());
            if(state.getBlock() instanceof FinanceTerminalBlock&&state.getValue(FinanceTerminalBlock.INDICATOR)!=indicator)
                level.setBlock(record.position(),state.setValue(FinanceTerminalBlock.INDICATOR,indicator),3);
        }
    }
    public static void clearDirect(){lastAuditDay=-1;lastShortage=false;lastBankIndicator=FinanceTerminalBlock.Indicator.NORMAL;}
}
