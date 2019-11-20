/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.quickstep;

import static junit.framework.TestCase.assertNull;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;

import androidx.test.filters.SmallTest;

import com.android.launcher3.util.LooperExecutor;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.KeyguardManagerCompat;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

@SmallTest
public class RecentTasksListTest {

    private ActivityManagerWrapper mockActivityManagerWrapper;

    // Class under test
    private RecentTasksList mRecentTasksList;

    @Before
    public void setup() {
        LooperExecutor mockMainThreadExecutor = mock(LooperExecutor.class);
        KeyguardManagerCompat mockKeyguardManagerCompat = mock(KeyguardManagerCompat.class);
        mockActivityManagerWrapper = mock(ActivityManagerWrapper.class);
        mRecentTasksList = new RecentTasksList(mockMainThreadExecutor, mockKeyguardManagerCompat,
                mockActivityManagerWrapper);
    }

    @Test
    public void onTaskRemoved_reloadsAllTasks() {
        mRecentTasksList.onTaskRemoved(0);
        verify(mockActivityManagerWrapper, times(1))
                .getRecentTasks(anyInt(), anyInt());
    }

    @Test
    public void onTaskStackChanged_doesNotFetchTasks() {
        mRecentTasksList.onTaskStackChanged();
        verify(mockActivityManagerWrapper, times(0))
                .getRecentTasks(anyInt(), anyInt());
    }

    @Test
    public void loadTasksInBackground_onlyKeys_noValidTaskDescription() {
        ActivityManager.RecentTaskInfo recentTaskInfo = new ActivityManager.RecentTaskInfo();
        when(mockActivityManagerWrapper.getRecentTasks(anyInt(), anyInt()))
                .thenReturn(Collections.singletonList(recentTaskInfo));

        List<Task> taskList = mRecentTasksList.loadTasksInBackground(Integer.MAX_VALUE, true);

        assertEquals(1, taskList.size());
        assertNull(taskList.get(0).taskDescription.getLabel());
    }

    @Test
    public void loadTasksInBackground_moreThanKeys_hasValidTaskDescription() {
        String taskDescription = "Wheeee!";
        ActivityManager.RecentTaskInfo recentTaskInfo = new ActivityManager.RecentTaskInfo();
        recentTaskInfo.taskDescription = new ActivityManager.TaskDescription(taskDescription);
        when(mockActivityManagerWrapper.getRecentTasks(anyInt(), anyInt()))
                .thenReturn(Collections.singletonList(recentTaskInfo));

        List<Task> taskList = mRecentTasksList.loadTasksInBackground(Integer.MAX_VALUE, false);

        assertEquals(1, taskList.size());
        assertEquals(taskDescription, taskList.get(0).taskDescription.getLabel());
    }
}
