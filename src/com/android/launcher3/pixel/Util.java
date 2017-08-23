package com.android.launcher3.pixel;

import android.content.IntentFilter;

public class Util {
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
