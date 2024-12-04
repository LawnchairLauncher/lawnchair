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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.SystemClock;
import android.testing.AndroidTestingRunner;
import android.view.View;
import android.widget.FrameLayout;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.test.filters.SmallTest;

import com.google.android.collect.Sets;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.Spy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SmallTest
@RunWith(AndroidTestingRunner.class)
/** Tests the PhysicsAnimationLayout itself, with a basic test animation controller. */
public class PhysicsAnimationLayoutTest extends PhysicsAnimationLayoutTestCase {
    static final float TEST_TRANSLATION_X_OFFSET = 15f;

    @Spy
    private TestableAnimationController mTestableController = new TestableAnimationController();

    @Before
    public void setUp() throws Exception {
        super.setUp();

        // By default, use translation animations, chain the X animations with the default
        // offset, and don't actually remove views immediately (since most implementations will wait
        // to animate child views out before actually removing them).
        mTestableController.setAnimatedProperties(Sets.newHashSet(
                DynamicAnimation.TRANSLATION_X,
                DynamicAnimation.TRANSLATION_Y,
                DynamicAnimation.TRANSLATION_Z));
        mTestableController.setChainedProperties(Sets.newHashSet(DynamicAnimation.TRANSLATION_X));
        mTestableController.setOffsetForProperty(
                DynamicAnimation.TRANSLATION_X, TEST_TRANSLATION_X_OFFSET);
        mTestableController.setRemoveImmediately(false);
    }

    @Test
    @Ignore
    public void testHierarchyChanges() throws InterruptedException {
        mLayout.setActiveController(mTestableController);
        addOneMoreThanBubbleLimitBubbles();

        // Make sure the controller was notified of all the views we added.
        for (View mView : mViews) {
            Mockito.verify(mTestableController).onChildAdded(mView, 0);
        }

        // Remove some views and ensure the controller was notified, with the proper indices.
        mTestableController.setRemoveImmediately(true);
        mLayout.removeView(mViews.get(1));
        mLayout.removeView(mViews.get(2));
        Mockito.verify(mTestableController).onChildRemoved(
                eq(mViews.get(1)), eq(1), any());
        Mockito.verify(mTestableController).onChildRemoved(
                eq(mViews.get(2)), eq(1), any());

        // Make sure we still get view added notifications after doing some removals.
        final View newBubble = new FrameLayout(mContext);
        mLayout.addView(newBubble, 0);
        Mockito.verify(mTestableController).onChildAdded(newBubble, 0);
    }

    @Test
    @Ignore
    public void testUpdateValueNotChained() throws InterruptedException {
        mLayout.setActiveController(mTestableController);
        addOneMoreThanBubbleLimitBubbles();

        // Don't chain any values.
        mTestableController.setChainedProperties(Sets.newHashSet());

        // Child views should not be translated.
        assertEquals(0, mLayout.getChildAt(0).getTranslationX(), .1f);
        assertEquals(0, mLayout.getChildAt(1).getTranslationX(), .1f);

        // Animate the first child's translation X.
        final CountDownLatch animLatch = new CountDownLatch(1);

        mTestableController
                .animationForChildAtIndex(0)
                .translationX(100)
                .start(animLatch::countDown);
        animLatch.await(1, TimeUnit.SECONDS);

        // Ensure that the first view has been translated, but not the second one.
        assertEquals(100, mLayout.getChildAt(0).getTranslationX(), .1f);
        assertEquals(0, mLayout.getChildAt(1).getTranslationX(), .1f);
    }

    @Test
    @Ignore
    public void testUpdateValueXChained() throws InterruptedException {
        testChainedTranslationAnimations();
    }

    @Test
    @Ignore
    public void testSetEndActions() throws InterruptedException {
        mLayout.setActiveController(mTestableController);
        addOneMoreThanBubbleLimitBubbles();
        mTestableController.setChainedProperties(Sets.newHashSet());

        final CountDownLatch xLatch = new CountDownLatch(1);
        Runnable xEndAction = Mockito.spy(new Runnable() {

            @Override
            public void run() {
                xLatch.countDown();
            }
        });

        final CountDownLatch yLatch = new CountDownLatch(1);
        Runnable yEndAction = Mockito.spy(new Runnable() {

            @Override
            public void run() {
                yLatch.countDown();
            }
        });

        // Set end listeners for both x and y.
        mTestableController.setEndActionForProperty(xEndAction, DynamicAnimation.TRANSLATION_X);
        mTestableController.setEndActionForProperty(yEndAction, DynamicAnimation.TRANSLATION_Y);

        // Animate x, and wait for it to finish.
        mTestableController.animationForChildAtIndex(0)
                .translationX(100)
                .start();

        xLatch.await();
        yLatch.await(1, TimeUnit.SECONDS);

        // Make sure the x end listener was called only one time, and the y listener was never
        // called since we didn't animate y. Wait 1 second after the original animation end trigger
        // to make sure it doesn't get called again.
        Mockito.verify(xEndAction, Mockito.after(1000).times(1)).run();
        Mockito.verify(yEndAction, Mockito.after(1000).never()).run();
    }

    @Test
    @Ignore
    public void testRemoveEndListeners() throws InterruptedException {
        mLayout.setActiveController(mTestableController);
        addOneMoreThanBubbleLimitBubbles();
        mTestableController.setChainedProperties(Sets.newHashSet());

        final CountDownLatch xLatch = new CountDownLatch(1);
        Runnable xEndListener = Mockito.spy(new Runnable() {

            @Override
            public void run() {
                xLatch.countDown();
            }
        });

        // Set the end listener.
        mTestableController.setEndActionForProperty(xEndListener, DynamicAnimation.TRANSLATION_X);

        // Animate x, and wait for it to finish.
        mTestableController.animationForChildAtIndex(0)
                .translationX(100)
                .start();
        xLatch.await();

        InOrder endListenerCalls = inOrder(xEndListener);
        endListenerCalls.verify(xEndListener, Mockito.times(1)).run();

        // Animate X again, remove the end listener.
        mTestableController.animationForChildAtIndex(0)
                .translationX(1000)
                .start();
        mTestableController.removeEndActionForProperty(DynamicAnimation.TRANSLATION_X);
        xLatch.await(1, TimeUnit.SECONDS);

        // Make sure the end listener was not called.
        endListenerCalls.verifyNoMoreInteractions();
    }

    @Test
    @Ignore
    public void testSetController() throws InterruptedException {
        // Add the bubbles, then set the controller, to make sure that a controller added to an
        // already-initialized view works correctly.
        addOneMoreThanBubbleLimitBubbles();
        mLayout.setActiveController(mTestableController);
        testChainedTranslationAnimations();

        TestableAnimationController secondController =
                Mockito.spy(new TestableAnimationController());
        secondController.setAnimatedProperties(Sets.newHashSet(
                DynamicAnimation.SCALE_X, DynamicAnimation.SCALE_Y));
        secondController.setChainedProperties(Sets.newHashSet(
                DynamicAnimation.SCALE_X));
        secondController.setOffsetForProperty(
                DynamicAnimation.SCALE_X, 10f);
        secondController.setRemoveImmediately(true);

        mLayout.setActiveController(secondController);
        mTestableController.animationForChildAtIndex(0)
                .scaleX(1.5f)
                .start();

        waitForPropertyAnimations(DynamicAnimation.SCALE_X);

        // Make sure we never asked the original controller about any SCALE animations, that would
        // mean the controller wasn't switched over properly.
        Mockito.verify(mTestableController, Mockito.never())
                .getNextAnimationInChain(eq(DynamicAnimation.SCALE_X), anyInt());
        Mockito.verify(mTestableController, Mockito.never())
                .getOffsetForChainedPropertyAnimation(eq(DynamicAnimation.SCALE_X), anyInt());

        // Make sure we asked the new controller about its animated properties, and configuration
        // options.
        Mockito.verify(secondController, Mockito.atLeastOnce())
                .getAnimatedProperties();
        Mockito.verify(secondController, Mockito.atLeastOnce())
                .getNextAnimationInChain(eq(DynamicAnimation.SCALE_X), anyInt());
        Mockito.verify(secondController, Mockito.atLeastOnce())
                .getOffsetForChainedPropertyAnimation(eq(DynamicAnimation.SCALE_X), anyInt());

        mLayout.setActiveController(mTestableController);
        mTestableController.animationForChildAtIndex(0)
                .translationX(100f)
                .start();

        waitForPropertyAnimations(DynamicAnimation.TRANSLATION_X);

        // Make sure we never asked the second controller about the TRANSLATION_X animation.
        Mockito.verify(secondController, Mockito.never())
                .getNextAnimationInChain(eq(DynamicAnimation.TRANSLATION_X), anyInt());
        Mockito.verify(secondController, Mockito.never())
                .getOffsetForChainedPropertyAnimation(eq(DynamicAnimation.TRANSLATION_X), anyInt());

    }

    @Test
    @Ignore
    public void testArePropertiesAnimating() throws InterruptedException {
        mLayout.setActiveController(mTestableController);
        addOneMoreThanBubbleLimitBubbles();

        assertFalse(mLayout.arePropertiesAnimating(
                DynamicAnimation.TRANSLATION_X,
                DynamicAnimation.TRANSLATION_Y,
                DynamicAnimation.TRANSLATION_Z));

        mTestableController.animationForChildAtIndex(0)
                .translationX(100f)
                .translationZ(100f)
                .start();

        // Wait for the animations to get underway.
        SystemClock.sleep(50);

        assertTrue(mLayout.arePropertiesAnimating(DynamicAnimation.TRANSLATION_X));
        assertFalse(mLayout.arePropertiesAnimating(DynamicAnimation.TRANSLATION_Y));
        assertTrue(mLayout.arePropertiesAnimating(DynamicAnimation.TRANSLATION_Z));

        waitForPropertyAnimations(DynamicAnimation.TRANSLATION_X, DynamicAnimation.TRANSLATION_Z);

        assertFalse(mLayout.arePropertiesAnimating(
                DynamicAnimation.TRANSLATION_X, DynamicAnimation.TRANSLATION_Y,
                DynamicAnimation.TRANSLATION_Z));
    }

    @Test
    @Ignore
    public void testCancelAllAnimations() throws InterruptedException {
        mLayout.setActiveController(mTestableController);
        addOneMoreThanBubbleLimitBubbles();

        mTestableController.animationForChildAtIndex(0)
                .position(1000, 1000, 1000)
                .start();

        mLayout.cancelAllAnimations();

        // Animations should be somewhere before their end point.
        assertTrue(mViews.get(0).getTranslationX() < 1000);
        assertTrue(mViews.get(0).getTranslationY() < 1000);
        assertTrue(mViews.get(0).getZ() < 10000);
    }

    /** Standard test of chained translation animations. */
    private void testChainedTranslationAnimations() throws InterruptedException {
        mLayout.setActiveController(mTestableController);
        addOneMoreThanBubbleLimitBubbles();

        assertEquals(0, mLayout.getChildAt(0).getTranslationX(), .1f);
        assertEquals(0, mLayout.getChildAt(1).getTranslationX(), .1f);

        mTestableController.animationForChildAtIndex(0)
                .translationX(100f)
                .start();

        waitForPropertyAnimations(DynamicAnimation.TRANSLATION_X);

        for (int i = 0; i < mLayout.getChildCount(); i++) {
            assertEquals(
                    100 + i * TEST_TRANSLATION_X_OFFSET,
                    mLayout.getChildAt(i).getTranslationX(), .1f);
        }

        // Ensure that the Y translations were unaffected.
        assertEquals(0, mLayout.getChildAt(0).getTranslationY(), .1f);
        assertEquals(0, mLayout.getChildAt(1).getTranslationY(), .1f);

        // Animate the first child's Y translation.
        mTestableController.animationForChildAtIndex(0)
                .translationY(100f)
                .start();

        waitForPropertyAnimations(DynamicAnimation.TRANSLATION_Y);

        // Ensure that only the first view's Y translation chained, since we only chained X
        // translations.
        assertEquals(100, mLayout.getChildAt(0).getTranslationY(), .1f);
        assertEquals(0, mLayout.getChildAt(1).getTranslationY(), .1f);
    }

    @Test
    @Ignore
    public void testPhysicsAnimator() throws InterruptedException {
        mLayout.setActiveController(mTestableController);
        addOneMoreThanBubbleLimitBubbles();

        Runnable afterAll = Mockito.mock(Runnable.class);
        Runnable after = Mockito.spy(new Runnable() {
            int mCallCount = 0;

            @Override
            public void run() {
                // Make sure that if only one of the animations has finished, we didn't already call
                // afterAll.
                if (mCallCount == 1) {
                    Mockito.verifyNoMoreInteractions(afterAll);
                }
            }
        });

        // Animate from x = 7 to x = 100, and from y = 100 to 7 = 200, calling 'after' after each
        // property's animation completes, then call afterAll when they're all complete.
        mTestableController.animationForChildAtIndex(0)
                .translationX(7, 100, after)
                .translationY(100, 200, after)
                .start(afterAll);

        // We should have immediately set the 'from' values.
        assertEquals(7, mViews.get(0).getTranslationX(), .01f);
        assertEquals(100, mViews.get(0).getTranslationY(), .01f);

        waitForPropertyAnimations(
                DynamicAnimation.TRANSLATION_X,
                DynamicAnimation.TRANSLATION_Y);

        // We should have called the after callback twice, and afterAll once. We verify in the
        // mocked callback that afterAll isn't called before both finish.
        Mockito.verify(after, times(2)).run();
        Mockito.verify(afterAll).run();

        // Make sure we actually animated the views.
        assertEquals(100, mViews.get(0).getTranslationX(), .01f);
        assertEquals(200, mViews.get(0).getTranslationY(), .01f);
    }

    @Test
    @Ignore
    public void testAnimationsForChildrenFromIndex() throws InterruptedException {
        // Don't chain since we're going to invoke each animation independently.
        mTestableController.setChainedProperties(new HashSet<>());

        mLayout.setActiveController(mTestableController);

        addOneMoreThanBubbleLimitBubbles();

        Runnable allEnd = Mockito.mock(Runnable.class);

        mTestableController.animationsForChildrenFromIndex(
                1, (index, animation) -> animation.translationX((index - 1) * 50))
            .startAll(allEnd);

        waitForPropertyAnimations(DynamicAnimation.TRANSLATION_X);

        assertEquals(0, mViews.get(0).getTranslationX(), .1f);
        assertEquals(0, mViews.get(1).getTranslationX(), .1f);
        assertEquals(50, mViews.get(2).getTranslationX(), .1f);
        assertEquals(100, mViews.get(3).getTranslationX(), .1f);

        Mockito.verify(allEnd, times(1)).run();
    }

    @Test
    @Ignore
    public void testAnimationsForChildrenFromIndex_noChildren() {
        mLayout.setActiveController(mTestableController);

        final Runnable after = Mockito.mock(Runnable.class);
        mTestableController
                .animationsForChildrenFromIndex(0, (index, animation) -> { })
                .startAll(after);

        verify(after, Mockito.times(1)).run();
    }

    /**
     * Animation controller with configuration methods whose return values can be set by individual
     * tests.
     */
    private class TestableAnimationController
            extends PhysicsAnimationLayout.PhysicsAnimationController {
        private Set<DynamicAnimation.ViewProperty> mAnimatedProperties = new HashSet<>();
        private Set<DynamicAnimation.ViewProperty> mChainedProperties = new HashSet<>();
        private HashMap<DynamicAnimation.ViewProperty, Float> mOffsetForProperty = new HashMap<>();
        private boolean mRemoveImmediately = false;

        void setAnimatedProperties(
                Set<DynamicAnimation.ViewProperty> animatedProperties) {
            mAnimatedProperties = animatedProperties;
        }

        void setChainedProperties(
                Set<DynamicAnimation.ViewProperty> chainedProperties) {
            mChainedProperties = chainedProperties;
        }

        void setOffsetForProperty(
                DynamicAnimation.ViewProperty property, float offset) {
            mOffsetForProperty.put(property, offset);
        }

        public void setRemoveImmediately(boolean removeImmediately) {
            mRemoveImmediately = removeImmediately;
        }

        @Override
        Set<DynamicAnimation.ViewProperty> getAnimatedProperties() {
            return mAnimatedProperties;
        }

        @Override
        int getNextAnimationInChain(DynamicAnimation.ViewProperty property, int index) {
            return mChainedProperties.contains(property) ? index + 1 : NONE;
        }

        @Override
        float getOffsetForChainedPropertyAnimation(DynamicAnimation.ViewProperty property,
                int index) {
            return mOffsetForProperty.getOrDefault(property, 0f);
        }

        @Override
        SpringForce getSpringForce(DynamicAnimation.ViewProperty property, View view) {
            return new SpringForce();
        }

        @Override
        void onChildAdded(View child, int index) {}

        @Override
        void onChildRemoved(View child, int index, Runnable finishRemoval) {
            if (mRemoveImmediately) {
                finishRemoval.run();
            }
        }

        @Override
        void onChildReordered(View child, int oldIndex, int newIndex) {}

        @Override
        void onActiveControllerForLayout(PhysicsAnimationLayout layout) {}
    }
}
