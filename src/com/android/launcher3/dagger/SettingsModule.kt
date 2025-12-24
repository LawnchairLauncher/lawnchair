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

package com.android.launcher3.dagger

import android.net.Uri
import com.android.launcher3.util.SettingsCache.NOTIFICATION_BADGING_URI
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import javax.inject.Named

@Module
class SettingsModule {

    @Provides
    @IntoSet
    @Named("SETTINGS_ENABLED_BY_DEFAULT")
    fun provideNotificationBadgingDefaults(): Uri = NOTIFICATION_BADGING_URI
}
