package finance.diagnostic;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class ModuleHealthRegistry {
    public enum Module { ACCOUNT, MARKET, WAREHOUSE, LOGISTICS, SETTLEMENT, EXPLORATION, CONTRACT, COMPANY_GAMEPLAY, STOCK, DEBT, BANKING, FUTURES, FUND, INSURANCE, HISTORY, CYCLE }
    public record Status(ModuleRunState state, String reason, long sinceDay) { }
    private static final EnumMap<Module, Status> STATES = new EnumMap<>(Module.class);
    static { clear(); }
    private ModuleHealthRegistry() { }
    public static synchronized boolean mayWrite(Module module) { return status(module).state() == ModuleRunState.ACTIVE; }
    public static synchronized Status status(Module module) { return STATES.getOrDefault(module, new Status(ModuleRunState.ACTIVE, "", -1)); }
    public static synchronized Map<Module, Status> statuses() { return Collections.unmodifiableMap(new EnumMap<>(STATES)); }
    public static synchronized void restrict(Module module, ModuleRunState state, String reason, long day) {
        if (module == null || state == null) return;
        STATES.put(module, new Status(state, reason == null ? "" : reason.substring(0, Math.min(256, reason.length())), Math.max(-1, day)));
    }
    public static synchronized void resume(Module module) { if (module != null) STATES.put(module, new Status(ModuleRunState.ACTIVE, "", -1)); }
    public static synchronized void clear() { STATES.clear(); for (Module module : Module.values()) resume(module); }
}
