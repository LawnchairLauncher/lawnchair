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

import android.content.Context;

import com.android.launcher3.R;

/**
 *
 * Cache for some of the string used in Launcher.
 */
public class StringCache {

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
    public void loadDefaultStrings(Context context) {
        workProfileEdu = context.getString(R.string.work_profile_edu_work_apps);
        workProfileEduAccept = context.getString(R.string.work_profile_edu_accept);
        workProfilePausedTitle = context.getString(R.string.work_apps_paused_title);
        workProfilePausedDescription = context.getString(R.string.work_apps_paused_body);
        workProfilePauseButton = context.getString(R.string.work_apps_pause_btn_text);
        workProfileEnableButton = context.getString(R.string.work_apps_enable_btn_text);
        allAppsWorkTab = context.getString(R.string.all_apps_work_tab);
        allAppsPersonalTab = context.getString(R.string.all_apps_personal_tab);
        allAppsWorkTabAccessibility = context.getString(R.string.all_apps_button_work_label);
        allAppsPersonalTabAccessibility = context.getString(
                R.string.all_apps_button_personal_label);
        workFolderName = context.getString(R.string.work_folder_name);
        widgetsWorkTab = context.getString(R.string.widgets_full_sheet_work_tab);
        widgetsPersonalTab = context.getString(R.string.widgets_full_sheet_personal_tab);
        disabledByAdminMessage = context.getString(R.string.msg_disabled_by_admin);
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
