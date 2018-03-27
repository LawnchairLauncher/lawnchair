/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.widget;

import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.RemoteViews;

import com.android.launcher3.R;

/**
 * A widget host views created while the host has not bind to the system service.
 */
public class DeferredAppWidgetHostView extends LauncherAppWidgetHostView {

    private final TextPaint mPaint;
    private Layout mSetupTextLayout;

    public DeferredAppWidgetHostView(Context context) {
        super(context);
        setWillNotDraw(false);

        mPaint = new TextPaint();
        mPaint.setColor(Color.WHITE);
        mPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX,
                mLauncher.getDeviceProfile().getFullScreenProfile().iconTextSizePx,
                getResources().getDisplayMetrics()));
        setBackgroundResource(R.drawable.bg_deferred_app_widget);
    }

    @Override
    public void updateAppWidget(RemoteViews remoteViews) {
        // Not allowed
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        AppWidgetProviderInfo info = getAppWidgetInfo();
        if (info == null || TextUtils.isEmpty(info.label)) {
            return;
        }

        // Use double padding so that there is extra space between background and text
        int availableWidth = getMeasuredWidth() - 2 * (getPaddingLeft() + getPaddingRight());
        if (mSetupTextLayout != null && mSetupTextLayout.getText().equals(info.label)
                && mSetupTextLayout.getWidth() == availableWidth) {
            return;
        }
        mSetupTextLayout = new StaticLayout(info.label, mPaint, availableWidth,
                Layout.Alignment.ALIGN_CENTER, 1, 0, true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mSetupTextLayout != null) {
            canvas.translate(getPaddingLeft() * 2,
                    (getHeight() - mSetupTextLayout.getHeight()) / 2);
            mSetupTextLayout.draw(canvas);
        }
    }
}
