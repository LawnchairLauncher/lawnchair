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
     * Sets a flag indicating whether to pause scroll.
     * <p>Should be set to {@code true} while the screen is binding or new data is being applied,
     * and to {@code false} once done. This prevents animation conflicts due to scrolling during
     * those periods.</p>
     */
    default void setPauseScroll(boolean pause, boolean isTwoPanels) {
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
