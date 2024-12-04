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

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.Flags;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.FloatingHeaderRow;
import com.android.launcher3.allapps.FloatingHeaderView;
import com.android.launcher3.anim.AlphaUpdateListener;
import com.android.launcher3.keyboard.FocusIndicatorHelper;
import com.android.launcher3.keyboard.FocusIndicatorHelper.SimpleFocusIndicatorHelper;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.views.ActivityContext;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PredictionRowView<T extends Context & ActivityContext>
        extends LinearLayout implements OnDeviceProfileChangeListener, FloatingHeaderRow {

    private final T mActivityContext;
    private int mNumPredictedAppsPerRow;
    // Vertical padding of the icon that contributes to the expected cell height.
    private final int mVerticalPadding;
    // Extra padding that is used in the top app rows (prediction and search) that is not used in
    // the regular A-Z list. This only applies to single line label.
    private final int mTopRowExtraHeight;

    // Helper to drawing the focus indicator.
    private final FocusIndicatorHelper mFocusHelper;

    // The set of predicted apps resolved from the component names and the current set of apps
    private final List<WorkspaceItemInfo> mPredictedApps = new ArrayList<>();

    private FloatingHeaderView mParent;

    private boolean mPredictionsEnabled = false;

    private boolean mPredictionUiUpdatePaused = false;

    public PredictionRowView(@NonNull Context context) {
        this(context, null);
    }

    public PredictionRowView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(LinearLayout.HORIZONTAL);

        mFocusHelper = new SimpleFocusIndicatorHelper(this);
        mActivityContext = ActivityContext.lookupContext(context);
        mNumPredictedAppsPerRow = mActivityContext.getDeviceProfile().numShownAllAppsColumns;
        mTopRowExtraHeight = getResources().getDimensionPixelSize(
                R.dimen.all_apps_search_top_row_extra_height);
        mVerticalPadding = getResources().getDimensionPixelSize(
                R.dimen.all_apps_predicted_icon_vertical_padding);
        updateVisibility();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mActivityContext.addOnDeviceProfileChangeListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mActivityContext.removeOnDeviceProfileChangeListener(this);
    }

    public void setup(FloatingHeaderView parent, FloatingHeaderRow[] rows, boolean tabsHidden) {
        mParent = parent;
    }

    private void updateVisibility() {
        setVisibility(mPredictionsEnabled ? VISIBLE : GONE);
        if (mActivityContext.getAppsView() != null) {
            if (mPredictionsEnabled) {
                mActivityContext.getAppsView().getAppsStore().registerIconContainer(this);
            } else {
                mActivityContext.getAppsView().getAppsStore().unregisterIconContainer(this);
            }
        }
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
        DeviceProfile deviceProfile = mActivityContext.getDeviceProfile();
        int iconHeight = deviceProfile.allAppsIconSizePx;
        int iconPadding = deviceProfile.allAppsIconDrawablePaddingPx;
        int textHeight = Utilities.calculateTextHeight(deviceProfile.allAppsIconTextSizePx);
        int totalHeight = iconHeight + iconPadding + textHeight + mVerticalPadding * 2;
        // Prediction row height will be 4dp bigger than the regular apps in A-Z list when two line
        // is not enabled. Otherwise, the extra height will increase by just the textHeight.
        int extraHeight = (Flags.enableTwolineToggle() &&
                LauncherPrefs.ENABLE_TWOLINE_ALLAPPS_TOGGLE.get(getContext()))
                ? textHeight : mTopRowExtraHeight;
        totalHeight += extraHeight;
        return getVisibility() == GONE ? 0 : totalHeight + getPaddingTop() + getPaddingBottom();
    }

    @Override
    public boolean shouldDraw() {
        return getVisibility() != GONE;
    }

    @Override
    public boolean hasVisibleContent() {
        return mPredictionsEnabled;
    }

    @Override
    public boolean isVisible() {
        return getVisibility() == VISIBLE;
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
        applyPredictedApps(items);
    }

    private void applyPredictedApps(List<ItemInfo> items) {
        mPredictedApps.clear();
        mPredictedApps.addAll(items.stream()
                .filter(itemInfo -> itemInfo instanceof WorkspaceItemInfo)
                .map(itemInfo -> (WorkspaceItemInfo) itemInfo).collect(Collectors.toList()));
        applyPredictionApps();
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        mNumPredictedAppsPerRow = dp.numShownAllAppsColumns;
        removeAllViews();
        applyPredictionApps();
    }

    /** Pause the prediction row UI update */
    public void setPredictionUiUpdatePaused(boolean predictionUiUpdatePaused) {
        mPredictionUiUpdatePaused = predictionUiUpdatePaused;
        if (!mPredictionUiUpdatePaused) {
            applyPredictionApps();
        }
    }

    private void applyPredictionApps() {
        if (mPredictionUiUpdatePaused) {
            return;
        }
        if (getChildCount() != mNumPredictedAppsPerRow) {
            while (getChildCount() > mNumPredictedAppsPerRow) {
                removeViewAt(0);
            }
            LayoutInflater inflater = mActivityContext.getAppsView().getLayoutInflater();
            while (getChildCount() < mNumPredictedAppsPerRow) {
                BubbleTextView icon = (BubbleTextView) inflater.inflate(
                        R.layout.all_apps_prediction_row_icon, this, false);
                icon.setOnClickListener(mActivityContext.getItemOnClickListener());
                icon.setOnLongClickListener(mActivityContext.getAllAppsItemLongClickListener());
                icon.setLongPressTimeoutFactor(1f);
                icon.setOnFocusChangeListener(mFocusHelper);

                LayoutParams lp = (LayoutParams) icon.getLayoutParams();
                if (Flags.enableFocusOutline()) {
                    lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                } else {
                    // Ensure the all apps icon height matches the workspace icons in portrait mode.
                    lp.height = mActivityContext.getDeviceProfile().allAppsCellHeightPx;
                }
                lp.width = 0;
                lp.weight = 1;
                addView(icon);
            }
        }

        int predictionCount = mPredictedApps.size();

        for (int i = 0; i < getChildCount(); i++) {
            BubbleTextView icon = (BubbleTextView) getChildAt(i);
            icon.reset();
            if (predictionCount > i) {
                icon.setVisibility(View.VISIBLE);
                WorkspaceItemInfo predictedItem = mPredictedApps.get(i);
                predictedItem.rank = i;
                predictedItem.cellX = i;
                predictedItem.cellY = 0;
                icon.applyFromWorkspaceItem(predictedItem);
            } else {
                icon.setVisibility(predictionCount == 0 ? GONE : INVISIBLE);
            }
        }

        boolean predictionsEnabled = predictionCount > 0;
        if (predictionsEnabled != mPredictionsEnabled) {
            mPredictionsEnabled = predictionsEnabled;
            updateVisibility();
        }
        mParent.onHeightUpdated();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }


    @Override
    public void setVerticalScroll(int scroll, boolean isScrolledOut) {
        if (!isScrolledOut) {
            setTranslationY(scroll);
        }
        setAlpha(isScrolledOut ? 0 : 1);
        if (getVisibility() != GONE) {
            AlphaUpdateListener.updateVisibility(this);
        }
    }

    @Override
    public Class<PredictionRowView> getTypeClass() {
        return PredictionRowView.class;
    }

    @Override
    public View getFocusedChild() {
        return getChildAt(0);
    }

    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "PredictionRowView");
        writer.println(prefix + "\tmPredictionsEnabled: " + mPredictionsEnabled);
        writer.println(prefix + "\tmPredictionUiUpdatePaused: " + mPredictionUiUpdatePaused);
        writer.println(prefix + "\tmNumPredictedAppsPerRow: " + mNumPredictedAppsPerRow);
        writer.println(prefix + "\tmPredictedApps: " + mPredictedApps);
    }
}
