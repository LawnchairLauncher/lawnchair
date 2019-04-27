/**
 * Copyright (C) 2019 The Android Open Source Project
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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Interpolator;
import android.widget.LinearLayout;

import com.android.launcher3.AppInfo;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.ItemInfoWithIcon;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.WorkspaceItemInfo;
import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.allapps.FloatingHeaderRow;
import com.android.launcher3.allapps.FloatingHeaderView;
import com.android.launcher3.anim.AlphaUpdateListener;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.keyboard.FocusIndicatorHelper;
import com.android.launcher3.keyboard.FocusIndicatorHelper.SimpleFocusIndicatorHelper;
import com.android.launcher3.logging.StatsLogUtils.LogContainerProvider;
import com.android.launcher3.model.AppLaunchTracker;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.util.Themes;
import com.android.quickstep.AnimatedFloat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@TargetApi(Build.VERSION_CODES.P)
public class PredictionRowView extends LinearLayout implements
        LogContainerProvider, OnDeviceProfileChangeListener, FloatingHeaderRow {

    private static final String TAG = "PredictionRowView";

    private static final IntProperty<PredictionRowView> TEXT_ALPHA =
            new IntProperty<PredictionRowView>("textAlpha") {
                @Override
                public void setValue(PredictionRowView view, int alpha) {
                    view.setTextAlpha(alpha);
                }

                @Override
                public Integer get(PredictionRowView view) {
                    return view.mIconCurrentTextAlpha;
                }
            };

    private static final Interpolator ALPHA_FACTOR_INTERPOLATOR =
            (t) -> (t < 0.8f) ? 0 : (t - 0.8f) / 0.2f;

    private static final OnClickListener PREDICTION_CLICK_LISTENER =
            ItemClickHandler.getInstance(AppLaunchTracker.CONTAINER_PREDICTIONS);

    private final Launcher mLauncher;
    private final PredictionUiStateManager mPredictionUiStateManager;
    private final int mNumPredictedAppsPerRow;

    // The set of predicted app component names
    private final List<ComponentKeyMapper> mPredictedAppComponents = new ArrayList<>();
    // The set of predicted apps resolved from the component names and the current set of apps
    private final ArrayList<ItemInfoWithIcon> mPredictedApps = new ArrayList<>();
    // Helper to drawing the focus indicator.
    private final FocusIndicatorHelper mFocusHelper;

    private final int mIconTextColor;
    private final int mIconFullTextAlpha;
    private int mIconCurrentTextAlpha;

    private FloatingHeaderView mParent;
    private boolean mScrolledOut;

    private float mScrollTranslation = 0;
    private final AnimatedFloat mContentAlphaFactor =
            new AnimatedFloat(this::updateTranslationAndAlpha);
    private final AnimatedFloat mOverviewScrollFactor =
            new AnimatedFloat(this::updateTranslationAndAlpha);

    private View mLoadingProgress;

    private boolean mPredictionsEnabled = false;

    public PredictionRowView(@NonNull Context context) {
        this(context, null);
    }

    public PredictionRowView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(LinearLayout.HORIZONTAL);

        mFocusHelper = new SimpleFocusIndicatorHelper(this);

        mNumPredictedAppsPerRow = LauncherAppState.getIDP(context).numColumns;
        mLauncher = Launcher.getLauncher(context);
        mLauncher.addOnDeviceProfileChangeListener(this);

        mPredictionUiStateManager = PredictionUiStateManager.INSTANCE.get(context);

        mIconTextColor = Themes.getAttrColor(context, android.R.attr.textColorSecondary);
        mIconFullTextAlpha = Color.alpha(mIconTextColor);
        mIconCurrentTextAlpha = mIconFullTextAlpha;

        updateVisibility();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mPredictionUiStateManager.setTargetAppsView(mLauncher.getAppsView());
        getAppsStore().registerIconContainer(this);
        AllAppsTipView.scheduleShowIfNeeded(mLauncher);
    }

    private AllAppsStore getAppsStore() {
        return mLauncher.getAppsView().getAppsStore();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        mPredictionUiStateManager.setTargetAppsView(null);
        getAppsStore().unregisterIconContainer(this);
    }

    public void setup(FloatingHeaderView parent, FloatingHeaderRow[] rows, boolean tabsHidden) {
        mParent = parent;
        setPredictionsEnabled(mPredictionUiStateManager.arePredictionsEnabled());
    }

    private void setPredictionsEnabled(boolean predictionsEnabled) {
        if (predictionsEnabled != mPredictionsEnabled) {
            mPredictionsEnabled = predictionsEnabled;
            updateVisibility();
        }
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
        return getVisibility() != GONE;
    }

    @Override
    public boolean hasVisibleContent() {
        return mPredictionUiStateManager.arePredictionsEnabled();
    }

    /**
     * Returns the predicted apps.
     */
    public List<ItemInfoWithIcon> getPredictedApps() {
        return mPredictedApps;
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
    public void setPredictedApps(boolean predictionsEnabled, List<ComponentKeyMapper> apps) {
        setPredictionsEnabled(predictionsEnabled);
        mPredictedAppComponents.clear();
        mPredictedAppComponents.addAll(apps);

        mPredictedApps.clear();
        mPredictedApps.addAll(processPredictedAppComponents(mPredictedAppComponents));
        applyPredictionApps();
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        removeAllViews();
        applyPredictionApps();
    }

    private void applyPredictionApps() {
        if (mLoadingProgress != null) {
            removeView(mLoadingProgress);
        }
        if (!mPredictionsEnabled) {
            mParent.onHeightUpdated();
            return;
        }

        if (getChildCount() != mNumPredictedAppsPerRow) {
            while (getChildCount() > mNumPredictedAppsPerRow) {
                removeViewAt(0);
            }
            while (getChildCount() < mNumPredictedAppsPerRow) {
                BubbleTextView icon = (BubbleTextView) mLauncher.getLayoutInflater().inflate(
                        R.layout.all_apps_icon, this, false);
                icon.setOnClickListener(PREDICTION_CLICK_LISTENER);
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
                if (mPredictedApps.get(i) instanceof AppInfo) {
                    icon.applyFromApplicationInfo((AppInfo) mPredictedApps.get(i));
                } else if (mPredictedApps.get(i) instanceof WorkspaceItemInfo) {
                    icon.applyFromWorkspaceItem((WorkspaceItemInfo) mPredictedApps.get(i));
                }
                icon.setTextColor(iconColor);
            } else {
                icon.setVisibility(predictionCount == 0 ? GONE : INVISIBLE);
            }
        }

        if (predictionCount == 0) {
            if (mLoadingProgress == null) {
                mLoadingProgress = LayoutInflater.from(getContext())
                        .inflate(R.layout.prediction_load_progress, this, false);
            }
            addView(mLoadingProgress);
        } else {
            mLoadingProgress = null;
        }

        mParent.onHeightUpdated();
    }

    private List<ItemInfoWithIcon> processPredictedAppComponents(List<ComponentKeyMapper> components) {
        if (getAppsStore().getApps().isEmpty()) {
            // Apps have not been bound yet.
            return Collections.emptyList();
        }

        List<ItemInfoWithIcon> predictedApps = new ArrayList<>();
        for (ComponentKeyMapper mapper : components) {
            ItemInfoWithIcon info = mapper.getApp(getAppsStore());
            if (info != null) {
                predictedApps.add(info);
            } else {
                if (FeatureFlags.IS_DOGFOOD_BUILD) {
                    Log.e(TAG, "Predicted app not found: " + mapper);
                }
            }
            // Stop at the number of predicted apps
            if (predictedApps.size() == mNumPredictedAppsPerRow) {
                break;
            }
        }
        return predictedApps;
    }

    @Override
    public void fillInLogContainerData(View v, ItemInfo info, LauncherLogProto.Target target,
            LauncherLogProto.Target targetParent) {
        for (int i = 0; i < mPredictedApps.size(); i++) {
            ItemInfoWithIcon appInfo = mPredictedApps.get(i);
            if (appInfo == info) {
                targetParent.containerType = LauncherLogProto.ContainerType.PREDICTION;
                target.predictedRank = i;
                break;
            }
        }
    }

    public void setTextAlpha(int alpha) {
        mIconCurrentTextAlpha = alpha;
        int iconColor = setColorAlphaBound(mIconTextColor, mIconCurrentTextAlpha);

        if (mLoadingProgress == null) {
            for (int i = 0; i < getChildCount(); i++) {
                ((BubbleTextView) getChildAt(i)).setTextColor(iconColor);
            }
        }
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
    public void setContentVisibility(boolean hasHeaderExtra, boolean hasContent,
            PropertySetter setter, Interpolator fadeInterpolator) {
        boolean isDrawn = getAlpha() > 0;
        int textAlpha = hasHeaderExtra
                ? (hasContent ? mIconFullTextAlpha : 0) // Text follows the content visibility
                : mIconCurrentTextAlpha; // Leave as before
        if (!isDrawn) {
            // If the header is not drawn, no need to animate the text alpha
            setTextAlpha(textAlpha);
        } else {
            setter.setInt(this, TEXT_ALPHA, textAlpha, fadeInterpolator);
        }

        setter.setFloat(mOverviewScrollFactor, AnimatedFloat.VALUE,
                (hasHeaderExtra && !hasContent) ? 1 : 0, LINEAR);
        setter.setFloat(mContentAlphaFactor, AnimatedFloat.VALUE, hasHeaderExtra ? 1 : 0,
                fadeInterpolator);
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
}
