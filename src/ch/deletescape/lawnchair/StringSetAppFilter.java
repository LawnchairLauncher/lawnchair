package ch.deletescape.lawnchair;

import android.content.ComponentName;
import android.content.Context;

import java.util.Set;

@SuppressWarnings("unused")
public class StringSetAppFilter extends AppFilter {
    @Override
    public boolean shouldShowApp(ComponentName app, Context context) {
        Set<String> hiddenApps = Utilities.getPrefs(context).getStringSet("pref_hiddenApps", null);
        return hiddenApps == null || !hiddenApps.contains(app.flattenToString());
    }
}
