package ch.deletescape.lawnchair.pixelify;

import android.content.IntentFilter;

public class C0330a {
    public static IntentFilter ca(String... strArr) {
        IntentFilter intentFilter = new IntentFilter();
        for (String addAction : strArr) {
            intentFilter.addAction(addAction);
        }
        intentFilter.addDataScheme("package");
        intentFilter.addDataSchemeSpecificPart("com.google.android.googlequicksearchbox", 0);
        return intentFilter;
    }
}