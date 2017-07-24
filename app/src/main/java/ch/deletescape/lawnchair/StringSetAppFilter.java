package ch.deletescape.lawnchair;

import android.content.ComponentName;
import android.content.Context;

import java.util.ArrayList;
import java.util.List;

public class StringSetAppFilter extends PreferenceAppFilter {

    private List<String> mHiddenPackages = new ArrayList<>();

    StringSetAppFilter() {
        mHiddenPackages.add("com.google.android.apps.wallpaper");
        mHiddenPackages.add("com.google.android.googlequicksearchbox");
    }

    @Override
    public boolean shouldShowApp(ComponentName app, Context context) {
        return !mHiddenPackages.contains(app.getPackageName()) && super.shouldShowApp(app, context);
    }
}
