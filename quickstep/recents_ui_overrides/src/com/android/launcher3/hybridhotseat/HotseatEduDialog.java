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

import static com.android.launcher3.logging.LoggerUtils.newLauncherEvent;
import static com.android.launcher3.userevent.nano.LauncherLogProto.ControlType.HYBRID_HOTSEAT_CANCELED;

import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.WorkspaceItemInfo;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.uioverrides.PredictedAppIcon;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.views.AbstractSlideInView;

import java.util.List;

/**
 * User education dialog for hybrid hotseat. Allows user to migrate hotseat items to a new page in
 * the workspace and shows predictions on the whole hotseat
 */
public class HotseatEduDialog extends AbstractSlideInView implements Insettable {

    private static final int DEFAULT_CLOSE_DURATION = 200;

    public static boolean shown = false;

    private final Rect mInsets = new Rect();
    private View mHotseatWrapper;
    private CellLayout mSampleHotseat;

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
    protected void onFinishInflate() {
        super.onFinishInflate();
        mHotseatWrapper = findViewById(R.id.hotseat_wrapper);
        mSampleHotseat = findViewById(R.id.sample_prediction);

        DeviceProfile grid = mLauncher.getDeviceProfile();
        Rect padding = grid.getHotseatLayoutPadding();

        mSampleHotseat.getLayoutParams().height = grid.cellHeightPx;
        mSampleHotseat.setGridSize(grid.inv.numHotseatIcons, 1);
        mSampleHotseat.setPadding(padding.left, 0, padding.right, 0);

        Button turnOnBtn = findViewById(R.id.turn_predictions_on);
        turnOnBtn.setOnClickListener(this::onMigrate);

        Button learnMoreBtn = findViewById(R.id.no_thanks);
        learnMoreBtn.setOnClickListener(this::onKeepDefault);

    }

    private void onMigrate(View v) {
        if (mHotseatEduController == null) return;
        handleClose(true);
        mHotseatEduController.migrate();
        mHotseatEduController.finishOnboarding();
        logUserAction(true);
        Toast.makeText(mLauncher, R.string.hotseat_items_migrated, Toast.LENGTH_LONG).show();
    }

    private void onKeepDefault(View v) {
        if (mHotseatEduController == null) return;
        Toast.makeText(getContext(), R.string.hotseat_no_migration, Toast.LENGTH_LONG).show();
        mHotseatEduController.finishOnboarding();
        logUserAction(false);
        handleClose(true);
    }

    @Override
    public void logActionCommand(int command) {
        // Since this is on-boarding popup, it is not a user controlled action.
    }

    @Override
    public int getLogContainerType() {
        return LauncherLogProto.ContainerType.TIP;
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
        setPadding(leftInset, getPaddingTop(), rightInset, 0);
        mHotseatWrapper.setPadding(mHotseatWrapper.getPaddingLeft(), getPaddingTop(),
                mHotseatWrapper.getPaddingRight(), bottomInset);
        mHotseatWrapper.getLayoutParams().height =
                mLauncher.getDeviceProfile().hotseatBarSizePx + insets.bottom;
    }

    private void logUserAction(boolean migrated) {
        LauncherLogProto.Action action = new LauncherLogProto.Action();
        LauncherLogProto.Target target = new LauncherLogProto.Target();
        action.type = LauncherLogProto.Action.Type.TOUCH;
        action.touch = LauncherLogProto.Action.Touch.TAP;
        target.containerType = LauncherLogProto.ContainerType.TIP;
        target.tipType = LauncherLogProto.TipType.HYBRID_HOTSEAT;
        target.controlType = migrated ? LauncherLogProto.ControlType.HYBRID_HOTSEAT_ACCEPTED
                : HYBRID_HOTSEAT_CANCELED;
        LauncherLogProto.LauncherEvent event = newLauncherEvent(action, target);
        UserEventDispatcher.newInstance(getContext()).dispatchUserEvent(event, null);
    }

    private void logOnBoardingSeen() {
        LauncherLogProto.Action action = new LauncherLogProto.Action();
        LauncherLogProto.Target target = new LauncherLogProto.Target();
        action.type = LauncherLogProto.Action.Type.TIP;
        target.containerType = LauncherLogProto.ContainerType.TIP;
        target.tipType = LauncherLogProto.TipType.HYBRID_HOTSEAT;
        LauncherLogProto.LauncherEvent event = newLauncherEvent(action, target);
        UserEventDispatcher.newInstance(getContext()).dispatchUserEvent(event, null);
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

    /**
     * Opens User education dialog with a list of suggested apps
     */
    public void show(List<WorkspaceItemInfo> predictions) {
        if (getParent() != null
                || predictions.size() < mLauncher.getDeviceProfile().inv.numHotseatIcons) {
            return;
        }
        mLauncher.getDragLayer().addView(this);
        logOnBoardingSeen();
        animateOpen();
        for (int i = 0; i < mLauncher.getDeviceProfile().inv.numHotseatIcons; i++) {
            WorkspaceItemInfo info = predictions.get(i);
            PredictedAppIcon icon = PredictedAppIcon.createIcon(mSampleHotseat, info);
            icon.setEnabled(false);
            icon.verifyHighRes();
            CellLayout.LayoutParams lp = new CellLayout.LayoutParams(i, 0, 1, 1);
            mSampleHotseat.addViewToCellLayout(icon, i, info.getViewId(), lp, true);
        }
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
