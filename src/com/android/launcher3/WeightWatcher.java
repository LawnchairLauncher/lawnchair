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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.launcher3.util.Thunk;

public class WeightWatcher extends LinearLayout {
    private static final int RAM_GRAPH_RSS_COLOR = 0xFF990000;
    private static final int RAM_GRAPH_PSS_COLOR = 0xFF99CC00;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int BACKGROUND_COLOR = 0xc0000000;

    private static final int UPDATE_RATE = 5000;

    private static final int MSG_START = 1;
    private static final int MSG_STOP = 2;
    private static final int MSG_UPDATE = 3;

    static int indexOf(int[] a, int x) {
        for (int i=0; i<a.length; i++) {
            if (a[i] == x) return i;
        }
        return -1;
    }

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message m) {
            switch (m.what) {
                case MSG_START:
                    mHandler.sendEmptyMessage(MSG_UPDATE);
                    break;
                case MSG_STOP:
                    mHandler.removeMessages(MSG_UPDATE);
                    break;
                case MSG_UPDATE:
                    int[] pids = mMemoryService.getTrackedProcesses();

                    final int N = getChildCount();
                    if (pids.length != N) initViews();
                    else for (int i=0; i<N; i++) {
                        ProcessWatcher pw = ((ProcessWatcher) getChildAt(i));
                        if (indexOf(pids, pw.getPid()) < 0) {
                            initViews();
                            break;
                        }
                        pw.update();
                    }
                    mHandler.sendEmptyMessageDelayed(MSG_UPDATE, UPDATE_RATE);
                    break;
            }
        }
    };
    @Thunk MemoryTracker mMemoryService;

    public WeightWatcher(Context context, AttributeSet attrs) {
        super(context, attrs);

        ServiceConnection connection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder service) {
                mMemoryService = ((MemoryTracker.MemoryTrackerInterface)service).getService();
                initViews();
            }

            public void onServiceDisconnected(ComponentName className) {
                mMemoryService = null;
            }
        };
        context.bindService(new Intent(context, MemoryTracker.class),
                connection, Context.BIND_AUTO_CREATE);

        setOrientation(LinearLayout.VERTICAL);

        setBackgroundColor(BACKGROUND_COLOR);
    }

    public void initViews() {
        removeAllViews();
        int[] processes = mMemoryService.getTrackedProcesses();
        for (int i=0; i<processes.length; i++) {
            final ProcessWatcher v = new ProcessWatcher(getContext());
            v.setPid(processes[i]);
            addView(v);
        }
    }

    public WeightWatcher(Context context) {
        this(context, null);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        mHandler.sendEmptyMessage(MSG_START);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mHandler.sendEmptyMessage(MSG_STOP);
    }

    public class ProcessWatcher extends LinearLayout {
        GraphView mRamGraph;
        TextView mText;
        int mPid;
        @Thunk MemoryTracker.ProcessMemInfo mMemInfo;

        public ProcessWatcher(Context context) {
            this(context, null);
        }

        public ProcessWatcher(Context context, AttributeSet attrs) {
            super(context, attrs);

            final float dp = getResources().getDisplayMetrics().density;

            mText = new TextView(getContext());
            mText.setTextColor(TEXT_COLOR);
            mText.setTextSize(TypedValue.COMPLEX_UNIT_PX, 10 * dp);
            mText.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);

            final int p = (int)(2*dp);
            setPadding(p, 0, p, 0);

            mRamGraph = new GraphView(getContext());

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0,
                    (int)(14 * dp),
                    1f
            );

            addView(mText, params);
            params.leftMargin = (int)(4*dp);
            params.weight = 0f;
            params.width = (int)(200 * dp);
            addView(mRamGraph, params);
        }

        public void setPid(int pid) {
            mPid = pid;
            mMemInfo = mMemoryService.getMemInfo(mPid);
            if (mMemInfo == null) {
                Log.v("WeightWatcher", "Missing info for pid " + mPid + ", removing view: " + this);
                initViews();
            }
        }

        public int getPid() {
            return mPid;
        }

        public String getUptimeString() {
            long sec = mMemInfo.getUptime() / 1000;
            StringBuilder sb = new StringBuilder();
            long days = sec / 86400;
            if (days > 0) {
                sec -= days * 86400;
                sb.append(days);
                sb.append("d");
            }

            long hours = sec / 3600;
            if (hours > 0) {
                sec -= hours * 3600;
                sb.append(hours);
                sb.append("h");
            }

            long mins = sec / 60;
            if (mins > 0) {
                sec -= mins * 60;
                sb.append(mins);
                sb.append("m");
            }

            sb.append(sec);
            sb.append("s");
            return sb.toString();
        }

        public void update() {
            //Log.v("WeightWatcher.ProcessWatcher",
            //        "MSG_UPDATE pss=" + mMemInfo.currentPss);
            mText.setText("(" + mPid
                          + (mPid == android.os.Process.myPid()
                                ? "/A"  // app
                                : "/S") // service
                          + ") up " + getUptimeString()
                          + " P=" + mMemInfo.currentPss
                          + " U=" + mMemInfo.currentUss
                          );
            mRamGraph.invalidate();
        }

        public class GraphView extends View {
            Paint pssPaint, ussPaint, headPaint;

            public GraphView(Context context, AttributeSet attrs) {
                super(context, attrs);

                pssPaint = new Paint();
                pssPaint.setColor(RAM_GRAPH_PSS_COLOR);
                ussPaint = new Paint();
                ussPaint.setColor(RAM_GRAPH_RSS_COLOR);
                headPaint = new Paint();
                headPaint.setColor(Color.WHITE);
            }

            public GraphView(Context context) {
                this(context, null);
            }

            @Override
            public void onDraw(Canvas c) {
                int w = c.getWidth();
                int h = c.getHeight();

                if (mMemInfo == null) return;

                final int N = mMemInfo.pss.length;
                final float barStep = (float) w / N;
                final float barWidth = Math.max(1, barStep);
                final float scale = (float) h / mMemInfo.max;

                int i;
                float x;
                for (i=0; i<N; i++) {
                    x = i * barStep;
                    c.drawRect(x, h - scale * mMemInfo.pss[i], x + barWidth, h, pssPaint);
                    c.drawRect(x, h - scale * mMemInfo.uss[i], x + barWidth, h, ussPaint);
                }
                x = mMemInfo.head * barStep;
                c.drawRect(x, 0, x + barWidth, h, headPaint);
            }
        }
    }
}
