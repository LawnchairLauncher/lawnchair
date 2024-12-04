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

package com.android.wm.shell.common;

import android.annotation.NonNull;
import android.os.Handler;

/** Executor implementation which is backed by a Handler. */
public class HandlerExecutor implements ShellExecutor {
    private final Handler mHandler;

    public HandlerExecutor(@NonNull Handler handler) {
        mHandler = handler;
    }

    @Override
    public void execute(@NonNull Runnable command) {
        if (mHandler.getLooper().isCurrentThread()) {
            command.run();
            return;
        }
        if (!mHandler.post(command)) {
            throw new RuntimeException(mHandler + " is probably exiting");
        }
    }

    @Override
    public void executeDelayed(@NonNull Runnable r, long delayMillis) {
        if (!mHandler.postDelayed(r, delayMillis)) {
            throw new RuntimeException(mHandler + " is probably exiting");
        }
    }

    @Override
    public void removeCallbacks(@NonNull Runnable r) {
        mHandler.removeCallbacks(r);
    }

    @Override
    public boolean hasCallback(Runnable r) {
        return mHandler.hasCallbacks(r);
    }
}
