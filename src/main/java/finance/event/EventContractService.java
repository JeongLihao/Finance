package finance.event;

import finance.commodity.CommodityRegistry;
import finance.contract.ContractManager;
import finance.feedback.*;
import net.minecraft.server.MinecraftServer;

import java.util.List;

/** Converts active adverse market events into bounded escrow-backed recovery work once per day. */
public final class EventContractService {
    private static final int MAX_EVENT_CONTRACTS_PER_DAY=2;
    private EventContractService(){}
    public static int processDay(MinecraftServer server,long day){if(server==null||day<0)return 0;int generated=0;for(MarketEvent event:EventManager.getActiveEvents()){if(generated>=MAX_EVENT_CONTRACTS_PER_DAY)break;if(event.getPriceMultiplier()<=1.0D)continue;List<String> ids=event.affectsAll()?CommodityRegistry.getAllCommodities().stream().map(c->c.getId()).sorted().toList():List.of(event.getCommodityId());for(String id:ids){if(generated>=MAX_EVENT_CONTRACTS_PER_DAY)break;var contract=ContractManager.generateEventProcurement(event.getName(),id,day);if(contract!=null){generated++;WorldEconomyFeedbackService.publish(server,new WorldEconomyEvent("event-contract:"+contract.id(),WorldFeedbackType.LARGE_CONTRACT,FeedbackSeverity.NOTICE,id,day,"",null,"finance.feedback.large_contract",List.of(id,Integer.toString(contract.requiredQuantity()),Long.toString(contract.rewardAmount())),FeedbackAudience.LOCAL,java.util.Set.of()));}}}return generated;}
}
