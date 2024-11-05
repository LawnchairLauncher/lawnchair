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

package com.android.wm.shell.onehanded;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.testing.AndroidTestingRunner;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidTestingRunner.class)
public class OneHandedSettingsUtilTest extends OneHandedTestCase {
    OneHandedSettingsUtil mSettingsUtil;

    @Mock
    ContentResolver mMockContentResolver;
    @Mock
    ContentObserver mMockContentObserver;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mSettingsUtil = new OneHandedSettingsUtil();
    }

    @Test
    public void testUnregisterSecureKeyObserver() {
        mSettingsUtil.unregisterSettingsKeyObserver(mMockContentResolver, mMockContentObserver);

        verify(mMockContentResolver).unregisterContentObserver(any());
    }
}
