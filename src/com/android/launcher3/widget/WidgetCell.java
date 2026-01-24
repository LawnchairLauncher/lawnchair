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

import static android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_WIDGETS_TRAY;
import static com.android.launcher3.icons.cache.CacheLookupFlag.DEFAULT_LOOKUP_FLAG;
import static com.android.launcher3.widget.util.WidgetSizes.getWidgetItemSizePx;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.appwidget.AppWidgetProviderInfo;
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

import androidx.annotation.Nullable;

import com.android.app.animation.Interpolators;
import com.android.launcher3.CheckLongPressHelper;
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
import com.android.launcher3.widget.DatabaseWidgetPreviewLoader.WidgetPreviewInfo;
import com.android.launcher3.widget.picker.util.WidgetPreviewContainerSize;
import com.android.launcher3.widget.util.WidgetSizes;

import java.util.function.Consumer;

import app.lawnchair.LawnchairAppWidgetHostView;
import app.lawnchair.font.FontManager;
import app.lawnchair.theme.drawable.DrawableTokens;
import app.lawnchair.preferences2.PreferenceManager2;
import com.patrykmichalik.opto.core.PreferenceExtensionsKt;

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

    PreferenceManager2 prefs2 = PreferenceManager2.INSTANCE.get(getContext());

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
    @Nullable
    private PreviewReadyListener mPreviewReadyListener = null;

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
    // Height enforced by the parent to align all widget cells displayed by it.
    private int mParentAlignedPreviewHeight;
    public WidgetCell(Context context) {
        this(context, null);
    }

    public WidgetCell(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WidgetCell(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mActivity = ActivityContext.lookupContext(context);
        mWidgetPreviewLoader = new DatabaseWidgetPreviewLoader(context,
                mActivity.getDeviceProfile());
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

        FontManager fontManager = FontManager.INSTANCE.get(getContext());
        fontManager.setCustomFont(mWidgetName, R.id.font_body_medium);
        fontManager.setCustomFont(mWidgetDescription, R.id.font_body);
        
        // LC: Allow customisability to the Add Button, Test: Press on any Widget on the Widget sheet.
        mWidgetAddButton.setBackground(DrawableTokens.WidgetAddButtonBackground.resolve(getContext()));
        fontManager.setCustomFont(mWidgetAddButton, R.id.font_body_medium);

        setAccessibilityDelegate(new AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host,
                    AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                if (hasOnClickListeners()) {
                    String accessibilityLabel = getResources().getString(
                            mWidgetAddButton.isShown()
                                    ? R.string.widget_cell_tap_to_hide_add_button_label
                                    : R.string.widget_cell_tap_to_show_add_button_label);
                    info.addAction(new AccessibilityNodeInfo.AccessibilityAction(ACTION_CLICK,
                            accessibilityLabel));
                }
            }
        });
        mWidgetAddButton.setVisibility(INVISIBLE);
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
        mPreviewReadyListener = null;
        mParentAlignedPreviewHeight = 0;
        showDescription(true);
        showDimensions(true);

        hideAddButton(/* animate= */ false);

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
            WidgetPreviewInfo previewInfo = new WidgetPreviewInfo();
            previewInfo.providerInfo = item.widgetInfo;
            previewInfo.remoteViews = mRemoteViewsPreview;
            applyPreview(previewInfo);
        } else {
            if (mActiveRequest == null) {
                mActiveRequest = mWidgetPreviewLoader.loadPreview(
                        mItem, mWidgetSize, this::applyPreview);
            }
        }
    }

    private void applyPreview(WidgetPreviewInfo previewInfo) {
        if (previewInfo.providerInfo != null) {
            mAppWidgetHostViewPreview = createAppWidgetHostView(getContext());
            setAppWidgetHostViewPreview(mAppWidgetHostViewPreview, previewInfo.providerInfo,
                    previewInfo.remoteViews);
        } else {
            applyBitmapPreview(previewInfo.previewBitmap);
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
            AppWidgetProviderInfo providerInfo,
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
        applyBitmapPreview(null);

        appWidgetHostViewPreview.addOnLayoutChangeListener(
                (v, l, t, r, b, ol, ot, or, ob) ->
                        updateAppWidgetHostScale(appWidgetHostViewPreview));
    }

    private void updateAppWidgetHostScale(NavigableAppWidgetHostView view) {
        // Scale the content such that all of the content is visible
        float contentWidth = view.getWidth();
        float contentHeight = view.getHeight();

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

        // layout based previews maybe ready at this point to inspect their inner height.
        if (mPreviewReadyListener != null) {
            mPreviewReadyListener.onPreviewAvailable();
            mPreviewReadyListener = null;
        }
    }

    /**
     * Returns a view (holding the previews) that can be dragged and dropped.
     */
    public View getDragAndDropView() {
        return mWidgetImageContainer;
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

    private void applyBitmapPreview(Bitmap bitmap) {
        if (bitmap != null) {
            Drawable drawable = new RoundDrawableWrapper(
                    new FastBitmapDrawable(bitmap), mEnforcedCornerRadius);
            mWidgetImage.setDrawable(drawable);
            mWidgetImage.setVisibility(View.VISIBLE);
            if (mAppWidgetHostViewPreview != null) {
                removeView(mAppWidgetHostViewPreview);
                mAppWidgetHostViewPreview = null;
            }

            // Drawables of the image previews are available at this point to measure.
            if (mPreviewReadyListener != null) {
                mPreviewReadyListener.onPreviewAvailable();
                mPreviewReadyListener = null;
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

    private static LauncherAppWidgetHostView createAppWidgetHostView(Context context) {
        return new LauncherAppWidgetHostView(context) {
            @Override
            protected boolean shouldAllowDirectClick() {
                return false;
            }
        };
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return WidgetCell.class.getName();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        ViewGroup.LayoutParams containerLp = mWidgetImageContainer.getLayoutParams();
        int maxWidth = MeasureSpec.getSize(widthMeasureSpec);

        // mPreviewContainerScale ensures the needed scaling with respect to original widget size.
        mAppWidgetHostViewScale = mPreviewContainerScale;
        containerLp.width = mPreviewContainerSize.getWidth();
        int height = mPreviewContainerSize.getHeight();

        // If we don't have enough available width, scale the preview container to fit.
        if (containerLp.width > maxWidth) {
            containerLp.width = maxWidth;
            mAppWidgetHostViewScale = (float) containerLp.width / mPreviewContainerSize.getWidth();
            height = Math.round(mPreviewContainerSize.getHeight() * mAppWidgetHostViewScale);
        }

        // Use parent aligned height in set.
        if (mParentAlignedPreviewHeight > 0) {
            containerLp.height = Math.min(height, mParentAlignedPreviewHeight);
        } else {
            containerLp.height = height;
        }

        // No need to call mWidgetImageContainer.setLayoutParams as we are in measure pass
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        if (changed && isShowingAddButton()) {
            post(this::setupIconOrTextButton);
        }
    }

    /**
     * Sets the height of the preview as adjusted by the parent to have this cell's content aligned
     * with other cells displayed by the parent.
     */
    public void setParentAlignedPreviewHeight(int previewHeight) {
        mParentAlignedPreviewHeight = previewHeight;
    }

    /**
     * Returns the height of the preview without any empty space.
     * In case of appwidget host views, it returns the height of first child. This way, if preview
     * view provided by an app doesn't fill bounds, this will return actual height without white
     * space.
     */
    public int getPreviewContentHeight() {
        // By default assume scaled height.
        int height = Math.round(mPreviewContainerScale * mWidgetSize.getHeight());

        if (mWidgetImage != null && mWidgetImage.getDrawable() != null) {
            // getBitmapBounds returns the scaled bounds.
            Rect bitmapBounds = mWidgetImage.getBitmapBounds();
            height = bitmapBounds.height();
        } else if (mAppWidgetHostViewPreview != null
                && mAppWidgetHostViewPreview.getChildCount() == 1) {
            int contentHeight = Math.round(
                    mPreviewContainerScale * mWidgetSize.getHeight());
            int previewInnerHeight = Math.round(
                    mAppWidgetHostViewScale * mAppWidgetHostViewPreview.getChildAt(
                            0).getMeasuredHeight());
            // Use either of the inner scaled height or the scaled widget height
            height = Math.min(contentHeight, previewInnerHeight);
        }

        return height;
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
                    .updateIconInBackground(this::reapplyIconInfo, tmpPackageItem,
                            DEFAULT_LOOKUP_FLAG);
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
        if (PreferenceExtensionsKt.firstBlocking(prefs2.getLockHomeScreen())) return;
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

    /**
     * Returns true if this WidgetCell is displaying the same item as info.
     */
    public boolean matchesItem(WidgetItem info) {
        if (info == null || mItem == null) return false;
        if (info.widgetInfo != null && mItem.widgetInfo != null) {
            return info.widgetInfo.getUser().equals(mItem.widgetInfo.getUser())
                    && info.widgetInfo.getComponent().equals(mItem.widgetInfo.getComponent());
        } else if (info.activityInfo != null && mItem.activityInfo != null) {
            return info.activityInfo.getUser().equals(mItem.activityInfo.getUser())
                    && info.activityInfo.getComponent().equals(mItem.activityInfo.getComponent());
        }
        return false;
    }

    /**
     * Listener to notify when previews are available.
     */
    public void addPreviewReadyListener(PreviewReadyListener previewReadyListener) {
        mPreviewReadyListener = previewReadyListener;
    }

    /**
     * Listener interface for subscribers to listen to preview's availability.
     */
    public interface PreviewReadyListener {
        /** Handler on to invoke when previews are available. */
        void onPreviewAvailable();
    }
}
