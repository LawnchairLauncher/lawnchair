package com.google.android.apps.nexuslauncher.clock;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.SystemClock;
import com.android.launcher3.FastBitmapDrawable;

import java.util.TimeZone;

public class AutoUpdateClock extends FastBitmapDrawable implements Runnable {
    private ClockLayers mLayers;

    AutoUpdateClock(Bitmap bitmap, ClockLayers layers) {
        super(bitmap);
        mLayers = layers;
    }

    private void rescheduleUpdate() {
        unscheduleSelf(this);
        scheduleSelf(this, SystemClock.uptimeMillis() + 100);
    }

    void updateLayers(ClockLayers layers) {
        mLayers = layers;
        if (mLayers != null) {
            mLayers.mDrawable.setBounds(getBounds());
        }
        invalidateSelf();
    }

    void setTimeZone(TimeZone timeZone) {
        if (mLayers != null) {
            mLayers.setTimeZone(timeZone);
            invalidateSelf();
        }
    }

    @Override
    public void drawInternal(Canvas canvas, Rect bounds) {
        if (mLayers != null) {
            canvas.drawBitmap(mLayers.iconBitmap, null, bounds, mPaint);
            canvas.scale(mLayers.scale, mLayers.scale, bounds.exactCenterX() + ((float) mLayers.offset), bounds.exactCenterY() + ((float) mLayers.offset));
            mLayers.clipToMask(canvas);
            mLayers.drawForeground(canvas);
            rescheduleUpdate();
        } else {
            super.drawInternal(canvas, bounds);
        }
    }

    @Override
    protected void onBoundsChange(final Rect bounds) {
        super.onBoundsChange(bounds);
        if (mLayers != null) {
            mLayers.mDrawable.setBounds(bounds);
        }
    }

    @Override
    public void run() {
        if (mLayers.updateAngles()) {
            invalidateSelf();
        } else {
            rescheduleUpdate();
        }
    }
}
