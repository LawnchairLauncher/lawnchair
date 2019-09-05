/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;

/**
 * Utility class to listen for secure settings changes
 */
public class SecureSettingsObserver extends ContentObserver {

    /** Hidden field Settings.Secure.NOTIFICATION_BADGING */
    public static final String NOTIFICATION_BADGING = "notification_badging";

    private final ContentResolver mResolver;
    private final String mKeySetting;
    private final int mDefaultValue;
    private final OnChangeListener mOnChangeListener;

    public SecureSettingsObserver(ContentResolver resolver, OnChangeListener listener,
            String keySetting, int defaultValue) {
        super(new Handler());

        mResolver = resolver;
        mOnChangeListener = listener;
        mKeySetting = keySetting;
        mDefaultValue = defaultValue;
    }

    @Override
    public void onChange(boolean selfChange) {
        mOnChangeListener.onSettingsChanged(getValue());
    }

    public boolean getValue() {
        return Settings.Secure.getInt(mResolver, mKeySetting, mDefaultValue) == 1;
    }

    public void register() {
        mResolver.registerContentObserver(Settings.Secure.getUriFor(mKeySetting), false, this);
    }

    public ContentResolver getResolver() {
        return mResolver;
    }

    public void dispatchOnChange() {
        onChange(true);
    }

    public void unregister() {
        mResolver.unregisterContentObserver(this);
    }

    public interface OnChangeListener {
        void onSettingsChanged(boolean isEnabled);
    }

    public static SecureSettingsObserver newNotificationSettingsObserver(Context context,
            OnChangeListener listener) {
        return new SecureSettingsObserver(
                context.getContentResolver(), listener, NOTIFICATION_BADGING, 1);
    }
}
