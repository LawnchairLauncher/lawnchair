package com.google.android.apps.nexuslauncher.superg;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;

public class SuperGContainerView extends BaseGContainerView {

    public SuperGContainerView(Context paramContext) {
        this(paramContext, null);
    }

    public SuperGContainerView(Context paramContext, AttributeSet paramAttributeSet) {
        this(paramContext, paramAttributeSet, 0);
    }

    public SuperGContainerView(Context paramContext, AttributeSet paramAttributeSet, int paramInt) {
        super(paramContext, paramAttributeSet, paramInt);
        View.inflate(paramContext, R.layout.qsb_blocker_view, this);
    }

    @Override
    protected int getQsbView(boolean withMic) {
        return R.layout.qsb_without_mic;
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding(0, 0, 0, 0);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int qsbOverlapMargin = -getResources().getDimensionPixelSize(R.dimen.qsb_overlap_margin);
        DeviceProfile deviceProfile = mLauncher.getDeviceProfile();
        int size = MeasureSpec.getSize(widthMeasureSpec) - qsbOverlapMargin;

        int qsbWidth;
        int marginStart;
        if (deviceProfile.isVerticalBarLayout()) {
            qsbWidth = size;
            marginStart = qsbOverlapMargin + getResources().getDimensionPixelSize(R.dimen.qsb_button_elevation);
        } else {
            Rect workspacePadding = deviceProfile.workspacePadding;
            int fullWidth = size - workspacePadding.left - workspacePadding.right;
            qsbWidth = DeviceProfile.calculateCellWidth(fullWidth, deviceProfile.inv.numColumns) * deviceProfile.inv.numColumns;
            marginStart = 0;
        }

        if (mQsbView != null) {
            LayoutParams layoutParams = (LayoutParams) mQsbView.getLayoutParams();
            layoutParams.width = qsbWidth / deviceProfile.inv.numColumns;
            if (mLauncher.useVerticalBarLayout()) {
                layoutParams.width = Math.max(layoutParams.width,
                        getResources().getDimensionPixelSize(R.dimen.qsb_min_width_with_mic));
            } else {
                layoutParams.width = Math.max(layoutParams.width,
                        getResources().getDimensionPixelSize(R.dimen.qsb_min_width_portrait));
            }
            layoutParams.setMarginStart(marginStart);
            layoutParams.resolveLayoutDirection(layoutParams.getLayoutDirection());
        }
        if (mConnectorView != null) {
            LayoutParams layoutParams = (LayoutParams) mConnectorView.getLayoutParams();
            layoutParams.width = marginStart + layoutParams.height / 2;
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void setGoogleAnimationStart(Rect rect, Intent intent) {

    }
}