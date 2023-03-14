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
package com.android.launcher3.util;

import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.WorkerThread;

import java.util.function.Consumer;

/**
 * Utility class to define an object which does most of it's processing on a
 * dedicated background thread.
 */
public abstract class BgObjectWithLooper {

    /**
     * Start initialization of the object
     */
    public final void initializeInBackground(String threadName) {
        new Thread(this::runOnThread, threadName).start();
    }

    private void runOnThread() {
        Looper.prepare();
        onInitialized(Looper.myLooper());
        Looper.loop();
    }

    /**
     * Called on the background thread to handle initialization
     */
    @WorkerThread
    protected abstract void onInitialized(Looper looper);

    /**
     * Helper method to create a content provider
     */
    protected static ContentObserver newContentObserver(Handler handler, Consumer<Uri> command) {
        return new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                command.accept(uri);
            }
        };
    }
}
