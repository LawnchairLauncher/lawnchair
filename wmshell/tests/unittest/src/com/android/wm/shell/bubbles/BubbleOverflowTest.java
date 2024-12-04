/*
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

package com.android.wm.shell.bubbles;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.WindowManager;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.ShellTestCase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link com.android.wm.shell.bubbles.BubbleOverflow}.
 */
@SmallTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class BubbleOverflowTest extends ShellTestCase {

    private TestableBubblePositioner mPositioner;
    private BubbleOverflow mOverflow;
    private BubbleExpandedViewManager mExpandedViewManager;

    @Mock
    private BubbleController mBubbleController;
    @Mock
    private BubbleStackView mBubbleStackView;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mExpandedViewManager = BubbleExpandedViewManager.fromBubbleController(mBubbleController);
        mPositioner = new TestableBubblePositioner(mContext,
                mContext.getSystemService(WindowManager.class));
        when(mBubbleController.getPositioner()).thenReturn(mPositioner);
        when(mBubbleController.getStackView()).thenReturn(mBubbleStackView);

        mOverflow = new BubbleOverflow(mContext, mPositioner);
    }

    @Test
    public void test_initialize_forStack() {
        assertThat(mOverflow.getExpandedView()).isNull();

        mOverflow.initialize(mExpandedViewManager, mBubbleStackView, mPositioner);

        assertThat(mOverflow.getExpandedView()).isNotNull();
        assertThat(mOverflow.getExpandedView().getBubbleKey()).isEqualTo(BubbleOverflow.KEY);
        assertThat(mOverflow.getBubbleBarExpandedView()).isNull();
    }

    @Test
    public void test_initialize_forBubbleBar() {
        mOverflow.initializeForBubbleBar(mExpandedViewManager, mPositioner);

        assertThat(mOverflow.getBubbleBarExpandedView()).isNotNull();
        assertThat(mOverflow.getExpandedView()).isNull();
    }

    @Test
    public void test_cleanUpExpandedState() {
        mOverflow.initialize(mExpandedViewManager, mBubbleStackView, mPositioner);
        assertThat(mOverflow.getExpandedView()).isNotNull();

        mOverflow.cleanUpExpandedState();
        assertThat(mOverflow.getExpandedView()).isNull();
    }
}
