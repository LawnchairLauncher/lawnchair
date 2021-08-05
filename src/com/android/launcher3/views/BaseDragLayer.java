/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.launcher3.views;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_UP;

import static com.android.launcher3.util.DefaultDisplay.getSingleFrameMs;

import android.annotation.TargetApi;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Property;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.testing.TestProtocol;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.MultiValueAlpha.AlphaProperty;
import com.android.launcher3.util.SimpleBroadcastReceiver;
import com.android.launcher3.util.TouchController;

import java.io.PrintWriter;
import java.util.ArrayList;

import app.lawnchair.preferences.PreferenceManager;

/**
 * A viewgroup with utility methods for drag-n-drop and touch interception
 */
public abstract class BaseDragLayer<T extends Context & ActivityContext>
        extends InsettableFrameLayout {

    public static final Property<LayoutParams, Integer> LAYOUT_X =
            new Property<LayoutParams, Integer>(Integer.TYPE, "x") {
                @Override
                public Integer get(LayoutParams lp) {
                    return lp.x;
                }

                @Override
                public void set(LayoutParams lp, Integer x) {
                    lp.x = x;
                }
            };

    public static final Property<LayoutParams, Integer> LAYOUT_Y =
            new Property<LayoutParams, Integer>(Integer.TYPE, "y") {
                @Override
                public Integer get(LayoutParams lp) {
                    return lp.y;
                }

                @Override
                public void set(LayoutParams lp, Integer y) {
                    lp.y = y;
                }
            };

    // Touch coming from normal view system is being dispatched.
    private static final int TOUCH_DISPATCHING_FROM_VIEW = 1 << 0;
    // Touch is being dispatched through the normal view dispatch system, and started at the
    // system gesture region. In this case we prevent internal gesture handling and only allow
    // normal view event handling.
    private static final int TOUCH_DISPATCHING_FROM_VIEW_GESTURE_REGION = 1 << 1;
    // Touch coming from InputMonitor proxy is being dispatched 'only to gestures'. Note that both
    // this and view-system can be active at the same time where view-system would go to the views,
    // and this would go to the gestures.
    // Note that this is not set when events are coming from proxy, but going through full dispatch
    // process (both views and gestures) to allow view-system to easily take over in case it
    // comes later.
    private static final int TOUCH_DISPATCHING_FROM_PROXY = 1 << 2;
    // ACTION_DOWN has been dispatched to child views and ACTION_UP or ACTION_CANCEL is pending.
    // Note that the event source can either be view-dispatching or proxy-dispatching based on if
    // TOUCH_DISPATCHING_VIEW is present or not.
    private static final int TOUCH_DISPATCHING_TO_VIEW_IN_PROGRESS = 1 << 3;

    protected final float[] mTmpXY = new float[2];
    protected final float[] mTmpRectPoints = new float[4];
    protected final Rect mHitRect = new Rect();

    @ViewDebug.ExportedProperty(category = "launcher")
    private final RectF mSystemGestureRegion = new RectF();
    private int mTouchDispatchState = 0;

    protected final T mActivity;
    private final MultiValueAlpha mMultiValueAlpha;
    private final WallpaperManager mWallpaperManager;
    private final SimpleBroadcastReceiver mWallpaperChangeReceiver =
            new SimpleBroadcastReceiver(this::onWallpaperChanged);
    private final String[] mWallpapersWithoutSysuiScrims;

    // All the touch controllers for the view
    protected TouchController[] mControllers;
    // Touch controller which is currently active for the normal view dispatch
    protected TouchController mActiveController;
    // Touch controller which is being used for the proxy events
    protected TouchController mProxyTouchController;

    private TouchCompleteListener mTouchCompleteListener;

    protected boolean mAllowSysuiScrims = true;

    public BaseDragLayer(Context context, AttributeSet attrs, int alphaChannelCount) {
        super(context, attrs);
        mActivity = (T) ActivityContext.lookupContext(context);
        mMultiValueAlpha = new MultiValueAlpha(this, alphaChannelCount);
        mWallpaperManager = context.getSystemService(WallpaperManager.class);
        mWallpapersWithoutSysuiScrims = getResources().getStringArray(
                R.array.live_wallpapers_remove_sysui_scrims);

        PreferenceManager prefs = PreferenceManager.getInstance(context);
        prefs.getShowSysUiScrim().subscribeChanges(this, () -> onWallpaperChanged(null));
    }

    /**
     * Called to reinitialize touch controllers.
     */
    public abstract void recreateControllers();

    /**
     * Same as {@link #isEventOverView(View, MotionEvent, View)} where evView == this drag layer.
     */
    public boolean isEventOverView(View view, MotionEvent ev) {
        getDescendantRectRelativeToSelf(view, mHitRect);
        return mHitRect.contains((int) ev.getX(), (int) ev.getY());
    }

    /**
     * Given a motion event in evView's coordinates, return whether the event is within another
     * view's bounds.
     */
    public boolean isEventOverView(View view, MotionEvent ev, View evView) {
        int[] xy = new int[] {(int) ev.getX(), (int) ev.getY()};
        getDescendantCoordRelativeToSelf(evView, xy);
        getDescendantRectRelativeToSelf(view, mHitRect);
        return mHitRect.contains(xy[0], xy[1]);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getAction();

        if (action == ACTION_UP || action == ACTION_CANCEL) {
            if (mTouchCompleteListener != null) {
                mTouchCompleteListener.onTouchComplete();
            }
            mTouchCompleteListener = null;
        } else if (action == MotionEvent.ACTION_DOWN) {
            mActivity.finishAutoCancelActionMode();
        }
        return findActiveController(ev);
    }

    private boolean isEventInLauncher(MotionEvent ev) {
        final float x = ev.getX();
        final float y = ev.getY();

        return x >= mSystemGestureRegion.left && x < getWidth() - mSystemGestureRegion.right
                && y >= mSystemGestureRegion.top && y < getHeight() - mSystemGestureRegion.bottom;
    }

    private TouchController findControllerToHandleTouch(MotionEvent ev) {
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.PAUSE_NOT_DETECTED, "findControllerToHandleTouch ev=" + ev
                    + ", isEventInLauncher=" + isEventInLauncher(ev)
                    + ", topOpenView=" + AbstractFloatingView.getTopOpenView(mActivity));
        }

        AbstractFloatingView topView = AbstractFloatingView.getTopOpenView(mActivity);
        if (topView != null
                && (isEventInLauncher(ev) || topView.canInterceptEventsInSystemGestureRegion())
                && topView.onControllerInterceptTouchEvent(ev)) {
            return topView;
        }

        for (TouchController controller : mControllers) {
            if (controller.onControllerInterceptTouchEvent(ev)) {
                return controller;
            }
        }
        return null;
    }

    protected boolean findActiveController(MotionEvent ev) {
        mActiveController = null;
        if ((mTouchDispatchState & (TOUCH_DISPATCHING_FROM_VIEW_GESTURE_REGION
                | TOUCH_DISPATCHING_FROM_PROXY)) == 0) {
            // Only look for controllers if we are not dispatching from gesture area and proxy is
            // not active
            mActiveController = findControllerToHandleTouch(ev);
        }
        return mActiveController != null;
    }

    @Override
    public boolean onRequestSendAccessibilityEvent(View child, AccessibilityEvent event) {
        // Shortcuts can appear above folder
        View topView = AbstractFloatingView.getTopOpenViewWithType(mActivity,
                AbstractFloatingView.TYPE_ACCESSIBLE);
        if (topView != null) {
            if (child == topView) {
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
        View topView = AbstractFloatingView.getTopOpenViewWithType(mActivity,
                AbstractFloatingView.TYPE_ACCESSIBLE);
        if (topView != null) {
            // Only add the top view as a child for accessibility when it is open
            addAccessibleChildToList(topView, childrenForAccessibility);
        } else {
            super.addChildrenForAccessibility(childrenForAccessibility);
        }
    }

    protected void addAccessibleChildToList(View child, ArrayList<View> outList) {
        if (child.isImportantForAccessibility()) {
            outList.add(child);
        } else {
            child.addChildrenForAccessibility(outList);
        }
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        if (child instanceof AbstractFloatingView) {
            // Handles the case where the view is removed without being properly closed.
            // This can happen if something goes wrong during a state change/transition.
            AbstractFloatingView floatingView = (AbstractFloatingView) child;
            if (floatingView.isOpen()) {
                postDelayed(() -> floatingView.close(false), getSingleFrameMs(getContext()));
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getAction();
        if (action == ACTION_UP || action == ACTION_CANCEL) {
            if (mTouchCompleteListener != null) {
                mTouchCompleteListener.onTouchComplete();
            }
            mTouchCompleteListener = null;
        }

        if (mActiveController != null) {
            return mActiveController.onControllerTouchEvent(ev);
        } else {
            // In case no child view handled the touch event, we may not get onIntercept anymore
            return findActiveController(ev);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case ACTION_DOWN: {
                if ((mTouchDispatchState & TOUCH_DISPATCHING_TO_VIEW_IN_PROGRESS) != 0) {
                    // Cancel the previous touch
                    int action = ev.getAction();
                    ev.setAction(ACTION_CANCEL);
                    super.dispatchTouchEvent(ev);
                    ev.setAction(action);
                }
                mTouchDispatchState |= TOUCH_DISPATCHING_FROM_VIEW
                        | TOUCH_DISPATCHING_TO_VIEW_IN_PROGRESS;

                if (isEventInLauncher(ev)) {
                    mTouchDispatchState &= ~TOUCH_DISPATCHING_FROM_VIEW_GESTURE_REGION;
                } else {
                    mTouchDispatchState |= TOUCH_DISPATCHING_FROM_VIEW_GESTURE_REGION;
                }
                break;
            }
            case ACTION_CANCEL:
            case ACTION_UP:
                mTouchDispatchState &= ~TOUCH_DISPATCHING_FROM_VIEW_GESTURE_REGION;
                mTouchDispatchState &= ~TOUCH_DISPATCHING_FROM_VIEW;
                mTouchDispatchState &= ~TOUCH_DISPATCHING_TO_VIEW_IN_PROGRESS;
                break;
        }
        super.dispatchTouchEvent(ev);

        // We want to get all events so that mTouchDispatchSource is maintained properly
        return true;
    }

    /**
     * Proxies the touch events to the gesture handlers
     */
    public boolean proxyTouchEvent(MotionEvent ev, boolean allowViewDispatch) {
        int actionMasked = ev.getActionMasked();
        boolean isViewDispatching = (mTouchDispatchState & TOUCH_DISPATCHING_FROM_VIEW) != 0;

        // Only do view dispatch if another view-dispatching is not running, or we already started
        // proxy-dispatching before. Note that view-dispatching can always take over the proxy
        // dispatching at anytime, but not vice-versa.
        allowViewDispatch = allowViewDispatch && !isViewDispatching
                && (actionMasked == ACTION_DOWN
                    || ((mTouchDispatchState & TOUCH_DISPATCHING_TO_VIEW_IN_PROGRESS) != 0));

        if (allowViewDispatch) {
            mTouchDispatchState |= TOUCH_DISPATCHING_TO_VIEW_IN_PROGRESS;
            super.dispatchTouchEvent(ev);

            if (actionMasked == ACTION_UP || actionMasked == ACTION_CANCEL) {
                mTouchDispatchState &= ~TOUCH_DISPATCHING_TO_VIEW_IN_PROGRESS;
                mTouchDispatchState &= ~TOUCH_DISPATCHING_FROM_PROXY;
            }
            return true;
        } else {
            boolean handled;
            if (mProxyTouchController != null) {
                handled = mProxyTouchController.onControllerTouchEvent(ev);
            } else {
                if (actionMasked == ACTION_DOWN) {
                    if (isViewDispatching && mActiveController != null) {
                        // A controller is already active, we can't initiate our own controller
                        mTouchDispatchState &= ~TOUCH_DISPATCHING_FROM_PROXY;
                    } else {
                        // We will control the handler via proxy
                        mTouchDispatchState |= TOUCH_DISPATCHING_FROM_PROXY;
                    }
                }
                if ((mTouchDispatchState & TOUCH_DISPATCHING_FROM_PROXY) != 0) {
                    mProxyTouchController = findControllerToHandleTouch(ev);
                }
                handled = mProxyTouchController != null;
            }
            if (actionMasked == ACTION_UP || actionMasked == ACTION_CANCEL) {
                mProxyTouchController = null;
                mTouchDispatchState &= ~TOUCH_DISPATCHING_FROM_PROXY;
            }
            return handled;
        }
    }

    /**
     * Determine the rect of the descendant in this DragLayer's coordinates
     *
     * @param descendant The descendant whose coordinates we want to find.
     * @param r The rect into which to place the results.
     * @return The factor by which this descendant is scaled relative to this DragLayer.
     */
    public float getDescendantRectRelativeToSelf(View descendant, Rect r) {
        mTmpRectPoints[0] = 0;
        mTmpRectPoints[1] = 0;
        mTmpRectPoints[2] = descendant.getWidth();
        mTmpRectPoints[3] = descendant.getHeight();
        float s = getDescendantCoordRelativeToSelf(descendant, mTmpRectPoints);
        r.left = Math.round(Math.min(mTmpRectPoints[0], mTmpRectPoints[2]));
        r.top = Math.round(Math.min(mTmpRectPoints[1], mTmpRectPoints[3]));
        r.right = Math.round(Math.max(mTmpRectPoints[0], mTmpRectPoints[2]));
        r.bottom = Math.round(Math.max(mTmpRectPoints[1], mTmpRectPoints[3]));
        return s;
    }

    public float getLocationInDragLayer(View child, int[] loc) {
        loc[0] = 0;
        loc[1] = 0;
        return getDescendantCoordRelativeToSelf(child, loc);
    }

    public float getDescendantCoordRelativeToSelf(View descendant, int[] coord) {
        mTmpXY[0] = coord[0];
        mTmpXY[1] = coord[1];
        float scale = getDescendantCoordRelativeToSelf(descendant, mTmpXY);
        Utilities.roundArray(mTmpXY, coord);
        return scale;
    }

    public float getDescendantCoordRelativeToSelf(View descendant, float[] coord) {
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
    public float getDescendantCoordRelativeToSelf(View descendant, float[] coord,
            boolean includeRootScroll) {
        return Utilities.getDescendantCoordRelativeToAncestor(descendant, this,
                coord, includeRootScroll);
    }

    /**
     * Inverse of {@link #getDescendantCoordRelativeToSelf(View, float[])}.
     */
    public void mapCoordInSelfToDescendant(View descendant, float[] coord) {
        Utilities.mapCoordInSelfToDescendant(descendant, this, coord);
    }

    /**
     * Inverse of {@link #getDescendantCoordRelativeToSelf(View, int[])}.
     */
    public void mapCoordInSelfToDescendant(View descendant, int[] coord) {
        mTmpXY[0] = coord[0];
        mTmpXY[1] = coord[1];
        Utilities.mapCoordInSelfToDescendant(descendant, this, mTmpXY);
        Utilities.roundArray(mTmpXY, coord);
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
        return AbstractFloatingView.getTopOpenView(mActivity) != null;
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        View topView = AbstractFloatingView.getTopOpenView(mActivity);
        if (topView != null) {
            return topView.requestFocus(direction, previouslyFocusedRect);
        } else {
            return super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
        }
    }

    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        View topView = AbstractFloatingView.getTopOpenView(mActivity);
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
        void onTouchComplete();
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

    public AlphaProperty getAlphaProperty(int index) {
        return mMultiValueAlpha.getProperty(index);
    }

    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "DragLayer:");
        if (mActiveController != null) {
            writer.println(prefix + "\tactiveController: " + mActiveController);
            mActiveController.dump(prefix + "\t", writer);
        }
        writer.println(prefix + "\tdragLayerAlpha : " + mMultiValueAlpha );
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

    @Override
    @TargetApi(Build.VERSION_CODES.Q)
    public WindowInsets dispatchApplyWindowInsets(WindowInsets insets) {
        if (Utilities.ATLEAST_Q) {
            Insets gestureInsets = insets.getMandatorySystemGestureInsets();
            mSystemGestureRegion.set(gestureInsets.left, gestureInsets.top,
                    gestureInsets.right, gestureInsets.bottom);
        }
        return super.dispatchApplyWindowInsets(insets);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mWallpaperChangeReceiver.register(mActivity, Intent.ACTION_WALLPAPER_CHANGED);
        onWallpaperChanged(null);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mActivity.unregisterReceiver(mWallpaperChangeReceiver);
    }

    private void onWallpaperChanged(Intent unusedBroadcastIntent) {
        WallpaperInfo newWallpaperInfo = mWallpaperManager.getWallpaperInfo();
        boolean oldAllowSysuiScrims = mAllowSysuiScrims;
        mAllowSysuiScrims = computeAllowSysuiScrims(newWallpaperInfo);
        if (mAllowSysuiScrims != oldAllowSysuiScrims) {
            // Reapply insets so scrim can be removed or re-added if necessary.
            setInsets(mInsets);
        }
    }

    /**
     * Determines whether we can scrim the status bar and nav bar for the given wallpaper by
     * checking against a list of live wallpapers that we don't show the scrims on.
     */
    private boolean computeAllowSysuiScrims(@Nullable WallpaperInfo newWallpaperInfo) {
        PreferenceManager prefs = PreferenceManager.getInstance(getContext());
        if (!prefs.getShowSysUiScrim().get()) {
            return false;
        }
        if (newWallpaperInfo == null) {
            // Static wallpapers need scrim unless determined otherwise by wallpaperColors.
            return true;
        }
        ComponentName newWallpaper = newWallpaperInfo.getComponent();
        for (String wallpaperWithoutScrim : mWallpapersWithoutSysuiScrims) {
            if (newWallpaper.equals(ComponentName.unflattenFromString(wallpaperWithoutScrim))) {
                // New wallpaper does not need a scrim.
                return false;
            }
        }
        return true;
    }
}
