package com.tiny.arcade;

public interface ArcadeGame {
    void persistSessionNow();

    void onHide();

    default void onShow() {
    }

    default void onAppPause() {
    }
}
