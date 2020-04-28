/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.view.inputmethod.InputMethodManager;

/**
 * Utility class for offloading some class from UI thread
 */
public class UiThreadHelper {

    private static Handler sHandler;

    private static final int MSG_HIDE_KEYBOARD = 1;
    private static final int MSG_SET_ORIENTATION = 2;
    private static final int MSG_RUN_COMMAND = 3;

    private static Handler getHandler(Context context) {
        if (sHandler == null) {
            sHandler = new Handler(UI_HELPER_EXECUTOR.getLooper(),
                    new UiCallbacks(context.getApplicationContext()));
        }
        return sHandler;
    }

    public static void hideKeyboardAsync(Context context, IBinder token) {
        Message.obtain(getHandler(context), MSG_HIDE_KEYBOARD, token).sendToTarget();
    }

    public static void setOrientationAsync(Activity activity, int orientation) {
        Message.obtain(getHandler(activity), MSG_SET_ORIENTATION, orientation, 0, activity)
                .sendToTarget();
    }

    public static void runAsyncCommand(Context context, AsyncCommand command, int arg1, int arg2) {
        Message.obtain(getHandler(context), MSG_RUN_COMMAND, arg1, arg2, command).sendToTarget();
    }

    private static class UiCallbacks implements Handler.Callback {

        private final InputMethodManager mIMM;

        UiCallbacks(Context context) {
            mIMM = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        }

        @Override
        public boolean handleMessage(Message message) {
            switch (message.what) {
                case MSG_HIDE_KEYBOARD:
                    mIMM.hideSoftInputFromWindow((IBinder) message.obj, 0);
                    return true;
                case MSG_SET_ORIENTATION:
                    ((Activity) message.obj).setRequestedOrientation(message.arg1);
                    return true;
                case MSG_RUN_COMMAND:
                    ((AsyncCommand) message.obj).execute(message.arg1, message.arg2);
                    return true;
            }
            return false;
        }
    }

    public interface AsyncCommand {

        void execute(int arg1, int arg2);
    }
}
