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

package com.android.wm.shell.activityembedding;

import static android.view.WindowManager.TRANSIT_OPEN;
import static android.window.TransitionInfo.FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY;
import static android.window.TransitionInfo.FLAG_IS_BEHIND_STARTING_WINDOW;

import static com.android.wm.shell.transition.Transitions.TRANSIT_TASK_FRAGMENT_DRAG_RESIZE;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.animation.Animator;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.window.TransitionInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.window.flags.Flags;
import com.android.wm.shell.transition.TransitionInfoBuilder;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;

/**
 * Tests for {@link ActivityEmbeddingAnimationRunner}.
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:ActivityEmbeddingAnimationRunnerTests
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ActivityEmbeddingAnimationRunnerTests extends ActivityEmbeddingAnimationTestBase {

    @Rule
    public SetFlagsRule mRule = new SetFlagsRule();

    @Before
    public void setup() {
        super.setUp();
        doNothing().when(mController).onAnimationFinished(any());
    }

    @EnableFlags(Flags.FLAG_MOVE_ANIMATION_OPTIONS_TO_CHANGE)
    @Test
    public void testStartAnimation() {
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN, 0)
                .addChange(createChange(FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY))
                .build();
        doReturn(mAnimator).when(mAnimRunner).createAnimator(any(), any(), any(), any(),
                any());

        mAnimRunner.startAnimation(mTransition, info, mStartTransaction, mFinishTransaction);

        final ArgumentCaptor<Runnable> finishCallback = ArgumentCaptor.forClass(Runnable.class);
        verify(mAnimRunner).createAnimator(eq(info), eq(mStartTransaction),
                eq(mFinishTransaction),
                finishCallback.capture(), any());
        verify(mStartTransaction).apply();
        verify(mAnimator).start();
        verifyNoMoreInteractions(mFinishTransaction);
        verify(mController, never()).onAnimationFinished(any());

        // Call onAnimationFinished() when the animation is finished.
        finishCallback.getValue().run();

        verify(mController).onAnimationFinished(mTransition);
    }

    @EnableFlags(Flags.FLAG_MOVE_ANIMATION_OPTIONS_TO_CHANGE)
    @Test
    public void testChangesBehindStartingWindow() {
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN, 0)
                .addChange(createChange(FLAG_IS_BEHIND_STARTING_WINDOW))
                .build();
        final Animator animator = mAnimRunner.createAnimator(
                info, mStartTransaction, mFinishTransaction,
                () -> mFinishCallback.onTransitionFinished(null /* wct */),
                new ArrayList());

        // The animation should be empty when it is behind starting window.
        assertEquals(0, animator.getDuration());
    }

    @EnableFlags(Flags.FLAG_MOVE_ANIMATION_OPTIONS_TO_CHANGE)
    @Test
    public void testTransitionTypeDragResize() {
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TASK_FRAGMENT_DRAG_RESIZE, 0)
                .addChange(createChange(FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY))
                .build();
        final Animator animator = mAnimRunner.createAnimator(
                info, mStartTransaction, mFinishTransaction,
                () -> mFinishCallback.onTransitionFinished(null /* wct */),
                new ArrayList());

        // The animation should be empty when it is a jump cut for drag resize.
        assertEquals(0, animator.getDuration());
    }

    @DisableFlags(Flags.FLAG_MOVE_ANIMATION_OPTIONS_TO_CHANGE)
    @Test
    public void testInvalidCustomAnimation_disableAnimationOptionsPerChange() {
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN, 0)
                .addChange(createChange(FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY))
                .build();
        info.setAnimationOptions(TransitionInfo.AnimationOptions
                .makeCustomAnimOptions("packageName", 0 /* enterResId */, 0 /* exitResId */,
                        0 /* backgroundColor */, false /* overrideTaskTransition */));
        final Animator animator = mAnimRunner.createAnimator(
                info, mStartTransaction, mFinishTransaction,
                () -> mFinishCallback.onTransitionFinished(null /* wct */),
                new ArrayList<>());

        // An invalid custom animation is equivalent to jump-cut.
        assertEquals(0, animator.getDuration());
    }

    @EnableFlags(Flags.FLAG_MOVE_ANIMATION_OPTIONS_TO_CHANGE)
    @Test
    public void testInvalidCustomAnimation_enableAnimationOptionsPerChange() {
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN, 0)
                .addChange(createChange(FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY))
                .build();
        info.getChanges().getFirst().setAnimationOptions(TransitionInfo.AnimationOptions
                .makeCustomAnimOptions("packageName", 0 /* enterResId */, 0 /* exitResId */,
                        0 /* backgroundColor */, false /* overrideTaskTransition */));
        final Animator animator = mAnimRunner.createAnimator(
                info, mStartTransaction, mFinishTransaction,
                () -> mFinishCallback.onTransitionFinished(null /* wct */),
                new ArrayList<>());

        // An invalid custom animation is equivalent to jump-cut.
        assertEquals(0, animator.getDuration());
    }
}
