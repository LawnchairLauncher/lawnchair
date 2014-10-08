/*
 * Copyright (C) 2014 The Android Open Source Project
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
import android.content.Intent;
import android.content.res.Resources.Theme;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.View;
import android.view.View.OnClickListener;

public class PendingAppWidgetHostView extends LauncherAppWidgetHostView implements OnClickListener {

    private static Theme sPreloaderTheme;

    private final Rect mRect = new Rect();
    private View mDefaultView;
    private OnClickListener mClickListener;
    private final LauncherAppWidgetInfo mInfo;
    private final int mStartState;
    private final Intent mIconLookupIntent;
    private final boolean mDisabledForSafeMode;

    private Bitmap mIcon;

    private Drawable mCenterDrawable;
    private Drawable mTopCornerDrawable;

    private boolean mDrawableSizeChanged;

    private final TextPaint mPaint;
    private Layout mSetupTextLayout;

    public PendingAppWidgetHostView(Context context, LauncherAppWidgetInfo info,
            boolean disabledForSafeMode) {
        super(context);
        mInfo = info;
        mStartState = info.restoreStatus;
        mIconLookupIntent = new Intent().setComponent(info.providerName);
        mDisabledForSafeMode = disabledForSafeMode;

        mPaint = new TextPaint();
        mPaint.setColor(0xFFFFFFFF);
        mPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX,
                getDeviceProfile().iconTextSizePx, getResources().getDisplayMetrics()));
        setBackgroundResource(R.drawable.quantum_panel_dark);
        setWillNotDraw(false);
    }

    @Override
    public void updateAppWidgetSize(Bundle newOptions, int minWidth, int minHeight, int maxWidth,
            int maxHeight) {
        // No-op
    }

    @Override
    protected View getDefaultView() {
        if (mDefaultView == null) {
            mDefaultView = mInflater.inflate(R.layout.appwidget_not_ready, this, false);
            mDefaultView.setOnClickListener(this);
            applyState();
        }
        return mDefaultView;
    }

    @Override
    public void setOnClickListener(OnClickListener l) {
        mClickListener = l;
    }

    @Override
    public boolean isReinflateRequired() {
        // Re inflate is required any time the widget restore status changes
        return mStartState != mInfo.restoreStatus;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mDrawableSizeChanged = true;
    }

    public void updateIcon(IconCache cache) {
        Bitmap icon = cache.getIcon(mIconLookupIntent, mInfo.user);
        if (mIcon == icon) {
            return;
        }
        mIcon = icon;
        if (mCenterDrawable != null) {
            mCenterDrawable.setCallback(null);
            mCenterDrawable = null;
        }
        if (mIcon != null) {
            // The view displays three modes,
            //   1) App icon in the center
            //   2) Preload icon in the center
            //   3) Setup icon in the center and app icon in the top right corner.
            if (mDisabledForSafeMode) {
                FastBitmapDrawable disabledIcon = Utilities.createIconDrawable(mIcon);
                disabledIcon.setGhostModeEnabled(true);
                mCenterDrawable = disabledIcon;
                mTopCornerDrawable = null;
            } else if (isReadyForClickSetup()) {
                mCenterDrawable = getResources().getDrawable(R.drawable.ic_setting);
                mTopCornerDrawable = new FastBitmapDrawable(mIcon);
            } else {
                if (sPreloaderTheme == null) {
                    sPreloaderTheme = getResources().newTheme();
                    sPreloaderTheme.applyStyle(R.style.PreloadIcon, true);
                }

                FastBitmapDrawable drawable = Utilities.createIconDrawable(mIcon);
                mCenterDrawable = new PreloadIconDrawable(drawable, sPreloaderTheme);
                mCenterDrawable.setCallback(this);
                mTopCornerDrawable = null;
                applyState();
            }
            mDrawableSizeChanged = true;
        }
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return (who == mCenterDrawable) || super.verifyDrawable(who);
    }

    public void applyState() {
        if (mCenterDrawable != null) {
            mCenterDrawable.setLevel(Math.max(mInfo.installProgress, 0));
        }
    }

    @Override
    public void onClick(View v) {
        // AppWidgetHostView blocks all click events on the root view. Instead handle click events
        // on the content and pass it along.
        if (mClickListener != null) {
            mClickListener.onClick(this);
        }
    }

    public boolean isReadyForClickSetup() {
        return (mInfo.restoreStatus & LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY) == 0
                && (mInfo.restoreStatus & LauncherAppWidgetInfo.FLAG_UI_NOT_READY) != 0;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mCenterDrawable == null) {
            // Nothing to draw
            return;
        }

        if (mTopCornerDrawable == null) {
            if (mDrawableSizeChanged) {
                int outset = (mCenterDrawable instanceof PreloadIconDrawable) ?
                        ((PreloadIconDrawable) mCenterDrawable).getOutset() : 0;
                int maxSize = LauncherAppState.getInstance().getDynamicGrid()
                        .getDeviceProfile().iconSizePx + 2 * outset;
                int size = Math.min(maxSize, Math.min(
                        getWidth() - getPaddingLeft() - getPaddingRight(),
                        getHeight() - getPaddingTop() - getPaddingBottom()));

                mRect.set(0, 0, size, size);
                mRect.inset(outset, outset);
                mRect.offsetTo((getWidth() - mRect.width()) / 2, (getHeight() - mRect.height()) / 2);
                mCenterDrawable.setBounds(mRect);
                mDrawableSizeChanged = false;
            }
            mCenterDrawable.draw(canvas);
        } else  {
            // Draw the top corner icon and "Setup" text is possible
            if (mDrawableSizeChanged) {
                DeviceProfile grid = getDeviceProfile();
                int iconSize = grid.iconSizePx;
                int paddingTop = getPaddingTop();
                int paddingBottom = getPaddingBottom();
                int paddingLeft = getPaddingLeft();
                int paddingRight = getPaddingRight();

                int availableWidth = getWidth() - paddingLeft - paddingRight;
                int availableHeight = getHeight() - paddingTop - paddingBottom;

                // Recreate the setup text.
                mSetupTextLayout = new StaticLayout(
                        getResources().getText(R.string.gadget_setup_text), mPaint, availableWidth,
                        Layout.Alignment.ALIGN_CENTER, 1, 0, true);
                if (mSetupTextLayout.getLineCount() == 1) {
                    // The text fits in a single line. No need to draw the setup icon.
                    int size = Math.min(iconSize, Math.min(availableWidth,
                            availableHeight - mSetupTextLayout.getHeight()));
                    mRect.set(0, 0, size, size);
                    mRect.offsetTo((getWidth() - mRect.width()) / 2,
                            (getHeight() - mRect.height() - mSetupTextLayout.getHeight()
                                    - grid.iconDrawablePaddingPx) / 2);

                    mTopCornerDrawable.setBounds(mRect);

                    // Update left and top to indicate the position where the text will be drawn.
                    mRect.left = paddingLeft;
                    mRect.top = mRect.bottom + grid.iconDrawablePaddingPx;
                } else {
                    // The text can't be drawn in a single line. Draw a setup icon instead.
                    mSetupTextLayout = null;
                    int size = Math.min(iconSize, Math.min(
                            getWidth() - paddingLeft - paddingRight,
                            getHeight() - paddingTop - paddingBottom));
                    mRect.set(0, 0, size, size);
                    mRect.offsetTo((getWidth() - mRect.width()) / 2, (getHeight() - mRect.height()) / 2);
                    mCenterDrawable.setBounds(mRect);

                    size = Math.min(size / 2,
                            Math.max(mRect.top - paddingTop, mRect.left - paddingLeft));
                    mTopCornerDrawable.setBounds(paddingLeft, paddingTop,
                            paddingLeft + size, paddingTop + size);
                }
                mDrawableSizeChanged = false;
            }

            if (mSetupTextLayout == null) {
                mCenterDrawable.draw(canvas);
                mTopCornerDrawable.draw(canvas);
            } else {
                canvas.save();
                canvas.translate(mRect.left, mRect.top);
                mSetupTextLayout.draw(canvas);
                canvas.restore();
                mTopCornerDrawable.draw(canvas);
            }
        }
    }

    private DeviceProfile getDeviceProfile() {
        return LauncherAppState.getInstance().getDynamicGrid().getDeviceProfile();
    }
}
