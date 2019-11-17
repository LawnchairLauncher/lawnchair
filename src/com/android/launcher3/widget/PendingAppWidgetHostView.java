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

package com.android.launcher3.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.View.OnClickListener;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.FastBitmapDrawable;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.icons.IconCache.ItemInfoUpdateReceiver;
import com.android.launcher3.ItemInfoWithIcon;
import com.android.launcher3.LauncherAppWidgetInfo;
import com.android.launcher3.R;
import com.android.launcher3.graphics.DrawableFactory;
import com.android.launcher3.model.PackageItemInfo;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.util.Themes;

public class PendingAppWidgetHostView extends LauncherAppWidgetHostView
        implements OnClickListener, ItemInfoUpdateReceiver {
    private static final float SETUP_ICON_SIZE_FACTOR = 2f / 5;
    private static final float MIN_SATUNATION = 0.7f;

    private final Rect mRect = new Rect();
    private View mDefaultView;
    private OnClickListener mClickListener;
    private final LauncherAppWidgetInfo mInfo;
    private final int mStartState;
    private final boolean mDisabledForSafeMode;

    private Drawable mCenterDrawable;
    private Drawable mSettingIconDrawable;

    private boolean mDrawableSizeChanged;

    private final TextPaint mPaint;
    private Layout mSetupTextLayout;

    public PendingAppWidgetHostView(Context context, LauncherAppWidgetInfo info,
            IconCache cache, boolean disabledForSafeMode) {
        super(new ContextThemeWrapper(context, R.style.WidgetContainerTheme));

        mInfo = info;
        mStartState = info.restoreStatus;
        mDisabledForSafeMode = disabledForSafeMode;

        mPaint = new TextPaint();
        mPaint.setColor(Themes.getAttrColor(getContext(), android.R.attr.textColorPrimary));
        mPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX,
                mLauncher.getDeviceProfile().iconTextSizePx, getResources().getDisplayMetrics()));
        setBackgroundResource(R.drawable.pending_widget_bg);
        setWillNotDraw(false);

        setElevation(getResources().getDimension(R.dimen.pending_widget_elevation));
        updateAppWidget(null);
        setOnClickListener(ItemClickHandler.INSTANCE);

        if (info.pendingItemInfo == null) {
            info.pendingItemInfo = new PackageItemInfo(info.providerName.getPackageName());
            info.pendingItemInfo.user = info.user;
            cache.updateIconInBackground(this, info.pendingItemInfo);
        } else {
            reapplyItemInfo(info.pendingItemInfo);
        }
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

    public boolean isReinflateIfNeeded() {
        return mStartState != mInfo.restoreStatus;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mDrawableSizeChanged = true;
    }

    @Override
    public void reapplyItemInfo(ItemInfoWithIcon info) {
        if (mCenterDrawable != null) {
            mCenterDrawable.setCallback(null);
            mCenterDrawable = null;
        }
        if (info.iconBitmap != null) {
            // The view displays three modes,
            //   1) App icon in the center
            //   2) Preload icon in the center
            //   3) Setup icon in the center and app icon in the top right corner.
            DrawableFactory drawableFactory = DrawableFactory.INSTANCE.get(getContext());
            if (mDisabledForSafeMode) {
                FastBitmapDrawable disabledIcon = drawableFactory.newIcon(getContext(), info);
                disabledIcon.setIsDisabled(true);
                mCenterDrawable = disabledIcon;
                mSettingIconDrawable = null;
            } else if (isReadyForClickSetup()) {
                mCenterDrawable = drawableFactory.newIcon(getContext(), info);
                mSettingIconDrawable = getResources().getDrawable(R.drawable.ic_setting).mutate();
                updateSettingColor(info.iconColor);
            } else {
                mCenterDrawable = DrawableFactory.INSTANCE.get(getContext())
                        .newPendingIcon(getContext(), info);
                mSettingIconDrawable = null;
                applyState();
            }
            mCenterDrawable.setCallback(this);
            mDrawableSizeChanged = true;
        }
        invalidate();
    }

    private void updateSettingColor(int dominantColor) {
        // Make the dominant color bright.
        float[] hsv = new float[3];
        Color.colorToHSV(dominantColor, hsv);
        hsv[1] = Math.min(hsv[1], MIN_SATUNATION);
        hsv[2] = 1;
        mSettingIconDrawable.setColorFilter(Color.HSVToColor(hsv),  PorterDuff.Mode.SRC_IN);
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

    /**
     * A pending widget is ready for setup after the provider is installed and
     *   1) Widget id is not valid: the widget id is not yet bound to the provider, probably
     *                              because the launcher doesn't have appropriate permissions.
     *                              Note that we would still have an allocated id as that does not
     *                              require any permissions and can be done during view inflation.
     *   2) UI is not ready: the id is valid and the bound. But the widget has a configure activity
     *                       which needs to be called once.
     */
    public boolean isReadyForClickSetup() {
        return !mInfo.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY)
                && (mInfo.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_UI_NOT_READY)
                || mInfo.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_NOT_VALID));
    }

    private void updateDrawableBounds() {
        DeviceProfile grid = mLauncher.getDeviceProfile();
        int paddingTop = getPaddingTop();
        int paddingBottom = getPaddingBottom();
        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();

        int minPadding = getResources()
                .getDimensionPixelSize(R.dimen.pending_widget_min_padding);

        int availableWidth = getWidth() - paddingLeft - paddingRight - 2 * minPadding;
        int availableHeight = getHeight() - paddingTop - paddingBottom - 2 * minPadding;

        if (mSettingIconDrawable == null) {
            int maxSize = grid.iconSizePx;
            int size = Math.min(maxSize, Math.min(availableWidth, availableHeight));

            mRect.set(0, 0, size, size);
            mRect.offsetTo((getWidth() - mRect.width()) / 2, (getHeight() - mRect.height()) / 2);
            mCenterDrawable.setBounds(mRect);
        } else  {
            float iconSize = Math.max(0, Math.min(availableWidth, availableHeight));

            // Use twice the setting size factor, as the setting is drawn at a corner and the
            // icon is drawn in the center.
            float settingIconScaleFactor = 1 + SETUP_ICON_SIZE_FACTOR * 2;
            int maxSize = Math.max(availableWidth, availableHeight);
            if (iconSize * settingIconScaleFactor > maxSize) {
                // There is an overlap
                iconSize = maxSize / settingIconScaleFactor;
            }

            int actualIconSize = (int) Math.min(iconSize, grid.iconSizePx);

            // Icon top when we do not draw the text
            int iconTop = (getHeight() - actualIconSize) / 2;
            mSetupTextLayout = null;

            if (availableWidth > 0) {
                // Recreate the setup text.
                mSetupTextLayout = new StaticLayout(
                        getResources().getText(R.string.gadget_setup_text), mPaint, availableWidth,
                        Layout.Alignment.ALIGN_CENTER, 1, 0, true);
                int textHeight = mSetupTextLayout.getHeight();

                // Extra icon size due to the setting icon
                float minHeightWithText = textHeight + actualIconSize * settingIconScaleFactor
                        + grid.iconDrawablePaddingPx;

                if (minHeightWithText < availableHeight) {
                    // We can draw the text as well
                    iconTop = (getHeight() - textHeight -
                            grid.iconDrawablePaddingPx - actualIconSize) / 2;

                } else {
                    // We can't draw the text. Let the iconTop be same as before.
                    mSetupTextLayout = null;
                }
            }

            mRect.set(0, 0, actualIconSize, actualIconSize);
            mRect.offset((getWidth() - actualIconSize) / 2, iconTop);
            mCenterDrawable.setBounds(mRect);

            mRect.left = paddingLeft + minPadding;
            mRect.right = mRect.left + (int) (SETUP_ICON_SIZE_FACTOR * actualIconSize);
            mRect.top = paddingTop + minPadding;
            mRect.bottom = mRect.top + (int) (SETUP_ICON_SIZE_FACTOR * actualIconSize);
            mSettingIconDrawable.setBounds(mRect);

            if (mSetupTextLayout != null) {
                // Set up position for dragging the text
                mRect.left = paddingLeft + minPadding;
                mRect.top = mCenterDrawable.getBounds().bottom + grid.iconDrawablePaddingPx;
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mCenterDrawable == null) {
            // Nothing to draw
            return;
        }

        if (mDrawableSizeChanged) {
            updateDrawableBounds();
            mDrawableSizeChanged = false;
        }

        mCenterDrawable.draw(canvas);
        if (mSettingIconDrawable != null) {
            mSettingIconDrawable.draw(canvas);
        }
        if (mSetupTextLayout != null) {
            canvas.save();
            canvas.translate(mRect.left, mRect.top);
            mSetupTextLayout.draw(canvas);
            canvas.restore();
        }

    }
}
