/*
 * Copyright (C) 2021 The Android Open Source Project
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
import static com.android.launcher3.config.FeatureFlags.ENABLE_TWO_PANEL_HOME;
import static com.android.launcher3.util.DisplayController.CHANGE_DENSITY;
import static com.android.launcher3.util.DisplayController.CHANGE_SUPPORTED_BOUNDS;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.annotation.TargetApi;
import android.appwidget.AppWidgetHostView;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.util.Xml;
import android.view.Display;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.model.DeviceGridState;
import com.android.launcher3.provider.RestoreDbTask;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.DisplayController.Info;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.WindowBounds;

import com.android.quickstep.SystemUiProxy;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class InvariantDeviceProfile implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String TAG = "IDP";
    // We do not need any synchronization for this variable as its only written on UI thread.
    public static final MainThreadInitializedObject<InvariantDeviceProfile> INSTANCE =
            new MainThreadInitializedObject<>(InvariantDeviceProfile::new);

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({TYPE_PHONE, TYPE_MULTI_DISPLAY, TYPE_TABLET})
    public @interface DeviceType{}
    public static final int TYPE_PHONE = 0;
    public static final int TYPE_MULTI_DISPLAY = 1;
    public static final int TYPE_TABLET = 2;

    private static final String KEY_IDP_GRID_NAME = "idp_grid_name";

    private static final float ICON_SIZE_DEFINED_IN_APP_DP = 48;

    // Constants that affects the interpolation curve between statically defined device profile
    // buckets.
    private static final float KNEARESTNEIGHBOR = 3;
    private static final float WEIGHT_POWER = 5;

    // used to offset float not being able to express extremely small weights in extreme cases.
    private static final float WEIGHT_EFFICIENT = 100000f;

    // Used for arrays to specify different sizes (e.g. border spaces, width/height) in different
    // constraints
    static final int COUNT_SIZES = 5;
    static final int INDEX_DEFAULT = 0;
    static final int INDEX_LANDSCAPE = 1;
    static final int INDEX_TWO_PANEL_PORTRAIT = 2;
    static final int INDEX_TWO_PANEL_LANDSCAPE = 3;
    static final int INDEX_ALL_APPS = 4;

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
    public float[] iconSize;
    public float[] iconTextSize;
    public int iconBitmapSize;
    public int fillResIconDpi;
    public @DeviceType int deviceType;

    public PointF[] minCellSize;

    public PointF[] borderSpaces;
    public float folderBorderSpace;

    public float[] horizontalMargin;

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

    @Nullable
    public DevicePaddings devicePaddings;

    public Point defaultWallpaperSize;
    public Rect defaultWidgetPadding;

    private final ArrayList<OnIDPChangeListener> mChangeListeners = new ArrayList<>();
    private Context mContext;

    @VisibleForTesting
    public InvariantDeviceProfile() {
    }

    @TargetApi(23)
    private InvariantDeviceProfile(Context context) {
        String gridName = getCurrentGridName(context);
        String newGridName = initGrid(context, gridName);
        if (!newGridName.equals(gridName)) {
            Utilities.getPrefs(context).edit().putString(KEY_IDP_GRID_NAME, newGridName).apply();
        }
        new DeviceGridState(this).writeToPrefs(context);

        DisplayController.INSTANCE.get(context).setPriorityListener(
                (displayContext, info, flags) -> {
                    if ((flags & (CHANGE_DENSITY | CHANGE_SUPPORTED_BOUNDS)) != 0) {
                        onConfigChanged(displayContext);
                    }
                });

        mContext = context;
        Utilities.getPrefs(context).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (DeviceProfile.KEY_PHONE_TASKBAR.equals(key)) {
            // Create the illusion of this taking effect immediately
            // Also needed because TaskbarManager inits before SystemUiProxy on start
            boolean enabled = Utilities.getPrefs(mContext).getBoolean(DeviceProfile.KEY_PHONE_TASKBAR, false);
            SystemUiProxy.INSTANCE.get(mContext).setTaskbarEnabled(enabled);

            onConfigChanged(mContext, true);
        }
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
        Info defaultInfo = DisplayController.INSTANCE.get(context).getInfo();
        @DeviceType int defaultDeviceType = getDeviceType(defaultInfo);
        DisplayOption defaultDisplayOption = invDistWeightedInterpolate(
                defaultInfo,
                getPredefinedDeviceProfiles(context, gridName, defaultDeviceType,
                        /*allowDisabledGrid=*/false),
                defaultDeviceType);

        Info myInfo = new Info(context, display);
        @DeviceType int deviceType = getDeviceType(myInfo);
        DisplayOption myDisplayOption = invDistWeightedInterpolate(
                myInfo,
                getPredefinedDeviceProfiles(context, gridName, deviceType,
                        /*allowDisabledGrid=*/false),
                deviceType);

        DisplayOption result = new DisplayOption(defaultDisplayOption.grid)
                .add(myDisplayOption);
        result.iconSizes[INDEX_DEFAULT] =
                defaultDisplayOption.iconSizes[INDEX_DEFAULT];
        for (int i = 1; i < COUNT_SIZES; i++) {
            result.iconSizes[i] = Math.min(
                    defaultDisplayOption.iconSizes[i], myDisplayOption.iconSizes[i]);
        }

        System.arraycopy(defaultDisplayOption.minCellSize, 0, result.minCellSize, 0,
                COUNT_SIZES);
        System.arraycopy(defaultDisplayOption.borderSpaces, 0, result.borderSpaces, 0,
                COUNT_SIZES);

        initGrid(context, myInfo, result, deviceType);
    }

    /**
     * Reinitialize the current grid after a restore, where some grids might now be disabled.
     */
    public void reinitializeAfterRestore(Context context) {
        String currentDbFile = dbFile;
        String gridName = getCurrentGridName(context);
        String newGridName = initGrid(context, gridName);
        if (!newGridName.equals(gridName)) {
            Log.d(TAG, "Restored grid is disabled : " + gridName
                    + ", migrating to: " + newGridName
                    + ", removing all other grid db files");
            for (String gridDbFile : LauncherFiles.GRID_DB_FILES) {
                if (gridDbFile.equals(currentDbFile)) {
                    continue;
                }
                if (context.getDatabasePath(gridDbFile).delete()) {
                    Log.d(TAG, "Removed old grid db file: " + gridDbFile);
                }
            }
            setCurrentGrid(context, gridName);
        }
    }

    private static @DeviceType int getDeviceType(Info displayInfo) {
        // Each screen has two profiles (portrait/landscape), so devices with four or more
        // supported profiles implies two or more internal displays.
        if (displayInfo.supportedBounds.size() >= 4 && ENABLE_TWO_PANEL_HOME.get()) {
            return TYPE_MULTI_DISPLAY;
        } else if (displayInfo.supportedBounds.stream().allMatch(displayInfo::isTablet)) {
            return TYPE_TABLET;
        } else {
            return TYPE_PHONE;
        }
    }

    public static String getCurrentGridName(Context context) {
        return Utilities.isGridOptionsEnabled(context)
                ? Utilities.getPrefs(context).getString(KEY_IDP_GRID_NAME, null) : null;
    }

    private String initGrid(Context context, String gridName) {
        Info displayInfo = DisplayController.INSTANCE.get(context).getInfo();
        @DeviceType int deviceType = getDeviceType(displayInfo);

        ArrayList<DisplayOption> allOptions =
                getPredefinedDeviceProfiles(context, gridName, deviceType,
                        RestoreDbTask.isPending(context));
        DisplayOption displayOption =
                invDistWeightedInterpolate(displayInfo, allOptions, deviceType);
        initGrid(context, displayInfo, displayOption, deviceType);
        return displayOption.grid.name;
    }

    private void initGrid(Context context, Info displayInfo, DisplayOption displayOption,
            @DeviceType int deviceType) {
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
        this.deviceType = deviceType;

        mExtraAttrs = closestProfile.extraAttrs;

        iconSize = displayOption.iconSizes;
        float maxIconSize = iconSize[0];
        for (int i = 1; i < iconSize.length; i++) {
            maxIconSize = Math.max(maxIconSize, iconSize[i]);
        }
        iconBitmapSize = ResourceUtils.pxFromDp(maxIconSize, metrics);
        fillResIconDpi = getLauncherIconDensity(iconBitmapSize);

        iconTextSize = displayOption.textSizes;

        minCellSize = displayOption.minCellSize;

        borderSpaces = displayOption.borderSpaces;
        folderBorderSpace = displayOption.folderBorderSpace;

        horizontalMargin = displayOption.horizontalMargin;

        numShownHotseatIcons = closestProfile.numHotseatIcons;
        numDatabaseHotseatIcons = deviceType == TYPE_MULTI_DISPLAY
                ? closestProfile.numDatabaseHotseatIcons : closestProfile.numHotseatIcons;

        numAllAppsColumns = closestProfile.numAllAppsColumns;
        numDatabaseAllAppsColumns = deviceType == TYPE_MULTI_DISPLAY
                ? closestProfile.numDatabaseAllAppsColumns : closestProfile.numAllAppsColumns;

        if (!Utilities.isGridOptionsEnabled(context)) {
            iconSize[INDEX_ALL_APPS] = iconSize[INDEX_DEFAULT];
            iconTextSize[INDEX_ALL_APPS] = iconTextSize[INDEX_DEFAULT];
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
                    .setUseTwoPanels(deviceType == TYPE_MULTI_DISPLAY)
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

    private Object[] toModelState() {
        return new Object[]{
                numColumns, numRows, numDatabaseHotseatIcons, iconBitmapSize, fillResIconDpi,
                numDatabaseAllAppsColumns, dbFile};
    }

    private void onConfigChanged(Context context) {
        onConfigChanged(context, false);
    }

    private void onConfigChanged(Context context, boolean taskbarChanged) {
        Object[] oldState = toModelState();

        // Re-init grid
        String gridName = getCurrentGridName(context);
        initGrid(context, gridName);

        boolean modelPropsChanged = !Arrays.equals(oldState, toModelState());
        for (OnIDPChangeListener listener : mChangeListeners) {
            listener.onIdpChanged(modelPropsChanged, taskbarChanged);
        }
    }

    private static ArrayList<DisplayOption> getPredefinedDeviceProfiles(Context context,
            String gridName, @DeviceType int deviceType, boolean allowDisabledGrid) {
        ArrayList<DisplayOption> profiles = new ArrayList<>();

        try (XmlResourceParser parser = context.getResources().getXml(R.xml.device_profiles)) {
            final int depth = parser.getDepth();
            int type;
            while (((type = parser.next()) != XmlPullParser.END_TAG ||
                    parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {
                if ((type == XmlPullParser.START_TAG)
                        && GridOption.TAG_NAME.equals(parser.getName())) {

                    GridOption gridOption = new GridOption(context, Xml.asAttributeSet(parser),
                            deviceType);
                    if (gridOption.isEnabled || allowDisabledGrid) {
                        final int displayDepth = parser.getDepth();
                        while (((type = parser.next()) != XmlPullParser.END_TAG
                                || parser.getDepth() > displayDepth)
                                && type != XmlPullParser.END_DOCUMENT) {
                            if ((type == XmlPullParser.START_TAG) && "display-option".equals(
                                    parser.getName())) {
                                profiles.add(new DisplayOption(gridOption, context,
                                        Xml.asAttributeSet(parser)));
                            }
                        }
                    }
                }
            }
        } catch (IOException | XmlPullParserException e) {
            throw new RuntimeException(e);
        }

        ArrayList<DisplayOption> filteredProfiles = new ArrayList<>();
        if (!TextUtils.isEmpty(gridName)) {
            for (DisplayOption option : profiles) {
                if (gridName.equals(option.grid.name)
                        && (option.grid.isEnabled || allowDisabledGrid)) {
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

    /**
     * @return all the grid options that can be shown on the device
     */
    public List<GridOption> parseAllGridOptions(Context context) {
        List<GridOption> result = new ArrayList<>();

        try (XmlResourceParser parser = context.getResources().getXml(R.xml.device_profiles)) {
            final int depth = parser.getDepth();
            int type;
            while (((type = parser.next()) != XmlPullParser.END_TAG
                    || parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {
                if ((type == XmlPullParser.START_TAG)
                        && GridOption.TAG_NAME.equals(parser.getName())) {
                    GridOption option =
                            new GridOption(context, Xml.asAttributeSet(parser), deviceType);
                    if (option.isEnabled) {
                        result.add(option);
                    }
                }
            }
        } catch (IOException | XmlPullParserException e) {
            Log.e(TAG, "Error parsing device profile", e);
            return Collections.emptyList();
        }
        return result;
    }

    private int getLauncherIconDensity(int requiredSize) {
        // Densities typically defined by an app.
        int[] densityBuckets = new int[]{
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
            Info displayInfo, ArrayList<DisplayOption> points, @DeviceType int deviceType) {
        int minWidthPx = Integer.MAX_VALUE;
        int minHeightPx = Integer.MAX_VALUE;
        for (WindowBounds bounds : displayInfo.supportedBounds) {
            boolean isTablet = displayInfo.isTablet(bounds);
            if (isTablet && deviceType == TYPE_MULTI_DISPLAY) {
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

        DisplayOption closestPoint = points.get(0);
        GridOption closestOption = closestPoint.grid;
        float weights = 0;

        if (dist(width, height, closestPoint.minWidthDps, closestPoint.minHeightDps) == 0) {
            return closestPoint;
        }

        DisplayOption out = new DisplayOption(closestOption);
        for (int i = 0; i < points.size() && i < KNEARESTNEIGHBOR; ++i) {
            DisplayOption p = points.get(i);
            float w = weight(width, height, p.minWidthDps, p.minHeightDps, WEIGHT_POWER);
            weights += w;
            out.add(new DisplayOption().add(p).multiply(w));
        }
        out.multiply(1.0f / weights);

        // Since the bitmaps are persisted, ensure that all bitmap sizes are not larger than
        // predefined size to avoid cache invalidation
        for (int i = INDEX_DEFAULT; i < COUNT_SIZES; i++) {
            out.iconSizes[i] = Math.min(out.iconSizes[i], closestPoint.iconSizes[i]);
        }

        return out;
    }

    public DeviceProfile getDeviceProfile(Context context) {
        Resources res = context.getResources();
        Configuration config = context.getResources().getConfiguration();

        float screenWidth = config.screenWidthDp * res.getDisplayMetrics().density;
        float screenHeight = config.screenHeightDp * res.getDisplayMetrics().density;
        return getBestMatch(screenWidth, screenHeight);
    }

    public DeviceProfile getBestMatch(float screenWidth, float screenHeight) {
        DeviceProfile bestMatch = supportedProfiles.get(0);
        float minDiff = Float.MAX_VALUE;

        for (DeviceProfile profile : supportedProfiles) {
            float diff = Math.abs(profile.widthPx - screenWidth)
                    + Math.abs(profile.heightPx - screenHeight);
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

        final float ASPECT_RATIO_LANDSCAPE = 16 / 10f;
        final float ASPECT_RATIO_PORTRAIT = 10 / 16f;
        final float WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE = 1.5f;
        final float WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT = 1.2f;

        // To find out the desired width at different aspect ratios, we use the following two
        // formulas, where the coefficient on x is the aspect ratio (width/height):
        //   (16/10)x + y = 1.5
        //   (10/16)x + y = 1.2
        // We solve for x and y and end up with a final formula:
        final float x =
                (WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE
                        - WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT) /
                        (ASPECT_RATIO_LANDSCAPE - ASPECT_RATIO_PORTRAIT);
        final float y = WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT - x * ASPECT_RATIO_PORTRAIT;
        return x * aspectRatio + y;
    }

    public interface OnIDPChangeListener {

        /**
         * Called when the device provide changes
         */
        void onIdpChanged(boolean modelPropertiesChanged, boolean taskbarChanged);
    }


    public static final class GridOption {

        public static final String TAG_NAME = "grid-option";

        private static final int DEVICE_CATEGORY_PHONE = 1 << 0;
        private static final int DEVICE_CATEGORY_TABLET = 1 << 1;
        private static final int DEVICE_CATEGORY_MULTI_DISPLAY = 1 << 2;
        private static final int DEVICE_CATEGORY_ALL =
                DEVICE_CATEGORY_PHONE | DEVICE_CATEGORY_TABLET | DEVICE_CATEGORY_MULTI_DISPLAY;

        public final String name;
        public final int numRows;
        public final int numColumns;
        public final boolean isEnabled;

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

        public GridOption(Context context, AttributeSet attrs, @DeviceType int deviceType) {
            TypedArray a = context.obtainStyledAttributes(
                    attrs, R.styleable.GridDisplayOption);
            name = a.getString(R.styleable.GridDisplayOption_name);
            numRows = a.getInt(R.styleable.GridDisplayOption_numRows, 0);
            numColumns = a.getInt(R.styleable.GridDisplayOption_numColumns, 0);

            dbFile = a.getString(R.styleable.GridDisplayOption_dbFile);
            defaultLayoutId = a.getResourceId(deviceType == TYPE_MULTI_DISPLAY && a.hasValue(
                    R.styleable.GridDisplayOption_defaultSplitDisplayLayoutId)
                    ? R.styleable.GridDisplayOption_defaultSplitDisplayLayoutId
                    : R.styleable.GridDisplayOption_defaultLayoutId, 0);
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

            int deviceCategory = a.getInt(R.styleable.GridDisplayOption_deviceCategory,
                    DEVICE_CATEGORY_ALL);
            isEnabled = (deviceType == TYPE_PHONE
                        && ((deviceCategory & DEVICE_CATEGORY_PHONE) == DEVICE_CATEGORY_PHONE))
                    || (deviceType == TYPE_TABLET
                        && ((deviceCategory & DEVICE_CATEGORY_TABLET) == DEVICE_CATEGORY_TABLET))
                    || (deviceType == TYPE_MULTI_DISPLAY
                        && ((deviceCategory & DEVICE_CATEGORY_MULTI_DISPLAY)
                            == DEVICE_CATEGORY_MULTI_DISPLAY));

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

        private final PointF[] minCellSize = new PointF[COUNT_SIZES];

        private float folderBorderSpace;
        private final PointF[] borderSpaces = new PointF[COUNT_SIZES];
        private final float[] horizontalMargin = new float[COUNT_SIZES];

        private final float[] iconSizes = new float[COUNT_SIZES];
        private final float[] textSizes = new float[COUNT_SIZES];

        DisplayOption(GridOption grid, Context context, AttributeSet attrs) {
            this.grid = grid;

            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ProfileDisplayOption);

            minWidthDps = a.getFloat(R.styleable.ProfileDisplayOption_minWidthDps, 0);
            minHeightDps = a.getFloat(R.styleable.ProfileDisplayOption_minHeightDps, 0);

            canBeDefault = a.getBoolean(R.styleable.ProfileDisplayOption_canBeDefault, false);

            float x;
            float y;

            x = a.getFloat(R.styleable.ProfileDisplayOption_minCellWidthDps, 0);
            y = a.getFloat(R.styleable.ProfileDisplayOption_minCellHeightDps, 0);
            minCellSize[INDEX_DEFAULT] = new PointF(x, y);
            minCellSize[INDEX_LANDSCAPE] = new PointF(x, y);
            minCellSize[INDEX_ALL_APPS] = new PointF(x, y);

            x = a.getFloat(R.styleable.ProfileDisplayOption_twoPanelPortraitMinCellWidthDps,
                    minCellSize[INDEX_DEFAULT].x);
            y = a.getFloat(R.styleable.ProfileDisplayOption_twoPanelPortraitMinCellHeightDps,
                    minCellSize[INDEX_DEFAULT].y);
            minCellSize[INDEX_TWO_PANEL_PORTRAIT] = new PointF(x, y);

            x = a.getFloat(R.styleable.ProfileDisplayOption_twoPanelLandscapeMinCellWidthDps,
                    minCellSize[INDEX_DEFAULT].x);
            y = a.getFloat(R.styleable.ProfileDisplayOption_twoPanelLandscapeMinCellHeightDps,
                    minCellSize[INDEX_DEFAULT].y);
            minCellSize[INDEX_TWO_PANEL_LANDSCAPE] = new PointF(x, y);

            float borderSpace = a.getFloat(R.styleable.ProfileDisplayOption_borderSpaceDps, 0);
            float twoPanelPortraitBorderSpaceDps = a.getFloat(
                    R.styleable.ProfileDisplayOption_twoPanelPortraitBorderSpaceDps, borderSpace);
            float twoPanelLandscapeBorderSpaceDps = a.getFloat(
                    R.styleable.ProfileDisplayOption_twoPanelLandscapeBorderSpaceDps, borderSpace);

            x = a.getFloat(R.styleable.ProfileDisplayOption_borderSpaceHorizontalDps, borderSpace);
            y = a.getFloat(R.styleable.ProfileDisplayOption_borderSpaceVerticalDps, borderSpace);
            borderSpaces[INDEX_DEFAULT] = new PointF(x, y);
            borderSpaces[INDEX_LANDSCAPE] = new PointF(x, y);

            x = a.getFloat(
                    R.styleable.ProfileDisplayOption_twoPanelPortraitBorderSpaceHorizontalDps,
                    twoPanelPortraitBorderSpaceDps);
            y = a.getFloat(
                    R.styleable.ProfileDisplayOption_twoPanelPortraitBorderSpaceVerticalDps,
                    twoPanelPortraitBorderSpaceDps);
            borderSpaces[INDEX_TWO_PANEL_PORTRAIT] = new PointF(x, y);

            x = a.getFloat(
                    R.styleable.ProfileDisplayOption_twoPanelLandscapeBorderSpaceHorizontalDps,
                    twoPanelLandscapeBorderSpaceDps);
            y = a.getFloat(
                    R.styleable.ProfileDisplayOption_twoPanelLandscapeBorderSpaceVerticalDps,
                    twoPanelLandscapeBorderSpaceDps);
            borderSpaces[INDEX_TWO_PANEL_LANDSCAPE] = new PointF(x, y);

            x = y = a.getFloat(R.styleable.ProfileDisplayOption_allAppsCellSpacingDps,
                    borderSpace);
            borderSpaces[INDEX_ALL_APPS] = new PointF(x, y);
            folderBorderSpace = borderSpace;

            iconSizes[INDEX_DEFAULT] =
                    a.getFloat(R.styleable.ProfileDisplayOption_iconImageSize, 0);
            iconSizes[INDEX_LANDSCAPE] =
                    a.getFloat(R.styleable.ProfileDisplayOption_landscapeIconSize,
                            iconSizes[INDEX_DEFAULT]);
            iconSizes[INDEX_ALL_APPS] =
                    a.getFloat(R.styleable.ProfileDisplayOption_allAppsIconSize,
                            iconSizes[INDEX_DEFAULT]);
            iconSizes[INDEX_TWO_PANEL_PORTRAIT] =
                    a.getFloat(R.styleable.ProfileDisplayOption_twoPanelPortraitIconSize,
                            iconSizes[INDEX_DEFAULT]);
            iconSizes[INDEX_TWO_PANEL_LANDSCAPE] =
                    a.getFloat(R.styleable.ProfileDisplayOption_twoPanelLandscapeIconSize,
                            iconSizes[INDEX_DEFAULT]);

            textSizes[INDEX_DEFAULT] =
                    a.getFloat(R.styleable.ProfileDisplayOption_iconTextSize, 0);
            textSizes[INDEX_LANDSCAPE] =
                    a.getFloat(R.styleable.ProfileDisplayOption_landscapeIconTextSize,
                            textSizes[INDEX_DEFAULT]);
            textSizes[INDEX_ALL_APPS] =
                    a.getFloat(R.styleable.ProfileDisplayOption_allAppsIconTextSize,
                            textSizes[INDEX_DEFAULT]);
            textSizes[INDEX_TWO_PANEL_PORTRAIT] =
                    a.getFloat(R.styleable.ProfileDisplayOption_twoPanelPortraitIconTextSize,
                            textSizes[INDEX_DEFAULT]);
            textSizes[INDEX_TWO_PANEL_LANDSCAPE] =
                    a.getFloat(R.styleable.ProfileDisplayOption_twoPanelLandscapeIconTextSize,
                            textSizes[INDEX_DEFAULT]);

            horizontalMargin[INDEX_DEFAULT] = a.getFloat(
                    R.styleable.ProfileDisplayOption_horizontalMargin, 0);
            horizontalMargin[INDEX_LANDSCAPE] = horizontalMargin[INDEX_DEFAULT];
            horizontalMargin[INDEX_ALL_APPS] = horizontalMargin[INDEX_DEFAULT];
            horizontalMargin[INDEX_TWO_PANEL_LANDSCAPE] = a.getFloat(
                    R.styleable.ProfileDisplayOption_twoPanelLandscapeHorizontalMargin,
                    horizontalMargin[INDEX_DEFAULT]);
            horizontalMargin[INDEX_TWO_PANEL_PORTRAIT] = a.getFloat(
                    R.styleable.ProfileDisplayOption_twoPanelPortraitHorizontalMargin,
                    horizontalMargin[INDEX_DEFAULT]);

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
            for (int i = 0; i < COUNT_SIZES; i++) {
                iconSizes[i] = 0;
                textSizes[i] = 0;
                borderSpaces[i] = new PointF();
                minCellSize[i] = new PointF();
            }
        }

        private DisplayOption multiply(float w) {
            for (int i = 0; i < COUNT_SIZES; i++) {
                iconSizes[i] *= w;
                textSizes[i] *= w;
                borderSpaces[i].x *= w;
                borderSpaces[i].y *= w;
                minCellSize[i].x *= w;
                minCellSize[i].y *= w;
                horizontalMargin[i] *= w;
            }

            folderBorderSpace *= w;

            return this;
        }

        private DisplayOption add(DisplayOption p) {
            for (int i = 0; i < COUNT_SIZES; i++) {
                iconSizes[i] += p.iconSizes[i];
                textSizes[i] += p.textSizes[i];
                borderSpaces[i].x += p.borderSpaces[i].x;
                borderSpaces[i].y += p.borderSpaces[i].y;
                minCellSize[i].x += p.minCellSize[i].x;
                minCellSize[i].y += p.minCellSize[i].y;
                horizontalMargin[i] += p.horizontalMargin[i];
            }

            folderBorderSpace += p.folderBorderSpace;

            return this;
        }
    }
}
