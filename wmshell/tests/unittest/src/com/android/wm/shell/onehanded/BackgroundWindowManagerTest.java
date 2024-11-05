/**
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

package com.android.wm.shell.onehanded;

import static com.google.common.truth.Truth.assertThat;

import android.testing.TestableLooper;

import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.DisplayLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link BackgroundWindowManager} */
@SmallTest
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@RunWith(AndroidJUnit4.class)
public class BackgroundWindowManagerTest extends ShellTestCase {
    private BackgroundWindowManager mBackgroundWindowManager;
    @Mock
    private DisplayLayout  mMockDisplayLayout;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mBackgroundWindowManager = new BackgroundWindowManager(mContext);
        mBackgroundWindowManager.onDisplayChanged(mMockDisplayLayout);
    }

    @Test
    @UiThreadTest
    public void testInitRelease() {
        mBackgroundWindowManager.initView();
        assertThat(mBackgroundWindowManager.getSurfaceControl()).isNotNull();

        mBackgroundWindowManager.removeBackgroundLayer();
        assertThat(mBackgroundWindowManager.getSurfaceControl()).isNull();
    }
}
