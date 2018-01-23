package com.google.android.apps.nexuslauncher.utils;

import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;

public class ActionIntentFilter {
    public static IntentFilter googleInstance(String... array) {
        return newInstance("com.google.android.googlequicksearchbox", array);
    }
    
    public static IntentFilter newInstance(String s, String... array) {
        IntentFilter intentFilter = new IntentFilter();
        for (int length = array.length, i = 0; i < length; ++i) {
            intentFilter.addAction(array[i]);
        }
        intentFilter.addDataScheme("package");
        intentFilter.addDataSchemeSpecificPart(s, 0);
        return intentFilter;
    }
    
    public static boolean googleEnabled(final Context context) {
        try {
            return context.getPackageManager().getApplicationInfo("com.google.android.googlequicksearchbox", 0).enabled;
        } catch (PackageManager.NameNotFoundException ex) {
            return false;
        }
    }
}
