package finance.exploration;

import net.minecraft.core.BlockPos;
import java.util.UUID;

public final class ExplorationAssignment {
    private final UUID id,playerId,escrowId;
    private final String dimensionId,theme;
    private final BlockPos target;
    private final ExplorationTargetType targetType;
    private final long reward,createdDay,deadlineDay;
    private ExplorationStatus status;
    public ExplorationAssignment(UUID id,UUID playerId,UUID escrowId,String dimensionId,BlockPos target,
                                 ExplorationTargetType targetType,String theme,long reward,long createdDay,
                                 long deadlineDay,ExplorationStatus status){
        if(id==null||playerId==null||escrowId==null||dimensionId==null||dimensionId.isBlank()||dimensionId.length()>128
                ||target==null||targetType==null||theme==null||theme.isBlank()||theme.length()>32||reward<=0
                ||createdDay<0||deadlineDay<=createdDay||status==null)throw new IllegalArgumentException("invalid exploration assignment");
        this.id=id;this.playerId=playerId;this.escrowId=escrowId;this.dimensionId=dimensionId;this.target=target.immutable();
        this.targetType=targetType;this.theme=theme;this.reward=reward;this.createdDay=createdDay;this.deadlineDay=deadlineDay;this.status=status;
    }
    public UUID id(){return id;} public UUID playerId(){return playerId;} public UUID escrowId(){return escrowId;}
    public String dimensionId(){return dimensionId;} public BlockPos target(){return target;} public ExplorationTargetType targetType(){return targetType;}
    public String theme(){return theme;} public long reward(){return reward;} public long createdDay(){return createdDay;} public long deadlineDay(){return deadlineDay;}
    public ExplorationStatus status(){return status;} public void complete(){if(status==ExplorationStatus.ACTIVE)status=ExplorationStatus.COMPLETED;}
    public void expire(){if(status==ExplorationStatus.ACTIVE)status=ExplorationStatus.EXPIRED;} public void cancel(){if(status==ExplorationStatus.ACTIVE)status=ExplorationStatus.CANCELLED;}
    public void quarantine(){status=ExplorationStatus.QUARANTINED;}
}
