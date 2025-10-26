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

package com.android.quickstep;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.Flags;
import com.android.launcher3.R;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.IconProvider;
import com.android.launcher3.util.DaggerSingletonTracker;
import com.android.launcher3.util.LockedUserState;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.util.SplitTask;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.TaskStackChangeListeners;
import com.android.wm.shell.shared.split.SplitScreenConstants;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.function.Consumer;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class RecentsModelTest {
    @Mock
    private Context mContext;

    @Mock
    private TaskThumbnailCache mThumbnailCache;

    @Mock
    private RecentTasksList mTasksList;

    @Mock
    private HighResLoadingState mHighResLoadingState;

    @Mock
    private LockedUserState mLockedUserState;

    @Mock
    private ThemeManager mThemeManager;

    private RecentsModel mRecentsModel;

    private RecentTasksList.TaskLoadResult mTaskResult;

    private Resources mResource;

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Before
    public void setup() throws NoSuchFieldException {
        MockitoAnnotations.initMocks(this);
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_GRID_ONLY_OVERVIEW);
        mTaskResult = getTaskResult();
        doAnswer(invocation-> {
            Consumer<ArrayList<GroupTask>> callback = invocation.getArgument(1);
            callback.accept(mTaskResult);
            return null;
        }).when(mTasksList).getTaskKeys(anyInt(), any());

        when(mHighResLoadingState.isEnabled()).thenReturn(true);
        when(mThumbnailCache.getHighResLoadingState()).thenReturn(mHighResLoadingState);
        when(mThumbnailCache.isPreloadingEnabled()).thenReturn(true);

        mRecentsModel = new RecentsModel(mContext, mTasksList, mock(TaskIconCache.class),
                mThumbnailCache, mock(IconProvider.class), mock(TaskStackChangeListeners.class),
                mLockedUserState, () -> mThemeManager, mock(DaggerSingletonTracker.class));

        mResource = mock(Resources.class);
        when(mResource.getInteger((R.integer.recentsThumbnailCacheSize))).thenReturn(3);
        when(mContext.getResources()).thenReturn(mResource);
    }

    @Test
    @UiThreadTest
    public void preloadOnHighResolutionEnabled() {
        mRecentsModel.preloadCacheIfNeeded();

        ArgumentCaptor<Task> taskArgs = ArgumentCaptor.forClass(Task.class);
        verify(mRecentsModel.getThumbnailCache(), times(2))
                .updateThumbnailInCache(taskArgs.capture(), /* lowResolution= */ eq(false));

        GroupTask expectedGroupTask = mTaskResult.get(0);
        var taskArgsValues = taskArgs.getAllValues();
        var expectedTasks = expectedGroupTask.getTasks();
        assertThat(taskArgsValues.size()).isEqualTo(expectedTasks.size());
        for (int i = 0; i < expectedTasks.size(); ++i) {
            assertThat(taskArgsValues.get(i)).isEqualTo(expectedTasks.get(i));
        }
    }

    @Test
    public void notPreloadOnHighResolutionDisabled() {
        when(mHighResLoadingState.isEnabled()).thenReturn(false);
        when(mThumbnailCache.isPreloadingEnabled()).thenReturn(true);
        mRecentsModel.preloadCacheIfNeeded();
        verify(mRecentsModel.getThumbnailCache(), never())
                .updateThumbnailInCache(any(), anyBoolean());
    }

    @Test
    public void notPreloadOnPreloadDisabled() {
        when(mThumbnailCache.isPreloadingEnabled()).thenReturn(false);
        mRecentsModel.preloadCacheIfNeeded();
        verify(mRecentsModel.getThumbnailCache(), never())
                .updateThumbnailInCache(any(), anyBoolean());

    }

    @Test
    public void increaseCacheSizeAndPreload() {
        // Mock to return preload is needed
        when(mThumbnailCache.updateCacheSizeAndRemoveExcess()).thenReturn(true);
        // Update cache size
        mRecentsModel.updateCacheSizeAndPreloadIfNeeded();
        // Assert update cache is called
        verify(mRecentsModel.getThumbnailCache(), times(2))
                .updateThumbnailInCache(any(), /* lowResolution= */ eq(false));
    }

    @Test
    public void decreaseCacheSizeAndNotPreload() {
        // Mock to return preload is not needed
        when(mThumbnailCache.updateCacheSizeAndRemoveExcess()).thenReturn(false);
        // Update cache size
        mRecentsModel.updateCacheSizeAndPreloadIfNeeded();
        // Assert update cache is never called
        verify(mRecentsModel.getThumbnailCache(), never())
                .updateThumbnailInCache(any(), anyBoolean());
    }

    @Test
    public void themeCallbackAttachedOnUnlock() {
        verify(mThemeManager, never()).addChangeListener(any());

        ArgumentCaptor<Runnable> callbackCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mLockedUserState).runOnUserUnlocked(callbackCaptor.capture());

        callbackCaptor.getAllValues().forEach(Runnable::run);
        verify(mThemeManager, times(1)).addChangeListener(any());
    }

    private RecentTasksList.TaskLoadResult getTaskResult() {
        RecentTasksList.TaskLoadResult allTasks = new RecentTasksList.TaskLoadResult(0, false, 1);
        ActivityManager.RecentTaskInfo taskInfo1 = new ActivityManager.RecentTaskInfo();
        Task.TaskKey taskKey1 = new Task.TaskKey(taskInfo1);
        Task task1 = Task.from(taskKey1, taskInfo1, false);

        ActivityManager.RecentTaskInfo taskInfo2 = new ActivityManager.RecentTaskInfo();
        Task.TaskKey taskKey2 = new Task.TaskKey(taskInfo2);
        Task task2 = Task.from(taskKey2, taskInfo2, false);

        allTasks.add(
                new SplitTask(task1, task2, new SplitConfigurationOptions.SplitBounds(
                        /* leftTopBounds = */ new Rect(),
                        /* rightBottomBounds = */ new Rect(),
                        /* leftTopTaskId = */ -1,
                        /* rightBottomTaskId = */ -1,
                        /* snapPosition = */ SplitScreenConstants.SNAP_TO_2_50_50)));
        return allTasks;
    }
}
