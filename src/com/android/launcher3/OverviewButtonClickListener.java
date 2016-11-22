package com.android.launcher3;

import android.view.View;

import com.android.launcher3.userevent.nano.LauncherLogProto.Action;

/**
 * A specialized listener for Overview buttons where both clicks and long clicks are logged
 * handled the same via {@link #handleViewClick(View)}.
 */
public abstract class OverviewButtonClickListener implements View.OnClickListener,
        View.OnLongClickListener {

    private int mControlType; /** ControlType enum as defined in {@link Action.Touch} */

    public OverviewButtonClickListener(int controlType) {
        mControlType = controlType;
    }

    public void attachTo(View v) {
        v.setOnClickListener(this);
        v.setOnLongClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if (shouldPerformClick(view)) {
            handleViewClick(view, Action.Touch.TAP);
        }
    }

    @Override
    public boolean onLongClick(View view) {
        if (shouldPerformClick(view)) {
            handleViewClick(view, Action.Touch.LONGPRESS);
        }
        return true;
    }

    private boolean shouldPerformClick(View view) {
        return !Launcher.getLauncher(view.getContext()).getWorkspace().isSwitchingState();
    }

    private void handleViewClick(View view, int action) {
        handleViewClick(view);
        Launcher.getLauncher(view.getContext()).getUserEventDispatcher()
                .logActionOnControl(action, mControlType);
    }

    public abstract void handleViewClick(View view);
}