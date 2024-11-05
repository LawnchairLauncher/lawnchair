/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Rect;
import android.view.SurfaceControl;
import android.view.SurfaceSession;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.TestRunningTaskInfoBuilder;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayImeController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.LaunchAdjacentController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.TransactionPool;
import com.android.wm.shell.common.split.SplitLayout;
import com.android.wm.shell.recents.RecentTasksController;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.windowdecor.WindowDecorViewModel;

import java.util.Optional;

public class SplitTestUtils {

    static SplitLayout createMockSplitLayout() {
        final Rect dividerBounds = new Rect(48, 0, 52, 100);
        final Rect bounds1 = new Rect(0, 0, 40, 100);
        final Rect bounds2 = new Rect(60, 0, 100, 100);
        final SurfaceControl leash = createMockSurface();
        SplitLayout out = mock(SplitLayout.class);
        doReturn(dividerBounds).when(out).getDividerBounds();
        doReturn(dividerBounds).when(out).getRefDividerBounds();
        doReturn(leash).when(out).getDividerLeash();
        doReturn(bounds1).when(out).getBounds1();
        doReturn(bounds2).when(out).getBounds2();
        return out;
    }

    static SurfaceControl createMockSurface() {
        return createMockSurface(true);
    }

    static SurfaceControl createMockSurface(boolean valid) {
        SurfaceControl sc = mock(SurfaceControl.class);
        ExtendedMockito.doReturn(valid).when(sc).isValid();
        return sc;
    }

    static class TestStageCoordinator extends StageCoordinator {
        final ActivityManager.RunningTaskInfo mRootTask;
        final SurfaceControl mRootLeash;

        TestStageCoordinator(Context context, int displayId, SyncTransactionQueue syncQueue,
                ShellTaskOrganizer taskOrganizer, MainStage mainStage, SideStage sideStage,
                DisplayController displayController, DisplayImeController imeController,
                DisplayInsetsController insetsController, SplitLayout splitLayout,
                Transitions transitions, TransactionPool transactionPool,
                ShellExecutor mainExecutor,
                Optional<RecentTasksController> recentTasks,
                LaunchAdjacentController launchAdjacentController,
                Optional<WindowDecorViewModel> windowDecorViewModel) {
            super(context, displayId, syncQueue, taskOrganizer, mainStage,
                    sideStage, displayController, imeController, insetsController, splitLayout,
                    transitions, transactionPool, mainExecutor, recentTasks,
                    launchAdjacentController, windowDecorViewModel);

            // Prepare root task for testing.
            mRootTask = new TestRunningTaskInfoBuilder().build();
            mRootLeash = new SurfaceControl.Builder(new SurfaceSession()).setName("test").build();
            onTaskAppeared(mRootTask, mRootLeash);
        }
    }
}
