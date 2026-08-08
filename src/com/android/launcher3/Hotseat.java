/*
 * Copyright (C) 2011 The Android Open Source Project
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
 *
 * Modifications copyright 2025 Lawnchair
 */

package com.android.launcher3;

import static android.view.View.MeasureSpec.makeMeasureSpec;

import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.util.MultiTranslateDelegate.INDEX_BUBBLE_ADJUSTMENT_ANIM;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.InsetDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import com.android.launcher3.accessibility.DragAndDropAccessibilityDelegate;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.pageindicators.PageIndicatorDots;
import com.android.launcher3.util.HorizontalInsettableView;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.MultiPropertyFactory.MultiProperty;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.views.ActivityContext;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import app.lawnchair.hotseat.DisabledHotseat;
import app.lawnchair.hotseat.HotseatMode;
import app.lawnchair.hotseat.HotseatPagedView;
import app.lawnchair.hotseat.LawnchairHotseat;
import app.lawnchair.preferences.PreferenceManager;
import app.lawnchair.preferences2.PreferenceCacheExtensionsKt;
import app.lawnchair.preferences2.PreferenceManager2;
import app.lawnchair.theme.drawable.DrawableTokens;

/**
 * View class that represents the bottom dock of the home screen.
 * Hosts a {@link app.lawnchair.hotseat.HotseatPagedView} of icon grids plus an optional QSB.
 */
public class Hotseat extends FrameLayout implements Insettable {

    public static final int ALPHA_CHANNEL_TASKBAR_ALIGNMENT = 0;
    public static final int ALPHA_CHANNEL_PREVIEW_RENDERER = 1;
    public static final int ALPHA_CHANNEL_TASKBAR_STASH = 2;
    public static final int ALPHA_CHANNEL_ASSISTANT_VISIBILITY = 3;
    public static final int ALPHA_CHANNEL_CHANNELS_COUNT = 4;

    @Retention(RetentionPolicy.RUNTIME)
    @IntDef({ALPHA_CHANNEL_TASKBAR_ALIGNMENT, ALPHA_CHANNEL_PREVIEW_RENDERER,
            ALPHA_CHANNEL_TASKBAR_STASH, ALPHA_CHANNEL_ASSISTANT_VISIBILITY})
    public @interface HotseatQsbAlphaId {
    }

    public static final int ICONS_TRANSLATION_X_NAV_BAR_ALIGNMENT = 0;
    public static final int ICONS_TRANSLATION_X_CHANNELS_COUNT = 1;

    @Retention(RetentionPolicy.RUNTIME)
    @IntDef({ICONS_TRANSLATION_X_NAV_BAR_ALIGNMENT})
    public @interface IconsTranslationX {
    }

    // Ratio of empty space, qsb should take up to appear visually centered.
    public static final float QSB_CENTER_FACTOR = .325f;
    private static final int BUBBLE_BAR_ADJUSTMENT_ANIMATION_DURATION_MS = 250;
    private static final int DOCK_PAGE_INDICATOR_HEIGHT_DP = 8;

    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mHasVerticalHotseat;
    private Workspace<?> mWorkspace;
    private boolean mSendTouchToWorkspace;
    private boolean mSendTouchToPager;
    private final MultiValueAlpha mIconsAlphaChannels;
    private final MultiValueAlpha mQsbAlphaChannels;

    private @Nullable MultiProperty mQsbTranslationX;

    private final MultiPropertyFactory mIconsTranslationXFactory;

    private final View mQsb;
    private final FrameLayout mIconsContainer;
    private final HotseatPagedView mPagedView;
    private final PageIndicatorDots mPageIndicator;
    private final int mPageIndicatorHeight;

    private final ActivityContext mActivity;

    PreferenceManager2 preferenceManager2;
    PreferenceManager preferenceManager;

    public Hotseat(Context context) {
        this(context, null);
    }

    public Hotseat(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Hotseat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mActivity = ActivityContext.lookupContext(context);

        preferenceManager2 = PreferenceManager2.getInstance(context);
        preferenceManager = PreferenceManager.getInstance(context);
        HotseatMode hotseatMode = PreferenceCacheExtensionsKt.firstCached(preferenceManager2.getHotseatMode());
        var hotseatEnabled = PreferenceCacheExtensionsKt.firstCached(preferenceManager2.isHotseatEnabled());

        if (!hotseatEnabled) {
            hotseatMode = DisabledHotseat.INSTANCE;
        }

        if (!hotseatMode.isAvailable(context)) {
            // The current hotseat mode is not available,
            // setting the hotseat mode to one that is always available
            hotseatMode = LawnchairHotseat.INSTANCE;
            com.patrykmichalik.opto.core.PreferenceExtensionsKt.setBlocking(preferenceManager2.getHotseatMode(), hotseatMode);
        }
        int layoutId = hotseatMode.getLayoutResourceId();

        mIconsContainer = new FrameLayout(context);
        mIconsContainer.setLayoutParams(new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mIconsContainer.setClipChildren(false);
        mIconsContainer.setClipToPadding(false);
        addView(mIconsContainer);

        mPagedView = new HotseatPagedView(context);
        mPagedView.setLayoutParams(new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mIconsContainer.addView(mPagedView);

        mPageIndicatorHeight = Math.round(
                DOCK_PAGE_INDICATOR_HEIGHT_DP * getResources().getDisplayMetrics().density);
        mPageIndicator = new PageIndicatorDots(context);
        LayoutParams indicatorLp = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, mPageIndicatorHeight);
        indicatorLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        mPageIndicator.setLayoutParams(indicatorLp);
        mPageIndicator.setVisibility(GONE);
        mIconsContainer.addView(mPageIndicator);
        mPagedView.setPageIndicator(mPageIndicator);

        if (Flags.enableQsbOnHotseat()) {
            mQsb = LayoutInflater.from(context).inflate(R.layout.qsb_container_hotseat, this,
                    false);
        } else {
            mQsb = LayoutInflater.from(context).inflate(layoutId, this,
                    false);
        }

        addView(mQsb);

        // Create an initial page so alpha/translation channels have a target during construction.
        mPagedView.resetPages(false, null, mActivity.getDeviceProfile());

        mIconsAlphaChannels = new MultiValueAlpha(mIconsContainer, ALPHA_CHANNEL_CHANNELS_COUNT);
        mIconsAlphaChannels.setUpdateVisibility(true);
        if (mQsb instanceof Reorderable qsbReorderable) {
            mQsbTranslationX = qsbReorderable.getTranslateDelegate()
                    .getTranslationX(MultiTranslateDelegate.INDEX_NAV_BAR_ANIM);
        }
        mIconsTranslationXFactory = new MultiPropertyFactory<>(mIconsContainer,
                VIEW_TRANSLATE_X, ICONS_TRANSLATION_X_CHANNELS_COUNT, Float::sum);
        mQsbAlphaChannels = new MultiValueAlpha(mQsb, ALPHA_CHANNEL_CHANNELS_COUNT);
        mQsbAlphaChannels.setUpdateVisibility(true);

        setUpBackground();
        setClipChildren(false);
        setClipToPadding(false);
    }

    private void setUpBackground() {
        if(!preferenceManager.getHotseatBG().get()) return;

        var bgColor = PreferenceCacheExtensionsKt.firstCached(preferenceManager2.getHotseatBackgroundColor());
        var transparency = preferenceManager.getHotseatBGAlpha().get();
        var alphaValue = (transparency * 255) / 100;
        var baseColor = bgColor.getColorPreferenceEntry().getLightColor().invoke(getContext());
        var finalColor = Color.argb(alphaValue, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor));
        int insetHorizontalLeft = preferenceManager.getHotseatBGHorizontalInsetLeft().get();
        int insetHorizontalRight = preferenceManager.getHotseatBGHorizontalInsetRight().get();
        int insetVerticalTop = preferenceManager.getHotseatBGVerticalInsetTop().get();
        int insetVerticalBottom = preferenceManager.getHotseatBGVerticalInsetBottom().get();
        InsetDrawable bg = new InsetDrawable(DrawableTokens.BgCellLayout.resolve(getContext()),
            insetHorizontalLeft, insetVerticalTop, insetHorizontalRight, insetVerticalBottom);
        bg.setTint(finalColor);
        setBackground(bg);
    }

    /** Provides translation X for hotseat icons for the channel. */
    public MultiProperty getIconsTranslationX(@IconsTranslationX int channelId) {
        return mIconsTranslationXFactory.get(channelId);
    }

    /** Provides translation X for hotseat Qsb. */
    @Nullable
    public MultiProperty getQsbTranslationX() {
        return mQsbTranslationX;
    }

    public HotseatPagedView getPagedView() {
        return mPagedView;
    }

    /** Returns the CellLayout for the given dock page. */
    @Nullable
    public CellLayout getPageAt(int page) {
        return mPagedView.getPageAt(page);
    }

    /** Returns the currently visible dock page layout. */
    @Nullable
    public CellLayout getCurrentPageLayout() {
        return mPagedView.getCurrentCellLayout();
    }

    /** Returns all dock page layouts. */
    public CellLayout[] getPageLayouts() {
        int count = mPagedView.getPageCount();
        CellLayout[] pages = new CellLayout[count];
        for (int i = 0; i < count; i++) {
            pages[i] = mPagedView.getPageAt(i);
        }
        return pages;
    }

    /** Whether {@code layout} is one of this hotseat's page CellLayouts. */
    public boolean isHotseatPage(View layout) {
        if (!(layout instanceof CellLayout)) {
            return false;
        }
        return layout.getParent() == mPagedView;
    }

    /**
     * Returns orientation specific cell X given invariant order in the hotseat
     */
    public int getCellXFromOrder(int rank) {
        if (mHasVerticalHotseat) {
            return 0;
        }
        DeviceProfile dp = mActivity.getDeviceProfile();
        int numColumns = dp.numShownHotseatIcons;
        int localRank = getLocalRank(rank, dp);
        return localRank % numColumns;
    }

    /**
     * Returns orientation specific cell Y given invariant order in the hotseat
     */
    public int getCellYFromOrder(int rank) {
        if (mHasVerticalHotseat) {
            CellLayout page = getCurrentPageLayout();
            int countY = page != null ? page.getCountY() : mActivity.getDeviceProfile().numShownHotseatIcons;
            return countY - (rank + 1);
        }
        DeviceProfile dp = mActivity.getDeviceProfile();
        int numColumns = dp.numShownHotseatIcons;
        int localRank = getLocalRank(rank, dp);
        return localRank / numColumns;
    }

    /** Returns the dock page index for a global hotseat rank. */
    public int getPageFromOrder(int rank) {
        if (mHasVerticalHotseat) {
            return 0;
        }
        DeviceProfile dp = mActivity.getDeviceProfile();
        int slotsPerPage = Math.max(1, dp.numShownHotseatIcons * dp.numHotseatRows);
        return rank / slotsPerPage;
    }

    private static int getLocalRank(int rank, DeviceProfile dp) {
        int slotsPerPage = Math.max(1, dp.numShownHotseatIcons * dp.numHotseatRows);
        return rank % slotsPerPage;
    }

    boolean isHasVerticalHotseat() {
        return mHasVerticalHotseat;
    }

    public void resetLayout(boolean hasVerticalHotseat) {
        ActivityContext activityContext = ActivityContext.lookupContext(getContext());
        boolean bubbleBarEnabled = activityContext.isBubbleBarEnabled();
        boolean hasBubbles = activityContext.hasBubbles();
        mHasVerticalHotseat = hasVerticalHotseat;
        DeviceProfile dp = mActivity.getDeviceProfile();

        mPagedView.resetPages(hasVerticalHotseat, mWorkspace, dp);

        if (bubbleBarEnabled) {
            for (CellLayout page : getPageLayouts()) {
                if (dp.shouldAdjustHotseatForBubbleBar(getContext(), hasBubbles)) {
                    page.getShortcutsAndWidgets().setTranslationProvider(
                            cellX -> dp.getHotseatAdjustedTranslation(getContext(), cellX));
                } else {
                    page.getShortcutsAndWidgets().setTranslationProvider(null);
                }
            }
            if (mQsb instanceof HorizontalInsettableView) {
                HorizontalInsettableView insettableQsb = (HorizontalInsettableView) mQsb;
                if (dp.shouldAdjustHotseatForBubbleBar(getContext(), hasBubbles)) {
                    final float insetFraction = (float) dp.iconSizePx / dp.hotseatQsbWidth;
                    mQsb.post(() -> insettableQsb.setHorizontalInsets(insetFraction));
                } else {
                    insettableQsb.setHorizontalInsets(0);
                }
            }
        }
    }

    /**
     * Adjust the hotseat icons for the bubble bar.
     */
    public void adjustForBubbleBar(boolean isBubbleBarVisible) {
        DeviceProfile dp = mActivity.getDeviceProfile();
        boolean shouldAdjust = isBubbleBarVisible
                && dp.shouldAdjustHotseatOrQsbForBubbleBar(getContext());
        boolean shouldAdjustHotseat = shouldAdjust
                && dp.shouldAlignBubbleBarWithHotseat();
        AnimatorSet animatorSet = new AnimatorSet();
        for (CellLayout page : getPageLayouts()) {
            ShortcutAndWidgetContainer icons = page.getShortcutsAndWidgets();
            if (shouldAdjustHotseat) {
                icons.setTranslationProvider(
                        cellX -> dp.getHotseatAdjustedTranslation(getContext(), cellX));
            } else {
                icons.setTranslationProvider(null);
            }
            for (int i = 0; i < icons.getChildCount(); i++) {
                View child = icons.getChildAt(i);
                if (child.getLayoutParams() instanceof CellLayoutLayoutParams lp) {
                    float tx = shouldAdjustHotseat
                            ? dp.getHotseatAdjustedTranslation(getContext(), lp.getCellX()) : 0;
                    if (child instanceof Reorderable) {
                        MultiTranslateDelegate mtd = ((Reorderable) child).getTranslateDelegate();
                        animatorSet.play(
                                mtd.getTranslationX(INDEX_BUBBLE_ADJUSTMENT_ANIM).animateToValue(tx));
                    } else {
                        animatorSet.play(ObjectAnimator.ofFloat(child, VIEW_TRANSLATE_X, tx));
                    }
                }
            }
        }
        boolean shouldAdjustQsb =
                shouldAdjustHotseat || (shouldAdjust && dp.shouldAlignBubbleBarWithQSB());
        if (mQsb instanceof HorizontalInsettableView horizontalInsettableQsb) {
            final float currentInsetFraction = horizontalInsettableQsb.getHorizontalInsets();
            final float targetInsetFraction = shouldAdjustQsb
                    ? (float) dp.iconSizePx / dp.hotseatQsbWidth : 0;
            ValueAnimator qsbAnimator =
                    ValueAnimator.ofFloat(currentInsetFraction, targetInsetFraction);
            qsbAnimator.addUpdateListener(animation -> {
                float insetFraction = (float) animation.getAnimatedValue();
                horizontalInsettableQsb.setHorizontalInsets(insetFraction);
            });
            animatorSet.play(qsbAnimator);
        }
        animatorSet.setDuration(BUBBLE_BAR_ADJUSTMENT_ANIMATION_DURATION_MS).start();
    }

    @Override
    public void setInsets(Rect insets) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        DeviceProfile grid = mActivity.getDeviceProfile();

        if (grid.isVerticalBarLayout()) {
            mQsb.setVisibility(View.GONE);
            mPageIndicator.setVisibility(GONE);
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            if (grid.isSeascape()) {
                lp.gravity = Gravity.LEFT;
                lp.width = grid.hotseatBarSizePx + insets.left;
            } else {
                lp.gravity = Gravity.RIGHT;
                lp.width = grid.hotseatBarSizePx + insets.right;
            }
        } else {
            mQsb.setVisibility(View.VISIBLE);
            lp.gravity = Gravity.BOTTOM;
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = grid.hotseatBarSizePx;
        }

        Rect padding = grid.getHotseatLayoutPadding(getContext());
        setPadding(padding.left, padding.top, padding.right, padding.bottom);
        setLayoutParams(lp);
        InsettableFrameLayout.dispatchInsets(this, insets);
    }

    public void setWorkspace(Workspace<?> w) {
        mWorkspace = w;
        for (CellLayout page : getPageLayouts()) {
            page.setCellLayoutContainer(w);
        }
    }

    private boolean isTouchOnQsb(MotionEvent ev) {
        if (mQsb == null || mQsb.getVisibility() != VISIBLE) {
            return false;
        }
        float x = ev.getX();
        float y = ev.getY();
        return x >= mQsb.getLeft() && x < mQsb.getRight()
                && y >= mQsb.getTop() && y < mQsb.getBottom();
    }

    private MotionEvent obtainPagerEvent(MotionEvent ev) {
        MotionEvent pagerEv = MotionEvent.obtain(ev);
        pagerEv.offsetLocation(-mIconsContainer.getLeft() - mPagedView.getLeft(),
                -mIconsContainer.getTop() - mPagedView.getTop());
        return pagerEv;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int yThreshold = getMeasuredHeight() - getPaddingBottom();
        if (ev.getY() > yThreshold || isTouchOnQsb(ev)) {
            return false;
        }

        // Multi-page dock: own the icon-band stream and dispatch it to the pager so empty
        // pages can still scroll (same pattern as single-page workspace forwarding).
        if (mPagedView.isPagingEnabled()) {
            final int action = ev.getAction() & MotionEvent.ACTION_MASK;
            if (action == MotionEvent.ACTION_DOWN) {
                mSendTouchToPager = true;
                mSendTouchToWorkspace = false;
            }
            return mSendTouchToPager;
        }

        // Single-page dock: forward horizontal swipes to workspace.
        if (mWorkspace != null) {
            mSendTouchToWorkspace = mWorkspace.onInterceptTouchEvent(ev);
            return mSendTouchToWorkspace;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mSendTouchToPager) {
            MotionEvent pagerEv = obtainPagerEvent(event);
            boolean handled = mPagedView.dispatchTouchEvent(pagerEv);
            pagerEv.recycle();
            final int action = event.getAction() & MotionEvent.ACTION_MASK;
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                mSendTouchToPager = false;
            }
            return handled;
        }
        if (mSendTouchToWorkspace) {
            final int action = event.getAction();
            switch (action & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mSendTouchToWorkspace = false;
            }
            return mWorkspace.onTouchEvent(event);
        }
        return false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(widthSize, heightSize);

        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();
        int paddingTop = getPaddingTop();
        int paddingBottom = getPaddingBottom();

        DeviceProfile dp = mActivity.getDeviceProfile();
        boolean showIndicator = mPagedView.isPagingEnabled() && !dp.isVerticalBarLayout();
        int indicatorSpace = showIndicator ? mPageIndicatorHeight : 0;

        int pagerWidth = widthSize - paddingLeft - paddingRight;
        int pagerHeight = heightSize - paddingTop - paddingBottom;
        mIconsContainer.measure(
                makeMeasureSpec(Math.max(0, pagerWidth), MeasureSpec.EXACTLY),
                makeMeasureSpec(Math.max(0, pagerHeight), MeasureSpec.EXACTLY));

        int pageHeight = Math.max(0, pagerHeight - indicatorSpace);
        mPagedView.measure(
                makeMeasureSpec(Math.max(0, pagerWidth), MeasureSpec.EXACTLY),
                makeMeasureSpec(pageHeight, MeasureSpec.EXACTLY));

        if (showIndicator) {
            mPageIndicator.measure(
                    makeMeasureSpec(Math.max(0, pagerWidth), MeasureSpec.EXACTLY),
                    makeMeasureSpec(mPageIndicatorHeight, MeasureSpec.EXACTLY));
        }

        int width;
        if (dp.isQsbInline) {
            width = dp.hotseatQsbWidth;
        } else {
            width = mPagedView.getMeasuredWidth();
        }

        mQsb.measure(makeMeasureSpec(Math.max(0, width), MeasureSpec.EXACTLY),
                makeMeasureSpec(dp.getHotseatProfile().getQsbHeight(), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        int height = b - t;
        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();
        int paddingTop = getPaddingTop();
        int paddingBottom = getPaddingBottom();

        DeviceProfile dp = mActivity.getDeviceProfile();
        mIconsContainer.layout(paddingLeft, paddingTop, width - paddingRight,
                height - paddingBottom);

        int containerWidth = mIconsContainer.getWidth();
        int containerHeight = mIconsContainer.getHeight();
        boolean showIndicator = mPagedView.isPagingEnabled() && !dp.isVerticalBarLayout();
        int indicatorSpace = showIndicator ? mPageIndicatorHeight : 0;

        mPagedView.layout(0, 0, containerWidth, Math.max(0, containerHeight - indicatorSpace));

        if (showIndicator) {
            int indicatorTop = containerHeight - mPageIndicatorHeight;
            mPageIndicator.layout(0, indicatorTop, containerWidth,
                    indicatorTop + mPageIndicatorHeight);
            mPageIndicator.setVisibility(VISIBLE);
        } else {
            mPageIndicator.setVisibility(GONE);
        }

        int qsbMeasuredWidth = mQsb.getMeasuredWidth();
        int left;
        if (dp.isQsbInline) {
            int qsbSpace = dp.hotseatBorderSpace;
            left = Utilities.isRtl(getResources()) ? r - getPaddingRight() + qsbSpace
                    : l + getPaddingLeft() - qsbMeasuredWidth - qsbSpace;
        } else {
            left = (width - qsbMeasuredWidth) / 2;
        }
        int right = left + qsbMeasuredWidth;

        int bottom = height - dp.getQsbOffsetY();
        int top = bottom - dp.getHotseatProfile().getQsbHeight();
        mQsb.layout(left, top, right, bottom);
    }

    /**
     * Sets the alpha value of the specified alpha channel of just our ShortcutAndWidgetContainer.
     */
    public void setIconsAlpha(float alpha, @HotseatQsbAlphaId int channelId) {
        getIconsAlpha(channelId).setValue(alpha);
    }

    /**
     * Sets the alpha value of just our QSB.
     */
    public void setQsbAlpha(float alpha, @HotseatQsbAlphaId int channelId) {
        getQsbAlpha(channelId).setValue(alpha);
    }

    /** Returns the alpha channel for ShortcutAndWidgetContainer */
    public MultiProperty getIconsAlpha(@HotseatQsbAlphaId int channelId) {
        return mIconsAlphaChannels.get(channelId);
    }

    /** Returns the alpha channel for Qsb */
    public MultiProperty getQsbAlpha(@HotseatQsbAlphaId int channelId) {
        return mQsbAlphaChannels.get(channelId);
    }

    /**
     * Returns the QSB inside hotseat
     */
    public View getQsb() {
        return mQsb;
    }

    /** Delegates to the current page's shortcuts container. */
    public ShortcutAndWidgetContainer getShortcutsAndWidgets() {
        CellLayout page = getCurrentPageLayout();
        return page != null ? page.getShortcutsAndWidgets() : null;
    }

    /** Delegates accessibility drag helper to the current page. */
    public DragAndDropAccessibilityDelegate getDragAndDropAccessibilityDelegate() {
        CellLayout page = getCurrentPageLayout();
        return page != null ? page.getDragAndDropAccessibilityDelegate() : null;
    }

    /** Dumps the Hotseat internal state */
    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "Hotseat:");
        writer.println(prefix + "\tpages: " + mPagedView.getPageCount()
                + " pagingEnabled=" + mPagedView.isPagingEnabled());
        mIconsAlphaChannels.dump(
                prefix + "\t",
                writer,
                "mIconsAlphaChannels",
                "ALPHA_CHANNEL_TASKBAR_ALIGNMENT",
                "ALPHA_CHANNEL_PREVIEW_RENDERER",
                "ALPHA_CHANNEL_TASKBAR_STASH");
        mQsbAlphaChannels.dump(
                prefix + "\t",
                writer,
                "mQsbAlphaChannels",
                "ALPHA_CHANNEL_TASKBAR_ALIGNMENT",
                "ALPHA_CHANNEL_PREVIEW_RENDERER",
                "ALPHA_CHANNEL_TASKBAR_STASH"
        );
    }

}
