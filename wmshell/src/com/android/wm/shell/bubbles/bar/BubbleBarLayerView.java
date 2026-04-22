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

import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES_NOISY;
import static com.android.wm.shell.shared.animation.Interpolators.ALPHA_IN;
import static com.android.wm.shell.shared.animation.Interpolators.ALPHA_OUT;
import static com.android.wm.shell.bubbles.Bubbles.DISMISS_USER_GESTURE;
import static com.android.wm.shell.shared.bubbles.BubbleConstants.BUBBLE_EXPANDED_SCRIM_ALPHA;

import android.annotation.Nullable;
import android.content.Context;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceControl;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.R;
import com.android.wm.shell.bubbles.Bubble;
import com.android.wm.shell.bubbles.BubbleController;
import com.android.wm.shell.bubbles.BubbleData;
import com.android.wm.shell.bubbles.BubbleExpandedViewTransitionAnimator;
import com.android.wm.shell.bubbles.BubbleLogger;
import com.android.wm.shell.bubbles.BubbleOverflow;
import com.android.wm.shell.bubbles.BubblePositioner;
import com.android.wm.shell.bubbles.BubbleViewProvider;
import com.android.wm.shell.bubbles.DismissViewUtils;
import com.android.wm.shell.bubbles.bar.BubbleBarExpandedViewDragController.DragListener;
import com.android.wm.shell.shared.bubbles.BaseBubblePinController;
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper;
import com.android.wm.shell.shared.bubbles.BubbleBarLocation;
import com.android.wm.shell.shared.bubbles.DeviceConfig;
import com.android.wm.shell.shared.bubbles.DismissView;
import com.android.wm.shell.shared.bubbles.DragZone;
import com.android.wm.shell.shared.bubbles.DragZoneFactory;
import com.android.wm.shell.shared.bubbles.DraggedObject;
import com.android.wm.shell.shared.bubbles.DropTargetManager;

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
        implements ViewTreeObserver.OnComputeInternalInsetsListener,
        BubbleExpandedViewTransitionAnimator {

    private static final String TAG = BubbleBarLayerView.class.getSimpleName();

    private final BubbleController mBubbleController;
    private final BubbleData mBubbleData;
    private final BubblePositioner mPositioner;
    private final BubbleLogger mBubbleLogger;
    private final BubbleBarAnimationHelper mAnimationHelper;
    private final BubbleEducationViewController mEducationViewController;
    private final View mScrimView;
    private final BubbleExpandedViewPinController mBubbleExpandedViewPinController;
    @Nullable
    private DropTargetManager mDropTargetManager = null;
    @Nullable
    private DragZoneFactory mDragZoneFactory = null;

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
    private Insets mInsets;

    public BubbleBarLayerView(Context context, BubbleController controller, BubbleData bubbleData,
            BubbleLogger bubbleLogger) {
        super(context);
        mBubbleController = controller;
        mBubbleData = bubbleData;
        mPositioner = mBubbleController.getPositioner();
        mBubbleLogger = bubbleLogger;

        mAnimationHelper = new BubbleBarAnimationHelper(context, mPositioner);
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
        LocationChangeListener locationChangeListener = new LocationChangeListener();
        mBubbleExpandedViewPinController.setListener(locationChangeListener);

        if (BubbleAnythingFlagHelper.enableBubbleToFullscreen()) {
            mDropTargetManager = new DropTargetManager(context, this,
                    new DropTargetManager.DragZoneChangedListener() {
                        private DragZone mLastBubbleLocationDragZone = null;
                        private BubbleBarLocation mInitialLocation = null;
                        @Override
                        public void onDragEnded(@Nullable DragZone zone) {
                            if (mExpandedBubble == null || !(mExpandedBubble instanceof Bubble)) {
                                Log.w(TAG, "dropped invalid bubble: " + mExpandedBubble);
                                return;
                            }

                            final boolean isBubbleLeft = zone instanceof DragZone.Bubble.Left;
                            final boolean isBubbleRight = zone instanceof DragZone.Bubble.Right;
                            if (!isBubbleLeft && !isBubbleRight) {
                                // If we didn't finish the "change" animation make sure to animate
                                // it back to the right spot
                                locationChangeListener.onChange(mInitialLocation);
                            }
                            if (zone instanceof DragZone.FullScreen) {
                                ((Bubble) mExpandedBubble).getTaskView().moveToFullscreen();
                                // Make sure location change listener is updated with the initial
                                // location -- even if we "switched sides" during the drag, since
                                // we've ended up in fullscreen, the location shouldn't change.
                                locationChangeListener.onRelease(mInitialLocation);
                            } else if (isBubbleLeft) {
                                locationChangeListener.onRelease(BubbleBarLocation.LEFT);
                            } else if (isBubbleRight) {
                                locationChangeListener.onRelease(BubbleBarLocation.RIGHT);
                            }
                        }

                        @Override
                        public void onInitialDragZoneSet(@Nullable DragZone dragZone) {
                            mInitialLocation = dragZone instanceof DragZone.Bubble.Left
                                    ? BubbleBarLocation.LEFT
                                    : BubbleBarLocation.RIGHT;
                            locationChangeListener.onStart(mInitialLocation);
                        }

                        @Override
                        public void onDragZoneChanged(@NonNull DraggedObject draggedObject,
                                @Nullable DragZone from, @Nullable DragZone to) {
                            final boolean isBubbleLeft = to instanceof DragZone.Bubble.Left;
                            final boolean isBubbleRight = to instanceof DragZone.Bubble.Right;
                            if ((isBubbleLeft || isBubbleRight)
                                    && to != mLastBubbleLocationDragZone) {
                                mLastBubbleLocationDragZone = to;
                                locationChangeListener.onChange(isBubbleLeft
                                        ? BubbleBarLocation.LEFT
                                        : BubbleBarLocation.RIGHT);

                            }
                        }
                    });
            // TODO - currently only fullscreen is supported, should enable for split & desktop
            mDragZoneFactory = new DragZoneFactory(context, mPositioner.getCurrentConfig(),
                    new DragZoneFactory.SplitScreenModeChecker() {
                        @NonNull
                        @Override
                        public SplitScreenMode getSplitScreenMode() {
                            return SplitScreenMode.UNSUPPORTED;
                        }
                    },
                    new DragZoneFactory.DesktopWindowModeChecker() {
                        @Override
                        public boolean isSupported() {
                            return false;
                        }
                    },
                    new DragZoneFactory.BubbleBarPropertiesProvider() {
                        // this is only used in launcher
                        @Override
                        public int getBottomPadding() {
                            return 0;
                        }

                        @Override
                        public int getWidth() {
                            return 0;
                        }

                        @Override
                        public int getHeight() {
                            return 0;
                        }
                    });
        }
        setOnClickListener(view -> hideModalOrCollapse());
    }

    /** Hides the expanded view drop target. */
    public void hideBubbleBarExpandedViewDropTarget() {
        mBubbleExpandedViewPinController.hideDropTarget();
    }

    /** Shows the expanded view drop target at the requested {@link BubbleBarLocation location} */
    public void showBubbleBarExtendedViewDropTarget(@NonNull BubbleBarLocation bubbleBarLocation) {
        setVisibility(VISIBLE);
        mBubbleExpandedViewPinController.showDropTarget(bubbleBarLocation);
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
    @Override
    public boolean isExpanded() {
        return mIsExpanded;
    }

    /** Return whether the expanded view is being dragged */
    public boolean isExpandedViewDragged() {
        return mDragController != null && mDragController.isDragged();
    }

    /** Shows the expanded view of the provided bubble. */
    public void showExpandedView(BubbleViewProvider b) {
        if (!canExpandView(b)) return;
        animateExpand(prepareExpandedView(b));
    }

    /**
     * @return whether it's possible to expand {@param b} right now. This is {@code false} if
     *         the bubble has no view or if the bubble is already showing.
     */
    @Override
    public boolean canExpandView(BubbleViewProvider b) {
        if (b.getBubbleBarExpandedView() == null) return false;
        if (mExpandedBubble != null && mIsExpanded && b.getKey().equals(mExpandedBubble.getKey())) {
            // Already showing this bubble so can't expand it.
            return false;
        }
        return true;
    }

    @Override
    public void removeViewFromTransition(View view) {
        removeView(view);
    }

    /**
     * Prepares the expanded view of the provided bubble to be shown. This includes removing any
     * stale content and cancelling any related animations.
     *
     * @return previous open bubble if there was one.
     */
    private BubbleViewProvider prepareExpandedView(BubbleViewProvider b) {
        if (!canExpandView(b)) {
            throw new IllegalStateException("Can't prepare expand. Check canExpandView(b) first.");
        }
        BubbleBarExpandedView expandedView = b.getBubbleBarExpandedView();
        BubbleViewProvider previousBubble = null;
        if (mExpandedBubble != null && !b.getKey().equals(mExpandedBubble.getKey())) {
            if (mIsExpanded && mExpandedBubble.getBubbleBarExpandedView() != null) {
                // Previous expanded view open, keep it visible to animate the switch
                previousBubble = mExpandedBubble;
            } else {
                removeView(mExpandedView);
            }
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
            if (width <= 0 || height <= 0) {
                Log.e(TAG,
                        String.format("got expanded view with non-positive width=%d or height=%d."
                                        + " this could result in the expanded view not having a"
                                        + " surface!",
                                width, height));
            }

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
                    logBubbleEvent(BubbleLogger.Event.BUBBLE_BAR_BUBBLE_DISMISSED_DRAG_EXP_VIEW);
                }
            };
            mDragController = new BubbleBarExpandedViewDragController(
                    mContext,
                    mExpandedView,
                    mDismissView,
                    mAnimationHelper,
                    mPositioner,
                    mBubbleExpandedViewPinController,
                    mDropTargetManager,
                    mDragZoneFactory,
                    dragListener);

            addView(mExpandedView, new LayoutParams(width, height, Gravity.LEFT));
        }

        if (mEducationViewController.isEducationVisible()) {
            mEducationViewController.hideEducation(/* animated = */ true);
        }

        mIsExpanded = true;
        mBubbleController.getSysuiProxy().onStackExpandChanged(true);
        showScrim(true);
        return previousBubble;
    }

    /**
     * Performs an animation to open a bubble with content that is not already visible.
     *
     * @param previousBubble If non-null, this is a bubble that is already showing before the new
     *                       bubble is expanded.
     */
    public void animateExpand(BubbleViewProvider previousBubble) {
        animateExpand(previousBubble, null /* finishCallback */);
    }

    /**
     * Performs an animation to open a bubble with content that is not already visible.
     *
     * @param previousBubble If non-null, this is a bubble that is already showing before the new
     *                       bubble is expanded.
     * @param animFinish If non-null, the callback triggered after the expand animation completes
     */
    @Override
    public void animateExpand(BubbleViewProvider previousBubble,
            @Nullable Runnable animFinish) {
        if (!mIsExpanded || mExpandedBubble == null) {
            throw new IllegalStateException("Can't animateExpand without expnaded state");
        }
        final Runnable afterAnimation = () -> {
            if (mExpandedView == null) return;
            // Touch delegate for the menu
            BubbleBarHandleView view = mExpandedView.getHandleView();
            view.getBoundsOnScreen(mHandleTouchBounds);
            // Move top value up to ensure touch target is large enough
            mHandleTouchBounds.top -= mPositioner.getBubblePaddingTop();
            mHandleTouchDelegate = new TouchDelegate(mHandleTouchBounds,
                    mExpandedView.getHandleView());
            setTouchDelegate(mHandleTouchDelegate);

            if (animFinish != null) {
                animFinish.run();
            }
        };

        if (previousBubble != null) {
            final BubbleBarExpandedView previousExpandedView =
                    previousBubble.getBubbleBarExpandedView();
            mAnimationHelper.animateSwitch(previousBubble, mExpandedBubble, () -> {
                removeView(previousExpandedView);
                afterAnimation.run();
            });
        } else {
            mAnimationHelper.animateExpansion(mExpandedBubble, afterAnimation);
        }
    }

    /**
     * Like {@link #prepareExpandedView} but also makes the current expanded bubble visible
     * immediately so it gets a surface that can be animated. Since the surface may not be ready
     * yet, this keeps the TaskView alpha=0.
     */
    @Override
    public BubbleViewProvider prepareConvertedView(BubbleViewProvider b) {
        final BubbleViewProvider prior = prepareExpandedView(b);

        final BubbleBarExpandedView bbev = mExpandedBubble.getBubbleBarExpandedView();
        if (bbev != null) {
            updateExpandedView();
            bbev.setAnimating(true);
            bbev.setContentVisibility(true);
            bbev.setSurfaceZOrderedOnTop(true);
            bbev.setTaskViewAlpha(0.f);
            bbev.setVisibility(VISIBLE);
        }

        return prior;
    }

    /**
     * Starts and animates a conversion-from transition.
     *
     * @param startT A transaction with first-frame work. this *will* be applied here!
     */
    @Override
    public void animateConvert(@NonNull SurfaceControl.Transaction startT,
            @NonNull Rect startBounds, float startScale, @NonNull SurfaceControl snapshot,
            SurfaceControl taskLeash, Runnable animFinish) {
        if (!mIsExpanded || mExpandedBubble == null) {
            throw new IllegalStateException("Can't animateExpand without expanded state");
        }
        mAnimationHelper.animateConvert(mExpandedBubble, startT, startBounds, startScale, snapshot,
                taskLeash, animFinish);
    }

    public void removeBubble(@NonNull Bubble bubble, @NonNull Runnable endAction) {
        final boolean inTransition = bubble.getPreparingTransition() != null;
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY,
                "BBLayerView.removeBubble(): bubble=%s hasBubbles=%b inTransition=%b",
                bubble, !mBubbleData.getBubbles().isEmpty(), inTransition);
        Runnable cleanUp = () -> {
            // The transition is already managing the task/wm state.
            bubble.cleanupViews(!inTransition);
            endAction.run();
        };
        if (mBubbleData.getBubbles().isEmpty() || inTransition) {
            if (mExpandedBubble != null && mExpandedBubble.getKey().equals(bubble.getKey())) {
                // If we are removing the last bubble or removing the current bubble via transition,
                // collapse the expanded view and clean up bubbles at the end.
                collapse(cleanUp);
            } else {
                ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "  Skipping, does not match expanded view");
                cleanUp.run();
            }
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
        DismissViewUtils.setupWithMarginIgnoringNavBarInset(
                mDismissView, R.dimen.bubble_bar_dismiss_view_bottom_margin);
        addView(mDismissView);
    }

    /** Hides the current modal education/menu view, IME or collapses the expanded view */
    private void hideModalOrCollapse() {
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "hideModalOrCollapse(): expanded=%s",
                mExpandedBubble != null ? mExpandedBubble.getKey() : "null");
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
        if (mExpandedView == null || mExpandedBubble == null || mExpandedView.isAnimating()) return;
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
        mExpandedView.updateBottomClip();
    }

    private void showScrim(boolean show) {
        if (show) {
            mScrimView.animate()
                    .setInterpolator(ALPHA_IN)
                    .alpha(BUBBLE_EXPANDED_SCRIM_ALPHA)
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

    /** Handles IME position changes. */
    public void onImeTopChanged(int imeTop) {
        if (mIsExpanded) {
            mAnimationHelper.onImeTopChanged(imeTop);
        }
    }

    /**
     * Log the event only if {@link #mExpandedBubble} is a {@link Bubble}.
     * <p>
     * Skips logging if it is {@link BubbleOverflow}.
     */
    private void logBubbleEvent(BubbleLogger.Event event) {
        if (mExpandedBubble != null && mExpandedBubble instanceof Bubble) {
            mBubbleLogger.log((Bubble) mExpandedBubble, event);
        }
    }

    @Nullable
    @VisibleForTesting
    public BubbleBarExpandedViewDragController getDragController() {
        return mDragController;
    }

    /** Notifies view of device config update. */
    public void update(DeviceConfig deviceConfig) {
        Insets newInsets = deviceConfig.getInsets();
        if (!newInsets.equals(mInsets)) {
            mInsets = newInsets;
            updateExpandedView();
        }
    }

    private class LocationChangeListener implements
            BaseBubblePinController.LocationChangeListener {

        private BubbleBarLocation mInitialLocation;

        @Override
        public void onStart(@NonNull BubbleBarLocation location) {
            mInitialLocation = location;
        }

        @Override
        public void onChange(@NonNull BubbleBarLocation bubbleBarLocation) {
            mBubbleController.animateBubbleBarLocation(bubbleBarLocation);
        }

        @Override
        public void onRelease(@NonNull BubbleBarLocation location) {
            mBubbleController.setBubbleBarLocation(location,
                    BubbleBarLocation.UpdateSource.DRAG_EXP_VIEW);
            if (location != mInitialLocation) {
                BubbleLogger.Event event = location.isOnLeft(isLayoutRtl())
                        ? BubbleLogger.Event.BUBBLE_BAR_MOVED_LEFT_DRAG_EXP_VIEW
                        : BubbleLogger.Event.BUBBLE_BAR_MOVED_RIGHT_DRAG_EXP_VIEW;
                logBubbleEvent(event);
            }
        }
    }
}
