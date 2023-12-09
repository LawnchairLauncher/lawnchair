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
package com.android.launcher3.taskbar.bubbles;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.DynamicAnimation;

import com.android.launcher3.R;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.taskbar.TaskbarDragLayer;
import com.android.wm.shell.common.bubbles.DismissView;
import com.android.wm.shell.common.magnetictarget.MagnetizedObject;

/**
 * Controls dismiss view presentation for the bubble bar dismiss functionality.
 * Provides the dragged view snapping to the target dismiss area and animates it.
 * When the dragged bubble/bubble stack is released inside of the target area, it gets dismissed.
 *
 * @see BubbleDragController
 */
public class BubbleDismissController {
    private static final String TAG = BubbleDismissController.class.getSimpleName();
    private static final float FLING_TO_DISMISS_MIN_VELOCITY = 6000f;
    private final TaskbarActivityContext mActivity;
    private final TaskbarDragLayer mDragLayer;
    @Nullable
    private BubbleBarViewController mBubbleBarViewController;

    // Dismiss view that's attached to drag layer. It consists of the scrim view and the circular
    // dismiss view used as a dismiss target.
    @Nullable
    private DismissView mDismissView;

    // The currently magnetized object, which is being dragged and will be attracted to the magnetic
    // dismiss target. This is either the stack itself, or an individual bubble.
    @Nullable
    private MagnetizedObject<View> mMagnetizedObject;

    // The MagneticTarget instance for our circular dismiss view. This is added to the
    // MagnetizedObject instances for the stack and any dragged-out bubbles.
    @Nullable
    private MagnetizedObject.MagneticTarget mMagneticTarget;

    // The bubble drag animator that synchronizes bubble drag and dismiss view animations
    // A new instance is provided when the dismiss view is setup
    @Nullable
    private BubbleDragAnimator mAnimator;

    public BubbleDismissController(TaskbarActivityContext activity, TaskbarDragLayer dragLayer) {
        mActivity = activity;
        mDragLayer = dragLayer;
    }

    /**
     * Initializes dependencies when bubble controllers are created.
     * Should be careful to only access things that were created in constructors for now, as some
     * controllers may still be waiting for init().
     */
    public void init(@NonNull BubbleControllers bubbleControllers) {
        mBubbleBarViewController = bubbleControllers.bubbleBarViewController;
    }

    /**
     * Setup the dismiss view and magnetized object that will be attracted to magnetic target.
     * Should be called before handling events or showing/hiding dismiss view.
     *
     * @param magnetizedView the view to be pulled into target dismiss area
     * @param animator       the bubble animator to be used for the magnetized view, it syncs bubble
     *                       dragging and dismiss animations with the dismiss view provided.
     */
    public void setupDismissView(@NonNull View magnetizedView,
            @NonNull BubbleDragAnimator animator) {
        setupDismissView();
        setupMagnetizedObject(magnetizedView);
        if (mDismissView != null) {
            animator.setDismissView(mDismissView);
            mAnimator = animator;
        }
    }

    /**
     * Handle the touch event and pass it to the magnetized object.
     * It should be called after {@code setupDismissView}
     */
    public boolean handleTouchEvent(@NonNull MotionEvent event) {
        return mMagnetizedObject != null && mMagnetizedObject.maybeConsumeMotionEvent(event);
    }

    /**
     * Show dismiss view with animation
     * It should be called after {@code setupDismissView}
     */
    public void showDismissView() {
        if (mDismissView == null) return;
        mDismissView.show();
    }

    /**
     * Hide dismiss view with animation
     * It should be called after {@code setupDismissView}
     */
    public void hideDismissView() {
        if (mDismissView == null) return;
        mDismissView.hide();
    }

    /**
     * Dismiss magnetized object when it's released in the dismiss target area
     */
    private void dismissMagnetizedObject() {
        if (mMagnetizedObject == null || mBubbleBarViewController == null) return;
        if (mMagnetizedObject.getUnderlyingObject() instanceof BubbleView) {
            BubbleView bubbleView = (BubbleView) mMagnetizedObject.getUnderlyingObject();
            if (bubbleView.getBubble() != null) {
                mBubbleBarViewController.onDismissBubbleWhileDragging(bubbleView.getBubble());
            }
        } else if (mMagnetizedObject.getUnderlyingObject() instanceof BubbleBarView) {
            mBubbleBarViewController.onDismissAllBubblesWhileDragging();
        }
    }

    private void setupDismissView() {
        if (mDismissView != null) return;
        mDismissView = new DismissView(mActivity.getApplicationContext());
        BubbleDismissViewUtils.setup(mDismissView);
        mDragLayer.addView(mDismissView, /* index = */ 0,
                new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        mDismissView.setElevation(mDismissView.getResources().getDimensionPixelSize(
                R.dimen.bubblebar_elevation));
        setupMagneticTarget(mDismissView.getCircle());
    }

    private void setupMagneticTarget(@NonNull View view) {
        int magneticFieldRadius = mActivity.getResources().getDimensionPixelSize(
                R.dimen.bubblebar_dismiss_target_size);
        mMagneticTarget = new MagnetizedObject.MagneticTarget(view, magneticFieldRadius);
    }

    private void setupMagnetizedObject(@NonNull View magnetizedView) {
        mMagnetizedObject = new MagnetizedObject<>(mActivity.getApplicationContext(),
                magnetizedView, DynamicAnimation.TRANSLATION_X, DynamicAnimation.TRANSLATION_Y) {
            @Override
            public float getWidth(@NonNull View underlyingObject) {
                return underlyingObject.getWidth() * underlyingObject.getScaleX();
            }

            @Override
            public float getHeight(@NonNull View underlyingObject) {
                return underlyingObject.getHeight() * underlyingObject.getScaleY();
            }

            @Override
            public void getLocationOnScreen(@NonNull View underlyingObject, @NonNull int[] loc) {
                underlyingObject.getLocationOnScreen(loc);
            }
        };

        mMagnetizedObject.setHapticsEnabled(true);
        mMagnetizedObject.setFlingToTargetMinVelocity(FLING_TO_DISMISS_MIN_VELOCITY);
        if (mMagneticTarget != null) {
            mMagnetizedObject.addTarget(mMagneticTarget);
        } else {
            Log.e(TAG,"Requires MagneticTarget to add target to MagnetizedObject!");
        }
        mMagnetizedObject.setMagnetListener(new MagnetizedObject.MagnetListener() {
            @Override
            public void onStuckToTarget(@NonNull MagnetizedObject.MagneticTarget target) {
                if (mAnimator == null) return;
                mAnimator.animateDismissCaptured();
            }

            @Override
            public void onUnstuckFromTarget(@NonNull MagnetizedObject.MagneticTarget target,
                    float velX, float velY, boolean wasFlungOut) {
                if (mAnimator == null) return;
                mAnimator.animateDismissReleased();
            }

            @Override
            public void onReleasedInTarget(@NonNull MagnetizedObject.MagneticTarget target) {
                dismissMagnetizedObject();
            }
        });
    }
}
