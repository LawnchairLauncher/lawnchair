package ch.deletescape.lawnchair;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceAppFilter implements AppFilter {

    @Override
    public boolean shouldShowApp(ComponentName app, Context context) {
        if (app.getPackageName().equals(context.getPackageName()))
            return false;
        SharedPreferences prefs = Utilities.getPrefs(context);
        return prefs.getBoolean("pref_showHidden", false) || !Utilities.isAppHidden(context, app.flattenToString());
    }
}
