/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.launcher3.Utilities.dpiFromPx;
import static com.android.launcher3.Utilities.getPointString;
import static com.android.launcher3.config.FeatureFlags.ENABLE_TWO_PANEL_HOME;
import static com.android.launcher3.util.DisplayController.CHANGE_DENSITY;
import static com.android.launcher3.util.DisplayController.CHANGE_SUPPORTED_BOUNDS;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.annotation.TargetApi;
import android.appwidget.AppWidgetHostView;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.Point;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.util.Xml;
import android.view.Display;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.testing.TestProtocol;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.DisplayController.Info;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.WindowBounds;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InvariantDeviceProfile {

    public static final String TAG = "IDP";
    // We do not need any synchronization for this variable as its only written on UI thread.
    public static final MainThreadInitializedObject<InvariantDeviceProfile> INSTANCE =
            new MainThreadInitializedObject<>(InvariantDeviceProfile::new);

    public static final String KEY_MIGRATION_SRC_WORKSPACE_SIZE = "migration_src_workspace_size";
    public static final String KEY_MIGRATION_SRC_HOTSEAT_COUNT = "migration_src_hotseat_count";

    private static final int DEFAULT_TRUE = -1;
    private static final int DEFAULT_SPLIT_DISPLAY = 2;

    private static final String KEY_IDP_GRID_NAME = "idp_grid_name";

    private static final float ICON_SIZE_DEFINED_IN_APP_DP = 48;

    // Constants that affects the interpolation curve between statically defined device profile
    // buckets.
    private static final float KNEARESTNEIGHBOR = 3;
    private static final float WEIGHT_POWER = 5;

    // used to offset float not being able to express extremely small weights in extreme cases.
    private static final float WEIGHT_EFFICIENT = 100000f;

    /**
     * Number of icons per row and column in the workspace.
     */
    public int numRows;
    public int numColumns;

    /**
     * Number of icons per row and column in the folder.
     */
    public int numFolderRows;
    public int numFolderColumns;
    public float iconSize;
    public float landscapeIconSize;
    public float landscapeIconTextSize;
    public int iconBitmapSize;
    public int fillResIconDpi;
    public float iconTextSize;
    public float allAppsIconSize;
    public float allAppsIconTextSize;

    public float minCellHeight;
    public float minCellWidth;
    public float borderSpacing;

    private SparseArray<TypedValue> mExtraAttrs;

    /**
     * Number of icons inside the hotseat area.
     */
    protected int numShownHotseatIcons;

    /**
     * Number of icons inside the hotseat area that is stored in the database. This is greater than
     * or equal to numnShownHotseatIcons, allowing for a seamless transition between two hotseat
     * sizes that share the same DB.
     */
    public int numDatabaseHotseatIcons;

    /**
     * Number of columns in the all apps list.
     */
    public int numAllAppsColumns;
    public int numDatabaseAllAppsColumns;

    /**
     * Do not query directly. see {@link DeviceProfile#isScalableGrid}.
     */
    protected boolean isScalable;
    public int devicePaddingId;

    public String dbFile;
    public int defaultLayoutId;
    int demoModeLayoutId;

    /**
     * An immutable list of supported profiles.
     */
    public List<DeviceProfile> supportedProfiles = Collections.EMPTY_LIST;

    @Nullable public DevicePaddings devicePaddings;

    public Point defaultWallpaperSize;
    public Rect defaultWidgetPadding;

    private final ArrayList<OnIDPChangeListener> mChangeListeners = new ArrayList<>();

    @VisibleForTesting
    public InvariantDeviceProfile() {}

    private InvariantDeviceProfile(InvariantDeviceProfile p) {
        numRows = p.numRows;
        numColumns = p.numColumns;
        numFolderRows = p.numFolderRows;
        numFolderColumns = p.numFolderColumns;
        iconSize = p.iconSize;
        landscapeIconSize = p.landscapeIconSize;
        iconBitmapSize = p.iconBitmapSize;
        iconTextSize = p.iconTextSize;
        landscapeIconTextSize = p.landscapeIconTextSize;
        numShownHotseatIcons = p.numShownHotseatIcons;
        numDatabaseHotseatIcons = p.numDatabaseHotseatIcons;
        numAllAppsColumns = p.numAllAppsColumns;
        numDatabaseAllAppsColumns = p.numDatabaseAllAppsColumns;
        isScalable = p.isScalable;
        devicePaddingId = p.devicePaddingId;
        minCellHeight = p.minCellHeight;
        minCellWidth = p.minCellWidth;
        borderSpacing = p.borderSpacing;
        dbFile = p.dbFile;
        allAppsIconSize = p.allAppsIconSize;
        allAppsIconTextSize = p.allAppsIconTextSize;
        defaultLayoutId = p.defaultLayoutId;
        demoModeLayoutId = p.demoModeLayoutId;
        mExtraAttrs = p.mExtraAttrs;
        devicePaddings = p.devicePaddings;
    }

    @TargetApi(23)
    private InvariantDeviceProfile(Context context) {
        String gridName = getCurrentGridName(context);
        String newGridName = initGrid(context, gridName);
        if (!newGridName.equals(gridName)) {
            Utilities.getPrefs(context).edit().putString(KEY_IDP_GRID_NAME, newGridName).apply();
        }
        Utilities.getPrefs(context).edit()
                .putInt(KEY_MIGRATION_SRC_HOTSEAT_COUNT, numDatabaseHotseatIcons)
                .putString(KEY_MIGRATION_SRC_WORKSPACE_SIZE, getPointString(numColumns, numRows))
                .apply();

        DisplayController.INSTANCE.get(context).addChangeListener(
                (displayContext, info, flags) -> {
                    if ((flags & (CHANGE_DENSITY | CHANGE_SUPPORTED_BOUNDS)) != 0) {
                        onConfigChanged(displayContext);
                    }
                });
    }

    /**
     * This constructor should NOT have any monitors by design.
     */
    public InvariantDeviceProfile(Context context, String gridName) {
        String newName = initGrid(context, gridName);
        if (newName == null || !newName.equals(gridName)) {
            throw new IllegalArgumentException("Unknown grid name");
        }
    }

    /**
     * This constructor should NOT have any monitors by design.
     */
    public InvariantDeviceProfile(Context context, Display display) {
        // Ensure that the main device profile is initialized
        INSTANCE.get(context);
        String gridName = getCurrentGridName(context);

        // Get the display info based on default display and interpolate it to existing display
        DisplayOption defaultDisplayOption = invDistWeightedInterpolate(
                DisplayController.INSTANCE.get(context).getInfo(),
                getPredefinedDeviceProfiles(context, gridName, false), false);

        Info myInfo = new Info(context, display);
        DisplayOption myDisplayOption = invDistWeightedInterpolate(
                myInfo, getPredefinedDeviceProfiles(context, gridName, false), false);

        DisplayOption result = new DisplayOption(defaultDisplayOption.grid)
                .add(myDisplayOption);
        result.iconSize = defaultDisplayOption.iconSize;
        result.landscapeIconSize = defaultDisplayOption.landscapeIconSize;
        if (defaultDisplayOption.allAppsIconSize < myDisplayOption.allAppsIconSize) {
            result.allAppsIconSize = defaultDisplayOption.allAppsIconSize;
        } else {
            result.allAppsIconSize = myDisplayOption.allAppsIconSize;
        }
        result.minCellHeight = defaultDisplayOption.minCellHeight;
        result.minCellWidth = defaultDisplayOption.minCellWidth;
        result.borderSpacing = defaultDisplayOption.borderSpacing;

        initGrid(context, myInfo, result, false);
    }

    public static String getCurrentGridName(Context context) {
        return Utilities.isGridOptionsEnabled(context)
                ? Utilities.getPrefs(context).getString(KEY_IDP_GRID_NAME, null) : null;
    }

    private String initGrid(Context context, String gridName) {
        Info displayInfo = DisplayController.INSTANCE.get(context).getInfo();
        // Determine if we have split display

        boolean isTablet = false, isPhone = false;
        for (WindowBounds bounds : displayInfo.supportedBounds) {
            if (displayInfo.isTablet(bounds)) {
                isTablet = true;
            } else {
                isPhone = true;
            }
        }
        boolean isSplitDisplay = isPhone && isTablet && ENABLE_TWO_PANEL_HOME.get();

        ArrayList<DisplayOption> allOptions =
                getPredefinedDeviceProfiles(context, gridName, isSplitDisplay);
        DisplayOption displayOption =
                invDistWeightedInterpolate(displayInfo, allOptions, isSplitDisplay);
        initGrid(context, displayInfo, displayOption, isSplitDisplay);
        return displayOption.grid.name;
    }

    private void initGrid(
            Context context, Info displayInfo, DisplayOption displayOption,
            boolean isSplitDisplay) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        GridOption closestProfile = displayOption.grid;
        numRows = closestProfile.numRows;
        numColumns = closestProfile.numColumns;
        dbFile = closestProfile.dbFile;
        defaultLayoutId = closestProfile.defaultLayoutId;
        demoModeLayoutId = closestProfile.demoModeLayoutId;
        numFolderRows = closestProfile.numFolderRows;
        numFolderColumns = closestProfile.numFolderColumns;
        isScalable = closestProfile.isScalable;
        devicePaddingId = closestProfile.devicePaddingId;

        mExtraAttrs = closestProfile.extraAttrs;

        iconSize = displayOption.iconSize;
        landscapeIconSize = displayOption.landscapeIconSize;
        iconBitmapSize = ResourceUtils.pxFromDp(iconSize, metrics);
        iconTextSize = displayOption.iconTextSize;
        landscapeIconTextSize = displayOption.landscapeIconTextSize;
        fillResIconDpi = getLauncherIconDensity(iconBitmapSize);

        minCellHeight = displayOption.minCellHeight;
        minCellWidth = displayOption.minCellWidth;
        borderSpacing = displayOption.borderSpacing;

        numShownHotseatIcons = closestProfile.numHotseatIcons;
        numDatabaseHotseatIcons = isSplitDisplay
                ? closestProfile.numDatabaseHotseatIcons : closestProfile.numHotseatIcons;

        numAllAppsColumns = closestProfile.numAllAppsColumns;
        numDatabaseAllAppsColumns = isSplitDisplay
                ? closestProfile.numDatabaseAllAppsColumns : closestProfile.numAllAppsColumns;

        if (Utilities.isGridOptionsEnabled(context)) {
            allAppsIconSize = displayOption.allAppsIconSize;
            allAppsIconTextSize = displayOption.allAppsIconTextSize;
        } else {
            allAppsIconSize = iconSize;
            allAppsIconTextSize = iconTextSize;
        }

        if (devicePaddingId != 0) {
            devicePaddings = new DevicePaddings(context, devicePaddingId);
        }

        // If the partner customization apk contains any grid overrides, apply them
        // Supported overrides: numRows, numColumns, iconSize
        applyPartnerDeviceProfileOverrides(context, metrics);

        final List<DeviceProfile> localSupportedProfiles = new ArrayList<>();
        defaultWallpaperSize = new Point(displayInfo.currentSize);
        for (WindowBounds bounds : displayInfo.supportedBounds) {
            localSupportedProfiles.add(new DeviceProfile.Builder(context, this, displayInfo)
                    .setUseTwoPanels(isSplitDisplay)
                    .setWindowBounds(bounds).build());

            // Wallpaper size should be the maximum of the all possible sizes Launcher expects
            int displayWidth = bounds.bounds.width();
            int displayHeight = bounds.bounds.height();
            defaultWallpaperSize.y = Math.max(defaultWallpaperSize.y, displayHeight);

            // We need to ensure that there is enough extra space in the wallpaper
            // for the intended parallax effects
            float parallaxFactor =
                    dpiFromPx(Math.min(displayWidth, displayHeight), displayInfo.densityDpi) < 720
                            ? 2
                            : wallpaperTravelToScreenWidthRatio(displayWidth, displayHeight);
            defaultWallpaperSize.x =
                    Math.max(defaultWallpaperSize.x, Math.round(parallaxFactor * displayWidth));
        }
        supportedProfiles = Collections.unmodifiableList(localSupportedProfiles);

        ComponentName cn = new ComponentName(context.getPackageName(), getClass().getName());
        defaultWidgetPadding = AppWidgetHostView.getDefaultPaddingForWidget(context, cn, null);
    }

    @Nullable
    public TypedValue getAttrValue(int attr) {
        return mExtraAttrs == null ? null : mExtraAttrs.get(attr);
    }

    public void addOnChangeListener(OnIDPChangeListener listener) {
        mChangeListeners.add(listener);
    }

    public void removeOnChangeListener(OnIDPChangeListener listener) {
        mChangeListeners.remove(listener);
    }


    public void setCurrentGrid(Context context, String gridName) {
        Context appContext = context.getApplicationContext();
        Utilities.getPrefs(appContext).edit().putString(KEY_IDP_GRID_NAME, gridName).apply();
        MAIN_EXECUTOR.execute(() -> onConfigChanged(appContext));
    }

    private void onConfigChanged(Context context) {
        // Re-init grid
        String gridName = getCurrentGridName(context);
        initGrid(context, gridName);

        for (OnIDPChangeListener listener : mChangeListeners) {
            listener.onIdpChanged(this);
        }
    }

    private static ArrayList<DisplayOption> getPredefinedDeviceProfiles(
            Context context, String gridName, boolean isSplitDisplay) {
        ArrayList<DisplayOption> profiles = new ArrayList<>();
        try (XmlResourceParser parser = context.getResources().getXml(R.xml.device_profiles)) {
            final int depth = parser.getDepth();
            int type;
            while (((type = parser.next()) != XmlPullParser.END_TAG ||
                    parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {
                if ((type == XmlPullParser.START_TAG)
                        && GridOption.TAG_NAME.equals(parser.getName())) {

                    GridOption gridOption = new GridOption(context, Xml.asAttributeSet(parser));
                    final int displayDepth = parser.getDepth();
                    while (((type = parser.next()) != XmlPullParser.END_TAG ||
                            parser.getDepth() > displayDepth)
                            && type != XmlPullParser.END_DOCUMENT) {
                        if ((type == XmlPullParser.START_TAG) && "display-option".equals(
                                parser.getName())) {
                            profiles.add(new DisplayOption(gridOption, context,
                                    Xml.asAttributeSet(parser),
                                    isSplitDisplay ? DEFAULT_SPLIT_DISPLAY : DEFAULT_TRUE));
                        }
                    }
                }
            }
        } catch (IOException|XmlPullParserException e) {
            throw new RuntimeException(e);
        }

        ArrayList<DisplayOption> filteredProfiles = new ArrayList<>();
        if (!TextUtils.isEmpty(gridName)) {
            for (DisplayOption option : profiles) {
                if (gridName.equals(option.grid.name)) {
                    filteredProfiles.add(option);
                }
            }
        }
        if (filteredProfiles.isEmpty()) {
            // No grid found, use the default options
            for (DisplayOption option : profiles) {
                if (option.canBeDefault) {
                    filteredProfiles.add(option);
                }
            }
        }
        if (filteredProfiles.isEmpty()) {
            throw new RuntimeException("No display option with canBeDefault=true");
        }
        return filteredProfiles;
    }

    private int getLauncherIconDensity(int requiredSize) {
        // Densities typically defined by an app.
        int[] densityBuckets = new int[] {
                DisplayMetrics.DENSITY_LOW,
                DisplayMetrics.DENSITY_MEDIUM,
                DisplayMetrics.DENSITY_TV,
                DisplayMetrics.DENSITY_HIGH,
                DisplayMetrics.DENSITY_XHIGH,
                DisplayMetrics.DENSITY_XXHIGH,
                DisplayMetrics.DENSITY_XXXHIGH
        };

        int density = DisplayMetrics.DENSITY_XXXHIGH;
        for (int i = densityBuckets.length - 1; i >= 0; i--) {
            float expectedSize = ICON_SIZE_DEFINED_IN_APP_DP * densityBuckets[i]
                    / DisplayMetrics.DENSITY_DEFAULT;
            if (expectedSize >= requiredSize) {
                density = densityBuckets[i];
            }
        }

        return density;
    }

    /**
     * Apply any Partner customization grid overrides.
     *
     * Currently we support: all apps row / column count.
     */
    private void applyPartnerDeviceProfileOverrides(Context context, DisplayMetrics dm) {
        Partner p = Partner.get(context.getPackageManager());
        if (p != null) {
            p.applyInvariantDeviceProfileOverrides(this, dm);
        }
    }

    private static float dist(float x0, float y0, float x1, float y1) {
        return (float) Math.hypot(x1 - x0, y1 - y0);
    }

    private static DisplayOption invDistWeightedInterpolate(
            Info displayInfo, ArrayList<DisplayOption> points, boolean isSplitDisplay) {
        int minWidthPx = Integer.MAX_VALUE;
        int minHeightPx = Integer.MAX_VALUE;
        for (WindowBounds bounds : displayInfo.supportedBounds) {
            boolean isTablet = displayInfo.isTablet(bounds);
            if (isTablet && isSplitDisplay) {
                // For split displays, take half width per page
                minWidthPx = Math.min(minWidthPx, bounds.availableSize.x / 2);
                minHeightPx = Math.min(minHeightPx, bounds.availableSize.y);

            } else if (!isTablet && bounds.isLandscape()) {
                // We will use transposed layout in this case
                minWidthPx = Math.min(minWidthPx, bounds.availableSize.y);
                minHeightPx = Math.min(minHeightPx, bounds.availableSize.x);
            } else {
                minWidthPx = Math.min(minWidthPx, bounds.availableSize.x);
                minHeightPx = Math.min(minHeightPx, bounds.availableSize.y);
            }
        }

        float width = dpiFromPx(minWidthPx, displayInfo.densityDpi);
        float height = dpiFromPx(minHeightPx, displayInfo.densityDpi);

        // Sort the profiles based on the closeness to the device size
        Collections.sort(points, (a, b) ->
                Float.compare(dist(width, height, a.minWidthDps, a.minHeightDps),
                        dist(width, height, b.minWidthDps, b.minHeightDps)));

        GridOption closestOption = points.get(0).grid;
        float weights = 0;

        DisplayOption p = points.get(0);
        if (dist(width, height, p.minWidthDps, p.minHeightDps) == 0) {
            return p;
        }

        DisplayOption out = new DisplayOption(closestOption);
        for (int i = 0; i < points.size() && i < KNEARESTNEIGHBOR; ++i) {
            p = points.get(i);
            float w = weight(width, height, p.minWidthDps, p.minHeightDps, WEIGHT_POWER);
            weights += w;
            out.add(new DisplayOption().add(p).multiply(w));
        }
        return out.multiply(1.0f / weights);
    }

    public DeviceProfile getDeviceProfile(Context context) {
        Resources res = context.getResources();
        Configuration config = context.getResources().getConfiguration();

        float availableWidth = config.screenWidthDp * res.getDisplayMetrics().density;
        float availableHeight = config.screenHeightDp * res.getDisplayMetrics().density;

        DeviceProfile bestMatch = supportedProfiles.get(0);
        float minDiff = Float.MAX_VALUE;

        for (DeviceProfile profile : supportedProfiles) {
            float diff = Math.abs(profile.availableWidthPx - availableWidth)
                    + Math.abs(profile.availableHeightPx - availableHeight);
            if (diff < minDiff) {
                minDiff = diff;
                bestMatch = profile;
            }
        }
        return bestMatch;
    }

    private static float weight(float x0, float y0, float x1, float y1, float pow) {
        float d = dist(x0, y0, x1, y1);
        if (Float.compare(d, 0f) == 0) {
            return Float.POSITIVE_INFINITY;
        }
        return (float) (WEIGHT_EFFICIENT / Math.pow(d, pow));
    }

    /**
     * As a ratio of screen height, the total distance we want the parallax effect to span
     * horizontally
     */
    private static float wallpaperTravelToScreenWidthRatio(int width, int height) {
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

    public interface OnIDPChangeListener {

        /**
         * Called when the device provide changes
         */
        void onIdpChanged(InvariantDeviceProfile profile);
    }


    public static final class GridOption {

        public static final String TAG_NAME = "grid-option";

        public final String name;
        public final int numRows;
        public final int numColumns;

        private final int numFolderRows;
        private final int numFolderColumns;

        private final int numAllAppsColumns;
        private final int numDatabaseAllAppsColumns;
        private final int numHotseatIcons;
        private final int numDatabaseHotseatIcons;

        private final String dbFile;

        private final int defaultLayoutId;
        private final int demoModeLayoutId;

        private final boolean isScalable;
        private final int devicePaddingId;

        private final SparseArray<TypedValue> extraAttrs;

        public GridOption(Context context, AttributeSet attrs) {
            TypedArray a = context.obtainStyledAttributes(
                    attrs, R.styleable.GridDisplayOption);
            name = a.getString(R.styleable.GridDisplayOption_name);
            numRows = a.getInt(R.styleable.GridDisplayOption_numRows, 0);
            numColumns = a.getInt(R.styleable.GridDisplayOption_numColumns, 0);

            dbFile = a.getString(R.styleable.GridDisplayOption_dbFile);
            defaultLayoutId = a.getResourceId(
                    R.styleable.GridDisplayOption_defaultLayoutId, 0);
            demoModeLayoutId = a.getResourceId(
                    R.styleable.GridDisplayOption_demoModeLayoutId, defaultLayoutId);

            numAllAppsColumns = a.getInt(
                    R.styleable.GridDisplayOption_numAllAppsColumns, numColumns);
            numDatabaseAllAppsColumns = a.getInt(
                    R.styleable.GridDisplayOption_numExtendedAllAppsColumns, 2 * numAllAppsColumns);

            numHotseatIcons = a.getInt(
                    R.styleable.GridDisplayOption_numHotseatIcons, numColumns);
            numDatabaseHotseatIcons = a.getInt(
                    R.styleable.GridDisplayOption_numExtendedHotseatIcons, 2 * numHotseatIcons);

            numFolderRows = a.getInt(
                    R.styleable.GridDisplayOption_numFolderRows, numRows);
            numFolderColumns = a.getInt(
                    R.styleable.GridDisplayOption_numFolderColumns, numColumns);

            isScalable = a.getBoolean(
                    R.styleable.GridDisplayOption_isScalable, false);
            devicePaddingId = a.getResourceId(
                    R.styleable.GridDisplayOption_devicePaddingId, 0);

            a.recycle();
            extraAttrs = Themes.createValueMap(context, attrs,
                    IntArray.wrap(R.styleable.GridDisplayOption));
        }
    }

    @VisibleForTesting
    static final class DisplayOption {

        public final GridOption grid;

        private final float minWidthDps;
        private final float minHeightDps;
        private final boolean canBeDefault;

        private float minCellHeight;
        private float minCellWidth;
        private float borderSpacing;

        private float iconSize;
        private float iconTextSize;
        private float landscapeIconSize;
        private float landscapeIconTextSize;
        private float allAppsIconSize;
        private float allAppsIconTextSize;

        DisplayOption(GridOption grid, Context context, AttributeSet attrs, int defaultFlagValue) {
            this.grid = grid;

            TypedArray a = context.obtainStyledAttributes(
                    attrs, R.styleable.ProfileDisplayOption);

            minWidthDps = a.getFloat(R.styleable.ProfileDisplayOption_minWidthDps, 0);
            minHeightDps = a.getFloat(R.styleable.ProfileDisplayOption_minHeightDps, 0);

            canBeDefault = a.getInt(R.styleable.ProfileDisplayOption_canBeDefault, 0)
                    == defaultFlagValue;

            minCellHeight = a.getFloat(R.styleable.ProfileDisplayOption_minCellHeightDps, 0);
            minCellWidth = a.getFloat(R.styleable.ProfileDisplayOption_minCellWidthDps, 0);
            borderSpacing = a.getFloat(R.styleable.ProfileDisplayOption_borderSpacingDps, 0);

            iconSize = a.getFloat(R.styleable.ProfileDisplayOption_iconImageSize, 0);
            landscapeIconSize = a.getFloat(R.styleable.ProfileDisplayOption_landscapeIconSize,
                    iconSize);
            iconTextSize = a.getFloat(R.styleable.ProfileDisplayOption_iconTextSize, 0);
            landscapeIconTextSize = a.getFloat(
                    R.styleable.ProfileDisplayOption_landscapeIconTextSize, iconTextSize);

            allAppsIconSize = a.getFloat(R.styleable.ProfileDisplayOption_allAppsIconSize,
                    iconSize);
            allAppsIconTextSize = a.getFloat(R.styleable.ProfileDisplayOption_allAppsIconTextSize,
                    iconTextSize);
            a.recycle();
        }

        DisplayOption() {
            this(null);
        }

        DisplayOption(GridOption grid) {
            this.grid = grid;
            minWidthDps = 0;
            minHeightDps = 0;
            canBeDefault = false;
            minCellHeight = 0;
            minCellWidth = 0;
            borderSpacing = 0;
        }

        private DisplayOption multiply(float w) {
            iconSize *= w;
            landscapeIconSize *= w;
            allAppsIconSize *= w;
            iconTextSize *= w;
            landscapeIconTextSize *= w;
            allAppsIconTextSize *= w;
            minCellHeight *= w;
            minCellWidth *= w;
            borderSpacing *= w;
            return this;
        }

        private DisplayOption add(DisplayOption p) {
            iconSize += p.iconSize;
            landscapeIconSize += p.landscapeIconSize;
            allAppsIconSize += p.allAppsIconSize;
            iconTextSize += p.iconTextSize;
            landscapeIconTextSize += p.landscapeIconTextSize;
            allAppsIconTextSize += p.allAppsIconTextSize;
            minCellHeight += p.minCellHeight;
            minCellWidth += p.minCellWidth;
            borderSpacing += p.borderSpacing;
            return this;
        }
    }
}
