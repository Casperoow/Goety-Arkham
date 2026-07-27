package com.casper.goetyarkham.illager_treachery;

@FunctionalInterface
public interface TreacheryRandom {
    long nextLong();

    default double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }
}
