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

package com.android.wm.shell.common.split;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.content.res.Configuration;
import android.graphics.Rect;
import android.view.InsetsState;

import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.wm.shell.ShellTestCase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link SplitWindowManager} */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class SplitWindowManagerTests extends ShellTestCase {
    @Mock SplitLayout mSplitLayout;
    @Mock SplitWindowManager.ParentContainerCallbacks mCallbacks;
    private SplitWindowManager mSplitWindowManager;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final Configuration configuration = new Configuration();
        configuration.setToDefaults();
        mSplitWindowManager = new SplitWindowManager("TestSplitDivider", mContext, configuration,
                mCallbacks);
        when(mSplitLayout.getDividerBounds()).thenReturn(
                new Rect(0, 0, configuration.windowConfiguration.getBounds().width(),
                        configuration.windowConfiguration.getBounds().height()));
    }

    @Test
    @UiThreadTest
    public void testInitRelease() {
        mSplitWindowManager.init(mSplitLayout, new InsetsState(), false /* isRestoring */);
        assertThat(mSplitWindowManager.getSurfaceControl()).isNotNull();
        mSplitWindowManager.release(null /* t */);
        assertThat(mSplitWindowManager.getSurfaceControl()).isNull();
    }
}
