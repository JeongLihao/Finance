package finance.village;

import java.util.Map;

/**
 * 村民职业：决定每种职业的人均日需求篮子（Vicky 3 Pops 需求体系的映射）。
 *
 * <p>人口模型是抽象的，不读取真实村民实体（见设计文档 §2）：原版没有"村庄"对象，
 * 读实体会有性能抖动与刷人口印钱风险。</p>
 */
public enum VillageProfession {
    /** 农民：主食需求大 */
    FARMER,
    /** 工匠：建材与工具需求大 */
    ARTISAN,
    /** 守卫：铁器需求大 */
    GUARD,
    /** 其他 */
    OTHER;

    /** 默认职业占比（基点，和 = 10000）。 */
    public int defaultRatioBps() {
        switch (this) {
            case FARMER:
                return 4000;
            case ARTISAN:
                return 3000;
            case GUARD:
                return 1500;
            case OTHER:
                return 1500;
            default:
                throw new AssertionError("未知职业: " + this);
        }
    }

    /**
     * 人均日需求篮子：商品 id → 每人每天需求单位数。
     *
     * <p>返回不可变映射；篮子商品限定有 MC 实物映射的商品（wheat/iron/stone），
     * 与 {@code LocalDemand} 现状对齐。</p>
     */
    public Map<String, Double> needsBasket() {
        switch (this) {
            case FARMER:
                return Map.of("wheat", 2.0, "stone", 0.2, "iron", 0.1);
            case ARTISAN:
                return Map.of("wheat", 1.0, "stone", 1.0, "iron", 0.3);
            case GUARD:
                return Map.of("wheat", 1.5, "stone", 0.1, "iron", 0.5);
            case OTHER:
                return Map.of("wheat", 1.0, "stone", 0.3, "iron", 0.1);
            default:
                throw new AssertionError("未知职业: " + this);
        }
    }
}
