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

package com.android.wm.shell.unfold;

import static com.android.wm.shell.unfold.UnfoldAnimationControllerTest.TestUnfoldTaskAnimator.UNSET;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager.RunningTaskInfo;
import android.app.TaskInfo;
import android.testing.AndroidTestingRunner;
import android.view.SurfaceControl;
import android.view.SurfaceControl.Transaction;

import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestRunningTaskInfoBuilder;
import com.android.wm.shell.TestShellExecutor;
import com.android.wm.shell.shared.TransactionPool;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.unfold.animation.UnfoldTaskAnimator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

/**
 * Tests for {@link UnfoldAnimationController}.
 *
 * Build/Install/Run:
 * atest WMShellUnitTests:UnfoldAnimationControllerTest
 */
@RunWith(AndroidTestingRunner.class)
public class UnfoldAnimationControllerTest extends ShellTestCase {

    @Mock
    private TransactionPool mTransactionPool;
    @Mock
    private UnfoldTransitionHandler mUnfoldTransitionHandler;
    @Mock
    private ShellInit mShellInit;
    @Mock
    private SurfaceControl mLeash;

    private UnfoldAnimationController mUnfoldAnimationController;

    private final TestShellUnfoldProgressProvider mProgressProvider =
            new TestShellUnfoldProgressProvider();
    private final TestShellExecutor mShellExecutor = new TestShellExecutor();

    private final TestUnfoldTaskAnimator mTaskAnimator1 = new TestUnfoldTaskAnimator();
    private final TestUnfoldTaskAnimator mTaskAnimator2 = new TestUnfoldTaskAnimator();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mTransactionPool.acquire()).thenReturn(mock(SurfaceControl.Transaction.class));

        final List<UnfoldTaskAnimator> animators = new ArrayList<>();
        animators.add(mTaskAnimator1);
        animators.add(mTaskAnimator2);
        mUnfoldAnimationController = new UnfoldAnimationController(
                mShellInit,
                mTransactionPool,
                mProgressProvider,
                animators,
                () -> Optional.of(mUnfoldTransitionHandler),
                mShellExecutor
        );
    }

    @Test
    public void instantiateController_addInitCallback() {
        verify(mShellInit, times(1)).addInitCallback(any(), any());
    }

    @Test
    public void testAppearedMatchingTask_appliesUnfoldProgress() {
        mTaskAnimator1.setTaskMatcher((info) -> info.getWindowingMode() == 2);
        RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setWindowingMode(2).build();

        mUnfoldAnimationController.onTaskAppeared(taskInfo, mLeash);

        mUnfoldAnimationController.onStateChangeProgress(0.5f);
        assertThat(mTaskAnimator1.mLastAppliedProgress).isEqualTo(0.5f);
    }

    @Test
    public void testAppearedMatchingTaskTwoDifferentAnimators_appliesUnfoldProgressToBoth() {
        mTaskAnimator1.setTaskMatcher((info) -> info.getWindowingMode() == 1);
        mTaskAnimator2.setTaskMatcher((info) -> info.getWindowingMode() == 2);
        RunningTaskInfo taskInfo1 = new TestRunningTaskInfoBuilder()
                .setWindowingMode(1).build();
        RunningTaskInfo taskInfo2 = new TestRunningTaskInfoBuilder()
                .setWindowingMode(2).build();

        mUnfoldAnimationController.onTaskAppeared(taskInfo1, mLeash);
        mUnfoldAnimationController.onTaskAppeared(taskInfo2, mLeash);

        mUnfoldAnimationController.onStateChangeProgress(0.5f);
        assertThat(mTaskAnimator1.mLastAppliedProgress).isEqualTo(0.5f);
        assertThat(mTaskAnimator2.mLastAppliedProgress).isEqualTo(0.5f);
    }

    @Test
    public void testAppearedNonMatchingTask_doesNotApplyUnfoldProgress() {
        mTaskAnimator1.setTaskMatcher((info) -> info.getWindowingMode() == 2);
        RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setWindowingMode(0).build();

        mUnfoldAnimationController.onTaskAppeared(taskInfo, mLeash);

        mUnfoldAnimationController.onStateChangeProgress(0.5f);
        assertThat(mTaskAnimator1.mLastAppliedProgress).isEqualTo(UNSET);
    }

    @Test
    public void testAppearedAndChangedToNonMatchingTask_doesNotApplyUnfoldProgress() {
        mTaskAnimator1.setTaskMatcher((info) -> info.getWindowingMode() == 2);
        RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setWindowingMode(2).build();

        mUnfoldAnimationController.onTaskAppeared(taskInfo, mLeash);
        taskInfo.configuration.windowConfiguration.setWindowingMode(0);
        mUnfoldAnimationController.onTaskInfoChanged(taskInfo);

        mUnfoldAnimationController.onStateChangeProgress(0.5f);
        assertThat(mTaskAnimator1.mLastAppliedProgress).isEqualTo(UNSET);
    }

    @Test
    public void testAppearedAndChangedToNonMatchingTaskAndBack_appliesUnfoldProgress() {
        mTaskAnimator1.setTaskMatcher((info) -> info.getWindowingMode() == 2);
        RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setWindowingMode(2).build();

        mUnfoldAnimationController.onTaskAppeared(taskInfo, mLeash);
        taskInfo.configuration.windowConfiguration.setWindowingMode(0);
        mUnfoldAnimationController.onTaskInfoChanged(taskInfo);
        taskInfo.configuration.windowConfiguration.setWindowingMode(2);
        mUnfoldAnimationController.onTaskInfoChanged(taskInfo);

        mUnfoldAnimationController.onStateChangeProgress(0.5f);
        assertThat(mTaskAnimator1.mLastAppliedProgress).isEqualTo(0.5f);
    }

    @Test
    public void testAppearedNonMatchingTaskAndChangedToMatching_appliesUnfoldProgress() {
        mTaskAnimator1.setTaskMatcher((info) -> info.getWindowingMode() == 2);
        RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setWindowingMode(0).build();

        mUnfoldAnimationController.onTaskAppeared(taskInfo, mLeash);
        taskInfo.configuration.windowConfiguration.setWindowingMode(2);
        mUnfoldAnimationController.onTaskInfoChanged(taskInfo);

        mUnfoldAnimationController.onStateChangeProgress(0.5f);
        assertThat(mTaskAnimator1.mLastAppliedProgress).isEqualTo(0.5f);
    }

    @Test
    public void testAppearedMatchingTaskAndChanged_appliesUnfoldProgress() {
        mTaskAnimator1.setTaskMatcher((info) -> info.getWindowingMode() == 2);
        RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setWindowingMode(2).build();

        mUnfoldAnimationController.onTaskAppeared(taskInfo, mLeash);
        mUnfoldAnimationController.onTaskInfoChanged(taskInfo);

        mUnfoldAnimationController.onStateChangeProgress(0.5f);
        assertThat(mTaskAnimator1.mLastAppliedProgress).isEqualTo(0.5f);
    }

    @Test
    public void testShellTransitionRunning_doesNotApplyUnfoldProgress() {
        when(mUnfoldTransitionHandler.willHandleTransition()).thenReturn(true);
        mTaskAnimator1.setTaskMatcher((info) -> info.getWindowingMode() == 2);
        RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setWindowingMode(2).build();

        mUnfoldAnimationController.onTaskAppeared(taskInfo, mLeash);

        mUnfoldAnimationController.onStateChangeProgress(0.5f);
        assertThat(mTaskAnimator1.mLastAppliedProgress).isEqualTo(UNSET);
    }

    @Test
    public void testApplicableTaskDisappeared_resetsSurface() {
        mTaskAnimator1.setTaskMatcher((info) -> info.getWindowingMode() == 0);
        RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setWindowingMode(0).build();
        mUnfoldAnimationController.onTaskAppeared(taskInfo, mLeash);
        assertThat(mTaskAnimator1.mResetTasks).doesNotContain(taskInfo.taskId);

        mUnfoldAnimationController.onStateChangeStarted();
        mUnfoldAnimationController.onTaskVanished(taskInfo);
        mUnfoldAnimationController.onStateChangeFinished();

        assertThat(mTaskAnimator1.mResetTasks).contains(taskInfo.taskId);
    }

    @Test
    public void testApplicableTaskDisappeared_noStateChange_doesNotResetSurface() {
        mTaskAnimator1.setTaskMatcher((info) -> info.getWindowingMode() == 0);
        RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setWindowingMode(0).build();
        mUnfoldAnimationController.onTaskAppeared(taskInfo, mLeash);
        assertThat(mTaskAnimator1.mResetTasks).doesNotContain(taskInfo.taskId);

        mUnfoldAnimationController.onTaskVanished(taskInfo);

        assertThat(mTaskAnimator1.mResetTasks).doesNotContain(taskInfo.taskId);
    }

    @Test
    public void testNonApplicableTaskAppearedDisappeared_doesNotResetSurface() {
        mTaskAnimator1.setTaskMatcher((info) -> info.getWindowingMode() == 2);
        RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setWindowingMode(0).build();

        mUnfoldAnimationController.onTaskAppeared(taskInfo, mLeash);
        mUnfoldAnimationController.onStateChangeStarted();
        mUnfoldAnimationController.onTaskVanished(taskInfo);
        mUnfoldAnimationController.onStateChangeFinished();

        assertThat(mTaskAnimator1.mResetTasks).doesNotContain(taskInfo.taskId);
    }

    @Test
    public void testInit_initsAndStartsAnimators() {
        mUnfoldAnimationController.onInit();
        mShellExecutor.flushAll();

        assertThat(mTaskAnimator1.mInitialized).isTrue();
        assertThat(mTaskAnimator1.mStarted).isTrue();
    }

    private static class TestShellUnfoldProgressProvider implements ShellUnfoldProgressProvider,
            ShellUnfoldProgressProvider.UnfoldListener {

        private final List<UnfoldListener> mListeners = new ArrayList<>();

        @Override
        public void addListener(Executor executor, UnfoldListener listener) {
            mListeners.add(listener);
        }

        @Override
        public void onStateChangeStarted() {
            mListeners.forEach(UnfoldListener::onStateChangeStarted);
        }

        @Override
        public void onStateChangeProgress(float progress) {
            mListeners.forEach(unfoldListener -> unfoldListener.onStateChangeProgress(progress));
        }

        @Override
        public void onStateChangeFinished() {
            mListeners.forEach(UnfoldListener::onStateChangeFinished);
        }
    }

    public static class TestUnfoldTaskAnimator implements UnfoldTaskAnimator {

        public static final float UNSET = -1f;
        private Predicate<TaskInfo> mTaskMatcher = (info) -> false;

        Map<Integer, TaskInfo> mTasksMap = new HashMap<>();
        Set<Integer> mResetTasks = new HashSet<>();

        boolean mInitialized = false;
        boolean mStarted = false;
        float mLastAppliedProgress = UNSET;

        @Override
        public void init() {
            mInitialized = true;
        }

        @Override
        public void start() {
            mStarted = true;
        }

        @Override
        public void stop() {
            mStarted = false;
        }

        @Override
        public boolean isApplicableTask(TaskInfo taskInfo) {
            return mTaskMatcher.test(taskInfo);
        }

        @Override
        public void applyAnimationProgress(float progress, Transaction transaction) {
            mLastAppliedProgress = progress;
        }

        public void setTaskMatcher(Predicate<TaskInfo> taskMatcher) {
            mTaskMatcher = taskMatcher;
        }

        @Override
        public void onTaskAppeared(TaskInfo taskInfo, SurfaceControl leash) {
            mTasksMap.put(taskInfo.taskId, taskInfo);
        }

        @Override
        public void onTaskVanished(TaskInfo taskInfo) {
            mTasksMap.remove(taskInfo.taskId);
        }

        @Override
        public void onTaskChanged(TaskInfo taskInfo) {
            mTasksMap.put(taskInfo.taskId, taskInfo);
        }

        @Override
        public void resetSurface(TaskInfo taskInfo, Transaction transaction) {
            mResetTasks.add(taskInfo.taskId);
        }

        @Override
        public void resetAllSurfaces(Transaction transaction) {
            mTasksMap.values().forEach((t) -> mResetTasks.add(t.taskId));
        }

        @Override
        public boolean hasActiveTasks() {
            return mTasksMap.size() > 0;
        }

        public List<TaskInfo> getCurrentTasks() {
            return new ArrayList<>(mTasksMap.values());
        }
    }
}
