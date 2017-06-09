package ch.deletescape.lawnchair.pixelify;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;

import ch.deletescape.lawnchair.DeviceProfile;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.config.FeatureFlags;
import ch.deletescape.lawnchair.util.TransformingTouchDelegate;

public class SuperGContainerView extends BaseQsbView {
    private static final Rect sTempRect = new Rect();
    private final TransformingTouchDelegate bz;


    public SuperGContainerView(Context context) {
        this(context, null);
    }

    public SuperGContainerView(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public SuperGContainerView(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        if (FeatureFlags.useFullWidthSearchbar(getContext())) {
            bz = null;
        } else {
            bz = new TransformingTouchDelegate(this);
        }
    }

    @Override
    public void applyVoiceSearchPreference(SharedPreferences prefs) {
        if (!FeatureFlags.useFullWidthSearchbar(getContext())) {
            super.applyVoiceSearchPreference(prefs);
            if (bz != null) {
                bz.setDelegateView(mQsbView);
            }
        }
    }

    @Override
    protected int getQsbView(boolean withMic) {
        if (bz != null) {
            float f;
            if (withMic) {
                f = 0.0f;
            } else {
                f = getResources().getDimension(R.dimen.qsb_touch_extension);
            }
            bz.extendTouchBounds(f);
        }

        return withMic ? R.layout.qsb_with_mic : R.layout.qsb_without_mic;
    }

    @Override
    public void setPadding(int i, int i2, int i3, int i4) {
        super.setPadding(0, 0, 0, 0);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (bz != null) {
            mLauncher.getWorkspace().findViewById(R.id.workspace_blocked_row).setTouchDelegate(bz);
        }
    }

    @Override
    protected void onMeasure(int i, int i2) {
        int i3;
        LayoutParams layoutParams;
        int i4 = -getResources().getDimensionPixelSize(R.dimen.qsb_overlap_margin);
        DeviceProfile deviceProfile = mLauncher.getDeviceProfile();
        Rect workspacePadding = deviceProfile.getWorkspacePadding(sTempRect);
        int size = MeasureSpec.getSize(i) - i4;
        int i5 = (size - workspacePadding.left) - workspacePadding.right;
        size = DeviceProfile.calculateCellWidth(i5, deviceProfile.inv.numColumns) * deviceProfile.inv.numColumns;
        i4 += workspacePadding.left + ((i5 - size) / 2);
        i3 = size;
        size = i4;
        if (mQsbView != null) {
            layoutParams = (LayoutParams) mQsbView.getLayoutParams();
            layoutParams.width = i3 / deviceProfile.inv.numColumns;
            if (showMic) {
                layoutParams.width = Math.max(layoutParams.width, getResources().getDimensionPixelSize(R.dimen.qsb_min_width_with_mic));
            }
            layoutParams.setMarginStart(size);
            layoutParams.resolveLayoutDirection(layoutParams.getLayoutDirection());
        }
        if (qsbConnector != null) {
            layoutParams = (LayoutParams) qsbConnector.getLayoutParams();
            layoutParams.width = size + (layoutParams.height / 2);
        }
        super.onMeasure(i, i2);
    }


    @Override
    protected void onLayout(boolean z, int i, int i2, int i3, int i4) {
        super.onLayout(z, i, i2, i3, i4);
        if (bz != null) {
            int i5 = 0;
            if (Utilities.isRtl(getResources())) {
                i5 = mQsbView.getLeft() - mLauncher.getDeviceProfile().getWorkspacePadding(sTempRect).left;
            }
            bz.setBounds(i5, mQsbView.getTop(), mQsbView.getWidth() + i5, mQsbView.getBottom());
        }
    }

    @Override
    protected void aL(Rect rect, Intent intent) {
        int height = mQsbView.getHeight() / 2;
        if (Utilities.isRtl(getResources())) {
            rect.right = height + getRight();
        } else {
            rect.left = -height;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent motionEvent) {
        return bz == null && super.dispatchTouchEvent(motionEvent);
    }
}