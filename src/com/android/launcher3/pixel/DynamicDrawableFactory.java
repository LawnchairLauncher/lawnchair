package com.android.launcher3.pixel;

import com.android.launcher3.FastBitmapDrawable;
import com.android.launcher3.ItemInfo;
import android.graphics.Bitmap;
import android.content.Context;
import com.android.launcher3.graphics.DrawableFactory;

public class DynamicDrawableFactory extends DrawableFactory
{
    ClockUpdateReceiver du;

    public DynamicDrawableFactory(final Context context) {
        this.du = ClockUpdateReceiver.getInstance(context);
    }

    public FastBitmapDrawable newIcon(final Bitmap bitmap, final ItemInfo itemInfo) {
        if (itemInfo != null && itemInfo.itemType == 0 && ClockUpdateReceiver.componentName.equals(itemInfo.getTargetComponent())) {
            final ClockStatus b = new ClockStatus(bitmap, this.du);
            b.setFilterBitmap(true);
            return b;
        }
        return super.newIcon(bitmap, itemInfo);
    }
}

