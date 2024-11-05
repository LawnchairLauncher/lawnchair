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

package com.android.wm.shell.splitscreen;

import static android.view.Display.DEFAULT_DISPLAY;

import static com.google.common.truth.Truth.assertThat;

import android.app.ActivityManager;
import android.view.SurfaceControl;
import android.view.SurfaceSession;
import android.window.WindowContainerTransaction;

import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.icons.IconProvider;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestRunningTaskInfoBuilder;
import com.android.wm.shell.common.SyncTransactionQueue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

/** Tests for {@link MainStage} */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class MainStageTests extends ShellTestCase {
    @Mock private ShellTaskOrganizer mTaskOrganizer;
    @Mock private StageTaskListener.StageListenerCallbacks mCallbacks;
    @Mock private SyncTransactionQueue mSyncQueue;
    @Mock private ActivityManager.RunningTaskInfo mRootTaskInfo;
    @Mock private SurfaceControl mRootLeash;
    @Mock private IconProvider mIconProvider;
    private WindowContainerTransaction mWct = new WindowContainerTransaction();
    private SurfaceSession mSurfaceSession = new SurfaceSession();
    private MainStage mMainStage;

    @Before
    @UiThreadTest
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mRootTaskInfo = new TestRunningTaskInfoBuilder().build();
        mMainStage = new MainStage(mContext, mTaskOrganizer, DEFAULT_DISPLAY, mCallbacks,
                mSyncQueue, mSurfaceSession, mIconProvider, Optional.empty());
        mMainStage.onTaskAppeared(mRootTaskInfo, mRootLeash);
    }

    @Test
    public void testActiveDeactivate() {
        mMainStage.activate(mWct, true /* reparent */);
        assertThat(mMainStage.isActive()).isTrue();

        mMainStage.deactivate(mWct);
        assertThat(mMainStage.isActive()).isFalse();
    }
}
