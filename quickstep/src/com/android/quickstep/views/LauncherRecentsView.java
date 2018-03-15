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
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.widget.FrameLayout;

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

    private Bitmap mScrim;
    private Paint mFadePaint;
    private Shader mFadeShader;
    private Matrix mFadeMatrix;
    private boolean mScrimOnLeft;

    private float mTranslationXFactor;
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
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        DeviceProfile dp = mActivity.getDeviceProfile();
        Rect padding = getPadding(dp, getContext());
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        lp.bottomMargin = padding.bottom;
        setLayoutParams(lp);

        setPadding(padding.left, padding.top, padding.right, 0);
        mPagePadding.set(padding);
        mPagePadding.top += getResources().getDimensionPixelSize(R.dimen.task_thumbnail_top_margin);

        if (dp.isVerticalBarLayout()) {
            boolean wasScrimOnLeft = mScrimOnLeft;
            mScrimOnLeft = dp.isSeascape();

            if (mScrim == null || wasScrimOnLeft != mScrimOnLeft) {
                Drawable scrim = getContext().getDrawable(mScrimOnLeft
                        ? R.drawable.recents_horizontal_scrim_left
                        : R.drawable.recents_horizontal_scrim_right);
                if (scrim instanceof BitmapDrawable) {
                    mScrim = ((BitmapDrawable) scrim).getBitmap();
                    mFadePaint = new Paint();
                    mFadePaint.setXfermode(new PorterDuffXfermode(Mode.DST_IN));
                    mFadeShader = new BitmapShader(mScrim, TileMode.CLAMP, TileMode.REPEAT);
                    mFadeMatrix = new Matrix();
                } else {
                    mScrim = null;
                }
            }
        } else {
            mScrim = null;
            mFadePaint = null;
            mFadeShader = null;
            mFadeMatrix = null;
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (mScrim == null) {
            super.draw(canvas);
            return;
        }

        final int flags = Canvas.HAS_ALPHA_LAYER_SAVE_FLAG;

        int length = mScrim.getWidth();
        int height = getHeight();
        int saveCount = canvas.getSaveCount();

        int scrimLeft;
        if (mScrimOnLeft) {
            scrimLeft = getScrollX();
        } else {
            scrimLeft = getScrollX() + getWidth() - length;
        }
        canvas.saveLayer(scrimLeft, 0, scrimLeft + length, height, null, flags);
        super.draw(canvas);

        mFadeMatrix.setTranslate(scrimLeft, 0);
        mFadeShader.setLocalMatrix(mFadeMatrix);
        mFadePaint.setShader(mFadeShader);
        canvas.drawRect(scrimLeft, 0, scrimLeft + length, height, mFadePaint);
        canvas.restoreToCount(saveCount);
    }

    @Override
    protected void onAllTasksRemoved() {
        mActivity.getStateManager().goToState(NORMAL);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        int width = right - left;
        setTranslationX(mTranslationXFactor * (mIsRtl ? -width : width));
        setTranslationYFactor(mTranslationYFactor);
    }

    public void setTranslationXFactor(float translationFactor) {
        mTranslationXFactor = translationFactor;
        setTranslationX(translationFactor * (mIsRtl ? -getWidth() : getWidth()));
    }

    public float getTranslationXFactor() {
        return mTranslationXFactor;
    }

    public void setTranslationYFactor(float translationFactor) {
        mTranslationYFactor = translationFactor;
        setTranslationY(mTranslationYFactor * (mPagePadding.bottom - mPagePadding.top));
    }

    public float getTranslationYFactor() {
        return mTranslationYFactor;
    }
}
