package com.android.launcher3.widget;

import static com.android.launcher3.Utilities.ATLEAST_S;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.UserHandle;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.Utilities;
import com.android.launcher3.icons.ComponentWithLabelAndIcon;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;

/**
 * This class is a thin wrapper around the framework AppWidgetProviderInfo class. This class affords
 * a common object for describing both framework provided AppWidgets as well as custom widgets
 * (who's implementation is owned by the launcher). This object represents a widget type / class,
 * as opposed to a widget instance, and so should not be confused with {@link LauncherAppWidgetInfo}
 */
public class LauncherAppWidgetProviderInfo extends AppWidgetProviderInfo
        implements ComponentWithLabelAndIcon {

    public static final String CLS_CUSTOM_WIDGET_PREFIX = "#custom-widget-";

    /**
     * The desired number of cells that this widget occupies horizontally in
     * {@link com.android.launcher3.CellLayout}.
     */
    public int spanX;

    /**
     * The desired number of cells that this widget occupies vertically in
     * {@link com.android.launcher3.CellLayout}.
     */
    public int spanY;

    /**
     * The minimum number of cells that this widget can occupy horizontally in
     * {@link com.android.launcher3.CellLayout}.
     */
    public int minSpanX;

    /**
     * The minimum number of cells that this widget can occupy vertically in
     * {@link com.android.launcher3.CellLayout}.
     */
    public int minSpanY;

    /**
     * The maximum number of cells that this widget can occupy horizontally in
     * {@link com.android.launcher3.CellLayout}.
     */
    public int maxSpanX;

    /**
     * The maximum number of cells that this widget can occupy vertically in
     * {@link com.android.launcher3.CellLayout}.
     */
    public int maxSpanY;

    protected boolean mIsMinSizeFulfilled;

    public static LauncherAppWidgetProviderInfo fromProviderInfo(Context context,
            AppWidgetProviderInfo info) {
        final LauncherAppWidgetProviderInfo launcherInfo;
        if (info instanceof LauncherAppWidgetProviderInfo) {
            launcherInfo = (LauncherAppWidgetProviderInfo) info;
        } else {

            // In lieu of a public super copy constructor, we first write the AppWidgetProviderInfo
            // into a parcel, and then construct a new LauncherAppWidgetProvider info from the
            // associated super parcel constructor. This allows us to copy non-public members without
            // using reflection.
            Parcel p = Parcel.obtain();
            info.writeToParcel(p, 0);
            p.setDataPosition(0);
            launcherInfo = new LauncherAppWidgetProviderInfo(p);
            p.recycle();
        }
        launcherInfo.initSpans(context, LauncherAppState.getIDP(context));
        return launcherInfo;
    }

    protected LauncherAppWidgetProviderInfo() {}

    protected LauncherAppWidgetProviderInfo(Parcel in) {
        super(in);
    }

    public void initSpans(Context context, InvariantDeviceProfile idp) {
        int minSpanX = 0;
        int minSpanY = 0;
        int maxSpanX = idp.numColumns;
        int maxSpanY = idp.numRows;
        int spanX = 0;
        int spanY = 0;


        Point cellSize = new Point();
        for (DeviceProfile dp : idp.supportedProfiles) {
            dp.getCellSize(cellSize);
            Rect widgetPadding = dp.widgetPadding;

            minSpanX = Math.max(minSpanX,
                    getSpanX(widgetPadding, minResizeWidth, dp.cellLayoutBorderSpacePx.x,
                            cellSize.x));
            minSpanY = Math.max(minSpanY,
                    getSpanY(widgetPadding, minResizeHeight, dp.cellLayoutBorderSpacePx.y,
                            cellSize.y));

            if (ATLEAST_S) {
                if (maxResizeWidth > 0) {
                    maxSpanX = Math.min(maxSpanX, getSpanX(widgetPadding, maxResizeWidth,
                            dp.cellLayoutBorderSpacePx.x, cellSize.x));
                }
                if (maxResizeHeight > 0) {
                    maxSpanY = Math.min(maxSpanY, getSpanY(widgetPadding, maxResizeHeight,
                            dp.cellLayoutBorderSpacePx.y, cellSize.y));
                }
            }

            spanX = Math.max(spanX,
                    getSpanX(widgetPadding, minWidth, dp.cellLayoutBorderSpacePx.x,
                            cellSize.x));
            spanY = Math.max(spanY,
                    getSpanY(widgetPadding, minHeight, dp.cellLayoutBorderSpacePx.y,
                            cellSize.y));
        }

        if (ATLEAST_S) {
            // Ensures maxSpan >= minSpan
            maxSpanX = Math.max(maxSpanX, minSpanX);
            maxSpanY = Math.max(maxSpanY, minSpanY);

            // Use targetCellWidth/Height if it is within the min/max ranges.
            // Otherwise, use the span of minWidth/Height.
            if (targetCellWidth >= minSpanX && targetCellWidth <= maxSpanX
                    && targetCellHeight >= minSpanY && targetCellHeight <= maxSpanY) {
                spanX = targetCellWidth;
                spanY = targetCellHeight;
            }
        }

        // If minSpanX/Y > spanX/Y, ignore the minSpanX/Y to match the behavior described in
        // minResizeWidth & minResizeHeight Android documentation. See
        // https://developer.android.com/reference/android/appwidget/AppWidgetProviderInfo
        this.minSpanX = Math.min(spanX, minSpanX);
        this.minSpanY = Math.min(spanY, minSpanY);
        this.maxSpanX = maxSpanX;
        this.maxSpanY = maxSpanY;
        this.mIsMinSizeFulfilled = Math.min(spanX, minSpanX) <= idp.numColumns
            && Math.min(spanY, minSpanY) <= idp.numRows;
        // Ensures the default span X and span Y will not exceed the current grid size.
        this.spanX = Math.min(spanX, idp.numColumns);
        this.spanY = Math.min(spanY, idp.numRows);
    }

    /**
     * Returns {@code true} if the widget's minimum size requirement can be fulfilled in the device
     * grid setting, {@link InvariantDeviceProfile}, that was passed in
     * {@link #initSpans(Context, InvariantDeviceProfile)}.
     */
    public boolean isMinSizeFulfilled() {
        return mIsMinSizeFulfilled;
    }

    private int getSpanX(Rect widgetPadding, int widgetWidth, int cellSpacing, float cellWidth) {
        return getSpan(widgetPadding.left + widgetPadding.right,
                widgetWidth, cellSpacing, cellWidth);
    }

    private int getSpanY(Rect widgetPadding, int widgetHeight, int cellSpacing, float cellHeight) {
        return getSpan(widgetPadding.top + widgetPadding.bottom, widgetHeight,
                cellSpacing, cellHeight);
    }

    /**
     * Solving the equation:
     *   n * cellSize + (n - 1) * cellSpacing - widgetPadding = widgetSize
     */
    private int getSpan(int widgetPadding, int widgetSize, int cellSpacing, float cellSize) {
        return Math.max(1, (int) Math.ceil(
                (widgetSize + widgetPadding + cellSpacing) / (cellSize + cellSpacing)));
    }

    public String getLabel(PackageManager packageManager) {
        return super.loadLabel(packageManager);
    }

    public Point getMinSpans() {
        return new Point((resizeMode & RESIZE_HORIZONTAL) != 0 ? minSpanX : -1,
                (resizeMode & RESIZE_VERTICAL) != 0 ? minSpanY : -1);
    }

    public boolean isCustomWidget() {
        return provider.getClassName().startsWith(CLS_CUSTOM_WIDGET_PREFIX);
    }

    public int getWidgetFeatures() {
        if (Utilities.ATLEAST_P) {
            return widgetFeatures;
        } else {
            return 0;
        }
    }

    public boolean isReconfigurable() {
        return configure != null && (getWidgetFeatures() & WIDGET_FEATURE_RECONFIGURABLE) != 0;
    }

    public boolean isConfigurationOptional() {
        return ATLEAST_S
                && isReconfigurable()
                && (getWidgetFeatures() & WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0;
    }

    @Override
    public final ComponentName getComponent() {
        return provider;
    }

    @Override
    public final UserHandle getUser() {
        return getProfile();
    }

    @Override
    public Drawable getFullResIcon(IconCache cache) {
        return cache.getFullResIcon(provider.getPackageName(), icon);
    }
}