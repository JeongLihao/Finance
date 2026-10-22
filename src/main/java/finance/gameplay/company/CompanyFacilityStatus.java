package finance.gameplay.company;

/** 公司机器的状态：在干活、缺原料或燃料、成品放不下、停了（如矿挖到底）、公司破产冻结、公司已不存在。 */
public enum CompanyFacilityStatus {
    ACTIVE,
    MISSING_INPUT,
    OUTPUT_FULL,
    DISABLED,
    BANKRUPTCY_HOLD,
    ORPHANED
}
