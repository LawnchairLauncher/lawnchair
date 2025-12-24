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

package com.android.wm.shell.common;

import static android.app.WindowConfiguration.ROTATION_UNDEFINED;
import static android.view.Display.INVALID_DISPLAY;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayTopology;
import android.os.RemoteException;
import android.util.ArraySet;
import android.util.Size;
import android.util.Slog;
import android.util.SparseArray;
import android.view.Display;
import android.view.IDisplayWindowListener;
import android.view.IWindowManager;
import android.view.InsetsState;
import android.window.DesktopExperienceFlags;
import android.window.WindowContainerTransaction;

import androidx.annotation.BinderThread;

import com.android.wm.shell.common.DisplayChangeController.OnDisplayChangingListener;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.shared.desktopmode.DesktopState;
import com.android.wm.shell.sysui.ShellInit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This module deals with display rotations coming from WM. When WM starts a rotation: after it has
 * frozen the screen, it will call into this class. This will then call all registered local
 * controllers and give them a chance to queue up task changes to be applied synchronously with that
 * rotation.
 */
public class DisplayController {
    private static final String TAG = "DisplayController";

    private final ShellExecutor mMainExecutor;
    private final Context mContext;
    private final IWindowManager mWmService;
    private final DisplayManager mDisplayManager;
    private final DisplayChangeController mChangeController;
    private final IDisplayWindowListener mDisplayContainerListener;
    private final DesktopState mDesktopState;

    private final SparseArray<DisplayRecord> mDisplays = new SparseArray<>();

    private final ArrayList<OnDisplaysChangedListener> mDisplayChangedListeners = new ArrayList<>();

    private DisplayTopology mDisplayTopology;

    public DisplayController(Context context, IWindowManager wmService, ShellInit shellInit,
            ShellExecutor mainExecutor, DisplayManager displayManager,
            DesktopState desktopState) {
        mMainExecutor = mainExecutor;
        mContext = context;
        mWmService = wmService;
        mDisplayManager = displayManager;
        mDesktopState = desktopState;
        mChangeController = new DisplayChangeController(this, wmService, shellInit,
                mainExecutor);
        mDisplayContainerListener = new DisplayWindowListenerImpl();
        // Note, add this after DisplaceChangeController is constructed to ensure that is
        // initialized first
        shellInit.addInitCallback(this::onInit, this);
    }

    /**
     * Initializes the window listener and the topology listener.
     */
    public void onInit() {
        try {
            int[] displayIds = mWmService.registerDisplayWindowListener(mDisplayContainerListener);
            for (int i = 0; i < displayIds.length; i++) {
                onDisplayAdded(displayIds[i]);
            }

            if (DesktopExperienceFlags.ENABLE_CONNECTED_DISPLAYS_WINDOW_DRAG.isTrue()
                    && mDesktopState.canEnterDesktopMode()) {
                mDisplayManager.registerTopologyListener(mMainExecutor,
                        this::onDisplayTopologyChanged);
                onDisplayTopologyChanged(mDisplayManager.getDisplayTopology());
            }
        } catch (RemoteException e) {
            throw new RuntimeException("Unable to register display controller");
        }
    }

    /**
     * Gets a display by id from DisplayManager.
     */
    public Display getDisplay(int displayId) {
        return mDisplayManager.getDisplay(displayId);
    }

    /**
     * Gets the uniqueId associated with the provided displayId, if it is associated with one.
     */
    @Nullable
    public String getDisplayUniqueId(int displayId) {
        final DisplayRecord r = mDisplays.get(displayId);
        return r != null ? r.mUniqueId : null;
    }

    /**
     * Gets the displayId associated with the provided uniqueId, if it is associated with one.
     * Because this calls an IPC, we should only use this in time sensitive cases where we suspect
     * DisplayManager has more up to date information than mDisplays (i.e., during reboot). For
     * other cases, use getAllDisplaysByUniqueId below.
     */
    public int getDisplayIdByUniqueIdBlocking(String uniqueId) {
        for (Display display : mDisplayManager.getDisplays()) {
            if (uniqueId.equals(display.getUniqueId())) return display.getDisplayId();
        }
        return INVALID_DISPLAY;
    }

    /**
     * Gets a map of all displays by uniqueId from DisplayManager.
     */
    @Nullable
    public Map<String, Integer> getAllDisplaysByUniqueId() {
        HashMap<String, Integer> map = new HashMap<>();
        for (int i = 0; i < mDisplays.size(); i++) {
            final String uniqueId = mDisplays.valueAt(i).mUniqueId;
            if (uniqueId != null) {
                map.put(uniqueId, mDisplays.keyAt(i));
            }
        }
        return map;
    }

    /**
     * Returns true if the display with the given displayId is part of the topology.
     */
    public boolean isDisplayInTopology(int displayId) {
        return mDisplayTopology != null
                && mDisplayTopology.findDisplay(displayId, mDisplayTopology.getRoot()) != null;
    }

    /**
     * Gets the DisplayLayout associated with a display.
     */
    public @Nullable DisplayLayout getDisplayLayout(int displayId) {
        final DisplayRecord r = mDisplays.get(displayId);
        return r != null ? r.mDisplayLayout : null;
    }

    /**
     * Gets a display-specific context for a display.
     */
    public @Nullable Context getDisplayContext(int displayId) {
        final DisplayRecord r = mDisplays.get(displayId);
        return r != null ? r.mContext : null;
    }

    /**
     *  Get the InsetsState of a display.
     */
    public InsetsState getInsetsState(int displayId) {
        final DisplayRecord r = mDisplays.get(displayId);
        return r != null ? r.mInsetsState : null;
    }

    /**
     * Returns whether animations are disabled for the given displayId.
     */
    public boolean isAnimationsDisabled(int displayId) {
        final DisplayRecord r = mDisplays.get(displayId);
        return r == null || r.mAnimationsDisabled;
    }

    /**
     * Updates the insets for a given display.
     */
    public void updateDisplayInsets(int displayId, InsetsState state) {
        final Rect oldStableBounds = new Rect();
        final Rect newStableBounds = new Rect();
        final DisplayLayout oldDisplayLayout = getDisplayLayout(displayId);
        if (oldDisplayLayout != null) {
            oldDisplayLayout.getStableBounds(oldStableBounds);
        }
        final DisplayRecord r = mDisplays.get(displayId);
        if (r != null) {
            r.setInsets(state);
        }
        final DisplayLayout newDisplayLayout = getDisplayLayout(displayId);
        if (newDisplayLayout != null) {
            newDisplayLayout.getStableBounds(newStableBounds);
        }

        if (!oldStableBounds.equals(newStableBounds)) {
            for (int i = 0; i < mDisplayChangedListeners.size(); ++i) {
                mDisplayChangedListeners.get(i).onStableInsetsChanging(
                        displayId, oldDisplayLayout);
            }
        }
    }

    /**
     * Add a display window-container listener. It will get notified whenever a display's
     * configuration changes or when displays are added/removed from the WM hierarchy.
     */
    public void addDisplayWindowListener(OnDisplaysChangedListener listener) {
        synchronized (mDisplays) {
            if (mDisplayChangedListeners.contains(listener)) {
                return;
            }
            mDisplayChangedListeners.add(listener);
            for (int i = 0; i < mDisplays.size(); ++i) {
                listener.onDisplayAdded(mDisplays.keyAt(i));
            }
            listener.onTopologyChanged(mDisplayTopology);
        }
    }

    /**
     * Remove a display window-container listener.
     */
    public void removeDisplayWindowListener(OnDisplaysChangedListener listener) {
        synchronized (mDisplays) {
            mDisplayChangedListeners.remove(listener);
        }
    }

    /**
     * Adds a display rotation controller.
     */
    public void addDisplayChangingController(OnDisplayChangingListener controller) {
        mChangeController.addDisplayChangeListener(controller);
    }

    /**
     * Removes a display rotation controller.
     */
    public void removeDisplayChangingController(OnDisplayChangingListener controller) {
        mChangeController.removeDisplayChangeListener(controller);
    }

    private void onDisplayAdded(int displayId) {
        synchronized (mDisplays) {
            if (mDisplays.get(displayId) != null) {
                return;
            }
            final Display display = getDisplay(displayId);
            if (display == null) {
                // It's likely that the display is private to some app and thus not
                // accessible by system-ui.
                return;
            }

            final Context context = (displayId == Display.DEFAULT_DISPLAY)
                    ? mContext
                    : mContext.createDisplayContext(display);
            boolean hasStatusAndNavBars = false;
            if (DesktopExperienceFlags.ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT.isTrue()) {
                hasStatusAndNavBars = mDesktopState.isDesktopModeSupportedOnDisplay(displayId);
            }
            final DisplayRecord record = new DisplayRecord(displayId, hasStatusAndNavBars);
            DisplayLayout displayLayout = record.createLayout(context, display);
            record.setDisplayLayout(context, displayLayout);
            final String uniqueId = display.getUniqueId();
            if (uniqueId != null) {
                record.setUniqueId(uniqueId);
            }
            mDisplays.put(displayId, record);
            for (int i = 0; i < mDisplayChangedListeners.size(); ++i) {
                mDisplayChangedListeners.get(i).onDisplayAdded(displayId);
            }
        }
    }

    /** Called when a display rotate requested. */
    public void onDisplayChangeRequested(WindowContainerTransaction wct, int displayId,
            Rect startAbsBounds, Rect endAbsBounds, int fromRotation, int toRotation) {
        synchronized (mDisplays) {
            final DisplayLayout dl = getDisplayLayout(displayId);
            if (dl == null) {
                Slog.w(TAG, "Skipping Display rotate on non-added display.");
                return;
            }
            updateDisplayLayout(displayId, startAbsBounds, endAbsBounds, fromRotation, toRotation);

            mChangeController.dispatchOnDisplayChange(
                    wct, displayId, fromRotation, toRotation, null /* newDisplayAreaInfo */);
        }
    }

    void updateDisplayLayout(int displayId,
            @NonNull Rect startBounds, @Nullable Rect endBounds, int fromRotation, int toRotation) {
        final DisplayLayout dl = getDisplayLayout(displayId);
        final Context ctx = getDisplayContext(displayId);
        if (dl == null || ctx == null) return;

        boolean hasRotationChanged = fromRotation != toRotation && toRotation != ROTATION_UNDEFINED;
        final Size endSize = endBounds != null
                ? new Size(endBounds.width(), endBounds.height()) : null;

        if (hasRotationChanged && endSize != null) {
            // If rotation and display size are happening in sync, we have to follow a convention
            // that DisplayLayout implements.
            dl.rotateAndResizeTo(ctx.getResources(), toRotation, endSize);
        } else if (hasRotationChanged) {
            dl.rotateTo(ctx.getResources(), toRotation);
        } else if (endBounds != null) {
            dl.resizeTo(ctx.getResources(), endSize);
        }
    }

    private void onDisplayTopologyChanged(DisplayTopology topology) {
        if (topology == null) {
            return;
        }
        mDisplayTopology = topology;
        SparseArray<RectF> absoluteBounds = topology.getAbsoluteBounds();
        for (int i = 0; i < absoluteBounds.size(); ++i) {
            int displayId = absoluteBounds.keyAt(i);
            DisplayLayout displayLayout = getDisplayLayout(displayId);
            if (displayLayout != null) {
                displayLayout.setGlobalBoundsDp(absoluteBounds.valueAt(i));
            }
        }

        for (int i = 0; i < mDisplayChangedListeners.size(); ++i) {
            mDisplayChangedListeners.get(i).onTopologyChanged(topology);
        }
    }

    private void onDisplayConfigurationChanged(int displayId, Configuration newConfig) {
        synchronized (mDisplays) {
            final DisplayRecord dr = mDisplays.get(displayId);
            if (dr == null) {
                Slog.w(TAG, "Skipping Display Configuration change on non-added"
                        + " display.");
                return;
            }
            final Display display = getDisplay(displayId);
            if (display == null) {
                Slog.w(TAG, "Skipping Display Configuration change on invalid"
                        + " display. It may have been removed.");
                return;
            }
            final Context perDisplayContext = (displayId == Display.DEFAULT_DISPLAY)
                    ? mContext
                    : mContext.createDisplayContext(display);
            DisplayLayout oldLayout = dr.mDisplayLayout;
            final Context context = perDisplayContext.createConfigurationContext(newConfig);
            final DisplayLayout displayLayout = dr.createLayout(context, display);
            dr.setDisplayLayout(context, displayLayout);
            for (int i = 0; i < mDisplayChangedListeners.size(); ++i) {
                mDisplayChangedListeners.get(i).onDisplayConfigurationChanged(
                        displayId, newConfig, oldLayout);
            }
        }
    }

    private void onDisplayRemoved(int displayId) {
        synchronized (mDisplays) {
            if (mDisplays.get(displayId) == null) {
                return;
            }
            for (int i = mDisplayChangedListeners.size() - 1; i >= 0; --i) {
                mDisplayChangedListeners.get(i).onDisplayRemoved(displayId);
            }
            mDisplays.remove(displayId);
        }
    }

    private void onFixedRotationStarted(int displayId, int newRotation) {
        synchronized (mDisplays) {
            if (mDisplays.get(displayId) == null || getDisplay(displayId) == null) {
                Slog.w(TAG, "Skipping onFixedRotationStarted on unknown"
                        + " display, displayId=" + displayId);
                return;
            }
            for (int i = mDisplayChangedListeners.size() - 1; i >= 0; --i) {
                mDisplayChangedListeners.get(i).onFixedRotationStarted(
                        displayId, newRotation);
            }
        }
    }

    private void onFixedRotationFinished(int displayId) {
        synchronized (mDisplays) {
            if (mDisplays.get(displayId) == null || getDisplay(displayId) == null) {
                Slog.w(TAG, "Skipping onFixedRotationFinished on unknown"
                        + " display, displayId=" + displayId);
                return;
            }
            for (int i = mDisplayChangedListeners.size() - 1; i >= 0; --i) {
                mDisplayChangedListeners.get(i).onFixedRotationFinished(displayId);
            }
        }
    }

    private void onKeepClearAreasChanged(int displayId, Set<Rect> restricted,
            Set<Rect> unrestricted) {
        synchronized (mDisplays) {
            if (mDisplays.get(displayId) == null || getDisplay(displayId) == null) {
                Slog.w(TAG, "Skipping onKeepClearAreasChanged on unknown"
                        + " display, displayId=" + displayId);
                return;
            }
            for (int i = mDisplayChangedListeners.size() - 1; i >= 0; --i) {
                mDisplayChangedListeners.get(i)
                    .onKeepClearAreasChanged(displayId, restricted, unrestricted);
            }
        }
    }

    private void onDesktopModeEligibleChanged(int displayId) {
        synchronized (mDisplays) {
            DisplayRecord r = mDisplays.get(displayId);
            Display display = getDisplay(displayId);
            if (r == null ||  display == null) {
                Slog.w(TAG, "Skipping onDesktopModeEligibleChanged on unknown"
                        + " display, displayId=" + displayId);
                return;
            }
            if (DesktopExperienceFlags.ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT.isTrue()) {
                r.updateHasStatusAndNavBars(display,
                        mDesktopState.isDesktopModeSupportedOnDisplay(display));
            }
            for (int i = mDisplayChangedListeners.size() - 1; i >= 0; --i) {
                mDisplayChangedListeners.get(i).onDesktopModeEligibleChanged(displayId);
            }
        }
    }

    private void onAnimationsDisabled(int displayId, boolean disabled) {
        synchronized (mDisplays) {
            DisplayRecord r = mDisplays.get(displayId);
            if (r != null) {
                r.mAnimationsDisabled = disabled;
            }
        }
    }

    private class DisplayRecord {
        private final int mDisplayId;
        private String mUniqueId;
        private Context mContext;
        private DisplayLayout mDisplayLayout;
        private InsetsState mInsetsState = new InsetsState();
        private boolean mHasStatusAndNavBars;
        private boolean mAnimationsDisabled;

        private DisplayRecord(int displayId, boolean hasStatusAndNavBars) {
            mDisplayId = displayId;
            mHasStatusAndNavBars = hasStatusAndNavBars;
            mAnimationsDisabled = false;
        }

        private DisplayLayout createLayout(Context context, Display display) {
            final boolean shouldInitWithSystemDecorations =
                    mDisplayId != Display.DEFAULT_DISPLAY && mHasStatusAndNavBars;
            final DisplayLayout layout = shouldInitWithSystemDecorations
                    ? new DisplayLayout(
                            context, display, true /* hasNavigationBar */, true /* hasTaskBar */)
                    : new DisplayLayout(context, display);
            if (DesktopExperienceFlags.ENABLE_CONNECTED_DISPLAYS_WINDOW_DRAG.isTrue()
                    && mDisplayTopology != null) {
                final RectF globalBounds = mDisplayTopology.getAbsoluteBounds().get(mDisplayId);
                if (globalBounds != null) {
                    layout.setGlobalBoundsDp(globalBounds);
                }
            }
            return layout;
        }


        private void updateHasStatusAndNavBars(Display display, boolean hasStatusAndNavBars) {
            if (mHasStatusAndNavBars == hasStatusAndNavBars) {
                return;
            }
            mHasStatusAndNavBars = hasStatusAndNavBars;
            // Don't change how DEFAULT_DISPLAY is handled: the default heuristic is correct.
            if (mDisplayId != Display.DEFAULT_DISPLAY) {
                setDisplayLayout(mContext, createLayout(mContext, display));
            }
        }

        private void setDisplayLayout(Context context, DisplayLayout displayLayout) {
            mContext = context;
            mDisplayLayout = displayLayout;
            mDisplayLayout.setInsets(mContext.getResources(), mInsetsState);
        }

        private void setUniqueId(String uniqueId) {
            mUniqueId = uniqueId;
        }

        private void setInsets(InsetsState state) {
            mInsetsState = state;
            mDisplayLayout.setInsets(mContext.getResources(), state);
        }
    }

    @BinderThread
    private class DisplayWindowListenerImpl extends IDisplayWindowListener.Stub {
        @Override
        public void onDisplayAdded(int displayId) {
            mMainExecutor.execute(() -> {
                DisplayController.this.onDisplayAdded(displayId);
            });
        }

        @Override
        public void onDisplayConfigurationChanged(int displayId, Configuration newConfig) {
            mMainExecutor.execute(() -> {
                DisplayController.this.onDisplayConfigurationChanged(displayId, newConfig);
            });
        }

        @Override
        public void onDisplayRemoved(int displayId) {
            mMainExecutor.execute(() -> {
                DisplayController.this.onDisplayRemoved(displayId);
            });
        }

        @Override
        public void onFixedRotationStarted(int displayId, int newRotation) {
            mMainExecutor.execute(() -> {
                DisplayController.this.onFixedRotationStarted(displayId, newRotation);
            });
        }

        @Override
        public void onFixedRotationFinished(int displayId) {
            mMainExecutor.execute(() -> {
                DisplayController.this.onFixedRotationFinished(displayId);
            });
        }

        @Override
        public void onKeepClearAreasChanged(int displayId, List<Rect> restricted,
                List<Rect> unrestricted) {
            mMainExecutor.execute(() -> {
                DisplayController.this.onKeepClearAreasChanged(displayId,
                        new ArraySet<>(restricted), new ArraySet<>(unrestricted));
            });
        }

        @Override
        public void onDesktopModeEligibleChanged(int displayId) {
            mMainExecutor.execute(() -> {
                DisplayController.this.onDesktopModeEligibleChanged(displayId);
            });
        }

        @Override
        public void onDisplayAddSystemDecorations(int displayId) { }

        @Override
        public void onDisplayRemoveSystemDecorations(int displayId) { }

        @Override
        public void onDisplayAnimationsDisabledChanged(int displayId, boolean disabled) {
            mMainExecutor.execute(
                    () -> DisplayController.this.onAnimationsDisabled(displayId, disabled));
        }
    }

    /**
     * Gets notified when a display is added/removed to the WM hierarchy and when a display's
     * window-configuration changes.
     *
     * @see IDisplayWindowListener
     */
    @ShellMainThread
    public interface OnDisplaysChangedListener {
        /**
         * Called when a display has been added to the WM hierarchy.
         */
        default void onDisplayAdded(int displayId) {}

        /**
         * Called when a display's window-container configuration changes.
         */
        default void onDisplayConfigurationChanged(int displayId, Configuration newConfig) {}

        /**
         * Called when a display's window-container configuration changes, includes old layout.
         */
        default void onDisplayConfigurationChanged(int displayId, Configuration newConfig,
                DisplayLayout oldLayout) {
            this.onDisplayConfigurationChanged(displayId, newConfig);
        }

        /**
         * Notifies listeners of a stable insets change.
         * This is usually called after a configuration change when the system components update
         * their bounds.
         * @param displayId display who's layout is changing.
         * @param oldLayout the layout of this display before the change is applied.
         */
        default void onStableInsetsChanging(int displayId, DisplayLayout oldLayout) {}
        /**
         * Called when a display is removed.
         */
        default void onDisplayRemoved(int displayId) {}

        /**
         * Called when fixed rotation on a display is started.
         */
        default void onFixedRotationStarted(int displayId, int newRotation) {}

        /**
         * Called when fixed rotation on a display is finished.
         */
        default void onFixedRotationFinished(int displayId) {}

        /**
         * Called when keep-clear areas on a display have changed.
         */
        default void onKeepClearAreasChanged(int displayId, Set<Rect> restricted,
                Set<Rect> unrestricted) {}

        /**
         * Called when the display topology has changed.
         */
        default void onTopologyChanged(DisplayTopology topology) {}

        /**
         * Called when the eligibility of the desktop mode for a display have changed.
         */
        default void onDesktopModeEligibleChanged(int displayId) {}
    }
}
