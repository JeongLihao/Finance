package finance.hedge;

import java.util.UUID;

/**
 * A reporting link between a company's physical risk and an operator's real futures position.
 * The link never owns margin, cash or positions and therefore cannot manufacture a hedge trade.
 */
public final class CompanyHedgeObjective {
    private final UUID id, companyId, operatorId, contractId;
    private final String commodityId, operationKey;
    private final HedgeObjectiveType type;
    private final long targetQuantity, createdDay, deadlineDay;

    public CompanyHedgeObjective(UUID id, UUID companyId, UUID operatorId, UUID contractId,
                                 String commodityId, HedgeObjectiveType type, long targetQuantity,
                                 long createdDay, long deadlineDay, String operationKey) {
        if (id == null || companyId == null || operatorId == null || contractId == null || commodityId == null
                || commodityId.isBlank() || commodityId.length() > 64 || type == null || targetQuantity <= 0
                || createdDay < 0 || deadlineDay <= createdDay || operationKey == null || operationKey.isBlank()
                || operationKey.length() > 48) throw new IllegalArgumentException("invalid hedge objective");
        this.id=id; this.companyId=companyId; this.operatorId=operatorId; this.contractId=contractId;
        this.commodityId=commodityId.trim().toLowerCase(java.util.Locale.ROOT); this.type=type;
        this.targetQuantity=targetQuantity; this.createdDay=createdDay; this.deadlineDay=deadlineDay;
        this.operationKey=operationKey;
    }
    public UUID id(){return id;} public UUID companyId(){return companyId;} public UUID operatorId(){return operatorId;}
    public UUID contractId(){return contractId;} public String commodityId(){return commodityId;}
    public HedgeObjectiveType type(){return type;} public long targetQuantity(){return targetQuantity;}
    public long createdDay(){return createdDay;} public long deadlineDay(){return deadlineDay;}
    public String operationKey(){return operationKey;}
}
