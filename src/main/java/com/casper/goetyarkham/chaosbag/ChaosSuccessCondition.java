package com.casper.goetyarkham.chaosbag;

@FunctionalInterface
public interface ChaosSuccessCondition {
    ChaosSuccessCondition AT_LEAST = (finalValue, targetValue) ->
            finalValue >= targetValue;

    boolean succeeds(int finalValue, int targetValue);
}
