package com.casper.goetyarkham.illager_treachery;

public final class TreacheryEventLock {
    private IllagerTreacheryState state = IllagerTreacheryState.IDLE;

    public synchronized IllagerTreacheryState state() {
        return state;
    }

    public synchronized boolean acceptsTrigger() {
        return state == IllagerTreacheryState.IDLE;
    }

    public synchronized boolean beginPreparing() {
        if (state != IllagerTreacheryState.IDLE) {
            return false;
        }
        state = IllagerTreacheryState.PREPARING;
        return true;
    }

    public synchronized void beginResolving() {
        if (state != IllagerTreacheryState.PREPARING) {
            throw new IllegalStateException(
                    "Cannot enter RESOLVING from " + state);
        }
        state = IllagerTreacheryState.RESOLVING;
    }

    public synchronized void release() {
        state = IllagerTreacheryState.IDLE;
    }
}
