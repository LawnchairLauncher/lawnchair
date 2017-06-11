package com.android.launcher3;

import android.content.IntentFilter;

public class SuperUtil {

    public static IntentFilter createIntentFilter(String... Actions) {
        IntentFilter intentFilter = new IntentFilter();
        for (String action : Actions) {
            intentFilter.addAction(action);
        }
        intentFilter.addDataScheme("package");
        intentFilter.addDataSchemeSpecificPart("com.google.android.googlequicksearchbox", 0);
        return intentFilter;
    }
}