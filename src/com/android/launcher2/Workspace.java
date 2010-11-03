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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region.Op;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.IBinder;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import com.android.launcher.R;
import com.android.launcher2.InstallWidgetReceiver.WidgetMimeTypeHandlerData;

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
    private static final float EXTRA_SCALE_FACTOR_0 = 0.972f;
    private static final float EXTRA_SCALE_FACTOR_1 = 1.0f;
    private static final float EXTRA_SCALE_FACTOR_2 = 1.10f;

    private static final int BACKGROUND_FADE_OUT_DELAY = 300;
    private static final int BACKGROUND_FADE_OUT_DURATION = 300;
    private static final int BACKGROUND_FADE_IN_DURATION = 100;

    // These animators are used to fade the background
    private ObjectAnimator mBackgroundFadeInAnimation;
    private ObjectAnimator mBackgroundFadeOutAnimation;
    private float mBackgroundAlpha = 0;

    private final WallpaperManager mWallpaperManager;

    private int mDefaultPage;

    private boolean mPageMoving = false;

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

    // These are temporary variables to prevent having to allocate a new object just to
    // return an (x, y) value from helper functions. Do NOT use them to maintain other state.
    private int[] mTempCell = new int[2];
    private int[] mTempEstimate = new int[2];
    private float[] mTempOriginXY = new float[2];
    private float[] mTempDragCoordinates = new float[2];
    private float[] mTempTouchCoordinates = new float[2];
    private float[] mTempCellLayoutCenterCoordinates = new float[2];
    private float[] mTempDragBottomRightCoordinates = new float[2];
    private Matrix mTempInverseMatrix = new Matrix();

    private static final int DEFAULT_CELL_COUNT_X = 4;
    private static final int DEFAULT_CELL_COUNT_Y = 4;

    private Drawable mPreviousIndicator;
    private Drawable mNextIndicator;

    // State variable that indicates whether the pages are small (ie when you're
    // in all apps or customize mode)
    private boolean mIsSmall = false;
    private boolean mIsInUnshrinkAnimation = false;
    private AnimatorListener mUnshrinkAnimationListener;
    private enum ShrinkPosition {
        SHRINK_TO_TOP, SHRINK_TO_MIDDLE, SHRINK_TO_BOTTOM_HIDDEN, SHRINK_TO_BOTTOM_VISIBLE };
    private ShrinkPosition mShrunkenState;
    private boolean mWaitingToShrink = false;
    private ShrinkPosition mWaitingToShrinkPosition;
    private AnimatorSet mAnimator;

    private boolean mInScrollArea = false;
    private boolean mInDragMode = false;

    private final HolographicOutlineHelper mOutlineHelper = new HolographicOutlineHelper();
    private Bitmap mDragOutline = null;
    private final Rect mTempRect = new Rect();
    private final int[] mTempXY = new int[2];

    private ValueAnimator mDropAnim = null;
    private TimeInterpolator mQuintEaseOutInterpolator = new DecelerateInterpolator(2.5f);
    private View mDropView = null;
    private int[] mDropViewPos = new int[] { -1, -1 };

    // Paint used to draw external drop outline
    private final Paint mExternalDragOutlinePaint = new Paint();

    /** Used to trigger an animation as soon as the workspace stops scrolling. */
    private Animator mAnimOnPageEndMoving = null;

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
        mExternalDragOutlinePaint.setAntiAlias(true);
        setWillNotDraw(false);

        mUnshrinkAnimationListener = new LauncherAnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mIsInUnshrinkAnimation = true;
            }
            @Override
            public void onAnimationEndOrCancel(Animator animation) {
                mIsInUnshrinkAnimation = false;
            }
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
        ((CellLayout) child).setOnInterceptTouchListener(this);
        super.addView(child, index, params);
    }

    @Override
    public void addView(View child) {
        if (!(child instanceof CellLayout)) {
            throw new IllegalArgumentException("A Workspace can only have CellLayout children.");
        }
        ((CellLayout) child).setOnInterceptTouchListener(this);
        super.addView(child);
    }

    @Override
    public void addView(View child, int index) {
        if (!(child instanceof CellLayout)) {
            throw new IllegalArgumentException("A Workspace can only have CellLayout children.");
        }
        ((CellLayout) child).setOnInterceptTouchListener(this);
        super.addView(child, index);
    }

    @Override
    public void addView(View child, int width, int height) {
        if (!(child instanceof CellLayout)) {
            throw new IllegalArgumentException("A Workspace can only have CellLayout children.");
        }
        ((CellLayout) child).setOnInterceptTouchListener(this);
        super.addView(child, width, height);
    }

    @Override
    public void addView(View child, LayoutParams params) {
        if (!(child instanceof CellLayout)) {
            throw new IllegalArgumentException("A Workspace can only have CellLayout children.");
        }
        ((CellLayout) child).setOnInterceptTouchListener(this);
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
        boolean markCellsAsOccupied = !(child instanceof Folder);
        if (!group.addViewToCellLayout(child, insert ? 0 : -1, childId, lp, markCellsAsOccupied)) {
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
        if (mIsSmall || mIsInUnshrinkAnimation) {
            mLauncher.onWorkspaceClick((CellLayout) v);
            return true;
        } else if (!mPageMoving) {
            if (v == getChildAt(mCurrentPage - 1)) {
                snapToPage(mCurrentPage - 1);
                return true;
            } else if (v == getChildAt(mCurrentPage + 1)) {
                snapToPage(mCurrentPage + 1);
                return true;
            }
        }
        return false;
    }

    protected void onWindowVisibilityChanged (int visibility) {
        mLauncher.onWindowVisibilityChanged(visibility);
    }

    @Override
    public boolean dispatchUnhandledMove(View focused, int direction) {
        if (mIsSmall || mIsInUnshrinkAnimation) {
            // when the home screens are shrunken, shouldn't allow side-scrolling
            return false;
        }
        return super.dispatchUnhandledMove(focused, direction);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mIsSmall || mIsInUnshrinkAnimation) {
            // when the home screens are shrunken, shouldn't allow side-scrolling
            return false;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    protected void determineScrollingStart(MotionEvent ev) {
        if (!mIsSmall && !mIsInUnshrinkAnimation) super.determineScrollingStart(ev);
    }

    protected void onPageBeginMoving() {
        if (mNextPage != INVALID_PAGE) {
            // we're snapping to a particular screen
            enableChildrenCache(mCurrentPage, mNextPage);
        } else {
            // this is when user is actively dragging a particular screen, they might
            // swipe it either left or right (but we won't advance by more than one screen)
            enableChildrenCache(mCurrentPage - 1, mCurrentPage + 1);
        }
        showOutlines();
        mPageMoving = true;
    }

    protected void onPageEndMoving() {
        clearChildrenCache();
        // Hide the outlines, as long as we're not dragging
        if (!mDragController.dragging()) {
            hideOutlines();
        }
        // Check for an animation that's waiting to be started
        if (mAnimOnPageEndMoving != null) {
            mAnimOnPageEndMoving.start();
            mAnimOnPageEndMoving = null;
        }

        mPageMoving = false;
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

    public void showOutlines() {
        if (!mIsSmall && !mIsInUnshrinkAnimation) {
            if (mBackgroundFadeOutAnimation != null) mBackgroundFadeOutAnimation.cancel();
            if (mBackgroundFadeInAnimation != null) mBackgroundFadeInAnimation.cancel();
            mBackgroundFadeInAnimation = ObjectAnimator.ofFloat(this, "backgroundAlpha", 1.0f);
            mBackgroundFadeInAnimation.setDuration(BACKGROUND_FADE_IN_DURATION);
            mBackgroundFadeInAnimation.start();
        }
    }

    public void hideOutlines() {
        if (!mIsSmall && !mIsInUnshrinkAnimation) {
            if (mBackgroundFadeInAnimation != null) mBackgroundFadeInAnimation.cancel();
            if (mBackgroundFadeOutAnimation != null) mBackgroundFadeOutAnimation.cancel();
            mBackgroundFadeOutAnimation = ObjectAnimator.ofFloat(this, "backgroundAlpha", 0.0f);
            mBackgroundFadeOutAnimation.setDuration(BACKGROUND_FADE_OUT_DURATION);
            mBackgroundFadeOutAnimation.setStartDelay(BACKGROUND_FADE_OUT_DELAY);
            mBackgroundFadeOutAnimation.start();
        }
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
        final int halfScreenSize = getMeasuredWidth() / 2;
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout cl = (CellLayout) getChildAt(i);
            if (cl != null) {
                int totalDistance = cl.getMeasuredWidth() + mPageSpacing;
                int delta = screenCenter - (getChildOffset(i) -
                        getRelativeChildOffset(i) + halfScreenSize);

                float scrollProgress = delta/(totalDistance*1.0f);
                scrollProgress = Math.min(scrollProgress, 1.0f);
                scrollProgress = Math.max(scrollProgress, -1.0f);

                float mult =  mInDragMode ? 1.0f : Math.abs(scrollProgress);
                cl.setBackgroundAlphaMultiplier(mult);

                float rotation = WORKSPACE_ROTATION * scrollProgress;
                cl.setRotationY(rotation);
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
        if (mWaitingToShrink) {
            shrink(mWaitingToShrinkPosition, false);
            mWaitingToShrink = false;
        }

        if (LauncherApplication.isInPlaceRotationEnabled()) {
            // When the device is rotated, the scroll position of the current screen
            // needs to be refreshed
            setCurrentPage(getCurrentPage());
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mIsSmall || mIsInUnshrinkAnimation) {
            // Draw all the workspaces if we're small
            final int pageCount = getChildCount();
            final long drawingTime = getDrawingTime();
            for (int i = 0; i < pageCount; i++) {
                final View page = (View) getChildAt(i);

                drawChild(canvas, page, drawingTime);
            }
        } else {
            super.dispatchDraw(canvas);

            final int width = getWidth();
            final int height = getHeight();

            // In portrait orientation, draw the glowing edge when dragging to adjacent screens
            if (mInScrollArea && (height > width)) {
                final int pageHeight = getChildAt(0).getHeight();

                // This determines the height of the glowing edge: 90% of the page height
                final int padding = (int) ((height - pageHeight) * 0.5f + pageHeight * 0.1f);

                final CellLayout leftPage = (CellLayout) getChildAt(mCurrentPage - 1);
                final CellLayout rightPage = (CellLayout) getChildAt(mCurrentPage + 1);

                if (leftPage != null && leftPage.getHover()) {
                    final Drawable d = getResources().getDrawable(R.drawable.page_hover_left);
                    d.setBounds(mScrollX, padding, mScrollX + d.getIntrinsicWidth(), height - padding);
                    d.draw(canvas);
                } else if (rightPage != null && rightPage.getHover()) {
                    final Drawable d = getResources().getDrawable(R.drawable.page_hover_right);
                    d.setBounds(mScrollX + width - d.getIntrinsicWidth(), padding, mScrollX + width, height - padding);
                    d.draw(canvas);
                }
            }

            if (mDropView != null) {
                // We are animating an item that was just dropped on the home screen.
                // Render its View in the current animation position.
                canvas.save(Canvas.MATRIX_SAVE_FLAG);
                final int xPos = mDropViewPos[0] - mDropView.getScrollX();
                final int yPos = mDropViewPos[1] - mDropView.getScrollY();
                canvas.translate(xPos, yPos);
                mDropView.draw(canvas);
                canvas.restore();
            }
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
            setCurrentPage(mCurrentPage);
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
        shrink(ShrinkPosition.SHRINK_TO_BOTTOM_HIDDEN, animated);
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
        if (mFirstLayout) {
            // (mFirstLayout == "first layout has not happened yet")
            // if we get a call to shrink() as part of our initialization (for example, if
            // Launcher is started in All Apps mode) then we need to wait for a layout call
            // to get our width so we can layout the mini-screen views correctly
            mWaitingToShrink = true;
            mWaitingToShrinkPosition = shrinkPosition;
            return;
        }
        mIsSmall = true;
        mShrunkenState = shrinkPosition;

        // Stop any scrolling, move to the current page right away
        setCurrentPage((mNextPage != INVALID_PAGE) ? mNextPage : mCurrentPage);
        updateWhichPagesAcceptDrops(mShrunkenState);

        // we intercept and reject all touch events when we're small, so be sure to reset the state
        mTouchState = TOUCH_STATE_REST;
        mActivePointerId = INVALID_POINTER;

        CellLayout currentPage = (CellLayout) getChildAt(mCurrentPage);
        currentPage.setBackgroundAlphaMultiplier(1.0f);

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

        boolean isPortrait = getMeasuredHeight() > getMeasuredWidth();
        float newY = (isPortrait ?
                getResources().getDimension(R.dimen.allAppsSmallScreenVerticalMarginPortrait) :
                getResources().getDimension(R.dimen.allAppsSmallScreenVerticalMarginLandscape));
        float finalAlpha = 1.0f;
        float extraShrinkFactor = 1.0f;
        if (shrinkPosition == ShrinkPosition.SHRINK_TO_BOTTOM_VISIBLE) {
             newY = screenHeight - newY - scaledPageHeight;
        } else if (shrinkPosition == ShrinkPosition.SHRINK_TO_BOTTOM_HIDDEN) {

            // We shrink and disappear to nothing in the case of all apps
            // (which is when we shrink to the bottom)
            newY = screenHeight - newY - scaledPageHeight;
            finalAlpha = 0.25f;
        } else if (shrinkPosition == ShrinkPosition.SHRINK_TO_MIDDLE) {
            newY = screenHeight / 2 - scaledPageHeight / 2;
            finalAlpha = 1.0f;
        } else if (shrinkPosition == ShrinkPosition.SHRINK_TO_TOP) {
            newY = (isPortrait ?
                getResources().getDimension(R.dimen.customizeSmallScreenVerticalMarginPortrait) :
                getResources().getDimension(R.dimen.customizeSmallScreenVerticalMarginLandscape));
        }

        // We animate all the screens to the centered position in workspace
        // At the same time, the screens become greyed/dimmed

        // newX is initialized to the left-most position of the centered screens
        float newX = mScroller.getFinalX() + screenWidth / 2 - totalWidth / 2;

        // We are going to scale about the center of the view, so we need to adjust the positions
        // of the views accordingly
        newX -= (pageWidth - scaledPageWidth) / 2.0f;
        newY -= (pageHeight - scaledPageHeight) / 2.0f;

        if (mAnimator != null) {
            mAnimator.cancel();
        }
        mAnimator = new AnimatorSet();
        for (int i = 0; i < screenCount; i++) {
            CellLayout cl = (CellLayout) getChildAt(i);

            float rotation = (-i + 2) * WORKSPACE_ROTATION;
            float rotationScaleX = (float) (1.0f / Math.cos(Math.PI * rotation / 180.0f));
            float rotationScaleY = getYScaleForScreen(i);

            if (animated) {
                final int duration = res.getInteger(R.integer.config_workspaceShrinkTime);
                ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(cl,
                        PropertyValuesHolder.ofFloat("x", newX),
                        PropertyValuesHolder.ofFloat("y", newY),
                        PropertyValuesHolder.ofFloat("scaleX",
                                SHRINK_FACTOR * rotationScaleX * extraShrinkFactor),
                        PropertyValuesHolder.ofFloat("scaleY",
                                SHRINK_FACTOR * rotationScaleY * extraShrinkFactor),
                        PropertyValuesHolder.ofFloat("backgroundAlpha", finalAlpha),
                        PropertyValuesHolder.ofFloat("alpha", finalAlpha),
                        PropertyValuesHolder.ofFloat("rotationY", rotation));
                anim.setDuration(duration);
                mAnimator.playTogether(anim);
            } else {
                cl.setX((int)newX);
                cl.setY((int)newY);
                cl.setScaleX(SHRINK_FACTOR * rotationScaleX * extraShrinkFactor);
                cl.setScaleY(SHRINK_FACTOR * rotationScaleY * extraShrinkFactor);
                cl.setBackgroundAlpha(finalAlpha);
                cl.setAlpha(finalAlpha);
                cl.setRotationY(rotation);
            }
            // increment newX for the next screen
            newX += scaledPageWidth + extraScaledSpacing;
        }
        if (animated) {
            mAnimator.start();
        }
        setChildrenDrawnWithCacheEnabled(true);
    }


    private void updateWhichPagesAcceptDrops(ShrinkPosition state) {
        updateWhichPagesAcceptDropsHelper(state, false, 1, 1);
    }


    private void updateWhichPagesAcceptDropsDuringDrag(ShrinkPosition state, int spanX, int spanY) {
        updateWhichPagesAcceptDropsHelper(state, true, spanX, spanY);
    }

    private void updateWhichPagesAcceptDropsHelper(
            ShrinkPosition state, boolean isDragHappening, int spanX, int spanY) {
        final int screenCount = getChildCount();
        for (int i = 0; i < screenCount; i++) {
            CellLayout cl = (CellLayout) getChildAt(i);

            switch (state) {
                case SHRINK_TO_TOP:
                    if (!isDragHappening) {
                        boolean showDropHighlight = i == mCurrentPage;
                        cl.setAcceptsDrops(showDropHighlight);
                        break;
                    }
                    // otherwise, fall through below and mark non-full screens as accepting drops
                case SHRINK_TO_BOTTOM_HIDDEN:
                case SHRINK_TO_BOTTOM_VISIBLE:
                    if (!isDragHappening) {
                        // even if a drag isn't happening, we don't want to show a screen as
                        // accepting drops if it doesn't have at least one free cell
                        spanX = 1;
                        spanY = 1;
                    }
                    // the page accepts drops if we can find at least one empty spot
                    cl.setAcceptsDrops(cl.findCellForSpan(null, spanX, spanY));
                    break;
                default:
                     throw new RuntimeException(
                             "updateWhichPagesAcceptDropsHelper passed an unhandled ShrinkPosition");
            }
        }
    }

    /*
     *
     * We call these methods (onDragStartedWithItemSpans/onDragStartedWithItemMinSize) whenever we
     * start a drag in Launcher, regardless of whether the drag has ever entered the Workspace
     *
     * These methods mark the appropriate pages as accepting drops (which alters their visual
     * appearance) and, if the pages are hidden, makes them visible.
     *
     */
    public void onDragStartedWithItemSpans(int spanX, int spanY) {
        updateWhichPagesAcceptDropsDuringDrag(mShrunkenState, spanX, spanY);
        if (mShrunkenState == ShrinkPosition.SHRINK_TO_BOTTOM_HIDDEN) {
            shrink(ShrinkPosition.SHRINK_TO_BOTTOM_VISIBLE, true);
        }
    }

    public void onDragStartedWithItemMinSize(int minWidth, int minHeight) {
        int[] spanXY = CellLayout.rectToCell(getResources(), minWidth, minHeight, null);
        onDragStartedWithItemSpans(spanXY[0], spanXY[1]);
    }

    // we call this method whenever a drag and drop in Launcher finishes, even if Workspace was
    // never dragged over
    public void onDragStopped() {
        updateWhichPagesAcceptDrops(mShrunkenState);
        if (mShrunkenState == ShrinkPosition.SHRINK_TO_BOTTOM_VISIBLE) {
            shrink(ShrinkPosition.SHRINK_TO_BOTTOM_HIDDEN, true);
        }
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

    @Override
    protected boolean handlePagingClicks() {
        return true;
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
            setCurrentPage(newCurrentPage);
            unshrink();
        }
    }

    void unshrink() {
        unshrink(true);
    }

    void unshrink(boolean animated) {
        if (mIsSmall) {
            mIsSmall = false;
            if (mAnimator != null) {
                mAnimator.cancel();
            }
            mAnimator = new AnimatorSet();
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
                    mAnimator.playTogether(
                            ObjectAnimator.ofFloat(cl, "translationX", 0.0f).setDuration(duration),
                            ObjectAnimator.ofFloat(cl, "translationY", 0.0f).setDuration(duration),
                            ObjectAnimator.ofFloat(cl, "scaleX", 1.0f).setDuration(duration),
                            ObjectAnimator.ofFloat(cl, "scaleY", 1.0f).setDuration(duration),
                            ObjectAnimator.ofFloat(cl, "backgroundAlpha", 0.0f).setDuration(duration),
                            ObjectAnimator.ofFloat(cl, "alpha", finalAlphaValue).setDuration(duration),
                            ObjectAnimator.ofFloat(cl, "rotationY", rotation).setDuration(duration));
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
            if (animated) {
                // If we call this when we're not animated, onAnimationEnd is never called on
                // the listener; make sure we only use the listener when we're actually animating
                mAnimator.addListener(mUnshrinkAnimationListener);
                mAnimator.start();
            }
        }
    }

    /**
     * Draw the View v into the given Canvas.
     *
     * @param v the view to draw
     * @param destCanvas the canvas to draw on
     * @param padding the horizontal and vertical padding to use when drawing
     */
    private void drawDragView(View v, Canvas destCanvas, int padding) {
        final Rect clipRect = mTempRect;
        v.getDrawingRect(clipRect);

        // For a TextView, adjust the clip rect so that we don't include the text label
        if (v instanceof TextView) {
            final int iconHeight = ((TextView)v).getCompoundPaddingTop() - v.getPaddingTop();
            clipRect.bottom = clipRect.top + iconHeight;
        }

        // Draw the View into the bitmap.
        // The translate of scrollX and scrollY is necessary when drawing TextViews, because
        // they set scrollX and scrollY to large values to achieve centered text

        destCanvas.save();
        destCanvas.translate(-v.getScrollX() + padding / 2, -v.getScrollY() + padding / 2);
        destCanvas.clipRect(clipRect, Op.REPLACE);
        v.draw(destCanvas);
        destCanvas.restore();
    }

    /**
     * Returns a new bitmap to be used as the object outline, e.g. to visualize the drop location.
     * Responsibility for the bitmap is transferred to the caller.
     */
    private Bitmap createDragOutline(View v, Canvas canvas, int padding) {
        final int outlineColor = getResources().getColor(R.color.drag_outline_color);
        final Bitmap b = Bitmap.createBitmap(
                v.getWidth() + padding, v.getHeight() + padding, Bitmap.Config.ARGB_8888);

        canvas.setBitmap(b);
        drawDragView(v, canvas, padding);
        mOutlineHelper.applyExpensiveOuterOutline(b, canvas, outlineColor, true);

        return b;
    }

    /**
     * Creates a drag outline to represent a drop (that we don't have the actual information for
     * yet).  May be changed in the future to alter the drop outline slightly depending on the
     * clip description mime data.
     */
    private Bitmap createExternalDragOutline(Canvas canvas, int padding) {
        Resources r = getResources();
        final int outlineColor = r.getColor(R.color.drag_outline_color);
        final int iconWidth = r.getDimensionPixelSize(R.dimen.workspace_cell_width);
        final int iconHeight = r.getDimensionPixelSize(R.dimen.workspace_cell_height);
        final int rectRadius = r.getDimensionPixelSize(R.dimen.external_drop_icon_rect_radius);
        final int inset = (int) (Math.min(iconWidth, iconHeight) * 0.2f);
        final Bitmap b = Bitmap.createBitmap(
                iconWidth + padding, iconHeight + padding, Bitmap.Config.ARGB_8888);

        canvas.setBitmap(b);
        canvas.drawRoundRect(new RectF(inset, inset, iconWidth - inset, iconHeight - inset),
                rectRadius, rectRadius, mExternalDragOutlinePaint);
        mOutlineHelper.applyExpensiveOuterOutline(b, canvas, outlineColor, true);

        return b;
    }

    /**
     * Returns a new bitmap to show when the given View is being dragged around.
     * Responsibility for the bitmap is transferred to the caller.
     */
    private Bitmap createDragBitmap(View v, Canvas canvas, int padding) {
        final int outlineColor = getResources().getColor(R.color.drag_outline_color);
        final Bitmap b = Bitmap.createBitmap(
                mDragOutline.getWidth(), mDragOutline.getHeight(), Bitmap.Config.ARGB_8888);

        canvas.setBitmap(b);
        canvas.drawBitmap(mDragOutline, 0, 0, null);
        drawDragView(v, canvas, padding);
        mOutlineHelper.applyOuterBlur(b, canvas, outlineColor);

        return b;
    }

    void startDrag(CellLayout.CellInfo cellInfo) {
        View child = cellInfo.cell;

        // Make sure the drag was started by a long press as opposed to a long click.
        if (!child.isInTouchMode()) {
            return;
        }

        mDragInfo = cellInfo;
        mDragInfo.screen = mCurrentPage;

        CellLayout current = getCurrentDropLayout();

        current.onDragChild(child);
        child.setVisibility(View.GONE);

        child.clearFocus();
        child.setPressed(false);

        final Canvas canvas = new Canvas();

        // We need to add extra padding to the bitmap to make room for the glow effect
        final int bitmapPadding = HolographicOutlineHelper.OUTER_BLUR_RADIUS;

        // The outline is used to visualize where the item will land if dropped
        mDragOutline = createDragOutline(child, canvas, bitmapPadding);

        // The drag bitmap follows the touch point around on the screen
        final Bitmap b = createDragBitmap(child, canvas, bitmapPadding);

        final int bmpWidth = b.getWidth();
        final int bmpHeight = b.getHeight();
        child.getLocationOnScreen(mTempXY);
        final int screenX = (int) mTempXY[0] + (child.getWidth() - bmpWidth) / 2;
        final int screenY = (int) mTempXY[1] + (child.getHeight() - bmpHeight) / 2;
        mDragController.startDrag(b, screenX, screenY, 0, 0, bmpWidth, bmpHeight, this,
                child.getTag(), DragController.DRAG_ACTION_MOVE, null);
        b.recycle();
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

    private void setPositionForDropAnimation(
            View dragView, int dragViewX, int dragViewY, View parent, View child) {
        final CellLayout.LayoutParams lp = (CellLayout.LayoutParams) child.getLayoutParams();

        // Based on the position of the drag view, find the top left of the original view
        int viewX = dragViewX + (dragView.getWidth() - child.getWidth()) / 2;
        int viewY = dragViewY + (dragView.getHeight() - child.getHeight()) / 2;
        viewX -= getResources().getInteger(R.integer.config_dragViewOffsetX);
        viewY -= getResources().getInteger(R.integer.config_dragViewOffsetY);

        // Set its old pos (in the new parent's coordinates); it will be animated
        // in animateViewIntoPosition after the next layout pass
        lp.oldX = viewX - (parent.getLeft() - mScrollX);
        lp.oldY = viewY - (parent.getTop() - mScrollY);
    }

    public void animateViewIntoPosition(final View view) {
        final CellLayout parent = (CellLayout) view.getParent();
        final CellLayout.LayoutParams lp = (CellLayout.LayoutParams) view.getLayoutParams();

        // Convert the animation params to be relative to the Workspace, not the CellLayout
        final int fromX = lp.oldX + parent.getLeft();
        final int fromY = lp.oldY + parent.getTop();

        final int dx = lp.x - lp.oldX;
        final int dy = lp.y - lp.oldY;

        // Calculate the duration of the animation based on the object's distance
        final float dist = (float) Math.sqrt(dx*dx + dy*dy);
        final Resources res = getResources();
        final float maxDist = (float) res.getInteger(R.integer.config_dropAnimMaxDist);
        int duration = res.getInteger(R.integer.config_dropAnimMaxDuration);
        if (dist < maxDist) {
            duration *= mQuintEaseOutInterpolator.getInterpolation(dist / maxDist);
        }

        if (mDropAnim != null) {
            // This should really be end(), but that will not be called synchronously,
            // so instead we use LauncherAnimatorListenerAdapter.onAnimationEndOrCancel()
            // and call cancel() here.
            mDropAnim.cancel();
        }
        mDropAnim = new ValueAnimator();
        mDropAnim.setInterpolator(mQuintEaseOutInterpolator);

        // The view is invisible during the animation; we render it manually.
        mDropAnim.addListener(new LauncherAnimatorListenerAdapter() {
            public void onAnimationStart(Animator animation) {
                // Set this here so that we don't render it until the animation begins
                mDropView = view;
            }

            public void onAnimationEndOrCancel(Animator animation) {
                if (mDropView != null) {
                    mDropView.setVisibility(View.VISIBLE);
                    mDropView = null;
                }
            }
        });

        mDropAnim.setDuration(duration);
        mDropAnim.setFloatValues(0.0f, 1.0f);
        mDropAnim.removeAllUpdateListeners();
        mDropAnim.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                final float percent = (Float) animation.getAnimatedValue();
                // Invalidate the old position
                invalidate(mDropViewPos[0], mDropViewPos[1],
                        mDropViewPos[0] + view.getWidth(), mDropViewPos[1] + view.getHeight());

                mDropViewPos[0] = fromX + (int) (percent * dx + 0.5f);
                mDropViewPos[1] = fromY + (int) (percent * dy + 0.5f);
                invalidate(mDropViewPos[0], mDropViewPos[1],
                        mDropViewPos[0] + view.getWidth(), mDropViewPos[1] + view.getHeight());
            }
        });


        view.setVisibility(View.INVISIBLE);

        if (!mScroller.isFinished()) {
            mAnimOnPageEndMoving = mDropAnim;
        } else {
            mDropAnim.start();
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean acceptDrop(DragSource source, int x, int y,
            int xOffset, int yOffset, DragView dragView, Object dragInfo) {

        // If it's an external drop (e.g. from All Apps), check if it should be accepted
        if (source != this) {
            // Don't accept the drop if we're not over a screen at time of drop
            if (mDragTargetLayout == null) {
                return false;
            }

            final CellLayout.CellInfo dragCellInfo = mDragInfo;
            final int spanX = dragCellInfo == null ? 1 : dragCellInfo.spanX;
            final int spanY = dragCellInfo == null ? 1 : dragCellInfo.spanY;

            final View ignoreView = dragCellInfo == null ? null : dragCellInfo.cell;

            // Don't accept the drop if there's no room for the item
            if (!mDragTargetLayout.findCellForSpanIgnoring(null, spanX, spanY, ignoreView)) {
                mLauncher.showOutOfSpaceMessage();
                return false;
            }
        }
        return true;
    }

    public void onDrop(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {

        int originX = x - xOffset;
        int originY = y - yOffset;

        if (mIsSmall || mIsInUnshrinkAnimation) {
            // get originX and originY in the local coordinate system of the screen
            mTempOriginXY[0] = originX;
            mTempOriginXY[1] = originY;
            mapPointFromSelfToChild(mDragTargetLayout, mTempOriginXY);
            originX = (int)mTempOriginXY[0];
            originY = (int)mTempOriginXY[1];
        }

        if (source != this) {
            onDropExternal(originX, originY, dragInfo, mDragTargetLayout);
        } else if (mDragInfo != null) {
            final View cell = mDragInfo.cell;
            if (mDragTargetLayout != null) {
                // Move internally
                mTargetCell = findNearestVacantArea(originX, originY,
                        mDragInfo.spanX, mDragInfo.spanY, cell, mDragTargetLayout,
                        mTargetCell);

                if (mTargetCell == null) {
                    snapToPage(mDragInfo.screen);
                } else {
                    int screen = indexOfChild(mDragTargetLayout);
                    if (screen != mDragInfo.screen) {
                        // Reparent the view
                        ((CellLayout) getChildAt(mDragInfo.screen)).removeView(cell);
                        addInScreen(cell, screen, mTargetCell[0], mTargetCell[1],
                                mDragInfo.spanX, mDragInfo.spanY);
                    }

                    // update the item's position after drop
                    final ItemInfo info = (ItemInfo) cell.getTag();
                    CellLayout.LayoutParams lp = (CellLayout.LayoutParams) cell.getLayoutParams();
                    mDragTargetLayout.onMove(cell, mTargetCell[0], mTargetCell[1]);
                    lp.cellX = mTargetCell[0];
                    lp.cellY = mTargetCell[1];
                    cell.setId(LauncherModel.getCellLayoutChildId(-1, mDragInfo.screen,
                            mTargetCell[0], mTargetCell[1], mDragInfo.spanX, mDragInfo.spanY));

                    LauncherModel.moveItemInDatabase(mLauncher, info,
                            LauncherSettings.Favorites.CONTAINER_DESKTOP, screen,
                            lp.cellX, lp.cellY);
                }
            }

            final CellLayout parent = (CellLayout) cell.getParent();

            // Prepare it to be animated into its new position
            // This must be called after the view has been re-parented
            setPositionForDropAnimation(dragView, originX, originY, parent, cell);
            parent.onDropChild(cell);
        }
    }

    public void onDragEnter(DragSource source, int x, int y, int xOffset,
            int yOffset, DragView dragView, Object dragInfo) {
        mDragTargetLayout = null; // Reset the drag state

        if (!mIsSmall) {
            mDragTargetLayout = getCurrentDropLayout();
            mDragTargetLayout.onDragEnter();
            showOutlines();
            mInDragMode = true;
            CellLayout cl = (CellLayout) getChildAt(mCurrentPage);
            cl.setBackgroundAlphaMultiplier(1.0f);
        }
    }

    public DropTarget getDropTargetDelegate(DragSource source, int x, int y,
            int xOffset, int yOffset, DragView dragView, Object dragInfo) {

        if (mIsSmall || mIsInUnshrinkAnimation) {
            // If we're shrunken, don't let anyone drag on folders/etc that are on the mini-screens
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

    /**
     * Global drag and drop handler
     */
    @Override
    public boolean onDragEvent(DragEvent event) {
        final ClipDescription desc = event.getClipDescription();
        final CellLayout layout = (CellLayout) getChildAt(mCurrentPage);
        final int[] pos = new int[2];
        layout.getLocationOnScreen(pos);
        // We need to offset the drag coordinates to layout coordinate space
        final int x = (int) event.getX() - pos[0];
        final int y = (int) event.getY() - pos[1];

        switch (event.getAction()) {
        case DragEvent.ACTION_DRAG_STARTED:
            // Check if we have enough space on this screen to add a new shortcut
            if (!layout.findCellForSpan(pos, 1, 1)) {
                Toast.makeText(mContext, mContext.getString(R.string.out_of_space),
                        Toast.LENGTH_SHORT).show();
                return false;
            }

            // Create the drag outline
            // We need to add extra padding to the bitmap to make room for the glow effect
            final Canvas canvas = new Canvas();
            final int bitmapPadding = HolographicOutlineHelper.OUTER_BLUR_RADIUS;
            mDragOutline = createExternalDragOutline(canvas, bitmapPadding);

            // Show the current page outlines to indicate that we can accept this drop
            showOutlines();
            layout.setHover(true);
            layout.onDragEnter();
            layout.visualizeDropLocation(null, mDragOutline, x, y, 1, 1);

            return true;
        case DragEvent.ACTION_DRAG_LOCATION:
            // Visualize the drop location
            layout.visualizeDropLocation(null, mDragOutline, x, y, 1, 1);
            return true;
        case DragEvent.ACTION_DROP:
            // Check if we have enough space on this screen to add a new shortcut
            if (!layout.findCellForSpan(pos, 1, 1)) {
                Toast.makeText(mContext, mContext.getString(R.string.out_of_space),
                        Toast.LENGTH_SHORT).show();
                return false;
            }

            // Try and add any shortcuts
            int newDropCount = 0;
            final LauncherModel model = mLauncher.getModel();
            final ClipData data = event.getClipData();

            // We assume that the mime types are ordered in descending importance of
            // representation. So we enumerate the list of mime types and alert the
            // user if any widgets can handle the drop.  Only the most preferred
            // representation will be handled.
            pos[0] = x;
            pos[1] = y;
            final int mimeTypeCount = desc.getMimeTypeCount();
            for (int j = 0; j < mimeTypeCount; ++j) {
                final String mimeType = desc.getMimeType(j);

                if (mimeType.equals(InstallShortcutReceiver.SHORTCUT_MIMETYPE)) {
                    final Intent intent = data.getItem(j).getIntent();
                    Object info = model.infoFromShortcutIntent(mContext, intent, data.getIcon());
                    onDropExternal(x, y, info, layout);
                } else {
                    final List<WidgetMimeTypeHandlerData> widgets =
                        model.resolveWidgetsForMimeType(mContext, mimeType);
                    final int numWidgets = widgets.size();

                    if (numWidgets == 0) {
                        continue;
                    } else if (numWidgets == 1) {
                        // If there is only one item, then go ahead and add and configure
                        // that widget
                        final AppWidgetProviderInfo widgetInfo = widgets.get(0).widgetInfo;
                        final PendingAddWidgetInfo createInfo =
                                new PendingAddWidgetInfo(widgetInfo, mimeType, data);
                        mLauncher.addAppWidgetFromDrop(createInfo, mCurrentPage, pos);
                    } else if (numWidgets > 1) {
                        // Show the widget picker dialog if there is more than one widget
                        // that can handle this data type
                        final InstallWidgetReceiver.WidgetListAdapter adapter =
                            new InstallWidgetReceiver.WidgetListAdapter(mLauncher, mimeType,
                                    data, widgets, layout, mCurrentPage, pos);
                        final AlertDialog.Builder builder =
                            new AlertDialog.Builder(mContext);
                        builder.setAdapter(adapter, adapter);
                        builder.setCancelable(true);
                        builder.setTitle(mContext.getString(
                                R.string.external_drop_widget_pick_title));
                        builder.setIcon(R.drawable.ic_no_applications);
                        builder.show();
                    }
                }
                newDropCount++;
                break;
            }

            // Show error message if we couldn't accept any of the items
            if (newDropCount <= 0) {
                Toast.makeText(mContext, mContext.getString(R.string.external_drop_widget_error),
                        Toast.LENGTH_SHORT).show();
            }

            return true;
        case DragEvent.ACTION_DRAG_ENDED:
            // Hide the page outlines after the drop
            layout.setHover(false);
            layout.onDragExit();
            hideOutlines();
            return true;
        }
        return super.onDragEvent(event);
    }

    /*
    *
    * Convert the 2D coordinate xy from the parent View's coordinate space to this CellLayout's
    * coordinate space. The argument xy is modified with the return result.
    *
    */
   void mapPointFromSelfToChild(View v, float[] xy) {
       mapPointFromSelfToChild(v, xy, null);
   }

   /*
    *
    * Convert the 2D coordinate xy from the parent View's coordinate space to this CellLayout's
    * coordinate space. The argument xy is modified with the return result.
    *
    * if cachedInverseMatrix is not null, this method will just use that matrix instead of
    * computing it itself; we use this to avoid redundant matrix inversions in
    * findMatchingPageForDragOver
    *
    */
   void mapPointFromSelfToChild(View v, float[] xy, Matrix cachedInverseMatrix) {
       if (cachedInverseMatrix == null) {
           v.getMatrix().invert(mTempInverseMatrix);
           cachedInverseMatrix = mTempInverseMatrix;
       }
       xy[0] = xy[0] + mScrollX - v.getLeft();
       xy[1] = xy[1] + mScrollY - v.getTop();
       cachedInverseMatrix.mapPoints(xy);
   }

   /*
    *
    * Convert the 2D coordinate xy from this CellLayout's coordinate space to
    * the parent View's coordinate space. The argument xy is modified with the return result.
    *
    */
   void mapPointFromChildToSelf(View v, float[] xy) {
       v.getMatrix().mapPoints(xy);
       xy[0] -= (mScrollX - v.getLeft());
       xy[1] -= (mScrollY - v.getTop());
   }

    static private float squaredDistance(float[] point1, float[] point2) {
        float distanceX = point1[0] - point2[0];
        float distanceY = point2[1] - point2[1];
        return distanceX * distanceX + distanceY * distanceY;
    }

    /*
     *
     * Returns true if the passed CellLayout cl overlaps with dragView
     *
     */
    boolean overlaps(CellLayout cl, DragView dragView,
            int dragViewX, int dragViewY, Matrix cachedInverseMatrix) {
        // Transform the coordinates of the item being dragged to the CellLayout's coordinates
        final float[] draggedItemTopLeft = mTempDragCoordinates;
        draggedItemTopLeft[0] = dragViewX + dragView.getScaledDragRegionXOffset();
        draggedItemTopLeft[1] = dragViewY + dragView.getScaledDragRegionYOffset();
        final float[] draggedItemBottomRight = mTempDragBottomRightCoordinates;
        draggedItemBottomRight[0] = draggedItemTopLeft[0] + dragView.getScaledDragRegionWidth();
        draggedItemBottomRight[1] = draggedItemTopLeft[1] + dragView.getScaledDragRegionHeight();

        // Transform the dragged item's top left coordinates
        // to the CellLayout's local coordinates
        mapPointFromSelfToChild(cl, draggedItemTopLeft, cachedInverseMatrix);
        float overlapRegionLeft = Math.max(0f, draggedItemTopLeft[0]);
        float overlapRegionTop = Math.max(0f, draggedItemTopLeft[1]);

        if (overlapRegionLeft <= cl.getWidth() && overlapRegionTop >= 0) {
            // Transform the dragged item's bottom right coordinates
            // to the CellLayout's local coordinates
            mapPointFromSelfToChild(cl, draggedItemBottomRight, cachedInverseMatrix);
            float overlapRegionRight = Math.min(cl.getWidth(), draggedItemBottomRight[0]);
            float overlapRegionBottom = Math.min(cl.getHeight(), draggedItemBottomRight[1]);

            if (overlapRegionRight >= 0 && overlapRegionBottom <= cl.getHeight()) {
                float overlap = (overlapRegionRight - overlapRegionLeft) *
                         (overlapRegionBottom - overlapRegionTop);
                if (overlap > 0) {
                    return true;
                }
             }
        }
        return false;
    }

    /*
     *
     * This method returns the CellLayout that is currently being dragged to. In order to drag
     * to a CellLayout, either the touch point must be directly over the CellLayout, or as a second
     * strategy, we see if the dragView is overlapping any CellLayout and choose the closest one
     *
     * Return null if no CellLayout is currently being dragged over
     *
     */
    private CellLayout findMatchingPageForDragOver(
            DragView dragView, int originX, int originY, int offsetX, int offsetY) {
        // We loop through all the screens (ie CellLayouts) and see which ones overlap
        // with the item being dragged and then choose the one that's closest to the touch point
        final int screenCount = getChildCount();
        CellLayout bestMatchingScreen = null;
        float smallestDistSoFar = Float.MAX_VALUE;

        for (int i = 0; i < screenCount; i++) {
            CellLayout cl = (CellLayout)getChildAt(i);

            final float[] touchXy = mTempTouchCoordinates;
            touchXy[0] = originX + offsetX;
            touchXy[1] = originY + offsetY;

            // Transform the touch coordinates to the CellLayout's local coordinates
            // If the touch point is within the bounds of the cell layout, we can return immediately
            cl.getMatrix().invert(mTempInverseMatrix);
            mapPointFromSelfToChild(cl, touchXy, mTempInverseMatrix);

            if (touchXy[0] >= 0 && touchXy[0] <= cl.getWidth() &&
                    touchXy[1] >= 0 && touchXy[1] <= cl.getHeight()) {
                return cl;
            }

            if (overlaps(cl, dragView, originX, originY, mTempInverseMatrix)) {
                // Get the center of the cell layout in screen coordinates
                final float[] cellLayoutCenter = mTempCellLayoutCenterCoordinates;
                cellLayoutCenter[0] = cl.getWidth()/2;
                cellLayoutCenter[1] = cl.getHeight()/2;
                mapPointFromChildToSelf(cl, cellLayoutCenter);

                touchXy[0] = originX + offsetX;
                touchXy[1] = originY + offsetY;

                // Calculate the distance between the center of the CellLayout
                // and the touch point
                float dist = squaredDistance(touchXy, cellLayoutCenter);

                if (dist < smallestDistSoFar) {
                    smallestDistSoFar = dist;
                    bestMatchingScreen = cl;
                }
            }
        }
        return bestMatchingScreen;
    }

    public void onDragOver(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        // When touch is inside the scroll area, skip dragOver actions for the current screen
        if (!mInScrollArea) {
            CellLayout layout;
            int originX = x - xOffset;
            int originY = y - yOffset;
            if (mIsSmall || mIsInUnshrinkAnimation) {
                layout = findMatchingPageForDragOver(
                        dragView, originX, originY, xOffset, yOffset);

                if (layout != mDragTargetLayout) {
                    if (mDragTargetLayout != null) {
                        mDragTargetLayout.setHover(false);
                    }
                    mDragTargetLayout = layout;
                    if (mDragTargetLayout != null) {
                        mDragTargetLayout.setHover(true);
                    }
                }
            } else {
                layout = getCurrentDropLayout();

                final ItemInfo item = (ItemInfo)dragInfo;
                if (dragInfo instanceof LauncherAppWidgetInfo) {
                    LauncherAppWidgetInfo widgetInfo = (LauncherAppWidgetInfo)dragInfo;

                    if (widgetInfo.spanX == -1) {
                        // Calculate the grid spans needed to fit this widget
                        int[] spans = layout.rectToCell(
                                widgetInfo.minWidth, widgetInfo.minHeight, null);
                        item.spanX = spans[0];
                        item.spanY = spans[1];
                    }
                }

                if (source instanceof AllAppsPagedView) {
                    // This is a hack to fix the point used to determine which cell an icon from
                    // the all apps screen is over
                    if (item != null && item.spanX == 1 && layout != null) {
                        int dragRegionLeft = (dragView.getWidth() - layout.getCellWidth()) / 2;

                        originX += dragRegionLeft - dragView.getDragRegionLeft();
                        if (dragView.getDragRegionWidth() != layout.getCellWidth()) {
                            dragView.setDragRegion(dragView.getDragRegionLeft(),
                                    dragView.getDragRegionTop(),
                                    layout.getCellWidth(),
                                    dragView.getDragRegionHeight());
                        }
                    }
                }

                if (layout != mDragTargetLayout) {
                    if (mDragTargetLayout != null) {
                        mDragTargetLayout.onDragExit();
                    }
                    layout.onDragEnter();
                    mDragTargetLayout = layout;
                }

                // only visualize the drop locations for moving icons within the home screen on
                // tablet on phone, we also visualize icons dragged in from All Apps
                if ((!LauncherApplication.isScreenXLarge() || source == this)
                        && mDragTargetLayout != null) {
                    final View child = (mDragInfo == null) ? null : mDragInfo.cell;
                    int localOriginX = originX - (mDragTargetLayout.getLeft() - mScrollX);
                    int localOriginY = originY - (mDragTargetLayout.getTop() - mScrollY);
                    mDragTargetLayout.visualizeDropLocation(child, mDragOutline,
                            localOriginX, localOriginY, item.spanX, item.spanY);
                }
            }
        }
    }

    public void onDragExit(DragSource source, int x, int y, int xOffset,
            int yOffset, DragView dragView, Object dragInfo) {
        if (mDragTargetLayout != null) {
            mDragTargetLayout.onDragExit();
        }
        if (!mIsPageMoving) {
            hideOutlines();
            mInDragMode = false;
        }
        clearAllHovers();
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
            onDropExternal(-1, -1, dragInfo, cl, false);
            return true;
        }
        mLauncher.showOutOfSpaceMessage();
        return false;
    }

    // Drag from somewhere else
    // NOTE: This can also be called when we are outside of a drag event, when we want
    // to add an item to one of the workspace screens.
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
                    mLauncher.addAppWidgetFromDrop((PendingAddWidgetInfo) info, screen, touchXY);
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
            cellLayout.animateDrop();
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
                    cellLayout, (UserFolderInfo) info, mIconCache);
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
            cellLayout.animateDrop();
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
        // if we're currently small, use findMatchingPageForDragOver instead
        if (mIsSmall) return null;
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
        } else if (mDragInfo != null) {
            ((CellLayout) getChildAt(mDragInfo.screen)).onDropChild(mDragInfo.cell);
        }

        mDragOutline = null;
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
        if (!mIsSmall && !mIsInUnshrinkAnimation) {
            super.scrollLeft();
        }
    }

    @Override
    public void scrollRight() {
        if (!mIsSmall && !mIsInUnshrinkAnimation) {
            super.scrollRight();
        }
    }

    @Override
    public void onEnterScrollArea(int direction) {
        if (!mIsSmall && !mIsInUnshrinkAnimation) {
            mInScrollArea = true;
            final int screen = getCurrentPage() + ((direction == DragController.SCROLL_LEFT) ? -1 : 1);
            if (0 <= screen && screen < getChildCount()) {
                ((CellLayout) getChildAt(screen)).setHover(true);

                if (mDragTargetLayout != null) {
                    mDragTargetLayout.onDragExit();
                    mDragTargetLayout = null;
                }
            }
        }
    }

    private void clearAllHovers() {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            ((CellLayout) getChildAt(i)).setHover(false);
        }
    }

    @Override
    public void onExitScrollArea() {
        if (mInScrollArea) {
            mInScrollArea = false;
            clearAllHovers();
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
        if (mIsSmall || mIsInUnshrinkAnimation) {
            mLauncher.showWorkspace(animate, (CellLayout)getChildAt(mDefaultPage));
        } else if (animate) {
            snapToPage(mDefaultPage);
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
