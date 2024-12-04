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

package com.android.wm.shell.bubbles.bar;

import static com.android.wm.shell.animation.Interpolators.ALPHA_IN;
import static com.android.wm.shell.animation.Interpolators.ALPHA_OUT;
import static com.android.wm.shell.bubbles.Bubbles.DISMISS_USER_GESTURE;

import android.annotation.Nullable;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.android.wm.shell.bubbles.Bubble;
import com.android.wm.shell.bubbles.BubbleController;
import com.android.wm.shell.bubbles.BubbleData;
import com.android.wm.shell.bubbles.BubbleOverflow;
import com.android.wm.shell.bubbles.BubblePositioner;
import com.android.wm.shell.bubbles.BubbleViewProvider;
import com.android.wm.shell.bubbles.DeviceConfig;
import com.android.wm.shell.bubbles.DismissViewUtils;
import com.android.wm.shell.bubbles.bar.BubbleBarExpandedViewDragController.DragListener;
import com.android.wm.shell.common.bubbles.BaseBubblePinController;
import com.android.wm.shell.common.bubbles.BubbleBarLocation;
import com.android.wm.shell.common.bubbles.DismissView;

import kotlin.Unit;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Similar to {@link com.android.wm.shell.bubbles.BubbleStackView}, this view is added to window
 * manager to display bubbles. However, it is only used when bubbles are being displayed in
 * launcher in the bubble bar. This view does not show a stack of bubbles that can be moved around
 * on screen and instead shows & animates the expanded bubble for the bubble bar.
 */
public class BubbleBarLayerView extends FrameLayout
        implements ViewTreeObserver.OnComputeInternalInsetsListener {

    private static final String TAG = BubbleBarLayerView.class.getSimpleName();

    private static final float SCRIM_ALPHA = 0.2f;

    private final BubbleController mBubbleController;
    private final BubbleData mBubbleData;
    private final BubblePositioner mPositioner;
    private final BubbleBarAnimationHelper mAnimationHelper;
    private final BubbleEducationViewController mEducationViewController;
    private final View mScrimView;
    private final BubbleExpandedViewPinController mBubbleExpandedViewPinController;

    @Nullable
    private BubbleViewProvider mExpandedBubble;
    @Nullable
    private BubbleBarExpandedView mExpandedView;
    @Nullable
    private BubbleBarExpandedViewDragController mDragController;
    private DismissView mDismissView;
    private @Nullable Consumer<String> mUnBubbleConversationCallback;

    /** Whether a bubble is expanded. */
    private boolean mIsExpanded = false;

    private final Region mTouchableRegion = new Region();
    private final Rect mTempRect = new Rect();

    // Used to ensure touch target size for the menu shown on a bubble expanded view
    private TouchDelegate mHandleTouchDelegate;
    private final Rect mHandleTouchBounds = new Rect();

    public BubbleBarLayerView(Context context, BubbleController controller, BubbleData bubbleData) {
        super(context);
        mBubbleController = controller;
        mBubbleData = bubbleData;
        mPositioner = mBubbleController.getPositioner();

        mAnimationHelper = new BubbleBarAnimationHelper(context,
                this, mPositioner);
        mEducationViewController = new BubbleEducationViewController(context, (boolean visible) -> {
            if (mExpandedView == null) return;
            mExpandedView.setObscured(visible);
        });

        mScrimView = new View(getContext());
        mScrimView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        mScrimView.setBackgroundDrawable(new ColorDrawable(
                getResources().getColor(android.R.color.system_neutral1_1000)));
        addView(mScrimView);
        mScrimView.setAlpha(0f);
        mScrimView.setBackgroundDrawable(new ColorDrawable(
                getResources().getColor(android.R.color.system_neutral1_1000)));

        setUpDismissView();

        mBubbleExpandedViewPinController = new BubbleExpandedViewPinController(
                context, this, mPositioner);
        mBubbleExpandedViewPinController.setListener(
                new BaseBubblePinController.LocationChangeListener() {
                    @Override
                    public void onChange(@NonNull BubbleBarLocation bubbleBarLocation) {
                        mBubbleController.animateBubbleBarLocation(bubbleBarLocation);
                    }

                    @Override
                    public void onRelease(@NonNull BubbleBarLocation location) {
                        mBubbleController.setBubbleBarLocation(location);
                    }
                });

        setOnClickListener(view -> hideModalOrCollapse());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        WindowManager windowManager = mContext.getSystemService(WindowManager.class);
        mPositioner.update(DeviceConfig.create(mContext, Objects.requireNonNull(windowManager)));
        getViewTreeObserver().addOnComputeInternalInsetsListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getViewTreeObserver().removeOnComputeInternalInsetsListener(this);

        if (mExpandedView != null) {
            mEducationViewController.hideEducation(/* animated = */ false);
            removeView(mExpandedView);
            mExpandedView = null;
        }
    }

    @Override
    public void onComputeInternalInsets(ViewTreeObserver.InternalInsetsInfo inoutInfo) {
        inoutInfo.setTouchableInsets(ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION);
        mTouchableRegion.setEmpty();
        getTouchableRegion(mTouchableRegion);
        inoutInfo.touchableRegion.set(mTouchableRegion);
    }

    /** Updates the sizes of any displaying expanded view. */
    public void onDisplaySizeChanged() {
        if (mIsExpanded && mExpandedView != null) {
            updateExpandedView();
        }
    }

    /** Whether the stack of bubbles is expanded or not. */
    public boolean isExpanded() {
        return mIsExpanded;
    }

    /** Shows the expanded view of the provided bubble. */
    public void showExpandedView(BubbleViewProvider b) {
        BubbleBarExpandedView expandedView = b.getBubbleBarExpandedView();
        if (expandedView == null) {
            return;
        }
        if (mExpandedBubble != null && !b.getKey().equals(mExpandedBubble.getKey())) {
            removeView(mExpandedView);
            mExpandedView = null;
        }
        if (mExpandedView == null) {
            if (expandedView.getParent() != null) {
                // Expanded view might be animating collapse and is still attached
                // Cancel current animations and remove from parent
                mAnimationHelper.cancelAnimations();
                removeView(expandedView);
            }
            mExpandedBubble = b;
            mExpandedView = expandedView;
            boolean isOverflowExpanded = b.getKey().equals(BubbleOverflow.KEY);
            final int width = mPositioner.getExpandedViewWidthForBubbleBar(isOverflowExpanded);
            final int height = mPositioner.getExpandedViewHeightForBubbleBar(isOverflowExpanded);
            mExpandedView.setVisibility(GONE);
            mExpandedView.setY(mPositioner.getExpandedViewBottomForBubbleBar() - height);
            mExpandedView.setLayerBoundsSupplier(() -> new Rect(0, 0, getWidth(), getHeight()));
            mExpandedView.setListener(new BubbleBarExpandedView.Listener() {
                @Override
                public void onTaskCreated() {
                    if (mEducationViewController != null && mExpandedView != null) {
                        mEducationViewController.maybeShowManageEducation(b, mExpandedView);
                    }
                }

                @Override
                public void onUnBubbleConversation(String bubbleKey) {
                    if (mUnBubbleConversationCallback != null) {
                        mUnBubbleConversationCallback.accept(bubbleKey);
                    }
                }

                @Override
                public void onBackPressed() {
                    hideModalOrCollapse();
                }
            });

            DragListener dragListener = inDismiss -> {
                if (inDismiss && mExpandedBubble != null) {
                    mBubbleController.dismissBubble(mExpandedBubble.getKey(), DISMISS_USER_GESTURE);
                }
            };
            mDragController = new BubbleBarExpandedViewDragController(
                    mExpandedView,
                    mDismissView,
                    mAnimationHelper,
                    mPositioner,
                    mBubbleExpandedViewPinController,
                    dragListener);

            addView(mExpandedView, new LayoutParams(width, height, Gravity.LEFT));
        }

        if (mEducationViewController.isEducationVisible()) {
            mEducationViewController.hideEducation(/* animated = */ true);
        }

        mIsExpanded = true;
        mBubbleController.getSysuiProxy().onStackExpandChanged(true);
        mAnimationHelper.animateExpansion(mExpandedBubble, () -> {
            if (mExpandedView == null) return;
            // Touch delegate for the menu
            BubbleBarHandleView view = mExpandedView.getHandleView();
            view.getBoundsOnScreen(mHandleTouchBounds);
            // Move top value up to ensure touch target is large enough
            mHandleTouchBounds.top -= mPositioner.getBubblePaddingTop();
            mHandleTouchDelegate = new TouchDelegate(mHandleTouchBounds,
                    mExpandedView.getHandleView());
            setTouchDelegate(mHandleTouchDelegate);
        });

        showScrim(true);
    }

    /** Removes the given {@code bubble}. */
    public void removeBubble(Bubble bubble, Runnable endAction) {
        Runnable cleanUp = () -> {
            bubble.cleanupViews();
            endAction.run();
        };
        if (mBubbleData.getBubbles().isEmpty()) {
            // we're removing the last bubble. collapse the expanded view and cleanup bubble views
            // at the end.
            collapse(cleanUp);
        } else {
            cleanUp.run();
        }
    }

    /** Collapses any showing expanded view */
    public void collapse() {
        collapse(/* endAction= */ null);
    }

    /**
     * Collapses any showing expanded view.
     *
     * @param endAction an action to run and the end of the collapse animation.
     */
    public void collapse(@Nullable Runnable endAction) {
        if (!mIsExpanded) {
            if (endAction != null) {
                endAction.run();
            }
            return;
        }
        mIsExpanded = false;
        final BubbleBarExpandedView viewToRemove = mExpandedView;
        mEducationViewController.hideEducation(/* animated = */ true);
        Runnable runnable = () -> {
            removeView(viewToRemove);
            if (endAction != null) {
                endAction.run();
            }
            if (mBubbleData.getBubbles().isEmpty()) {
                mBubbleController.onAllBubblesAnimatedOut();
            }
        };
        if (mDragController != null && mDragController.isStuckToDismiss()) {
            mAnimationHelper.animateDismiss(runnable);
        } else {
            mAnimationHelper.animateCollapse(runnable);
        }
        mBubbleController.getSysuiProxy().onStackExpandChanged(false);
        mExpandedView = null;
        mDragController = null;
        setTouchDelegate(null);
        showScrim(false);
    }

    /**
     * Show bubble bar user education relative to the reference position.
     * @param position the reference position in Screen coordinates.
     */
    public void showUserEducation(Point position) {
        mEducationViewController.showStackEducation(position, /* root = */ this, () -> {
            // When the user education is clicked hide it and expand the selected bubble
            mEducationViewController.hideEducation(/* animated = */ true, () -> {
                mBubbleController.expandStackWithSelectedBubble();
                return Unit.INSTANCE;
            });
            return Unit.INSTANCE;
        });
    }

    /** Sets the function to call to un-bubble the given conversation. */
    public void setUnBubbleConversationCallback(
            @Nullable Consumer<String> unBubbleConversationCallback) {
        mUnBubbleConversationCallback = unBubbleConversationCallback;
    }

    private void setUpDismissView() {
        if (mDismissView != null) {
            removeView(mDismissView);
        }
        mDismissView = new DismissView(getContext());
        DismissViewUtils.setup(mDismissView);
        addView(mDismissView);
    }

    /** Hides the current modal education/menu view, IME or collapses the expanded view */
    private void hideModalOrCollapse() {
        if (mEducationViewController.isEducationVisible()) {
            mEducationViewController.hideEducation(/* animated = */ true);
            return;
        }
        if (isExpanded() && mExpandedView != null) {
            boolean menuHidden = mExpandedView.hideMenuIfVisible();
            if (menuHidden) {
                return;
            }
            boolean imeHidden = mExpandedView.hideImeIfVisible();
            if (imeHidden) {
                return;
            }
        }
        mBubbleController.collapseStack();
    }

    /** Updates the expanded view size and position. */
    public void updateExpandedView() {
        if (mExpandedView == null || mExpandedBubble == null) return;
        boolean isOverflowExpanded = mExpandedBubble.getKey().equals(BubbleOverflow.KEY);
        mPositioner.getBubbleBarExpandedViewBounds(mPositioner.isBubbleBarOnLeft(),
                isOverflowExpanded, mTempRect);
        FrameLayout.LayoutParams lp = (LayoutParams) mExpandedView.getLayoutParams();
        lp.width = mTempRect.width();
        lp.height = mTempRect.height();
        mExpandedView.setLayoutParams(lp);
        mExpandedView.setX(mTempRect.left);
        mExpandedView.setY(mTempRect.top);
        mExpandedView.updateLocation();
    }

    private void showScrim(boolean show) {
        if (show) {
            mScrimView.animate()
                    .setInterpolator(ALPHA_IN)
                    .alpha(SCRIM_ALPHA)
                    .start();
        } else {
            mScrimView.animate()
                    .alpha(0f)
                    .setInterpolator(ALPHA_OUT)
                    .start();
        }
    }

    /**
     * Fills in the touchable region for expanded view. This is used by window manager to
     * decide which touch events go to the expanded view.
     */
    private void getTouchableRegion(Region outRegion) {
        mTempRect.setEmpty();
        if (mIsExpanded || mEducationViewController.isEducationVisible()) {
            getBoundsOnScreen(mTempRect);
            outRegion.op(mTempRect, Region.Op.UNION);
        }
    }

}
