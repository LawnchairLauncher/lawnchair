package com.android.launcher3.widget;

import static com.android.launcher3.Utilities.ATLEAST_S;

import android.appwidget.AppWidgetHostView;
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

    public int spanX;
    public int spanY;
    public int minSpanX;
    public int minSpanY;
    public int maxSpanX;
    public int maxSpanY;

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
        // Always assume we're working with the smallest span to make sure we
        // reserve enough space in both orientations.
        float smallestCellWidth = Float.MAX_VALUE;
        float smallestCellHeight = Float.MAX_VALUE;

        Point cellSize = new Point();
        boolean isWidgetPadded = false;
        for (DeviceProfile dp : idp.supportedProfiles) {
            dp.getCellSize(cellSize);
            smallestCellWidth = Math.min(smallestCellWidth, cellSize.x);
            smallestCellHeight = Math.min(smallestCellHeight, cellSize.y);
            isWidgetPadded = isWidgetPadded || !dp.shouldInsetWidgets();
        }

        // We want to account for the extra amount of padding that we are adding to the widget
        // to ensure that it gets the full amount of space that it has requested.
        // If grids supports insetting widgets, we do not account for widget padding.
        Rect widgetPadding = new Rect();
        if (isWidgetPadded) {
            AppWidgetHostView.getDefaultPaddingForWidget(context, provider, widgetPadding);
        }

        minSpanX = getSpanX(widgetPadding, minResizeWidth, smallestCellWidth);
        minSpanY = getSpanY(widgetPadding, minResizeHeight, smallestCellHeight);

        // Use maxResizeWidth/Height if they are defined and we're on S or above.
        maxSpanX =
                (ATLEAST_S && maxResizeWidth > 0)
                        ? getSpanX(widgetPadding, maxResizeWidth, smallestCellWidth)
                        : idp.numColumns;
        maxSpanY =
                (ATLEAST_S && maxResizeHeight > 0)
                        ? getSpanY(widgetPadding, maxResizeHeight, smallestCellHeight)
                        : idp.numRows;

        // Use targetCellWidth/Height if it is within the min/max ranges and we're on S or above.
        // Otherwise, fall back to minWidth/Height.
        if (ATLEAST_S && targetCellWidth >= minSpanX && targetCellWidth <= maxSpanX
                && targetCellHeight >= minSpanY && targetCellHeight <= maxSpanY) {
            spanX = targetCellWidth;
            spanY = targetCellHeight;
        } else {
            spanX = getSpanX(widgetPadding, minWidth, smallestCellWidth);
            spanY = getSpanY(widgetPadding, minHeight, smallestCellHeight);
        }
    }

    private int getSpanX(Rect widgetPadding, int widgetWidth, float cellWidth) {
        return Math.max(1, (int) Math.ceil(
                (widgetWidth + widgetPadding.left + widgetPadding.right) / cellWidth));
    }

    private int getSpanY(Rect widgetPadding, int widgetHeight, float cellHeight) {
        return Math.max(1, (int) Math.ceil(
                (widgetHeight + widgetPadding.top + widgetPadding.bottom) / cellHeight));
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