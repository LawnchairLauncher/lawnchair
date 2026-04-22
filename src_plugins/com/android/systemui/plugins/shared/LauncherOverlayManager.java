/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.systemui.plugins.shared;

import android.view.MotionEvent;

import java.io.PrintWriter;

/**
 * Interface to control the overlay on Launcher
 */
public interface LauncherOverlayManager {

    default void onDeviceProvideChanged() { }

    default void onAttachedToWindow() { }
    default void onDetachedFromWindow() { }

    default void dump(String prefix, PrintWriter w) { }

    default void openOverlay() { }

    default void hideOverlay(boolean animate) {
        hideOverlay(animate ? 200 : 0);
    }

    default void hideOverlay(int duration) { }

    default void onActivityStarted() { }

    default void onActivityResumed() { }

    default void onActivityPaused() { }

    default void onActivityStopped() { }

    default void onActivityDestroyed() { }

    default void onDisallowSwipeToMinusOnePage() {}

    /**
     * @deprecated use LauncherOverlayTouchProxy directly
     */
    @Deprecated
    interface LauncherOverlay extends LauncherOverlayTouchProxy {

        /**
         * Touch interaction leading to overscroll has begun
         */
        void onScrollInteractionBegin();

        /**
         * Touch interaction related to overscroll has ended
         */
        void onScrollInteractionEnd();

        /**
         * Scroll progress, between 0 and 100, when the user scrolls beyond the leftmost
         * screen (or in the case of RTL, the rightmost screen).
         */
        void onScrollChange(float progress, boolean rtl);

        /**
         * Called when the launcher is ready to use the overlay
         * @param callbacks A set of callbacks provided by Launcher in relation to the overlay
         */
        void setOverlayCallbacks(LauncherOverlayCallbacks callbacks);

        @Override
        default void onFlingVelocity(float velocity) { }

        @Override
        default void onOverlayMotionEvent(MotionEvent ev, float scrollProgress) {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN ->  onScrollInteractionBegin();
                case MotionEvent.ACTION_MOVE -> onScrollChange(scrollProgress, false);
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onScrollInteractionEnd();
            }

        }
    }

    interface LauncherOverlayTouchProxy {

        /**
         * Called just before finishing scroll interaction to indicate the fling velocity
         */
        void onFlingVelocity(float velocity);

        /**
         * Called to dispatch various motion events to the overlay
         */
        void onOverlayMotionEvent(MotionEvent ev, float scrollProgress);

        /**
         * Called when the launcher is ready to use the overlay
         * @param callbacks A set of callbacks provided by Launcher in relation to the overlay
         */
        default void setOverlayCallbacks(LauncherOverlayCallbacks callbacks) { }
    }

    interface LauncherOverlayCallbacks {

        void onOverlayScrollChanged(float progress);
    }
}
