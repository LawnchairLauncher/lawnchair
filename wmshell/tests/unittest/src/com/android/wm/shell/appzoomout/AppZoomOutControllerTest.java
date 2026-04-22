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

package com.android.wm.shell.appzoomout;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.testing.AndroidTestingRunner;
import android.view.Display;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.sysui.ShellInit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidTestingRunner.class)
public class AppZoomOutControllerTest extends ShellTestCase {

    @Mock private ShellTaskOrganizer mTaskOrganizer;
    @Mock private DisplayController mDisplayController;
    @Mock private AppZoomOutDisplayAreaOrganizer mAppDisplayAreaOrganizer;
    @Mock private TopLevelZoomOutDisplayAreaOrganizer mTopLevelDisplayAreaOrganizer;
    @Mock private ShellExecutor mExecutor;
    @Mock private ActivityManager.RunningTaskInfo mRunningTaskInfo;

    private AppZoomOutController mController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        Display display = mContext.getDisplay();
        DisplayLayout displayLayout = new DisplayLayout(mContext, display);
        when(mDisplayController.getDisplayLayout(anyInt())).thenReturn(displayLayout);

        ShellInit shellInit = spy(new ShellInit(mExecutor));
        mController = spy(new AppZoomOutController(mContext, shellInit, mTaskOrganizer,
                mDisplayController, mAppDisplayAreaOrganizer, mTopLevelDisplayAreaOrganizer,
                mExecutor));
    }

    @Test
    public void isHomeTaskFocused_zoomOutForHome() {
        mRunningTaskInfo.isFocused = true;
        when(mRunningTaskInfo.getActivityType()).thenReturn(ACTIVITY_TYPE_HOME);
        mController.onFocusTaskChanged(mRunningTaskInfo);

        verify(mAppDisplayAreaOrganizer).setIsHomeTaskFocused(true);
    }

    @Test
    public void isHomeTaskNotFocused_zoomOutForApp() {
        mRunningTaskInfo.isFocused = false;
        when(mRunningTaskInfo.getActivityType()).thenReturn(ACTIVITY_TYPE_HOME);
        mController.onFocusTaskChanged(mRunningTaskInfo);

        verify(mAppDisplayAreaOrganizer).setIsHomeTaskFocused(false);
    }
}
