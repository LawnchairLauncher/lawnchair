/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3.dragndrop;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Region;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.AppWidgetResizeFrame;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DropTargetBar;
import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppWidgetHostView;
import com.android.launcher3.PinchToOverviewListener;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.AllAppsTransitionController;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.keyboard.ViewGroupFocusHelper;
import com.android.launcher3.logging.LoggerUtils;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.widget.WidgetsBottomSheet;

import java.util.ArrayList;

/**
 * A ViewGroup that coordinates dragging across its descendants
 */
public class DragLayer extends InsettableFrameLayout {

    public static final int ANIMATION_END_DISAPPEAR = 0;
    public static final int ANIMATION_END_REMAIN_VISIBLE = 2;

    // Scrim color without any alpha component.
    private static final int SCRIM_COLOR = Color.BLACK & 0x00FFFFFF;

    private final int[] mTmpXY = new int[2];

    @Thunk DragController mDragController;

    private Launcher mLauncher;

    // Variables relating to resizing widgets
    private final boolean mIsRtl;
    private AppWidgetResizeFrame mCurrentResizeFrame;

    // Variables relating to animation of views after drop
    private ValueAnimator mDropAnim = null;
    private final TimeInterpolator mCubicEaseOutInterpolator = new DecelerateInterpolator(1.5f);
    @Thunk DragView mDropView = null;
    @Thunk int mAnchorViewInitialScrollX = 0;
    @Thunk View mAnchorView = null;

    private boolean mHoverPointClosesFolder = false;
    private final Rect mHitRect = new Rect();
    private final Rect mHighlightRect = new Rect();

    private TouchCompleteListener mTouchCompleteListener;

    private int mTopViewIndex;
    private int mChildCountOnLastUpdate = -1;

    // Darkening scrim
    private float mBackgroundAlpha = 0;

    // Related to adjacent page hints
    private final Rect mScrollChildPosition = new Rect();
    private final ViewGroupFocusHelper mFocusIndicatorHelper;

    // Related to pinch-to-go-to-overview gesture.
    private PinchToOverviewListener mPinchListener = null;

    // Handles all apps pull up interaction
    private AllAppsTransitionController mAllAppsController;

    private TouchController mActiveController;
    /**
     * Used to create a new DragLayer from XML.
     *
     * @param context The application's context.
     * @param attrs The attributes set containing the Workspace's customization values.
     */
    public DragLayer(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Disable multitouch across the workspace/all apps/customize tray
        setMotionEventSplittingEnabled(false);
        setChildrenDrawingOrderEnabled(true);

        mIsRtl = Utilities.isRtl(getResources());
        mFocusIndicatorHelper = new ViewGroupFocusHelper(this);
    }

    public void setup(Launcher launcher, DragController dragController,
            AllAppsTransitionController allAppsTransitionController) {
        mLauncher = launcher;
        mDragController = dragController;
        mAllAppsController = allAppsTransitionController;

        boolean isAccessibilityEnabled = ((AccessibilityManager) mLauncher.getSystemService(
                Context.ACCESSIBILITY_SERVICE)).isEnabled();
        onAccessibilityStateChanged(isAccessibilityEnabled);
    }

    public ViewGroupFocusHelper getFocusIndicatorHelper() {
        return mFocusIndicatorHelper;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return mDragController.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    public void onAccessibilityStateChanged(boolean isAccessibilityEnabled) {
        mPinchListener = FeatureFlags.LAUNCHER3_DISABLE_PINCH_TO_OVERVIEW || isAccessibilityEnabled
                ? null : new PinchToOverviewListener(mLauncher);
    }

    public boolean isEventOverPageIndicator(MotionEvent ev) {
        return isEventOverView(mLauncher.getWorkspace().getPageIndicator(), ev);
    }

    public boolean isEventOverHotseat(MotionEvent ev) {
        return isEventOverView(mLauncher.getHotseat(), ev);
    }

    private boolean isEventOverFolder(Folder folder, MotionEvent ev) {
        return isEventOverView(folder, ev);
    }

    private boolean isEventOverDropTargetBar(MotionEvent ev) {
        return isEventOverView(mLauncher.getDropTargetBar(), ev);
    }

    public boolean isEventOverView(View view, MotionEvent ev) {
        getDescendantRectRelativeToSelf(view, mHitRect);
        return mHitRect.contains((int) ev.getX(), (int) ev.getY());
    }

    private boolean handleTouchDown(MotionEvent ev, boolean intercept) {
        AbstractFloatingView topView = AbstractFloatingView.getTopOpenView(mLauncher);
        if (topView != null && intercept) {
            ExtendedEditText textView = topView.getActiveTextView();
            if (textView != null) {
                if (!isEventOverView(textView, ev)) {
                    textView.dispatchBackKey();
                    return true;
                }
            } else if (!isEventOverView(topView, ev)) {
                if (isInAccessibleDrag()) {
                    // Do not close the container if in drag and drop.
                    if (!isEventOverDropTargetBar(ev)) {
                        return true;
                    }
                } else {
                    mLauncher.getUserEventDispatcher().logActionTapOutside(
                            LoggerUtils.newContainerTarget(topView.getLogContainerType()));
                    topView.close(true);

                    // We let touches on the original icon go through so that users can launch
                    // the app with one tap if they don't find a shortcut they want.
                    View extendedTouch = topView.getExtendedTouchView();
                    return extendedTouch == null || !isEventOverView(extendedTouch, ev);
                }
            }
        }
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getAction();

        if (action == MotionEvent.ACTION_DOWN) {
            // Cancel discovery bounce animation when a user start interacting on anywhere on
            // dray layer even if mAllAppsController is NOT the active controller.
            // TODO: handle other input other than touch
            mAllAppsController.cancelDiscoveryAnimation();
            if (handleTouchDown(ev, true)) {
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (mTouchCompleteListener != null) {
                mTouchCompleteListener.onTouchComplete();
            }
            mTouchCompleteListener = null;
        }
        mActiveController = null;

        if (mCurrentResizeFrame != null
                && mCurrentResizeFrame.onControllerInterceptTouchEvent(ev)) {
            mActiveController = mCurrentResizeFrame;
            return true;
        } else {
            clearResizeFrame();
        }

        if (mDragController.onControllerInterceptTouchEvent(ev)) {
            mActiveController = mDragController;
            return true;
        }

        if (FeatureFlags.LAUNCHER3_ALL_APPS_PULL_UP && mAllAppsController.onControllerInterceptTouchEvent(ev)) {
            mActiveController = mAllAppsController;
            return true;
        }

        WidgetsBottomSheet widgetsBottomSheet = WidgetsBottomSheet.getOpen(mLauncher);
        if (widgetsBottomSheet != null && widgetsBottomSheet.onControllerInterceptTouchEvent(ev)) {
            mActiveController = widgetsBottomSheet;
            return true;
        }

        if (mPinchListener != null && mPinchListener.onControllerInterceptTouchEvent(ev)) {
            // Stop listening for scrolling etc. (onTouchEvent() handles the rest of the pinch.)
            mActiveController = mPinchListener;
            return true;
        }
        return false;
    }

    @Override
    public boolean onInterceptHoverEvent(MotionEvent ev) {
        if (mLauncher == null || mLauncher.getWorkspace() == null) {
            return false;
        }
        Folder currentFolder = Folder.getOpen(mLauncher);
        if (currentFolder == null) {
            return false;
        } else {
                AccessibilityManager accessibilityManager = (AccessibilityManager)
                        getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (accessibilityManager.isTouchExplorationEnabled()) {
                final int action = ev.getAction();
                boolean isOverFolderOrSearchBar;
                switch (action) {
                    case MotionEvent.ACTION_HOVER_ENTER:
                        isOverFolderOrSearchBar = isEventOverFolder(currentFolder, ev) ||
                            (isInAccessibleDrag() && isEventOverDropTargetBar(ev));
                        if (!isOverFolderOrSearchBar) {
                            sendTapOutsideFolderAccessibilityEvent(currentFolder.isEditingName());
                            mHoverPointClosesFolder = true;
                            return true;
                        }
                        mHoverPointClosesFolder = false;
                        break;
                    case MotionEvent.ACTION_HOVER_MOVE:
                        isOverFolderOrSearchBar = isEventOverFolder(currentFolder, ev) ||
                            (isInAccessibleDrag() && isEventOverDropTargetBar(ev));
                        if (!isOverFolderOrSearchBar && !mHoverPointClosesFolder) {
                            sendTapOutsideFolderAccessibilityEvent(currentFolder.isEditingName());
                            mHoverPointClosesFolder = true;
                            return true;
                        } else if (!isOverFolderOrSearchBar) {
                            return true;
                        }
                        mHoverPointClosesFolder = false;
                }
            }
        }
        return false;
    }

    private void sendTapOutsideFolderAccessibilityEvent(boolean isEditingName) {
        int stringId = isEditingName ? R.string.folder_tap_to_rename : R.string.folder_tap_to_close;
        Utilities.sendCustomAccessibilityEvent(
                this, AccessibilityEvent.TYPE_VIEW_FOCUSED, getContext().getString(stringId));
    }

    private boolean isInAccessibleDrag() {
        return mLauncher.getAccessibilityDelegate().isInAccessibleDrag();
    }

    @Override
    public boolean onRequestSendAccessibilityEvent(View child, AccessibilityEvent event) {
        // Shortcuts can appear above folder
        View topView = AbstractFloatingView.getTopOpenView(mLauncher);
        if (topView != null) {
            if (child == topView) {
                return super.onRequestSendAccessibilityEvent(child, event);
            }
            if (isInAccessibleDrag() && child instanceof DropTargetBar) {
                return super.onRequestSendAccessibilityEvent(child, event);
            }
            // Skip propagating onRequestSendAccessibilityEvent for all other children
            // which are not topView
            return false;
        }
        return super.onRequestSendAccessibilityEvent(child, event);
    }

    @Override
    public void addChildrenForAccessibility(ArrayList<View> childrenForAccessibility) {
        View topView = AbstractFloatingView.getTopOpenView(mLauncher);
        if (topView != null) {
            // Only add the top view as a child for accessibility when it is open
            childrenForAccessibility.add(topView);

            if (isInAccessibleDrag()) {
                childrenForAccessibility.add(mLauncher.getDropTargetBar());
            }
        } else {
            super.addChildrenForAccessibility(childrenForAccessibility);
        }
    }

    @Override
    public boolean onHoverEvent(MotionEvent ev) {
        // If we've received this, we've already done the necessary handling
        // in onInterceptHoverEvent. Return true to consume the event.
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getAction();

        if (action == MotionEvent.ACTION_DOWN) {
            if (handleTouchDown(ev, false)) {
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (mTouchCompleteListener != null) {
                mTouchCompleteListener.onTouchComplete();
            }
            mTouchCompleteListener = null;
        }

        if (mActiveController != null) {
            return mActiveController.onControllerTouchEvent(ev);
        }
        return false;
    }

    /**
     * Determine the rect of the descendant in this DragLayer's coordinates
     *
     * @param descendant The descendant whose coordinates we want to find.
     * @param r The rect into which to place the results.
     * @return The factor by which this descendant is scaled relative to this DragLayer.
     */
    public float getDescendantRectRelativeToSelf(View descendant, Rect r) {
        mTmpXY[0] = 0;
        mTmpXY[1] = 0;
        float scale = getDescendantCoordRelativeToSelf(descendant, mTmpXY);

        r.set(mTmpXY[0], mTmpXY[1],
                (int) (mTmpXY[0] + scale * descendant.getMeasuredWidth()),
                (int) (mTmpXY[1] + scale * descendant.getMeasuredHeight()));
        return scale;
    }

    public float getLocationInDragLayer(View child, int[] loc) {
        loc[0] = 0;
        loc[1] = 0;
        return getDescendantCoordRelativeToSelf(child, loc);
    }

    public float getDescendantCoordRelativeToSelf(View descendant, int[] coord) {
        return getDescendantCoordRelativeToSelf(descendant, coord, false);
    }

    /**
     * Given a coordinate relative to the descendant, find the coordinate in this DragLayer's
     * coordinates.
     *
     * @param descendant The descendant to which the passed coordinate is relative.
     * @param coord The coordinate that we want mapped.
     * @param includeRootScroll Whether or not to account for the scroll of the root descendant:
     *          sometimes this is relevant as in a child's coordinates within the root descendant.
     * @return The factor by which this descendant is scaled relative to this DragLayer. Caution
     *         this scale factor is assumed to be equal in X and Y, and so if at any point this
     *         assumption fails, we will need to return a pair of scale factors.
     */
    public float getDescendantCoordRelativeToSelf(View descendant, int[] coord,
            boolean includeRootScroll) {
        return Utilities.getDescendantCoordRelativeToAncestor(descendant, this,
                coord, includeRootScroll);
    }

    /**
     * Inverse of {@link #getDescendantCoordRelativeToSelf(View, int[])}.
     */
    public void mapCoordInSelfToDescendant(View descendant, int[] coord) {
        Utilities.mapCoordInSelfToDescendant(descendant, this, coord);
    }

    public void getViewRectRelativeToSelf(View v, Rect r) {
        int[] loc = new int[2];
        getLocationInWindow(loc);
        int x = loc[0];
        int y = loc[1];

        v.getLocationInWindow(loc);
        int vX = loc[0];
        int vY = loc[1];

        int left = vX - x;
        int top = vY - y;
        r.set(left, top, left + v.getMeasuredWidth(), top + v.getMeasuredHeight());
    }

    @Override
    public boolean dispatchUnhandledMove(View focused, int direction) {
        // Consume the unhandled move if a container is open, to avoid switching pages underneath.
        boolean isContainerOpen = AbstractFloatingView.getTopOpenView(mLauncher) != null;
        return isContainerOpen || mDragController.dispatchUnhandledMove(focused, direction);
    }

    @Override
    public void setInsets(Rect insets) {
        super.setInsets(insets);
        setBackgroundResource(insets.top == 0 ? 0 : R.drawable.workspace_bg);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    // Override to allow type-checking of LayoutParams.
    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    public static class LayoutParams extends InsettableFrameLayout.LayoutParams {
        public int x, y;
        public boolean customPosition = false;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(ViewGroup.LayoutParams lp) {
            super(lp);
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getWidth() {
            return width;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public int getHeight() {
            return height;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getX() {
            return x;
        }

        public void setY(int y) {
            this.y = y;
        }

        public int getY() {
            return y;
        }
    }

    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            final FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) child.getLayoutParams();
            if (flp instanceof LayoutParams) {
                final LayoutParams lp = (LayoutParams) flp;
                if (lp.customPosition) {
                    child.layout(lp.x, lp.y, lp.x + lp.width, lp.y + lp.height);
                }
            }
        }
    }

    public void clearResizeFrame() {
        if (mCurrentResizeFrame != null) {
            mCurrentResizeFrame.commitResize();
            removeView(mCurrentResizeFrame);
            mCurrentResizeFrame = null;
        }
    }

    public void addResizeFrame(LauncherAppWidgetHostView widget, CellLayout cellLayout) {
        clearResizeFrame();

        mCurrentResizeFrame = (AppWidgetResizeFrame) LayoutInflater.from(mLauncher)
                .inflate(R.layout.app_widget_resize_frame, this, false);
        mCurrentResizeFrame.setupForWidget(widget, cellLayout, this);
        ((LayoutParams) mCurrentResizeFrame.getLayoutParams()).customPosition = true;

        addView(mCurrentResizeFrame);
        mCurrentResizeFrame.snapToWidget(false);
    }

    public void animateViewIntoPosition(DragView dragView, final int[] pos, float alpha,
            float scaleX, float scaleY, int animationEndStyle, Runnable onFinishRunnable,
            int duration) {
        Rect r = new Rect();
        getViewRectRelativeToSelf(dragView, r);
        final int fromX = r.left;
        final int fromY = r.top;

        animateViewIntoPosition(dragView, fromX, fromY, pos[0], pos[1], alpha, 1, 1, scaleX, scaleY,
                onFinishRunnable, animationEndStyle, duration, null);
    }

    public void animateViewIntoPosition(DragView dragView, final View child,
            final Runnable onFinishAnimationRunnable, View anchorView) {
        animateViewIntoPosition(dragView, child, -1, onFinishAnimationRunnable, anchorView);
    }

    public void animateViewIntoPosition(DragView dragView, final View child, int duration,
            final Runnable onFinishAnimationRunnable, View anchorView) {
        ShortcutAndWidgetContainer parentChildren = (ShortcutAndWidgetContainer) child.getParent();
        CellLayout.LayoutParams lp =  (CellLayout.LayoutParams) child.getLayoutParams();
        parentChildren.measureChild(child);

        Rect r = new Rect();
        getViewRectRelativeToSelf(dragView, r);

        int coord[] = new int[2];
        float childScale = child.getScaleX();
        coord[0] = lp.x + (int) (child.getMeasuredWidth() * (1 - childScale) / 2);
        coord[1] = lp.y + (int) (child.getMeasuredHeight() * (1 - childScale) / 2);

        // Since the child hasn't necessarily been laid out, we force the lp to be updated with
        // the correct coordinates (above) and use these to determine the final location
        float scale = getDescendantCoordRelativeToSelf((View) child.getParent(), coord);
        // We need to account for the scale of the child itself, as the above only accounts for
        // for the scale in parents.
        scale *= childScale;
        int toX = coord[0];
        int toY = coord[1];
        float toScale = scale;
        if (child instanceof TextView) {
            TextView tv = (TextView) child;
            // Account for the source scale of the icon (ie. from AllApps to Workspace, in which
            // the workspace may have smaller icon bounds).
            toScale = scale / dragView.getIntrinsicIconScaleFactor();

            // The child may be scaled (always about the center of the view) so to account for it,
            // we have to offset the position by the scaled size.  Once we do that, we can center
            // the drag view about the scaled child view.
            toY += Math.round(toScale * tv.getPaddingTop());
            toY -= dragView.getMeasuredHeight() * (1 - toScale) / 2;
            if (dragView.getDragVisualizeOffset() != null) {
                toY -=  Math.round(toScale * dragView.getDragVisualizeOffset().y);
            }

            toX -= (dragView.getMeasuredWidth() - Math.round(scale * child.getMeasuredWidth())) / 2;
        } else if (child instanceof FolderIcon) {
            // Account for holographic blur padding on the drag view
            toY += Math.round(scale * (child.getPaddingTop() - dragView.getDragRegionTop()));
            toY -= scale * dragView.getBlurSizeOutline() / 2;
            toY -= (1 - scale) * dragView.getMeasuredHeight() / 2;
            // Center in the x coordinate about the target's drawable
            toX -= (dragView.getMeasuredWidth() - Math.round(scale * child.getMeasuredWidth())) / 2;
        } else {
            toY -= (Math.round(scale * (dragView.getHeight() - child.getMeasuredHeight()))) / 2;
            toX -= (Math.round(scale * (dragView.getMeasuredWidth()
                    - child.getMeasuredWidth()))) / 2;
        }

        final int fromX = r.left;
        final int fromY = r.top;
        child.setVisibility(INVISIBLE);
        Runnable onCompleteRunnable = new Runnable() {
            public void run() {
                child.setVisibility(VISIBLE);
                if (onFinishAnimationRunnable != null) {
                    onFinishAnimationRunnable.run();
                }
            }
        };
        animateViewIntoPosition(dragView, fromX, fromY, toX, toY, 1, 1, 1, toScale, toScale,
                onCompleteRunnable, ANIMATION_END_DISAPPEAR, duration, anchorView);
    }

    public void animateViewIntoPosition(final DragView view, final int fromX, final int fromY,
            final int toX, final int toY, float finalAlpha, float initScaleX, float initScaleY,
            float finalScaleX, float finalScaleY, Runnable onCompleteRunnable,
            int animationEndStyle, int duration, View anchorView) {
        Rect from = new Rect(fromX, fromY, fromX +
                view.getMeasuredWidth(), fromY + view.getMeasuredHeight());
        Rect to = new Rect(toX, toY, toX + view.getMeasuredWidth(), toY + view.getMeasuredHeight());
        animateView(view, from, to, finalAlpha, initScaleX, initScaleY, finalScaleX, finalScaleY, duration,
                null, null, onCompleteRunnable, animationEndStyle, anchorView);
    }

    /**
     * This method animates a view at the end of a drag and drop animation.
     *
     * @param view The view to be animated. This view is drawn directly into DragLayer, and so
     *        doesn't need to be a child of DragLayer.
     * @param from The initial location of the view. Only the left and top parameters are used.
     * @param to The final location of the view. Only the left and top parameters are used. This
     *        location doesn't account for scaling, and so should be centered about the desired
     *        final location (including scaling).
     * @param finalAlpha The final alpha of the view, in case we want it to fade as it animates.
     * @param finalScaleX The final scale of the view. The view is scaled about its center.
     * @param finalScaleY The final scale of the view. The view is scaled about its center.
     * @param duration The duration of the animation.
     * @param motionInterpolator The interpolator to use for the location of the view.
     * @param alphaInterpolator The interpolator to use for the alpha of the view.
     * @param onCompleteRunnable Optional runnable to run on animation completion.
     * @param animationEndStyle Whether or not to fade out the view once the animation completes.
     *        {@link #ANIMATION_END_DISAPPEAR} or {@link #ANIMATION_END_REMAIN_VISIBLE}.
     * @param anchorView If not null, this represents the view which the animated view stays
     *        anchored to in case scrolling is currently taking place. Note: currently this is
     *        only used for the X dimension for the case of the workspace.
     */
    public void animateView(final DragView view, final Rect from, final Rect to,
            final float finalAlpha, final float initScaleX, final float initScaleY,
            final float finalScaleX, final float finalScaleY, int duration,
            final Interpolator motionInterpolator, final Interpolator alphaInterpolator,
            final Runnable onCompleteRunnable, final int animationEndStyle, View anchorView) {

        // Calculate the duration of the animation based on the object's distance
        final float dist = (float) Math.hypot(to.left - from.left, to.top - from.top);
        final Resources res = getResources();
        final float maxDist = (float) res.getInteger(R.integer.config_dropAnimMaxDist);

        // If duration < 0, this is a cue to compute the duration based on the distance
        if (duration < 0) {
            duration = res.getInteger(R.integer.config_dropAnimMaxDuration);
            if (dist < maxDist) {
                duration *= mCubicEaseOutInterpolator.getInterpolation(dist / maxDist);
            }
            duration = Math.max(duration, res.getInteger(R.integer.config_dropAnimMinDuration));
        }

        // Fall back to cubic ease out interpolator for the animation if none is specified
        TimeInterpolator interpolator = null;
        if (alphaInterpolator == null || motionInterpolator == null) {
            interpolator = mCubicEaseOutInterpolator;
        }

        // Animate the view
        final float initAlpha = view.getAlpha();
        final float dropViewScale = view.getScaleX();
        AnimatorUpdateListener updateCb = new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                final float percent = (Float) animation.getAnimatedValue();
                final int width = view.getMeasuredWidth();
                final int height = view.getMeasuredHeight();

                float alphaPercent = alphaInterpolator == null ? percent :
                        alphaInterpolator.getInterpolation(percent);
                float motionPercent = motionInterpolator == null ? percent :
                        motionInterpolator.getInterpolation(percent);

                float initialScaleX = initScaleX * dropViewScale;
                float initialScaleY = initScaleY * dropViewScale;
                float scaleX = finalScaleX * percent + initialScaleX * (1 - percent);
                float scaleY = finalScaleY * percent + initialScaleY * (1 - percent);
                float alpha = finalAlpha * alphaPercent + initAlpha * (1 - alphaPercent);

                float fromLeft = from.left + (initialScaleX - 1f) * width / 2;
                float fromTop = from.top + (initialScaleY - 1f) * height / 2;

                int x = (int) (fromLeft + Math.round(((to.left - fromLeft) * motionPercent)));
                int y = (int) (fromTop + Math.round(((to.top - fromTop) * motionPercent)));

                int anchorAdjust = mAnchorView == null ? 0 : (int) (mAnchorView.getScaleX() *
                    (mAnchorViewInitialScrollX - mAnchorView.getScrollX()));

                int xPos = x - mDropView.getScrollX() + anchorAdjust;
                int yPos = y - mDropView.getScrollY();

                mDropView.setTranslationX(xPos);
                mDropView.setTranslationY(yPos);
                mDropView.setScaleX(scaleX);
                mDropView.setScaleY(scaleY);
                mDropView.setAlpha(alpha);
            }
        };
        animateView(view, updateCb, duration, interpolator, onCompleteRunnable, animationEndStyle,
                anchorView);
    }

    public void animateView(final DragView view, AnimatorUpdateListener updateCb, int duration,
            TimeInterpolator interpolator, final Runnable onCompleteRunnable,
            final int animationEndStyle, View anchorView) {
        // Clean up the previous animations
        if (mDropAnim != null) mDropAnim.cancel();

        // Show the drop view if it was previously hidden
        mDropView = view;
        mDropView.cancelAnimation();
        mDropView.requestLayout();

        // Set the anchor view if the page is scrolling
        if (anchorView != null) {
            mAnchorViewInitialScrollX = anchorView.getScrollX();
        }
        mAnchorView = anchorView;

        // Create and start the animation
        mDropAnim = new ValueAnimator();
        mDropAnim.setInterpolator(interpolator);
        mDropAnim.setDuration(duration);
        mDropAnim.setFloatValues(0f, 1f);
        mDropAnim.addUpdateListener(updateCb);
        mDropAnim.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animation) {
                if (onCompleteRunnable != null) {
                    onCompleteRunnable.run();
                }
                switch (animationEndStyle) {
                case ANIMATION_END_DISAPPEAR:
                    clearAnimatedView();
                    break;
                case ANIMATION_END_REMAIN_VISIBLE:
                    break;
                }
            }
        });
        mDropAnim.start();
    }

    public void clearAnimatedView() {
        if (mDropAnim != null) {
            mDropAnim.cancel();
        }
        if (mDropView != null) {
            mDragController.onDeferredEndDrag(mDropView);
        }
        mDropView = null;
        invalidate();
    }

    public View getAnimatedView() {
        return mDropView;
    }

    @Override
    public void onChildViewAdded(View parent, View child) {
        super.onChildViewAdded(parent, child);
        updateChildIndices();
    }

    @Override
    public void onChildViewRemoved(View parent, View child) {
        updateChildIndices();
    }

    @Override
    public void bringChildToFront(View child) {
        super.bringChildToFront(child);
        updateChildIndices();
    }

    private void updateChildIndices() {
        mTopViewIndex = -1;
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            if (getChildAt(i) instanceof DragView) {
                mTopViewIndex = i;
            }
        }
        mChildCountOnLastUpdate = childCount;
    }

    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        if (mChildCountOnLastUpdate != childCount) {
            // between platform versions 17 and 18, behavior for onChildViewRemoved / Added changed.
            // Pre-18, the child was not added / removed by the time of those callbacks. We need to
            // force update our representation of things here to avoid crashing on pre-18 devices
            // in certain instances.
            updateChildIndices();
        }

        // i represents the current draw iteration
        if (mTopViewIndex == -1) {
            // in general we do nothing
            return i;
        } else if (i == childCount - 1) {
            // if we have a top index, we return it when drawing last item (highest z-order)
            return mTopViewIndex;
        } else if (i < mTopViewIndex) {
            return i;
        } else {
            // for indexes greater than the top index, we fetch one item above to shift for the
            // displacement of the top index
            return i + 1;
        }
    }

    public void invalidateScrim() {
        if (mBackgroundAlpha > 0.0f) {
            invalidate();
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // Draw the background below children.
        if (mBackgroundAlpha > 0.0f) {
            // Update the scroll position first to ensure scrim cutout is in the right place.
            mLauncher.getWorkspace().computeScrollWithoutInvalidation();

            int alpha = (int) (mBackgroundAlpha * 255);
            CellLayout currCellLayout = mLauncher.getWorkspace().getCurrentDragOverlappingLayout();
            canvas.save();
            if (currCellLayout != null && currCellLayout != mLauncher.getHotseat().getLayout()) {
                // Cut a hole in the darkening scrim on the page that should be highlighted, if any.
                getDescendantRectRelativeToSelf(currCellLayout, mHighlightRect);
                canvas.clipRect(mHighlightRect, Region.Op.DIFFERENCE);
            }
            canvas.drawColor((alpha << 24) | SCRIM_COLOR);
            canvas.restore();
        }

        mFocusIndicatorHelper.draw(canvas);
        super.dispatchDraw(canvas);
    }

    public void setBackgroundAlpha(float alpha) {
        if (alpha != mBackgroundAlpha) {
            mBackgroundAlpha = alpha;
            invalidate();
        }
    }

    public float getBackgroundAlpha() {
        return mBackgroundAlpha;
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        View topView = AbstractFloatingView.getTopOpenView(mLauncher);
        if (topView != null) {
            return topView.requestFocus(direction, previouslyFocusedRect);
        } else {
            return super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
        }
    }

    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        View topView = AbstractFloatingView.getTopOpenView(mLauncher);
        if (topView != null) {
            topView.addFocusables(views, direction);
        } else {
            super.addFocusables(views, direction, focusableMode);
        }
    }

    public void setTouchCompleteListener(TouchCompleteListener listener) {
        mTouchCompleteListener = listener;
    }

    public interface TouchCompleteListener {
        public void onTouchComplete();
    }
}
