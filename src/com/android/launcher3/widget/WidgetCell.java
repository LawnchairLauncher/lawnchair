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

import static android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN;

import static com.android.launcher3.Flags.enableWidgetTapToAdd;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_WIDGETS_TRAY;
import static com.android.launcher3.widget.LauncherAppWidgetProviderInfo.fromProviderInfo;
import static com.android.launcher3.widget.util.WidgetSizes.getWidgetItemSizePx;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.app.animation.Interpolators;
import com.android.launcher3.CheckLongPressHelper;
import com.android.launcher3.Flags;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimatedPropertySetter;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.icons.RoundDrawableWrapper;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.util.CancellableTask;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.widget.picker.util.WidgetPreviewContainerSize;
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
    private static final int ADD_BUTTON_FADE_DURATION_MS = 100;

    /**
     * The requested scale of the preview container. It can be lower than this as well.
     */
    private float mPreviewContainerScale = 1f;
    private Size mPreviewContainerSize = new Size(0, 0);
    private FrameLayout mWidgetImageContainer;
    private WidgetImageView mWidgetImage;
    private TextView mWidgetName;
    private TextView mWidgetDims;
    private TextView mWidgetDescription;
    private Button mWidgetAddButton;
    private LinearLayout mWidgetTextContainer;

    private WidgetItem mItem;
    private Size mWidgetSize;

    private final DatabaseWidgetPreviewLoader mWidgetPreviewLoader;

    protected CancellableTask mActiveRequest;
    private boolean mAnimatePreview = true;

    protected final ActivityContext mActivity;
    private final CheckLongPressHelper mLongPressHelper;
    private final float mEnforcedCornerRadius;

    private RemoteViews mRemoteViewsPreview;
    private NavigableAppWidgetHostView mAppWidgetHostViewPreview;
    private float mAppWidgetHostViewScale = 1f;
    private int mSourceContainer = CONTAINER_WIDGETS_TRAY;

    private CancellableTask mIconLoadRequest;
    private boolean mIsShowingAddButton = false;

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
        mWidgetName = findViewById(R.id.widget_name);
        mWidgetDims = findViewById(R.id.widget_dims);
        mWidgetDescription = findViewById(R.id.widget_description);
        mWidgetTextContainer = findViewById(R.id.widget_text_container);
        mWidgetAddButton = findViewById(R.id.widget_add_button);
        if (enableWidgetTapToAdd()) {
            mWidgetAddButton.setVisibility(INVISIBLE);
        }
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

    /** Returns the {@link WidgetItem} for this {@link WidgetCell}. */
    public WidgetItem getWidgetItem() {
        return mItem;
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
        showDescription(true);
        showDimensions(true);

        if (enableWidgetTapToAdd()) {
            hideAddButton(/* animate= */ false);
        }

        if (mActiveRequest != null) {
            mActiveRequest.cancel();
            mActiveRequest = null;
        }
        mRemoteViewsPreview = null;
        if (mAppWidgetHostViewPreview != null) {
            mWidgetImageContainer.removeView(mAppWidgetHostViewPreview);
        }
        mAppWidgetHostViewPreview = null;
        mPreviewContainerSize = new Size(0, 0);
        mAppWidgetHostViewScale = 1f;
        mPreviewContainerScale = 1f;
        mItem = null;
        mWidgetSize = new Size(0, 0);
        showAppIconInWidgetTitle(false);
    }

    public void setSourceContainer(int sourceContainer) {
        this.mSourceContainer = sourceContainer;
    }

    /**
     * Applies the item to this view
     */
    public void applyFromCellItem(WidgetItem item) {
        applyFromCellItem(item, this::applyPreview, /*cachedPreview=*/null);
    }

    /**
     * Applies the item to this view
     * @param item item to apply
     * @param callback callback when preview is loaded in case the preview is being loaded or cached
     * @param cachedPreview previously cached preview bitmap is present
     */
    public void applyFromCellItem(WidgetItem item, @NonNull Consumer<Bitmap> callback,
            @Nullable Bitmap cachedPreview) {
        Context context = getContext();
        mItem = item;
        mWidgetSize = getWidgetItemSizePx(getContext(), mActivity.getDeviceProfile(), mItem);
        initPreviewContainerSizeAndScale();

        mWidgetName.setText(mItem.label);
        mWidgetDims.setText(context.getString(R.string.widget_dims_format,
                mItem.spanX, mItem.spanY));
        if (!TextUtils.isEmpty(mItem.description)) {
            mWidgetDescription.setText(mItem.description);
            mWidgetDescription.setVisibility(VISIBLE);
        } else {
            mWidgetDescription.setVisibility(GONE);
        }

        // Setting the content description on the WidgetCell itself ensures that it remains
        // screen reader focusable when the add button is showing and the text is hidden.
        setContentDescription(createContentDescription(context));
        if (mWidgetAddButton != null) {
            mWidgetAddButton.setContentDescription(context.getString(
                    R.string.widget_add_button_content_description, mItem.label));
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
        } else if (Flags.enableGeneratedPreviews()
                && item.hasGeneratedPreview(WIDGET_CATEGORY_HOME_SCREEN)) {
            mAppWidgetHostViewPreview = createAppWidgetHostView(context);
            setAppWidgetHostViewPreview(mAppWidgetHostViewPreview, item.widgetInfo,
                    item.generatedPreviews.get(WIDGET_CATEGORY_HOME_SCREEN));
        } else if (item.hasPreviewLayout()) {
            // If the context is a Launcher activity, DragView will show mAppWidgetHostViewPreview
            // as a preview during drag & drop. And thus, we should use LauncherAppWidgetHostView,
            // which supports applying local color extraction during drag & drop.
            mAppWidgetHostViewPreview = isLauncherContext(context)
                    ? new LauncherAppWidgetHostView(context)
                    : createAppWidgetHostView(context);
            LauncherAppWidgetProviderInfo providerInfo =
                    fromProviderInfo(context, item.widgetInfo.clone());
            // A hack to force the initial layout to be the preview layout since there is no API for
            // rendering a preview layout for work profile apps yet. For non-work profile layout, a
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

    private void initPreviewContainerSizeAndScale() {
        WidgetPreviewContainerSize previewSize = WidgetPreviewContainerSize.Companion.forItem(mItem,
                mActivity.getDeviceProfile());
        mPreviewContainerSize = WidgetSizes.getWidgetSizePx(mActivity.getDeviceProfile(),
                previewSize.spanX, previewSize.spanY);

        float scaleX = (float) mPreviewContainerSize.getWidth() / mWidgetSize.getWidth();
        float scaleY = (float) mPreviewContainerSize.getHeight() / mWidgetSize.getHeight();
        mPreviewContainerScale = Math.min(scaleX, scaleY);
    }

    private String createContentDescription(Context context) {
        String contentDescription =
                context.getString(R.string.widget_preview_name_and_dims_content_description,
                        mItem.label, mItem.spanX, mItem.spanY);
        if (!TextUtils.isEmpty(mItem.description)) {
            contentDescription += " " + mItem.description;
        }
        return contentDescription;
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
                (v, l, t, r, b, ol, ot, or, ob) ->
                        updateAppWidgetHostScale(appWidgetHostViewPreview));
    }

    private void updateAppWidgetHostScale(NavigableAppWidgetHostView view) {
        // Scale the content such that all of the content is visible
        int contentWidth = view.getWidth();
        int contentHeight = view.getHeight();

        if (view.getChildCount() == 1) {
            View content = view.getChildAt(0);
            // Take the content width based on the edge furthest from the center, so that when
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
     * Shows or hides the long description displayed below each widget.
     *
     * @param show a flag that shows the long description of the widget if {@code true}, hides it if
     *             {@code false}.
     */
    public void showDescription(boolean show) {
        mWidgetDescription.setVisibility(show ? VISIBLE : GONE);
    }

    /**
     * Shows or hides the dimensions displayed below each widget.
     *
     * @param show a flag that shows the dimensions of the widget if {@code true}, hides it if
     *             {@code false}.
     */
    public void showDimensions(boolean show) {
        mWidgetDims.setVisibility(show ? VISIBLE : GONE);
    }

    /**
     * Set whether the app icon, for the app that provides the widget, should be shown next to the
     * title text of the widget.
     *
     * @param show true if the app icon should be shown in the title text of the cell, false hides
     *             it.
     */
    public void showAppIconInWidgetTitle(boolean show) {
        if (show) {
            if (mItem.widgetInfo != null) {
                loadHighResPackageIcon();

                Drawable icon = mItem.bitmap.newIcon(getContext());
                int size = getResources().getDimensionPixelSize(R.dimen.widget_cell_app_icon_size);
                icon.setBounds(0, 0, size, size);
                mWidgetName.setCompoundDrawablesRelative(
                        icon,
                        null, null, null);
            }
        } else {
            cancelIconLoadRequest();
            mWidgetName.setCompoundDrawables(null, null, null, null);
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
        int maxWidth = MeasureSpec.getSize(widthMeasureSpec);

        // mPreviewContainerScale ensures the needed scaling with respect to original widget size.
        mAppWidgetHostViewScale = mPreviewContainerScale;
        containerLp.width = mPreviewContainerSize.getWidth();
        containerLp.height = mPreviewContainerSize.getHeight();

        // If we don't have enough available width, scale the preview container to fit.
        if (containerLp.width > maxWidth) {
            containerLp.width = maxWidth;
            mAppWidgetHostViewScale = (float) containerLp.width / mPreviewContainerSize.getWidth();
            containerLp.height = Math.round(
                    mPreviewContainerSize.getHeight() * mAppWidgetHostViewScale);
        }

        // No need to call mWidgetImageContainer.setLayoutParams as we are in measure pass
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * Loads a high resolution package icon to show next to the widget title.
     */
    public void loadHighResPackageIcon() {
        cancelIconLoadRequest();
        if (mItem.bitmap.isLowRes()) {
            // We use the package icon instead of the receiver one so that the overall package that
            // the widget came from can be identified in the recommended widgets. This matches with
            // the package icon headings in the all widgets list.
            PackageItemInfo tmpPackageItem = new PackageItemInfo(
                    mItem.componentName.getPackageName(),
                    mItem.user);
            mIconLoadRequest = LauncherAppState.getInstance(getContext()).getIconCache()
                    .updateIconInBackground(this::reapplyIconInfo, tmpPackageItem);
        }
    }

    /** Can be called to update the package icon shown in the label of recommended widgets. */
    private void reapplyIconInfo(ItemInfoWithIcon info) {
        if (mItem == null || info.bitmap.isNullOrLowRes()) {
            showAppIconInWidgetTitle(false);
            return;
        }
        mItem.bitmap = info.bitmap;
        showAppIconInWidgetTitle(true);
    }

    private void cancelIconLoadRequest() {
        if (mIconLoadRequest != null) {
            mIconLoadRequest.cancel();
            mIconLoadRequest = null;
        }
    }

    /**
     * Show tap to add button.
     * @param callback Callback to be set on the button.
     */
    public void showAddButton(View.OnClickListener callback) {
        if (mIsShowingAddButton) return;
        mIsShowingAddButton = true;

        setupIconOrTextButton();
        mWidgetAddButton.setOnClickListener(callback);
        fadeThrough(/* hide= */ mWidgetTextContainer, /* show= */ mWidgetAddButton,
                ADD_BUTTON_FADE_DURATION_MS, Interpolators.LINEAR);
    }

    /**
     * Depending on the width of the cell, set up the add button to be icon-only or icon+text.
     */
    private void setupIconOrTextButton() {
        String addText = getResources().getString(R.string.widget_add_button_label);
        Rect textSize = new Rect();
        mWidgetAddButton.getPaint().getTextBounds(addText, 0, addText.length(), textSize);
        int startPadding = getResources()
                .getDimensionPixelSize(R.dimen.widget_cell_add_button_start_padding);
        int endPadding = getResources()
                .getDimensionPixelSize(R.dimen.widget_cell_add_button_end_padding);
        int drawableWidth = getResources()
                .getDimensionPixelSize(R.dimen.widget_cell_add_button_drawable_width);
        int drawablePadding = getResources()
                .getDimensionPixelSize(R.dimen.widget_cell_add_button_drawable_padding);
        int textButtonWidth = textSize.width() + startPadding + endPadding + drawableWidth
                + drawablePadding;
        if (textButtonWidth > getMeasuredWidth()) {
            // Setup icon-only button
            mWidgetAddButton.setText(null);
            int startIconPadding = getResources()
                    .getDimensionPixelSize(R.dimen.widget_cell_add_icon_button_start_padding);
            mWidgetAddButton.setPaddingRelative(/* start= */ startIconPadding, /* top= */ 0,
                    /* end= */ endPadding, /* bottom= */ 0);
            mWidgetAddButton.setCompoundDrawablePadding(0);
        } else {
            // Setup icon + text button
            mWidgetAddButton.setText(addText);
            mWidgetAddButton.setPaddingRelative(/* start= */ startPadding, /* top= */ 0,
                    /* end= */ endPadding, /* bottom= */ 0);
            mWidgetAddButton.setCompoundDrawablePadding(drawablePadding);
        }
    }

    /**
     * Hide tap to add button.
     */
    public void hideAddButton(boolean animate) {
        if (!mIsShowingAddButton) return;
        mIsShowingAddButton = false;

        mWidgetAddButton.setOnClickListener(null);

        if (!animate) {
            mWidgetAddButton.setVisibility(INVISIBLE);
            mWidgetTextContainer.setVisibility(VISIBLE);
            mWidgetTextContainer.setAlpha(1F);
            return;
        }

        fadeThrough(/* hide= */ mWidgetAddButton, /* show= */ mWidgetTextContainer,
                ADD_BUTTON_FADE_DURATION_MS, Interpolators.LINEAR);
    }

    public boolean isShowingAddButton() {
        return mIsShowingAddButton;
    }

    private static void fadeThrough(View hide, View show, int durationMs,
            TimeInterpolator interpolator) {
        AnimatedPropertySetter setter = new AnimatedPropertySetter();

        Animator hideAnim = setter.setViewAlpha(hide, 0F, interpolator).setDuration(durationMs);
        if (hideAnim instanceof ObjectAnimator anim) {
            anim.setAutoCancel(true);
        }

        Animator showAnim = setter.setViewAlpha(show, 1F, interpolator).setDuration(durationMs);
        if (showAnim instanceof ObjectAnimator anim) {
            anim.setAutoCancel(true);
        }

        AnimatorSet set = new AnimatorSet();
        set.playSequentially(hideAnim, showAnim);
        set.start();
    }
}
