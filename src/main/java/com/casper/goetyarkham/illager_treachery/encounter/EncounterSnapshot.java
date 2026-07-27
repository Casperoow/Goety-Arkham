package com.casper.goetyarkham.illager_treachery.encounter;

import com.casper.goetyarkham.illager_treachery.TreacheryRandom;
import net.minecraft.resources.ResourceLocation;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

public final class EncounterSnapshot {
    private static final BigInteger TWO_TO_64 = BigInteger.ONE.shiftLeft(64);
    private static final BigInteger UNSIGNED_LONG_MASK = TWO_TO_64.subtract(BigInteger.ONE);

    private final List<Entry> entries;
    private final BigInteger totalWeight;

    public EncounterSnapshot(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        BigInteger total = BigInteger.ZERO;
        for (Entry entry : entries) {
            if (entry.weight() <= 0L) {
                throw new IllegalArgumentException("Snapshot weights must be positive");
            }
            total = total.add(BigInteger.valueOf(entry.weight()));
        }
        this.totalWeight = total;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    public List<Entry> entries() {
        return entries;
    }

    public Optional<Entry> draw(TreacheryRandom random) {
        if (isEmpty()) {
            return Optional.empty();
        }
        BigInteger selected = randomBelow(totalWeight, random);
        BigInteger cursor = BigInteger.ZERO;
        for (Entry entry : entries) {
            cursor = cursor.add(BigInteger.valueOf(entry.weight()));
            if (selected.compareTo(cursor) < 0) {
                return Optional.of(entry);
            }
        }
        throw new IllegalStateException("Weighted encounter selection escaped its snapshot");
    }

    private static BigInteger randomBelow(BigInteger bound, TreacheryRandom random) {
        int bitLength = bound.bitLength();
        int words = Math.max(1, (bitLength + 63) / 64);
        int excessBits = words * 64 - bitLength;
        BigInteger candidate = BigInteger.ZERO;
        for (int attempt = 0; attempt < 128; attempt++) {
            candidate = BigInteger.ZERO;
            for (int word = 0; word < words; word++) {
                BigInteger unsigned = BigInteger.valueOf(random.nextLong());
                if (unsigned.signum() < 0) {
                    unsigned = unsigned.add(TWO_TO_64);
                }
                candidate = candidate.shiftLeft(64).add(unsigned.and(UNSIGNED_LONG_MASK));
            }
            if (excessBits > 0) {
                candidate = candidate.shiftRight(excessBits);
            }
            if (candidate.compareTo(bound) < 0) {
                return candidate;
            }
        }
        return candidate.mod(bound);
    }

    public record Entry(
            ResourceLocation id,
            long weight,
            IllagerTreacheryEncounter encounter) {
    }
}
