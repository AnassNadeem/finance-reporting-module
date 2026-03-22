package com.raez.finance.util;

/**
 * Views that should reload server-backed data on a timer (see MainLayoutController).
 */
@FunctionalInterface
public interface UiAutoRefreshable {

    void refreshVisibleData();
}
