/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.compatui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import android.app.ActivityManager;
import android.app.TaskInfo;
import android.content.res.Configuration;
import android.testing.AndroidTestingRunner;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestShellExecutor;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.SyncTransactionQueue;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.function.BiConsumer;

/**
 * Tests for {@link ReachabilityEduWindowManager}.
 *
 * Build/Install/Run:
 * atest WMShellUnitTests:ReachabilityEduWindowManagerTest
 */
@RunWith(AndroidTestingRunner.class)
@SmallTest
public class ReachabilityEduWindowManagerTest extends ShellTestCase {
    @Mock
    private SyncTransactionQueue mSyncTransactionQueue;
    @Mock
    private ShellTaskOrganizer.TaskListener mTaskListener;
    @Mock
    private CompatUIConfiguration mCompatUIConfiguration;
    @Mock
    private DisplayLayout mDisplayLayout;
    @Mock
    private BiConsumer<TaskInfo, ShellTaskOrganizer.TaskListener> mOnDismissCallback;
    private TestShellExecutor mExecutor;
    private TaskInfo mTaskInfo;
    private ReachabilityEduWindowManager mWindowManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mExecutor = new TestShellExecutor();
        mTaskInfo = new ActivityManager.RunningTaskInfo();
        mTaskInfo.configuration.uiMode =
                (mTaskInfo.configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                        | Configuration.UI_MODE_NIGHT_NO;
        mTaskInfo.configuration.uiMode =
                (mTaskInfo.configuration.uiMode & ~Configuration.UI_MODE_TYPE_MASK)
                        | Configuration.UI_MODE_TYPE_NORMAL;
        mWindowManager = createReachabilityEduWindowManager(mTaskInfo);
    }

    @Test
    public void testCreateLayout_notEligible_doesNotCreateLayout() {
        assertFalse(mWindowManager.createLayout(/* canShow= */ true));

        assertNull(mWindowManager.mLayout);
    }

    @Test
    public void testWhenDockedStateHasChanged_needsToBeRecreated() {
        ActivityManager.RunningTaskInfo newTaskInfo = new ActivityManager.RunningTaskInfo();
        newTaskInfo.configuration.uiMode =
                (newTaskInfo.configuration.uiMode & ~Configuration.UI_MODE_TYPE_MASK)
                        | Configuration.UI_MODE_TYPE_DESK;

        Assert.assertTrue(mWindowManager.needsToBeRecreated(newTaskInfo, mTaskListener));
    }

    @Test
    public void testWhenDarkLightThemeHasChanged_needsToBeRecreated() {
        ActivityManager.RunningTaskInfo newTaskInfo = new ActivityManager.RunningTaskInfo();
        mTaskInfo.configuration.uiMode =
                (mTaskInfo.configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                        | Configuration.UI_MODE_NIGHT_YES;

        Assert.assertTrue(mWindowManager.needsToBeRecreated(newTaskInfo, mTaskListener));
    }

    private ReachabilityEduWindowManager createReachabilityEduWindowManager(TaskInfo taskInfo) {
        return new ReachabilityEduWindowManager(mContext, taskInfo, mSyncTransactionQueue,
                mTaskListener, mDisplayLayout, mCompatUIConfiguration, mExecutor,
                mOnDismissCallback, flags -> 0);
    }
}
