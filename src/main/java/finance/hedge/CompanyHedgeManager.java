package finance.hedge;

import java.util.*;

public final class CompanyHedgeManager {
    public static final int MAX_OBJECTIVES=4096;
    private static final Map<UUID,CompanyHedgeObjective> OBJECTIVES=new LinkedHashMap<>();
    private CompanyHedgeManager(){}
    public static synchronized boolean register(CompanyHedgeObjective value){
        if(value==null||OBJECTIVES.size()>=finance.config.FinanceConfig.maxHedgeObjectives()||OBJECTIVES.containsKey(value.id()))return false;
        OBJECTIVES.put(value.id(),value);return true;
    }
    public static synchronized boolean restore(CompanyHedgeObjective value){if(value==null||OBJECTIVES.size()>=MAX_OBJECTIVES||OBJECTIVES.containsKey(value.id()))return false;OBJECTIVES.put(value.id(),value);return true;}
    public static synchronized CompanyHedgeObjective get(UUID id){return OBJECTIVES.get(id);}
    public static synchronized Collection<CompanyHedgeObjective> all(){return List.copyOf(OBJECTIVES.values());}
    public static synchronized List<CompanyHedgeObjective> forCompany(UUID companyId){return OBJECTIVES.values().stream().filter(v->v.companyId().equals(companyId)).toList();}
    public static synchronized List<CompanyHedgeObjective> visibleTo(UUID companyId,boolean admin,int limit){if(limit<=0)return List.of();List<CompanyHedgeObjective> out=new ArrayList<>(Math.min(limit,OBJECTIVES.size()));for(var value:OBJECTIVES.values()){if(admin||companyId!=null&&value.companyId().equals(companyId))out.add(value);if(out.size()>=limit)break;}return List.copyOf(out);}
    public static synchronized boolean remove(UUID id){return OBJECTIVES.remove(id)!=null;}
    public static synchronized boolean operationExists(UUID companyId,UUID actor,String key){return OBJECTIVES.values().stream().anyMatch(v->v.companyId().equals(companyId)&&v.operatorId().equals(actor)&&v.operationKey().equals(key));}
    public static synchronized void clearDirect(){OBJECTIVES.clear();}
}
