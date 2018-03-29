/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.quickstep.views;

import static com.android.launcher3.LauncherState.NORMAL;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.view.View;
import android.view.ViewDebug;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;

/**
 * {@link RecentsView} used in Launcher activity
 */
@TargetApi(Build.VERSION_CODES.O)
public class LauncherRecentsView extends RecentsView<Launcher> implements Insettable {

    public static final FloatProperty<LauncherRecentsView> TRANSLATION_X_FACTOR =
            new FloatProperty<LauncherRecentsView>("translationXFactor") {

                @Override
                public void setValue(LauncherRecentsView view, float v) {
                    view.setTranslationXFactor(v);
                }

                @Override
                public Float get(LauncherRecentsView view) {
                    return view.mTranslationXFactor;
                }
            };

    public static final FloatProperty<LauncherRecentsView> TRANSLATION_Y_FACTOR =
            new FloatProperty<LauncherRecentsView>("translationYFactor") {

                @Override
                public void setValue(LauncherRecentsView view, float v) {
                    view.setTranslationYFactor(v);
                }

                @Override
                public Float get(LauncherRecentsView view) {
                    return view.mTranslationYFactor;
                }
            };

    @ViewDebug.ExportedProperty(category = "launcher")
    private float mTranslationXFactor;
    @ViewDebug.ExportedProperty(category = "launcher")
    private float mTranslationYFactor;

    private Rect mPagePadding = new Rect();

    public LauncherRecentsView(Context context) {
        this(context, null);
    }

    public LauncherRecentsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LauncherRecentsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setContentAlpha(0);
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        DeviceProfile dp = mActivity.getDeviceProfile();
        Rect padding = getPadding(dp, getContext());
        setPadding(padding.left, padding.top, padding.right, padding.bottom);
        mPagePadding.set(padding);
        mPagePadding.top += getResources().getDimensionPixelSize(R.dimen.task_thumbnail_top_margin);
    }

    @Override
    protected void onAllTasksRemoved() {
        mActivity.getStateManager().goToState(NORMAL);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        setTranslationYFactor(mTranslationYFactor);
    }

    public void setTranslationXFactor(float translationFactor) {
        mTranslationXFactor = translationFactor;
        invalidate();
    }

    public void setTranslationYFactor(float translationFactor) {
        mTranslationYFactor = translationFactor;
        setTranslationY(mTranslationYFactor * (mPagePadding.bottom - mPagePadding.top));
    }

    @Override
    public void draw(Canvas canvas) {
        maybeDrawEmptyMessage(canvas);
        int count = canvas.save();
        canvas.translate(mTranslationXFactor * (mIsRtl ? -getWidth() : getWidth()), 0);
        super.draw(canvas);
        canvas.restoreToCount(count);
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        updateEmptyMessage();
    }

    @Override
    protected void onTaskStackUpdated() {
        // Lazily update the empty message only when the task stack is reapplied
        updateEmptyMessage();
    }
}
