package com.casper.goetyarkham.command;

import com.casper.goetyarkham.stats.IPlayerStats;

import java.util.Optional;

public final class StatsCommandSelfTest {
    private StatsCommandSelfTest() {
    }

    public static void run() {
        Optional<IPlayerStats> missingCapability = Optional.empty();
        if (StatsCommand.isCapabilityAvailable(missingCapability)) {
            throw new AssertionError("empty capability must take the command failure path");
        }
    }
}
