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

import static com.android.launcher3.Utilities.getDevicePrefs;
import static com.android.launcher3.Utilities.getPointString;
import static com.android.launcher3.config.FeatureFlags.APPLY_CONFIG_AT_RUNTIME;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.PackageManagerHelper.getPackageFilter;

import android.annotation.TargetApi;
import android.appwidget.AppWidgetHostView;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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

import com.android.launcher3.graphics.IconShape;
import com.android.launcher3.util.ConfigMonitor;
import com.android.launcher3.util.DefaultDisplay;
import com.android.launcher3.util.DefaultDisplay.Info;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.Themes;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

public class InvariantDeviceProfile {

    public static final String TAG = "IDP";
    // We do not need any synchronization for this variable as its only written on UI thread.
    public static final MainThreadInitializedObject<InvariantDeviceProfile> INSTANCE =
            new MainThreadInitializedObject<>(InvariantDeviceProfile::new);

    public static final String KEY_MIGRATION_SRC_WORKSPACE_SIZE = "migration_src_workspace_size";
    public static final String KEY_MIGRATION_SRC_HOTSEAT_COUNT = "migration_src_hotseat_count";

    private static final String KEY_IDP_GRID_NAME = "idp_grid_name";

    private static final float ICON_SIZE_DEFINED_IN_APP_DP = 48;

    public static final int CHANGE_FLAG_GRID = 1 << 0;
    public static final int CHANGE_FLAG_ICON_PARAMS = 1 << 1;

    public static final String KEY_ICON_PATH_REF = "pref_icon_shape_path";

    // Constants that affects the interpolation curve between statically defined device profile
    // buckets.
    private static final float KNEARESTNEIGHBOR = 3;
    private static final float WEIGHT_POWER = 5;

    // used to offset float not being able to express extremely small weights in extreme cases.
    private static final float WEIGHT_EFFICIENT = 100000f;

    private static final int CONFIG_ICON_MASK_RES_ID = Resources.getSystem().getIdentifier(
            "config_icon_mask", "string", "android");

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
    public String iconShapePath;
    public float landscapeIconSize;
    public int iconBitmapSize;
    public int fillResIconDpi;
    public float iconTextSize;
    public float allAppsIconSize;
    public float allAppsIconTextSize;

    private SparseArray<TypedValue> mExtraAttrs;

    /**
     * Number of icons inside the hotseat area.
     */
    public int numHotseatIcons;

    /**
     * Number of columns in the all apps list.
     */
    public int numAllAppsColumns;

    public String dbFile;
    public int defaultLayoutId;
    int demoModeLayoutId;

    public DeviceProfile landscapeProfile;
    public DeviceProfile portraitProfile;

    public Point defaultWallpaperSize;
    public Rect defaultWidgetPadding;

    private final ArrayList<OnIDPChangeListener> mChangeListeners = new ArrayList<>();
    private ConfigMonitor mConfigMonitor;
    private OverlayMonitor mOverlayMonitor;

    @VisibleForTesting
    public InvariantDeviceProfile() {}

    private InvariantDeviceProfile(InvariantDeviceProfile p) {
        numRows = p.numRows;
        numColumns = p.numColumns;
        numFolderRows = p.numFolderRows;
        numFolderColumns = p.numFolderColumns;
        iconSize = p.iconSize;
        iconShapePath = p.iconShapePath;
        landscapeIconSize = p.landscapeIconSize;
        iconBitmapSize = p.iconBitmapSize;
        iconTextSize = p.iconTextSize;
        numHotseatIcons = p.numHotseatIcons;
        numAllAppsColumns = p.numAllAppsColumns;
        dbFile = p.dbFile;
        allAppsIconSize = p.allAppsIconSize;
        allAppsIconTextSize = p.allAppsIconTextSize;
        defaultLayoutId = p.defaultLayoutId;
        demoModeLayoutId = p.demoModeLayoutId;
        mExtraAttrs = p.mExtraAttrs;
        mOverlayMonitor = p.mOverlayMonitor;
    }

    @TargetApi(23)
    private InvariantDeviceProfile(Context context) {
        String gridName = getCurrentGridName(context);
        String newGridName = initGrid(context, gridName);
        if (!newGridName.equals(gridName)) {
            Utilities.getPrefs(context).edit().putString(KEY_IDP_GRID_NAME, newGridName).apply();
        }
        Utilities.getPrefs(context).edit()
                .putInt(KEY_MIGRATION_SRC_HOTSEAT_COUNT, numHotseatIcons)
                .putString(KEY_MIGRATION_SRC_WORKSPACE_SIZE, getPointString(numColumns, numRows))
                .apply();

        mConfigMonitor = new ConfigMonitor(context,
                APPLY_CONFIG_AT_RUNTIME.get() ? this::onConfigChanged : this::killProcess);
        mOverlayMonitor = new OverlayMonitor(context);
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
        InvariantDeviceProfile originalProfile = INSTANCE.get(context);
        String gridName = getCurrentGridName(context);

        // Get the display info based on default display and interpolate it to existing display
        DisplayOption defaultDisplayOption = invDistWeightedInterpolate(
                DefaultDisplay.INSTANCE.get(context).getInfo(),
                getPredefinedDeviceProfiles(context, gridName));

        Info myInfo = new Info(context, display);
        DisplayOption myDisplayOption = invDistWeightedInterpolate(
                myInfo, getPredefinedDeviceProfiles(context, gridName));

        DisplayOption result = new DisplayOption(defaultDisplayOption.grid)
                .add(myDisplayOption);
        result.iconSize = defaultDisplayOption.iconSize;
        result.landscapeIconSize = defaultDisplayOption.landscapeIconSize;
        result.allAppsIconSize = Math.min(
                defaultDisplayOption.allAppsIconSize, myDisplayOption.allAppsIconSize);
        initGrid(context, myInfo, result);
    }

    public static String getCurrentGridName(Context context) {
        return Utilities.isGridOptionsEnabled(context)
                ? Utilities.getPrefs(context).getString(KEY_IDP_GRID_NAME, null) : null;
    }

    /**
     * Retrieve system defined or RRO overriden icon shape.
     */
    private static String getIconShapePath(Context context) {
        if (CONFIG_ICON_MASK_RES_ID == 0) {
            Log.e(TAG, "Icon mask res identifier failed to retrieve.");
            return "";
        }
        return context.getResources().getString(CONFIG_ICON_MASK_RES_ID);
    }

    private String initGrid(Context context, String gridName) {
        DefaultDisplay.Info displayInfo = DefaultDisplay.INSTANCE.get(context).getInfo();
        ArrayList<DisplayOption> allOptions = getPredefinedDeviceProfiles(context, gridName);

        DisplayOption displayOption = invDistWeightedInterpolate(displayInfo, allOptions);
        initGrid(context, displayInfo, displayOption);
        return displayOption.grid.name;
    }

    private void initGrid(
            Context context, DefaultDisplay.Info displayInfo, DisplayOption displayOption) {
        GridOption closestProfile = displayOption.grid;
        numRows = closestProfile.numRows;
        numColumns = closestProfile.numColumns;
        numHotseatIcons = closestProfile.numHotseatIcons;
        dbFile = closestProfile.dbFile;
        defaultLayoutId = closestProfile.defaultLayoutId;
        demoModeLayoutId = closestProfile.demoModeLayoutId;
        numFolderRows = closestProfile.numFolderRows;
        numFolderColumns = closestProfile.numFolderColumns;
        numAllAppsColumns = closestProfile.numAllAppsColumns;

        mExtraAttrs = closestProfile.extraAttrs;

        iconSize = displayOption.iconSize;
        iconShapePath = getIconShapePath(context);
        landscapeIconSize = displayOption.landscapeIconSize;
        iconBitmapSize = ResourceUtils.pxFromDp(iconSize, displayInfo.metrics);
        iconTextSize = displayOption.iconTextSize;
        fillResIconDpi = getLauncherIconDensity(iconBitmapSize);

        if (Utilities.isGridOptionsEnabled(context)) {
            allAppsIconSize = displayOption.allAppsIconSize;
            allAppsIconTextSize = displayOption.allAppsIconTextSize;
        } else {
            allAppsIconSize = iconSize;
            allAppsIconTextSize = iconTextSize;
        }

        // If the partner customization apk contains any grid overrides, apply them
        // Supported overrides: numRows, numColumns, iconSize
        applyPartnerDeviceProfileOverrides(context, displayInfo.metrics);

        Point realSize = new Point(displayInfo.realSize);
        // The real size never changes. smallSide and largeSide will remain the
        // same in any orientation.
        int smallSide = Math.min(realSize.x, realSize.y);
        int largeSide = Math.max(realSize.x, realSize.y);

        DeviceProfile.Builder builder = new DeviceProfile.Builder(context, this, displayInfo)
                .setSizeRange(new Point(displayInfo.smallestSize),
                        new Point(displayInfo.largestSize));

        landscapeProfile = builder.setSize(largeSide, smallSide).build();
        portraitProfile = builder.setSize(smallSide, largeSide).build();

        // We need to ensure that there is enough extra space in the wallpaper
        // for the intended parallax effects
        if (context.getResources().getConfiguration().smallestScreenWidthDp >= 720) {
            defaultWallpaperSize = new Point(
                    (int) (largeSide * wallpaperTravelToScreenWidthRatio(largeSide, smallSide)),
                    largeSide);
        } else {
            defaultWallpaperSize = new Point(Math.max(smallSide * 2, largeSide), largeSide);
        }

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

    private void killProcess(Context context) {
        Log.e("ConfigMonitor", "restarting launcher");
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    public void verifyConfigChangedInBackground(final Context context) {
        String savedIconMaskPath = getDevicePrefs(context).getString(KEY_ICON_PATH_REF, "");
        // Good place to check if grid size changed in themepicker when launcher was dead.
        if (savedIconMaskPath.isEmpty()) {
            getDevicePrefs(context).edit().putString(KEY_ICON_PATH_REF, getIconShapePath(context))
                    .apply();
        } else if (!savedIconMaskPath.equals(getIconShapePath(context))) {
            getDevicePrefs(context).edit().putString(KEY_ICON_PATH_REF, getIconShapePath(context))
                    .apply();
            apply(context, CHANGE_FLAG_ICON_PARAMS);
        }
    }

    public void setCurrentGrid(Context context, String gridName) {
        Context appContext = context.getApplicationContext();
        Utilities.getPrefs(appContext).edit().putString(KEY_IDP_GRID_NAME, gridName).apply();
        MAIN_EXECUTOR.execute(() -> onConfigChanged(appContext));
    }

    private void onConfigChanged(Context context) {
        // Config changes, what shall we do?
        InvariantDeviceProfile oldProfile = new InvariantDeviceProfile(this);

        // Re-init grid
        String gridName = getCurrentGridName(context);
        initGrid(context, gridName);

        int changeFlags = 0;
        if (numRows != oldProfile.numRows ||
                numColumns != oldProfile.numColumns ||
                numFolderColumns != oldProfile.numFolderColumns ||
                numFolderRows != oldProfile.numFolderRows ||
                numHotseatIcons != oldProfile.numHotseatIcons) {
            changeFlags |= CHANGE_FLAG_GRID;
        }

        if (iconSize != oldProfile.iconSize || iconBitmapSize != oldProfile.iconBitmapSize ||
                !iconShapePath.equals(oldProfile.iconShapePath)) {
            changeFlags |= CHANGE_FLAG_ICON_PARAMS;
        }
        if (!iconShapePath.equals(oldProfile.iconShapePath)) {
            IconShape.init(context);
        }

        apply(context, changeFlags);
    }

    private void apply(Context context, int changeFlags) {
        // Create a new config monitor
        mConfigMonitor.unregister();
        mConfigMonitor = new ConfigMonitor(context, this::onConfigChanged);

        for (OnIDPChangeListener listener : mChangeListeners) {
            listener.onIdpChanged(changeFlags, this);
        }
    }

    static ArrayList<DisplayOption> getPredefinedDeviceProfiles(Context context, String gridName) {
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
                            profiles.add(new DisplayOption(
                                    gridOption, context, Xml.asAttributeSet(parser)));
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

    @VisibleForTesting
    static DisplayOption invDistWeightedInterpolate(
            DefaultDisplay.Info displayInfo, ArrayList<DisplayOption> points) {
        Point smallestSize = new Point(displayInfo.smallestSize);
        Point largestSize = new Point(displayInfo.largestSize);

        // This guarantees that width < height
        float width = Utilities.dpiFromPx(Math.min(smallestSize.x, smallestSize.y),
                displayInfo.metrics);
        float height = Utilities.dpiFromPx(Math.min(largestSize.x, largestSize.y),
                displayInfo.metrics);

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

    @VisibleForTesting
    static DisplayOption invDistWeightedInterpolate(float width, float height,
            ArrayList<DisplayOption> points) {
        float weights = 0;

        DisplayOption p = points.get(0);
        if (dist(width, height, p.minWidthDps, p.minHeightDps) == 0) {
            return p;
        }

        DisplayOption out = new DisplayOption();
        for (int i = 0; i < points.size() && i < KNEARESTNEIGHBOR; ++i) {
            p = points.get(i);
            float w = weight(width, height, p.minWidthDps, p.minHeightDps, WEIGHT_POWER);
            weights += w;
            out.add(new DisplayOption().add(p).multiply(w));
        }
        return out.multiply(1.0f / weights);
    }

    public DeviceProfile getDeviceProfile(Context context) {
        return context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE ? landscapeProfile : portraitProfile;
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

        void onIdpChanged(int changeFlags, InvariantDeviceProfile profile);
    }


    public static final class GridOption {

        public static final String TAG_NAME = "grid-option";

        public final String name;
        public final int numRows;
        public final int numColumns;

        private final int numFolderRows;
        private final int numFolderColumns;

        private final int numHotseatIcons;

        private final String dbFile;
        private final int numAllAppsColumns;

        private final int defaultLayoutId;
        private final int demoModeLayoutId;

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
            numHotseatIcons = a.getInt(
                    R.styleable.GridDisplayOption_numHotseatIcons, numColumns);
            numFolderRows = a.getInt(
                    R.styleable.GridDisplayOption_numFolderRows, numRows);
            numFolderColumns = a.getInt(
                    R.styleable.GridDisplayOption_numFolderColumns, numColumns);
            numAllAppsColumns = a.getInt(
                    R.styleable.GridDisplayOption_numAllAppsColumns, numColumns);

            a.recycle();

            extraAttrs = Themes.createValueMap(context, attrs,
                    IntArray.wrap(R.styleable.GridDisplayOption));
        }
    }

    private static final class DisplayOption {
        private final GridOption grid;

        private final float minWidthDps;
        private final float minHeightDps;
        private final boolean canBeDefault;

        private float iconSize;
        private float iconTextSize;
        private float landscapeIconSize;
        private float allAppsIconSize;
        private float allAppsIconTextSize;

        DisplayOption(GridOption grid, Context context, AttributeSet attrs) {
            this.grid = grid;

            TypedArray a = context.obtainStyledAttributes(
                    attrs, R.styleable.ProfileDisplayOption);

            minWidthDps = a.getFloat(R.styleable.ProfileDisplayOption_minWidthDps, 0);
            minHeightDps = a.getFloat(R.styleable.ProfileDisplayOption_minHeightDps, 0);
            canBeDefault = a.getBoolean(
                    R.styleable.ProfileDisplayOption_canBeDefault, false);

            iconSize = a.getFloat(R.styleable.ProfileDisplayOption_iconImageSize, 0);
            landscapeIconSize = a.getFloat(R.styleable.ProfileDisplayOption_landscapeIconSize,
                    iconSize);
            iconTextSize = a.getFloat(R.styleable.ProfileDisplayOption_iconTextSize, 0);

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
        }

        private DisplayOption multiply(float w) {
            iconSize *= w;
            landscapeIconSize *= w;
            allAppsIconSize *= w;
            iconTextSize *= w;
            allAppsIconTextSize *= w;
            return this;
        }

        private DisplayOption add(DisplayOption p) {
            iconSize += p.iconSize;
            landscapeIconSize += p.landscapeIconSize;
            allAppsIconSize += p.allAppsIconSize;
            iconTextSize += p.iconTextSize;
            allAppsIconTextSize += p.allAppsIconTextSize;
            return this;
        }
    }

    private class OverlayMonitor extends BroadcastReceiver {

        private final String ACTION_OVERLAY_CHANGED = "android.intent.action.OVERLAY_CHANGED";

        OverlayMonitor(Context context) {
            context.registerReceiver(this, getPackageFilter("android", ACTION_OVERLAY_CHANGED));
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            onConfigChanged(context);
        }
    }
}
