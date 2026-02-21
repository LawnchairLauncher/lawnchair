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

import static com.android.launcher3.InvariantDeviceProfile.INDEX_DEFAULT;
import static com.android.launcher3.InvariantDeviceProfile.INDEX_LANDSCAPE;
import static com.android.launcher3.InvariantDeviceProfile.INDEX_TWO_PANEL_LANDSCAPE;
import static com.android.launcher3.InvariantDeviceProfile.INDEX_TWO_PANEL_PORTRAIT;
import static com.android.launcher3.InvariantDeviceProfile.createDisplayOptionSpec;
import static com.android.launcher3.InvariantDeviceProfile.deviceType;
import static com.android.launcher3.Utilities.dpiFromPx;
import static com.android.launcher3.Utilities.pxFromSp;
import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.ICON_OVERLAP_FACTOR;
import static com.android.launcher3.icons.IconNormalizer.ICON_VISIBLE_AREA_FACTOR;
import static com.android.launcher3.testing.shared.ResourceUtils.INVALID_RESOURCE_HANDLE;
import static com.android.launcher3.testing.shared.ResourceUtils.pxFromDp;
import static com.android.launcher3.testing.shared.ResourceUtils.roundPxValueFromFloat;
import static com.android.launcher3.util.OverviewReleaseFlags.enableGridOnlyOverview;
import static com.android.wm.shell.Flags.enableBubbleBar;
import static com.android.wm.shell.Flags.enableBubbleBarOnPhones;
import static com.android.wm.shell.Flags.enableTinyTaskbar;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import androidx.core.content.res.ResourcesCompat;
import app.lawnchair.DeviceProfileOverrides.TextFactors;
import com.android.launcher3.CellLayout.ContainerType;
import com.android.launcher3.DevicePaddings.DevicePadding;
import com.android.launcher3.InvariantDeviceProfile.DisplayOptionSpec;
import com.android.launcher3.deviceprofile.AllAppsProfile;
import com.android.launcher3.deviceprofile.BottomSheetProfile;
import com.android.launcher3.deviceprofile.DeviceProperties;
import com.android.launcher3.deviceprofile.DropTargetProfile;
import com.android.launcher3.deviceprofile.HotseatProfile;
import com.android.launcher3.deviceprofile.OverviewProfile;
import com.android.launcher3.deviceprofile.TaskbarProfile;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.DotRenderer;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.responsive.CalculatedCellSpec;
import com.android.launcher3.responsive.CalculatedHotseatSpec;
import com.android.launcher3.responsive.CalculatedResponsiveSpec;
import com.android.launcher3.responsive.HotseatSpecsProvider;
import com.android.launcher3.responsive.ResponsiveCellSpecsProvider;
import com.android.launcher3.responsive.ResponsiveSpec.Companion.ResponsiveSpecType;
import com.android.launcher3.responsive.ResponsiveSpec.DimensionType;
import com.android.launcher3.responsive.ResponsiveSpecsProvider;
import com.android.launcher3.testing.shared.ResourceUtils;
import com.android.launcher3.util.CellContentDimensions;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.DisplayController.Info;
import com.android.launcher3.util.IconSizeSteps;
import com.android.launcher3.util.ResourceHelper;
import com.android.launcher3.util.WindowBounds;
import com.android.launcher3.util.window.WindowManagerProxy;

import java.io.PrintWriter;
import java.util.Locale;
import java.util.function.Consumer;

import com.patrykmichalik.opto.core.PreferenceExtensionsKt;
import app.lawnchair.DeviceProfileOverrides;
import app.lawnchair.LawnchairApp;
import app.lawnchair.LawnchairAppKt;
import app.lawnchair.hotseat.HotseatMode;
import app.lawnchair.preferences2.PreferenceManager2;
import app.lawnchair.theme.color.ColorOption;

@SuppressLint("NewApi")
public class DeviceProfile {

    private static final int DEFAULT_DOT_SIZE = 100;
    private static final float MIN_FOLDER_TEXT_SIZE_SP = 16f;
    private static final float MIN_WIDGET_PADDING_DP = 6f;

    private static final float MAX_ASPECT_RATIO_FOR_ALTERNATE_EDIT_STATE = 1.5f;

    public static final PointF DEFAULT_SCALE = new PointF(1.0f, 1.0f);
    public static final ViewScaleProvider DEFAULT_PROVIDER = itemInfo -> DEFAULT_SCALE;
    public static final Consumer<DeviceProfile> DEFAULT_DIMENSION_PROVIDER = dp -> {
    };

    public final InvariantDeviceProfile inv;
    private final BottomSheetProfile mBottomSheetProfile;
    private final DisplayOptionSpec mDisplayOptionSpec;
    private final Info mInfo;
    private final DisplayMetrics mMetrics;
    private final IconSizeSteps mIconSizeSteps;

    // Device properties

    private final DeviceProperties mDeviceProperties;

    public boolean isPredictiveBackSwipe;
    public final boolean isQsbInline;

    // Device properties in current orientation

    public final boolean isLeftRightSplit;
    private final boolean mIsScalableGrid;
    private final int mTypeIndex;

    // Responsive grid
    private final boolean mIsResponsiveGrid;
    private CalculatedResponsiveSpec mResponsiveWorkspaceWidthSpec;
    private CalculatedResponsiveSpec mResponsiveWorkspaceHeightSpec;
    private CalculatedResponsiveSpec mResponsiveAllAppsWidthSpec;
    private CalculatedResponsiveSpec mResponsiveAllAppsHeightSpec;
    private CalculatedResponsiveSpec mResponsiveFolderWidthSpec;
    private CalculatedResponsiveSpec mResponsiveFolderHeightSpec;
    private CalculatedHotseatSpec mResponsiveHotseatSpec;
    private CalculatedCellSpec mResponsiveWorkspaceCellSpec;
    private CalculatedCellSpec mResponsiveAllAppsCellSpec;

    /**
     * The maximum amount of left/right workspace padding as a percentage of the screen width.
     * To be clear, this means that up to 7% of the screen width can be used as left padding, and
     * 7% of the screen width can be used as right padding.
     */
    private static final float MAX_HORIZONTAL_PADDING_PERCENT = 0.14f;

    private static final float TALL_DEVICE_ASPECT_RATIO_THRESHOLD = 2.0f;
    private static final float TALLER_DEVICE_ASPECT_RATIO_THRESHOLD = 2.15f;
    private static final float TALL_DEVICE_EXTRA_SPACE_THRESHOLD_DP = 252;
    private static final float TALL_DEVICE_MORE_EXTRA_SPACE_THRESHOLD_DP = 268;

    // Workspace
    public final int desiredWorkspaceHorizontalMarginOriginalPx;
    public int desiredWorkspaceHorizontalMarginPx;
    public int gridVisualizationPaddingX;
    public int gridVisualizationPaddingY;
    public Point cellLayoutBorderSpaceOriginalPx;
    public Point cellLayoutBorderSpacePx;
    public Rect cellLayoutPaddingPx = new Rect();

    public final int edgeMarginPx;
    public final float workspaceContentScale;
    public final int workspaceSpringLoadedMinNextPageVisiblePx;

    private final int extraSpace;
    private int maxEmptySpace;
    public int workspaceTopPadding;
    public int workspaceBottomPadding;

    // Workspace page indicator
    public int workspacePageIndicatorHeight;
    private final int mWorkspacePageIndicatorOverlapWorkspace;

    // Workspace icons
    public float iconScale;
    public int iconSizePx;
    public int iconTextSizePx;
    public int iconDrawablePaddingPx;
    private int mIconDrawablePaddingOriginalPx;
    public boolean iconCenterVertically;
    public int maxIconTextLineCount;

    public float cellScaleToFit;
    public int cellWidthPx;
    public int cellHeightPx;
    public int workspaceCellPaddingXPx;

    public int cellYPaddingPx = -1;

    // Folder
    public final int numFolderRows;
    public final int numFolderColumns;
    public final float folderLabelTextScale;
    public int folderLabelTextSizePx;
    public int folderFooterHeightPx;
    public int folderIconSizePx;
    public int folderIconOffsetYPx;

    // Folder content
    public Point folderCellLayoutBorderSpacePx;
    public int folderContentPaddingLeftRight;
    public int folderContentPaddingTop;

    // Folder cell
    public int folderCellWidthPx;
    public int folderCellHeightPx;

    // Folder child
    public int folderChildIconSizePx;
    public int folderChildTextSizePx;
    public int folderChildDrawablePaddingPx;
    public int maxFolderChildTextLineCount;

    // Hotseat
    private final HotseatProfile hotseatProfile;
    public int numShownHotseatIcons;
    public int hotseatCellHeightPx;
    private int mHotseatColumnSpan;
    private int mHotseatWidthPx; // not used in vertical bar layout
    // In portrait: size = height, in landscape: size = width
    public int hotseatBarSizePx;
    public int hotseatBarBottomSpacePx;
    public int hotseatQsbSpace;
    public int hotseatQsbWidth; // only used when isQsbInline
    public int hotseatBorderSpace;
    // Space required for the bubble bar between the hotseat and the edge of the screen. If there's
    // not enough space, the hotseat will adjust itself for the bubble bar.
    private final int mBubbleBarSpaceThresholdPx;

    private AllAppsProfile mAllAppsProfile;
    public int allAppsShiftRange;
    public Rect allAppsPadding = new Rect();
    public int allAppsOpenDuration;
    public int allAppsCloseDuration;
    public int allAppsLeftRightMargin;
    public final int numShownAllAppsColumns;

    private final OverviewProfile overviewProfile;

    // Split staging
    public int splitPlaceholderInset;

    // Widgets
    private final ViewScaleProvider mViewScaleProvider;

    private final DropTargetProfile mDropTargetProfile;

    // Insets
    private final Rect mInsets = new Rect();
    public final Rect workspacePadding = new Rect();
    // Additional padding added to the widget inside its cellSpace. It is applied outside
    // the widgetView, such that the actual view size is same as the widget size.
    public final Rect widgetPadding = new Rect();

    // Notification dots
    public final DotRenderer mDotRendererWorkSpace;
    public final DotRenderer mDotRendererAllApps;

    // Taskbar
    private final TaskbarProfile mTaskbarProfile;
    public boolean isTaskbarPresent;
    // Whether Taskbar will inset the bottom of apps by taskbarSize.
    public boolean isTaskbarPresentInApps;
    // DragController
    public int flingToDeleteThresholdVelocity;

    /** Used only as an alternative to mocking when null values cannot be used. */
    @VisibleForTesting
    public DeviceProfile() {
        mDeviceProperties = new DeviceProperties(
                0,0,
                0,
                0,0,
                0,0,
                0.0f,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false
        );
        mBottomSheetProfile = new BottomSheetProfile(0, 0, 0, 0f, 0f);
        overviewProfile = new OverviewProfile(
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0
        );
        hotseatProfile = new HotseatProfile(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        mTaskbarProfile = new TaskbarProfile(0, 0, 0, 0, 0, false, false);
        inv = null;
        mDisplayOptionSpec = null;
        mInfo = null;
        mMetrics = null;
        mIconSizeSteps = null;
        isPredictiveBackSwipe = false;
        isQsbInline = false;
        isLeftRightSplit = false;
        mIsScalableGrid = false;
        mTypeIndex = 0;
        mIsResponsiveGrid = false;
        desiredWorkspaceHorizontalMarginOriginalPx = 0;
        edgeMarginPx = 0;
        workspaceContentScale = 0;
        workspaceSpringLoadedMinNextPageVisiblePx = 0;
        extraSpace = 0;
        workspacePageIndicatorHeight = 0;
        mWorkspacePageIndicatorOverlapWorkspace = 0;
        numFolderRows = 0;
        numFolderColumns = 0;
        mDropTargetProfile = new DropTargetProfile(0, 0, 0, 0, 0, 0, 0, 0, 0);
        folderLabelTextScale = 0;
        hotseatQsbWidth = 0;
        hotseatBorderSpace = 0;
        mBubbleBarSpaceThresholdPx = 0;
        numShownAllAppsColumns = 0;
        mViewScaleProvider = null;
        mDotRendererWorkSpace = null;
        mDotRendererAllApps = null;
        mAllAppsProfile = new AllAppsProfile(new Point(0, 0), 0, 0, 0f, 0, 0, 0);
        mTextFactors = new TextFactors(0,0,0);
        preferenceManager2 = null;
    }

    private final TextFactors mTextFactors;
    private float allAppsCellHeightMultiplier;
    private PreferenceManager2 preferenceManager2 = null;

    /** TODO: Once we fully migrate to staged split, remove "isMultiWindowMode" */
    DeviceProfile(Context context, InvariantDeviceProfile inv, Info info,
            WindowManagerProxy wmProxy, ThemeManager themeManager, WindowBounds windowBounds,
            SparseArray<DotRenderer> dotRendererCache, boolean isMultiWindowMode,
            boolean transposeLayoutWithOrientation, boolean isMultiDisplay, boolean isGestureMode,
            @NonNull final ViewScaleProvider viewScaleProvider,
            @NonNull final Consumer<DeviceProfile> dimensionOverrideProvider,
            boolean isTransientTaskbar, DisplayOptionSpec displayOptionSpec) {
        mTextFactors = DeviceProfileOverrides.INSTANCE.get(context).getTextFactors();

        preferenceManager2 = PreferenceManager2.INSTANCE.get(context);
        allAppsCellHeightMultiplier = PreferenceExtensionsKt
                .firstBlocking(preferenceManager2.getDrawerCellHeightFactor());
        this.inv = inv;

        mDeviceProperties = DeviceProperties.Factory.createDeviceProperties(
                info,
                windowBounds,
                transposeLayoutWithOrientation,
                isMultiDisplay,
                isMultiWindowMode,
                isGestureMode
        );

        mInsets.set(windowBounds.insets);
        this.mDisplayOptionSpec = displayOptionSpec;

        // TODO(b/241386436): shouldn't change any launcher behaviour
        mIsResponsiveGrid = inv.workspaceSpecsId != INVALID_RESOURCE_HANDLE
                && inv.allAppsSpecsId != INVALID_RESOURCE_HANDLE
                && inv.folderSpecsId != INVALID_RESOURCE_HANDLE
                && inv.hotseatSpecsId != INVALID_RESOURCE_HANDLE
                && inv.workspaceCellSpecsId != INVALID_RESOURCE_HANDLE
                && inv.allAppsCellSpecsId != INVALID_RESOURCE_HANDLE;

        mIsScalableGrid = inv.isScalable && !isVerticalBarLayout() && !isMultiWindowMode;
        // Determine device posture.
        mInfo = info;
        boolean isTaskBarEnabled = PreferenceExtensionsKt.firstBlocking(preferenceManager2.getEnableTaskbarOnPhone());
        boolean taskbarOrBubbleBarOnPhones = enableTinyTaskbar()
                || (enableBubbleBar() && enableBubbleBarOnPhones());
        isTaskbarPresent = isTaskBarEnabled && (mDeviceProperties.isTablet() || (taskbarOrBubbleBarOnPhones && isGestureMode))
                && wmProxy.isTaskbarDrawnInProcess();

        // Some more constants.
        context = getContext(context, info, inv.isFixedLandscape
                        || isVerticalBarLayout()
                        || (mDeviceProperties.isTablet() && mDeviceProperties.isLandscape())
                        ? Configuration.ORIENTATION_LANDSCAPE
                        : Configuration.ORIENTATION_PORTRAIT,
                windowBounds);
        final Resources res = context.getResources();

        workspacePageIndicatorHeight = res.getDimensionPixelSize(
                R.dimen.workspace_page_indicator_height);
        mWorkspacePageIndicatorOverlapWorkspace =
                res.getDimensionPixelSize(R.dimen.workspace_page_indicator_overlap_workspace);

        overviewProfile = OverviewProfile.Factory.createOverviewProfile(res);

        mMetrics = res.getDisplayMetrics();

        mIconSizeSteps = new IconSizeSteps(res);

        mTypeIndex = displayOptionSpec.typeIndex;

        mTaskbarProfile = TaskbarProfile.Factory.createTaskbarProfile(
                res,
                isTransientTaskbar,
                isTaskbarPresent,
                mMetrics,
                displayOptionSpec,
                mTypeIndex,
                inv
        );

        edgeMarginPx = res.getDimensionPixelSize(R.dimen.dynamic_grid_edge_margin);
        workspaceContentScale = res.getFloat(R.dimen.workspace_content_scale);

        gridVisualizationPaddingX = res.getDimensionPixelSize(
                R.dimen.grid_visualization_horizontal_cell_spacing);
        gridVisualizationPaddingY = res.getDimensionPixelSize(
                R.dimen.grid_visualization_vertical_cell_spacing);

        mBottomSheetProfile = BottomSheetProfile.Factory.createBottomSheetProfile(
                getDeviceProperties(),
                mInsets,
                res,
                edgeMarginPx,
                shouldShowAllAppsOnSheet(),
                workspaceContentScale
        );

        folderLabelTextScale = res.getFloat(R.dimen.folder_label_text_scale);
        numFolderRows = inv.numFolderRows[mTypeIndex];
        numFolderColumns = inv.numFolderColumns[mTypeIndex];

        if (mIsScalableGrid && inv.folderStyle != INVALID_RESOURCE_HANDLE) {
            TypedArray folderStyle = context.obtainStyledAttributes(inv.folderStyle,
                    R.styleable.FolderStyle);
            // These are re-set in #updateFolderCellSize if the grid is not scalable
            folderCellHeightPx = folderStyle.getDimensionPixelSize(
                    R.styleable.FolderStyle_folderCellHeight, 0);
            folderCellWidthPx = folderStyle.getDimensionPixelSize(
                    R.styleable.FolderStyle_folderCellWidth, 0);

            folderContentPaddingTop = folderStyle.getDimensionPixelSize(
                    R.styleable.FolderStyle_folderTopPadding, 0);

            int gutter = folderStyle.getDimensionPixelSize(
                    R.styleable.FolderStyle_folderBorderSpace, 0);
            folderCellLayoutBorderSpacePx = new Point(gutter, gutter);
            folderFooterHeightPx = folderStyle.getDimensionPixelSize(
                    R.styleable.FolderStyle_folderFooterHeight, 0);
            folderStyle.recycle();
        } else if (!mIsResponsiveGrid) {
            folderCellLayoutBorderSpacePx = new Point(0, 0);
            folderFooterHeightPx = res.getDimensionPixelSize(R.dimen.folder_footer_height_default);
            folderContentPaddingTop = res.getDimensionPixelSize(R.dimen.folder_top_padding_default);
        }

        setupAllAppsStyle(context);

        workspacePageIndicatorHeight = res.getDimensionPixelSize(
                R.dimen.workspace_page_indicator_height);
        float pageIndicatorHeightFactor = PreferenceExtensionsKt
            .firstBlocking(preferenceManager2.getPageIndicatorHeightFactor());
        
        workspacePageIndicatorHeight *= (int) pageIndicatorHeightFactor;
//        mWorkspacePageIndicatorOverlapWorkspace = res
//                .getDimensionPixelSize(R.dimen.workspace_page_indicator_overlap_workspace);

        if (!mIsResponsiveGrid) {
            TypedArray cellStyle;
            if (inv.cellStyle != INVALID_RESOURCE_HANDLE) {
                cellStyle = context.obtainStyledAttributes(inv.cellStyle,
                        R.styleable.CellStyle);
            } else {
                cellStyle = context.obtainStyledAttributes(R.style.CellStyleDefault,
                        R.styleable.CellStyle);
            }
            mIconDrawablePaddingOriginalPx = cellStyle.getDimensionPixelSize(
                    R.styleable.CellStyle_iconDrawablePadding, 0);
            cellStyle.recycle();
        }

        // Some foldable portrait modes are too wide in terms of aspect ratio so we need to tweak
        // the dimensions for edit state.
        final boolean shouldApplyWidePortraitDimens = mDeviceProperties.isTablet()
                && !mDeviceProperties.isLandscape()
                && mDeviceProperties.getAspectRatio() < MAX_ASPECT_RATIO_FOR_ALTERNATE_EDIT_STATE;
        mDropTargetProfile = DropTargetProfile
                .Factory
                .createDropTargetProfile(res, shouldApplyWidePortraitDimens);

        workspaceSpringLoadedMinNextPageVisiblePx = res.getDimensionPixelSize(
                R.dimen.dynamic_grid_spring_loaded_min_next_space_visible);

        workspaceCellPaddingXPx = res.getDimensionPixelSize(R.dimen.dynamic_grid_cell_padding_x);

        HotseatMode hotseatMode = PreferenceExtensionsKt.firstBlocking(preferenceManager2.getHotseatMode());
        boolean isQsbEnable = hotseatMode.getLayoutResourceId() != R.layout.empty_view;

        numShownHotseatIcons = displayOptionSpec.numShownHotseatIcons;
        mHotseatColumnSpan = inv.numColumns;

        numShownAllAppsColumns = displayOptionSpec.numAllAppsColumns;

        int hotseatBarBottomSpace = !isQsbEnable ? 0 : pxFromDp(inv.hotseatBarBottomSpace[mTypeIndex], mMetrics);
        int minQsbMargin = res.getDimensionPixelSize(R.dimen.min_qsb_margin);

        if (mIsResponsiveGrid) {
            float responsiveAspectRatio = (float) mDeviceProperties.getWidthPx() / mDeviceProperties.getHeightPx();
            HotseatSpecsProvider hotseatSpecsProvider =
                    HotseatSpecsProvider.create(new ResourceHelper(context,
                            displayOptionSpec.hotseatSpecsId));
            mResponsiveHotseatSpec =
                    isVerticalBarLayout() ? hotseatSpecsProvider.getCalculatedSpec(
                            responsiveAspectRatio, DimensionType.WIDTH, mDeviceProperties.getWidthPx())
                            : hotseatSpecsProvider.getCalculatedSpec(responsiveAspectRatio,
                                    DimensionType.HEIGHT, mDeviceProperties.getHeightPx());
            hotseatQsbSpace = mResponsiveHotseatSpec.getHotseatQsbSpace();
            hotseatBarBottomSpace =
                    isVerticalBarLayout() ? 0 : mResponsiveHotseatSpec.getEdgePadding();

            ResponsiveCellSpecsProvider workspaceCellSpecs = ResponsiveCellSpecsProvider.create(
                    new ResourceHelper(context, displayOptionSpec.workspaceCellSpecsId));
            mResponsiveWorkspaceCellSpec = workspaceCellSpecs.getCalculatedSpec(
                    responsiveAspectRatio, mDeviceProperties.getHeightPx());
        } else {
            hotseatQsbSpace = pxFromDp(inv.hotseatQsbSpace[mTypeIndex], mMetrics);
            hotseatBarBottomSpace = pxFromDp(inv.hotseatBarBottomSpace[mTypeIndex], mMetrics);
        }

        hotseatProfile = HotseatProfile.Factory.createHotseatProfile(
                getDeviceProperties(),
                res,
                inv,
                isTaskbarPresent,
                shouldApplyWidePortraitDimens,
                isVerticalBarLayout(),
                mResponsiveHotseatSpec,
                workspacePageIndicatorHeight,
                hotseatMode
        );

        // Whether QSB might be inline in appropriate orientation (e.g. landscape).
        isQsbInline = isQsbInline(
                inv,
                hotseatProfile,
                mDeviceProperties,
                mIsScalableGrid
        );

        if (!isVerticalBarLayout()) {
            // Have a little space between the inset and the QSB
            if (!isQsbEnable && mInsets.bottom + minQsbMargin > hotseatBarBottomSpace) {
                int availableSpace = hotseatQsbSpace - (mInsets.bottom - hotseatBarBottomSpace);

                // Only change the spaces if there is space
                if (availableSpace > 0) {
                    // Make sure there is enough space between hotseat/QSB and QSB/navBar
                    if (availableSpace < minQsbMargin * 2) {
                        minQsbMargin = availableSpace / 2;
                        hotseatQsbSpace = minQsbMargin;
                    } else {
                        hotseatQsbSpace -= minQsbMargin;
                    }
                }
                hotseatBarBottomSpacePx = mInsets.bottom + minQsbMargin;

            } else {
                hotseatBarBottomSpacePx = hotseatBarBottomSpace;
            }
        }

        if (mIsResponsiveGrid) {
            updateHotseatSizes(mResponsiveWorkspaceCellSpec.getIconSize());
        } else {
            updateHotseatSizes(pxFromDp(inv.iconSize[mTypeIndex], mMetrics));
        }

        mBubbleBarSpaceThresholdPx =
                res.getDimensionPixelSize(R.dimen.bubblebar_hotseat_adjustment_threshold);

        int allAppsTopPadding = mInsets.top;

        // Needs to be calculated after hotseatBarSizePx is correct,
        // for the available height to be correct
        if (mIsResponsiveGrid) {
            int availableResponsiveWidth =
                    mDeviceProperties.getAvailableWidthPx() - (isVerticalBarLayout() ? hotseatBarSizePx : 0);
            int numWorkspaceColumns = getPanelCount() * inv.numColumns;
            // don't use availableHeightPx because it subtracts mInsets.bottom
            int availableResponsiveHeight = mDeviceProperties.getHeightPx() - mInsets.top
                    - (isVerticalBarLayout() ? 0 : hotseatBarSizePx);
            float responsiveAspectRatio = (float) mDeviceProperties.getWidthPx() / mDeviceProperties.getHeightPx();

            ResponsiveSpecsProvider workspaceSpecs = ResponsiveSpecsProvider.create(
                    new ResourceHelper(context, displayOptionSpec.workspaceSpecsId),
                    ResponsiveSpecType.Workspace);
            mResponsiveWorkspaceWidthSpec = workspaceSpecs.getCalculatedSpec(responsiveAspectRatio,
                    DimensionType.WIDTH, numWorkspaceColumns, availableResponsiveWidth);
            mResponsiveWorkspaceHeightSpec = workspaceSpecs.getCalculatedSpec(responsiveAspectRatio,
                    DimensionType.HEIGHT, inv.numRows, availableResponsiveHeight);

            ResponsiveSpecsProvider allAppsSpecs = ResponsiveSpecsProvider.create(
                    new ResourceHelper(context, displayOptionSpec.allAppsSpecsId),
                    ResponsiveSpecType.AllApps);
            mResponsiveAllAppsWidthSpec = allAppsSpecs.getCalculatedSpec(responsiveAspectRatio,
                    DimensionType.WIDTH, numShownAllAppsColumns, mDeviceProperties.getAvailableWidthPx(),
                    mResponsiveWorkspaceWidthSpec);
            if (inv.appListAlignedWithWorkspaceRow >= 0) {
                allAppsTopPadding += mResponsiveWorkspaceHeightSpec.getStartPaddingPx()
                       + inv.appListAlignedWithWorkspaceRow
                               * (mResponsiveWorkspaceHeightSpec.getCellSizePx()
                                       + mResponsiveWorkspaceHeightSpec.getGutterPx());
            }
            mResponsiveAllAppsHeightSpec = allAppsSpecs.getCalculatedSpec(responsiveAspectRatio,
                    DimensionType.HEIGHT, inv.numAllAppsRowsForCellHeightCalculation,
                    mDeviceProperties.getHeightPx() - allAppsTopPadding, mResponsiveWorkspaceHeightSpec);

            ResponsiveSpecsProvider folderSpecs = ResponsiveSpecsProvider.create(
                    new ResourceHelper(context, displayOptionSpec.folderSpecsId),
                    ResponsiveSpecType.Folder);
            mResponsiveFolderWidthSpec = folderSpecs.getCalculatedSpec(responsiveAspectRatio,
                    DimensionType.WIDTH, numFolderColumns,
                    mResponsiveWorkspaceWidthSpec.getAvailableSpace(),
                    mResponsiveWorkspaceWidthSpec);
            mResponsiveFolderHeightSpec = folderSpecs.getCalculatedSpec(responsiveAspectRatio,
                    DimensionType.HEIGHT, numFolderRows,
                    mResponsiveWorkspaceHeightSpec.getAvailableSpace(),
                    mResponsiveWorkspaceHeightSpec);

            ResponsiveCellSpecsProvider allAppsCellSpecs = ResponsiveCellSpecsProvider.create(
                    new ResourceHelper(context, displayOptionSpec.allAppsCellSpecsId));
            mResponsiveAllAppsCellSpec = allAppsCellSpecs.getCalculatedSpec(
                    responsiveAspectRatio,
                    mResponsiveAllAppsHeightSpec.getAvailableSpace(),
                    mResponsiveWorkspaceCellSpec);
        }

        desiredWorkspaceHorizontalMarginPx = getHorizontalMarginPx(inv, res);
        desiredWorkspaceHorizontalMarginOriginalPx = desiredWorkspaceHorizontalMarginPx;

        splitPlaceholderInset = res.getDimensionPixelSize(R.dimen.split_placeholder_inset);
        // We need to use the full window bounds for split determination because on near-square
        // devices, the available bounds (bounds minus insets) may actually be in landscape while
        // actually portrait
        int leftRightSplitPortraitResId = Resources.getSystem().getIdentifier(
                "config_leftRightSplitInPortrait", "bool", "android");
        boolean allowLeftRightSplitInPortrait =
                    leftRightSplitPortraitResId > 0
                    && res.getBoolean(leftRightSplitPortraitResId);
        if (allowLeftRightSplitInPortrait && mDeviceProperties.isTablet()) {
            isLeftRightSplit = !mDeviceProperties.isLandscape();
        } else {
            isLeftRightSplit = mDeviceProperties.isLandscape();
        }

        // Calculate all of the remaining variables.
        extraSpace = updateAvailableDimensions(context);

        calculateAndSetWorkspaceVerticalPadding(context, inv, extraSpace);

        int cellLayoutPadding =
                mDeviceProperties.isTwoPanels() ? cellLayoutBorderSpacePx.x / 2 : res.getDimensionPixelSize(
                        R.dimen.cell_layout_padding);
        cellLayoutPaddingPx = new Rect(cellLayoutPadding, cellLayoutPadding, cellLayoutPadding,
                cellLayoutPadding);
        updateWorkspacePadding();

        // Folder scaling requires correct workspace paddings
        updateAvailableFolderCellDimensions(res);

        // Hotseat and QSB width depends on updated cellSize and workspace padding
        recalculateHotseatWidthAndBorderSpace();

        if (mIsResponsiveGrid && isVerticalBarLayout()) {
            hotseatBorderSpace = cellLayoutBorderSpacePx.y;
        }


        if (shouldShowAllAppsOnSheet()) {
            allAppsPadding.top = allAppsTopPadding;
            allAppsShiftRange = mDeviceProperties.getHeightPx() - allAppsTopPadding + mInsets.top;
        } else {
            allAppsPadding.top = 0;
            allAppsShiftRange =
                    res.getDimensionPixelSize(R.dimen.all_apps_starting_vertical_translate);
        }
        allAppsOpenDuration = res.getInteger(R.integer.config_allAppsOpenDuration);
        allAppsCloseDuration = res.getInteger(R.integer.config_allAppsCloseDuration);

        flingToDeleteThresholdVelocity = res.getDimensionPixelSize(
                R.dimen.drag_flingToDeleteMinVelocity);

        mViewScaleProvider = viewScaleProvider;

        dimensionOverrideProvider.accept(this);

        // Check if notification dots should show the notification count
        boolean showNotificationCount = PreferenceExtensionsKt
                .firstBlocking(preferenceManager2.getShowNotificationCount());

        // Load the default font to use on notification dots
        Typeface typeface = null;
        if (showNotificationCount) {
            typeface = ResourcesCompat.getFont(context, R.font.googlesansflex_variable);
        }

        // Load dot color
        ColorOption dotColorOption = PreferenceExtensionsKt.firstBlocking(preferenceManager2.getNotificationDotColor());
        int dotColor = dotColorOption.getColorPreferenceEntry().getLightColor().invoke(context);

        // Load counter color
        ColorOption counterColorOption = PreferenceExtensionsKt
                .firstBlocking(preferenceManager2.getNotificationDotTextColor());
        int countColor = counterColorOption.getColorPreferenceEntry().getLightColor().invoke(context);

        // This is done last, after iconSizePx is calculated above.
        mDotRendererWorkSpace = createDotRenderer(themeManager, iconSizePx, dotRendererCache, showNotificationCount, typeface, dotColor, countColor);
        mDotRendererAllApps = createDotRenderer(themeManager,
                getAllAppsProfile().getIconSizePx(), dotRendererCache, showNotificationCount, typeface, dotColor, countColor);
    }

    public DeviceProperties getDeviceProperties() {
        return mDeviceProperties;
    }

    public OverviewProfile getOverviewProfile() {
        return overviewProfile;
    }

    public HotseatProfile getHotseatProfile() {
        return hotseatProfile;
    }

    /**
     * Takes care of the logic that determines if we show a the QSB inline or not.
     */
    private boolean isQsbInline(
            InvariantDeviceProfile inv,
            HotseatProfile hotseatProfile,
            DeviceProperties deviceProperties,
            boolean isScalableGrid
    ) {
        // For foldable (two panel), we inline the qsb if we have the screen open and we are in
        // either Landscape or Portrait. This cal also be disabled in the device_profile.xml
        boolean twoPanelCanInline = inv.inlineQsb[INDEX_TWO_PANEL_PORTRAIT]
                || inv.inlineQsb[INDEX_TWO_PANEL_LANDSCAPE];

        // In tablets we inline in both orientations but only if we have enough space in the QSB
        boolean tabletInlineQsb = inv.inlineQsb[INDEX_DEFAULT] || inv.inlineQsb[INDEX_LANDSCAPE];
        boolean canQsbInline = deviceProperties.isTwoPanels() ? twoPanelCanInline : tabletInlineQsb;
        canQsbInline = canQsbInline && hotseatProfile.getQsbHeight() > 0;

        return (isScalableGrid && inv.inlineQsb[mTypeIndex] && canQsbInline)
                || inv.isFixedLandscape;
    }

    private static DotRenderer createDotRenderer(
            @NonNull ThemeManager themeManager, int size, @NonNull SparseArray<DotRenderer> cache) {
        DotRenderer renderer = cache.get(size);
        if (renderer == null) {
            renderer = new DotRenderer(
                    size,
                    themeManager.getIconShape().getPath(DEFAULT_DOT_SIZE),
                    DEFAULT_DOT_SIZE);
            cache.put(size, renderer);
        }
        return renderer;
    }

    // Lawnchair
    private static DotRenderer createDotRenderer(
        @NonNull ThemeManager themeManager, int size, @NonNull SparseArray<DotRenderer> cache, boolean showNotificationCount, Typeface typeface, int dotColor, int countColor) {
        DotRenderer renderer = cache.get(size);

        if (renderer == null) {
            renderer = new DotRenderer(
                size,
                themeManager.getIconShape().getPath(DEFAULT_DOT_SIZE),
                DEFAULT_DOT_SIZE,
                showNotificationCount,
                typeface,
                dotColor,
                countColor);
            cache.put(size, renderer);
        }
        return renderer;
    }

    /**
     * Return maximum of all apps row count displayed on screen. Note that 1) Partially displayed
     * row is counted as 1 row, and 2) we don't exclude the space of floating search bar. This
     * method is used for calculating number of {@link BubbleTextView} we need to pre-inflate. Thus
     * reasonable over estimation is fine.
     */
    public int getMaxAllAppsRowCount() {
        return (int) (Math.ceil((mDeviceProperties.getAvailableHeightPx() - allAppsPadding.top)
                / (float) getAllAppsProfile().getCellHeightPx()));
    }

    /**
     * QSB width is always calculated because when in 3 button nav the width doesn't follow the
     * width of the hotseat.
     */
    private int calculateQsbWidth(int hotseatBorderSpace) {
        int iconExtraSpacePx = iconSizePx - getIconVisibleSizePx(iconSizePx);
        if (isQsbInline) {
            int columns = getPanelCount() * inv.numColumns;
            return getIconToIconWidthForColumns(columns)
                    - iconSizePx * numShownHotseatIcons
                    - hotseatBorderSpace * numShownHotseatIcons
                    - iconExtraSpacePx;
        } else {
            return getIconToIconWidthForColumns(mHotseatColumnSpan) - iconExtraSpacePx;
        }
    }

    private int getIconToIconWidthForColumns(int columns) {
        return columns * getCellSize().x
                + (columns - 1) * cellLayoutBorderSpacePx.x
                - getCellHorizontalSpace();
    }

    private int getHorizontalMarginPx(InvariantDeviceProfile idp, Resources res) {
        if (mIsResponsiveGrid) {
            return mResponsiveWorkspaceWidthSpec.getStartPaddingPx();
        }

        if (isVerticalBarLayout()) {
            return 0;
        }

        return mIsScalableGrid
                ? pxFromDp(idp.horizontalMargin[mTypeIndex], mMetrics)
                : res.getDimensionPixelSize(R.dimen.dynamic_grid_left_right_margin);
    }

    private void calculateAndSetWorkspaceVerticalPadding(Context context,
            InvariantDeviceProfile inv,
            int extraSpace) {
        if (mIsResponsiveGrid) {
            workspaceTopPadding = mResponsiveWorkspaceHeightSpec.getStartPaddingPx();
            workspaceBottomPadding = mResponsiveWorkspaceHeightSpec.getEndPaddingPx();
        } else if (mIsScalableGrid && inv.devicePaddingId != INVALID_RESOURCE_HANDLE) {
            // Paddings were created assuming no scaling, so we first unscale the extra space.
            int unscaledExtraSpace = (int) (extraSpace / cellScaleToFit);
            DevicePaddings devicePaddings = new DevicePaddings(context, inv.devicePaddingId);
            DevicePadding padding = devicePaddings.getDevicePadding(unscaledExtraSpace);
            maxEmptySpace = padding.getMaxEmptySpacePx();

            int paddingWorkspaceTop = padding.getWorkspaceTopPadding(unscaledExtraSpace);
            int paddingWorkspaceBottom = padding.getWorkspaceBottomPadding(unscaledExtraSpace);

            workspaceTopPadding = Math.round(paddingWorkspaceTop * cellScaleToFit);
            workspaceBottomPadding = Math.round(paddingWorkspaceBottom * cellScaleToFit);
        }
    }

    /** Updates hotseatCellHeightPx and hotseatBarSizePx */
    private void updateHotseatSizes(int hotseatIconSizePx) {
        int iconTextHeight = Utilities.calculateTextHeight(iconTextSizePx);
        boolean isLabelInDock = PreferenceExtensionsKt.firstBlocking(preferenceManager2.getEnableLabelInDock());
        // Ensure there is enough space for folder icons, which have a slightly larger radius.
        hotseatCellHeightPx = getIconSizeWithOverlap(hotseatIconSizePx * 2) - hotseatIconSizePx / 2;
        hotseatCellHeightPx += isLabelInDock ? iconTextHeight : 0;
        hotseatQsbSpace += isLabelInDock ? (iconTextHeight / 2) : 0;
        
        int space = Math.abs(hotseatCellHeightPx / 2) - 16;

        hotseatBarBottomSpacePx *= PreferenceExtensionsKt
            .firstBlocking(preferenceManager2.getHotseatBottomFactor());

        if (isVerticalBarLayout()) {
            hotseatBarSizePx = hotseatIconSizePx + getHotseatProfile().getBarEdgePaddingPx()
                    + getHotseatProfile().getBarWorkspaceSpacePx()
                    + space;
        } else if (isQsbInline) {
            hotseatBarSizePx = Math.max(hotseatIconSizePx, getHotseatProfile().getQsbVisualHeight())
                    + hotseatBarBottomSpacePx
                    + space;
        } else {
            hotseatBarSizePx = hotseatIconSizePx
                    + hotseatQsbSpace
                    + getHotseatProfile().getQsbVisualHeight()
                    + hotseatBarBottomSpacePx
                    + space;
        }
        var isHotseatEnabled = PreferenceExtensionsKt.firstBlocking(preferenceManager2.isHotseatEnabled());
        if (!isHotseatEnabled) {
            hotseatBarSizePx = 0;
        }
    }

    /**
     * Calculates the width of the hotseat, changing spaces between the icons and removing icons if
     * necessary.
     */
    public void recalculateHotseatWidthAndBorderSpace() {
        if (!mIsScalableGrid) return;

        updateHotseatWidthAndBorderSpace(inv.numColumns);
        int numWorkspaceColumns = getPanelCount() * inv.numColumns;
        if (mDeviceProperties.isTwoPanels()) {
            updateHotseatWidthAndBorderSpace(inv.numDatabaseHotseatIcons);
            // If hotseat doesn't fit with current width, increase column span to fit by multiple
            // of 2.
            while (hotseatBorderSpace < getHotseatProfile().getMinIconSpacePx()
                    && mHotseatColumnSpan < numWorkspaceColumns) {
                updateHotseatWidthAndBorderSpace(mHotseatColumnSpan + 2);
            }
        }
        if (isQsbInline) {
            // If QSB is inline, reduce column span until it fits.
            int maxHotseatWidthAllowedPx = getIconToIconWidthForColumns(numWorkspaceColumns);
            int minHotseatWidthRequiredPx =
                    getHotseatProfile().getMinQsbWidthPx() + hotseatBorderSpace + mHotseatWidthPx;
            while (minHotseatWidthRequiredPx > maxHotseatWidthAllowedPx
                    && mHotseatColumnSpan > 1) {
                updateHotseatWidthAndBorderSpace(mHotseatColumnSpan - 1);
                minHotseatWidthRequiredPx = getHotseatProfile().getMinQsbWidthPx()
                        + hotseatBorderSpace + mHotseatWidthPx;
            }
        }
        hotseatQsbWidth = calculateQsbWidth(hotseatBorderSpace);

        // Spaces should be correct when the nav buttons are not inline
        if (!getHotseatProfile().getAreNavButtonsInline()) {
            return;
        }

        // The side space with inline buttons should be what is defined in InvariantDeviceProfile
        int sideSpacePx = getHotseatProfile().getInlineNavButtonsEndSpacingPx();
        int maxHotseatWidthPx = mDeviceProperties.getAvailableWidthPx() - sideSpacePx
                - getHotseatProfile().getBarEndOffset();
        int maxHotseatIconsWidthPx = maxHotseatWidthPx - (isQsbInline ? hotseatQsbWidth : 0);
        hotseatBorderSpace = calculateHotseatBorderSpace(maxHotseatIconsWidthPx,
                (isQsbInline ? 1 : 0) + /* border between nav buttons and first icon */ 1);

        if (hotseatBorderSpace >= getHotseatProfile().getMinIconSpacePx()) {
            return;
        }

        // Border space can't be less than the minimum
        hotseatBorderSpace = getHotseatProfile().getMinIconSpacePx();
        int requiredWidth = getHotseatRequiredWidth();

        // If there is an inline qsb, change its size
        if (isQsbInline) {
            hotseatQsbWidth -= requiredWidth - maxHotseatWidthPx;
            if (hotseatQsbWidth >= getHotseatProfile().getMinQsbWidthPx()) {
                return;
            }

            // QSB can't be less than the minimum
            hotseatQsbWidth = getHotseatProfile().getMinQsbWidthPx();
        }

        maxHotseatIconsWidthPx = maxHotseatWidthPx - (isQsbInline ? hotseatQsbWidth : 0);

        // If it still doesn't fit, start removing icons
        do {
            numShownHotseatIcons--;
            hotseatBorderSpace = calculateHotseatBorderSpace(maxHotseatIconsWidthPx,
                    (isQsbInline ? 1 : 0) + /* border between nav buttons and first icon */ 1);
        } while (
                hotseatBorderSpace < getHotseatProfile().getMinIconSpacePx()
                        && numShownHotseatIcons > 1);
    }

    private void updateHotseatWidthAndBorderSpace(int columns) {
        mHotseatColumnSpan = columns;
        mHotseatWidthPx = getIconToIconWidthForColumns(mHotseatColumnSpan);
        hotseatBorderSpace = calculateHotseatBorderSpace(mHotseatWidthPx, /* numExtraBorder= */ 0);
    }

    private Point getCellLayoutBorderSpace(InvariantDeviceProfile idp) {
        return getCellLayoutBorderSpace(idp, 1f);
    }

    private Point getCellLayoutBorderSpace(InvariantDeviceProfile idp, float scale) {
        int horizontalSpacePx = 0;
        int verticalSpacePx = 0;

        if (mIsResponsiveGrid) {
            horizontalSpacePx = mResponsiveWorkspaceWidthSpec.getGutterPx();
            verticalSpacePx = mResponsiveWorkspaceHeightSpec.getGutterPx();
        } else if (mIsScalableGrid) {
            horizontalSpacePx = pxFromDp(idp.borderSpaces[mTypeIndex].x, mMetrics, scale);
            verticalSpacePx = pxFromDp(idp.borderSpaces[mTypeIndex].y, mMetrics, scale);
        }

        return new Point(horizontalSpacePx, verticalSpacePx);
    }

    public Info getDisplayInfo() {
        return mInfo;
    }

    @VisibleForTesting
    public int getHotseatColumnSpan() {
        return mHotseatColumnSpan;
    }

    @VisibleForTesting
    public int getHotseatWidthPx() {
        return mHotseatWidthPx;
    }

    public Builder toBuilder(Context context) {
        WindowBounds bounds = new WindowBounds(
                mDeviceProperties.getWidthPx(),
                mDeviceProperties.getHeightPx(),
                mDeviceProperties.getAvailableWidthPx(),
                mDeviceProperties.getAvailableHeightPx(),
                mDeviceProperties.getRotationHint()
        );
        bounds.bounds.offsetTo(mDeviceProperties.getWindowX(), mDeviceProperties.getWindowY());
        bounds.insets.set(mInsets);

        SparseArray<DotRenderer> dotRendererCache = new SparseArray<>();
        dotRendererCache.put(iconSizePx, mDotRendererWorkSpace);
        dotRendererCache.put(getAllAppsProfile().getIconSizePx(), mDotRendererAllApps);

        return inv.newDPBuilder(context, mInfo)
                .setWindowBounds(bounds)
                .setIsMultiDisplay(mDeviceProperties.isMultiDisplay())
                .setMultiWindowMode(mDeviceProperties.isMultiWindowMode())
                .setDotRendererCache(dotRendererCache)
                .setGestureMode(mDeviceProperties.isGestureMode())
                .setDisplayOptionSpec(mDisplayOptionSpec);
    }

    public DeviceProfile copy(Context context) {
        return toBuilder(context).build();
    }

    /**
     * TODO: Move this to the builder as part of setMultiWindowMode
     */
    public DeviceProfile getMultiWindowProfile(Context context, WindowBounds windowBounds) {
        DeviceProfile profile = toBuilder(context)
                .setWindowBounds(windowBounds)
                .setMultiWindowMode(true)
                .build();

        // We use these scales to measure and layout the widgets using their full invariant profile
        // sizes and then draw them scaled and centered to fit in their multi-window mode cellspans.
        float appWidgetScaleX = (float) profile.getCellSize().x / getCellSize().x;
        float appWidgetScaleY = (float) profile.getCellSize().y / getCellSize().y;
        if (appWidgetScaleX != 1 || appWidgetScaleY != 1) {
            final PointF p = new PointF(appWidgetScaleX, appWidgetScaleY);
            profile = profile.toBuilder(context)
                    .setViewScaleProvider(i -> p)
                    .build();
        }

        profile.hideWorkspaceLabelsIfNotEnoughSpace();

        return profile;
    }

    /**
     * Checks if there is enough space for labels on the workspace.
     * If there is not, labels on the Workspace are hidden.
     * It is important to call this method after the All Apps variables have been set.
     */
    private void hideWorkspaceLabelsIfNotEnoughSpace() {
        float iconTextHeight = Utilities.calculateTextHeight(iconTextSizePx);
        float workspaceCellPaddingY = getCellSize().y - iconSizePx - iconDrawablePaddingPx
                - iconTextHeight;

        // We want enough space so that the text is closer to its corresponding icon.
        if (workspaceCellPaddingY < iconTextHeight) {
            iconTextSizePx = 0;
            iconDrawablePaddingPx = 0;
            cellHeightPx = getIconSizeWithOverlap(iconSizePx);
            maxIconTextLineCount = 0;
            // TODO(420933882) Group all modifications of AllAppsProfile in one place
            mAllAppsProfile = AllAppsProfile.Factory.autoResizeAllAppsCells(getAllAppsProfile());
        }
    }

    /**
     * Returns the amount of extra (or unused) vertical space.
     */
    private int updateAvailableDimensions(Context context) {
        iconCenterVertically = (mIsScalableGrid || mIsResponsiveGrid) && isVerticalBarLayout();

        if (mIsResponsiveGrid) {
            iconSizePx = mResponsiveWorkspaceCellSpec.getIconSize();
            iconTextSizePx = mResponsiveWorkspaceCellSpec.getIconTextSize();
            mIconDrawablePaddingOriginalPx = mResponsiveWorkspaceCellSpec.getIconDrawablePadding();
            maxIconTextLineCount = mResponsiveWorkspaceCellSpec.getIconTextMaxLineCount();
            updateIconSize(1f, context);
            updateWorkspacePadding();
            return 0;
        }

        float invIconSizeDp = inv.iconSize[mTypeIndex];
        float invIconTextSizeSp = inv.iconTextSize[mTypeIndex];
        iconSizePx = Math.max(1, pxFromDp(invIconSizeDp, mMetrics));
        iconTextSizePx = pxFromSp(invIconTextSizeSp, mMetrics);

        updateIconSize(1f, context);
        updateWorkspacePadding();

        // Check to see if the icons fit within the available height.
        float usedHeight = getCellLayoutHeightSpecification();
        final int maxHeight = getCellLayoutHeight();
        float extraHeight = Math.max(0, maxHeight - usedHeight);
        float scaleY = maxHeight / usedHeight;
        boolean shouldScale = scaleY < 1f;

        float scaleX = 1f;
        if (mIsScalableGrid) {
            // We scale to fit the cellWidth and cellHeight in the available space.
            // The benefit of scalable grids is that we can get consistent aspect ratios between
            // devices.
            float usedWidth =
                    getCellLayoutWidthSpecification() + (desiredWorkspaceHorizontalMarginPx * 2);
            // We do not subtract padding here, as we also scale the workspace padding if needed.
            scaleX = mDeviceProperties.getAvailableWidthPx() / usedWidth;
            shouldScale = true;
        }

        if (shouldScale) {
            float scale = Math.min(scaleX, scaleY);
            updateIconSize(scale, context);
            extraHeight = Math.max(0, maxHeight - getCellLayoutHeightSpecification());
        }

        return Math.round(extraHeight);
    }

    private int getCellLayoutHeightSpecification() {
        return (cellHeightPx * inv.numRows) + (cellLayoutBorderSpacePx.y * (inv.numRows - 1))
                + cellLayoutPaddingPx.top + cellLayoutPaddingPx.bottom;
    }

    private int getCellLayoutWidthSpecification() {
        int numColumns = getPanelCount() * inv.numColumns;
        return (cellWidthPx * numColumns) + (cellLayoutBorderSpacePx.x * (numColumns - 1))
                + cellLayoutPaddingPx.left + cellLayoutPaddingPx.right;
    }

    private int getNormalizedIconDrawablePadding(int iconSizePx, int iconDrawablePadding) {
        return Math.max(0, iconDrawablePadding
                - ((iconSizePx - getIconVisibleSizePx(iconSizePx)) / 2));
    }

    private int getNormalizedIconDrawablePadding() {
        return getNormalizedIconDrawablePadding(iconSizePx, mIconDrawablePaddingOriginalPx);
    }

    private int getNormalizedFolderChildDrawablePaddingPx(int textHeight) {
        // TODO(b/235886078): workaround needed because of this bug
        // Icons are 10% larger on XML than their visual size,
        // so remove that extra space to get labels closer to the correct padding
        int drawablePadding = (folderCellHeightPx - folderChildIconSizePx - textHeight) / 3;

        int iconSizeDiff = folderChildIconSizePx - getIconVisibleSizePx(folderChildIconSizePx);
        return Math.max(0, drawablePadding - iconSizeDiff / 2);
    }

    private int getIconSizeWithOverlap(int iconSize) {
        return (int) Math.ceil(iconSize * ICON_OVERLAP_FACTOR);
    }

    /**
     * Updating the iconSize affects many aspects of the launcher layout, such as: iconSizePx,
     * iconTextSizePx, iconDrawablePaddingPx, cellWidth/Height, allApps* variants,
     * hotseat sizes, workspaceSpringLoadedShrinkFactor, folderIconSizePx, and folderIconOffsetYPx.
     */
    public void updateIconSize(float scale, Context context) {
        // Icon scale should never exceed 1, otherwise pixellation may occur.
        iconScale = Math.min(1f, scale);
        cellScaleToFit = scale;

        // Workspace
        final boolean isVerticalLayout = isVerticalBarLayout();
        cellLayoutBorderSpacePx = getCellLayoutBorderSpace(inv, scale);

        if (mIsResponsiveGrid) {
            cellWidthPx = mResponsiveWorkspaceWidthSpec.getCellSizePx();
            cellHeightPx = mResponsiveWorkspaceHeightSpec.getCellSizePx();
            maxIconTextLineCount = mResponsiveWorkspaceCellSpec.getIconTextMaxLineCount();

            if (cellWidthPx < iconSizePx) {
                // get a smaller icon size
                iconSizePx = mIconSizeSteps.getIconSmallerThan(cellWidthPx);
            }

            if (isVerticalLayout) {
                iconDrawablePaddingPx = 0;
                iconTextSizePx = 0;
                maxIconTextLineCount = 0;
            } else {
                iconDrawablePaddingPx = getNormalizedIconDrawablePadding();
            }

            CellContentDimensions cellContentDimensions = new CellContentDimensions(iconSizePx,
                    iconDrawablePaddingPx,
                    iconTextSizePx,
                    maxIconTextLineCount);
            int cellContentHeight = cellContentDimensions.resizeToFitCellHeight(cellHeightPx,
                    mIconSizeSteps);
            iconSizePx = cellContentDimensions.getIconSizePx();
            iconDrawablePaddingPx = cellContentDimensions.getIconDrawablePaddingPx();
            iconTextSizePx = cellContentDimensions.getIconTextSizePx();
            maxIconTextLineCount = cellContentDimensions.getMaxLineCount();

            if (isVerticalLayout) {
                cellYPaddingPx = Math.max(0, getCellSize().y - getIconSizeWithOverlap(iconSizePx))
                        / 2;
            } else {
                cellYPaddingPx = Math.max(0, cellHeightPx - cellContentHeight) / 2;
            }
        } else if (mIsScalableGrid) {
            iconDrawablePaddingPx = (int) (getNormalizedIconDrawablePadding() * iconScale);
            cellWidthPx = pxFromDp(inv.minCellSize[mTypeIndex].x, mMetrics, scale);
            cellHeightPx = pxFromDp(inv.minCellSize[mTypeIndex].y, mMetrics, scale);
            maxIconTextLineCount = 1;

            if (cellWidthPx < iconSizePx) {
                // If cellWidth no longer fit iconSize, reduce borderSpace to make cellWidth bigger.
                int numColumns = getPanelCount() * inv.numColumns;
                int numBorders = numColumns - 1;
                int extraWidthRequired = (iconSizePx - cellWidthPx) * numColumns;
                if (cellLayoutBorderSpacePx.x * numBorders >= extraWidthRequired) {
                    cellWidthPx = iconSizePx;
                    cellLayoutBorderSpacePx.x -= extraWidthRequired / numBorders;
                } else {
                    // If it still doesn't fit, set borderSpace to 0 and distribute the space for
                    // cellWidth, and reduce iconSize.
                    cellWidthPx = (cellWidthPx * numColumns
                            + cellLayoutBorderSpacePx.x * numBorders) / numColumns;
                    iconSizePx = Math.min(iconSizePx, cellWidthPx);
                    cellLayoutBorderSpacePx.x = 0;
                }
            }

            int cellTextAndPaddingHeight =
                    iconDrawablePaddingPx + Utilities.calculateTextHeight(iconTextSizePx);
            int cellContentHeight = iconSizePx + cellTextAndPaddingHeight;
            if (cellHeightPx < cellContentHeight) {
                // If cellHeight no longer fit iconSize, reduce borderSpace to make cellHeight
                // bigger.
                int numBorders = inv.numRows - 1;
                int extraHeightRequired = (cellContentHeight - cellHeightPx) * inv.numRows;
                if (cellLayoutBorderSpacePx.y * numBorders >= extraHeightRequired) {
                    cellHeightPx = cellContentHeight;
                    cellLayoutBorderSpacePx.y -= extraHeightRequired / numBorders;
                } else {
                    // If it still doesn't fit, set borderSpace to 0 to recover space.
                    cellHeightPx = (cellHeightPx * inv.numRows
                            + cellLayoutBorderSpacePx.y * numBorders) / inv.numRows;
                    cellLayoutBorderSpacePx.y = 0;
                    // Reduce iconDrawablePaddingPx to make cellContentHeight smaller.
                    int cellContentWithoutPadding = cellContentHeight - iconDrawablePaddingPx;
                    if (cellContentWithoutPadding <= cellHeightPx) {
                        iconDrawablePaddingPx = cellContentHeight - cellHeightPx;
                    } else {
                        // If it still doesn't fit, set iconDrawablePaddingPx to 0 to recover space,
                        // then proportional reduce iconSizePx and iconTextSizePx to fit.
                        iconDrawablePaddingPx = 0;
                        float ratio = cellHeightPx / (float) cellContentWithoutPadding;
                        iconSizePx = (int) (iconSizePx * ratio);
                        iconTextSizePx = (int) (iconTextSizePx * ratio);
                    }
                    cellTextAndPaddingHeight =
                            iconDrawablePaddingPx + Utilities.calculateTextHeight(iconTextSizePx);
                }
                cellContentHeight = iconSizePx + cellTextAndPaddingHeight;
            }
            cellYPaddingPx = Math.max(0, cellHeightPx - cellContentHeight) / 2;
            desiredWorkspaceHorizontalMarginPx =
                    (int) (desiredWorkspaceHorizontalMarginOriginalPx * scale);
        } else {
            iconDrawablePaddingPx = (int) (getNormalizedIconDrawablePadding() * iconScale);
            cellWidthPx = iconSizePx + iconDrawablePaddingPx;
            cellHeightPx = getIconSizeWithOverlap(iconSizePx)
                    + iconDrawablePaddingPx
                    + Utilities.calculateTextHeight(iconTextSizePx);
            maxIconTextLineCount = 1;
            int cellPaddingY = (getCellSize().y - cellHeightPx) / 2;
            if (iconDrawablePaddingPx > cellPaddingY && !isVerticalLayout
                    && !mDeviceProperties.isMultiWindowMode()) {
                // Ensures that the label is closer to its corresponding icon. This is not an issue
                // with vertical bar layout or multi-window mode since the issue is handled
                // separately with their calls to {@link #adjustToHideWorkspaceLabels}.
                cellHeightPx -= (iconDrawablePaddingPx - cellPaddingY);
                iconDrawablePaddingPx = cellPaddingY;
            }
        }

        iconTextSizePx *= mTextFactors.getIconTextSizeFactor();

        // All apps
        if (mIsResponsiveGrid) {
            mAllAppsProfile = AllAppsProfile.Factory.createAllAppsWithResponsive(
                    mResponsiveAllAppsCellSpec,
                    mResponsiveAllAppsWidthSpec,
                    mResponsiveAllAppsHeightSpec,
                    mIconSizeSteps,
                    isVerticalBarLayout()
            );
            updateAllAppsWithResponsiveMeasures();
        } else {
            // LC: All apps should use scale 1.0, not workspace scale
            // This ensures drawer icons are independent of workspace scaling
            //updateAllAppsIconSize(1.0f, context.getResources());
            // pE-TODO(QPR1): Investigate
            mAllAppsProfile = AllAppsProfile.Factory.createAllAppsProfile(
                    context.getResources(),
                    inv,
                    mMetrics,
                    mIsScalableGrid,
                    mTypeIndex,
                    scale,
                    iconSizePx,
                    mIconDrawablePaddingOriginalPx
            );
        }
        updateAllAppsContainerWidth();
        if (isVerticalLayout && !mIsResponsiveGrid) {
            hideWorkspaceLabelsIfNotEnoughSpace();
        }

        if (inv.enableTwoLinesInAllApps
                && !(mIsResponsiveGrid && getAllAppsProfile().getMaxAllAppsTextLineCount() == 2)) {
            // Add extra textHeight to the existing allAppsCellHeight.
            mAllAppsProfile = getAllAppsProfile().copyWithCellHeightPx(
                    getAllAppsProfile().getCellHeightPx() + Utilities.calculateTextHeight(
                            getAllAppsProfile().getIconTextSizePx())
            );
        }

        updateHotseatSizes(iconSizePx);

        // Folder icon
        folderIconSizePx = Math.round(iconSizePx * ICON_VISIBLE_AREA_FACTOR);
        folderIconOffsetYPx = (iconSizePx - folderIconSizePx) / 2;

        // Update widget padding:
        float minSpacing = pxFromDp(MIN_WIDGET_PADDING_DP, mMetrics);
        if (cellLayoutBorderSpacePx.x < minSpacing
                || cellLayoutBorderSpacePx.y < minSpacing) {
            widgetPadding.left = widgetPadding.right =
                    Math.round(Math.max(0, minSpacing - cellLayoutBorderSpacePx.x));
            widgetPadding.top = widgetPadding.bottom =
                    Math.round(Math.max(0, minSpacing - cellLayoutBorderSpacePx.y));
        } else {
            widgetPadding.setEmpty();
        }
    }

    /**
     * This method calculates the space between the icons to achieve a certain width.
     */
    private int calculateHotseatBorderSpace(float hotseatWidthPx, int numExtraBorder) {
        int numBorders = (numShownHotseatIcons - 1 + numExtraBorder);
        if (numBorders <= 0) return 0;

        float hotseatIconsTotalPx = iconSizePx * numShownHotseatIcons;
        int hotseatBorderSpacePx = (int) (hotseatWidthPx - hotseatIconsTotalPx) / numBorders;
        return Math.min(hotseatBorderSpacePx, getHotseatProfile().getMaxIconSpacePx());
    }

    private void updateAllAppsWithResponsiveMeasures() {
        // This workaround is needed to align AllApps icons with Workspace icons
        // since AllApps doesn't have borders between cells
        int halfBorder = getAllAppsProfile().getBorderSpacePx().x / 2;
        allAppsPadding.left = mResponsiveAllAppsWidthSpec.getStartPaddingPx() - halfBorder;
        allAppsPadding.right = mResponsiveAllAppsWidthSpec.getEndPaddingPx() - halfBorder;
    }


    private void updateAllAppsContainerWidth() {
        int cellLayoutHorizontalPadding =
                (cellLayoutPaddingPx.left + cellLayoutPaddingPx.right) / 2;
        if (mDeviceProperties.isTablet()) {
            int usedWidth = (getAllAppsProfile().getCellWidthPx() * numShownAllAppsColumns)
                    + (getAllAppsProfile().getBorderSpacePx().x * (numShownAllAppsColumns - 1))
                    + allAppsPadding.left + allAppsPadding.right;
            allAppsLeftRightMargin = Math.max(1, (mDeviceProperties.getAvailableWidthPx() - usedWidth) / 2);
        } else if (!mIsResponsiveGrid) {
            allAppsPadding.left = allAppsPadding.right =
                    Math.max(0, desiredWorkspaceHorizontalMarginPx + cellLayoutHorizontalPadding
                            - (getAllAppsProfile().getBorderSpacePx().x / 2));
        }
        var allAppLeftRightMarginMultiplier = PreferenceExtensionsKt
                .firstBlocking(preferenceManager2.getDrawerLeftRightMarginFactor());
        var marginMultiplier = allAppLeftRightMarginMultiplier * (!getDeviceProperties().isTablet() ? 100 : 2);
        allAppsLeftRightMargin = (int) (allAppsLeftRightMargin * marginMultiplier);

        // todo fix how drawer padding values are calculated in responsive grid type
        int leftPadding = (int) (allAppsPadding.left != 0 ? allAppsPadding.left * marginMultiplier : marginMultiplier);
        int rightPadding = (int) (allAppsPadding.right != 0 ? allAppsPadding.right * marginMultiplier
                : marginMultiplier);

        allAppsPadding.left = leftPadding;
        allAppsPadding.right = rightPadding;
    }

    /** Whether All Apps should be presented on a bottom sheet. */
    public boolean shouldShowAllAppsOnSheet() {
        return mDeviceProperties.isTablet() || Flags.allAppsSheetForHandheld();
    }

    private void setupAllAppsStyle(Context context) {
        TypedArray allAppsStyle = context.obtainStyledAttributes(
                inv.allAppsStyle != INVALID_RESOURCE_HANDLE ? inv.allAppsStyle
                        : R.style.AllAppsStyleDefault, R.styleable.AllAppsStyle);

        allAppsPadding.left = allAppsPadding.right = allAppsStyle.getDimensionPixelSize(
                R.styleable.AllAppsStyle_horizontalPadding, 0);
        allAppsStyle.recycle();
    }

    private void updateAvailableFolderCellDimensions(Resources res) {
        updateFolderCellSize(1f, res);

        // Responsive grid doesn't need to scale the folder
        if (mIsResponsiveGrid) return;

        // For usability we can't have the folder use the whole width of the screen
        Point totalWorkspacePadding = getTotalWorkspacePadding();

        // Check if the folder fit within the available height.
        float contentUsedHeight = folderCellHeightPx * numFolderRows
                + ((numFolderRows - 1) * folderCellLayoutBorderSpacePx.y)
                + folderFooterHeightPx
                + folderContentPaddingTop;
        int contentMaxHeight = mDeviceProperties.getAvailableHeightPx() - totalWorkspacePadding.y;
        float scaleY = contentMaxHeight / contentUsedHeight;

        // Check if the folder fit within the available width.
        float contentUsedWidth = folderCellWidthPx * numFolderColumns
                + ((numFolderColumns - 1) * folderCellLayoutBorderSpacePx.x)
                + folderContentPaddingLeftRight * 2;
        int contentMaxWidth = mDeviceProperties.getAvailableWidthPx() - totalWorkspacePadding.x;
        float scaleX = contentMaxWidth / contentUsedWidth;

        float scale = Math.min(scaleX, scaleY);
        if (scale < 1f) {
            updateFolderCellSize(scale, res);
        }
    }

    private void updateFolderCellSize(float scale, Resources res) {
        int minLabelTextSize = pxFromSp(MIN_FOLDER_TEXT_SIZE_SP, mMetrics, scale);
        if (mIsResponsiveGrid) {
            folderChildIconSizePx = mResponsiveWorkspaceCellSpec.getIconSize();
            folderChildTextSizePx = mResponsiveWorkspaceCellSpec.getIconTextSize();
            folderLabelTextSizePx = Math.max(minLabelTextSize,
                    (int) (folderChildTextSizePx * folderLabelTextScale));
            int textHeight = Utilities.calculateTextHeight(folderChildTextSizePx);

            folderCellWidthPx = mResponsiveFolderWidthSpec.getCellSizePx();
            folderCellHeightPx = mResponsiveFolderHeightSpec.getCellSizePx();
            folderContentPaddingTop = mResponsiveFolderHeightSpec.getStartPaddingPx();
            folderFooterHeightPx = mResponsiveFolderHeightSpec.getEndPaddingPx();

            folderCellLayoutBorderSpacePx = new Point(mResponsiveFolderWidthSpec.getGutterPx(),
                    mResponsiveFolderHeightSpec.getGutterPx());

            folderContentPaddingLeftRight = mResponsiveFolderWidthSpec.getStartPaddingPx();

            // Reduce icon width if it's wider than the expected folder cell width
            if (folderCellWidthPx < folderChildIconSizePx) {
                folderChildIconSizePx = mIconSizeSteps.getIconSmallerThan(folderCellWidthPx);
            }

            // Recalculating padding and cell height
            folderChildDrawablePaddingPx = mResponsiveWorkspaceCellSpec.getIconDrawablePadding();

            CellContentDimensions cellContentDimensions = new CellContentDimensions(
                    folderChildIconSizePx,
                    folderChildDrawablePaddingPx,
                    folderChildTextSizePx,
                    mResponsiveWorkspaceCellSpec.getIconTextMaxLineCount());
            cellContentDimensions.resizeToFitCellHeight(folderCellHeightPx, mIconSizeSteps);
            folderChildIconSizePx = cellContentDimensions.getIconSizePx();
            folderChildDrawablePaddingPx = cellContentDimensions.getIconDrawablePaddingPx();
            folderChildTextSizePx = cellContentDimensions.getIconTextSizePx();
            folderLabelTextSizePx = Math.max(minLabelTextSize,
                    (int) (folderChildTextSizePx * folderLabelTextScale));
            maxFolderChildTextLineCount = cellContentDimensions.getMaxLineCount();
            return;
        }

        float invIconSizeDp = inv.iconSize[mTypeIndex];
        float invIconTextSizeDp = inv.iconTextSize[mTypeIndex];
        folderChildIconSizePx = Math.max(1, pxFromDp(invIconSizeDp, mMetrics, scale));
        folderChildTextSizePx = pxFromSp(invIconTextSizeDp, mMetrics, scale);
        folderLabelTextSizePx = Math.max(minLabelTextSize,
                (int) (folderChildTextSizePx * folderLabelTextScale));
        int textHeight = Utilities.calculateTextHeight(folderChildTextSizePx);
        maxFolderChildTextLineCount = 1;

        if (mIsScalableGrid) {
            if (inv.folderStyle == INVALID_RESOURCE_HANDLE) {
                folderCellWidthPx = roundPxValueFromFloat(getCellSize().x * scale);
                folderCellHeightPx = roundPxValueFromFloat(getCellSize().y * scale);
            } else {
                folderCellWidthPx = roundPxValueFromFloat(folderCellWidthPx * scale);
                folderCellHeightPx = roundPxValueFromFloat(folderCellHeightPx * scale);
            }
            // Recalculating padding and cell height
            folderChildDrawablePaddingPx = getNormalizedFolderChildDrawablePaddingPx(textHeight);

            CellContentDimensions cellContentDimensions = new CellContentDimensions(
                    folderChildIconSizePx,
                    folderChildDrawablePaddingPx,
                    folderChildTextSizePx,
                    maxFolderChildTextLineCount);
            cellContentDimensions.resizeToFitCellHeight(folderCellHeightPx, mIconSizeSteps);
            folderChildIconSizePx = cellContentDimensions.getIconSizePx();
            folderChildDrawablePaddingPx = cellContentDimensions.getIconDrawablePaddingPx();
            folderChildTextSizePx = cellContentDimensions.getIconTextSizePx();
            maxFolderChildTextLineCount = cellContentDimensions.getMaxLineCount();

            folderContentPaddingTop = roundPxValueFromFloat(folderContentPaddingTop * scale);
            folderCellLayoutBorderSpacePx = new Point(
                    roundPxValueFromFloat(folderCellLayoutBorderSpacePx.x * scale),
                    roundPxValueFromFloat(folderCellLayoutBorderSpacePx.y * scale)
            );
            folderFooterHeightPx = roundPxValueFromFloat(folderFooterHeightPx * scale);
            folderContentPaddingLeftRight = folderCellLayoutBorderSpacePx.x;
        } else {
            int cellPaddingX = (int) (res.getDimensionPixelSize(R.dimen.folder_cell_x_padding)
                    * scale);
            int cellPaddingY = (int) (res.getDimensionPixelSize(R.dimen.folder_cell_y_padding)
                    * scale);

            folderCellWidthPx = folderChildIconSizePx + 2 * cellPaddingX;
            folderCellHeightPx = folderChildIconSizePx + 2 * cellPaddingY + textHeight;
            folderContentPaddingTop = roundPxValueFromFloat(folderContentPaddingTop * scale);
            folderContentPaddingLeftRight =
                    res.getDimensionPixelSize(R.dimen.folder_content_padding_left_right);
            folderFooterHeightPx =
                    roundPxValueFromFloat(
                            res.getDimensionPixelSize(R.dimen.folder_footer_height_default)
                                    * scale);

            folderChildDrawablePaddingPx = getNormalizedFolderChildDrawablePaddingPx(textHeight);
        }

        folderLabelTextSizePx *= mTextFactors.getIconFolderTextSizeFactor();
        folderChildTextSizePx *= mTextFactors.getIconFolderTextSizeFactor();
    }

    public void updateInsets(Rect insets) {
        mInsets.set(insets);
    }

    /**
     * The current device insets. This is generally same as the insets being dispatched to
     * {@link Insettable} elements, but can differ if the element is using a different profile.
     */
    public Rect getInsets() {
        return mInsets;
    }

    public Point getCellSize() {
        return getCellSize(null);
    }

    public Point getCellSize(Point result) {
        if (result == null) {
            result = new Point();
        }

        int shortcutAndWidgetContainerWidth =
                getCellLayoutWidth() - (cellLayoutPaddingPx.left + cellLayoutPaddingPx.right);
        result.x = calculateCellWidth(shortcutAndWidgetContainerWidth, cellLayoutBorderSpacePx.x,
                inv.numColumns);
        int shortcutAndWidgetContainerHeight =
                getCellLayoutHeight() - (cellLayoutPaddingPx.top + cellLayoutPaddingPx.bottom);
        result.y = calculateCellHeight(shortcutAndWidgetContainerHeight, cellLayoutBorderSpacePx.y,
                inv.numRows);
        return result;
    }

    /**
     * Returns the left and right space on the cell, which is the cell width - icon size
     */
    public int getCellHorizontalSpace() {
        return getCellSize().x - iconSizePx;
    }

    /**
     * Gets the number of panels within the workspace.
     */
    public int getPanelCount() {
        return mDeviceProperties.isTwoPanels() ? 2 : 1;
    }

    /**
     * Gets the space in px from the bottom of last item in the vertical-bar hotseat to the
     * bottom of the screen.
     */
    private int getVerticalHotseatLastItemBottomOffset(Context context) {
        Rect hotseatBarPadding = getHotseatLayoutPadding(context);
        int cellHeight = calculateCellHeight(
                mDeviceProperties.getHeightPx() - hotseatBarPadding.top - hotseatBarPadding.bottom, hotseatBorderSpace,
                numShownHotseatIcons);
        int extraIconEndSpacing = (cellHeight - iconSizePx) / 2;
        return extraIconEndSpacing + hotseatBarPadding.bottom;
    }

    /**
     * Gets the scaled top of the workspace in px for the spring-loaded edit state.
     */
    public float getCellLayoutSpringLoadShrunkTop() {
        return mInsets.top + getDropTargetProfile().getBarTopMarginPx()
                + getDropTargetProfile().getBarSizePx()
                + getDropTargetProfile().getBarBottomMarginPx();
    }

    /**
     * Gets the scaled bottom of the workspace in px for the spring-loaded edit state.
     */
    public float getCellLayoutSpringLoadShrunkBottom(Context context) {
        int topOfHotseat = hotseatBarSizePx + getHotseatProfile().getSpringLoadedBarTopMarginPx();
        return mDeviceProperties.getHeightPx() - (isVerticalBarLayout()
                ? getVerticalHotseatLastItemBottomOffset(context) : topOfHotseat);
    }

    /**
     * Gets the scale of the workspace for the spring-loaded edit state.
     */
    public float getWorkspaceSpringLoadScale(Context context) {
        float scale =
                (getCellLayoutSpringLoadShrunkBottom(context) - getCellLayoutSpringLoadShrunkTop())
                        / getCellLayoutHeight();
        scale = Math.min(scale, 1f);

        // Reduce scale if next pages would not be visible after scaling the workspace.
        int workspaceWidth = mDeviceProperties.getAvailableWidthPx();
        float scaledWorkspaceWidth = workspaceWidth * scale;
        float maxAvailableWidth = workspaceWidth - (2 * workspaceSpringLoadedMinNextPageVisiblePx);
        if (scaledWorkspaceWidth > maxAvailableWidth) {
            scale *= maxAvailableWidth / scaledWorkspaceWidth;
        }
        return scale;
    }

    /**
     * Gets the width of a single Cell Layout, aka a single panel within a Workspace.
     *
     * <p>This is the width of a Workspace, less its horizontal padding. Note that two-panel
     * layouts have two Cell Layouts per workspace.
     */
    public int getCellLayoutWidth() {
        return (mDeviceProperties.getAvailableWidthPx() - getTotalWorkspacePadding().x) / getPanelCount();
    }

    /**
     * Gets the height of a single Cell Layout, aka a single panel within a Workspace.
     *
     * <p>This is the height of a Workspace, less its vertical padding.
     */
    public int getCellLayoutHeight() {
        return mDeviceProperties.getAvailableHeightPx() - getTotalWorkspacePadding().y;
    }

    public Point getTotalWorkspacePadding() {
        return new Point(workspacePadding.left + workspacePadding.right,
                workspacePadding.top + workspacePadding.bottom);
    }

    /**
     * Updates {@link #workspacePadding} as a result of any internal value change to reflect the
     * new workspace padding
     */
    private void updateWorkspacePadding() {
        Rect padding = workspacePadding;
        if (isVerticalBarLayout()) {
            if (mIsResponsiveGrid) {
                padding.top = mResponsiveWorkspaceHeightSpec.getStartPaddingPx();
                padding.bottom = Math.max(0,
                        mResponsiveWorkspaceHeightSpec.getEndPaddingPx() - mInsets.bottom);
                if (isSeascape()) {
                    padding.left =
                            hotseatBarSizePx + mResponsiveWorkspaceWidthSpec.getEndPaddingPx();
                    padding.right = mResponsiveWorkspaceWidthSpec.getStartPaddingPx();
                } else {
                    padding.left = mResponsiveWorkspaceWidthSpec.getStartPaddingPx();
                    padding.right =
                            hotseatBarSizePx + mResponsiveWorkspaceWidthSpec.getEndPaddingPx();
                }
            } else {
                padding.top = 0;
                padding.bottom = edgeMarginPx;
                if (isSeascape()) {
                    padding.left = hotseatBarSizePx;
                    padding.right = getHotseatProfile().getBarEdgePaddingPx();
                } else {
                    padding.left = getHotseatProfile().getBarEdgePaddingPx();
                    padding.right = hotseatBarSizePx;
                }
            }
        } else {
            // Pad the bottom of the workspace with hotseat bar
            // and leave a bit of space in case a widget go all the way down
            int paddingBottom = hotseatBarSizePx + workspaceBottomPadding - mInsets.bottom;
            if (!mIsResponsiveGrid) {
                paddingBottom +=
                        workspacePageIndicatorHeight - mWorkspacePageIndicatorOverlapWorkspace;
            }
            int paddingTop = workspaceTopPadding + (mIsScalableGrid ? 0 : edgeMarginPx);
            int paddingLeft = desiredWorkspaceHorizontalMarginPx;
            int paddingRight = desiredWorkspaceHorizontalMarginPx;

            // In fixed Landscape we don't need padding on the side next to the cutout because
            // the cutout is already adding padding to all of Launcher, we only need on the other
            // side
            if (inv.isFixedLandscape) {
                paddingLeft = isSeascape() ? desiredWorkspaceHorizontalMarginPx : 0;
                paddingRight = isSeascape() ? 0 : desiredWorkspaceHorizontalMarginPx;
            }
            padding.set(paddingLeft, paddingTop, paddingRight, paddingBottom);
        }
        insetPadding(workspacePadding, cellLayoutPaddingPx);
    }

    private void insetPadding(Rect paddings, Rect insets) {
        insets.left = Math.min(insets.left, paddings.left);
        paddings.left -= insets.left;

        insets.top = Math.min(insets.top, paddings.top);
        paddings.top -= insets.top;

        insets.right = Math.min(insets.right, paddings.right);
        paddings.right -= insets.right;

        insets.bottom = Math.min(insets.bottom, paddings.bottom);
        paddings.bottom -= insets.bottom;
    }


    /**
     * Returns the new border space that should be used between hotseat icons after adjusting it to
     * the bubble bar.
     *
     * <p>Does not check for visible bubbles persistence, so caller should call
     * {@link #shouldAdjustHotseatOrQsbForBubbleBar} first.
     *
     * <p>If there's no adjustment needed, this method returns {@code 0}.
     * @see #shouldAdjustHotseatOrQsbForBubbleBar(Context, boolean)
     */
    public float getHotseatAdjustedBorderSpaceForBubbleBar(Context context) {
        if (shouldAlignBubbleBarWithQSB() || !shouldAdjustHotseatOrQsbForBubbleBar(context)) {
            return 0;
        }
        // The adjustment is shrinking the hotseat's width by 1 icon on either side.
        int iconsWidth =
                iconSizePx * numShownHotseatIcons + hotseatBorderSpace * (numShownHotseatIcons - 1);
        int newWidth = iconsWidth - 2 * iconSizePx;
        // Evenly space the icons within the boundaries of the new width.
        return (float) (newWidth - iconSizePx * numShownHotseatIcons) / (numShownHotseatIcons - 1);
    }

    /**
     * Returns the hotseat icon translation X for the cellX index.
     *
     * <p>Does not check for visible bubbles persistence, so caller should call
     * {@link #shouldAdjustHotseatOrQsbForBubbleBar} first.
     *
     * <p>If there's no adjustment needed, this method returns {@code 0}.
     * @see #shouldAdjustHotseatOrQsbForBubbleBar(Context, boolean)
     */
    public float getHotseatAdjustedTranslation(Context context, int cellX) {
        float borderSpace = getHotseatAdjustedBorderSpaceForBubbleBar(context);
        if (borderSpace == 0) return borderSpace;
        float borderSpaceDelta = borderSpace - hotseatBorderSpace;
        return iconSizePx + cellX * borderSpaceDelta;
    }

    /** Returns whether hotseat or QSB should be adjusted for the bubble bar. */
    public boolean shouldAdjustHotseatOrQsbForBubbleBar(Context context, boolean hasBubbles) {
        return hasBubbles && shouldAdjustHotseatOrQsbForBubbleBar(context);
    }

    /** Returns whether hotseat should be adjusted for the bubble bar. */
    public boolean shouldAdjustHotseatForBubbleBar(Context context, boolean hasBubbles) {
        return shouldAlignBubbleBarWithHotseat()
                && shouldAdjustHotseatOrQsbForBubbleBar(context, hasBubbles);
    }

    /** Returns whether hotseat or QSB should be adjusted for the bubble bar. */
    public boolean shouldAdjustHotseatOrQsbForBubbleBar(Context context) {
        // only need to adjust if QSB is on top of the hotseat and there's not enough space for the
        // bubble bar to either side of the hotseat.
        if (isQsbInline) return false;
        Rect hotseatPadding = getHotseatLayoutPadding(context);
        int hotseatMinHorizontalPadding = Math.min(hotseatPadding.left, hotseatPadding.right);
        return hotseatMinHorizontalPadding <= mBubbleBarSpaceThresholdPx;
    }

    /**
     * Returns the padding for hotseat view
     */
    public Rect getHotseatLayoutPadding(Context context) {
        Rect hotseatBarPadding = new Rect();
        if (isVerticalBarLayout()) {
            // The hotseat icons will be placed in the middle of the hotseat cells.
            // Changing the hotseatCellHeightPx is not affecting hotseat icon positions
            // in vertical bar layout.
            int paddingTop = Math.max((int) (mInsets.top + cellLayoutPaddingPx.top), 0);
            int paddingBottom = Math.max((int) (mInsets.bottom + cellLayoutPaddingPx.bottom), 0);

            if (isSeascape()) {
                hotseatBarPadding.set(mInsets.left + getHotseatProfile().getBarEdgePaddingPx(),
                        paddingTop, getHotseatProfile().getBarWorkspaceSpacePx(), paddingBottom);
            } else {
                hotseatBarPadding.set(getHotseatProfile().getBarWorkspaceSpacePx(), paddingTop,
                        mInsets.right + getHotseatProfile().getBarEdgePaddingPx(), paddingBottom);
            }
        } else if (inv.isFixedLandscape) {
            // Center the QSB vertically with hotseat
            int hotseatBarBottomPadding = getHotseatBarBottomPadding();
            int hotseatPlusQSBWidth = getIconToIconWidthForColumns(inv.numColumns);

            // This is needed because of b/235886078 since QSB needs to span to the icon borders
            int iconExtraSpacePx = iconSizePx - getIconVisibleSizePx(iconSizePx);
            int qsbWidth = getAdditionalQsbSpace() + iconExtraSpacePx / 2;

            int availableWidthPxForHotseat = mDeviceProperties.getAvailableWidthPx() - Math.abs(workspacePadding.width())
                    - Math.abs(cellLayoutPaddingPx.width());
            int remainingSpaceOnSide = (availableWidthPxForHotseat - hotseatPlusQSBWidth) / 2;

            hotseatBarPadding.set(
                    remainingSpaceOnSide + mInsets.left + workspacePadding.left
                            + cellLayoutPaddingPx.left,
                    hotseatBarSizePx - hotseatBarBottomPadding - hotseatCellHeightPx,
                    remainingSpaceOnSide + mInsets.right + workspacePadding.right
                            + cellLayoutPaddingPx.right,
                    hotseatBarBottomPadding
            );
            if (Utilities.isRtl(context.getResources())) {
                hotseatBarPadding.right += qsbWidth;
            } else {
                hotseatBarPadding.left += qsbWidth;
            }
        } else if (isTaskbarPresent || isQsbInline) {
            // Center the QSB vertically with hotseat
            int hotseatBarBottomPadding = getHotseatBarBottomPadding();
            int hotseatBarTopPadding =
                    hotseatBarSizePx - hotseatBarBottomPadding - hotseatCellHeightPx;

            int hotseatWidth = getHotseatRequiredWidth();
            int startSpacing;
            int endSpacing;
            // Hotseat aligns to the left with nav buttons
            if (getHotseatProfile().getBarEndOffset() > 0) {
                startSpacing = getHotseatProfile().getInlineNavButtonsEndSpacingPx();
                endSpacing = mDeviceProperties.getAvailableWidthPx() - hotseatWidth - startSpacing + hotseatBorderSpace;
            } else {
                startSpacing = (mDeviceProperties.getAvailableWidthPx() - hotseatWidth) / 2;
                endSpacing = startSpacing;
            }
            startSpacing += getAdditionalQsbSpace();

            hotseatBarPadding.top = hotseatBarTopPadding;
            hotseatBarPadding.bottom = hotseatBarBottomPadding;
            boolean isRtl = Utilities.isRtl(context.getResources());
            if (isRtl) {
                hotseatBarPadding.left = endSpacing;
                hotseatBarPadding.right = startSpacing;
            } else {
                hotseatBarPadding.left = startSpacing;
                hotseatBarPadding.right = endSpacing;
            }

        } else if (mIsScalableGrid) {
            int iconExtraSpacePx = iconSizePx - getIconVisibleSizePx(iconSizePx);
            int sideSpacing = (mDeviceProperties.getAvailableWidthPx() - (hotseatQsbWidth + iconExtraSpacePx)) / 2;
            hotseatBarPadding.set(sideSpacing,
                    0,
                    sideSpacing,
                    getHotseatBarBottomPadding());
        } else {
            // We want the edges of the hotseat to line up with the edges of the workspace, but the
            // icons in the hotseat are a different size, and so don't line up perfectly. To account
            // for this, we pad the left and right of the hotseat with half of the difference of a
            // workspace cell vs a hotseat cell.
            float workspaceCellWidth = (float) mDeviceProperties.getWidthPx() / inv.numColumns;
            float hotseatCellWidth = (float) mDeviceProperties.getWidthPx() / numShownHotseatIcons;
            int hotseatAdjustment = Math.round((workspaceCellWidth - hotseatCellWidth) / 2);
            hotseatBarPadding.set(
                    hotseatAdjustment + workspacePadding.left + cellLayoutPaddingPx.left
                            + mInsets.left,
                    0,
                    hotseatAdjustment + workspacePadding.right + cellLayoutPaddingPx.right
                            + mInsets.right,
                    getHotseatBarBottomPadding());
        }
        return hotseatBarPadding;
    }

    /** The margin between the edge of all apps and the edge of the first icon. */
    public int getAllAppsIconStartMargin(Context context) {
        int allAppsSpacing;
        if (isVerticalBarLayout()) {
            // On phones, the landscape layout uses a different setup.
            allAppsSpacing = workspacePadding.left + workspacePadding.right;
        } else {
            allAppsSpacing =
                    allAppsPadding.left + allAppsPadding.right + allAppsLeftRightMargin * 2;
        }

        int cellWidth = DeviceProfile.calculateCellWidth(
                mDeviceProperties.getAvailableWidthPx() - allAppsSpacing,
                0 /* borderSpace */,
                numShownAllAppsColumns);
        int iconAlignmentMargin = (cellWidth - getIconVisibleSizePx(
                getAllAppsProfile().getIconSizePx())) / 2;

        return (Utilities.isRtl(context.getResources()) ? allAppsPadding.right
                : allAppsPadding.left) + iconAlignmentMargin;
    }

    /**
     * TODO(b/235886078): workaround needed because of this bug
     * Icons are 10% larger on XML than their visual size, so remove that extra space to get
     * some dimensions correct.
     *
     * When this bug is resolved this method will no longer be needed and we would be able to
     * replace all instances where this method is called with iconSizePx.
     */
    private int getIconVisibleSizePx(int iconSizePx) {
        return Math.round(ICON_VISIBLE_AREA_FACTOR * iconSizePx);
    }

    private int getAdditionalQsbSpace() {
        return isQsbInline ? hotseatQsbWidth + hotseatBorderSpace : 0;
    }

    /**
     * Calculate how much space the hotseat needs to be shown completely
     */
    private int getHotseatRequiredWidth() {
        int additionalQsbSpace = getAdditionalQsbSpace();
        return iconSizePx * numShownHotseatIcons
                + hotseatBorderSpace * (numShownHotseatIcons
                    - (getHotseatProfile().getAreNavButtonsInline() ? 0 : 1))
                + additionalQsbSpace;
    }

    /**
     * Returns the number of pixels the QSB is translated from the bottom of the screen.
     */
    public int getQsbOffsetY() {
        if (mDeviceProperties.isPhone() && isQsbInline) {
            return getHotseatBarBottomPadding()
                    - ((getHotseatProfile().getQsbHeight() - hotseatCellHeightPx) / 2);
        } else if (isTaskbarPresent || (mDeviceProperties.isLandscape() && isQsbInline)) { // QSB on top
            return hotseatBarSizePx - getHotseatProfile().getQsbHeight()
                    + getHotseatProfile().getQsbShadowHeight();
        } else {
            return hotseatBarBottomSpacePx - getHotseatProfile().getQsbShadowHeight();
        }
    }

    /**
     * Returns the number of pixels the hotseat is translated from the bottom of the screen.
     */
    private int getHotseatBarBottomPadding() {
        if (isTaskbarPresent || isQsbInline) { // QSB on top or inline
            return hotseatBarBottomSpacePx - (Math.abs(hotseatCellHeightPx - iconSizePx) / 2);
        } else {
            return hotseatBarSizePx - hotseatCellHeightPx;
        }
    }

    /**
     * Returns the number of pixels the hotseat icons or QSB vertical center is translated from the
     * bottom of the screen.
     */
    public int getBubbleBarVerticalCenterForHome() {
        if (shouldAlignBubbleBarWithHotseat()) {
            return hotseatBarSizePx
                    - (isQsbInline ? 0 : getHotseatProfile().getQsbVisualHeight())
                    - hotseatQsbSpace
                    - (hotseatCellHeightPx / 2)
                    + ((hotseatCellHeightPx - iconSizePx) / 2);
        } else {
            return hotseatBarSizePx - (getHotseatProfile().getQsbVisualHeight() / 2);
        }
    }

    /** Returns whether bubble bar should be aligned with the hotseat. */
    public boolean shouldAlignBubbleBarWithQSB() {
        return !shouldAlignBubbleBarWithHotseat();
    }

    /** Returns whether bubble bar should be aligned with the hotseat. */
    public boolean shouldAlignBubbleBarWithHotseat() {
        return isQsbInline || mDeviceProperties.isGestureMode();
    }

    /**
     * Returns the number of pixels the taskbar is translated from the bottom of the screen.
     */
    public int getTaskbarOffsetY() {
        int taskbarIconBottomSpace = (getTaskbarProfile().getHeight() - iconSizePx) / 2;
        int launcherIconBottomSpace =
                Math.min((hotseatCellHeightPx - iconSizePx) / 2, gridVisualizationPaddingY);
        return getHotseatBarBottomPadding() + launcherIconBottomSpace - taskbarIconBottomSpace;
    }

    /** Returns the number of pixels required below OverviewActions. */
    public int getOverviewActionsClaimedSpaceBelow() {
        return isTaskbarPresent
                ? getTaskbarProfile().getTransientTaskbarClaimedSpace()
                : mInsets.bottom;
    }

    /** Gets the space that the overview actions will take, including bottom margin. */
    public int getOverviewActionsClaimedSpace() {
        int overviewActionsSpace = mDeviceProperties.isTablet() && enableGridOnlyOverview()
                ? 0
                : (overviewProfile.getActionsTopMarginPx() + overviewProfile.getActionsHeight());
        return overviewActionsSpace + getOverviewActionsClaimedSpaceBelow();
    }

    /**
     * Takes the View and return the scales of width and height depending on the DeviceProfile
     * specifications
     *
     * @param itemInfo The tag of the widget view
     * @return A PointF instance with the x set to be the scale of width, and y being the scale of
     * height
     */
    @NonNull
    public PointF getAppWidgetScale(@Nullable final ItemInfo itemInfo) {
        return mViewScaleProvider.getScaleFromItemInfo(itemInfo);
    }

    /**
     * @return the bounds for which the open folders should be contained within
     */
    public Rect getAbsoluteOpenFolderBounds() {
        if (isVerticalBarLayout()) {
            // Folders should only appear right of the drop target bar and left of the hotseat
            return new Rect(
                    mInsets.left + getDropTargetProfile().getBarSizePx() + edgeMarginPx,
                    mInsets.top,
                    mInsets.left + mDeviceProperties.getAvailableWidthPx() - hotseatBarSizePx - edgeMarginPx,
                    mInsets.top + mDeviceProperties.getAvailableHeightPx());
        } else {
            // Folders should only appear below the drop target bar and above the hotseat
            int hotseatTop = isTaskbarPresent ? getTaskbarProfile().getHeight() : hotseatBarSizePx;
            return new Rect(mInsets.left + edgeMarginPx,
                    mInsets.top + getDropTargetProfile().getBarSizePx() + edgeMarginPx,
                    mInsets.left + mDeviceProperties.getAvailableWidthPx() - edgeMarginPx,
                    mInsets.top + mDeviceProperties.getAvailableHeightPx() - hotseatTop
                            - workspacePageIndicatorHeight - edgeMarginPx);
        }
    }

    public static int calculateCellWidth(int width, int borderSpacing, int countX) {
        return (width - ((countX - 1) * borderSpacing)) / countX;
    }

    public static int calculateCellHeight(int height, int borderSpacing, int countY) {
        return (height - ((countY - 1) * borderSpacing)) / countY;
    }

    /**
     * When {@code true}, the device is in landscape mode and the hotseat is on the right column.
     * When {@code false}, either device is in portrait mode or the device is in landscape mode and
     * the hotseat is on the bottom row.
     */
    public boolean isVerticalBarLayout() {
        return mDeviceProperties.isLandscape() && mDeviceProperties.getTransposeLayoutWithOrientation();
    }

    public boolean isSeascape() {
        return mDeviceProperties.getRotationHint() == Surface.ROTATION_270
                && (isVerticalBarLayout() || inv.isFixedLandscape);
    }

    public boolean shouldFadeAdjacentWorkspaceScreens() {
        return isVerticalBarLayout();
    }

    public int getCellContentHeight(@ContainerType int containerType) {
        switch (containerType) {
            case CellLayout.WORKSPACE:
                return cellHeightPx;
            case CellLayout.FOLDER:
                return folderCellHeightPx;
            case CellLayout.HOTSEAT:
                // The hotseat is the only container where the cell height is going to be
                // different from the content within that cell.
                return iconSizePx;
            default:
                // ??
                return 0;
        }
    }

    private String pxToDpStr(String name, float value) {
        return "\t" + name + ": " + value + "px (" + dpiFromPx(value, mMetrics.densityDpi) + "dp)";
    }

    private String dpPointFToString(String name, PointF value) {
        return String.format(Locale.ENGLISH, "\t%s: PointF(%.1f, %.1f)dp", name, value.x, value.y);
    }

    /** Dumps various DeviceProfile variables to the specified writer. */
    public void dump(Context context, String prefix, PrintWriter writer) {
        writer.println(prefix + "DeviceProfile:");
        writer.println(prefix + "\t1 dp = " + mMetrics.density + " px");

        writer.println(prefix + "\tisTablet:" + mDeviceProperties.isTablet());
        writer.println(prefix + "\tisPhone:" + mDeviceProperties.isPhone());
        writer.println(prefix + "\ttransposeLayoutWithOrientation:"
                + mDeviceProperties.getTransposeLayoutWithOrientation());
        writer.println(prefix + "\tisGestureMode:" + mDeviceProperties.isGestureMode());

        writer.println(prefix + "\tisLandscape:" + mDeviceProperties.isLandscape());
        writer.println(prefix + "\tisMultiWindowMode:" + mDeviceProperties.isMultiWindowMode());
        writer.println(prefix + "\tisTwoPanels:" + mDeviceProperties.isTwoPanels());
        writer.println(prefix + "\tisLeftRightSplit:" + isLeftRightSplit);

        writer.println(prefix + pxToDpStr("windowX", mDeviceProperties.getWindowX()));
        writer.println(prefix + pxToDpStr("windowY", mDeviceProperties.getWindowY()));
        writer.println(prefix + pxToDpStr("widthPx", mDeviceProperties.getWidthPx()));
        writer.println(prefix + pxToDpStr("heightPx", mDeviceProperties.getHeightPx()));
        writer.println(prefix + pxToDpStr("availableWidthPx", mDeviceProperties.getAvailableWidthPx()));
        writer.println(prefix + pxToDpStr("availableHeightPx", mDeviceProperties.getAvailableHeightPx()));
        writer.println(prefix + pxToDpStr("mInsets.left", mInsets.left));
        writer.println(prefix + pxToDpStr("mInsets.top", mInsets.top));
        writer.println(prefix + pxToDpStr("mInsets.right", mInsets.right));
        writer.println(prefix + pxToDpStr("mInsets.bottom", mInsets.bottom));

        writer.println(prefix + "\taspectRatio:" + mDeviceProperties.getAspectRatio());

        writer.println(prefix + "\tisResponsiveGrid:" + mIsResponsiveGrid);
        writer.println(prefix + "\tisScalableGrid:" + mIsScalableGrid);

        writer.println(prefix + "\tinv.numRows: " + inv.numRows);
        writer.println(prefix + "\tinv.numColumns: " + inv.numColumns);
        writer.println(prefix + "\tinv.numSearchContainerColumns: "
                + inv.numSearchContainerColumns);

        writer.println(prefix + dpPointFToString("minCellSize", inv.minCellSize[mTypeIndex]));

        writer.println(prefix + pxToDpStr("cellWidthPx", cellWidthPx));
        writer.println(prefix + pxToDpStr("cellHeightPx", cellHeightPx));

        writer.println(prefix + pxToDpStr("getCellSize().x", getCellSize().x));
        writer.println(prefix + pxToDpStr("getCellSize().y", getCellSize().y));

        writer.println(prefix + pxToDpStr("cellLayoutBorderSpacePx Horizontal",
                cellLayoutBorderSpacePx.x));
        writer.println(prefix + pxToDpStr("cellLayoutBorderSpacePx Vertical",
                cellLayoutBorderSpacePx.y));
        writer.println(
                prefix + pxToDpStr("cellLayoutPaddingPx.left", cellLayoutPaddingPx.left));
        writer.println(
                prefix + pxToDpStr("cellLayoutPaddingPx.top", cellLayoutPaddingPx.top));
        writer.println(
                prefix + pxToDpStr("cellLayoutPaddingPx.right", cellLayoutPaddingPx.right));
        writer.println(
                prefix + pxToDpStr("cellLayoutPaddingPx.bottom", cellLayoutPaddingPx.bottom));

        writer.println(prefix + pxToDpStr("iconSizePx", iconSizePx));
        writer.println(prefix + pxToDpStr("iconTextSizePx", iconTextSizePx));
        writer.println(prefix + pxToDpStr("iconDrawablePaddingPx", iconDrawablePaddingPx));

        writer.println(prefix + "\tnumFolderRows: " + numFolderRows);
        writer.println(prefix + "\tnumFolderColumns: " + numFolderColumns);
        writer.println(prefix + pxToDpStr("folderCellWidthPx", folderCellWidthPx));
        writer.println(prefix + pxToDpStr("folderCellHeightPx", folderCellHeightPx));
        writer.println(prefix + pxToDpStr("folderChildIconSizePx", folderChildIconSizePx));
        writer.println(prefix + pxToDpStr("folderChildTextSizePx", folderChildTextSizePx));
        writer.println(prefix + pxToDpStr("folderChildDrawablePaddingPx",
                folderChildDrawablePaddingPx));
        writer.println(prefix + pxToDpStr("folderCellLayoutBorderSpacePx.x",
                folderCellLayoutBorderSpacePx.x));
        writer.println(prefix + pxToDpStr("folderCellLayoutBorderSpacePx.y",
                folderCellLayoutBorderSpacePx.y));
        writer.println(prefix + pxToDpStr("folderContentPaddingLeftRight",
                folderContentPaddingLeftRight));
        writer.println(prefix + pxToDpStr("folderTopPadding", folderContentPaddingTop));
        writer.println(prefix + pxToDpStr("folderFooterHeight", folderFooterHeightPx));

        writer.println(prefix + pxToDpStr("bottomSheetTopPadding",
                getBottomSheetProfile().getBottomSheetTopPadding()));
        writer.println(prefix + "\tbottomSheetOpenDuration: "
                + getBottomSheetProfile().getBottomSheetOpenDuration());
        writer.println(prefix + "\tbottomSheetCloseDuration: "
                + getBottomSheetProfile().getBottomSheetCloseDuration());
        writer.println(prefix + "\tbottomSheetWorkspaceScale: "
                + getBottomSheetProfile().getBottomSheetWorkspaceScale());
        writer.println(prefix + "\tbottomSheetDepth: "
                + getBottomSheetProfile().getBottomSheetDepth());

        writer.println(prefix + pxToDpStr("allAppsShiftRange", allAppsShiftRange));
        writer.println(prefix + "\tallAppsOpenDuration: " + allAppsOpenDuration);
        writer.println(prefix + "\tallAppsCloseDuration: " + allAppsCloseDuration);
        writer.println(prefix + pxToDpStr("allAppsIconSizePx",
                getAllAppsProfile().getIconSizePx()));
        writer.println(prefix + pxToDpStr("allAppsIconTextSizePx",
                getAllAppsProfile().getIconTextSizePx()));
        writer.println(prefix + pxToDpStr("allAppsIconDrawablePaddingPx",
                getAllAppsProfile().getIconDrawablePaddingPx()));
        writer.println(prefix + pxToDpStr("allAppsCellHeightPx",
                getAllAppsProfile().getCellHeightPx()));
        writer.println(prefix + pxToDpStr("allAppsCellWidthPx",
                getAllAppsProfile().getCellWidthPx()));
        writer.println(prefix + pxToDpStr("allAppsBorderSpacePxX",
                getAllAppsProfile().getBorderSpacePx().x));
        writer.println(prefix + pxToDpStr("allAppsBorderSpacePxY",
                getAllAppsProfile().getBorderSpacePx().y));
        writer.println(prefix + "\tnumShownAllAppsColumns: " + numShownAllAppsColumns);
        writer.println(prefix + pxToDpStr("allAppsPadding.top", allAppsPadding.top));
        writer.println(prefix + pxToDpStr("allAppsPadding.left", allAppsPadding.left));
        writer.println(prefix + pxToDpStr("allAppsPadding.right", allAppsPadding.right));
        writer.println(prefix + pxToDpStr("allAppsLeftRightMargin", allAppsLeftRightMargin));

        writer.println(prefix + pxToDpStr("hotseatBarSizePx", hotseatBarSizePx));
        writer.println(prefix + "\tmHotseatColumnSpan: " + mHotseatColumnSpan);
        writer.println(prefix + pxToDpStr("mHotseatWidthPx", mHotseatWidthPx));
        writer.println(prefix + pxToDpStr("hotseatCellHeightPx", hotseatCellHeightPx));
        writer.println(prefix + pxToDpStr("hotseatBarBottomSpacePx", hotseatBarBottomSpacePx));
        writer.println(prefix + pxToDpStr("mHotseatBarEdgePaddingPx",
                getHotseatProfile().getBarEdgePaddingPx()));
        writer.println(prefix + pxToDpStr("mHotseatBarWorkspaceSpacePx",
                getHotseatProfile().getBarWorkspaceSpacePx()));
        writer.println(prefix + pxToDpStr("inlineNavButtonsEndSpacingPx",
                getHotseatProfile().getInlineNavButtonsEndSpacingPx()));
        writer.println(prefix + pxToDpStr("navButtonsLayoutWidthPx",
                getHotseatProfile().getNavButtonsLayoutWidthPx()));
        writer.println(prefix + pxToDpStr("hotseatBarEndOffset",
                getHotseatProfile().getBarEndOffset()));
        writer.println(prefix + pxToDpStr("hotseatQsbSpace", hotseatQsbSpace));
        writer.println(prefix + pxToDpStr("hotseatQsbHeight", getHotseatProfile().getQsbHeight()));
        writer.println(prefix + pxToDpStr("springLoadedHotseatBarTopMarginPx",
                getHotseatProfile().getSpringLoadedBarTopMarginPx()));
        Rect hotseatLayoutPadding = getHotseatLayoutPadding(context);
        writer.println(prefix + pxToDpStr("getHotseatLayoutPadding(context).top",
                hotseatLayoutPadding.top));
        writer.println(prefix + pxToDpStr("getHotseatLayoutPadding(context).bottom",
                hotseatLayoutPadding.bottom));
        writer.println(prefix + pxToDpStr("getHotseatLayoutPadding(context).left",
                hotseatLayoutPadding.left));
        writer.println(prefix + pxToDpStr("getHotseatLayoutPadding(context).right",
                hotseatLayoutPadding.right));
        writer.println(prefix + "\tnumShownHotseatIcons: " + numShownHotseatIcons);
        writer.println(prefix + pxToDpStr("hotseatBorderSpace", hotseatBorderSpace));
        writer.println(prefix + "\tisQsbInline: " + isQsbInline);
        writer.println(prefix + pxToDpStr("hotseatQsbWidth", hotseatQsbWidth));

        writer.println(prefix + "\tisTaskbarPresent:" + isTaskbarPresent);
        writer.println(prefix + "\tisTaskbarPresentInApps:" + isTaskbarPresentInApps);
        writer.println(prefix + pxToDpStr("taskbarHeight", getTaskbarProfile().getHeight()));
        writer.println(prefix + pxToDpStr("stashedTaskbarHeight",
                getTaskbarProfile().getStashedTaskbarHeight()));
        writer.println(prefix + pxToDpStr("taskbarBottomMargin",
                getTaskbarProfile().getBottomMargin()));
        writer.println(prefix + pxToDpStr("taskbarIconSize", getTaskbarProfile().getIconSize()));

        writer.println(prefix + pxToDpStr("desiredWorkspaceHorizontalMarginPx",
                desiredWorkspaceHorizontalMarginPx));
        writer.println(prefix + pxToDpStr("workspacePadding.left", workspacePadding.left));
        writer.println(prefix + pxToDpStr("workspacePadding.top", workspacePadding.top));
        writer.println(prefix + pxToDpStr("workspacePadding.right", workspacePadding.right));
        writer.println(prefix + pxToDpStr("workspacePadding.bottom", workspacePadding.bottom));

        writer.println(prefix + pxToDpStr("iconScale", iconScale));
        writer.println(prefix + pxToDpStr("cellScaleToFit ", cellScaleToFit));
        writer.println(prefix + pxToDpStr("extraSpace", extraSpace));
        writer.println(prefix + pxToDpStr("unscaled extraSpace", extraSpace / iconScale));

        writer.println(prefix + pxToDpStr("maxEmptySpace", maxEmptySpace));
        writer.println(prefix + pxToDpStr("workspaceTopPadding", workspaceTopPadding));
        writer.println(prefix + pxToDpStr("workspaceBottomPadding", workspaceBottomPadding));

        writer.println(prefix + pxToDpStr("overviewTaskMarginPx",
                getOverviewProfile().getTaskMarginPx()));
        writer.println(prefix + pxToDpStr("overviewTaskIconSizePx",
                getOverviewProfile().getTaskIconSizePx()));
        writer.println(prefix + pxToDpStr("overviewTaskIconDrawableSizePx",
                getOverviewProfile().getTaskIconDrawableSizePx()));
        writer.println(prefix + pxToDpStr("overviewTaskIconDrawableSizeGridPx",
                getOverviewProfile().getTaskIconDrawableSizeGridPx()));
        writer.println(prefix + pxToDpStr("overviewTaskThumbnailTopMarginPx",
                getOverviewProfile().getTaskThumbnailTopMarginPx()));
        writer.println(prefix + pxToDpStr("overviewActionsTopMarginPx",
                getOverviewProfile().getActionsTopMarginPx()));
        writer.println(prefix + pxToDpStr("overviewActionsHeight",
                getOverviewProfile().getActionsHeight()));
        writer.println(prefix + pxToDpStr("overviewActionsClaimedSpaceBelow",
                getOverviewActionsClaimedSpaceBelow()));
        writer.println(prefix + pxToDpStr("overviewPageSpacing",
                getOverviewProfile().getPageSpacing()));
        writer.println(prefix + pxToDpStr("overviewRowSpacing",
                getOverviewProfile().getRowSpacing()));
        writer.println(prefix + pxToDpStr("overviewGridSideMargin",
                getOverviewProfile().getGridSideMargin()));

        writer.println(prefix + pxToDpStr("dropTargetBarTopMarginPx",
                getDropTargetProfile().getBarTopMarginPx()));
        writer.println(prefix + pxToDpStr("dropTargetBarSizePx",
                getDropTargetProfile().getBarSizePx()));
        writer.println(
                prefix + pxToDpStr("dropTargetBarBottomMarginPx",
                        getDropTargetProfile().getBarBottomMarginPx()));

        writer.println(prefix + pxToDpStr("getCellLayoutSpringLoadShrunkTop()",
                getCellLayoutSpringLoadShrunkTop()));
        writer.println(prefix + pxToDpStr("getCellLayoutSpringLoadShrunkBottom()",
                getCellLayoutSpringLoadShrunkBottom(context)));
        writer.println(prefix + pxToDpStr("workspaceSpringLoadedMinNextPageVisiblePx",
                workspaceSpringLoadedMinNextPageVisiblePx));
        writer.println(prefix + pxToDpStr("getWorkspaceSpringLoadScale()",
                getWorkspaceSpringLoadScale(context)));
        writer.println(prefix + pxToDpStr("getCellLayoutHeight()", getCellLayoutHeight()));
        writer.println(prefix + pxToDpStr("getCellLayoutWidth()", getCellLayoutWidth()));
        if (mIsResponsiveGrid) {
            writer.println(prefix + "\tmResponsiveWorkspaceHeightSpec:"
                    + mResponsiveWorkspaceHeightSpec.toString());
            writer.println(prefix + "\tmResponsiveWorkspaceWidthSpec:"
                    + mResponsiveWorkspaceWidthSpec.toString());
            writer.println(prefix + "\tmResponsiveAllAppsHeightSpec:"
                    + mResponsiveAllAppsHeightSpec.toString());
            writer.println(prefix + "\tmResponsiveAllAppsWidthSpec:"
                    + mResponsiveAllAppsWidthSpec.toString());
            writer.println(prefix + "\tmResponsiveFolderHeightSpec:" + mResponsiveFolderHeightSpec);
            writer.println(prefix + "\tmResponsiveFolderWidthSpec:" + mResponsiveFolderWidthSpec);
            writer.println(prefix + "\tmResponsiveHotseatSpec:" + mResponsiveHotseatSpec);
            writer.println(prefix + "\tmResponsiveWorkspaceCellSpec:"
                    + mResponsiveWorkspaceCellSpec);
            writer.println(prefix + "\tmResponsiveAllAppsCellSpec:" + mResponsiveAllAppsCellSpec);
        }
    }

    /** Returns a reduced representation of this DeviceProfile. */
    public String toSmallString() {
        return "isTablet:" + mDeviceProperties.isTablet() + ", "
                + "mDeviceProperties.isMultiDisplay():" + mDeviceProperties.isMultiDisplay() + ", "
                + "widthPx:" + mDeviceProperties.getWidthPx() + ", "
                + "heightPx:" + mDeviceProperties.getHeightPx() + ", "
                + "insets:" + mInsets + ", "
                + "rotationHint:" + mDeviceProperties.getRotationHint();
    }

    private static Context getContext(Context c, Info info, int orientation, WindowBounds bounds) {
        Configuration config = new Configuration(c.getResources().getConfiguration());
        config.orientation = orientation;
        config.densityDpi = info.getDensityDpi();
        config.smallestScreenWidthDp = (int) info.smallestSizeDp(bounds);
        return c.createConfigurationContext(config);
    }

    /**
     * Returns whether Taskbar and Hotseat should adjust horizontally on bubble bar location update.
     */
    public boolean shouldAdjustHotseatOnNavBarLocationUpdate(Context context) {
        return enableBubbleBar()
                && !DisplayController.getNavigationMode(context).hasGestures;
    }

    /** Returns hotseat translation X for the bubble bar position. */
    public int getHotseatTranslationXForNavBar(Context context, boolean isBubblesOnLeft) {
        if (shouldAdjustHotseatOnNavBarLocationUpdate(context)) {
            boolean isRtl = Utilities.isRtl(context.getResources());
            if (isBubblesOnLeft) {
                return isRtl ? -getHotseatProfile().getNavButtonsLayoutWidthPx() : 0;
            } else {
                return isRtl ? 0 : getHotseatProfile().getNavButtonsLayoutWidthPx();
            }
        } else {
            return 0;
        }
    }

    public TaskbarProfile getTaskbarProfile() {
        return mTaskbarProfile;
    }

    public DropTargetProfile getDropTargetProfile() {
        return mDropTargetProfile;
    }

    public BottomSheetProfile getBottomSheetProfile() {
        return mBottomSheetProfile;
    }

    public AllAppsProfile getAllAppsProfile() {
        return mAllAppsProfile;
    }

    public void setAllAppsProfile(AllAppsProfile allAppsProfile) {
        mAllAppsProfile = allAppsProfile;
    }

    /**
     * Callback when a component changes the DeviceProfile associated with it, as a result of
     * configuration change
     */
    public interface OnDeviceProfileChangeListener {

        /**
         * Called when the device profile is reassigned. Note that for layout and measurements, it
         * is sufficient to listen for inset changes. Use this callback when you need to perform
         * a one time operation.
         */
        void onDeviceProfileChanged(DeviceProfile dp);
    }

    /**
     * Handler that deals with ItemInfo of the views for the DeviceProfile
     */
    @FunctionalInterface
    public interface ViewScaleProvider {
        /**
         * Get the scales from the view
         *
         * @param itemInfo The tag of the widget view
         * @return PointF instance containing the scale information, or null if using the default
         * app widget scale of this device profile.
         */
        @NonNull
        PointF getScaleFromItemInfo(@Nullable ItemInfo itemInfo);
    }

    public static class Builder {
        private final Context mContext;
        private final InvariantDeviceProfile mInv;
        private final Info mInfo;
        private final WindowManagerProxy mWMProxy;
        private final ThemeManager mThemeManager;

        private WindowBounds mWindowBounds;
        private boolean mIsMultiDisplay;

        private boolean mIsMultiWindowMode = false;
        private Boolean mTransposeLayoutWithOrientation;
        private Boolean mIsGestureMode;
        private ViewScaleProvider mViewScaleProvider = null;

        private SparseArray<DotRenderer> mDotRendererCache;

        private Consumer<DeviceProfile> mOverrideProvider;

        private boolean mIsTransientTaskbar;
        private DisplayOptionSpec mDisplayOptionSpec;

        public Builder(Context context, InvariantDeviceProfile inv, Info info,
                WindowManagerProxy wmProxy, ThemeManager themeManager) {
            mContext = context;
            mInv = inv;
            mInfo = info;
            mWMProxy = wmProxy;
            mThemeManager = themeManager;
            mIsTransientTaskbar = info.isTransientTaskbar();
        }

        public Builder setMultiWindowMode(boolean isMultiWindowMode) {
            mIsMultiWindowMode = isMultiWindowMode;
            return this;
        }

        public Builder setIsMultiDisplay(boolean isMultiDisplay) {
            mIsMultiDisplay = isMultiDisplay;
            return this;
        }

        public Builder setDotRendererCache(SparseArray<DotRenderer> dotRendererCache) {
            mDotRendererCache = dotRendererCache;
            return this;
        }

        public Builder setWindowBounds(WindowBounds bounds) {
            mWindowBounds = bounds;
            return this;
        }

        public Builder setTransposeLayoutWithOrientation(boolean transposeLayoutWithOrientation) {
            mTransposeLayoutWithOrientation = transposeLayoutWithOrientation;
            return this;
        }

        public Builder setGestureMode(boolean isGestureMode) {
            mIsGestureMode = isGestureMode;
            return this;
        }

        public Builder withDimensionsOverride(Consumer<DeviceProfile> overrideProvider) {
            mOverrideProvider = overrideProvider;
            return this;
        }

        /**
         * Set the viewScaleProvider for the builder
         *
         * @param viewScaleProvider The viewScaleProvider to be set for the
         *                          DeviceProfile
         * @return This builder
         */
        @NonNull
        public Builder setViewScaleProvider(@Nullable ViewScaleProvider viewScaleProvider) {
            mViewScaleProvider = viewScaleProvider;
            return this;
        }

        /**
         * Set the isTransientTaskbar for the builder
         * @return This Builder
         */
        public Builder setIsTransientTaskbar(boolean isTransientTaskbar) {
            mIsTransientTaskbar = isTransientTaskbar;
            return this;
        }

        /**
         * Set the displayOptionSpec for the builder for secondary displays
         * @return This Builder
         */
        public Builder setSecondaryDisplayOptionSpec() {
            mDisplayOptionSpec = createDisplayOptionSpec(mContext, mInfo,
                    mWindowBounds.isLandscape());
            return this;
        }

        private Builder setDisplayOptionSpec(DisplayOptionSpec displayOptionSpec) {
            mDisplayOptionSpec = displayOptionSpec;
            return this;
        }

        public DeviceProfile build() {
            if (mWindowBounds == null) {
                throw new IllegalArgumentException("Window bounds not set");
            }
            if (mTransposeLayoutWithOrientation == null) {
                mTransposeLayoutWithOrientation =
                        !(mInfo.isTablet(mWindowBounds) || mInv.isFixedLandscape);
            }
            if (mIsGestureMode == null) {
                mIsGestureMode = mInfo.getNavigationMode().hasGestures;
            }
            if (mDotRendererCache == null) {
                mDotRendererCache = new SparseArray<>();
            }
            if (mViewScaleProvider == null) {
                mViewScaleProvider = DEFAULT_PROVIDER;
            }
            if (mOverrideProvider == null) {
                mOverrideProvider = DEFAULT_DIMENSION_PROVIDER;
            }
            if (mDisplayOptionSpec == null) {
                mDisplayOptionSpec = createDefaultDisplayOptionSpec(mInfo, mWindowBounds,
                        mIsMultiDisplay, mInv);
            }
            return new DeviceProfile(mContext, mInv, mInfo, mWMProxy, mThemeManager,
                    mWindowBounds, mDotRendererCache,
                    mIsMultiWindowMode, mTransposeLayoutWithOrientation, mIsMultiDisplay,
                    mIsGestureMode, mViewScaleProvider, mOverrideProvider, mIsTransientTaskbar,
                    mDisplayOptionSpec);
        }

        @VisibleForTesting
        static DisplayOptionSpec createDefaultDisplayOptionSpec(DisplayController.Info info,
                WindowBounds windowBounds, boolean isMultiDisplay, InvariantDeviceProfile inv) {
            boolean isTwoPanels = info.isTablet(windowBounds) && isMultiDisplay;
            boolean isLandscape = windowBounds.isLandscape();
            return new DisplayOptionSpec(inv, isTwoPanels, isLandscape);
        }
    }
}
