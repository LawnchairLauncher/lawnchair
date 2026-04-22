/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.quickstep.util;

import android.app.ActivityTaskManager;
import android.app.IActivityTaskManager;
import android.app.IAssistDataReceiver;
import android.app.assist.AssistContent;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import com.android.launcher3.util.Executors;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;

/**
 * Can be used to request the AssistContent from a provided task id, useful for getting the web uri
 * if provided from the task.
 */
public class AssistContentRequester {
    private static final String TAG = "AssistContentRequester";
    private static final String ASSIST_KEY_CONTENT = "content";

    /** For receiving content, called on the main thread. */
    public interface Callback {
        /**
         * Called when the {@link android.app.assist.AssistContent} of the requested task is
         * available.
         **/
        void onAssistContentAvailable(AssistContent assistContent);
    }

    private final IActivityTaskManager mActivityTaskManager;
    private final String mAttributionTag;
    private final String mPackageName;
    private final Executor mCallbackExecutor;
    private final Executor mSystemInteractionExecutor;

    // If system loses the callback, our internal cache of original callback will also get cleared.
    private final Map<Object, Callback> mPendingCallbacks =
            Collections.synchronizedMap(new WeakHashMap<>());

    public AssistContentRequester(Context context) {
        mActivityTaskManager = ActivityTaskManager.getService();
        mAttributionTag = context.getAttributionTag();
        mPackageName = context.getApplicationContext().getPackageName();
        mCallbackExecutor = Executors.MAIN_EXECUTOR;
        mSystemInteractionExecutor = Executors.UI_HELPER_EXECUTOR;
    }

    /**
     * Request the {@link AssistContent} from the task with the provided id.
     *
     * @param taskId to query for the content.
     * @param callback to call when the content is available, called on the main thread.
     */
    public void requestAssistContent(final int taskId, final Callback callback) {
        // ActivityTaskManager interaction here is synchronous, so call off the main thread.
        mSystemInteractionExecutor.execute(() -> {
            try {
                mActivityTaskManager.requestAssistDataForTask(
                        new AssistDataReceiver(callback, this), taskId, mPackageName,
                        mAttributionTag, false /* fetchStructure */);
            } catch (RemoteException e) {
                Log.e(TAG, "Requesting assist content failed for task: " + taskId, e);
            }
        });
    }

    private void executeOnMainExecutor(Runnable callback) {
        mCallbackExecutor.execute(callback);
    }

    private static final class AssistDataReceiver extends IAssistDataReceiver.Stub {

        // The AssistDataReceiver binder callback object is passed to a system server, that may
        // keep hold of it for longer than the lifetime of the AssistContentRequester object,
        // potentially causing a memory leak. In the callback passed to the system server, only
        // keep a weak reference to the parent object and lookup its callback if it still exists.
        private final WeakReference<AssistContentRequester> mParentRef;
        private final Object mCallbackKey = new Object();

        AssistDataReceiver(Callback callback, AssistContentRequester parent) {
            parent.mPendingCallbacks.put(mCallbackKey, callback);
            mParentRef = new WeakReference<>(parent);
        }

        @Override
        public void onHandleAssistData(Bundle data) {
            if (data == null) {
                return;
            }

            final AssistContent content = data.getParcelable(ASSIST_KEY_CONTENT);
            if (content == null) {
                Log.e(TAG, "Received AssistData, but no AssistContent found");
                return;
            }

            AssistContentRequester requester = mParentRef.get();
            if (requester != null) {
                Callback callback = requester.mPendingCallbacks.get(mCallbackKey);
                if (callback != null) {
                    requester.executeOnMainExecutor(
                            () -> callback.onAssistContentAvailable(content));
                } else {
                    Log.d(TAG, "Callback received after calling UI was disposed of");
                }
            } else {
                Log.d(TAG, "Callback received after Requester was collected");
            }
        }

        @Override
        public void onHandleAssistScreenshot(Bitmap screenshot) {}
    }
}
