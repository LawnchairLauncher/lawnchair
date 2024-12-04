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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.window.StartingWindowRemovalInfo.DEFER_MODE_NONE;
import static android.window.StartingWindowRemovalInfo.DEFER_MODE_NORMAL;
import static android.window.StartingWindowRemovalInfo.DEFER_MODE_ROTATION;

import android.annotation.CallSuper;
import android.annotation.NonNull;
import android.app.TaskInfo;
import android.app.WindowConfiguration;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.util.SparseArray;
import android.view.IWindow;
import android.view.SurfaceControl;
import android.view.SurfaceSession;
import android.view.WindowManager;
import android.view.WindowlessWindowManager;
import android.window.SplashScreenView;
import android.window.StartingWindowInfo;
import android.window.StartingWindowInfo.StartingWindowType;
import android.window.StartingWindowRemovalInfo;
import android.window.TaskSnapshot;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.common.ProtoLog;
import com.android.launcher3.icons.IconProvider;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.TransactionPool;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.annotations.ShellSplashscreenThread;

/**
 * A class which able to draw splash screen or snapshot as the starting window for a task.
 */
@ShellSplashscreenThread
public class StartingSurfaceDrawer {

    private final ShellExecutor mSplashScreenExecutor;
    @VisibleForTesting
    final SplashscreenContentDrawer mSplashscreenContentDrawer;
    @VisibleForTesting
    final SplashscreenWindowCreator mSplashscreenWindowCreator;
    private final SnapshotWindowCreator mSnapshotWindowCreator;
    private final WindowlessSplashWindowCreator mWindowlessSplashWindowCreator;
    private final WindowlessSnapshotWindowCreator mWindowlessSnapshotWindowCreator;

    @VisibleForTesting
    final StartingWindowRecordManager mWindowRecords = new StartingWindowRecordManager();
    // Windowless surface could co-exist with starting window in a task.
    @VisibleForTesting
    final StartingWindowRecordManager mWindowlessRecords = new StartingWindowRecordManager();
    /**
     * @param splashScreenExecutor The thread used to control add and remove starting window.
     */
    public StartingSurfaceDrawer(Context context, ShellExecutor splashScreenExecutor,
            IconProvider iconProvider, TransactionPool pool) {
        mSplashScreenExecutor = splashScreenExecutor;
        final DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        mSplashscreenContentDrawer = new SplashscreenContentDrawer(context, iconProvider, pool);
        displayManager.getDisplay(DEFAULT_DISPLAY);

        mSplashscreenWindowCreator = new SplashscreenWindowCreator(mSplashscreenContentDrawer,
                context, splashScreenExecutor, displayManager, mWindowRecords);
        mSnapshotWindowCreator = new SnapshotWindowCreator(splashScreenExecutor,
                mWindowRecords);
        mWindowlessSplashWindowCreator = new WindowlessSplashWindowCreator(
                mSplashscreenContentDrawer, context, splashScreenExecutor, displayManager,
                mWindowlessRecords, pool);
        mWindowlessSnapshotWindowCreator = new WindowlessSnapshotWindowCreator(
                mWindowlessRecords, context, displayManager, mSplashscreenContentDrawer, pool);
    }

    void setSysuiProxy(StartingSurface.SysuiProxy sysuiProxy) {
        mSplashscreenWindowCreator.setSysuiProxy(sysuiProxy);
        mWindowlessSplashWindowCreator.setSysuiProxy(sysuiProxy);
    }

    /**
     * Called when a task need a splash screen starting window.
     *
     * @param suggestType The suggestion type to draw the splash screen.
     */
    void addSplashScreenStartingWindow(StartingWindowInfo windowInfo,
            @StartingWindowType int suggestType) {
        mSplashscreenWindowCreator.addSplashScreenStartingWindow(windowInfo, suggestType);
    }

    int getStartingWindowBackgroundColorForTask(int taskId) {
        final StartingWindowRecord startingWindowRecord = mWindowRecords.getRecord(taskId);
        if (startingWindowRecord == null) {
            return Color.TRANSPARENT;
        }
        return startingWindowRecord.getBGColor();
    }

    int estimateTaskBackgroundColor(TaskInfo taskInfo) {
        return mSplashscreenWindowCreator.estimateTaskBackgroundColor(taskInfo);
    }

    /**
     * Called when a task need a snapshot starting window.
     */
    void makeTaskSnapshotWindow(StartingWindowInfo startingWindowInfo, TaskSnapshot snapshot) {
        mSnapshotWindowCreator.makeTaskSnapshotWindow(startingWindowInfo, snapshot);
    }

    /**
     * Called when the content of a task is ready to show, starting window can be removed.
     */
    public void removeStartingWindow(StartingWindowRemovalInfo removalInfo) {
        if (removalInfo.windowlessSurface) {
            mWindowlessRecords.removeWindow(removalInfo, removalInfo.removeImmediately);
        } else {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_STARTING_WINDOW,
                    "Task start finish, remove starting surface for task: %d",
                    removalInfo.taskId);
            mWindowRecords.removeWindow(removalInfo, removalInfo.removeImmediately);
        }
    }

    /**
     * Create a windowless starting surface and attach to the root surface.
     */
    void addWindowlessStartingSurface(StartingWindowInfo windowInfo) {
        if (windowInfo.taskSnapshot != null) {
            mWindowlessSnapshotWindowCreator.makeTaskSnapshotWindow(windowInfo,
                    windowInfo.rootSurface, windowInfo.taskSnapshot, mSplashScreenExecutor);
        } else {
            mWindowlessSplashWindowCreator.addSplashScreenStartingWindow(
                    windowInfo, windowInfo.rootSurface);
        }
    }

    /**
     * Clear all starting windows immediately.
     */
    public void clearAllWindows() {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_STARTING_WINDOW,
                "Clear all starting windows immediately");
        mWindowRecords.clearAllWindows();
        mWindowlessRecords.clearAllWindows();
    }

    /**
     * Called when the Task wants to copy the splash screen.
     */
    public void copySplashScreenView(int taskId) {
        mSplashscreenWindowCreator.copySplashScreenView(taskId);
    }

    /**
     * Called when the {@link SplashScreenView} is removed from the client Activity view's hierarchy
     * or when the Activity is clean up.
     *
     * @param taskId The Task id on which the splash screen was attached
     */
    public void onAppSplashScreenViewRemoved(int taskId) {
        mSplashscreenWindowCreator.onAppSplashScreenViewRemoved(taskId);
    }

    void onImeDrawnOnTask(int taskId) {
        onImeDrawnOnTask(mWindowRecords, taskId);
        onImeDrawnOnTask(mWindowlessRecords, taskId);
    }

    private void onImeDrawnOnTask(StartingWindowRecordManager records, int taskId) {
        final StartingSurfaceDrawer.StartingWindowRecord sRecord =
                records.getRecord(taskId);
        final SnapshotRecord record = sRecord instanceof SnapshotRecord
                ? (SnapshotRecord) sRecord : null;
        if (record != null && record.hasImeSurface()) {
            records.removeWindow(taskId);
        }
    }

    static class WindowlessStartingWindow extends WindowlessWindowManager {
        SurfaceControl mChildSurface;

        WindowlessStartingWindow(Configuration c, SurfaceControl rootSurface) {
            super(c, rootSurface, null /* hostInputToken */);
        }

        @Override
        protected SurfaceControl getParentSurface(IWindow window,
                WindowManager.LayoutParams attrs) {
            final SurfaceControl.Builder builder = new SurfaceControl.Builder(new SurfaceSession())
                    .setContainerLayer()
                    .setName("Windowless window")
                    .setHidden(false)
                    .setParent(mRootSurface)
                    .setCallsite("WindowlessStartingWindow#attachToParentSurface");
            mChildSurface = builder.build();
            try (SurfaceControl.Transaction t = new SurfaceControl.Transaction()) {
                t.setLayer(mChildSurface, Integer.MAX_VALUE);
                t.apply();
            }
            return mChildSurface;
        }
    }
    abstract static class StartingWindowRecord {
        protected int mBGColor;

        /**
         * Remove the starting window with the given {@link StartingWindowRemovalInfo} if possible.
         * @param info The removal info sent from the task organizer controller in the WM core.
         * @param immediately {@code true} means removing the starting window immediately,
         *                    {@code false} otherwise.
         * @return {@code true} means {@link StartingWindowRecordManager} can safely remove the
         *         record itself. {@code false} means {@link StartingWindowRecordManager} requires
         *         to manage the record reference and remove it later.
         */
        abstract boolean removeIfPossible(StartingWindowRemovalInfo info, boolean immediately);
        int getBGColor() {
            return mBGColor;
        }
    }

    abstract static class SnapshotRecord extends StartingWindowRecord {
        private static final long DELAY_REMOVAL_TIME_GENERAL = 100;
        /**
         * The max delay time in milliseconds for removing the task snapshot window with IME
         * visible.
         * Ideally the delay time will be shorter when receiving
         * {@link StartingSurfaceDrawer#onImeDrawnOnTask(int)}.
         */
        private static final long MAX_DELAY_REMOVAL_TIME_IME_VISIBLE = 600;

        /**
         * The max delay time in milliseconds for removing the task snapshot window with IME
         * visible after the fixed rotation finished.
         * Ideally the delay time will be shorter when receiving
         * {@link StartingSurfaceDrawer#onImeDrawnOnTask(int)}.
         */
        private static final long MAX_DELAY_REMOVAL_TIME_FIXED_ROTATION = 3000;

        private final Runnable mScheduledRunnable = this::removeImmediately;

        @WindowConfiguration.ActivityType protected final int mActivityType;
        protected final ShellExecutor mRemoveExecutor;
        private final int mTaskId;
        private final StartingWindowRecordManager mRecordManager;

        SnapshotRecord(int activityType, ShellExecutor removeExecutor, int taskId,
                StartingWindowRecordManager recordManager) {
            mActivityType = activityType;
            mRemoveExecutor = removeExecutor;
            mTaskId = taskId;
            mRecordManager = recordManager;
        }

        @Override
        public final boolean removeIfPossible(StartingWindowRemovalInfo info, boolean immediately) {
            if (immediately
                    // Show the latest content as soon as possible for unlocking to home.
                    || mActivityType == ACTIVITY_TYPE_HOME
                    || info.deferRemoveMode == DEFER_MODE_NONE) {
                removeImmediately();
                return true;
            }
            scheduleRemove(info.deferRemoveMode);
            return false;
        }

        void scheduleRemove(@StartingWindowRemovalInfo.DeferMode int deferRemoveForImeMode) {
            mRemoveExecutor.removeCallbacks(mScheduledRunnable);
            final long delayRemovalTime;
            switch (deferRemoveForImeMode) {
                case DEFER_MODE_ROTATION:
                    delayRemovalTime = MAX_DELAY_REMOVAL_TIME_FIXED_ROTATION;
                    break;
                case DEFER_MODE_NORMAL:
                    delayRemovalTime = MAX_DELAY_REMOVAL_TIME_IME_VISIBLE;
                    break;
                default:
                    delayRemovalTime = DELAY_REMOVAL_TIME_GENERAL;
            }
            mRemoveExecutor.executeDelayed(mScheduledRunnable, delayRemovalTime);
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_STARTING_WINDOW,
                    "Defer removing snapshot surface in %d", delayRemovalTime);
        }

        protected abstract boolean hasImeSurface();

        @CallSuper
        protected void removeImmediately() {
            mRemoveExecutor.removeCallbacks(mScheduledRunnable);
            mRecordManager.onRecordRemoved(this, mTaskId);
        }
    }

    static class StartingWindowRecordManager {
        private final StartingWindowRemovalInfo mTmpRemovalInfo = new StartingWindowRemovalInfo();
        private final SparseArray<StartingWindowRecord> mStartingWindowRecords =
                new SparseArray<>();

        void clearAllWindows() {
            final int taskSize = mStartingWindowRecords.size();
            final int[] taskIds = new int[taskSize];
            for (int i = taskSize - 1; i >= 0; --i) {
                taskIds[i] = mStartingWindowRecords.keyAt(i);
            }
            for (int i = taskSize - 1; i >= 0; --i) {
                removeWindow(taskIds[i]);
            }
        }

        void addRecord(int taskId, StartingWindowRecord record) {
            final StartingWindowRecord original = mStartingWindowRecords.get(taskId);
            if (original != null) {
                mTmpRemovalInfo.taskId = taskId;
                original.removeIfPossible(mTmpRemovalInfo, true /* immediately */);
            }
            mStartingWindowRecords.put(taskId, record);
        }

        void removeWindow(StartingWindowRemovalInfo removeInfo, boolean immediately) {
            final int taskId = removeInfo.taskId;
            final StartingWindowRecord record = mStartingWindowRecords.get(taskId);
            if (record != null) {
                final boolean canRemoveRecord = record.removeIfPossible(removeInfo, immediately);
                if (canRemoveRecord) {
                    mStartingWindowRecords.remove(taskId);
                }
            }
        }

        void removeWindow(int taskId) {
            mTmpRemovalInfo.taskId = taskId;
            removeWindow(mTmpRemovalInfo, true/* immediately */);
        }

        void onRecordRemoved(@NonNull StartingWindowRecord record, int taskId) {
            final StartingWindowRecord currentRecord = mStartingWindowRecords.get(taskId);
            if (currentRecord == record) {
                mStartingWindowRecords.remove(taskId);
            }
        }

        StartingWindowRecord getRecord(int taskId) {
            return mStartingWindowRecords.get(taskId);
        }

        @VisibleForTesting
        int recordSize() {
            return mStartingWindowRecords.size();
        }
    }
}
