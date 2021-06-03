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

package com.android.systemui.shared.system;

import android.content.ComponentName;
import android.content.pm.ParceledListSlice;
import android.view.DisplayInfo;
import android.view.IPinnedStackController;
import android.view.IPinnedStackListener;

import java.util.ArrayList;
import java.util.List;

/**
 * PinnedStackListener that simply forwards all calls to each listener added via
 * {@link #addListener}. This is necessary since calling
 * {@link com.android.server.wm.WindowManagerService#registerPinnedStackListener} replaces any
 * previously set listener.
 */
public class PinnedStackListenerForwarder extends IPinnedStackListener.Stub {
    private List<PinnedStackListener> mListeners = new ArrayList<>();

    /** Adds a listener to receive updates from the WindowManagerService. */
    public void addListener(PinnedStackListener listener) {
        mListeners.add(listener);
    }

    /** Removes a listener so it will no longer receive updates from the WindowManagerService. */
    public void removeListener(PinnedStackListener listener) {
        mListeners.remove(listener);
    }

    @Override
    public void onListenerRegistered(IPinnedStackController controller) {
        for (PinnedStackListener listener : mListeners) {
            listener.onListenerRegistered(controller);
        }
    }

    @Override
    public void onMovementBoundsChanged(boolean fromImeAdjustment) {
        for (PinnedStackListener listener : mListeners) {
            listener.onMovementBoundsChanged(fromImeAdjustment);
        }
    }

    @Override
    public void onImeVisibilityChanged(boolean imeVisible, int imeHeight) {
        for (PinnedStackListener listener : mListeners) {
            listener.onImeVisibilityChanged(imeVisible, imeHeight);
        }
    }

    @Override
    public void onActionsChanged(ParceledListSlice actions) {
        for (PinnedStackListener listener : mListeners) {
            listener.onActionsChanged(actions);
        }
    }

    @Override
    public void onActivityHidden(ComponentName componentName) {
        for (PinnedStackListener listener : mListeners) {
            listener.onActivityHidden(componentName);
        }
    }

    @Override
    public void onDisplayInfoChanged(DisplayInfo displayInfo) {
        for (PinnedStackListener listener : mListeners) {
            listener.onDisplayInfoChanged(displayInfo);
        }
    }

    @Override
    public void onConfigurationChanged() {
        for (PinnedStackListener listener : mListeners) {
            listener.onConfigurationChanged();
        }
    }

    @Override
    public void onAspectRatioChanged(float aspectRatio) {
        for (PinnedStackListener listener : mListeners) {
            listener.onAspectRatioChanged(aspectRatio);
        }
    }

    /**
     * A counterpart of {@link IPinnedStackListener} with empty implementations.
     * Subclasses can ignore those methods they do not intend to take action upon.
     */
    public static class PinnedStackListener {
        public void onListenerRegistered(IPinnedStackController controller) {}

        public void onMovementBoundsChanged(boolean fromImeAdjustment) {}

        public void onImeVisibilityChanged(boolean imeVisible, int imeHeight) {}

        public void onActionsChanged(ParceledListSlice actions) {}

        public void onActivityHidden(ComponentName componentName) {}

        public void onDisplayInfoChanged(DisplayInfo displayInfo) {}

        public void onConfigurationChanged() {}

        public void onAspectRatioChanged(float aspectRatio) {}
    }
}
