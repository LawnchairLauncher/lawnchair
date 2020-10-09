/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.launcher3.appprediction;

import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.icons.GraphicsUtils.setColorAlphaBound;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.util.IntProperty;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Interpolator;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsSectionDecorator;
import com.android.launcher3.allapps.FloatingHeaderRow;
import com.android.launcher3.allapps.FloatingHeaderView;
import com.android.launcher3.anim.AlphaUpdateListener;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.keyboard.FocusIndicatorHelper;
import com.android.launcher3.keyboard.FocusIndicatorHelper.SimpleFocusIndicatorHelper;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.util.Themes;
import com.android.quickstep.AnimatedFloat;

import java.util.ArrayList;
import java.util.List;

@TargetApi(Build.VERSION_CODES.P)
public class PredictionRowView extends LinearLayout implements
        OnDeviceProfileChangeListener, FloatingHeaderRow {

    private static final IntProperty<PredictionRowView> TEXT_ALPHA =
            new IntProperty<PredictionRowView>("textAlpha") {
                @Override
                public void setValue(PredictionRowView view, int alpha) {
                    view.setTextAlpha(alpha);
                }

                @Override
                public Integer get(PredictionRowView view) {
                    return view.mIconLastSetTextAlpha;
                }
            };

    private static final Interpolator ALPHA_FACTOR_INTERPOLATOR =
            (t) -> (t < 0.8f) ? 0 : (t - 0.8f) / 0.2f;

    private final Launcher mLauncher;
    private int mNumPredictedAppsPerRow;

    // Helper to drawing the focus indicator.
    private final FocusIndicatorHelper mFocusHelper;

    // The set of predicted apps resolved from the component names and the current set of apps
    private final List<WorkspaceItemInfo> mPredictedApps = new ArrayList<>();

    private final int mIconTextColor;
    private final int mIconFullTextAlpha;
    private int mIconLastSetTextAlpha;
    // Might use mIconFullTextAlpha instead of mIconLastSetTextAlpha if we are translucent.
    private int mIconCurrentTextAlpha;

    private FloatingHeaderView mParent;
    private boolean mScrolledOut;

    private float mScrollTranslation = 0;
    private final AnimatedFloat mContentAlphaFactor =
            new AnimatedFloat(this::updateTranslationAndAlpha);
    private final AnimatedFloat mOverviewScrollFactor =
            new AnimatedFloat(this::updateTranslationAndAlpha);

    private boolean mPredictionsEnabled = false;

    AllAppsSectionDecorator.SectionDecorationHandler mDecorationHandler;

    @Nullable private List<ItemInfo> mPendingPredictedItems;

    public PredictionRowView(@NonNull Context context) {
        this(context, null);
    }

    public PredictionRowView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(LinearLayout.HORIZONTAL);

        mFocusHelper = new SimpleFocusIndicatorHelper(this);

        mNumPredictedAppsPerRow = LauncherAppState.getIDP(context).numAllAppsColumns;
        mLauncher = Launcher.getLauncher(context);
        mLauncher.addOnDeviceProfileChangeListener(this);

        mIconTextColor = Themes.getAttrColor(context, android.R.attr.textColorSecondary);
        mIconFullTextAlpha = Color.alpha(mIconTextColor);
        mIconCurrentTextAlpha = mIconFullTextAlpha;

        if (FeatureFlags.ENABLE_DEVICE_SEARCH.get()) {
            mDecorationHandler = new AllAppsSectionDecorator.SectionDecorationHandler(getContext(),
                    false);
        }

        updateVisibility();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        AllAppsTipView.scheduleShowIfNeeded(mLauncher);
    }

    public void setup(FloatingHeaderView parent, FloatingHeaderRow[] rows, boolean tabsHidden) {
        mParent = parent;
    }

    private void updateVisibility() {
        setVisibility(mPredictionsEnabled ? VISIBLE : GONE);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(getExpectedHeight(),
                MeasureSpec.EXACTLY));
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mDecorationHandler != null) {
            mDecorationHandler.reset();
            int childrenCount = getChildCount();
            for (int i = 0; i < childrenCount; i++) {
                mDecorationHandler.extendBounds(getChildAt(i));
            }
            mDecorationHandler.onDraw(canvas);
            mDecorationHandler.onFocusDraw(canvas, getFocusedChild());
            mLauncher.getAppsView().getActiveRecyclerView().invalidateItemDecorations();
        }
        mFocusHelper.draw(canvas);
        super.dispatchDraw(canvas);
    }

    @Override
    public int getExpectedHeight() {
        return getVisibility() == GONE ? 0 :
                Launcher.getLauncher(getContext()).getDeviceProfile().allAppsCellHeightPx
                        + getPaddingTop() + getPaddingBottom();
    }

    @Override
    public boolean shouldDraw() {
        return getVisibility() == VISIBLE;
    }

    @Override
    public boolean hasVisibleContent() {
        return mPredictionsEnabled;
    }

    /**
     * Returns the predicted apps.
     */
    public List<ItemInfoWithIcon> getPredictedApps() {
        return new ArrayList<>(mPredictedApps);
    }

    /**
     * Sets the current set of predicted apps.
     *
     * This can be called before we get the full set of applications, we should merge the results
     * only in onPredictionsUpdated() which is idempotent.
     *
     * If the number of predicted apps is the same as the previous list of predicted apps,
     * we can optimize by swapping them in place.
     */
    public void setPredictedApps(List<ItemInfo> items) {
        if (isShown() && getWindowVisibility() == View.VISIBLE) {
            mPendingPredictedItems = items;
            return;
        }

        applyPredictedApps(items);
    }

    private void applyPredictedApps(List<ItemInfo> items) {
        mPendingPredictedItems = null;
        mPredictedApps.clear();
        items.stream()
                .filter(itemInfo -> itemInfo instanceof WorkspaceItemInfo)
                .map(itemInfo -> (WorkspaceItemInfo) itemInfo)
                .forEach(mPredictedApps::add);
        applyPredictionApps();
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        mNumPredictedAppsPerRow = dp.inv.numAllAppsColumns;
        removeAllViews();
        applyPredictionApps();
    }

    private void applyPredictionApps() {
        if (getChildCount() != mNumPredictedAppsPerRow) {
            while (getChildCount() > mNumPredictedAppsPerRow) {
                removeViewAt(0);
            }
            LayoutInflater inflater = mLauncher.getAppsView().getLayoutInflater();
            while (getChildCount() < mNumPredictedAppsPerRow) {
                BubbleTextView icon = (BubbleTextView) inflater.inflate(
                        R.layout.all_apps_icon, this, false);
                icon.setOnClickListener(ItemClickHandler.INSTANCE);
                icon.setOnLongClickListener(ItemLongClickListener.INSTANCE_ALL_APPS);
                icon.setLongPressTimeoutFactor(1f);
                icon.setOnFocusChangeListener(mFocusHelper);

                LayoutParams lp = (LayoutParams) icon.getLayoutParams();
                // Ensure the all apps icon height matches the workspace icons in portrait mode.
                lp.height = mLauncher.getDeviceProfile().allAppsCellHeightPx;
                lp.width = 0;
                lp.weight = 1;
                addView(icon);
            }
        }

        int predictionCount = mPredictedApps.size();
        int iconColor = setColorAlphaBound(mIconTextColor, mIconCurrentTextAlpha);

        for (int i = 0; i < getChildCount(); i++) {
            BubbleTextView icon = (BubbleTextView) getChildAt(i);
            icon.reset();
            if (predictionCount > i) {
                icon.setVisibility(View.VISIBLE);
                icon.applyFromWorkspaceItem(mPredictedApps.get(i));
                icon.setTextColor(iconColor);
            } else {
                icon.setVisibility(predictionCount == 0 ? GONE : INVISIBLE);
            }
        }

        boolean predictionsEnabled = predictionCount > 0;
        if (predictionsEnabled != mPredictionsEnabled) {
            mPredictionsEnabled = predictionsEnabled;
            mLauncher.reapplyUi(false /* cancelCurrentAnimation */);
            updateVisibility();
        }
        mParent.onHeightUpdated();
    }

    public void setTextAlpha(int textAlpha) {
        mIconLastSetTextAlpha = textAlpha;
        if (getAlpha() < 1 && textAlpha > 0) {
            // If the entire header is translucent, make sure the text is at full opacity so it's
            // not double-translucent. However, we support keeping the text invisible (alpha == 0).
            textAlpha = mIconFullTextAlpha;
        }
        mIconCurrentTextAlpha = textAlpha;
        int iconColor = setColorAlphaBound(mIconTextColor, mIconCurrentTextAlpha);
        for (int i = 0; i < getChildCount(); i++) {
            ((BubbleTextView) getChildAt(i)).setTextColor(iconColor);
        }
    }

    @Override
    public void setAlpha(float alpha) {
        super.setAlpha(alpha);
        // Reapply text alpha so that we update it to be full alpha if the row is now translucent.
        setTextAlpha(mIconLastSetTextAlpha);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }


    @Override
    public void setVerticalScroll(int scroll, boolean isScrolledOut) {
        mScrolledOut = isScrolledOut;
        updateTranslationAndAlpha();
        if (!isScrolledOut) {
            mScrollTranslation = scroll;
            updateTranslationAndAlpha();
        }
    }

    private void updateTranslationAndAlpha() {
        if (mPredictionsEnabled) {
            setTranslationY((1 - mOverviewScrollFactor.value) * mScrollTranslation);

            float factor = ALPHA_FACTOR_INTERPOLATOR.getInterpolation(mOverviewScrollFactor.value);
            float endAlpha = factor + (1 - factor) * (mScrolledOut ? 0 : 1);
            setAlpha(mContentAlphaFactor.value * endAlpha);
            AlphaUpdateListener.updateVisibility(this);
        }
    }

    @Override
    public void setContentVisibility(boolean hasHeaderExtra, boolean hasAllAppsContent,
            PropertySetter setter, Interpolator headerFade, Interpolator allAppsFade) {
        // Text follows all apps visibility
        int textAlpha = hasHeaderExtra && hasAllAppsContent ? mIconFullTextAlpha : 0;
        setter.setInt(this, TEXT_ALPHA, textAlpha, allAppsFade);
        setter.setFloat(mOverviewScrollFactor, AnimatedFloat.VALUE,
                (hasHeaderExtra && !hasAllAppsContent) ? 1 : 0, LINEAR);
        setter.setFloat(mContentAlphaFactor, AnimatedFloat.VALUE, hasHeaderExtra ? 1 : 0,
                headerFade);
    }

    @Override
    public void setInsets(Rect insets, DeviceProfile grid) {
        int leftRightPadding = grid.desiredWorkspaceLeftRightMarginPx
                + grid.cellLayoutPaddingLeftRightPx;
        setPadding(leftRightPadding, getPaddingTop(), leftRightPadding, getPaddingBottom());
    }

    @Override
    public Class<PredictionRowView> getTypeClass() {
        return PredictionRowView.class;
    }

    @Override
    public View getFocusedChild() {
        return getChildAt(0);
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);

        if (mPendingPredictedItems != null && !isVisible) {
            applyPredictedApps(mPendingPredictedItems);
        }
    }
}
