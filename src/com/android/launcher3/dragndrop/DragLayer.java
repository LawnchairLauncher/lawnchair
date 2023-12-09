
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

import static android.animation.ObjectAnimator.ofFloat;

import static com.android.app.animation.Interpolators.DECELERATE_1_5;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_Y;
import static com.android.launcher3.Utilities.mapRange;
import static com.android.launcher3.anim.AnimatorListeners.forEndCallback;
import static com.android.launcher3.compat.AccessibilityManagerCompat.sendCustomAccessibilityEvent;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.TypeEvaluator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.Interpolator;

import com.android.app.animation.Interpolators;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DropTargetBar;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.anim.SpringProperty;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.graphics.Scrim;
import com.android.launcher3.keyboard.ViewGroupFocusHelper;
import com.android.launcher3.views.BaseDragLayer;
import com.android.systemui.plugins.shared.LauncherOverlayManager.LauncherOverlayCallbacks;

import java.util.ArrayList;

/**
 * A ViewGroup that coordinates dragging across its descendants
 */
public class DragLayer extends BaseDragLayer<Launcher> implements LauncherOverlayCallbacks {

    public static final int ALPHA_INDEX_OVERLAY = 0;
    private static final int ALPHA_CHANNEL_COUNT = 1;

    public static final int ANIMATION_END_DISAPPEAR = 0;
    public static final int ANIMATION_END_REMAIN_VISIBLE = 2;

    private final boolean mIsRtl;

    private DragController mDragController;

    // Variables relating to animation of views after drop
    private Animator mDropAnim = null;

    private DragView mDropView = null;

    private boolean mHoverPointClosesFolder = false;

    private int mTopViewIndex;
    private int mChildCountOnLastUpdate = -1;

    // Related to adjacent page hints
    private final ViewGroupFocusHelper mFocusIndicatorHelper;
    private Scrim mWorkspaceDragScrim;

    /**
     * Used to create a new DragLayer from XML.
     *
     * @param context The application's context.
     * @param attrs The attributes set containing the Workspace's customization values.
     */
    public DragLayer(Context context, AttributeSet attrs) {
        super(context, attrs, ALPHA_CHANNEL_COUNT);

        // Disable multitouch across the workspace/all apps/customize tray
        setMotionEventSplittingEnabled(false);
        setChildrenDrawingOrderEnabled(true);

        mFocusIndicatorHelper = new ViewGroupFocusHelper(this);
        mIsRtl = Utilities.isRtl(getResources());
    }

    /**
     * Set up the drag layer with the parameters.
     */
    public void setup(DragController dragController, Workspace<?> workspace) {
        mDragController = dragController;
        recreateControllers();
        mWorkspaceDragScrim = new Scrim(this);
        workspace.addOverlayCallback(this);
    }

    @Override
    public void recreateControllers() {
        mControllers = mActivity.createTouchControllers();
    }

    public ViewGroupFocusHelper getFocusIndicatorHelper() {
        return mFocusIndicatorHelper;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return mDragController.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    private boolean isEventOverAccessibleDropTargetBar(MotionEvent ev) {
        return isInAccessibleDrag() && isEventOverView(mActivity.getDropTargetBar(), ev);
    }

    @Override
    public boolean onInterceptHoverEvent(MotionEvent ev) {
        if (mActivity == null || mActivity.getWorkspace() == null) {
            return false;
        }
        AbstractFloatingView topView = AbstractFloatingView.getTopOpenView(mActivity);
        if (!(topView instanceof Folder)) {
            return false;
        } else {
            AccessibilityManager accessibilityManager = (AccessibilityManager)
                    getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (accessibilityManager.isTouchExplorationEnabled()) {
                Folder currentFolder = (Folder) topView;
                final int action = ev.getAction();
                boolean isOverFolderOrSearchBar;
                switch (action) {
                    case MotionEvent.ACTION_HOVER_ENTER:
                        isOverFolderOrSearchBar = isEventOverView(topView, ev) ||
                                isEventOverAccessibleDropTargetBar(ev);
                        if (!isOverFolderOrSearchBar) {
                            sendTapOutsideFolderAccessibilityEvent(currentFolder.isEditingName());
                            mHoverPointClosesFolder = true;
                            return true;
                        }
                        mHoverPointClosesFolder = false;
                        break;
                    case MotionEvent.ACTION_HOVER_MOVE:
                        isOverFolderOrSearchBar = isEventOverView(topView, ev) ||
                                isEventOverAccessibleDropTargetBar(ev);
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
        sendCustomAccessibilityEvent(
                this, AccessibilityEvent.TYPE_VIEW_FOCUSED, getContext().getString(stringId));
    }

    @Override
    public boolean onHoverEvent(MotionEvent ev) {
        // If we've received this, we've already done the necessary handling
        // in onInterceptHoverEvent. Return true to consume the event.
        return false;
    }


    private boolean isInAccessibleDrag() {
        return mActivity.getAccessibilityDelegate().isInAccessibleDrag();
    }

    @Override
    public boolean onRequestSendAccessibilityEvent(View child, AccessibilityEvent event) {
        if (isInAccessibleDrag() && child instanceof DropTargetBar) {
            return true;
        }
        return super.onRequestSendAccessibilityEvent(child, event);
    }

    @Override
    public void addChildrenForAccessibility(ArrayList<View> childrenForAccessibility) {
        View topView = AbstractFloatingView.getTopOpenViewWithType(mActivity,
                AbstractFloatingView.TYPE_ACCESSIBLE);
        if (topView != null) {
            addAccessibleChildToList(topView, childrenForAccessibility);
            if (isInAccessibleDrag()) {
                addAccessibleChildToList(mActivity.getDropTargetBar(), childrenForAccessibility);
            }
        } else {
            super.addChildrenForAccessibility(childrenForAccessibility);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        ev.offsetLocation(getTranslationX(), 0);
        try {
            return super.dispatchTouchEvent(ev);
        } finally {
            ev.offsetLocation(-getTranslationX(), 0);
        }
    }

    public void animateViewIntoPosition(DragView dragView, final int[] pos, float alpha,
            float scaleX, float scaleY, int animationEndStyle, Runnable onFinishRunnable,
            int duration) {
        animateViewIntoPosition(dragView, pos[0], pos[1], alpha, scaleX, scaleY,
                onFinishRunnable, animationEndStyle, duration, null);
    }

    public void animateViewIntoPosition(DragView dragView, final View child, View anchorView) {
        animateViewIntoPosition(dragView, child, -1, anchorView);
    }

    public void animateViewIntoPosition(DragView dragView, final View child, int duration,
            View anchorView) {

        ShortcutAndWidgetContainer parentChildren = (ShortcutAndWidgetContainer) child.getParent();
        CellLayoutLayoutParams lp =  (CellLayoutLayoutParams) child.getLayoutParams();
        parentChildren.measureChild(child);
        parentChildren.layoutChild(child);

        float coord[] = new float[2];
        float childScale = child.getScaleX();

        coord[0] = lp.x + (child.getMeasuredWidth() * (1 - childScale) / 2);
        coord[1] = lp.y + (child.getMeasuredHeight() * (1 - childScale) / 2);

        // Since the child hasn't necessarily been laid out, we force the lp to be updated with
        // the correct coordinates (above) and use these to determine the final location
        float scale = getDescendantCoordRelativeToSelf((View) child.getParent(), coord);

        // We need to account for the scale of the child itself, as the above only accounts for
        // for the scale in parents.
        scale *= childScale;
        int toX = Math.round(coord[0]);
        int toY = Math.round(coord[1]);

        float toScale = scale;

        if (child instanceof DraggableView) {
            // This code is fairly subtle. Please verify drag and drop is pixel-perfect in a number
            // of scenarios before modifying (from all apps, from workspace, different grid-sizes,
            // shortcuts from in and out of Launcher etc).
            DraggableView d = (DraggableView) child;
            Rect destRect = new Rect();
            d.getWorkspaceVisualDragBounds(destRect);

            // In most cases this additional scale factor should be a no-op (1). It mainly accounts
            // for alternate grids where the source and destination icon sizes are different
            toScale *= ((1f * destRect.width())
                    / (dragView.getMeasuredWidth() - dragView.getBlurSizeOutline()));

            // This accounts for the offset of the DragView created by scaling it about its
            // center as it animates into place.
            float scaleShiftX = dragView.getMeasuredWidth() * (1 - toScale) / 2;
            float scaleShiftY = dragView.getMeasuredHeight() * (1 - toScale) / 2;

            toX += scale * destRect.left - toScale * dragView.getBlurSizeOutline() / 2 - scaleShiftX;
            toY += scale * destRect.top - toScale * dragView.getBlurSizeOutline() / 2 - scaleShiftY;
        }

        child.setVisibility(INVISIBLE);
        Runnable onCompleteRunnable = () -> child.setVisibility(VISIBLE);
        animateViewIntoPosition(dragView, toX, toY, 1, toScale, toScale,
                onCompleteRunnable, ANIMATION_END_DISAPPEAR, duration, anchorView);
    }

    /**
     * This method animates a view at the end of a drag and drop animation.
     */
    public void animateViewIntoPosition(final DragView view,
            final int toX, final int toY, float finalAlpha,
            float finalScaleX, float finalScaleY, Runnable onCompleteRunnable,
            int animationEndStyle, int duration, View anchorView) {
        Rect to = new Rect(toX, toY, toX + view.getMeasuredWidth(), toY + view.getMeasuredHeight());
        animateView(view, to, finalAlpha, finalScaleX, finalScaleY, duration,
                null, onCompleteRunnable, animationEndStyle, anchorView);
    }

    /**
     * This method animates a view at the end of a drag and drop animation.
     * @param view The view to be animated. This view is drawn directly into DragLayer, and so
     *        doesn't need to be a child of DragLayer.
     * @param to The final location of the view. Only the left and top parameters are used. This
*        location doesn't account for scaling, and so should be centered about the desired
*        final location (including scaling).
     * @param finalAlpha The final alpha of the view, in case we want it to fade as it animates.
     * @param finalScaleX The final scale of the view. The view is scaled about its center.
     * @param finalScaleY The final scale of the view. The view is scaled about its center.
     * @param duration The duration of the animation.
     * @param motionInterpolator The interpolator to use for the location of the view.
     * @param onCompleteRunnable Optional runnable to run on animation completion.
     * @param animationEndStyle Whether or not to fade out the view once the animation completes.
*        {@link #ANIMATION_END_DISAPPEAR} or {@link #ANIMATION_END_REMAIN_VISIBLE}.
     * @param anchorView If not null, this represents the view which the animated view stays
     */
    public void animateView(final DragView view, final Rect to,
            final float finalAlpha, final float finalScaleX, final float finalScaleY, int duration,
            final Interpolator motionInterpolator, final Runnable onCompleteRunnable,
            final int animationEndStyle, View anchorView) {
        view.cancelAnimation();
        view.requestLayout();

        final int[] from = getViewLocationRelativeToSelf(view);

        // Calculate the duration of the animation based on the object's distance
        final float dist = (float) Math.hypot(to.left - from[0], to.top - from[1]);
        final Resources res = getResources();
        final float maxDist = (float) res.getInteger(R.integer.config_dropAnimMaxDist);

        // If duration < 0, this is a cue to compute the duration based on the distance
        if (duration < 0) {
            duration = res.getInteger(R.integer.config_dropAnimMaxDuration);
            if (dist < maxDist) {
                duration *= DECELERATE_1_5.getInterpolation(dist / maxDist);
            }
            duration = Math.max(duration, res.getInteger(R.integer.config_dropAnimMinDuration));
        }

        // Fall back to cubic ease out interpolator for the animation if none is specified
        TimeInterpolator interpolator =
                motionInterpolator == null ? DECELERATE_1_5 : motionInterpolator;

        // Animate the view
        PendingAnimation anim = new PendingAnimation(duration);
        anim.add(ofFloat(view, View.SCALE_X, finalScaleX), interpolator, SpringProperty.DEFAULT);
        anim.add(ofFloat(view, View.SCALE_Y, finalScaleY), interpolator, SpringProperty.DEFAULT);
        anim.setViewAlpha(view, finalAlpha, interpolator);
        anim.setFloat(view, VIEW_TRANSLATE_Y, to.top, interpolator);

        ObjectAnimator xMotion = ofFloat(view, VIEW_TRANSLATE_X, to.left);
        if (anchorView != null) {
            final int startScroll = anchorView.getScrollX();
            TypeEvaluator<Float> evaluator = (f, s, e) -> mapRange(f, s, e)
                    + (anchorView.getScaleX() * (startScroll - anchorView.getScrollX()));
            xMotion.setEvaluator(evaluator);
        }
        anim.add(xMotion, interpolator, SpringProperty.DEFAULT);
        if (onCompleteRunnable != null) {
            anim.addListener(forEndCallback(onCompleteRunnable));
        }
        playDropAnimation(view, anim.buildAnim(), animationEndStyle);
    }

    /**
     * Runs a previously constructed drop animation
     */
    public void playDropAnimation(final DragView view, Animator animator, int animationEndStyle) {
        // Clean up the previous animations
        if (mDropAnim != null) mDropAnim.cancel();

        // Show the drop view if it was previously hidden
        mDropView = view;
        // Create and start the animation
        mDropAnim = animator;
        mDropAnim.addListener(forEndCallback(() -> mDropAnim = null));
        if (animationEndStyle == ANIMATION_END_DISAPPEAR) {
            mDropAnim.addListener(forEndCallback(this::clearAnimatedView));
        }
        mDropAnim.start();
    }

    public void clearAnimatedView() {
        if (mDropAnim != null) {
            mDropAnim.cancel();
        }
        mDropAnim = null;
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
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        updateChildIndices();
        mActivity.onDragLayerHierarchyChanged();
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        updateChildIndices();
        mActivity.onDragLayerHierarchyChanged();
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

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // Draw the background below children.
        mWorkspaceDragScrim.draw(canvas);
        mFocusIndicatorHelper.draw(canvas);
        super.dispatchDraw(canvas);
    }

    public Scrim getWorkspaceDragScrim() {
        return mWorkspaceDragScrim;
    }

    @Override
    public void onOverlayScrollChanged(float progress) {
        float alpha = 1 - Interpolators.DECELERATE_3.getInterpolation(progress);
        float transX = getMeasuredWidth() * progress;

        if (mIsRtl) {
            transX = -transX;
        }
        setTranslationX(transX);
        getAlphaProperty(ALPHA_INDEX_OVERLAY).setValue(alpha);
    }
}
