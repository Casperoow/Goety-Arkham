package com.casper.goetyarkham.illager_treachery;

public final class DailyProgress {
    public static final long UNINITIALIZED_DAY = Long.MIN_VALUE;

    private int validDays;
    private long lastProcessedDay;

    public DailyProgress(int validDays, long lastProcessedDay) {
        this.validDays = Math.max(0, validDays);
        this.lastProcessedDay = lastProcessedDay;
    }

    public boolean enterDay(long minecraftDay) {
        if (lastProcessedDay == UNINITIALIZED_DAY) {
            lastProcessedDay = minecraftDay;
            return false;
        }
        if (minecraftDay <= lastProcessedDay) {
            return false;
        }
        lastProcessedDay = minecraftDay;
        return true;
    }

    public int recordValidDay() {
        if (validDays < Integer.MAX_VALUE) {
            validDays++;
        }
        return validDays;
    }

    public void reset() {
        validDays = 0;
    }

    public int validDays() {
        return validDays;
    }

    public long lastProcessedDay() {
        return lastProcessedDay;
    }
}
