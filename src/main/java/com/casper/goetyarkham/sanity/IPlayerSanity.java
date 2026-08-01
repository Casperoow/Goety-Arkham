package com.casper.goetyarkham.sanity;

public interface IPlayerSanity {
    int getCurrentSanity();

    int getPermanentMaxLoss();

    boolean isCollapseActive();

    int getCollapseTickCounter();
}
