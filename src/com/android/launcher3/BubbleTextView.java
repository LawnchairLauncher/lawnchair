/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static com.android.launcher3.config.FeatureFlags.ENABLE_ICON_LABEL_AUTO_SCALING;
import static com.android.launcher3.graphics.PreloadIconDrawable.newPendingIcon;
import static com.android.launcher3.icons.GraphicsUtils.setColorAlphaBound;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.icu.text.MessageFormat;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.util.Property;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.dot.DotInfo;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.graphics.IconPalette;
import com.android.launcher3.graphics.IconShape;
import com.android.launcher3.graphics.PreloadIconDrawable;
import com.android.launcher3.icons.DotRenderer;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.icons.IconCache.ItemInfoUpdateReceiver;
import com.android.launcher3.icons.PlaceHolderIconDrawable;
import com.android.launcher3.icons.cache.HandlerRunnable;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.model.data.SearchActionItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BubbleTextHolder;
import com.android.launcher3.views.IconLabelDotView;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;

/**
 * TextView that draws a bubble behind the text. We cannot use a LineBackgroundSpan
 * because we want to make the bubble taller than the text and TextView's clip is
 * too aggressive.
 */
public class BubbleTextView extends TextView implements ItemInfoUpdateReceiver,
        IconLabelDotView, DraggableView, Reorderable {

    private static final int DISPLAY_WORKSPACE = 0;
    private static final int DISPLAY_ALL_APPS = 1;
    private static final int DISPLAY_FOLDER = 2;
    protected static final int DISPLAY_TASKBAR = 5;
    private static final int DISPLAY_SEARCH_RESULT = 6;
    private static final int DISPLAY_SEARCH_RESULT_SMALL = 7;

    private static final float MIN_LETTER_SPACING = -0.05f;
    private static final int MAX_SEARCH_LOOP_COUNT = 20;

    private static final int[] STATE_PRESSED = new int[]{android.R.attr.state_pressed};
    private static final float HIGHLIGHT_SCALE = 1.16f;

    private final PointF mTranslationForReorderBounce = new PointF(0, 0);
    private final PointF mTranslationForReorderPreview = new PointF(0, 0);

    private float mTranslationXForTaskbarAlignmentAnimation = 0f;

    private final PointF mTranslationForMoveFromCenterAnimation = new PointF(0, 0);

    private float mScaleForReorderBounce = 1f;

    private static final Property<BubbleTextView, Float> DOT_SCALE_PROPERTY
            = new Property<BubbleTextView, Float>(Float.TYPE, "dotScale") {
        @Override
        public Float get(BubbleTextView bubbleTextView) {
            return bubbleTextView.mDotParams.scale;
        }

        @Override
        public void set(BubbleTextView bubbleTextView, Float value) {
            bubbleTextView.mDotParams.scale = value;
            bubbleTextView.invalidate();
        }
    };

    public static final Property<BubbleTextView, Float> TEXT_ALPHA_PROPERTY
            = new Property<BubbleTextView, Float>(Float.class, "textAlpha") {
        @Override
        public Float get(BubbleTextView bubbleTextView) {
            return bubbleTextView.mTextAlpha;
        }

        @Override
        public void set(BubbleTextView bubbleTextView, Float alpha) {
            bubbleTextView.setTextAlpha(alpha);
        }
    };

    private final ActivityContext mActivity;
    private FastBitmapDrawable mIcon;
    private boolean mCenterVertically;

    protected final int mDisplay;

    private final CheckLongPressHelper mLongPressHelper;

    private final boolean mLayoutHorizontal;
    private final boolean mIsRtl;
    private final int mIconSize;

    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mIsIconVisible = true;
    @ViewDebug.ExportedProperty(category = "launcher")
    private int mTextColor;
    @ViewDebug.ExportedProperty(category = "launcher")
    private float mTextAlpha = 1;

    @ViewDebug.ExportedProperty(category = "launcher")
    private DotInfo mDotInfo;
    private DotRenderer mDotRenderer;
    @ViewDebug.ExportedProperty(category = "launcher", deepExport = true)
    protected DotRenderer.DrawParams mDotParams;
    private Animator mDotScaleAnim;
    private boolean mForceHideDot;

    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mStayPressed;
    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mIgnorePressedStateChange;
    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mDisableRelayout = false;

    private HandlerRunnable mIconLoadRequest;

    private boolean mEnableIconUpdateAnimation = false;
    private BubbleTextHolder mBubbleTextHolder;

    public BubbleTextView(Context context) {
        this(context, null, 0);
    }

    public BubbleTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BubbleTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mActivity = ActivityContext.lookupContext(context);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.BubbleTextView, defStyle, 0);
        mLayoutHorizontal = a.getBoolean(R.styleable.BubbleTextView_layoutHorizontal, false);
        mIsRtl = (getResources().getConfiguration().getLayoutDirection()
                == View.LAYOUT_DIRECTION_RTL);
        DeviceProfile grid = mActivity.getDeviceProfile();

        mDisplay = a.getInteger(R.styleable.BubbleTextView_iconDisplay, DISPLAY_WORKSPACE);
        final int defaultIconSize;
        if (mDisplay == DISPLAY_WORKSPACE) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, grid.iconTextSizePx);
            setCompoundDrawablePadding(grid.iconDrawablePaddingPx);
            defaultIconSize = grid.iconSizePx;
            setCenterVertically(grid.isScalableGrid);
        } else if (mDisplay == DISPLAY_ALL_APPS) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, grid.allAppsIconTextSizePx);
            setCompoundDrawablePadding(grid.allAppsIconDrawablePaddingPx);
            defaultIconSize = grid.allAppsIconSizePx;
        } else if (mDisplay == DISPLAY_FOLDER) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, grid.folderChildTextSizePx);
            setCompoundDrawablePadding(grid.folderChildDrawablePaddingPx);
            defaultIconSize = grid.folderChildIconSizePx;
        } else if (mDisplay == DISPLAY_SEARCH_RESULT) {
            defaultIconSize = getResources().getDimensionPixelSize(R.dimen.search_row_icon_size);
        } else if (mDisplay == DISPLAY_SEARCH_RESULT_SMALL) {
            defaultIconSize = getResources().getDimensionPixelSize(
                    R.dimen.search_row_small_icon_size);
        } else if (mDisplay == DISPLAY_TASKBAR) {
            defaultIconSize = grid.iconSizePx;
        } else {
            // widget_selection or shortcut_popup
            defaultIconSize = grid.iconSizePx;
        }

        mCenterVertically = a.getBoolean(R.styleable.BubbleTextView_centerVertically, false);

        mIconSize = a.getDimensionPixelSize(R.styleable.BubbleTextView_iconSizeOverride,
                defaultIconSize);
        a.recycle();

        mLongPressHelper = new CheckLongPressHelper(this);

        mDotParams = new DotRenderer.DrawParams();

        setEllipsize(TruncateAt.END);
        setAccessibilityDelegate(mActivity.getAccessibilityDelegate());
        setTextAlpha(1f);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        // Disable marques when not focused to that, so that updating text does not cause relayout.
        setEllipsize(focused ? TruncateAt.MARQUEE : TruncateAt.END);
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    /**
     * Resets the view so it can be recycled.
     */
    public void reset() {
        mDotInfo = null;
        mDotParams.color = Color.TRANSPARENT;
        cancelDotScaleAnim();
        mDotParams.scale = 0f;
        mForceHideDot = false;
        setBackground(null);
    }

    private void cancelDotScaleAnim() {
        if (mDotScaleAnim != null) {
            mDotScaleAnim.cancel();
        }
    }

    private void animateDotScale(float... dotScales) {
        cancelDotScaleAnim();
        mDotScaleAnim = ObjectAnimator.ofFloat(this, DOT_SCALE_PROPERTY, dotScales);
        mDotScaleAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mDotScaleAnim = null;
            }
        });
        mDotScaleAnim.start();
    }

    @UiThread
    public void applyFromWorkspaceItem(WorkspaceItemInfo info) {
        applyFromWorkspaceItem(info, /* animate = */ false, /* staggerIndex = */ 0);
    }

    @UiThread
    public void applyFromWorkspaceItem(WorkspaceItemInfo info, boolean animate, int staggerIndex) {
        applyFromWorkspaceItem(info, false);
    }

    /**
     * Returns whether the newInfo differs from the current getTag().
     */
    public boolean shouldAnimateIconChange(WorkspaceItemInfo newInfo) {
        WorkspaceItemInfo oldInfo = getTag() instanceof WorkspaceItemInfo
                ? (WorkspaceItemInfo) getTag()
                : null;
        boolean changedIcons = oldInfo != null && oldInfo.getTargetComponent() != null
                && newInfo.getTargetComponent() != null
                && !oldInfo.getTargetComponent().equals(newInfo.getTargetComponent());
        return changedIcons && isShown();
    }

    @Override
    public void setAccessibilityDelegate(AccessibilityDelegate delegate) {
        if (delegate instanceof LauncherAccessibilityDelegate) {
            super.setAccessibilityDelegate(delegate);
        } else {
            // NO-OP
            // Workaround for b/129745295 where RecyclerView is setting our Accessibility
            // delegate incorrectly. There are no cases when we shouldn't be using the
            // LauncherAccessibilityDelegate for BubbleTextView.
        }
    }

    @UiThread
    public void applyFromWorkspaceItem(WorkspaceItemInfo info, boolean promiseStateChanged) {
        applyIconAndLabel(info);
        setItemInfo(info);
        applyLoadingState(promiseStateChanged);
        applyDotState(info, false /* animate */);
        setDownloadStateContentDescription(info, info.getProgressLevel());
    }

    @UiThread
    public void applyFromApplicationInfo(AppInfo info) {
        applyIconAndLabel(info);

        // We don't need to check the info since it's not a WorkspaceItemInfo
        setItemInfo(info);


        // Verify high res immediately
        verifyHighRes();

        if ((info.runtimeStatusFlags & ItemInfoWithIcon.FLAG_SHOW_DOWNLOAD_PROGRESS_MASK) != 0) {
            applyProgressLevel();
        }
        applyDotState(info, false /* animate */);
        setDownloadStateContentDescription(info, info.getProgressLevel());
    }

    /**
     * Apply label and tag using a generic {@link ItemInfoWithIcon}
     */
    @UiThread
    public void applyFromItemInfoWithIcon(ItemInfoWithIcon info) {
        applyIconAndLabel(info);
        // We don't need to check the info since it's not a WorkspaceItemInfo
        setItemInfo(info);

        // Verify high res immediately
        verifyHighRes();

        setDownloadStateContentDescription(info, info.getProgressLevel());
    }

    private void setItemInfo(ItemInfoWithIcon itemInfo) {
        setTag(itemInfo);
        if (mBubbleTextHolder != null) {
            mBubbleTextHolder.onItemInfoUpdated(itemInfo);
        }
    }

    public void setBubbleTextHolder(
            BubbleTextHolder bubbleTextHolder) {
        mBubbleTextHolder = bubbleTextHolder;
    }

    @UiThread
    protected void applyIconAndLabel(ItemInfoWithIcon info) {
        boolean useTheme = mDisplay == DISPLAY_WORKSPACE || mDisplay == DISPLAY_FOLDER
                || mDisplay == DISPLAY_TASKBAR;
        FastBitmapDrawable iconDrawable = info.newIcon(getContext(), useTheme);
        mDotParams.color = IconPalette.getMutedColor(iconDrawable.getIconColor(), 0.54f);

        setIcon(iconDrawable);
        applyLabel(info);
    }

    @UiThread
    private void applyLabel(ItemInfoWithIcon info) {
        setText(info.title);
        if (info.contentDescription != null) {
            setContentDescription(info.isDisabled()
                    ? getContext().getString(R.string.disabled_app_label, info.contentDescription)
                    : info.contentDescription);
        }
    }

    /**
     * Overrides the default long press timeout.
     */
    public void setLongPressTimeoutFactor(float longPressTimeoutFactor) {
        mLongPressHelper.setLongPressTimeoutFactor(longPressTimeoutFactor);
    }

    @Override
    public void refreshDrawableState() {
        if (!mIgnorePressedStateChange) {
            super.refreshDrawableState();
        }
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (mStayPressed) {
            mergeDrawableStates(drawableState, STATE_PRESSED);
        }
        return drawableState;
    }

    /** Returns the icon for this view. */
    public FastBitmapDrawable getIcon() {
        return mIcon;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // ignore events if they happen in padding area
        if (event.getAction() == MotionEvent.ACTION_DOWN
                && shouldIgnoreTouchDown(event.getX(), event.getY())) {
            return false;
        }
        if (isLongClickable()) {
            super.onTouchEvent(event);
            mLongPressHelper.onTouchEvent(event);
            // Keep receiving the rest of the events
            return true;
        } else {
            return super.onTouchEvent(event);
        }
    }

    /**
     * Returns true if the touch down at the provided position be ignored
     */
    protected boolean shouldIgnoreTouchDown(float x, float y) {
        if (mDisplay == DISPLAY_TASKBAR) {
            // Allow touching within padding on taskbar, given icon sizes are smaller.
            return false;
        }
        return y < getPaddingTop()
                || x < getPaddingLeft()
                || y > getHeight() - getPaddingBottom()
                || x > getWidth() - getPaddingRight();
    }

    void setStayPressed(boolean stayPressed) {
        mStayPressed = stayPressed;
        refreshDrawableState();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (mIcon != null) {
            mIcon.setVisible(isVisible, false);
        }
    }

    public void clearPressedBackground() {
        setPressed(false);
        setStayPressed(false);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // Unlike touch events, keypress event propagate pressed state change immediately,
        // without waiting for onClickHandler to execute. Disable pressed state changes here
        // to avoid flickering.
        mIgnorePressedStateChange = true;
        boolean result = super.onKeyUp(keyCode, event);
        mIgnorePressedStateChange = false;
        refreshDrawableState();
        return result;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        checkForEllipsis();
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        checkForEllipsis();
    }

    private void checkForEllipsis() {
        if (!ENABLE_ICON_LABEL_AUTO_SCALING.get()) {
            return;
        }
        float width = getWidth() - getCompoundPaddingLeft() - getCompoundPaddingRight();
        if (width <= 0) {
            return;
        }
        setLetterSpacing(0);

        String text = getText().toString();
        TextPaint paint = getPaint();
        if (paint.measureText(text) < width) {
            return;
        }

        float spacing = findBestSpacingValue(paint, text, width, MIN_LETTER_SPACING);
        // Reset the paint value so that the call to TextView does appropriate diff.
        paint.setLetterSpacing(0);
        setLetterSpacing(spacing);
    }

    /**
     * Find the appropriate text spacing to display the provided text
     * @param paint the paint used by the text view
     * @param text the text to display
     * @param allowedWidthPx available space to render the text
     * @param minSpacingEm minimum spacing allowed between characters
     * @return the final textSpacing value
     *
     * @see #setLetterSpacing(float)
     */
    private float findBestSpacingValue(TextPaint paint, String text, float allowedWidthPx,
            float minSpacingEm) {
        paint.setLetterSpacing(minSpacingEm);
        if (paint.measureText(text) > allowedWidthPx) {
            // If there is no result at high limit, we can do anything more
            return minSpacingEm;
        }

        float lowLimit = 0;
        float highLimit = minSpacingEm;

        for (int i = 0; i < MAX_SEARCH_LOOP_COUNT; i++) {
            float value = (lowLimit + highLimit) / 2;
            paint.setLetterSpacing(value);
            if (paint.measureText(text) < allowedWidthPx) {
                highLimit = value;
            } else {
                lowLimit = value;
            }
        }

        // At the end error on the higher side
        return highLimit;
    }

    @SuppressWarnings("wrongcall")
    protected void drawWithoutDot(Canvas canvas) {
        super.onDraw(canvas);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawDotIfNecessary(canvas);
    }

    /**
     * Draws the notification dot in the top right corner of the icon bounds.
     *
     * @param canvas The canvas to draw to.
     */
    protected void drawDotIfNecessary(Canvas canvas) {
        if (!mForceHideDot && (hasDot() || mDotParams.scale > 0)) {
            getIconBounds(mDotParams.iconBounds);
            Utilities.scaleRectAboutCenter(mDotParams.iconBounds,
                    IconShape.getNormalizationScale());
            final int scrollX = getScrollX();
            final int scrollY = getScrollY();
            canvas.translate(scrollX, scrollY);
            mDotRenderer.draw(canvas, mDotParams);
            canvas.translate(-scrollX, -scrollY);
        }
    }

    @Override
    public void setForceHideDot(boolean forceHideDot) {
        if (mForceHideDot == forceHideDot) {
            return;
        }
        mForceHideDot = forceHideDot;

        if (forceHideDot) {
            invalidate();
        } else if (hasDot()) {
            animateDotScale(0, 1);
        }
    }

    private boolean hasDot() {
        return mDotInfo != null;
    }

    /**
     * Get the icon bounds on the view depending on the layout type.
     */
    public void getIconBounds(Rect outBounds) {
        getIconBounds(mIconSize, outBounds);
    }

    /**
     * Get the icon bounds on the view depending on the layout type.
     */
    public void getIconBounds(int iconSize, Rect outBounds) {
        Utilities.setRectToViewCenter(this, iconSize, outBounds);
        if (mLayoutHorizontal) {
            if (mIsRtl) {
                outBounds.offsetTo(getWidth() - iconSize - getPaddingRight(), outBounds.top);
            } else {
                outBounds.offsetTo(getPaddingLeft(), outBounds.top);
            }
        } else {
            outBounds.offsetTo(outBounds.left, getPaddingTop());
        }
    }

    /**
     * Sets whether to vertically center the content.
     */
    public void setCenterVertically(boolean centerVertically) {
        mCenterVertically = centerVertically;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mCenterVertically) {
            Paint.FontMetrics fm = getPaint().getFontMetrics();
            int cellHeightPx = mIconSize + getCompoundDrawablePadding() +
                    (int) Math.ceil(fm.bottom - fm.top);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            setPadding(getPaddingLeft(), (height - cellHeightPx) / 2, getPaddingRight(),
                    getPaddingBottom());
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public void setTextColor(int color) {
        mTextColor = color;
        super.setTextColor(getModifiedColor());
    }

    @Override
    public void setTextColor(ColorStateList colors) {
        mTextColor = colors.getDefaultColor();
        if (Float.compare(mTextAlpha, 1) == 0) {
            super.setTextColor(colors);
        } else {
            super.setTextColor(getModifiedColor());
        }
    }

    public boolean shouldTextBeVisible() {
        // Text should be visible everywhere but the hotseat.
        Object tag = getParent() instanceof FolderIcon ? ((View) getParent()).getTag() : getTag();
        ItemInfo info = tag instanceof ItemInfo ? (ItemInfo) tag : null;
        return info == null || (info.container != LauncherSettings.Favorites.CONTAINER_HOTSEAT
                && info.container != LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION);
    }

    public void setTextVisibility(boolean visible) {
        setTextAlpha(visible ? 1 : 0);
    }

    private void setTextAlpha(float alpha) {
        mTextAlpha = alpha;
        super.setTextColor(getModifiedColor());
    }

    private int getModifiedColor() {
        if (mTextAlpha == 0) {
            // Special case to prevent text shadows in high contrast mode
            return Color.TRANSPARENT;
        }
        return setColorAlphaBound(mTextColor, Math.round(Color.alpha(mTextColor) * mTextAlpha));
    }

    /**
     * Creates an animator to fade the text in or out.
     *
     * @param fadeIn Whether the text should fade in or fade out.
     */
    public ObjectAnimator createTextAlphaAnimator(boolean fadeIn) {
        float toAlpha = shouldTextBeVisible() && fadeIn ? 1 : 0;
        return ObjectAnimator.ofFloat(this, TEXT_ALPHA_PROPERTY, toAlpha);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mLongPressHelper.cancelLongPress();
    }

    /**
     * Applies the loading progress value to the progress bar.
     *
     * If this app is installing, the progress bar will be updated with the installation progress.
     * If this app is installed and downloading incrementally, the progress bar will be updated
     * with the total download progress.
     */
    public void applyLoadingState(boolean promiseStateChanged) {
        if (getTag() instanceof ItemInfoWithIcon) {
            WorkspaceItemInfo info = (WorkspaceItemInfo) getTag();
            if ((info.runtimeStatusFlags & ItemInfoWithIcon.FLAG_INCREMENTAL_DOWNLOAD_ACTIVE)
                    != 0) {
                updateProgressBarUi(info.getProgressLevel() == 100);
            } else if (info.hasPromiseIconUi() || (info.runtimeStatusFlags
                        & ItemInfoWithIcon.FLAG_INSTALL_SESSION_ACTIVE) != 0) {
                updateProgressBarUi(promiseStateChanged);
            }
        }
    }

    private void updateProgressBarUi(boolean maybePerformFinishedAnimation) {
        PreloadIconDrawable preloadDrawable = applyProgressLevel();
        if (preloadDrawable != null && maybePerformFinishedAnimation) {
            preloadDrawable.maybePerformFinishedAnimation();
        }
    }

    /** Applies the given progress level to the this icon's progress bar. */
    @Nullable
    public PreloadIconDrawable applyProgressLevel() {
        if (!(getTag() instanceof ItemInfoWithIcon)) {
            return null;
        }

        ItemInfoWithIcon info = (ItemInfoWithIcon) getTag();
        int progressLevel = info.getProgressLevel();
        if (progressLevel >= 100) {
            setContentDescription(info.contentDescription != null
                    ? info.contentDescription : "");
        } else if (progressLevel > 0) {
            setDownloadStateContentDescription(info, progressLevel);
        } else {
            setContentDescription(getContext()
                    .getString(R.string.app_waiting_download_title, info.title));
        }
        if (mIcon != null) {
            PreloadIconDrawable preloadIconDrawable;
            if (mIcon instanceof PreloadIconDrawable) {
                preloadIconDrawable = (PreloadIconDrawable) mIcon;
                preloadIconDrawable.setLevel(progressLevel);
                preloadIconDrawable.setIsDisabled(!info.isAppStartable());
            } else {
                preloadIconDrawable = makePreloadIcon();
                setIcon(preloadIconDrawable);
            }
            return preloadIconDrawable;
        }
        return null;
    }

    /**
     * Creates a PreloadIconDrawable with the appropriate progress level without mutating this
     * object.
     */
    @Nullable
    public PreloadIconDrawable makePreloadIcon() {
        if (!(getTag() instanceof ItemInfoWithIcon)) {
            return null;
        }

        ItemInfoWithIcon info = (ItemInfoWithIcon) getTag();
        int progressLevel = info.getProgressLevel();
        final PreloadIconDrawable preloadDrawable = newPendingIcon(getContext(), info);

        preloadDrawable.setLevel(progressLevel);
        preloadDrawable.setIsDisabled(!info.isAppStartable());

        return preloadDrawable;
    }

    public void applyDotState(ItemInfo itemInfo, boolean animate) {
        if (mIcon instanceof FastBitmapDrawable) {
            boolean wasDotted = mDotInfo != null;
            mDotInfo = mActivity.getDotInfoForItem(itemInfo);
            boolean isDotted = mDotInfo != null;
            float newDotScale = isDotted ? 1f : 0;
            if (mDisplay == DISPLAY_ALL_APPS) {
                mDotRenderer = mActivity.getDeviceProfile().mDotRendererAllApps;
            } else {
                mDotRenderer = mActivity.getDeviceProfile().mDotRendererWorkSpace;
            }
            if (wasDotted || isDotted) {
                // Animate when a dot is first added or when it is removed.
                if (animate && (wasDotted ^ isDotted) && isShown()) {
                    animateDotScale(newDotScale);
                } else {
                    cancelDotScaleAnim();
                    mDotParams.scale = newDotScale;
                    invalidate();
                }
            }
            if (!TextUtils.isEmpty(itemInfo.contentDescription)) {
                if (itemInfo.isDisabled()) {
                    setContentDescription(getContext().getString(R.string.disabled_app_label,
                            itemInfo.contentDescription));
                } else if (hasDot()) {
                    int count = mDotInfo.getNotificationCount();
                    setContentDescription(
                            getAppLabelPluralString(itemInfo.contentDescription.toString(), count));
                } else {
                    setContentDescription(itemInfo.contentDescription);
                }
            }
        }
    }

    private void setDownloadStateContentDescription(ItemInfoWithIcon info, int progressLevel) {
        if ((info.runtimeStatusFlags & ItemInfoWithIcon.FLAG_SHOW_DOWNLOAD_PROGRESS_MASK)
                != 0) {
            String percentageString = NumberFormat.getPercentInstance()
                    .format(progressLevel * 0.01);
            if ((info.runtimeStatusFlags & ItemInfoWithIcon.FLAG_INSTALL_SESSION_ACTIVE) != 0) {
                setContentDescription(getContext()
                        .getString(
                            R.string.app_installing_title, info.title, percentageString));
            } else if ((info.runtimeStatusFlags
                    & ItemInfoWithIcon.FLAG_INCREMENTAL_DOWNLOAD_ACTIVE) != 0) {
                setContentDescription(getContext()
                        .getString(
                            R.string.app_downloading_title, info.title, percentageString));
            }
        }
    }

    /**
     * Sets the icon for this view based on the layout direction.
     */
    protected void setIcon(FastBitmapDrawable icon) {
        if (mIsIconVisible) {
            applyCompoundDrawables(icon);
        }
        mIcon = icon;
        if (mIcon != null) {
            mIcon.setVisible(getWindowVisibility() == VISIBLE && isShown(), false);
        }
    }

    @Override
    public void setIconVisible(boolean visible) {
        mIsIconVisible = visible;
        if (!mIsIconVisible) {
            resetIconScale();
        }
        Drawable icon = visible ? mIcon : new ColorDrawable(Color.TRANSPARENT);
        applyCompoundDrawables(icon);
    }

    protected boolean iconUpdateAnimationEnabled() {
        return mEnableIconUpdateAnimation;
    }

    protected void applyCompoundDrawables(Drawable icon) {
        // If we had already set an icon before, disable relayout as the icon size is the
        // same as before.
        mDisableRelayout = mIcon != null;

        icon.setBounds(0, 0, mIconSize, mIconSize);

        updateIcon(icon);

        // If the current icon is a placeholder color, animate its update.
        if (mIcon != null
                && mIcon instanceof PlaceHolderIconDrawable
                && iconUpdateAnimationEnabled()) {
            ((PlaceHolderIconDrawable) mIcon).animateIconUpdate(icon);
        }

        mDisableRelayout = false;
    }

    @Override
    public void requestLayout() {
        if (!mDisableRelayout) {
            super.requestLayout();
        }
    }

    /**
     * Applies the item info if it is same as what the view is pointing to currently.
     */
    @Override
    public void reapplyItemInfo(ItemInfoWithIcon info) {
        if (getTag() == info) {
            mIconLoadRequest = null;
            mDisableRelayout = true;
            mEnableIconUpdateAnimation = true;

            // Optimization: Starting in N, pre-uploads the bitmap to RenderThread.
            info.bitmap.icon.prepareToDraw();

            if (info instanceof AppInfo) {
                applyFromApplicationInfo((AppInfo) info);
            } else if (info instanceof WorkspaceItemInfo) {
                applyFromWorkspaceItem((WorkspaceItemInfo) info);
                mActivity.invalidateParent(info);
            } else if (info instanceof PackageItemInfo) {
                applyFromItemInfoWithIcon((PackageItemInfo) info);
            } else if (info instanceof SearchActionItemInfo) {
                applyFromItemInfoWithIcon((SearchActionItemInfo) info);
            }

            mDisableRelayout = false;
            mEnableIconUpdateAnimation = false;
        }
    }

    /**
     * Verifies that the current icon is high-res otherwise posts a request to load the icon.
     */
    public void verifyHighRes() {
        if (mIconLoadRequest != null) {
            mIconLoadRequest.cancel();
            mIconLoadRequest = null;
        }
        if (getTag() instanceof ItemInfoWithIcon) {
            ItemInfoWithIcon info = (ItemInfoWithIcon) getTag();
            if (info.usingLowResIcon()) {
                mIconLoadRequest = LauncherAppState.getInstance(getContext()).getIconCache()
                        .updateIconInBackground(BubbleTextView.this, info);
            }
        }
    }

    public int getIconSize() {
        return mIconSize;
    }

    private void updateTranslation() {
        super.setTranslationX(mTranslationForReorderBounce.x + mTranslationForReorderPreview.x
                + mTranslationForMoveFromCenterAnimation.x
                + mTranslationXForTaskbarAlignmentAnimation);
        super.setTranslationY(mTranslationForReorderBounce.y + mTranslationForReorderPreview.y
                + mTranslationForMoveFromCenterAnimation.y);
    }

    public void setReorderBounceOffset(float x, float y) {
        mTranslationForReorderBounce.set(x, y);
        updateTranslation();
    }

    public void getReorderBounceOffset(PointF offset) {
        offset.set(mTranslationForReorderBounce);
    }

    @Override
    public void setReorderPreviewOffset(float x, float y) {
        mTranslationForReorderPreview.set(x, y);
        updateTranslation();
    }

    @Override
    public void getReorderPreviewOffset(PointF offset) {
        offset.set(mTranslationForReorderPreview);
    }

    public void setReorderBounceScale(float scale) {
        mScaleForReorderBounce = scale;
        super.setScaleX(scale);
        super.setScaleY(scale);
    }

    public float getReorderBounceScale() {
        return mScaleForReorderBounce;
    }

    /**
     * Sets translation values for move from center animation
     */
    public void setTranslationForMoveFromCenterAnimation(float x, float y) {
        mTranslationForMoveFromCenterAnimation.set(x, y);
        updateTranslation();
    }

    /**
     * Sets translationX for taskbar to launcher alignment animation
     */
    public void setTranslationXForTaskbarAlignmentAnimation(float translationX) {
        mTranslationXForTaskbarAlignmentAnimation = translationX;
        updateTranslation();
    }

    /**
     * Returns translationX value for taskbar to launcher alignment animation
     */
    public float getTranslationXForTaskbarAlignmentAnimation() {
        return mTranslationXForTaskbarAlignmentAnimation;
    }

    public View getView() {
        return this;
    }

    @Override
    public int getViewType() {
        return DRAGGABLE_ICON;
    }

    @Override
    public void getWorkspaceVisualDragBounds(Rect bounds) {
        getIconBounds(mIconSize, bounds);
    }

    private int getIconSizeForDisplay(int display) {
        DeviceProfile grid = mActivity.getDeviceProfile();
        switch (display) {
            case DISPLAY_ALL_APPS:
                return grid.allAppsIconSizePx;
            case DISPLAY_FOLDER:
                return grid.folderChildIconSizePx;
            case DISPLAY_WORKSPACE:
            default:
                return grid.iconSizePx;
        }
    }

    public void getSourceVisualDragBounds(Rect bounds) {
        getIconBounds(mIconSize, bounds);
    }

    @Override
    public SafeCloseable prepareDrawDragView() {
        resetIconScale();
        setForceHideDot(true);
        return () -> { };
    }

    private void resetIconScale() {
        if (mIcon instanceof FastBitmapDrawable) {
            ((FastBitmapDrawable) mIcon).resetScale();
        }
    }

    private void updateIcon(Drawable newIcon) {
        if (mLayoutHorizontal) {
            setCompoundDrawablesRelative(newIcon, null, null, null);
        } else {
            setCompoundDrawables(null, newIcon, null, null);
        }
    }

    private String getAppLabelPluralString(String appName, int notificationCount) {
        MessageFormat icuCountFormat = new MessageFormat(
                getResources().getString(R.string.dotted_app_label),
                Locale.getDefault());
        HashMap<String, Object> args = new HashMap();
        args.put("app_name", appName);
        args.put("count", notificationCount);
        return icuCountFormat.format(args);
    }
}
