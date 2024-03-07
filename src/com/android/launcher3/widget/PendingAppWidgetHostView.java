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

import static android.graphics.Paint.ANTI_ALIAS_FLAG;
import static android.graphics.Paint.DITHER_FLAG;
import static android.graphics.Paint.FILTER_BITMAP_FLAG;

import static com.android.launcher3.graphics.PreloadIconDrawable.newPendingIcon;
import static com.android.launcher3.icons.FastBitmapDrawable.getDisabledColorFilter;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.SizeF;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.icons.IconCache.ItemInfoUpdateReceiver;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.Themes;

import java.util.List;

public class PendingAppWidgetHostView extends LauncherAppWidgetHostView
        implements OnClickListener, ItemInfoUpdateReceiver {
    private static final float SETUP_ICON_SIZE_FACTOR = 2f / 5;
    private static final float MIN_SATURATION = 0.7f;

    private static final int FLAG_DRAW_SETTINGS = 1;
    private static final int FLAG_DRAW_ICON = 2;
    private static final int FLAG_DRAW_LABEL = 4;

    private static final int DEFERRED_ALPHA = 0x77;

    private final Rect mRect = new Rect();

    private final Matrix mMatrix = new Matrix();
    private final RectF mPreviewBitmapRect = new RectF();
    private final RectF mCanvasRect = new RectF();

    private final LauncherWidgetHolder mWidgetHolder;
    private final LauncherAppWidgetProviderInfo mAppwidget;
    private final LauncherAppWidgetInfo mInfo;
    private final int mStartState;
    private final boolean mDisabledForSafeMode;
    private final CharSequence mLabel;

    private OnClickListener mClickListener;
    private SafeCloseable mOnDetachCleanup;

    private int mDragFlags;

    private Drawable mCenterDrawable;
    private Drawable mSettingIconDrawable;

    private boolean mDrawableSizeChanged;
    private boolean mIsDeferredWidget;

    private final TextPaint mPaint;

    private final Paint mPreviewPaint;
    private Layout mSetupTextLayout;

    @Nullable private Bitmap mPreviewBitmap;

    public PendingAppWidgetHostView(Context context, LauncherWidgetHolder widgetHolder,
            LauncherAppWidgetInfo info, @Nullable LauncherAppWidgetProviderInfo appWidget) {
        this(context, widgetHolder, info, appWidget, null);
    }

    public PendingAppWidgetHostView(Context context, LauncherWidgetHolder widgetHolder,
            LauncherAppWidgetInfo info, @Nullable LauncherAppWidgetProviderInfo appWidget,
            @Nullable Bitmap previewBitmap) {
        this(context, widgetHolder, info, appWidget,
                context.getResources().getText(R.string.gadget_complete_setup_text), previewBitmap);
        super.updateAppWidget(null);
        setOnClickListener(mActivityContext.getItemOnClickListener());

        if (info.pendingItemInfo == null) {
            info.pendingItemInfo = new PackageItemInfo(info.providerName.getPackageName(),
                    info.user);
            LauncherAppState.getInstance(context).getIconCache()
                    .updateIconInBackground(this, info.pendingItemInfo);
        } else {
            reapplyItemInfo(info.pendingItemInfo);
        }
    }

    public PendingAppWidgetHostView(
            Context context, LauncherWidgetHolder widgetHolder,
            int appWidgetId, @NonNull LauncherAppWidgetProviderInfo appWidget) {
        this(context, widgetHolder, new LauncherAppWidgetInfo(appWidgetId, appWidget.provider),
                appWidget, appWidget.label, null);
        getBackground().mutate().setAlpha(DEFERRED_ALPHA);

        mCenterDrawable = new ColorDrawable(Color.TRANSPARENT);
        mDragFlags = FLAG_DRAW_LABEL;
        mDrawableSizeChanged = true;
        mIsDeferredWidget = true;
    }

    /**
     * Set {@link Bitmap} of widget preview and update background drawable. When showing preview
     * bitmap, we shouldn't draw background.
     */
    public void setPreviewBitmapAndUpdateBackground(@Nullable Bitmap previewBitmap) {
        setBackgroundResource(previewBitmap != null ? 0 : R.drawable.pending_widget_bg);
        if (this.mPreviewBitmap == previewBitmap) {
            return;
        }
        this.mPreviewBitmap = previewBitmap;
        invalidate();
    }

    private PendingAppWidgetHostView(Context context,
            LauncherWidgetHolder widgetHolder, LauncherAppWidgetInfo info,
            LauncherAppWidgetProviderInfo appwidget, CharSequence label,
            @Nullable Bitmap previewBitmap) {
        super(new ContextThemeWrapper(context, R.style.WidgetContainerTheme));
        mWidgetHolder = widgetHolder;
        mAppwidget = appwidget;
        mInfo = info;
        mStartState = info.restoreStatus;
        mDisabledForSafeMode = LauncherAppState.getInstance(context).isSafeModeEnabled();
        mLabel = label;

        mPaint = new TextPaint();
        mPaint.setColor(Themes.getAttrColor(getContext(), android.R.attr.textColorPrimary));
        mPaint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_PX,
                mActivityContext.getDeviceProfile().iconTextSizePx,
                getResources().getDisplayMetrics()));
        mPreviewPaint = new Paint(ANTI_ALIAS_FLAG | DITHER_FLAG | FILTER_BITMAP_FLAG);

        setWillNotDraw(false);
        setPreviewBitmapAndUpdateBackground(previewBitmap);
    }

    @Override
    public AppWidgetProviderInfo getAppWidgetInfo() {
        return mAppwidget;
    }

    @Override
    public int getAppWidgetId() {
        return mInfo.appWidgetId;
    }

    @Override
    public void updateAppWidget(RemoteViews remoteViews) {
        checkIfRestored();
    }

    private void checkIfRestored() {
        WidgetManagerHelper widgetManagerHelper = new WidgetManagerHelper(getContext());
        if (widgetManagerHelper.isAppWidgetRestored(mInfo.appWidgetId)) {
            MAIN_EXECUTOR.getHandler().post(this::reInflate);
        }
    }

    public boolean isDeferredWidget() {
        return mIsDeferredWidget;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if ((mAppwidget != null)
                && !mInfo.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_NOT_VALID)
                && mInfo.restoreStatus != LauncherAppWidgetInfo.RESTORE_COMPLETED) {
            // If the widget is not completely restored, but has a valid ID, then listen of
            // updates from provider app for potential restore complete.
            if (mOnDetachCleanup != null) {
                mOnDetachCleanup.close();
            }
            mOnDetachCleanup = mWidgetHolder.addOnUpdateListener(
                    mInfo.appWidgetId, mAppwidget, this::checkIfRestored);
            checkIfRestored();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mOnDetachCleanup != null) {
            mOnDetachCleanup.close();
            mOnDetachCleanup = null;
        }
    }

    /**
     * Forces the Launcher to reinflate the widget view
     */
    public void reInflate() {
        if (!isAttachedToWindow()) {
            return;
        }
        LauncherAppWidgetInfo info = (LauncherAppWidgetInfo) getTag();
        if (info == null) {
            // This occurs when LauncherAppWidgetHostView is used to render a preview layout.
            return;
        }
        if (mActivityContext instanceof Launcher launcher) {
            // Remove and rebind the current widget (which was inflated in the wrong
            // orientation), but don't delete it from the database
            launcher.removeItem(this, info, false  /* deleteFromDb */,
                    "widget removed because of configuration change");
            launcher.bindAppWidget(info);
        }
    }

    @Override
    public void updateAppWidgetSize(Bundle newOptions, int minWidth, int minHeight, int maxWidth,
            int maxHeight) {
        // No-op
    }

    @Override
    public void updateAppWidgetSize(Bundle newOptions, List<SizeF> sizes) {
        // No-op
    }

    @Override
    protected View getDefaultView() {
        View defaultView = mInflater.inflate(R.layout.appwidget_not_ready, this, false);
        defaultView.setOnClickListener(this);
        applyState();
        invalidate();
        return defaultView;
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
        mDragFlags = 0;
        if (info.bitmap.icon != null) {
            mDragFlags = FLAG_DRAW_ICON;

            Drawable widgetCategoryIcon = getWidgetCategoryIcon();
            // The view displays three modes,
            //   1) App icon in the center
            //   2) Preload icon in the center
            //   3) App icon in the center with a setup icon on the top left corner.
            if (mDisabledForSafeMode) {
                if (widgetCategoryIcon == null) {
                    FastBitmapDrawable disabledIcon = info.newIcon(getContext());
                    disabledIcon.setIsDisabled(true);
                    mCenterDrawable = disabledIcon;
                } else {
                    widgetCategoryIcon.setColorFilter(getDisabledColorFilter());
                    mCenterDrawable = widgetCategoryIcon;
                }
                mSettingIconDrawable = null;
            } else if (isReadyForClickSetup()) {
                mCenterDrawable = widgetCategoryIcon == null
                        ? info.newIcon(getContext())
                        : widgetCategoryIcon;
                mSettingIconDrawable = getResources().getDrawable(R.drawable.ic_setting).mutate();
                updateSettingColor(info.bitmap.color);

                mDragFlags |= FLAG_DRAW_SETTINGS | FLAG_DRAW_LABEL;
            } else {
                mCenterDrawable = widgetCategoryIcon == null
                        ? newPendingIcon(getContext(), info)
                        : widgetCategoryIcon;
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
        hsv[1] = Math.min(hsv[1], MIN_SATURATION);
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
        DeviceProfile grid = mActivityContext.getDeviceProfile();
        int paddingTop = getPaddingTop();
        int paddingBottom = getPaddingBottom();
        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();

        int minPadding = getResources()
                .getDimensionPixelSize(R.dimen.pending_widget_min_padding);

        int availableWidth = getWidth() - paddingLeft - paddingRight - 2 * minPadding;
        int availableHeight = getHeight() - paddingTop - paddingBottom - 2 * minPadding;

        float iconSize = ((mDragFlags & FLAG_DRAW_ICON) == 0) ? 0
                : Math.max(0, Math.min(availableWidth, availableHeight));
        // Use twice the setting size factor, as the setting is drawn at a corner and the
        // icon is drawn in the center.
        float settingIconScaleFactor = ((mDragFlags & FLAG_DRAW_SETTINGS) == 0) ? 0
                : 1 + SETUP_ICON_SIZE_FACTOR * 2;

        int maxSize = Math.max(availableWidth, availableHeight);
        if (iconSize * settingIconScaleFactor > maxSize) {
            // There is an overlap
            iconSize = maxSize / settingIconScaleFactor;
        }

        int actualIconSize = (int) Math.min(iconSize, grid.iconSizePx);

        // Icon top when we do not draw the text
        int iconTop = (getHeight() - actualIconSize) / 2;
        mSetupTextLayout = null;

        if (availableWidth > 0 && !TextUtils.isEmpty(mLabel)
                && ((mDragFlags & FLAG_DRAW_LABEL) != 0)) {
            // Recreate the setup text.
            mSetupTextLayout = new StaticLayout(
                    mLabel, mPaint, availableWidth, Layout.Alignment.ALIGN_CENTER, 1, 0, true);
            int textHeight = mSetupTextLayout.getHeight();

            // Extra icon size due to the setting icon
            float minHeightWithText = textHeight + actualIconSize * settingIconScaleFactor
                    + grid.iconDrawablePaddingPx;

            if (minHeightWithText < availableHeight) {
                // We can draw the text as well
                iconTop = (getHeight() - textHeight
                        - grid.iconDrawablePaddingPx - actualIconSize) / 2;

            } else {
                // We can't draw the text. Let the iconTop be same as before.
                mSetupTextLayout = null;
            }
        }

        mRect.set(0, 0, actualIconSize, actualIconSize);
        mRect.offset((getWidth() - actualIconSize) / 2, iconTop);
        mCenterDrawable.setBounds(mRect);

        if (mSettingIconDrawable != null) {
            mRect.left = paddingLeft + minPadding;
            mRect.right = mRect.left + (int) (SETUP_ICON_SIZE_FACTOR * actualIconSize);
            mRect.top = paddingTop + minPadding;
            mRect.bottom = mRect.top + (int) (SETUP_ICON_SIZE_FACTOR * actualIconSize);
            mSettingIconDrawable.setBounds(mRect);
        }

        if (mSetupTextLayout != null) {
            // Set up position for dragging the text
            mRect.left = paddingLeft + minPadding;
            mRect.top = mCenterDrawable.getBounds().bottom + grid.iconDrawablePaddingPx;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mPreviewBitmap != null
                && (mInfo.restoreStatus & LauncherAppWidgetInfo.FLAG_UI_NOT_READY) != 0) {
            mPreviewBitmapRect.set(0, 0, mPreviewBitmap.getWidth(), mPreviewBitmap.getHeight());
            mCanvasRect.set(0, 0, getWidth(), getHeight());

            mMatrix.setRectToRect(mPreviewBitmapRect, mCanvasRect, Matrix.ScaleToFit.CENTER);
            canvas.drawBitmap(mPreviewBitmap, mMatrix, mPreviewPaint);
            return;
        }
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

    /**
     * Returns the widget category icon for {@link #mInfo}.
     *
     * <p>If {@link #mInfo}'s category is {@code PackageItemInfo#NO_CATEGORY} or unknown, returns
     * {@code null}.
     */
    @Nullable
    private Drawable getWidgetCategoryIcon() {
        if (mInfo.pendingItemInfo.widgetCategory == WidgetSections.NO_CATEGORY) {
            return null;
        }
        return mInfo.pendingItemInfo.newIcon(getContext());
    }
}
