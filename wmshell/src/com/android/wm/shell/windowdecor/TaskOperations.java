/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;

import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.hardware.input.InputManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.freeform.FreeformTaskTransitionStarter;
import com.android.wm.shell.transition.Transitions;

/**
 * Utility class to handle task operations performed on a window decoration.
 */
class TaskOperations {
    private static final String TAG = "TaskOperations";

    private final FreeformTaskTransitionStarter mTransitionStarter;
    private final Context mContext;
    private final SyncTransactionQueue mSyncQueue;

    TaskOperations(FreeformTaskTransitionStarter transitionStarter, Context context,
            SyncTransactionQueue syncQueue) {
        mTransitionStarter = transitionStarter;
        mContext = context;
        mSyncQueue = syncQueue;
    }

    void injectBackKey(int displayId) {
        sendBackEvent(KeyEvent.ACTION_DOWN, displayId);
        sendBackEvent(KeyEvent.ACTION_UP, displayId);
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

    void closeTask(WindowContainerToken taskToken) {
        closeTask(taskToken, new WindowContainerTransaction());
    }

    void closeTask(WindowContainerToken taskToken, WindowContainerTransaction wct) {
        wct.removeTask(taskToken);
        if (Transitions.ENABLE_SHELL_TRANSITIONS) {
            mTransitionStarter.startRemoveTransition(wct);
        } else {
            mSyncQueue.queue(wct);
        }
    }

    void minimizeTask(WindowContainerToken taskToken) {
        WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.reorder(taskToken, false);
        if (Transitions.ENABLE_SHELL_TRANSITIONS) {
            mTransitionStarter.startMinimizedModeTransition(wct);
        } else {
            mSyncQueue.queue(wct);
        }
    }

    void maximizeTask(RunningTaskInfo taskInfo, int containerWindowingMode) {
        WindowContainerTransaction wct = new WindowContainerTransaction();
        int targetWindowingMode = taskInfo.getWindowingMode() != WINDOWING_MODE_FULLSCREEN
                ? WINDOWING_MODE_FULLSCREEN : WINDOWING_MODE_FREEFORM;
        wct.setWindowingMode(taskInfo.token,
                targetWindowingMode == containerWindowingMode
                        ? WINDOWING_MODE_UNDEFINED : targetWindowingMode);
        if (targetWindowingMode == WINDOWING_MODE_FULLSCREEN) {
            wct.setBounds(taskInfo.token, null);
        }
        if (Transitions.ENABLE_SHELL_TRANSITIONS) {
            mTransitionStarter.startWindowingModeTransition(targetWindowingMode, wct);
        } else {
            mSyncQueue.queue(wct);
        }
    }
}
