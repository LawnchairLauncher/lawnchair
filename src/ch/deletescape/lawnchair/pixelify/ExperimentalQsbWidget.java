package ch.deletescape.lawnchair.pixelify;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.util.AttributeSet;

import ch.deletescape.lawnchair.DeviceProfile;
import ch.deletescape.lawnchair.R;

public class ExperimentalQsbWidget extends C0276c {
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
    protected int aK(boolean z) {
        return z ? R.layout.qsb_wide_with_mic : R.layout.qsb_wide_without_mic;
    }

    @Override
    protected boolean aM(SharedPreferences sharedPreferences) {
        return false;
    }

    @Override
    protected void onMeasure(int i, int i2) {
        if (this.bB != null) {
            DeviceProfile deviceProfile = this.bC.getDeviceProfile();
            int size = MeasureSpec.getSize(i);
            ((LayoutParams) this.bB.getLayoutParams()).width = size - (DeviceProfile.calculateCellWidth(size, deviceProfile.inv.numColumns) - deviceProfile.iconSizePx);
        }
        super.onMeasure(i, i2);
    }

    @Override
    protected void aL(Rect rect, Intent intent) {
        if (!this.bD) {
            intent.putExtra("source_mic_alpha", 0.0f);
        }
    }
}