package com.android.launcher3;

import android.content.ComponentName;
import android.content.Context;
import android.os.Process;

import com.android.launcher3.util.ComponentKey;
import com.patrykmichalik.opto.core.PreferenceExtensionsKt;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import app.lawnchair.preferences2.PreferenceManager2;
/**
 * Utility class to filter out components from various lists
 */
public class AppFilter {

    private final Set<ComponentName> mFilteredComponents;
    private final PreferenceManager2 pref;

    public AppFilter(Context context) {
        mFilteredComponents = Arrays.stream(
                context.getResources().getStringArray(R.array.filtered_components))
                .map(ComponentName::unflattenFromString)
                .collect(Collectors.toSet());
        pref = PreferenceManager2.getInstance (context);
    }

    public boolean shouldShowApp(ComponentName app) {
        return !mFilteredComponents.contains(app) &&
                !PreferenceExtensionsKt.firstBlocking(pref.getHiddenApps())
                        .contains(new ComponentKey(app, Process.myUserHandle()).toString ());
    }

    public boolean shouldShowAppHidden(ComponentName app) {
        return !mFilteredComponents.contains(app);
    }
}
