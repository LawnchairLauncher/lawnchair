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

import static android.app.CameraCompatTaskInfo.CAMERA_COMPAT_CONTROL_HIDDEN;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import android.app.ActivityManager;
import android.app.CameraCompatTaskInfo.CameraCompatControlState;
import android.app.TaskInfo;
import android.content.ComponentName;
import android.testing.AndroidTestingRunner;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.SurfaceControlViewHost;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.R;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestShellExecutor;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.SyncTransactionQueue;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.function.BiConsumer;

/**
 * Tests for {@link UserAspectRatioSettingsLayout}.
 *
 * Build/Install/Run:
 * atest WMShellUnitTests:UserAspectRatioSettingsLayoutTest
 */
@RunWith(AndroidTestingRunner.class)
@SmallTest
public class UserAspectRatioSettingsLayoutTest extends ShellTestCase {

    private static final int TASK_ID = 1;

    @Mock
    private SyncTransactionQueue mSyncTransactionQueue;
    @Mock
    private BiConsumer<TaskInfo, ShellTaskOrganizer.TaskListener>
            mOnUserAspectRatioSettingsButtonClicked;
    @Mock
    private ShellTaskOrganizer.TaskListener mTaskListener;
    @Mock
    private SurfaceControlViewHost mViewHost;
    @Captor
    private ArgumentCaptor<ShellTaskOrganizer.TaskListener> mUserAspectRatioTaskListenerCaptor;
    @Captor
    private ArgumentCaptor<TaskInfo> mUserAspectRationTaskInfoCaptor;

    private UserAspectRatioSettingsWindowManager mWindowManager;
    private UserAspectRatioSettingsLayout mLayout;
    private TaskInfo mTaskInfo;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTaskInfo = createTaskInfo(/* hasSizeCompat= */ false, CAMERA_COMPAT_CONTROL_HIDDEN);
        mWindowManager = new UserAspectRatioSettingsWindowManager(mContext, mTaskInfo,
                mSyncTransactionQueue, mTaskListener, new DisplayLayout(),
                new CompatUIController.CompatUIHintsState(),
                mOnUserAspectRatioSettingsButtonClicked, new TestShellExecutor(), flags -> 0,
                () -> false, s -> {});

        mLayout = (UserAspectRatioSettingsLayout) LayoutInflater.from(mContext).inflate(
                R.layout.user_aspect_ratio_settings_layout, null);
        mLayout.inject(mWindowManager);

        spyOn(mWindowManager);
        spyOn(mLayout);
        doReturn(mViewHost).when(mWindowManager).createSurfaceViewHost();
        doReturn(mLayout).when(mWindowManager).inflateLayout();
    }

    @Test
    public void testOnClickForUserAspectRatioSettingsButton() {
        final ImageButton button = mLayout.findViewById(R.id.user_aspect_ratio_settings_button);
        button.performClick();

        verify(mWindowManager).onUserAspectRatioSettingsButtonClicked();
        verify(mOnUserAspectRatioSettingsButtonClicked).accept(
                mUserAspectRationTaskInfoCaptor.capture(),
                mUserAspectRatioTaskListenerCaptor.capture());
        final Pair<TaskInfo, ShellTaskOrganizer.TaskListener> result =
                new Pair<>(mUserAspectRationTaskInfoCaptor.getValue(),
                        mUserAspectRatioTaskListenerCaptor.getValue());
        Assert.assertEquals(mTaskInfo, result.first);
        Assert.assertEquals(mTaskListener, result.second);
    }

    @Test
    public void testOnLongClickForUserAspectRatioButton() {
        doNothing().when(mWindowManager).onUserAspectRatioSettingsButtonLongClicked();

        final ImageButton button = mLayout.findViewById(R.id.user_aspect_ratio_settings_button);
        button.performLongClick();

        verify(mWindowManager).onUserAspectRatioSettingsButtonLongClicked();
    }

    @Test
    public void testOnClickForUserAspectRatioSettingsHint() {
        mWindowManager.mHasUserAspectRatioSettingsButton = true;
        mWindowManager.createLayout(/* canShow= */ true);
        final LinearLayout sizeCompatHint = mLayout.findViewById(
                R.id.user_aspect_ratio_settings_hint);
        sizeCompatHint.performClick();

        verify(mLayout).setUserAspectRatioSettingsHintVisibility(/* show= */ false);
    }

    private static TaskInfo createTaskInfo(boolean hasSizeCompat,
            @CameraCompatControlState int cameraCompatControlState) {
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = TASK_ID;
        taskInfo.appCompatTaskInfo.topActivityInSizeCompat = hasSizeCompat;
        taskInfo.appCompatTaskInfo.cameraCompatTaskInfo.cameraCompatControlState =
                cameraCompatControlState;
        taskInfo.realActivity = new ComponentName("com.mypackage.test", "TestActivity");
        return taskInfo;
    }
}
