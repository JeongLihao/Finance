package finance.compat.ponder;

import java.util.concurrent.atomic.AtomicBoolean;
import net.createmod.ponder.foundation.PonderIndex;

/** Loaded reflectively only when the optional Ponder mod is present. */
public final class FinancePonderBootstrap {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private FinancePonderBootstrap() {}

    public static void register() {
        if (REGISTERED.compareAndSet(false, true)) {
            PonderIndex.addPlugin(new FinancePonderPlugin());
        }
    }
}
