package com.android.launcher3;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Parcel;

/**
 * This class is a thin wrapper around the framework AppWidgetProviderInfo class. This class affords
 * a common object for describing both framework provided AppWidgets as well as custom widgets
 * (who's implementation is owned by the launcher). This object represents a widget type / class,
 * as opposed to a widget instance, and so should not be confused with {@link LauncherAppWidgetInfo}
 */
public class LauncherAppWidgetProviderInfo extends AppWidgetProviderInfo {

    public boolean isCustomWidget = false;
    public int spanX = -1;
    public int spanY = -1;
    public int minSpanX = -1;
    public int minSpanY = -1;

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

        int[] minResizeSpan = Launcher.getMinSpanForWidget(context, lawpi);
        int[] span = Launcher.getSpanForWidget(context, lawpi);

        lawpi.spanX = span[0];
        lawpi.spanY = span[1];
        lawpi.minSpanX = minResizeSpan[0];
        lawpi.minSpanY = minResizeSpan[1];

        return lawpi;
    }

    public LauncherAppWidgetProviderInfo(Parcel in) {
        super(in);
    }

    public LauncherAppWidgetProviderInfo(Context context, CustomAppWidget widget) {
        isCustomWidget = true;

        provider = new ComponentName(context, widget.getClass().getName());
        icon = widget.getIcon();
        label = widget.getLabel();
        previewImage = widget.getPreviewImage();
        initialLayout = widget.getWidgetLayout();
        resizeMode = widget.getResizeMode();

        spanX = widget.getSpanX();
        spanY = widget.getSpanY();
        minSpanX = widget.getMinSpanX();
        minSpanY = widget.getMinSpanY();
    }

    public String getLabel(PackageManager packageManager) {
        if (isCustomWidget) {
            return Utilities.trim(label);
        }
        return super.loadLabel(packageManager);
    }

    public Drawable getIcon(Context context, IconCache cache) {
        if (isCustomWidget) {
            return cache.getFullResIcon(provider.getPackageName(), icon);
        }
        return super.loadIcon(context, cache.getFullResIconDpi());
    }

    public String toString(PackageManager pm) {
        if (isCustomWidget) {
            return "WidgetProviderInfo(" + provider + ")";
        }
        return String.format("WidgetProviderInfo provider:%s package:%s short:%s label:%s span(%d, %d) minSpan(%d, %d)",
                provider.toString(), provider.getPackageName(), provider.getShortClassName(), getLabel(pm), spanX, spanY, minSpanX, minSpanY);
    }
 }
