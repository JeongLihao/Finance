package finance.gameplay.company;

/**
 * Explanatory health classification for the operating snapshot. It is derived
 * from authoritative state and is only used for UI and diagnostics; it never
 * gates writes or bypasses {@link finance.diagnostic.ModuleHealthRegistry}.
 */
public enum CompanyOperatingHealth {
    HEALTHY,
    MATERIAL_SHORTAGE,
    CAPACITY_BLOCKED,
    LOGISTICS_DELAY,
    DEBT_PRESSURE,
    BANKRUPTCY_RISK
}
