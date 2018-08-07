package com.google.android.apps.nexuslauncher.superg;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.TransformingTouchDelegate;

public class SuperGContainerView extends BaseGContainerView {
    private static final Rect sTempRect = new Rect();
    private final TransformingTouchDelegate mTouchDelegate; //bY

    public SuperGContainerView(Context paramContext) {
        this(paramContext, null);
    }

    public SuperGContainerView(Context paramContext, AttributeSet paramAttributeSet) {
        this(paramContext, paramAttributeSet, 0);
    }

    public SuperGContainerView(Context paramContext, AttributeSet paramAttributeSet, int paramInt) {
        super(paramContext, paramAttributeSet, paramInt);
        if (mLauncher.useVerticalBarLayout()) {
            View.inflate(paramContext, R.layout.qsb_blocker_view, this);
            mTouchDelegate = null;
        } else {
            mTouchDelegate = new TransformingTouchDelegate(this);
        }
    }

    @Override
    public void applyOpaPreference() {
        super.applyOpaPreference();
        if (mTouchDelegate != null) {
            mTouchDelegate.setDelegateView(mQsbView);
        }
    }

    @Override
    protected int getQsbView(boolean withMic) {
        if (mTouchDelegate != null) {
            mTouchDelegate.extendTouchBounds(getResources().getDimension(R.dimen.qsb_touch_extension));
        }
        return R.layout.qsb_without_mic;
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding(0, 0, 0, 0);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mTouchDelegate != null) {
            View view = mLauncher.getWorkspace().findViewById(R.id.workspace_blocked_row);
            if (view != null) {
                view.setTouchDelegate(mTouchDelegate);
            }
        }
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
            marginStart = qsbOverlapMargin + (workspacePadding.left + (fullWidth - qsbWidth) / 2);
        }

        if (mQsbView != null) {
            LayoutParams layoutParams = (LayoutParams) mQsbView.getLayoutParams();
            layoutParams.width = qsbWidth / deviceProfile.inv.numColumns;
            if (mLauncher.useVerticalBarLayout()) {
                layoutParams.width = Math.max(layoutParams.width, getResources().getDimensionPixelSize(R.dimen.qsb_min_width_with_mic));
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
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mTouchDelegate != null) {
            int leftPos = 0;
            if (Utilities.isRtl(getResources())) {
                leftPos = mQsbView.getLeft() - mLauncher.getDeviceProfile().workspacePadding.left;
            }
            mTouchDelegate.setBounds(leftPos, mQsbView.getTop(), mQsbView.getWidth() + leftPos, mQsbView.getBottom());
        }
    }

    @Override
    protected void setGoogleAnimationStart(Rect rect, Intent intent) {
        if (!mLauncher.useVerticalBarLayout()) {
            int height = mQsbView.getHeight() / 2;
            if (Utilities.isRtl(getResources())) {
                rect.right = height + getRight();
            } else {
                rect.left = -height;
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent motionEvent) {
        return mTouchDelegate == null && super.dispatchTouchEvent(motionEvent);
    }
}