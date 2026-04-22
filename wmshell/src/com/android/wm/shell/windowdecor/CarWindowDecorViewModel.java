/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.wm.shell.windowdecor;

import android.annotation.NonNull;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityTaskManager;
import android.app.IActivityTaskManager;
import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;
import android.util.SparseArray;
import android.view.InputDevice;
import android.view.InsetsState;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.SurfaceControl;
import android.view.View;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import com.android.wm.shell.R;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.freeform.FreeformTaskTransitionStarter;
import com.android.wm.shell.shared.FocusTransitionListener;
import com.android.wm.shell.shared.annotations.ShellBackgroundThread;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.splitscreen.SplitScreenController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.FocusTransitionObserver;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost;
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier;

/**
 * Works with decorations that extend {@link CarWindowDecoration}.
 */
public abstract class CarWindowDecorViewModel
        implements WindowDecorViewModel, FocusTransitionListener,
        DisplayInsetsController.OnInsetsChangedListener {
    private static final String TAG = "CarWindowDecorViewModel";

    private final ShellTaskOrganizer mTaskOrganizer;
    private final Context mContext;
    private final @NonNull @ShellMainThread Handler mHandler;
    private final @NonNull Transitions mTransitions;
    private final @ShellBackgroundThread ShellExecutor mBgExecutor;
    private final ShellExecutor mMainExecutor;
    private final DisplayController mDisplayController;
    private final DisplayInsetsController mDisplayInsetsController;
    private final FocusTransitionObserver mFocusTransitionObserver;
    private final SyncTransactionQueue mSyncQueue;
    private final SparseArray<CarWindowDecoration> mWindowDecorByTaskId = new SparseArray<>();
    private final WindowDecorViewHostSupplier<WindowDecorViewHost> mWindowDecorViewHostSupplier;
    private final IActivityTaskManager mActivityTaskManager;

    public CarWindowDecorViewModel(
            Context context,
            @NonNull @ShellMainThread Handler handler,
            @NonNull Transitions transitions,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellBackgroundThread ShellExecutor bgExecutor,
            ShellInit shellInit,
            ShellTaskOrganizer taskOrganizer,
            DisplayController displayController,
            DisplayInsetsController displayInsetsController,
            SyncTransactionQueue syncQueue,
            FocusTransitionObserver focusTransitionObserver,
            WindowDecorViewHostSupplier<WindowDecorViewHost> windowDecorViewHostSupplier) {
        mContext = context;
        mHandler = handler;
        mTransitions = transitions;
        mMainExecutor = mainExecutor;
        mBgExecutor = bgExecutor;
        mTaskOrganizer = taskOrganizer;
        mDisplayController = displayController;
        mDisplayInsetsController = displayInsetsController;
        mFocusTransitionObserver = focusTransitionObserver;
        mSyncQueue = syncQueue;
        mWindowDecorViewHostSupplier = windowDecorViewHostSupplier;
        mActivityTaskManager = ActivityTaskManager.getService();

        shellInit.addInitCallback(this::onInit, this);
        displayInsetsController.addGlobalInsetsChangedListener(this);
    }

    private void onInit() {
        mFocusTransitionObserver.setLocalFocusTransitionListener(this, mMainExecutor);
    }

    @Override
    public void onFocusedTaskChanged(RunningTaskInfo taskInfo, boolean isFocusedOnDisplay,
            boolean isFocusedGlobally) {
        // no-op
    }

    @Override
    public void setFreeformTaskTransitionStarter(FreeformTaskTransitionStarter transitionStarter) {
        // no-op
    }

    @Override
    public void setSplitScreenController(SplitScreenController splitScreenController) {
        // no-op
    }

    @Override
    public boolean onTaskOpening(
            RunningTaskInfo taskInfo,
            SurfaceControl taskSurface,
            SurfaceControl.Transaction startT,
            SurfaceControl.Transaction finishT) {
        createWindowDecoration(taskInfo, taskSurface, startT, finishT);
        return true;
    }

    @Override
    public void onTaskInfoChanged(RunningTaskInfo taskInfo) {
        final CarWindowDecoration decoration = mWindowDecorByTaskId.get(taskInfo.taskId);

        if (decoration == null) {
            return;
        }

        final SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        decoration.relayout(taskInfo, t, t,
                /* isCaptionVisible= */ shouldShowWindowDecor(taskInfo));
    }

    @Override
    public void onTaskVanished(RunningTaskInfo taskInfo) {
        // A task vanishing doesn't necessarily mean the task was closed, it could also mean its
        // windowing mode changed. We're only interested in closing tasks so checking whether
        // its info still exists in the task organizer is one way to disambiguate.
        final boolean closed = mTaskOrganizer.getRunningTaskInfo(taskInfo.taskId) == null;
        if (closed) {
            // Destroying the window decoration is usually handled when a TRANSIT_CLOSE transition
            // changes happen, but there are certain cases in which closing tasks aren't included
            // in transitions, such as when a non-visible task is closed. See b/296921167.
            // Destroy the decoration here in case the lack of transition missed it.
            destroyWindowDecoration(taskInfo);
        }
    }

    @Override
    public void onTaskChanging(
            RunningTaskInfo taskInfo,
            SurfaceControl taskSurface,
            SurfaceControl.Transaction startT,
            SurfaceControl.Transaction finishT) {
        final CarWindowDecoration decoration = mWindowDecorByTaskId.get(taskInfo.taskId);

        if (!shouldShowWindowDecor(taskInfo)) {
            if (decoration != null) {
                destroyWindowDecoration(taskInfo);
            }
            return;
        }

        if (decoration == null) {
            createWindowDecoration(taskInfo, taskSurface, startT, finishT);
        } else {
            decoration.relayout(taskInfo, startT, finishT);
        }
    }

    @Override
    public void onTaskClosing(
            RunningTaskInfo taskInfo,
            SurfaceControl.Transaction startT,
            SurfaceControl.Transaction finishT) {
        final CarWindowDecoration decoration = mWindowDecorByTaskId.get(taskInfo.taskId);
        if (decoration == null) {
            return;
        }
        decoration.relayout(taskInfo, startT, finishT);
    }

    @Override
    public void destroyWindowDecoration(RunningTaskInfo taskInfo) {
        final CarWindowDecoration decoration =
                mWindowDecorByTaskId.removeReturnOld(taskInfo.taskId);
        if (decoration == null) {
            return;
        }

        decoration.close();
    }

    @Override
    public void insetsChanged(int displayId, InsetsState insetsState) {
        final SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        try {
            mActivityTaskManager.getTasks(/* maxNum= */ Integer.MAX_VALUE,
                            /* filterOnlyVisibleRecents= */ false, /* keepIntentExtra= */ false,
                            displayId)
                    .stream().filter(taskInfo -> taskInfo.isVisible && taskInfo.isRunning)
                    .forEach(taskInfo -> {
                        final CarWindowDecoration decoration = mWindowDecorByTaskId.get(
                                taskInfo.taskId);
                        if (decoration != null) {
                            decoration.relayout(taskInfo, t, t);
                        }
                    });
        } catch (RemoteException e) {
            Log.e(TAG, "Cannot update decoration on inset change on displayId: " + displayId);
        }
    }

    /**
     * @return {@code true} if the task/activity associated with {@code taskInfo} should show
     * window decoration.
     */
    protected abstract boolean shouldShowWindowDecor(RunningTaskInfo taskInfo);

    private void createWindowDecoration(
            RunningTaskInfo taskInfo,
            SurfaceControl taskSurface,
            SurfaceControl.Transaction startT,
            SurfaceControl.Transaction finishT) {
        final CarWindowDecoration oldDecoration = mWindowDecorByTaskId.get(taskInfo.taskId);
        if (oldDecoration != null) {
            // close the old decoration if it exists to avoid two window decorations being added
            oldDecoration.close();
        }
        final CarWindowDecoration windowDecoration =
                new CarWindowDecoration(
                        mContext,
                        mHandler,
                        mTransitions,
                        mContext.createContextAsUser(UserHandle.of(taskInfo.userId), 0 /* flags */),
                        mDisplayController,
                        mTaskOrganizer,
                        taskInfo,
                        taskSurface,
                        mBgExecutor,
                        mWindowDecorViewHostSupplier,
                        new ButtonClickListener(taskInfo));
        mWindowDecorByTaskId.put(taskInfo.taskId, windowDecoration);
        windowDecoration.relayout(taskInfo, startT, finishT,
                /* isCaptionVisible= */ shouldShowWindowDecor(taskInfo));
    }

    private class ButtonClickListener implements View.OnClickListener {
        private final WindowContainerToken mTaskToken;
        private final int mDisplayId;

        private ButtonClickListener(RunningTaskInfo taskInfo) {
            mTaskToken = taskInfo.token;
            mDisplayId = taskInfo.displayId;
        }

        @Override
        public void onClick(View v) {
            final int id = v.getId();
            if (id == R.id.close_window) {
                WindowContainerTransaction wct = new WindowContainerTransaction();
                wct.removeTask(mTaskToken);
                mSyncQueue.queue(wct);
            } else if (id == R.id.back_button) {
                sendBackEvent(KeyEvent.ACTION_DOWN, mDisplayId);
                sendBackEvent(KeyEvent.ACTION_UP, mDisplayId);
            }
        }

        private void sendBackEvent(int action, int displayId) {
            final long when = SystemClock.uptimeMillis();
            final KeyEvent ev = new KeyEvent(when, when, action, KeyEvent.KEYCODE_BACK,
                    0 /* repeat */, 0 /* metaState */, KeyCharacterMap.VIRTUAL_KEYBOARD,
                    0 /* scancode */, KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                    InputDevice.SOURCE_KEYBOARD);

            ev.setDisplayId(displayId);
            if (!mContext.getSystemService(InputManager.class)
                    .injectInputEvent(ev, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC)) {
                Log.e(TAG, "Inject input event fail");
            }
        }
    }
}
