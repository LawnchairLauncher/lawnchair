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

package com.android.launcher3.model;

import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.app.admin.ManagedSubscriptionsPolicy;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;

import java.util.function.Supplier;

/**
 *
 * Cache for the device policy strings used in Launcher.
 */
public class StringCache {

    private static final String PREFIX = "Launcher.";

    /**
     * Work folder name.
     */
    public static final String WORK_FOLDER_NAME = PREFIX + "WORK_FOLDER_NAME";

    /**
     * User on-boarding title for work profile apps.
     */
    private static final String WORK_PROFILE_EDU = PREFIX + "WORK_PROFILE_EDU";

    /**
     * Action label to finish work profile edu.
     */
    private static final String WORK_PROFILE_EDU_ACCEPT = PREFIX + "WORK_PROFILE_EDU_ACCEPT";

    /**
     * Title shown when user opens work apps tab while work profile is paused.
     */
    private static final String WORK_PROFILE_PAUSED_TITLE =
            PREFIX + "WORK_PROFILE_PAUSED_TITLE";

    /**
     * Description shown when user opens work apps tab while work profile is paused.
     */
    private static final String WORK_PROFILE_PAUSED_DESCRIPTION =
            PREFIX + "WORK_PROFILE_PAUSED_DESCRIPTION";

    /**
     * Shown on the button to pause work profile.
     */
    private static final String WORK_PROFILE_PAUSE_BUTTON =
            PREFIX + "WORK_PROFILE_PAUSE_BUTTON";

    /**
     * Shown on the button to enable work profile.
     */
    private static final String WORK_PROFILE_ENABLE_BUTTON =
            PREFIX + "WORK_PROFILE_ENABLE_BUTTON";

    /**
     * Label on launcher tab to indicate work apps.
     */
    private static final String ALL_APPS_WORK_TAB = PREFIX + "ALL_APPS_WORK_TAB";

    /**
     * Label on launcher tab to indicate personal apps.
     */
    private static final String ALL_APPS_PERSONAL_TAB = PREFIX + "ALL_APPS_PERSONAL_TAB";

    /**
     * Accessibility description for launcher tab to indicate work apps.
     */
    private static final String ALL_APPS_WORK_TAB_ACCESSIBILITY =
            PREFIX + "ALL_APPS_WORK_TAB_ACCESSIBILITY";

    /**
     * Accessibility description for launcher tab to indicate personal apps.
     */
    private static final String ALL_APPS_PERSONAL_TAB_ACCESSIBILITY =
            PREFIX + "ALL_APPS_PERSONAL_TAB_ACCESSIBILITY";

    /**
     * Label on widget tab to indicate work app widgets.
     */
    private static final String WIDGETS_WORK_TAB = PREFIX + "WIDGETS_WORK_TAB";

    /**
     * Label on widget tab to indicate personal app widgets.
     */
    private static final String WIDGETS_PERSONAL_TAB = PREFIX + "WIDGETS_PERSONAL_TAB";

    /**
     * Message shown when a feature is disabled by the admin (e.g. changing wallpaper).
     */
    private static final String DISABLED_BY_ADMIN_MESSAGE =
            PREFIX + "DISABLED_BY_ADMIN_MESSAGE";

    /**
     * User on-boarding title for work profile apps.
     */
    public String workProfileEdu;

    /**
     * Action label to finish work profile edu.
     */
    public String workProfileEduAccept;

    /**
     * Title shown when user opens work apps tab while work profile is paused.
     */
    public String workProfilePausedTitle;

    /**
     * Description shown when user opens work apps tab while work profile is paused.
     */
    public String workProfilePausedDescription;

    /**
     * Shown on the button to pause work profile.
     */
    public String workProfilePauseButton;

    /**
     * Shown on the button to enable work profile.
     */
    public String workProfileEnableButton;

    /**
     * Label on launcher tab to indicate work apps.
     */
    public String allAppsWorkTab;

    /**
     * Label on launcher tab to indicate personal apps.
     */
    public String allAppsPersonalTab;

    /**
     * Accessibility description for launcher tab to indicate work apps.
     */
    public String allAppsWorkTabAccessibility;

    /**
     * Accessibility description for launcher tab to indicate personal apps.
     */
    public String allAppsPersonalTabAccessibility;

    /**
     * Work folder name.
     */
    public String workFolderName;

    /**
     * Label on widget tab to indicate work app widgets.
     */
    public String widgetsWorkTab;

    /**
     * Label on widget tab to indicate personal app widgets.
     */
    public String widgetsPersonalTab;

    /**
     * Message shown when a feature is disabled by the admin (e.g. changing wallpaper).
     */
    public String disabledByAdminMessage;

    /**
     * Sets the default values for the strings.
     */
    public void loadStrings(Context context) {
        workProfileEdu = getEnterpriseString(
                context, WORK_PROFILE_EDU, R.string.work_profile_edu_work_apps);
        workProfileEduAccept = getEnterpriseString(
                context, WORK_PROFILE_EDU_ACCEPT, R.string.work_profile_edu_accept);
        workProfilePausedTitle = getEnterpriseString(
                context, WORK_PROFILE_PAUSED_TITLE, R.string.work_apps_paused_title);
        workProfilePausedDescription = getEnterpriseString(
                context,
                WORK_PROFILE_PAUSED_DESCRIPTION,
                () -> getDefaultWorkProfilePausedDescriptionString(context));
        workProfilePauseButton = getEnterpriseString(
                context, WORK_PROFILE_PAUSE_BUTTON, R.string.work_apps_pause_btn_text);
        workProfileEnableButton = getEnterpriseString(
                context, WORK_PROFILE_ENABLE_BUTTON, R.string.work_apps_enable_btn_text);
        allAppsWorkTab = getEnterpriseString(
                context, ALL_APPS_WORK_TAB, R.string.all_apps_work_tab);
        allAppsPersonalTab = getEnterpriseString(
                context, ALL_APPS_PERSONAL_TAB, R.string.all_apps_personal_tab);
        allAppsWorkTabAccessibility = getEnterpriseString(
                context, ALL_APPS_WORK_TAB_ACCESSIBILITY, R.string.all_apps_button_work_label);
        allAppsPersonalTabAccessibility = getEnterpriseString(
                context, ALL_APPS_PERSONAL_TAB_ACCESSIBILITY,
                R.string.all_apps_button_personal_label);
        workFolderName = getEnterpriseString(
                context, WORK_FOLDER_NAME, R.string.work_folder_name);
        widgetsWorkTab = getEnterpriseString(
                context, WIDGETS_WORK_TAB, R.string.widgets_full_sheet_work_tab);
        widgetsPersonalTab = getEnterpriseString(
                context, WIDGETS_PERSONAL_TAB, R.string.widgets_full_sheet_personal_tab);
        disabledByAdminMessage = getEnterpriseString(
                context, DISABLED_BY_ADMIN_MESSAGE, R.string.msg_disabled_by_admin);
    }

    private String getDefaultWorkProfilePausedDescriptionString(Context context) {
        if (Utilities.ATLEAST_U) {
            DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
            boolean telephonyIsUnavailable =
                    dpm.getManagedSubscriptionsPolicy().getPolicyType()
                            == ManagedSubscriptionsPolicy.TYPE_ALL_MANAGED_SUBSCRIPTIONS;
            return telephonyIsUnavailable
                    ? context.getString(R.string.work_apps_paused_telephony_unavailable_body)
                    : context.getString(R.string.work_apps_paused_info_body);
        }
        return context.getString(R.string.work_apps_paused_body);
    }

    @SuppressLint("NewApi")
    private String getEnterpriseString(
            Context context, String updatableStringId, int defaultStringId) {
        return getEnterpriseString(
                context,
                updatableStringId,
                () -> context.getString(defaultStringId));
    }

    @SuppressLint("NewApi")
    private String getEnterpriseString(
            Context context, String updateableStringId, Supplier<String> defaultStringSupplier) {
        return Utilities.ATLEAST_T
                ? getUpdatableEnterpriseString(context, updateableStringId, defaultStringSupplier)
                : defaultStringSupplier.get();
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private String getUpdatableEnterpriseString(
            Context context, String updatableStringId, Supplier<String> defaultStringSupplier) {
        DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        return dpm.getResources().getString(updatableStringId, defaultStringSupplier);
    }

    @Override
    public StringCache clone() {
        StringCache clone = new StringCache();
        clone.workProfileEdu = this.workProfileEdu;
        clone.workProfileEduAccept = this.workProfileEduAccept;
        clone.workProfilePausedTitle = this.workProfilePausedTitle;
        clone.workProfilePausedDescription = this.workProfilePausedDescription;
        clone.workProfilePauseButton = this.workProfilePauseButton;
        clone.workProfileEnableButton = this.workProfileEnableButton;
        clone.allAppsWorkTab = this.allAppsWorkTab;
        clone.allAppsPersonalTab = this.allAppsPersonalTab;
        clone.allAppsWorkTabAccessibility = this.allAppsWorkTabAccessibility;
        clone.allAppsPersonalTabAccessibility = this.allAppsPersonalTabAccessibility;
        clone.workFolderName = this.workFolderName;
        clone.widgetsWorkTab = this.widgetsWorkTab;
        clone.widgetsPersonalTab = this.widgetsPersonalTab;
        clone.disabledByAdminMessage = this.disabledByAdminMessage;
        return clone;
    }
}
