/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.compatui;

import android.annotation.NonNull;
import android.app.TaskInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.DeviceConfig;

import com.android.wm.shell.R;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.dagger.WMSingleton;
import com.android.wm.shell.shared.annotations.ShellMainThread;

import javax.inject.Inject;

/**
 * Configuration flags for the CompatUX implementation
 */
@WMSingleton
public class CompatUIConfiguration implements DeviceConfig.OnPropertiesChangedListener {

    private static final String KEY_ENABLE_LETTERBOX_RESTART_DIALOG =
            "enable_letterbox_restart_confirmation_dialog";

    private static final String KEY_ENABLE_LETTERBOX_REACHABILITY_EDUCATION =
            "enable_letterbox_education_for_reachability";

    private static final boolean DEFAULT_VALUE_ENABLE_LETTERBOX_RESTART_DIALOG = true;

    private static final boolean DEFAULT_VALUE_ENABLE_LETTERBOX_REACHABILITY_EDUCATION = true;

    /**
     * The name of the {@link SharedPreferences} that holds information about compat ui.
     */
    private static final String COMPAT_UI_SHARED_PREFERENCES = "dont_show_restart_dialog";

    /**
     * The name of the {@link SharedPreferences} that holds which user has seen the Letterbox
     * Education dialog.
     */
    private static final String HAS_SEEN_LETTERBOX_EDUCATION_SHARED_PREFERENCES =
            "has_seen_letterbox_education";

    /**
     * Key prefix for the {@link SharedPreferences} entries related to the horizontal
     * reachability education.
     */
    private static final String HAS_SEEN_HORIZONTAL_REACHABILITY_EDUCATION_KEY_PREFIX =
            "has_seen_horizontal_reachability_education";

    /**
     * Key prefix for the {@link SharedPreferences} entries related to the vertical reachability
     * education.
     */
    private static final String HAS_SEEN_VERTICAL_REACHABILITY_EDUCATION_KEY_PREFIX =
            "has_seen_vertical_reachability_education";

    private static final int MAX_PERCENTAGE_VAL = 100;

    /**
     * The {@link SharedPreferences} instance for the restart dialog and the reachability
     * education.
     */
    private final SharedPreferences mCompatUISharedPreferences;

    /**
     * The {@link SharedPreferences} instance for the letterbox education dialog.
     */
    private final SharedPreferences mLetterboxEduSharedPreferences;

    /**
     * The minimum tolerance of the percentage of activity bounds within its task to hide
     * size compat restart button.
     */
    private final int mHideSizeCompatRestartButtonTolerance;

    // Whether the extended restart dialog is enabled
    private boolean mIsRestartDialogEnabled;

    // Whether the additional education about reachability is enabled
    private boolean mIsReachabilityEducationEnabled;

    // Whether the extended restart dialog is enabled
    private boolean mIsRestartDialogOverrideEnabled;

    // Whether the additional education about reachability is enabled
    private boolean mIsReachabilityEducationOverrideEnabled;

    // Whether the extended restart dialog is allowed from backend
    private boolean mIsLetterboxRestartDialogAllowed;

    // Whether the additional education about reachability is allowed from backend
    private boolean mIsLetterboxReachabilityEducationAllowed;

    @Inject
    public CompatUIConfiguration(Context context, @ShellMainThread ShellExecutor mainExecutor) {
        mIsRestartDialogEnabled = context.getResources().getBoolean(
                R.bool.config_letterboxIsRestartDialogEnabled);
        mIsReachabilityEducationEnabled = context.getResources().getBoolean(
                R.bool.config_letterboxIsReachabilityEducationEnabled);
        final int tolerance = context.getResources().getInteger(
                R.integer.config_letterboxRestartButtonHideTolerance);
        mHideSizeCompatRestartButtonTolerance = getHideSizeCompatRestartButtonTolerance(tolerance);
        mIsLetterboxRestartDialogAllowed = DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_WINDOW_MANAGER, KEY_ENABLE_LETTERBOX_RESTART_DIALOG,
                DEFAULT_VALUE_ENABLE_LETTERBOX_RESTART_DIALOG);
        mIsLetterboxReachabilityEducationAllowed = DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_WINDOW_MANAGER, KEY_ENABLE_LETTERBOX_REACHABILITY_EDUCATION,
                DEFAULT_VALUE_ENABLE_LETTERBOX_REACHABILITY_EDUCATION);
        DeviceConfig.addOnPropertiesChangedListener(DeviceConfig.NAMESPACE_APP_COMPAT, mainExecutor,
                this);
        mCompatUISharedPreferences = context.getSharedPreferences(getCompatUISharedPreferenceName(),
                Context.MODE_PRIVATE);
        mLetterboxEduSharedPreferences = context.getSharedPreferences(
                getHasSeenLetterboxEducationSharedPreferencedName(), Context.MODE_PRIVATE);
    }

    /**
     * @return {@value true} if the restart dialog is enabled.
     */
    boolean isRestartDialogEnabled() {
        return mIsRestartDialogOverrideEnabled || (mIsRestartDialogEnabled
                && mIsLetterboxRestartDialogAllowed);
    }

    /**
     * Enables/Disables the restart education dialog
     */
    void setIsRestartDialogOverrideEnabled(boolean enabled) {
        mIsRestartDialogOverrideEnabled = enabled;
    }

    /**
     * Enables/Disables the reachability education
     */
    void setIsReachabilityEducationOverrideEnabled(boolean enabled) {
        mIsReachabilityEducationOverrideEnabled = enabled;
    }

    void setDontShowRestartDialogAgain(TaskInfo taskInfo) {
        mCompatUISharedPreferences.edit().putBoolean(
                dontShowAgainRestartKey(taskInfo.userId, taskInfo.topActivity.getPackageName()),
                true).apply();
    }

    boolean shouldShowRestartDialogAgain(TaskInfo taskInfo) {
        return !mCompatUISharedPreferences.getBoolean(dontShowAgainRestartKey(taskInfo.userId,
                taskInfo.topActivity.getPackageName()), /* default= */ false);
    }

    void setUserHasSeenHorizontalReachabilityEducation(TaskInfo taskInfo) {
        mCompatUISharedPreferences.edit().putBoolean(
                hasSeenHorizontalReachabilityEduKey(taskInfo.userId), true).apply();
    }

    void setUserHasSeenVerticalReachabilityEducation(TaskInfo taskInfo) {
        mCompatUISharedPreferences.edit().putBoolean(
                hasSeenVerticalReachabilityEduKey(taskInfo.userId), true).apply();
    }

    boolean hasSeenHorizontalReachabilityEducation(@NonNull TaskInfo taskInfo) {
        return mCompatUISharedPreferences.getBoolean(
                hasSeenHorizontalReachabilityEduKey(taskInfo.userId), /* default= */false);
    }

    boolean hasSeenVerticalReachabilityEducation(@NonNull TaskInfo taskInfo) {
        return mCompatUISharedPreferences.getBoolean(
                hasSeenVerticalReachabilityEduKey(taskInfo.userId), /* default= */false);
    }

    boolean shouldShowReachabilityEducation(@NonNull TaskInfo taskInfo) {
        return isReachabilityEducationEnabled()
                && (!hasSeenHorizontalReachabilityEducation(taskInfo)
                    || !hasSeenVerticalReachabilityEducation(taskInfo));
    }

    int getHideSizeCompatRestartButtonTolerance() {
        return mHideSizeCompatRestartButtonTolerance;
    }

    int getDefaultHideRestartButtonTolerance() {
        return MAX_PERCENTAGE_VAL;
    }

    boolean getHasSeenLetterboxEducation(int userId) {
        return mLetterboxEduSharedPreferences
                .getBoolean(dontShowLetterboxEduKey(userId), /* default= */ false);
    }

    void setSeenLetterboxEducation(int userId) {
        mLetterboxEduSharedPreferences.edit().putBoolean(dontShowLetterboxEduKey(userId),
                true).apply();
    }

    protected String getCompatUISharedPreferenceName() {
        return COMPAT_UI_SHARED_PREFERENCES;
    }

    protected String getHasSeenLetterboxEducationSharedPreferencedName() {
        return HAS_SEEN_LETTERBOX_EDUCATION_SHARED_PREFERENCES;
    }

    /**
     * Updates the {@link DeviceConfig} state for the CompatUI
     * @param properties Contains the complete collection of properties which have changed for a
     *                   single namespace. This includes only those which were added, updated,
     */
    @Override
    public void onPropertiesChanged(@NonNull DeviceConfig.Properties properties) {
        if (properties.getKeyset().contains(KEY_ENABLE_LETTERBOX_RESTART_DIALOG)) {
            mIsLetterboxRestartDialogAllowed = DeviceConfig.getBoolean(
                    DeviceConfig.NAMESPACE_WINDOW_MANAGER, KEY_ENABLE_LETTERBOX_RESTART_DIALOG,
                    DEFAULT_VALUE_ENABLE_LETTERBOX_RESTART_DIALOG);
        }
        // TODO(b/263349751): Update flag and default value to true
        if (properties.getKeyset().contains(KEY_ENABLE_LETTERBOX_REACHABILITY_EDUCATION)) {
            mIsLetterboxReachabilityEducationAllowed = DeviceConfig.getBoolean(
                    DeviceConfig.NAMESPACE_WINDOW_MANAGER,
                    KEY_ENABLE_LETTERBOX_REACHABILITY_EDUCATION,
                    DEFAULT_VALUE_ENABLE_LETTERBOX_REACHABILITY_EDUCATION);
        }
    }

    // Returns the minimum tolerance of the percentage of activity bounds within its task to hide
    // size compat restart button. Value lower than 0 or higher than 100 will be ignored.
    // 100 is the default value where the activity has to fit exactly within the task to allow
    // size compat restart button to be hidden. 0 means size compat restart button will always
    // be hidden.
    private int getHideSizeCompatRestartButtonTolerance(int tolerance) {
        return tolerance < 0 || tolerance > MAX_PERCENTAGE_VAL ? MAX_PERCENTAGE_VAL : tolerance;
    }

    private boolean isReachabilityEducationEnabled() {
        return mIsReachabilityEducationOverrideEnabled || (mIsReachabilityEducationEnabled
                && mIsLetterboxReachabilityEducationAllowed);
    }

    private static String hasSeenHorizontalReachabilityEduKey(int userId) {
        return HAS_SEEN_HORIZONTAL_REACHABILITY_EDUCATION_KEY_PREFIX + "@" + userId;
    }

    private static String hasSeenVerticalReachabilityEduKey(int userId) {
        return HAS_SEEN_VERTICAL_REACHABILITY_EDUCATION_KEY_PREFIX + "@" + userId;
    }

    private static String dontShowLetterboxEduKey(int userId) {
        return String.valueOf(userId);
    }

    private String dontShowAgainRestartKey(int userId, String packageName) {
        return packageName + "@" + userId;
    }
}