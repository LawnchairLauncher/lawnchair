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

import static android.os.Trace.TRACE_TAG_WINDOW_MANAGER;
import static android.window.StartingWindowInfo.STARTING_WINDOW_TYPE_LEGACY_SPLASH_SCREEN;
import static android.window.StartingWindowInfo.STARTING_WINDOW_TYPE_NONE;
import static android.window.StartingWindowInfo.STARTING_WINDOW_TYPE_SNAPSHOT;
import static android.window.StartingWindowInfo.STARTING_WINDOW_TYPE_SOLID_COLOR_SPLASH_SCREEN;
import static android.window.StartingWindowInfo.STARTING_WINDOW_TYPE_SPLASH_SCREEN;
import static android.window.StartingWindowInfo.STARTING_WINDOW_TYPE_WINDOWLESS;
import static android.window.TransitionInfo.FLAG_BACK_GESTURE_ANIMATED;
import static android.window.TransitionInfo.FLAG_IS_BEHIND_STARTING_WINDOW;

import android.annotation.NonNull;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.TaskInfo;
import android.content.Context;
import android.graphics.Color;
import android.os.IBinder;
import android.os.Trace;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.SurfaceControl;
import android.window.StartingWindowInfo;
import android.window.StartingWindowInfo.StartingWindowType;
import android.window.StartingWindowRemovalInfo;
import android.window.TaskOrganizer;
import android.window.TaskSnapshot;
import android.window.TransitionInfo;

import androidx.annotation.BinderThread;
import androidx.annotation.VisibleForTesting;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.protolog.ProtoLog;
import com.android.internal.util.function.TriConsumer;
import com.android.launcher3.icons.IconProvider;
import com.android.window.flags2.Flags;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.ExternalInterfaceBinder;
import com.android.wm.shell.common.RemoteCallable;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SingleInstanceRemoteListener;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.TransactionPool;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.Transitions;

import java.util.ArrayList;

/**
 * Implementation to draw the starting window to an application, and remove the starting window
 * until the application displays its own window.
 *
 * When receive {@link TaskOrganizer#addStartingWindow} callback, use this class to create a
 * starting window and attached to the Task, then when the Task want to remove the starting window,
 * the TaskOrganizer will receive {@link TaskOrganizer#removeStartingWindow} callback then use this
 * class to remove the starting window of the Task.
 * Besides add/remove starting window, There is an API #setStartingWindowListener to register
 * a callback when starting window is about to create which let the registerer knows the next
 * starting window's type.
 * So far all classes in this package is an enclose system so there is no interact with other shell
 * component, all the methods must be executed in splash screen thread or the thread used in
 * constructor to keep everything synchronized.
 * @hide
 */
public class StartingWindowController implements RemoteCallable<StartingWindowController> {
    public static final String TAG = "ShellStartingWindow";

    private static final long TASK_BG_COLOR_RETAIN_TIME_MS = 5000;

    private final StartingSurfaceDrawer mStartingSurfaceDrawer;
    private final StartingWindowTypeAlgorithm mStartingWindowTypeAlgorithm;

    private TriConsumer<Integer, Integer, Integer> mTaskLaunchingCallback;
    private final StartingSurfaceImpl mImpl = new StartingSurfaceImpl();
    private final Context mContext;
    private final ShellController mShellController;
    private final ShellTaskOrganizer mShellTaskOrganizer;
    private final ShellExecutor mSplashScreenExecutor;
    private final ShellExecutor mShellMainExecutor;
    private final Transitions mTransitions;
    /**
     * Need guarded because it has exposed to StartingSurface
     */
    @GuardedBy("mTaskBackgroundColors")
    private final SparseIntArray mTaskBackgroundColors = new SparseIntArray();
    @VisibleForTesting
    final RemoveStartingObserver mRemoveStartingObserver = new RemoveStartingObserver();

    public StartingWindowController(Context context,
            ShellInit shellInit,
            ShellController shellController,
            ShellTaskOrganizer shellTaskOrganizer,
            ShellExecutor splashScreenExecutor,
            StartingWindowTypeAlgorithm startingWindowTypeAlgorithm,
            IconProvider iconProvider,
            TransactionPool pool,
            ShellExecutor mainExecutor,
            Transitions transitions) {
        mContext = context;
        mShellController = shellController;
        mShellTaskOrganizer = shellTaskOrganizer;
        mStartingSurfaceDrawer = new StartingSurfaceDrawer(context, splashScreenExecutor,
                iconProvider, pool);
        mStartingWindowTypeAlgorithm = startingWindowTypeAlgorithm;
        mSplashScreenExecutor = splashScreenExecutor;
        mShellMainExecutor = mainExecutor;
        mTransitions = transitions;
        shellInit.addInitCallback(this::onInit, this);
    }

    /**
     * Provide the implementation for Shell Module.
     */
    public StartingSurface asStartingSurface() {
        return mImpl;
    }

    private ExternalInterfaceBinder createExternalInterface() {
        return new IStartingWindowImpl(this);
    }

    private void onInit() {
        mShellTaskOrganizer.initStartingWindow(this);
        mShellController.addExternalInterface(IStartingWindow.DESCRIPTOR,
                this::createExternalInterface, this);
        if (Flags.removeStartingInTransition()) {
            mTransitions.registerObserver(mRemoveStartingObserver);
        }
    }

    @VisibleForTesting
    @ShellMainThread
    class RemoveStartingObserver implements Transitions.TransitionObserver {
        /** Task id -> removal info */
        private final SparseArray<WindowRecord> mWindowRecords = new SparseArray<>();
        /** Transition -> removal */
        private final ArrayMap<IBinder, UncertainTracker> mUncertainTrackers = new ArrayMap<>();

        @Override
        public void onTransitionReady(@NonNull IBinder transition, @NonNull TransitionInfo info,
                @NonNull SurfaceControl.Transaction startTransaction,
                @NonNull SurfaceControl.Transaction finishTransaction) {
            if (!hasPendingRemoval()) {
                return;
            }
            final ArrayList<WindowRecord> records = findRecords(transition);
            if (records != null) {
                startTransaction.addTransactionCommittedListener(mShellMainExecutor, () -> {
                    for (int i = records.size() - 1; i >= 0; --i) {
                        final int taskId = records.get(i).mTaskId;
                        final WindowRecord wr = mWindowRecords.get(taskId);
                        if (wr == null) {
                            return;
                        }
                        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_REMOVE_STARTING_TRACKER,
                                "RSO:Transaction applied for task=%d", taskId);
                        wr.mTransactionApplied = true;
                        executeRemovalIfPossible(wr);
                    }
                });
                return;
            }

            for (int i = info.getChanges().size() - 1; i >= 0; --i) {
                final TransitionInfo.Change c = info.getChanges().get(i);
                if ((c.hasFlags(FLAG_IS_BEHIND_STARTING_WINDOW)
                        || c.hasFlags(FLAG_BACK_GESTURE_ANIMATED))
                        && TransitionUtil.isOpeningMode(c.getMode())) {
                    // Uncertain condition, this is activity transition so we don't know which
                    // task the starting window belongs.
                    final UncertainTracker tracker = new UncertainTracker(
                            () -> uncertainTrackComplete(transition));
                    mUncertainTrackers.put(transition, tracker);
                    startTransaction.addTransactionCommittedListener(mShellMainExecutor,
                            tracker);
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_REMOVE_STARTING_TRACKER,
                            "RSO:Create uncertain transition tracker=%s", tracker);
                    break;
                }
            }
        }

        @Override
        public void onTransitionFinished(@NonNull IBinder transition, boolean aborted) {
            if (!hasPendingRemoval()) {
                return;
            }
            // Ensure nothing left.
            final ArrayList<WindowRecord> records = findRecords(transition);
            if (records != null) {
                for (int i = records.size() - 1; i >= 0; --i) {
                    final WindowRecord r = records.get(i);
                    r.mTransactionApplied = true;
                    executeRemovalIfPossible(r);
                }
            } else {
                uncertainTrackComplete(transition);
            }
        }

        void onAddingWindow(int taskId, IBinder transitionToken, IBinder appToken) {
            final WindowRecord wr = mWindowRecords.get(taskId);
            if (wr != null) {
                wr.addAppToken(appToken);
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_REMOVE_STARTING_TRACKER,
                        "RSO:Start tracking appToken=%s for task=%d", appToken, taskId);
            } else {
                mWindowRecords.put(taskId, new WindowRecord(taskId, transitionToken, appToken));
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_REMOVE_STARTING_TRACKER,
                        "RSO:Start tracking for task=%d", taskId);
            }
        }

        // Stop tracking because the window is not created.
        void forceRemoveWindow(int taskId, IBinder appToken) {
            final WindowRecord wr = mWindowRecords.get(taskId);
            if (wr == null || !wr.removeAppToken(appToken)) {
                return;
            }
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_REMOVE_STARTING_TRACKER,
                    "RSO:Window wasn't created, removal record task=%d", taskId);
            mWindowRecords.remove(taskId);
        }

        boolean hasPendingRemoval() {
            return mWindowRecords.size() != 0;
        }

        ArrayList<WindowRecord> findRecords(IBinder transition) {
            ArrayList<WindowRecord> records = null;
            for (int i = mWindowRecords.size() - 1; i >= 0; --i) {
                final WindowRecord record = mWindowRecords.valueAt(i);
                if (record.mTransition == transition) {
                    if (records == null) {
                        records = new ArrayList<>();
                    }
                    records.add(record);
                }
            }
            return records;
        }

        void requestRemoval(int taskId, StartingWindowRemovalInfo removalInfo) {
            final WindowRecord wr = mWindowRecords.get(taskId);
            if (wr == null) {
                return;
            }
            wr.mStartingWindowRemovalInfo = removalInfo;
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_REMOVE_STARTING_TRACKER,
                    "RSO:Receive removal info for task=%d", taskId);
            executeRemovalIfPossible(wr);
        }

        void executeRemovalIfPossible(WindowRecord record) {
            if (record.mStartingWindowRemovalInfo == null) {
                return;
            }
            if (record.mTransition == null
                    || (record.mTransactionApplied && mUncertainTrackers.isEmpty())) {
                mWindowRecords.remove(record.mTaskId);
                removeStartingWindowInner(record.mStartingWindowRemovalInfo);
            }
        }

        private void uncertainTrackComplete(IBinder transition) {
            final boolean hasRemove = mUncertainTrackers.remove(transition) != null;
            if (!hasRemove || !mUncertainTrackers.isEmpty()) {
                return;
            }
            // check if anything task left due to uncertain transition.
            for (int i = mWindowRecords.size() - 1; i >= 0; --i) {
                final WindowRecord record = mWindowRecords.valueAt(i);
                executeRemovalIfPossible(record);
            }
        }

        static class UncertainTracker implements SurfaceControl.TransactionCommittedListener {
            private final Runnable mCleanUp;
            UncertainTracker(Runnable cleanUp) {
                mCleanUp = cleanUp;
            }

            @Override
            public void onTransactionCommitted() {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_REMOVE_STARTING_TRACKER,
                        "RSO:Uncertain transition tracker complete=%s", this);
                mCleanUp.run();
            }
        }

        private static class WindowRecord {
            final int mTaskId;
            final IBinder mTransition;
            boolean mTransactionApplied;
            StartingWindowRemovalInfo mStartingWindowRemovalInfo;

            final ArraySet<IBinder> mAppTokens = new ArraySet<>();

            WindowRecord(int taskId, IBinder transition, IBinder appToken) {
                mTaskId = taskId;
                mTransition = transition;
                addAppToken(appToken);
            }

            void addAppToken(IBinder token) {
                mAppTokens.add(token);
            }

            boolean removeAppToken(IBinder token) {
                mAppTokens.remove(token);
                return mAppTokens.isEmpty();
            }
        }
    }

    @Override
    public Context getContext() {
        return mContext;
    }

    @Override
    public ShellExecutor getRemoteCallExecutor() {
        return mSplashScreenExecutor;
    }

    /*
     * Registers the starting window listener.
     *
     * @param listener The callback when need a starting window.
     */
    @VisibleForTesting
    void setStartingWindowListener(TriConsumer<Integer, Integer, Integer> listener) {
        mTaskLaunchingCallback = listener;
    }

    @VisibleForTesting
    boolean hasStartingWindowListener() {
        return mTaskLaunchingCallback != null;
    }

    /**
     * Called when a task need a starting window.
     */
    public void addStartingWindow(StartingWindowInfo windowInfo) {
        if (Flags.removeStartingInTransition()) {
            mShellMainExecutor.execute(() -> mRemoveStartingObserver.onAddingWindow(
                    windowInfo.taskInfo.taskId, windowInfo.transitionToken, windowInfo.appToken));
        }
        mSplashScreenExecutor.execute(() -> {
            Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "addStartingWindow");

            final int suggestionType = mStartingWindowTypeAlgorithm.getSuggestedWindowType(
                    windowInfo);
            final RunningTaskInfo runningTaskInfo = windowInfo.taskInfo;
            final int taskId = runningTaskInfo.taskId;
            final boolean isWindowless = suggestionType == STARTING_WINDOW_TYPE_WINDOWLESS;
            if (isWindowless) {
                mStartingSurfaceDrawer.addWindowlessStartingSurface(windowInfo);
            } else if (isSplashScreenType(suggestionType)) {
                mStartingSurfaceDrawer.addSplashScreenStartingWindow(windowInfo, suggestionType);
            } else if (suggestionType == STARTING_WINDOW_TYPE_SNAPSHOT) {
                final TaskSnapshot snapshot = windowInfo.taskSnapshot;
                mStartingSurfaceDrawer.makeTaskSnapshotWindow(windowInfo, snapshot);
            }
            if (suggestionType != STARTING_WINDOW_TYPE_NONE
                    && suggestionType != STARTING_WINDOW_TYPE_WINDOWLESS) {
                int color = mStartingSurfaceDrawer
                        .getStartingWindowBackgroundColorForTask(taskId);
                if (color != Color.TRANSPARENT) {
                    synchronized (mTaskBackgroundColors) {
                        mTaskBackgroundColors.append(taskId, color);
                    }
                }
                if (mTaskLaunchingCallback != null && isSplashScreenType(suggestionType)) {
                    mTaskLaunchingCallback.accept(taskId, suggestionType, color);
                }
            }
            if (Flags.removeStartingInTransition()) {
                if (!mStartingSurfaceDrawer.hasStartingWindow(taskId, isWindowless)) {
                    mShellMainExecutor.execute(() ->
                            mRemoveStartingObserver.forceRemoveWindow(taskId, windowInfo.appToken));
                }
            }
            Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
        });
    }

    private static boolean isSplashScreenType(@StartingWindowType int suggestionType) {
        return suggestionType == STARTING_WINDOW_TYPE_SPLASH_SCREEN
                || suggestionType == STARTING_WINDOW_TYPE_SOLID_COLOR_SPLASH_SCREEN
                || suggestionType == STARTING_WINDOW_TYPE_LEGACY_SPLASH_SCREEN;
    }

    public void copySplashScreenView(int taskId) {
        mSplashScreenExecutor.execute(() -> {
            mStartingSurfaceDrawer.copySplashScreenView(taskId);
        });
    }

    /**
     * @see StartingSurfaceDrawer#onAppSplashScreenViewRemoved(int)
     */
    public void onAppSplashScreenViewRemoved(int taskId) {
        mSplashScreenExecutor.execute(
                () -> mStartingSurfaceDrawer.onAppSplashScreenViewRemoved(taskId));
    }

    /**
     * Called when the IME has drawn on the organized task.
     */
    public void onImeDrawnOnTask(int taskId) {
        mSplashScreenExecutor.execute(() -> mStartingSurfaceDrawer.onImeDrawnOnTask(taskId));
    }

    /**
     * Called when the content of a task is ready to show, starting window can be removed.
     */
    public void removeStartingWindow(StartingWindowRemovalInfo removalInfo) {
        final int taskId = removalInfo.taskId;
        if (Flags.removeStartingInTransition()) {
            mShellMainExecutor.execute(() ->
                    mRemoveStartingObserver.requestRemoval(taskId, removalInfo));
        } else {
            removeStartingWindowInner(removalInfo);
        }
    }

    void removeStartingWindowInner(StartingWindowRemovalInfo removalInfo) {
        mSplashScreenExecutor.execute(() -> mStartingSurfaceDrawer.removeStartingWindow(
                removalInfo));
        if (!removalInfo.windowlessSurface) {
            mSplashScreenExecutor.executeDelayed(() -> {
                synchronized (mTaskBackgroundColors) {
                    mTaskBackgroundColors.delete(removalInfo.taskId);
                }
            }, TASK_BG_COLOR_RETAIN_TIME_MS);
        }
    }

    /**
     * Clear all starting window immediately, called this method when releasing the task organizer.
     */
    public void clearAllWindows() {
        mSplashScreenExecutor.execute(() -> {
            mStartingSurfaceDrawer.clearAllWindows();
            synchronized (mTaskBackgroundColors) {
                mTaskBackgroundColors.clear();
            }
        });
    }

    /**
     * The interface for calls from outside the Shell, within the host process.
     */
    private class StartingSurfaceImpl implements StartingSurface {
        @Override
        public int getBackgroundColor(TaskInfo taskInfo) {
            synchronized (mTaskBackgroundColors) {
                final int index = mTaskBackgroundColors.indexOfKey(taskInfo.taskId);
                if (index >= 0) {
                    return mTaskBackgroundColors.valueAt(index);
                }
            }
            final int color = mStartingSurfaceDrawer.estimateTaskBackgroundColor(taskInfo);
            return color != Color.TRANSPARENT
                    ? color : SplashscreenContentDrawer.getSystemBGColor();
        }

        @Override
        public void setSysuiProxy(SysuiProxy proxy) {
            mSplashScreenExecutor.execute(() -> mStartingSurfaceDrawer.setSysuiProxy(proxy));
        }
    }

    /**
     * The interface for calls from outside the host process.
     */
    @BinderThread
    private static class IStartingWindowImpl extends IStartingWindow.Stub
            implements ExternalInterfaceBinder {
        private StartingWindowController mController;
        private SingleInstanceRemoteListener<StartingWindowController,
                IStartingWindowListener> mListener;
        private final TriConsumer<Integer, Integer, Integer> mStartingWindowListener =
                (taskId, supportedType, startingWindowBackgroundColor) -> {
                    mListener.call(l -> l.onTaskLaunching(taskId, supportedType,
                            startingWindowBackgroundColor));
                };

        public IStartingWindowImpl(StartingWindowController controller) {
            mController = controller;
            mListener = new SingleInstanceRemoteListener<>(controller,
                    c -> c.setStartingWindowListener(mStartingWindowListener),
                    c -> c.setStartingWindowListener(null));
        }

        /**
         * Invalidates this instance, preventing future calls from updating the controller.
         */
        @Override
        public void invalidate() {
            mController = null;
            // Unregister the listener to ensure any registered binder death recipients are unlinked
            mListener.unregister();
        }

        @Override
        public void setStartingWindowListener(IStartingWindowListener listener) {
            executeRemoteCallWithTaskPermission(mController, "setStartingWindowListener",
                    (controller) -> {
                        if (listener != null) {
                            mListener.register(listener);
                        } else {
                            mListener.unregister();
                        }
                    });
        }
    }
}
