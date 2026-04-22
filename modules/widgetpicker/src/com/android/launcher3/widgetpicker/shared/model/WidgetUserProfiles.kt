/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.widgetpicker.shared.model

/**
 * Holds data about all the available user profiles on device that are supported in the widget
 * picker.
 */
data class WidgetUserProfiles(val personal: WidgetUserProfile, val work: WidgetUserProfile?)

/**
 * Data about a specific user profile that is supported in the widget picker.
 *
 * @property type profile type of the user e.g. personal / work etc.
 * @property label a user friendly string representing the profile e.g. Personal / Work; when a work
 *   profile is setup, this may be set by the enterprise.
 * @property paused indicates if the user profile is currently paused
 * @property pausedProfileMessage optional message that can be shown to the user when a profile is
 *   [paused] (instead of listing widgets for the profile); this may be set by the enterprise; if
 *   null and the profile is [paused] a default message will be displayed.
 */
data class WidgetUserProfile(
    val type: WidgetUserProfileType,
    val label: String,
    val paused: Boolean = false,
    val pausedProfileMessage: String? = null,
)

/** Represents types of user profiles that are supported in the widget picker. */
enum class WidgetUserProfileType {
    PERSONAL,
    WORK,
}
