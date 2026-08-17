package finance.insurance;

import java.util.UUID;

/** Immutable authoritative evidence produced before/while the server applies a real loss. */
public record InsuredLossEvent(UUID id,RiskEventType type,UUID companyId,UUID objectId,long occurredDay,long endDay,long seed,int intensityBps,String commodityId,long quantityBefore,long quantityLost,long unitPrice,long verifiedLoss,long offsetCompensation,String evidence,boolean processed){
    public InsuredLossEvent{commodityId=commodityId==null?"":commodityId;evidence=evidence==null?"":evidence;if(id==null||type==null||companyId==null||objectId==null||occurredDay<0||endDay<occurredDay||intensityBps<0||intensityBps>10_000||quantityBefore<0||quantityLost<0||quantityLost>quantityBefore||unitPrice<0||verifiedLoss<0||offsetCompensation<0)throw new IllegalArgumentException();}
}
