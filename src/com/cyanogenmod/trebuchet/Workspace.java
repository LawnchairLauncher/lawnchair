/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copytight (C) 2011 The CyanogenMod Project
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

package com.cyanogenmod.trebuchet;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.Region.Op;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.IBinder;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ImageView;
import android.widget.TextView;

import com.cyanogenmod.trebuchet.FolderIcon.FolderRingAnimator;
import com.cyanogenmod.trebuchet.LauncherSettings.Favorites;
import com.cyanogenmod.trebuchet.preference.PreferencesProvider;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * The workspace is a wide area with a wallpaper and a finite number of pages.
 * Each page contains a number of icons, folders or widgets the user can
 * interact with. A workspace is meant to be used with a fixed width only.
 */
public class Workspace extends PagedView
        implements DropTarget, DragSource, DragScroller, View.OnTouchListener,
        DragController.DragListener, LauncherTransitionable, ViewGroup.OnHierarchyChangeListener {
    private static final String TAG = "Trebuchet.Workspace";

    // Y rotation to apply to the workspace screens
    private static final float WORKSPACE_ROTATION = 12.5f;
    private static final float WORKSPACE_OVERSCROLL_ROTATION = 24f;

    private static final int CHILDREN_OUTLINE_FADE_OUT_DELAY = 0;
    private static final int CHILDREN_OUTLINE_FADE_OUT_DURATION = 375;
    private static final int CHILDREN_OUTLINE_FADE_IN_DURATION = 100;

    private static final int BACKGROUND_FADE_OUT_DURATION = 350;
    private static final int ADJACENT_SCREEN_DROP_DURATION = 300;
    private static final int FLING_THRESHOLD_VELOCITY = 500;

    // Pivot point for rotate anim
    private float mRotatePivotPoint = -1;

    // These animators are used to fade the children's outlines
    private ObjectAnimator mChildrenOutlineFadeInAnimation;
    private ObjectAnimator mChildrenOutlineFadeOutAnimation;
    private float mChildrenOutlineAlpha = 0;

    // These properties refer to the background protection gradient used for AllApps and Customize
    private ValueAnimator mBackgroundFadeInAnimation;
    private ValueAnimator mBackgroundFadeOutAnimation;
    private Drawable mBackground;
    boolean mDrawBackground = true;
    private float mBackgroundAlpha = 0;

    private float mWallpaperScrollRatio = 1.0f;

    private final WallpaperManager mWallpaperManager;
    private boolean mWallpaperHack;
    private Bitmap mWallpaperBitmap;
    private float mWallpaperScroll;
    private int[] mWallpaperOffsets = new int[2];
    private Paint mPaint = new Paint();
    private IBinder mWindowToken;

    /**
     * CellInfo for the cell that is currently being dragged
     */
    private CellLayout.CellInfo mDragInfo;

    /**
     * Target drop area calculated during last acceptDrop call.
     */
    private int[] mTargetCell = new int[2];
    private int mDragOverX = -1;
    private int mDragOverY = -1;

    static Rect mLandscapeCellLayoutMetrics = null;
    static Rect mPortraitCellLayoutMetrics = null;

    /**
     * The CellLayout that is currently being dragged over
     */
    private CellLayout mDragTargetLayout = null;
    /**
     * The CellLayout that we will show as glowing
     */
    private CellLayout mDragOverlappingLayout = null;

    /**
     * The CellLayout which will be dropped to
     */
    private CellLayout mDropToLayout = null;

    private Launcher mLauncher;
    private IconCache mIconCache;
    private DragController mDragController;

    // These are temporary variables to prevent having to allocate a new object just to
    // return an (x, y) value from helper functions. Do NOT use them to maintain other state.
    private int[] mTempCell = new int[2];
    private int[] mTempEstimate = new int[2];
    private float[] mDragViewVisualCenter = new float[2];
    private float[] mTempCellLayoutCenterCoordinates = new float[2];
    private Matrix mTempInverseMatrix = new Matrix();

    private SpringLoadedDragController mSpringLoadedDragController;
    private float mSpringLoadedShrinkFactor;

    private static final int DEFAULT_CELL_COUNT_X = 4;
    private static final int DEFAULT_CELL_COUNT_Y = 4;

    // State variable that indicates whether the pages are small (ie when you're
    // in all apps or customize mode)

    enum State { NORMAL, SPRING_LOADED, SMALL }
    private State mState = State.NORMAL;
    private boolean mIsSwitchingState = false;

    boolean mAnimatingViewIntoPlace = false;
    boolean mIsDragOccuring = false;
    boolean mChildrenLayersEnabled = true;

    private boolean mIsLandscape;

    /** Is the user is dragging an item near the edge of a page? */
    private boolean mInScrollArea = false;

    private final HolographicOutlineHelper mOutlineHelper = new HolographicOutlineHelper();
    private Bitmap mDragOutline = null;
    private final Rect mTempRect = new Rect();
    private final int[] mTempXY = new int[2];
    private int[] mTempVisiblePagesRange = new int[2];
    private float mOverscrollFade = 0;
    private boolean mScrollTransformsDirty = false;
    private boolean mOverscrollTransformsDirty = false;
    public static final int DRAG_BITMAP_PADDING = 2;

    // Camera and Matrix used to determine the final position of a neighboring CellLayout
    private final Matrix mMatrix = new Matrix();
    private final Camera mCamera = new Camera();
    private final float mTempFloat2[] = new float[2];

    int mWallpaperWidth;
    int mWallpaperHeight;
    WallpaperOffsetInterpolator mWallpaperInterpolator;
    boolean mUpdateWallpaperOffsetImmediately = false;
    private Runnable mDelayedResizeRunnable;
    private Runnable mDelayedSnapToPageRunnable;
    private Point mDisplaySize = new Point();
    private boolean mIsStaticWallpaper;
    private int mWallpaperTravelWidth;
    private int mCameraDistance;

    // Variables relating to the creation of user folders by hovering shortcuts over shortcuts
    private static final int FOLDER_CREATION_TIMEOUT = 0;
    private static final int REORDER_TIMEOUT = 250;
    private final Alarm mFolderCreationAlarm = new Alarm();
    private final Alarm mReorderAlarm = new Alarm();
    private FolderRingAnimator mDragFolderRingAnimator = null;
    private FolderIcon mDragOverFolderIcon = null;
    private boolean mCreateUserFolderOnDrop = false;
    private boolean mAddToExistingFolderOnDrop = false;
    private DropTarget.DragEnforcer mDragEnforcer;
    private float mMaxDistanceForFolderCreation;

    // Variables relating to touch disambiguation (scrolling workspace vs. scrolling a widget)
    private float mXDown;
    private float mYDown;
    final static float START_DAMPING_TOUCH_SLOP_ANGLE = (float) Math.PI / 6;
    final static float MAX_SWIPE_ANGLE = (float) Math.PI / 3;
    final static float TOUCH_SLOP_DAMPING_FACTOR = 4;

    // Relating to the animation of items being dropped externally
    public static final int ANIMATE_INTO_POSITION_AND_DISAPPEAR = 0;
    public static final int ANIMATE_INTO_POSITION_AND_REMAIN = 1;
    public static final int ANIMATE_INTO_POSITION_AND_RESIZE = 2;
    public static final int COMPLETE_TWO_STAGE_WIDGET_DROP_ANIMATION = 3;
    public static final int CANCEL_TWO_STAGE_WIDGET_DROP_ANIMATION = 4;

    // Related to dragging, folder creation and reordering
    private static final int DRAG_MODE_NONE = 0;
    private static final int DRAG_MODE_CREATE_FOLDER = 1;
    private static final int DRAG_MODE_ADD_TO_FOLDER = 2;
    private static final int DRAG_MODE_REORDER = 3;
    private int mDragMode = DRAG_MODE_NONE;
    private int mLastReorderX = -1;
    private int mLastReorderY = -1;

    private SparseArray<Parcelable> mSavedStates;
    private final ArrayList<Integer> mRestoredPages = new ArrayList<Integer>();

    // These variables are used for storing the initial and final values during workspace animations
    private int mSavedScrollX;
    private float mSavedRotationY;
    private float mSavedTranslationX;
    private float mCurrentScaleX;
    private float mCurrentScaleY;
    private float mCurrentRotationY;
    private float mCurrentTranslationX;
    private float mCurrentTranslationY;
    private float[] mOldTranslationXs;
    private float[] mOldTranslationYs;
    private float[] mOldScaleXs;
    private float[] mOldScaleYs;
    private float[] mOldBackgroundAlphas;
    private float[] mOldAlphas;
    private float[] mOldRotations;
    private float[] mOldRotationYs;
    private float[] mNewTranslationXs;
    private float[] mNewTranslationYs;
    private float[] mNewScaleXs;
    private float[] mNewScaleYs;
    private float[] mNewBackgroundAlphas;
    private float[] mNewAlphas;
    private float[] mNewRotations;
    private float[] mNewRotationYs;
    private float mTransitionProgress;

    public enum TransitionEffect {
        Standard,
        Tablet,
        ZoomIn,
        ZoomOut,
        RotateUp,
        RotateDown,
        Spin,
        Flip,
        CubeIn,
        CubeOut,
        Stack,
        Accordian,
        CylinderIn,
        CylinderOut
    }
    private TransitionEffect mTransitionEffect = TransitionEffect.Standard;

    private final Runnable mBindPages = new Runnable() {
        @Override
        public void run() {
            mLauncher.getModel().bindRemainingSynchronousPages();
        }
    };
    // Preferences
    private int mNumberHomescreens;
    private int mDefaultHomescreen;
    private boolean mStretchScreens;
    private boolean mShowSearchBar;
    private boolean mResizeAnyWidget;
    private boolean mHideIconLabels;
    private boolean mScrollWallpaper;
    private int mWallpaperSize;
    private boolean mShowScrollingIndicator;
    private boolean mFadeScrollingIndicator;
    private int mScrollingIndicatorPosition;
    private boolean mShowDockDivider;
    private boolean mShowOutlines;

    private static final int SCROLLING_INDICATOR_DOCK = 0;
    private static final int SCROLLING_INDICATOR_TOP = 1;
    private static final int SCROLLING_INDICATOR_BOTTOM = 2;

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

        mDragEnforcer = new DropTarget.DragEnforcer(context);
        // With workspace, data is available straight from the get-go
        setDataIsReady();

        mLauncher = (Launcher) context;
        final Resources res = getResources();
        mHandleFadeInAdjacentScreens = true;
        mWallpaperManager = WallpaperManager.getInstance(context);

        mUsePagingTouchSlop = false;

        int cellCountX = DEFAULT_CELL_COUNT_X;
        int cellCountY = DEFAULT_CELL_COUNT_Y;

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.Workspace, defStyle, 0);

        if (LauncherApplication.isScreenLarge()) {
            int[] cellCount = getCellCountsForLarge(context);
            cellCountX = cellCount[0];
            cellCountY = cellCount[1];
        }

        mSpringLoadedShrinkFactor =
            res.getInteger(R.integer.config_workspaceSpringLoadShrinkPercentage) / 100.0f;
        mCameraDistance = res.getInteger(R.integer.config_cameraDistance);

        // if the value is manually specified, use that instead
        cellCountX = a.getInt(R.styleable.Workspace_cellCountX, cellCountX);
        cellCountY = a.getInt(R.styleable.Workspace_cellCountY, cellCountY);
        a.recycle();

        setOnHierarchyChangeListener(this);

        // if there is a value set it the preferences, use that instead
        if (!LauncherApplication.isScreenLarge()) {
            cellCountX = PreferencesProvider.Interface.Homescreen.getCellCountX(cellCountX);
            cellCountY = PreferencesProvider.Interface.Homescreen.getCellCountY(cellCountY);
        }

        LauncherModel.updateWorkspaceLayoutCells(cellCountX, cellCountY);
        setHapticFeedbackEnabled(false);

        // Preferences
        mNumberHomescreens = PreferencesProvider.Interface.Homescreen.getNumberHomescreens();
        mDefaultHomescreen = PreferencesProvider.Interface.Homescreen.getDefaultHomescreen(mNumberHomescreens / 2);
        if (mDefaultHomescreen >= mNumberHomescreens) {
            mDefaultHomescreen = mNumberHomescreens / 2;
        }

        mStretchScreens = PreferencesProvider.Interface.Homescreen.getStretchScreens();
        mShowSearchBar = PreferencesProvider.Interface.Homescreen.getShowSearchBar();
        mResizeAnyWidget = PreferencesProvider.Interface.Homescreen.getResizeAnyWidget();
        mHideIconLabels = PreferencesProvider.Interface.Homescreen.getHideIconLabels();
        mTransitionEffect = PreferencesProvider.Interface.Homescreen.Scrolling.getTransitionEffect(
                res.getString(R.string.config_workspaceDefaultTransitionEffect));
        mScrollWallpaper = PreferencesProvider.Interface.Homescreen.Scrolling.getScrollWallpaper();
        mWallpaperHack = PreferencesProvider.Interface.Homescreen.Scrolling.getWallpaperHack();
        mWallpaperSize = PreferencesProvider.Interface.Homescreen.Scrolling.getWallpaperSize();
        mShowOutlines = PreferencesProvider.Interface.Homescreen.Scrolling.getShowOutlines(
                res.getBoolean(R.bool.config_workspaceDefaultShowOutlines));
        mFadeInAdjacentScreens = PreferencesProvider.Interface.Homescreen.Scrolling.getFadeInAdjacentScreens(
                res.getBoolean(R.bool.config_workspaceDefualtFadeInAdjacentScreens));
        mShowScrollingIndicator = PreferencesProvider.Interface.Homescreen.Indicator.getShowScrollingIndicator();
        mFadeScrollingIndicator = PreferencesProvider.Interface.Homescreen.Indicator.getFadeScrollingIndicator();
        mScrollingIndicatorPosition = PreferencesProvider.Interface.Homescreen.Indicator.getScrollingIndicatorPosition();
        mShowDockDivider = PreferencesProvider.Interface.Dock.getShowDivider();

        initWorkspace();
        checkWallpaper();

        // Disable multitouch across the workspace/all apps/customize tray
        setMotionEventSplittingEnabled(true);

        // Unless otherwise specified this view is important for accessibility.
        if (getImportantForAccessibility() == View.IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
    }

    public static int[] getCellCountsForLarge(Context context) {
        int[] cellCount = new int[2];

        final Resources res = context.getResources();
        // Determine number of rows/columns dynamically
        // TODO: This code currently fails on tablets with an aspect ratio < 1.3.
        // Around that ratio we should make cells the same size in portrait and
        // landscape
        TypedArray actionBarSizeTypedArray =
            context.obtainStyledAttributes(new int[] { android.R.attr.actionBarSize });
        DisplayMetrics displayMetrics = res.getDisplayMetrics();
        final float actionBarHeight = actionBarSizeTypedArray.getDimension(0, 0f);
        final float systemBarHeight = res.getDimension(R.dimen.status_bar_height);
        final float smallestScreenDim = res.getConfiguration().smallestScreenWidthDp *
            displayMetrics.density;

        cellCount[0] = 1;
        while (CellLayout.widthInPortrait(res, cellCount[0] + 1) <= smallestScreenDim) {
            cellCount[0]++;
        }

        cellCount[1] = 1;
        while (actionBarHeight + CellLayout.heightInLandscape(res, cellCount[1] + 1)
                <= smallestScreenDim - systemBarHeight) {
            cellCount[1]++;
        }
        return cellCount;
    }

    // estimate the size of a widget with spans hSpan, vSpan. return MAX_VALUE for each
    // dimension if unsuccessful
    public int[] estimateItemSize(int hSpan, int vSpan,
            boolean springLoaded) {
        int[] size = new int[2];
        if (getChildCount() > 0) {
            CellLayout cl = (CellLayout) mLauncher.getWorkspace().getChildAt(0);
            Rect r = estimateItemPosition(cl, 0, 0, hSpan, vSpan);
            size[0] = r.width();
            size[1] = r.height();
            if (springLoaded) {
                size[0] *= mSpringLoadedShrinkFactor;
                size[1] *= mSpringLoadedShrinkFactor;
            }
            return size;
        } else {
            size[0] = Integer.MAX_VALUE;
            size[1] = Integer.MAX_VALUE;
            return size;
        }
    }
    public Rect estimateItemPosition(CellLayout cl, int hCell, int vCell, int hSpan, int vSpan) {
        Rect r = new Rect();
        cl.cellToRect(hCell, vCell, hSpan, vSpan, r);
        return r;
    }

    public void onDragStart(DragSource source, Object info, int dragAction) {
        mIsDragOccuring = true;
        updateChildrenLayersEnabled(false);
        mLauncher.lockScreenOrientation();
        setChildrenBackgroundAlphaMultipliers(1f);
        mLauncher.getHotseat().setChildrenOutlineAlpha(1f);
        // Prevent any Un/InstallShortcutReceivers from updating the db while we are dragging
        InstallShortcutReceiver.enableInstallQueue();
        UninstallShortcutReceiver.enableUninstallQueue();
    }

    public void onDragEnd() {
        mIsDragOccuring = false;
        updateChildrenLayersEnabled(false);
        mLauncher.unlockScreenOrientation(false);
        mLauncher.getHotseat().setChildrenOutlineAlpha(0f);

        // Re-enable any Un/InstallShortcutReceiver and now process any queued items
        InstallShortcutReceiver.disableAndFlushInstallQueue(getContext());
        UninstallShortcutReceiver.disableAndFlushUninstallQueue(getContext());
    }

    /**
     * Initializes various states for this workspace.
     */
    protected void initWorkspace() {
        Context context = getContext();
        mCurrentPage = mDefaultHomescreen;
        LauncherApplication app = (LauncherApplication)context.getApplicationContext();
        mIconCache = app.getIconCache();
        setWillNotDraw(false);
        setChildrenDrawnWithCacheEnabled(true);

        final Resources res = getResources();

        LayoutInflater inflater =
                (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        for (int i = 0; i < mNumberHomescreens; i++) {
            CellLayout screen = (CellLayout) inflater.inflate(R.layout.workspace_screen, null);
            if (mStretchScreens) {
                screen.setCellGaps(-1, -1);
            }
            addView(screen);
        }

        try {
            mBackground = res.getDrawable(R.drawable.apps_customize_bg);
        } catch (Resources.NotFoundException e) {
            // In this case, we will skip drawing background protection
        }

        if (!mShowSearchBar) {
            int paddingTop = 0;
            int paddingLeft = 0;
            if (mLauncher.getCurrentOrientation() == Configuration.ORIENTATION_PORTRAIT) {
                paddingTop = (int)res.getDimension(R.dimen.qsb_bar_hidden_inset);
                paddingLeft = getPaddingRight();
            }
            setPadding(paddingLeft, paddingTop, getPaddingRight(), getPaddingBottom());
        }

        if (!mShowScrollingIndicator) {
            disableScrollingIndicator();
        }

        mWallpaperInterpolator = new WallpaperOffsetInterpolator();
        Display display = mLauncher.getWindowManager().getDefaultDisplay();
        display.getSize(mDisplaySize);
        if (mScrollWallpaper) {
            mWallpaperTravelWidth = (int) (mDisplaySize.x *
                    wallpaperTravelToScreenWidthRatio(mDisplaySize.x, mDisplaySize.y));
        }

        mMaxDistanceForFolderCreation = (0.55f * res.getDimensionPixelSize(R.dimen.app_icon_size));
        mFlingThresholdVelocity = (int) (FLING_THRESHOLD_VELOCITY * mDensity);
    }

    protected void checkWallpaper() {
        if (mWallpaperHack) {
            if (mWallpaperBitmap != null) {
                mWallpaperBitmap = null;
            }
            if (mWallpaperManager.getWallpaperInfo() == null) {
                Drawable wallpaper = mWallpaperManager.getDrawable();
                if (wallpaper instanceof BitmapDrawable) {
                    mWallpaperBitmap = ((BitmapDrawable) wallpaper).getBitmap();
                }
            }
        }
        mLauncher.setWallpaperVisibility(mWallpaperBitmap == null);

        // Make sure wallpaper gets redrawn to avoid ghost wallpapers
        invalidate();
    }

    @Override
    public void onChildViewAdded(View parent, View child) {
        if (!(child instanceof CellLayout)) {
            throw new IllegalArgumentException("A Workspace can only have CellLayout children.");
        }
        CellLayout cl = ((CellLayout) child);
        cl.setOnInterceptTouchListener(this);
        cl.setClickable(true);
        cl.setContentDescription(getContext().getString(
                R.string.workspace_description_format, getChildCount()));
    }

    @Override
    public void onChildViewRemoved(View parent, View child) {
    }

    protected boolean shouldDrawChild(View child) {
        final CellLayout cl = (CellLayout) child;
        return super.shouldDrawChild(child) &&
            (cl.getShortcutsAndWidgets().getAlpha() > 0 ||
             cl.getBackgroundAlpha() > 0);
    }

    /**
     * @return The open folder on the current screen, or null if there is none
     */
    Folder getOpenFolder() {
        DragLayer dragLayer = mLauncher.getDragLayer();
        int count = dragLayer.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = dragLayer.getChildAt(i);
            if (child instanceof Folder) {
                Folder folder = (Folder) child;
                if (folder.getInfo().opened)
                    return folder;
            }
        }
        return null;
    }

    boolean isTouchActive() {
        return mTouchState != TOUCH_STATE_REST;
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
    void addInScreen(View child, long container, int screen, int x, int y, int spanX, int spanY) {
        addInScreen(child, container, screen, x, y, spanX, spanY, false);
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
    void addInScreen(View child, long container, int screen, int x, int y, int spanX, int spanY,
            boolean insert) {
        if (container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
            if (screen < 0 || screen >= getChildCount()) {
                Log.e(TAG, "The screen must be >= 0 and < " + getChildCount()
                    + " (was " + screen + "); skipping child");
                return;
            }
        } else if (container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            if (screen < 0 || screen >= mLauncher.getHotseat().getChildCount()) {
                Log.e(TAG, "The screen must be >= 0 and < " + getChildCount()
                        + " (was " + screen + "); skipping child");
                return;
            }
        }

        final CellLayout layout;
        if (container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            // Note: We do this to ensure that the hotseat is always laid out in the orientation
            // of the hotseat in order regardless of which orientation they were added
            y = mLauncher.getHotseat().getCellYFromOrder(x);
            x = mLauncher.getHotseat().getCellXFromOrder(x);
            screen = mLauncher.getHotseat().getScreenFromOrder(screen);
            layout = (CellLayout) mLauncher.getHotseat().getPageAt(screen);
            child.setOnKeyListener(null);

            // Hide titles in the hotseat
            if (child instanceof FolderIcon) {
                ((FolderIcon) child).setTextVisible(false);
            } else if (child instanceof BubbleTextView) {
                ((BubbleTextView) child).setTextVisible(false);
            }
        } else {
            if (!mHideIconLabels) {
                // Show titles if not in the hotseat
                if (child instanceof FolderIcon) {
                    ((FolderIcon) child).setTextVisible(true);
                } else if (child instanceof BubbleTextView) {
                    ((BubbleTextView) child).setTextVisible(true);
                }
            } else {
                if (child instanceof FolderIcon) {
                    ((FolderIcon) child).setTextVisible(false);
                } else if (child instanceof BubbleTextView) {
                    ((BubbleTextView) child).setTextVisible(false);
                }
            }

            layout = (CellLayout) getChildAt(screen);
            child.setOnKeyListener(new IconKeyEventListener());
        }

        LayoutParams genericLp = child.getLayoutParams();
        CellLayout.LayoutParams lp;
        if (genericLp == null || !(genericLp instanceof CellLayout.LayoutParams)) {
            lp = new CellLayout.LayoutParams(x, y, spanX, spanY);
        } else {
            lp = (CellLayout.LayoutParams) genericLp;
            lp.cellX = x;
            lp.cellY = y;
            lp.cellHSpan = spanX;
            lp.cellVSpan = spanY;
        }

        if (spanX < 0 && spanY < 0) {
            lp.isLockedToGrid = false;
        }

        // Get the canonical child id to uniquely represent this view in this screen
        int childId = LauncherModel.getCellLayoutChildId(container, screen, x, y);
        boolean markCellsAsOccupied = !(child instanceof Folder);
        if (!layout.addViewToCellLayout(child, insert ? 0 : -1, childId, lp, markCellsAsOccupied)) {
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

    /**
     * Check if the point (x, y) hits a given page.
     */
    private boolean hitsPage(int index, float x, float y) {
        final View page = getChildAt(index);
        if (page != null) {
            float[] localXY = { x, y };
            mapPointFromSelfToChild(page, localXY);
            return (localXY[0] >= 0 && localXY[0] < page.getWidth()
                    && localXY[1] >= 0 && localXY[1] < page.getHeight());
        }
        return false;
    }

    @Override
    protected boolean hitsPreviousPage(float x, float y) {
        // mNextPage is set to INVALID_PAGE whenever we are stationary.
        // Calculating "next page" this way ensures that you scroll to whatever page you tap on
        final int current = (mNextPage == INVALID_PAGE) ? mCurrentPage : mNextPage;

        // Only allow tap to next page on large devices, where there's significant margin outside
        // the active workspace
        return LauncherApplication.isScreenLarge() && hitsPage(current - 1, x, y);
    }

    @Override
    protected boolean hitsNextPage(float x, float y) {
        // mNextPage is set to INVALID_PAGE whenever we are stationary.
        // Calculating "next page" this way ensures that you scroll to whatever page you tap on
        final int current = (mNextPage == INVALID_PAGE) ? mCurrentPage : mNextPage;

        // Only allow tap to next page on large devices, where there's significant margin outside
        // the active workspace
        return LauncherApplication.isScreenLarge() && hitsPage(current + 1, x, y);
    }

    /**
     * Called directly from a CellLayout (not by the framework), after we've been added as a
     * listener via setOnInterceptTouchEventListener(). This allows us to tell the CellLayout
     * that it should intercept touch events, which is not something that is normally supported.
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return (isSmall() || !isFinishedSwitchingState());
    }

    public boolean isSwitchingState() {
        return mIsSwitchingState;
    }

    /** This differs from isSwitchingState in that we take into account how far the transition
     *  has completed. */
    public boolean isFinishedSwitchingState() {
        return !mIsSwitchingState || (mTransitionProgress > 0.5f);
    }

    protected void onWindowVisibilityChanged (int visibility) {
        mLauncher.onWindowVisibilityChanged(visibility);
    }

    @Override
    public boolean dispatchUnhandledMove(View focused, int direction) {
        return !isSmall() && isFinishedSwitchingState() && super.dispatchUnhandledMove(focused, direction);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getAction() & MotionEvent.ACTION_MASK) {
        case MotionEvent.ACTION_DOWN:
            mXDown = ev.getX();
            mYDown = ev.getY();
            break;
        case MotionEvent.ACTION_POINTER_UP:
        case MotionEvent.ACTION_UP:
            if (mTouchState == TOUCH_STATE_REST) {
                final CellLayout currentPage = (CellLayout) getChildAt(mCurrentPage);
                if (!currentPage.lastDownOnOccupiedCell()) {
                    onWallpaperTap(ev);
                }
            }
        }
        return super.onInterceptTouchEvent(ev);
    }

    protected void reinflateWidgetsIfNecessary() {
        final int clCount = getChildCount();
        for (int i = 0; i < clCount; i++) {
            CellLayout cl = (CellLayout) getChildAt(i);
            ShortcutAndWidgetContainer swc = cl.getShortcutsAndWidgets();
            final int itemCount = swc.getChildCount();
            for (int j = 0; j < itemCount; j++) {
                View v = swc.getChildAt(j);

                if (v.getTag() instanceof LauncherAppWidgetInfo) {
                    LauncherAppWidgetInfo info = (LauncherAppWidgetInfo) v.getTag();
                    LauncherAppWidgetHostView lahv = (LauncherAppWidgetHostView) info.hostView;
                    if (lahv != null && lahv.orientationChangedSincedInflation()) {
                        mLauncher.removeAppWidget(info);
                        // Remove the current widget which is inflated with the wrong orientation
                        cl.removeView(lahv);
                        mLauncher.bindAppWidget(info);
                    }
                }
            }
        }
    }

    @Override
    protected void determineScrollingStart(MotionEvent ev) {
        if (isSmall()) return;
        if (!isFinishedSwitchingState()) return;

        float deltaX = Math.abs(ev.getX() - mXDown);
        float deltaY = Math.abs(ev.getY() - mYDown);

        if (Float.compare(deltaX, 0f) == 0) return;

        float slope = deltaY / deltaX;
        float theta = (float) Math.atan(slope);

        if (deltaX > mTouchSlop || deltaY > mTouchSlop) {
            cancelCurrentPageLongPress();
        }

        if (theta > MAX_SWIPE_ANGLE) {
            // Above MAX_SWIPE_ANGLE, we don't want to ever start scrolling the workspace
            return;
        } else if (theta > START_DAMPING_TOUCH_SLOP_ANGLE) {
            // Above START_DAMPING_TOUCH_SLOP_ANGLE and below MAX_SWIPE_ANGLE, we want to
            // increase the touch slop to make it harder to begin scrolling the workspace. This
            // results in vertically scrolling widgets to more easily. The higher the angle, the
            // more we increase touch slop.
            theta -= START_DAMPING_TOUCH_SLOP_ANGLE;
            float extraRatio = (float)
                    Math.sqrt((theta / (MAX_SWIPE_ANGLE - START_DAMPING_TOUCH_SLOP_ANGLE)));
            super.determineScrollingStart(ev, 1 + TOUCH_SLOP_DAMPING_FACTOR * extraRatio);
        } else {
            // Below START_DAMPING_TOUCH_SLOP_ANGLE, we don't do anything special
            super.determineScrollingStart(ev);
        }
    }

    protected void onPageBeginMoving() {
        super.onPageBeginMoving();

        if (isHardwareAccelerated()) {
            updateChildrenLayersEnabled(false);
        } else {
            if (mNextPage != INVALID_PAGE) {
                // we're snapping to a particular screen
                enableChildrenCache(mCurrentPage, mNextPage);
            } else {
                // this is when user is actively dragging a particular screen, they might
                // swipe it either left or right (but we won't advance by more than one screen)
                enableChildrenCache(mCurrentPage - 1, mCurrentPage + 1);
            }
        }

        if (mShowOutlines) {
            showOutlines();
        }
        mIsStaticWallpaper = mWallpaperManager.getWallpaperInfo() == null;

        // If we are not fading in adjacent screens, we still need to restore the alpha in case the
        // user scrolls while we are transitioning (should not affect dispatchDraw optimizations)
        if (!mFadeInAdjacentScreens) {
            for (int i = 0; i < getChildCount(); ++i) {
                ((CellLayout) getPageAt(i)).setShortcutAndWidgetAlpha(1f);
            }
        }

        // Show the scroll indicator as you pan the page
        showScrollingIndicator(false);

        if (mScrollWallpaper && mWallpaperHack && mWallpaperBitmap != null) {
            mLauncher.setWallpaperVisibility(false);
        }
    }

    protected void onPageEndMoving() {
        if (mFadeScrollingIndicator) {
            hideScrollingIndicator(false);
        }

        if (isHardwareAccelerated()) {
            updateChildrenLayersEnabled(false);
        } else {
            clearChildrenCache();
        }


        if (mDragController.isDragging()) {
            if (isSmall()) {
                // If we are in springloaded mode, then force an event to check if the current touch
                // is under a new page (to scroll to)
                mDragController.forceMoveEvent();
            }
        } else {
            // If we are not mid-dragging, hide the page outlines
            if (mShowOutlines) {
                hideOutlines();
            }

            // Hide the scroll indicator as you pan the page
            if (mFadeScrollingIndicator && !mDragController.isDragging()) {
                hideScrollingIndicator(false);
            }
        }

        if (mDelayedResizeRunnable != null) {
            mDelayedResizeRunnable.run();
            mDelayedResizeRunnable = null;
        }

        if (mDelayedSnapToPageRunnable != null) {
            mDelayedSnapToPageRunnable.run();
            mDelayedSnapToPageRunnable = null;
        }

        // Update wallpaper offsets to match hack (for recent apps window)
        if (mScrollWallpaper && mWallpaperHack && mWallpaperBitmap != null) {
            mLauncher.setWallpaperVisibility(true);
            mWallpaperManager.setWallpaperOffsetSteps(1.0f / (getChildCount() - 1), 1.0f);
            mWallpaperManager.setWallpaperOffsets(mWindowToken, mWallpaperScroll, 0);
        }
    }

    @Override
    protected int getScrollingIndicatorId() {
        switch (mScrollingIndicatorPosition) {
            case SCROLLING_INDICATOR_TOP:
                return R.id.paged_view_indicator_top;
            case SCROLLING_INDICATOR_BOTTOM:
                return R.id.paged_view_indicator_bottom;
            case SCROLLING_INDICATOR_DOCK:
            default:
                return R.id.paged_view_indicator_dock;
        }
    }

    @Override
    protected void flashScrollingIndicator(boolean animated) {
        if (mFadeScrollingIndicator) {
            super.flashScrollingIndicator(animated);
        } else {
            showScrollingIndicator(true);
        }
    }

    // As a ratio of screen height, the total distance we want the parallax effect to span
    // horizontally
    private float wallpaperTravelToScreenWidthRatio(int width, int height) {
        float aspectRatio = width / (float) height;

        // At an aspect ratio of 16/10, the wallpaper parallax effect should span 1.5 * screen width
        // At an aspect ratio of 10/16, the wallpaper parallax effect should span 1.2 * screen width
        // We will use these two data points to extrapolate how much the wallpaper parallax effect
        // to span (ie travel) at any aspect ratio:

        final float ASPECT_RATIO_LANDSCAPE = 16/10f;
        final float ASPECT_RATIO_PORTRAIT = 10/16f;
        final float WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE = 1.5f;
        final float WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT = 1.2f;

        // To find out the desired width at different aspect ratios, we use the following two
        // formulas, where the coefficient on x is the aspect ratio (width/height):
        //   (16/10)x + y = 1.5
        //   (10/16)x + y = 1.2
        // We solve for x and y and end up with a final formula:
        final float x =
            (WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE - WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT) /
            (ASPECT_RATIO_LANDSCAPE - ASPECT_RATIO_PORTRAIT);
        final float y = WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT - x * ASPECT_RATIO_PORTRAIT;
        return x * aspectRatio + y;
    }

    // The range of scroll values for Workspace
    private int getScrollRange() {
        return getChildOffset(getChildCount() - 1) - getChildOffset(0);
    }

    protected void setWallpaperDimension() {
        Point minDims = new Point();
        Point maxDims = new Point();
        mLauncher.getWindowManager().getDefaultDisplay().getCurrentSizeRange(minDims, maxDims);

        final int maxDim = Math.max(maxDims.x, maxDims.y);
        final int minDim = Math.min(minDims.x, minDims.y);

        // We need to ensure that there is enough extra space in the wallpaper for the intended
        // parallax effects
        if (LauncherApplication.isScreenLarge()) {
            mWallpaperWidth = (int) (maxDim * wallpaperTravelToScreenWidthRatio(maxDim, minDim));
            mWallpaperHeight = maxDim;
        } else {
            int screens = mWallpaperSize;
            mWallpaperWidth = Math.max(minDim * screens, maxDim);
            mWallpaperHeight = maxDim;
        }
        new Thread("setWallpaperDimension") {
            public void run() {
                mWallpaperManager.suggestDesiredDimensions(mWallpaperWidth, mWallpaperHeight);
                post(new Runnable() {
                    public void run() {
                        checkWallpaper();
                    }
                });
            }
        }.start();
    }

    private float wallpaperOffsetForCurrentScroll() {
        // Set wallpaper offset steps (1 / (number of screens - 1))
        mWallpaperManager.setWallpaperOffsetSteps(1.0f / (getChildCount() - 1), 1.0f);

        // For the purposes of computing the scrollRange and overScrollOffset, we assume
        // that mLayoutScale is 1. This means that when we're in spring-loaded mode,
        // there's no discrepancy between the wallpaper offset for a given page.
        float layoutScale = mLayoutScale;
        mLayoutScale = 1f;
        int scrollRange = getScrollRange();

        // Again, we adjust the wallpaper offset to be consistent between values of mLayoutScale
        float adjustedScrollX = Math.max(0, Math.min(getScrollX(), mMaxScrollX));
        adjustedScrollX *= mWallpaperScrollRatio;
        mLayoutScale = layoutScale;

        float scrollProgress =
            adjustedScrollX / (float) scrollRange;

        if (LauncherApplication.isScreenLarge() && mIsStaticWallpaper) {
            // The wallpaper travel width is how far, from left to right, the wallpaper will move
            // at this orientation. On tablets in portrait mode we don't move all the way to the
            // edges of the wallpaper, or otherwise the parallax effect would be too strong.
            int wallpaperTravelWidth = Math.min(mWallpaperTravelWidth, mWallpaperWidth);

            float offsetInDips = wallpaperTravelWidth * scrollProgress +
                (mWallpaperWidth - wallpaperTravelWidth) / 2; // center it
            float offset = offsetInDips / (float) mWallpaperWidth;
            return offset;
        } else {
            return scrollProgress;
        }
    }

    private void syncWallpaperOffsetWithScroll() {
        final boolean enableWallpaperEffects = isHardwareAccelerated();
        if (enableWallpaperEffects) {
            mWallpaperInterpolator.setFinalX(wallpaperOffsetForCurrentScroll());
        }
    }

    private void centerWallpaperOffset() {
        mWallpaperScroll = 0.5f;
        if (mWindowToken != null) {
            mWallpaperManager.setWallpaperOffsets(mWindowToken, 0.5f, 0);
        }
    }

    public void updateWallpaperOffsetImmediately() {
        mUpdateWallpaperOffsetImmediately = true;
    }

    private void updateWallpaperOffsets() {
        boolean updateNow = false;
        boolean keepUpdating = true;
        if (mUpdateWallpaperOffsetImmediately) {
            updateNow = true;
            keepUpdating = false;
            mWallpaperInterpolator.jumpToFinal();
            mUpdateWallpaperOffsetImmediately = false;
        } else {
            updateNow = keepUpdating = mWallpaperInterpolator.computeScrollOffset();
        }
        if (updateNow) {
            mWallpaperScroll = mWallpaperInterpolator.getCurrX();
            if (!mWallpaperHack && mWindowToken != null) {
                mWallpaperManager.setWallpaperOffsets(mWindowToken, mWallpaperScroll, 0);
            }
        }
        if (keepUpdating) {
            invalidate();
        }
    }

    @Override
    protected void updateCurrentPageScroll() {
        super.updateCurrentPageScroll();
        if (mScrollWallpaper) {
            computeWallpaperScrollRatio(mCurrentPage);
        }
    }

    @Override
    protected void snapToPage(int whichPage) {
        super.snapToPage(whichPage);
        if (mScrollWallpaper) {
            computeWallpaperScrollRatio(whichPage);
        }
    }

    @Override
    protected void snapToPage(int whichPage, int duration) {
        super.snapToPage(whichPage, duration);
        computeWallpaperScrollRatio(whichPage);
    }

    protected void snapToPage(int whichPage, Runnable r) {
        if (mDelayedSnapToPageRunnable != null) {
            mDelayedSnapToPageRunnable.run();
        }
        mDelayedSnapToPageRunnable = r;
        snapToPage(whichPage, SLOW_PAGE_SNAP_ANIMATION_DURATION);
    }

    private void computeWallpaperScrollRatio(int page) {
        // Here, we determine what the desired scroll would be with and without a layout scale,
        // and compute a ratio between the two. This allows us to adjust the wallpaper offset
        // as though there is no layout scale.
        float layoutScale = mLayoutScale;
        int scaled = getChildOffset(page) - getRelativeChildOffset(page);
        mLayoutScale = 1.0f;
        float unscaled = getChildOffset(page) - getRelativeChildOffset(page);
        mLayoutScale = layoutScale;
        if (scaled > 0) {
            mWallpaperScrollRatio = (1.0f * unscaled) / scaled;
        } else {
            mWallpaperScrollRatio = 1f;
        }
    }

    @Override
    protected Interpolator getScrollInterpolator() {
        return new PagedView.QuadInterpolator();
    }

    class WallpaperOffsetInterpolator {
        float mFinalHorizontalWallpaperOffset = 0.0f;
        float mHorizontalWallpaperOffset = 0.0f;
        long mLastWallpaperOffsetUpdateTime;
        boolean mIsMovingFast;
        boolean mOverrideHorizontalCatchupConstant;
        float mHorizontalCatchupConstant = 0.35f;

        public WallpaperOffsetInterpolator() {
        }

        public void setOverrideHorizontalCatchupConstant(boolean override) {
            mOverrideHorizontalCatchupConstant = override;
        }

        public void setHorizontalCatchupConstant(float f) {
            mHorizontalCatchupConstant = f;
        }

        public boolean computeScrollOffset() {
            if (Float.compare(mHorizontalWallpaperOffset, mFinalHorizontalWallpaperOffset) == 0) {
                mIsMovingFast = false;
                return false;
            }

            // Don't have any lag between workspace and wallpaper on non-large devices
            if (!LauncherApplication.isScreenLarge()) {
                mHorizontalWallpaperOffset = mFinalHorizontalWallpaperOffset;
                return true;
            }

            boolean isLandscape = mDisplaySize.x > mDisplaySize.y;

            long currentTime = System.currentTimeMillis();
            long timeSinceLastUpdate = currentTime - mLastWallpaperOffsetUpdateTime;
            timeSinceLastUpdate = Math.min((long) (1000/30f), timeSinceLastUpdate);
            timeSinceLastUpdate = Math.max(1L, timeSinceLastUpdate);

            float xdiff = Math.abs(mFinalHorizontalWallpaperOffset - mHorizontalWallpaperOffset);
            if (!mIsMovingFast && xdiff > 0.07) {
                mIsMovingFast = true;
            }

            float fractionToCatchUpIn1MsHorizontal;
            if (mOverrideHorizontalCatchupConstant) {
                fractionToCatchUpIn1MsHorizontal = mHorizontalCatchupConstant;
            } else if (mIsMovingFast) {
                fractionToCatchUpIn1MsHorizontal = isLandscape ? 0.5f : 0.75f;
            } else {
                // slow
                fractionToCatchUpIn1MsHorizontal = isLandscape ? 0.27f : 0.5f;
            }

            fractionToCatchUpIn1MsHorizontal /= 33f;

            final float UPDATE_THRESHOLD = 0.00001f;
            float hOffsetDelta = mFinalHorizontalWallpaperOffset - mHorizontalWallpaperOffset;
            boolean jumpToFinalValue = Math.abs(hOffsetDelta) < UPDATE_THRESHOLD;

            if (!LauncherApplication.isScreenLarge() || jumpToFinalValue) {
                mHorizontalWallpaperOffset = mFinalHorizontalWallpaperOffset;
            } else {
                float percentToCatchUpHorizontal =
                    Math.min(1.0f, timeSinceLastUpdate * fractionToCatchUpIn1MsHorizontal);
                mHorizontalWallpaperOffset += percentToCatchUpHorizontal * hOffsetDelta;
            }

            mLastWallpaperOffsetUpdateTime = System.currentTimeMillis();
            return true;
        }

        public float getCurrX() {
            return mHorizontalWallpaperOffset;
        }

        public float getFinalX() {
            return mFinalHorizontalWallpaperOffset;
        }

        public void setFinalX(float x) {
            mFinalHorizontalWallpaperOffset = Math.max(0f, Math.min(x, 1.0f));
        }

        public void jumpToFinal() {
            mHorizontalWallpaperOffset = mFinalHorizontalWallpaperOffset;
        }
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (mScrollWallpaper) {
            syncWallpaperOffsetWithScroll();
        }
    }

    void showOutlines() {
        if (!mIsSwitchingState) {
            if (mChildrenOutlineFadeOutAnimation != null) mChildrenOutlineFadeOutAnimation.cancel();
            if (mChildrenOutlineFadeInAnimation != null) mChildrenOutlineFadeInAnimation.cancel();
            mChildrenOutlineFadeInAnimation = LauncherAnimUtils.ofFloat(this, "childrenOutlineAlpha", 1.0f);
            mChildrenOutlineFadeInAnimation.setDuration(CHILDREN_OUTLINE_FADE_IN_DURATION);
            mChildrenOutlineFadeInAnimation.start();
        }
    }

    void hideOutlines() {
        if (!mIsSwitchingState) {
            if (mChildrenOutlineFadeInAnimation != null) mChildrenOutlineFadeInAnimation.cancel();
            if (mChildrenOutlineFadeOutAnimation != null) mChildrenOutlineFadeOutAnimation.cancel();
            mChildrenOutlineFadeOutAnimation = LauncherAnimUtils.ofFloat(this, "childrenOutlineAlpha", 0.0f);
            mChildrenOutlineFadeOutAnimation.setDuration(CHILDREN_OUTLINE_FADE_OUT_DURATION);
            mChildrenOutlineFadeOutAnimation.setStartDelay(CHILDREN_OUTLINE_FADE_OUT_DELAY);
            mChildrenOutlineFadeOutAnimation.start();
        }
    }

    public void showOutlinesTemporarily() {
        if (!mIsPageMoving && !isTouchActive()) {
            snapToPage(mCurrentPage);
        }
    }

    public void setChildrenOutlineAlpha(float alpha) {
        mChildrenOutlineAlpha = alpha;
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout cl = (CellLayout) getChildAt(i);
            cl.setBackgroundAlpha(alpha);
        }
    }

    public float getChildrenOutlineAlpha() {
        return mChildrenOutlineAlpha;
    }

    void disableBackground() {
        mDrawBackground = false;
    }
    void enableBackground() {
        mDrawBackground = true;
    }

    private void animateBackgroundGradient(float finalAlpha, boolean animated) {
        if (mBackground == null) return;
        if (mBackgroundFadeInAnimation != null) {
            mBackgroundFadeInAnimation.cancel();
            mBackgroundFadeInAnimation = null;
        }
        if (mBackgroundFadeOutAnimation != null) {
            mBackgroundFadeOutAnimation.cancel();
            mBackgroundFadeOutAnimation = null;
        }
        float startAlpha = getBackgroundAlpha();
        if (finalAlpha != startAlpha) {
            if (animated) {
                mBackgroundFadeOutAnimation = LauncherAnimUtils.ofFloat(startAlpha, finalAlpha);
                mBackgroundFadeOutAnimation.addUpdateListener(new AnimatorUpdateListener() {
                    public void onAnimationUpdate(ValueAnimator animation) {
                        setBackgroundAlpha((Float) animation.getAnimatedValue());
                    }
                });
                mBackgroundFadeOutAnimation.setInterpolator(new DecelerateInterpolator(1.5f));
                mBackgroundFadeOutAnimation.setDuration(BACKGROUND_FADE_OUT_DURATION);
                mBackgroundFadeOutAnimation.start();
            } else {
                setBackgroundAlpha(finalAlpha);
            }
        }
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

    /**
     * Due to 3D transformations, if two CellLayouts are theoretically touching each other,
     * on the xy plane, when one is rotated along the y-axis, the gap between them is perceived
     * as being larger. This method computes what offset the rotated view should be translated
     * in order to minimize this perceived gap.
     * @param degrees Angle of the view
     * @param width Width of the view
     * @param height Height of the view
     * @return Offset to be used in a View.setTranslationX() call
     */
    protected float getOffsetXForRotation(float degrees, int width, int height) {
        mMatrix.reset();
        mCamera.save();
        mCamera.rotateY(Math.abs(degrees));
        mCamera.getMatrix(mMatrix);
        mCamera.restore();

        mMatrix.preTranslate(-width * 0.5f, -height * 0.5f);
        mMatrix.postTranslate(width * 0.5f, height * 0.5f);
        mTempFloat2[0] = width;
        mTempFloat2[1] = height;
        mMatrix.mapPoints(mTempFloat2);
        return (width - mTempFloat2[0]) * (degrees > 0.0f ? 1.0f : -1.0f);
    }

    float backgroundAlphaInterpolator(float r) {
        float pivotA = 0.1f;
        float pivotB = 0.4f;
        if (r < pivotA) {
            return 0;
        } else if (r > pivotB) {
            return 1.0f;
        } else {
            return (r - pivotA)/(pivotB - pivotA);
        }
    }

    private void setChildrenBackgroundAlphaMultipliers(float a) {
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout child = (CellLayout) getChildAt(i);
            child.setBackgroundAlphaMultiplier(a);
        }
    }

    private void screenScrolledStandard(int screenScroll) {
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout cl = (CellLayout) getPageAt(i);
            if (cl != null) {
                float scrollProgress = getScrollProgress(screenScroll, cl, i);
                if (mFadeInAdjacentScreens && !isSmall()) {
                    setCellLayoutFadeAdjacent(cl, scrollProgress);
                }
            }
        }
    }

    private void screenScrolledTablet(int screenScroll) {
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout cl = (CellLayout) getPageAt(i);
            if (cl != null) {
                float scrollProgress = getScrollProgress(screenScroll, cl, i);
                float rotation = WORKSPACE_ROTATION * scrollProgress;
                float translationX = getOffsetXForRotation(rotation, cl.getWidth(), cl.getHeight());

                cl.setTranslationX(translationX);
                cl.setRotationY(rotation);
                if (mFadeInAdjacentScreens && !isSmall()) {
                    setCellLayoutFadeAdjacent(cl, scrollProgress);
                }
            }
        }
        invalidate();
    }

    private void screenScrolledZoom(int screenScroll, boolean in) {
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout cl = (CellLayout) getPageAt(i);
            if (cl != null) {
                float scrollProgress = getScrollProgress(screenScroll, cl, i);
                float scale = 1.0f + (in ? -0.2f : 0.1f) * Math.abs(scrollProgress);

                // Extra translation to account for the increase in size
                if (!in) {
                    float translationX = cl.getMeasuredWidth() * 0.1f * -scrollProgress;
                    cl.setTranslationX(translationX);
                }

                cl.setScaleX(scale);
                cl.setScaleY(scale);
                if (mFadeInAdjacentScreens && !isSmall()) {
                    setCellLayoutFadeAdjacent(cl, scrollProgress);
                }
            }
        }
    }

    private void screenScrolledRotate(int screenScroll, boolean up) {
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout cl = (CellLayout) getPageAt(i);
            if (cl != null) {
                float scrollProgress = getScrollProgress(screenScroll, cl, i);
                float rotation =
                        (up ? WORKSPACE_ROTATION : -WORKSPACE_ROTATION) * scrollProgress;

                if (mRotatePivotPoint < 0) {
                    mRotatePivotPoint =
                            (cl.getMeasuredWidth() * 0.5f) /
                            (float) Math.tan(Math.toRadians((double) (WORKSPACE_ROTATION * 0.5f)));
                }

                cl.setPivotX(cl.getMeasuredWidth() * 0.5f);
                cl.setPivotY(cl.getMeasuredHeight() * 0.5f);

                float translationX = cl.getMeasuredWidth() * scrollProgress;
                float translationY = 0.0f;

                translationX += (up ? -1.0f : 1.0f) *
                        Math.sin(Math.toRadians((double) rotation)) * (mRotatePivotPoint + cl.getMeasuredHeight() * 0.5f);
                translationY += (up ? -1.0f : 1.0f) *
                        (1.0f - Math.cos(Math.toRadians((double) rotation))) * (mRotatePivotPoint + cl.getMeasuredHeight() * 0.5f);

                cl.setRotation(rotation);
                cl.setTranslationX(translationX);
                cl.setTranslationY(translationY);
                if (mFadeInAdjacentScreens && !isSmall()) {
                    setCellLayoutFadeAdjacent(cl, scrollProgress);
                }
            }
        }
    }

    private void screenScrolledCube(int screenScroll, boolean in) {
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout cl = (CellLayout) getPageAt(i);
            if (cl != null) {
                float scrollProgress = getScrollProgress(screenScroll, cl, i);
                float rotation = (in ? 90.0f : -90.0f) * scrollProgress;
                float scale = 1.0f - Math.abs(scrollProgress) * 0.2f;

                if (in) {
                    cl.setCameraDistance(mDensity * mCameraDistance);
                    cl.setPivotX(scrollProgress < 0 ? 0 : cl.getMeasuredWidth());
                } else {
                    cl.setScaleX(scale);
                    cl.setScaleY(scale);
                    cl.setPivotX((scrollProgress + 1) * cl.getMeasuredWidth() * 0.5f);
                }
                cl.setPivotY(cl.getMeasuredHeight() * 0.5f);
                cl.setRotationY(rotation);
                if (mFadeInAdjacentScreens && !isSmall()) {
                    setCellLayoutFadeAdjacent(cl, scrollProgress);
                }
            }
        }
    }

    private void screenScrolledStack(int screenScroll) {
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout cl = (CellLayout) getPageAt(i);
            if (cl != null) {
                float scrollProgress = getScrollProgress(screenScroll, cl, i);
                float interpolatedProgress =
                        mZInterpolator.getInterpolation(Math.abs(Math.min(scrollProgress, 0)));
                float scale = (1 - interpolatedProgress) + interpolatedProgress * 0.76f;
                float translationX = Math.min(0, scrollProgress) * cl.getMeasuredWidth();
                float alpha;

                if (!LauncherApplication.isScreenLarge() || scrollProgress < 0) {
                    alpha = scrollProgress < 0 ? mAlphaInterpolator.getInterpolation(
                        1 - Math.abs(scrollProgress)) : 1.0f;
                } else {
                    // On large screens we need to fade the page as it nears its leftmost position
                    alpha = mLeftScreenAlphaInterpolator.getInterpolation(1 - scrollProgress);
                }

                cl.setTranslationX(translationX);
                cl.setScaleX(scale);
                cl.setScaleY(scale);
                cl.setAlpha(alpha);

                // If the view has 0 alpha, we set it to be invisible so as to prevent
                // it from accepting touches
                if (alpha <= 0) {
                    cl.setVisibility(INVISIBLE);
                } else if (cl.getVisibility() != VISIBLE) {
                    cl.setVisibility(VISIBLE);
                }
            }
        }
        invalidate();
    }

    private void screenScrolledAccordian(int screenScroll) {
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout cl = (CellLayout) getPageAt(i);
            if (cl != null) {
                float scrollProgress = getScrollProgress(screenScroll, cl, i);
                float scaleX = 1.0f - Math.abs(scrollProgress);

                cl.setPivotX(scrollProgress < 0 ? 0 : cl.getMeasuredWidth());
                cl.setScaleX(scaleX);
                if (scaleX == 0.0f) {
                    cl.setVisibility(INVISIBLE);
                } else if (cl.getVisibility() != VISIBLE) {
                    cl.setVisibility(VISIBLE);
                }
                if (mFadeInAdjacentScreens && !isSmall()) {
                    setCellLayoutFadeAdjacent(cl, scrollProgress);
                }
            }
        }
    }

    private void screenScrolledSpin(int screenScroll) {
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout cl = (CellLayout) getPageAt(i);
            if (cl != null) {
                float scrollProgress = getScrollProgress(screenScroll, cl, i);
                float rotation = 180.0f * scrollProgress;

                if (getMeasuredHeight() > getMeasuredWidth()) {
                    float translationX = (getMeasuredHeight() - getMeasuredWidth()) / 2.0f * -scrollProgress;
                    cl.setTranslationX(translationX);
                }

                cl.setRotation(rotation);

                if (mFadeInAdjacentScreens && !isSmall()) {
                    setCellLayoutFadeAdjacent(cl, scrollProgress);
                }
            }
        }
    }

    private void screenScrolledFlip(int screenScroll) {
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout cl = (CellLayout) getPageAt(i);
            if (cl != null) {
                float scrollProgress = getScrollProgress(screenScroll, cl, i);
                float rotation = -180.0f * scrollProgress;

                if (scrollProgress >= -0.5f && scrollProgress <= 0.5f) {
                    cl.setCameraDistance(mDensity * mCameraDistance);
                    cl.setTranslationX(cl.getMeasuredWidth() * scrollProgress);
                    cl.setPivotX(cl.getMeasuredWidth() * 0.5f);
                    cl.setPivotY(cl.getMeasuredHeight() * 0.5f);
                    cl.setRotationY(rotation);
                    if (cl.getVisibility() != VISIBLE) {
                        cl.setVisibility(VISIBLE);
                    }
                    if (mFadeInAdjacentScreens && !isSmall()) {
                        setCellLayoutFadeAdjacent(cl, scrollProgress);
                    }
                } else {
                    cl.setVisibility(INVISIBLE);
                }
            }
        }
        invalidate();
    }

    private void screenScrolledCylinder(int screenScroll, boolean in) {
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout cl = (CellLayout) getPageAt(i);
            if (cl != null) {
                float scrollProgress = getScrollProgress(screenScroll, cl, i);
                float rotation = (in ? WORKSPACE_ROTATION : -WORKSPACE_ROTATION) * scrollProgress;

                cl.setPivotX((scrollProgress + 1) * cl.getMeasuredWidth() * 0.5f);
                cl.setPivotY(cl.getMeasuredHeight() * 0.5f);
                cl.setRotationY(rotation);
                if (mFadeInAdjacentScreens && !isSmall()) {
                    setCellLayoutFadeAdjacent(cl, scrollProgress);
                }
            }
        }
    }

    @Override
    protected void screenScrolled(int screenScroll) {
        super.screenScrolled(screenScroll);
        enableHwLayersOnVisiblePages();

        if (isSwitchingState()) return;
        if (isSmall()) {
            for (int i = 0; i < getChildCount(); i++) {
                CellLayout cl = (CellLayout) getPageAt(i);
                if (cl != null) {
                    float scrollProgress = getScrollProgress(screenScroll, cl, i);
                    float rotation = WORKSPACE_ROTATION * scrollProgress;
                    cl.setRotationY(rotation);
                }
            }
        } else {
            boolean isInOverscroll = mOverScrollX < 0 || mOverScrollX > mMaxScrollX;

            if (isInOverscroll && !mOverscrollTransformsDirty) {
                mScrollTransformsDirty = true;
            }
            if (!isInOverscroll || mScrollTransformsDirty) {
                // Limit the "normal" effects to mScrollX
                int scroll = mScrollX;

                // Reset transforms when we aren't in overscroll
                if (mOverscrollFade != 0) {
                    setFadeForOverScroll(0);
                }
                if (mOverscrollTransformsDirty) {
                    mOverscrollTransformsDirty = false;
                    ((CellLayout) getChildAt(0)).resetOverscrollTransforms();
                    ((CellLayout) getChildAt(getChildCount() - 1)).resetOverscrollTransforms();
                }

                switch (mTransitionEffect) {
                    case Standard:
                        screenScrolledStandard(scroll);
                        break;
                    case Tablet:
                        screenScrolledTablet(scroll);
                        break;
                    case ZoomIn:
                        screenScrolledZoom(scroll, true);
                        break;
                    case ZoomOut:
                        screenScrolledZoom(scroll, false);
                        break;
                    case RotateUp:
                        screenScrolledRotate(scroll, true);
                        break;
                    case RotateDown:
                        screenScrolledRotate(scroll, false);
                        break;
                    case Spin:
                        screenScrolledSpin(scroll);
                        break;
                    case Flip:
                        screenScrolledFlip(scroll);
                        break;
                    case CubeIn:
                        screenScrolledCube(scroll, true);
                        break;
                    case CubeOut:
                        screenScrolledCube(scroll, false);
                        break;
                    case Stack:
                        screenScrolledStack(scroll);
                        break;
                    case Accordian:
                        screenScrolledAccordian(scroll);
                        break;
                    case CylinderIn:
                        screenScrolledCylinder(scroll, true);
                        break;
                    case CylinderOut:
                        screenScrolledCylinder(scroll, false);
                        break;
                }
                mScrollTransformsDirty = false;
            }

            if (isInOverscroll) {
                int index = mOverScrollX < 0 ? 0 : getChildCount() - 1;
                CellLayout cl = (CellLayout) getChildAt(index);
                if (getChildCount() > 1) {
                    float scrollProgress = getScrollProgress(screenScroll, cl, index);
                    cl.setOverScrollAmount(Math.abs(scrollProgress), index == 0);
                    float rotation = -WORKSPACE_OVERSCROLL_ROTATION * scrollProgress;
                    cl.setRotationY(rotation);
                    setFadeForOverScroll(Math.abs(scrollProgress));
                    if (!mOverscrollTransformsDirty) {
                        mOverscrollTransformsDirty = true;
                        cl.setCameraDistance(mDensity * mCameraDistance);
                        cl.setPivotY(cl.getMeasuredHeight() * 0.5f);
                        cl.setPivotX(cl.getMeasuredWidth() * (index == 0 ? 0.75f : 0.25f));
                        cl.setOverscrollTransformsDirty(true);
                    }
                }
            }
        }
    }

    private void setCellLayoutFadeAdjacent(CellLayout child, float scrollProgress) {
        float alpha = 1 - Math.abs(scrollProgress);
        child.getShortcutsAndWidgets().setAlpha(alpha);
        if (!mIsDragOccuring) {
           child.setBackgroundAlphaMultiplier(
           backgroundAlphaInterpolator(Math.abs(scrollProgress)));
        } else {
           child.setBackgroundAlphaMultiplier(1f);
        }
    }

    @Override
    protected void overScroll(float amount) {
        acceleratedOverScroll(amount);
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mWindowToken = getWindowToken();
        computeScroll();
        mDragController.setWindowToken(mWindowToken);
    }

    protected void onDetachedFromWindow() {
        mWindowToken = null;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (mFirstLayout && mCurrentPage >= 0 && mCurrentPage < getChildCount()) {
            mUpdateWallpaperOffsetImmediately = true;
        }
        super.onLayout(changed, left, top, right, bottom);
    }


    protected void onSizeChanged (int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        getLocationOnScreen(mWallpaperOffsets);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mScrollWallpaper) {
            updateWallpaperOffsets();
        }

        // Draw the wallpaper if necessary
        if (mWallpaperHack && mWallpaperBitmap != null) {
            float x = getScrollX();
            float y = getScrollY();

            int width = getWidth();
            int height = getHeight();
            int wallpaperWidth = mWallpaperBitmap.getWidth();
            int wallpaperHeight = mWallpaperBitmap.getHeight();

            if (width + mWallpaperOffsets[0] > wallpaperWidth) {
                // Wallpaper is smaller than screen
                x += (width - wallpaperWidth) / 2;
            } else {
                x -= mWallpaperScroll * (wallpaperWidth - (width + mWallpaperOffsets[0])) + mWallpaperOffsets[0];
            }
            if (height + mWallpaperOffsets[0] > wallpaperHeight) {
                // Wallpaper is smaller than screen
                y += (height - wallpaperHeight) / 2;
            } else {
                if (!mIsLandscape) {
                    y -= mWallpaperOffsets[1];
                } else {
                    y -= (wallpaperHeight - (height - mWallpaperOffsets[1])) / 2 - mWallpaperOffsets[1];
                }
            }

            canvas.drawBitmap(mWallpaperBitmap, x, y, mPaint);
        }

        // Draw the background gradient if necessary
        if (mBackground != null && mBackgroundAlpha > 0.0f && mDrawBackground) {
            int alpha = (int) (mBackgroundAlpha * 255);
            mBackground.setAlpha(alpha);
            mBackground.setBounds(getScrollX(), 0, getScrollX() + getMeasuredWidth(),
                    getMeasuredHeight());
            mBackground.draw(canvas);
        }

        super.onDraw(canvas);

        // Call back to LauncherModel to finish binding after the first draw
        post(mBindPages);
    }

    boolean isDrawingBackgroundGradient() {
        return (mBackground != null && mBackgroundAlpha > 0.0f && mDrawBackground);
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
    public int getDescendantFocusability() {
        if (isSmall()) {
            return ViewGroup.FOCUS_BLOCK_DESCENDANTS;
        }
        return super.getDescendantFocusability();
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

    public boolean isSmall() {
        return mState == State.SMALL || mState == State.SPRING_LOADED;
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
            // In software mode, we don't want the items to continue to be drawn into bitmaps
            if (!isHardwareAccelerated()) {
                layout.setChildrenDrawingCacheEnabled(false);
            }
        }
    }


    private void updateChildrenLayersEnabled(boolean force) {
        boolean small = mState == State.SMALL || mIsSwitchingState;
        boolean enableChildrenLayers = force || small || mAnimatingViewIntoPlace || isPageMoving();

        if (enableChildrenLayers != mChildrenLayersEnabled) {
            mChildrenLayersEnabled = enableChildrenLayers;
            if (mChildrenLayersEnabled) {
                enableHwLayersOnVisiblePages();
            } else {
                for (int i = 0; i < getPageCount(); i++) {
                    final CellLayout cl = (CellLayout) getChildAt(i);
                    cl.disableHardwareLayers();
                }
            }
        }
    }

    private void enableHwLayersOnVisiblePages() {
        if (mChildrenLayersEnabled) {
            final int screenCount = getChildCount();
            getVisiblePages(mTempVisiblePagesRange);
            int leftScreen = mTempVisiblePagesRange[0];
            int rightScreen = mTempVisiblePagesRange[1];
            if (leftScreen == rightScreen) {
                // make sure we're caching at least two pages always
                if (rightScreen < screenCount - 1) {
                    rightScreen++;
                } else if (leftScreen > 0) {
                    leftScreen--;
                }
            }
            for (int i = 0; i < screenCount; i++) {
                final CellLayout layout = (CellLayout) getChildAt(i);
                if (!(leftScreen <= i && i <= rightScreen && shouldDrawChild(layout))) {
                    layout.disableHardwareLayers();
                }
            }
            for (int i = 0; i < screenCount; i++) {
                final CellLayout layout = (CellLayout) getChildAt(i);
                if (leftScreen <= i && i <= rightScreen && shouldDrawChild(layout)) {
                    layout.enableHardwareLayers();
                }
            }
        }
    }

    public void buildPageHardwareLayers() {
        // force layers to be enabled just for the call to buildLayer
        updateChildrenLayersEnabled(true);
        if (getWindowToken() != null) {
            final int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                CellLayout cl = (CellLayout) getChildAt(i);
                cl.buildHardwareLayer();
            }
        }
        updateChildrenLayersEnabled(false);
    }

    protected void onWallpaperTap(MotionEvent ev) {
        final int[] position = mTempCell;
        getLocationOnScreen(position);

        int pointerIndex = ev.getActionIndex();
        position[0] += (int) ev.getX(pointerIndex);
        position[1] += (int) ev.getY(pointerIndex);

        mWallpaperManager.sendWallpaperCommand(getWindowToken(),
                ev.getAction() == MotionEvent.ACTION_UP
                        ? WallpaperManager.COMMAND_TAP : WallpaperManager.COMMAND_SECONDARY_TAP,
                position[0], position[1], 0, null);
    }

    /*
     * This interpolator emulates the rate at which the perceived scale of an object changes
     * as its distance from a camera increases. When this interpolator is applied to a scale
     * animation on a view, it evokes the sense that the object is shrinking due to moving away
     * from the camera.
     */
    static class ZInterpolator implements TimeInterpolator {
        private float focalLength;

        public ZInterpolator(float foc) {
            focalLength = foc;
        }

        public float getInterpolation(float input) {
            return (1.0f - focalLength / (focalLength + input)) /
                (1.0f - focalLength / (focalLength + 1.0f));
        }
    }

    /*
     * The exact reverse of ZInterpolator.
     */
    static class InverseZInterpolator implements TimeInterpolator {
        private ZInterpolator zInterpolator;
        public InverseZInterpolator(float foc) {
            zInterpolator = new ZInterpolator(foc);
        }
        public float getInterpolation(float input) {
            return 1 - zInterpolator.getInterpolation(1 - input);
        }
    }

    /*
     * ZInterpolator compounded with an ease-out.
     */
    static class ZoomOutInterpolator implements TimeInterpolator {
        private final DecelerateInterpolator decelerate = new DecelerateInterpolator(0.75f);
        private final ZInterpolator zInterpolator = new ZInterpolator(0.13f);

        public float getInterpolation(float input) {
            return decelerate.getInterpolation(zInterpolator.getInterpolation(input));
        }
    }

    /*
     * InvereZInterpolator compounded with an ease-out.
     */
    static class ZoomInInterpolator implements TimeInterpolator {
        private final InverseZInterpolator inverseZInterpolator = new InverseZInterpolator(0.35f);
        private final DecelerateInterpolator decelerate = new DecelerateInterpolator(3.0f);

        public float getInterpolation(float input) {
            return decelerate.getInterpolation(inverseZInterpolator.getInterpolation(input));
        }
    }

    private final ZoomInInterpolator mZoomInInterpolator = new ZoomInInterpolator();
    private final ZInterpolator mZInterpolator = new ZInterpolator(0.5f);
    private AccelerateInterpolator mAlphaInterpolator = new AccelerateInterpolator(0.9f);
    private DecelerateInterpolator mLeftScreenAlphaInterpolator = new DecelerateInterpolator(4);

    /**
     * We call these methods (onDragStartedWithItemSpans/onDragStartedWithSize) whenever we
     * start a drag in Launcher, regardless of whether the drag has ever entered the Workspace
     *
     * These methods mark the appropriate pages as accepting drops (which alters their visual
     * appearance).
     *
     */
    public void onDragStartedWithItem(View v) {
        final Canvas canvas = new Canvas();

        // The outline is used to visualize where the item will land if dropped
        mDragOutline = createDragOutline(v, canvas, DRAG_BITMAP_PADDING);
    }

    public void onDragStartedWithItem(PendingAddItemInfo info, Bitmap b, boolean clipAlpha) {
        final Canvas canvas = new Canvas();

        int[] size = estimateItemSize(info.spanX, info.spanY, false);

        // The outline is used to visualize where the item will land if dropped
        mDragOutline = createDragOutline(b, canvas, DRAG_BITMAP_PADDING, size[0],
                size[1], clipAlpha);
    }

    public void exitWidgetResizeMode() {
        DragLayer dragLayer = mLauncher.getDragLayer();
        dragLayer.clearAllResizeFrames();
    }

    private void initAnimationArrays() {
        final int childCount = getChildCount();
        if (mOldTranslationXs != null) return;
        mOldTranslationXs = new float[childCount];
        mOldTranslationYs = new float[childCount];
        mOldScaleXs = new float[childCount];
        mOldScaleYs = new float[childCount];
        mOldBackgroundAlphas = new float[childCount];
        mOldAlphas = new float[childCount];
        mOldRotations = new float[childCount];
        mOldRotationYs = new float[childCount];
        mNewTranslationXs = new float[childCount];
        mNewTranslationYs = new float[childCount];
        mNewScaleXs = new float[childCount];
        mNewScaleYs = new float[childCount];
        mNewBackgroundAlphas = new float[childCount];
        mNewAlphas = new float[childCount];
        mNewRotations = new float[childCount];
        mNewRotationYs = new float[childCount];
    }

    Animator getChangeStateAnimation(final State state, boolean animated) {
        return getChangeStateAnimation(state, animated, 0);
    }

    Animator getChangeStateAnimation(final State state, boolean animated, int delay) {
        if (mState == state) {
            return null;
        }

        // Initialize animation arrays for the first time if necessary
        initAnimationArrays();

        AnimatorSet anim = animated ? LauncherAnimUtils.createAnimatorSet() : null;

        // Stop any scrolling, move to the current page right away
        setCurrentPage(getNextPage());

        final State oldState = mState;
        final boolean oldStateIsNormal = (oldState == State.NORMAL);
        final boolean oldStateIsSpringLoaded = (oldState == State.SPRING_LOADED);
        final boolean oldStateIsSmall = (oldState == State.SMALL);
        mState = state;
        final boolean stateIsNormal = (state == State.NORMAL);
        final boolean stateIsSpringLoaded = (state == State.SPRING_LOADED);
        final boolean stateIsSmall = (state == State.SMALL);
        float finalScaleFactor = 1.0f;
        float finalBackgroundAlpha = stateIsSpringLoaded ? 1.0f : 0f;
        boolean zoomIn = true;

        if (state != State.NORMAL) {
            finalScaleFactor = mSpringLoadedShrinkFactor - (stateIsSmall ? 0.1f : 0);
            if (oldStateIsNormal && stateIsSmall) {
                zoomIn = false;
                setLayoutScale(finalScaleFactor);
                updateChildrenLayersEnabled(false);
            } else {
                finalBackgroundAlpha = 1.0f;
                setLayoutScale(finalScaleFactor);
            }
        } else {
            setLayoutScale(1.0f);
        }

        final int duration = zoomIn ?
                getResources().getInteger(R.integer.config_workspaceUnshrinkTime) :
                getResources().getInteger(R.integer.config_appsCustomizeWorkspaceShrinkTime);
        for (int i = 0; i < getChildCount(); i++) {
            final CellLayout cl = (CellLayout) getChildAt(i);
            float rotation = 0f;
            float rotationY = 0f;
            float translationX = 0f;
            float translationY = 0f;
            float scale = finalScaleFactor;
            float finalAlpha = (!mFadeInAdjacentScreens || stateIsSpringLoaded ||
                    (i == mCurrentPage)) ? 1f : 0f;
            float initialAlpha = cl.getShortcutsAndWidgets().getAlpha();

            // Tablet effect
            if (mTransitionEffect == TransitionEffect.Tablet || stateIsSmall || stateIsSpringLoaded) {
                translationX = getOffsetXForRotation(rotationY, cl.getMeasuredWidth(), cl.getMeasuredHeight());
                if (i < mCurrentPage) {
                    rotationY = WORKSPACE_ROTATION;
                } else if (i > mCurrentPage) {
                    rotationY = -WORKSPACE_ROTATION;
                }
            }

            // Zoom Effects
            if ((mTransitionEffect == TransitionEffect.ZoomIn ||
                    mTransitionEffect == TransitionEffect.ZoomOut) && stateIsNormal) {
                if (i != mCurrentPage) {
                    scale = (mTransitionEffect == TransitionEffect.ZoomIn ? 0.5f : 1.1f);
                }
            }

            // Stack Effect
            if (mTransitionEffect == TransitionEffect.Stack) {
                if (stateIsSpringLoaded) {
                    cl.setVisibility(VISIBLE);
                } else if (stateIsNormal) {
                    if (i <= mCurrentPage) {
                        cl.setVisibility(VISIBLE);
                    } else {
                        cl.setVisibility(INVISIBLE);
                    }
                }
            }


            // Flip Effect
            if (mTransitionEffect == TransitionEffect.Flip) {
                if (stateIsSpringLoaded) {
                    cl.setVisibility(VISIBLE);
                } else if (stateIsNormal) {
                    if (i == mCurrentPage) {
                        cl.setVisibility(VISIBLE);
                    } else {
                        cl.setVisibility(INVISIBLE);
                    }
                }
            }

            // Rotate Effects
            if ((mTransitionEffect == TransitionEffect.RotateUp ||
                    mTransitionEffect == TransitionEffect.RotateDown) && stateIsNormal) {
                boolean up = mTransitionEffect == TransitionEffect.RotateUp;
                rotation = (up ? WORKSPACE_ROTATION : -WORKSPACE_ROTATION) * Math.max(-1.0f, Math.min(1.0f , mCurrentPage - i));
                translationX = cl.getMeasuredWidth() * (Math.max(-1.0f, Math.min(1.0f, i - mCurrentPage))) +
                        (up ? -1.0f : 1.0f) * (float) Math.sin(Math.toRadians((double) rotation)) *
                        (mRotatePivotPoint + cl.getMeasuredHeight() * 0.5f);
                translationY += (up ? -1.0f : 1.0f) * (1.0f - Math.cos(Math.toRadians((double) rotation))) *
                        (mRotatePivotPoint + cl.getMeasuredHeight() * 0.5f);
            }

            // Cube Effects
            if ((mTransitionEffect == TransitionEffect.CubeIn || mTransitionEffect == TransitionEffect.CubeOut) && stateIsNormal) {
                if (i < mCurrentPage) {
                    rotationY = mTransitionEffect == TransitionEffect.CubeOut ? -90.0f : 90.0f;
                } else if (i > mCurrentPage) {
                    rotationY = mTransitionEffect == TransitionEffect.CubeOut ? 90.0f : -90.0f;
                }
            }

            // Cylinder Effects
            if ((mTransitionEffect == TransitionEffect.CylinderIn || mTransitionEffect == TransitionEffect.CylinderOut) && stateIsNormal) {
                if (i < mCurrentPage) {
                    rotationY = mTransitionEffect == TransitionEffect.CylinderOut ? -WORKSPACE_ROTATION : WORKSPACE_ROTATION;
                } else if (i > mCurrentPage) {
                    rotationY = mTransitionEffect == TransitionEffect.CylinderOut ? WORKSPACE_ROTATION : -WORKSPACE_ROTATION;
                }
            }

            // Accordian Effect
            if (mTransitionEffect == TransitionEffect.Accordian) {
                if (stateIsSpringLoaded) {
                    cl.setVisibility(VISIBLE);
                } else if (stateIsNormal) {
                    if (i == mCurrentPage) {
                        cl.setVisibility(VISIBLE);
                    } else {
                        cl.setVisibility(INVISIBLE);
                    }
                }
            }

            if (stateIsSmall || stateIsSpringLoaded) {
                cl.setCameraDistance(1280 * mDensity);
                cl.setPivotX(cl.getMeasuredWidth() * 0.5f);
                cl.setPivotY(cl.getMeasuredHeight() * 0.5f);
            }

            // Determine the pages alpha during the state transition
            if ((oldStateIsSmall && stateIsNormal) ||
                (oldStateIsNormal && stateIsSmall)) {
                // To/from workspace - only show the current page unless the transition is not
                //                     animated and the animation end callback below doesn't run;
                //                     or, if we're in spring-loaded mode
                if (i == mCurrentPage || !animated || oldStateIsSpringLoaded) {
                    finalAlpha = 1f;
                } else {
                    initialAlpha = 0f;
                    finalAlpha = 0f;
                }
            }

            mOldAlphas[i] = initialAlpha;
            mNewAlphas[i] = finalAlpha;
            if (animated) {
                mOldTranslationXs[i] = cl.getTranslationX();
                mOldTranslationYs[i] = cl.getTranslationY();
                mOldScaleXs[i] = cl.getScaleX();
                mOldScaleYs[i] = cl.getScaleY();
                mOldBackgroundAlphas[i] = cl.getBackgroundAlpha();
                mOldRotations[i] = cl.getRotation();
                mOldRotationYs[i] = cl.getRotationY();

                mNewTranslationXs[i] = translationX;
                mNewTranslationYs[i] = translationY;
                mNewScaleXs[i] = scale;
                mNewScaleYs[i] = scale;
                mNewBackgroundAlphas[i] = finalBackgroundAlpha;
                mNewRotations[i] = rotation;
                mNewRotationYs[i] = rotationY;
            } else {
                cl.setTranslationX(translationX);
                cl.setTranslationY(translationY);
                cl.setScaleX(finalScaleFactor);
                cl.setScaleY(finalScaleFactor);
                cl.setBackgroundAlpha(finalBackgroundAlpha);
                cl.setShortcutAndWidgetAlpha(finalAlpha);
                cl.setRotation(rotation);
                cl.setRotationY(rotationY);
            }
        }

        if (animated) {
            for (int index = 0; index < getChildCount(); index++) {
                final int i = index;
                final CellLayout cl = (CellLayout) getChildAt(i);
                float currentAlpha = cl.getShortcutsAndWidgets().getAlpha();
                if (mOldAlphas[i] == 0 && mNewAlphas[i] == 0) {
                    cl.setTranslationX(mNewTranslationXs[i]);
                    cl.setTranslationY(mNewTranslationYs[i]);
                    cl.setScaleX(mNewScaleXs[i]);
                    cl.setScaleY(mNewScaleYs[i]);
                    cl.setBackgroundAlpha(mNewBackgroundAlphas[i]);
                    cl.setShortcutAndWidgetAlpha(mNewAlphas[i]);
                    cl.setRotation(mNewRotations[i]);
                    cl.setRotationY(mNewRotationYs[i]);
                } else {
                    LauncherViewPropertyAnimator a = new LauncherViewPropertyAnimator(cl);
                    a.translationX(mNewTranslationXs[i])
                        .translationY(mNewTranslationYs[i])
                        .scaleX(mNewScaleXs[i])
                        .scaleY(mNewScaleYs[i])
                        .rotation(mNewRotations[i])
                        .rotationY(mNewRotationYs[i])
                        .setDuration(duration)
                        .setInterpolator(mZoomInInterpolator);
                    anim.play(a);

                    if (mOldAlphas[i] != mNewAlphas[i] || currentAlpha != mNewAlphas[i]) {
                        LauncherViewPropertyAnimator alphaAnim =
                            new LauncherViewPropertyAnimator(cl.getShortcutsAndWidgets());
                        alphaAnim.alpha(mNewAlphas[i])
                            .setDuration(duration)
                            .setInterpolator(mZoomInInterpolator);
                        anim.play(alphaAnim);
                    }
                    if (mOldBackgroundAlphas[i] != 0 ||
                        mNewBackgroundAlphas[i] != 0) {
                        ValueAnimator bgAnim = LauncherAnimUtils.ofFloat(0f, 1f).setDuration(duration);
                        bgAnim.setInterpolator(mZoomInInterpolator);
                        bgAnim.addUpdateListener(new LauncherAnimatorUpdateListener() {
                                public void onAnimationUpdate(float a, float b) {
                                    cl.setBackgroundAlpha(
                                            a * mOldBackgroundAlphas[i] +
                                            b * mNewBackgroundAlphas[i]);
                                }
                            });
                        anim.play(bgAnim);
                    }
                }
            }
            buildPageHardwareLayers();
            anim.setStartDelay(delay);
        }

        if (stateIsSpringLoaded) {
            // Right now we're covered by Apps Customize
            // Show the background gradient immediately, so the gradient will
            // be showing once AppsCustomize disappears
            animateBackgroundGradient(getResources().getInteger(
                    R.integer.config_appsCustomizeSpringLoadedBgAlpha) / 100f, false);
        } else {
            // Fade the background gradient away
            animateBackgroundGradient(0f, true);
        }
        return anim;
    }

    @Override
    public void onLauncherTransitionPrepare(Launcher l, boolean animated, boolean toWorkspace) {
        mIsSwitchingState = true;
        cancelScrollingIndicatorAnimations();
    }

    @Override
    public void onLauncherTransitionStart(Launcher l, boolean animated, boolean toWorkspace) {
    }

    @Override
    public void onLauncherTransitionStep(Launcher l, float t) {
        mTransitionProgress = t;
    }

    @Override
    public void onLauncherTransitionEnd(Launcher l, boolean animated, boolean toWorkspace) {
        mIsSwitchingState = false;
        mWallpaperInterpolator.setOverrideHorizontalCatchupConstant(false);
        updateChildrenLayersEnabled(false);
        // The code in getChangeStateAnimation to determine initialAlpha and finalAlpha will ensure
        // ensure that only the current page is visible during (and subsequently, after) the
        // transition animation.  If fade adjacent pages is disabled, then re-enable the page
        // visibility after the transition animation.
        if (!mFadeInAdjacentScreens) {
            for (int i = 0; i < getChildCount(); i++) {
                final CellLayout cl = (CellLayout) getChildAt(i);
                cl.setShortcutAndWidgetAlpha(1f);
            }
        }
    }

    @Override
    public View getContent() {
        return this;
    }

    /**
     * Draw the View v into the given Canvas.
     *
     * @param v the view to draw
     * @param destCanvas the canvas to draw on
     * @param padding the horizontal and vertical padding to use when drawing
     */
    private void drawDragView(View v, Canvas destCanvas, int padding, boolean pruneToDrawable) {
        final Rect clipRect = mTempRect;
        v.getDrawingRect(clipRect);

        boolean textVisible = false;

        destCanvas.save();
        if (v instanceof TextView && pruneToDrawable) {
            Drawable d = ((TextView) v).getCompoundDrawables()[1];
            clipRect.set(0, 0, d.getIntrinsicWidth() + padding, d.getIntrinsicHeight() + padding);
            destCanvas.translate(padding / 2, padding / 2);
            d.draw(destCanvas);
        } else {
            if (v instanceof FolderIcon) {
                if (!mHideIconLabels) {
                    // For FolderIcons the text can bleed into the icon area, and so we need to
                    // hide the text completely (which can't be achieved by clipping).
                    if (((FolderIcon) v).getTextVisible()) {
                        ((FolderIcon) v).setTextVisible(false);
                        textVisible = true;
                    }
                }
            } else if (v instanceof BubbleTextView) {
                final BubbleTextView tv = (BubbleTextView) v;
                clipRect.bottom = tv.getExtendedPaddingTop() - (int) BubbleTextView.PADDING_V +
                        tv.getLayout().getLineTop(0);
            } else if (v instanceof TextView) {
                final TextView tv = (TextView) v;
                clipRect.bottom = tv.getExtendedPaddingTop() - tv.getCompoundDrawablePadding() +
                        tv.getLayout().getLineTop(0);
            }
            destCanvas.translate(-v.getScrollX() + padding / 2, -v.getScrollY() + padding / 2);
            destCanvas.clipRect(clipRect, Op.REPLACE);
            v.draw(destCanvas);

            // Restore text visibility of FolderIcon if necessary
            if (!mHideIconLabels && textVisible) {
                ((FolderIcon) v).setTextVisible(true);
            }
        }
        destCanvas.restore();
    }

    /**
     * Returns a new bitmap to show when the given View is being dragged around.
     * Responsibility for the bitmap is transferred to the caller.
     */
    public Bitmap createDragBitmap(View v, Canvas canvas, int padding) {
        Bitmap b;

        if (v instanceof TextView) {
            Drawable d = ((TextView) v).getCompoundDrawables()[1];
            b = Bitmap.createBitmap(d.getIntrinsicWidth() + padding,
                    d.getIntrinsicHeight() + padding, Bitmap.Config.ARGB_8888);
        } else {
            b = Bitmap.createBitmap(
                    v.getWidth() + padding, v.getHeight() + padding, Bitmap.Config.ARGB_8888);
        }

        canvas.setBitmap(b);
        drawDragView(v, canvas, padding, true);
        canvas.setBitmap(null);

        return b;
    }

    /**
     * Returns a new bitmap to be used as the object outline, e.g. to visualize the drop location.
     * Responsibility for the bitmap is transferred to the caller.
     */
    private Bitmap createDragOutline(View v, Canvas canvas, int padding) {
        final int outlineColor = getResources().getColor(android.R.color.holo_blue_light);
        final Bitmap b = Bitmap.createBitmap(
                v.getWidth() + padding, v.getHeight() + padding, Bitmap.Config.ARGB_8888);

        canvas.setBitmap(b);
        drawDragView(v, canvas, padding, true);
        mOutlineHelper.applyMediumExpensiveOutlineWithBlur(b, canvas, outlineColor, outlineColor);
        canvas.setBitmap(null);
        return b;
    }

    /**
     * Returns a new bitmap to be used as the object outline, e.g. to visualize the drop location.
     * Responsibility for the bitmap is transferred to the caller.
     */
    private Bitmap createDragOutline(Bitmap orig, Canvas canvas, int padding, int w, int h,
            boolean clipAlpha) {
        final int outlineColor = getResources().getColor(android.R.color.holo_blue_light);
        final Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        canvas.setBitmap(b);

        Rect src = new Rect(0, 0, orig.getWidth(), orig.getHeight());
        float scaleFactor = Math.min((w - padding) / (float) orig.getWidth(),
                (h - padding) / (float) orig.getHeight());
        int scaledWidth = (int) (scaleFactor * orig.getWidth());
        int scaledHeight = (int) (scaleFactor * orig.getHeight());
        Rect dst = new Rect(0, 0, scaledWidth, scaledHeight);

        // center the image
        dst.offset((w - scaledWidth) / 2, (h - scaledHeight) / 2);

        canvas.drawBitmap(orig, src, dst, null);
        mOutlineHelper.applyMediumExpensiveOutlineWithBlur(b, canvas, outlineColor, outlineColor,
                clipAlpha);
        canvas.setBitmap(null);

        return b;
    }

    void startDrag(CellLayout.CellInfo cellInfo) {
        View child = cellInfo.cell;

        // Make sure the drag was started by a long press as opposed to a long click.
        if (!child.isInTouchMode()) {
            return;
        }

        mDragInfo = cellInfo;
        child.setVisibility(INVISIBLE);
        CellLayout layout = (CellLayout) child.getParent().getParent();
        layout.prepareChildForDrag(child);

        child.clearFocus();
        child.setPressed(false);

        final Canvas canvas = new Canvas();

        // The outline is used to visualize where the item will land if dropped
        mDragOutline = createDragOutline(child, canvas, DRAG_BITMAP_PADDING);
        beginDragShared(child, this);
    }

    public void beginDragShared(View child, DragSource source) {
        Resources r = getResources();

        // The drag bitmap follows the touch point around on the screen
        final Bitmap b = createDragBitmap(child, new Canvas(), DRAG_BITMAP_PADDING);

        final int bmpWidth = b.getWidth();
        final int bmpHeight = b.getHeight();

        float scale = mLauncher.getDragLayer().getLocationInDragLayer(child, mTempXY);
        int dragLayerX =
                Math.round(mTempXY[0] - (bmpWidth - scale * child.getWidth()) / 2);
        int dragLayerY =
                Math.round(mTempXY[1] - (bmpHeight - scale * bmpHeight) / 2
                        - DRAG_BITMAP_PADDING / 2);

        Point dragVisualizeOffset = null;
        Rect dragRect = null;
        if (child instanceof BubbleTextView || child instanceof PagedViewIcon) {
            int iconSize = r.getDimensionPixelSize(R.dimen.app_icon_size);
            int iconPaddingTop = r.getDimensionPixelSize(R.dimen.app_icon_padding_top);
            int top = child.getPaddingTop();
            int left = (bmpWidth - iconSize) / 2;
            int right = left + iconSize;
            int bottom = top + iconSize;
            dragLayerY += top;
            // Note: The drag region is used to calculate drag layer offsets, but the
            // dragVisualizeOffset in addition to the dragRect (the size) to position the outline.
            dragVisualizeOffset = new Point(-DRAG_BITMAP_PADDING / 2,
                    iconPaddingTop - DRAG_BITMAP_PADDING / 2);
            dragRect = new Rect(left, top, right, bottom);
        } else if (child instanceof FolderIcon) {
            int previewSize = r.getDimensionPixelSize(R.dimen.folder_preview_size);
            dragRect = new Rect(0, 0, child.getWidth(), previewSize);
        }

        // Clear the pressed state if necessary
        if (child instanceof BubbleTextView) {
            BubbleTextView icon = (BubbleTextView) child;
            icon.clearPressedOrFocusedBackground();
        }

        mDragController.startDrag(b, dragLayerX, dragLayerY, source, child.getTag(),
                DragController.DRAG_ACTION_MOVE, dragVisualizeOffset, dragRect, scale);
        b.recycle();

        // Show the scrolling indicator when you pick up an item
        showScrollingIndicator(false);
    }

    void addApplicationShortcut(ShortcutInfo info, CellLayout target, long container, int screen,
            boolean insertAtFirst, int intersectX, int intersectY) {
        View view = mLauncher.createShortcut(R.layout.application, target, info);

        final int[] cellXY = new int[2];
        target.findCellForSpanThatIntersects(cellXY, 1, 1, intersectX, intersectY);
        addInScreen(view, container, screen, cellXY[0], cellXY[1], 1, 1, insertAtFirst);
        LauncherModel.addOrMoveItemInDatabase(mLauncher, info, container, screen, cellXY[0],
                cellXY[1]);
    }

    public boolean transitionStateShouldAllowDrop() {
        return ((!isSwitchingState() || mTransitionProgress > 0.5f) && mState != State.SMALL);
    }

    /**
     * {@inheritDoc}
     */
    public boolean acceptDrop(DragObject d) {
        // If it's an external drop (e.g. from All Apps), check if it should be accepted
        CellLayout dropTargetLayout = mDropToLayout;
        if (d.dragSource != this) {
            // Don't accept the drop if we're not over a screen at time of drop
            if (dropTargetLayout == null) {
                return false;
            }
            if (!transitionStateShouldAllowDrop()) return false;

            mDragViewVisualCenter = getDragViewVisualCenter(d.x, d.y, d.xOffset, d.yOffset,
                    d.dragView, mDragViewVisualCenter);

            // We want the point to be mapped to the dragTarget.
            if (mLauncher.isHotseatLayout(dropTargetLayout)) {
                mapPointFromSelfToHotseatLayout(mLauncher.getHotseat(), mDragViewVisualCenter);
            } else {
                mapPointFromSelfToChild(dropTargetLayout, mDragViewVisualCenter, null);
            }

            int spanX;
            int spanY;
            if (mDragInfo != null) {
                final CellLayout.CellInfo dragCellInfo = mDragInfo;
                spanX = dragCellInfo.spanX;
                spanY = dragCellInfo.spanY;
            } else {
                final ItemInfo dragInfo = (ItemInfo) d.dragInfo;
                spanX = dragInfo.spanX;
                spanY = dragInfo.spanY;
            }

            int minSpanX = spanX;
            int minSpanY = spanY;
            if (d.dragInfo instanceof PendingAddWidgetInfo) {
                minSpanX = ((PendingAddWidgetInfo) d.dragInfo).minSpanX;
                minSpanY = ((PendingAddWidgetInfo) d.dragInfo).minSpanY;
            }

            mTargetCell = findNearestArea((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], minSpanX, minSpanY, dropTargetLayout,
                    mTargetCell);
            float distance = dropTargetLayout.getDistanceFromCell(mDragViewVisualCenter[0],
                    mDragViewVisualCenter[1], mTargetCell);
            if (willCreateUserFolder((ItemInfo) d.dragInfo, dropTargetLayout,
                    mTargetCell, distance, true)) {
                return true;
            }
            if (willAddToExistingUserFolder((ItemInfo) d.dragInfo, dropTargetLayout,
                    mTargetCell, distance)) {
                return true;
            }

            int[] resultSpan = new int[2];
            mTargetCell = dropTargetLayout.createArea((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], minSpanX, minSpanY, spanX, spanY,
                    null, mTargetCell, resultSpan, CellLayout.MODE_ACCEPT_DROP);
            boolean foundCell = mTargetCell[0] >= 0 && mTargetCell[1] >= 0;

            // Don't accept the drop if there's no room for the item
            if (!foundCell) {
                mLauncher.showOutOfSpaceMessage(mLauncher.isHotseatLayout(dropTargetLayout));
                return false;
            }
        }
        return true;
    }

    boolean willCreateUserFolder(ItemInfo info, CellLayout target, int[] targetCell, float
            distance, boolean considerTimeout) {
        if (distance > mMaxDistanceForFolderCreation) return false;
        View dropOverView = target.getChildAt(targetCell[0], targetCell[1]);

        if (dropOverView != null) {
            CellLayout.LayoutParams lp = (CellLayout.LayoutParams) dropOverView.getLayoutParams();
            if (lp.useTmpCoords && (lp.tmpCellX != lp.cellX || lp.tmpCellY != lp.tmpCellY)) {
                return false;
            }
        }

        boolean hasntMoved = false;
        if (mDragInfo != null) {
            hasntMoved = dropOverView == mDragInfo.cell;
        }

        if (dropOverView == null || hasntMoved || (considerTimeout && !mCreateUserFolderOnDrop)) {
            return false;
        }

        ItemInfo aboveInfo = (ItemInfo) dropOverView.getTag();
        boolean aboveShortcut =
                (aboveInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION ||
                aboveInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT);
        boolean willBecomeShortcut =
                (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION ||
                info.itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT);

        return (aboveShortcut && willBecomeShortcut);
    }

    boolean willAddToExistingUserFolder(ItemInfo dragInfo, CellLayout target, int[] targetCell,
            float distance) {
        if (distance > mMaxDistanceForFolderCreation) return false;
        View dropOverView = target.getChildAt(targetCell[0], targetCell[1]);

        if (dropOverView != null) {
            CellLayout.LayoutParams lp = (CellLayout.LayoutParams) dropOverView.getLayoutParams();
            if (lp.useTmpCoords && (lp.tmpCellX != lp.cellX || lp.tmpCellY != lp.tmpCellY)) {
                return false;
            }
        }

        if (dropOverView instanceof FolderIcon) {
            FolderIcon fi = (FolderIcon) dropOverView;
            if (fi.acceptDrop(dragInfo)) {
                return true;
            }
        }
        return false;
    }

    boolean createUserFolderIfNecessary(View newView, long container, CellLayout target,
            int[] targetCell, float distance, boolean external, DragView dragView,
            Runnable postAnimationRunnable) {
        if (distance > mMaxDistanceForFolderCreation) return false;
        View v = target.getChildAt(targetCell[0], targetCell[1]);

        boolean hasntMoved = false;
        if (mDragInfo != null) {
            CellLayout cellParent = getParentCellLayoutForView(mDragInfo.cell);
            hasntMoved = (mDragInfo.cellX == targetCell[0] &&
                    mDragInfo.cellY == targetCell[1]) && (cellParent == target);
        }

        if (v == null || hasntMoved || !mCreateUserFolderOnDrop) return false;
        mCreateUserFolderOnDrop = false;
        final int screen = (targetCell == null) ? mDragInfo.screen :
                (mLauncher.isHotseatLayout(target) ?
                mLauncher.getHotseat().indexOfChild(target) :
                indexOfChild(target));

        boolean aboveShortcut = (v.getTag() instanceof ShortcutInfo);
        boolean willBecomeShortcut = (newView.getTag() instanceof ShortcutInfo);

        if (aboveShortcut && willBecomeShortcut) {
            ShortcutInfo sourceInfo = (ShortcutInfo) newView.getTag();
            ShortcutInfo destInfo = (ShortcutInfo) v.getTag();
            // if the drag started here, we need to remove it from the workspace
            if (!external) {
                getParentCellLayoutForView(mDragInfo.cell).removeView(mDragInfo.cell);
            }

            Rect folderLocation = new Rect();
            float scale = mLauncher.getDragLayer().getDescendantRectRelativeToSelf(v, folderLocation);
            target.removeView(v);

            FolderIcon fi =
                mLauncher.addFolder(target, container, screen, targetCell[0], targetCell[1]);
            destInfo.cellX = -1;
            destInfo.cellY = -1;
            sourceInfo.cellX = -1;
            sourceInfo.cellY = -1;

            // If the dragView is null, we can't animate
            boolean animate = dragView != null;
            if (animate) {
                fi.performCreateAnimation(destInfo, v, sourceInfo, dragView, folderLocation, scale,
                        postAnimationRunnable);
            } else {
                fi.addItem(destInfo);
                fi.addItem(sourceInfo);
            }
            return true;
        }
        return false;
    }

    boolean addToExistingFolderIfNecessary(CellLayout target, int[] targetCell,
            float distance, DragObject d, boolean external) {
        if (distance > mMaxDistanceForFolderCreation) return false;

        View dropOverView = target.getChildAt(targetCell[0], targetCell[1]);
        if (!mAddToExistingFolderOnDrop) return false;
        mAddToExistingFolderOnDrop = false;

        if (dropOverView instanceof FolderIcon) {
            FolderIcon fi = (FolderIcon) dropOverView;
            if (fi.acceptDrop((ItemInfo)d.dragInfo)) {
                fi.onDrop(d);

                // if the drag started here, we need to remove it from the workspace
                if (!external) {
                    getParentCellLayoutForView(mDragInfo.cell).removeView(mDragInfo.cell);
                }
                return true;
            }
        }
        return false;
    }

    public void onDrop(final DragObject d) {
        mDragViewVisualCenter = getDragViewVisualCenter(d.x, d.y, d.xOffset, d.yOffset, d.dragView,
                mDragViewVisualCenter);

        CellLayout dropTargetLayout = mDropToLayout;

        // We want the point to be mapped to the dragTarget.
        if (dropTargetLayout != null) {
            if (mLauncher.isHotseatLayout(dropTargetLayout)) {
                mapPointFromSelfToHotseatLayout(mLauncher.getHotseat(), mDragViewVisualCenter);
            } else {
                mapPointFromSelfToChild(dropTargetLayout, mDragViewVisualCenter, null);
            }
        }

        int snapScreen = -1;
        boolean resizeOnDrop = false;
        if (d.dragSource != this) {
            final int[] touchXY = new int[] { (int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1] };
            onDropExternal(touchXY, d.dragInfo, dropTargetLayout, false, d);
        } else if (mDragInfo != null) {
            final View cell = mDragInfo.cell;

            Runnable resizeRunnable = null;
            if (dropTargetLayout != null) {
                // Move internally
                boolean hasMovedLayouts = (getParentCellLayoutForView(cell) != dropTargetLayout);
                boolean hasMovedIntoHotseat = mLauncher.isHotseatLayout(dropTargetLayout);
                long container = hasMovedIntoHotseat ?
                        LauncherSettings.Favorites.CONTAINER_HOTSEAT :
                        LauncherSettings.Favorites.CONTAINER_DESKTOP;
                int screen = (mTargetCell[0] < 0) ?
                        mDragInfo.screen : (hasMovedIntoHotseat ?
                        mLauncher.getHotseat().indexOfChild(dropTargetLayout) :
                        indexOfChild(dropTargetLayout));
                int spanX = mDragInfo != null ? mDragInfo.spanX : 1;
                int spanY = mDragInfo != null ? mDragInfo.spanY : 1;
                // First we find the cell nearest to point at which the item is
                // dropped, without any consideration to whether there is an item there.

                mTargetCell = findNearestArea((int) mDragViewVisualCenter[0], (int)
                        mDragViewVisualCenter[1], spanX, spanY, dropTargetLayout, mTargetCell);
                float distance = dropTargetLayout.getDistanceFromCell(mDragViewVisualCenter[0],
                        mDragViewVisualCenter[1], mTargetCell);

                // If the item being dropped is a shortcut and the nearest drop
                // cell also contains a shortcut, then create a folder with the two shortcuts.
                if (!mInScrollArea && createUserFolderIfNecessary(cell, container,
                        dropTargetLayout, mTargetCell, distance, false, d.dragView, null)) {
                    return;
                }

                if (addToExistingFolderIfNecessary(dropTargetLayout, mTargetCell,
                        distance, d, false)) {
                    return;
                }

                // Aside from the special case where we're dropping a shortcut onto a shortcut,
                // we need to find the nearest cell location that is vacant
                ItemInfo item = (ItemInfo) d.dragInfo;
                int minSpanX = item.spanX;
                int minSpanY = item.spanY;
                if (item.minSpanX > 0 && item.minSpanY > 0) {
                    minSpanX = item.minSpanX;
                    minSpanY = item.minSpanY;
                }

                int[] resultSpan = new int[2];
                mTargetCell = dropTargetLayout.createArea((int) mDragViewVisualCenter[0],
                        (int) mDragViewVisualCenter[1], minSpanX, minSpanY, spanX, spanY, cell,
                        mTargetCell, resultSpan, CellLayout.MODE_ON_DROP);

                boolean foundCell = mTargetCell[0] >= 0 && mTargetCell[1] >= 0;

                // if the widget resizes on drop
                if (foundCell && (cell instanceof AppWidgetHostView) &&
                        (resultSpan[0] != item.spanX || resultSpan[1] != item.spanY)) {
                    resizeOnDrop = true;
                    item.spanX = resultSpan[0];
                    item.spanY = resultSpan[1];
                    AppWidgetHostView awhv = (AppWidgetHostView) cell;
                    AppWidgetResizeFrame.updateWidgetSizeRanges(awhv, mLauncher, resultSpan[0],
                            resultSpan[1]);
                }

                if (mCurrentPage != screen && !hasMovedIntoHotseat) {
                    snapScreen = screen;
                    snapToPage(screen);
                } else if (mLauncher.getHotseat().getCurrentPage() != screen && hasMovedIntoHotseat) {
                    mLauncher.getHotseat().snapToPage(screen);
                }

                if (foundCell) {
                    final ItemInfo info = (ItemInfo) cell.getTag();
                    if (hasMovedLayouts) {
                        // Reparent the view
                        getParentCellLayoutForView(cell).removeView(cell);
                        addInScreen(cell, container, screen, mTargetCell[0], mTargetCell[1],
                                info.spanX, info.spanY);
                    }

                    // update the item's position after drop
                    CellLayout.LayoutParams lp = (CellLayout.LayoutParams) cell.getLayoutParams();
                    lp.cellX = lp.tmpCellX = mTargetCell[0];
                    lp.cellY = lp.tmpCellY = mTargetCell[1];
                    lp.cellHSpan = item.spanX;
                    lp.cellVSpan = item.spanY;
                    lp.isLockedToGrid = true;
                    cell.setId(LauncherModel.getCellLayoutChildId(container, mDragInfo.screen,
                            mTargetCell[0], mTargetCell[1]));

                    if (container != LauncherSettings.Favorites.CONTAINER_HOTSEAT &&
                            cell instanceof LauncherAppWidgetHostView) {
                        final CellLayout cellLayout = dropTargetLayout;
                        // We post this call so that the widget has a chance to be placed
                        // in its final location

                        final LauncherAppWidgetHostView hostView = (LauncherAppWidgetHostView) cell;
                        AppWidgetProviderInfo pinfo = hostView.getAppWidgetInfo();
                        if (pinfo != null &&
                                pinfo.resizeMode != AppWidgetProviderInfo.RESIZE_NONE || mResizeAnyWidget) {
                            final Runnable addResizeFrame = new Runnable() {
                                public void run() {
                                    DragLayer dragLayer = mLauncher.getDragLayer();
                                    dragLayer.addResizeFrame(info, hostView, cellLayout);
                                }
                            };
                            resizeRunnable = (new Runnable() {
                                public void run() {
                                    if (!isPageMoving()) {
                                        addResizeFrame.run();
                                    } else {
                                        mDelayedResizeRunnable = addResizeFrame;
                                    }
                                }
                            });
                        }
                    }

                    LauncherModel.moveItemInDatabase(mLauncher, info, container, screen, lp.cellX,
                            lp.cellY);
                } else {
                    // If we can't find a drop location, we return the item to its original position
                    CellLayout.LayoutParams lp = (CellLayout.LayoutParams) cell.getLayoutParams();
                    mTargetCell[0] = lp.cellX;
                    mTargetCell[1] = lp.cellY;
                    CellLayout layout = (CellLayout) cell.getParent().getParent();
                    layout.markCellsAsOccupiedForView(cell);
                }
            }

            final CellLayout parent = (CellLayout) cell.getParent().getParent();
            final Runnable finalResizeRunnable = resizeRunnable;
            // Prepare it to be animated into its new position
            // This must be called after the view has been re-parented
            final Runnable onCompleteRunnable = new Runnable() {
                @Override
                public void run() {
                    mAnimatingViewIntoPlace = false;
                    updateChildrenLayersEnabled(false);
                    if (finalResizeRunnable != null) {
                        finalResizeRunnable.run();
                    }
                }
            };
            mAnimatingViewIntoPlace = true;
            if (d.dragView.hasDrawn()) {
                final ItemInfo info = (ItemInfo) cell.getTag();
                if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET) {
                    int animationType = resizeOnDrop ? ANIMATE_INTO_POSITION_AND_RESIZE :
                            ANIMATE_INTO_POSITION_AND_DISAPPEAR;
                    animateWidgetDrop(info, parent, d.dragView,
                            onCompleteRunnable, animationType, cell, false);
                } else {
                    int duration = snapScreen < 0 ? -1 : ADJACENT_SCREEN_DROP_DURATION;
                    mLauncher.getDragLayer().animateViewIntoPosition(d.dragView, cell, duration,
                            onCompleteRunnable, this);
                }
            } else {
                d.deferDragViewCleanupPostAnimation = false;
                cell.setVisibility(VISIBLE);
            }
            parent.onDropChild(cell);
        }
    }

    public void setFinalScrollForPageChange(int screen) {
        if (screen >= 0) {
            mSavedScrollX = getScrollX();
            CellLayout cl = (CellLayout) getChildAt(screen);
            mSavedTranslationX = cl.getTranslationX();
            mSavedRotationY = cl.getRotationY();
            final int newX = getChildOffset(screen) - getRelativeChildOffset(screen);
            setScrollX(newX);
            cl.setTranslationX(0f);
            cl.setRotationY(0f);
        }
    }

    public void resetFinalScrollForPageChange(int screen) {
        if (screen >= 0) {
            CellLayout cl = (CellLayout) getChildAt(screen);
            setScrollX(mSavedScrollX);
            cl.setTranslationX(mSavedTranslationX);
            cl.setRotationY(mSavedRotationY);
        }
    }

    public void onDragEnter(DragObject d) {
        mDragEnforcer.onDragEnter();
        mCreateUserFolderOnDrop = false;
        mAddToExistingFolderOnDrop = false;

        mDropToLayout = null;
        CellLayout layout = getCurrentDropLayout();
        setCurrentDropLayout(layout);
        setCurrentDragOverlappingLayout(layout);

        // Because we don't have space in the Phone UI (the CellLayouts run to the edge) we
        // don't need to show the outlines
        if (mShowOutlines) {
            showOutlines();
        }
    }

    static Rect getCellLayoutMetrics(Launcher launcher, int orientation) {
        Resources res = launcher.getResources();
        Display display = launcher.getWindowManager().getDefaultDisplay();
        Point smallestSize = new Point();
        Point largestSize = new Point();
        display.getCurrentSizeRange(smallestSize, largestSize);
        if (orientation == CellLayout.LANDSCAPE) {
            if (mLandscapeCellLayoutMetrics == null) {
                int paddingLeft = res.getDimensionPixelSize(R.dimen.workspace_left_padding_land);
                int paddingRight = res.getDimensionPixelSize(R.dimen.workspace_right_padding_land);
                int paddingTop = res.getDimensionPixelSize(R.dimen.workspace_top_padding_land);
                int paddingBottom = res.getDimensionPixelSize(R.dimen.workspace_bottom_padding_land);
                int width = largestSize.x - paddingLeft - paddingRight;
                int height = smallestSize.y - paddingTop - paddingBottom;
                mLandscapeCellLayoutMetrics = new Rect();
                CellLayout.getMetrics(mLandscapeCellLayoutMetrics, res,
                        width, height, LauncherModel.getCellCountX(), LauncherModel.getCellCountY(),
                        orientation);
            }
            return mLandscapeCellLayoutMetrics;
        } else if (orientation == CellLayout.PORTRAIT) {
            if (mPortraitCellLayoutMetrics == null) {
                int paddingLeft = res.getDimensionPixelSize(R.dimen.workspace_left_padding_land);
                int paddingRight = res.getDimensionPixelSize(R.dimen.workspace_right_padding_land);
                int paddingTop = res.getDimensionPixelSize(R.dimen.workspace_top_padding_land);
                int paddingBottom = res.getDimensionPixelSize(R.dimen.workspace_bottom_padding_land);
                int width = smallestSize.x - paddingLeft - paddingRight;
                int height = largestSize.y - paddingTop - paddingBottom;
                mPortraitCellLayoutMetrics = new Rect();
                CellLayout.getMetrics(mPortraitCellLayoutMetrics, res,
                        width, height, LauncherModel.getCellCountX(), LauncherModel.getCellCountY(),
                        orientation);
            }
            return mPortraitCellLayoutMetrics;
        }
        return null;
    }

    public void onDragExit(DragObject d) {
        mDragEnforcer.onDragExit();

        // Here we store the final page that will be dropped to, if the workspace in fact
        // receives the drop
        if (mInScrollArea) {
            PagedView target = mDragInfo != null && mDragInfo.container == Favorites.CONTAINER_HOTSEAT ?
                    mLauncher.getHotseat() : this;
            if (target.isPageMoving()) {
                // If the user drops while the page is scrolling, we should use that page as the
                // destination instead of the page that is being hovered over.
                mDropToLayout = (CellLayout) target.getPageAt(target.getNextPage());
            } else {
                mDropToLayout = mDragOverlappingLayout;
            }
        } else {
            mDropToLayout = mDragTargetLayout;
        }

        if (mDragMode == DRAG_MODE_CREATE_FOLDER) {
            mCreateUserFolderOnDrop = true;
        } else if (mDragMode == DRAG_MODE_ADD_TO_FOLDER) {
            mAddToExistingFolderOnDrop = true;
        }

        // Reset the scroll area and previous drag target
        onResetScrollArea();
        setCurrentDropLayout(null);
        setCurrentDragOverlappingLayout(null);

        mSpringLoadedDragController.cancel();

        if (!mIsPageMoving) {
            hideOutlines();
        }
    }

    void setCurrentDropLayout(CellLayout layout) {
        if (mDragTargetLayout != null) {
            mDragTargetLayout.revertTempState();
            mDragTargetLayout.onDragExit();
        }
        mDragTargetLayout = layout;
        if (mDragTargetLayout != null) {
            mDragTargetLayout.onDragEnter();
        }
        cleanupReorder(true);
        cleanupFolderCreation();
        setCurrentDropOverCell(-1, -1);
    }

    PagedView getCurrentDropTarget() {
        return mLauncher.isHotseatLayout(mDragTargetLayout) ? mLauncher.getHotseat() : this;
    }

    void setCurrentDragOverlappingLayout(CellLayout layout) {
        if (mDragOverlappingLayout != null) {
            mDragOverlappingLayout.setIsDragOverlapping(false);
        }
        mDragOverlappingLayout = layout;
        if (mDragOverlappingLayout != null) {
            mDragOverlappingLayout.setIsDragOverlapping(true);
        }
        invalidate();
    }

    void setCurrentDropOverCell(int x, int y) {
        if (x != mDragOverX || y != mDragOverY) {
            mDragOverX = x;
            mDragOverY = y;
            setDragMode(DRAG_MODE_NONE);
        }
    }

    void setDragMode(int dragMode) {
        if (dragMode != mDragMode) {
            if (dragMode == DRAG_MODE_NONE) {
                cleanupAddToFolder();
                // We don't want to cancel the re-order alarm every time the target cell changes
                // as this feels to slow / unresponsive.
                cleanupReorder(false);
                cleanupFolderCreation();
            } else if (dragMode == DRAG_MODE_ADD_TO_FOLDER) {
                cleanupReorder(true);
                cleanupFolderCreation();
            } else if (dragMode == DRAG_MODE_CREATE_FOLDER) {
                cleanupAddToFolder();
                cleanupReorder(true);
            } else if (dragMode == DRAG_MODE_REORDER) {
                cleanupAddToFolder();
                cleanupFolderCreation();
            }
            mDragMode = dragMode;
        }
    }

    private void cleanupFolderCreation() {
        if (mDragFolderRingAnimator != null) {
            mDragFolderRingAnimator.animateToNaturalState();
        }
        mFolderCreationAlarm.cancelAlarm();
    }

    private void cleanupAddToFolder() {
        if (mDragOverFolderIcon != null) {
            mDragOverFolderIcon.onDragExit();
            mDragOverFolderIcon = null;
        }
    }

    private void cleanupReorder(boolean cancelAlarm) {
        // Any pending reorders are canceled
        if (cancelAlarm) {
            mReorderAlarm.cancelAlarm();
        }
        mLastReorderX = -1;
        mLastReorderY = -1;
    }

    public DropTarget getDropTargetDelegate(DragObject d) {
        return null;
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
       int scrollX = getScrollX();
       if (mNextPage != INVALID_PAGE) {
           scrollX = mScroller.getFinalX();
       }
       xy[0] = xy[0] + scrollX - v.getLeft();
       xy[1] = xy[1] + getScrollY() - v.getTop();
       cachedInverseMatrix.mapPoints(xy);
   }


   void mapPointFromSelfToHotseatLayout(Hotseat hotseat, float[] xy) {
       hotseat.getPageAt(hotseat.getNextPage()).getMatrix().invert(mTempInverseMatrix);
       xy[0] = xy[0] - hotseat.getLeft() - hotseat.getPageAt(hotseat.getNextPage()).getLeft();
       xy[1] = xy[1] - hotseat.getTop() - hotseat.getPageAt(hotseat.getNextPage()).getTop();
       mTempInverseMatrix.mapPoints(xy);
   }

   /*
    *
    * Convert the 2D coordinate xy from this CellLayout's coordinate space to
    * the parent View's coordinate space. The argument xy is modified with the return result.
    *
    */
   void mapPointFromChildToSelf(View v, float[] xy) {
       v.getMatrix().mapPoints(xy);
       int scrollX = getScrollX();
       if (mNextPage != INVALID_PAGE) {
           scrollX = mScroller.getFinalX();
       }
       xy[0] -= (scrollX - v.getLeft());
       xy[1] -= (getScrollY() - v.getTop());
   }

   static float squaredDistance(float[] point1, float[] point2) {
        float distanceX = point1[0] - point2[0];
        float distanceY = point2[1] - point2[1];
        return distanceX * distanceX + distanceY * distanceY;
   }

    /**
     * This method returns the CellLayout that is currently being dragged to. In order to drag
     * to a CellLayout, either the touch point must be directly over the CellLayout, or as a second
     * strategy, we see if the dragView is overlapping any CellLayout and choose the closest one
     *
     * Return null if no CellLayout is currently being dragged over
     */
    private CellLayout findMatchingPageForDragOver(float originX, float originY, boolean exact) {
        // We loop through all the screens (ie CellLayouts) and see which ones overlap
        // with the item being dragged and then choose the one that's closest to the touch point
        final int screenCount = getChildCount();
        CellLayout bestMatchingScreen = null;
        float smallestDistSoFar = Float.MAX_VALUE;

        for (int i = 0; i < screenCount; i++) {
            CellLayout cl = (CellLayout) getChildAt(i);

            final float[] touchXy = {originX, originY};
            // Transform the touch coordinates to the CellLayout's local coordinates
            // If the touch point is within the bounds of the cell layout, we can return immediately
            cl.getMatrix().invert(mTempInverseMatrix);
            mapPointFromSelfToChild(cl, touchXy, mTempInverseMatrix);

            if (touchXy[0] >= 0 && touchXy[0] <= cl.getWidth() &&
                    touchXy[1] >= 0 && touchXy[1] <= cl.getHeight()) {
                return cl;
            }

            if (!exact) {
                // Get the center of the cell layout in screen coordinates
                final float[] cellLayoutCenter = mTempCellLayoutCenterCoordinates;
                cellLayoutCenter[0] = cl.getWidth()/2;
                cellLayoutCenter[1] = cl.getHeight()/2;
                mapPointFromChildToSelf(cl, cellLayoutCenter);

                touchXy[0] = originX;
                touchXy[1] = originY;

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

    // This is used to compute the visual center of the dragView. This point is then
    // used to visualize drop locations and determine where to drop an item. The idea is that
    // the visual center represents the user's interpretation of where the item is, and hence
    // is the appropriate point to use when determining drop location.
    private float[] getDragViewVisualCenter(int x, int y, int xOffset, int yOffset,
            DragView dragView, float[] recycle) {
        float res[];
        if (recycle == null) {
            res = new float[2];
        } else {
            res = recycle;
        }

        // First off, the drag view has been shifted in a way that is not represented in the
        // x and y values or the x/yOffsets. Here we account for that shift.
        x += getResources().getDimensionPixelSize(R.dimen.dragViewOffsetX);
        y += getResources().getDimensionPixelSize(R.dimen.dragViewOffsetY);

        // These represent the visual top and left of drag view if a dragRect was provided.
        // If a dragRect was not provided, then they correspond to the actual view left and
        // top, as the dragRect is in that case taken to be the entire dragView.
        // R.dimen.dragViewOffsetY.
        int left = x - xOffset;
        int top = y - yOffset;

        // In order to find the visual center, we shift by half the dragRect
        res[0] = left + dragView.getDragRegion().width() / 2;
        res[1] = top + dragView.getDragRegion().height() / 2;

        return res;
    }

    private boolean isDragWidget(DragObject d) {
        return (d.dragInfo instanceof LauncherAppWidgetInfo ||
                d.dragInfo instanceof PendingAddWidgetInfo);
    }
    private boolean isExternalDragWidget(DragObject d) {
        return d.dragSource != this && isDragWidget(d);
    }

    public void onDragOver(DragObject d) {
        // Skip drag over events while we are dragging over side pages
        if (mInScrollArea || mIsSwitchingState || mState == State.SMALL) return;

        Rect r = new Rect();
        CellLayout layout = null;
        ItemInfo item = (ItemInfo) d.dragInfo;

        // Ensure that we have proper spans for the item that we are dropping
        if (item.spanX < 0 || item.spanY < 0) throw new RuntimeException("Improper spans found");
        mDragViewVisualCenter = getDragViewVisualCenter(d.x, d.y, d.xOffset, d.yOffset,
            d.dragView, mDragViewVisualCenter);

        final View child = (mDragInfo == null) ? null : mDragInfo.cell;
        // Identify whether we have dragged over a side page
        if (isSmall()) {
            if (mLauncher.getHotseat() != null && !isExternalDragWidget(d)) {
                mLauncher.getHotseat().getHitRect(r);
                if (r.contains(d.x, d.y)) {
                    layout = mLauncher.getHotseat().findMatchingPageForDragOver(d.x, d.y, false);
                }
            }
            if (layout == null) {
                layout = findMatchingPageForDragOver(d.x, d.y, false);
            }
            if (layout != mDragTargetLayout) {

                setCurrentDropLayout(layout);
                setCurrentDragOverlappingLayout(layout);

                boolean isInSpringLoadedMode = (mState == State.SPRING_LOADED);
                if (isInSpringLoadedMode || mLauncher.isHotseatLayout(mDragTargetLayout)) {
                    mSpringLoadedDragController.setAlarm(mDragTargetLayout);
                }
            }
        } else {
            // Test to see if we are over the hotseat otherwise just use the current page
            if (mLauncher.getHotseat() != null && !isDragWidget(d)) {
                mLauncher.getHotseat().getHitRect(r);
                if (r.contains(d.x, d.y)) {
                    layout = mLauncher.getHotseat().findMatchingPageForDragOver(d.x, d.y, false);
                }
            }
            if (layout == null) {
                layout = getCurrentDropLayout();
            }
            if (layout != mDragTargetLayout) {
                setCurrentDropLayout(layout);
                setCurrentDragOverlappingLayout(layout);

                if (mLauncher.isHotseatLayout(mDragTargetLayout)) {
                    mSpringLoadedDragController.setAlarm(mDragTargetLayout);
                }
            }
        }

        // Handle the drag over
        if (mDragTargetLayout != null) {
            // We want the point to be mapped to the dragTarget.
            if (mLauncher.isHotseatLayout(mDragTargetLayout)) {
                mapPointFromSelfToHotseatLayout(mLauncher.getHotseat(), mDragViewVisualCenter);
            } else {
                mapPointFromSelfToChild(mDragTargetLayout, mDragViewVisualCenter, null);
            }

            ItemInfo info = (ItemInfo) d.dragInfo;

            mTargetCell = findNearestArea((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], item.spanX, item.spanY,
                    mDragTargetLayout, mTargetCell);

            setCurrentDropOverCell(mTargetCell[0], mTargetCell[1]);

            float targetCellDistance = mDragTargetLayout.getDistanceFromCell(
                    mDragViewVisualCenter[0], mDragViewVisualCenter[1], mTargetCell);

            final View dragOverView = mDragTargetLayout.getChildAt(mTargetCell[0],
                    mTargetCell[1]);

            manageFolderFeedback(info, mDragTargetLayout, mTargetCell,
                    targetCellDistance, dragOverView);

            int minSpanX = item.spanX;
            int minSpanY = item.spanY;
            if (item.minSpanX > 0 && item.minSpanY > 0) {
                minSpanX = item.minSpanX;
                minSpanY = item.minSpanY;
            }

            boolean nearestDropOccupied = mDragTargetLayout.isNearestDropLocationOccupied((int)
                    mDragViewVisualCenter[0], (int) mDragViewVisualCenter[1], item.spanX,
                    item.spanY, child, mTargetCell);

            if (!nearestDropOccupied) {
                mDragTargetLayout.visualizeDropLocation(child, mDragOutline,
                        (int) mDragViewVisualCenter[0], (int) mDragViewVisualCenter[1],
                        mTargetCell[0], mTargetCell[1], item.spanX, item.spanY, false,
                        d.dragView.getDragVisualizeOffset(), d.dragView.getDragRegion());
            } else if ((mDragMode == DRAG_MODE_NONE || mDragMode == DRAG_MODE_REORDER)
                    && !mReorderAlarm.alarmPending() && (mLastReorderX != mTargetCell[0] ||
                    mLastReorderY != mTargetCell[1])) {

                // Otherwise, if we aren't adding to or creating a folder and there's no pending
                // reorder, then we schedule a reorder
                ReorderAlarmListener listener = new ReorderAlarmListener(mDragViewVisualCenter,
                        minSpanX, minSpanY, item.spanX, item.spanY, d.dragView, child);
                mReorderAlarm.setOnAlarmListener(listener);
                mReorderAlarm.setAlarm(REORDER_TIMEOUT);
            }

            if (mDragMode == DRAG_MODE_CREATE_FOLDER || mDragMode == DRAG_MODE_ADD_TO_FOLDER ||
                    !nearestDropOccupied) {
                if (mDragTargetLayout != null) {
                    mDragTargetLayout.revertTempState();
                }
            }
        }
    }

    private void manageFolderFeedback(ItemInfo info, CellLayout targetLayout,
            int[] targetCell, float distance, View dragOverView) {
        boolean userFolderPending = willCreateUserFolder(info, targetLayout, targetCell, distance,
                false);

        if (mDragMode == DRAG_MODE_NONE && userFolderPending &&
                !mFolderCreationAlarm.alarmPending()) {
            mFolderCreationAlarm.setOnAlarmListener(new
                    FolderCreationAlarmListener(targetLayout, targetCell[0], targetCell[1]));
            mFolderCreationAlarm.setAlarm(FOLDER_CREATION_TIMEOUT);
            return;
        }

        boolean willAddToFolder =
                willAddToExistingUserFolder(info, targetLayout, targetCell, distance);

        if (willAddToFolder && mDragMode == DRAG_MODE_NONE) {
            mDragOverFolderIcon = ((FolderIcon) dragOverView);
            mDragOverFolderIcon.onDragEnter(info);
            if (targetLayout != null) {
                targetLayout.clearDragOutlines();
            }
            setDragMode(DRAG_MODE_ADD_TO_FOLDER);
            return;
        }

        if (mDragMode == DRAG_MODE_ADD_TO_FOLDER && !willAddToFolder) {
            setDragMode(DRAG_MODE_NONE);
        }
        if (mDragMode == DRAG_MODE_CREATE_FOLDER && !userFolderPending) {
            setDragMode(DRAG_MODE_NONE);
        }
    }

    class FolderCreationAlarmListener implements OnAlarmListener {
        CellLayout layout;
        int cellX;
        int cellY;

        public FolderCreationAlarmListener(CellLayout layout, int cellX, int cellY) {
            this.layout = layout;
            this.cellX = cellX;
            this.cellY = cellY;
        }

        public void onAlarm(Alarm alarm) {
            if (mDragFolderRingAnimator == null) {
                mDragFolderRingAnimator = new FolderRingAnimator(mLauncher, null);
            }
            mDragFolderRingAnimator.setCell(cellX, cellY);
            mDragFolderRingAnimator.setCellLayout(layout);
            mDragFolderRingAnimator.animateToAcceptState();
            layout.showFolderAccept(mDragFolderRingAnimator);
            layout.clearDragOutlines();
            setDragMode(DRAG_MODE_CREATE_FOLDER);
        }
    }

    class ReorderAlarmListener implements OnAlarmListener {
        float[] dragViewCenter;
        int minSpanX, minSpanY, spanX, spanY;
        DragView dragView;
        View child;

        public ReorderAlarmListener(float[] dragViewCenter, int minSpanX, int minSpanY, int spanX,
                int spanY, DragView dragView, View child) {
            this.dragViewCenter = dragViewCenter;
            this.minSpanX = minSpanX;
            this.minSpanY = minSpanY;
            this.spanX = spanX;
            this.spanY = spanY;
            this.child = child;
            this.dragView = dragView;
        }

        public void onAlarm(Alarm alarm) {
            int[] resultSpan = new int[2];
            mTargetCell = findNearestArea((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], spanX, spanY, mDragTargetLayout, mTargetCell);
            mLastReorderX = mTargetCell[0];
            mLastReorderY = mTargetCell[1];

            mTargetCell = mDragTargetLayout.createArea((int) mDragViewVisualCenter[0],
                (int) mDragViewVisualCenter[1], minSpanX, minSpanY, spanX, spanY,
                child, mTargetCell, resultSpan, CellLayout.MODE_DRAG_OVER);

            if (mTargetCell[0] < 0 || mTargetCell[1] < 0) {
                mDragTargetLayout.revertTempState();
            } else {
                setDragMode(DRAG_MODE_REORDER);
            }

            boolean resize = resultSpan[0] != spanX || resultSpan[1] != spanY;
            mDragTargetLayout.visualizeDropLocation(child, mDragOutline,
                (int) mDragViewVisualCenter[0], (int) mDragViewVisualCenter[1],
                mTargetCell[0], mTargetCell[1], resultSpan[0], resultSpan[1], resize,
                dragView.getDragVisualizeOffset(), dragView.getDragRegion());
        }
    }

    @Override
    public void getHitRect(Rect outRect) {
        // We want the workspace to have the whole area of the display (it will find the correct
        // cell layout to drop to in the existing drag/drop logic.
        outRect.set(0, 0, mDisplaySize.x, mDisplaySize.y);
    }

    /**
     * Add the item specified by dragInfo to the given layout.
     * @return true if successful
     */
    public boolean addExternalItemToScreen(ItemInfo dragInfo, CellLayout layout) {
        if (layout.findCellForSpan(mTempEstimate, dragInfo.spanX, dragInfo.spanY)) {
            onDropExternal(dragInfo.dropPos, dragInfo, layout, false);
            return true;
        }
        mLauncher.showOutOfSpaceMessage(mLauncher.isHotseatLayout(layout));
        return false;
    }

    private void onDropExternal(int[] touchXY, Object dragInfo,
            CellLayout cellLayout, boolean insertAtFirst) {
        onDropExternal(touchXY, dragInfo, cellLayout, insertAtFirst, null);
    }

    /**
     * Drop an item that didn't originate on one of the workspace screens.
     * It may have come from Launcher (e.g. from all apps or customize), or it may have
     * come from another app altogether.
     *
     * NOTE: This can also be called when we are outside of a drag event, when we want
     * to add an item to one of the workspace screens.
     */
    private void onDropExternal(final int[] touchXY, final Object dragInfo,
            final CellLayout cellLayout, boolean insertAtFirst, DragObject d) {
        final Runnable exitSpringLoadedRunnable = new Runnable() {
            @Override
            public void run() {
                mLauncher.exitSpringLoadedDragModeDelayed(true, false, null);
            }
        };

        ItemInfo info = (ItemInfo) dragInfo;
        int spanX = info.spanX;
        int spanY = info.spanY;
        if (mDragInfo != null) {
            spanX = mDragInfo.spanX;
            spanY = mDragInfo.spanY;
        }

        final long container = mLauncher.isHotseatLayout(cellLayout) ?
                LauncherSettings.Favorites.CONTAINER_HOTSEAT :
                    LauncherSettings.Favorites.CONTAINER_DESKTOP;
        final int screen = mLauncher.isHotseatLayout(cellLayout) ?
                mLauncher.getHotseat().indexOfChild(cellLayout) :
                indexOfChild(cellLayout);
        if (mState != State.SPRING_LOADED) {
            if (!mLauncher.isHotseatLayout(cellLayout) && screen != mCurrentPage) {
                snapToPage(screen);
            } else if (mLauncher.isHotseatLayout(cellLayout) && screen != mLauncher.getHotseat().getCurrentPage()) {
                mLauncher.getHotseat().snapToPage(screen);
            }
        }

        if (info instanceof PendingAddItemInfo) {
            final PendingAddItemInfo pendingInfo = (PendingAddItemInfo) dragInfo;

            boolean findNearestVacantCell = true;
            if (pendingInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT ||
                    pendingInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_LAUNCHER_ACTION) {
                mTargetCell = findNearestArea(touchXY[0], touchXY[1], spanX, spanY,
                        cellLayout, mTargetCell);
                float distance = cellLayout.getDistanceFromCell(mDragViewVisualCenter[0],
                        mDragViewVisualCenter[1], mTargetCell);
                if (willCreateUserFolder((ItemInfo) d.dragInfo, cellLayout, mTargetCell,
                        distance, true) || willAddToExistingUserFolder((ItemInfo) d.dragInfo,
                                cellLayout, mTargetCell, distance)) {
                    findNearestVacantCell = false;
                }
            }

            final ItemInfo item = (ItemInfo) d.dragInfo;
            boolean updateWidgetSize = false;
            if (findNearestVacantCell) {
                int minSpanX = item.spanX;
                int minSpanY = item.spanY;
                if (item.minSpanX > 0 && item.minSpanY > 0) {
                    minSpanX = item.minSpanX;
                    minSpanY = item.minSpanY;
                }
                int[] resultSpan = new int[2];
                mTargetCell = cellLayout.createArea((int) mDragViewVisualCenter[0],
                        (int) mDragViewVisualCenter[1], minSpanX, minSpanY, info.spanX, info.spanY,
                        null, mTargetCell, resultSpan, CellLayout.MODE_ON_DROP_EXTERNAL);

                if (resultSpan[0] != item.spanX || resultSpan[1] != item.spanY) {
                    updateWidgetSize = true;
                }
                item.spanX = resultSpan[0];
                item.spanY = resultSpan[1];
            }

            Runnable onAnimationCompleteRunnable = new Runnable() {
                @Override
                public void run() {
                    // When dragging and dropping from customization tray, we deal with creating
                    // widgets/shortcuts/folders in a slightly different way
                    switch (pendingInfo.itemType) {
                    case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                        int span[] = new int[2];
                        span[0] = item.spanX;
                        span[1] = item.spanY;
                        mLauncher.addAppWidgetFromDrop((PendingAddWidgetInfo) pendingInfo,
                                container, screen, mTargetCell, span, null);
                        break;
                    case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                        case LauncherSettings.Favorites.ITEM_TYPE_LAUNCHER_ACTION:
                        if (pendingInfo instanceof PendingAddActionInfo) {
                            mLauncher.processActionFromDrop(((PendingAddActionInfo)pendingInfo).action,
                                    container, screen, mTargetCell, null);
                        } else {
                            mLauncher.processShortcutFromDrop(pendingInfo.componentName,
                                    container, screen, mTargetCell, null);
                        }
                        break;
                    default:
                        throw new IllegalStateException("Unknown item type: " +
                                pendingInfo.itemType);
                    }
                }
            };
            View finalView = pendingInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
                    ? ((PendingAddWidgetInfo) pendingInfo).boundWidget : null;

            if (finalView instanceof AppWidgetHostView && updateWidgetSize) {
                AppWidgetHostView awhv = (AppWidgetHostView) finalView;
                AppWidgetResizeFrame.updateWidgetSizeRanges(awhv, mLauncher, item.spanX,
                        item.spanY);
            }

            int animationStyle = ANIMATE_INTO_POSITION_AND_DISAPPEAR;
            if (pendingInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET &&
                    ((PendingAddWidgetInfo) pendingInfo).info.configure != null) {
                animationStyle = ANIMATE_INTO_POSITION_AND_REMAIN;
            }
            animateWidgetDrop(info, cellLayout, d.dragView, onAnimationCompleteRunnable,
                    animationStyle, finalView, true);
        } else {
            // This is for other drag/drop cases, like dragging from All Apps
            View view;

            switch (info.itemType) {
            case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
            case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
            case LauncherSettings.Favorites.ITEM_TYPE_LAUNCHER_ACTION:
                if (info.container == NO_ID && info instanceof ApplicationInfo) {
                    // Came from all apps -- make a copy
                    info = new ShortcutInfo((ApplicationInfo) info);
                }
                view = mLauncher.createShortcut(R.layout.application, cellLayout,
                        (ShortcutInfo) info);
                break;
            case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                view = FolderIcon.fromXml(R.layout.folder_icon, mLauncher, cellLayout,
                        (FolderInfo) info);
                if (mHideIconLabels) {
                    ((FolderIcon) view).setTextVisible(false);
                }
                break;
            default:
                throw new IllegalStateException("Unknown item type: " + info.itemType);
            }

            // First we find the cell nearest to point at which the item is
            // dropped, without any consideration to whether there is an item there.
            if (touchXY != null) {
                mTargetCell = findNearestArea(touchXY[0], touchXY[1], spanX, spanY,
                        cellLayout, mTargetCell);
                float distance = cellLayout.getDistanceFromCell(mDragViewVisualCenter[0],
                        mDragViewVisualCenter[1], mTargetCell);
                d.postAnimationRunnable = exitSpringLoadedRunnable;
                if (createUserFolderIfNecessary(view, container, cellLayout, mTargetCell, distance,
                        true, d.dragView, d.postAnimationRunnable)) {
                    return;
                }
                if (addToExistingFolderIfNecessary(cellLayout, mTargetCell, distance, d,
                        true)) {
                    return;
                }
            }

            if (touchXY != null) {
                // when dragging and dropping, just find the closest free spot
                mTargetCell = cellLayout.createArea((int) mDragViewVisualCenter[0],
                        (int) mDragViewVisualCenter[1], 1, 1, 1, 1,
                        null, mTargetCell, null, CellLayout.MODE_ON_DROP_EXTERNAL);
            } else {
                cellLayout.findCellForSpan(mTargetCell, 1, 1);
            }
            addInScreen(view, container, screen, mTargetCell[0], mTargetCell[1], info.spanX,
                    info.spanY, insertAtFirst);
            cellLayout.onDropChild(view);
            CellLayout.LayoutParams lp = (CellLayout.LayoutParams) view.getLayoutParams();
            cellLayout.getShortcutsAndWidgets().measureChild(view);


            LauncherModel.addOrMoveItemInDatabase(mLauncher, info, container, screen,
                    lp.cellX, lp.cellY);

            if (d.dragView != null) {
                // We wrap the animation call in the temporary set and reset of the current
                // cellLayout to its final transform -- this means we animate the drag view to
                // the correct final location.
                setFinalTransitionTransform(cellLayout);
                mLauncher.getDragLayer().animateViewIntoPosition(d.dragView, view,
                        exitSpringLoadedRunnable);
                resetTransitionTransform(cellLayout);
            }
        }
    }

    public Bitmap createWidgetBitmap(ItemInfo widgetInfo, View layout) {
        int[] unScaledSize = mLauncher.getWorkspace().estimateItemSize(widgetInfo.spanX,
                widgetInfo.spanY, false);
        int visibility = layout.getVisibility();
        layout.setVisibility(VISIBLE);

        int width = MeasureSpec.makeMeasureSpec(unScaledSize[0], MeasureSpec.EXACTLY);
        int height = MeasureSpec.makeMeasureSpec(unScaledSize[1], MeasureSpec.EXACTLY);
        Bitmap b = Bitmap.createBitmap(unScaledSize[0], unScaledSize[1],
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);

        layout.measure(width, height);
        layout.layout(0, 0, unScaledSize[0], unScaledSize[1]);
        layout.draw(c);
        c.setBitmap(null);
        layout.setVisibility(visibility);
        return b;
    }

    private void getFinalPositionForDropAnimation(int[] loc, float[] scaleXY,
            DragView dragView, CellLayout layout, ItemInfo info, int[] targetCell,
            boolean external, boolean scale) {
        // Now we animate the dragView, (ie. the widget or shortcut preview) into its final
        // location and size on the home screen.
        int spanX = info.spanX;
        int spanY = info.spanY;

        Rect r = estimateItemPosition(layout, targetCell[0], targetCell[1], spanX, spanY);
        loc[0] = r.left;
        loc[1] = r.top;

        setFinalTransitionTransform(layout);
        float cellLayoutScale =
                mLauncher.getDragLayer().getDescendantCoordRelativeToSelf(layout, loc);
        resetTransitionTransform(layout);

        float dragViewScaleX;
        float dragViewScaleY;
        if (scale) {
            dragViewScaleX = (1.0f * r.width()) / dragView.getMeasuredWidth();
            dragViewScaleY = (1.0f * r.height()) / dragView.getMeasuredHeight();
        } else {
            dragViewScaleX = 1f;
            dragViewScaleY = 1f;
        }

        // The animation will scale the dragView about its center, so we need to center about
        // the final location.
        loc[0] -= (dragView.getMeasuredWidth() - cellLayoutScale * r.width()) / 2;
        loc[1] -= (dragView.getMeasuredHeight() - cellLayoutScale * r.height()) / 2;

        scaleXY[0] = dragViewScaleX * cellLayoutScale;
        scaleXY[1] = dragViewScaleY * cellLayoutScale;
    }

    public void animateWidgetDrop(ItemInfo info, CellLayout cellLayout, DragView dragView,
            final Runnable onCompleteRunnable, int animationType, final View finalView,
            boolean external) {
        Rect from = new Rect();
        mLauncher.getDragLayer().getViewRectRelativeToSelf(dragView, from);

        int[] finalPos = new int[2];
        float scaleXY[] = new float[2];
        boolean scalePreview = !(info instanceof PendingAddShortcutInfo || info instanceof PendingAddActionInfo);
        getFinalPositionForDropAnimation(finalPos, scaleXY, dragView, cellLayout, info, mTargetCell,
                external, scalePreview);

        Resources res = mLauncher.getResources();
        int duration = res.getInteger(R.integer.config_dropAnimMaxDuration) - 200;

        // In the case where we've prebound the widget, we remove it from the DragLayer
        if (finalView instanceof AppWidgetHostView && external) {
            Log.d(TAG, "6557954 Animate widget drop, final view is appWidgetHostView");
            mLauncher.getDragLayer().removeView(finalView);
        }
        if ((animationType == ANIMATE_INTO_POSITION_AND_RESIZE || external) && finalView != null) {
            Bitmap crossFadeBitmap = createWidgetBitmap(info, finalView);
            dragView.setCrossFadeBitmap(crossFadeBitmap);
            dragView.crossFade((int) (duration * 0.8f));
        } else if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET && external) {
            scaleXY[0] = scaleXY[1] = Math.min(scaleXY[0],  scaleXY[1]);
        }

        DragLayer dragLayer = mLauncher.getDragLayer();
        if (animationType == CANCEL_TWO_STAGE_WIDGET_DROP_ANIMATION) {
            mLauncher.getDragLayer().animateViewIntoPosition(dragView, finalPos, 0f, 0.1f, 0.1f,
                    DragLayer.ANIMATION_END_DISAPPEAR, onCompleteRunnable, duration);
        } else {
            int endStyle;
            if (animationType == ANIMATE_INTO_POSITION_AND_REMAIN) {
                endStyle = DragLayer.ANIMATION_END_REMAIN_VISIBLE;
            } else {
                endStyle = DragLayer.ANIMATION_END_DISAPPEAR;
            }

            Runnable onComplete = new Runnable() {
                @Override
                public void run() {
                    if (finalView != null) {
                        finalView.setVisibility(VISIBLE);
                    }
                    if (onCompleteRunnable != null) {
                        onCompleteRunnable.run();
                    }
                }
            };
            dragLayer.animateViewIntoPosition(dragView, from.left, from.top, finalPos[0],
                    finalPos[1], 1, 1, 1, scaleXY[0], scaleXY[1], onComplete, endStyle,
                    duration, this);
        }
    }

    public void setFinalTransitionTransform(CellLayout layout) {
        if (isSwitchingState()) {
            int index = indexOfChild(layout);
            mCurrentScaleX = layout.getScaleX();
            mCurrentScaleY = layout.getScaleY();
            mCurrentTranslationX = layout.getTranslationX();
            mCurrentTranslationY = layout.getTranslationY();
            mCurrentRotationY = layout.getRotationY();
            layout.setScaleX(mNewScaleXs[index]);
            layout.setScaleY(mNewScaleYs[index]);
            layout.setTranslationX(mNewTranslationXs[index]);
            layout.setTranslationY(mNewTranslationYs[index]);
            layout.setRotationY(mNewRotationYs[index]);
        }
    }
    public void resetTransitionTransform(CellLayout layout) {
        if (isSwitchingState()) {
            mCurrentScaleX = layout.getScaleX();
            mCurrentScaleY = layout.getScaleY();
            mCurrentTranslationX = layout.getTranslationX();
            mCurrentTranslationY = layout.getTranslationY();
            mCurrentRotationY = layout.getRotationY();
            layout.setScaleX(mCurrentScaleX);
            layout.setScaleY(mCurrentScaleY);
            layout.setTranslationX(mCurrentTranslationX);
            layout.setTranslationY(mCurrentTranslationY);
            layout.setRotationY(mCurrentRotationY);
        }
    }

    /**
     * Return the current {@link CellLayout}, correctly picking the destination
     * screen while a scroll is in progress.
     */
    public CellLayout getCurrentDropLayout() {
        return (CellLayout) getChildAt(getNextPage());
    }

    /**
     * Calculate the nearest cell where the given object would be dropped.
     *
     * pixelX and pixelY should be in the coordinate system of layout
     */
    private int[] findNearestArea(int pixelX, int pixelY,
            int spanX, int spanY, CellLayout layout, int[] recycle) {
        return layout.findNearestArea(
                pixelX, pixelY, spanX, spanY, recycle);
    }

    void setup(DragController dragController) {
        mSpringLoadedDragController = new SpringLoadedDragController(mLauncher);
        mDragController = dragController;

        // hardware layers on children are enabled on startup, but should be disabled until
        // needed
        updateChildrenLayersEnabled(false);
        setWallpaperDimension();
        if (!mScrollWallpaper) {
            centerWallpaperOffset();
        }

        mIsLandscape = LauncherApplication.isScreenLandscape(mLauncher);
    }

    /**
     * Called at the end of a drag which originated on the workspace.
     */
    public void onDropCompleted(View target, DragObject d, boolean isFlingToDelete,
            boolean success) {
        if (success) {
            if (target != this) {
                if (mDragInfo != null) {
                    getParentCellLayoutForView(mDragInfo.cell).removeView(mDragInfo.cell);
                    if (mDragInfo.cell instanceof DropTarget) {
                        mDragController.removeDropTarget((DropTarget) mDragInfo.cell);
                    }
                }
            }
        } else if (mDragInfo != null) {
            CellLayout cellLayout;
            if (mLauncher.isHotseatLayout(target)) {
                cellLayout = (CellLayout) mLauncher.getHotseat().getChildAt(mDragInfo.screen);
            } else {
                cellLayout = (CellLayout) getChildAt(mDragInfo.screen);
            }
            cellLayout.onDropChild(mDragInfo.cell);
            if (mDragInfo.cell != null) {
                mDragInfo.cell.setVisibility(VISIBLE);
            }
        }
        mDragOutline = null;
        mDragInfo = null;

        // Hide the scrolling indicator after you pick up an item
        hideScrollingIndicator(false);
    }

    void updateItemLocationsInDatabase(CellLayout cl) {
        int count = cl.getShortcutsAndWidgets().getChildCount();

        int screen = indexOfChild(cl);
        int container = Favorites.CONTAINER_DESKTOP;

        if (mLauncher.isHotseatLayout(cl)) {
            screen = mLauncher.getHotseat().indexOfChild(cl);
            container = Favorites.CONTAINER_HOTSEAT;
        }

        for (int i = 0; i < count; i++) {
            View v = cl.getShortcutsAndWidgets().getChildAt(i);
            ItemInfo info = (ItemInfo) v.getTag();
            // Null check required as the AllApps button doesn't have an item info
            if (info != null && info.requiresDbUpdate) {
                info.requiresDbUpdate = false;
                LauncherModel.modifyItemInDatabase(mLauncher, info, container, screen, info.cellX,
                        info.cellY, info.spanX, info.spanY);
            }
        }
    }

    @Override
    public boolean supportsFlingToDelete() {
        return true;
    }

    @Override
    public void onFlingToDelete(DragObject d, int x, int y, PointF vec) {
        // Do nothing
    }

    @Override
    public void onFlingToDeleteCompleted() {
        // Do nothing
    }

    public boolean isDropEnabled() {
        return true;
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        // We don't dispatch restoreInstanceState to our children using this code path.
        // Some pages will be restored immediately as their items are bound immediately, and 
        // others we will need to wait until after their items are bound.
        mSavedStates = container;
    }

    public void restoreInstanceStateForChild(int child) {
        if (mSavedStates != null) {
            mRestoredPages.add(child);
            CellLayout cl = (CellLayout) getChildAt(child);
            cl.restoreInstanceState(mSavedStates);
        }
    }

    public void restoreInstanceStateForRemainingPages() {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            if (!mRestoredPages.contains(i)) {
                restoreInstanceStateForChild(i);
            }
        }
        mRestoredPages.clear();
    }

    @Override
    public void scrollLeft() {
        if (!isSmall() && !mIsSwitchingState) {
            super.scrollLeft();
        }
        Folder openFolder = getOpenFolder();
        if (openFolder != null) {
            openFolder.completeDragExit();
        }
    }

    @Override
    public void scrollRight() {
        if (!isSmall() && !mIsSwitchingState) {
            super.scrollRight();
        }
        Folder openFolder = getOpenFolder();
        if (openFolder != null) {
            openFolder.completeDragExit();
        }
    }

    @Override
    public boolean onEnterScrollArea(int x, int y, int direction) {
        // Ignore the scroll area if we are dragging over the hot seat
        boolean isPortrait = !LauncherApplication.isScreenLandscape(getContext());
        if (mLauncher.getHotseat() != null && isPortrait) {
            Rect r = new Rect();
            mLauncher.getHotseat().getHitRect(r);
            if (r.contains(x, y)) {
                return false;
            }
        }

        boolean result = false;
        if (!isSmall() && !mIsSwitchingState) {
            mInScrollArea = true;

            final int page = getNextPage() +
                       (direction == DragController.SCROLL_LEFT ? -1 : 1);

            // We always want to exit the current layout to ensure parity of enter / exit
            setCurrentDropLayout(null);

            if (0 <= page && page < getChildCount()) {
                CellLayout layout = (CellLayout) getChildAt(page);
                setCurrentDragOverlappingLayout(layout);

                // Workspace is responsible for drawing the edge glow on adjacent pages,
                // so we need to redraw the workspace when this may have changed.
                invalidate();
                result = true;
            }
        }
        return result;
    }

    @Override
    public boolean onExitScrollArea() {
        boolean result = false;
        if (mInScrollArea) {
            invalidate();
            CellLayout layout = getCurrentDropLayout();
            setCurrentDropLayout(layout);
            setCurrentDragOverlappingLayout(layout);

            result = true;
            mInScrollArea = false;
        }
        return result;
    }

    private void onResetScrollArea() {
        setCurrentDragOverlappingLayout(null);
        mInScrollArea = false;
    }

    /**
     * Returns a specific CellLayout
     */
    CellLayout getParentCellLayoutForView(View v) {
        ArrayList<CellLayout> layouts = getWorkspaceAndHotseatCellLayouts();
        for (CellLayout layout : layouts) {
            if (layout.getShortcutsAndWidgets().indexOfChild(v) > -1) {
                return layout;
            }
        }
        return null;
    }

    /**
     * Returns a list of all the CellLayouts in the workspace.
     */
    ArrayList<CellLayout> getWorkspaceAndHotseatCellLayouts() {
        ArrayList<CellLayout> layouts = new ArrayList<CellLayout>();
        int screenCount = getChildCount();
        for (int screen = 0; screen < screenCount; screen++) {
            layouts.add(((CellLayout) getChildAt(screen)));
        }
        Hotseat hotseat = mLauncher.getHotseat();
        if (hotseat != null) {
            for (int screen = 0; screen < hotseat.getChildCount(); screen++) {
                layouts.add((CellLayout) hotseat.getPageAt(screen));
            }
        }
        return layouts;
    }

    /**
     * We should only use this to search for specific children.  Do not use this method to modify
     * ShortcutsAndWidgetsContainer directly. Includes ShortcutAndWidgetContainers from
     * the hotseat and workspace pages
     */
    ArrayList<ShortcutAndWidgetContainer> getAllShortcutAndWidgetContainers() {
        ArrayList<ShortcutAndWidgetContainer> childrenLayouts =
                new ArrayList<ShortcutAndWidgetContainer>();
        int screenCount = getChildCount();
        for (int screen = 0; screen < screenCount; screen++) {
            childrenLayouts.add(((CellLayout) getChildAt(screen)).getShortcutsAndWidgets());
        }
        Hotseat hotseat = mLauncher.getHotseat();
        if (hotseat != null) {
            for (int screen = 0; screen < hotseat.getChildCount(); screen++) {
                childrenLayouts.add(((CellLayout) hotseat.getPageAt(screen)).getShortcutsAndWidgets());
            }
        }
        return childrenLayouts;
    }

    public Folder getFolderForTag(Object tag) {
        ArrayList<ShortcutAndWidgetContainer> childrenLayouts =
                getAllShortcutAndWidgetContainers();
        for (ShortcutAndWidgetContainer layout: childrenLayouts) {
            int count = layout.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = layout.getChildAt(i);
                if (child instanceof Folder) {
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
        ArrayList<ShortcutAndWidgetContainer> childrenLayouts =
                getAllShortcutAndWidgetContainers();
        for (ShortcutAndWidgetContainer layout: childrenLayouts) {
            int count = layout.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = layout.getChildAt(i);
                if (child.getTag() == tag) {
                    return child;
                }
            }
        }
        return null;
    }

    void clearDropTargets() {
        ArrayList<ShortcutAndWidgetContainer> childrenLayouts =
                getAllShortcutAndWidgetContainers();
        for (ShortcutAndWidgetContainer layout: childrenLayouts) {
            int childCount = layout.getChildCount();
            for (int j = 0; j < childCount; j++) {
                View v = layout.getChildAt(j);
                if (v instanceof DropTarget) {
                    mDragController.removeDropTarget((DropTarget) v);
                }
            }
        }
    }

    void removeItems(final ArrayList<String> packages) {
        final HashSet<String> packageNames = new HashSet<String>();
        packageNames.addAll(packages);

        ArrayList<CellLayout> cellLayouts = getWorkspaceAndHotseatCellLayouts();
        for (final CellLayout layoutParent: cellLayouts) {
            final ViewGroup layout = layoutParent.getShortcutsAndWidgets();

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

                            if (name != null) {
                                if (packageNames.contains(name.getPackageName())) {
                                    LauncherModel.deleteItemFromDatabase(mLauncher, info);
                                    childrenToRemove.add(view);
                                }
                            }
                        } else if (tag instanceof FolderInfo) {
                            final FolderInfo info = (FolderInfo) tag;
                            final ArrayList<ShortcutInfo> contents = info.contents;
                            final int contentsCount = contents.size();
                            final ArrayList<ShortcutInfo> appsToRemoveFromFolder =
                                    new ArrayList<ShortcutInfo>();

                            for (final ShortcutInfo appInfo : contents) {
                                final Intent intent = appInfo.intent;
                                final ComponentName name = intent.getComponent();

                                if (name != null) {
                                    if (packageNames.contains(name.getPackageName())) {
                                        appsToRemoveFromFolder.add(appInfo);
                                    }
                                }
                            }
                            for (ShortcutInfo item: appsToRemoveFromFolder) {
                                info.remove(item);
                                LauncherModel.deleteItemFromDatabase(mLauncher, item);
                            }
                        } else if (tag instanceof LauncherAppWidgetInfo) {
                            final LauncherAppWidgetInfo info = (LauncherAppWidgetInfo) tag;
                            final ComponentName provider = info.providerName;
                            if (provider != null) {
                                if (packageNames.contains(provider.getPackageName())) {
                                    LauncherModel.deleteItemFromDatabase(mLauncher, info);
                                    childrenToRemove.add(view);
                                }
                            }
                        }
                    }

                    childCount = childrenToRemove.size();
                    for (int j = 0; j < childCount; j++) {
                        View child = childrenToRemove.get(j);
                        // Note: We can not remove the view directly from CellLayoutChildren as this
                        // does not re-mark the spaces as unoccupied.
                        layoutParent.removeViewInLayout(child);
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

        // Clean up new-apps animation list
        final Context context = getContext();
        post(new Runnable() {
            @Override
            public void run() {
                String spKey = LauncherApplication.getSharedPreferencesKey();
                SharedPreferences sp = context.getSharedPreferences(spKey,
                        Context.MODE_PRIVATE);
                Set<String> newApps = sp.getStringSet(InstallShortcutReceiver.NEW_APPS_LIST_KEY,
                        null);

                // Remove all queued items that match the same package
                if (newApps != null) {
                    synchronized (newApps) {
                        Iterator<String> iter = newApps.iterator();
                        while (iter.hasNext()) {
                            try {
                                Intent intent = Intent.parseUri(iter.next(), 0);
                                String pn = ItemInfo.getPackageName(intent);
                                if (packageNames.contains(pn)) {
                                    iter.remove();
                                }

                                // It is possible that we've queued an item to be loaded, yet it has
                                // not been added to the workspace, so remove those items as well.
                                ArrayList<ItemInfo> shortcuts;
                                shortcuts = LauncherModel.getWorkspaceShortcutItemInfosWithIntent(
                                        intent);
                                for (ItemInfo info : shortcuts) {
                                    LauncherModel.deleteItemFromDatabase(context, info);
                                }
                            } catch (URISyntaxException e) {
                                // Ignore
                            }
                        }
                    }
                }
            }
        });
    }

    void updateShortcuts(ArrayList<ApplicationInfo> apps) {
        ArrayList<ShortcutAndWidgetContainer> childrenLayouts = getAllShortcutAndWidgetContainers();
        for (ShortcutAndWidgetContainer layout: childrenLayouts) {
            int childCount = layout.getChildCount();
            for (int j = 0; j < childCount; j++) {
                final View view = layout.getChildAt(j);
                Object tag = view.getTag();
                if (tag instanceof ShortcutInfo) {
                    ShortcutInfo info = (ShortcutInfo) tag;
                    // We need to check for ACTION_MAIN otherwise getComponent() might
                    // return null for some shortcuts (for instance, for shortcuts to
                    // web pages.)
                    final Intent intent = info.intent;
                    if (intent != null && info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION &&
                            Intent.ACTION_MAIN.equals(intent.getAction())) {
                        final ComponentName name = intent.getComponent();
                        if (name == null) {
                            continue;
                        }
                        for (ApplicationInfo app : apps) {
                            if (app.componentName.equals(name)) {
                                BubbleTextView shortcut = (BubbleTextView) view;
                                info.updateIcon(mIconCache);
                                info.title = app.title.toString();
                                shortcut.applyFromShortcutInfo(info, mIconCache);
                            }
                        }
                    }
                }
            }
        }
    }

    void moveToDefaultScreen(boolean animate) {
        if (!isSmall()) {
            if (animate) {
                snapToPage(mDefaultHomescreen);
            } else {
                setCurrentPage(mDefaultHomescreen);
            }
        }
        getChildAt(mDefaultHomescreen).requestFocus();
    }

    @Override
    public void syncPages() {
    }

    @Override
    public void syncPageItems(int page, boolean immediate) {
    }

    @Override
    protected String getCurrentPageDescription() {
        int page = (mNextPage != INVALID_PAGE) ? mNextPage : mCurrentPage;
        return String.format(getContext().getString(R.string.workspace_scroll_format),
                page + 1, getChildCount());
    }

    public void getLocationInDragLayer(int[] loc) {
        mLauncher.getDragLayer().getLocationInDragLayer(this, loc);
    }

    void setFadeForOverScroll(float fade) {
        if (!isScrollingIndicatorEnabled()) return;

        mOverscrollFade = fade;
        float reducedFade = 0.5f + 0.5f * (1 - fade);
        final ViewGroup parent = (ViewGroup) getParent();
        final ImageView qsbDivider = (ImageView) (parent.findViewById(R.id.qsb_divider));
        final ImageView dockDivider = (ImageView) (parent.findViewById(R.id.dock_divider));
        final View scrollIndicator = getScrollingIndicator();

        cancelScrollingIndicatorAnimations();
        if (qsbDivider != null && mShowSearchBar) qsbDivider.setAlpha(reducedFade);
        if (dockDivider != null && mShowDockDivider) dockDivider.setAlpha(reducedFade);
        if (scrollIndicator != null && mShowScrollingIndicator) scrollIndicator.setAlpha(1 - fade);
    }
}
