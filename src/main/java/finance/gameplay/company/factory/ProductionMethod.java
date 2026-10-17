package finance.gameplay.company.factory;

import finance.company.CompanyType;

import java.util.Map;

/**
 * 一种生产方式。投入和产出按"等级 1、满员"给出，实际再乘等级倍率、在岗比例和改造期系数。
 *
 * @param key           本地化与网络用的稳定键，如 food.manual
 * @param required      该档所需的最少设备数量
 * @param jobsPerDevice 每台被计入的设备提供的岗位
 */
public record ProductionMethod(CompanyType companyType, ProductionTier tier, String key, String displayName,
                               Map<FactoryEquipment, Integer> required, int jobsPerDevice,
                               Map<String, Integer> inputs, Map<String, Integer> outputs) {
    public ProductionMethod {
        required = Map.copyOf(required);
        inputs = Map.copyOf(inputs);
        outputs = Map.copyOf(outputs);
    }

    public String translationKey() { return "screen.finance.factory.method." + key; }

    /** 设备是否满足本档的最少数量要求。 */
    public boolean satisfiedBy(Map<FactoryEquipment, Integer> counts) {
        for (Map.Entry<FactoryEquipment, Integer> e : required.entrySet()) {
            if (count(counts, e.getKey()) < e.getValue()) return false;
        }
        return true;
    }

    /** 本档在给定设备下的岗位（未受等级上限约束）。每种设备最多计 {@code maxPerType} 台。 */
    public long deviceJobs(Map<FactoryEquipment, Integer> counts, int maxPerType) {
        long jobs = 0;
        for (FactoryEquipment e : required.keySet()) {
            jobs += (long) Math.min(count(counts, e), Math.max(0, maxPerType)) * jobsPerDevice;
        }
        return jobs;
    }

    static int count(Map<FactoryEquipment, Integer> counts, FactoryEquipment e) {
        if (counts == null) return 0;
        Integer v = counts.get(e);
        return v == null ? 0 : Math.max(0, v);
    }
}
