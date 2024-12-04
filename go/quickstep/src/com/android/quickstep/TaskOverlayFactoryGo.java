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

import static java.lang.annotation.RetentionPolicy.SOURCE;

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
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.views.ArrowTipView;
import com.android.quickstep.util.AssistContentRequester;
import com.android.quickstep.util.RecentsOrientedState;
import com.android.quickstep.views.GoOverviewActionsView;
import com.android.quickstep.views.TaskView.TaskContainer;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;

import java.lang.annotation.Retention;

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
    public static final String NIU_ACTIONS_CONFIRMED = "launcher_go.niu_actions_confirmed";
    private static final String ASSIST_SETTINGS_ARGS_BUNDLE = ":settings:show_fragment_args";
    private static final String ASSIST_SETTINGS_ARGS_KEY = ":settings:fragment_args_key";
    private static final String ASSIST_SETTINGS_PREFERENCE_KEY = "default_assist";
    private static final String TAG = "TaskOverlayFactoryGo";

    public static final String LISTEN_TOOL_TIP_SEEN = "launcher.go_listen_tip_seen";
    public static final String TRANSLATE_TOOL_TIP_SEEN = "launcher.go_translate_tip_seen";

    @Retention(SOURCE)
    @IntDef({PRIVACY_CONFIRMATION, ASSISTANT_NOT_SELECTED, ASSISTANT_NOT_SUPPORTED})
    @VisibleForTesting
    public @interface DialogType{}
    public static final int PRIVACY_CONFIRMATION = 0;
    public static final int ASSISTANT_NOT_SELECTED = 1;
    public static final int ASSISTANT_NOT_SUPPORTED = 2;

    private AssistContentRequester mContentRequester;

    public TaskOverlayFactoryGo(Context context) {
        mContentRequester = new AssistContentRequester(context);
    }

    /**
     * Create a new overlay instance for the given View
     */
    public TaskOverlayGo createOverlay(TaskContainer taskContainer) {
        return new TaskOverlayGo(taskContainer, mContentRequester);
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
        private OverlayDialogGo mDialog;
        private ArrowTipView mArrowTipView;

        private TaskOverlayGo(TaskContainer taskContainer,
                AssistContentRequester assistContentRequester) {
            super(taskContainer);
            mFactoryContentRequester = assistContentRequester;
            mSharedPreferences = LauncherPrefs.getPrefs(mApplicationContext);
        }

        /**
         * Called when the current task is interactive for the user
         */
        @Override
        public void initOverlay(Task task, ThumbnailData thumbnail, Matrix matrix,
                boolean rotated) {
            if (mDialog != null && mDialog.isShowing()) {
                // Redraw the dialog in case the layout changed
                mDialog.dismiss();
                showDialog(mDialog.getAction(), mDialog.getType());
            }

            getActionsView().updateDisabledFlags(DISABLED_NO_THUMBNAIL, thumbnail == null);
            if (thumbnail == null) {
                return;
            }

            getActionsView().updateDisabledFlags(DISABLED_ROTATED, rotated);
            // Disable Overview Actions for Work Profile apps
            boolean isManagedProfileTask =
                    UserManager.get(mApplicationContext).isManagedProfile(task.key.userId);
            boolean isAllowedByPolicy = mTaskContainer.getThumbnailViewDeprecated().isRealSnapshot()
                    && !isManagedProfileTask;
            getActionsView().setCallbacks(new OverlayUICallbacksGoImpl(isAllowedByPolicy, task));
            mTaskPackageName = task.key.getPackageName();
            mSharedPreferences = LauncherPrefs.getPrefs(mApplicationContext);
            checkSettings();

            if (!mAssistStructurePermitted || !mAssistScreenshotPermitted
                    || TextUtils.isEmpty(mNIUPackageName)) {
                return;
            }

            int taskId = task.key.id;
            mFactoryContentRequester.requestAssistContent(taskId, this::onAssistContentReceived);

            RecentsOrientedState orientedState = mTaskContainer.getTaskView().getOrientedState();
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
            if (mDialog != null && mDialog.isShowing()) {
                mDialog.dismiss();
            }
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
            if (TextUtils.isEmpty(mNIUPackageName)) {
                showDialog(actionType, ASSISTANT_NOT_SELECTED);
                return;
            }

            if (!mSharedPreferences.getBoolean(NIU_ACTIONS_CONFIRMED, false)) {
                showDialog(actionType, PRIVACY_CONFIRMATION);
                return;
            }

            Intent intent = createNIUIntent(actionType);
            // Only add and send the image if the appropriate permissions are held
            if (mAssistStructurePermitted && mAssistScreenshotPermitted) {
                mImageApi.shareAsDataWithExplicitIntent(/* crop */ null, intent,
                        () -> showDialog(actionType, ASSISTANT_NOT_SUPPORTED));
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
                    showDialog(actionType, ASSISTANT_NOT_SUPPORTED);
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
            if (!TextUtils.isEmpty(assistantPackage)) {
                mNIUPackageName = assistantPackage.split("/", 2)[0];
            } else {
                mNIUPackageName = "";
            }
        }

        protected class OverlayUICallbacksGoImpl extends OverlayUICallbacksImpl
                implements OverlayUICallbacksGo {
            public OverlayUICallbacksGoImpl(boolean isAllowedByPolicy, Task task) {
                super(isAllowedByPolicy, task);
            }

            @SuppressLint("NewApi")
            public void onListen() {
                if (mIsAllowedByPolicy) {
                    endLiveTileMode(() -> sendNIUIntent(ACTION_LISTEN));
                } else {
                    showBlockedByPolicyMessage();
                }
            }

            @SuppressLint("NewApi")
            public void onTranslate() {
                if (mIsAllowedByPolicy) {
                    endLiveTileMode(() -> sendNIUIntent(ACTION_TRANSLATE));
                } else {
                    showBlockedByPolicyMessage();
                }
            }

            @SuppressLint("NewApi")
            public void onSearch() {
                if (mIsAllowedByPolicy) {
                    endLiveTileMode(() -> sendNIUIntent(ACTION_SEARCH));
                } else {
                    showBlockedByPolicyMessage();
                }
            }
        }

        @VisibleForTesting
        public void setImageActionsAPI(ImageActionsApi imageActionsApi) {
            mImageApi = imageActionsApi;
        }

        private void showDialog(String action, @DialogType int type) {
            switch (type) {
                case PRIVACY_CONFIRMATION:
                    showDialog(action, PRIVACY_CONFIRMATION,
                            R.string.niu_actions_confirmation_title,
                            R.string.niu_actions_confirmation_text, R.string.dialog_cancel,
                            this::onDialogClickCancel, R.string.dialog_acknowledge,
                            this::onNiuActionsConfirmationAccept);
                    break;
                case ASSISTANT_NOT_SELECTED:
                    showDialog(action, ASSISTANT_NOT_SELECTED,
                            R.string.assistant_not_selected_title,
                            R.string.assistant_not_selected_text, R.string.dialog_cancel,
                            this::onDialogClickCancel, R.string.dialog_settings,
                            this::onDialogClickSettings);
                    break;
                case ASSISTANT_NOT_SUPPORTED:
                    showDialog(action, ASSISTANT_NOT_SUPPORTED,
                            R.string.assistant_not_supported_title,
                            R.string.assistant_not_supported_text, R.string.dialog_cancel,
                            this::onDialogClickCancel, R.string.dialog_settings,
                            this::onDialogClickSettings);
                    break;
                default:
                    Log.e(TAG, "Unexpected dialog type");
            }
        }

        private void showDialog(String action, @DialogType int type, int titleTextID,
                                int bodyTextID, int button1TextID,
                                View.OnClickListener button1Callback, int button2TextID,
                                View.OnClickListener button2Callback) {
            BaseActivity activity = BaseActivity.fromContext(getActionsView().getContext());
            LayoutInflater inflater = LayoutInflater.from(activity);
            View view = inflater.inflate(R.layout.niu_actions_dialog, /* root */ null);

            TextView dialogTitle = view.findViewById(R.id.niu_actions_dialog_header);
            dialogTitle.setText(titleTextID);

            TextView dialogBody = view.findViewById(R.id.niu_actions_dialog_description);
            dialogBody.setText(bodyTextID);

            Button button1 = view.findViewById(R.id.niu_actions_dialog_button_1);
            button1.setText(button1TextID);
            button1.setOnClickListener(button1Callback);

            Button button2 = view.findViewById(R.id.niu_actions_dialog_button_2);
            button2.setText(button2TextID);
            button2.setOnClickListener(button2Callback);

            mDialog = new OverlayDialogGo(activity, type, action);
            mDialog.setView(view);
            mDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            mDialog.show();
        }

        private void onNiuActionsConfirmationAccept(View v) {
            mDialog.dismiss();
            mSharedPreferences.edit().putBoolean(NIU_ACTIONS_CONFIRMED, true).apply();
            sendNIUIntent(mDialog.getAction());
        }

        private void onDialogClickCancel(View v) {
            mDialog.cancel();
        }

        @VisibleForTesting
        public OverlayDialogGo getDialog() {
            return mDialog;
        }

        private void onDialogClickSettings(View v) {
            mDialog.dismiss();

            Bundle fragmentArgs = new Bundle();
            fragmentArgs.putString(ASSIST_SETTINGS_ARGS_KEY, ASSIST_SETTINGS_PREFERENCE_KEY);
            Intent intent = new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(ASSIST_SETTINGS_ARGS_BUNDLE, fragmentArgs);
            try {
                mApplicationContext.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "No activity found to receive assistant settings intent");
            }
        }

        /**
         * Checks and Shows the tooltip if they are not seen by user
         * Order of tooltips are translate and then listen
         */
        private void showTooltipsIfUnseen() {
            if (mArrowTipView != null && mArrowTipView.isOpen()) {
                return;
            }
            if (!mSharedPreferences.getBoolean(TRANSLATE_TOOL_TIP_SEEN, false)) {
                mArrowTipView = ((GoOverviewActionsView) getActionsView()).showTranslateToolTip();
                mSharedPreferences.edit().putBoolean(TRANSLATE_TOOL_TIP_SEEN, true).apply();
            } else if (!mSharedPreferences.getBoolean(LISTEN_TOOL_TIP_SEEN, false)) {
                mArrowTipView = ((GoOverviewActionsView) getActionsView()).showListenToolTip();
                mSharedPreferences.edit().putBoolean(LISTEN_TOOL_TIP_SEEN, true).apply();
            }
        }
    }

    /**
     * Basic modal dialog for various user prompts
     */
    @VisibleForTesting
    public static final class OverlayDialogGo extends AlertDialog {
        private final String mAction;
        private final @DialogType int mType;

        OverlayDialogGo(Context context, @DialogType int type, String action) {
            super(context);
            mType = type;
            mAction = action;
        }

        public String getAction() {
            return mAction;
        }
        public @DialogType int getType() {
            return mType;
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
