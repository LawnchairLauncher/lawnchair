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
import static com.android.launcher3.widget.LauncherAppWidgetProviderInfo.fromProviderInfo;
import static com.android.launcher3.widget.util.WidgetSizes.getWidgetItemSizePx;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.CheckLongPressHelper;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.icons.RoundDrawableWrapper;
import com.android.launcher3.icons.cache.HandlerRunnable;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;

import java.util.function.Consumer;

import app.lawnchair.LawnchairAppWidgetHostView;

/**
 * Represents the individual cell of the widget inside the widget tray. The
 * preview is drawn
 * horizontally centered, and scaled down if needed.
 *
 * This view does not support padding. Since the image is scaled down to fit the
 * view, padding will
 * further decrease the scaling factor. Drag-n-drop uses the view bounds for
 * showing a smooth
 * transition from the view to drag view, so when adding padding support, DnD
 * would need to
 * consider the appropriate scaling factor.
 */
public class WidgetCell extends LinearLayout {

    private static final String TAG = "WidgetCell";
    private static final boolean DEBUG = false;

    private static final int FADE_IN_DURATION_MS = 90;

    /**
     * The requested scale of the preview container. It can be lower than this as
     * well.
     */
    private float mPreviewContainerScale = 1f;

    private FrameLayout mWidgetImageContainer;
    private WidgetImageView mWidgetImage;
    private ImageView mWidgetBadge;
    private TextView mWidgetName;
    private TextView mWidgetDims;
    private TextView mWidgetDescription;

    private WidgetItem mItem;
    private Size mWidgetSize;

    private final DatabaseWidgetPreviewLoader mWidgetPreviewLoader;

    protected HandlerRunnable mActiveRequest;
    private boolean mAnimatePreview = true;

    protected final ActivityContext mActivity;
    private final CheckLongPressHelper mLongPressHelper;
    private final float mEnforcedCornerRadius;

    private RemoteViews mRemoteViewsPreview;
    private NavigableAppWidgetHostView mAppWidgetHostViewPreview;
    private float mAppWidgetHostViewScale = 1f;
    private int mSourceContainer = CONTAINER_WIDGETS_TRAY;

    public WidgetCell(Context context) {
        this(context, null);
    }

    public WidgetCell(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WidgetCell(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mActivity = ActivityContext.lookupContext(context);
        mWidgetPreviewLoader = new DatabaseWidgetPreviewLoader(context);
        mLongPressHelper = new CheckLongPressHelper(this);
        mLongPressHelper.setLongPressTimeoutFactor(1);
        mEnforcedCornerRadius = RoundedCornerEnforcement.computeEnforcedRadius(context);
        mWidgetSize = new Size(0, 0);

        setClipToPadding(false);
        setAccessibilityDelegate(mActivity.getAccessibilityDelegate());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mWidgetImageContainer = findViewById(R.id.widget_preview_container);
        mWidgetImage = findViewById(R.id.widget_preview);
        mWidgetBadge = findViewById(R.id.widget_badge);
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
     * Returns the app widget host view scale, which is a value between [0f, 1f].
     */
    public float getAppWidgetHostViewScale() {
        return mAppWidgetHostViewScale;
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
        mWidgetBadge.setImageDrawable(null);
        mWidgetBadge.setVisibility(View.GONE);
        mWidgetName.setText(null);
        mWidgetDims.setText(null);
        mWidgetDescription.setText(null);
        mWidgetDescription.setVisibility(GONE);

        if (mActiveRequest != null) {
            mActiveRequest.cancel();
            mActiveRequest = null;
        }
        mRemoteViewsPreview = null;
        if (mAppWidgetHostViewPreview != null) {
            mWidgetImageContainer.removeView(mAppWidgetHostViewPreview);
        }
        mAppWidgetHostViewPreview = null;
        mAppWidgetHostViewScale = 1f;
        mPreviewContainerScale = 1f;
        mItem = null;
        mWidgetSize = new Size(0, 0);
    }

    public void setSourceContainer(int sourceContainer) {
        this.mSourceContainer = sourceContainer;
    }

    /**
     * Applies the item to this view
     */
    public void applyFromCellItem(WidgetItem item) {
        applyFromCellItem(item, 1f);
    }

    /**
     * Applies the item to this view
     */
    public void applyFromCellItem(WidgetItem item, float previewScale) {
        applyFromCellItem(item, previewScale, this::applyPreview, null);
    }

    /**
     * Applies the item to this view
     * 
     * @param item          item to apply
     * @param previewScale  factor to scale the preview
     * @param callback      callback when preview is loaded in case the preview is
     *                      being loaded or cached
     * @param cachedPreview previously cached preview bitmap is present
     */
    public void applyFromCellItem(WidgetItem item, float previewScale,
            @NonNull Consumer<Bitmap> callback, @Nullable Bitmap cachedPreview) {
        mPreviewContainerScale = previewScale;

        Context context = getContext();
        mItem = item;
        mWidgetSize = getWidgetItemSizePx(getContext(), mActivity.getDeviceProfile(), mItem);

        mWidgetName.setText(mItem.label);
        mWidgetName.setContentDescription(
                context.getString(R.string.widget_preview_context_description, mItem.label));
        mWidgetDims.setText(context.getString(R.string.widget_dims_format,
                mItem.spanX, mItem.spanY));
        mWidgetDims.setContentDescription(context.getString(
                R.string.widget_accessible_dims_format, mItem.spanX, mItem.spanY));
        if (!TextUtils.isEmpty(mItem.description)) {
            mWidgetDescription.setText(mItem.description);
            mWidgetDescription.setVisibility(VISIBLE);
        } else {
            mWidgetDescription.setVisibility(GONE);
        }

        if (item.activityInfo != null) {
            setTag(new PendingAddShortcutInfo(item.activityInfo));
        } else {
            setTag(new PendingAddWidgetInfo(item.widgetInfo, mSourceContainer));
        }

        if (mRemoteViewsPreview != null) {
            mAppWidgetHostViewPreview = createAppWidgetHostView(context);
            setAppWidgetHostViewPreview(mAppWidgetHostViewPreview, item.widgetInfo,
                    mRemoteViewsPreview);
        } else if (item.hasPreviewLayout()) {
            // If the context is a Launcher activity, DragView will show
            // mAppWidgetHostViewPreview
            // as a preview during drag & drop. And thus, we should use
            // LauncherAppWidgetHostView,
            // which supports applying local color extraction during drag & drop.
            mAppWidgetHostViewPreview = isLauncherContext(context)
                    ? new LauncherAppWidgetHostView(context)
                    : createAppWidgetHostView(context);
            LauncherAppWidgetProviderInfo providerInfo = fromProviderInfo(context, item.widgetInfo.clone());
            // A hack to force the initial layout to be the preview layout since there is no
            // API for
            // rendering a preview layout for work profile apps yet. For non-work profile
            // layout, a
            // proper solution is to use RemoteViews(PackageName, LayoutId).
            providerInfo.initialLayout = item.widgetInfo.previewLayout;
            setAppWidgetHostViewPreview(mAppWidgetHostViewPreview, providerInfo, null);
        } else if (cachedPreview != null) {
            applyPreview(cachedPreview);
        } else {
            if (mActiveRequest == null) {
                mActiveRequest = mWidgetPreviewLoader.loadPreview(mItem, mWidgetSize, callback);
            }
        }
    }

    private void setAppWidgetHostViewPreview(
            NavigableAppWidgetHostView appWidgetHostViewPreview,
            LauncherAppWidgetProviderInfo providerInfo,
            @Nullable RemoteViews remoteViews) {
        appWidgetHostViewPreview.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        appWidgetHostViewPreview.setAppWidget(/* appWidgetId= */ -1, providerInfo);
        appWidgetHostViewPreview.updateAppWidget(remoteViews);
        appWidgetHostViewPreview.setClipToPadding(false);
        appWidgetHostViewPreview.setClipChildren(false);

        FrameLayout.LayoutParams widgetHostLP = new FrameLayout.LayoutParams(
                mWidgetSize.getWidth(), mWidgetSize.getHeight(), Gravity.CENTER);
        mWidgetImageContainer.addView(appWidgetHostViewPreview, /* index= */ 0, widgetHostLP);
        mWidgetImage.setVisibility(View.GONE);
        applyPreview(null);

        appWidgetHostViewPreview.addOnLayoutChangeListener(
                (v, l, t, r, b, ol, ot, or, ob) -> updateAppWidgetHostScale(appWidgetHostViewPreview));
    }

    private void updateAppWidgetHostScale(NavigableAppWidgetHostView view) {
        // Scale the content such that all of the content is visible
        int contentWidth = view.getWidth();
        int contentHeight = view.getHeight();

        if (view.getChildCount() == 1) {
            View content = view.getChildAt(0);
            // Take the content width based on the edge furthest from the center, so that
            // when
            // scaling the hostView, the farthest edge is still visible.
            contentWidth = 2 * Math.max(contentWidth / 2 - content.getLeft(),
                    content.getRight() - contentWidth / 2);
            contentHeight = 2 * Math.max(contentHeight / 2 - content.getTop(),
                    content.getBottom() - contentHeight / 2);
        }

        if (contentWidth <= 0 || contentHeight <= 0) {
            mAppWidgetHostViewScale = 1;
        } else {
            float pWidth = mWidgetImageContainer.getWidth();
            float pHeight = mWidgetImageContainer.getHeight();
            mAppWidgetHostViewScale = Math.min(pWidth / contentWidth, pHeight / contentHeight);
        }
        view.setScaleToFit(mAppWidgetHostViewScale);
    }

    public WidgetImageView getWidgetView() {
        return mWidgetImage;
    }

    @Nullable
    public NavigableAppWidgetHostView getAppWidgetHostViewPreview() {
        return mAppWidgetHostViewPreview;
    }

    public void setAnimatePreview(boolean shouldAnimate) {
        mAnimatePreview = shouldAnimate;
    }

    private void applyPreview(Bitmap bitmap) {
        if (bitmap != null) {
            Drawable drawable = new RoundDrawableWrapper(
                    new FastBitmapDrawable(bitmap), mEnforcedCornerRadius);
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
        if (mActiveRequest != null) {
            mActiveRequest.cancel();
            mActiveRequest = null;
        }
    }

    /**
     * Used to show the badge when the widget is in the recommended section
     */
    public void showBadge() {
        if (Process.myUserHandle().equals(mItem.user)) {
            mWidgetBadge.setVisibility(View.GONE);
        } else {
            mWidgetBadge.setVisibility(View.VISIBLE);
            mWidgetBadge.setImageResource(R.drawable.ic_work_app_badge);
        }
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

    private static NavigableAppWidgetHostView createAppWidgetHostView(Context context) {
        return new NavigableAppWidgetHostView(context) {
            @Override
            protected boolean shouldAllowDirectClick() {
                return false;
            }
        };
    }

    private static boolean isLauncherContext(Context context) {
        return ActivityContext.lookupContext(context) instanceof Launcher;
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

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        ViewGroup.LayoutParams containerLp = mWidgetImageContainer.getLayoutParams();

        mAppWidgetHostViewScale = mPreviewContainerScale;
        int maxWidth = MeasureSpec.getSize(widthMeasureSpec);
        containerLp.width = Math.round(mWidgetSize.getWidth() * mAppWidgetHostViewScale);
        if (containerLp.width > maxWidth) {
            containerLp.width = maxWidth;
            mAppWidgetHostViewScale = (float) containerLp.width / mWidgetSize.getWidth();
        }
        containerLp.height = Math.round(mWidgetSize.getHeight() * mAppWidgetHostViewScale);
        // No need to call mWidgetImageContainer.setLayoutParams as we are in measure
        // pass

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
