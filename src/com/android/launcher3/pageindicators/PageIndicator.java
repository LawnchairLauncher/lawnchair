package com.android.launcher3.pageindicators;

import android.view.View;

import java.util.ArrayList;

public interface PageIndicator {
    View getView();
    void setScroll(int currentScroll, int totalScroll);

    void setActiveMarker(int activePage);

    void addMarker();
    void removeMarker();
    void setMarkersCount(int numMarkers);
}
