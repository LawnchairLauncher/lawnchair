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
package com.android.wm.shell.bubbles;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;

import android.app.ActivityManager;
import android.os.IBinder;
import android.view.SurfaceControl;
import android.window.TransitionInfo;

import androidx.annotation.NonNull;

import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.transition.Transitions;

/**
 * Observer used to identify tasks that are opening or moving to front. If a bubble activity is
 * currently opened when this happens, we'll collapse the bubbles.
 */
public class BubblesTransitionObserver implements Transitions.TransitionObserver {

    private BubbleController mBubbleController;
    private BubbleData mBubbleData;

    public BubblesTransitionObserver(BubbleController controller,
            BubbleData bubbleData) {
        mBubbleController = controller;
        mBubbleData = bubbleData;
    }

    @Override
    public void onTransitionReady(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction) {
        for (TransitionInfo.Change change : info.getChanges()) {
            final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
            // We only care about opens / move to fronts when bubbles are expanded & not animating.
            if (taskInfo == null
                    || taskInfo.taskId == INVALID_TASK_ID
                    || !TransitionUtil.isOpeningType(change.getMode())
                    || mBubbleController.isStackAnimating()
                    || !mBubbleData.isExpanded()
                    || mBubbleData.getSelectedBubble() == null) {
                continue;
            }
            int expandedId = mBubbleData.getSelectedBubble().getTaskId();
            // If the task id that's opening is the same as the expanded bubble, skip collapsing
            // because it is our bubble that is opening.
            if (expandedId != INVALID_TASK_ID && expandedId != taskInfo.taskId) {
                mBubbleData.setExpanded(false);
            }
        }
    }

    @Override
    public void onTransitionStarting(@NonNull IBinder transition) {

    }

    @Override
    public void onTransitionMerged(@NonNull IBinder merged, @NonNull IBinder playing) {

    }

    @Override
    public void onTransitionFinished(@NonNull IBinder transition, boolean aborted) {

    }
}
