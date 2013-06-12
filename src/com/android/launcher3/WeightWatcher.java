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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Debug;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public class WeightWatcher extends LinearLayout {
    private static final long UPDATE_RATE = 5000;

    private static final int RAM_GRAPH_COLOR = 0x9099CC00;
    private static final int TEXT_COLOR = 0x90FFFFFF;
    private static final int BACKGROUND_COLOR = 0x40000000;

    private static final int MSG_START = 1;
    private static final int MSG_STOP = 2;
    private static final int MSG_UPDATE = 3;

    TextView mRamText;
    GraphView mRamGraph;
    TextView mUptimeText;

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
                    update();
                    mHandler.sendEmptyMessageDelayed(MSG_UPDATE, UPDATE_RATE);
                    break;
            }
        }
    };

    public WeightWatcher(Context context, AttributeSet attrs) {
        super(context, attrs);

        final float dp = getResources().getDisplayMetrics().density;

        setBackgroundColor(BACKGROUND_COLOR);

        mRamText = new TextView(getContext());
        mUptimeText = new TextView(getContext());
        mRamText.setTextColor(TEXT_COLOR);
        mUptimeText.setTextColor(TEXT_COLOR);

        final int p = (int)(4*dp);
        setPadding(p, 0, p, 0);

        mRamGraph = new GraphView(getContext());

        LinearLayout.LayoutParams wrapParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        wrapParams.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
        wrapParams.setMarginEnd((int)(8*dp));

        LinearLayout.LayoutParams fillParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1.0f
        );

        addView(mUptimeText, wrapParams);
        addView(mRamText, wrapParams);
        addView(mRamGraph, fillParams);
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
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mRamText.setTextSize(h * 0.25f);
        mUptimeText.setTextSize(h * 0.25f);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mHandler.sendEmptyMessage(MSG_STOP);
    }

    public String getUptimeString() {
        long sec = LauncherAppState.getInstance().getUptime() / 1000;
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

    void update() {
        final long pss = Debug.getPss();

        mRamGraph.add(pss);
        mRamText.setText("pss=" + pss);
        mUptimeText.setText("uptime=" + getUptimeString());

        postInvalidate();
    }

    public static class GraphView extends View {
        final long[] data = new long[256];
        long max = 1;
        int head = 0;

        Paint paint;

        public GraphView(Context context, AttributeSet attrs) {
            super(context, attrs);

            paint = new Paint();
            paint.setColor(RAM_GRAPH_COLOR);
        }

        public GraphView(Context context) {
            this(context, null);
        }

        public void add(long dat) {
            head = (head+1) % data.length;
            data[head] = dat;
            if (dat > max) max = dat;
            invalidate();
        }

        @Override
        public void onDraw(Canvas c) {
            int w = c.getWidth();
            int h = c.getHeight();

            final float barWidth = (float) w / data.length;
            final float scale = (float) h / max;

            for (int i=0; i<data.length; i++) {
                c.drawRect(i * barWidth, h - scale * data[i], (i+1) * barWidth, h, paint);
            }
        }
    }
}
