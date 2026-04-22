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

package com.android.wm.shell.pip2.phone;

import static com.android.internal.jank.Cuj.CUJ_PIP_TRANSITION;
import static com.android.wm.shell.pip2.phone.PipInteractionHandler.INTERACTION_EXIT_PIP;
import static com.android.wm.shell.pip2.phone.PipInteractionHandler.INTERACTION_EXIT_PIP_TO_SPLIT;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.kotlin.VerificationKt.times;

import android.content.Context;
import android.os.Handler;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.SurfaceControl;

import androidx.test.filters.SmallTest;

import com.android.internal.jank.InteractionJankMonitor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit test against {@link PipInteractionHandler}.
 */
@SmallTest
@TestableLooper.RunWithLooper
@RunWith(AndroidTestingRunner.class)
public class PipInteractionHandlerTest {
    @Mock private Context mMockContext;
    @Mock private Handler mMockHandler;
    @Mock private InteractionJankMonitor mMockInteractionJankMonitor;

    private SurfaceControl mTestLeash;

    private PipInteractionHandler mPipInteractionHandler;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mPipInteractionHandler = new PipInteractionHandler(mMockContext, mMockHandler,
                mMockInteractionJankMonitor);
        mTestLeash = new SurfaceControl.Builder()
                .setContainerLayer()
                .setName("PipInteractionHandlerTest")
                .setCallsite("PipInteractionHandlerTest")
                .build();
    }

    @Test
    public void begin_expand_startsTracking() {
        mPipInteractionHandler.begin(mTestLeash, INTERACTION_EXIT_PIP);

        verify(mMockInteractionJankMonitor, times(1)).begin(eq(mTestLeash),
                eq(mMockContext), eq(mMockHandler), eq(CUJ_PIP_TRANSITION),
                eq(PipInteractionHandler.pipInteractionToString(INTERACTION_EXIT_PIP)));
    }

    @Test
    public void begin_expandToSplit_startsTracking() {
        mPipInteractionHandler.begin(mTestLeash, INTERACTION_EXIT_PIP_TO_SPLIT);

        verify(mMockInteractionJankMonitor, times(1)).begin(eq(mTestLeash),
                eq(mMockContext), eq(mMockHandler), eq(CUJ_PIP_TRANSITION),
                eq(PipInteractionHandler.pipInteractionToString(INTERACTION_EXIT_PIP_TO_SPLIT)));
    }

    @Test
    public void end_stopsTracking() {
        mPipInteractionHandler.end();

        verify(mMockInteractionJankMonitor, times(1)).end(CUJ_PIP_TRANSITION);
    }
}
