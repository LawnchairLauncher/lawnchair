/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.quickstep;

import com.android.launcher3.util.LooperExecutor;
import com.android.launcher3.util.TraceHelper;
import com.android.launcher3.util.UiThreadHelper;
import com.android.quickstep.util.RemoteAnimationTargetSet;
import com.android.systemui.shared.system.RecentsAnimationControllerCompat;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

/**
 * Wrapper around RecentsAnimationController to help with some synchronization
 */
public class RecentsAnimationWrapper {

    // A list of callbacks to run when we receive the recents animation target. There are different
    // than the state callbacks as these run on the current worker thread.
    private final ArrayList<Runnable> mCallbacks = new ArrayList<>();

    public RemoteAnimationTargetSet targetSet;

    private RecentsAnimationControllerCompat mController;
    private boolean mInputConsumerEnabled = false;
    private boolean mBehindSystemBars = true;
    private boolean mSplitScreenMinimized = false;

    private final ExecutorService mExecutorService =
            new LooperExecutor(UiThreadHelper.getBackgroundLooper());

    public synchronized void setController(
            RecentsAnimationControllerCompat controller, RemoteAnimationTargetSet targetSet) {
        TraceHelper.partitionSection("RecentsController", "Set controller " + controller);
        this.mController = controller;
        this.targetSet = targetSet;

        if (controller == null) {
            return;
        }
        if (mInputConsumerEnabled) {
            enableInputConsumer();
        }

        if (!mCallbacks.isEmpty()) {
            for (Runnable action : new ArrayList<>(mCallbacks)) {
                action.run();
            }
            mCallbacks.clear();
        }
    }

    public synchronized void runOnInit(Runnable action) {
        if (targetSet == null) {
            mCallbacks.add(action);
        } else {
            action.run();
        }
    }

    /**
     * @param onFinishComplete A callback that runs after the animation controller has finished
     *                         on the background thread.
     */
    public void finish(boolean toHome, Runnable onFinishComplete) {
        mExecutorService.submit(() -> {
            RecentsAnimationControllerCompat controller = mController;
            mController = null;
            TraceHelper.endSection("RecentsController",
                    "Finish " + controller + ", toHome=" + toHome);
            if (controller != null) {
                controller.setInputConsumerEnabled(false);
                controller.finish(toHome);
                if (onFinishComplete != null) {
                    onFinishComplete.run();
                }
            }
        });
    }

    public void enableInputConsumer() {
        mInputConsumerEnabled = true;
        if (mInputConsumerEnabled) {
            mExecutorService.submit(() -> {
                RecentsAnimationControllerCompat controller = mController;
                TraceHelper.partitionSection("RecentsController",
                        "Enabling consumer on " + controller);
                if (controller != null) {
                    controller.setInputConsumerEnabled(true);
                }
            });
        }
    }

    public void setAnimationTargetsBehindSystemBars(boolean behindSystemBars) {
        if (mBehindSystemBars == behindSystemBars) {
            return;
        }
        mBehindSystemBars = behindSystemBars;
        mExecutorService.submit(() -> {
            RecentsAnimationControllerCompat controller = mController;
            TraceHelper.partitionSection("RecentsController",
                    "Setting behind system bars on " + controller);
            if (controller != null) {
                controller.setAnimationTargetsBehindSystemBars(behindSystemBars);
            }
        });
    }

    /**
     * NOTE: As a workaround for conflicting animations (Launcher animating the task leash, and
     * SystemUI resizing the docked stack, which resizes the task), we currently only set the
     * minimized mode, and not the inverse.
     * TODO: Synchronize the minimize animation with the launcher animation
     */
    public void setSplitScreenMinimizedForTransaction(boolean minimized) {
        if (mSplitScreenMinimized || !minimized) {
            return;
        }
        mSplitScreenMinimized = minimized;
        mExecutorService.submit(() -> {
            RecentsAnimationControllerCompat controller = mController;
            TraceHelper.partitionSection("RecentsController",
                    "Setting minimize dock on " + controller);
            if (controller != null) {
                controller.setSplitScreenMinimized(minimized);
            }
        });
    }

    public void hideCurrentInputMethod() {
        mExecutorService.submit(() -> {
            RecentsAnimationControllerCompat controller = mController;
            TraceHelper.partitionSection("RecentsController",
                    "Hiding currentinput method on " + controller);
            if (controller != null) {
                controller.hideCurrentInputMethod();
            }
        });
    }

    public RecentsAnimationControllerCompat getController() {
        return mController;
    }
}
