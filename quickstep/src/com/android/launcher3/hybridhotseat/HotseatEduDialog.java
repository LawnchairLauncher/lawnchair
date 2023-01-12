/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.hybridhotseat;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_HOTSEAT_EDU_ACCEPT;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_HOTSEAT_EDU_DENY;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_HOTSEAT_EDU_SEEN;

import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.uioverrides.PredictedAppIcon;
import com.android.launcher3.views.AbstractSlideInView;

import java.util.List;

/**
 * User education dialog for hybrid hotseat. Allows user to migrate hotseat items to a new page in
 * the workspace and shows predictions on the whole hotseat
 */
public class HotseatEduDialog extends AbstractSlideInView<Launcher> implements Insettable {

    private static final int DEFAULT_CLOSE_DURATION = 200;
    protected static final int FINAL_SCRIM_BG_COLOR = 0x88000000;

    // we use this value to keep track of migration logs as we experiment with different migrations
    private static final int MIGRATION_EXPERIMENT_IDENTIFIER = 1;

    private final Rect mInsets = new Rect();
    private View mHotseatWrapper;
    private CellLayout mSampleHotseat;
    private Button mDismissBtn;

    public void setHotseatEduController(HotseatEduController hotseatEduController) {
        mHotseatEduController = hotseatEduController;
    }

    private HotseatEduController mHotseatEduController;

    public HotseatEduDialog(Context context, AttributeSet attr) {
        this(context, attr, 0);
    }

    public HotseatEduDialog(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContent = this;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        setTranslationShift(TRANSLATION_SHIFT_CLOSED);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mHotseatWrapper = findViewById(R.id.hotseat_wrapper);
        mSampleHotseat = findViewById(R.id.sample_prediction);

        Context context = getContext();
        DeviceProfile grid = mActivityContext.getDeviceProfile();
        Rect padding = grid.getHotseatLayoutPadding(context);

        mSampleHotseat.getLayoutParams().height = grid.cellHeightPx;
        mSampleHotseat.setGridSize(grid.numShownHotseatIcons, 1);
        mSampleHotseat.setPadding(padding.left, 0, padding.right, 0);

        Button turnOnBtn = findViewById(R.id.turn_predictions_on);
        turnOnBtn.setOnClickListener(this::onAccept);

        mDismissBtn = findViewById(R.id.no_thanks);
        mDismissBtn.setOnClickListener(this::onDismiss);

        LinearLayout buttonContainer = findViewById(R.id.button_container);
        int adjustedMarginEnd = grid.hotseatBarEndOffset - buttonContainer.getPaddingEnd();
        if (InvariantDeviceProfile.INSTANCE.get(context)
                .getDeviceProfile(context).isTaskbarPresent && adjustedMarginEnd > 0) {
            ((LinearLayout.LayoutParams) buttonContainer.getLayoutParams()).setMarginEnd(
                    adjustedMarginEnd);
        }
    }

    private void onAccept(View v) {
        mHotseatEduController.migrate();
        handleClose(true);

        mHotseatEduController.moveHotseatItems();
        mHotseatEduController.finishOnboarding();
        mActivityContext.getStatsLogManager().logger().log(LAUNCHER_HOTSEAT_EDU_ACCEPT);
    }

    private void onDismiss(View v) {
        mHotseatEduController.showDimissTip();
        mHotseatEduController.finishOnboarding();
        mActivityContext.getStatsLogManager().logger().log(LAUNCHER_HOTSEAT_EDU_DENY);
        handleClose(true);
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_ON_BOARD_POPUP) != 0;
    }

    @Override
    public void setInsets(Rect insets) {
        int leftInset = insets.left - mInsets.left;
        int rightInset = insets.right - mInsets.right;
        int bottomInset = insets.bottom - mInsets.bottom;
        mInsets.set(insets);
        if (mActivityContext.getOrientation() == Configuration.ORIENTATION_PORTRAIT) {
            setPadding(leftInset, getPaddingTop(), rightInset, 0);
            mHotseatWrapper.setPadding(mHotseatWrapper.getPaddingLeft(), getPaddingTop(),
                    mHotseatWrapper.getPaddingRight(), bottomInset);
            mHotseatWrapper.getLayoutParams().height =
                    mActivityContext.getDeviceProfile().hotseatBarSizePx + insets.bottom;

        } else {
            setPadding(0, getPaddingTop(), 0, 0);
            mHotseatWrapper.setPadding(mHotseatWrapper.getPaddingLeft(), getPaddingTop(),
                    mHotseatWrapper.getPaddingRight(),
                    (int) getResources().getDimension(R.dimen.bottom_sheet_edu_padding));
            ((TextView) findViewById(R.id.hotseat_edu_heading)).setText(
                    R.string.hotseat_edu_title_migrate_landscape);
            ((TextView) findViewById(R.id.hotseat_edu_content)).setText(
                    R.string.hotseat_edu_message_migrate_landscape);
        }
    }

    private void animateOpen() {
        if (mIsOpen || mOpenCloseAnimator.isRunning()) {
            return;
        }
        mIsOpen = true;
        mOpenCloseAnimator.setValues(
                PropertyValuesHolder.ofFloat(TRANSLATION_SHIFT, TRANSLATION_SHIFT_OPENED));
        mOpenCloseAnimator.setInterpolator(Interpolators.FAST_OUT_SLOW_IN);
        mOpenCloseAnimator.start();
    }

    @Override
    protected void handleClose(boolean animate) {
        handleClose(true, DEFAULT_CLOSE_DURATION);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        handleClose(false);
    }

    @Override
    protected int getScrimColor(Context context) {
        return FINAL_SCRIM_BG_COLOR;
    }

    private void populatePreview(List<WorkspaceItemInfo> predictions) {
        for (int i = 0; i < mActivityContext.getDeviceProfile().numShownHotseatIcons; i++) {
            WorkspaceItemInfo info = predictions.get(i);
            PredictedAppIcon icon = PredictedAppIcon.createIcon(mSampleHotseat, info);
            icon.setEnabled(false);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            icon.verifyHighRes();
            CellLayoutLayoutParams lp = new CellLayoutLayoutParams(i, 0, 1, 1, -1);
            mSampleHotseat.addViewToCellLayout(icon, i, info.getViewId(), lp, true);
        }
    }

    /**
     * Opens User education dialog with a list of suggested apps
     */
    public void show(List<WorkspaceItemInfo> predictions) {
        if (getParent() != null
                || predictions.size() < mActivityContext.getDeviceProfile().numShownHotseatIcons
                || mHotseatEduController == null) {
            return;
        }
        AbstractFloatingView.closeAllOpenViews(mActivityContext);
        attachToContainer();
        animateOpen();
        populatePreview(predictions);
        mActivityContext.getStatsLogManager().logger().log(LAUNCHER_HOTSEAT_EDU_SEEN);
    }

    /**
     * Factory method for HotseatPredictionUserEdu dialog
     */
    public static HotseatEduDialog getDialog(Launcher launcher) {
        LayoutInflater layoutInflater = LayoutInflater.from(launcher);
        return (HotseatEduDialog) layoutInflater.inflate(
                R.layout.predicted_hotseat_edu, launcher.getDragLayer(),
                false);

    }
}
