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

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_UP;

import android.view.MotionEvent;

import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.util.LooperExecutor;
import com.android.launcher3.util.TraceHelper;
import com.android.launcher3.util.UiThreadHelper;
import com.android.quickstep.util.RemoteAnimationTargetSet;
import com.android.systemui.shared.system.InputConsumerController;
import com.android.systemui.shared.system.RecentsAnimationControllerCompat;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

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

    private final MainThreadExecutor mMainThreadExecutor = new MainThreadExecutor();
    private final InputConsumerController mInputConsumer;
    private final Supplier<TouchConsumer> mTouchProxySupplier;

    private TouchConsumer mTouchConsumer;
    private boolean mTouchInProgress;

    private boolean mFinishPending;

    public RecentsAnimationWrapper(InputConsumerController inputConsumer,
            Supplier<TouchConsumer> touchProxySupplier) {
        mInputConsumer = inputConsumer;
        mTouchProxySupplier = touchProxySupplier;
    }

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
     * @param onFinishComplete A callback that runs on the main thread after the animation
     *                         controller has finished on the background thread.
     */
    public void finish(boolean toRecents, Runnable onFinishComplete) {
        if (!toRecents) {
            mExecutorService.submit(() -> finishBg(false, onFinishComplete));
            return;
        }

        mMainThreadExecutor.execute(() -> {
            if (mTouchInProgress) {
                mFinishPending = true;
                // Execute the callback
                if (onFinishComplete != null) {
                    onFinishComplete.run();
                }
            } else {
                mExecutorService.submit(() -> finishBg(true, onFinishComplete));
            }
        });
    }

    protected void finishBg(boolean toRecents, Runnable onFinishComplete) {
        RecentsAnimationControllerCompat controller = mController;
        mController = null;
        TraceHelper.endSection("RecentsController", "Finish " + controller
                + ", toRecents=" + toRecents);
        if (controller != null) {
            controller.setInputConsumerEnabled(false);
            controller.finish(toRecents);

            if (onFinishComplete != null) {
                mMainThreadExecutor.execute(onFinishComplete);
            }
        }
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

    public void enableTouchProxy() {
        mMainThreadExecutor.execute(this::enableTouchProxyUi);
    }

    private void enableTouchProxyUi() {
        mInputConsumer.setTouchListener(this::onInputConsumerTouch);
    }

    private boolean onInputConsumerTouch(MotionEvent ev) {
        int action = ev.getAction();
        if (action == ACTION_DOWN) {
            mTouchInProgress = true;
            mTouchConsumer = mTouchProxySupplier.get();
        } else if (action == ACTION_CANCEL || action == ACTION_UP) {
            // Finish any pending actions
            mTouchInProgress = false;
            if (mFinishPending) {
                mFinishPending = false;
                mExecutorService.submit(() -> finishBg(true, null));
            }
        }
        if (mTouchConsumer != null) {
            mTouchConsumer.accept(ev);
        }

        return true;
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
