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

import static com.android.wm.shell.activityembedding.ActivityEmbeddingAnimationRunner.calculateParentBounds;
import static com.android.wm.shell.activityembedding.ActivityEmbeddingAnimationRunner.shouldUseJumpCutForAnimation;
import static com.android.wm.shell.transition.Transitions.TRANSIT_TASK_FRAGMENT_DRAG_RESIZE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.animation.Animator;
import android.annotation.NonNull;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.window.TransitionInfo;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.transition.TransitionInfoBuilder;

import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Tests for {@link ActivityEmbeddingAnimationRunner}.
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:ActivityEmbeddingAnimationRunnerTests
 */
@SmallTest
@RunWith(TestParameterInjector.class)
public class ActivityEmbeddingAnimationRunnerTests extends ActivityEmbeddingAnimationTestBase {

    @Before
    public void setup() {
        super.setUp();
        doNothing().when(mController).onAnimationFinished(any());
    }

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

    @Test
    public void testInvalidCustomAnimation_enableAnimationOptionsPerChange() {
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN, 0)
                .addChange(createChange(FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY, TRANSIT_OPEN))
                .build();
        info.getChanges().getFirst().setAnimationOptions(TransitionInfo.AnimationOptions
                .makeCustomAnimOptions("packageName", 0 /* enterResId */, 0 /* changeResId */,
                        0 /* exitResId */, false /* overrideTaskTransition */));
        final Animator animator = mAnimRunner.createAnimator(
                info, mStartTransaction, mFinishTransaction,
                () -> mFinishCallback.onTransitionFinished(null /* wct */),
                new ArrayList<>());

        // An invalid custom animation is equivalent to jump-cut.
        assertEquals(0, animator.getDuration());
    }

    @Test
    public void testCalculateParentBounds_flagEnabled_emptyParentSize() {
        TransitionInfo.Change change;
        final Rect actualParentBounds = new Rect();
        change = prepareChangeForParentBoundsCalculationTest(
                new Point(0, 0) /* endRelOffset */,
                new Rect(0, 0, 2000, 2000),
                new Point() /* endParentSize */
        );

        calculateParentBounds(change, actualParentBounds);

        assertTrue("Parent bounds must be empty because end parent size is not set.",
                actualParentBounds.isEmpty());
    }

    @Test
    public void testCalculateParentBounds_flagEnabled(
            @TestParameter ParentBoundsTestParameters params) {
        final Rect parentBounds = params.getParentBounds();
        final Rect endAbsBounds = params.getEndAbsBounds();
        final TransitionInfo.Change change = prepareChangeForParentBoundsCalculationTest(
                new Point(endAbsBounds.left - parentBounds.left,
                        endAbsBounds.top - parentBounds.top),
                endAbsBounds, new Point(parentBounds.width(), parentBounds.height()));
        final Rect actualParentBounds = new Rect();

        calculateParentBounds(change, actualParentBounds);

        assertEquals(parentBounds, actualParentBounds);
    }

    private enum ParentBoundsTestParameters {
        PARENT_START_WITH_0_0(
                new int[]{0, 0, 2000, 2000},
                new int[]{0, 0, 2000, 2000}),
        CONTAINER_NOT_START_WITH_0_0(
                new int[] {0, 0, 2000, 2000},
                new int[] {1000, 500, 1500, 1500}),
        PARENT_ON_THE_RIGHT(
                new int[] {1000, 0, 2000, 2000},
                new int[] {1000, 500, 1500, 1500}),
        PARENT_ON_THE_BOTTOM(
                new int[] {0, 1000, 2000, 2000},
                new int[] {500, 1500, 1500, 2000}),
        PARENT_IN_THE_MIDDLE(
                new int[] {500, 500, 1500, 1500},
                new int[] {1000, 500, 1500, 1000});

        /**
         * An int array to present {left, top, right, bottom} of the parent {@link Rect bounds}.
         */
        @NonNull
        private final int[] mParentBounds;

        /**
         * An int array to present {left, top, right, bottom} of the absolute container
         * {@link Rect bounds} after the transition finishes.
         */
        @NonNull
        private final int[] mEndAbsBounds;

        ParentBoundsTestParameters(
                @NonNull int[] parentBounds, @NonNull int[] endAbsBounds) {
            mParentBounds = parentBounds;
            mEndAbsBounds = endAbsBounds;
        }

        @NonNull
        private Rect getParentBounds() {
            return asRect(mParentBounds);
        }

        @NonNull
        private Rect getEndAbsBounds() {
            return asRect(mEndAbsBounds);
        }

        @NonNull
        private static Rect asRect(@NonNull int[] bounds) {
            if (bounds.length != 4) {
                throw new IllegalArgumentException("There must be exactly 4 elements in bounds, "
                        + "but found " + bounds.length + ": " + Arrays.toString(bounds));
            }
            return new Rect(bounds[0], bounds[1], bounds[2], bounds[3]);
        }
    }

    @Test
    public void testShouldUseJumpCutForAnimation() {
        final Animation noopAnimation = new AlphaAnimation(0f, 1f);
        assertTrue("Animation without duration should use jump cut.",
                shouldUseJumpCutForAnimation(noopAnimation));

        final Animation alphaAnimation = new AlphaAnimation(0f, 1f);
        alphaAnimation.setDuration(100);
        assertFalse("Animation with duration should not use jump cut.",
                shouldUseJumpCutForAnimation(alphaAnimation));
    }

    @NonNull
    private static TransitionInfo.Change prepareChangeForParentBoundsCalculationTest(
            @NonNull Point endRelOffset, @NonNull Rect endAbsBounds, @NonNull Point endParentSize) {
        final TransitionInfo.Change change = createChange(0 /* flags */);
        change.setEndRelOffset(endRelOffset.x, endRelOffset.y);
        change.setEndAbsBounds(endAbsBounds);
        change.setEndParentSize(endParentSize.x, endParentSize.y);
        return change;
    }
}
