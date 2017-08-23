/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.pixel;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.util.Property;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.RemoteViews;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Workspace;
import com.android.launcher3.Workspace.OnStateChangeListener;

/**
 * A simple view used to show the region blocked by QSB during drag and drop.
 */
public class QsbBlockerView extends FrameLayout implements OnStateChangeListener, WeatherListener.OnGsaListener {
    public static final Property<QsbBlockerView, Integer> QSB_BLOCKER_VIEW_ALPHA = new QsbBlockerViewAlpha(Integer.TYPE, "bgAlpha");
    private int mState = 0;
    private View mView;

    private final Paint mBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public QsbBlockerView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mBgPaint.setColor(Color.WHITE);
        mBgPaint.setAlpha(0);
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mView != null && mState == 2) {
            DeviceProfile deviceProfile = Launcher.getLauncher(getContext()).getDeviceProfile();
            LayoutParams layoutParams = (LayoutParams) mView.getLayoutParams();
            int size = ((MeasureSpec.getSize(widthMeasureSpec) / deviceProfile.inv.numColumns) - deviceProfile.iconSizePx) / 2;
            layoutParams.rightMargin = size;
            layoutParams.leftMargin = size;
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        Workspace w = Launcher.getLauncher(getContext()).getWorkspace();
        w.setOnStateChangeListener(this);
        prepareStateChange(w.getState(), null);
        GoogleSearchApp gsa = WeatherListener.getInstance(getContext()).getGoogleSearchAppAndAddListener(this);
        if (gsa != null) {
            onGsa(gsa.mRemoteViews);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        WeatherListener.getInstance(getContext()).removeListener(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void prepareStateChange(Workspace.State state, AnimatorSet animatorSet) {
        int i;
        if (state == Workspace.State.SPRING_LOADED) {
            i = 60;
        } else {
            i = 0;
        }
        if (animatorSet == null) {
            QSB_BLOCKER_VIEW_ALPHA.set(this, i);
            return;
        }
        animatorSet.play(ObjectAnimator.ofInt(this, QSB_BLOCKER_VIEW_ALPHA, i));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawPaint(mBgPaint);
    }

    @Override
    public void onGsa(RemoteViews views) {
        long n = 200L;
        View oldView = mView;
        int oldState = mState;

        mView = ShadowHostView.getView(views, this, mView);
        mState = 2;
        if (mView == null) {
            mState = 1;
            View inflatedView;
            if (oldView != null && oldState == 1) {
                inflatedView = oldView;
            }
            else {
                inflatedView = LayoutInflater.from(getContext()).inflate(R.layout.date_widget, this, false);
            }
            mView = inflatedView;
        }
        if (oldState == mState) {
            if (oldView != mView) {
                if (oldView != null) {
                    removeView(oldView);
                }
                addView(mView);
            }
        }
        else {
            if (oldView != null) {
                oldView.animate().setDuration(n).alpha(0.0f).withEndAction(new QsbBlockerViewViewRemover(this, oldView));
            }
            addView(mView);
            mView.setAlpha(0.0f);
            mView.animate().setDuration(n).alpha(1.0f);
        }
    }

    public void setPadding(final int n, final int n2, final int n3, final int n4) {
        super.setPadding(0, 0, 0, 0);
    }

    class QsbBlockerViewViewRemover implements Runnable {
        final QsbBlockerView mQsbBlockerView;
        final View mView;

        QsbBlockerViewViewRemover(QsbBlockerView qsbBlockerView, View view) {
            mQsbBlockerView = qsbBlockerView;
            mView = view;
        }

        @Override
        public void run() {
            mQsbBlockerView.removeView(mView);
        }
    }

    static class QsbBlockerViewAlpha extends Property<QsbBlockerView, Integer> {

        public QsbBlockerViewAlpha(Class<Integer> type, String name) {
            super(type, name);
        }

        @Override
        public void set(QsbBlockerView qsbBlockerView, Integer num) {
            qsbBlockerView.mBgPaint.setAlpha(num);
            qsbBlockerView.setWillNotDraw(num == 0);
            qsbBlockerView.invalidate();
        }

        @Override
        public Integer get(QsbBlockerView obj) {
            return obj.mBgPaint.getAlpha();
        }

    }
}
