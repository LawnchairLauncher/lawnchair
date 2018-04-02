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

package com.android.launcher3;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.SuppressLint;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Property;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.Toast;

import com.android.launcher3.Launcher.CustomContentCallbacks;
import com.android.launcher3.Launcher.LauncherOverlay;
import com.android.launcher3.LauncherAppWidgetHost.ProviderChangedListener;
import com.android.launcher3.UninstallDropTarget.DropTargetSource;
import com.android.launcher3.accessibility.AccessibleDragListenerAdapter;
import com.android.launcher3.accessibility.OverviewAccessibilityDelegate;
import com.android.launcher3.accessibility.OverviewScreenAccessibilityDelegate;
import com.android.launcher3.accessibility.WorkspaceAccessibilityHelper;
import com.android.launcher3.anim.AnimationLayerSet;
import com.android.launcher3.badge.FolderBadgeInfo;
import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.dragndrop.SpringLoadedDragController;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.folder.PreviewBackground;
import com.android.launcher3.graphics.DragPreviewProvider;
import com.android.launcher3.graphics.PreloadIconDrawable;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.shortcuts.ShortcutDragPreviewProvider;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.LongArrayMap;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.util.VerticalFlingDetector;
import com.android.launcher3.util.WallpaperOffsetInterpolator;
import com.android.launcher3.widget.PendingAddShortcutInfo;
import com.android.launcher3.widget.PendingAddWidgetInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * The workspace is a wide area with a wallpaper and a finite number of pages.
 * Each page contains a number of icons, folders or widgets the user can
 * interact with. A workspace is meant to be used with a fixed width only.
 */
public class Workspace extends PagedView
        implements DropTarget, DragSource, View.OnTouchListener,
        DragController.DragListener, ViewGroup.OnHierarchyChangeListener,
        Insettable, DropTargetSource {
    private static final String TAG = "Launcher.Workspace";

    /** The value that {@link #mTransitionProgress} must be greater than for
     * {@link #transitionStateShouldAllowDrop()} to return true. */
    private static final float ALLOW_DROP_TRANSITION_PROGRESS = 0.25f;

    /** The value that {@link #mTransitionProgress} must be greater than for
     * {@link #isFinishedSwitchingState()} ()} to return true. */
    private static final float FINISHED_SWITCHING_STATE_TRANSITION_PROGRESS = 0.5f;

    private static final boolean ENFORCE_DRAG_EVENT_ORDER = false;

    private static final int SNAP_OFF_EMPTY_SCREEN_DURATION = 400;
    private static final int FADE_EMPTY_SCREEN_DURATION = 150;

    private static final int ADJACENT_SCREEN_DROP_DURATION = 300;

    private static final boolean MAP_NO_RECURSE = false;
    private static final boolean MAP_RECURSE = true;

    // The screen id used for the empty screen always present to the right.
    public static final long EXTRA_EMPTY_SCREEN_ID = -201;
    // The is the first screen. It is always present, even if its empty.
    public static final long FIRST_SCREEN_ID = 0;

    private final static long CUSTOM_CONTENT_SCREEN_ID = -301;

    private static final long CUSTOM_CONTENT_GESTURE_DELAY = 200;
    private long mTouchDownTime = -1;
    private long mCustomContentShowTime = -1;

    private LayoutTransition mLayoutTransition;
    @Thunk final WallpaperManager mWallpaperManager;

    private ShortcutAndWidgetContainer mDragSourceInternal;

    @Thunk final LongArrayMap<CellLayout> mWorkspaceScreens = new LongArrayMap<>();
    @Thunk final ArrayList<Long> mScreenOrder = new ArrayList<>();

    @Thunk Runnable mRemoveEmptyScreenRunnable;
    @Thunk boolean mDeferRemoveExtraEmptyScreen = false;

    /**
     * CellInfo for the cell that is currently being dragged
     */
    private CellLayout.CellInfo mDragInfo;

    /**
     * Target drop area calculated during last acceptDrop call.
     */
    @Thunk int[] mTargetCell = new int[2];
    private int mDragOverX = -1;
    private int mDragOverY = -1;

    CustomContentCallbacks mCustomContentCallbacks;
    boolean mCustomContentShowing;
    private float mLastCustomContentScrollProgress = -1f;
    private String mCustomContentDescription = "";

    /**
     * The CellLayout that is currently being dragged over
     */
    @Thunk CellLayout mDragTargetLayout = null;
    /**
     * The CellLayout that we will show as highlighted
     */
    private CellLayout mDragOverlappingLayout = null;

    /**
     * The CellLayout which will be dropped to
     */
    private CellLayout mDropToLayout = null;

    @Thunk final Launcher mLauncher;
    @Thunk DragController mDragController;

    // These are temporary variables to prevent having to allocate a new object just to
    // return an (x, y) value from helper functions. Do NOT use them to maintain other state.
    private static final Rect sTempRect = new Rect();

    private final int[] mTempXY = new int[2];
    @Thunk float[] mDragViewVisualCenter = new float[2];
    private final float[] mTempTouchCoordinates = new float[2];

    private SpringLoadedDragController mSpringLoadedDragController;
    private final float mOverviewModeShrinkFactor;

    // State variable that indicates whether the pages are small (ie when you're
    // in all apps or customize mode)

    public enum State {
        NORMAL          (false, false, ContainerType.WORKSPACE),
        NORMAL_HIDDEN   (false, false, ContainerType.ALLAPPS),
        SPRING_LOADED   (false, true, ContainerType.WORKSPACE),
        OVERVIEW        (true, true, ContainerType.OVERVIEW),
        OVERVIEW_HIDDEN (true, false, ContainerType.WIDGETS);

        public final boolean shouldUpdateWidget;
        public final boolean hasMultipleVisiblePages;
        public final int containerType;

        State(boolean shouldUpdateWidget, boolean hasMultipleVisiblePages, int containerType) {
            this.shouldUpdateWidget = shouldUpdateWidget;
            this.hasMultipleVisiblePages = hasMultipleVisiblePages;
            this.containerType = containerType;
        }
    }

    // Direction used for moving the workspace and hotseat UI
    public enum Direction {
        X  (TRANSLATION_X),
        Y  (TRANSLATION_Y);

        private final Property<View, Float> viewProperty;

        Direction(Property<View, Float> viewProperty) {
            this.viewProperty = viewProperty;
        }
    }

    private static final int HOTSEAT_STATE_ALPHA_INDEX = 2;

    /**
     * These values correspond to {@link Direction#X} & {@link Direction#Y}
     */
    private final float[] mPageAlpha = new float[] {1, 1};
    /**
     * Hotseat alpha can be changed when moving horizontally, vertically, changing states.
     * The values correspond to {@link Direction#X}, {@link Direction#Y} &
     * {@link #HOTSEAT_STATE_ALPHA_INDEX} respectively.
     */
    private final float[] mHotseatAlpha = new float[] {1, 1, 1};

    @ViewDebug.ExportedProperty(category = "launcher")
    private State mState = State.NORMAL;
    private boolean mIsSwitchingState = false;

    boolean mAnimatingViewIntoPlace = false;
    boolean mChildrenLayersEnabled = true;

    private boolean mStripScreensOnPageStopMoving = false;

    private DragPreviewProvider mOutlineProvider = null;
    private final boolean mWorkspaceFadeInAdjacentScreens;

    final WallpaperOffsetInterpolator mWallpaperOffset;
    private boolean mUnlockWallpaperFromDefaultPageOnLayout;

    @Thunk Runnable mDelayedResizeRunnable;
    private Runnable mDelayedSnapToPageRunnable;

    // Variables relating to the creation of user folders by hovering shortcuts over shortcuts
    private static final int FOLDER_CREATION_TIMEOUT = 0;
    public static final int REORDER_TIMEOUT = 350;
    private final Alarm mFolderCreationAlarm = new Alarm();
    private final Alarm mReorderAlarm = new Alarm();
    private PreviewBackground mFolderCreateBg;
    private FolderIcon mDragOverFolderIcon = null;
    private boolean mCreateUserFolderOnDrop = false;
    private boolean mAddToExistingFolderOnDrop = false;
    private float mMaxDistanceForFolderCreation;

    private final Canvas mCanvas = new Canvas();

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
    @Thunk int mLastReorderX = -1;
    @Thunk int mLastReorderY = -1;

    private SparseArray<Parcelable> mSavedStates;
    private final ArrayList<Integer> mRestoredPages = new ArrayList<>();

    private float mCurrentScale;
    private float mTransitionProgress;

    @Thunk Runnable mDeferredAction;
    private boolean mDeferDropAfterUninstall;
    private boolean mUninstallSuccessful;

    // State related to Launcher Overlay
    LauncherOverlay mLauncherOverlay;
    boolean mScrollInteractionBegan;
    boolean mStartedSendingScrollEvents;
    float mLastOverlayScroll = 0;
    boolean mOverlayShown = false;

    private boolean mForceDrawAdjacentPages = false;
    // Total over scrollX in the overlay direction.
    private float mOverlayTranslation;

    // Handles workspace state transitions
    private final WorkspaceStateTransitionAnimation mStateTransitionAnimation;

    private AccessibilityDelegate mPagesAccessibilityDelegate;

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

        mLauncher = Launcher.getLauncher(context);
        mStateTransitionAnimation = new WorkspaceStateTransitionAnimation(mLauncher, this);
        final Resources res = getResources();
        DeviceProfile grid = mLauncher.getDeviceProfile();
        mWorkspaceFadeInAdjacentScreens = grid.shouldFadeAdjacentWorkspaceScreens();
        mWallpaperManager = WallpaperManager.getInstance(context);

        mWallpaperOffset = new WallpaperOffsetInterpolator(this);
        mOverviewModeShrinkFactor =
                res.getInteger(R.integer.config_workspaceOverviewShrinkPercentage) / 100f;

        setOnHierarchyChangeListener(this);
        setHapticFeedbackEnabled(false);

        initWorkspace();

        // Disable multitouch across the workspace/all apps/customize tray
        setMotionEventSplittingEnabled(true);
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);

        CellLayout customScreen = getScreenWithId(CUSTOM_CONTENT_SCREEN_ID);
        if (customScreen != null) {
            View customContent = customScreen.getShortcutsAndWidgets().getChildAt(0);
            if (customContent instanceof Insettable) {
                ((Insettable) customContent).setInsets(mInsets);
            }
        }
    }

    /**
     * Estimates the size of an item using spans: hSpan, vSpan.
     *
     * @param springLoaded True if we are in spring loaded mode.
     * @param unscaledSize True if caller wants to return the unscaled size
     * @return MAX_VALUE for each dimension if unsuccessful.
     */
    public int[] estimateItemSize(ItemInfo itemInfo, boolean springLoaded, boolean unscaledSize) {
        float shrinkFactor = mLauncher.getDeviceProfile().workspaceSpringLoadShrinkFactor;
        int[] size = new int[2];
        if (getChildCount() > 0) {
            // Use the first non-custom page to estimate the child position
            CellLayout cl = (CellLayout) getChildAt(numCustomPages());
            boolean isWidget = itemInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET;

            Rect r = estimateItemPosition(cl, 0, 0, itemInfo.spanX, itemInfo.spanY);

            float scale = 1;
            if (isWidget) {
                DeviceProfile profile = mLauncher.getDeviceProfile();
                scale = Utilities.shrinkRect(r, profile.appWidgetScale.x, profile.appWidgetScale.y);
            }
            size[0] = r.width();
            size[1] = r.height();

            if (isWidget && unscaledSize) {
                size[0] /= scale;
                size[1] /= scale;
            }

            if (springLoaded) {
                size[0] *= shrinkFactor;
                size[1] *= shrinkFactor;
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

    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
        if (ENFORCE_DRAG_EVENT_ORDER) {
            enforceDragParity("onDragStart", 0, 0);
        }

        if (mDragInfo != null && mDragInfo.cell != null) {
            CellLayout layout = (CellLayout) mDragInfo.cell.getParent().getParent();
            layout.markCellsAsUnoccupiedForView(mDragInfo.cell);
        }

        if (mOutlineProvider != null) {
            // The outline is used to visualize where the item will land if dropped
            mOutlineProvider.generateDragOutline(mCanvas);
        }

        updateChildrenLayersEnabled(false);
        mLauncher.onDragStarted();
        mLauncher.lockScreenOrientation();
        mLauncher.onInteractionBegin();
        // Prevent any Un/InstallShortcutReceivers from updating the db while we are dragging
        InstallShortcutReceiver.enableInstallQueue(InstallShortcutReceiver.FLAG_DRAG_AND_DROP);

        // Do not add a new page if it is a accessible drag which was not started by the workspace.
        // We do not support accessibility drag from other sources and instead provide a direct
        // action for move/add to homescreen.
        // When a accessible drag is started by the folder, we only allow rearranging withing the
        // folder.
        boolean addNewPage = !(options.isAccessibleDrag && dragObject.dragSource != this);

        if (addNewPage) {
            mDeferRemoveExtraEmptyScreen = false;
            addExtraEmptyScreenOnDrag();

            if (dragObject.dragInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
                    && dragObject.dragSource != this) {
                // When dragging a widget from different source, move to a page which has
                // enough space to place this widget (after rearranging/resizing). We special case
                // widgets as they cannot be placed inside a folder.
                // Start at the current page and search right (on LTR) until finding a page with
                // enough space. Since an empty screen is the furthest right, a page must be found.
                int currentPage = getPageNearestToCenterOfScreen();
                for (int pageIndex = currentPage; pageIndex < getPageCount(); pageIndex++) {
                    CellLayout page = (CellLayout) getPageAt(pageIndex);
                    if (page.hasReorderSolution(dragObject.dragInfo)) {
                        setCurrentPage(pageIndex);
                        break;
                    }
                }
            }
        }

        // Always enter the spring loaded mode
        mLauncher.enterSpringLoadedDragMode();
    }

    public void deferRemoveExtraEmptyScreen() {
        mDeferRemoveExtraEmptyScreen = true;
    }

    @Override
    public void onDragEnd() {
        if (ENFORCE_DRAG_EVENT_ORDER) {
            enforceDragParity("onDragEnd", 0, 0);
        }

        if (!mDeferRemoveExtraEmptyScreen) {
            removeExtraEmptyScreen(true, mDragSourceInternal != null);
        }

        updateChildrenLayersEnabled(false);
        mLauncher.unlockScreenOrientation(false);

        // Re-enable any Un/InstallShortcutReceiver and now process any queued items
        InstallShortcutReceiver.disableAndFlushInstallQueue(
                InstallShortcutReceiver.FLAG_DRAG_AND_DROP, getContext());

        mOutlineProvider = null;
        mDragInfo = null;
        mDragSourceInternal = null;
        mLauncher.onInteractionEnd();
    }

    /**
     * Initializes various states for this workspace.
     */
    protected void initWorkspace() {
        mCurrentPage = getDefaultPage();
        DeviceProfile grid = mLauncher.getDeviceProfile();
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);

        setMinScale(mOverviewModeShrinkFactor);
        setupLayoutTransition();

        mMaxDistanceForFolderCreation = (0.55f * grid.iconSizePx);

        // Set the wallpaper dimensions when Launcher starts up
        setWallpaperDimension();
    }

    @Override
    public void initParentViews(View parent) {
        super.initParentViews(parent);
        mPageIndicator.setAccessibilityDelegate(new OverviewAccessibilityDelegate());
    }

    private int getDefaultPage() {
        return numCustomPages();
    }

    private void setupLayoutTransition() {
        // We want to show layout transitions when pages are deleted, to close the gap.
        mLayoutTransition = new LayoutTransition();
        mLayoutTransition.enableTransitionType(LayoutTransition.DISAPPEARING);
        mLayoutTransition.enableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
        mLayoutTransition.disableTransitionType(LayoutTransition.APPEARING);
        mLayoutTransition.disableTransitionType(LayoutTransition.CHANGE_APPEARING);
        setLayoutTransition(mLayoutTransition);
    }

    void enableLayoutTransitions() {
        setLayoutTransition(mLayoutTransition);
    }
    void disableLayoutTransitions() {
        setLayoutTransition(null);
    }

    @Override
    public void onChildViewAdded(View parent, View child) {
        if (!(child instanceof CellLayout)) {
            throw new IllegalArgumentException("A Workspace can only have CellLayout children.");
        }
        CellLayout cl = ((CellLayout) child);
        cl.setOnInterceptTouchListener(this);
        cl.setClickable(true);
        cl.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        super.onChildViewAdded(parent, child);
    }

    boolean isTouchActive() {
        return mTouchState != TOUCH_STATE_REST;
    }

    /**
     * Initializes and binds the first page
     * @param qsb an existing qsb to recycle or null.
     */
    public void bindAndInitFirstWorkspaceScreen(View qsb) {
        if (!FeatureFlags.QSB_ON_FIRST_SCREEN) {
            return;
        }
        // Add the first page
        CellLayout firstPage = insertNewWorkspaceScreen(Workspace.FIRST_SCREEN_ID, 0);
        if (FeatureFlags.PULLDOWN_SEARCH) {
            firstPage.setOnTouchListener(new VerticalFlingDetector(mLauncher) {
                // detect fling when touch started from empty space
                @Override
                public boolean onTouch(View v, MotionEvent ev) {
                    if (workspaceInModalState()) return false;
                    if (shouldConsumeTouch(v)) return true;
                    if (super.onTouch(v, ev)) {
                        mLauncher.startSearch("", false, null, false);
                        return true;
                    }
                    return false;
                }
            });
            firstPage.setOnInterceptTouchListener(new VerticalFlingDetector(mLauncher) {
                // detect fling when touch started from on top of the icons
                @Override
                public boolean onTouch(View v, MotionEvent ev) {
                    if (shouldConsumeTouch(v)) return true;
                    if (super.onTouch(v, ev)) {
                        mLauncher.startSearch("", false, null, false);
                        return true;
                    }
                    return false;
                }
            });
        }
        // Always add a QSB on the first screen.
        if (qsb == null) {
            // In transposed layout, we add the QSB in the Grid. As workspace does not touch the
            // edges, we do not need a full width QSB.
            qsb = LayoutInflater.from(getContext())
                    .inflate(R.layout.search_container_workspace,firstPage, false);
        }

        CellLayout.LayoutParams lp = new CellLayout.LayoutParams(0, 0, firstPage.getCountX(), 1);
        lp.canReorder = false;
        if (!firstPage.addViewToCellLayout(qsb, 0, R.id.search_container_workspace, lp, true)) {
            Log.e(TAG, "Failed to add to item at (0, 0) to CellLayout");
        }
    }

    public void removeAllWorkspaceScreens() {
        // Disable all layout transitions before removing all pages to ensure that we don't get the
        // transition animations competing with us changing the scroll when we add pages or the
        // custom content screen
        disableLayoutTransitions();

        // Since we increment the current page when we call addCustomContentPage via bindScreens
        // (and other places), we need to adjust the current page back when we clear the pages
        if (hasCustomContent()) {
            removeCustomContentPage();
        }

        // Recycle the QSB widget
        View qsb = findViewById(R.id.search_container_workspace);
        if (qsb != null) {
            ((ViewGroup) qsb.getParent()).removeView(qsb);
        }

        // Remove the pages and clear the screen models
        removeAllViews();
        mScreenOrder.clear();
        mWorkspaceScreens.clear();

        // Ensure that the first page is always present
        bindAndInitFirstWorkspaceScreen(qsb);

        // Re-enable the layout transitions
        enableLayoutTransitions();
    }

    public void insertNewWorkspaceScreenBeforeEmptyScreen(long screenId) {
        // Find the index to insert this view into.  If the empty screen exists, then
        // insert it before that.
        int insertIndex = mScreenOrder.indexOf(EXTRA_EMPTY_SCREEN_ID);
        if (insertIndex < 0) {
            insertIndex = mScreenOrder.size();
        }
        insertNewWorkspaceScreen(screenId, insertIndex);
    }

    public void insertNewWorkspaceScreen(long screenId) {
        insertNewWorkspaceScreen(screenId, getChildCount());
    }

    public CellLayout insertNewWorkspaceScreen(long screenId, int insertIndex) {
        if (mWorkspaceScreens.containsKey(screenId)) {
            throw new RuntimeException("Screen id " + screenId + " already exists!");
        }

        // Inflate the cell layout, but do not add it automatically so that we can get the newly
        // created CellLayout.
        CellLayout newScreen = (CellLayout) LayoutInflater.from(getContext()).inflate(
                        R.layout.workspace_screen, this, false /* attachToRoot */);
        newScreen.setOnLongClickListener(mLongClickListener);
        newScreen.setOnClickListener(mLauncher);
        newScreen.setSoundEffectsEnabled(false);

        int paddingLeftRight = mLauncher.getDeviceProfile().cellLayoutPaddingLeftRightPx;
        int paddingBottom = mLauncher.getDeviceProfile().cellLayoutBottomPaddingPx;
        newScreen.setPadding(paddingLeftRight, 0, paddingLeftRight, paddingBottom);

        mWorkspaceScreens.put(screenId, newScreen);
        mScreenOrder.add(insertIndex, screenId);
        addView(newScreen, insertIndex);

        if (mLauncher.getAccessibilityDelegate().isInAccessibleDrag()) {
            newScreen.enableAccessibleDrag(true, CellLayout.WORKSPACE_ACCESSIBILITY_DRAG);
        }

        return newScreen;
    }

    public void createCustomContentContainer() {
        CellLayout customScreen = (CellLayout)
                LayoutInflater.from(getContext()).inflate(R.layout.workspace_screen, this, false);
        customScreen.disableDragTarget();
        customScreen.disableJailContent();

        mWorkspaceScreens.put(CUSTOM_CONTENT_SCREEN_ID, customScreen);
        mScreenOrder.add(0, CUSTOM_CONTENT_SCREEN_ID);

        // We want no padding on the custom content
        customScreen.setPadding(0, 0, 0, 0);

        addFullScreenPage(customScreen);

        // Update the custom content hint
        setCurrentPage(getCurrentPage() + 1);
    }

    public void removeCustomContentPage() {
        CellLayout customScreen = getScreenWithId(CUSTOM_CONTENT_SCREEN_ID);
        if (customScreen == null) {
            throw new RuntimeException("Expected custom content screen to exist");
        }

        mWorkspaceScreens.remove(CUSTOM_CONTENT_SCREEN_ID);
        mScreenOrder.remove(CUSTOM_CONTENT_SCREEN_ID);
        removeView(customScreen);

        if (mCustomContentCallbacks != null) {
            mCustomContentCallbacks.onScrollProgressChanged(0);
            mCustomContentCallbacks.onHide();
        }

        mCustomContentCallbacks = null;

        // Update the custom content hint
        setCurrentPage(getCurrentPage() - 1);
    }

    public void addToCustomContentPage(View customContent, CustomContentCallbacks callbacks,
            String description) {
        if (getPageIndexForScreenId(CUSTOM_CONTENT_SCREEN_ID) < 0) {
            throw new RuntimeException("Expected custom content screen to exist");
        }

        // Add the custom content to the full screen custom page
        CellLayout customScreen = getScreenWithId(CUSTOM_CONTENT_SCREEN_ID);
        int spanX = customScreen.getCountX();
        int spanY = customScreen.getCountY();
        CellLayout.LayoutParams lp = new CellLayout.LayoutParams(0, 0, spanX, spanY);
        lp.canReorder  = false;
        lp.isFullscreen = true;
        if (customContent instanceof Insettable) {
            ((Insettable)customContent).setInsets(mInsets);
        }

        // Verify that the child is removed from any existing parent.
        if (customContent.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) customContent.getParent();
            parent.removeView(customContent);
        }
        customScreen.removeAllViews();
        customContent.setFocusable(true);
        customContent.setOnKeyListener(new FullscreenKeyEventListener());
        customContent.setOnFocusChangeListener(mLauncher.mFocusHandler
                .getHideIndicatorOnFocusListener());
        customScreen.addViewToCellLayout(customContent, 0, 0, lp, true);
        mCustomContentDescription = description;

        mCustomContentCallbacks = callbacks;
    }

    public void addExtraEmptyScreenOnDrag() {
        boolean lastChildOnScreen = false;
        boolean childOnFinalScreen = false;

        // Cancel any pending removal of empty screen
        mRemoveEmptyScreenRunnable = null;

        if (mDragSourceInternal != null) {
            if (mDragSourceInternal.getChildCount() == 1) {
                lastChildOnScreen = true;
            }
            CellLayout cl = (CellLayout) mDragSourceInternal.getParent();
            if (indexOfChild(cl) == getChildCount() - 1) {
                childOnFinalScreen = true;
            }
        }

        // If this is the last item on the final screen
        if (lastChildOnScreen && childOnFinalScreen) {
            return;
        }
        if (!mWorkspaceScreens.containsKey(EXTRA_EMPTY_SCREEN_ID)) {
            insertNewWorkspaceScreen(EXTRA_EMPTY_SCREEN_ID);
        }
    }

    public boolean addExtraEmptyScreen() {
        if (!mWorkspaceScreens.containsKey(EXTRA_EMPTY_SCREEN_ID)) {
            insertNewWorkspaceScreen(EXTRA_EMPTY_SCREEN_ID);
            return true;
        }
        return false;
    }

    private void convertFinalScreenToEmptyScreenIfNecessary() {
        if (mLauncher.isWorkspaceLoading()) {
            // Invalid and dangerous operation if workspace is loading
            return;
        }

        if (hasExtraEmptyScreen() || mScreenOrder.size() == 0) return;
        long finalScreenId = mScreenOrder.get(mScreenOrder.size() - 1);

        if (finalScreenId == CUSTOM_CONTENT_SCREEN_ID) return;
        CellLayout finalScreen = mWorkspaceScreens.get(finalScreenId);

        // If the final screen is empty, convert it to the extra empty screen
        if (finalScreen.getShortcutsAndWidgets().getChildCount() == 0 &&
                !finalScreen.isDropPending()) {
            mWorkspaceScreens.remove(finalScreenId);
            mScreenOrder.remove(finalScreenId);

            // if this is the last non-custom content screen, convert it to the empty screen
            mWorkspaceScreens.put(EXTRA_EMPTY_SCREEN_ID, finalScreen);
            mScreenOrder.add(EXTRA_EMPTY_SCREEN_ID);

            // Update the model if we have changed any screens
            LauncherModel.updateWorkspaceScreenOrder(mLauncher, mScreenOrder);
        }
    }

    public void removeExtraEmptyScreen(final boolean animate, boolean stripEmptyScreens) {
        removeExtraEmptyScreenDelayed(animate, null, 0, stripEmptyScreens);
    }

    public void removeExtraEmptyScreenDelayed(final boolean animate, final Runnable onComplete,
            final int delay, final boolean stripEmptyScreens) {
        if (mLauncher.isWorkspaceLoading()) {
            // Don't strip empty screens if the workspace is still loading
            return;
        }

        if (delay > 0) {
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    removeExtraEmptyScreenDelayed(animate, onComplete, 0, stripEmptyScreens);
                }
            }, delay);
            return;
        }

        convertFinalScreenToEmptyScreenIfNecessary();
        if (hasExtraEmptyScreen()) {
            int emptyIndex = mScreenOrder.indexOf(EXTRA_EMPTY_SCREEN_ID);
            if (getNextPage() == emptyIndex) {
                snapToPage(getNextPage() - 1, SNAP_OFF_EMPTY_SCREEN_DURATION);
                fadeAndRemoveEmptyScreen(SNAP_OFF_EMPTY_SCREEN_DURATION, FADE_EMPTY_SCREEN_DURATION,
                        onComplete, stripEmptyScreens);
            } else {
                snapToPage(getNextPage(), 0);
                fadeAndRemoveEmptyScreen(0, FADE_EMPTY_SCREEN_DURATION,
                        onComplete, stripEmptyScreens);
            }
            return;
        } else if (stripEmptyScreens) {
            // If we're not going to strip the empty screens after removing
            // the extra empty screen, do it right away.
            stripEmptyScreens();
        }

        if (onComplete != null) {
            onComplete.run();
        }
    }

    private void fadeAndRemoveEmptyScreen(int delay, int duration, final Runnable onComplete,
            final boolean stripEmptyScreens) {
        // XXX: Do we need to update LM workspace screens below?
        PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat("alpha", 0f);
        PropertyValuesHolder bgAlpha = PropertyValuesHolder.ofFloat("backgroundAlpha", 0f);

        final CellLayout cl = mWorkspaceScreens.get(EXTRA_EMPTY_SCREEN_ID);

        mRemoveEmptyScreenRunnable = new Runnable() {
            @Override
            public void run() {
                if (hasExtraEmptyScreen()) {
                    mWorkspaceScreens.remove(EXTRA_EMPTY_SCREEN_ID);
                    mScreenOrder.remove(EXTRA_EMPTY_SCREEN_ID);
                    removeView(cl);
                    if (stripEmptyScreens) {
                        stripEmptyScreens();
                    }
                    // Update the page indicator to reflect the removed page.
                    showPageIndicatorAtCurrentScroll();
                }
            }
        };

        ObjectAnimator oa = ObjectAnimator.ofPropertyValuesHolder(cl, alpha, bgAlpha);
        oa.setDuration(duration);
        oa.setStartDelay(delay);
        oa.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mRemoveEmptyScreenRunnable != null) {
                    mRemoveEmptyScreenRunnable.run();
                }
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
        oa.start();
    }

    public boolean hasExtraEmptyScreen() {
        int nScreens = getChildCount();
        nScreens = nScreens - numCustomPages();
        return mWorkspaceScreens.containsKey(EXTRA_EMPTY_SCREEN_ID) && nScreens > 1;
    }

    public long commitExtraEmptyScreen() {
        if (mLauncher.isWorkspaceLoading()) {
            // Invalid and dangerous operation if workspace is loading
            return -1;
        }

        CellLayout cl = mWorkspaceScreens.get(EXTRA_EMPTY_SCREEN_ID);
        mWorkspaceScreens.remove(EXTRA_EMPTY_SCREEN_ID);
        mScreenOrder.remove(EXTRA_EMPTY_SCREEN_ID);

        long newId = LauncherSettings.Settings.call(getContext().getContentResolver(),
                LauncherSettings.Settings.METHOD_NEW_SCREEN_ID)
                .getLong(LauncherSettings.Settings.EXTRA_VALUE);
        mWorkspaceScreens.put(newId, cl);
        mScreenOrder.add(newId);

        // Update the model for the new screen
        LauncherModel.updateWorkspaceScreenOrder(mLauncher, mScreenOrder);

        return newId;
    }

    public CellLayout getScreenWithId(long screenId) {
        return mWorkspaceScreens.get(screenId);
    }

    public long getIdForScreen(CellLayout layout) {
        int index = mWorkspaceScreens.indexOfValue(layout);
        if (index != -1) {
            return mWorkspaceScreens.keyAt(index);
        }
        return -1;
    }

    public int getPageIndexForScreenId(long screenId) {
        return indexOfChild(mWorkspaceScreens.get(screenId));
    }

    public long getScreenIdForPageIndex(int index) {
        if (0 <= index && index < mScreenOrder.size()) {
            return mScreenOrder.get(index);
        }
        return -1;
    }

    public ArrayList<Long> getScreenOrder() {
        return mScreenOrder;
    }

    public void stripEmptyScreens() {
        if (mLauncher.isWorkspaceLoading()) {
            // Don't strip empty screens if the workspace is still loading.
            // This is dangerous and can result in data loss.
            return;
        }

        if (isPageInTransition()) {
            mStripScreensOnPageStopMoving = true;
            return;
        }

        int currentPage = getNextPage();
        ArrayList<Long> removeScreens = new ArrayList<>();
        int total = mWorkspaceScreens.size();
        for (int i = 0; i < total; i++) {
            long id = mWorkspaceScreens.keyAt(i);
            CellLayout cl = mWorkspaceScreens.valueAt(i);
            // FIRST_SCREEN_ID can never be removed.
            if ((!FeatureFlags.QSB_ON_FIRST_SCREEN || id > FIRST_SCREEN_ID)
                    && cl.getShortcutsAndWidgets().getChildCount() == 0) {
                removeScreens.add(id);
            }
        }

        boolean isInAccessibleDrag = mLauncher.getAccessibilityDelegate().isInAccessibleDrag();

        // We enforce at least one page to add new items to. In the case that we remove the last
        // such screen, we convert the last screen to the empty screen
        int minScreens = 1 + numCustomPages();

        int pageShift = 0;
        for (Long id: removeScreens) {
            CellLayout cl = mWorkspaceScreens.get(id);
            mWorkspaceScreens.remove(id);
            mScreenOrder.remove(id);

            if (getChildCount() > minScreens) {
                if (indexOfChild(cl) < currentPage) {
                    pageShift++;
                }

                if (isInAccessibleDrag) {
                    cl.enableAccessibleDrag(false, CellLayout.WORKSPACE_ACCESSIBILITY_DRAG);
                }

                removeView(cl);
            } else {
                // if this is the last non-custom content screen, convert it to the empty screen
                mRemoveEmptyScreenRunnable = null;
                mWorkspaceScreens.put(EXTRA_EMPTY_SCREEN_ID, cl);
                mScreenOrder.add(EXTRA_EMPTY_SCREEN_ID);
            }
        }

        if (!removeScreens.isEmpty()) {
            // Update the model if we have changed any screens
            LauncherModel.updateWorkspaceScreenOrder(mLauncher, mScreenOrder);
        }

        if (pageShift >= 0) {
            setCurrentPage(currentPage - pageShift);
        }
    }

    /**
     * At bind time, we use the rank (screenId) to compute x and y for hotseat items.
     * See {@link #addInScreen}.
     */
    public void addInScreenFromBind(View child, ItemInfo info) {
        int x = info.cellX;
        int y = info.cellY;
        if (info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            int screenId = (int) info.screenId;
            x = mLauncher.getHotseat().getCellXFromOrder(screenId);
            y = mLauncher.getHotseat().getCellYFromOrder(screenId);
        }
        addInScreen(child, info.container, info.screenId, x, y, info.spanX, info.spanY);
    }

    /**
     * Adds the specified child in the specified screen based on the {@param info}
     * See {@link #addInScreen(View, long, long, int, int, int, int)}.
     */
    public void addInScreen(View child, ItemInfo info) {
        addInScreen(child, info.container, info.screenId, info.cellX, info.cellY,
                info.spanX, info.spanY);
    }

    /**
     * Adds the specified child in the specified screen. The position and dimension of
     * the child are defined by x, y, spanX and spanY.
     *
     * @param child The child to add in one of the workspace's screens.
     * @param screenId The screen in which to add the child.
     * @param x The X position of the child in the screen's grid.
     * @param y The Y position of the child in the screen's grid.
     * @param spanX The number of cells spanned horizontally by the child.
     * @param spanY The number of cells spanned vertically by the child.
     */
    private void addInScreen(View child, long container, long screenId, int x, int y,
            int spanX, int spanY) {
        if (container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
            if (getScreenWithId(screenId) == null) {
                Log.e(TAG, "Skipping child, screenId " + screenId + " not found");
                // DEBUGGING - Print out the stack trace to see where we are adding from
                new Throwable().printStackTrace();
                return;
            }
        }
        if (screenId == EXTRA_EMPTY_SCREEN_ID) {
            // This should never happen
            throw new RuntimeException("Screen id should not be EXTRA_EMPTY_SCREEN_ID");
        }

        final CellLayout layout;
        if (container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            layout = mLauncher.getHotseat().getLayout();
            child.setOnKeyListener(new HotseatIconKeyEventListener());

            // Hide folder title in the hotseat
            if (child instanceof FolderIcon) {
                ((FolderIcon) child).setTextVisible(false);
            }
        } else {
            // Show folder title if not in the hotseat
            if (child instanceof FolderIcon) {
                ((FolderIcon) child).setTextVisible(true);
            }
            layout = getScreenWithId(screenId);
            child.setOnKeyListener(new IconKeyEventListener());
        }

        ViewGroup.LayoutParams genericLp = child.getLayoutParams();
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
        ItemInfo info = (ItemInfo) child.getTag();
        int childId = mLauncher.getViewIdForItem(info);

        boolean markCellsAsOccupied = !(child instanceof Folder);
        if (!layout.addViewToCellLayout(child, -1, childId, lp, markCellsAsOccupied)) {
            // TODO: This branch occurs when the workspace is adding views
            // outside of the defined grid
            // maybe we should be deleting these items from the LauncherModel?
            Log.e(TAG, "Failed to add to item at (" + lp.cellX + "," + lp.cellY + ") to CellLayout");
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
     * Called directly from a CellLayout (not by the framework), after we've been added as a
     * listener via setOnInterceptTouchEventListener(). This allows us to tell the CellLayout
     * that it should intercept touch events, which is not something that is normally supported.
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return shouldConsumeTouch(v);
    }

    private boolean shouldConsumeTouch(View v) {
        return !workspaceIconsCanBeDragged()
                || (!workspaceInModalState() && indexOfChild(v) != mCurrentPage);
    }

    public boolean isSwitchingState() {
        return mIsSwitchingState;
    }

    /** This differs from isSwitchingState in that we take into account how far the transition
     *  has completed. */
    public boolean isFinishedSwitchingState() {
        return !mIsSwitchingState
                || (mTransitionProgress > FINISHED_SWITCHING_STATE_TRANSITION_PROGRESS);
    }

    protected void onWindowVisibilityChanged (int visibility) {
        mLauncher.onWindowVisibilityChanged(visibility);
    }

    @Override
    public boolean dispatchUnhandledMove(View focused, int direction) {
        if (workspaceInModalState() || !isFinishedSwitchingState()) {
            // when the home screens are shrunken, shouldn't allow side-scrolling
            return false;
        }
        return super.dispatchUnhandledMove(focused, direction);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getAction() & MotionEvent.ACTION_MASK) {
        case MotionEvent.ACTION_DOWN:
            mXDown = ev.getX();
            mYDown = ev.getY();
            mTouchDownTime = System.currentTimeMillis();
            break;
        case MotionEvent.ACTION_POINTER_UP:
        case MotionEvent.ACTION_UP:
            if (mTouchState == TOUCH_STATE_REST) {
                final CellLayout currentPage = (CellLayout) getChildAt(mCurrentPage);
                if (currentPage != null) {
                    onWallpaperTap(ev);
                }
            }
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        // Ignore pointer scroll events if the custom content doesn't allow scrolling.
        if ((getScreenIdForPageIndex(getCurrentPage()) == CUSTOM_CONTENT_SCREEN_ID)
                && (mCustomContentCallbacks != null)
                && !mCustomContentCallbacks.isScrollingAllowed()) {
            return false;
        }
        return super.onGenericMotionEvent(event);
    }

    protected void reinflateWidgetsIfNecessary() {
        final int clCount = getChildCount();
        for (int i = 0; i < clCount; i++) {
            CellLayout cl = (CellLayout) getChildAt(i);
            ShortcutAndWidgetContainer swc = cl.getShortcutsAndWidgets();
            final int itemCount = swc.getChildCount();
            for (int j = 0; j < itemCount; j++) {
                View v = swc.getChildAt(j);

                if (v instanceof LauncherAppWidgetHostView
                        && v.getTag() instanceof LauncherAppWidgetInfo) {
                    LauncherAppWidgetInfo info = (LauncherAppWidgetInfo) v.getTag();
                    LauncherAppWidgetHostView lahv = (LauncherAppWidgetHostView) v;
                    if (lahv.isReinflateRequired(mLauncher.getOrientation())) {
                        // Remove and rebind the current widget (which was inflated in the wrong
                        // orientation), but don't delete it from the database
                        mLauncher.removeItem(lahv, info, false  /* deleteFromDb */);
                        mLauncher.bindAppWidget(info);
                    }
                }
            }
        }
    }

    @Override
    protected void determineScrollingStart(MotionEvent ev) {
        if (!isFinishedSwitchingState()) return;

        float deltaX = ev.getX() - mXDown;
        float absDeltaX = Math.abs(deltaX);
        float absDeltaY = Math.abs(ev.getY() - mYDown);

        if (Float.compare(absDeltaX, 0f) == 0) return;

        float slope = absDeltaY / absDeltaX;
        float theta = (float) Math.atan(slope);

        if (absDeltaX > mTouchSlop || absDeltaY > mTouchSlop) {
            cancelCurrentPageLongPress();
        }

        boolean passRightSwipesToCustomContent =
                (mTouchDownTime - mCustomContentShowTime) > CUSTOM_CONTENT_GESTURE_DELAY;

        boolean swipeInIgnoreDirection = mIsRtl ? deltaX < 0 : deltaX > 0;
        boolean onCustomContentScreen =
                getScreenIdForPageIndex(getCurrentPage()) == CUSTOM_CONTENT_SCREEN_ID;
        if (swipeInIgnoreDirection && onCustomContentScreen && passRightSwipesToCustomContent) {
            // Pass swipes to the right to the custom content page.
            return;
        }

        if (onCustomContentScreen && (mCustomContentCallbacks != null)
                && !mCustomContentCallbacks.isScrollingAllowed()) {
            // Don't allow workspace scrolling if the current custom content screen doesn't allow
            // scrolling.
            return;
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

    protected void onPageBeginTransition() {
        super.onPageBeginTransition();
        updateChildrenLayersEnabled(false);
        AbstractFloatingView.closeAllOpenViews(mLauncher);
    }

    protected void onPageEndTransition() {
        super.onPageEndTransition();
        updateChildrenLayersEnabled(false);

        if (mDragController.isDragging()) {
            if (workspaceInModalState()) {
                // If we are in springloaded mode, then force an event to check if the current touch
                // is under a new page (to scroll to)
                mDragController.forceTouchMove();
            }
        }

        if (mDelayedResizeRunnable != null && !mIsSwitchingState) {
            mDelayedResizeRunnable.run();
            mDelayedResizeRunnable = null;
        }

        if (mDelayedSnapToPageRunnable != null) {
            mDelayedSnapToPageRunnable.run();
            mDelayedSnapToPageRunnable = null;
        }
        if (mStripScreensOnPageStopMoving) {
            stripEmptyScreens();
            mStripScreensOnPageStopMoving = false;
        }
    }

    protected void onScrollInteractionBegin() {
        super.onScrollInteractionEnd();
        mScrollInteractionBegan = true;
    }

    protected void onScrollInteractionEnd() {
        super.onScrollInteractionEnd();
        mScrollInteractionBegan = false;
        if (mStartedSendingScrollEvents) {
            mStartedSendingScrollEvents = false;
            mLauncherOverlay.onScrollInteractionEnd();
        }
    }

    public void setLauncherOverlay(LauncherOverlay overlay) {
        mLauncherOverlay = overlay;
        // A new overlay has been set. Reset event tracking
        mStartedSendingScrollEvents = false;
        onOverlayScrollChanged(0);
    }


    private boolean isScrollingOverlay() {
        return mLauncherOverlay != null &&
                ((mIsRtl && getUnboundedScrollX() > mMaxScrollX) || (!mIsRtl && getUnboundedScrollX() < 0));
    }

    @Override
    protected void snapToDestination() {
        // If we're overscrolling the overlay, we make sure to immediately reset the PagedView
        // to it's baseline position instead of letting the overscroll settle. The overlay handles
        // it's own settling, and every gesture to the overlay should be self-contained and start
        // from 0, so we zero it out here.
        if (isScrollingOverlay()) {
            // We reset mWasInOverscroll so that PagedView doesn't zero out the overscroll
            // interaction when we call snapToPageImmediately.
            mWasInOverscroll = false;
            snapToPageImmediately(0);
        } else {
            super.snapToDestination();
        }
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);

        // Update the page indicator progress.
        boolean isTransitioning = mIsSwitchingState
                || (getLayoutTransition() != null && getLayoutTransition().isRunning());
        if (!isTransitioning) {
            showPageIndicatorAtCurrentScroll();
        }

        updatePageAlphaValues();
        updateStateForCustomContent();
        enableHwLayersOnVisiblePages();
    }

    private void showPageIndicatorAtCurrentScroll() {
        if (mPageIndicator != null) {
            mPageIndicator.setScroll(getScrollX(), computeMaxScrollX());
        }
    }

    @Override
    protected void overScroll(float amount) {
        boolean shouldOverScroll = (amount <= 0 && (!hasCustomContent() || mIsRtl)) ||
                (amount >= 0 && (!hasCustomContent() || !mIsRtl));

        boolean shouldScrollOverlay = mLauncherOverlay != null &&
                !mLauncher.isAllAppsVisible() &&
                ((amount <= 0 && !mIsRtl) || (amount >= 0 && mIsRtl));

        boolean shouldZeroOverlay = mLauncherOverlay != null && mLastOverlayScroll != 0 &&
                ((amount >= 0 && !mIsRtl) || (amount <= 0 && mIsRtl));

        if (shouldScrollOverlay) {
            if (!mStartedSendingScrollEvents && mScrollInteractionBegan) {
                mStartedSendingScrollEvents = true;
                mLauncherOverlay.onScrollInteractionBegin();
            }

            mLastOverlayScroll = Math.abs(amount / getViewportWidth());
            mLauncherOverlay.onScrollChange(mLastOverlayScroll, mIsRtl);
        } else if (shouldOverScroll) {
            dampedOverScroll(amount);
        }

        if (shouldZeroOverlay) {
            mLauncherOverlay.onScrollChange(0, mIsRtl);
        }
    }

    @Override
    protected boolean shouldFlingForVelocity(int velocityX) {
        // When the overlay is moving, the fling or settle transition is controlled by the overlay.
        return Float.compare(Math.abs(mOverlayTranslation), 0) == 0 &&
                super.shouldFlingForVelocity(velocityX);
    }

    private final Interpolator mAlphaInterpolator = new DecelerateInterpolator(3f);

    /**
     * The overlay scroll is being controlled locally, just update our overlay effect
     */
    public void onOverlayScrollChanged(float scroll) {

        if (Float.compare(scroll, 1f) == 0) {
            if (!mOverlayShown) {
                mLauncher.getUserEventDispatcher().logActionOnContainer(Action.Touch.SWIPE,
                        Action.Direction.LEFT, ContainerType.WORKSPACE, 0);
            }
            mOverlayShown = true;
        } else if (Float.compare(scroll, 0f) == 0) {
            if (mOverlayShown) {
                mLauncher.getUserEventDispatcher().logActionOnContainer(Action.Touch.SWIPE,
                        Action.Direction.RIGHT, ContainerType.WORKSPACE, -1);
            }
            mOverlayShown = false;
        }
        float offset = 0f;
        float slip = 0f;

        scroll = Math.max(scroll - offset, 0);
        scroll = Math.min(1, scroll / (1 - offset));

        float alpha = 1 - mAlphaInterpolator.getInterpolation(scroll);
        float transX = mLauncher.getDragLayer().getMeasuredWidth() * scroll;
        transX *= 1 - slip;

        if (mIsRtl) {
            transX = -transX;
        }
        mOverlayTranslation = transX;

        // TODO(adamcohen): figure out a final effect here. We may need to recommend
        // different effects based on device performance. On at least one relatively high-end
        // device I've tried, translating the launcher causes things to get quite laggy.
        setWorkspaceTranslationAndAlpha(Direction.X, transX, alpha);
        setHotseatTranslationAndAlpha(Direction.X, transX, alpha);
    }

    /**
     * Moves the workspace UI in the Y direction.
     * @param translation the amount of shift.
     * @param alpha the alpha for the workspace page
     */
    public void setWorkspaceYTranslationAndAlpha(float translation, float alpha) {
        setWorkspaceTranslationAndAlpha(Direction.Y, translation, alpha);
    }

    /**
     * Moves the workspace UI in the provided direction.
     * @param direction the direction to move the workspace
     * @param translation the amount of shift.
     * @param alpha the alpha for the workspace page
     */
    private void setWorkspaceTranslationAndAlpha(Direction direction, float translation, float alpha) {
        Property<View, Float> property = direction.viewProperty;
        mPageAlpha[direction.ordinal()] = alpha;
        float finalAlpha = mPageAlpha[0] * mPageAlpha[1];

        View currentChild = getChildAt(getCurrentPage());
        if (currentChild != null) {
            property.set(currentChild, translation);
            currentChild.setAlpha(finalAlpha);
        }

        if (direction == Direction.Y) {
            View nextChild = getChildAt(getNextPage());
            if (nextChild != null) {
                property.set(nextChild, translation);
                nextChild.setAlpha(finalAlpha);
            }
        }

        // When the animation finishes, reset all pages, just in case we missed a page.
        if (Float.compare(translation, 0) == 0) {
            for (int i = getChildCount() - 1; i >= 0; i--) {
                View child = getChildAt(i);
                property.set(child, translation);
                child.setAlpha(finalAlpha);
            }
        }
    }

    /**
     * Moves the Hotseat UI in the provided direction.
     * @param direction the direction to move the workspace
     * @param translation the amount of shift.
     * @param alpha the alpha for the hotseat page
     */
    public void setHotseatTranslationAndAlpha(Direction direction, float translation, float alpha) {
        Property<View, Float> property = direction.viewProperty;
        // Skip the page indicator movement in the vertical bar layout
        if (direction != Direction.Y || !mLauncher.getDeviceProfile().isVerticalBarLayout()) {
            property.set(mPageIndicator, translation);
        }
        property.set(mLauncher.getHotseat(), translation);
        setHotseatAlphaAtIndex(alpha, direction.ordinal());
    }

    private void setHotseatAlphaAtIndex(float alpha, int index) {
        mHotseatAlpha[index] = alpha;
        final float hotseatAlpha = mHotseatAlpha[0] * mHotseatAlpha[1] * mHotseatAlpha[2];
        final float pageIndicatorAlpha = mHotseatAlpha[0] * mHotseatAlpha[2];

        mLauncher.getHotseat().setAlpha(hotseatAlpha);
        mPageIndicator.setAlpha(pageIndicatorAlpha);
    }

    public ValueAnimator createHotseatAlphaAnimator(float finalValue) {
        if (Float.compare(finalValue, mHotseatAlpha[HOTSEAT_STATE_ALPHA_INDEX]) == 0) {
            // Return a dummy animator to avoid null checks.
            return ValueAnimator.ofFloat(0, 0);
        } else {
            ValueAnimator animator = ValueAnimator
                    .ofFloat(mHotseatAlpha[HOTSEAT_STATE_ALPHA_INDEX], finalValue);
            animator.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    float value = (Float) valueAnimator.getAnimatedValue();
                    setHotseatAlphaAtIndex(value, HOTSEAT_STATE_ALPHA_INDEX);
                }
            });

            AccessibilityManager am = (AccessibilityManager)
                    mLauncher.getSystemService(Context.ACCESSIBILITY_SERVICE);
            final boolean accessibilityEnabled = am.isEnabled();
            animator.addUpdateListener(
                    new AlphaUpdateListener(mLauncher.getHotseat(), accessibilityEnabled));
            animator.addUpdateListener(
                    new AlphaUpdateListener(mPageIndicator, accessibilityEnabled));
            return animator;
        }
    }

    @Override
    protected void notifyPageSwitchListener(int prevPage) {
        super.notifyPageSwitchListener(prevPage);
        if (prevPage != mCurrentPage) {
            int swipeDirection = (prevPage < mCurrentPage) ? Action.Direction.RIGHT : Action.Direction.LEFT;
            mLauncher.getUserEventDispatcher().logActionOnContainer(Action.Touch.SWIPE,
                    swipeDirection, ContainerType.WORKSPACE, prevPage);
        }
        if (hasCustomContent() && getNextPage() == 0 && !mCustomContentShowing) {
            mCustomContentShowing = true;
            if (mCustomContentCallbacks != null) {
                mCustomContentCallbacks.onShow(false);
                mCustomContentShowTime = System.currentTimeMillis();
            }
        } else if (hasCustomContent() && getNextPage() != 0 && mCustomContentShowing) {
            mCustomContentShowing = false;
            if (mCustomContentCallbacks != null) {
                mCustomContentCallbacks.onHide();
            }
        }
    }

    protected CustomContentCallbacks getCustomContentCallbacks() {
        return mCustomContentCallbacks;
    }

    protected void setWallpaperDimension() {
        Utilities.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                final Point size = LauncherAppState.getIDP(getContext()).defaultWallpaperSize;
                if (size.x != mWallpaperManager.getDesiredMinimumWidth()
                        || size.y != mWallpaperManager.getDesiredMinimumHeight()) {
                    mWallpaperManager.suggestDesiredDimensions(size.x, size.y);
                }
            }
        });
    }

    public void lockWallpaperToDefaultPage() {
        mWallpaperOffset.setLockToDefaultPage(true);
    }

    public void unlockWallpaperFromDefaultPageOnNextLayout() {
        if (mWallpaperOffset.isLockedToDefaultPage()) {
            mUnlockWallpaperFromDefaultPageOnLayout = true;
            requestLayout();
        }
    }

    protected void snapToPage(int whichPage, Runnable r) {
        snapToPage(whichPage, SLOW_PAGE_SNAP_ANIMATION_DURATION, r);
    }

    protected void snapToPage(int whichPage, int duration, Runnable r) {
        if (mDelayedSnapToPageRunnable != null) {
            mDelayedSnapToPageRunnable.run();
        }
        mDelayedSnapToPageRunnable = r;
        snapToPage(whichPage, duration);
    }

    public void snapToScreenId(long screenId) {
        snapToScreenId(screenId, null);
    }

    protected void snapToScreenId(long screenId, Runnable r) {
        snapToPage(getPageIndexForScreenId(screenId), r);
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        mWallpaperOffset.syncWithScroll();
    }

    public void computeScrollWithoutInvalidation() {
        computeScrollHelper(false);
    }

    @Override
    protected void determineScrollingStart(MotionEvent ev, float touchSlopScale) {
        if (!isSwitchingState()) {
            super.determineScrollingStart(ev, touchSlopScale);
        }
    }

    @Override
    public void announceForAccessibility(CharSequence text) {
        // Don't announce if apps is on top of us.
        if (!mLauncher.isAppsViewVisible()) {
            super.announceForAccessibility(text);
        }
    }

    public void showOutlinesTemporarily() {
        if (!mIsPageInTransition && !isTouchActive()) {
            snapToPage(mCurrentPage);
        }
    }

    private void updatePageAlphaValues() {
        if (!workspaceInModalState() && !mIsSwitchingState) {
            int screenCenter = getScrollX() + getViewportWidth() / 2;
            for (int i = numCustomPages(); i < getChildCount(); i++) {
                CellLayout child = (CellLayout) getChildAt(i);
                if (child != null) {
                    float scrollProgress = getScrollProgress(screenCenter, child, i);
                    float alpha = 1 - Math.abs(scrollProgress);
                    if (mWorkspaceFadeInAdjacentScreens) {
                        child.getShortcutsAndWidgets().setAlpha(alpha);
                    } else {
                        // Pages that are off-screen aren't important for accessibility.
                        child.getShortcutsAndWidgets().setImportantForAccessibility(
                                alpha > 0 ? IMPORTANT_FOR_ACCESSIBILITY_AUTO
                                        : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                    }
                }
            }
        }
    }

    public boolean hasCustomContent() {
        return (mScreenOrder.size() > 0 && mScreenOrder.get(0) == CUSTOM_CONTENT_SCREEN_ID);
    }

    public int numCustomPages() {
        return hasCustomContent() ? 1 : 0;
    }

    public boolean isOnOrMovingToCustomContent() {
        return hasCustomContent() && getNextPage() == 0;
    }

    private void updateStateForCustomContent() {
        float translationX = 0;
        float progress = 0;
        if (hasCustomContent()) {
            int index = mScreenOrder.indexOf(CUSTOM_CONTENT_SCREEN_ID);

            int scrollDelta = getScrollX() - getScrollForPage(index) -
                    getLayoutTransitionOffsetForPage(index);
            float scrollRange = getScrollForPage(index + 1) - getScrollForPage(index);
            translationX = scrollRange - scrollDelta;
            progress = (scrollRange - scrollDelta) / scrollRange;

            if (mIsRtl) {
                translationX = Math.min(0, translationX);
            } else {
                translationX = Math.max(0, translationX);
            }
            progress = Math.max(0, progress);
        }

        if (Float.compare(progress, mLastCustomContentScrollProgress) == 0) return;

        CellLayout cc = mWorkspaceScreens.get(CUSTOM_CONTENT_SCREEN_ID);
        if (progress > 0 && cc.getVisibility() != VISIBLE && !workspaceInModalState()) {
            cc.setVisibility(VISIBLE);
        }

        mLastCustomContentScrollProgress = progress;

        // We should only update the drag layer background alpha if we are not in all apps or the
        // widgets tray
        if (mState == State.NORMAL) {
            mLauncher.getDragLayer().setBackgroundAlpha(progress == 1 ? 0 : progress * 0.8f);
        }

        if (mLauncher.getHotseat() != null) {
            mLauncher.getHotseat().setTranslationX(translationX);
        }

        if (mPageIndicator != null) {
            mPageIndicator.setTranslationX(translationX);
        }

        if (mCustomContentCallbacks != null) {
            mCustomContentCallbacks.onScrollProgressChanged(progress);
        }
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        IBinder windowToken = getWindowToken();
        mWallpaperOffset.setWindowToken(windowToken);
        computeScroll();
        mDragController.setWindowToken(windowToken);
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mWallpaperOffset.setWindowToken(null);
    }

    protected void onResume() {
        mWallpaperOffset.onResume();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (mUnlockWallpaperFromDefaultPageOnLayout) {
            mWallpaperOffset.setLockToDefaultPage(false);
            mUnlockWallpaperFromDefaultPageOnLayout = false;
        }
        if (mFirstLayout && mCurrentPage >= 0 && mCurrentPage < getChildCount()) {
            mWallpaperOffset.syncWithScroll();
            mWallpaperOffset.jumpToFinal();
        }
        super.onLayout(changed, left, top, right, bottom);
        updatePageAlphaValues();
    }

    @Override
    public int getDescendantFocusability() {
        if (workspaceInModalState()) {
            return ViewGroup.FOCUS_BLOCK_DESCENDANTS;
        }
        return super.getDescendantFocusability();
    }

    public boolean workspaceInModalState() {
        return mState != State.NORMAL;
    }

    /** Returns whether a drag should be allowed to be started from the current workspace state. */
    public boolean workspaceIconsCanBeDragged() {
        return mState == State.NORMAL || mState == State.SPRING_LOADED;
    }

    @Thunk void updateChildrenLayersEnabled(boolean force) {
        boolean small = mState == State.OVERVIEW || mIsSwitchingState;
        boolean enableChildrenLayers = force || small || mAnimatingViewIntoPlace || isPageInTransition();

        if (enableChildrenLayers != mChildrenLayersEnabled) {
            mChildrenLayersEnabled = enableChildrenLayers;
            if (mChildrenLayersEnabled) {
                enableHwLayersOnVisiblePages();
            } else {
                for (int i = 0; i < getPageCount(); i++) {
                    final CellLayout cl = (CellLayout) getChildAt(i);
                    cl.enableHardwareLayer(false);
                }
            }
        }
    }

    private void enableHwLayersOnVisiblePages() {
        if (mChildrenLayersEnabled) {
            final int screenCount = getChildCount();

            float visibleLeft = getViewportOffsetX();
            float visibleRight = visibleLeft + getViewportWidth();
            float scaleX = getScaleX();
            if (scaleX < 1 && scaleX > 0) {
                float mid = getMeasuredWidth() / 2;
                visibleLeft = mid - ((mid - visibleLeft) / scaleX);
                visibleRight = mid + ((visibleRight - mid) / scaleX);
            }

            int leftScreen = -1;
            int rightScreen = -1;
            for (int i = numCustomPages(); i < screenCount; i++) {
                final View child = getPageAt(i);

                float left = child.getLeft() + child.getTranslationX() - getScrollX();
                if (left <= visibleRight && (left + child.getMeasuredWidth()) >= visibleLeft) {
                    if (leftScreen == -1) {
                        leftScreen = i;
                    }
                    rightScreen = i;
                }
            }
            if (mForceDrawAdjacentPages) {
                // In overview mode, make sure that the two side pages are visible.
                leftScreen = Utilities.boundToRange(getCurrentPage() - 1,
                    numCustomPages(), rightScreen);
                rightScreen = Utilities.boundToRange(getCurrentPage() + 1,
                    leftScreen, getPageCount() - 1);
            }

            if (leftScreen == rightScreen) {
                // make sure we're caching at least two pages always
                if (rightScreen < screenCount - 1) {
                    rightScreen++;
                } else if (leftScreen > 0) {
                    leftScreen--;
                }
            }

            for (int i = numCustomPages(); i < screenCount; i++) {
                final CellLayout layout = (CellLayout) getPageAt(i);
                // enable layers between left and right screen inclusive.
                boolean enableLayer = leftScreen <= i && i <= rightScreen;
                layout.enableHardwareLayer(enableLayer);
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
        final int[] position = mTempXY;
        getLocationOnScreen(position);

        int pointerIndex = ev.getActionIndex();
        position[0] += (int) ev.getX(pointerIndex);
        position[1] += (int) ev.getY(pointerIndex);

        mWallpaperManager.sendWallpaperCommand(getWindowToken(),
                ev.getAction() == MotionEvent.ACTION_UP
                        ? WallpaperManager.COMMAND_TAP : WallpaperManager.COMMAND_SECONDARY_TAP,
                position[0], position[1], 0, null);
    }

    public void prepareDragWithProvider(DragPreviewProvider outlineProvider) {
        mOutlineProvider = outlineProvider;
    }

    public void exitWidgetResizeMode() {
        DragLayer dragLayer = mLauncher.getDragLayer();
        dragLayer.clearResizeFrame();
    }

    @Override
    protected void getFreeScrollPageRange(int[] range) {
        getOverviewModePages(range);
    }

    private void getOverviewModePages(int[] range) {
        int start = numCustomPages();
        int end = getChildCount() - 1;

        range[0] = Math.max(0, Math.min(start, getChildCount() - 1));
        range[1] = Math.max(0, end);
    }

    public void onStartReordering() {
        super.onStartReordering();
        // Reordering handles its own animations, disable the automatic ones.
        disableLayoutTransitions();
    }

    public void onEndReordering() {
        super.onEndReordering();

        if (mLauncher.isWorkspaceLoading()) {
            // Invalid and dangerous operation if workspace is loading
            return;
        }

        ArrayList<Long> prevScreenOrder = (ArrayList<Long>) mScreenOrder.clone();
        mScreenOrder.clear();
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            CellLayout cl = ((CellLayout) getChildAt(i));
            mScreenOrder.add(getIdForScreen(cl));
        }

        for (int i = 0; i < prevScreenOrder.size(); i++) {
            if (mScreenOrder.get(i) != prevScreenOrder.get(i)) {
                mLauncher.getUserEventDispatcher().logOverviewReorder();
                break;
            }
        }
        LauncherModel.updateWorkspaceScreenOrder(mLauncher, mScreenOrder);

        // Re-enable auto layout transitions for page deletion.
        enableLayoutTransitions();
    }

    public boolean isInOverviewMode() {
        return mState == State.OVERVIEW;
    }

    public void snapToPageFromOverView(int whichPage) {
        mStateTransitionAnimation.snapToPageFromOverView(whichPage);
    }

    int getOverviewModeTranslationY() {
        DeviceProfile grid = mLauncher.getDeviceProfile();
        int overviewButtonBarHeight = grid.getOverviewModeButtonBarHeight();

        int scaledHeight = (int) (mOverviewModeShrinkFactor * getNormalChildHeight());
        Rect workspacePadding = grid.getWorkspacePadding(sTempRect);
        int workspaceTop = mInsets.top + workspacePadding.top;
        int workspaceBottom = getViewportHeight() - mInsets.bottom - workspacePadding.bottom;
        int overviewTop = mInsets.top;
        int overviewBottom = getViewportHeight() - mInsets.bottom - overviewButtonBarHeight;
        int workspaceOffsetTopEdge = workspaceTop + ((workspaceBottom - workspaceTop) - scaledHeight) / 2;
        int overviewOffsetTopEdge = overviewTop + (overviewBottom - overviewTop - scaledHeight) / 2;
        return -workspaceOffsetTopEdge + overviewOffsetTopEdge;
    }

    float getSpringLoadedTranslationY() {
        DeviceProfile grid = mLauncher.getDeviceProfile();
        if (grid.isVerticalBarLayout() || getChildCount() == 0) {
            return 0;
        }

        float scaledHeight = grid.workspaceSpringLoadShrinkFactor * getNormalChildHeight();
        float shrunkTop = mInsets.top + grid.dropTargetBarSizePx;
        float shrunkBottom = getViewportHeight() - mInsets.bottom
                - grid.getWorkspacePadding(sTempRect).bottom
                - grid.workspaceSpringLoadedBottomSpace;
        float totalShrunkSpace = shrunkBottom - shrunkTop;

        float desiredCellTop = shrunkTop + (totalShrunkSpace - scaledHeight) / 2;

        float halfHeight = getHeight() / 2;
        float myCenter = getTop() + halfHeight;
        float cellTopFromCenter = halfHeight - getChildAt(0).getTop();
        float actualCellTop = myCenter - cellTopFromCenter * grid.workspaceSpringLoadShrinkFactor;
        return (desiredCellTop - actualCellTop) / grid.workspaceSpringLoadShrinkFactor;
    }

    float getOverviewModeShrinkFactor() {
        return mOverviewModeShrinkFactor;
    }

    /**
     * Sets the current workspace {@link State}, returning an animation transitioning the workspace
     * to that new state.
     */
    public Animator setStateWithAnimation(State toState, boolean animated,
            AnimationLayerSet layerViews) {
        final State fromState = mState;

        // Update the current state
        mState = toState;

        // Create the animation to the new state
        AnimatorSet workspaceAnim =  mStateTransitionAnimation.getAnimationToState(fromState,
                toState, animated, layerViews);

        boolean shouldNotifyWidgetChange = !fromState.shouldUpdateWidget
                && toState.shouldUpdateWidget;

        updateAccessibilityFlags();

        if (shouldNotifyWidgetChange) {
            mLauncher.notifyWidgetProvidersChanged();
        }

        onPrepareStateTransition(mState.hasMultipleVisiblePages);

        StateTransitionListener listener = new StateTransitionListener();
        if (animated) {
            ValueAnimator stepAnimator = ValueAnimator.ofFloat(0, 1);
            stepAnimator.addUpdateListener(listener);

            workspaceAnim.play(stepAnimator);
            workspaceAnim.addListener(listener);
        } else {
            listener.onAnimationStart(null);
            listener.onAnimationEnd(null);
        }

        return workspaceAnim;
    }

    public State getState() {
        return mState;
    }

    public void updateAccessibilityFlags() {
        // TODO: Update the accessibility flags appropriately when dragging.
        if (!mLauncher.getAccessibilityDelegate().isInAccessibleDrag()) {
            int total = getPageCount();
            for (int i = numCustomPages(); i < total; i++) {
                updateAccessibilityFlags((CellLayout) getPageAt(i), i);
            }
            setImportantForAccessibility((mState == State.NORMAL || mState == State.OVERVIEW)
                    ? IMPORTANT_FOR_ACCESSIBILITY_AUTO
                    : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
    }

    private void updateAccessibilityFlags(CellLayout page, int pageNo) {
        if (mState == State.OVERVIEW) {
            page.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            page.getShortcutsAndWidgets().setImportantForAccessibility(
                    IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            page.setContentDescription(getPageDescription(pageNo));

            // No custom action for the first page.
            if (!FeatureFlags.QSB_ON_FIRST_SCREEN || pageNo > 0) {
                if (mPagesAccessibilityDelegate == null) {
                    mPagesAccessibilityDelegate = new OverviewScreenAccessibilityDelegate(this);
                }
                page.setAccessibilityDelegate(mPagesAccessibilityDelegate);
            }
        } else {
            int accessible = mState == State.NORMAL ?
                    IMPORTANT_FOR_ACCESSIBILITY_AUTO :
                        IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS;
            page.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            page.getShortcutsAndWidgets().setImportantForAccessibility(accessible);
            page.setContentDescription(null);
            page.setAccessibilityDelegate(null);
        }
    }

    public void onPrepareStateTransition(boolean multiplePagesVisible) {
        mIsSwitchingState = true;
        mTransitionProgress = 0;

        if (multiplePagesVisible) {
            mForceDrawAdjacentPages = true;
        }
        invalidate(); // This will call dispatchDraw(), which calls getVisiblePages().

        updateChildrenLayersEnabled(false);
        hideCustomContentIfNecessary();
    }

    public void onEndStateTransition() {
        mIsSwitchingState = false;
        updateChildrenLayersEnabled(false);
        showCustomContentIfNecessary();
        mForceDrawAdjacentPages = false;
        mTransitionProgress = 1;
    }

    void updateCustomContentVisibility() {
        int visibility = mState == Workspace.State.NORMAL ? VISIBLE : INVISIBLE;
        setCustomContentVisibility(visibility);
    }

    void setCustomContentVisibility(int visibility) {
        if (hasCustomContent()) {
            mWorkspaceScreens.get(CUSTOM_CONTENT_SCREEN_ID).setVisibility(visibility);
        }
    }

    void showCustomContentIfNecessary() {
        boolean show  = mState == Workspace.State.NORMAL;
        if (show && hasCustomContent()) {
            mWorkspaceScreens.get(CUSTOM_CONTENT_SCREEN_ID).setVisibility(VISIBLE);
        }
    }

    void hideCustomContentIfNecessary() {
        boolean hide  = mState != Workspace.State.NORMAL;
        if (hide && hasCustomContent()) {
            disableLayoutTransitions();
            mWorkspaceScreens.get(CUSTOM_CONTENT_SCREEN_ID).setVisibility(INVISIBLE);
            enableLayoutTransitions();
        }
    }

    public void startDrag(CellLayout.CellInfo cellInfo, DragOptions options) {
        View child = cellInfo.cell;

        mDragInfo = cellInfo;
        child.setVisibility(INVISIBLE);

        if (options.isAccessibleDrag) {
            mDragController.addDragListener(new AccessibleDragListenerAdapter(
                    this, CellLayout.WORKSPACE_ACCESSIBILITY_DRAG) {
                @Override
                protected void enableAccessibleDrag(boolean enable) {
                    super.enableAccessibleDrag(enable);
                    setEnableForLayout(mLauncher.getHotseat().getLayout(),enable);

                    // We need to allow our individual children to become click handlers in this
                    // case, so temporarily unset the click handlers.
                    setOnClickListener(enable ? null : mLauncher);
                }
            });
        }

        beginDragShared(child, this, options);
    }

    public void beginDragShared(View child, DragSource source, DragOptions options) {
        Object dragObject = child.getTag();
        if (!(dragObject instanceof ItemInfo)) {
            String msg = "Drag started with a view that has no tag set. This "
                    + "will cause a crash (issue 11627249) down the line. "
                    + "View: " + child + "  tag: " + child.getTag();
            throw new IllegalStateException(msg);
        }
        beginDragShared(child, source, (ItemInfo) dragObject,
                new DragPreviewProvider(child), options);
    }


    public DragView beginDragShared(View child, DragSource source, ItemInfo dragObject,
            DragPreviewProvider previewProvider, DragOptions dragOptions) {
        child.clearFocus();
        child.setPressed(false);
        mOutlineProvider = previewProvider;

        // The drag bitmap follows the touch point around on the screen
        final Bitmap b = previewProvider.createDragBitmap(mCanvas);
        int halfPadding = previewProvider.previewPadding / 2;

        float scale = previewProvider.getScaleAndPosition(b, mTempXY);
        int dragLayerX = mTempXY[0];
        int dragLayerY = mTempXY[1];

        DeviceProfile grid = mLauncher.getDeviceProfile();
        Point dragVisualizeOffset = null;
        Rect dragRect = null;
        if (child instanceof BubbleTextView) {
            dragRect = new Rect();
            ((BubbleTextView) child).getIconBounds(dragRect);
            dragLayerY += dragRect.top;
            // Note: The dragRect is used to calculate drag layer offsets, but the
            // dragVisualizeOffset in addition to the dragRect (the size) to position the outline.
            dragVisualizeOffset = new Point(- halfPadding, halfPadding);
        } else if (child instanceof FolderIcon) {
            int previewSize = grid.folderIconSizePx;
            dragVisualizeOffset = new Point(- halfPadding, halfPadding - child.getPaddingTop());
            dragRect = new Rect(0, child.getPaddingTop(), child.getWidth(), previewSize);
        } else if (previewProvider instanceof ShortcutDragPreviewProvider) {
            dragVisualizeOffset = new Point(- halfPadding, halfPadding);
        }

        // Clear the pressed state if necessary
        if (child instanceof BubbleTextView) {
            BubbleTextView icon = (BubbleTextView) child;
            icon.clearPressedBackground();
        }

        if (child.getParent() instanceof ShortcutAndWidgetContainer) {
            mDragSourceInternal = (ShortcutAndWidgetContainer) child.getParent();
        }

        if (child instanceof BubbleTextView && !dragOptions.isAccessibleDrag) {
            PopupContainerWithArrow popupContainer = PopupContainerWithArrow
                    .showForIcon((BubbleTextView) child);
            if (popupContainer != null) {
                dragOptions.preDragCondition = popupContainer.createPreDragCondition();

                mLauncher.getUserEventDispatcher().resetElapsedContainerMillis();
            }
        }

        DragView dv = mDragController.startDrag(b, dragLayerX, dragLayerY, source,
                dragObject, dragVisualizeOffset, dragRect, scale, dragOptions);
        dv.setIntrinsicIconScaleFactor(source.getIntrinsicIconScaleFactor());
        b.recycle();
        return dv;
    }

    private boolean transitionStateShouldAllowDrop() {
        return ((!isSwitchingState() || mTransitionProgress > ALLOW_DROP_TRANSITION_PROGRESS) &&
                (mState == State.NORMAL || mState == State.SPRING_LOADED));
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

            mDragViewVisualCenter = d.getVisualCenter(mDragViewVisualCenter);

            // We want the point to be mapped to the dragTarget.
            if (mLauncher.isHotseatLayout(dropTargetLayout)) {
                mapPointFromSelfToHotseatLayout(mLauncher.getHotseat(), mDragViewVisualCenter);
            } else {
                mapPointFromSelfToChild(dropTargetLayout, mDragViewVisualCenter);
            }

            int spanX;
            int spanY;
            if (mDragInfo != null) {
                final CellLayout.CellInfo dragCellInfo = mDragInfo;
                spanX = dragCellInfo.spanX;
                spanY = dragCellInfo.spanY;
            } else {
                spanX = d.dragInfo.spanX;
                spanY = d.dragInfo.spanY;
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
            if (mCreateUserFolderOnDrop && willCreateUserFolder(d.dragInfo,
                    dropTargetLayout, mTargetCell, distance, true)) {
                return true;
            }

            if (mAddToExistingFolderOnDrop && willAddToExistingUserFolder(d.dragInfo,
                    dropTargetLayout, mTargetCell, distance)) {
                return true;
            }

            int[] resultSpan = new int[2];
            mTargetCell = dropTargetLayout.performReorder((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], minSpanX, minSpanY, spanX, spanY,
                    null, mTargetCell, resultSpan, CellLayout.MODE_ACCEPT_DROP);
            boolean foundCell = mTargetCell[0] >= 0 && mTargetCell[1] >= 0;

            // Don't accept the drop if there's no room for the item
            if (!foundCell) {
                onNoCellFound(dropTargetLayout);
                return false;
            }
        }

        long screenId = getIdForScreen(dropTargetLayout);
        if (screenId == EXTRA_EMPTY_SCREEN_ID) {
            commitExtraEmptyScreen();
        }

        return true;
    }

    boolean willCreateUserFolder(ItemInfo info, CellLayout target, int[] targetCell,
            float distance, boolean considerTimeout) {
        if (distance > mMaxDistanceForFolderCreation) return false;
        View dropOverView = target.getChildAt(targetCell[0], targetCell[1]);
        return willCreateUserFolder(info, dropOverView, considerTimeout);
    }

    boolean willCreateUserFolder(ItemInfo info, View dropOverView, boolean considerTimeout) {
        if (dropOverView != null) {
            CellLayout.LayoutParams lp = (CellLayout.LayoutParams) dropOverView.getLayoutParams();
            if (lp.useTmpCoords && (lp.tmpCellX != lp.cellX || lp.tmpCellY != lp.cellY)) {
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

        boolean aboveShortcut = (dropOverView.getTag() instanceof ShortcutInfo);
        boolean willBecomeShortcut =
                (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION ||
                        info.itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT ||
                        info.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT);

        return (aboveShortcut && willBecomeShortcut);
    }

    boolean willAddToExistingUserFolder(ItemInfo dragInfo, CellLayout target, int[] targetCell,
            float distance) {
        if (distance > mMaxDistanceForFolderCreation) return false;
        View dropOverView = target.getChildAt(targetCell[0], targetCell[1]);
        return willAddToExistingUserFolder(dragInfo, dropOverView);

    }
    boolean willAddToExistingUserFolder(ItemInfo dragInfo, View dropOverView) {
        if (dropOverView != null) {
            CellLayout.LayoutParams lp = (CellLayout.LayoutParams) dropOverView.getLayoutParams();
            if (lp.useTmpCoords && (lp.tmpCellX != lp.cellX || lp.tmpCellY != lp.cellY)) {
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
        final long screenId = getIdForScreen(target);

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
                mLauncher.addFolder(target, container, screenId, targetCell[0], targetCell[1]);
            destInfo.cellX = -1;
            destInfo.cellY = -1;
            sourceInfo.cellX = -1;
            sourceInfo.cellY = -1;

            // If the dragView is null, we can't animate
            boolean animate = dragView != null;
            if (animate) {
                // In order to keep everything continuous, we hand off the currently rendered
                // folder background to the newly created icon. This preserves animation state.
                fi.setFolderBackground(mFolderCreateBg);
                mFolderCreateBg = new PreviewBackground();
                fi.performCreateAnimation(destInfo, v, sourceInfo, dragView, folderLocation, scale,
                        postAnimationRunnable);
            } else {
                fi.prepareCreateAnimation(v);
                fi.addItem(destInfo);
                fi.addItem(sourceInfo);
            }
            return true;
        }
        return false;
    }

    boolean addToExistingFolderIfNecessary(View newView, CellLayout target, int[] targetCell,
            float distance, DragObject d, boolean external) {
        if (distance > mMaxDistanceForFolderCreation) return false;

        View dropOverView = target.getChildAt(targetCell[0], targetCell[1]);
        if (!mAddToExistingFolderOnDrop) return false;
        mAddToExistingFolderOnDrop = false;

        if (dropOverView instanceof FolderIcon) {
            FolderIcon fi = (FolderIcon) dropOverView;
            if (fi.acceptDrop(d.dragInfo)) {
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

    @Override
    public void prepareAccessibilityDrop() { }

    public void onDrop(final DragObject d) {
        mDragViewVisualCenter = d.getVisualCenter(mDragViewVisualCenter);
        CellLayout dropTargetLayout = mDropToLayout;

        // We want the point to be mapped to the dragTarget.
        if (dropTargetLayout != null) {
            if (mLauncher.isHotseatLayout(dropTargetLayout)) {
                mapPointFromSelfToHotseatLayout(mLauncher.getHotseat(), mDragViewVisualCenter);
            } else {
                mapPointFromSelfToChild(dropTargetLayout, mDragViewVisualCenter);
            }
        }

        boolean droppedOnOriginalCell = false;

        int snapScreen = -1;
        boolean resizeOnDrop = false;
        if (d.dragSource != this) {
            final int[] touchXY = new int[] { (int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1] };
            onDropExternal(touchXY, dropTargetLayout, d);
        } else if (mDragInfo != null) {
            final View cell = mDragInfo.cell;
            boolean droppedOnOriginalCellDuringTransition = false;

            if (dropTargetLayout != null && !d.cancelled) {
                // Move internally
                boolean hasMovedLayouts = (getParentCellLayoutForView(cell) != dropTargetLayout);
                boolean hasMovedIntoHotseat = mLauncher.isHotseatLayout(dropTargetLayout);
                long container = hasMovedIntoHotseat ?
                        LauncherSettings.Favorites.CONTAINER_HOTSEAT :
                        LauncherSettings.Favorites.CONTAINER_DESKTOP;
                long screenId = (mTargetCell[0] < 0) ?
                        mDragInfo.screenId : getIdForScreen(dropTargetLayout);
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
                if (createUserFolderIfNecessary(cell, container,
                        dropTargetLayout, mTargetCell, distance, false, d.dragView, null)) {
                    return;
                }

                if (addToExistingFolderIfNecessary(cell, dropTargetLayout, mTargetCell,
                        distance, d, false)) {
                    return;
                }

                // Aside from the special case where we're dropping a shortcut onto a shortcut,
                // we need to find the nearest cell location that is vacant
                ItemInfo item = d.dragInfo;
                int minSpanX = item.spanX;
                int minSpanY = item.spanY;
                if (item.minSpanX > 0 && item.minSpanY > 0) {
                    minSpanX = item.minSpanX;
                    minSpanY = item.minSpanY;
                }

                droppedOnOriginalCell = item.screenId == screenId && item.container == container
                        && item.cellX == mTargetCell[0] && item.cellY == mTargetCell[1];
                droppedOnOriginalCellDuringTransition = droppedOnOriginalCell && mIsSwitchingState;

                // When quickly moving an item, a user may accidentally rearrange their
                // workspace. So instead we move the icon back safely to its original position.
                boolean returnToOriginalCellToPreventShuffling = !isFinishedSwitchingState()
                        && !droppedOnOriginalCellDuringTransition && !dropTargetLayout
                        .isRegionVacant(mTargetCell[0], mTargetCell[1], spanX, spanY);
                int[] resultSpan = new int[2];
                if (returnToOriginalCellToPreventShuffling) {
                    mTargetCell[0] = mTargetCell[1] = -1;
                } else {
                    mTargetCell = dropTargetLayout.performReorder((int) mDragViewVisualCenter[0],
                            (int) mDragViewVisualCenter[1], minSpanX, minSpanY, spanX, spanY, cell,
                            mTargetCell, resultSpan, CellLayout.MODE_ON_DROP);
                }

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

                if (foundCell) {
                    if (getScreenIdForPageIndex(mCurrentPage) != screenId && !hasMovedIntoHotseat) {
                        snapScreen = getPageIndexForScreenId(screenId);
                        snapToPage(snapScreen);
                    }

                    final ItemInfo info = (ItemInfo) cell.getTag();
                    if (hasMovedLayouts) {
                        // Reparent the view
                        CellLayout parentCell = getParentCellLayoutForView(cell);
                        if (parentCell != null) {
                            parentCell.removeView(cell);
                        } else if (FeatureFlags.IS_DOGFOOD_BUILD) {
                            throw new NullPointerException("mDragInfo.cell has null parent");
                        }
                        addInScreen(cell, container, screenId, mTargetCell[0], mTargetCell[1],
                                info.spanX, info.spanY);
                    }

                    // update the item's position after drop
                    CellLayout.LayoutParams lp = (CellLayout.LayoutParams) cell.getLayoutParams();
                    lp.cellX = lp.tmpCellX = mTargetCell[0];
                    lp.cellY = lp.tmpCellY = mTargetCell[1];
                    lp.cellHSpan = item.spanX;
                    lp.cellVSpan = item.spanY;
                    lp.isLockedToGrid = true;

                    if (container != LauncherSettings.Favorites.CONTAINER_HOTSEAT &&
                            cell instanceof LauncherAppWidgetHostView) {
                        final CellLayout cellLayout = dropTargetLayout;
                        // We post this call so that the widget has a chance to be placed
                        // in its final location

                        final LauncherAppWidgetHostView hostView = (LauncherAppWidgetHostView) cell;
                        LauncherAppWidgetProviderInfo pInfo = (LauncherAppWidgetProviderInfo) hostView.getAppWidgetInfo();
                        if (pInfo != null && (pInfo.resizeMode != AppWidgetProviderInfo.RESIZE_NONE || pInfo.minSpanX > 1 || pInfo.minSpanY > 1)
                                && !d.accessibleDrag) {
                            mDelayedResizeRunnable = new Runnable() {
                                public void run() {
                                    if (!isPageInTransition()) {
                                        DragLayer dragLayer = mLauncher.getDragLayer();
                                        dragLayer.addResizeFrame(hostView, cellLayout);
                                    }
                                }
                            };
                        }
                    }

                    mLauncher.getModelWriter().modifyItemInDatabase(info, container, screenId,
                            lp.cellX, lp.cellY, item.spanX, item.spanY);
                } else {
                    if (!returnToOriginalCellToPreventShuffling) {
                        onNoCellFound(dropTargetLayout);
                    }

                    // If we can't find a drop location, we return the item to its original position
                    CellLayout.LayoutParams lp = (CellLayout.LayoutParams) cell.getLayoutParams();
                    mTargetCell[0] = lp.cellX;
                    mTargetCell[1] = lp.cellY;
                    CellLayout layout = (CellLayout) cell.getParent().getParent();
                    layout.markCellsAsOccupiedForView(cell);
                }
            }

            final CellLayout parent = (CellLayout) cell.getParent().getParent();
            // Prepare it to be animated into its new position
            // This must be called after the view has been re-parented
            final Runnable onCompleteRunnable = new Runnable() {
                @Override
                public void run() {
                    mAnimatingViewIntoPlace = false;
                    updateChildrenLayersEnabled(false);
                }
            };
            mAnimatingViewIntoPlace = true;
            if (d.dragView.hasDrawn()) {
                if (droppedOnOriginalCellDuringTransition) {
                    // Animate the item to its original position, while simultaneously exiting
                    // spring-loaded mode so the page meets the icon where it was picked up.
                    mLauncher.getDragController().animateDragViewToOriginalPosition(
                            mDelayedResizeRunnable, cell,
                            mStateTransitionAnimation.mSpringLoadedTransitionTime);
                    mLauncher.exitSpringLoadedDragMode();
                    mLauncher.getDropTargetBar().onDragEnd();
                    parent.onDropChild(cell);
                    return;
                }
                final ItemInfo info = (ItemInfo) cell.getTag();
                boolean isWidget = info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
                        || info.itemType == LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET;
                if (isWidget) {
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
        if (d.stateAnnouncer != null && !droppedOnOriginalCell) {
            d.stateAnnouncer.completeAction(R.string.item_moved);
        }
    }

    public void onNoCellFound(View dropTargetLayout) {
        if (mLauncher.isHotseatLayout(dropTargetLayout)) {
            Hotseat hotseat = mLauncher.getHotseat();
            boolean droppedOnAllAppsIcon = !FeatureFlags.NO_ALL_APPS_ICON
                    && mTargetCell != null && !mLauncher.getDeviceProfile().inv.isAllAppsButtonRank(
                    hotseat.getOrderInHotseat(mTargetCell[0], mTargetCell[1]));
            if (!droppedOnAllAppsIcon) {
                // Only show message when hotseat is full and drop target was not AllApps button
                showOutOfSpaceMessage(true);
            }
        } else {
            showOutOfSpaceMessage(false);
        }
    }

    private void showOutOfSpaceMessage(boolean isHotseatLayout) {
        int strId = (isHotseatLayout ? R.string.hotseat_out_of_space : R.string.out_of_space);
        Toast.makeText(mLauncher, mLauncher.getString(strId), Toast.LENGTH_SHORT).show();
    }

    /**
     * Computes the area relative to dragLayer which is used to display a page.
     */
    public void getPageAreaRelativeToDragLayer(Rect outArea) {
        CellLayout child = (CellLayout) getChildAt(getNextPage());
        if (child == null) {
            return;
        }
        ShortcutAndWidgetContainer boundingLayout = child.getShortcutsAndWidgets();

        // Use the absolute left instead of the child left, as we want the visible area
        // irrespective of the visible child. Since the view can only scroll horizontally, the
        // top position is not affected.
        mTempXY[0] = getViewportOffsetX() + getPaddingLeft() + boundingLayout.getLeft();
        mTempXY[1] = child.getTop() + boundingLayout.getTop();

        float scale = mLauncher.getDragLayer().getDescendantCoordRelativeToSelf(this, mTempXY);
        outArea.set(mTempXY[0], mTempXY[1],
                (int) (mTempXY[0] + scale * boundingLayout.getMeasuredWidth()),
                (int) (mTempXY[1] + scale * boundingLayout.getMeasuredHeight()));
    }

    @Override
    public void onDragEnter(DragObject d) {
        if (ENFORCE_DRAG_EVENT_ORDER) {
            enforceDragParity("onDragEnter", 1, 1);
        }

        mCreateUserFolderOnDrop = false;
        mAddToExistingFolderOnDrop = false;

        mDropToLayout = null;
        mDragViewVisualCenter = d.getVisualCenter(mDragViewVisualCenter);
        setDropLayoutForDragObject(d, mDragViewVisualCenter[0], mDragViewVisualCenter[1]);
    }

    @Override
    public void onDragExit(DragObject d) {
        if (ENFORCE_DRAG_EVENT_ORDER) {
            enforceDragParity("onDragExit", -1, 0);
        }

        // Here we store the final page that will be dropped to, if the workspace in fact
        // receives the drop
        mDropToLayout = mDragTargetLayout;
        if (mDragMode == DRAG_MODE_CREATE_FOLDER) {
            mCreateUserFolderOnDrop = true;
        } else if (mDragMode == DRAG_MODE_ADD_TO_FOLDER) {
            mAddToExistingFolderOnDrop = true;
        }

        // Reset the previous drag target
        setCurrentDropLayout(null);
        setCurrentDragOverlappingLayout(null);

        mSpringLoadedDragController.cancel();
    }

    private void enforceDragParity(String event, int update, int expectedValue) {
        enforceDragParity(this, event, update, expectedValue);
        for (int i = 0; i < getChildCount(); i++) {
            enforceDragParity(getChildAt(i), event, update, expectedValue);
        }
    }

    private void enforceDragParity(View v, String event, int update, int expectedValue) {
        Object tag = v.getTag(R.id.drag_event_parity);
        int value = tag == null ? 0 : (Integer) tag;
        value += update;
        v.setTag(R.id.drag_event_parity, value);

        if (value != expectedValue) {
            Log.e(TAG, event + ": Drag contract violated: " + value);
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

    void setCurrentDragOverlappingLayout(CellLayout layout) {
        if (mDragOverlappingLayout != null) {
            mDragOverlappingLayout.setIsDragOverlapping(false);
        }
        mDragOverlappingLayout = layout;
        if (mDragOverlappingLayout != null) {
            mDragOverlappingLayout.setIsDragOverlapping(true);
        }
        // Invalidating the scrim will also force this CellLayout
        // to be invalidated so that it is highlighted if necessary.
        mLauncher.getDragLayer().invalidateScrim();
    }

    public CellLayout getCurrentDragOverlappingLayout() {
        return mDragOverlappingLayout;
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
        if (mFolderCreateBg != null) {
            mFolderCreateBg.animateToRest();
        }
        mFolderCreationAlarm.setOnAlarmListener(null);
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

   /*
    *
    * Convert the 2D coordinate xy from the parent View's coordinate space to this CellLayout's
    * coordinate space. The argument xy is modified with the return result.
    */
   void mapPointFromSelfToChild(View v, float[] xy) {
       xy[0] = xy[0] - v.getLeft();
       xy[1] = xy[1] - v.getTop();
   }

   boolean isPointInSelfOverHotseat(int x, int y) {
       mTempXY[0] = x;
       mTempXY[1] = y;
       mLauncher.getDragLayer().getDescendantCoordRelativeToSelf(this, mTempXY, true);
       View hotseat = mLauncher.getHotseat();
       return mTempXY[0] >= hotseat.getLeft() &&
               mTempXY[0] <= hotseat.getRight() &&
               mTempXY[1] >= hotseat.getTop() &&
               mTempXY[1] <= hotseat.getBottom();
   }

   void mapPointFromSelfToHotseatLayout(Hotseat hotseat, float[] xy) {
       mTempXY[0] = (int) xy[0];
       mTempXY[1] = (int) xy[1];
       mLauncher.getDragLayer().getDescendantCoordRelativeToSelf(this, mTempXY, true);
       mLauncher.getDragLayer().mapCoordInSelfToDescendant(hotseat.getLayout(), mTempXY);

       xy[0] = mTempXY[0];
       xy[1] = mTempXY[1];
   }

   /*
    *
    * Convert the 2D coordinate xy from this CellLayout's coordinate space to
    * the parent View's coordinate space. The argument xy is modified with the return result.
    *
    */
   void mapPointFromChildToSelf(View v, float[] xy) {
       xy[0] += v.getLeft();
       xy[1] += v.getTop();
   }

    private boolean isDragWidget(DragObject d) {
        return (d.dragInfo instanceof LauncherAppWidgetInfo ||
                d.dragInfo instanceof PendingAddWidgetInfo);
    }

    public void onDragOver(DragObject d) {
        // Skip drag over events while we are dragging over side pages
        if (!transitionStateShouldAllowDrop()) return;

        ItemInfo item = d.dragInfo;
        if (item == null) {
            if (FeatureFlags.IS_DOGFOOD_BUILD) {
                throw new NullPointerException("DragObject has null info");
            }
            return;
        }

        // Ensure that we have proper spans for the item that we are dropping
        if (item.spanX < 0 || item.spanY < 0) throw new RuntimeException("Improper spans found");
        mDragViewVisualCenter = d.getVisualCenter(mDragViewVisualCenter);

        final View child = (mDragInfo == null) ? null : mDragInfo.cell;
        if (setDropLayoutForDragObject(d, mDragViewVisualCenter[0], mDragViewVisualCenter[1])) {
            if (mLauncher.isHotseatLayout(mDragTargetLayout)) {
                mSpringLoadedDragController.cancel();
            } else {
                mSpringLoadedDragController.setAlarm(mDragTargetLayout);
            }
        }

        // Handle the drag over
        if (mDragTargetLayout != null) {
            // We want the point to be mapped to the dragTarget.
            if (mLauncher.isHotseatLayout(mDragTargetLayout)) {
                mapPointFromSelfToHotseatLayout(mLauncher.getHotseat(), mDragViewVisualCenter);
            } else {
                mapPointFromSelfToChild(mDragTargetLayout, mDragViewVisualCenter);
            }

            int minSpanX = item.spanX;
            int minSpanY = item.spanY;
            if (item.minSpanX > 0 && item.minSpanY > 0) {
                minSpanX = item.minSpanX;
                minSpanY = item.minSpanY;
            }

            mTargetCell = findNearestArea((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], minSpanX, minSpanY,
                    mDragTargetLayout, mTargetCell);
            int reorderX = mTargetCell[0];
            int reorderY = mTargetCell[1];

            setCurrentDropOverCell(mTargetCell[0], mTargetCell[1]);

            float targetCellDistance = mDragTargetLayout.getDistanceFromCell(
                    mDragViewVisualCenter[0], mDragViewVisualCenter[1], mTargetCell);

            manageFolderFeedback(mDragTargetLayout, mTargetCell, targetCellDistance, d);

            boolean nearestDropOccupied = mDragTargetLayout.isNearestDropLocationOccupied((int)
                    mDragViewVisualCenter[0], (int) mDragViewVisualCenter[1], item.spanX,
                    item.spanY, child, mTargetCell);

            if (!nearestDropOccupied) {
                mDragTargetLayout.visualizeDropLocation(child, mOutlineProvider,
                        mTargetCell[0], mTargetCell[1], item.spanX, item.spanY, false, d);
            } else if ((mDragMode == DRAG_MODE_NONE || mDragMode == DRAG_MODE_REORDER)
                    && !mReorderAlarm.alarmPending() && (mLastReorderX != reorderX ||
                    mLastReorderY != reorderY)) {

                int[] resultSpan = new int[2];
                mDragTargetLayout.performReorder((int) mDragViewVisualCenter[0],
                        (int) mDragViewVisualCenter[1], minSpanX, minSpanY, item.spanX, item.spanY,
                        child, mTargetCell, resultSpan, CellLayout.MODE_SHOW_REORDER_HINT);

                // Otherwise, if we aren't adding to or creating a folder and there's no pending
                // reorder, then we schedule a reorder
                ReorderAlarmListener listener = new ReorderAlarmListener(mDragViewVisualCenter,
                        minSpanX, minSpanY, item.spanX, item.spanY, d, child);
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

    /**
     * Updates {@link #mDragTargetLayout} and {@link #mDragOverlappingLayout}
     * based on the DragObject's position.
     *
     * The layout will be:
     * - The Hotseat if the drag object is over it
     * - A side page if we are in spring-loaded mode and the drag object is over it
     * - The current page otherwise
     *
     * @return whether the layout is different from the current {@link #mDragTargetLayout}.
     */
    private boolean setDropLayoutForDragObject(DragObject d, float centerX, float centerY) {
        CellLayout layout = null;
        // Test to see if we are over the hotseat first
        if (mLauncher.getHotseat() != null && !isDragWidget(d)) {
            if (isPointInSelfOverHotseat(d.x, d.y)) {
                layout = mLauncher.getHotseat().getLayout();
            }
        }

        int nextPage = getNextPage();
        if (layout == null && !isPageInTransition()) {
            // Check if the item is dragged over left page
            mTempTouchCoordinates[0] = Math.min(centerX, d.x);
            mTempTouchCoordinates[1] = d.y;
            layout = verifyInsidePage(nextPage + (mIsRtl ? 1 : -1), mTempTouchCoordinates);
        }

        if (layout == null && !isPageInTransition()) {
            // Check if the item is dragged over right page
            mTempTouchCoordinates[0] = Math.max(centerX, d.x);
            mTempTouchCoordinates[1] = d.y;
            layout = verifyInsidePage(nextPage + (mIsRtl ? -1 : 1), mTempTouchCoordinates);
        }

        // Always pick the current page.
        if (layout == null && nextPage >= numCustomPages() && nextPage < getPageCount()) {
            layout = (CellLayout) getChildAt(nextPage);
        }
        if (layout != mDragTargetLayout) {
            setCurrentDropLayout(layout);
            setCurrentDragOverlappingLayout(layout);
            return true;
        }
        return false;
    }

    /**
     * Returns the child CellLayout if the point is inside the page coordinates, null otherwise.
     */
    private CellLayout verifyInsidePage(int pageNo, float[] touchXy)  {
        if (pageNo >= numCustomPages() && pageNo < getPageCount()) {
            CellLayout cl = (CellLayout) getChildAt(pageNo);
            mapPointFromSelfToChild(cl, touchXy);
            if (touchXy[0] >= 0 && touchXy[0] <= cl.getWidth() &&
                    touchXy[1] >= 0 && touchXy[1] <= cl.getHeight()) {
                // This point is inside the cell layout
                return cl;
            }
        }
        return null;
    }

    private void manageFolderFeedback(CellLayout targetLayout,
            int[] targetCell, float distance, DragObject dragObject) {
        if (distance > mMaxDistanceForFolderCreation) return;

        final View dragOverView = mDragTargetLayout.getChildAt(mTargetCell[0], mTargetCell[1]);
        ItemInfo info = dragObject.dragInfo;
        boolean userFolderPending = willCreateUserFolder(info, dragOverView, false);
        if (mDragMode == DRAG_MODE_NONE && userFolderPending &&
                !mFolderCreationAlarm.alarmPending()) {

            FolderCreationAlarmListener listener = new
                    FolderCreationAlarmListener(targetLayout, targetCell[0], targetCell[1]);

            if (!dragObject.accessibleDrag) {
                mFolderCreationAlarm.setOnAlarmListener(listener);
                mFolderCreationAlarm.setAlarm(FOLDER_CREATION_TIMEOUT);
            } else {
                listener.onAlarm(mFolderCreationAlarm);
            }

            if (dragObject.stateAnnouncer != null) {
                dragObject.stateAnnouncer.announce(WorkspaceAccessibilityHelper
                        .getDescriptionForDropOver(dragOverView, getContext()));
            }
            return;
        }

        boolean willAddToFolder = willAddToExistingUserFolder(info, dragOverView);
        if (willAddToFolder && mDragMode == DRAG_MODE_NONE) {
            mDragOverFolderIcon = ((FolderIcon) dragOverView);
            mDragOverFolderIcon.onDragEnter(info);
            if (targetLayout != null) {
                targetLayout.clearDragOutlines();
            }
            setDragMode(DRAG_MODE_ADD_TO_FOLDER);

            if (dragObject.stateAnnouncer != null) {
                dragObject.stateAnnouncer.announce(WorkspaceAccessibilityHelper
                        .getDescriptionForDropOver(dragOverView, getContext()));
            }
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
        final CellLayout layout;
        final int cellX;
        final int cellY;

        final PreviewBackground bg = new PreviewBackground();

        public FolderCreationAlarmListener(CellLayout layout, int cellX, int cellY) {
            this.layout = layout;
            this.cellX = cellX;
            this.cellY = cellY;

            BubbleTextView cell = (BubbleTextView) layout.getChildAt(cellX, cellY);
            bg.setup(mLauncher, null, cell.getMeasuredWidth(), cell.getPaddingTop());

            // The full preview background should appear behind the icon
            bg.isClipping = false;
        }

        public void onAlarm(Alarm alarm) {
            mFolderCreateBg = bg;
            mFolderCreateBg.animateToAccept(layout, cellX, cellY);
            layout.clearDragOutlines();
            setDragMode(DRAG_MODE_CREATE_FOLDER);
        }
    }

    class ReorderAlarmListener implements OnAlarmListener {
        final float[] dragViewCenter;
        final int minSpanX, minSpanY, spanX, spanY;
        final DragObject dragObject;
        final View child;

        public ReorderAlarmListener(float[] dragViewCenter, int minSpanX, int minSpanY, int spanX,
                int spanY, DragObject dragObject, View child) {
            this.dragViewCenter = dragViewCenter;
            this.minSpanX = minSpanX;
            this.minSpanY = minSpanY;
            this.spanX = spanX;
            this.spanY = spanY;
            this.child = child;
            this.dragObject = dragObject;
        }

        public void onAlarm(Alarm alarm) {
            int[] resultSpan = new int[2];
            mTargetCell = findNearestArea((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], minSpanX, minSpanY, mDragTargetLayout,
                    mTargetCell);
            mLastReorderX = mTargetCell[0];
            mLastReorderY = mTargetCell[1];

            mTargetCell = mDragTargetLayout.performReorder((int) mDragViewVisualCenter[0],
                (int) mDragViewVisualCenter[1], minSpanX, minSpanY, spanX, spanY,
                child, mTargetCell, resultSpan, CellLayout.MODE_DRAG_OVER);

            if (mTargetCell[0] < 0 || mTargetCell[1] < 0) {
                mDragTargetLayout.revertTempState();
            } else {
                setDragMode(DRAG_MODE_REORDER);
            }

            boolean resize = resultSpan[0] != spanX || resultSpan[1] != spanY;
            mDragTargetLayout.visualizeDropLocation(child, mOutlineProvider,
                mTargetCell[0], mTargetCell[1], resultSpan[0], resultSpan[1], resize, dragObject);
        }
    }

    @Override
    public void getHitRectRelativeToDragLayer(Rect outRect) {
        // We want the workspace to have the whole area of the display (it will find the correct
        // cell layout to drop to in the existing drag/drop logic.
        mLauncher.getDragLayer().getDescendantRectRelativeToSelf(this, outRect);
    }

    /**
     * Drop an item that didn't originate on one of the workspace screens.
     * It may have come from Launcher (e.g. from all apps or customize), or it may have
     * come from another app altogether.
     *
     * NOTE: This can also be called when we are outside of a drag event, when we want
     * to add an item to one of the workspace screens.
     */
    private void onDropExternal(final int[] touchXY, final CellLayout cellLayout, DragObject d) {
        final Runnable exitSpringLoadedRunnable = new Runnable() {
            @Override
            public void run() {
                mLauncher.exitSpringLoadedDragModeDelayed(true,
                        Launcher.EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT, null);
            }
        };

        if (d.dragInfo instanceof PendingAddShortcutInfo) {
            ShortcutInfo si = ((PendingAddShortcutInfo) d.dragInfo)
                    .activityInfo.createShortcutInfo();
            if (si != null) {
                d.dragInfo = si;
            }
        }

        ItemInfo info = d.dragInfo;
        int spanX = info.spanX;
        int spanY = info.spanY;
        if (mDragInfo != null) {
            spanX = mDragInfo.spanX;
            spanY = mDragInfo.spanY;
        }

        final long container = mLauncher.isHotseatLayout(cellLayout) ?
                LauncherSettings.Favorites.CONTAINER_HOTSEAT :
                    LauncherSettings.Favorites.CONTAINER_DESKTOP;
        final long screenId = getIdForScreen(cellLayout);
        if (!mLauncher.isHotseatLayout(cellLayout)
                && screenId != getScreenIdForPageIndex(mCurrentPage)
                && mState != State.SPRING_LOADED) {
            snapToScreenId(screenId, null);
        }

        if (info instanceof PendingAddItemInfo) {
            final PendingAddItemInfo pendingInfo = (PendingAddItemInfo) info;

            boolean findNearestVacantCell = true;
            if (pendingInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT) {
                mTargetCell = findNearestArea(touchXY[0], touchXY[1], spanX, spanY,
                        cellLayout, mTargetCell);
                float distance = cellLayout.getDistanceFromCell(mDragViewVisualCenter[0],
                        mDragViewVisualCenter[1], mTargetCell);
                if (willCreateUserFolder(d.dragInfo, cellLayout, mTargetCell, distance, true)
                        || willAddToExistingUserFolder(
                                d.dragInfo, cellLayout, mTargetCell, distance)) {
                    findNearestVacantCell = false;
                }
            }

            final ItemInfo item = d.dragInfo;
            boolean updateWidgetSize = false;
            if (findNearestVacantCell) {
                int minSpanX = item.spanX;
                int minSpanY = item.spanY;
                if (item.minSpanX > 0 && item.minSpanY > 0) {
                    minSpanX = item.minSpanX;
                    minSpanY = item.minSpanY;
                }
                int[] resultSpan = new int[2];
                mTargetCell = cellLayout.performReorder((int) mDragViewVisualCenter[0],
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
                    // Normally removeExtraEmptyScreen is called in Workspace#onDragEnd, but when
                    // adding an item that may not be dropped right away (due to a config activity)
                    // we defer the removal until the activity returns.
                    deferRemoveExtraEmptyScreen();

                    // When dragging and dropping from customization tray, we deal with creating
                    // widgets/shortcuts/folders in a slightly different way
                    mLauncher.addPendingItem(pendingInfo, container, screenId, mTargetCell,
                            item.spanX, item.spanY);
                }
            };
            boolean isWidget = pendingInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
                    || pendingInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET;

            AppWidgetHostView finalView = isWidget ?
                    ((PendingAddWidgetInfo) pendingInfo).boundWidget : null;

            if (finalView != null && updateWidgetSize) {
                AppWidgetResizeFrame.updateWidgetSizeRanges(finalView, mLauncher, item.spanX,
                        item.spanY);
            }

            int animationStyle = ANIMATE_INTO_POSITION_AND_DISAPPEAR;
            if (isWidget && ((PendingAddWidgetInfo) pendingInfo).info != null &&
                    ((PendingAddWidgetInfo) pendingInfo).getHandler().needsConfigure()) {
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
            case LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT:
                if (info.container == NO_ID && info instanceof AppInfo) {
                    // Came from all apps -- make a copy
                    info = ((AppInfo) info).makeShortcut();
                    d.dragInfo = info;
                }
                view = mLauncher.createShortcut(cellLayout, (ShortcutInfo) info);
                break;
            case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                view = FolderIcon.fromXml(R.layout.folder_icon, mLauncher, cellLayout,
                        (FolderInfo) info);
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
                if (addToExistingFolderIfNecessary(view, cellLayout, mTargetCell, distance, d,
                        true)) {
                    return;
                }
            }

            if (touchXY != null) {
                // when dragging and dropping, just find the closest free spot
                mTargetCell = cellLayout.performReorder((int) mDragViewVisualCenter[0],
                        (int) mDragViewVisualCenter[1], 1, 1, 1, 1,
                        null, mTargetCell, null, CellLayout.MODE_ON_DROP_EXTERNAL);
            } else {
                cellLayout.findCellForSpan(mTargetCell, 1, 1);
            }
            // Add the item to DB before adding to screen ensures that the container and other
            // values of the info is properly updated.
            mLauncher.getModelWriter().addOrMoveItemInDatabase(info, container, screenId,
                    mTargetCell[0], mTargetCell[1]);

            addInScreen(view, container, screenId, mTargetCell[0], mTargetCell[1],
                    info.spanX, info.spanY);
            cellLayout.onDropChild(view);
            cellLayout.getShortcutsAndWidgets().measureChild(view);

            if (d.dragView != null) {
                // We wrap the animation call in the temporary set and reset of the current
                // cellLayout to its final transform -- this means we animate the drag view to
                // the correct final location.
                setFinalTransitionTransform(cellLayout);
                mLauncher.getDragLayer().animateViewIntoPosition(d.dragView, view,
                        exitSpringLoadedRunnable, this);
                resetTransitionTransform(cellLayout);
            }
        }
    }

    public Bitmap createWidgetBitmap(ItemInfo widgetInfo, View layout) {
        int[] unScaledSize = mLauncher.getWorkspace().estimateItemSize(widgetInfo, false, true);
        int visibility = layout.getVisibility();
        layout.setVisibility(VISIBLE);

        int width = MeasureSpec.makeMeasureSpec(unScaledSize[0], MeasureSpec.EXACTLY);
        int height = MeasureSpec.makeMeasureSpec(unScaledSize[1], MeasureSpec.EXACTLY);
        Bitmap b = Bitmap.createBitmap(unScaledSize[0], unScaledSize[1],
                Bitmap.Config.ARGB_8888);
        mCanvas.setBitmap(b);

        layout.measure(width, height);
        layout.layout(0, 0, unScaledSize[0], unScaledSize[1]);
        layout.draw(mCanvas);
        mCanvas.setBitmap(null);
        layout.setVisibility(visibility);
        return b;
    }

    private void getFinalPositionForDropAnimation(int[] loc, float[] scaleXY,
            DragView dragView, CellLayout layout, ItemInfo info, int[] targetCell, boolean scale) {
        // Now we animate the dragView, (ie. the widget or shortcut preview) into its final
        // location and size on the home screen.
        int spanX = info.spanX;
        int spanY = info.spanY;

        Rect r = estimateItemPosition(layout, targetCell[0], targetCell[1], spanX, spanY);
        if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET) {
            DeviceProfile profile = mLauncher.getDeviceProfile();
            Utilities.shrinkRect(r, profile.appWidgetScale.x, profile.appWidgetScale.y);
        }
        loc[0] = r.left;
        loc[1] = r.top;

        setFinalTransitionTransform(layout);
        float cellLayoutScale =
                mLauncher.getDragLayer().getDescendantCoordRelativeToSelf(layout, loc, true);
        resetTransitionTransform(layout);

        if (scale) {
            float dragViewScaleX = (1.0f * r.width()) / dragView.getMeasuredWidth();
            float dragViewScaleY = (1.0f * r.height()) / dragView.getMeasuredHeight();

            // The animation will scale the dragView about its center, so we need to center about
            // the final location.
            loc[0] -= (dragView.getMeasuredWidth() - cellLayoutScale * r.width()) / 2
                    - Math.ceil(layout.getUnusedHorizontalSpace() / 2f);
            loc[1] -= (dragView.getMeasuredHeight() - cellLayoutScale * r.height()) / 2;
            scaleXY[0] = dragViewScaleX * cellLayoutScale;
            scaleXY[1] = dragViewScaleY * cellLayoutScale;
        } else {
            // Since we are not cross-fading the dragView, align the drag view to the
            // final cell position.
            float dragScale = dragView.getInitialScale() * cellLayoutScale;
            loc[0] += (dragScale - 1) * dragView.getWidth() / 2;
            loc[1] += (dragScale - 1) * dragView.getHeight() / 2;
            scaleXY[0] = scaleXY[1] = dragScale;

            // If a dragRegion was provided, offset the final position accordingly.
            Rect dragRegion = dragView.getDragRegion();
            if (dragRegion != null) {
                loc[0] += cellLayoutScale * dragRegion.left;
                loc[1] += cellLayoutScale * dragRegion.top;
            }
        }
    }

    public void animateWidgetDrop(ItemInfo info, CellLayout cellLayout, final DragView dragView,
            final Runnable onCompleteRunnable, int animationType, final View finalView,
            boolean external) {
        Rect from = new Rect();
        mLauncher.getDragLayer().getViewRectRelativeToSelf(dragView, from);

        int[] finalPos = new int[2];
        float scaleXY[] = new float[2];
        boolean scalePreview = !(info instanceof PendingAddShortcutInfo);
        getFinalPositionForDropAnimation(finalPos, scaleXY, dragView, cellLayout, info, mTargetCell,
                scalePreview);

        Resources res = mLauncher.getResources();
        final int duration = res.getInteger(R.integer.config_dropAnimMaxDuration) - 200;

        boolean isWidget = info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET ||
                info.itemType == LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET;
        if ((animationType == ANIMATE_INTO_POSITION_AND_RESIZE || external) && finalView != null) {
            Bitmap crossFadeBitmap = createWidgetBitmap(info, finalView);
            dragView.setCrossFadeBitmap(crossFadeBitmap);
            dragView.crossFade((int) (duration * 0.8f));
        } else if (isWidget && external) {
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
            mCurrentScale = getScaleX();
            setScaleX(mStateTransitionAnimation.getFinalScale());
            setScaleY(mStateTransitionAnimation.getFinalScale());
        }
    }
    public void resetTransitionTransform(CellLayout layout) {
        if (isSwitchingState()) {
            setScaleX(mCurrentScale);
            setScaleY(mCurrentScale);
        }
    }

    public WorkspaceStateTransitionAnimation getStateTransitionAnimation() {
        return mStateTransitionAnimation;
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

    public int getCurrentPageOffsetFromCustomContent() {
        return getNextPage() - numCustomPages();
    }

    /**
     * Calculate the nearest cell where the given object would be dropped.
     *
     * pixelX and pixelY should be in the coordinate system of layout
     */
    @Thunk int[] findNearestArea(int pixelX, int pixelY,
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
    }

    /**
     * Called at the end of a drag which originated on the workspace.
     */
    public void onDropCompleted(final View target, final DragObject d,
            final boolean isFlingToDelete, final boolean success) {
        if (mDeferDropAfterUninstall) {
            final CellLayout.CellInfo dragInfo = mDragInfo;
            mDeferredAction = new Runnable() {
                public void run() {
                    mDragInfo = dragInfo; // Restore the drag info that was cleared in onDragEnd()
                    onDropCompleted(target, d, isFlingToDelete, success);
                    mDeferredAction = null;
                }
            };
            return;
        }

        boolean beingCalledAfterUninstall = mDeferredAction != null;

        if (success && !(beingCalledAfterUninstall && !mUninstallSuccessful)) {
            if (target != this && mDragInfo != null) {
                removeWorkspaceItem(mDragInfo.cell);
            }
        } else if (mDragInfo != null) {
            final CellLayout cellLayout = mLauncher.getCellLayout(
                    mDragInfo.container, mDragInfo.screenId);
            if (cellLayout != null) {
                cellLayout.onDropChild(mDragInfo.cell);
            } else if (FeatureFlags.IS_DOGFOOD_BUILD) {
                throw new RuntimeException("Invalid state: cellLayout == null in "
                        + "Workspace#onDropCompleted. Please file a bug. ");
            }
        }
        if ((d.cancelled || (beingCalledAfterUninstall && !mUninstallSuccessful))
                && mDragInfo != null && mDragInfo.cell != null) {
            mDragInfo.cell.setVisibility(VISIBLE);
        }
        mDragInfo = null;

        if (!isFlingToDelete) {
            // Fling to delete already exits spring loaded mode after the animation finishes.
            mLauncher.exitSpringLoadedDragModeDelayed(success,
                    Launcher.EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT, mDelayedResizeRunnable);
            mDelayedResizeRunnable = null;
        }
    }

    /**
     * For opposite operation. See {@link #addInScreen}.
     */
    public void removeWorkspaceItem(View v) {
        CellLayout parentCell = getParentCellLayoutForView(v);
        if (parentCell != null) {
            parentCell.removeView(v);
        } else if (FeatureFlags.IS_DOGFOOD_BUILD) {
            // When an app is uninstalled using the drop target, we wait until resume to remove
            // the icon. We also remove all the corresponding items from the workspace at
            // {@link Launcher#bindComponentsRemoved}. That call can come before or after
            // {@link Launcher#mOnResumeCallbacks} depending on how busy the worker thread is.
            Log.e(TAG, "mDragInfo.cell has null parent");
        }
        if (v instanceof DropTarget) {
            mDragController.removeDropTarget((DropTarget) v);
        }
    }

    /**
     * Removes all folder listeners
     */
    public void removeFolderListeners() {
        mapOverItems(false, new ItemOperator() {
            @Override
            public boolean evaluate(ItemInfo info, View view) {
                if (view instanceof FolderIcon) {
                    ((FolderIcon) view).removeListeners();
                }
                return false;
            }
        });
    }

    @Override
    public void deferCompleteDropAfterUninstallActivity() {
        mDeferDropAfterUninstall = true;
    }

    /// maybe move this into a smaller part
    @Override
    public void onDragObjectRemoved(boolean success) {
        mDeferDropAfterUninstall = false;
        mUninstallSuccessful = success;
        if (mDeferredAction != null) {
            mDeferredAction.run();
        }
    }

    @Override
    public float getIntrinsicIconScaleFactor() {
        return 1f;
    }

    @Override
    public boolean supportsAppInfoDropTarget() {
        return true;
    }

    @Override
    public boolean supportsDeleteDropTarget() {
        return true;
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
            if (cl != null) {
                cl.restoreInstanceState(mSavedStates);
            }
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
        mSavedStates = null;
    }

    @Override
    public void scrollLeft() {
        if (!workspaceInModalState() && !mIsSwitchingState) {
            super.scrollLeft();
        }
        Folder openFolder = Folder.getOpen(mLauncher);
        if (openFolder != null) {
            openFolder.completeDragExit();
        }
    }

    @Override
    public void scrollRight() {
        if (!workspaceInModalState() && !mIsSwitchingState) {
            super.scrollRight();
        }
        Folder openFolder = Folder.getOpen(mLauncher);
        if (openFolder != null) {
            openFolder.completeDragExit();
        }
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
        ArrayList<CellLayout> layouts = new ArrayList<>();
        int screenCount = getChildCount();
        for (int screen = 0; screen < screenCount; screen++) {
            layouts.add(((CellLayout) getChildAt(screen)));
        }
        if (mLauncher.getHotseat() != null) {
            layouts.add(mLauncher.getHotseat().getLayout());
        }
        return layouts;
    }

    /**
     * We should only use this to search for specific children.  Do not use this method to modify
     * ShortcutsAndWidgetsContainer directly. Includes ShortcutAndWidgetContainers from
     * the hotseat and workspace pages
     */
    ArrayList<ShortcutAndWidgetContainer> getAllShortcutAndWidgetContainers() {
        ArrayList<ShortcutAndWidgetContainer> childrenLayouts = new ArrayList<>();
        int screenCount = getChildCount();
        for (int screen = 0; screen < screenCount; screen++) {
            childrenLayouts.add(((CellLayout) getChildAt(screen)).getShortcutsAndWidgets());
        }
        if (mLauncher.getHotseat() != null) {
            childrenLayouts.add(mLauncher.getHotseat().getLayout().getShortcutsAndWidgets());
        }
        return childrenLayouts;
    }

    public View getHomescreenIconByItemId(final long id) {
        return getFirstMatch(new ItemOperator() {

            @Override
            public boolean evaluate(ItemInfo info, View v) {
                return info != null && info.id == id;
            }
        });
    }

    public View getViewForTag(final Object tag) {
        return getFirstMatch(new ItemOperator() {

            @Override
            public boolean evaluate(ItemInfo info, View v) {
                return info == tag;
            }
        });
    }

    public LauncherAppWidgetHostView getWidgetForAppWidgetId(final int appWidgetId) {
        return (LauncherAppWidgetHostView) getFirstMatch(new ItemOperator() {

            @Override
            public boolean evaluate(ItemInfo info, View v) {
                return (info instanceof LauncherAppWidgetInfo) &&
                        ((LauncherAppWidgetInfo) info).appWidgetId == appWidgetId;
            }
        });
    }

    public View getFirstMatch(final ItemOperator operator) {
        final View[] value = new View[1];
        mapOverItems(MAP_NO_RECURSE, new ItemOperator() {
            @Override
            public boolean evaluate(ItemInfo info, View v) {
                if (operator.evaluate(info, v)) {
                    value[0] = v;
                    return true;
                }
                return false;
            }
        });
        return value[0];
    }

    void clearDropTargets() {
        mapOverItems(MAP_NO_RECURSE, new ItemOperator() {
            @Override
            public boolean evaluate(ItemInfo info, View v) {
                if (v instanceof DropTarget) {
                    mDragController.removeDropTarget((DropTarget) v);
                }
                // not done, process all the shortcuts
                return false;
            }
        });
    }

    /**
     * Removes items that match the {@param matcher}. When applications are removed
     * as a part of an update, this is called to ensure that other widgets and application
     * shortcuts are not removed.
     */
    public void removeItemsByMatcher(final ItemInfoMatcher matcher) {
        ArrayList<CellLayout> cellLayouts = getWorkspaceAndHotseatCellLayouts();
        for (final CellLayout layoutParent: cellLayouts) {
            final ViewGroup layout = layoutParent.getShortcutsAndWidgets();

            LongArrayMap<View> idToViewMap = new LongArrayMap<>();
            ArrayList<ItemInfo> items = new ArrayList<>();
            for (int j = 0; j < layout.getChildCount(); j++) {
                final View view = layout.getChildAt(j);
                if (view.getTag() instanceof ItemInfo) {
                    ItemInfo item = (ItemInfo) view.getTag();
                    items.add(item);
                    idToViewMap.put(item.id, view);
                }
            }

            for (ItemInfo itemToRemove : matcher.filterItemInfos(items)) {
                View child = idToViewMap.get(itemToRemove.id);

                if (child != null) {
                    // Note: We can not remove the view directly from CellLayoutChildren as this
                    // does not re-mark the spaces as unoccupied.
                    layoutParent.removeViewInLayout(child);
                    if (child instanceof DropTarget) {
                        mDragController.removeDropTarget((DropTarget) child);
                    }
                } else if (itemToRemove.container >= 0) {
                    // The item may belong to a folder.
                    View parent = idToViewMap.get(itemToRemove.container);
                    if (parent != null) {
                        FolderInfo folderInfo = (FolderInfo) parent.getTag();
                        folderInfo.prepareAutoUpdate();
                        folderInfo.remove((ShortcutInfo) itemToRemove, false);
                    }
                }
            }
        }

        // Strip all the empty screens
        stripEmptyScreens();
    }

    public interface ItemOperator {
        /**
         * Process the next itemInfo, possibly with side-effect on the next item.
         *
         * @param info info for the shortcut
         * @param view view for the shortcut
         * @return true if done, false to continue the map
         */
        boolean evaluate(ItemInfo info, View view);
    }

    /**
     * Map the operator over the shortcuts and widgets, return the first-non-null value.
     *
     * @param recurse true: iterate over folder children. false: op get the folders themselves.
     * @param op the operator to map over the shortcuts
     */
    void mapOverItems(boolean recurse, ItemOperator op) {
        ArrayList<ShortcutAndWidgetContainer> containers = getAllShortcutAndWidgetContainers();
        final int containerCount = containers.size();
        for (int containerIdx = 0; containerIdx < containerCount; containerIdx++) {
            ShortcutAndWidgetContainer container = containers.get(containerIdx);
            // map over all the shortcuts on the workspace
            final int itemCount = container.getChildCount();
            for (int itemIdx = 0; itemIdx < itemCount; itemIdx++) {
                View item = container.getChildAt(itemIdx);
                ItemInfo info = (ItemInfo) item.getTag();
                if (recurse && info instanceof FolderInfo && item instanceof FolderIcon) {
                    FolderIcon folder = (FolderIcon) item;
                    ArrayList<View> folderChildren = folder.getFolder().getItemsInReadingOrder();
                    // map over all the children in the folder
                    final int childCount = folderChildren.size();
                    for (int childIdx = 0; childIdx < childCount; childIdx++) {
                        View child = folderChildren.get(childIdx);
                        info = (ItemInfo) child.getTag();
                        if (op.evaluate(info, child)) {
                            return;
                        }
                    }
                } else {
                    if (op.evaluate(info, item)) {
                        return;
                    }
                }
            }
        }
    }

    void updateShortcuts(ArrayList<ShortcutInfo> shortcuts) {
        int total  = shortcuts.size();
        final HashSet<ShortcutInfo> updates = new HashSet<>(total);
        final HashSet<Long> folderIds = new HashSet<>();

        for (int i = 0; i < total; i++) {
            ShortcutInfo s = shortcuts.get(i);
            updates.add(s);
            folderIds.add(s.container);
        }

        mapOverItems(MAP_RECURSE, new ItemOperator() {
            @Override
            public boolean evaluate(ItemInfo info, View v) {
                if (info instanceof ShortcutInfo && v instanceof BubbleTextView &&
                        updates.contains(info)) {
                    ShortcutInfo si = (ShortcutInfo) info;
                    BubbleTextView shortcut = (BubbleTextView) v;
                    Drawable oldIcon = shortcut.getIcon();
                    boolean oldPromiseState = (oldIcon instanceof PreloadIconDrawable)
                            && ((PreloadIconDrawable) oldIcon).hasNotCompleted();
                    shortcut.applyFromShortcutInfo(si, si.isPromise() != oldPromiseState);
                }
                // process all the shortcuts
                return false;
            }
        });

        // Update folder icons
        mapOverItems(MAP_NO_RECURSE, new ItemOperator() {
            @Override
            public boolean evaluate(ItemInfo info, View v) {
                if (info instanceof FolderInfo && folderIds.contains(info.id)) {
                    ((FolderInfo) info).itemsChanged(false);
                }
                // process all the shortcuts
                return false;
            }
        });
    }

    public void updateIconBadges(final Set<PackageUserKey> updatedBadges) {
        final PackageUserKey packageUserKey = new PackageUserKey(null, null);
        final HashSet<Long> folderIds = new HashSet<>();
        mapOverItems(MAP_RECURSE, new ItemOperator() {
            @Override
            public boolean evaluate(ItemInfo info, View v) {
                if (info instanceof ShortcutInfo && v instanceof BubbleTextView
                        && packageUserKey.updateFromItemInfo(info)) {
                    if (updatedBadges.contains(packageUserKey)) {
                        ((BubbleTextView) v).applyBadgeState(info, true /* animate */);
                        folderIds.add(info.container);
                    }
                }
                // process all the shortcuts
                return false;
            }
        });

        // Update folder icons
        mapOverItems(MAP_NO_RECURSE, new ItemOperator() {
            @Override
            public boolean evaluate(ItemInfo info, View v) {
                if (info instanceof FolderInfo && folderIds.contains(info.id)
                        && v instanceof FolderIcon) {
                    FolderBadgeInfo folderBadgeInfo = new FolderBadgeInfo();
                    for (ShortcutInfo si : ((FolderInfo) info).contents) {
                        folderBadgeInfo.addBadgeInfo(mLauncher.getPopupDataProvider()
                                .getBadgeInfoForItem(si));
                    }
                    ((FolderIcon) v).setBadgeInfo(folderBadgeInfo);
                }
                // process all the shortcuts
                return false;
            }
        });
    }

    public void removeAbandonedPromise(String packageName, UserHandle user) {
        HashSet<String> packages = new HashSet<>(1);
        packages.add(packageName);
        ItemInfoMatcher matcher = ItemInfoMatcher.ofPackages(packages, user);
        mLauncher.getModelWriter().deleteItemsFromDatabase(matcher);
        removeItemsByMatcher(matcher);
    }

    public void updateRestoreItems(final HashSet<ItemInfo> updates) {
        mapOverItems(MAP_RECURSE, new ItemOperator() {
            @Override
            public boolean evaluate(ItemInfo info, View v) {
                if (info instanceof ShortcutInfo && v instanceof BubbleTextView
                        && updates.contains(info)) {
                    ((BubbleTextView) v).applyPromiseState(false /* promiseStateChanged */);
                } else if (v instanceof PendingAppWidgetHostView
                        && info instanceof LauncherAppWidgetInfo
                        && updates.contains(info)) {
                    ((PendingAppWidgetHostView) v).applyState();
                }
                // process all the shortcuts
                return false;
            }
        });
    }

    public void widgetsRestored(final ArrayList<LauncherAppWidgetInfo> changedInfo) {
        if (!changedInfo.isEmpty()) {
            DeferredWidgetRefresh widgetRefresh = new DeferredWidgetRefresh(changedInfo,
                    mLauncher.getAppWidgetHost());

            LauncherAppWidgetInfo item = changedInfo.get(0);
            final AppWidgetProviderInfo widgetInfo;
            if (item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_NOT_VALID)) {
                widgetInfo = AppWidgetManagerCompat
                        .getInstance(mLauncher).findProvider(item.providerName, item.user);
            } else {
                widgetInfo = AppWidgetManagerCompat.getInstance(mLauncher)
                        .getAppWidgetInfo(item.appWidgetId);
            }

            if (widgetInfo != null) {
                // Re-inflate the widgets which have changed status
                widgetRefresh.run();
            } else {
                // widgetRefresh will automatically run when the packages are updated.
                // For now just update the progress bars
                mapOverItems(MAP_NO_RECURSE, new ItemOperator() {
                    @Override
                    public boolean evaluate(ItemInfo info, View view) {
                        if (view instanceof PendingAppWidgetHostView
                                && changedInfo.contains(info)) {
                            ((LauncherAppWidgetInfo) info).installProgress = 100;
                            ((PendingAppWidgetHostView) view).applyState();
                        }
                        // process all the shortcuts
                        return false;
                    }
                });
            }
        }
    }

    void moveToDefaultScreen(boolean animate) {
        int page = getDefaultPage();
        if (!workspaceInModalState() && getNextPage() != page) {
            if (animate) {
                snapToPage(page);
            } else {
                setCurrentPage(page);
            }
        }
        View child = getChildAt(page);
        if (child != null) {
            child.requestFocus();
        }
    }

    void moveToCustomContentScreen(boolean animate) {
        if (hasCustomContent()) {
            int ccIndex = getPageIndexForScreenId(CUSTOM_CONTENT_SCREEN_ID);
            if (animate) {
                snapToPage(ccIndex);
            } else {
                setCurrentPage(ccIndex);
            }
            View child = getChildAt(ccIndex);
            if (child != null) {
                child.requestFocus();
            }
         }
        exitWidgetResizeMode();
    }

    @Override
    protected String getPageIndicatorDescription() {
        return getResources().getString(R.string.all_apps_button_label);
    }

    @Override
    protected String getCurrentPageDescription() {
        if (hasCustomContent() && getNextPage() == 0) {
            return mCustomContentDescription;
        }
        int page = (mNextPage != INVALID_PAGE) ? mNextPage : mCurrentPage;
        return getPageDescription(page);
    }

    private String getPageDescription(int page) {
        int delta = numCustomPages();
        int nScreens = getChildCount() - delta;
        int extraScreenId = mScreenOrder.indexOf(EXTRA_EMPTY_SCREEN_ID);
        if (extraScreenId >= 0 && nScreens > 1) {
            if (page == extraScreenId) {
                return getContext().getString(R.string.workspace_new_page);
            }
            nScreens--;
        }
        if (nScreens == 0) {
            // When the workspace is not loaded, we do not know how many screen will be bound.
            return getContext().getString(R.string.all_apps_home_button_label);
        }
        return getContext().getString(R.string.workspace_scroll_format,
                page + 1 - delta, nScreens);
    }

    @Override
    public void fillInLogContainerData(View v, ItemInfo info, Target target, Target targetParent) {
        target.gridX = info.cellX;
        target.gridY = info.cellY;
        target.pageIndex = getCurrentPage();
        targetParent.containerType = ContainerType.WORKSPACE;
        if (info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            target.rank = info.rank;
            targetParent.containerType = ContainerType.HOTSEAT;
        } else if (info.container >= 0) {
            targetParent.containerType = ContainerType.FOLDER;
        }
    }

    @Override
    public boolean enableFreeScroll() {
        if (getState() == State.OVERVIEW) {
            return super.enableFreeScroll();
        } else {
            Log.w(TAG, "enableFreeScroll called but not in overview: state=" + getState());
            return false;
        }
    }

    /**
     * Used as a workaround to ensure that the AppWidgetService receives the
     * PACKAGE_ADDED broadcast before updating widgets.
     */
    private class DeferredWidgetRefresh implements Runnable, ProviderChangedListener {
        private final ArrayList<LauncherAppWidgetInfo> mInfos;
        private final LauncherAppWidgetHost mHost;
        private final Handler mHandler;

        private boolean mRefreshPending;

        DeferredWidgetRefresh(ArrayList<LauncherAppWidgetInfo> infos,
            LauncherAppWidgetHost host) {
            mInfos = infos;
            mHost = host;
            mHandler = new Handler();
            mRefreshPending = true;

            mHost.addProviderChangeListener(this);
            // Force refresh after 10 seconds, if we don't get the provider changed event.
            // This could happen when the provider is no longer available in the app.
            mHandler.postDelayed(this, 10000);
        }

        @Override
        public void run() {
            mHost.removeProviderChangeListener(this);
            mHandler.removeCallbacks(this);

            if (!mRefreshPending) {
                return;
            }

            mRefreshPending = false;

            mapOverItems(MAP_NO_RECURSE, new ItemOperator() {
                @Override
                public boolean evaluate(ItemInfo info, View view) {
                    if (view instanceof PendingAppWidgetHostView && mInfos.contains(info)) {
                        mLauncher.removeItem(view, info, false /* deleteFromDb */);
                        mLauncher.bindAppWidget((LauncherAppWidgetInfo) info);
                    }
                    // process all the shortcuts
                    return false;
                }
            });
        }

        @Override
        public void notifyWidgetProvidersChanged() {
            run();
        }
    }

    private class StateTransitionListener extends AnimatorListenerAdapter
            implements AnimatorUpdateListener {
        @Override
        public void onAnimationUpdate(ValueAnimator anim) {
            mTransitionProgress = anim.getAnimatedFraction();
        }

        @Override
        public void onAnimationStart(Animator animation) {
            if (mState == State.SPRING_LOADED) {
                // Show the page indicator at the same time as the rest of the transition.
                showPageIndicatorAtCurrentScroll();
            }
            mTransitionProgress = 0;
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            onEndStateTransition();
        }
    }
}
