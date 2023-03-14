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

import static android.view.View.MeasureSpec.makeMeasureSpec;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_WIDGETS_TRAY;
import static com.android.launcher3.Utilities.ATLEAST_S;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
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
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.icons.RoundDrawableWrapper;
import com.android.launcher3.icons.cache.HandlerRunnable;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.widget.util.WidgetSizes;

import java.util.function.Consumer;

/**
 * Represents the individual cell of the widget inside the widget tray. The preview is drawn
 * horizontally centered, and scaled down if needed.
 *
 * This view does not support padding. Since the image is scaled down to fit the view, padding will
 * further decrease the scaling factor. Drag-n-drop uses the view bounds for showing a smooth
 * transition from the view to drag view, so when adding padding support, DnD would need to
 * consider the appropriate scaling factor.
 */
public class WidgetCell extends LinearLayout {

    private static final String TAG = "WidgetCell";
    private static final boolean DEBUG = false;

    private static final int FADE_IN_DURATION_MS = 90;

    /** Widget cell width is calculated by multiplying this factor to grid cell width. */
    private static final float WIDTH_SCALE = 3f;

    /** Widget preview width is calculated by multiplying this factor to the widget cell width. */
    private static final float PREVIEW_SCALE = 0.8f;

    /**
     * The maximum dimension that can be used as the size in
     * {@link android.view.View.MeasureSpec#makeMeasureSpec(int, int)}.
     *
     * <p>This is equal to (1 << MeasureSpec.MODE_SHIFT) - 1.
     */
    private static final int MAX_MEASURE_SPEC_DIMENSION = (1 << 30) - 1;

    /**
     * The target preview width, in pixels, of a widget or a shortcut.
     *
     * <p>The actual preview width may be smaller than or equal to this value subjected to scaling.
     */
    protected int mTargetPreviewWidth;

    /**
     * The target preview height, in pixels, of a widget or a shortcut.
     *
     * <p>The actual preview height may be smaller than or equal to this value subjected to scaling.
     */
    protected int mTargetPreviewHeight;

    protected int mPresetPreviewSize;

    private int mCellSize;

    /**
     * The scale of the preview container.
     */
    private float mPreviewContainerScale = 1f;

    private FrameLayout mWidgetImageContainer;
    private WidgetImageView mWidgetImage;
    private ImageView mWidgetBadge;
    private TextView mWidgetName;
    private TextView mWidgetDims;
    private TextView mWidgetDescription;

    protected WidgetItem mItem;

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

        setContainerWidth();
        setWillNotDraw(false);
        setClipToPadding(false);
        setAccessibilityDelegate(mActivity.getAccessibilityDelegate());
        mEnforcedCornerRadius = RoundedCornerEnforcement.computeEnforcedRadius(context);
    }

    private void setContainerWidth() {
        mCellSize = (int) (mActivity.getDeviceProfile().allAppsIconSizePx * WIDTH_SCALE);
        mPresetPreviewSize = (int) (mCellSize * PREVIEW_SCALE);
        mTargetPreviewWidth = mTargetPreviewHeight = mPresetPreviewSize;
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

    /** Returns the app widget host view scale, which is a value between [0f, 1f]. */
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
        mTargetPreviewWidth = mTargetPreviewHeight = mPresetPreviewSize;

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
        mItem = null;
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
     * @param item item to apply
     * @param previewScale factor to scale the preview
     * @param callback callback when preview is loaded in case the preview is being loaded or cached
     * @param cachedPreview previously cached preview bitmap is present
     */
    public void applyFromCellItem(WidgetItem item, float previewScale,
            @NonNull Consumer<Bitmap> callback, @Nullable Bitmap cachedPreview) {
        // setPreviewSize
        DeviceProfile deviceProfile = mActivity.getDeviceProfile();
        Size widgetSize = WidgetSizes.getWidgetItemSizePx(getContext(), deviceProfile, item);
        mTargetPreviewWidth = widgetSize.getWidth();
        mTargetPreviewHeight = widgetSize.getHeight();
        mPreviewContainerScale = previewScale;

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

        if (item.activityInfo != null) {
            setTag(new PendingAddShortcutInfo(item.activityInfo));
        } else {
            setTag(new PendingAddWidgetInfo(item.widgetInfo, mSourceContainer));
        }

        ensurePreviewWithCallback(callback, cachedPreview);
    }

    private static class ScaledAppWidgetHostView extends LauncherAppWidgetHostView {
        private boolean mKeepOrigForDragging = true;

        ScaledAppWidgetHostView(Context context) {
            super(context);
        }

        /**
         * Set if the view will keep its original scale when dragged
         * @param isKeepOrig True if keep original scale when dragged, false otherwise
         */
        public void setKeepOrigForDragging(boolean isKeepOrig) {
            mKeepOrigForDragging = isKeepOrig;
        }

        /**
         * @return True if the view is set to preserve original scale when dragged, false otherwise
         */
        public boolean isKeepOrigForDragging() {
            return mKeepOrigForDragging;
        }

        @Override
        public void startDrag() {
            super.startDrag();
            if (!isKeepOrigForDragging()) {
                // restore to original scale when being dragged, if set to do so
                setScaleToFit(1.0f);
            }
            // When the drag start, translations need to be set to zero to center the view
            setTranslationForCentering(0f, 0f);
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
                ? new ScaledAppWidgetHostView(context)
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
        appWidgetHostViewPreview.updateAppWidget(remoteViews);
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

            // Scale down the preview size if it's wider than the cell.
            float scale = 1f;
            if (mTargetPreviewWidth > 0) {
                float maxWidth = mTargetPreviewWidth;
                float previewWidth = drawable.getIntrinsicWidth() * mPreviewContainerScale;
                scale = Math.min(maxWidth / previewWidth, 1);
            }
            setContainerSize(
                    Math.round(drawable.getIntrinsicWidth() * scale * mPreviewContainerScale),
                    Math.round(drawable.getIntrinsicHeight() * scale * mPreviewContainerScale));
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

    /** Used to show the badge when the widget is in the recommended section
     */
    public void showBadge() {
        if (Process.myUserHandle().equals(mItem.user)) {
            mWidgetBadge.setVisibility(View.GONE);
        } else {
            mWidgetBadge.setVisibility(View.VISIBLE);
            mWidgetBadge.setImageResource(R.drawable.ic_work_app_badge);
        }
    }

    private void setContainerSize(int width, int height) {
        LayoutParams layoutParams = (LayoutParams) mWidgetImageContainer.getLayoutParams();
        layoutParams.width = width;
        layoutParams.height = height;
        mWidgetImageContainer.setLayoutParams(layoutParams);
    }

    /**
     * Ensures that the preview is already loaded or being loaded. If the preview is not loaded,
     * it applies the provided cachedPreview. If that is null, it starts a loader and notifies the
     * callback on successful load.
     */
    private void ensurePreviewWithCallback(Consumer<Bitmap> callback,
            @Nullable Bitmap cachedPreview) {
        if (mAppWidgetHostViewPreview != null) {
            int containerWidth = (int) (mTargetPreviewWidth * mPreviewContainerScale);
            int containerHeight = (int) (mTargetPreviewHeight * mPreviewContainerScale);
            setContainerSize(containerWidth, containerHeight);
            boolean shouldMeasureAndScale = false;
            if (mAppWidgetHostViewPreview.getChildCount() == 1) {
                View widgetContent = mAppWidgetHostViewPreview.getChildAt(0);
                ViewGroup.LayoutParams layoutParams = widgetContent.getLayoutParams();
                // We only scale preview if both the width & height of the outermost view group are
                // not set to MATCH_PARENT.
                shouldMeasureAndScale =
                        layoutParams.width != MATCH_PARENT && layoutParams.height != MATCH_PARENT;
                if (shouldMeasureAndScale) {
                    setNoClip(mWidgetImageContainer);
                    setNoClip(mAppWidgetHostViewPreview);
                    mAppWidgetHostViewScale = measureAndComputeWidgetPreviewScale();
                }
            }

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    mTargetPreviewWidth, mTargetPreviewHeight, Gravity.FILL);
            mAppWidgetHostViewPreview.setLayoutParams(params);

            if (!shouldMeasureAndScale
                    && mAppWidgetHostViewPreview instanceof ScaledAppWidgetHostView) {
                // If the view is not measured & scaled, at least one side will match the grid size,
                // so it should be safe to restore the original scale once it is dragged.
                ScaledAppWidgetHostView tempView =
                        (ScaledAppWidgetHostView) mAppWidgetHostViewPreview;
                tempView.setKeepOrigForDragging(false);
                tempView.setScaleToFit(mPreviewContainerScale);
            } else if (!shouldMeasureAndScale) {
                mAppWidgetHostViewPreview.setScaleToFit(mPreviewContainerScale);
            } else {
                mAppWidgetHostViewPreview.setScaleToFit(mAppWidgetHostViewScale);
            }
            mAppWidgetHostViewPreview.setTranslationForCentering(
                    -(params.width - (params.width * mPreviewContainerScale)) / 2.0f,
                    -(params.height - (params.height * mPreviewContainerScale)) / 2.0f);
            mWidgetImageContainer.addView(mAppWidgetHostViewPreview, /* index= */ 0);
            mWidgetImage.setVisibility(View.GONE);
            applyPreview(null);
            return;
        }
        if (cachedPreview != null) {
            applyPreview(cachedPreview);
            return;
        }
        if (mActiveRequest != null) {
            return;
        }
        mActiveRequest = mWidgetPreviewLoader.loadPreview(
                mItem, new Size(mTargetPreviewWidth, mTargetPreviewHeight), callback);
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

    private static void setNoClip(ViewGroup view) {
        view.setClipChildren(false);
        view.setClipToPadding(false);
    }

    private float measureAndComputeWidgetPreviewScale() {
        if (mAppWidgetHostViewPreview.getChildCount() != 1) {
            return 1f;
        }

        // Measure the largest possible width & height that the app widget wants to display.
        mAppWidgetHostViewPreview.measure(
                makeMeasureSpec(MAX_MEASURE_SPEC_DIMENSION, MeasureSpec.UNSPECIFIED),
                makeMeasureSpec(MAX_MEASURE_SPEC_DIMENSION, MeasureSpec.UNSPECIFIED));
        if (mRemoteViewsPreview != null) {
            // If RemoteViews contains multiple sizes, the best fit sized RemoteViews will be
            // selected in onLayout. To work out the right measurement, let's layout and then
            // measure again.
            mAppWidgetHostViewPreview.layout(
                    /* left= */ 0,
                    /* top= */ 0,
                    /* right= */ mTargetPreviewWidth,
                    /* bottom= */ mTargetPreviewHeight);
            mAppWidgetHostViewPreview.measure(
                    makeMeasureSpec(mTargetPreviewWidth, MeasureSpec.UNSPECIFIED),
                    makeMeasureSpec(mTargetPreviewHeight, MeasureSpec.UNSPECIFIED));

        }
        View widgetContent = mAppWidgetHostViewPreview.getChildAt(0);
        int appWidgetContentWidth = widgetContent.getMeasuredWidth();
        int appWidgetContentHeight = widgetContent.getMeasuredHeight();
        if (appWidgetContentWidth == 0 || appWidgetContentHeight == 0) {
            return 1f;
        }

        // If the width / height of the widget content is set to wrap content, overrides the width /
        // height with the measured dimension. This avoids incorrect measurement after scaling.
        FrameLayout.LayoutParams layoutParam =
                (FrameLayout.LayoutParams) widgetContent.getLayoutParams();
        if (layoutParam.width == WRAP_CONTENT) {
            layoutParam.width = widgetContent.getMeasuredWidth();
        }
        if (layoutParam.height == WRAP_CONTENT) {
            layoutParam.height = widgetContent.getMeasuredHeight();
        }
        widgetContent.setLayoutParams(layoutParam);

        int horizontalPadding = mAppWidgetHostViewPreview.getPaddingStart()
                + mAppWidgetHostViewPreview.getPaddingEnd();
        int verticalPadding = mAppWidgetHostViewPreview.getPaddingTop()
                + mAppWidgetHostViewPreview.getPaddingBottom();
        return Math.min(
                (mTargetPreviewWidth - horizontalPadding) * mPreviewContainerScale
                        / appWidgetContentWidth,
                (mTargetPreviewHeight - verticalPadding) * mPreviewContainerScale
                        / appWidgetContentHeight);
    }
}
