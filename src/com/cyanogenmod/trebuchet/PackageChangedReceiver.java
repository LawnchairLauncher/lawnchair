package com.cyanogenmod.trebuchet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class PackageChangedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        final String packageName = intent.getData().getSchemeSpecificPart();

        if (packageName == null || packageName.length() == 0) {
            // they sent us a bad intent
            return;
        }
        WidgetPreviewLoader.removeFromDb(context, packageName);
    }
}
