package com.casper.goetyarkham.command;

import com.casper.goetyarkham.illager_treachery.IllagerTreacheryState;

public final class IllagerTreacheryCommandSelfTest {
    private IllagerTreacheryCommandSelfTest() {
    }

    public static void run() {
        if (IllagerTreacheryCommand.requiredPermissionLevel() != 2) {
            throw new AssertionError("mutating command permission is not level 2");
        }
        if (IllagerTreacheryCommand.isWeightValid(-1L)) {
            throw new AssertionError("negative encounter weight accepted");
        }
        if (!IllagerTreacheryCommand.isWeightValid(0L)) {
            throw new AssertionError("zero encounter weight rejected");
        }
        if (IllagerTreacheryCommand.canReset(IllagerTreacheryState.RESOLVING)) {
            throw new AssertionError("reset accepted during resolution");
        }
        if (!IllagerTreacheryCommand.canReset(IllagerTreacheryState.PREPARING)) {
            throw new AssertionError("reset should only reject RESOLVING");
        }
    }
}
