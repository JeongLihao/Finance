package finance.gameplay.company;

import finance.company.CompanyType;

import java.util.Map;

public final class CompanyUpgradeRequirementService {
    public record Requirement(long cash, Map<String, Integer> materials) {}
    private CompanyUpgradeRequirementService() {}
    public static Requirement requirement(CompanyType type, CompanyFacilityType facilityType, int currentLevel) {
        if (type == null || facilityType == null || currentLevel < 1 || currentLevel >= CompanyFacilityRecord.MAX_LEVEL) return null;
        int next = currentLevel + 1;
        return new Requirement(5_000L * next, Map.of("iron", 16 * next, "stone", 32 * next));
    }
}
