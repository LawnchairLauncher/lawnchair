package ch.deletescape.lawnchair;

import android.content.ComponentName;
import android.content.Context;

import java.util.HashSet;
import java.util.Set;

public class StringSetAppFilter extends PreferenceAppFilter {
    private Set<ComponentName> mHiddenComponents = new HashSet<>();

    StringSetAppFilter() {
        mHiddenComponents.add(ComponentName.unflattenFromString("com.google.android.apps.wallpaper/.picker.CategoryPickerActivity"));
        mHiddenComponents.add(ComponentName.unflattenFromString("com.google.android.googlequicksearchbox/.VoiceSearchActivity"));
    }

    @Override
    public boolean shouldShowApp(ComponentName app, Context context) {
        return !mHiddenComponents.contains(app) && super.shouldShowApp(app, context);
    }
}
