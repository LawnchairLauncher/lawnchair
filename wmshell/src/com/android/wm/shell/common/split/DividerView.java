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

package com.android.wm.shell.common.split;

import static android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW;
import static android.view.PointerIcon.TYPE_VERTICAL_DOUBLE_ARROW;

import static com.android.internal.config.sysui.SystemUiDeviceConfigFlags.CURSOR_HOVER_STATES_ENABLED;
import static com.android.wm.shell.shared.split.SplitScreenConstants.snapPositionToUIString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.provider.DeviceConfig;
import android.util.AttributeSet;
import android.util.Property;
import android.view.GestureDetector;
import android.view.InsetsController;
import android.view.InsetsSource;
import android.view.InsetsState;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.SurfaceControlViewHost;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.ProtoLog;
import com.android.mechanics.spec.InputDirection;
import com.android.mechanics.view.DistanceGestureContext;
import com.android.mechanics.view.ViewMotionValue;
import com.android.wm.shell.Flags;
import com.android.wm.shell.R;
import com.android.wm.shell.common.split.DividerSnapAlgorithm.SnapTarget;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.animation.Interpolators;
import com.android.wm.shell.shared.desktopmode.DesktopState;

import com.google.android.msdl.data.model.MSDLToken;

import java.util.Objects;

/**
 * Divider for multi window splits.
 */
public class DividerView extends FrameLayout implements View.OnTouchListener {
    public static final long TOUCH_ANIMATION_DURATION = 150;
    public static final long TOUCH_RELEASE_ANIMATION_DURATION = 200;
    private static final boolean SHOW_DRAG_TOOLTIP = true;

    private final Paint mPaint = new Paint();
    private final Rect mBackgroundRect = new Rect();
    private final int mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();

    private SplitLayout mSplitLayout;
    private SplitWindowManager mSplitWindowManager;
    private SurfaceControlViewHost mViewHost;
    private DividerHandleView mHandle;
    private DividerRoundedCorner mCorners;
    /** A tooltip view that appears to educate users about split screen breakpoints. */
    private DividerTooltip mTooltip;
    private int mTouchElevation;

    private VelocityTracker mVelocityTracker;
    private boolean mMoving;
    private int mStartPos;
    private GestureDetector mDoubleTapDetector;
    private boolean mInteractive;
    private boolean mHideHandle;
    private boolean mSetTouchRegion = true;
    private int mLastDraggingPosition;
    private int mHandleRegionWidth;
    private int mHandleRegionHeight;

    // Calculation classes for "magnetic snap" user-controlled movement
    private DistanceGestureContext mDistanceGestureContext;
    private ViewMotionValue mViewMotionValue;
    /**
     * The @SnapPosition where the user started dragging from. Assigned at the beginning of a drag
     * and set back to null once the drag ends.
     */
    @Nullable private Integer mDragStartingSnapPosition;
    /**
     * When the divider is dragged out of the starting region {@link #mDragStartingSnapPosition}
     * for the first time, this is flipped to true. Used for tooltip logic.
     */
    private boolean mDraggedOutOfStartingRegion = false;
    @Nullable private Integer mLastHoveredOverSnapPosition;

    /**
     * This is not the visible bounds you see on screen, but the actual behind-the-scenes window
     * bounds, which is larger.
     */
    private final Rect mDividerBounds = new Rect();
    private final Rect mTempRect = new Rect();
    private FrameLayout mDividerBar;

    static final Property<DividerView, Integer> DIVIDER_HEIGHT_PROPERTY =
            new Property<DividerView, Integer>(Integer.class, "height") {
                @Override
                public Integer get(DividerView object) {
                    return object.mDividerBar.getLayoutParams().height;
                }

                @Override
                public void set(DividerView object, Integer value) {
                    ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams)
                            object.mDividerBar.getLayoutParams();
                    lp.height = value;
                    object.mDividerBar.setLayoutParams(lp);
                }
            };

    private AnimatorListenerAdapter mAnimatorListener = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
            mSetTouchRegion = true;
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            mSetTouchRegion = true;
        }
    };

    final AccessibilityDelegate mHandleDelegate = new AccessibilityDelegate() {
        @Override
        public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            final DividerSnapAlgorithm snapAlgorithm = mSplitLayout.mDividerSnapAlgorithm;
            if (mSplitLayout.isLeftRightSplit()) {
                info.addAction(new AccessibilityAction(R.id.action_move_tl_full,
                        mContext.getString(R.string.accessibility_action_divider_left_full)));
                if (snapAlgorithm.isFirstSplitTargetAvailable()) {
                    info.addAction(new AccessibilityAction(R.id.action_move_tl_70,
                            mContext.getString(R.string.accessibility_action_divider_left_70)));
                }
                if (snapAlgorithm.showMiddleSplitTargetForAccessibility()) {
                    // Only show the middle target if there are more than 1 split target
                    info.addAction(new AccessibilityAction(R.id.action_move_tl_50,
                            mContext.getString(R.string.accessibility_action_divider_left_50)));
                }
                if (snapAlgorithm.isLastSplitTargetAvailable()) {
                    info.addAction(new AccessibilityAction(R.id.action_move_tl_30,
                            mContext.getString(R.string.accessibility_action_divider_left_30)));
                }
                info.addAction(new AccessibilityAction(R.id.action_move_rb_full,
                        mContext.getString(R.string.accessibility_action_divider_right_full)));
                info.addAction(new AccessibilityAction(R.id.action_swap_apps,
                        mContext.getString(R.string.accessibility_action_divider_swap_horizontal)));
            } else {
                info.addAction(new AccessibilityAction(R.id.action_move_tl_full,
                        mContext.getString(R.string.accessibility_action_divider_top_full)));
                if (snapAlgorithm.isFirstSplitTargetAvailable()) {
                    info.addAction(new AccessibilityAction(R.id.action_move_tl_70,
                            mContext.getString(R.string.accessibility_action_divider_top_70)));
                }
                if (snapAlgorithm.showMiddleSplitTargetForAccessibility()) {
                    // Only show the middle target if there are more than 1 split target
                    info.addAction(new AccessibilityAction(R.id.action_move_tl_50,
                            mContext.getString(R.string.accessibility_action_divider_top_50)));
                }
                if (snapAlgorithm.isLastSplitTargetAvailable()) {
                    info.addAction(new AccessibilityAction(R.id.action_move_tl_30,
                            mContext.getString(R.string.accessibility_action_divider_top_30)));
                }
                info.addAction(new AccessibilityAction(R.id.action_move_rb_full,
                        mContext.getString(R.string.accessibility_action_divider_bottom_full)));
                info.addAction(new AccessibilityAction(R.id.action_swap_apps,
                        mContext.getString(R.string.accessibility_action_divider_swap_vertical)));
            }
        }

        @Override
        public boolean performAccessibilityAction(@NonNull View host, int action,
                @Nullable Bundle args) {
            if (action == R.id.action_swap_apps) {
                mSplitLayout.onDoubleTappedDivider();
                return true;
            }

            SnapTarget nextTarget = null;
            DividerSnapAlgorithm snapAlgorithm = mSplitLayout.mDividerSnapAlgorithm;
            if (action == R.id.action_move_tl_full) {
                nextTarget = snapAlgorithm.getDismissEndTarget();
            } else if (action == R.id.action_move_tl_70) {
                nextTarget = snapAlgorithm.getLastSplitTarget();
            } else if (action == R.id.action_move_tl_50) {
                nextTarget = snapAlgorithm.getMiddleTarget();
            } else if (action == R.id.action_move_tl_30) {
                nextTarget = snapAlgorithm.getFirstSplitTarget();
            } else if (action == R.id.action_move_rb_full) {
                nextTarget = snapAlgorithm.getDismissStartTarget();
            }
            if (nextTarget != null) {
                mSplitLayout.snapToTarget(mSplitLayout.getDividerPosition(), nextTarget);
                return true;
            }
            return super.performAccessibilityAction(host, action, args);
        }
    };

    public DividerView(@NonNull Context context) {
        super(context);
    }

    public DividerView(@NonNull Context context,
            @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public DividerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public DividerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    /** Sets up essential dependencies of the divider bar. */
    public void setup(SplitLayout layout, SplitWindowManager splitWindowManager,
            SurfaceControlViewHost viewHost, InsetsState insetsState,
            DesktopState desktopState) {
        mSplitLayout = layout;
        mSplitWindowManager = splitWindowManager;
        mViewHost = viewHost;
        layout.getDividerBounds(mDividerBounds);
        onInsetsChanged(insetsState, false /* animate */);

        final boolean isLeftRightSplit = mSplitLayout.isLeftRightSplit();
        mHandle.setIsLeftRightSplit(isLeftRightSplit);
        mCorners.setIsLeftRightSplit(isLeftRightSplit);
        mTooltip.setIsLeftRightSplit(isLeftRightSplit);

        mHandleRegionWidth = getResources().getDimensionPixelSize(isLeftRightSplit
                ? R.dimen.split_divider_handle_region_height
                : R.dimen.split_divider_handle_region_width);
        mHandleRegionHeight = getResources().getDimensionPixelSize(isLeftRightSplit
                ? R.dimen.split_divider_handle_region_width
                : desktopState.canEnterDesktopMode()
                        ? R.dimen.desktop_mode_portrait_split_divider_handle_region_height
                        : R.dimen.split_divider_handle_region_height);
    }

    void onInsetsChanged(InsetsState insetsState, boolean animate) {
        mSplitLayout.getDividerBounds(mTempRect);
        // Only insets the divider bar with task bar when it's expanded so that the rounded corners
        // will be drawn against task bar.
        // But there is no need to do it when IME showing because there are no rounded corners at
        // the bottom. This also avoids the problem of task bar height not changing when IME
        // floating.
        if (!insetsState.isSourceOrDefaultVisible(InsetsSource.ID_IME, WindowInsets.Type.ime())) {
            for (int i = insetsState.sourceSize() - 1; i >= 0; i--) {
                final InsetsSource source = insetsState.sourceAt(i);
                if (source.getType() == WindowInsets.Type.navigationBars()
                        && source.hasFlags(InsetsSource.FLAG_INSETS_ROUNDED_CORNER)) {
                    mTempRect.inset(source.calculateVisibleInsets(mTempRect, mTempRect));
                }
            }
        }

        if (!mTempRect.equals(mDividerBounds)) {
            if (animate) {
                ObjectAnimator animator = ObjectAnimator.ofInt(this,
                        DIVIDER_HEIGHT_PROPERTY, mDividerBounds.height(), mTempRect.height());
                animator.setInterpolator(InsetsController.RESIZE_INTERPOLATOR);
                animator.setDuration(InsetsController.ANIMATION_DURATION_RESIZE);
                animator.addListener(mAnimatorListener);
                animator.start();
            } else {
                DIVIDER_HEIGHT_PROPERTY.set(this, mTempRect.height());
                mSetTouchRegion = true;
            }
            mDividerBounds.set(mTempRect);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mDividerBar = findViewById(R.id.divider_bar);
        mHandle = findViewById(R.id.docked_divider_handle);
        mCorners = findViewById(R.id.docked_divider_rounded_corner);
        mTooltip = findViewById(R.id.docked_divider_tooltip);
        mTouchElevation = getResources().getDimensionPixelSize(
                R.dimen.docked_stack_divider_lift_elevation);
        mDoubleTapDetector = new GestureDetector(getContext(), new DoubleTapListener());
        mInteractive = true;
        mHideHandle = false;
        setOnTouchListener(this);
        mHandle.setAccessibilityDelegate(mHandleDelegate);
        setWillNotDraw(false);
        mPaint.setColor(getResources().getColor(R.color.split_divider_background, null));
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mSetTouchRegion) {
            int startX = (mDividerBounds.width() - mHandleRegionWidth) / 2;
            int startY = (mDividerBounds.height() - mHandleRegionHeight) / 2;
            mTempRect.set(startX, startY, startX + mHandleRegionWidth,
                    startY + mHandleRegionHeight);
            mSplitWindowManager.setTouchRegion(mTempRect);
            mSetTouchRegion = false;
        }

        if (changed) {
            boolean isHorizontalSplit = mSplitLayout.isLeftRightSplit();
            int dividerSize = getResources().getDimensionPixelSize(R.dimen.split_divider_bar_width);
            left = isHorizontalSplit ? (getWidth() - dividerSize) / 2 : 0;
            top = isHorizontalSplit ? 0 : (getHeight() - dividerSize) / 2;
            right = isHorizontalSplit ? left + dividerSize : getWidth();
            bottom = isHorizontalSplit ? getHeight() : top + dividerSize;
            mBackgroundRect.set(left, top, right, bottom);
        }
    }

    @Override
    public PointerIcon onResolvePointerIcon(MotionEvent event, int pointerIndex) {
        return PointerIcon.getSystemIcon(getContext(),
                mSplitLayout.isLeftRightSplit() ? TYPE_HORIZONTAL_DOUBLE_ARROW
                        : TYPE_VERTICAL_DOUBLE_ARROW);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (mSplitLayout == null || !mInteractive) {
            return false;
        }

        mDoubleTapDetector.onTouchEvent(event);

        // Convert to use screen-based coordinates to prevent lost track of motion events while
        // moving divider bar and calculating dragging velocity.
        event.setLocation(event.getRawX(), event.getRawY());
        final int action = event.getAction() & MotionEvent.ACTION_MASK;
        final boolean isLeftRightSplit = mSplitLayout.isLeftRightSplit();
        final int touchPos = (int) (isLeftRightSplit ? event.getX() : event.getY());
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mVelocityTracker = VelocityTracker.obtain();
                mVelocityTracker.addMovement(event);
                setTouching();
                mStartPos = touchPos;
                mMoving = false;
                mSplitLayout.onStartDragging();
                break;
            case MotionEvent.ACTION_MOVE:
                mVelocityTracker.addMovement(event);
                int displacement = touchPos - mStartPos;
                if (!mMoving && Math.abs(displacement) > mTouchSlop) {
                    mStartPos = touchPos;
                    mMoving = true;
                    if (Flags.enableMagneticSplitDivider()) {
                        // Move gesture is confirmed, create framework for magnetic snap
                        InputDirection direction =
                                displacement > 0 ? InputDirection.Max : InputDirection.Min;
                        mDistanceGestureContext = DistanceGestureContext.create(mContext, mStartPos,
                                direction);
                        mViewMotionValue = new ViewMotionValue(mStartPos,
                                mDistanceGestureContext,
                                mSplitLayout.mDividerSnapAlgorithm.getMotionSpec(),
                                "dividerView::pos" /* label */);
                        mLastHoveredOverSnapPosition = mSplitLayout.calculateCurrentSnapPosition();
                        // Set a "starting region" in which we don't want to show the tooltip yet.
                        mDragStartingSnapPosition = mSplitLayout.calculateCurrentSnapPosition();
                        mViewMotionValue.addUpdateCallback(viewMotionValue -> {
                            int snappedPosition = (int) viewMotionValue.getOutput();
                            // Whenever MotionValue updates (from user moving the divider):
                            // - Place divider in its new position
                            placeDivider(snappedPosition);
                            // - Play a haptic if entering a magnetic zone
                            Integer currentlyHoveredOverSnapZone = viewMotionValue.get(
                                    MagneticDividerUtils.getSNAP_POSITION_KEY());

                            boolean changedSnapPosition = !Objects.equals(
                                    currentlyHoveredOverSnapZone, mLastHoveredOverSnapPosition);
                            if (currentlyHoveredOverSnapZone != null && changedSnapPosition) {
                                playHapticClick();
                            }
                            // - Update the last-hovered-over snap zone
                            mLastHoveredOverSnapPosition = currentlyHoveredOverSnapZone;
                            // - Update tooltip state if needed
                            if (SHOW_DRAG_TOOLTIP) {
                                // - Update internal state for closest snap position (i.e. where the
                                // user will end up if drag is released)
                                final float velocity = isLeftRightSplit
                                        ? mVelocityTracker.getXVelocity()
                                        : mVelocityTracker.getYVelocity();
                                int closestSnapPosition = mSplitLayout
                                        .findSnapTarget(snappedPosition,
                                                velocity, false /* hardDismiss */)
                                        .snapPosition;
                                // If we are still in the starting zone, wait until the user drags
                                // to a point where the closest snap position is a different one.
                                if (!mDraggedOutOfStartingRegion
                                        && closestSnapPosition != mDragStartingSnapPosition) {
                                    mDraggedOutOfStartingRegion = true;
                                }
                                // Afterwards, always show the tooltip, updating to reflect the
                                // nearest snap point.
                                if (mDraggedOutOfStartingRegion) {
                                    showTooltip(snapPositionToUIString(closestSnapPosition));
                                }
                            }
                        });
                    }
                }
                if (mMoving) {
                    final int position = mSplitLayout.getDividerPosition() + touchPos - mStartPos;
                    mLastDraggingPosition = position;
                    if (Flags.enableMagneticSplitDivider()) {
                        updateMagneticSnapCalculation(position);
                    } else {
                        placeDivider(position);
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                releaseTouching();
                if (!mMoving) {
                    mSplitLayout.onDraggingCancelled();
                    if (Flags.enableMagneticSplitDivider()) {
                        cleanUpMagneticSnapFramework();
                    }
                    break;
                }

                mVelocityTracker.addMovement(event);
                mVelocityTracker.computeCurrentVelocity(1000 /* units */);
                final float velocity = isLeftRightSplit
                        ? mVelocityTracker.getXVelocity()
                        : mVelocityTracker.getYVelocity();
                final int position = mSplitLayout.getDividerPosition() + touchPos - mStartPos;
                final SnapTarget snapTarget =
                        mSplitLayout.findSnapTarget(position, velocity, false /* hardDismiss */);
                mSplitLayout.snapToTarget(position, snapTarget);
                mMoving = false;
                if (Flags.enableMagneticSplitDivider()) {
                    cleanUpMagneticSnapFramework();
                }
                break;
        }

        return true;
    }

    /** Plays a short haptic to indicate attaching or detaching from a divider snap point. */
    private void playHapticClick() {
        mSplitLayout.getHapticPlayer().playToken(MSDLToken.SWIPE_THRESHOLD_INDICATOR, null);
    }

    private void showTooltip(String tooltipText) {
        mTooltip.setText(tooltipText);
        if (mTooltip.getVisibility() == VISIBLE) {
            return;
        }
        mTooltip.setVisibility(VISIBLE);
        mTooltip.setAlpha(1f);
    }

    private void hideTooltip() {
        if (mTooltip.getVisibility() == GONE) {
            return;
        }
        mTooltip.setAlpha(0f);
        mTooltip.setVisibility(GONE);
    }

    /** Updates the position of the divider. */
    private void placeDivider(int position) {
        mSplitLayout.updateDividerBounds(position, true /* shouldUseParallaxEffect */);
    }

    /**
     * Sends a position update to the magnetic snap framework, allowing a calculation to occur. The
     * position of the divider will be updated.
     * @param position The current position of the user's finger.
     */
    private void updateMagneticSnapCalculation(int position) {
        if (mDistanceGestureContext != null) {
            mDistanceGestureContext.setDragOffset(position);
        }
        if (mViewMotionValue != null) {
            mViewMotionValue.setInput(position);
        }
    }

    /** Cleans up the magnetic snap framework after the drag gesture completes. */
    private void cleanUpMagneticSnapFramework() {
        if (mViewMotionValue != null) {
            mViewMotionValue.dispose();
        }
        mDistanceGestureContext = null;
        mViewMotionValue = null;
        mLastHoveredOverSnapPosition = null;
        mDragStartingSnapPosition = null;
        mDraggedOutOfStartingRegion = false;
        hideTooltip();
    }

    private void setTouching() {
        mHandle.setTouching(true, true);
        // Lift handle as well so it doesn't get behind the background, even though it doesn't
        // cast shadow.
        mHandle.animate()
                .setInterpolator(Interpolators.TOUCH_RESPONSE)
                .setDuration(TOUCH_ANIMATION_DURATION)
                .translationZ(mTouchElevation)
                .start();
    }

    private void releaseTouching() {
        mHandle.setTouching(false, true);
        mHandle.animate()
                .setInterpolator(Interpolators.FAST_OUT_SLOW_IN)
                .setDuration(TOUCH_RELEASE_ANIMATION_DURATION)
                .translationZ(0)
                .start();
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        if (!DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_SYSTEMUI, CURSOR_HOVER_STATES_ENABLED,
                /* defaultValue = */ false)) {
            return false;
        }

        if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
            setHovering();
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
            releaseHovering();
            return true;
        }
        return false;
    }

    @VisibleForTesting
    void setHovering() {
        mHandle.setHovering(true, true);
        mHandle.animate()
                .setInterpolator(Interpolators.TOUCH_RESPONSE)
                .setDuration(TOUCH_ANIMATION_DURATION)
                .translationZ(mTouchElevation)
                .start();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        canvas.drawRect(mBackgroundRect, mPaint);
    }

    @VisibleForTesting
    void releaseHovering() {
        mHandle.setHovering(false, true);
        mHandle.animate()
                .setInterpolator(Interpolators.FAST_OUT_SLOW_IN)
                .setDuration(TOUCH_RELEASE_ANIMATION_DURATION)
                .translationZ(0)
                .start();
    }

    /**
     * Set divider should interactive to user or not.
     *
     * @param interactive divider interactive.
     * @param hideHandle divider handle hidden or not, only work when interactive is false.
     * @param from caller from where.
     */
    void setInteractive(boolean interactive, boolean hideHandle, String from) {
        if (interactive == mInteractive) return;
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN,
                "Set divider bar %s hide handle=%b from %s",
                interactive ? "interactive" : "non-interactive", hideHandle, from);
        mInteractive = interactive;
        mHideHandle = hideHandle;
        if (!mInteractive && mHideHandle && mMoving) {
            final int position = mSplitLayout.getDividerPosition();
            mSplitLayout.flingDividerPosition(
                    mLastDraggingPosition,
                    position,
                    mSplitLayout.FLING_RESIZE_DURATION,
                    Interpolators.FAST_OUT_SLOW_IN,
                    () -> mSplitLayout.setDividerPosition(position, true /* applyLayoutChange */));
            mMoving = false;
        }
        releaseTouching();
        mHandle.setVisibility(!mInteractive && mHideHandle ? View.INVISIBLE : View.VISIBLE);
    }

    boolean isInteractive() {
        return mInteractive;
    }

    boolean isHandleHidden() {
        return mHideHandle;
    }

    /** Returns true if the divider is currently being physically controlled by the user. */
    boolean isMoving() {
        return mMoving;
    }

    private class DoubleTapListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDoubleTapEvent(@NonNull MotionEvent e) {
            // User could have started double tap and then dragged before letting go. Skip the
            // swap if so
            if (!mMoving && e.getAction() == MotionEvent.ACTION_UP) {
                if (mSplitLayout != null) {
                    mSplitLayout.onDoubleTappedDivider();
                }
                return true;
            }
            return false;
        }
    }
}
