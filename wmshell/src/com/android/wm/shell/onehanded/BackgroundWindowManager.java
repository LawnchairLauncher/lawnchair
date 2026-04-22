/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.onehanded;

import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
import static android.view.WindowManager.LayoutParams.FLAG_SLIPPERY;
import static android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_NO_MOVE_ANIMATION;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY;

import static com.android.wm.shell.onehanded.OneHandedState.STATE_ACTIVE;
import static com.android.wm.shell.onehanded.OneHandedState.STATE_ENTERING;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Binder;
import android.util.Slog;
import android.view.ContextThemeWrapper;
import android.view.IWindow;
import android.view.LayoutInflater;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowlessWindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.wm.shell.R;
import com.android.wm.shell.common.DisplayLayout;

import java.io.PrintWriter;

/**
 * Holds view hierarchy of a root surface and helps inflate a themeable view for background.
 */
public final class BackgroundWindowManager extends WindowlessWindowManager {
    private static final String TAG = BackgroundWindowManager.class.getSimpleName();
    private static final int THEME_COLOR_OFFSET = 10;

    private final OneHandedSurfaceTransactionHelper.SurfaceControlTransactionFactory
            mTransactionFactory;

    private Context mContext;
    private Rect mDisplayBounds;
    private SurfaceControlViewHost mViewHost;
    private SurfaceControl mLeash;
    private View mBackgroundView;
    private @OneHandedState.State int mCurrentState;

    public BackgroundWindowManager(Context context) {
        super(context.getResources().getConfiguration(), null /* rootSurface */,
                null /* hostInputToken */);
        mContext = context;
        mTransactionFactory = SurfaceControl.Transaction::new;
    }

    @Override
    public SurfaceControl getSurfaceControl(IWindow window) {
        return super.getSurfaceControl(window);
    }

    @Override
    public void setConfiguration(Configuration configuration) {
        super.setConfiguration(configuration);
        mContext = mContext.createConfigurationContext(configuration);
    }

    /**
     * onConfigurationChanged events for updating background theme color.
     */
    public void onConfigurationChanged() {
        if (mCurrentState == STATE_ENTERING || mCurrentState == STATE_ACTIVE) {
            updateThemeOnly();
        }
    }

    /**
     * One-handed mode state changed callback
     * @param newState of One-handed mode representing by {@link OneHandedState}
     */
    public void onStateChanged(int newState) {
        mCurrentState = newState;
    }

    @Override
    protected SurfaceControl getParentSurface(IWindow window, WindowManager.LayoutParams attrs) {
        final SurfaceControl.Builder builder = new SurfaceControl.Builder()
                .setColorLayer()
                .setBufferSize(mDisplayBounds.width(), mDisplayBounds.height())
                .setFormat(PixelFormat.RGB_888)
                .setOpaque(true)
                .setName(TAG)
                .setCallsite("BackgroundWindowManager#attachToParentSurface");
        mLeash = builder.build();
        return mLeash;
    }

    /** Inflates background view on to the root surface. */
    boolean initView() {
        if (mBackgroundView != null || mViewHost != null) {
            return false;
        }

        mViewHost = new SurfaceControlViewHost(mContext, mContext.getDisplay(), this,
                "BackgroundWindowManager");
        mBackgroundView = (View) LayoutInflater.from(mContext)
                .inflate(R.layout.background_panel, null /* root */);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                mDisplayBounds.width(), mDisplayBounds.height(), 0 /* TYPE NONE */,
                FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_WATCH_OUTSIDE_TOUCH
                        | FLAG_SLIPPERY, PixelFormat.TRANSLUCENT);
        lp.token = new Binder();
        lp.setTitle("background-panel");
        lp.privateFlags |= PRIVATE_FLAG_NO_MOVE_ANIMATION | PRIVATE_FLAG_TRUSTED_OVERLAY;
        mBackgroundView.setBackgroundColor(getThemeColorForBackground());
        mViewHost.setView(mBackgroundView, lp);
        return true;
    }

    /**
     * Called when onDisplayAdded() or onDisplayRemoved() callback.
     * @param displayLayout The latest {@link DisplayLayout} for display bounds.
     */
    public void onDisplayChanged(DisplayLayout displayLayout) {
        mDisplayBounds = new Rect(0, 0, displayLayout.width(), displayLayout.height());
    }

    private void updateThemeOnly() {
        if (mBackgroundView == null || mViewHost == null || mLeash == null) {
            Slog.w(TAG, "Background view or SurfaceControl does not exist when trying to "
                    + "update theme only!");
            return;
        }

        WindowManager.LayoutParams lp = (WindowManager.LayoutParams)
                mBackgroundView.getLayoutParams();
        mBackgroundView.setBackgroundColor(getThemeColorForBackground());
        mViewHost.setView(mBackgroundView, lp);
    }

    /**
     * Shows the background layer when One-handed mode triggered.
     */
    public void showBackgroundLayer() {
        if (!initView()) {
            updateThemeOnly();
            return;
        }
        if (mLeash == null) {
            Slog.w(TAG, "SurfaceControl mLeash is null, can't show One-handed mode "
                    + "background panel!");
            return;
        }

        mTransactionFactory.getTransaction()
                .setAlpha(mLeash, 1.0f)
                .setLayer(mLeash, -1 /* at bottom-most layer */)
                .show(mLeash)
                .apply();
    }

    /**
     * Remove the leash of background layer after stop One-handed mode.
     */
    public void removeBackgroundLayer() {
        if (mBackgroundView != null) {
            mBackgroundView = null;
        }

        if (mViewHost != null) {
            mViewHost.release();
            mViewHost = null;
        }

        if (mLeash != null) {
            mTransactionFactory.getTransaction().remove(mLeash).apply();
            mLeash = null;
        }
    }

    /**
     * Gets {@link SurfaceControl} of the background layer.
     * @return {@code null} if not exist.
     */
    @Nullable
    SurfaceControl getSurfaceControl() {
        return mLeash;
    }

    private int getThemeColor() {
        final Context themedContext = new ContextThemeWrapper(mContext,
                com.android.internal.R.style.Theme_DeviceDefault_DayNight);
        return themedContext.getColor(R.color.one_handed_tutorial_background_color);
    }

    int getThemeColorForBackground() {
        final int origThemeColor = getThemeColor();
        return android.graphics.Color.argb(Color.alpha(origThemeColor),
                Color.red(origThemeColor) - THEME_COLOR_OFFSET,
                Color.green(origThemeColor) - THEME_COLOR_OFFSET,
                Color.blue(origThemeColor) - THEME_COLOR_OFFSET);
    }

    private float adjustColor(int origColor) {
        return Math.max(origColor - THEME_COLOR_OFFSET, 0) / 255.0f;
    }

    void dump(@NonNull PrintWriter pw) {
        final String innerPrefix = "  ";
        pw.println(TAG);
        pw.print(innerPrefix + "mDisplayBounds=");
        pw.println(mDisplayBounds);
        pw.print(innerPrefix + "mViewHost=");
        pw.println(mViewHost);
        pw.print(innerPrefix + "mLeash=");
        pw.println(mLeash);
        pw.print(innerPrefix + "mBackgroundView=");
        pw.println(mBackgroundView);
    }

}
