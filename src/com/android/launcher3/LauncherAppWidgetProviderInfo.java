package com.android.launcher3;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Parcel;

/**
 * This class is a thin wrapper around the framework AppWidgetProviderInfo class. This class affords
 * a common object for describing both framework provided AppWidgets as well as custom widgets
 * (who's implementation is owned by the launcher). This object represents a widget type / class,
 * as opposed to a widget instance, and so should not be confused with {@link LauncherAppWidgetInfo}
 */
public class LauncherAppWidgetProviderInfo extends AppWidgetProviderInfo {

    public static final String CLS_CUSTOM_WIDGET_PREFIX = "#custom-widget-";

    public int spanX;
    public int spanY;
    public int minSpanX;
    public int minSpanY;

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
        launcherInfo.initSpans(context);
        return launcherInfo;
    }

    protected LauncherAppWidgetProviderInfo() {}

    protected LauncherAppWidgetProviderInfo(Parcel in) {
        super(in);
    }

    public void initSpans(Context context) {
        InvariantDeviceProfile idp = LauncherAppState.getIDP(context);

        Point paddingLand = idp.landscapeProfile.getTotalWorkspacePadding();
        Point paddingPort = idp.portraitProfile.getTotalWorkspacePadding();

        // Always assume we're working with the smallest span to make sure we
        // reserve enough space in both orientations.
        float smallestCellWidth = DeviceProfile.calculateCellWidth(Math.min(
                idp.landscapeProfile.widthPx - paddingLand.x,
                idp.portraitProfile.widthPx - paddingPort.x),
                idp.numColumns);
        float smallestCellHeight = DeviceProfile.calculateCellWidth(Math.min(
                idp.landscapeProfile.heightPx - paddingLand.y,
                idp.portraitProfile.heightPx - paddingPort.y),
                idp.numRows);

        // We want to account for the extra amount of padding that we are adding to the widget
        // to ensure that it gets the full amount of space that it has requested.
        Rect widgetPadding = AppWidgetHostView.getDefaultPaddingForWidget(
                context, provider, null);
        spanX = Math.max(1, (int) Math.ceil(
                        (minWidth + widgetPadding.left + widgetPadding.right) / smallestCellWidth));
        spanY = Math.max(1, (int) Math.ceil(
                (minHeight + widgetPadding.top + widgetPadding.bottom) / smallestCellHeight));

        minSpanX = Math.max(1, (int) Math.ceil(
                (minResizeWidth + widgetPadding.left + widgetPadding.right) / smallestCellWidth));
        minSpanY = Math.max(1, (int) Math.ceil(
                (minResizeHeight + widgetPadding.top + widgetPadding.bottom) / smallestCellHeight));
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
 }
