/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.util.settings;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.net.Uri;
import android.provider.Settings;

import com.android.systemui.util.settings.SettingsSingleThreadBackground;

import kotlinx.coroutines.CoroutineScope;

import javax.inject.Inject;

// use UserHandle.USER_SYSTEM everywhere
@SuppressLint("StaticSettingsProvider")
class GlobalSettingsImpl implements GlobalSettings {
    private final ContentResolver mContentResolver;
    private final CoroutineScope mSettingsScope;

    @Inject
    GlobalSettingsImpl(ContentResolver contentResolver,
            @SettingsSingleThreadBackground CoroutineScope settingsScope) {
        mContentResolver = contentResolver;
        mSettingsScope = settingsScope;
    }

    @NonNull
    @Override
    public ContentResolver getContentResolver() {
        return mContentResolver;
    }

    @NonNull
    @Override
    public Uri getUriFor(@NonNull String name) {
        return Settings.Global.getUriFor(name);
    }

    @NonNull
    @Override
    public CoroutineScope getSettingsScope() {
        return mSettingsScope;
    }

    @Override
    public String getString(@NonNull String name) {
        return Settings.Global.getString(mContentResolver, name);
    }

    @Override
    public boolean putString(@NonNull String name, String value) {
        return Settings.Global.putString(mContentResolver, name, value);
    }

    @Override
    public boolean putString(@NonNull String name, @Nullable String value, @Nullable String tag,
            boolean makeDefault) {
        return Settings.Global.putString(mContentResolver, name, value, tag, makeDefault);
    }
}
