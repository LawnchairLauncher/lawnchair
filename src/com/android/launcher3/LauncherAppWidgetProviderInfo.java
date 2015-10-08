package com.android.launcher3;

import android.annotation.TargetApi;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Parcel;

/**
 * This class is a thin wrapper around the framework AppWidgetProviderInfo class. This class affords
 * a common object for describing both framework provided AppWidgets as well as custom widgets
 * (who's implementation is owned by the launcher). This object represents a widget type / class,
 * as opposed to a widget instance, and so should not be confused with {@link LauncherAppWidgetInfo}
 */
public class LauncherAppWidgetProviderInfo extends AppWidgetProviderInfo {

    public boolean isCustomWidget = false;

    public int spanX;
    public int spanY;
    public int minSpanX;
    public int minSpanY;

    public static LauncherAppWidgetProviderInfo fromProviderInfo(Context context,
            AppWidgetProviderInfo info) {

        // In lieu of a public super copy constructor, we first write the AppWidgetProviderInfo
        // into a parcel, and then construct a new LauncherAppWidgetProvider info from the
        // associated super parcel constructor. This allows us to copy non-public members without
        // using reflection.
        Parcel p = Parcel.obtain();
        info.writeToParcel(p, 0);
        p.setDataPosition(0);
        LauncherAppWidgetProviderInfo lawpi = new LauncherAppWidgetProviderInfo(p);
        p.recycle();
        return lawpi;
    }

    public LauncherAppWidgetProviderInfo(Parcel in) {
        super(in);
        initSpans();
    }

    public LauncherAppWidgetProviderInfo(Context context, CustomAppWidget widget) {
        isCustomWidget = true;

        provider = new ComponentName(context, widget.getClass().getName());
        icon = widget.getIcon();
        label = widget.getLabel();
        previewImage = widget.getPreviewImage();
        initialLayout = widget.getWidgetLayout();
        resizeMode = widget.getResizeMode();
        initSpans();
    }

    public void initSpans() {
        LauncherAppState app = LauncherAppState.getInstance();
        InvariantDeviceProfile idp = app.getInvariantDeviceProfile();

        // We only care out the cell size, which is independent of the the layout direction.
        Rect paddingLand = idp.landscapeProfile.getWorkspacePadding(false /* isLayoutRtl */);
        Rect paddingPort = idp.portraitProfile.getWorkspacePadding(false /* isLayoutRtl */);

        // Always assume we're working with the smallest span to make sure we
        // reserve enough space in both orientations.
        float smallestCellWidth = DeviceProfile.calculateCellWidth(Math.min(
                idp.landscapeProfile.widthPx - paddingLand.left - paddingLand.right,
                idp.portraitProfile.widthPx - paddingPort.left - paddingPort.right),
                idp.numColumns);
        float smallestCellHeight = DeviceProfile.calculateCellWidth(Math.min(
                idp.landscapeProfile.heightPx - paddingLand.top - paddingLand.bottom,
                idp.portraitProfile.heightPx - paddingPort.top - paddingPort.bottom),
                idp.numRows);

        // We want to account for the extra amount of padding that we are adding to the widget
        // to ensure that it gets the full amount of space that it has requested.
        Rect widgetPadding = AppWidgetHostView.getDefaultPaddingForWidget(
                app.getContext(), provider, null);
        spanX = Math.max(1, (int) Math.ceil(
                        (minWidth + widgetPadding.left + widgetPadding.right) / smallestCellWidth));
        spanY = Math.max(1, (int) Math.ceil(
                (minHeight + widgetPadding.top + widgetPadding.bottom) / smallestCellHeight));

        minSpanX = Math.max(1, (int) Math.ceil(
                (minResizeWidth + widgetPadding.left + widgetPadding.right) / smallestCellWidth));
        minSpanY = Math.max(1, (int) Math.ceil(
                (minResizeHeight + widgetPadding.top + widgetPadding.bottom) / smallestCellHeight));
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public String getLabel(PackageManager packageManager) {
        if (isCustomWidget) {
            return Utilities.trim(label);
        }
        return super.loadLabel(packageManager);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public Drawable getIcon(Context context, IconCache cache) {
        if (isCustomWidget) {
            return cache.getFullResIcon(provider.getPackageName(), icon);
        }
        return super.loadIcon(context,
                LauncherAppState.getInstance().getInvariantDeviceProfile().fillResIconDpi);
    }

    public String toString(PackageManager pm) {
        if (isCustomWidget) {
            return "WidgetProviderInfo(" + provider + ")";
        }
        return String.format("WidgetProviderInfo provider:%s package:%s short:%s label:%s",
                provider.toString(), provider.getPackageName(), provider.getShortClassName(), getLabel(pm));
    }

    public Point getMinSpans(InvariantDeviceProfile idp, Context context) {
        return new Point(
                (resizeMode & RESIZE_HORIZONTAL) != 0 ? minSpanX : -1,
                        (resizeMode & RESIZE_VERTICAL) != 0 ? minSpanY : -1);
    }
 }
