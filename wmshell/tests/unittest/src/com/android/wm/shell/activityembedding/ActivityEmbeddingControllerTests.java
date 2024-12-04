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

import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_OPEN;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.view.SurfaceControl;
import android.window.TransitionInfo;

import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.window.flags.Flags;
import com.android.wm.shell.transition.TransitionInfoBuilder;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link ActivityEmbeddingController}.
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:ActivityEmbeddingControllerTests
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ActivityEmbeddingControllerTests extends ActivityEmbeddingAnimationTestBase {

    private static final Rect TASK_BOUNDS = new Rect(0, 0, 1000, 500);
    private static final Rect EMBEDDED_LEFT_BOUNDS = new Rect(0, 0, 500, 500);
    private static final Rect EMBEDDED_RIGHT_BOUNDS = new Rect(500, 0, 1000, 500);

    @Rule
    public SetFlagsRule mRule = new SetFlagsRule();

    @Before
    public void setup() {
        super.setUp();
        doReturn(mAnimator).when(mAnimRunner).createAnimator(any(), any(), any(), any(),
                any());
    }

    @EnableFlags(Flags.FLAG_MOVE_ANIMATION_OPTIONS_TO_CHANGE)
    @Test
    public void testInstantiate() {
        verify(mShellInit).addInitCallback(any(), any());
    }

    @EnableFlags(Flags.FLAG_MOVE_ANIMATION_OPTIONS_TO_CHANGE)
    @Test
    public void testOnInit() {
        mController.onInit();

        verify(mTransitions).addHandler(mController);
    }

    @EnableFlags(Flags.FLAG_MOVE_ANIMATION_OPTIONS_TO_CHANGE)
    @Test
    public void testSetAnimScaleSetting() {
        mController.setAnimScaleSetting(1.0f);

        verify(mAnimRunner).setAnimScaleSetting(1.0f);
        verify(mAnimSpec).setAnimScaleSetting(1.0f);
    }

    @EnableFlags(Flags.FLAG_MOVE_ANIMATION_OPTIONS_TO_CHANGE)
    @Test
    public void testStartAnimation_containsNonActivityEmbeddingChange() {
        final TransitionInfo.Change nonEmbeddedOpen = createChange(0 /* flags */);
        final TransitionInfo.Change embeddedOpen = createEmbeddedChange(
                EMBEDDED_LEFT_BOUNDS, EMBEDDED_LEFT_BOUNDS, TASK_BOUNDS);
        nonEmbeddedOpen.setMode(TRANSIT_OPEN);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN, 0)
                .addChange(embeddedOpen)
                .addChange(nonEmbeddedOpen)
                .build();

        // No-op because it contains non-embedded change.
        assertFalse(mController.startAnimation(mTransition, info, mStartTransaction,
                mFinishTransaction, mFinishCallback));
        verify(mAnimRunner, never()).startAnimation(any(), any(), any(), any());
        verifyNoMoreInteractions(mStartTransaction);
        verifyNoMoreInteractions(mFinishTransaction);
        verifyNoMoreInteractions(mFinishCallback);

        final TransitionInfo.Change nonEmbeddedClose = createChange(0 /* flags */);
        nonEmbeddedClose.setMode(TRANSIT_CLOSE);
        nonEmbeddedClose.setEndAbsBounds(TASK_BOUNDS);
        final TransitionInfo.Change embeddedOpen2 = createEmbeddedChange(
                EMBEDDED_RIGHT_BOUNDS, EMBEDDED_RIGHT_BOUNDS, TASK_BOUNDS);
        final TransitionInfo info2 = new TransitionInfoBuilder(TRANSIT_OPEN, 0)
                .addChange(embeddedOpen)
                .addChange(embeddedOpen2)
                .addChange(nonEmbeddedClose)
                .build();
        // Ok to animate because nonEmbeddedClose is occluded by embeddedOpen and embeddedOpen2.
        assertTrue(mController.startAnimation(mTransition, info2, mStartTransaction,
                mFinishTransaction, mFinishCallback));
        // The non-embedded change is dropped to avoid affecting embedded animation.
        assertFalse(info2.getChanges().contains(nonEmbeddedClose));
    }

    @EnableFlags(Flags.FLAG_MOVE_ANIMATION_OPTIONS_TO_CHANGE)
    @Test
    public void testStartAnimation_containsOnlyFillTaskActivityEmbeddingChange() {
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN, 0)
                .addChange(createEmbeddedChange(TASK_BOUNDS, TASK_BOUNDS, TASK_BOUNDS))
                .build();

        // No-op because it only contains embedded change that fills the Task. We will let the
        // default handler to animate such transition.
        assertFalse(mController.startAnimation(mTransition, info, mStartTransaction,
                mFinishTransaction, mFinishCallback));
        verify(mAnimRunner, never()).startAnimation(any(), any(), any(), any());
        verifyNoMoreInteractions(mStartTransaction);
        verifyNoMoreInteractions(mFinishTransaction);
        verifyNoMoreInteractions(mFinishCallback);
    }

    @EnableFlags(Flags.FLAG_MOVE_ANIMATION_OPTIONS_TO_CHANGE)
    @Test
    public void testStartAnimation_containsActivityEmbeddingSplitChange() {
        // Change that occupies only part of the Task.
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN, 0)
                .addChange(createEmbeddedChange(
                        EMBEDDED_LEFT_BOUNDS, EMBEDDED_LEFT_BOUNDS, TASK_BOUNDS))
                .build();

        // ActivityEmbeddingController will handle such transition.
        assertTrue(mController.startAnimation(mTransition, info, mStartTransaction,
                mFinishTransaction, mFinishCallback));
        verify(mAnimRunner).startAnimation(mTransition, info, mStartTransaction,
                mFinishTransaction);
        verify(mStartTransaction).apply();
        verifyNoMoreInteractions(mFinishTransaction);
    }

    @EnableFlags(Flags.FLAG_MOVE_ANIMATION_OPTIONS_TO_CHANGE)
    @Test
    public void testStartAnimation_containsChangeEnterActivityEmbeddingSplit() {
        // Change that is entering ActivityEmbedding split.
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN, 0)
                .addChange(createEmbeddedChange(TASK_BOUNDS, EMBEDDED_LEFT_BOUNDS, TASK_BOUNDS))
                .build();

        // ActivityEmbeddingController will handle such transition.
        assertTrue(mController.startAnimation(mTransition, info, mStartTransaction,
                mFinishTransaction, mFinishCallback));
        verify(mAnimRunner).startAnimation(mTransition, info, mStartTransaction,
                mFinishTransaction);
        verify(mStartTransaction).apply();
        verifyNoMoreInteractions(mFinishTransaction);
    }

    @EnableFlags(Flags.FLAG_MOVE_ANIMATION_OPTIONS_TO_CHANGE)
    @Test
    public void testStartAnimation_containsChangeExitActivityEmbeddingSplit() {
        // Change that is exiting ActivityEmbedding split.
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN, 0)
                .addChange(createEmbeddedChange(EMBEDDED_RIGHT_BOUNDS, TASK_BOUNDS, TASK_BOUNDS))
                .build();

        // ActivityEmbeddingController will handle such transition.
        assertTrue(mController.startAnimation(mTransition, info, mStartTransaction,
                mFinishTransaction, mFinishCallback));
        verify(mAnimRunner).startAnimation(mTransition, info, mStartTransaction,
                mFinishTransaction);
        verify(mStartTransaction).apply();
        verifyNoMoreInteractions(mFinishTransaction);
    }

    @DisableFlags(Flags.FLAG_MOVE_ANIMATION_OPTIONS_TO_CHANGE)
    @Test
    public void testShouldAnimate_containsAnimationOptions_disableAnimOptionsPerChange() {
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_CLOSE, 0)
                .addChange(createEmbeddedChange(EMBEDDED_RIGHT_BOUNDS, TASK_BOUNDS, TASK_BOUNDS))
                .build();

        info.setAnimationOptions(TransitionInfo.AnimationOptions
                .makeCustomAnimOptions("packageName", 0 /* enterResId */, 0 /* exitResId */,
                        0 /* backgroundColor */, false /* overrideTaskTransition */));
        assertTrue(mController.shouldAnimate(info));

        info.setAnimationOptions(TransitionInfo.AnimationOptions
                .makeSceneTransitionAnimOptions());
        assertFalse(mController.shouldAnimate(info));

        info.setAnimationOptions(TransitionInfo.AnimationOptions.makeCrossProfileAnimOptions());
        assertFalse(mController.shouldAnimate(info));
    }

    @EnableFlags(Flags.FLAG_MOVE_ANIMATION_OPTIONS_TO_CHANGE)
    @Test
    public void testShouldAnimate_containsAnimationOptions_enableAnimOptionsPerChange() {
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_CLOSE, 0)
                .addChange(createEmbeddedChange(EMBEDDED_RIGHT_BOUNDS, TASK_BOUNDS, TASK_BOUNDS))
                .build();
        final TransitionInfo.Change change = info.getChanges().getFirst();

        change.setAnimationOptions(TransitionInfo.AnimationOptions
                .makeCustomAnimOptions("packageName", 0 /* enterResId */, 0 /* exitResId */,
                        0 /* backgroundColor */, false /* overrideTaskTransition */));
        assertTrue(mController.shouldAnimate(info));

        change.setAnimationOptions(TransitionInfo.AnimationOptions
                .makeSceneTransitionAnimOptions());
        assertFalse(mController.shouldAnimate(info));

        change.setAnimationOptions(TransitionInfo.AnimationOptions.makeCrossProfileAnimOptions());
        assertFalse(mController.shouldAnimate(info));
    }

    @EnableFlags(Flags.FLAG_MOVE_ANIMATION_OPTIONS_TO_CHANGE)
    @UiThreadTest
    @Test
    public void testMergeAnimation() {
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN, 0)
                .addChange(createEmbeddedChange(
                        EMBEDDED_LEFT_BOUNDS, EMBEDDED_LEFT_BOUNDS, TASK_BOUNDS))
                .build();

        final ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mController.onAnimationFinished(mTransition);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });
        doReturn(animator).when(mAnimRunner).createAnimator(any(), any(), any(), any(), any());
        mController.startAnimation(mTransition, info, mStartTransaction,
                mFinishTransaction, mFinishCallback);
        verify(mFinishCallback, never()).onTransitionFinished(any());
        mController.mergeAnimation(mTransition, info, new SurfaceControl.Transaction(),
                mTransition, (wct) -> {});
        verify(mFinishCallback).onTransitionFinished(any());
    }

    @EnableFlags(Flags.FLAG_MOVE_ANIMATION_OPTIONS_TO_CHANGE)
    @Test
    public void testOnAnimationFinished() {
        // Should not call finish when there is no transition.
        assertThrows(IllegalStateException.class,
                () -> mController.onAnimationFinished(mTransition));

        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN, 0)
                .addChange(createEmbeddedChange(
                        EMBEDDED_LEFT_BOUNDS, EMBEDDED_LEFT_BOUNDS, TASK_BOUNDS))
                .build();
        mController.startAnimation(mTransition, info, mStartTransaction,
                mFinishTransaction, mFinishCallback);

        verify(mFinishCallback, never()).onTransitionFinished(any());
        mController.onAnimationFinished(mTransition);
        verify(mFinishCallback).onTransitionFinished(any());

        // Should not call finish when the finish has already been called.
        assertThrows(IllegalStateException.class,
                () -> mController.onAnimationFinished(mTransition));
    }
}
