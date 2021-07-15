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

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_KEYBOARD_CLOSED;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import com.android.launcher3.Launcher;
import com.android.launcher3.views.ActivityContext;

/**
 * Utility class for offloading some class from UI thread
 */
public class UiThreadHelper {

    private static final MainThreadInitializedObject<Handler> HANDLER =
            new MainThreadInitializedObject<>(
                    c -> new Handler(UI_HELPER_EXECUTOR.getLooper(), new UiCallbacks(c)));

    private static final int MSG_HIDE_KEYBOARD = 1;
    private static final int MSG_SET_ORIENTATION = 2;
    private static final int MSG_RUN_COMMAND = 3;
    private static final String STATS_LOGGER_KEY = "STATS_LOGGER_KEY";

    @SuppressLint("NewApi")
    public static void hideKeyboardAsync(ActivityContext activityContext, IBinder token) {
        View root = activityContext.getDragLayer();

        // Since the launcher context cannot be accessed directly from callback, adding secondary
        // message to log keyboard close event asynchronously.
        Bundle mHideKeyboardLoggerMsg = new Bundle();
        mHideKeyboardLoggerMsg.putParcelable(
                STATS_LOGGER_KEY,
                Message.obtain(
                        HANDLER.get(root.getContext()),
                        () -> Launcher.cast(activityContext)
                                .getStatsLogManager()
                                .logger()
                                .log(LAUNCHER_ALLAPPS_KEYBOARD_CLOSED)
                )
        );

        Message mHideKeyboardMsg = Message.obtain(HANDLER.get(root.getContext()), MSG_HIDE_KEYBOARD,
                token);
        mHideKeyboardMsg.setData(mHideKeyboardLoggerMsg);
        mHideKeyboardMsg.sendToTarget();
    }

    public static void setOrientationAsync(Activity activity, int orientation) {
        Message.obtain(HANDLER.get(activity), MSG_SET_ORIENTATION, orientation, 0, activity)
                .sendToTarget();
    }

    public static void setBackButtonAlphaAsync(Context context, AsyncCommand command, float alpha,
            boolean animate) {
        runAsyncCommand(context, command, Float.floatToIntBits(alpha), animate ? 1 : 0);
    }

    public static void runAsyncCommand(Context context, AsyncCommand command, int arg1, int arg2) {
        Message.obtain(HANDLER.get(context), MSG_RUN_COMMAND, arg1, arg2, command).sendToTarget();
    }

    private static class UiCallbacks implements Handler.Callback {

        private final Context mContext;
        private final InputMethodManager mIMM;

        UiCallbacks(Context context) {
            mContext = context;
            mIMM = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        }

        @Override
        public boolean handleMessage(Message message) {
            switch (message.what) {
                case MSG_HIDE_KEYBOARD:
                    if (mIMM.hideSoftInputFromWindow((IBinder) message.obj, 0)) {
                        // log keyboard close event only when keyboard is actually closed
                        ((Message) message.getData().getParcelable(STATS_LOGGER_KEY))
                                .sendToTarget();
                    }
                    return true;
                case MSG_SET_ORIENTATION:
                    ((Activity) message.obj).setRequestedOrientation(message.arg1);
                    return true;
                case MSG_RUN_COMMAND:
                    ((AsyncCommand) message.obj).execute(mContext, message.arg1, message.arg2);
                    return true;
            }
            return false;
        }
    }

    public interface AsyncCommand {
        void execute(Context proxy, int arg1, int arg2);
    }
}
