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

package com.android.quickstep;

import static com.android.quickstep.views.OverviewActionsView.DISABLED_NO_THUMBNAIL;
import static com.android.quickstep.views.OverviewActionsView.DISABLED_ROTATED;

import android.annotation.SuppressLint;
import android.app.assist.AssistContent;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Matrix;
import android.os.SystemClock;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.quickstep.util.AssistContentRequester;
import com.android.quickstep.views.OverviewActionsView;
import com.android.quickstep.views.TaskThumbnailView;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;

/**
 * Go-specific extension of the factory class that adds an overlay to TaskView
 */
public final class TaskOverlayFactoryGo extends TaskOverlayFactory {
    public static final String ACTION_LISTEN = "com.android.quickstep.ACTION_LISTEN";
    public static final String ACTION_TRANSLATE = "com.android.quickstep.ACTION_TRANSLATE";
    public static final String ACTION_SEARCH = "com.android.quickstep.ACTION_SEARCH";
    public static final String ELAPSED_NANOS = "niu_actions_elapsed_realtime_nanos";
    public static final String ACTIONS_URL = "niu_actions_app_url";
    public static final String ACTIONS_APP_PACKAGE = "niu_actions_app_package";
    public static final String ACTIONS_ERROR_CODE = "niu_actions_app_error_code";
    public static final int ERROR_PERMISSIONS_STRUCTURE = 1;
    public static final int ERROR_PERMISSIONS_SCREENSHOT = 2;
    private static final String TAG = "TaskOverlayFactoryGo";

    private AssistContentRequester mContentRequester;

    public TaskOverlayFactoryGo(Context context) {
        mContentRequester = new AssistContentRequester(context);
    }

    /**
     * Create a new overlay instance for the given View
     */
    public TaskOverlayGo createOverlay(TaskThumbnailView thumbnailView) {
        return new TaskOverlayGo(thumbnailView, mContentRequester);
    }

    /**
     * Overlay on each task handling Overview Action Buttons.
     * @param <T> The type of View in which the overlay will be placed
     */
    public static final class TaskOverlayGo<T extends OverviewActionsView> extends TaskOverlay {
        private String mNIUPackageName;
        private String mTaskPackageName;
        private String mWebUrl;
        private boolean mAssistStructurePermitted;
        private boolean mAssistScreenshotPermitted;
        private AssistContentRequester mFactoryContentRequester;

        private TaskOverlayGo(TaskThumbnailView taskThumbnailView,
                AssistContentRequester assistContentRequester) {
            super(taskThumbnailView);
            mFactoryContentRequester = assistContentRequester;
        }

        /**
         * Called when the current task is interactive for the user
         */
        @Override
        public void initOverlay(Task task, ThumbnailData thumbnail, Matrix matrix,
                boolean rotated) {
            getActionsView().updateDisabledFlags(DISABLED_NO_THUMBNAIL, thumbnail == null);
            checkSettings();
            if (thumbnail == null || TextUtils.isEmpty(mNIUPackageName)) {
                return;
            }

            getActionsView().updateDisabledFlags(DISABLED_ROTATED, rotated);
            // Disable Overview Actions for Work Profile apps
            boolean isManagedProfileTask =
                    UserManager.get(mApplicationContext).isManagedProfile(task.key.userId);
            boolean isAllowedByPolicy = mThumbnailView.isRealSnapshot() && !isManagedProfileTask;
            getActionsView().setCallbacks(new OverlayUICallbacksGoImpl(isAllowedByPolicy, task));
            mTaskPackageName = task.key.getPackageName();

            if (!mAssistStructurePermitted || !mAssistScreenshotPermitted) {
                return;
            }

            int taskId = task.key.id;
            mFactoryContentRequester.requestAssistContent(taskId, this::onAssistContentReceived);
        }

        /** Provide Assist Content to the overlay. */
        @VisibleForTesting
        public void onAssistContentReceived(AssistContent assistContent) {
            mWebUrl = assistContent.getWebUri() != null
                    ? assistContent.getWebUri().toString() : null;
        }

        @Override
        public void reset() {
            super.reset();
            mWebUrl = null;
        }

        /**
         * Creates and sends an Intent corresponding to the button that was clicked
         */
        private void sendNIUIntent(String actionType) {
            Intent intent = createNIUIntent(actionType);
            // Only add and send the image if the appropriate permissions are held
            if (mAssistStructurePermitted && mAssistScreenshotPermitted) {
                mImageApi.shareAsDataWithExplicitIntent(/* crop */ null, intent);
            } else {
                // If both permissions are disabled, the structure error code takes priority
                // The user must enable that one before they can enable screenshots
                int code = mAssistStructurePermitted ? ERROR_PERMISSIONS_SCREENSHOT
                        : ERROR_PERMISSIONS_STRUCTURE;
                intent.putExtra(ACTIONS_ERROR_CODE, code);
                try {
                    mApplicationContext.startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Log.e(TAG, "No activity found to receive permission error intent");
                }
            }
        }

        private Intent createNIUIntent(String actionType) {
            Intent intent = new Intent(actionType)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    .setPackage(mNIUPackageName)
                    .putExtra(ACTIONS_APP_PACKAGE, mTaskPackageName)
                    .putExtra(ELAPSED_NANOS, SystemClock.elapsedRealtimeNanos());

            if (mWebUrl != null) {
                intent.putExtra(ACTIONS_URL, mWebUrl);
            }

            return intent;
        }

        /**
         * Checks whether the Assistant has screen context permissions
         */
        @VisibleForTesting
        public void checkSettings() {
            ContentResolver contentResolver = mApplicationContext.getContentResolver();
            mAssistStructurePermitted = Settings.Secure.getInt(contentResolver,
                    Settings.Secure.ASSIST_STRUCTURE_ENABLED, 1) != 0;
            mAssistScreenshotPermitted = Settings.Secure.getInt(contentResolver,
                    Settings.Secure.ASSIST_SCREENSHOT_ENABLED, 1) != 0;

            String assistantPackage =
                    Settings.Secure.getString(contentResolver, Settings.Secure.ASSISTANT);
            mNIUPackageName = assistantPackage.split("/", 2)[0];
        }

        protected class OverlayUICallbacksGoImpl extends OverlayUICallbacksImpl
                implements OverlayUICallbacksGo {
            public OverlayUICallbacksGoImpl(boolean isAllowedByPolicy, Task task) {
                super(isAllowedByPolicy, task);
            }

            @SuppressLint("NewApi")
            public void onListen() {
                if (mIsAllowedByPolicy) {
                    sendNIUIntent(ACTION_LISTEN);
                } else {
                    showBlockedByPolicyMessage();
                }
            }

            @SuppressLint("NewApi")
            public void onTranslate() {
                if (mIsAllowedByPolicy) {
                    sendNIUIntent(ACTION_TRANSLATE);
                } else {
                    showBlockedByPolicyMessage();
                }
            }

            @SuppressLint("NewApi")
            public void onSearch() {
                if (mIsAllowedByPolicy) {
                    sendNIUIntent(ACTION_SEARCH);
                } else {
                    showBlockedByPolicyMessage();
                }
            }
        }

        @VisibleForTesting
        public void setImageActionsAPI(ImageActionsApi imageActionsApi) {
            mImageApi = imageActionsApi;
        }
    }

    /**
     * Callbacks the Ui can generate. This is the only way for a Ui to call methods on the
     * controller.
     */
    public interface OverlayUICallbacksGo extends OverlayUICallbacks {
        /** User has requested to listen to the current content read aloud */
        void onListen();

        /** User has requested a translation of the current content */
        void onTranslate();

        /** User has requested a visual search of the current content */
        void onSearch();
    }
}
