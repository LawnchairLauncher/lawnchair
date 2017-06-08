package ch.deletescape.lawnchair.pixelify;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.util.AttributeSet;

import ch.deletescape.lawnchair.DeviceProfile;
import ch.deletescape.lawnchair.R;

public class ExperimentalQsbWidget extends BaseQsbView {
    public ExperimentalQsbWidget(Context context) {
        this(context, null);
    }

    public ExperimentalQsbWidget(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public ExperimentalQsbWidget(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
    }

    @Override
    protected int getQsbView(boolean withMic) {
        return withMic ? R.layout.qsb_wide_with_mic : R.layout.qsb_wide_without_mic;
    }

    @Override
    protected boolean isMinusOneEnabled(SharedPreferences sharedPreferences) {
        return false;
    }

    @Override
    protected void onMeasure(int i, int i2) {
        if (this.mQsbView != null) {
            DeviceProfile deviceProfile = this.mLauncher.getDeviceProfile();
            int size = MeasureSpec.getSize(i);
            ((LayoutParams) this.mQsbView.getLayoutParams()).width = size - (DeviceProfile.calculateCellWidth(size, deviceProfile.inv.numColumns) - deviceProfile.iconSizePx);
        }
        super.onMeasure(i, i2);
    }

    @Override
    protected void aL(Rect rect, Intent intent) {
        if (!this.showMic) {
            intent.putExtra("source_mic_alpha", 0.0f);
        }
    }
}