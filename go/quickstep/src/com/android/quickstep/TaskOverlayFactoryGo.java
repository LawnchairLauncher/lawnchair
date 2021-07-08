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

import static android.view.Surface.ROTATION_0;

import static com.android.quickstep.views.OverviewActionsView.DISABLED_NO_THUMBNAIL;
import static com.android.quickstep.views.OverviewActionsView.DISABLED_ROTATED;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.assist.AssistContent;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.SystemClock;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

import androidx.annotation.VisibleForTesting;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.quickstep.util.AssistContentRequester;
import com.android.quickstep.util.RecentsOrientedState;
import com.android.quickstep.views.GoOverviewActionsView;
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
    private static final String NIU_ACTIONS_CONFIRMED = "launcher_go.niu_actions_confirmed";
    private static final String TAG = "TaskOverlayFactoryGo";

    public static final String LISTEN_TOOL_TIP_SEEN = "launcher.go_listen_tip_seen";
    public static final String TRANSLATE_TOOL_TIP_SEEN = "launcher.go_translate_tip_seen";

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
    public static final class TaskOverlayGo<T extends GoOverviewActionsView> extends TaskOverlay {
        private String mNIUPackageName;
        private String mTaskPackageName;
        private String mWebUrl;
        private boolean mAssistStructurePermitted;
        private boolean mAssistScreenshotPermitted;
        private AssistContentRequester mFactoryContentRequester;
        private SharedPreferences mSharedPreferences;
        private String mPreviousAction;
        private AlertDialog mConfirmationDialog;

        private TaskOverlayGo(TaskThumbnailView taskThumbnailView,
                AssistContentRequester assistContentRequester) {
            super(taskThumbnailView);
            mFactoryContentRequester = assistContentRequester;
            mSharedPreferences = Utilities.getPrefs(mApplicationContext);
        }

        /**
         * Called when the current task is interactive for the user
         */
        @Override
        public void initOverlay(Task task, ThumbnailData thumbnail, Matrix matrix,
                boolean rotated) {
            if (mConfirmationDialog != null && mConfirmationDialog.isShowing()) {
                // Redraw the dialog in case the layout changed
                mConfirmationDialog.dismiss();
                showConfirmationDialog();
            }

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
            mSharedPreferences = Utilities.getPrefs(mApplicationContext);

            if (!mAssistStructurePermitted || !mAssistScreenshotPermitted) {
                return;
            }

            int taskId = task.key.id;
            mFactoryContentRequester.requestAssistContent(taskId, this::onAssistContentReceived);

            RecentsOrientedState orientedState =
                    mThumbnailView.getTaskView().getRecentsView().getPagedViewOrientedState();
            boolean isInLandscape = orientedState.getDisplayRotation() != ROTATION_0;

            // show tooltips in portrait mode only
            // TODO: remove If check once b/183714277 is fixed
            if (!isInLandscape) {
                new Handler().post(() -> {
                    showTooltipsIfUnseen();
                });
            }
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

        @Override
        public void updateOrientationState(RecentsOrientedState state) {
            super.updateOrientationState(state);
            ((GoOverviewActionsView) getActionsView()).updateOrientationState(state);
        }

        /**
         * Creates and sends an Intent corresponding to the button that was clicked
         */
        private void sendNIUIntent(String actionType) {
            if (!mSharedPreferences.getBoolean(NIU_ACTIONS_CONFIRMED, false)) {
                mPreviousAction = actionType;
                showConfirmationDialog();
                return;
            }

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

        private void showConfirmationDialog() {
            BaseDraggingActivity activity = BaseActivity.fromContext(getActionsView().getContext());
            LayoutInflater inflater = LayoutInflater.from(activity);
            View view = inflater.inflate(R.layout.niu_actions_confirmation_dialog, /* root */ null);

            Button acceptButton = view.findViewById(R.id.niu_actions_confirmation_accept);
            acceptButton.setOnClickListener(this::onNiuActionsConfirmationAccept);

            Button rejectButton = view.findViewById(R.id.niu_actions_confirmation_reject);
            rejectButton.setOnClickListener(this::onNiuActionsConfirmationReject);

            mConfirmationDialog = new AlertDialog.Builder(activity)
                    .setView(view)
                    .create();
            mConfirmationDialog.getWindow()
                    .setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            mConfirmationDialog.show();
        }

        private void onNiuActionsConfirmationAccept(View v) {
            mConfirmationDialog.dismiss();
            mSharedPreferences.edit().putBoolean(NIU_ACTIONS_CONFIRMED, true).apply();
            sendNIUIntent(mPreviousAction);
        }

        private void onNiuActionsConfirmationReject(View v) {
            mConfirmationDialog.cancel();
        }

        /**
         * Checks and Shows the tooltip if they are not seen by user
         * Order of tooltips are translate and then listen
         */
        private void showTooltipsIfUnseen() {
            if (!mSharedPreferences.getBoolean(TRANSLATE_TOOL_TIP_SEEN, false)) {
                ((GoOverviewActionsView) getActionsView()).showTranslateToolTip();
                mSharedPreferences.edit().putBoolean(TRANSLATE_TOOL_TIP_SEEN, true).apply();
            } else if (!mSharedPreferences.getBoolean(LISTEN_TOOL_TIP_SEEN, false)) {
                ((GoOverviewActionsView) getActionsView()).showListenToolTip();
                mSharedPreferences.edit().putBoolean(LISTEN_TOOL_TIP_SEEN, true).apply();
            }
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
