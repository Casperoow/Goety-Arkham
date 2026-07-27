package com.casper.goetyarkham.illager_treachery;

public final class TriggerDecisionPolicy {
    private TriggerDecisionPolicy() {
    }

    public static Mode choose(
            boolean forced,
            boolean guaranteed,
            boolean coolingDown) {
        if (forced) {
            return Mode.FORCED;
        }
        if (guaranteed) {
            return Mode.GUARANTEED;
        }
        return coolingDown ? Mode.SKIP_COOLDOWN : Mode.RANDOM;
    }

    public enum Mode {
        FORCED,
        GUARANTEED,
        RANDOM,
        SKIP_COOLDOWN
    }
}
