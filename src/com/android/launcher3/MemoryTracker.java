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

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.*;
import android.util.Log;
import android.util.LongSparseArray;

import java.util.ArrayList;

public class MemoryTracker extends Service {
    public static final String TAG = MemoryTracker.class.getSimpleName();
    public static final String ACTION_START_TRACKING = "com.android.launcher3.action.START_TRACKING";

    private static final long UPDATE_RATE = 5000;

    private static final int MSG_START = 1;
    private static final int MSG_STOP = 2;
    private static final int MSG_UPDATE = 3;

    public static class ProcessMemInfo {
        public int pid;
        public String name;
        public long currentPss, currentUss;
        public long[] pss = new long[256];
        public long[] uss = new long[256];
            //= new Meminfo[(int) (30 * 60 / (UPDATE_RATE / 1000))]; // 30 minutes
        public long max = 1;
        public int head = 0;
        public ProcessMemInfo(int pid, String name) {
            this.pid = pid;
            this.name = name;
        }
    };
    public final LongSparseArray<ProcessMemInfo> mData = new LongSparseArray<ProcessMemInfo>();
    public final ArrayList<Long> mPids = new ArrayList<Long>();
    private int[] mPidsArray = new int[0];

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message m) {
            switch (m.what) {
                case MSG_START:
                    mHandler.removeMessages(MSG_UPDATE);
                    mHandler.sendEmptyMessage(MSG_UPDATE);
                    break;
                case MSG_STOP:
                    mHandler.removeMessages(MSG_UPDATE);
                    break;
                case MSG_UPDATE:
                    update();
                    mHandler.removeMessages(MSG_UPDATE);
                    mHandler.sendEmptyMessageDelayed(MSG_UPDATE, UPDATE_RATE);
                    break;
            }
        }
    };

    ActivityManager mAm;

    public ProcessMemInfo getMemInfo(int pid) {
        return mData.get(pid);
    }

    public int[] getTrackedProcesses() {
        return mPidsArray;
    }

    public void startTrackingProcess(int pid, String name) {
        mPids.add(new Long(pid));
        final int N = mPids.size();
        mPidsArray = new int[N];
        StringBuffer sb = new StringBuffer("Now tracking processes: ");
        for (int i=0; i<N; i++) {
            final int p = mPids.get(i).intValue();
            mPidsArray[i] = p;
            sb.append(p); sb.append(" ");
        }
        mData.put(pid, new ProcessMemInfo(pid, name));
        Log.v(TAG, sb.toString());
    }

    void update() {
        Debug.MemoryInfo[] dinfos = mAm.getProcessMemoryInfo(mPidsArray);
        for (int i=0; i<dinfos.length; i++) {
            Debug.MemoryInfo dinfo = dinfos[i];
            final long pid = mPids.get(i).intValue();
            final ProcessMemInfo info = mData.get(pid);
            info.head = (info.head+1) % info.pss.length;
            info.pss[info.head] = info.currentPss = dinfo.getTotalPss();
            info.uss[info.head] = info.currentUss = dinfo.getTotalPrivateDirty();
            if (info.currentPss > info.max) info.max = info.currentPss;
            if (info.currentUss > info.max) info.max = info.currentUss;
            //Log.v(TAG, "update: pid " + pid + " pss=" + info.currentPss + " uss=" + info.currentUss);
        }

        // XXX: notify listeners
    }

    @Override
    public void onCreate() {
        mAm = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
    }

    @Override
    public void onDestroy() {
        mHandler.sendEmptyMessage(MSG_STOP);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + ": " + intent);

        if (intent != null) {
            if (ACTION_START_TRACKING.equals(intent.getAction())) {
                final int pid = intent.getIntExtra("pid", -1);
                final String name = intent.getStringExtra("name");
                startTrackingProcess(pid, name);
            }
        }

        mHandler.sendEmptyMessage(MSG_START);

        return START_STICKY;
    }

    public class MemoryTrackerInterface extends Binder {
        MemoryTracker getService() {
            return MemoryTracker.this;
        }
    }

    private final IBinder mBinder = new MemoryTrackerInterface();

    public IBinder onBind(Intent intent) {
        mHandler.sendEmptyMessage(MSG_START);

        return mBinder;
    }
}
