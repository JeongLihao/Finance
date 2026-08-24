package finance.warehouse;

import finance.config.FinanceConfig;

/** Three bounded, world-visible warehouse tiers. */
public enum WarehouseTier {
    BASIC(1, 1),
    REINFORCED(2, 4),
    INDUSTRIAL(3, 8);

    private final int level;
    private final int facilitySlots;

    WarehouseTier(int level, int facilitySlots) {
        this.level = level;
        this.facilitySlots = facilitySlots;
    }

    public int level() { return level; }
    public int facilitySlots() { return facilitySlots; }
    public int capacity() { return FinanceConfig.warehouseCapacity(level); }
    public int transferLimit() { return FinanceConfig.warehouseTransferLimit(level); }

    public WarehouseTier next() {
        return this == BASIC ? REINFORCED : this == REINFORCED ? INDUSTRIAL : null;
    }

    public static WarehouseTier fromLevel(int level) {
        return level >= 3 ? INDUSTRIAL : level == 2 ? REINFORCED : BASIC;
    }

    public static WarehouseTier fromLegacyCapacity(int capacity) {
        if (capacity > FinanceConfig.warehouseCapacity(2)) return INDUSTRIAL;
        if (capacity > FinanceConfig.warehouseCapacity(1)) return REINFORCED;
        return BASIC;
    }
}
