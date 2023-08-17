/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.pageindicators;

/**
 * Base class for a page indicator.
 */
public interface PageIndicator {

    void setScroll(int currentScroll, int totalScroll);

    void setActiveMarker(int activePage);

    void setMarkersCount(int numMarkers);

    /**
     * Sets flag to indicate when the screens are in the process of binding so that we don't animate
     * during that period.
     */
    default void setAreScreensBinding(boolean areScreensBinding) {
        // No-op by default
    }

    /**
     * Sets the flag if the Page Indicator should autohide.
     */
    default void setShouldAutoHide(boolean shouldAutoHide) {
        // No-op by default
    }

    /**
     * Pauses all currently running animations.
     */
    default void pauseAnimations() {
        // No-op by default
    }

    /**
     * Force-ends all currently running or paused animations.
     */
    default void skipAnimationsToEnd() {
        // No-op by default
    }

    /**
     * Sets the paint color.
     */
    default void setPaintColor(int color) {
        // No-op by default
    }
}
