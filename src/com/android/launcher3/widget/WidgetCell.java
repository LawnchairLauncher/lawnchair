/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_WIDGETS_TRAY;
import static com.android.launcher3.Utilities.ATLEAST_S;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.CancellationSignal;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewPropertyAnimator;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.CheckLongPressHelper;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.icons.RoundDrawableWrapper;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.widget.util.WidgetSizes;

/**
 * Represents the individual cell of the widget inside the widget tray. The preview is drawn
 * horizontally centered, and scaled down if needed.
 *
 * This view does not support padding. Since the image is scaled down to fit the view, padding will
 * further decrease the scaling factor. Drag-n-drop uses the view bounds for showing a smooth
 * transition from the view to drag view, so when adding padding support, DnD would need to
 * consider the appropriate scaling factor.
 */
public class WidgetCell extends LinearLayout implements OnLayoutChangeListener {

    private static final String TAG = "WidgetCell";
    private static final boolean DEBUG = false;

    private static final int FADE_IN_DURATION_MS = 90;

    /** Widget cell width is calculated by multiplying this factor to grid cell width. */
    private static final float WIDTH_SCALE = 3f;

    /** Widget preview width is calculated by multiplying this factor to the widget cell width. */
    private static final float PREVIEW_SCALE = 0.8f;

    protected int mPreviewWidth;
    protected int mPreviewHeight;
    protected int mPresetPreviewSize;
    private int mCellSize;
    private float mPreviewScale = 1f;

    private FrameLayout mWidgetImageContainer;
    private WidgetImageView mWidgetImage;
    private TextView mWidgetName;
    private TextView mWidgetDims;
    private TextView mWidgetDescription;

    protected WidgetItem mItem;

    private WidgetPreviewLoader mWidgetPreviewLoader;

    protected CancellationSignal mActiveRequest;
    private boolean mAnimatePreview = true;

    private boolean mApplyBitmapDeferred = false;
    private Drawable mDeferredDrawable;

    protected final BaseActivity mActivity;
    private final CheckLongPressHelper mLongPressHelper;
    private final float mEnforcedCornerRadius;
    private final int mShortcutPreviewPadding;

    private RemoteViews mRemoteViewsPreview;
    private NavigableAppWidgetHostView mAppWidgetHostViewPreview;
    private int mSourceContainer = CONTAINER_WIDGETS_TRAY;

    public WidgetCell(Context context) {
        this(context, null);
    }

    public WidgetCell(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WidgetCell(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mActivity = BaseActivity.fromContext(context);
        mLongPressHelper = new CheckLongPressHelper(this);
        mLongPressHelper.setLongPressTimeoutFactor(1);

        setContainerWidth();
        setWillNotDraw(false);
        setClipToPadding(false);
        setAccessibilityDelegate(mActivity.getAccessibilityDelegate());
        mEnforcedCornerRadius = RoundedCornerEnforcement.computeEnforcedRadius(context);
        mShortcutPreviewPadding =
                2 * getResources().getDimensionPixelSize(R.dimen.widget_preview_shortcut_padding);
    }

    private void setContainerWidth() {
        mCellSize = (int) (mActivity.getDeviceProfile().allAppsIconSizePx * WIDTH_SCALE);
        mPresetPreviewSize = (int) (mCellSize * PREVIEW_SCALE);
        mPreviewWidth = mPreviewHeight = mPresetPreviewSize;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mWidgetImageContainer = findViewById(R.id.widget_preview_container);
        mWidgetImage = findViewById(R.id.widget_preview);
        mWidgetName = findViewById(R.id.widget_name);
        mWidgetDims = findViewById(R.id.widget_dims);
        mWidgetDescription = findViewById(R.id.widget_description);
    }

    public void setRemoteViewsPreview(RemoteViews view) {
        mRemoteViewsPreview = view;
    }

    @Nullable
    public RemoteViews getRemoteViewsPreview() {
        return mRemoteViewsPreview;
    }

    /**
     * Called to clear the view and free attached resources. (e.g., {@link Bitmap}
     */
    public void clear() {
        if (DEBUG) {
            Log.d(TAG, "reset called on:" + mWidgetName.getText());
        }
        mWidgetImage.animate().cancel();
        mWidgetImage.setDrawable(null);
        mWidgetImage.setVisibility(View.VISIBLE);
        mWidgetName.setText(null);
        mWidgetDims.setText(null);
        mWidgetDescription.setText(null);
        mWidgetDescription.setVisibility(GONE);
        mPreviewWidth = mPreviewHeight = mPresetPreviewSize;

        if (mActiveRequest != null) {
            mActiveRequest.cancel();
            mActiveRequest = null;
        }
        mRemoteViewsPreview = null;
        if (mAppWidgetHostViewPreview != null) {
            mWidgetImageContainer.removeView(mAppWidgetHostViewPreview);
        }
        mAppWidgetHostViewPreview = null;
        mItem = null;
    }

    public void setSourceContainer(int sourceContainer) {
        this.mSourceContainer = sourceContainer;
    }

    public void applyFromCellItem(WidgetItem item, WidgetPreviewLoader loader) {
        applyPreviewOnAppWidgetHostView(item);

        Context context = getContext();
        mItem = item;
        mWidgetName.setText(mItem.label);
        mWidgetName.setContentDescription(
                context.getString(R.string.widget_preview_context_description, mItem.label));
        mWidgetDims.setText(context.getString(R.string.widget_dims_format,
                mItem.spanX, mItem.spanY));
        mWidgetDims.setContentDescription(context.getString(
                R.string.widget_accessible_dims_format, mItem.spanX, mItem.spanY));
        if (ATLEAST_S && mItem.widgetInfo != null) {
            CharSequence description = mItem.widgetInfo.loadDescription(context);
            if (description != null && description.length() > 0) {
                mWidgetDescription.setText(description);
                mWidgetDescription.setVisibility(VISIBLE);
            } else {
                mWidgetDescription.setVisibility(GONE);
            }
        }

        mWidgetPreviewLoader = loader;
        if (item.activityInfo != null) {
            setTag(new PendingAddShortcutInfo(item.activityInfo));
        } else {
            setTag(new PendingAddWidgetInfo(item.widgetInfo, mSourceContainer));
        }
    }


    private void applyPreviewOnAppWidgetHostView(WidgetItem item) {
        if (mRemoteViewsPreview != null) {
            mAppWidgetHostViewPreview = createAppWidgetHostView(getContext());
            setAppWidgetHostViewPreview(mAppWidgetHostViewPreview, item.widgetInfo,
                    mRemoteViewsPreview);
            return;
        }

        if (!item.hasPreviewLayout()) return;

        Context context = getContext();
        // If the context is a Launcher activity, DragView will show mAppWidgetHostViewPreview as
        // a preview during drag & drop. And thus, we should use LauncherAppWidgetHostView, which
        // supports applying local color extraction during drag & drop.
        mAppWidgetHostViewPreview = isLauncherContext(context)
                ? new LauncherAppWidgetHostView(context)
                : createAppWidgetHostView(context);
        LauncherAppWidgetProviderInfo launcherAppWidgetProviderInfo =
                LauncherAppWidgetProviderInfo.fromProviderInfo(context, item.widgetInfo.clone());
        // A hack to force the initial layout to be the preview layout since there is no API for
        // rendering a preview layout for work profile apps yet. For non-work profile layout, a
        // proper solution is to use RemoteViews(PackageName, LayoutId).
        launcherAppWidgetProviderInfo.initialLayout = item.widgetInfo.previewLayout;
        setAppWidgetHostViewPreview(mAppWidgetHostViewPreview,
                launcherAppWidgetProviderInfo, /* remoteViews= */ null);
    }

    private void setAppWidgetHostViewPreview(
            NavigableAppWidgetHostView appWidgetHostViewPreview,
            LauncherAppWidgetProviderInfo providerInfo,
            @Nullable RemoteViews remoteViews) {
        appWidgetHostViewPreview.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        appWidgetHostViewPreview.setAppWidget(/* appWidgetId= */ -1, providerInfo);
        Rect padding;
        DeviceProfile deviceProfile = mActivity.getDeviceProfile();
        if (deviceProfile.shouldInsetWidgets()) {
            padding = new Rect();
            appWidgetHostViewPreview.getWidgetInset(deviceProfile, padding);
        } else {
            padding = deviceProfile.inv.defaultWidgetPadding;
        }
        appWidgetHostViewPreview.setPadding(padding.left, padding.top, padding.right,
                padding.bottom);
        appWidgetHostViewPreview.updateAppWidget(remoteViews);
    }

    public WidgetImageView getWidgetView() {
        return mWidgetImage;
    }

    @Nullable
    public NavigableAppWidgetHostView getAppWidgetHostViewPreview() {
        return mAppWidgetHostViewPreview;
    }

    /**
     * Sets if applying bitmap preview should be deferred. The UI will still load the bitmap, but
     * will not cause invalidate, so that when deferring is disabled later, all the bitmaps are
     * ready.
     * This prevents invalidates while the animation is running.
     */
    public void setApplyBitmapDeferred(boolean isDeferred) {
        if (mApplyBitmapDeferred != isDeferred) {
            mApplyBitmapDeferred = isDeferred;
            if (!mApplyBitmapDeferred && mDeferredDrawable != null) {
                applyPreview(mDeferredDrawable);
                mDeferredDrawable = null;
            }
        }
    }

    public void setAnimatePreview(boolean shouldAnimate) {
        mAnimatePreview = shouldAnimate;
    }

    public void applyPreview(Bitmap bitmap) {
        FastBitmapDrawable drawable = new FastBitmapDrawable(bitmap);
        applyPreview(new RoundDrawableWrapper(drawable, mEnforcedCornerRadius));
    }

    private void applyPreview(Drawable drawable) {
        if (mApplyBitmapDeferred) {
            mDeferredDrawable = drawable;
            return;
        }
        if (drawable != null) {
            float scale = 1f;
            if (getWidth() > 0 && getHeight() > 0) {
                // Scale down the preview size if it's wider than the cell.
                float maxWidth = getWidth();
                float previewWidth = drawable.getIntrinsicWidth() * mPreviewScale;
                scale = Math.min(maxWidth / previewWidth, 1);
            }
            setContainerSize(
                    Math.round(drawable.getIntrinsicWidth() * scale),
                    Math.round(drawable.getIntrinsicHeight() * scale));
            mWidgetImage.setDrawable(drawable);
            mWidgetImage.setVisibility(View.VISIBLE);
            if (mAppWidgetHostViewPreview != null) {
                removeView(mAppWidgetHostViewPreview);
                mAppWidgetHostViewPreview = null;
            }
        }
        if (mAnimatePreview) {
            mWidgetImageContainer.setAlpha(0f);
            ViewPropertyAnimator anim = mWidgetImageContainer.animate();
            anim.alpha(1.0f).setDuration(FADE_IN_DURATION_MS);
        } else {
            mWidgetImageContainer.setAlpha(1f);
        }
    }

    private void setContainerSize(int width, int height) {
        LayoutParams layoutParams = (LayoutParams) mWidgetImageContainer.getLayoutParams();
        layoutParams.width = (int) (width * mPreviewScale);
        layoutParams.height = (int) (height * mPreviewScale);
        mWidgetImageContainer.setLayoutParams(layoutParams);
    }

    public void ensurePreview() {
        if (mAppWidgetHostViewPreview != null) {
            setContainerSize(mPreviewWidth, mPreviewHeight);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    mPreviewWidth, mPreviewHeight, Gravity.FILL);
            mAppWidgetHostViewPreview.setLayoutParams(params);
            mWidgetImageContainer.addView(mAppWidgetHostViewPreview, /* index= */ 0);
            mWidgetImage.setVisibility(View.GONE);
            applyPreview((Drawable) null);
            return;
        }
        if (mActiveRequest != null) {
            return;
        }
        mActiveRequest = mWidgetPreviewLoader.loadPreview(
                BaseActivity.fromContext(getContext()), mItem,
                new Size(mPreviewWidth, mPreviewHeight),
                this::applyPreview);
    }

    /** Sets the widget preview image size in number of cells. */
    public Size setPreviewSize(WidgetItem widgetItem) {
        return setPreviewSize(widgetItem, 1f);
    }

    /** Sets the widget preview image size, in number of cells, and preview scale. */
    public Size setPreviewSize(WidgetItem widgetItem, float previewScale) {
        DeviceProfile deviceProfile = mActivity.getDeviceProfile();
        Size widgetSize = WidgetSizes.getWidgetItemSizePx(getContext(), deviceProfile, widgetItem);
        mPreviewWidth = widgetSize.getWidth();
        mPreviewHeight = widgetSize.getHeight();
        mPreviewScale = previewScale;
        return widgetSize;
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
            int oldTop, int oldRight, int oldBottom) {
        removeOnLayoutChangeListener(this);
        ensurePreview();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        super.onTouchEvent(ev);
        mLongPressHelper.onTouchEvent(ev);
        return true;
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mLongPressHelper.cancelLongPress();
    }

    /**
     * Helper method to get the string info of the tag.
     */
    private String getTagToString() {
        if (getTag() instanceof PendingAddWidgetInfo ||
                getTag() instanceof PendingAddShortcutInfo) {
            return getTag().toString();
        }
        return "";
    }

    private static NavigableAppWidgetHostView createAppWidgetHostView(Context context) {
        return new NavigableAppWidgetHostView(context) {
            @Override
            protected boolean shouldAllowDirectClick() {
                return false;
            }
        };
    }

    private static boolean isLauncherContext(Context context) {
        try {
            Launcher.getLauncher(context);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return WidgetCell.class.getName();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
    }
}
