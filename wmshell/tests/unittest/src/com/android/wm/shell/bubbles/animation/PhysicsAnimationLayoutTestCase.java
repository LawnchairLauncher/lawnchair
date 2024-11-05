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

import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.DisplayCutout;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.android.wm.shell.R;
import com.android.wm.shell.ShellTestCase;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test case for tests that involve the {@link PhysicsAnimationLayout}. This test case constructs a
 * testable version of the layout, and provides some helpful methods to add views to the layout and
 * wait for physics animations to finish running.
 *
 * See physics-animation-testing.md.
 */
public class PhysicsAnimationLayoutTestCase extends ShellTestCase {
    TestablePhysicsAnimationLayout mLayout;
    List<View> mViews = new ArrayList<>();

    Handler mMainThreadHandler;

    int mSystemWindowInsetSize = 50;
    int mCutoutInsetSize = 100;

    int mWidth = 1000;
    int mHeight = 1000;

    @Mock
    private WindowInsets mWindowInsets;

    @Mock
    private DisplayCutout mCutout;

    protected int mMaxBubbles;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mLayout = new TestablePhysicsAnimationLayout(mContext);
        mLayout.setLeft(0);
        mLayout.setRight(mWidth);
        mLayout.setTop(0);
        mLayout.setBottom(mHeight);

        mMaxBubbles =
                getContext().getResources().getInteger(R.integer.bubbles_max_rendered);
        mMainThreadHandler = new Handler(Looper.getMainLooper());

        when(mWindowInsets.getSystemWindowInsetTop()).thenReturn(mSystemWindowInsetSize);
        when(mWindowInsets.getSystemWindowInsetBottom()).thenReturn(mSystemWindowInsetSize);
        when(mWindowInsets.getSystemWindowInsetLeft()).thenReturn(mSystemWindowInsetSize);
        when(mWindowInsets.getSystemWindowInsetRight()).thenReturn(mSystemWindowInsetSize);

        when(mWindowInsets.getDisplayCutout()).thenReturn(mCutout);
        when(mCutout.getSafeInsetTop()).thenReturn(mCutoutInsetSize);
        when(mCutout.getSafeInsetBottom()).thenReturn(mCutoutInsetSize);
        when(mCutout.getSafeInsetLeft()).thenReturn(mCutoutInsetSize);
        when(mCutout.getSafeInsetRight()).thenReturn(mCutoutInsetSize);
    }

    /** Add one extra bubble over the limit, so we can make sure it's gone/chains appropriately. */
    void addOneMoreThanBubbleLimitBubbles() throws InterruptedException {
        for (int i = 0; i < mMaxBubbles + 1; i++) {
            final View newView = new FrameLayout(mContext);
            mLayout.addView(newView, 0);
            mViews.add(0, newView);

            newView.setTranslationX(0);
            newView.setTranslationY(0);
        }
    }

    /**
     * Uses a {@link java.util.concurrent.CountDownLatch} to wait for the given properties'
     * animations to finish before allowing the test to proceed.
     */
    void waitForPropertyAnimations(DynamicAnimation.ViewProperty... properties)
            throws InterruptedException {
        final CountDownLatch animLatch = new CountDownLatch(properties.length);
        for (DynamicAnimation.ViewProperty property : properties) {
            mLayout.setTestEndActionForProperty(animLatch::countDown, property);
        }

        animLatch.await(2, TimeUnit.SECONDS);
    }

    /** Uses a latch to wait for the main thread message queue to finish. */
    void waitForLayoutMessageQueue() throws InterruptedException {
        CountDownLatch layoutLatch = new CountDownLatch(1);
        mMainThreadHandler.post(layoutLatch::countDown);
        layoutLatch.await(2, TimeUnit.SECONDS);
    }

    /**
     * Testable subclass of the PhysicsAnimationLayout that ensures methods that trigger animations
     * are run on the main thread, which is a requirement of DynamicAnimation.
     */
    protected class TestablePhysicsAnimationLayout extends PhysicsAnimationLayout {
        public TestablePhysicsAnimationLayout(Context context) {
            super(context);
        }

        @Override
        protected boolean isActiveController(PhysicsAnimationController controller) {
            // Return true since otherwise all test controllers will be seen as inactive since they
            // are wrapped by MainThreadAnimationControllerWrapper.
            return true;
        }

        @Override
        public boolean post(Runnable action) {
            return mMainThreadHandler.post(action);
        }

        @Override
        public boolean postDelayed(Runnable action, long delayMillis) {
            return mMainThreadHandler.postDelayed(action, delayMillis);
        }

        @Override
        public void setActiveController(PhysicsAnimationController controller) {
            runOnMainThreadAndBlock(
                    () -> super.setActiveController(
                            new MainThreadAnimationControllerWrapper(controller)));
        }

        @Override
        public void cancelAllAnimations() {
            if (mLayout.getChildCount() == 0) {
                return;
            }
            mMainThreadHandler.post(super::cancelAllAnimations);
        }

        @Override
        public void cancelAnimationsOnView(View view) {
            if (mLayout.getChildCount() == 0) {
                return;
            }
            mMainThreadHandler.post(() -> super.cancelAnimationsOnView(view));
        }

        @Override
        public WindowInsets getRootWindowInsets() {
            return mWindowInsets;
        }

        @Override
        public void addView(View child, int index) {
            child.setTag(R.id.physics_animator_tag, new TestablePhysicsPropertyAnimator(child));
            super.addView(child, index);
        }

        @Override
        public void addView(View child, int index, ViewGroup.LayoutParams params) {
            child.setTag(R.id.physics_animator_tag, new TestablePhysicsPropertyAnimator(child));
            super.addView(child, index, params);
        }

        /**
         * Sets an end action that will be called after the 'real' end action that was already set.
         */
        private void setTestEndActionForProperty(
                Runnable action, DynamicAnimation.ViewProperty property) {
            final Runnable realEndAction = mEndActionForProperty.get(property);
            mLayout.mEndActionForProperty.put(property, () -> {
                if (realEndAction != null) {
                    realEndAction.run();
                }

                action.run();
            });
        }

        /** PhysicsPropertyAnimator that posts its animations to the main thread. */
        protected class TestablePhysicsPropertyAnimator extends PhysicsPropertyAnimator {
            public TestablePhysicsPropertyAnimator(View view) {
                super(view);
            }

            @Override
            protected void animateValueForChild(DynamicAnimation.ViewProperty property, View view,
                    float value, float startVel, long startDelay, float stiffness,
                    float dampingRatio, Runnable[] afterCallbacks) {
                mMainThreadHandler.post(() -> super.animateValueForChild(
                        property, view, value, startVel, startDelay, stiffness, dampingRatio,
                        afterCallbacks));
            }

            @Override
            protected void startPathAnimation() {
                if (mLayout.getChildCount() == 0) {
                    return;
                }
                mMainThreadHandler.post(super::startPathAnimation);
            }
        }

        /**
         * Wrapper around an animation controller that dispatches methods that could start
         * animations to the main thread.
         */
        protected class MainThreadAnimationControllerWrapper extends PhysicsAnimationController {

            private final PhysicsAnimationController mWrappedController;

            protected MainThreadAnimationControllerWrapper(PhysicsAnimationController controller) {
                mWrappedController = controller;
            }

            @Override
            protected void setLayout(PhysicsAnimationLayout layout) {
                mWrappedController.setLayout(layout);
            }

            @Override
            protected PhysicsAnimationLayout getLayout() {
                return mWrappedController.getLayout();
            }

            @Override
            Set<DynamicAnimation.ViewProperty> getAnimatedProperties() {
                return mWrappedController.getAnimatedProperties();
            }

            @Override
            int getNextAnimationInChain(DynamicAnimation.ViewProperty property, int index) {
                return mWrappedController.getNextAnimationInChain(property, index);
            }

            @Override
            float getOffsetForChainedPropertyAnimation(DynamicAnimation.ViewProperty property,
                    int index) {
                return mWrappedController.getOffsetForChainedPropertyAnimation(property, index);
            }

            @Override
            SpringForce getSpringForce(DynamicAnimation.ViewProperty property, View view) {
                return mWrappedController.getSpringForce(property, view);
            }

            @Override
            void onChildAdded(View child, int index) {
                runOnMainThreadAndBlock(() -> mWrappedController.onChildAdded(child, index));
            }

            @Override
            void onChildRemoved(View child, int index, Runnable finishRemoval) {
                runOnMainThreadAndBlock(
                        () -> mWrappedController.onChildRemoved(child, index, finishRemoval));
            }

            @Override
            void onChildReordered(View child, int oldIndex, int newIndex) {
                runOnMainThreadAndBlock(
                        () -> mWrappedController.onChildReordered(child, oldIndex, newIndex));
            }

            @Override
            void onActiveControllerForLayout(PhysicsAnimationLayout layout) {
                runOnMainThreadAndBlock(
                        () -> mWrappedController.onActiveControllerForLayout(layout));
            }

            @Override
            protected PhysicsPropertyAnimator animationForChild(View child) {
                PhysicsPropertyAnimator animator =
                        (PhysicsPropertyAnimator) child.getTag(R.id.physics_animator_tag);

                if (!(animator instanceof TestablePhysicsPropertyAnimator)) {
                    animator = new TestablePhysicsPropertyAnimator(child);
                    child.setTag(R.id.physics_animator_tag, animator);
                }

                return animator;
            }
        }
    }

    /**
     * Posts the given Runnable on the main thread, and blocks the calling thread until it's run.
     */
    private void runOnMainThreadAndBlock(Runnable action) {
        final CountDownLatch latch = new CountDownLatch(1);
        mMainThreadHandler.post(() -> {
            action.run();
            latch.countDown();
        });

        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /** Waits for the main thread to finish processing all pending runnables. */
    public void waitForMainThread() {
        runOnMainThreadAndBlock(() -> {});
    }
}
