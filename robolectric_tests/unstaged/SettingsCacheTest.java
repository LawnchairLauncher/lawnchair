/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
public class SettingsCacheTest {

    public static final Uri KEY_SYSTEM_URI_TEST1 = Uri.parse("content://settings/system/test1");
    public static final Uri KEY_SYSTEM_URI_TEST2 = Uri.parse("content://settings/system/test2");;

    private SettingsCache.OnChangeListener mChangeListener;
    private SettingsCache mSettingsCache;

    @Before
    public void setup() {
        mChangeListener = mock(SettingsCache.OnChangeListener.class);
        Context targetContext = RuntimeEnvironment.application;
        mSettingsCache = SettingsCache.INSTANCE.get(targetContext);
        mSettingsCache.register(KEY_SYSTEM_URI_TEST1, mChangeListener);
    }

    @Test
    public void listenerCalledOnChange() {
        mSettingsCache.onChange(true, KEY_SYSTEM_URI_TEST1);
        verify(mChangeListener, times(1)).onSettingsChanged(true);
    }

    @Test
    public void getValueRespectsDefaultValue() {
        // Case of key not found
        boolean val = mSettingsCache.getValue(KEY_SYSTEM_URI_TEST1, 0);
        assertFalse(val);
    }

    @Test
    public void getValueHitsCache() {
        mSettingsCache.setKeyCache(Collections.singletonMap(KEY_SYSTEM_URI_TEST1, true));
        boolean val = mSettingsCache.getValue(KEY_SYSTEM_URI_TEST1, 0);
        assertTrue(val);
    }

    @Test
    public void getValueUpdatedCache() {
        // First ensure there's nothing in cache
        boolean val = mSettingsCache.getValue(KEY_SYSTEM_URI_TEST1, 0);
        assertFalse(val);

        mSettingsCache.setKeyCache(Collections.singletonMap(KEY_SYSTEM_URI_TEST1, true));
        val = mSettingsCache.getValue(KEY_SYSTEM_URI_TEST1, 0);
        assertTrue(val);
    }

    @Test
    public void multipleListenersSingleKey() {
        SettingsCache.OnChangeListener secondListener = mock(SettingsCache.OnChangeListener.class);
        mSettingsCache.register(KEY_SYSTEM_URI_TEST1, secondListener);

        mSettingsCache.onChange(true, KEY_SYSTEM_URI_TEST1);
        verify(mChangeListener, times(1)).onSettingsChanged(true);
        verify(secondListener, times(1)).onSettingsChanged(true);
    }

    @Test
    public void singleListenerMultipleKeys() {
        SettingsCache.OnChangeListener secondListener = mock(SettingsCache.OnChangeListener.class);
        mSettingsCache.register(KEY_SYSTEM_URI_TEST2, secondListener);

        mSettingsCache.onChange(true, KEY_SYSTEM_URI_TEST1);
        mSettingsCache.onChange(true, KEY_SYSTEM_URI_TEST2);
        verify(mChangeListener, times(1)).onSettingsChanged(true);
        verify(secondListener, times(1)).onSettingsChanged(true);
    }

    @Test
    public void sameListenerMultipleKeys() {
        SettingsCache.OnChangeListener secondListener = mock(SettingsCache.OnChangeListener.class);
        mSettingsCache.register(KEY_SYSTEM_URI_TEST2, mChangeListener);

        mSettingsCache.onChange(true, KEY_SYSTEM_URI_TEST1);
        mSettingsCache.onChange(true, KEY_SYSTEM_URI_TEST2);
        verify(mChangeListener, times(2)).onSettingsChanged(true);
        verify(secondListener, times(0)).onSettingsChanged(true);
    }
}
