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

package com.android.wm.shell.compatui;

import static android.view.WindowManager.LARGE_SCREEN_SMALLEST_SCREEN_WIDTH_DP;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.ActivityManager;
import android.app.TaskInfo;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.testing.AndroidTestingRunner;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.Flags;
import com.android.wm.shell.ShellTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link CompatUIUtils}.
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:CompatUIUtilsTest
 */
@RunWith(AndroidTestingRunner.class)
@SmallTest
public class CompatUIUtilsTest extends ShellTestCase {

    private static final int SMALL_SCREEN_WIDTH_DP = LARGE_SCREEN_SMALLEST_SCREEN_WIDTH_DP - 1;
    private static final int LARGE_SCREEN_WIDTH_DP = LARGE_SCREEN_SMALLEST_SCREEN_WIDTH_DP + 1;
    private static final int THRESHOLD_SCREEN_WIDTH_DP = LARGE_SCREEN_SMALLEST_SCREEN_WIDTH_DP;

    private static final int TASK_ID = 1;

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
    public void shouldShowSizeCompatForPhoneSizedDisplay_NonBubbleSmallScreenWidth() {
        TaskInfo taskInfo =
                createTaskInfo(
                        /* isAppBubble= */ false,
                        /* smallestScreenWidthDp= */ SMALL_SCREEN_WIDTH_DP);
        assertTrue(CompatUIUtils.shouldShowSizeCompatRestartForPhoneScreen(taskInfo));
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
    public void shouldShowSizeCompatForPhoneSizedDisplay_NonBubbleLargeScreenWidth() {
        TaskInfo taskInfo =
                createTaskInfo(
                        /* isAppBubble= */ false,
                        /* smallestScreenWidthDp= */ LARGE_SCREEN_WIDTH_DP);
        assertFalse(CompatUIUtils.shouldShowSizeCompatRestartForPhoneScreen(taskInfo));
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
    public void shouldShowSizeCompatForPhoneSizedDisplay_NonBubbleThresholdScreenWidth() {
        TaskInfo taskInfo =
                createTaskInfo(
                        /* isAppBubble= */ false,
                        /* smallestScreenWidthDp= */ THRESHOLD_SCREEN_WIDTH_DP);
        assertFalse(CompatUIUtils.shouldShowSizeCompatRestartForPhoneScreen(taskInfo));
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
    public void shouldShowSizeCompatForPhoneSizedDisplay_BubbleSmallScreenWidth() {
        TaskInfo taskInfo =
                createTaskInfo(
                        /* isAppBubble= */ true,
                        /* smallestScreenWidthDp= */ SMALL_SCREEN_WIDTH_DP);
        assertFalse(CompatUIUtils.shouldShowSizeCompatRestartForPhoneScreen(taskInfo));
    }

    private static TaskInfo createTaskInfo(boolean isAppBubble, int smallestScreenWidthDp) {
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = TASK_ID;
        taskInfo.isAppBubble = isAppBubble;
        taskInfo.configuration.smallestScreenWidthDp = smallestScreenWidthDp;
        return taskInfo;
    }
}
