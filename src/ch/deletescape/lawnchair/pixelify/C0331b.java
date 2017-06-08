package ch.deletescape.lawnchair.pixelify;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import ch.deletescape.lawnchair.Launcher;
import ch.deletescape.lawnchair.Utilities;

public class C0331b {
    private final int cA;
    private final AppWidgetManager cz;

    public C0331b(Context context) {
        this.cz = AppWidgetManager.getInstance(context);
        this.cA = cd(context, this.cz);
    }

    public Bundle cb() {
        return this.cA != -1 ? this.cz.getAppWidgetOptions(this.cA) : null;
    }

    public void cc(Bundle bundle) {
        if (this.cA != -1) {
            this.cz.updateAppWidgetOptions(this.cA, bundle);
        }
    }

    private int cd(Context context, AppWidgetManager appWidgetManager) {
        Object obj = 1;
        SharedPreferences prefs = Utilities.getPrefs(context);
        int i = prefs.getInt("bundle_store_widget_id", -1);
        Object obj2 = i == -1 ? 1 : null;
        ComponentName componentName = new ComponentName(context, Launcher.class);
        if (obj2 == null) {
            AppWidgetProviderInfo appWidgetInfo = this.cz.getAppWidgetInfo(i);
            if (appWidgetInfo != null && componentName.equals(appWidgetInfo.provider)) {
                obj = null;
            }
        } else {
            obj = obj2;
        }
        if (obj == null) {
            return i;
        }
        int allocateAppWidgetId;
        AppWidgetHost appWidgetHost = new AppWidgetHost(context, 2048);
        appWidgetHost.deleteHost();
        if (componentName != null) {
            allocateAppWidgetId = appWidgetHost.allocateAppWidgetId();
            if (!this.cz.bindAppWidgetIdIfAllowed(allocateAppWidgetId, componentName)) {
                appWidgetHost.deleteAppWidgetId(allocateAppWidgetId);
                allocateAppWidgetId = -1;
            }
        } else {
            allocateAppWidgetId = -1;
        }
        prefs.edit().putInt("bundle_store_widget_id", allocateAppWidgetId).apply();
        return allocateAppWidgetId;
    }
}