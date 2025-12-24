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

import com.android.launcher3.ConstantItem
import com.android.launcher3.LifecycleTracker
import com.android.launcher3.graphics.ThemeManager.Companion.ICON_FACTORY_DAGGER_KEY
import com.android.launcher3.graphics.theme.IconThemeFactory
import com.android.launcher3.graphics.theme.MonoIconThemeFactory
import com.android.launcher3.graphics.theme.MonoIconThemeFactory.MONO_FACTORY_ID
import com.android.launcher3.graphics.theme.ThemePreference.Companion.THEME_OVERRIDES_DAGGER_KEY
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.PopupDataRepository
import com.android.launcher3.popup.PopupDataRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import dagger.multibindings.Multibinds
import dagger.multibindings.StringKey
import javax.inject.Named

@Module
abstract class LauncherModelModule {
    @Binds abstract fun bindPopupDataRepository(impl: PopupDataRepositoryImpl): PopupDataRepository

    @Multibinds @Named("MODEL_ITEMS") abstract fun extraModelItems(): Set<ItemInfo>

    @Multibinds abstract fun lifecycleTrackers(): Set<LifecycleTracker>

    @Multibinds
    @Named(THEME_OVERRIDES_DAGGER_KEY)
    abstract fun legacyThemeKeys(): Map<String, ConstantItem<String>>

    companion object {

        @Provides
        @IntoMap
        @StringKey(MONO_FACTORY_ID)
        @Named(ICON_FACTORY_DAGGER_KEY)
        @JvmStatic
        fun monoIconFactory(): IconThemeFactory = MonoIconThemeFactory
    }
}
