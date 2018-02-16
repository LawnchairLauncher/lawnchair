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

import android.os.Handler;
import android.os.Looper;

import com.android.systemui.shared.system.BackgroundExecutor;
import com.android.systemui.shared.system.RecentsAnimationControllerCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

/**
 * Wrapper around RecentsAnimationController to help with some synchronization
 */
public class RecentsAnimationWrapper {

    public RecentsAnimationControllerCompat controller;
    public RemoteAnimationTargetCompat[] targets;

    private boolean mInputConsumerEnabled;

    public synchronized void setController(
            RecentsAnimationControllerCompat controller, RemoteAnimationTargetCompat[] targets) {
        this.controller = controller;
        this.targets = targets;

        if (mInputConsumerEnabled) {
            enableInputConsumer();
        }
    }

    /**
     * @param onFinishComplete A callback that runs on the UI thread.
     */
    public void finish(boolean toHome, Runnable onFinishComplete) {
        BackgroundExecutor.get().submit(() -> {
            synchronized (this) {
                if (controller != null) {
                    controller.setInputConsumerEnabled(false);
                    controller.finish(toHome);
                    if (onFinishComplete != null) {
                        new Handler(Looper.getMainLooper()).post(onFinishComplete);
                    }
                }
            }
        });
    }

    public void enableInputConsumer() {
        mInputConsumerEnabled = true;
        if (mInputConsumerEnabled) {
            BackgroundExecutor.get().submit(() -> {
                synchronized (this) {
                    if (controller != null) {
                        controller.setInputConsumerEnabled(true);
                    }
                }
            });
        }
    }
}
