package com.casper.goetyarkham.stats;

public record StatSnapshot(int base, int equipment, int temporary, int derived) {
    public int finalValue() {
        return base + equipment + temporary + derived;
    }
}
