package com.casper.goetyarkham.illager_treachery;

import java.util.List;

/** Pure slot-order selection rules for a Grotesque Statue payment. */
public final class GrotesqueStatuePaymentPolicy {
    private GrotesqueStatuePaymentPolicy() {
    }

    public static int firstAffordableIndex(List<Integer> storedSouls, int cost) {
        if (cost <= 0) {
            throw new IllegalArgumentException("cost must be positive");
        }
        for (int index = 0; index < storedSouls.size(); index++) {
            if (storedSouls.get(index) >= cost) {
                return index;
            }
        }
        return -1;
    }
}
