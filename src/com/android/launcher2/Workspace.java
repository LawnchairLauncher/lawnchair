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

package com.android.launcher2;

import com.android.launcher.R;
import com.android.launcher2.CellLayout.CellInfo;

import android.animation.Animatable;
import android.animation.PropertyAnimator;
import android.animation.Sequencer;
import android.animation.Animatable.AnimatableListener;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.Interpolator;
import android.widget.Scroller;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * The workspace is a wide area with a wallpaper and a finite number of screens.
 * Each screen contains a number of icons, folders or widgets the user can
 * interact with. A workspace is meant to be used with a fixed width only.
 */
public class Workspace extends ViewGroup
        implements DropTarget, DragSource, DragScroller, View.OnTouchListener {
    @SuppressWarnings({"UnusedDeclaration"})
    private static final String TAG = "Launcher.Workspace";
    private static final int INVALID_SCREEN = -1;
    // This is how much the workspace shrinks when we enter all apps or
    // customization mode
    private static final float SHRINK_FACTOR = 0.16f;
    private static final int SHRINK_TO_TOP = 0;
    private static final int SHRINK_TO_MIDDLE = 1;
    private static final int SHRINK_TO_BOTTOM = 2;

    /**
     * The velocity at which a fling gesture will cause us to snap to the next
     * screen
     */
    private static final int SNAP_VELOCITY = 600;

    private final WallpaperManager mWallpaperManager;

    private int mDefaultScreen;

    private boolean mFirstLayout = true;
    private boolean mWaitingToShrinkToBottom = false;

    private int mCurrentScreen;
    private int mNextScreen = INVALID_SCREEN;
    private Scroller mScroller;
    private VelocityTracker mVelocityTracker;

    /**
     * CellInfo for the cell that is currently being dragged
     */
    private CellLayout.CellInfo mDragInfo;

    /**
     * Target drop area calculated during last acceptDrop call.
     */
    private int[] mTargetCell = null;

    /**
     * The CellLayout that is currently being dragged over
     */
    private CellLayout mDragTargetLayout = null;

    private float mLastMotionX;
    private float mLastMotionY;

    private final static int TOUCH_STATE_REST = 0;
    private final static int TOUCH_STATE_SCROLLING = 1;

    private int mTouchState = TOUCH_STATE_REST;

    private OnLongClickListener mLongClickListener;

    private Launcher mLauncher;
    private IconCache mIconCache;
    private DragController mDragController;


    private int[] mTempCell = new int[2];
    private int[] mTempEstimate = new int[2];
    private float[] mTempDragCoordinates = new float[2];
    private float[] mTempDragBottomRightCoordinates = new float[2];

    private boolean mAllowLongPress = true;

    private int mTouchSlop;
    private int mMaximumVelocity;

    private static final int INVALID_POINTER = -1;
    private static final int DEFAULT_CELL_COUNT_X = 4;
    private static final int DEFAULT_CELL_COUNT_Y = 4;

    private int mActivePointerId = INVALID_POINTER;

    private Drawable mPreviousIndicator;
    private Drawable mNextIndicator;

    private static final float NANOTIME_DIV = 1000000000.0f;
    private static final float SMOOTHING_SPEED = 0.75f;
    private static final float SMOOTHING_CONSTANT = (float) (0.016 / Math.log(SMOOTHING_SPEED));
    private float mSmoothingTime;
    private float mTouchX;

    private WorkspaceOvershootInterpolator mScrollInterpolator;

    private static final float BASELINE_FLING_VELOCITY = 2500.f;
    private static final float FLING_VELOCITY_INFLUENCE = 0.4f;

    private Paint mDropIndicatorPaint;

    // State variable that indicated whether the screens are small (ie when you're
    // in all apps or customize mode)
    private boolean mIsSmall;
    private AnimatableListener mUnshrinkAnimationListener;

    private static class WorkspaceOvershootInterpolator implements Interpolator {
        private static final float DEFAULT_TENSION = 1.3f;
        private float mTension;

        public WorkspaceOvershootInterpolator() {
            mTension = DEFAULT_TENSION;
        }

        public void setDistance(int distance) {
            mTension = distance > 0 ? DEFAULT_TENSION / distance : DEFAULT_TENSION;
        }

        public void disableSettle() {
            mTension = 0.f;
        }

        public float getInterpolation(float t) {
            // _o(t) = t * t * ((tension + 1) * t + tension)
            // o(t) = _o(t - 1) + 1
            t -= 1.0f;
            return t * t * ((mTension + 1) * t + mTension) + 1.0f;
        }
    }

    /**
     * Used to inflate the Workspace from XML.
     *
     * @param context The application's context.
     * @param attrs The attribtues set containing the Workspace's customization values.
     */
    public Workspace(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Used to inflate the Workspace from XML.
     *
     * @param context The application's context.
     * @param attrs The attribtues set containing the Workspace's customization values.
     * @param defStyle Unused.
     */
    public Workspace(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mWallpaperManager = WallpaperManager.getInstance(context);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.Workspace, defStyle, 0);
        int cellCountX = a.getInt(R.styleable.Workspace_cellCountX, DEFAULT_CELL_COUNT_X);
        int cellCountY = a.getInt(R.styleable.Workspace_cellCountY, DEFAULT_CELL_COUNT_Y);
        mDefaultScreen = a.getInt(R.styleable.Workspace_defaultScreen, 1);
        a.recycle();

        LauncherModel.updateWorkspaceLayoutCells(cellCountX, cellCountY);
        setHapticFeedbackEnabled(false);
        initWorkspace();
    }

    /**
     * Initializes various states for this workspace.
     */
    private void initWorkspace() {
        Context context = getContext();
        mScrollInterpolator = new WorkspaceOvershootInterpolator();
        mScroller = new Scroller(context, mScrollInterpolator);
        mCurrentScreen = mDefaultScreen;
        Launcher.setScreen(mCurrentScreen);
        LauncherApplication app = (LauncherApplication)context.getApplicationContext();
        mIconCache = app.getIconCache();

        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mTouchSlop = configuration.getScaledTouchSlop();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
        mUnshrinkAnimationListener = new AnimatableListener() {
            public void onAnimationStart(Animatable animation) {}
            public void onAnimationEnd(Animatable animation) {
                mIsSmall = false;
            }
            public void onAnimationCancel(Animatable animation) {}
            public void onAnimationRepeat(Animatable animation) {}
        };
    }

    @Override
    public void addView(View child, int index, LayoutParams params) {
        if (!(child instanceof CellLayout)) {
            throw new IllegalArgumentException("A Workspace can only have CellLayout children.");
        }
        super.addView(child, index, params);
    }

    @Override
    public void addView(View child) {
        if (!(child instanceof CellLayout)) {
            throw new IllegalArgumentException("A Workspace can only have CellLayout children.");
        }
        super.addView(child);
    }

    @Override
    public void addView(View child, int index) {
        if (!(child instanceof CellLayout)) {
            throw new IllegalArgumentException("A Workspace can only have CellLayout children.");
        }
        super.addView(child, index);
    }

    @Override
    public void addView(View child, int width, int height) {
        if (!(child instanceof CellLayout)) {
            throw new IllegalArgumentException("A Workspace can only have CellLayout children.");
        }
        super.addView(child, width, height);
    }

    @Override
    public void addView(View child, LayoutParams params) {
        if (!(child instanceof CellLayout)) {
            throw new IllegalArgumentException("A Workspace can only have CellLayout children.");
        }
        super.addView(child, params);
    }

    /**
     * @return The open folder on the current screen, or null if there is none
     */
    Folder getOpenFolder() {
        CellLayout currentScreen = (CellLayout) getChildAt(mCurrentScreen);
        int count = currentScreen.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = currentScreen.getChildAt(i);
            if (child instanceof Folder) {
                Folder folder = (Folder) child;
                if (folder.getInfo().opened)
                    return folder;
            }
        }
        return null;
    }

    ArrayList<Folder> getOpenFolders() {
        final int screenCount = getChildCount();
        ArrayList<Folder> folders = new ArrayList<Folder>(screenCount);

        for (int screen = 0; screen < screenCount; screen++) {
            CellLayout currentScreen = (CellLayout) getChildAt(screen);
            int count = currentScreen.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = currentScreen.getChildAt(i);
                CellLayout.LayoutParams lp = (CellLayout.LayoutParams) child
                        .getLayoutParams();
                if (child instanceof Folder) {
                    Folder folder = (Folder) child;
                    if (folder.getInfo().opened)
                        folders.add(folder);
                    break;
                }
            }
        }

        return folders;
    }

    boolean isDefaultScreenShowing() {
        return mCurrentScreen == mDefaultScreen;
    }

    /**
     * Returns the index of the currently displayed screen.
     *
     * @return The index of the currently displayed screen.
     */
    int getCurrentScreen() {
        return mCurrentScreen;
    }

    /**
     * Sets the current screen.
     *
     * @param currentScreen
     */
    void setCurrentScreen(int currentScreen) {
        setCurrentScreen(currentScreen, true);
    }

    void setCurrentScreen(int currentScreen, boolean animateScrolling) {
        setCurrentScreen(currentScreen, animateScrolling, getWidth());
    }

    void setCurrentScreen(int currentScreen, boolean animateScrolling, int screenWidth) {
        if (!mScroller.isFinished())
            mScroller.abortAnimation();
        mCurrentScreen = Math.max(0, Math.min(currentScreen, getChildCount() - 1));
        if (mPreviousIndicator != null) {
            mPreviousIndicator.setLevel(mCurrentScreen);
            mNextIndicator.setLevel(mCurrentScreen);
        }
        if (animateScrolling) {
            scrollTo(mCurrentScreen * screenWidth, 0);
        } else {
            mScrollX = mCurrentScreen * screenWidth;
        }
        updateWallpaperOffset(screenWidth * (getChildCount() - 1));
        invalidate();
    }

    /**
     * Adds the specified child in the current screen. The position and dimension of
     * the child are defined by x, y, spanX and spanY.
     *
     * @param child The child to add in one of the workspace's screens.
     * @param x The X position of the child in the screen's grid.
     * @param y The Y position of the child in the screen's grid.
     * @param spanX The number of cells spanned horizontally by the child.
     * @param spanY The number of cells spanned vertically by the child.
     */
    void addInCurrentScreen(View child, int x, int y, int spanX, int spanY) {
        addInScreen(child, mCurrentScreen, x, y, spanX, spanY, false);
    }

    /**
     * Adds the specified child in the current screen. The position and dimension of
     * the child are defined by x, y, spanX and spanY.
     *
     * @param child The child to add in one of the workspace's screens.
     * @param x The X position of the child in the screen's grid.
     * @param y The Y position of the child in the screen's grid.
     * @param spanX The number of cells spanned horizontally by the child.
     * @param spanY The number of cells spanned vertically by the child.
     * @param insert When true, the child is inserted at the beginning of the children list.
     */
    void addInCurrentScreen(View child, int x, int y, int spanX, int spanY, boolean insert) {
        addInScreen(child, mCurrentScreen, x, y, spanX, spanY, insert);
    }

    /**
     * Adds the specified child in the specified screen. The position and dimension of
     * the child are defined by x, y, spanX and spanY.
     *
     * @param child The child to add in one of the workspace's screens.
     * @param screen The screen in which to add the child.
     * @param x The X position of the child in the screen's grid.
     * @param y The Y position of the child in the screen's grid.
     * @param spanX The number of cells spanned horizontally by the child.
     * @param spanY The number of cells spanned vertically by the child.
     */
    void addInScreen(View child, int screen, int x, int y, int spanX, int spanY) {
        addInScreen(child, screen, x, y, spanX, spanY, false);
    }

    void addInFullScreen(View child, int screen) {
        addInScreen(child, screen, 0, 0, -1, -1);
    }

    /**
     * Adds the specified child in the specified screen. The position and dimension of
     * the child are defined by x, y, spanX and spanY.
     *
     * @param child The child to add in one of the workspace's screens.
     * @param screen The screen in which to add the child.
     * @param x The X position of the child in the screen's grid.
     * @param y The Y position of the child in the screen's grid.
     * @param spanX The number of cells spanned horizontally by the child.
     * @param spanY The number of cells spanned vertically by the child.
     * @param insert When true, the child is inserted at the beginning of the children list.
     */
    void addInScreen(View child, int screen, int x, int y, int spanX, int spanY, boolean insert) {
        if (screen < 0 || screen >= getChildCount()) {
            Log.e(TAG, "The screen must be >= 0 and < " + getChildCount()
                + " (was " + screen + "); skipping child");
            return;
        }

        final CellLayout group = (CellLayout) getChildAt(screen);
        CellLayout.LayoutParams lp = (CellLayout.LayoutParams) child.getLayoutParams();
        if (lp == null) {
            lp = new CellLayout.LayoutParams(x, y, spanX, spanY);
        } else {
            lp.cellX = x;
            lp.cellY = y;
            lp.cellHSpan = spanX;
            lp.cellVSpan = spanY;
        }

        // Get the canonical child id to uniquely represent this view in this screen
        int childId = LauncherModel.getCellLayoutChildId(child.getId(), screen, x, y, spanX, spanY);
        if (!group.addViewToCellLayout(child, insert ? 0 : -1, childId, lp)) {
            // TODO: This branch occurs when the workspace is adding views
            // outside of the defined grid
            // maybe we should be deleting these items from the LauncherModel?
            Log.w(TAG, "Failed to add to item at (" + lp.cellX + "," + lp.cellY + ") to CellLayout");
        }

        if (!(child instanceof Folder)) {
            child.setHapticFeedbackEnabled(false);
            child.setOnLongClickListener(mLongClickListener);
        }
        if (child instanceof DropTarget) {
            mDragController.addDropTarget((DropTarget) child);
        }
    }

    CellLayout.CellInfo updateOccupiedCellsForCurrentScreen(boolean[] occupied) {
        CellLayout group = (CellLayout) getChildAt(mCurrentScreen);
        if (group != null) {
            return group.updateOccupiedCells(occupied, null);
        }
        return null;
    }

    public boolean onTouch(View v, MotionEvent event) {
        // this is an intercepted event being forwarded from a cell layout
        if (mIsSmall) {
            unshrink((CellLayout)v);
            mLauncher.onWorkspaceUnshrink();
            return true;
        }
        return false;
    }

    /**
     * Registers the specified listener on each screen contained in this workspace.
     *
     * @param l The listener used to respond to long clicks.
     */
    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        mLongClickListener = l;
        final int screenCount = getChildCount();
        for (int i = 0; i < screenCount; i++) {
            getChildAt(i).setOnLongClickListener(l);
        }
    }

    private void updateWallpaperOffset() {
        updateWallpaperOffset(getChildAt(getChildCount() - 1).getRight() - (mRight - mLeft));
    }

    private void updateWallpaperOffset(int scrollRange) {
        final boolean isStaticWallpaper = (mWallpaperManager != null) &&
                (mWallpaperManager.getWallpaperInfo() == null);
        if (LauncherApplication.isScreenXLarge() && !isStaticWallpaper) {
            IBinder token = getWindowToken();
            if (token != null) {
                mWallpaperManager.setWallpaperOffsetSteps(1.0f / (getChildCount() - 1), 0 );
                mWallpaperManager.setWallpaperOffsets(getWindowToken(),
                        Math.max(0.f, Math.min(mScrollX/(float)scrollRange, 1.f)), 0);
            }
        }
    }

    @Override
    public void scrollTo(int x, int y) {
        super.scrollTo(x, y);
        mTouchX = x;
        mSmoothingTime = System.nanoTime() / NANOTIME_DIV;
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            mTouchX = mScrollX = mScroller.getCurrX();
            mSmoothingTime = System.nanoTime() / NANOTIME_DIV;
            mScrollY = mScroller.getCurrY();
            updateWallpaperOffset();
            postInvalidate();
        } else if (mNextScreen != INVALID_SCREEN) {
            mCurrentScreen = Math.max(0, Math.min(mNextScreen, getChildCount() - 1));
            if (mPreviousIndicator != null) {
                mPreviousIndicator.setLevel(mCurrentScreen);
                mNextIndicator.setLevel(mCurrentScreen);
            }
            Launcher.setScreen(mCurrentScreen);
            mNextScreen = INVALID_SCREEN;
            clearChildrenCache();
        } else if (mTouchState == TOUCH_STATE_SCROLLING) {
            final float now = System.nanoTime() / NANOTIME_DIV;
            final float e = (float) Math.exp((now - mSmoothingTime) / SMOOTHING_CONSTANT);
            final float dx = mTouchX - mScrollX;
            mScrollX += dx * e;
            mSmoothingTime = now;

            // Keep generating points as long as we're more than 1px away from the target
            if (dx > 1.f || dx < -1.f) {
                updateWallpaperOffset();
                postInvalidate();
            }
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        boolean restore = false;
        int restoreCount = 0;

        // ViewGroup.dispatchDraw() supports many features we don't need:
        // clip to padding, layout animation, animation listener, disappearing
        // children, etc. The following implementation attempts to fast-track
        // the drawing dispatch by drawing only what we know needs to be drawn.

        boolean fastDraw = mTouchState != TOUCH_STATE_SCROLLING && mNextScreen == INVALID_SCREEN;

        // if the screens are all small, we need to draw all the screens since
        // they're most likely all visible
        if (mIsSmall) {
            final int screenCount = getChildCount();
            for (int i = 0; i < screenCount; i++) {
                CellLayout cl = (CellLayout)getChildAt(i);
                drawChild(canvas, cl, getDrawingTime());
            }
        } else if (fastDraw) {
            // If we are not scrolling or flinging, draw only the current screen
            drawChild(canvas, getChildAt(mCurrentScreen), getDrawingTime());
        } else {
            final long drawingTime = getDrawingTime();
            final float scrollPos = (float) mScrollX / getWidth();
            final int leftScreen = (int) scrollPos;
            final int rightScreen = leftScreen + 1;
            if (leftScreen >= 0) {
                drawChild(canvas, getChildAt(leftScreen), drawingTime);
            }
            if (scrollPos != leftScreen && rightScreen < getChildCount()) {
                drawChild(canvas, getChildAt(rightScreen), drawingTime);
            }
        }

        if (restore) {
            canvas.restoreToCount(restoreCount);
        }
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        computeScroll();
        mDragController.setWindowToken(getWindowToken());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        if (widthMode != MeasureSpec.EXACTLY) {
            throw new IllegalStateException("Workspace can only be used in EXACTLY mode.");
        }

        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        if (heightMode != MeasureSpec.EXACTLY) {
            throw new IllegalStateException("Workspace can only be used in EXACTLY mode.");
        }

        // The children are given the same width and height as the workspace
        final int screenCount = getChildCount();
        for (int i = 0; i < screenCount; i++) {
            getChildAt(i).measure(widthMeasureSpec, heightMeasureSpec);
        }

        if (mFirstLayout) {
            setHorizontalScrollBarEnabled(false);
            setCurrentScreen(mCurrentScreen, false, width);
            setHorizontalScrollBarEnabled(true);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (mFirstLayout) {
            mFirstLayout = false;
        }
        int childLeft = 0;
        final int screenCount = getChildCount();
        for (int i = 0; i < screenCount; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != View.GONE) {
                final int childWidth = child.getMeasuredWidth();
                child.layout(childLeft, 0,
                        childLeft + childWidth, child.getMeasuredHeight());
                childLeft += childWidth;
            }
        }

        // if shrinkToBottom() is called on initialization, it has to be deferred
        // until after the first call to onLayout so that it has the correct width
        if (mWaitingToShrinkToBottom) {
            shrinkToBottom(false);
            mWaitingToShrinkToBottom = false;
        }

        if (LauncherApplication.isInPlaceRotationEnabled()) {
            // When the device is rotated, the scroll position of the current screen
            // needs to be refreshed
            setCurrentScreen(getCurrentScreen());
        }
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        int screen = indexOfChild(child);
        if (screen != mCurrentScreen || !mScroller.isFinished()) {
            if (!mLauncher.isWorkspaceLocked()) {
                snapToScreen(screen);
            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        if (!mLauncher.isAllAppsVisible()) {
            final Folder openFolder = getOpenFolder();
            if (openFolder != null) {
                return openFolder.requestFocus(direction, previouslyFocusedRect);
            } else {
                int focusableScreen;
                if (mNextScreen != INVALID_SCREEN) {
                    focusableScreen = mNextScreen;
                } else {
                    focusableScreen = mCurrentScreen;
                }
                getChildAt(focusableScreen).requestFocus(direction, previouslyFocusedRect);
            }
        }
        return false;
    }

    @Override
    public boolean dispatchUnhandledMove(View focused, int direction) {
        if (direction == View.FOCUS_LEFT) {
            if (getCurrentScreen() > 0) {
                snapToScreen(getCurrentScreen() - 1);
                return true;
            }
        } else if (direction == View.FOCUS_RIGHT) {
            if (getCurrentScreen() < getChildCount() - 1) {
                snapToScreen(getCurrentScreen() + 1);
                return true;
            }
        }
        return super.dispatchUnhandledMove(focused, direction);
    }

    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        if (!mLauncher.isAllAppsVisible()) {
            final Folder openFolder = getOpenFolder();
            if (openFolder == null) {
                getChildAt(mCurrentScreen).addFocusables(views, direction);
                if (direction == View.FOCUS_LEFT) {
                    if (mCurrentScreen > 0) {
                        getChildAt(mCurrentScreen - 1).addFocusables(views, direction);
                    }
                } else if (direction == View.FOCUS_RIGHT) {
                    if (mCurrentScreen < getChildCount() - 1) {
                        getChildAt(mCurrentScreen + 1).addFocusables(views, direction);
                    }
                }
            } else {
                openFolder.addFocusables(views, direction);
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            // (In XLarge mode, the workspace is shrunken below all apps, and responds to taps
            // ie when you click on a mini-screen, it zooms back to that screen)
            if (mLauncher.isWorkspaceLocked() ||
                    (!LauncherApplication.isScreenXLarge() && mLauncher.isAllAppsVisible())) {
                return false;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (disallowIntercept) {
            // We need to make sure to cancel our long press if
            // a scrollable widget takes over touch events
            final View currentScreen = getChildAt(mCurrentScreen);
            currentScreen.cancelLongPress();
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final boolean workspaceLocked = mLauncher.isWorkspaceLocked();
        final boolean allAppsVisible = mLauncher.isAllAppsVisible();

        // (In XLarge mode, the workspace is shrunken below all apps, and responds to taps
        // ie when you click on a mini-screen, it zooms back to that screen)
        if (workspaceLocked || (!LauncherApplication.isScreenXLarge() && allAppsVisible)) {
            return false; // We don't want the events.  Let them fall through to the all apps view.
        }

        /*
         * This method JUST determines whether we want to intercept the motion.
         * If we return true, onTouchEvent will be called and we do the actual
         * scrolling there.
         */

        /*
         * Shortcut the most recurring case: the user is in the dragging
         * state and he is moving his finger.  We want to intercept this
         * motion.
         */
        final int action = ev.getAction();
        if ((action == MotionEvent.ACTION_MOVE) && (mTouchState != TOUCH_STATE_REST)) {
            return true;
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_MOVE: {
                /*
                 * mIsBeingDragged == false, otherwise the shortcut would have caught it. Check
                 * whether the user has moved far enough from his original down touch.
                 */

                /*
                 * Locally do absolute value. mLastMotionX is set to the y value
                 * of the down event.
                 */
                final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                final float x = ev.getX(pointerIndex);
                final float y = ev.getY(pointerIndex);
                final int xDiff = (int) Math.abs(x - mLastMotionX);
                final int yDiff = (int) Math.abs(y - mLastMotionY);

                final int touchSlop = mTouchSlop;
                boolean xMoved = xDiff > touchSlop;
                boolean yMoved = yDiff > touchSlop;

                if (xMoved || yMoved) {

                    if (xMoved) {
                        // Scroll if the user moved far enough along the X axis
                        mTouchState = TOUCH_STATE_SCROLLING;
                        mLastMotionX = x;
                        mTouchX = mScrollX;
                        mSmoothingTime = System.nanoTime() / NANOTIME_DIV;
                        enableChildrenCache(mCurrentScreen - 1, mCurrentScreen + 1);
                    }
                    // Either way, cancel any pending longpress
                    if (mAllowLongPress) {
                        mAllowLongPress = false;
                        // Try canceling the long press. It could also have been scheduled
                        // by a distant descendant, so use the mAllowLongPress flag to block
                        // everything
                        final View currentScreen = getChildAt(mCurrentScreen);
                        currentScreen.cancelLongPress();
                    }
                }
                break;
            }

        case MotionEvent.ACTION_DOWN: {
            final float x = ev.getX();
            final float y = ev.getY();
            // Remember location of down touch
            mLastMotionX = x;
            mLastMotionY = y;
            mActivePointerId = ev.getPointerId(0);
            mAllowLongPress = true;

                /*
                 * If being flinged and user touches the screen, initiate drag;
                 * otherwise don't.  mScroller.isFinished should be false when
                 * being flinged.
                 */
                mTouchState = mScroller.isFinished() ? TOUCH_STATE_REST : TOUCH_STATE_SCROLLING;
                break;
            }

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:

                if (mTouchState != TOUCH_STATE_SCROLLING) {
                    final CellLayout currentScreen = (CellLayout)getChildAt(mCurrentScreen);
                    if (!currentScreen.lastDownOnOccupiedCell()) {
                        getLocationOnScreen(mTempCell);
                        // Send a tap to the wallpaper if the last down was on empty space
                        final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                        mWallpaperManager.sendWallpaperCommand(getWindowToken(),
                                "android.wallpaper.tap",
                                mTempCell[0] + (int) ev.getX(pointerIndex),
                                mTempCell[1] + (int) ev.getY(pointerIndex), 0, null);
                    }
                }

                // Release the drag
                clearChildrenCache();
                mTouchState = TOUCH_STATE_REST;
                mActivePointerId = INVALID_POINTER;
                mAllowLongPress = false;

                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }

            break;

        case MotionEvent.ACTION_POINTER_UP:
            onSecondaryPointerUp(ev);
            break;
        }

        /*
         * The only time we want to intercept motion events is if we are in the
         * drag mode.
         */
        return mTouchState != TOUCH_STATE_REST;
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = (ev.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
                MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            // TODO: Make this decision more intelligent.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mLastMotionX = ev.getX(newPointerIndex);
            mLastMotionY = ev.getY(newPointerIndex);
            mActivePointerId = ev.getPointerId(newPointerIndex);
            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
    }

    /**
     * If one of our descendant views decides that it could be focused now, only
     * pass that along if it's on the current screen.
     *
     * This happens when live folders requery, and if they're off screen, they
     * end up calling requestFocus, which pulls it on screen.
     */
    @Override
    public void focusableViewAvailable(View focused) {
        View current = getChildAt(mCurrentScreen);
        View v = focused;
        while (true) {
            if (v == current) {
                super.focusableViewAvailable(focused);
                return;
            }
            if (v == this) {
                return;
            }
            ViewParent parent = v.getParent();
            if (parent instanceof View) {
                v = (View) v.getParent();
            } else {
                return;
            }
        }
    }

    void enableChildrenCache(int fromScreen, int toScreen) {
        if (fromScreen > toScreen) {
            final int temp = fromScreen;
            fromScreen = toScreen;
            toScreen = temp;
        }

        final int screenCount = getChildCount();

        fromScreen = Math.max(fromScreen, 0);
        toScreen = Math.min(toScreen, screenCount - 1);

        for (int i = fromScreen; i <= toScreen; i++) {
            final CellLayout layout = (CellLayout) getChildAt(i);
            layout.setChildrenDrawnWithCacheEnabled(true);
            layout.setChildrenDrawingCacheEnabled(true);
        }
    }

    void clearChildrenCache() {
        final int screenCount = getChildCount();
        for (int i = 0; i < screenCount; i++) {
            final CellLayout layout = (CellLayout) getChildAt(i);
            layout.setChildrenDrawnWithCacheEnabled(false);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {

        if (mLauncher.isWorkspaceLocked()) {
            return false; // We don't want the events.  Let them fall through to the all apps view.
        }
        if (mLauncher.isAllAppsVisible()) {
            // Cancel any scrolling that is in progress.
            if (!mScroller.isFinished()) {
                mScroller.abortAnimation();
            }
            snapToScreen(mCurrentScreen);
            return false; // We don't want the events.  Let them fall through to the all apps view.
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        final int action = ev.getAction();

        switch (action & MotionEvent.ACTION_MASK) {
        case MotionEvent.ACTION_DOWN:
            /*
             * If being flinged and user touches, stop the fling. isFinished
             * will be false if being flinged.
             */
            if (!mScroller.isFinished()) {
                mScroller.abortAnimation();
            }

            // Remember where the motion event started
            mLastMotionX = ev.getX();
            mActivePointerId = ev.getPointerId(0);
            if (mTouchState == TOUCH_STATE_SCROLLING) {
                enableChildrenCache(mCurrentScreen - 1, mCurrentScreen + 1);
            }
            break;
        case MotionEvent.ACTION_MOVE:
            if (mTouchState == TOUCH_STATE_SCROLLING) {
                // Scroll to follow the motion event
                final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                final float x = ev.getX(pointerIndex);
                final float deltaX = mLastMotionX - x;
                mLastMotionX = x;

                if (deltaX < 0) {
                    if (mTouchX > 0) {
                        mTouchX += Math.max(-mTouchX, deltaX);
                        mSmoothingTime = System.nanoTime() / NANOTIME_DIV;
                        invalidate();
                    }
                } else if (deltaX > 0) {
                    final float availableToScroll = getChildAt(getChildCount() - 1).getRight() -
                            mTouchX - getWidth();
                    if (availableToScroll > 0) {
                        mTouchX += Math.min(availableToScroll, deltaX);
                        mSmoothingTime = System.nanoTime() / NANOTIME_DIV;
                        invalidate();
                    }
                } else {
                    awakenScrollBars();
                }
            }
            break;
        case MotionEvent.ACTION_UP:
            if (mTouchState == TOUCH_STATE_SCROLLING) {
                final VelocityTracker velocityTracker = mVelocityTracker;
                velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                final int velocityX = (int) velocityTracker.getXVelocity(mActivePointerId);

                final int screenWidth = getWidth();
                final int whichScreen = (mScrollX + (screenWidth / 2)) / screenWidth;
                final float scrolledPos = (float) mScrollX / screenWidth;

                if (velocityX > SNAP_VELOCITY && mCurrentScreen > 0) {
                    // Fling hard enough to move left.
                    // Don't fling across more than one screen at a time.
                    final int bound = scrolledPos < whichScreen ?
                            mCurrentScreen - 1 : mCurrentScreen;
                    snapToScreen(Math.min(whichScreen, bound), velocityX, true);
                } else if (velocityX < -SNAP_VELOCITY && mCurrentScreen < getChildCount() - 1) {
                    // Fling hard enough to move right
                    // Don't fling across more than one screen at a time.
                    final int bound = scrolledPos > whichScreen ?
                            mCurrentScreen + 1 : mCurrentScreen;
                    snapToScreen(Math.max(whichScreen, bound), velocityX, true);
                } else {
                    snapToScreen(whichScreen, 0, true);
                }

                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
            }
            mTouchState = TOUCH_STATE_REST;
            mActivePointerId = INVALID_POINTER;
            break;
        case MotionEvent.ACTION_CANCEL:
            mTouchState = TOUCH_STATE_REST;
            mActivePointerId = INVALID_POINTER;
            break;
        case MotionEvent.ACTION_POINTER_UP:
            onSecondaryPointerUp(ev);
            break;
        }

        return true;
    }

    public boolean isSmall() {
        return mIsSmall;
    }

    void shrinkToTop() {
        shrink(SHRINK_TO_TOP, true);
    }

    void shrinkToMiddle() {
        shrink(SHRINK_TO_MIDDLE, true);
    }

    void shrinkToBottom() {
        shrinkToBottom(true);
    }

    void shrinkToBottom(boolean animated) {
        if (mFirstLayout) {
            // (mFirstLayout == "first layout has not happened yet")
            // if we get a call to shrink() as part of our initialization (for example, if
            // Launcher is started in All Apps mode) then we need to wait for a layout call
            // to get our width so we can layout the mini-screen views correctly
            mWaitingToShrinkToBottom = true;
        } else {
            shrink(SHRINK_TO_BOTTOM, animated);
        }
    }

    // we use this to shrink the workspace for the all apps view and the customize view
    private void shrink(int shrinkPosition, boolean animated) {
        mIsSmall = true;
        final Resources res = getResources();
        final int screenWidth = getWidth();
        final int screenHeight = getHeight();
        final int scaledScreenWidth = (int) (SHRINK_FACTOR * screenWidth);
        final int scaledScreenHeight = (int) (SHRINK_FACTOR * screenHeight);
        final float scaledSpacing = res.getDimension(R.dimen.smallScreenSpacing);

        final int screenCount = getChildCount();
        float totalWidth = screenCount * scaledScreenWidth + (screenCount - 1) * scaledSpacing;

        float newY = getResources().getDimension(R.dimen.smallScreenVerticalMargin);
        if (shrinkPosition == SHRINK_TO_BOTTOM) {
            newY = screenHeight - newY - scaledScreenHeight;
        } else if (shrinkPosition == SHRINK_TO_MIDDLE) {
            newY = screenHeight / 2 - scaledScreenHeight / 2;
        }

        // We animate all the screens to the centered position in workspace
        // At the same time, the screens become greyed/dimmed

        // newX is initialized to the left-most position of the centered screens
        float newX = (mCurrentScreen + 1) * screenWidth - screenWidth / 2 - totalWidth / 2;
        Sequencer s = new Sequencer();
        for (int i = 0; i < screenCount; i++) {
            CellLayout cl = (CellLayout) getChildAt(i);
            cl.setPivotX(0.0f);
            cl.setPivotY(0.0f);
            if (animated) {
                final int duration = res.getInteger(R.integer.config_workspaceShrinkTime);
                s.playTogether(
                        new PropertyAnimator(duration, cl, "x", newX),
                        new PropertyAnimator(duration, cl, "y", newY),
                        new PropertyAnimator(duration, cl, "scaleX", SHRINK_FACTOR),
                        new PropertyAnimator(duration, cl, "scaleY", SHRINK_FACTOR),
                        new PropertyAnimator(duration, cl, "dimmedBitmapAlpha", 1.0f));
            } else {
                cl.setX((int)newX);
                cl.setY((int)newY);
                cl.setScaleX(SHRINK_FACTOR);
                cl.setScaleY(SHRINK_FACTOR);
                cl.setDimmedBitmapAlpha(1.0f);
            }
            // increment newX for the next screen
            newX += scaledScreenWidth + scaledSpacing;
            cl.setOnInterceptTouchListener(this);
        }
        setChildrenDrawnWithCacheEnabled(true);
        if (animated) s.start();
    }

    // We call this when we trigger an unshrink by clicking on the CellLayout cl
    private void unshrink(CellLayout clThatWasClicked) {
        int newCurrentScreen = mCurrentScreen;
        final int screenCount = getChildCount();
        for (int i = 0; i < screenCount; i++) {
            if (getChildAt(i) == clThatWasClicked) {
                newCurrentScreen = i;
            }
        }
        unshrink(newCurrentScreen);
    }

    private void unshrink(int newCurrentScreen) {
        if (mIsSmall) {
            int delta = (newCurrentScreen - mCurrentScreen)*getWidth();

            final int screenCount = getChildCount();
            for (int i = 0; i < screenCount; i++) {
                CellLayout cl = (CellLayout) getChildAt(i);
                cl.setX(cl.getX() + delta);
            }
            mScrollX = newCurrentScreen * getWidth();

            unshrink();
            setCurrentScreen(newCurrentScreen);
        }
    }

    void unshrink() {
        unshrink(true);
    }

    void unshrink(boolean animated) {
        if (mIsSmall) {
            Sequencer s = new Sequencer();
            final int screenCount = getChildCount();

            final int duration = getResources().getInteger(R.integer.config_workspaceUnshrinkTime);
            for (int i = 0; i < screenCount; i++) {
                final CellLayout cl = (CellLayout)getChildAt(i);
                cl.setPivotX(0.0f);
                cl.setPivotY(0.0f);
                if (animated) {
                    s.playTogether(
                            new PropertyAnimator(duration, cl, "translationX", 0.0f),
                            new PropertyAnimator(duration, cl, "translationY", 0.0f),
                            new PropertyAnimator(duration, cl, "scaleX", 1.0f),
                            new PropertyAnimator(duration, cl, "scaleY", 1.0f),
                            new PropertyAnimator(duration, cl, "dimmedBitmapAlpha", 0.0f));
                } else {
                    cl.setTranslationX(0.0f);
                    cl.setTranslationY(0.0f);
                    cl.setScaleX(1.0f);
                    cl.setScaleY(1.0f);
                    cl.setDimmedBitmapAlpha(0.0f);
                }
            }
            s.addListener(mUnshrinkAnimationListener);
            s.start();
        }
    }

    void snapToScreen(int whichScreen) {
        snapToScreen(whichScreen, 0, false);
    }

    private void snapToScreen(int whichScreen, int velocity, boolean settle) {
        // if (!mScroller.isFinished()) return;

        whichScreen = Math.max(0, Math.min(whichScreen, getChildCount() - 1));

        enableChildrenCache(mCurrentScreen, whichScreen);

        mNextScreen = whichScreen;

        if (mPreviousIndicator != null) {
            mPreviousIndicator.setLevel(mNextScreen);
            mNextIndicator.setLevel(mNextScreen);
        }

        View focusedChild = getFocusedChild();
        if (focusedChild != null && whichScreen != mCurrentScreen &&
                focusedChild == getChildAt(mCurrentScreen)) {
            focusedChild.clearFocus();
        }

        final int screenDelta = Math.max(1, Math.abs(whichScreen - mCurrentScreen));
        final int newX = whichScreen * getWidth();
        final int delta = newX - mScrollX;
        int duration = (screenDelta + 1) * 100;

        if (!mScroller.isFinished()) {
            mScroller.abortAnimation();
        }

        if (settle) {
            mScrollInterpolator.setDistance(screenDelta);
        } else {
            mScrollInterpolator.disableSettle();
        }

        velocity = Math.abs(velocity);
        if (velocity > 0) {
            duration += (duration / (velocity / BASELINE_FLING_VELOCITY))
                    * FLING_VELOCITY_INFLUENCE;
        } else {
            duration += 100;
        }

        awakenScrollBars(duration);
        mScroller.startScroll(mScrollX, 0, delta, 0, duration);
        invalidate();
    }

    void startDrag(CellLayout.CellInfo cellInfo) {
        View child = cellInfo.cell;

        // Make sure the drag was started by a long press as opposed to a long click.
        if (!child.isInTouchMode()) {
            return;
        }

        mDragInfo = cellInfo;
        mDragInfo.screen = mCurrentScreen;

        CellLayout current = ((CellLayout) getChildAt(mCurrentScreen));

        current.onDragChild(child);
        mDragController.startDrag(child, this, child.getTag(), DragController.DRAG_ACTION_MOVE);
        invalidate();
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final SavedState state = new SavedState(super.onSaveInstanceState());
        state.currentScreen = mCurrentScreen;
        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());
        if (savedState.currentScreen != -1) {
            setCurrentScreen(savedState.currentScreen, false);
            Launcher.setScreen(mCurrentScreen);
        }
    }

    void addApplicationShortcut(ShortcutInfo info, CellLayout.CellInfo cellInfo) {
        addApplicationShortcut(info, cellInfo, false);
    }

    void addApplicationShortcut(ShortcutInfo info, CellLayout.CellInfo cellInfo,
            boolean insertAtFirst) {
        final CellLayout layout = (CellLayout) getChildAt(cellInfo.screen);
        final int[] result = new int[2];

        layout.cellToPoint(cellInfo.cellX, cellInfo.cellY, result);
        onDropExternal(result[0], result[1], info, layout, insertAtFirst);
    }

    public void onDrop(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        CellLayout cellLayout = getCurrentDropLayout();
        int originX = x - xOffset;
        int originY = y - yOffset;
        if (mIsSmall) {
            // find out which target layout is over
            final float[] localXY = mTempDragCoordinates;
            localXY[0] = originX;
            localXY[1] = originY;
            final float[] localBottomRightXY = mTempDragBottomRightCoordinates;
            // we need to subtract left/top here because DragController already adds
            // dragRegionLeft/Top to xOffset and yOffset
            localBottomRightXY[0] = originX + dragView.getDragRegionWidth();
            localBottomRightXY[1] = originY + dragView.getDragRegionHeight();
            cellLayout =  findMatchingScreenForDragOver(localXY, localBottomRightXY);
            if (cellLayout == null) {
                // cancel the drag if we're not over a mini-screen at time of drop
                // TODO: maybe add a nice fade here?
                return;
            }
            // localXY will be transformed into the local screen's coordinate space; save that info
            originX = (int)localXY[0];
            originY = (int)localXY[1];
        }
        if (source != this) {
            onDropExternal(originX, originY, dragInfo, cellLayout);
        } else {
            // Move internally
            if (mDragInfo != null) {
                final View cell = mDragInfo.cell;
                int index = mScroller.isFinished() ? mCurrentScreen : mNextScreen;
                if (index != mDragInfo.screen) {
                    final CellLayout originalCellLayout = (CellLayout) getChildAt(mDragInfo.screen);
                    originalCellLayout.removeView(cell);
                    addInScreen(cell, index, mDragInfo.cellX, mDragInfo.cellY,
                            mDragInfo.spanX, mDragInfo.spanY);
                }

                mTargetCell = estimateDropCell(originX, originY,
                        mDragInfo.spanX, mDragInfo.spanY, cell, cellLayout,
                        mTargetCell);
                cellLayout.onDropChild(cell);

                // update the item's position after drop
                final ItemInfo info = (ItemInfo) cell.getTag();
                CellLayout.LayoutParams lp = (CellLayout.LayoutParams) cell
                        .getLayoutParams();
                lp.cellX = mTargetCell[0];
                lp.cellY = mTargetCell[1];

                LauncherModel.moveItemInDatabase(mLauncher, info,
                        LauncherSettings.Favorites.CONTAINER_DESKTOP, index,
                        lp.cellX, lp.cellY);
            }
        }
    }

    public void onDragEnter(DragSource source, int x, int y, int xOffset,
            int yOffset, DragView dragView, Object dragInfo) {
    }

    public DropTarget getDropTargetDelegate(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {

        // We may need to delegate the drag to a child view. If a 1x1 item
        // would land in a cell occupied by a DragTarget (e.g. a Folder),
        // then drag events should be handled by that child.

        ItemInfo item = (ItemInfo)dragInfo;
        CellLayout currentLayout = getCurrentDropLayout();

        int dragPointX, dragPointY;
        if (item.spanX == 1 && item.spanY == 1) {
            // For a 1x1, calculate the drop cell exactly as in onDragOver
            dragPointX = x - xOffset;
            dragPointY = y - yOffset;
        } else {
            // Otherwise, use the exact drag coordinates
            dragPointX = x;
            dragPointY = y;
        }

        // If we are dragging over a cell that contains a DropTarget that will
        // accept the drop, delegate to that DropTarget.
        final int[] cellXY = mTempCell;
        currentLayout.estimateDropCell(dragPointX, dragPointY, item.spanX, item.spanY, cellXY);
        View child = currentLayout.getChildAt(cellXY[0], cellXY[1]);
        if (child instanceof DropTarget) {
            DropTarget target = (DropTarget)child;
            if (target.acceptDrop(source, x, y, xOffset, yOffset, dragView, dragInfo)) {
                return target;
            }
        }
        return null;
    }

    // xy = upper left corner of item being dragged
    // bottomRightXy = lower right corner of item being dragged
    // This method will see which mini-screen is most overlapped by the item being dragged, and
    // return it. It will also transform the parameters xy and bottomRightXy into the local
    // coordinate space of the returned screen
    private CellLayout findMatchingScreenForDragOver(float[] xy, float[] bottomRightXy) {
        float x = xy[0];
        float y = xy[1];
        float right = bottomRightXy[0];
        float bottom = bottomRightXy[1];

        float bestX = 0;
        float bestY = 0;
        float bestRight = 0;
        float bestBottom = 0;

        Matrix inverseMatrix = new Matrix();

        // We loop through all the screens (ie CellLayouts) and see which one overlaps the most
        // with the item being dragged.
        final int screenCount = getChildCount();
        CellLayout bestMatchingScreen = null;
        float bestOverlapSoFar = 0;
        for (int i = 0; i < screenCount; i++) {
            CellLayout cl = (CellLayout)getChildAt(i);
            // Transform the coordinates of the item being dragged to the CellLayout's coordinates
            float left = cl.getLeft();
            float top = cl.getTop();
            xy[0] = x + mScrollX - left;
            xy[1] = y + mScrollY - top;
            cl.getMatrix().invert(inverseMatrix);

            bottomRightXy[0] = right + mScrollX - left;
            bottomRightXy[1] = bottom + mScrollY - top;

            inverseMatrix.mapPoints(xy);
            inverseMatrix.mapPoints(bottomRightXy);

            float dragRegionX = xy[0];
            float dragRegionY = xy[1];
            float dragRegionRight = bottomRightXy[0];
            float dragRegionBottom = bottomRightXy[1];

            // Find the overlapping region
            float overlapLeft = Math.max(0f, dragRegionX);
            float overlapTop = Math.max(0f, dragRegionY);
            float overlapBottom = Math.min(cl.getHeight(), dragRegionBottom);
            float overlapRight = Math.min(cl.getWidth(), dragRegionRight);

            if (overlapRight >= 0 && overlapLeft <= cl.getWidth() &&
                    overlapTop >= 0 && overlapBottom <= cl.getHeight()) {
                // Calculate the size of the overlapping region
                float overlap = (overlapRight - overlapLeft) * (overlapBottom - overlapTop);
                if (overlap > bestOverlapSoFar) {
                    bestOverlapSoFar = overlap;
                    bestMatchingScreen = cl;
                    bestX = xy[0];
                    bestY = xy[1];
                    bestRight = bottomRightXy[0];
                    bestBottom = bottomRightXy[1];
                }
             }
        }
        if (bestMatchingScreen != null && bestMatchingScreen != mDragTargetLayout) {
            if (mDragTargetLayout != null) {
                mDragTargetLayout.onDragComplete();
            }
            mDragTargetLayout = bestMatchingScreen;
        }
        xy[0] = bestX;
        xy[1] = bestY;
        bottomRightXy[0] = bestRight;
        bottomRightXy[1] = bestBottom;
        return bestMatchingScreen;
    }

    public void onDragOver(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {

        final ItemInfo item = (ItemInfo)dragInfo;
        CellLayout currentLayout = getCurrentDropLayout();

        if (dragInfo instanceof LauncherAppWidgetInfo) {
            LauncherAppWidgetInfo widgetInfo = (LauncherAppWidgetInfo)dragInfo;

            if (widgetInfo.spanX == -1) {
                // Calculate the grid spans needed to fit this widget
                int[] spans = currentLayout.rectToCell(widgetInfo.minWidth, widgetInfo.minHeight, null);
                item.spanX = spans[0];
                item.spanY = spans[1];
            }
        }
        int originX = x - xOffset;
        int originY = y - yOffset;
        if (mIsSmall) {
            // find out which mini screen the dragged item is over
            final float[] localXY = mTempDragCoordinates;
            localXY[0] = originX;
            localXY[1] = originY;
            final float[] localBottomRightXY = mTempDragBottomRightCoordinates;

            localBottomRightXY[0] = originX + dragView.getDragRegionWidth();
            localBottomRightXY[1] = originY + dragView.getDragRegionHeight();
            currentLayout = findMatchingScreenForDragOver(localXY, localBottomRightXY);
            if (currentLayout != null) {
                currentLayout.setHover(true);
            }

            originX = (int)localXY[0];
            originY = (int)localXY[1];
        }

        if (source != this) {
            // This is a hack to fix the point used to determine which cell an icon from the all
            // apps screen is over
            if (item != null && item.spanX == 1 && currentLayout != null) {
                int dragRegionLeft = (dragView.getWidth() - currentLayout.getCellWidth()) / 2;

                originX += dragRegionLeft - dragView.getDragRegionLeft();
                if (dragView.getDragRegionWidth() != currentLayout.getCellWidth()) {
                    dragView.setDragRegion(dragView.getDragRegionLeft(), dragView.getDragRegionTop(),
                            currentLayout.getCellWidth(), dragView.getDragRegionHeight());
                }
            }
        }
        if (currentLayout != mDragTargetLayout) {
            if (mDragTargetLayout != null) {
                mDragTargetLayout.onDragComplete();
            }
            mDragTargetLayout = currentLayout;
        }

        // only visualize the drop locations for moving icons within the home screen on tablet
        // on phone, we also visualize icons dragged in from All Apps
        if ((!LauncherApplication.isScreenXLarge() || source == this)
                && mDragTargetLayout != null) {
            final View child = (mDragInfo == null) ? null : mDragInfo.cell;
            mDragTargetLayout.visualizeDropLocation(
                    child, originX, originY, item.spanX, item.spanY);
        }
    }

    public void onDragExit(DragSource source, int x, int y, int xOffset,
            int yOffset, DragView dragView, Object dragInfo) {
        if (mDragTargetLayout != null) {
            mDragTargetLayout.onDragComplete();
            mDragTargetLayout = null;
        }
    }

    private void onDropExternal(int x, int y, Object dragInfo,
            CellLayout cellLayout) {
        onDropExternal(x, y, dragInfo, cellLayout, false);
    }

    private void onDropExternal(int x, int y, Object dragInfo,
            CellLayout cellLayout, boolean insertAtFirst) {
        // Drag from somewhere else
        ItemInfo info = (ItemInfo) dragInfo;

        View view = null;

        switch (info.itemType) {
        case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
        case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
            if (info.container == NO_ID && info instanceof ApplicationInfo) {
                // Came from all apps -- make a copy
                info = new ShortcutInfo((ApplicationInfo) info);
            }
            view = mLauncher.createShortcut(R.layout.application, cellLayout,
                    (ShortcutInfo) info);
            break;
        case LauncherSettings.Favorites.ITEM_TYPE_USER_FOLDER:
            view = FolderIcon.fromXml(R.layout.folder_icon, mLauncher,
                    (ViewGroup) getChildAt(mCurrentScreen),
                    ((UserFolderInfo) info));
            break;
        case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
            cellLayout.setTagToCellInfoForPoint(x, y);
            int[] position = new int[2];
            position[0] = x;
            position[1] = y;
            mLauncher.addAppWidgetFromDrop(((LauncherAppWidgetInfo)dragInfo).providerName,
                    cellLayout.getTag(), position);
            break;
        default:
            throw new IllegalStateException("Unknown item type: "
                    + info.itemType);
        }

        // If the view is null, it has already been added.
        if (view == null) {
            cellLayout.onDragComplete();
        } else {
            mTargetCell = estimateDropCell(x, y, 1, 1, view, cellLayout, mTargetCell);
            addInScreen(view, indexOfChild(cellLayout), mTargetCell[0],
                    mTargetCell[1], info.spanX, info.spanY, insertAtFirst);
            cellLayout.onDropChild(view);
            CellLayout.LayoutParams lp = (CellLayout.LayoutParams) view.getLayoutParams();

            LauncherModel.addOrMoveItemInDatabase(mLauncher, info,
                    LauncherSettings.Favorites.CONTAINER_DESKTOP, mCurrentScreen,
                    lp.cellX, lp.cellY);
        }
    }

    /**
     * Return the current {@link CellLayout}, correctly picking the destination
     * screen while a scroll is in progress.
     */
    private CellLayout getCurrentDropLayout() {
        int index = mScroller.isFinished() ? mCurrentScreen : mNextScreen;
        return (CellLayout) getChildAt(index);
    }

    /**
     * {@inheritDoc}
     */
    public boolean acceptDrop(DragSource source, int x, int y,
            int xOffset, int yOffset, DragView dragView, Object dragInfo) {
        final CellLayout layout = getCurrentDropLayout();
        final CellLayout.CellInfo dragCellInfo = mDragInfo;
        final int spanX = dragCellInfo == null ? 1 : dragCellInfo.spanX;
        final int spanY = dragCellInfo == null ? 1 : dragCellInfo.spanY;

        final View ignoreView = dragCellInfo == null ? null : dragCellInfo.cell;
        final CellLayout.CellInfo cellInfo = layout.updateOccupiedCells(null, ignoreView);

        if (cellInfo.findCellForSpan(mTempEstimate, spanX, spanY)) {
            return true;
        } else {
            Toast.makeText(getContext(), getContext().getString(R.string.out_of_space), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    public Rect estimateDropLocation(DragSource source, int x, int y,
            int xOffset, int yOffset, DragView dragView, Object dragInfo, Rect recycle) {
        final CellLayout layout = getCurrentDropLayout();

        final CellLayout.CellInfo cellInfo = mDragInfo;
        final int spanX = cellInfo == null ? 1 : cellInfo.spanX;
        final int spanY = cellInfo == null ? 1 : cellInfo.spanY;
        final View ignoreView = cellInfo == null ? null : cellInfo.cell;

        final Rect location = recycle != null ? recycle : new Rect();

        // Find drop cell and convert into rectangle
        int[] dropCell = estimateDropCell(x - xOffset, y - yOffset, spanX,
                spanY, ignoreView, layout, mTempCell);

        if (dropCell == null) {
            return null;
        }

        layout.cellToPoint(dropCell[0], dropCell[1], mTempEstimate);
        location.left = mTempEstimate[0];
        location.top = mTempEstimate[1];

        layout.cellToPoint(dropCell[0] + spanX, dropCell[1] + spanY, mTempEstimate);
        location.right = mTempEstimate[0];
        location.bottom = mTempEstimate[1];

        return location;
    }

    /**
     * Calculate the nearest cell where the given object would be dropped.
     */
    private int[] estimateDropCell(int pixelX, int pixelY,
            int spanX, int spanY, View ignoreView, CellLayout layout, int[] recycle) {

        final int[] cellXY = mTempCell;
        layout.estimateDropCell(pixelX, pixelY, spanX, spanY, cellXY);
        layout.cellToPoint(cellXY[0], cellXY[1], mTempEstimate);

        final CellLayout.CellInfo cellInfo = layout.updateOccupiedCells(null, ignoreView);
        // Find the best target drop location
        return layout.findNearestVacantArea(mTempEstimate[0], mTempEstimate[1], spanX, spanY, cellInfo, recycle);
    }

    /**
     * Estimate the size that a child with the given dimensions will take in the current screen.
     */
    void estimateChildSize(int minWidth, int minHeight, int[] result) {
        ((CellLayout)getChildAt(mCurrentScreen)).estimateChildSize(minWidth, minHeight, result);
    }

    void setLauncher(Launcher launcher) {
        mLauncher = launcher;
    }

    public void setDragController(DragController dragController) {
        mDragController = dragController;
    }

    public void onDropCompleted(View target, boolean success) {
        if (success) {
            if (target != this && mDragInfo != null) {
                final CellLayout cellLayout = (CellLayout) getChildAt(mDragInfo.screen);
                cellLayout.removeView(mDragInfo.cell);
                if (mDragInfo.cell instanceof DropTarget) {
                    mDragController.removeDropTarget((DropTarget)mDragInfo.cell);
                }
                // final Object tag = mDragInfo.cell.getTag();
            }
        } else {
            if (mDragInfo != null) {
                final CellLayout cellLayout = (CellLayout) getChildAt(mDragInfo.screen);
                cellLayout.onDropAborted(mDragInfo.cell);
            }
        }

        mDragInfo = null;
    }

    public void scrollLeft() {
        if (mScroller.isFinished()) {
            if (mCurrentScreen > 0)
                snapToScreen(mCurrentScreen - 1);
        } else {
            if (mNextScreen > 0)
                snapToScreen(mNextScreen - 1);
        }
    }

    public void scrollRight() {
        if (mScroller.isFinished()) {
            if (mCurrentScreen < getChildCount() - 1)
                snapToScreen(mCurrentScreen + 1);
        } else {
            if (mNextScreen < getChildCount() - 1)
                snapToScreen(mNextScreen + 1);
        }
    }

    public int getScreenForView(View v) {
        int result = -1;
        if (v != null) {
            ViewParent vp = v.getParent();
            final int screenCount = getChildCount();
            for (int i = 0; i < screenCount; i++) {
                if (vp == getChildAt(i)) {
                    return i;
                }
            }
        }
        return result;
    }

    public Folder getFolderForTag(Object tag) {
        final int screenCount = getChildCount();
        for (int screen = 0; screen < screenCount; screen++) {
            CellLayout currentScreen = ((CellLayout) getChildAt(screen));
            int count = currentScreen.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = currentScreen.getChildAt(i);
                CellLayout.LayoutParams lp = (CellLayout.LayoutParams) child.getLayoutParams();
                if (lp.cellHSpan == 4 && lp.cellVSpan == 4 && child instanceof Folder) {
                    Folder f = (Folder) child;
                    if (f.getInfo() == tag && f.getInfo().opened) {
                        return f;
                    }
                }
            }
        }
        return null;
    }

    public View getViewForTag(Object tag) {
        int screenCount = getChildCount();
        for (int screen = 0; screen < screenCount; screen++) {
            CellLayout currentScreen = ((CellLayout) getChildAt(screen));
            int count = currentScreen.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = currentScreen.getChildAt(i);
                if (child.getTag() == tag) {
                    return child;
                }
            }
        }
        return null;
    }

    /**
     * @return True is long presses are still allowed for the current touch
     */
    public boolean allowLongPress() {
        return mAllowLongPress;
    }

    /**
     * Set true to allow long-press events to be triggered, usually checked by
     * {@link Launcher} to accept or block dpad-initiated long-presses.
     */
    public void setAllowLongPress(boolean allowLongPress) {
        mAllowLongPress = allowLongPress;
    }

    void removeItems(final ArrayList<ApplicationInfo> apps) {
        final int screenCount = getChildCount();
        final PackageManager manager = getContext().getPackageManager();
        final AppWidgetManager widgets = AppWidgetManager.getInstance(getContext());

        final HashSet<String> packageNames = new HashSet<String>();
        final int appCount = apps.size();
        for (int i = 0; i < appCount; i++) {
            packageNames.add(apps.get(i).componentName.getPackageName());
        }

        for (int i = 0; i < screenCount; i++) {
            final CellLayout layout = (CellLayout) getChildAt(i);

            // Avoid ANRs by treating each screen separately
            post(new Runnable() {
                public void run() {
                    final ArrayList<View> childrenToRemove = new ArrayList<View>();
                    childrenToRemove.clear();

                    int childCount = layout.getChildCount();
                    for (int j = 0; j < childCount; j++) {
                        final View view = layout.getChildAt(j);
                        Object tag = view.getTag();

                        if (tag instanceof ShortcutInfo) {
                            final ShortcutInfo info = (ShortcutInfo) tag;
                            final Intent intent = info.intent;
                            final ComponentName name = intent.getComponent();

                            if (Intent.ACTION_MAIN.equals(intent.getAction()) && name != null) {
                                for (String packageName: packageNames) {
                                    if (packageName.equals(name.getPackageName())) {
                                        // TODO: This should probably be done on a worker thread
                                        LauncherModel.deleteItemFromDatabase(mLauncher, info);
                                        childrenToRemove.add(view);
                                    }
                                }
                            }
                        } else if (tag instanceof UserFolderInfo) {
                            final UserFolderInfo info = (UserFolderInfo) tag;
                            final ArrayList<ShortcutInfo> contents = info.contents;
                            final ArrayList<ShortcutInfo> toRemove = new ArrayList<ShortcutInfo>(1);
                            final int contentsCount = contents.size();
                            boolean removedFromFolder = false;

                            for (int k = 0; k < contentsCount; k++) {
                                final ShortcutInfo appInfo = contents.get(k);
                                final Intent intent = appInfo.intent;
                                final ComponentName name = intent.getComponent();

                                if (Intent.ACTION_MAIN.equals(intent.getAction()) && name != null) {
                                    for (String packageName: packageNames) {
                                        if (packageName.equals(name.getPackageName())) {
                                            toRemove.add(appInfo);
                                            // TODO: This should probably be done on a worker thread
                                            LauncherModel.deleteItemFromDatabase(
                                                    mLauncher, appInfo);
                                            removedFromFolder = true;
                                        }
                                    }
                                }
                            }

                            contents.removeAll(toRemove);
                            if (removedFromFolder) {
                                final Folder folder = getOpenFolder();
                                if (folder != null)
                                    folder.notifyDataSetChanged();
                            }
                        } else if (tag instanceof LiveFolderInfo) {
                            final LiveFolderInfo info = (LiveFolderInfo) tag;
                            final Uri uri = info.uri;
                            final ProviderInfo providerInfo = manager.resolveContentProvider(
                                    uri.getAuthority(), 0);

                            if (providerInfo != null) {
                                for (String packageName: packageNames) {
                                    if (packageName.equals(providerInfo.packageName)) {
                                        // TODO: This should probably be done on a worker thread
                                        LauncherModel.deleteItemFromDatabase(mLauncher, info);
                                        childrenToRemove.add(view);
                                    }
                                }
                            }
                        } else if (tag instanceof LauncherAppWidgetInfo) {
                            final LauncherAppWidgetInfo info = (LauncherAppWidgetInfo) tag;
                            final AppWidgetProviderInfo provider =
                                    widgets.getAppWidgetInfo(info.appWidgetId);
                            if (provider != null) {
                                for (String packageName: packageNames) {
                                    if (packageName.equals(provider.provider.getPackageName())) {
                                        // TODO: This should probably be done on a worker thread
                                        LauncherModel.deleteItemFromDatabase(mLauncher, info);
                                        childrenToRemove.add(view);
                                    }
                                }
                            }
                        }
                    }

                    childCount = childrenToRemove.size();
                    for (int j = 0; j < childCount; j++) {
                        View child = childrenToRemove.get(j);
                        layout.removeViewInLayout(child);
                        if (child instanceof DropTarget) {
                            mDragController.removeDropTarget((DropTarget)child);
                        }
                    }

                    if (childCount > 0) {
                        layout.requestLayout();
                        layout.invalidate();
                    }
                }
            });
        }
    }

    void updateShortcuts(ArrayList<ApplicationInfo> apps) {
        final PackageManager pm = mLauncher.getPackageManager();

        final int screenCount = getChildCount();
        for (int i = 0; i < screenCount; i++) {
            final CellLayout layout = (CellLayout) getChildAt(i);
            int childCount = layout.getChildCount();
            for (int j = 0; j < childCount; j++) {
                final View view = layout.getChildAt(j);
                Object tag = view.getTag();
                if (tag instanceof ShortcutInfo) {
                    ShortcutInfo info = (ShortcutInfo)tag;
                    // We need to check for ACTION_MAIN otherwise getComponent() might
                    // return null for some shortcuts (for instance, for shortcuts to
                    // web pages.)
                    final Intent intent = info.intent;
                    final ComponentName name = intent.getComponent();
                    if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION &&
                            Intent.ACTION_MAIN.equals(intent.getAction()) && name != null) {
                        final int appCount = apps.size();
                        for (int k = 0; k < appCount; k++) {
                            ApplicationInfo app = apps.get(k);
                            if (app.componentName.equals(name)) {
                                info.setIcon(mIconCache.getIcon(info.intent));
                                ((TextView)view).setCompoundDrawablesWithIntrinsicBounds(null,
                                        new FastBitmapDrawable(info.getIcon(mIconCache)),
                                        null, null);
                                }
                        }
                    }
                }
            }
        }
    }

    void moveToDefaultScreen(boolean animate) {
        if (animate) {
            if (mIsSmall) {
                unshrink(mDefaultScreen);
            } else {
                snapToScreen(mDefaultScreen);
            }
        } else {
            setCurrentScreen(mDefaultScreen);
        }
        getChildAt(mDefaultScreen).requestFocus();
    }

    void setIndicators(Drawable previous, Drawable next) {
        mPreviousIndicator = previous;
        mNextIndicator = next;
        previous.setLevel(mCurrentScreen);
        next.setLevel(mCurrentScreen);
    }

    public static class SavedState extends BaseSavedState {
        int currentScreen = -1;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            currentScreen = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(currentScreen);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}
