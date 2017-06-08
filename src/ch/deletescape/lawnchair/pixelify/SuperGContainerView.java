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
import ch.deletescape.lawnchair.util.TransformingTouchDelegate;

public class SuperGContainerView extends C0276c {
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
        this.bz = new TransformingTouchDelegate(this);
    }

    @Override
    public void bj(SharedPreferences sharedPreferences) {
        super.bj(sharedPreferences);
        if (this.bz != null) {
            this.bz.setDelegateView(this.bB);
        }
    }

    @Override
    protected int aK(boolean z) {
        if (this.bz != null) {
            float f;
            TransformingTouchDelegate transformingTouchDelegate = this.bz;
            if (z) {
                f = 0.0f;
            } else {
                f = getResources().getDimension(R.dimen.qsb_touch_extension);
            }
            transformingTouchDelegate.extendTouchBounds(f);
        }

        return z ? R.layout.qsb_with_mic : R.layout.qsb_without_mic;
    }

    @Override
    public void setPadding(int i, int i2, int i3, int i4) {
        super.setPadding(0, 0, 0, 0);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (this.bz != null) {
            this.bC.getWorkspace().findViewById(R.id.workspace_blocked_row).setTouchDelegate(this.bz);
        }
    }

    @Override
    protected boolean aM(SharedPreferences sharedPreferences) {
        return super.aM(sharedPreferences);
    }

    @Override
    protected void onMeasure(int i, int i2) {
        int i3;
        LayoutParams layoutParams;
        int i4 = -getResources().getDimensionPixelSize(R.dimen.qsb_overlap_margin);
        DeviceProfile deviceProfile = this.bC.getDeviceProfile();
        Rect workspacePadding = deviceProfile.getWorkspacePadding(sTempRect);
        int size = MeasureSpec.getSize(i) - i4;
        int i5 = (size - workspacePadding.left) - workspacePadding.right;
        size = DeviceProfile.calculateCellWidth(i5, deviceProfile.inv.numColumns) * deviceProfile.inv.numColumns;
        i4 += workspacePadding.left + ((i5 - size) / 2);
        i3 = size;
        size = i4;
        if (this.bB != null) {
            layoutParams = (LayoutParams) this.bB.getLayoutParams();
            layoutParams.width = i3 / deviceProfile.inv.numColumns;
            if (this.bD) {
                layoutParams.width = Math.max(layoutParams.width, getResources().getDimensionPixelSize(R.dimen.qsb_min_width_with_mic));
            }
            layoutParams.setMarginStart(size);
            layoutParams.resolveLayoutDirection(layoutParams.getLayoutDirection());
        }
        if (this.bE != null) {
            layoutParams = (LayoutParams) this.bE.getLayoutParams();
            layoutParams.width = size + (layoutParams.height / 2);
        }
        super.onMeasure(i, i2);
    }


    @Override
    protected void onLayout(boolean z, int i, int i2, int i3, int i4) {
        super.onLayout(z, i, i2, i3, i4);
        if (this.bz != null) {
            int i5 = 0;
            if (Utilities.isRtl(getResources())) {
                i5 = this.bB.getLeft() - this.bC.getDeviceProfile().getWorkspacePadding(sTempRect).left;
            }
            this.bz.setBounds(i5, this.bB.getTop(), this.bB.getWidth() + i5, this.bB.getBottom());
        }
    }

    @Override
    protected void aL(Rect rect, Intent intent) {
        int height = this.bB.getHeight() / 2;
        if (Utilities.isRtl(getResources())) {
            rect.right = height + getRight();
        } else {
            rect.left = -height;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent motionEvent) {
        if (this.bz != null) {
            return false;
        }
        return super.dispatchTouchEvent(motionEvent);
    }
}