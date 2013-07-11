/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.util.Log;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MemoryDumpActivity extends Activity {
    private static final String TAG = "MemoryDumpActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public static String zipUp(ArrayList<String> paths) {
        final int BUFSIZ = 256 * 1024; // 256K
        final byte[] buf = new byte[BUFSIZ];
        final String zipfilePath = String.format("%s/hprof-%d.zip",
                Environment.getExternalStorageDirectory(),
                System.currentTimeMillis());
        ZipOutputStream zos = null;
        try {
            OutputStream os = new FileOutputStream(zipfilePath);
            zos = new ZipOutputStream(new BufferedOutputStream(os));
            for (String filename : paths) {
                InputStream is = null;
                try {
                    is = new BufferedInputStream(new FileInputStream(filename));
                    ZipEntry entry = new ZipEntry(filename);
                    zos.putNextEntry(entry);
                    int len;
                    while ( 0 < (len = is.read(buf, 0, BUFSIZ)) ) {
                        zos.write(buf, 0, len);
                    }
                    zos.closeEntry();
                } finally {
                    is.close();
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "error zipping up profile data", e);
            return null;
        } finally {
            if (zos != null) {
                try {
                    zos.close();
                } catch (IOException e) {
                    // ugh, whatever
                }
            }
        }
        return zipfilePath;
    }

    public static void dumpHprofAndShare(final Context context, MemoryTracker tracker) {
        final StringBuilder body = new StringBuilder();

        final ArrayList<String> paths = new ArrayList<String>();
        final int myPid = android.os.Process.myPid();

        final int[] pids_orig = tracker.getTrackedProcesses();
        final int[] pids_copy = Arrays.copyOf(pids_orig, pids_orig.length);
        for (int pid : pids_copy) {
            MemoryTracker.ProcessMemInfo info = tracker.getMemInfo(pid);
            if (info != null) {
                body.append("pid ").append(pid).append(":")
                    .append(" up=").append(info.getUptime())
                    .append(" pss=").append(info.currentPss)
                    .append(" uss=").append(info.currentUss)
                    .append("\n");
            }
            if (pid == myPid) {
                final String path = String.format("%s/launcher-memory-%d.ahprof",
                        Environment.getExternalStorageDirectory(),
                        pid);
                Log.v(TAG, "Dumping memory info for process " + pid + " to " + path);
                try {
                    android.os.Debug.dumpHprofData(path); // will block
                } catch (IOException e) {
                    Log.e(TAG, "error dumping memory:", e);
                }
                paths.add(path);
            }
        }

        String zipfile = zipUp(paths);

        if (zipfile == null) return;

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/zip");

        final PackageManager pm = context.getPackageManager();
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, String.format("Launcher memory dump (%d)", myPid));
        String appVersion;
        try {
            appVersion = pm.getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            appVersion = "?";
        }

        body.append("\nApp version: ").append(appVersion).append("\nBuild: ").append(Build.DISPLAY).append("\n");
        shareIntent.putExtra(Intent.EXTRA_TEXT, body.toString());

        final File pathFile = new File(zipfile);
        final Uri pathUri = Uri.fromFile(pathFile);

        shareIntent.putExtra(Intent.EXTRA_STREAM, pathUri);
        context.startActivity(shareIntent);
    }

    @Override
    public void onStart() {
        super.onStart();

        startDump(this, new Runnable() {
            @Override
            public void run() {
                finish();
            }
        });
    }

    public static void startDump(final Context context) {
        startDump(context, null);
    }

    public static void startDump(final Context context, final Runnable andThen) {
        final ServiceConnection connection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder service) {
                Log.v(TAG, "service connected, dumping...");
                dumpHprofAndShare(context,
                        ((MemoryTracker.MemoryTrackerInterface) service).getService());
                context.unbindService(this);
                if (andThen != null) andThen.run();
            }

            public void onServiceDisconnected(ComponentName className) {
            }
        };
        Log.v(TAG, "attempting to bind to memory tracker");
        context.bindService(new Intent(context, MemoryTracker.class),
                connection, Context.BIND_AUTO_CREATE);
    }
}
