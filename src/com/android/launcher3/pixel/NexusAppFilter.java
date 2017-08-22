package com.android.launcher3.pixel;

import android.content.ComponentName;
import java.util.HashSet;
import com.android.launcher3.AppFilter;

public class NexusAppFilter extends AppFilter {
    private final HashSet mHide;

    public NexusAppFilter() {
        mHide = new HashSet();
        mHide.add(ComponentName.unflattenFromString("com.google.android.googlequicksearchbox/.VoiceSearchActivity"));
        mHide.add(ComponentName.unflattenFromString("com.google.android.apps.wallpaper/.picker.CategoryPickerActivity"));
        mHide.add(ComponentName.unflattenFromString("com.google.android.launcher/com.google.android.launcher.StubApp"));
    }

    public boolean shouldShowApp(final ComponentName componentName) {
        return !mHide.contains(componentName);
    }
}