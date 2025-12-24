/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.database.ContentObserver;
import android.os.Handler;

import com.android.systemui.Flags;

/**
 * Helper for managing global settings through use of {@link SettingsProxy}. This should
 * <em>not</em> be used for {@link SecureSettings} or {@link SystemSettings} since those must be
 * user-aware (instead, use {@link com.android.systemui.qs.UserSettingObserver}).
 */
public abstract class SettingObserver extends ContentObserver {
    private final SettingsProxy mSettingsProxy;
    private final String mSettingName;
    private final int mDefaultValue;

    private boolean mListening;
    private int mObservedValue;

    protected abstract void handleValueChanged(int value, boolean observedChange);

    public SettingObserver(SettingsProxy settingsProxy, Handler handler, String settingName) {
        this(settingsProxy, handler, settingName, 0);
    }

    public SettingObserver(SettingsProxy settingsProxy, Handler handler, String settingName,
            int defaultValue) {
        super(handler);
        mSettingsProxy = settingsProxy;
        mSettingName = settingName;
        mObservedValue = mDefaultValue = defaultValue;
    }

    public int getValue() {
        return mListening ? mObservedValue : getValueFromProvider();
    }

    /**
     * Set the value of the observed setting.
     *
     * @param value The new value for the setting.
     */
    public void setValue(int value) {
        mSettingsProxy.putInt(mSettingName, value);
    }

    private int getValueFromProvider() {
        return mSettingsProxy.getInt(mSettingName, mDefaultValue);
    }

    /** Sets whether this observer should be listening for the setting or not. */
    public void setListening(boolean listening) {
        if (listening == mListening) return;
        mListening = listening;
        if (listening) {
            mObservedValue = getValueFromProvider();
            mSettingsProxy.registerContentObserverAsync(
                    mSettingsProxy.getUriFor(mSettingName), false, this,
                    () -> mObservedValue = getValueFromProvider());
        } else {
            mSettingsProxy.unregisterContentObserverAsync(this);
            mObservedValue = mDefaultValue;
        }
    }

    @Override
    public void onChange(boolean selfChange) {
        final int value = getValueFromProvider();
        final boolean changed = value != mObservedValue;
        mObservedValue = value;
        handleValueChanged(value, changed);
    }

    public String getKey() {
        return mSettingName;
    }

    public boolean isListening() {
        return mListening;
    }
}
