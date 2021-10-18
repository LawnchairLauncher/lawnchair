/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.taskbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * View that handles scrimming the taskbar and the inverted corners it draws. The scrim is used
 * when bubbles is expanded.
 */
public class TaskbarScrimView extends View {
    private final Paint mTaskbarScrimPaint;
    private final Path mInvertedLeftCornerPath, mInvertedRightCornerPath;

    private boolean mShowScrim;
    private float mLeftCornerRadius, mRightCornerRadius;
    private float mBackgroundHeight;

    public TaskbarScrimView(Context context) {
        this(context, null);
    }

    public TaskbarScrimView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskbarScrimView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TaskbarScrimView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        mTaskbarScrimPaint = new Paint();
        mTaskbarScrimPaint.setColor(getResources().getColor(android.R.color.system_neutral1_1000));
        mTaskbarScrimPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        mTaskbarScrimPaint.setStyle(Paint.Style.FILL);

        mInvertedLeftCornerPath = new Path();
        mInvertedRightCornerPath = new Path();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mShowScrim) {
            canvas.save();
            canvas.translate(0, canvas.getHeight() - mBackgroundHeight);

            // Scrim the taskbar itself.
            canvas.drawRect(0, 0, canvas.getWidth(), mBackgroundHeight, mTaskbarScrimPaint);

            // Scrim the inverted rounded corners above the taskbar.
            canvas.translate(0, -mLeftCornerRadius);
            canvas.drawPath(mInvertedLeftCornerPath, mTaskbarScrimPaint);
            canvas.translate(0, mLeftCornerRadius);
            canvas.translate(canvas.getWidth() - mRightCornerRadius, -mRightCornerRadius);
            canvas.drawPath(mInvertedRightCornerPath, mTaskbarScrimPaint);

            canvas.restore();
        }
    }

    /**
     * Sets the height of the taskbar background.
     * @param height the height of the background.
     */
    protected void setBackgroundHeight(float height) {
        mBackgroundHeight = height;
        if (mShowScrim) {
            invalidate();
        }
    }

    /**
     * Sets the alpha of the taskbar scrim.
     * @param alpha the alpha of the scrim.
     */
    protected void setScrimAlpha(float alpha) {
        mShowScrim = alpha > 0f;
        mTaskbarScrimPaint.setAlpha((int) (alpha * 255));
        invalidate();
    }

    /**
     * Sets the radius of the left and right corners above the taskbar.
     * @param leftCornerRadius the radius of the left corner.
     * @param rightCornerRadius the radius of the right corner.
     */
    protected void setCornerSizes(float leftCornerRadius, float rightCornerRadius) {
        mLeftCornerRadius = leftCornerRadius;
        mRightCornerRadius = rightCornerRadius;

        Path square = new Path();
        square.addRect(0, 0, mLeftCornerRadius, mLeftCornerRadius, Path.Direction.CW);
        Path circle = new Path();
        circle.addCircle(mLeftCornerRadius, 0, mLeftCornerRadius, Path.Direction.CW);
        mInvertedLeftCornerPath.op(square, circle, Path.Op.DIFFERENCE);
        square.reset();
        square.addRect(0, 0, mRightCornerRadius, mRightCornerRadius, Path.Direction.CW);
        circle.reset();
        circle.addCircle(0, 0, mRightCornerRadius, Path.Direction.CW);
        mInvertedRightCornerPath.op(square, circle, Path.Op.DIFFERENCE);

        if (mShowScrim) {
            invalidate();
        }
    }
}
