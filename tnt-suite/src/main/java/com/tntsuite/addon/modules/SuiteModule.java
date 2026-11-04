package com.tntsuite.addon.modules;

import com.tntsuite.addon.TntSuiteAddon;
import com.tntsuite.addon.util.Hotbar;
import meteordevelopment.meteorclient.systems.modules.Module;

import java.util.HashSet;
import java.util.Set;

/** Base class for every module in the suite: tick/cooldown bookkeeping, warn-once, error guard. */
public abstract class SuiteModule extends Module {
    protected int tick;
    protected int cooldown;
    private final Set<String> warned = new HashSet<>();

    protected SuiteModule(String name, String description) {
        super(TntSuiteAddon.CATEGORY, name, description);
    }

    /** Chat warning that is only shown once per activation (no spam). */
    protected void warnOnce(String key, String message) {
        if (warned.add(key)) warning(message);
    }

    /** Runs the body; on an unexpected exception, logs it and disables the module instead of crashing. */
    protected void safely(Runnable body) {
        try {
            body.run();
        } catch (RuntimeException ex) {
            TntSuiteAddon.LOG.error("{} crashed", name, ex);
            error("Unexpected error, module disabled. Check the log for details.");
            toggle();
        }
    }

    @Override
    public void onActivate() {
        resetBase();
        reset();
    }

    @Override
    public void onDeactivate() {
        Hotbar.restore();
        resetBase();
        reset();
    }

    private void resetBase() {
        tick = 0;
        cooldown = 0;
        warned.clear();
    }

    /** Clear module-specific state. */
    protected abstract void reset();
}
