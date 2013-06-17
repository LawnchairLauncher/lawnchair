package com.android.launcher3;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class MemoryDumpActivity extends Activity {
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public static void dumpHprofAndShare(final Context context) {
        try {
            final String path = String.format("%s/launcher-memory-%d.ahprof",
                    Environment.getExternalStorageDirectory(),
                    System.currentTimeMillis());
            Log.v(Launcher.TAG, "Dumping memory info to " + path);

            android.os.Debug.dumpHprofData(path); // will block

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/vnd.android.bugreport");

            final long pss = Debug.getPss();
            final PackageManager pm = context.getPackageManager();
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, String.format("Launcher memory dump (PSS=%d)", pss));
            String appVersion;
            try {
                appVersion = pm.getPackageInfo(context.getPackageName(), 0).versionName;
            } catch (PackageManager.NameNotFoundException e) {
                appVersion = "?";
            }
            shareIntent.putExtra(Intent.EXTRA_TEXT, String.format("App version: %s\nBuild: %s",
                    appVersion, Build.DISPLAY));
            shareIntent.setType("application/vnd.android.hprof");

            //shareIntent.putExtra(Intent.EXTRA_TEXT, android.os.SystemProperties.get("ro.build.description"));

            final File pathFile = new File(path);
            final Uri pathUri = Uri.fromFile(pathFile);

            shareIntent.putExtra(Intent.EXTRA_STREAM, pathUri);
            context.startActivity(shareIntent);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        dumpHprofAndShare(this);
        finish();
    }
}