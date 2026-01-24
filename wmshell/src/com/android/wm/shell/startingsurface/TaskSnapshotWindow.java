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

package com.android.wm.shell.startingsurface;

import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.graphics.Color.WHITE;
import static android.os.Trace.TRACE_TAG_WINDOW_MANAGER;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_STARTING;

import android.annotation.BinderThread;
import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.ActivityManager.TaskDescription;
import android.graphics.Paint;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.Trace;
import android.util.MergedConfiguration;
import android.util.Slog;
import android.view.IWindowSession;
import android.view.InputChannel;
import android.view.InsetsSourceControl;
import android.view.InsetsState;
import android.view.SurfaceControl;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.view.WindowRelayoutResult;
import android.window.ClientWindowFrames;
import android.window.SnapshotDrawerUtils;
import android.window.StartingWindowInfo;
import android.window.TaskSnapshot;

import com.android.internal.protolog.ProtoLog;
import com.android.internal.view.BaseIWindow;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.protolog.ShellProtoLogGroup;

import java.lang.ref.WeakReference;

/**
 * This class represents a starting window that shows a snapshot.
 *
 * @hide
 */
public class TaskSnapshotWindow {
    private static final String TAG = StartingWindowController.TAG;
    private static final String TITLE_FORMAT = "SnapshotStartingWindow for taskId=";

    private final Window mWindow;
    private final Runnable mClearWindowHandler;
    private final ShellExecutor mSplashScreenExecutor;
    private final IWindowSession mSession;
    private boolean mHasDrawn;
    private final Paint mBackgroundPaint = new Paint();
    private final int mOrientationOnCreation;

    private final boolean mHasImeSurface;

    static TaskSnapshotWindow create(StartingWindowInfo info, IBinder appToken,
            TaskSnapshot snapshot, ShellExecutor splashScreenExecutor,
            @NonNull Runnable clearWindowHandler) {
        final ActivityManager.RunningTaskInfo runningTaskInfo = info.taskInfo;
        final int taskId = runningTaskInfo.taskId;

        // if we're in PIP we don't want to create the snapshot
        if (runningTaskInfo.getWindowingMode() == WINDOWING_MODE_PINNED) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_STARTING_WINDOW,
                    "did not create taskSnapshot due to being in PIP");
            return null;
        }
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_STARTING_WINDOW,
                "create taskSnapshot surface for task: %d", taskId);

        final WindowManager.LayoutParams layoutParams = SnapshotDrawerUtils.createLayoutParameters(
                info, TITLE_FORMAT + taskId, TYPE_APPLICATION_STARTING,
                snapshot.getHardwareBuffer().getFormat(), appToken);
        if (layoutParams == null) {
            Slog.e(TAG, "TaskSnapshotWindow no layoutParams");
            return null;
        }

        final int orientation = snapshot.getOrientation();
        final int displayId = runningTaskInfo.displayId;

        final IWindowSession session = WindowManagerGlobal.getWindowSession();
        final SurfaceControl surfaceControl = new SurfaceControl();
        final ClientWindowFrames tmpFrames = new ClientWindowFrames();

        final InsetsSourceControl.Array tmpControls = new InsetsSourceControl.Array();
        final MergedConfiguration tmpMergedConfiguration = new MergedConfiguration();

        final TaskDescription taskDescription =
                SnapshotDrawerUtils.getOrCreateTaskDescription(runningTaskInfo);

        final TaskSnapshotWindow snapshotSurface = new TaskSnapshotWindow(
                snapshot, taskDescription, orientation,
                clearWindowHandler, splashScreenExecutor);
        final Window window = snapshotSurface.mWindow;

        final InsetsState tmpInsetsState = new InsetsState();
        final InputChannel tmpInputChannel = new InputChannel();

        try {
            Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "TaskSnapshot#addToDisplay");
            final WindowRelayoutResult addRes = new WindowRelayoutResult(new ClientWindowFrames(),
                    new MergedConfiguration(), tmpInsetsState, tmpControls);
            final int res = session.addToDisplay(window, layoutParams, View.GONE, displayId,
                    info.requestedVisibleTypes, tmpInputChannel, addRes);
            Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
            if (res < 0) {
                Slog.w(TAG, "Failed to add snapshot starting window res=" + res);
                return null;
            }
        } catch (RemoteException e) {
            snapshotSurface.clearWindowSynced();
        }
        try {
            Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "TaskSnapshot#relayout");
            final WindowRelayoutResult outRelayoutResult = new WindowRelayoutResult(tmpFrames,
                    tmpMergedConfiguration, tmpInsetsState, tmpControls);
            session.relayout(window, layoutParams, -1, -1, View.VISIBLE, 0, 0, 0,
                    outRelayoutResult, surfaceControl);
            Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
        } catch (RemoteException e) {
            snapshotSurface.clearWindowSynced();
            Slog.w(TAG, "Failed to relayout snapshot starting window");
            return null;
        }
        if (!surfaceControl.isValid()) {
            snapshotSurface.clearWindowSynced();
            Slog.w(TAG, "Unable to draw snapshot, no valid surface");
            return null;
        }

        SnapshotDrawerUtils.drawSnapshotOnSurface(layoutParams, surfaceControl, snapshot,
                info.taskBounds, true /* releaseAfterDraw */);
        snapshotSurface.mHasDrawn = true;
        snapshotSurface.reportDrawn();

        return snapshotSurface;
    }

    public TaskSnapshotWindow(TaskSnapshot snapshot, TaskDescription taskDescription,
            int currentOrientation, Runnable clearWindowHandler,
            ShellExecutor splashScreenExecutor) {
        mSplashScreenExecutor = splashScreenExecutor;
        mSession = WindowManagerGlobal.getWindowSession();
        mWindow = new Window(this);
        mWindow.setSession(mSession);
        int backgroundColor = taskDescription.getBackgroundColor();
        mBackgroundPaint.setColor(backgroundColor != 0 ? backgroundColor : WHITE);
        mOrientationOnCreation = currentOrientation;
        mClearWindowHandler = clearWindowHandler;
        mHasImeSurface = snapshot.hasImeSurface();
    }

    int getBackgroundColor() {
        return mBackgroundPaint.getColor();
    }

    boolean hasImeSurface() {
	return mHasImeSurface;
    }

    void removeImmediately() {
        try {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_STARTING_WINDOW,
                    "Removing taskSnapshot surface, mHasDrawn=%b", mHasDrawn);
            mSession.remove(mWindow.asBinder());
        } catch (RemoteException e) {
            // nothing
        }
    }

    /**
     * Clear window from drawer, must be post on main executor.
     */
    private void clearWindowSynced() {
        mSplashScreenExecutor.executeDelayed(mClearWindowHandler, 0);
    }

    private void reportDrawn() {
        try {
            mSession.finishDrawing(mWindow, null /* postDrawTransaction */, Integer.MAX_VALUE);
        } catch (RemoteException e) {
            clearWindowSynced();
        }
    }

    static class Window extends BaseIWindow {
        private final WeakReference<TaskSnapshotWindow> mOuter;

        Window(TaskSnapshotWindow outer) {
            mOuter = new WeakReference<>(outer);
        }

        @BinderThread
        @Override
        public void resized(WindowRelayoutResult layout, boolean reportDraw, boolean forceLayout,
                int displayId, boolean syncWithBuffers, boolean dragResizing) {
            final TaskSnapshotWindow snapshot = mOuter.get();
            if (snapshot == null) {
                return;
            }
            snapshot.mSplashScreenExecutor.execute(() -> {
                final boolean clearSnapshot = layout.mergedConfiguration != null
                        && (snapshot.mOrientationOnCreation != layout.mergedConfiguration
                                .getMergedConfiguration().orientation);
                if (clearSnapshot) {
                    // The orientation of the screen is changing. We better remove the snapshot
                    // ASAP as we are going to wait on the new window in any case to unfreeze
                    // the screen, and the starting window is not needed anymore.
                    snapshot.clearWindowSynced();
                } else if (reportDraw) {
                    if (snapshot.mHasDrawn) {
                        snapshot.reportDrawn();
                    }
                }
            });
        }
    }
}
