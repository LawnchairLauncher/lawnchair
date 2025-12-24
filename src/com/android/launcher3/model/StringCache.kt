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
package com.android.launcher3.model

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.app.admin.ManagedSubscriptionsPolicy
import android.content.Context
import com.android.launcher3.R
import com.android.launcher3.Utilities
import java.util.function.Supplier

/** Cache for the device policy strings used in Launcher. */
data class StringCache
private constructor(
    /** User on-boarding title for work profile apps. */
    @JvmField val workProfileEdu: String? = null,

    /** Title shown when user opens work apps tab while work profile is paused. */
    @JvmField val workProfilePausedTitle: String? = null,

    /** Description shown when user opens work apps tab while work profile is paused. */
    @JvmField val workProfilePausedDescription: String? = null,

    /** Shown on the button to pause work profile. */
    @JvmField val workProfilePauseButton: String? = null,

    /** Shown on the button to enable work profile. */
    @JvmField val workProfileEnableButton: String? = null,

    /** Label on launcher tab to indicate work apps. */
    @JvmField val allAppsWorkTab: String? = null,

    /** Label on launcher tab to indicate personal apps. */
    @JvmField val allAppsPersonalTab: String? = null,

    /** Accessibility description for launcher tab to indicate work apps. */
    @JvmField val allAppsWorkTabAccessibility: String? = null,

    /** Accessibility description for launcher tab to indicate personal apps. */
    @JvmField val allAppsPersonalTabAccessibility: String? = null,

    /** Label on widget tab to indicate work app widgets. */
    @JvmField val widgetsWorkTab: String? = null,

    /** Label on widget tab to indicate personal app widgets. */
    @JvmField val widgetsPersonalTab: String? = null,

    /** Message shown when a feature is disabled by the admin (e.g. changing wallpaper). */
    @JvmField val disabledByAdminMessage: String? = null,
) {

    companion object {

        @JvmField val EMPTY = StringCache()

        @JvmStatic
        /** Loads the String cache with system defined default values */
        fun fromContext(context: Context) =
            StringCache(
                workProfileEdu =
                    context.getEnterpriseString(
                        WORK_PROFILE_EDU,
                        R.string.work_profile_edu_work_apps,
                    ),
                workProfilePausedTitle =
                    context.getEnterpriseString(
                        WORK_PROFILE_PAUSED_TITLE,
                        R.string.work_apps_paused_title,
                    ),
                workProfilePausedDescription =
                    context.getEnterpriseString(WORK_PROFILE_PAUSED_DESCRIPTION) {
                        context.getString(
                            when {
                                !Utilities.ATLEAST_U -> R.string.work_apps_paused_body
                                context.dm().managedSubscriptionsPolicy.policyType ==
                                    ManagedSubscriptionsPolicy.TYPE_ALL_MANAGED_SUBSCRIPTIONS ->
                                    R.string.work_apps_paused_telephony_unavailable_body
                                else -> R.string.work_apps_paused_info_body
                            }
                        )
                    },
                workProfilePauseButton =
                    context.getEnterpriseString(
                        WORK_PROFILE_PAUSE_BUTTON,
                        R.string.work_apps_pause_btn_text,
                    ),
                workProfileEnableButton =
                    context.getEnterpriseString(
                        WORK_PROFILE_ENABLE_BUTTON,
                        R.string.work_apps_enable_btn_text,
                    ),
                allAppsWorkTab =
                    context.getEnterpriseString(ALL_APPS_WORK_TAB, R.string.all_apps_work_tab),
                allAppsPersonalTab =
                    context.getEnterpriseString(
                        ALL_APPS_PERSONAL_TAB,
                        R.string.all_apps_personal_tab,
                    ),
                allAppsWorkTabAccessibility =
                    context.getEnterpriseString(
                        ALL_APPS_WORK_TAB_ACCESSIBILITY,
                        R.string.all_apps_button_work_label,
                    ),
                allAppsPersonalTabAccessibility =
                    context.getEnterpriseString(
                        ALL_APPS_PERSONAL_TAB_ACCESSIBILITY,
                        R.string.all_apps_button_personal_label,
                    ),
                widgetsWorkTab =
                    context.getEnterpriseString(
                        WIDGETS_WORK_TAB,
                        R.string.widgets_full_sheet_work_tab,
                    ),
                widgetsPersonalTab =
                    context.getEnterpriseString(
                        WIDGETS_PERSONAL_TAB,
                        R.string.widgets_full_sheet_personal_tab,
                    ),
                disabledByAdminMessage =
                    context.getEnterpriseString(
                        DISABLED_BY_ADMIN_MESSAGE,
                        R.string.msg_disabled_by_admin,
                    ),
            )

        @SuppressLint("NewApi")
        private fun Context.getEnterpriseString(updatableStringId: String, defaultStringId: Int) =
            getEnterpriseString(updatableStringId) { getString(defaultStringId) }

        private fun Context.getEnterpriseString(
            updatableStringId: String,
            defaultSupplier: Supplier<String>,
        ) =
            if (Utilities.ATLEAST_T) dm().resources.getString(updatableStringId, defaultSupplier)
            else defaultSupplier.get()

        private fun Context.dm() = getSystemService(DevicePolicyManager::class.java)!!

        private const val PREFIX = "Launcher."

        /** Work folder name. */
        const val WORK_FOLDER_NAME: String = PREFIX + "WORK_FOLDER_NAME"

        /** User on-boarding title for work profile apps. */
        private const val WORK_PROFILE_EDU = PREFIX + "WORK_PROFILE_EDU"

        /** Title shown when user opens work apps tab while work profile is paused. */
        private const val WORK_PROFILE_PAUSED_TITLE = PREFIX + "WORK_PROFILE_PAUSED_TITLE"

        /** Description shown when user opens work apps tab while work profile is paused. */
        private const val WORK_PROFILE_PAUSED_DESCRIPTION =
            PREFIX + "WORK_PROFILE_PAUSED_DESCRIPTION"

        /** Shown on the button to pause work profile. */
        private const val WORK_PROFILE_PAUSE_BUTTON = PREFIX + "WORK_PROFILE_PAUSE_BUTTON"

        /** Shown on the button to enable work profile. */
        private const val WORK_PROFILE_ENABLE_BUTTON = PREFIX + "WORK_PROFILE_ENABLE_BUTTON"

        /** Label on launcher tab to indicate work apps. */
        private const val ALL_APPS_WORK_TAB = PREFIX + "ALL_APPS_WORK_TAB"

        /** Label on launcher tab to indicate personal apps. */
        private const val ALL_APPS_PERSONAL_TAB = PREFIX + "ALL_APPS_PERSONAL_TAB"

        /** Accessibility description for launcher tab to indicate work apps. */
        private const val ALL_APPS_WORK_TAB_ACCESSIBILITY =
            PREFIX + "ALL_APPS_WORK_TAB_ACCESSIBILITY"

        /** Accessibility description for launcher tab to indicate personal apps. */
        private const val ALL_APPS_PERSONAL_TAB_ACCESSIBILITY =
            PREFIX + "ALL_APPS_PERSONAL_TAB_ACCESSIBILITY"

        /** Label on widget tab to indicate work app widgets. */
        private const val WIDGETS_WORK_TAB = PREFIX + "WIDGETS_WORK_TAB"

        /** Label on widget tab to indicate personal app widgets. */
        private const val WIDGETS_PERSONAL_TAB = PREFIX + "WIDGETS_PERSONAL_TAB"

        /** Message shown when a feature is disabled by the admin (e.g. changing wallpaper). */
        private const val DISABLED_BY_ADMIN_MESSAGE = PREFIX + "DISABLED_BY_ADMIN_MESSAGE"
    }
}
