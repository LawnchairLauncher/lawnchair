package com.google.android.apps.nexuslauncher;

import android.content.ComponentName;
import android.content.Context;

import com.android.launcher3.AppFilter;

import java.util.HashSet;

public class NexusAppFilter extends AppFilter {
    private final HashSet<ComponentName> mHideList = new HashSet<>();

    public NexusAppFilter(Context context) {
        //Voice Search
        mHideList.add(ComponentName.unflattenFromString("com.google.android.googlequicksearchbox/.VoiceSearchActivity"));

        //Wallpapers
        mHideList.add(ComponentName.unflattenFromString("com.google.android.apps.wallpaper/.picker.CategoryPickerActivity"));

        //Google Now Launcher
        mHideList.add(ComponentName.unflattenFromString("com.google.android.launcher/com.google.android.launcher.StubApp"));
    }

    @Override
    public boolean shouldShowApp(ComponentName componentName) {
        return !mHideList.contains(componentName);
    }
}
