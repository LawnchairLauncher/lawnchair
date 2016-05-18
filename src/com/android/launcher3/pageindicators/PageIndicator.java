package com.android.launcher3.pageindicators;

import android.view.View;

import java.util.ArrayList;

public interface PageIndicator {
    View getView();
    void setProgress(float progress);

    void removeAllMarkers(boolean allowAnimations);
    void addMarkers(ArrayList<PageMarkerResources> markers, boolean allowAnimations);
    void setActiveMarker(int activePage);
    void addMarker(int pageIndex, PageMarkerResources pageIndicatorMarker, boolean allowAnimations);
    void removeMarker(int pageIndex, boolean allowAnimations);
    void updateMarker(int pageIndex, PageMarkerResources pageIndicatorMarker);

    /**
     * Contains two resource ids for each page indicator marker (e.g. dots):
     * one for when the page is active and one for when the page is inactive.
     */
    class PageMarkerResources {
        int activeId;
        int inactiveId;

        public PageMarkerResources(int aId, int iaId) {
            activeId = aId;
            inactiveId = iaId;
        }
    }
}
