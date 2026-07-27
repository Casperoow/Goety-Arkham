package com.casper.goetyarkham.illager_treachery;

public enum TriggerSource {
    DAILY(false, false),
    RITUAL(false, true),
    RAID(true, false),
    ILLAGER_ASSAULT(true, false),
    ITEM(true, true),
    COMMAND(true, true);

    private final boolean forced;
    private final boolean requiresSubmittedCandidate;

    TriggerSource(boolean forced, boolean requiresSubmittedCandidate) {
        this.forced = forced;
        this.requiresSubmittedCandidate = requiresSubmittedCandidate;
    }

    public boolean isForced() {
        return forced;
    }

    public boolean requiresSubmittedCandidate() {
        return requiresSubmittedCandidate;
    }
}
