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
import android.content.ContentResolver;
import android.net.Uri;
import android.provider.Settings;

import com.android.systemui.util.settings.SettingsSingleThreadBackground;

import kotlinx.coroutines.CoroutineScope;

import javax.inject.Inject;

class SystemSettingsImpl implements SystemSettings {
    private final ContentResolver mContentResolver;
    private final CurrentUserIdProvider mCurrentUserProvider;
    private final CoroutineScope mSettingsScope;

    @Inject
    SystemSettingsImpl(ContentResolver contentResolver,
            CurrentUserIdProvider currentUserProvider,
            @SettingsSingleThreadBackground CoroutineScope settingsScope) {
        mContentResolver = contentResolver;
        mCurrentUserProvider = currentUserProvider;
        mSettingsScope = settingsScope;
    }

    @NonNull
    @Override
    public ContentResolver getContentResolver() {
        return mContentResolver;
    }

    @NonNull
    @Override
    public CurrentUserIdProvider getCurrentUserProvider() {
        return mCurrentUserProvider;
    }

    @NonNull
    @Override
    public Uri getUriFor(@NonNull String name) {
        return Settings.System.getUriFor(name);
    }

    @NonNull
    @Override
    public CoroutineScope getSettingsScope() {
        return mSettingsScope;
    }

    @Override
    public String getStringForUser(@NonNull String name, int userHandle) {
        return Settings.System.getStringForUser(mContentResolver, name,
                getRealUserHandle(userHandle));
    }

    @Override
    public boolean putString(@NonNull String name, String value, boolean overrideableByRestore) {
        return Settings.System.putString(mContentResolver, name, value, overrideableByRestore);
    }

    @Override
    public boolean putStringForUser(@NonNull String name, String value, int userHandle) {
        return Settings.System.putStringForUser(mContentResolver, name, value,
                getRealUserHandle(userHandle));
    }

    @Override
    public boolean putStringForUser(@NonNull String name, String value, String tag,
            boolean makeDefault, int userHandle, boolean overrideableByRestore) {
        throw new UnsupportedOperationException(
                "This method only exists publicly for Settings.Secure and Settings.Global");
    }

    @Override
    public boolean putString(@NonNull String name, String value, String tag, boolean makeDefault) {
        throw new UnsupportedOperationException(
                "This method only exists publicly for Settings.Secure and Settings.Global");
    }
}
