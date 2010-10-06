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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
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
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.IBinder;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * The workspace is a wide area with a wallpaper and a finite number of pages.
 * Each page contains a number of icons, folders or widgets the user can
 * interact with. A workspace is meant to be used with a fixed width only.
 */
public class Workspace extends SmoothPagedView
        implements DropTarget, DragSource, DragScroller, View.OnTouchListener {
    @SuppressWarnings({"UnusedDeclaration"})
    private static final String TAG = "Launcher.Workspace";

    // This is how much the workspace shrinks when we enter all apps or
    // customization mode
    private static final float SHRINK_FACTOR = 0.16f;

    // Y rotation to apply to the workspace screens
    private static final float WORKSPACE_ROTATION = 12.5f;

    // These are extra scale factors to apply to the mini home screens
    // so as to achieve the desired transform
    private static final float EXTRA_SCALE_FACTOR_0 = 0.97f;
    private static final float EXTRA_SCALE_FACTOR_1 = 1.0f;
    private static final float EXTRA_SCALE_FACTOR_2 = 1.08f;

    private static final int BACKGROUND_FADE_OUT_DELAY = 300;
    private static final int BACKGROUND_FADE_OUT_DURATION = 300;
    private static final int BACKGROUND_FADE_IN_DURATION = 100;

    static final int SCROLL_RIGHT = 0;
    static final int SCROLL_LEFT = 1;

    // These animators are used to fade the
    private ObjectAnimator<Float> mBackgroundFadeIn;
    private ObjectAnimator<Float> mBackgroundFadeOut;
    private float mBackgroundAlpha = 0;

    private enum ShrinkPosition { SHRINK_TO_TOP, SHRINK_TO_MIDDLE, SHRINK_TO_BOTTOM };

    private final WallpaperManager mWallpaperManager;

    private int mDefaultPage;

    private boolean mWaitingToShrinkToBottom = false;

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

    private Launcher mLauncher;
    private IconCache mIconCache;
    private DragController mDragController;

    private int[] mTempCell = new int[2];
    private int[] mTempEstimate = new int[2];
    private float[] mTempOriginXY = new float[2];
    private float[] mTempDragCoordinates = new float[2];
    private float[] mTempDragBottomRightCoordinates = new float[2];
    private Matrix mTempInverseMatrix = new Matrix();

    private static final int DEFAULT_CELL_COUNT_X = 4;
    private static final int DEFAULT_CELL_COUNT_Y = 4;

    private Drawable mPreviousIndicator;
    private Drawable mNextIndicator;

    // State variable that indicated whether the pages are small (ie when you're
    // in all apps or customize mode)
    private boolean mIsSmall;
    private AnimatorListener mUnshrinkAnimationListener;

    /**
     * Used to inflate the Workspace from XML.
     *
     * @param context The application's context.
     * @param attrs The attributes set containing the Workspace's customization values.
     */
    public Workspace(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Used to inflate the Workspace from XML.
     *
     * @param context The application's context.
     * @param attrs The attributes set containing the Workspace's customization values.
     * @param defStyle Unused.
     */
    public Workspace(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContentIsRefreshable = false;

        if (!LauncherApplication.isScreenXLarge()) {
            mFadeInAdjacentScreens = false;
        }

        mWallpaperManager = WallpaperManager.getInstance(context);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.Workspace, defStyle, 0);
        int cellCountX = a.getInt(R.styleable.Workspace_cellCountX, DEFAULT_CELL_COUNT_X);
        int cellCountY = a.getInt(R.styleable.Workspace_cellCountY, DEFAULT_CELL_COUNT_Y);
        mDefaultPage = a.getInt(R.styleable.Workspace_defaultScreen, 1);
        a.recycle();

        LauncherModel.updateWorkspaceLayoutCells(cellCountX, cellCountY);
        setHapticFeedbackEnabled(false);

        initWorkspace();
    }

    /**
     * Initializes various states for this workspace.
     */
    protected void initWorkspace() {
        Context context = getContext();
        mCurrentPage = mDefaultPage;
        Launcher.setScreen(mCurrentPage);
        LauncherApplication app = (LauncherApplication)context.getApplicationContext();
        mIconCache = app.getIconCache();

        mUnshrinkAnimationListener = new AnimatorListener() {
            public void onAnimationStart(Animator animation) {}
            public void onAnimationEnd(Animator animation) {
                mIsSmall = false;
            }
            public void onAnimationCancel(Animator animation) {}
            public void onAnimationRepeat(Animator animation) {}
        };

        mSnapVelocity = 600;
    }

    @Override
    protected int getScrollMode() {
        if (LauncherApplication.isScreenXLarge()) {
            return SmoothPagedView.QUINTIC_MODE;
        } else {
            return SmoothPagedView.OVERSHOOT_MODE;
        }
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
        CellLayout currentPage = (CellLayout) getChildAt(mCurrentPage);
        int count = currentPage.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = currentPage.getChildAt(i);
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
            CellLayout currentPage = (CellLayout) getChildAt(screen);
            int count = currentPage.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = currentPage.getChildAt(i);
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

    boolean isDefaultPageShowing() {
        return mCurrentPage == mDefaultPage;
    }

    /**
     * Sets the current screen.
     *
     * @param currentPage
     */
    @Override
    void setCurrentPage(int currentPage) {
        super.setCurrentPage(currentPage);
        updateWallpaperOffset(mScrollX);
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
        int childId = LauncherModel.getCellLayoutChildId(-1, screen, x, y, spanX, spanY);
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

    public boolean onTouch(View v, MotionEvent event) {
        // this is an intercepted event being forwarded from a cell layout
        if (mIsSmall) {
            mLauncher.onWorkspaceClick((CellLayout) v);
            return true;
        }
        return false;
    }

    @Override
    public boolean dispatchUnhandledMove(View focused, int direction) {
        if (mIsSmall) {
            // when the home screens are shrunken, shouldn't allow side-scrolling
            return false;
        }
        return super.dispatchUnhandledMove(focused, direction);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mIsSmall) {
            // when the home screens are shrunken, shouldn't allow side-scrolling
            return false;
        }
        return super.onInterceptTouchEvent(ev);
    }

    protected void pageBeginMoving() {
        if (mNextPage != INVALID_PAGE) {
            // we're snapping to a particular screen
            enableChildrenCache(mCurrentPage, mNextPage);
        } else {
            // this is when user is actively dragging a particular screen, they might
            // swipe it either left or right (but we won't advance by more than one screen)
            enableChildrenCache(mCurrentPage - 1, mCurrentPage + 1);
        }
        showOutlines();
    }

    protected void pageEndMoving() {
        clearChildrenCache();
        hideOutlines();
    }

    @Override
    protected void notifyPageSwitchListener() {
        super.notifyPageSwitchListener();

        if (mPreviousIndicator != null) {
            // if we know the next page, we show the indication for it right away; it looks
            // weird if the indicators are lagging
            int page = mNextPage;
            if (page == INVALID_PAGE) {
                page = mCurrentPage;
            }
            mPreviousIndicator.setLevel(page);
            mNextIndicator.setLevel(page);
        }
        Launcher.setScreen(mCurrentPage);
    };

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

    private float getScaleXForRotation(float degrees) {
        return (float) (1.0f / Math.cos(Math.PI * degrees / 180.0f));
    }

    public void showOutlines() {
        if (mBackgroundFadeOut != null) mBackgroundFadeOut.cancel();
        if (mBackgroundFadeIn != null) mBackgroundFadeIn.cancel();
        mBackgroundFadeIn = new ObjectAnimator<Float>(BACKGROUND_FADE_IN_DURATION, this,
                        new PropertyValuesHolder<Float>("backgroundAlpha", 1.0f));
        mBackgroundFadeIn.start();
    }

    public void hideOutlines() {
        if (mBackgroundFadeIn != null) mBackgroundFadeIn.cancel();
        if (mBackgroundFadeOut != null) mBackgroundFadeOut.cancel();
        mBackgroundFadeOut = new ObjectAnimator<Float>(BACKGROUND_FADE_OUT_DURATION, this,
                        new PropertyValuesHolder<Float>("backgroundAlpha", 0.0f));
        mBackgroundFadeOut.setStartDelay(BACKGROUND_FADE_OUT_DELAY);
        mBackgroundFadeOut.start();
    }

    public void setBackgroundAlpha(float alpha) {
        mBackgroundAlpha = alpha;
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout cl = (CellLayout) getChildAt(i);
            cl.setBackgroundAlpha(alpha);
        }
    }

    public float getBackgroundAlpha() {
        return mBackgroundAlpha;
    }

    @Override
    protected void screenScrolled(int screenCenter) {
        CellLayout cur = (CellLayout) getChildAt(mCurrentPage);
        CellLayout toRight = (CellLayout) getChildAt(mCurrentPage + 1);
        CellLayout toLeft = (CellLayout) getChildAt(mCurrentPage - 1);

        for (int i = 0; i < mCurrentPage - 1; i++) {
            View v = getChildAt(i);
            if (v != null) {
                v.setRotationY(WORKSPACE_ROTATION);
                v.setScaleX(getScaleXForRotation(WORKSPACE_ROTATION));
            }
        }
        for (int i = mCurrentPage + 1; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v != null) {
                v.setRotationY(-WORKSPACE_ROTATION);
                v.setScaleX(getScaleXForRotation(-WORKSPACE_ROTATION));
            }
        }

        int halfScreenSize = getMeasuredWidth() / 2;
        int pageWidth = cur.getMeasuredWidth();
        int delta = screenCenter - (getChildOffset(mCurrentPage) -
                getRelativeChildOffset(mCurrentPage) + halfScreenSize);

        float scrollProgress = Math.abs(delta/(pageWidth*1.0f + mPageSpacing));
        int scrollDirection = delta > 0 ? SCROLL_LEFT : SCROLL_RIGHT;

        float rotation;

        if (scrollDirection == SCROLL_RIGHT) {
            rotation = -scrollProgress * WORKSPACE_ROTATION;
            cur.setRotationY(rotation);
            cur.setScaleX(getScaleXForRotation(rotation));

            if (toLeft != null) {
                rotation = WORKSPACE_ROTATION * (1 - scrollProgress);
                toLeft.setRotationY(rotation);
                toLeft.setScaleX(getScaleXForRotation(rotation));
            }
            if (toRight != null) {
                toRight.setRotationY(-WORKSPACE_ROTATION);
                toRight.setScaleX(getScaleXForRotation(WORKSPACE_ROTATION));
            }
        } else {
            rotation = scrollProgress * WORKSPACE_ROTATION;
            cur.setRotationY(rotation);
            cur.setScaleX(getScaleXForRotation(rotation));

            if (toRight != null) {
                rotation = -WORKSPACE_ROTATION * (1 - scrollProgress);
                toRight.setRotationY(rotation);
                toRight.setScaleX(getScaleXForRotation(rotation));
            }
            if (toLeft != null) {
                toLeft.setRotationY(WORKSPACE_ROTATION);
                toLeft.setScaleX(getScaleXForRotation(WORKSPACE_ROTATION));
            }
        }
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        computeScroll();
        mDragController.setWindowToken(getWindowToken());
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        // if shrinkToBottom() is called on initialization, it has to be deferred
        // until after the first call to onLayout so that it has the correct width
        if (mWaitingToShrinkToBottom) {
            shrinkToBottom(false);
            mWaitingToShrinkToBottom = false;
        }

        if (LauncherApplication.isInPlaceRotationEnabled()) {
            // When the device is rotated, the scroll position of the current screen
            // needs to be refreshed
            setCurrentPage(getCurrentPage());
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mIsSmall) {
            // Draw all the workspaces if we're small
            final int pageCount = getChildCount();
            final long drawingTime = getDrawingTime();
            for (int i = 0; i < pageCount; i++) {
                final View page = (View) getChildAt(i);

                drawChild(canvas, page, drawingTime);
            }
        } else {
            super.dispatchDraw(canvas);
        }
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        if (!mLauncher.isAllAppsVisible()) {
            final Folder openFolder = getOpenFolder();
            if (openFolder != null) {
                return openFolder.requestFocus(direction, previouslyFocusedRect);
            } else {
                return super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
            }
        }
        return false;
    }

    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        if (!mLauncher.isAllAppsVisible()) {
            final Folder openFolder = getOpenFolder();
            if (openFolder != null) {
                openFolder.addFocusables(views, direction);
            } else {
                super.addFocusables(views, direction, focusableMode);
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            // (In XLarge mode, the workspace is shrunken below all apps, and responds to taps
            // ie when you click on a mini-screen, it zooms back to that screen)
            if (!LauncherApplication.isScreenXLarge() && mLauncher.isAllAppsVisible()) {
                return false;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    void enableChildrenCache(int fromPage, int toPage) {
        if (fromPage > toPage) {
            final int temp = fromPage;
            fromPage = toPage;
            toPage = temp;
        }

        final int screenCount = getChildCount();

        fromPage = Math.max(fromPage, 0);
        toPage = Math.min(toPage, screenCount - 1);

        for (int i = fromPage; i <= toPage; i++) {
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
        if (mLauncher.isAllAppsVisible()) {
            // Cancel any scrolling that is in progress.
            if (!mScroller.isFinished()) {
                mScroller.abortAnimation();
            }
            snapToPage(mCurrentPage);
            return false; // We don't want the events.  Let them fall through to the all apps view.
        }

        return super.onTouchEvent(ev);
    }

    public boolean isSmall() {
        return mIsSmall;
    }

    void shrinkToTop(boolean animated) {
        shrink(ShrinkPosition.SHRINK_TO_TOP, animated);
    }

    void shrinkToMiddle() {
        shrink(ShrinkPosition.SHRINK_TO_MIDDLE, true);
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
            shrink(ShrinkPosition.SHRINK_TO_BOTTOM, animated);
        }
    }

    private float getYScaleForScreen(int screen) {
        int x = Math.abs(screen - 2);

        // TODO: This should be generalized for use with arbitrary rotation angles.
        switch(x) {
            case 0: return EXTRA_SCALE_FACTOR_0;
            case 1: return EXTRA_SCALE_FACTOR_1;
            case 2: return EXTRA_SCALE_FACTOR_2;
        }
        return 1.0f;
    }

    // we use this to shrink the workspace for the all apps view and the customize view
    private void shrink(ShrinkPosition shrinkPosition, boolean animated) {
        mIsSmall = true;
        // we intercept and reject all touch events when we're small, so be sure to reset the state
        mTouchState = TOUCH_STATE_REST;
        mActivePointerId = INVALID_POINTER;

        final Resources res = getResources();
        final int screenWidth = getWidth();
        final int screenHeight = getHeight();

        // Making the assumption that all pages have the same width as the 0th
        final int pageWidth = getChildAt(0).getMeasuredWidth();
        final int pageHeight = getChildAt(0).getMeasuredHeight();

        final int scaledPageWidth = (int) (SHRINK_FACTOR * pageWidth);
        final int scaledPageHeight = (int) (SHRINK_FACTOR * pageHeight);
        final float extraScaledSpacing = res.getDimension(R.dimen.smallScreenExtraSpacing);

        final int screenCount = getChildCount();
        float totalWidth = screenCount * scaledPageWidth + (screenCount - 1) * extraScaledSpacing;

        float newY = getResources().getDimension(R.dimen.smallScreenVerticalMargin);
        if (shrinkPosition == ShrinkPosition.SHRINK_TO_BOTTOM) {
            newY = screenHeight - newY - scaledPageHeight;
        } else if (shrinkPosition == ShrinkPosition.SHRINK_TO_MIDDLE) {
            newY = screenHeight / 2 - scaledPageHeight / 2;
        }

        // We animate all the screens to the centered position in workspace
        // At the same time, the screens become greyed/dimmed

        // newX is initialized to the left-most position of the centered screens
        float newX = mScroller.getFinalX() + screenWidth / 2 - totalWidth / 2;

        // We are going to scale about the center of the view, so we need to adjust the positions
        // of the views accordingly
        newX -= (pageWidth - scaledPageWidth) / 2.0f;
        newY -= (pageHeight - scaledPageHeight) / 2.0f;
        for (int i = 0; i < screenCount; i++) {
            CellLayout cl = (CellLayout) getChildAt(i);

            float rotation = (-i + 2) * WORKSPACE_ROTATION;
            float rotationScaleX = (float) (1.0f / Math.cos(Math.PI * rotation / 180.0f));
            float rotationScaleY = getYScaleForScreen(i);

            if (animated) {
                final int duration = res.getInteger(R.integer.config_workspaceShrinkTime);
                new ObjectAnimator<Float>(duration, cl,
                        new PropertyValuesHolder<Float>("x", newX),
                        new PropertyValuesHolder<Float>("y", newY),
                        new PropertyValuesHolder<Float>("scaleX", SHRINK_FACTOR * rotationScaleX),
                        new PropertyValuesHolder<Float>("scaleY", SHRINK_FACTOR * rotationScaleY),
                        new PropertyValuesHolder<Float>("backgroundAlpha", 1.0f),
                        new PropertyValuesHolder<Float>("alpha", 1.0f),
                        new PropertyValuesHolder<Float>("rotationY", rotation)).start();
            } else {
                cl.setX((int)newX);
                cl.setY((int)newY);
                cl.setScaleX(SHRINK_FACTOR * rotationScaleX);
                cl.setScaleY(SHRINK_FACTOR * rotationScaleY);
                cl.setBackgroundAlpha(1.0f);
                cl.setAlpha(0.0f);
                cl.setRotationY(rotation);
            }
            // increment newX for the next screen
            newX += scaledPageWidth + extraScaledSpacing;
            cl.setOnInterceptTouchListener(this);
        }
        setChildrenDrawnWithCacheEnabled(true);
    }

    // We call this when we trigger an unshrink by clicking on the CellLayout cl
    public void unshrink(CellLayout clThatWasClicked) {
        int newCurrentPage = mCurrentPage;
        final int screenCount = getChildCount();
        for (int i = 0; i < screenCount; i++) {
            if (getChildAt(i) == clThatWasClicked) {
                newCurrentPage = i;
            }
        }
        unshrink(newCurrentPage);
    }

    private void unshrink(int newCurrentPage) {
        if (mIsSmall) {
            int newX = getChildOffset(newCurrentPage) - getRelativeChildOffset(newCurrentPage);
            int delta = newX - mScrollX;

            final int screenCount = getChildCount();
            for (int i = 0; i < screenCount; i++) {
                CellLayout cl = (CellLayout) getChildAt(i);
                cl.setX(cl.getX() + delta);
            }
            snapToPage(newCurrentPage);
            unshrink();

            setCurrentPage(newCurrentPage);
        }
    }

    void unshrink() {
        unshrink(true);
    }

    void unshrink(boolean animated) {
        if (mIsSmall) {
            AnimatorSet s = new AnimatorSet();
            final int screenCount = getChildCount();

            final int duration = getResources().getInteger(R.integer.config_workspaceUnshrinkTime);
            for (int i = 0; i < screenCount; i++) {
                final CellLayout cl = (CellLayout)getChildAt(i);
                float finalAlphaValue = (i == mCurrentPage) ? 1.0f : 0.0f;
                float rotation = 0.0f;

                if (i < mCurrentPage) {
                    rotation = WORKSPACE_ROTATION;
                } else if (i > mCurrentPage) {
                    rotation = -WORKSPACE_ROTATION;
                }

                if (animated) {
                    s.playTogether(
                            new ObjectAnimator<Float>(duration, cl, "translationX", 0.0f),
                            new ObjectAnimator<Float>(duration, cl, "translationY", 0.0f),
                            new ObjectAnimator<Float>(duration, cl, "scaleX", 1.0f),
                            new ObjectAnimator<Float>(duration, cl, "scaleY", 1.0f),
                            new ObjectAnimator<Float>(duration, cl, "backgroundAlpha", 0.0f),
                            new ObjectAnimator<Float>(duration, cl, "alpha", finalAlphaValue),
                            new ObjectAnimator<Float>(duration, cl, "rotationY", rotation));
                } else {
                    cl.setTranslationX(0.0f);
                    cl.setTranslationY(0.0f);
                    cl.setScaleX(1.0f);
                    cl.setScaleY(1.0f);
                    cl.setBackgroundAlpha(0.0f);
                    cl.setAlpha(finalAlphaValue);
                    cl.setRotationY(rotation);
                }
            }
            s.addListener(mUnshrinkAnimationListener);
            s.start();
        }
    }

    void startDrag(CellLayout.CellInfo cellInfo) {
        View child = cellInfo.cell;

        // Make sure the drag was started by a long press as opposed to a long click.
        if (!child.isInTouchMode()) {
            return;
        }

        mDragInfo = cellInfo;
        mDragInfo.screen = mCurrentPage;

        CellLayout current = ((CellLayout) getChildAt(mCurrentPage));

        current.onDragChild(child);
        mDragController.startDrag(child, this, child.getTag(), DragController.DRAG_ACTION_MOVE);
        current.onDragEnter(child);
        invalidate();
    }

    void addApplicationShortcut(ShortcutInfo info, int screen, int cellX, int cellY,
            boolean insertAtFirst, int intersectX, int intersectY) {
        final CellLayout cellLayout = (CellLayout) getChildAt(screen);
        View view = mLauncher.createShortcut(R.layout.application, cellLayout, (ShortcutInfo) info);

        final int[] cellXY = new int[2];
        cellLayout.findCellForSpanThatIntersects(cellXY, 1, 1, intersectX, intersectY);
        addInScreen(view, screen, cellXY[0], cellXY[1], 1, 1, insertAtFirst);
        LauncherModel.addOrMoveItemInDatabase(mLauncher, info,
                LauncherSettings.Favorites.CONTAINER_DESKTOP, screen,
                cellXY[0], cellXY[1]);
    }


    public void onDrop(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        CellLayout cellLayout;
        int originX = x - xOffset;
        int originY = y - yOffset;
        if (mIsSmall) {
            cellLayout = findMatchingPageForDragOver(dragView, originX, originY);
            if (cellLayout == null) {
                // cancel the drag if we're not over a mini-screen at time of drop
                // TODO: maybe add a nice fade here?
                return;
            }
            // get originX and originY in the local coordinate system of the screen
            mTempOriginXY[0] = originX;
            mTempOriginXY[1] = originY;
            mapPointGlobalToLocal(cellLayout, mTempOriginXY);
            originX = (int)mTempOriginXY[0];
            originY = (int)mTempOriginXY[1];
        } else {
            cellLayout = getCurrentDropLayout();
        }

        if (source != this) {
            onDropExternal(originX, originY, dragInfo, cellLayout);
        } else {
            // Move internally
            if (mDragInfo != null) {
                final View cell = mDragInfo.cell;

                mTargetCell = findNearestVacantArea(originX, originY,
                        mDragInfo.spanX, mDragInfo.spanY, cell, cellLayout,
                        mTargetCell);

                int screen = indexOfChild(cellLayout);
                if (screen != mDragInfo.screen) {
                    final CellLayout originalCellLayout = (CellLayout) getChildAt(mDragInfo.screen);
                    originalCellLayout.removeView(cell);
                    addInScreen(cell, screen, mTargetCell[0], mTargetCell[1],
                            mDragInfo.spanX, mDragInfo.spanY);
                }
                cellLayout.onDropChild(cell);

                // update the item's position after drop
                final ItemInfo info = (ItemInfo) cell.getTag();
                CellLayout.LayoutParams lp = (CellLayout.LayoutParams) cell.getLayoutParams();
                cellLayout.onMove(cell, mTargetCell[0], mTargetCell[1]);
                lp.cellX = mTargetCell[0];
                lp.cellY = mTargetCell[1];
                cell.setId(LauncherModel.getCellLayoutChildId(-1, mDragInfo.screen,
                        mTargetCell[0], mTargetCell[1], mDragInfo.spanX, mDragInfo.spanY));

                LauncherModel.moveItemInDatabase(mLauncher, info,
                        LauncherSettings.Favorites.CONTAINER_DESKTOP, screen,
                        lp.cellX, lp.cellY);
            }
        }
    }

    public void onDragEnter(DragSource source, int x, int y, int xOffset,
            int yOffset, DragView dragView, Object dragInfo) {
        getCurrentDropLayout().onDragEnter(dragView);
    }

    public DropTarget getDropTargetDelegate(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {

        if (mIsSmall) {
            // If we're shrunken, don't let anyone drag on folders/etc  that are on the mini-screens
            return null;
        }
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
        dragPointX += mScrollX - currentLayout.getLeft();
        dragPointY += mScrollY - currentLayout.getTop();

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

    private void mapPointGlobalToLocal(View v, float[] xy) {
        xy[0] = xy[0] + mScrollX - v.getLeft();
        xy[1] = xy[1] + mScrollY - v.getTop();
        v.getMatrix().invert(mTempInverseMatrix);
        mTempInverseMatrix.mapPoints(xy);
    }

    // xy = upper left corner of item being dragged
    // bottomRightXy = lower right corner of item being dragged
    // This method will see which mini-screen is most overlapped by the item being dragged, and
    // return it. It will also transform the parameters xy and bottomRightXy into the local
    // coordinate space of the returned screen
    private CellLayout findMatchingPageForDragOver(DragView dragView, int originX, int originY) {
        float x = originX + dragView.getScaledDragRegionXOffset();
        float y = originY + dragView.getScaledDragRegionYOffset();
        float right = x + dragView.getScaledDragRegionWidth();
        float bottom = y + dragView.getScaledDragRegionHeight();

        // We loop through all the screens (ie CellLayouts) and see which one overlaps the most
        // with the item being dragged.
        final int screenCount = getChildCount();
        CellLayout bestMatchingScreen = null;
        float smallestDistSoFar = Float.MAX_VALUE;
        final float[] xy = mTempDragCoordinates;
        final float[] bottomRightXy = mTempDragBottomRightCoordinates;
        for (int i = 0; i < screenCount; i++) {
            CellLayout cl = (CellLayout)getChildAt(i);
            // Transform the coordinates of the item being dragged to the CellLayout's coordinates
            float left = cl.getLeft();
            float top = cl.getTop();
            xy[0] = x + mScrollX - left;
            xy[1] = y + mScrollY - top;

            bottomRightXy[0] = right + mScrollX - left;
            bottomRightXy[1] = bottom + mScrollY - top;

            cl.getMatrix().invert(mTempInverseMatrix);
            mTempInverseMatrix.mapPoints(xy);
            mTempInverseMatrix.mapPoints(bottomRightXy);

            float dragRegionX = xy[0];
            float dragRegionY = xy[1];
            float dragRegionRight = bottomRightXy[0];
            float dragRegionBottom = bottomRightXy[1];
            float dragRegionCenterX = (dragRegionX + dragRegionRight) / 2.0f;
            float dragRegionCenterY = (dragRegionY + dragRegionBottom) / 2.0f;

            // Find the overlapping region
            float overlapLeft = Math.max(0f, dragRegionX);
            float overlapTop = Math.max(0f, dragRegionY);
            float overlapBottom = Math.min(cl.getHeight(), dragRegionBottom);
            float overlapRight = Math.min(cl.getWidth(), dragRegionRight);
            if (overlapRight >= 0 && overlapLeft <= cl.getWidth() &&
                    (overlapTop >= 0 && overlapBottom <= cl.getHeight())) {
                // Calculate the distance between the two centers
                float distX = dragRegionCenterX - cl.getWidth()/2;
                float distY = dragRegionCenterY - cl.getHeight()/2;
                float dist = distX * distX + distY * distY;

                float overlap = (overlapRight - overlapLeft) * (overlapBottom - overlapTop);

                // Calculate the closest overlapping region
                if (overlap > 0 && dist < smallestDistSoFar) {
                    smallestDistSoFar = dist;
                    bestMatchingScreen = cl;
                }
             }
        }

        if (bestMatchingScreen != mDragTargetLayout) {
            if (mDragTargetLayout != null) {
                mDragTargetLayout.onDragExit();
            }
            mDragTargetLayout = bestMatchingScreen;
        }
        return bestMatchingScreen;
    }

    public void onDragOver(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        CellLayout currentLayout;
        int originX = x - xOffset;
        int originY = y - yOffset;
        if (mIsSmall) {
            currentLayout = findMatchingPageForDragOver(dragView, originX, originY);

            if (currentLayout == null) {
                return;
            }

            currentLayout.setHover(true);
            // get originX and originY in the local coordinate system of the screen
            mTempOriginXY[0] = originX;
            mTempOriginXY[1] = originY;
            mapPointGlobalToLocal(currentLayout, mTempOriginXY);
            originX = (int)mTempOriginXY[0];
            originY = (int)mTempOriginXY[1];
        } else {
            currentLayout = getCurrentDropLayout();
        }

        final ItemInfo item = (ItemInfo)dragInfo;

        if (dragInfo instanceof LauncherAppWidgetInfo) {
            LauncherAppWidgetInfo widgetInfo = (LauncherAppWidgetInfo)dragInfo;

            if (widgetInfo.spanX == -1) {
                // Calculate the grid spans needed to fit this widget
                int[] spans = currentLayout.rectToCell(widgetInfo.minWidth, widgetInfo.minHeight, null);
                item.spanX = spans[0];
                item.spanY = spans[1];
            }
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
                mDragTargetLayout.onDragExit();
                currentLayout.onDragEnter(dragView);
            }
            mDragTargetLayout = currentLayout;
        }

        // only visualize the drop locations for moving icons within the home screen on tablet
        // on phone, we also visualize icons dragged in from All Apps
        if ((!LauncherApplication.isScreenXLarge() || source == this)
                && mDragTargetLayout != null) {
            final View child = (mDragInfo == null) ? null : mDragInfo.cell;
            int localOriginX = originX - (mDragTargetLayout.getLeft() - mScrollX);
            int localOriginY = originY - (mDragTargetLayout.getTop() - mScrollY);
            mDragTargetLayout.visualizeDropLocation(
                    child, localOriginX, localOriginY, item.spanX, item.spanY);
        }
    }

    public void onDragExit(DragSource source, int x, int y, int xOffset,
            int yOffset, DragView dragView, Object dragInfo) {
        if (mDragTargetLayout != null) {
            mDragTargetLayout.onDragExit();
            mDragTargetLayout = null;
        }
    }

    private void onDropExternal(int x, int y, Object dragInfo,
            CellLayout cellLayout) {
        onDropExternal(x, y, dragInfo, cellLayout, false);
    }

    /**
     * Add the item specified by dragInfo to the given layout.
     * This is basically the equivalent of onDropExternal, except it's not initiated
     * by drag and drop.
     * @return true if successful
     */
    public boolean addExternalItemToScreen(Object dragInfo, View layout) {
        CellLayout cl = (CellLayout) layout;
        ItemInfo info = (ItemInfo) dragInfo;

        if (cl.findCellForSpan(mTempEstimate, info.spanX, info.spanY)) {
            onDropExternal(0, 0, dragInfo, cl, false);
            return true;
        }
        mLauncher.showOutOfSpaceMessage();
        return false;
    }

    // Drag from somewhere else
    private void onDropExternal(int x, int y, Object dragInfo,
            CellLayout cellLayout, boolean insertAtFirst) {
        int screen = indexOfChild(cellLayout);
        if (dragInfo instanceof PendingAddItemInfo) {
            PendingAddItemInfo info = (PendingAddItemInfo) dragInfo;
            // When dragging and dropping from customization tray, we deal with creating
            // widgets/shortcuts/folders in a slightly different way
            int[] touchXY = new int[2];
            touchXY[0] = x;
            touchXY[1] = y;
            switch (info.itemType) {
                case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                    mLauncher.addAppWidgetFromDrop(info.componentName, screen, touchXY);
                    break;
                case LauncherSettings.Favorites.ITEM_TYPE_LIVE_FOLDER:
                    mLauncher.addLiveFolderFromDrop(info.componentName, screen, touchXY);
                    break;
                case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                    mLauncher.processShortcutFromDrop(info.componentName, screen, touchXY);
                    break;
                default:
                    throw new IllegalStateException("Unknown item type: " + info.itemType);
            }
            cellLayout.onDragExit();
            return;
        }

        // This is for other drag/drop cases, like dragging from All Apps
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
                    cellLayout, ((UserFolderInfo) info));
            break;
        default:
            throw new IllegalStateException("Unknown item type: " + info.itemType);
        }

        // If the view is null, it has already been added.
        if (view == null) {
            cellLayout.onDragExit();
        } else {
            mTargetCell = findNearestVacantArea(x, y, 1, 1, null, cellLayout, mTargetCell);
            addInScreen(view, indexOfChild(cellLayout), mTargetCell[0],
                    mTargetCell[1], info.spanX, info.spanY, insertAtFirst);
            cellLayout.onDropChild(view);
            CellLayout.LayoutParams lp = (CellLayout.LayoutParams) view.getLayoutParams();

            LauncherModel.addOrMoveItemInDatabase(mLauncher, info,
                    LauncherSettings.Favorites.CONTAINER_DESKTOP, screen,
                    lp.cellX, lp.cellY);
        }
    }

    /**
     * Return the current {@link CellLayout}, correctly picking the destination
     * screen while a scroll is in progress.
     */
    private CellLayout getCurrentDropLayout() {
        int index = mScroller.isFinished() ? mCurrentPage : mNextPage;
        return (CellLayout) getChildAt(index);
    }

    /**
     * Return the current CellInfo describing our current drag; this method exists
     * so that Launcher can sync this object with the correct info when the activity is created/
     * destroyed
     *
     */
    public CellLayout.CellInfo getDragInfo() {
        return mDragInfo;
    }

    /**
     * {@inheritDoc}
     */
    public boolean acceptDrop(DragSource source, int x, int y,
            int xOffset, int yOffset, DragView dragView, Object dragInfo) {
        CellLayout layout;
        if (mIsSmall) {
            layout = findMatchingPageForDragOver(dragView, x - xOffset, y - yOffset);
            if (layout == null) {
                // cancel the drag if we're not over a mini-screen at time of drop
                return false;
            }
        } else {
            layout = getCurrentDropLayout();
        }
        final CellLayout.CellInfo dragCellInfo = mDragInfo;
        final int spanX = dragCellInfo == null ? 1 : dragCellInfo.spanX;
        final int spanY = dragCellInfo == null ? 1 : dragCellInfo.spanY;

        final View ignoreView = dragCellInfo == null ? null : dragCellInfo.cell;

        if (layout.findCellForSpanIgnoring(null, spanX, spanY, ignoreView)) {
            return true;
        } else {
            mLauncher.showOutOfSpaceMessage();
            return false;
        }
    }

    /**
     * Calculate the nearest cell where the given object would be dropped.
     */
    private int[] findNearestVacantArea(int pixelX, int pixelY,
            int spanX, int spanY, View ignoreView, CellLayout layout, int[] recycle) {

        int localPixelX = pixelX - (layout.getLeft() - mScrollX);
        int localPixelY = pixelY - (layout.getTop() - mScrollY);

        // Find the best target drop location
        return layout.findNearestVacantArea(
                localPixelX, localPixelY, spanX, spanY, ignoreView, recycle);
    }

    /**
     * Estimate the size that a child with the given dimensions will take in the current screen.
     */
    void estimateChildSize(int minWidth, int minHeight, int[] result) {
        ((CellLayout)getChildAt(mCurrentPage)).estimateChildSize(minWidth, minHeight, result);
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

    public boolean isDropEnabled() {
        return true;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(state);
        Launcher.setScreen(mCurrentPage);
    }

    @Override
    public void scrollLeft() {
        if (!mIsSmall) {
            super.scrollLeft();
        }
    }

    @Override
    public void scrollRight() {
        if (!mIsSmall) {
            super.scrollRight();
        }
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
                                            LauncherModel.deleteItemFromDatabase(mLauncher, appInfo);
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
                unshrink(mDefaultPage);
            } else {
                snapToPage(mDefaultPage);
            }
        } else {
            setCurrentPage(mDefaultPage);
        }
        getChildAt(mDefaultPage).requestFocus();
    }

    void setIndicators(Drawable previous, Drawable next) {
        mPreviousIndicator = previous;
        mNextIndicator = next;
        previous.setLevel(mCurrentPage);
        next.setLevel(mCurrentPage);
    }

    @Override
    public void syncPages() {
    }

    @Override
    public void syncPageItems(int page) {
    }

}
