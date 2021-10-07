/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.shared.system;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.graphics.Rect;
import android.graphics.Region;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnComputeInternalInsetsListener;

import java.util.HashMap;

public class ViewTreeObserverWrapper {

    private static final HashMap<OnComputeInsetsListener, ViewTreeObserver>
            sListenerObserverMap = new HashMap<>();
    private static final HashMap<OnComputeInsetsListener, OnComputeInternalInsetsListener>
            sListenerInternalListenerMap = new HashMap<>();

    /**
     * Register a callback to be invoked when the invoked when it is time to compute the window's
     * insets.
     *
     * @param observer The observer to be added
     * @param listener The callback to add
     * @throws IllegalStateException If {@link ViewTreeObserver#isAlive()} returns false
     */
    public static void addOnComputeInsetsListener(
            @NonNull ViewTreeObserver observer, @NonNull OnComputeInsetsListener listener) {
        final OnComputeInternalInsetsListener internalListener = internalInOutInfo -> {
            final InsetsInfo inOutInfo = new InsetsInfo();
            inOutInfo.contentInsets.set(internalInOutInfo.contentInsets);
            inOutInfo.visibleInsets.set(internalInOutInfo.visibleInsets);
            inOutInfo.touchableRegion.set(internalInOutInfo.touchableRegion);
            listener.onComputeInsets(inOutInfo);
            internalInOutInfo.contentInsets.set(inOutInfo.contentInsets);
            internalInOutInfo.visibleInsets.set(inOutInfo.visibleInsets);
            internalInOutInfo.touchableRegion.set(inOutInfo.touchableRegion);
            internalInOutInfo.setTouchableInsets(inOutInfo.mTouchableInsets);
        };
        sListenerObserverMap.put(listener, observer);
        sListenerInternalListenerMap.put(listener, internalListener);
        observer.addOnComputeInternalInsetsListener(internalListener);
    }

    /**
     * Remove a previously installed insets computation callback.
     *
     * @param victim The callback to remove
     * @throws IllegalStateException If {@link ViewTreeObserver#isAlive()} returns false
     * @see #addOnComputeInsetsListener(ViewTreeObserver, OnComputeInsetsListener)
     */
    public static void removeOnComputeInsetsListener(@NonNull OnComputeInsetsListener victim) {
        final ViewTreeObserver observer = sListenerObserverMap.get(victim);
        final OnComputeInternalInsetsListener listener = sListenerInternalListenerMap.get(victim);
        if (observer != null && listener != null) {
            observer.removeOnComputeInternalInsetsListener(listener);
        }
        sListenerObserverMap.remove(victim);
        sListenerInternalListenerMap.remove(victim);
    }

    /**
     * Interface definition for a callback to be invoked when layout has
     * completed and the client can compute its interior insets.
     */
    public interface OnComputeInsetsListener {
        /**
         * Callback method to be invoked when layout has completed and the
         * client can compute its interior insets.
         *
         * @param inoutInfo Should be filled in by the implementation with
         * the information about the insets of the window.  This is called
         * with whatever values the previous OnComputeInsetsListener
         * returned, if there are multiple such listeners in the window.
         */
        void onComputeInsets(InsetsInfo inoutInfo);
    }

    /**
     * Parameters used with OnComputeInsetsListener.
     */
    public final static class InsetsInfo {

        /**
         * Offsets from the frame of the window at which the content of
         * windows behind it should be placed.
         */
        public final Rect contentInsets = new Rect();

        /**
         * Offsets from the frame of the window at which windows behind it
         * are visible.
         */
        public final Rect visibleInsets = new Rect();

        /**
         * Touchable region defined relative to the origin of the frame of the window.
         * Only used when {@link #setTouchableInsets(int)} is called with
         * the option {@link #TOUCHABLE_INSETS_REGION}.
         */
        public final Region touchableRegion = new Region();

        /**
         * Option for {@link #setTouchableInsets(int)}: the entire window frame
         * can be touched.
         */
        public static final int TOUCHABLE_INSETS_FRAME =
                ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_FRAME;

        /**
         * Option for {@link #setTouchableInsets(int)}: the area inside of
         * the content insets can be touched.
         */
        public static final int TOUCHABLE_INSETS_CONTENT =
                ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_CONTENT;

        /**
         * Option for {@link #setTouchableInsets(int)}: the area inside of
         * the visible insets can be touched.
         */
        public static final int TOUCHABLE_INSETS_VISIBLE =
                ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_VISIBLE;

        /**
         * Option for {@link #setTouchableInsets(int)}: the area inside of
         * the provided touchable region in {@link #touchableRegion} can be touched.
         */
        public static final int TOUCHABLE_INSETS_REGION =
                ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION;

        /**
         * Set which parts of the window can be touched: either
         * {@link #TOUCHABLE_INSETS_FRAME}, {@link #TOUCHABLE_INSETS_CONTENT},
         * {@link #TOUCHABLE_INSETS_VISIBLE}, or {@link #TOUCHABLE_INSETS_REGION}.
         */
        public void setTouchableInsets(int val) {
            mTouchableInsets = val;
        }

        int mTouchableInsets;

        @Override
        public int hashCode() {
            int result = contentInsets.hashCode();
            result = 31 * result + visibleInsets.hashCode();
            result = 31 * result + touchableRegion.hashCode();
            result = 31 * result + mTouchableInsets;
            return result;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final InsetsInfo other = (InsetsInfo) o;
            return mTouchableInsets == other.mTouchableInsets &&
                    contentInsets.equals(other.contentInsets) &&
                    visibleInsets.equals(other.visibleInsets) &&
                    touchableRegion.equals(other.touchableRegion);
        }
    }
}
