package ch.deletescape.lawnchair;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.Set;

@SuppressWarnings("unused")
public class StringSetAppFilter extends AppFilter {
    @Override
    public boolean shouldShowApp(ComponentName app, Context context) {
        SharedPreferences prefs = Utilities.getPrefs(context);
        Set<String> hiddenApps = prefs.getStringSet("pref_hiddenApps", null);
        return prefs.getBoolean("pref_showHidden", false) || hiddenApps == null || !hiddenApps.contains(app.flattenToString());
    }
}
