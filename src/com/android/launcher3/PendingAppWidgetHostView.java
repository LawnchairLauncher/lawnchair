package com.android.launcher3;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

public class PendingAppWidgetHostView extends LauncherAppWidgetHostView implements OnClickListener {

    int mRestoreStatus;

    private TextView mDefaultView;
    private OnClickListener mClickListener;

    public PendingAppWidgetHostView(Context context, int restoreStatus) {
        super(context);
        mRestoreStatus = restoreStatus;
    }

    @Override
    public void updateAppWidgetSize(Bundle newOptions, int minWidth, int minHeight, int maxWidth,
            int maxHeight) {
        // No-op
    }

    @Override
    protected View getDefaultView() {
        if (mDefaultView == null) {
            mDefaultView = (TextView) mInflater.inflate(R.layout.appwidget_not_ready, this, false);
            mDefaultView.setOnClickListener(this);
            applyState();
        }
        return mDefaultView;
    }

    @Override
    public void setOnClickListener(OnClickListener l) {
        mClickListener = l;
    }

    public void setStatus(int status) {
        if (mRestoreStatus != status) {
            mRestoreStatus = status;
            applyState();
        }
    }

    @Override
    public boolean isReinflateRequired() {
        // Re inflate is required if the the widget is restored.
        return mRestoreStatus == LauncherAppWidgetInfo.RESTORE_COMPLETED;
    }

    private void applyState() {
        if (mDefaultView != null) {
            if (isReadyForClickSetup()) {
                mDefaultView.setText(R.string.gadget_setup_text);
            } else {
                mDefaultView.setText(R.string.gadget_pending_text);
            }
        }
    }

    @Override
    public void onClick(View v) {
        // AppWidgetHostView blocks all click events on the root view. Instead handle click events
        // on the content and pass it along.
        if (mClickListener != null) {
            mClickListener.onClick(this);
        }
    }

    public boolean isReadyForClickSetup() {
        return (mRestoreStatus & LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY) == 0
                && (mRestoreStatus & LauncherAppWidgetInfo.FLAG_UI_NOT_READY) != 0;
    }
}
