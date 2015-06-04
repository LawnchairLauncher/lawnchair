package com.android.launcher3;

import android.annotation.TargetApi;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
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

    private int mSpanX = -1;
    private int mSpanY = -1;
    private int mMinSpanX = -1;
    private int mMinSpanY = -1;

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
    }

    public LauncherAppWidgetProviderInfo(Context context, CustomAppWidget widget) {
        isCustomWidget = true;

        provider = new ComponentName(context, widget.getClass().getName());
        icon = widget.getIcon();
        label = widget.getLabel();
        previewImage = widget.getPreviewImage();
        initialLayout = widget.getWidgetLayout();
        resizeMode = widget.getResizeMode();
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

    public int getSpanX(Launcher launcher) {
        lazyLoadSpans(launcher);
        return mSpanX;
    }

    public int getSpanY(Launcher launcher) {
        lazyLoadSpans(launcher);
        return mSpanY;
    }

    public int getMinSpanX(Launcher launcher) {
        lazyLoadSpans(launcher);
        return mMinSpanX;
    }

    public int getMinSpanY(Launcher launcher) {
        lazyLoadSpans(launcher);
        return mMinSpanY;
    }

    private void lazyLoadSpans(Launcher launcher) {
        if (mSpanX < 0 || mSpanY < 0 || mMinSpanX < 0 || mMinSpanY < 0) {
            int[] minResizeSpan = launcher.getMinSpanForWidget(this);
            int[] span = launcher.getSpanForWidget(this);

            mSpanX = span[0];
            mSpanY = span[1];
            mMinSpanX = minResizeSpan[0];
            mMinSpanY = minResizeSpan[1];
        }
    }
 }
