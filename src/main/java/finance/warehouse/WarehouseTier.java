package finance.warehouse;

import finance.config.FinanceConfig;

/** Three bounded, world-visible warehouse tiers. */
public enum WarehouseTier {
    BASIC(1),
    REINFORCED(2),
    INDUSTRIAL(3);

    private final int level;

    WarehouseTier(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    public int capacity() {
        return FinanceConfig.warehouseCapacity(level);
    }

    public int transferLimit() {
        return FinanceConfig.warehouseTransferLimit(level);
    }

    public WarehouseTier next() {
        return this == BASIC ? REINFORCED : this == REINFORCED ? INDUSTRIAL : null;
    }

    public static WarehouseTier fromLegacyCapacity(int capacity) {
        if (capacity > FinanceConfig.warehouseCapacity(2)) return INDUSTRIAL;
        if (capacity > FinanceConfig.warehouseCapacity(1)) return REINFORCED;
        return BASIC;
    }
}
