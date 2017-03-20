package ch.deletescape.lawnchair;

import android.content.ComponentName;
import android.content.Context;

@SuppressWarnings("unused")
public class RegexAppFilter extends AppFilter {
    @Override
    public boolean shouldShowApp(ComponentName app, Context context) {
        String regex = Utilities.getPrefs(context).getString("pref_packageNameRegex", null);
        return regex == null || app.getPackageName().matches(regex);
    }
}
