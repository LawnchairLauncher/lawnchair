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

import android.content.pm.ParceledListSlice;
import android.graphics.Rect;
import android.os.RemoteException;
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
    private List<IPinnedStackListener> mListeners = new ArrayList<>();

    /** Adds a listener to receive updates from the WindowManagerService. */
    public void addListener(IPinnedStackListener listener) {
        mListeners.add(listener);
    }

    /** Removes a listener so it will no longer receive updates from the WindowManagerService. */
    public void removeListener(IPinnedStackListener listener) {
        mListeners.remove(listener);
    }

    @Override
    public void onListenerRegistered(IPinnedStackController controller) throws RemoteException {
        for (IPinnedStackListener listener : mListeners) {
            listener.onListenerRegistered(controller);
        }
    }

    @Override
    public void onMovementBoundsChanged(Rect insetBounds, Rect normalBounds, Rect animatingBounds,
            boolean fromImeAdjustment, boolean fromShelfAdjustment, int displayRotation)
            throws RemoteException {
        for (IPinnedStackListener listener : mListeners) {
            listener.onMovementBoundsChanged(
                    insetBounds, normalBounds, animatingBounds,
                    fromImeAdjustment, fromShelfAdjustment, displayRotation);
        }
    }

    @Override
    public void onImeVisibilityChanged(boolean imeVisible, int imeHeight) throws RemoteException {
        for (IPinnedStackListener listener : mListeners) {
            listener.onImeVisibilityChanged(imeVisible, imeHeight);
        }
    }

    @Override
    public void onShelfVisibilityChanged(boolean shelfVisible, int shelfHeight)
            throws RemoteException {
        for (IPinnedStackListener listener : mListeners) {
            listener.onShelfVisibilityChanged(shelfVisible, shelfHeight);
        }
    }

    @Override
    public void onMinimizedStateChanged(boolean isMinimized) throws RemoteException {
        for (IPinnedStackListener listener : mListeners) {
            listener.onMinimizedStateChanged(isMinimized);
        }
    }

    @Override
    public void onActionsChanged(ParceledListSlice actions) throws RemoteException {
        for (IPinnedStackListener listener : mListeners) {
            listener.onActionsChanged(actions);
        }
    }
}
