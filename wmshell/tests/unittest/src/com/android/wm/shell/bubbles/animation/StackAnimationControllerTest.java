/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.bubbles.animation;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.graphics.PointF;
import android.testing.AndroidTestingRunner;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.test.filters.SmallTest;

import com.android.wm.shell.R;
import com.android.wm.shell.bubbles.TestableBubblePositioner;
import com.android.wm.shell.common.FloatingContentCoordinator;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

@SmallTest
@RunWith(AndroidTestingRunner.class)
public class StackAnimationControllerTest extends PhysicsAnimationLayoutTestCase {

    @Mock
    private FloatingContentCoordinator mFloatingContentCoordinator;

    private TestableStackController mStackController;

    private int mStackOffset;
    private Runnable mCheckStartPosSet;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mStackController = spy(new TestableStackController(
                mFloatingContentCoordinator, new IntSupplier() {
                    @Override
                    public int getAsInt() {
                        return mLayout.getChildCount();
                    }
                }, mock(Runnable.class), mock(Runnable.class)));
        mLayout.setActiveController(mStackController);
        addOneMoreThanBubbleLimitBubbles();
        mStackOffset = mLayout.getResources().getDimensionPixelSize(R.dimen.bubble_stack_offset);
    }

    /**
     * Test moving around the stack, and make sure the position is updated correctly, and the stack
     * direction is correct.
     */
    @Test
    @Ignore("Flaking")
    public void testMoveFirstBubbleWithStackFollowing() throws InterruptedException {
        mStackController.moveFirstBubbleWithStackFollowing(200, 100);

        // The first bubble should have moved instantly, the rest should be waiting for animation.
        assertEquals(200, mViews.get(0).getTranslationX(), .1f);
        assertEquals(100, mViews.get(0).getTranslationY(), .1f);
        assertEquals(0, mViews.get(1).getTranslationX(), .1f);
        assertEquals(0, mViews.get(1).getTranslationY(), .1f);

        waitForPropertyAnimations(DynamicAnimation.TRANSLATION_X, DynamicAnimation.TRANSLATION_Y);

        // Make sure the rest of the stack got moved to the right place and is stacked to the left.
        testStackedAtPosition(200, 100, -1);
        assertEquals(new PointF(200, 100), mStackController.getStackPosition());

        mStackController.moveFirstBubbleWithStackFollowing(1000, 500);

        // The first bubble again should have moved instantly while the rest remained where they
        // were until the animation takes over.
        assertEquals(1000, mViews.get(0).getTranslationX(), .1f);
        assertEquals(500, mViews.get(0).getTranslationY(), .1f);
        assertEquals(200 + -mStackOffset, mViews.get(1).getTranslationX(), .1f);
        assertEquals(100, mViews.get(1).getTranslationY(), .1f);

        waitForPropertyAnimations(DynamicAnimation.TRANSLATION_X, DynamicAnimation.TRANSLATION_Y);

        // Make sure the rest of the stack moved again, including the first bubble not moving, and
        // is stacked to the right now that we're on the right side of the screen.
        testStackedAtPosition(1000, 500, 1);
        assertEquals(new PointF(1000, 500), mStackController.getStackPosition());
    }

    @Test
    @Ignore("Sporadically failing due to DynamicAnimation not settling.")
    public void testFlingSideways() throws InterruptedException {
        // Hard fling directly upwards, no X velocity. The X fling should terminate pretty much
        // immediately, and spring to 0f, the y fling is hard enough that it will overshoot the top
        // but should bounce back down.
        mStackController.flingThenSpringFirstBubbleWithStackFollowing(
                DynamicAnimation.TRANSLATION_X,
                5000f, 1.15f, new SpringForce(), mWidth * 1f);
        mStackController.flingThenSpringFirstBubbleWithStackFollowing(
                DynamicAnimation.TRANSLATION_Y,
                0f, 1.15f, new SpringForce(), 0f);

        // Nothing should move initially since the animations haven't begun, including the first
        // view.
        assertEquals(0f, mViews.get(0).getTranslationX(), 1f);
        assertEquals(0f, mViews.get(0).getTranslationY(), 1f);

        // Wait for the flinging.
        waitForPropertyAnimations(DynamicAnimation.TRANSLATION_X,
                DynamicAnimation.TRANSLATION_Y);

        // Wait for the springing.
        waitForPropertyAnimations(DynamicAnimation.TRANSLATION_X,
                DynamicAnimation.TRANSLATION_Y);

        // Once the dust has settled, we should have flung all the way to the right side, with the
        // stack stacked off to the right now.
        testStackedAtPosition(mWidth * 1f, 0f, 1);
    }

    @Test
    @Ignore("Sporadically failing due to DynamicAnimation not settling.")
    public void testFlingUpFromBelowBottomCenter() throws InterruptedException {
        // Move to the center of the screen, just past the bottom.
        mStackController.moveFirstBubbleWithStackFollowing(mWidth / 2f, mHeight + 100);
        waitForPropertyAnimations(DynamicAnimation.TRANSLATION_X, DynamicAnimation.TRANSLATION_Y);

        // Hard fling directly upwards, no X velocity. The X fling should terminate pretty much
        // immediately, and spring to 0f, the y fling is hard enough that it will overshoot the top
        // but should bounce back down.
        mStackController.flingThenSpringFirstBubbleWithStackFollowing(
                DynamicAnimation.TRANSLATION_X,
                0, 1.15f, new SpringForce(), 27f);
        mStackController.flingThenSpringFirstBubbleWithStackFollowing(
                DynamicAnimation.TRANSLATION_Y,
                5000f, 1.15f, new SpringForce(), 27f);

        // Nothing should move initially since the animations haven't begun.
        assertEquals(mWidth / 2f, mViews.get(0).getTranslationX(), .1f);
        assertEquals(mHeight + 100, mViews.get(0).getTranslationY(), .1f);

        waitForPropertyAnimations(DynamicAnimation.TRANSLATION_X,
                DynamicAnimation.TRANSLATION_Y);

        // Once the dust has settled, we should have flung a bit but then sprung to the final
        // destination which is (27, 27).
        testStackedAtPosition(27, 27, -1);
    }

    @Test
    @Ignore("Flaking")
    public void testChildAdded() throws InterruptedException {
        // Move the stack to y = 500.
        mStackController.moveFirstBubbleWithStackFollowing(0f, 500f);
        waitForPropertyAnimations(DynamicAnimation.TRANSLATION_X,
                DynamicAnimation.TRANSLATION_Y);

        final View newView = new FrameLayout(mContext);
        mLayout.addView(
                newView,
                0,
                new FrameLayout.LayoutParams(50, 50));

        waitForStartPosToBeSet();
        waitForLayoutMessageQueue();
        waitForPropertyAnimations(
                DynamicAnimation.TRANSLATION_X,
                DynamicAnimation.TRANSLATION_Y,
                DynamicAnimation.SCALE_X,
                DynamicAnimation.SCALE_Y);

        // The new view should be at the top of the stack, in the correct position.
        assertEquals(0f, newView.getTranslationX(), .1f);
        assertEquals(500f, newView.getTranslationY(), .1f);
        assertEquals(1f, newView.getScaleX(), .1f);
        assertEquals(1f, newView.getScaleY(), .1f);
        assertEquals(1f, newView.getAlpha(), .1f);
    }

    @Test
    @Ignore("Occasionally flakes, ignoring pending investigation.")
    public void testChildRemoved() throws InterruptedException {
        assertEquals(0, mLayout.getTransientViewCount());

        final View firstView = mLayout.getChildAt(0);
        mLayout.removeView(firstView);

        // The view should now be transient, and missing from the view's normal hierarchy.
        assertEquals(1, mLayout.getTransientViewCount());
        assertEquals(-1, mLayout.indexOfChild(firstView));

        waitForPropertyAnimations(DynamicAnimation.ALPHA);
        waitForLayoutMessageQueue();

        // The view should now be gone entirely, no transient views left.
        assertEquals(0, mLayout.getTransientViewCount());

        // The subsequent view should have been translated over to 0, not stacked off to the left.
        assertEquals(0, mLayout.getChildAt(0).getTranslationX(), .1f);
    }

    @Test
    @Ignore("Flaky")
    public void testRestoredAtRestingPosition() throws InterruptedException {
        mStackController.flingStackThenSpringToEdge(0, 5000, 5000);

        waitForPropertyAnimations(
                DynamicAnimation.TRANSLATION_X, DynamicAnimation.TRANSLATION_Y);
        waitForLayoutMessageQueue();

        final PointF prevStackPos = mStackController.getStackPosition();

        mLayout.removeAllViews();

        waitForLayoutMessageQueue();

        mLayout.addView(new FrameLayout(getContext()));

        waitForLayoutMessageQueue();
        waitForPropertyAnimations(
                DynamicAnimation.TRANSLATION_X, DynamicAnimation.TRANSLATION_Y);

        assertEquals(prevStackPos, mStackController.getStackPosition());
    }

    @Test
    public void testFloatingCoordinator() {
        // We should have called onContentAdded only once while adding all of the bubbles in
        // setup().
        verify(mFloatingContentCoordinator, times(1)).onContentAdded(any());
        verify(mFloatingContentCoordinator, never()).onContentRemoved(any());

        // Remove all views and verify that we called onContentRemoved only once.
        while (mLayout.getChildCount() > 0) {
            mLayout.removeView(mLayout.getChildAt(0));
        }

        verify(mFloatingContentCoordinator, times(1)).onContentRemoved(any());
    }

    /**
     * Checks every child view to make sure it's stacked at the given coordinates, off to the left
     * or right side depending on offset multiplier.
     */
    private void testStackedAtPosition(float x, float y, int offsetMultiplier) {
        // Make sure the rest of the stack moved again, including the first bubble not moving, and
        // is stacked to the right now that we're on the right side of the screen.
        for (int i = 0; i < mLayout.getChildCount(); i++) {
            assertEquals(x + i * offsetMultiplier * mStackOffset,
                    mViews.get(i).getTranslationX(), 2f);
            assertEquals(y, mViews.get(i).getTranslationY(), 2f);
        }
    }

    /** Waits up to 2 seconds for the initial stack position to be initialized. */
    private void waitForStartPosToBeSet() throws InterruptedException {
        final CountDownLatch animLatch = new CountDownLatch(1);

        mCheckStartPosSet = () -> {
            if (mStackController.getStackPosition().x >= 0) {
                animLatch.countDown();
            } else {
                mMainThreadHandler.post(mCheckStartPosSet);
            }
        };

        mMainThreadHandler.post(mCheckStartPosSet);

        try {
            animLatch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            mMainThreadHandler.removeCallbacks(mCheckStartPosSet);
            throw e;
        }
    }

    /**
     * Testable version of the stack controller that dispatches its animations on the main thread.
     */
    private class TestableStackController extends StackAnimationController {
        TestableStackController(
                FloatingContentCoordinator floatingContentCoordinator,
                IntSupplier bubbleCountSupplier,
                Runnable onBubbleAnimatedOutAction,
                Runnable onStackAnimationFinished) {
            super(floatingContentCoordinator,
                    bubbleCountSupplier,
                    onBubbleAnimatedOutAction,
                    onStackAnimationFinished,
                    new TestableBubblePositioner(mContext,
                            mContext.getSystemService(WindowManager.class)));
        }

        @Override
        protected void flingThenSpringFirstBubbleWithStackFollowing(
                DynamicAnimation.ViewProperty property, float vel, float friction,
                SpringForce spring, Float finalPosition) {
            mMainThreadHandler.post(() ->
                    super.flingThenSpringFirstBubbleWithStackFollowing(
                            property, vel, friction, spring, finalPosition));
        }

        @Override
        protected void springFirstBubbleWithStackFollowing(DynamicAnimation.ViewProperty property,
                SpringForce spring, float vel, float finalPosition, Runnable... after) {
            mMainThreadHandler.post(() ->
                    super.springFirstBubbleWithStackFollowing(
                            property, spring, vel, finalPosition, after));
        }
    }
}
